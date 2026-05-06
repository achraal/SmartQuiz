# 1. Montage du Drive pour ne pas perdre tes modèles
from google.colab import drive
drive.mount('/content/drive')

# 2. Installation de LivePortrait (Le plus réaliste en 2026 pour le CPU/GPU mobile)
!git clone https://github.com/KwaiVGI/LivePortrait.git
%cd LivePortrait

# 3. Installation des dépendances (Accepte le redémarrage si demandé)
!pip install -r requirements.txt
!pip install fastapi uvicorn pyngrok nest-asyncio python-multipart

import os
# Créer le dossier du projet s'il n'existe pas
project_path = "/content/drive/MyDrive/QuizApp_Avatar"
if not os.path.exists(project_path):
    os.makedirs(project_path)
    print("Dossier projet créé dans le Drive !")
	
	
	
	
%cd /content/LivePortrait

# 1. Installation de l'outil de téléchargement Hugging Face
!pip install huggingface_hub

# 2. Suppression des fichiers vides/erreurs précédents
!rm -rf pretrained_weights

# 3. Téléchargement propre du dépôt de modèles
from huggingface_hub import snapshot_download

# Télécharge uniquement les modèles nécessaires dans le bon dossier
snapshot_download(
    repo_id="KlingTeam/LivePortrait",
    local_dir="pretrained_weights",
    local_dir_use_symlinks=False
)

print("✅ Modèles réellement téléchargés !")



# Installer python-dotenv pour lire le fichier
!pip install python-dotenv

from dotenv import load_dotenv
import os

# Charger le fichier depuis le Drive
load_dotenv('/content/drive/MyDrive/QuizApp_Avatar/.env')

# Récupérer le token
NGROK_TOKEN = os.getenv('NGROK_TOKEN')

if NGROK_TOKEN:
    print("✅ Token chargé avec succès !")
else:
    print("❌ Erreur : Token non trouvé dans le fichier .env")
	
	
!pip install onnxruntime-gpu --extra-index-url https://aiinfra.pkgs.visualstudio.com/PublicPackages/_packaging/onnxruntime-cuda-12/pypi/simple/


!ls /content/LivePortrait | grep .py
!python /content/LivePortrait/inference.py --help





!cp -r /content/LivePortrait /content/drive/MyDrive/QuizApp_Avatar






import nest_asyncio
from fastapi import FastAPI, UploadFile, File
from fastapi.responses import FileResponse
import uvicorn
from pyngrok import ngrok
import shutil
import os
import subprocess
from datetime import datetime

# 1. INITIALISATION ET CONFIGURATION DES CHEMINS
app = FastAPI()

# Chemin vers ton Drive (Assure-toi d'avoir monté le drive avant)
LIVEPORTRAIT_PATH = "/content/drive/MyDrive/QuizApp_Avatar/LivePortrait"
OUTPUT_DIR = os.path.abspath("animations")

# Création du dossier de sortie s'il n'existe pas
os.makedirs(OUTPUT_DIR, exist_ok=True)

@app.post("/animate")
async def animate_avatar(image: UploadFile = File(...), audio: UploadFile = File(...)):
    # 1. Chemins absolus
    input_img_path = os.path.abspath("temp_input_image.jpg")
    input_audio_raw = os.path.abspath("temp_input_audio_raw")
    temp_driving_video = os.path.abspath("temp_driving_video.mp4") 
    abs_output_dir = os.path.abspath(OUTPUT_DIR)
    
    # Sauvegarde des fichiers entrants
    with open(input_img_path, "wb") as buffer:
        shutil.copyfileobj(image.file, buffer)
    with open(input_audio_raw, "wb") as buffer:
        shutil.copyfileobj(audio.file, buffer)

    # 2. Création de la vidéo de conduite (Driving Video)
    # Cette étape est cruciale pour que LivePortrait synchronise les lèvres
    print("⏳ Préparation de l'audio et de la vidéo de conduite...")
    simple_ffmpeg = f"ffmpeg -loop 1 -i {input_img_path} -i {input_audio_raw} -c:v libx264 -tune stillimage -c:a aac -b:a 192k -pix_fmt yuv420p -shortest {temp_driving_video} -y"
    
    subprocess.run(simple_ffmpeg, shell=True, capture_output=True)

    # 3. Nettoyage dossier de sortie
    if os.path.exists(abs_output_dir):
        shutil.rmtree(abs_output_dir)
    os.makedirs(abs_output_dir)

    # 4. Exécution de LivePortrait
    print(f"🎬 Animation Lip-Sync en cours pour Android...")
    
    # Configuration de l'environnement CUDA pour Colab
    cuda_env = "export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/cuda/lib64 && "

    cmd = (
        f"{cuda_env} cd {LIVEPORTRAIT_PATH} && "
        f"python inference.py --source {input_img_path} "
        f"--driving {temp_driving_video} " 
        f"--output_dir {abs_output_dir} "
        f"--flag_do_crop "
        f"--flag_stitching "
        f"--flag_relative_motion "
        f"--flag_lip_retargeting "
        f"--driving_option expression-friendly"
    )
    
    # On utilise subprocess.run pour capturer la sortie et déboguer
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    
    if result.returncode != 0:
        print("❌ ERREUR LIVEPORTRAIT :", result.stderr)

    # 5. Récupération du résultat .mp4
    generated_files = []
    for root, _, files in os.walk(abs_output_dir):
        for file in files:
            if file.endswith(".mp4"):
                generated_files.append(os.path.join(root, file))

    if generated_files:
        print(f"✅ Vidéo générée : {generated_files[0]}")
        return FileResponse(generated_files[0], media_type="video/mp4")
    
    return {"error": "Failed", "details": result.stderr}

# --- GESTION DU TUNNEL NGROK ---
ngrok.kill()
# Vérifie que NGROK_TOKEN est défini dans une cellule précédente
if 'NGROK_TOKEN' in locals() and NGROK_TOKEN:
    ngrok.set_auth_token(NGROK_TOKEN)
    public_url = ngrok.connect(8000)
    print(f"🚀 SERVEUR GPU ACTIF")
    print(f"🔗 URL Ngrok : {public_url.public_url}")
    print(f"⚠️ Copie cette URL dans la variable COLAB_URL de ton worker.py")
else:
    print("❌ ERREUR : NGROK_TOKEN manquant !")

# Lancement du serveur
nest_asyncio.apply()
config = uvicorn.Config(app, host="0.0.0.0", port=8000)
server = uvicorn.Server(config)
await server.serve()