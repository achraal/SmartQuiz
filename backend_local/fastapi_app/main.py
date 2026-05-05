from fastapi import FastAPI, Request, HTTPException, File, UploadFile, Form
import httpx, os, cv2
from dotenv import load_dotenv
from ultralytics import YOLO
import numpy as np
from fastapi import BackgroundTasks
import librosa
import numpy as np
import time
import threading
import subprocess
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
import asyncio
from redis import Redis
from rq import Queue

load_dotenv()

app = FastAPI()
# On crée un verrou pour éviter que YOLO ne crash si deux threads l'utilisent

# Pour ton Supabase local (Docker)
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

# --- CONNEXION UNIQUE HTTPX (Pour la vitesse) ---
# On crée un client global pour éviter de recréer des connexions à chaque requête
http_client = httpx.AsyncClient()

@app.on_event("shutdown")
async def shutdown_event():
    await http_client.aclose()

# Connexion à Redis
redis_conn = Redis(host='localhost', port=6379)
q = Queue(connection=redis_conn)


@app.get("/check-link")
async def check_link():
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    try:
        response = await http_client.get(f"{SUPABASE_URL}/rest/v1/", headers=headers)
        return {"status": "Success", "supabase_info": response.json()}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/auth/register")
async def register_user(user_data: dict):
    url = f"{SUPABASE_URL}/auth/v1/signup"
    headers = {"apikey": os.getenv("SUPABASE_ANON_KEY"), "Content-Type": "application/json"}
    
    response = await http_client.post(url, json=user_data, headers=headers)
    
    if response.status_code != 200:
        # On récupère le message d'erreur de Supabase (ex: "User already registered")
        error_detail = response.json().get("msg", "Registration failed")
        raise HTTPException(status_code=response.status_code, detail=error_detail)
        
    return response.json()

@app.post("/auth/login")
async def login_user(credentials: dict):
    url = f"{SUPABASE_URL}/auth/v1/token?grant_type=password"
    headers = {"apikey": os.getenv("SUPABASE_ANON_KEY"), "Content-Type": "application/json"}
    
    response = await http_client.post(url, json=credentials, headers=headers)
    
    if response.status_code != 200:
        # En cas d'email ou mdp incorrect, Supabase renvoie souvent une erreur 400
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, 
            detail="Email ou mot de passe incorrect"
        )
        
    return response.json()

@app.get("/quiz/questions/{level}")
async def get_questions(level: int):
    url = f"{SUPABASE_URL}/rest/v1/quizzes?level_number=eq.{level}"
    headers = {"apikey": os.getenv("SUPABASE_ANON_KEY")}
    response = await http_client.get(url, headers=headers)
    return response.json()

@app.post("/quiz/submit")
async def submit_quiz(data: dict):
    user_id = data.get("user_id")
    score = data.get("score")
    url = f"{SUPABASE_URL}/rest/v1/profiles?id=eq.{user_id}"
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}", "Content-Type": "application/json"}
    await http_client.patch(url, json={"best_score": score}, headers=headers)
    return {"status": "Score synchronisé"}

# --- LA PARTIE QUI CHANGE : MONITORING LÉGER ---

@app.post("/monitoring/upload")
async def upload_monitoring_file(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    file_path: str = Form(...) 
):
    file_content = await file.read()
    u_id = file_path.split('/')[0]

    # OPTIMISATION : On délègue tout au WORKER via Redis
    if "photos" in file_path.lower():
        # On envoie la tâche à Redis, le worker s'en occupe
        from worker import analyze_photo_task # Import local pour éviter les soucis
        q.enqueue(analyze_photo_task, file_content, u_id)

    if "audios" in file_path.lower() or "temp_audio" in file_path.lower():
        if len(file_content) > 1000:
            from worker import analyze_audio_task
            q.enqueue(analyze_audio_task, file_content, u_id)

    # Le stockage reste en background task car c'est juste du transfert réseau
    if "temp_audio" not in file_path.lower():
        background_tasks.add_task(upload_to_supabase_storage, file_content, file_path, file.content_type)

    return {"status": "ok"}

# --- FONCTIONS UTILITAIRES ---

async def upload_to_supabase_storage(content, path, content_type):
    url = f"{SUPABASE_URL}/storage/v1/object/monitoring/{path}"
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": content_type
    }
    await http_client.post(url, content=content, headers=headers)

@app.get("/user/status/{user_id}")
async def get_user_status(user_id: str):
    url = f"{SUPABASE_URL}/rest/v1/profiles?id=eq.{user_id}&select=is_cheating,fraud_reason"
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    response = await http_client.get(url, headers=headers)
    data = response.json()
    return data[0] if data else {"is_cheating": False, "fraud_reason": None, "last_fraud_detected_at": None}
       

@app.post("/monitoring/location")
async def save_location(data: dict):
    # Centralise l'enregistrement GPS
    url = f"{SUPABASE_URL}/rest/v1/monitoring"
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json"
    }
    async with httpx.AsyncClient() as client:
        await client.post(url, json=data, headers=headers)
    return {"status": "Position enregistrée"}


from ultralytics import YOLO
import cv2
import numpy as np
import httpx
from fastapi import UploadFile, File, Form

# Chargement du modèle au démarrage  

