import os
from google.colab import drive

# 1. Montage du Drive
drive.mount('/content/drive')

# 2. Définition des chemins
SADTALKER_PATH = "/content/drive/MyDrive/QuizApp_Avatar/SadTalker"

if not os.path.exists(SADTALKER_PATH):
    !git clone https://github.com/OpenTalker/SadTalker.git {SADTALKER_PATH}

%cd {SADTALKER_PATH}

# 3. Téléchargement des scripts de poids (Scripts officiels de SadTalker)
# On télécharge les modèles compressés pour gagner du temps
print("⏳ Téléchargement des modèles en cours (cela peut prendre 3-5 min)...")

!mkdir -p checkpoints
!mkdir -p gfpgan/weights

# Téléchargement des fichiers principaux (via les liens directs recommandés)
!wget https://github.com/OpenTalker/SadTalker/releases/download/v0.0.2-rc/mapping_00109-model.pth.tar -O checkpoints/mapping_00109-model.pth.tar
!wget https://github.com/OpenTalker/SadTalker/releases/download/v0.0.2-rc/mapping_00229-model.pth.tar -O checkpoints/mapping_00229-model.pth.tar
!wget https://github.com/OpenTalker/SadTalker/releases/download/v0.0.2-rc/SadTalker_V0.0.2_256.safetensors -O checkpoints/SadTalker_V0.0.2_256.safetensors
!wget https://github.com/OpenTalker/SadTalker/releases/download/v0.0.2-rc/SadTalker_V0.0.2_512.safetensors -O checkpoints/SadTalker_V0.0.2_512.safetensors

# Modèle pour l'amélioration du visage (GFPGAN) pour l'effet HeyGen HD
!wget https://github.com/TencentARC/GFPGAN/releases/download/v1.3.0/GFPGANv1.3.pth -O gfpgan/weights/GFPGANv1.3.pth

print("✅ Tous les modèles sont installés sur ton Drive dans /SadTalker/checkpoints")





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
	
	
	
	
!pip install kornia dlib yacs facexlib gfpgan scikit-image librosa



# 1. Remplace np.float par float dans le fichier qui cause l'erreur
!sed -i 's/np.float/float/g' /content/drive/MyDrive/QuizApp_Avatar/SadTalker/src/face3d/util/my_awing_arch.py

# 2. Sécurité : Correction de l'import Torchvision (au cas où ce n'est pas fait)
!sed -i 's/from torchvision.transforms.functional_tensor import rgb_to_grayscale/from torchvision.transforms.functional import rgb_to_grayscale/g' /usr/local/lib/python3.12/dist-packages/basicsr/data/degradations.py




!sed -i "s/trans_params = np.array(\[w0, h0, s, t\[0\], t\[1\]\])/trans_params = np.array([w0, h0, s, t[0], t[1]], dtype=object).astype(np.float32)/g" /content/drive/MyDrive/QuizApp_Avatar/SadTalker/src/face3d/util/preprocess.py





import nest_asyncio
from fastapi import FastAPI, UploadFile, File
from fastapi.responses import FileResponse
import uvicorn
from pyngrok import ngrok
import shutil
import os
import subprocess
import sys  # <--- INDISPENSABLE POUR LE PATCH
import types

app = FastAPI()

# CONFIGURATION DES CHEMINS
SADTALKER_PATH = "/content/drive/MyDrive/QuizApp_Avatar/SadTalker"
OUTPUT_DIR = os.path.abspath("animations")
os.makedirs(OUTPUT_DIR, exist_ok=True)

@app.post("/animate")
async def animate_avatar(image: UploadFile = File(...), audio: UploadFile = File(...)):
    # 1. Chemins des fichiers temporaires
    input_img_path = os.path.abspath("temp_image.jpg")
    input_audio_path = os.path.abspath("temp_audio.wav") # SadTalker adore le .wav
    abs_output_dir = os.path.abspath(OUTPUT_DIR)
    
    # Sauvegarde des fichiers reçus d'Android
    with open(input_img_path, "wb") as buffer:
        shutil.copyfileobj(image.file, buffer)
    with open(input_audio_path, "wb") as buffer:
        shutil.copyfileobj(audio.file, buffer)

    # 2. Nettoyage du dossier de sortie
    if os.path.exists(abs_output_dir):
        shutil.rmtree(abs_output_dir)
    os.makedirs(abs_output_dir)

    # 3. Exécution de SadTalker
    print(f"🎬 Génération de l'avatar 3D en cours...")
    
    cuda_env = "export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/cuda/lib64"

    # Commande simple maintenant que le système est patché
    cmd = (
        f"{cuda_env} && cd {SADTALKER_PATH} && "
        f"python inference.py "
        f"--source_image {input_img_path} "
        f"--driven_audio {input_audio_path} " 
        f"--result_dir {abs_output_dir} "
        f"--still "
        f"--preprocess full "
        f"--enhancer gfpgan"
    )
    
    process = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    
    if process.returncode != 0:
        print("❌ ERREUR :", process.stderr)
        return {"error": "Inference failed", "details": process.stderr}

    # 4. Récupération de la vidéo générée
    video_path = None
    for root, _, files in os.walk(abs_output_dir):
        for file in files:
            if file.endswith(".mp4"):
                video_path = os.path.join(root, file)
                break
    
    if video_path:
        print(f"✅ Vidéo prête : {video_path}")
        return FileResponse(video_path, media_type="video/mp4")
    
    return {"error": "No video generated"}

# --- GESTION DU TUNNEL NGROK ---
# Assure-toi que NGROK_TOKEN est bien défini dans ton environnement Colab
NGROK_TOKEN = os.getenv('NGROK_TOKEN') 

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