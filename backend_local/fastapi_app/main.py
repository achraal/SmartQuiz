from fastapi import FastAPI, Request, HTTPException, File, UploadFile, Form
import httpx, os, cv2
from dotenv import load_dotenv
from ultralytics import YOLO
import numpy as np

load_dotenv()

app = FastAPI()

# Pour ton Supabase local (Docker)
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

@app.get("/check-link")
async def check_link():
    # On tente de lister les tables ou simplement de contacter l'endpoint REST
    # pour voir si Supabase répond avec nos clés
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    
    async with httpx.AsyncClient() as client:
        try:
            # On interroge la racine de l'API REST de Supabase
            response = await client.get(f"{SUPABASE_URL}/rest/v1/", headers=headers)
            
            if response.status_code == 200:
                return {
                    "status": "Success",
                    "message": "FastAPI communique parfaitement avec Supabase !",
                    "supabase_info": response.json()
                }
            else:
                return {
                    "status": "Error",
                    "code": response.status_code,
                    "detail": "Supabase a répondu mais avec une erreur. Vérifie tes clés."
                }
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Impossible de joindre Supabase: {str(e)}")
        

@app.post("/auth/register")
async def register_user(user_data: dict):
    # On relaie vers l'URL de ton Supabase local (Docker)
    url = f"{SUPABASE_URL}/auth/v1/signup"
    headers = {
        "apikey": os.getenv("SUPABASE_ANON_KEY"), # On utilise la anon_key pour l'auth
        "Content-Type": "application/json"
    }
    async with httpx.AsyncClient() as client:
        response = await client.post(url, json=user_data, headers=headers)
        if response.status_code != 200:
            raise HTTPException(status_code=response.status_code, detail=response.text)
        return response.json()

@app.post("/auth/login")
async def login_user(credentials: dict):
    url = f"{SUPABASE_URL}/auth/v1/token?grant_type=password"
    headers = {
        "apikey": os.getenv("SUPABASE_ANON_KEY"),
        "Content-Type": "application/json"
    }
    async with httpx.AsyncClient() as client:
        response = await client.post(url, json=credentials, headers=headers)
        if response.status_code != 200:
            raise HTTPException(status_code=response.status_code, detail="Identifiants incorrects")
        return response.json()


@app.get("/quiz/questions/{level}")
async def get_questions(level: int):
    # FastAPI récupère les questions pour Android
    url = f"{SUPABASE_URL}/rest/v1/quizzes?level_number=eq.{level}"
    headers = {"apikey": os.getenv("SUPABASE_ANON_KEY")}
    
    async with httpx.AsyncClient() as client:
        response = await client.get(url, headers=headers)
        return response.json()

@app.post("/quiz/submit")
async def submit_quiz(data: dict):
    # Ici, FastAPI peut valider le score avant de l'enregistrer avec la SECRET_KEY
    user_id = data.get("user_id")
    score = data.get("score")
    
    url = f"{SUPABASE_URL}/rest/v1/profiles?id=eq.{user_id}"
    headers = {
        "apikey": SUPABASE_KEY, # Secret Key
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json"
    }
    
    async with httpx.AsyncClient() as client:
        await client.patch(url, json={"best_score": score}, headers=headers)
    return {"status": "Score synchronisé"}

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
fraud_model = YOLO('yolov8n.pt') 

@app.post("/monitoring/upload")
async def upload_monitoring_file(
    file: UploadFile = File(...),
    file_path: str = Form(...) 
):
    # 1. Lecture du contenu
    file_content = await file.read()
    
    # --- DÉBUT ANALYSE IA ---
    # On n'analyse que les photos venant de la caméra frontale pour ne pas surcharger le CPU
    if "photos/front" in file_path:
        # Conversion du binaire en image utilisable par OpenCV/YOLO
        nparr = np.frombuffer(file_content, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        # Inférence YOLO (on cherche les classes 0: person, 67: cell phone, 73: book)
        results = fraud_model(img, conf=0.4, verbose=False)
        
        fraud_detected = False
        reasons = []

        for r in results:
            # Détection de plusieurs personnes
            person_count = (r.boxes.cls == 0).sum()
            if person_count > 1:
                fraud_detected = True
                reasons.append(f"{int(person_count)} personnes détectées")

            # Détection d'objets (téléphone, livre, laptop)
            for box in r.boxes:
                label = fraud_model.names[int(box.cls[0])]
                if label in ["cell phone", "book", "laptop"]:
                    fraud_detected = True
                    reasons.append(f"Objet interdit : {label}")

        if fraud_detected:
            # Extraire le userId depuis le file_path (format: "userId/timestamp/...")
            user_id = file_path.split('/')[0]
            
            # Mise à jour du statut dans Supabase via REST API
            async with httpx.AsyncClient() as client:
                await client.patch(
                    f"{SUPABASE_URL}/rest/v1/profiles?id=eq.{user_id}",
                    json={
                        "is_cheating": True, 
                        "fraud_reason": ", ".join(reasons)
                    },
                    headers={"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
                )
    # --- FIN ANALYSE IA ---

    # 2. Ton code de stockage habituel vers Supabase Storage
    url = f"{SUPABASE_URL}/storage/v1/object/monitoring/{file_path}"
    headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": file.content_type
    }


    async with httpx.AsyncClient() as client:
        response = await client.post(url, content=file_content, headers=headers)
        #return {"status": "processed", "storage_resp": response.status_code}
        
        if response.status_code != 200:
            raise HTTPException(status_code=response.status_code, detail="Erreur Storage Supabase")

    return {"status": "success", "path": file_path}

@app.get("/user/status/{user_id}")
async def get_user_status(user_id: str):
    """
    Endpoint interrogé par Android (checkFraudStatus) pour savoir 
    si l'IA a détecté une triche.
    """
    url = f"{SUPABASE_URL}/rest/v1/profiles?id=eq.{user_id}&select=is_cheating,fraud_reason"
    headers = {
        "apikey": SUPABASE_KEY, 
        "Authorization": f"Bearer {SUPABASE_KEY}"
    }
    
    async with httpx.AsyncClient() as client:
        try:
            response = await client.get(url, headers=headers)
            data = response.json()
            
            # Si on trouve l'utilisateur, on renvoie son état de triche
            if data and len(data) > 0:
                return data[0] # Renvoie {"is_cheating": true/false, "fraud_reason": "..."}
            
            # Si l'utilisateur n'existe pas encore dans 'profiles'
            return {"is_cheating": False, "fraud_reason": None}
            
        except Exception as e:
            # En cas d'erreur serveur, on ne bloque pas l'utilisateur par défaut
            return {"is_cheating": False, "error": str(e)}

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
            "gps_points": gps_data,
            "sessions": final_sessions
        }
    

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
    
