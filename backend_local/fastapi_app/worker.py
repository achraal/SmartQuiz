import os, cv2, asyncio, redis, httpx
import numpy as np
from rq import Worker, Queue, SimpleWorker
from ultralytics import YOLO
from dotenv import load_dotenv
from datetime import datetime
import mediapipe as mp
from mediapipe.python.solutions import face_mesh as mp_face_mesh
from gtts import gTTS

# --- INITIALISATION MEDIAPIPE (Une seule fois au démarrage) ---

face_mesh = mp_face_mesh.FaceMesh(
    static_image_mode=False, 
    max_num_faces=1, 
    refine_landmarks=True,
    min_detection_confidence=0.3,
    min_tracking_confidence=0.3
)

load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY")
REDIS_URL = os.getenv("REDIS_URL")
COLAB_URL = os.getenv("COLAB_URL")

# Dictionnaire de traduction pour les messages de fraude
FRAUD_LABELS_FR = {
    "cell phone": "Téléphone détecté",
    "laptop": "Ordinateur interdit",
    "book": "Livre/Document détecté"
}

print("--- Initialisation du Worker ---")
fraud_model = YOLO('yolov8n.pt')
fraud_model(np.zeros((640, 640, 3))) # Warmup


async def save_fraud_log(user_id: str, type_event: str, reason: str, metadata: dict = None):
    """Enregistre silencieusement le log en base de données"""
    try:
        async with httpx.AsyncClient() as client:
            url = f"{SUPABASE_URL}/rest/v1/fraud_logs"
            headers = {
                "apikey": SUPABASE_KEY,
                "Authorization": f"Bearer {SUPABASE_KEY}",
                "Content-Type": "application/json",
                "Prefer": "return=minimal"
            }
            payload = {
                "user_id": user_id,
                "type": type_event,
                "reason": reason,
                "metadata": metadata or {}
            }
            await client.post(url, json=payload, headers=headers)
    except Exception as e:
        print(f"[LOG ERROR] Impossible d'enregistrer le log en BDD : {e}")

async def update_fraud_status_in_supabase(user_id: str, status: bool, reason: str):
    async with httpx.AsyncClient() as client:
        url = f"{SUPABASE_URL}/rest/v1/profiles?id=eq.{user_id}"
        headers = {
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}",
            "Content-Type": "application/json"
        }
        payload = {
            "is_cheating": status,
            "fraud_reason": reason,
            "last_fraud_detected_at": datetime.now().isoformat()
        }
        await client.patch(url, json=payload, headers=headers)

def analyze_photo_task(file_content, user_id, camera_type="front"):
    camera_type = str(camera_type).lower()
    print(f"\n[PHOTO] --- Analyse IA ({camera_type}) pour : {user_id} ---")
    
    try:
        reasons = []
        confidences = {} # Nouveau : pour stocker les scores
        
        nparr = np.frombuffer(file_content, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None: return

        # 1. ANALYSE YOLO
        img_yolo = cv2.resize(img, (640, 640))
        yolo_results = fraud_model(img_yolo, conf=0.2, verbose=False)
        
        person_count = 0
        for r in yolo_results:
            for box in r.boxes:
                label = fraud_model.names[int(box.cls[0])]
                conf = float(box.conf[0])

                if label == "person" and conf > 0.5:
                    person_count += 1
                
                if label in FRAUD_LABELS_FR and conf > 0.35:
                    fraud_msg = FRAUD_LABELS_FR[label]
                    reasons.append(fraud_msg)
                    # On garde le score de confiance le plus élevé pour ce label
                    confidences[fraud_msg] = round(conf, 3)

        if person_count > 1:
            msg = f"Multiples personnes ({person_count})"
            reasons.append(msg)
            confidences[msg] = 1.0 # Score arbitraire car c'est un comptage

        # 2. ANALYSE MEDIAPIPE (Posture)
        if camera_type == "front":
            is_suspicious, pose_reason = check_head_pose(img, user_id)
            if is_suspicious:
                reasons.append(pose_reason)
                confidences[pose_reason] = 1.0 # MediaPipe est basé sur des seuils fixes

        # 3. MISE À JOUR SUPABASE AVEC METADATA
        if reasons:
            unique_reasons = list(set(reasons))
            reason_str = " | ".join(unique_reasons)
            print(f"!!! [FRAUDE] {reason_str}")
            
            # On prépare l'objet metadata avec les scores
            # Ex: {"camera": "front", "confidences": {"Téléphone détecté": 0.85}}
            metadata_log = {
                "camera": camera_type,
                "confidences": {r: confidences.get(r, "N/A") for r in unique_reasons}
            }

            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            try:
                loop.run_until_complete(asyncio.gather(
                    update_fraud_status_in_supabase(user_id, True, reason_str),
                    save_fraud_log(user_id, "photo", reason_str, metadata_log)
                ))
            finally:
                loop.close()
        else:
            print("    > [RÉSULTAT] OK : RAS")
            
    except Exception as e:
        print(f"[PHOTO] ❌ Erreur : {e}")

def analyze_audio_task(audio_bytes, user_id):
    print(f"\n[AUDIO] Analyse sonore pour : {user_id}")
    try:
        import subprocess
        command = ['ffmpeg', '-i', 'pipe:0', '-f', 'f32le', '-ac', '1', '-ar', '16000', 'pipe:1']
        process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = process.communicate(input=audio_bytes)

        if out:
            audio_data = np.frombuffer(out, dtype=np.float32)
            if len(audio_data) > 0:
                rms = np.sqrt(np.mean(audio_data**2))
                print(f"   > [DEBUG AUDIO] RMS mesuré : {rms:.6f}")
                
                if rms > 0.012:
                    # --- MODIFICATION ICI : MESSAGE SIMPLE ---
                    msg = f"Bruit suspect ({rms:.4f})"
                    print(f"!!! [ALERTE AUDIO] {msg}")
                    async def update_all_audio():
                        await asyncio.gather(
                            update_fraud_status_in_supabase(user_id, True, msg),
                            save_fraud_log(user_id, "audio", msg, {"rms": float(rms)})
                        )
                    asyncio.run(update_all_audio())
                else:
                    print("   > [RESULTAT] Audio calme.")
        else:
            print("   > [ERREUR] FFmpeg n'a rien généré.")
            
    except Exception as e:
        print(f"[AUDIO] ❌ Erreur critique: {e}")
        
def get_user_calibration_sync(user_id: str):
    """Version synchrone pour éviter les erreurs d'Event Loop"""
    import httpx
    url = f"{SUPABASE_URL}/rest/v1/profiles?id=eq.{user_id}&select=ref_pitch,ref_yaw,ref_brightness"
    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}
    with httpx.Client() as client: # Client standard, pas 'AsyncClient'
        resp = client.get(url, headers=headers)
        data = resp.json()
        return data[0] if data else None
        
        
