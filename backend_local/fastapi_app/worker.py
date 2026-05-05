import os
import cv2
import numpy as np
import httpx
import asyncio
import redis
from rq import Worker, Queue, SimpleWorker
from ultralytics import YOLO
from dotenv import load_dotenv
from datetime import datetime
import mediapipe as mp
from mediapipe.python.solutions import face_mesh as mp_face_mesh

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
        nparr = np.frombuffer(file_content, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None: return

        # 1. ANALYSE YOLO (Objets seulement)
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
                    reasons.append(FRAUD_LABELS_FR[label])

        if person_count > 1:
            reasons.append(f"Multiples personnes ({person_count})")

        # 2. ANALYSE MEDIAPIPE (Posture seulement)
        if camera_type == "front":
            is_suspicious, pose_reason = check_head_pose(img)
            if is_suspicious:
                # On ajoute la raison sans condition liée à YOLO
                reasons.append(pose_reason)

        # 3. MISE À JOUR SUPABASE
        if reasons:
            unique_reasons = list(set(reasons))
            reason_str = " | ".join(unique_reasons)
            print(f"!!! [FRAUDE] {reason_str}")
            async def update_all():
                await asyncio.gather(
                    update_fraud_status_in_supabase(user_id, True, reason_str),
                    save_fraud_log(user_id, "photo", reason_str, {"camera": camera_type})
                )
            asyncio.run(update_all())
        else:
            print("   > [RÉSULTAT] OK : RAS")
            
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
        
def check_head_pose(img):
    h, w = img.shape[:2]
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    brightness = np.mean(gray)
    laplacian_var = cv2.Laplacian(gray, cv2.CV_64F).var()
    
    print(f"      [DIAGNOSTIC] Taille: {w}x{h} | Lum: {brightness:.1f} | Net: {laplacian_var:.1f}")

    def get_results(frame):
        h_f, w_f = frame.shape[:2]
        ratio = 800 / max(h_f, w_f)
        resized = cv2.resize(frame, (int(w_f * ratio), int(h_f * ratio)))
        frame_rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB)
        frame_rgb = cv2.GaussianBlur(frame_rgb, (5, 5), 0)
        return face_mesh.process(frame_rgb)

    # 1. Recherche du visage
    results = get_results(img)
    if not results or not results.multi_face_landmarks:
        margin_w, margin_h = int(w * 0.1), int(h * 0.1)
        cropped = img[margin_h:h-margin_h, margin_w:w-margin_w]
        results = get_results(cropped)

    # 2. Ignorer si pas de visage (pour tes tests)
    if not results or not results.multi_face_landmarks:
        print("      [INFO] Visage invisible : Ignoré.")
        return False, None

    # 3. Analyse des points
    face_landmarks = results.multi_face_landmarks[0]
    nose = face_landmarks.landmark[1]
    l_eye = face_landmarks.landmark[33]
    r_eye = face_landmarks.landmark[263]
    chin = face_landmarks.landmark[152]
    forehead = face_landmarks.landmark[10]

    face_height = abs(chin.y - forehead.y)
    
    # Yaw
    dist_l = abs(nose.x - l_eye.x)
    dist_r = abs(nose.x - r_eye.x)
    ratio_yaw = dist_l / dist_r if dist_r != 0 else 10

    # Pitch (Axe Y inversé : plus c'est grand, plus c'est bas)
    eye_y_avg = (l_eye.y + r_eye.y) / 2
    pitch_diff = nose.y - eye_y_avg
    relative_pitch = pitch_diff / face_height if face_height != 0 else 0

    print(f"      [DEBUG] Yaw: {ratio_yaw:.2f} | Pitch Rel: {relative_pitch:.3f}")

    # --- ÉTAPE 4 : LOGIQUE DE DÉTECTION ASSOUPLIE ---

    # 1. Rotation Latérale (Très tolérant)
    if ratio_yaw < 0.15 or ratio_yaw > 7.0:
        return True, "Visage tourné (Côté)"

    # 2. Tête Baissée (0.25 laisse de la marge pour lire sans frauder)
    if relative_pitch > 0.25: 
        return True, "Tête baissée (Regard vers le bas)"

    # 3. Tête Levée (0.01 signifie que le nez est presque sur la ligne des yeux)
    if relative_pitch < 0.01: 
        return True, "Tête levée (Regard vers le haut)"

    return False, None
    


if __name__ == '__main__':
    redis_conn = redis.from_url(REDIS_URL)
    queue = Queue('default', connection=redis_conn)
    worker = SimpleWorker([queue], connection=redis_conn)
    print("--- Worker Windows avec Débogage & Traduction prêt ---")
    worker.work()