@app.get("/admin/students")
async def list_students():
    # Récupère tous les profils triés par score
    url = f"{SUPABASE_URL}/rest/v1/profiles?select=*&order=best_score.desc"
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    async with httpx.AsyncClient() as client:
        response = await client.get(url, headers=headers)
        return response.json()

@app.get("/admin/monitoring/{student_id}")
async def get_full_monitoring(student_id: str):
        
    # On utilise la clé service_role ou anon pour l'accès storage interne
    headers = {
        "apikey": SUPABASE_KEY, 
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    
    # L'URL de base pour accéder aux objets (sans le slash final)
    base_storage_url = f"{SUPABASE_URL}/storage/v1/object/authenticated/monitoring"

    async with httpx.AsyncClient() as client:
        user_resp = await client.get(
            f"{SUPABASE_URL}/rest/v1/profiles?id=eq.{student_id}&select=is_cheating,fraud_reason,last_fraud_detected_at",
            headers=headers
        )
        user_data = user_resp.json()
        student_info = user_data[0] if user_data else None
        # 1. Récupération des points GPS
        gps_resp = await client.get(
            f"{SUPABASE_URL}/rest/v1/monitoring?user_id=eq.{student_id}&select=*", 
            headers=headers
        )
        gps_data = gps_resp.json()

        # 2. Lister le contenu du dossier de l'étudiant
        sessions_resp = await client.post(
            f"{SUPABASE_URL}/storage/v1/object/list/monitoring",
            json={"prefix": f"{student_id}/"},
            headers=headers
        )
        
        raw_items = sessions_resp.json()
        
        # CORRECTION : Un dossier dans Supabase API a 'id': None
        session_folders = [f for f in raw_items if f.get('id') is None]

        final_sessions = []

        for folder in session_folders:
            date_folder = folder['name']
            
            session_entry = {
                "date": date_folder,
                "front_photos": [],
                "back_photos": [],
                "audios": []
            }

            # On boucle sur les sous-dossiers attendus
            for sub in ["photos/front", "photos/back", "audios"]:
                # Chemin relatif dans le bucket
                path_prefix = f"{student_id}/{date_folder}/{sub}/"
                
                res = await client.post(
                    f"{SUPABASE_URL}/storage/v1/object/list/monitoring",
                    json={"prefix": path_prefix}, 
                    headers=headers
                )
                
                files = res.json()
                
                for f in files:
                    # Si 'id' n'est pas None, c'est un fichier réel
                    if f.get('id') is not None:
                        # Construction de l'URL d'authentification
                        # path_prefix contient déjà le slash final, f['name'] est le nom du fichier
                        file_url = f"{base_storage_url}/{path_prefix}{f['name']}"
                        
                        if "photos/front" in path_prefix:
                            session_entry["front_photos"].append(file_url)
                        elif "photos/back" in path_prefix:
                            session_entry["back_photos"].append(file_url)
                        elif "audios" in path_prefix:
                            session_entry["audios"].append(file_url)
            
            final_sessions.append(session_entry)

        return {
            "student_info": student_info,
            "gps_points": gps_data,
            "sessions": final_sessions
        }
    

@app.post("/admin/reset-fraud/{student_id}")
async def reset_student_fraud(student_id: str):
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal"
    }
    
    # On remet les compteurs à zéro
    payload = {
        "is_cheating": False,
        "fraud_reason": None,
        "last_fraud_detected_at": None
    }

    async with httpx.AsyncClient() as client:
        resp = await client.patch(
            f"{SUPABASE_URL}/rest/v1/profiles?id=eq.{student_id}",
            json=payload,
            headers=headers
        )
        
        if resp.status_code == 204 or resp.status_code == 200:
            return {"status": "success", "message": "L'étudiant peut repasser le quiz"}
        else:
            raise HTTPException(status_code=400, detail="Erreur lors de la réinitialisation")

@app.post("/admin/quiz/add")
async def add_quiz_level(question: dict):
    # Gère l'ajout automatique du prochain niveau
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}","Prefer": "return=representation"}
    async with httpx.AsyncClient() as client:
        # On poste directement la question
        resp = await client.post(f"{SUPABASE_URL}/rest/v1/quizzes", json=question, headers=headers)
        return resp.json()
    
@app.get("/admin/quiz/last-level")
async def get_last_level():
    # Récupère uniquement le niveau le plus élevé
    url = f"{SUPABASE_URL}/rest/v1/quizzes?select=level_number&order=level_number.desc&limit=1"
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    async with httpx.AsyncClient() as client:
        resp = await client.get(url, headers=headers)
        data = resp.json()
        if data and len(data) > 0:
            return {"last_level": data[0]['level_number']}
        return {"last_level": 0}
        
async def update_fraud_status(user_id: str, status: bool, reason: str):
    url = f"{SUPABASE_URL}/rest/v1/profiles?id=eq.{user_id}"
    
    # On génère le timestamp actuel
    now = datetime.utcnow().isoformat() 
    
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal"
    }
    
    payload = {
        "is_cheating": status, 
        "fraud_reason": reason,
        "last_fraud_detected_at": now  # Ajout de l'heure de détection
    }
    
    async with httpx.AsyncClient() as client:
        resp = await client.patch(url, json=payload, headers=headers)
        print(f"  [SUPABASE] Profil {user_id} marqué tricheur à {now}")