def check_head_pose(img, user_id):
    h, w = img.shape[:2]
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    current_brightness = np.mean(gray)
    
    print(f"      [DIAGNOSTIC] Taille: {w}x{h} | Lum: {current_brightness:.1f}")

    def get_results(frame):
        # On tente plusieurs échelles si le visage n'est pas trouvé
        h_f, w_f = frame.shape[:2]
        # Essai 1: Taille standard
        ratio = 800 / max(h_f, w_f)
        resized = cv2.resize(frame, (int(w_f * ratio), int(h_f * ratio)))
        frame_rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB)
        res = face_mesh.process(frame_rgb)
        
        # Essai 2: Si échec, on tente en plus grand (plus précis)
        if not res or not res.multi_face_landmarks:
            ratio = 1024 / max(h_f, w_f)
            resized = cv2.resize(frame, (int(w_f * ratio), int(h_f * ratio)))
            frame_rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB)
            res = face_mesh.process(frame_rgb)
            
        return res

    results = get_results(img)

    # GESTION DU VISAGE NON TROUVÉ
    if not results or not results.multi_face_landmarks:
        # --- NOUVELLE SÉCURITÉ ---
        # On ne déclenche la fraude QUE si on est vraiment sûr
        # Pour l'instant, on va juste logger l'échec de détection sans punir
        # sauf si la lumière chute drastiquement.
        
        try:
            calibration = get_user_calibration_sync(user_id)
            if calibration and calibration.get('ref_brightness'):
                ref_br = calibration['ref_brightness']
                
                # Cas 1 : Noir total (Fraude quasi-certaine)
                if current_brightness < (ref_br * 0.15) or current_brightness < 8:
                    return True, "Caméra obstruée (Noir total)"
                
                # Cas 2 : Visage absent mais lumière OK 
                # ATTENTION : On désactive l'alerte automatique ici pour éviter tes faux positifs
                # On ne renvoie True que si la luminosité est VRAIMENT différente ou si tu veux être sévère.
                print("      [DEBUG] MediaPipe a raté le visage, mais on ne punit pas encore.")
                return False, None 
                
        except Exception as e:
            print(f"      [ERREUR CALIB] : {e}")
            
        return False, None
            

    # 3. Analyse des points (si visage trouvé)
    face_landmarks = results.multi_face_landmarks[0]
    nose = face_landmarks.landmark[1]
    l_eye = face_landmarks.landmark[33]
    r_eye = face_landmarks.landmark[263]
    chin = face_landmarks.landmark[152]
    forehead = face_landmarks.landmark[10]

    face_height = abs(chin.y - forehead.y)
    ratio_yaw = abs(nose.x - l_eye.x) / abs(nose.x - r_eye.x) if abs(nose.x - r_eye.x) != 0 else 10
    relative_pitch = (nose.y - (l_eye.y + r_eye.y)/2) / face_height if face_height != 0 else 0

    print(f"      [DEBUG] Yaw: {ratio_yaw:.2f} | Pitch Rel: {relative_pitch:.3f}")

    # Seuils assouplis comme convenu
    if ratio_yaw < 0.15 or ratio_yaw > 7.0:
        return True, "Visage tourné (Côté)"
    if relative_pitch > 0.25: 
        return True, "Tête baissée (Regard vers le bas)"
    if relative_pitch < 0.01: 
        return True, "Tête levée (Regard vers le haut)"

    return False, None
    
    
