# Cahier des Charges - Projet QuizApp AitLahcen

## Application Quiz avec Système de Surveillance Anti-Fraude par IA

---

## Table des matières

1. [Description du Projet](#1-description-du-projet)
2. [Objectifs du Projet](#2-objectifs-du-projet)
3. [Acteurs du Système](#3-acteurs-du-système)
4. [Besoins Fonctionnels](#4-besoins-fonctionnels)
5. [Besoins Non Fonctionnels](#5-besoins-non-fonctionnels)
6. [Architecture Technique](#6-architecture-technique)
7. [Modèle de Données](#7-modèle-de-données)
8. [API REST - Spécifications](#8-api-rest---spécifications)
9. [Sécurité](#9-sécurité)
10. [Planning Prévisionnel](#10-planning-prévisionnel)
11. [Livrables](#11-livrables)
12. [Glossaire](#12-glossaire)

---

## 1. Description du Projet

**QuizApp AitLahcen** est une application mobile de quiz éducatif avec un système de surveillance automatique anti-fraude. Le système permet aux étudiants de passer des quiz de manière sécurisée tout en détectant automatiquement les tentatives de triche grâce à l'intelligence artificielle.

### Contexte et Justification

Dans le cadre de l'évaluation à distance, la triche représente un défi majeur. Cette application répond à ce besoin en proposant :

| Problème | Solution apportée |
|----------|------------------|
| Triche pendant les examens à distance | Surveillance automatique par IA (YOLO) |
| Absence de traçabilité | Enregistrement GPS, photos et audio |
| Gestion manuelle des questionnaires | Interface d'administration pour créer les quiz |
| Manque de visibilité pour les enseignants | Tableau de bord de monitoring complet |

### Périmètre Fonctionnel

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SYSTÈME QUIZAPP AITLAHCEN                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐ │
│   │   Application   │     │    Backend      │     │    Base de      │ │
│   │   Android       │────▶│    FastAPI      │────▶│   Données       │ │
│   │   (Étudiant)    │     │    (Python)     │     │   Supabase      │ │
│   └─────────────────┘     └─────────────────┘     └─────────────────┘ │
│          │                        │                                   │
│          │                        ▼                                   │
│          │              ┌─────────────────┐                          │
│          │              │   Modèle IA     │                          │
│          │              │   YOLO v8       │                          │
│          │              │   (Détection)   │                          │
│          │              └─────────────────┘                          │
│          │                                                            │
│          ▼                                                            │
│   ┌─────────────────┐     ┌─────────────────┐                        │
│   │   Caméra        │     │   Monitoring    │                        │
│   │   Front/Back    │     │   (Admin Web)   │                        │
│   │   + Microphone  │     │   (Consultation)│                        │
│   └─────────────────┘     └─────────────────┘                        │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Objectifs du Projet

### 2.1 Objectifs Pédagogiques

| Objectif | Description |
|----------|-------------|
| Évaluation sécurisée | Permettre aux étudiants de passer des quiz dans un environnement contrôlé |
| Progression par niveaux | Système de quiz progressif (niveau 1, 2, 3...) |

### 2.2 Objectifs Techniques

| Objectif | Indicateur de succès |
|----------|----------------------|
| Détection de fraude | Détection automatique de personnes multiples, téléphones, livres |
| Traçabilité complète | Enregistrement GPS, photos périodiques, audio |
| Performance | Temps de réponse API < 500ms |
| Disponibilité | Système fonctionnel en local (Docker) |

### 2.3 Objectifs de Sécurité

| Objectif | Implémentation |
|----------|----------------|
| Authentification sécurisée | JWT via Supabase Auth |
| Protection des données | HTTPS, tokens sécurisés |
| Anti-fraude | IA YOLO + surveillance temps réel |

---

## 3. Acteurs du Système

### 3.1 L'Étudiant (Utilisateur Mobile)

**Profil** : Apprenant passant un quiz

**Responsabilités** :
- S'inscrire et se connecter à l'application
- Passer les quiz niveau par niveau
- Accepter les permissions (caméra, microphone, localisation)

**Parcours type** :
```
Inscription → Connexion → Quiz (avec surveillance) → Score
```

**Besoins spécifiques** :
- Interface intuitive
- Timer visible pendant le quiz
- Retour immédiat sur le score

### 3.2 L'Administrateur (Enseignant)

**Profil** : Gestionnaire de la plateforme

**Responsabilités** :
- Créer et modifier les questions de quiz
- Consulter les scores des étudiants
- Surveiller les sessions (photos, GPS, audio)
- Détecter les fraudes signalées par l'IA

**Besoins spécifiques** :
- Dashboard de visualisation
- Ajout de questions facilité
- Accès aux preuves de fraude

---

## 4. Besoins Fonctionnels

### 4.1 Application Mobile Android (Côté Étudiant)

#### 4.1.1 Module Authentification

| Fonctionnalité | Description | Priorité |
|----------------|-------------|----------|
| Inscription | Création de compte (email, mot de passe) | Haute |
| Connexion | Authentification via Supabase | Haute |
| Déconnexion | Suppression du token local | Moyenne |

**Écrans concernés** :
- `Login.java` - Écran de connexion
- `Register.java` - Écran d'inscription
- `WelcomeActivity.java` - Écran d'accueil

#### 4.1.2 Module Quiz

| Fonctionnalité | Description | Priorité |
|----------------|-------------|----------|
| Chargement des questions | Récupération depuis le backend | Haute |
| Affichage des questions | Texte + image optionnelle | Haute |
| Timer | Compte à rebours 30 secondes par question | Haute |
| Calcul du score | Score final à la fin du quiz | Haute |
| Progression par niveau | Passage au niveau suivant | Haute |

**Écran concerné** : `QuizActivity.java`

**Structure d'une question** :
```java
public class QuizQuestion {
    String question_text;
    String option_a;
    String option_b;
    String option_c;
    String option_d;
    String correct_option;    // "A", "B", "C" ou "D"
    int level_number;
    String image_url;          // Optionnel
}
```

#### 4.1.3 Module Surveillance (Proctoring)

| Fonctionnalité | Description | Priorité |
|----------------|-------------|----------|
| Capture photo frontale | Photo de la face avant chaque seconde | Haute |
| Capture photo arrière | Photo de la face arrière chaque seconde | Haute |
| Enregistrement audio | Audio continu pendant tout le quiz | Haute |
| Géolocalisation | Position GPS envoyée périodiquement | Moyenne |
| Détection de fraude | Vérification temps réel du statut | Haute |

**Mécanisme de surveillance** :
```
┌─────────────────────────────────────────────────────────────────────┐
│                    CYCLE DE SURVEILLANCE                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   Toutes les 1 seconde :                                            │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐           │
│   │ Photo       │───▶│ Upload      │───▶│ Stockage    │           │
│   │ Front/Back  │    │ FastAPI     │    │ Supabase    │           │
│   └─────────────┘    └─────────────┘    └─────────────┘           │
│          │                                                          │
│          ▼                                                          │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐           │
│   │ Vérification│───▶│ Analyse IA  │───▶│ Si fraude : │           │
│   │ statut      │    │ (YOLO)      │    │ Bloquer     │           │
│   └─────────────┘    └─────────────┘    └─────────────┘           │
│                                                                     │
│   Audio : Enregistrement continu, upload à la fin du quiz          │
│   GPS : Envoi périodique de la position                            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Flux de détection de fraude** :
1. Photo capturée par l'appareil (front/back)
2. Upload vers FastAPI `/monitoring/upload`
3. Si photo frontale → Analyse YOLO en temps réel
4. Si fraude détectée → Mise à jour `is_cheating = true`
5. L'app vérifie périodiquement `/user/status/{id}`
6. Si fraude → Affichage dialogue et fin du quiz

**Objets détectés par YOLO** :
- Personnes multiples (`person`, id: 0)
- Téléphone (`cell phone`, id: 67)
- Livre (`book`, id: 73)
- Ordinateur portable (`laptop`, id: 63)

#### 4.1.4 Module Score

| Fonctionnalité | Description | Priorité |
|----------------|-------------|----------|
| Affichage du score | Score final sur X questions | Haute |
| Synchronisation | Envoi du score au serveur | Haute |

**Écran concerné** : `Score.java`

### 4.2 Application Mobile Android (Côté Admin)

#### 4.2.1 Module Gestion des Questions

| Fonctionnalité | Description | Priorité |
|----------------|-------------|----------|
| Création de question | Formulaire (texte, options, image) | Haute |
| Sélection du niveau | Numéro de niveau auto-incrémenté | Haute |
| Validation | Vérification des champs obligatoires | Moyenne |

**Écran concerné** : `AdminActivity.java`

**Champs du formulaire** :
- Texte de la question
- Option A
- Option B
- Option C
- Option D
- Réponse correcte (Spinner A/B/C/D)
- URL image (optionnel)

#### 4.2.2 Module Liste des Étudiants

| Fonctionnalité | Description | Priorité |
|----------------|-------------|----------|
| Liste des étudiants | Tous les profils inscrits | Haute |
| Score associé | Meilleur score affiché | Haute |
| Accès monitoring | Bouton pour voir les détails | Haute |

#### 4.2.3 Module Monitoring

| Fonctionnalité | Description | Priorité |
|----------------|-------------|----------|
| Carte GPS | Affichage des positions sur Google Maps | Haute |
| Galerie photos | Photos front/back par session | Haute |
| Lecteur audio | Lecture des enregistrements avec contrôle vitesse | Haute |

**Écran concerné** : `MonitoringActivity.java`

**Fonctionnalités audio** :
- Play/Pause
- SeekBar (barre de progression)
- Contrôle de vitesse (1x, 1.5x, 2x, 0.5x)

---

## 5. Besoins Non Fonctionnels

### 5.1 Performance

| Métrique | Cible | Contexte |
|----------|-------|----------|
| Temps de réponse API | < 500 ms | Requête standard |
| Chargement des questions | < 2 s | Première question |
| Upload photo | < 3 s | Par photo capturée |
| Analyse YOLO | < 2 s | Par photo frontale |

### 5.2 Disponibilité

| Exigence | Cible |
|----------|-------|
| Disponibilité du backend | 99% (local Docker) |
| Mode hors-ligne | Non requis (quiz en ligne obligatoire) |

### 5.3 Sécurité

| Exigence | Implémentation |
|----------|----------------|
| Authentification | JWT via Supabase Auth |
| Communication | HTTPS (Supabase local) |
| Permissions Android | CAMERA, RECORD_AUDIO, ACCESS_FINE_LOCATION |
| Protection admin | Variable `ADMIN_EMAIL` dans BuildConfig |

### 5.4 Ergonomie (UX/UI)

**Application Mobile** :
| Critère | Spécification |
|---------|---------------|
| Timer visible | Affichage en temps réel (30s par question) |
| Indicateur de progression | Barre de progression |
| Boutons radiogroup | Sélection unique des réponses |
| Gestion des images | Chargement avec Glide + headers auth |

### 5.5 Compatibilité

| Plateforme | Version minimale |
|------------|------------------|
| Android | API 21+ (Android 5.0) |
| CameraX | androidx.camera:* |
| Google Maps | Play Services Maps |

---

## 6. Architecture Technique

### 6.1 Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           ARCHITECTURE                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                    APPLICATION ANDROID                          │   │
│   │  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐         │   │
│   │  │    Login      │ │   Register    │ │  Welcome      │         │   │
│   │  │   Activity    │ │   Activity    │ │  Activity     │         │   │
│   │  └───────────────┘ └───────────────┘ └───────────────┘         │   │
│   │  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐         │   │
│   │  │    Quiz       │ │    Admin      │ │  Monitoring   │         │   │
│   │  │   Activity    │ │   Activity    │ │   Activity    │         │   │
│   │  └───────────────┘ └───────────────┘ └───────────────┘         │   │
│   │  ┌───────────────┐ ┌───────────────┐                           │   │
│   │  │    Score      │ │SupabaseConfig │                           │   │
│   │  │   Activity    │ │   (BuildConfig)│                          │   │
│   │  └───────────────┘ └───────────────┘                           │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                              │                                          │
│                              │ HTTP (OkHttp)                            │
│                              ▼                                          │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                    BACKEND FASTAPI                              │   │
│   │  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐         │   │
│   │  │  Auth Routes  │ │  Quiz Routes  │ │ Admin Routes  │         │   │
│   │  │  /auth/*      │ │  /quiz/*      │ │ /admin/*      │         │   │
│   │  └───────────────┘ └───────────────┘ └───────────────┘         │   │
│   │  ┌───────────────┐ ┌───────────────┐                           │   │
│   │  │Monitoring     │ │ Fraud Model  │                           │   │
│   │  │Routes         │ │  YOLO v8      │                           │   │
│   │  └───────────────┘ └───────────────┘                           │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                              │                                          │
│                              │ HTTP REST                                │
│                              ▼                                          │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                    SUPABASE (DOCKER)                            │   │
│   │  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐         │   │
│   │  │  PostgreSQL   │ │   Auth        │ │   Storage     │         │   │
│   │  │   Database    │ │   Service     │ │   (monitoring)│         │   │
│   │  └───────────────┘ └───────────────┘ └───────────────┘         │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Stack Technologique Détaillée

#### 6.2.1 Application Android

| Technologie | Version | Usage |
|-------------|---------|-------|
| Java | 8+ | Langage principal |
| Android SDK | API 21+ | SDK Android |
| CameraX | androidx.camera:* | Capture photo front/back |
| OkHttp | 4.x | Client HTTP |
| Gson | 2.x | Parsing JSON |
| Glide | 4.x | Chargement d'images |
| Google Maps SDK | - | Affichage carte monitoring |
| Material Design | - | UI Components |

**Dépendances principales** (build.gradle) :
```gradle
implementation 'androidx.camera:camera-core:1.3.0'
implementation 'androidx.camera:camera-camera2:1.3.0'
implementation 'androidx.camera:camera-lifecycle:1.3.0'
implementation 'androidx.camera:camera-view:1.3.0'
implementation 'com.squareup.okhttp3:okhttp:4.11.0'
implementation 'com.google.code.gson:gson:2.10.1'
implementation 'com.github.bumptech.glide:glide:4.16.0'
implementation 'com.google.android.gms:play-services-maps:18.2.0'
```

#### 6.2.2 Backend FastAPI

| Technologie | Version | Usage |
|-------------|---------|-------|
| Python | 3.10+ | Langage |
| FastAPI | 0.100+ | Framework web |
| httpx | - | Client HTTP async |
| Ultralytics | 8.x | YOLO (détection objets) |
| OpenCV | 4.x | Traitement d'image |
| NumPy | - | Calculs matriciels |
| python-dotenv | - | Gestion variables d'environnement |

**requirements.txt** :
```
fastapi
uvicorn
httpx
python-dotenv
ultralytics
opencv-python
numpy
```

#### 6.2.3 Base de Données (Supabase)

| Composant | Usage |
|-----------|-------|
| PostgreSQL | Base de données relationnelle |
| Supabase Auth | Authentification JWT |
| Supabase Storage | Stockage photos/audio |
| Supabase REST API | Interface HTTP |

### 6.3 Flux de Données

#### 6.3.1 Flux d'Authentification

```
┌─────────────┐                           ┌─────────────┐
│   Android   │                           │  FastAPI    │
└──────┬──────┘                           └──────┬──────┘
       │                                         │
       │  POST /auth/register                     │
       │────────────────────────────────────────▶│
       │                                         │
       │                    POST /auth/v1/signup  │
       │                                  ────────│
       │                                         ▼
       │                              ┌─────────────┐
       │                              │  Supabase   │
       │                              │   Auth      │
       │                              └─────────────┘
       │                                         │
       │  ◀──────────────────────────────────────│
       │         { access_token, user }          │
       │                                         │
```

#### 6.3.2 Flux de Surveillance

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Android   │     │  FastAPI    │     │    YOLO     │     │  Supabase   │
│  QuizActivity│    │  /upload    │     │   Model     │     │  Storage    │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                    │                    │                    │
       │ capturePhoto()    │                    │                    │
       │────────────────────▶                    │                    │
       │                    │                    │                    │
       │                    │ Si photo front     │                    │
       │                    │────────────────────▶                    │
       │                    │                    │                    │
       │                    │                    │ Détection objets   │
       │                    │                    │ - person           │
       │                    │                    │ - cell phone       │
       │                    │                    │ - book             │
       │                    │                    │ - laptop           │
       │                    │                    │                    │
       │                    │◀────────────────────│                    │
       │                    │  [fraud_detected]   │                    │
       │                    │                    │                    │
       │                    │ Stockage fichier   │                    │
       │                    │─────────────────────────────────────────▶│
       │                    │                    │                    │
       │                    │                    │ UPDATE profiles    │
       │                    │                    │ SET is_cheating    │
       │                    │─────────────────────────────────────────▶│
       │                    │                    │                    │
       │ GET /user/status   │                    │                    │
       │────────────────────▶                    │                    │
       │                    │────────────────────────────────────────▶│
       │                    │◀────────────────────────────────────────│
       │                    │  {is_cheating: true}                   │
       │◀────────────────────│                    │                    │
       │  showFraudDialog()  │                    │                    │
       │                    │                    │                    │
```

---

## 7. Modèle de Données

### 7.1 Diagramme Entité-Association

```
┌─────────────────────┐       ┌─────────────────────┐
│      PROFILES       │       │      QUIZZES        │
├─────────────────────┤       ├─────────────────────┤
│ id (UUID)           │       │ id (UUID)           │
│ username            │       │ question_text       │
│ email               │       │ option_a            │
│ best_score          │       │ option_b            │
│ is_cheating         │       │ option_c            │
│ fraud_reason        │       │ option_d            │
│ created_at          │       │ correct_option      │
│ updated_at          │       │ level_number        │
└─────────────────────┘       │ image_url           │
         │                    └─────────────────────┘
         │
         │ 1:N
         ▼
┌─────────────────────┐
│    MONITORING       │
├─────────────────────┤
│ id (UUID)           │
│ user_id (FK)        │
│ latitude            │
│ longitude           │
│ address             │
│ timestamp           │
└─────────────────────┘

┌─────────────────────┐
│    STORAGE          │
├─────────────────────┤
│ Bucket: monitoring  │
│ /{user_id}/         │
│   {session}/        │
│     photos/front/   │
│       *.jpg        │
│     photos/back/    │
│       *.jpg        │
│     audios/         │
│       *.mp4        │
└─────────────────────┘
```

### 7.2 Schémas PostgreSQL (Supabase)

#### Table `profiles`

```sql
CREATE TABLE profiles (
    id UUID REFERENCES auth.users(id) PRIMARY KEY,
    username TEXT,
    email TEXT,
    best_score INTEGER DEFAULT 0,
    is_cheating BOOLEAN DEFAULT FALSE,
    fraud_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

#### Table `quizzes`

```sql
CREATE TABLE quizzes (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    question_text TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT,
    option_d TEXT,
    correct_option TEXT NOT NULL CHECK (correct_option IN ('A', 'B', 'C', 'D')),
    level_number INTEGER NOT NULL,
    image_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_quizzes_level ON quizzes(level_number);
```

#### Table `monitoring`

```sql
CREATE TABLE monitoring (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES profiles(id),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    address TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_monitoring_user ON monitoring(user_id);
```

### 7.3 Structure du Stockage (Supabase Storage)

**Bucket** : `monitoring`

```
monitoring/
├── {user_id_1}/
│   ├── 2024-04-18_14-30/
│   │   ├── photos/
│   │   │   ├── front/
│   │   │   │   ├── 1713445200000_front.jpg
│   │   │   │   ├── 1713445201000_front.jpg
│   │   │   │   └── ...
│   │   │   └── back/
│   │   │       ├── 1713445200500_back.jpg
│   │   │       └── ...
│   │   └── audios/
│   │       └── 2024-04-18_14-30_full_session.mp4
│   └── 2024-04-19_10-15/
│       └── ...
├── {user_id_2}/
│   └── ...
```

---

## 8. API REST - Spécifications

### 8.1 Endpoints d'Authentification

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/auth/register` | Inscription utilisateur |
| POST | `/auth/login` | Connexion utilisateur |

#### POST /auth/register

**Request** :
```json
{
    "email": "student@example.com",
    "password": "securepassword123"
}
```

**Response 200** :
```json
{
    "access_token": "eyJ...",
    "refresh_token": "eyJ...",
    "user": {
        "id": "uuid",
        "email": "student@example.com"
    }
}
```

### 8.2 Endpoints Quiz

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/quiz/questions/{level}` | Récupère les questions d'un niveau |

#### GET /quiz/questions/{level}

**Response 200** :
```json
[
    {
        "id": "uuid",
        "question_text": "Quelle est la capitale de la France ?",
        "option_a": "Paris",
        "option_b": "Lyon",
        "option_c": "Marseille",
        "option_d": "Toulouse",
        "correct_option": "A",
        "level_number": 1,
        "image_url": null
    }
]
```

### 8.3 Endpoints Monitoring

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/monitoring/upload` | Upload photo/audio |
| POST | `/monitoring/location` | Enregistre position GPS |

#### POST /monitoring/upload

**Request** (multipart/form-data) :
```
file: (binary)
file_path: "{user_id}/{session}/photos/front/timestamp.jpg"
```

**Response 200** :
```json
{
    "status": "success",
    "path": "uuid/2024-04-18_14-30/photos/front/1713445200000_front.jpg"
}
```

### 8.4 Endpoints Utilisateur

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/user/status/{user_id}` | Vérifie statut fraude |
| POST | `/quiz/submit` | Soumet un score |

#### GET /user/status/{user_id}

**Response 200** :
```json
{
    "is_cheating": false,
    "fraud_reason": null
}
```

**Response 200 (fraude détectée)** :
```json
{
    "is_cheating": true,
    "fraud_reason": "2 personnes détectées, Objet interdit : cell phone"
}
```

### 8.5 Endpoints Admin

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/admin/students` | Liste tous les étudiants |
| GET | `/admin/monitoring/{student_id}` | Données monitoring d'un étudiant |
| POST | `/admin/quiz/add` | Ajoute une question |
| GET | `/admin/quiz/last-level` | Dernier niveau créé |

#### GET /admin/monitoring/{student_id}

**Response 200** :
```json
{
    "gps_points": [
        {
            "latitude": 48.8566,
            "longitude": 2.3522,
            "address": "Paris, France",
            "timestamp": "2024-04-18T14:30:00Z"
        }
    ],
    "sessions": [
        {
            "date": "2024-04-18_14-30",
            "front_photos": [
                "http://.../front/photo1.jpg",
                "http://.../front/photo2.jpg"
            ],
            "back_photos": [
                "http://.../back/photo1.jpg"
            ],
            "audios": [
                "http://.../audios/session.mp4"
            ]
        }
    ]
}
```

---

## 9. Sécurité

### 9.1 Authentification

| Composant | Implémentation |
|-----------|----------------|
| Provider | Supabase Auth |
| Token | JWT (access + refresh) |
| Stockage local | SharedPreferences Android |
| Headers | `apikey`, `Authorization: Bearer {token}` |

### 9.2 Permissions Android

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### 9.3 Protection Admin

```java
// Dans BuildConfig
ADMIN_EMAIL = "admin@example.com"

// Vérification dans QuizActivity
boolean isAdmin = (userEmail != null && userEmail.equalsIgnoreCase(BuildConfig.ADMIN_EMAIL));
```

### 9.4 Analyse IA (Détection de Fraude)

**Modèle** : YOLO v8 (yolov8n.pt)

**Classes surveillées** :
| Classe | ID YOLO | Action |
|--------|---------|--------|
| person | 0 | Comptage (fraude si > 1) |
| cell phone | 67 | Fraude immédiate |
| book | 73 | Fraude immédiate |
| laptop | 63 | Fraude immédiate |

**Seuil de confiance** : 40% (`conf=0.4`)

```python
results = fraud_model(img, conf=0.4, verbose=False)
```

### 9.5 Flux de Détection

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    PROCESSUS DE DÉTECTION IA                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   1. Photo reçue dans /monitoring/upload                                │
│      │                                                                  │
│      ▼                                                                  │
│   2. Vérification : "photos/front" dans le path ?                       │
│      │                                                                  │
│      ├─ OUI ──▶ 3. Conversion binaire → OpenCV Image                   │
│      │              │                                                   │
│      │              ▼                                                   │
│      │           4. Inférence YOLO                                     │
│      │              │                                                   │
│      │              ├─ Si person_count > 1 ──▶ FRAUDE                   │
│      │              ├─ Si cell phone détecté ──▶ FRAUDE                 │
│      │              ├─ Si book détecté ──▶ FRAUDE                       │
│      │              └─ Si laptop détecté ──▶ FRAUDE                    │
│      │                     │                                            │
│      │                     ▼                                            │
│      │              5. UPDATE profiles SET is_cheating = TRUE          │
│      │                                                                  │
│      └─ NON ──▶ Stockage direct (pas d'analyse)                       │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Planning Prévisionnel

### 10.1 Diagramme de Gantt

```
Phase                              Semaine 1    Semaine 2    Semaine 3
                                   S1  S2  S3  S4  S5  S6  S7  S8  S9
─────────────────────────────────────────────────────────────────────
1. Configuration
   - Setup Supabase Docker          ████
   - Configuration FastAPI          ████
   - BuildConfig Android            ████

2. Backend & BDD
   - Endpoints auth                 ████████
   - Endpoints quiz                 ████████
   - Endpoints monitoring           ████████
   - Intégration YOLO                   ████████

3. Application Android
   - Authentification               ████████
   - Quiz + Timer                   ████████
   - Proctoring (caméra/audio)      ████████████████
   - Monitoring admin                ████████

4. Tests & Finalisation
   - Tests unitaires                        ████████
   - Tests intégration                      ████████
   - Documentation                           ████████
   - Soutenance                                  ████
```

### 10.2 Détail des Tâches

#### Phase 1 : Configuration (2 jours)

| Jour | Tâches | Livrables |
|------|-------|-----------|
| J1 | Installation Docker, Supabase local | Environnement prêt |
| J2 | Configuration FastAPI, BuildConfig Android | Projet initialisé |

#### Phase 2 : Backend & BDD (4 jours)

| Jour | Tâches | Livrables |
|------|-------|-----------|
| J1 | Endpoints auth, connexion Supabase | Auth fonctionnelle |
| J2 | Endpoints quiz, CRUD questions | API Quiz |
| J3 | Endpoints monitoring, upload fichiers | Upload OK |
| J4 | Intégration YOLO, détection fraude | IA opérationnelle |

#### Phase 3 : Application Android (6 jours)

| Jour | Tâches | Livrables |
|------|-------|-----------|
| J1 | Login, Register, WelcomeActivity | Auth mobile |
| J2 | QuizActivity, chargement questions | Quiz de base |
| J3 | Timer, calcul score | Quiz complet |
| J4 | CameraX, capture photos | Proctoring photos |
| J5 | Audio recording, GPS | Proctoring complet |
| J6 | AdminActivity, MonitoringActivity | Dashboard admin |

#### Phase 4 : Tests (3 jours)

| Jour | Tâches | Livrables |
|------|-------|-----------|
| J1 | Tests unitaires backend | Tests passants |
| J2 | Tests intégration end-to-end | Scénarios validés |
| J3 | Documentation, préparation soutenance | Livrables finaux |

---

## 11. Livrables

### 11.1 Code Source

| Composant | Technologie | Répertoire |
|-----------|-------------|------------|
| Application Android | Java | `IQ_QuizApp_AitLahcen/` |
| Backend API | Python/FastAPI | `backend_local/fastapi_app/` |
| Configuration Supabase | Docker/TOML | `backend_local/supabase/` |

### 11.2 Documentation

| Document | Format | Description |
|----------|--------|-------------|
| Cahier des Charges | Markdown | Ce document |
| README | Markdown | Instructions d'installation |
| API Documentation | Swagger/OpenAPI | FastAPI auto-doc |

### 11.3 Configuration Requise

| Prérequis | Version |
|-----------|---------|
| Android Studio | Flamingo+ |
| Docker Desktop | 4.x |
| Python | 3.10+ |
| Java JDK | 17 |
| Supabase CLI | Latest |

### 11.4 Variables d'Environnement

```env
# .env (FastAPI)
SUPABASE_URL=http://localhost:54321
SUPABASE_ANON_KEY=your_anon_key
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key
```

```properties
# build.gradle (Android)
buildConfigField "String", "SUPABASE_URL", "\"http://192.168.x.x:54321\""
buildConfigField "String", "SUPABASE_KEY", "\"your_anon_key\""
buildConfigField "String", "FASTAPI_URL", "\"http://192.168.x.x:8000\""
buildConfigField "String", "ADMIN_EMAIL", "\"admin@example.com\""
```

---

## 12. Glossaire

| Terme | Définition |
|-------|------------|
| **API** | Application Programming Interface - Interface de programmation |
| **BuildConfig** | Classe Android générée avec les constantes de build |
| **CameraX** | Bibliothèque Jetpack pour la caméra Android |
| **FastAPI** | Framework web Python haute performance |
| **Glide** | Bibliothèque de chargement d'images Android |
| **JWT** | JSON Web Token - Token d'authentification sécurisé |
| **OkHttp** | Client HTTP Android |
| **Proctoring** | Surveillance d'examen à distance |
| **Supabase** | Alternative open-source à Firebase |
| **YOLO** | You Only Look Once - Modèle de détection d'objets |

---

## Annexes

### A. Arborescence du Projet

```
SmartQuiz/
├── IQ_QuizApp_AitLahcen/
│   ├── app/
│   │   └── src/main/
│   │       ├── java/com/example/quizapp_aitlahcen/
│   │       │   ├── AdminActivity.java
│   │       │   ├── AudioAdapter.java
│   │       │   ├── Login.java
│   │       │   ├── MonitoringActivity.java
│   │       │   ├── QuizActivity.java
│   │       │   ├── QuizQuestion.java
│   │       │   ├── Register.java
│   │       │   ├── Score.java
│   │       │   ├── Session.java
│   │       │   ├── SessionAdapter.java
│   │       │   ├── Student.java
│   │       │   ├── StudentAdapter.java
│   │       │   ├── SupabaseConfig.java
│   │       │   └── WelcomeActivity.java
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── backend_local/
│   ├── fastapi_app/
│   │   ├── main.py
│   │   ├── requirements.txt
│   │   └── .env
│   ├── supabase/
│   │   ├── config.toml
│   │   └── migrations/
│   └── package.json
│
└── README.md
```

### B. Commandes de Démarrage

```bash
# Démarrer Supabase local
cd backend_local
npx supabase start

# Démarrer FastAPI
cd fastapi_app
python -m uvicorn main:app --reload --host 0.0.0.0 --port 8000

# Construire l'APK Android
cd IQ_QuizApp_AitLahcen
./gradlew assembleDebug
```

### C. Classes YOLO Surveillées

```python
# Classes détectées pour la fraude
FRAUD_CLASSES = {
    0: "person",      # Fraude si count > 1
    67: "cell phone", # Fraude immédiate
    73: "book",       # Fraude immédiate
    63: "laptop"      # Fraude immédiate
}

# Analyse dans main.py
for r in results:
    person_count = (r.boxes.cls == 0).sum()
    if person_count > 1:
        fraud_detected = True
        reasons.append(f"{int(person_count)} personnes détectées")
    
    for box in r.boxes:
        label = fraud_model.names[int(box.cls[0])]
        if label in ["cell phone", "book", "laptop"]:
            fraud_detected = True
            reasons.append(f"Objet interdit : {label}")
```

---

**Document Version : 1.0**  
**Date de création :** Avril 2026  
**Dernière mise à jour :** Avril 2026  
**Auteur :** Équipe Projet QuizApp AitLahcen