def generate_avatar_video_task(user_id, audio_path, is_welcome=False):
    """
    Tâche Redis : Prend l'audio enregistré par l'admin, l'envoie au GPU Colab,
    et stocke la vidéo finale sur Supabase.
    """
    print(f"\n[AVATAR] 🎬 Début génération. Welcome mode: {is_welcome}")
    
    # Définition du chemin de stockage sur Supabase
    if is_welcome:
        # Stockage public pour tous les étudiants
        storage_path = "admin_assets/welcome_video.mp4"
        video_filename = "welcome_video.mp4"
    else:
        # Message privé pour un étudiant spécifique
        storage_path = f"avatars/{user_id}_{int(datetime.now().timestamp())}.mp4"
        video_filename = f"{user_id}.mp4"

    # Image de référence de l'admin (Achraf)
    # Assure-toi que ce fichier existe dans ton dossier assets/
    image_path = "assets/me.jpg" 

    try:
        if not os.path.exists(image_path):
            print(f"  ❌ Erreur : Photo de l'admin introuvable ({image_path})")
            return

        # 1. Envoi au Colab (SadTalker / LivePortrait)
        print(f"  > Envoi au GPU Cloud (Colab)... Audio: {audio_path}")
        with open(image_path, "rb") as img_file, open(audio_path, "rb") as aud_file:
            files = {
                "image": ("image.jpg", img_file, "image/jpeg"),
                "audio": ("audio.3gp", aud_file, "audio/3gp")
            }
            
            with httpx.Client(timeout=800.0) as client:
                response = client.post(f"{COLAB_URL}/animate", files=files)
                
                if response.status_code == 200:
                    # 2. Upload du résultat vers Supabase Storage
                    print(f"  > Vidéo reçue ! Upload vers Supabase : {storage_path}")
                    
                    # Note : on utilise le bucket 'admin-assets' selon ton setup
                    upload_url = f"{SUPABASE_URL}/storage/v1/object/admin-assets/{storage_path}"
                    headers = {
                        "apikey": SUPABASE_KEY,
                        "Authorization": f"Bearer {SUPABASE_KEY}",
                        "Content-Type": "video/mp4",
                        "x-upsert": "true"
                    }
                    
                    upload_resp = client.post(upload_url, content=response.content, headers=headers)
                    
                    if upload_resp.status_code == 200:
                        # 3. Si message privé, on met à jour le profil de l'étudiant
                        if not is_welcome:
                            print("  > Mise à jour du lien avatar dans le profil étudiant...")
                            asyncio.run(update_avatar_link_in_db(user_id, storage_path))
                        
                        print(f"✅ [AVATAR FINI] Disponible à : {storage_path}")
                else:
                    print(f"  ❌ Erreur Colab ({response.status_code}): {response.text}")

    except Exception as e:
        print(f"  ❌ [WORKER ERROR] : {e}")
    finally:
        # Nettoyage de l'audio temporaire envoyé par FastAPI
        if os.path.exists(audio_path):
            os.remove(audio_path)
            print(f"  > Nettoyage audio temporaire effectué.")

async def update_avatar_link_in_db(user_id, storage_path):
    """Met à jour le champ avatar_url dans Supabase pour l'étudiant"""
    async with httpx.AsyncClient() as client:
        # On pointe vers l'étudiant spécifique
        url = f"{SUPABASE_URL}/rest/v1/profiles?id=eq.{user_id}"
        headers = {
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}",
            "Content-Type": "application/json"
        }
        # On enregistre le chemin relatif pour que l'app Android puisse construire l'URL finale
        payload = {
            "avatar_url": storage_path, 
            "last_avatar_at": datetime.now().isoformat()
        }
        resp = await client.patch(url, json=payload, headers=headers)
        if resp.status_code == 204 or resp.status_code == 200:
            print(f"  > [DB] Lien mis à jour pour l'étudiant {user_id}")
        else:
            print(f"  > [DB ERROR] Échec mise à jour : {resp.text}")


if __name__ == '__main__':
    redis_conn = redis.from_url(REDIS_URL)
    queue = Queue('default', connection=redis_conn)
    worker = SimpleWorker([queue], connection=redis_conn)
    print("--- Worker Windows avec Débogage & Traduction prêt ---")
    worker.work()