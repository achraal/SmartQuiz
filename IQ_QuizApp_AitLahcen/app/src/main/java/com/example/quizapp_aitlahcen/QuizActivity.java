package com.example.quizapp_aitlahcen;

import android.content.Intent;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;

public class QuizActivity extends AppCompatActivity {

    private int currentLevel = 1;
    private int score = 0;
    private QuizQuestion currentQuestion;
    private String userId, userToken;

    private TextView tvTimer, tvQuestionText, tvProgress;
    private RadioGroup rgOptions;
    private Button btnNext;
    private ProgressBar pbQuiz;
    private ImageView ivQuestion;

    private CountDownTimer countDownTimer;
    private static final long TIME_LIMIT = 30000; // 30 secondes
    private OkHttpClient client = new OkHttpClient();
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    // Proctoring variables
    private Handler proctoringHandler = new Handler(Looper.getMainLooper());
    private boolean isFrontCamera = true;
    private ImageCapture imageCapture;
    private String sessionTimestamp; // Pour différencier les passages
    private ImageCapture imageCaptureFront;
    private ImageCapture imageCaptureBack;
    private static final int PHOTO_INTERVAL = 1000;
    private boolean isAdmin = false;
    private boolean isDialogShowing = false;
    private MediaRecorder mediaRecorder;
    private String audioPath;
    private boolean isRecording = false;
    private Handler audioChunkHandler = new Handler(Looper.getMainLooper());
    private MediaRecorder chunkRecorder;
    private String chunkPath;
    private boolean isProctoringActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        // 1. Récupération des infos utilisateur
        userId = getIntent().getStringExtra("USER_ID");
        userToken = getIntent().getStringExtra("TOKEN");

        // 2. Initialisation des vues
        tvTimer = findViewById(R.id.tvTimer);
        tvQuestionText = findViewById(R.id.tvQuestionText);
        tvProgress = findViewById(R.id.tvQuestionNumber);
        rgOptions = findViewById(R.id.rgOptions);
        btnNext = findViewById(R.id.btnNext);
        pbQuiz = findViewById(R.id.quizProgress);
        ivQuestion = findViewById(R.id.ivQuestion);

        String userEmail = getIntent().getStringExtra("USER_EMAIL");
        isAdmin = (userEmail != null && userEmail.equalsIgnoreCase(BuildConfig.ADMIN_EMAIL));

        sessionTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.getDefault())
                .format(new java.util.Date());

        // 3. Vérification des permissions dès le début (pour l'écran Score plus tard)
        checkLocationPermission();

        // CONDITION CRUCIALE : On ne lance le monitoring que si ce n'est PAS l'admin
        if (!isAdmin) {
            // 1. On définit la liste des permissions nécessaires
            String[] permissions = {
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };

            // 2. On vérifie si TOUTES les permissions sont déjà accordées
            boolean allPermissionsGranted = true;
            for (String p : permissions) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                // Si tout est OK, on lance la caméra et le proctoring (qui lancera l'audio)
                //bindCamera();
                startProctoring();
                startRecording();       // <-- AJOUTE ÇA : Lance l'audio complet (Stockage final)
                startFraudCheckLoop();
            } else {
                // Sinon, on demande les permissions manquantes
                ActivityCompat.requestPermissions(this, permissions, 101);
            }
        } else {
            Log.d("QUIZ_APP", "Mode Admin détecté : Caméra et Monitoring désactivés.");
        }

        // 4. Lancement du Quiz
        loadQuestionFromServer();

        btnNext.setOnClickListener(v -> checkAnswerAndNext());
    }
    private void startFraudCheckLoop() {
        if (!isProctoringActive) return;

        checkFraudStatus(); // Appelle ta méthode existante

        // On revérifie toutes les 10 secondes pour ne pas surcharger le réseau
        proctoringHandler.postDelayed(this::startFraudCheckLoop, 10000);
    }

    private void loadQuestionFromServer() {
        String url = SupabaseConfig.FASTAPI_URL + "/quiz/questions/" + currentLevel;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    QuizQuestion[] questions = new Gson().fromJson(json, QuizQuestion[].class);
                    if (questions != null && questions.length > 0) {
                        currentQuestion = questions[0];
                        runOnUiThread(() -> displayQuestion());
                    } else {
                        runOnUiThread(() -> finishQuiz());
                    }
                }
            }
            @Override
            public void onFailure(Call call, IOException e) { e.printStackTrace(); }
        });
    }

    private void displayQuestion() {
        tvQuestionText.setText(currentQuestion.question_text);
        tvProgress.setText("Question " + currentLevel);
        pbQuiz.setProgress(currentLevel);

        // Gestion de l'image avec Glide et Headers de sécurité
        if (currentQuestion.image_url != null && !currentQuestion.image_url.isEmpty()) {
            ivQuestion.setVisibility(View.VISIBLE);
            GlideUrl secureImageUrl = new GlideUrl(currentQuestion.image_url,
                    new LazyHeaders.Builder()
                            .addHeader("apikey", SupabaseConfig.API_KEY)
                            .addHeader("Authorization", "Bearer " + userToken)
                            .build());

            Glide.with(this)
                    .load(secureImageUrl)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(ivQuestion);
        } else {
            ivQuestion.setVisibility(View.GONE);
        }

        // Options de réponse
        ((RadioButton)rgOptions.getChildAt(0)).setText(currentQuestion.option_a);
        ((RadioButton)rgOptions.getChildAt(1)).setText(currentQuestion.option_b);
        ((RadioButton)rgOptions.getChildAt(2)).setText(currentQuestion.option_c);
        ((RadioButton)rgOptions.getChildAt(3)).setText(currentQuestion.option_d);

        rgOptions.clearCheck();
        startTimer();
    }
    private void checkAnswerAndNext() {
        int checkedId = rgOptions.getCheckedRadioButtonId();

        if (checkedId == -1) {
            Toast.makeText(this, "Veuillez choisir une réponse !", Toast.LENGTH_SHORT).show();
            return;
        }

        if (countDownTimer != null) countDownTimer.cancel();

        // Calcul du score
        RadioButton selected = findViewById(checkedId);
        int index = rgOptions.indexOfChild(selected);
        String answerLetter = (index == 0) ? "A" : (index == 1) ? "B" : (index == 2) ? "C" : "D";

        if (currentQuestion != null && answerLetter.equals(currentQuestion.correct_option)) {
            score++;
        }

        goToNextQuestion();
    }
    private void goToNextQuestion() {
        currentLevel++; // On incrémente toujours
        loadQuestionFromServer();
        // La méthode loadQuestionFromServer appellera finishQuiz()
        // automatiquement si le tableau de questions renvoyé par Supabase est vide.
    }

    private void startTimer() {
        if (countDownTimer != null) countDownTimer.cancel();

        countDownTimer = new CountDownTimer(TIME_LIMIT, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvTimer.setText("Temps: " + seconds + "s");
                if (seconds <= 5) tvTimer.setTextColor(Color.RED);
                else tvTimer.setTextColor(Color.BLACK);
            }
            @Override
            public void onFinish() {
                Toast.makeText(QuizActivity.this, "Temps écoulé !", Toast.LENGTH_SHORT).show();
                goToNextQuestion();
            }
        }.start();
    }
    private void finishQuiz() {
        Intent intent = new Intent(QuizActivity.this, Score.class);
        intent.putExtra("FINAL_SCORE", score);
        intent.putExtra("USER_ID", userId);
        intent.putExtra("TOKEN", userToken);
        stopAndFinalUploadAudio();
        startActivity(intent);
        finish();
    }
    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }
    @Override
    protected void onDestroy() {
        isProctoringActive = false; // Arrête la récursion du cycle audio
        audioChunkHandler.removeCallbacksAndMessages(null); // Coupe les délais en attente

        // Libération propre des ressources si elles sont encore actives
        if (chunkRecorder != null) {
            try {
                chunkRecorder.stop();
                chunkRecorder.release();
            } catch (Exception e) { /* Ignorer */ }
            chunkRecorder = null;
        }
        super.onDestroy();

        if (countDownTimer != null) countDownTimer.cancel();

        // Arrêter le cycle de photos
        proctoringHandler.removeCallbacksAndMessages(null);

        // Sécurité : au cas où l'utilisateur quitte sans finir le quiz
        if (isRecording) {
            stopAndFinalUploadAudio();
        }
    }
    private void bindCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();

                // Configuration commune
                ImageCapture.Builder builder = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(new android.util.Size(640, 480));

                imageCaptureFront = builder.build();
                imageCaptureBack = builder.build();

                // On bind les deux au cycle de vie
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageCaptureFront);
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageCaptureBack);

                Log.d("CEREBRO_DUAL", "Les deux caméras sont prêtes et bindées.");

            } catch (Exception e) {
                Log.e("CEREBRO_DUAL", "Erreur Dual Binding: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startProctoring() {
        isProctoringActive = true;
        startAudioChunkCycle(); // Ton cycle audio semble correct

        proctoringHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && isProctoringActive) {
                    // On lance la capture (le délai interne de 500ms gère le reste)
                    bindAndCapture(isFrontCamera);

                    // On alterne pour le prochain coup
                    isFrontCamera = !isFrontCamera;

                    // On relance la boucle dans 3 secondes pour laisser le temps à l'upload
                    proctoringHandler.postDelayed(this, 3000);
                }
            }
        });
    }

    private void bindAndCapture(boolean isFront) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // 1. On libère tout avant de changer de sens
                cameraProvider.unbindAll();

                // 2. On choisit la caméra
                CameraSelector selector = isFront ?
                        CameraSelector.DEFAULT_FRONT_CAMERA :
                        CameraSelector.DEFAULT_BACK_CAMERA;

                // 3. On reconstruit l'objet ImageCapture proprement
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(rotation)
                        .build();

                // 4. On attache au cycle de vie
                cameraProvider.bindToLifecycle(this, selector, imageCapture);

                // --- CRUCIAL ---
                // On attend 500ms (au lieu de 200) pour laisser le hardware
                // sortir de l'état "Closed" et s'ouvrir réellement.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isProctoringActive) {
                        takePictureInternal(isFront ? "front" : "back");
                    }
                }, 500);

            } catch (Exception e) {
                Log.e("QUIZ_DEBUG", "Erreur Binding: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private void takePictureInternal(String type) {
        if (imageCapture == null) return;

        int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
        imageCapture.setTargetRotation(displayRotation);

        File photoFile = new File(getExternalFilesDir(null), System.currentTimeMillis() + "_" + type + ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        // ON APPELLE LA MÉTHODE DE ROTATION/UPLOAD ICI
                        uploadToSupabaseStorage(photoFile, type);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("QUIZ_DEBUG", "Erreur capture " + type + " : " + exception.getMessage());
                    }
                });
    }

    // Une version plus flexible de ta méthode capturePhoto
    private void captureSpecificCamera(ImageCapture camera, String type) {
        if (isAdmin || camera == null) return;

        File photoFile = new File(getExternalFilesDir(null), System.currentTimeMillis() + "_" + type + ".jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        camera.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        uploadToSupabaseStorage(photoFile, type);
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("CameraX", "Erreur capture " + type + ": " + exception.getMessage());
                    }
                });
    }
    private void capturePhoto() {
        if (isAdmin) return;

        // On choisit l'objet ImageCapture selon l'état du switch
        ImageCapture activeCapture = isFrontCamera ? imageCaptureFront : imageCaptureBack;
        if (activeCapture == null) return;

        String type = isFrontCamera ? "front" : "back";
        File photoFile = new File(getExternalFilesDir(null), System.currentTimeMillis() + "_" + type + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        activeCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        uploadToSupabaseStorage(photoFile, type);

                        // ON SWITCH LE BOOLEAN POUR LA SECONDE SUIVANTE
                        isFrontCamera = !isFrontCamera;
                        // PLUS BESOIN DE bindCamera() ICI !
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("CameraX", "Erreur capture: " + exception.getMessage());
                        isFrontCamera = !isFrontCamera;
                    }
                });
    }

    private void uploadToSupabaseStorage(File file, String type) {
        // 1. Charger l'image originale
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return;

        // 2. CORRECTION DE LA ROTATION (90 degrés comme vu sur ta capture)
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        if ("front".equalsIgnoreCase(type)) {
            // Si c'est la frontale et qu'elle est à l'envers (180°), on la remet droite
            // Teste 270 ou -90 si 180 ne suffit pas (dépend du montage du capteur)
            matrix.postRotate(270);
            // Optionnel : matrix.postScale(-1, 1); // Décommente si tu veux enlever l'effet miroir
        } else {
            // La caméra arrière reste à 90° comme avant
            matrix.postRotate(90);
        }

        android.graphics.Bitmap rotatedBitmap = android.graphics.Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // 3. Redimensionnement (Optimisation)
        int maxWidth = 1024;
        int width = rotatedBitmap.getWidth();
        int height = rotatedBitmap.getHeight();
        float ratio = (float) width / (float) height;
        int newWidth = maxWidth;
        int newHeight = (int) (maxWidth / ratio);

        android.graphics.Bitmap finalBitmap = android.graphics.Bitmap.createScaledBitmap(rotatedBitmap, newWidth, newHeight, true);

        // 4. Compression en JPEG
        File optimizedFile = new File(getExternalFilesDir(null), "opt_" + file.getName());
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(optimizedFile)) {
            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 5. Envoi au Backend
        String remotePath = userId + "/" + sessionTimestamp + "/photos/" + type + "/" + file.getName();
        String url = SupabaseConfig.FASTAPI_URL + "/monitoring/upload";

        okhttp3.RequestBody requestBody = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file_path", remotePath)
                .addFormDataPart("file", file.getName(),
                        okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/jpeg"), optimizedFile))
                .build();

        Request request = new Request.Builder().url(url).post(requestBody).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    Log.d("FastAPI", "Photo redressée et envoyée");
                    file.delete();
                    optimizedFile.delete();
                }
            }
            @Override
            public void onFailure(Call call, IOException e) { e.printStackTrace(); }
        });
    }

    private void startRecording() {
        if (isAdmin || isRecording) return;
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            // Path avec dossier audios
            audioPath = getExternalFilesDir(null).getAbsolutePath() + "/" + sessionTimestamp + "_full_session.mp4";
            mediaRecorder.setOutputFile(audioPath);

            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
        } catch (Exception e) {
            Log.e("QUIZ_APP", "Audio error: " + e.getMessage());
        }
    }

    // APPELLER CETTE MÉTHODE DANS finishQuiz() JUSTE AVANT DE CHANGER D'ACTIVITY
    private void stopAndFinalUploadAudio() {
        if (mediaRecorder != null && isRecording) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;

            File audioFile = new File(audioPath);
            // Upload vers : monitoring/uuid/session/audios/file.mp4
            uploadAudioToSupabase(audioFile);
        }
    }
    private void uploadAudioToSupabase(File file) {
        String remotePath = userId + "/" + sessionTimestamp + "/audios/" + file.getName();
        String url = SupabaseConfig.FASTAPI_URL + "/monitoring/upload";

        okhttp3.RequestBody requestBody = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file_path", remotePath)
                .addFormDataPart("file", file.getName(),
                        okhttp3.RequestBody.create(okhttp3.MediaType.parse("audio/mp4"), file))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    Log.d("FastAPI", "Audio de session sauvegardé via Backend");
                    file.delete();
                }
            }
            @Override
            public void onFailure(Call call, IOException e) { e.printStackTrace(); }
        });
    }

    private void checkFraudStatus() {
        // On ajoute le timestamp pour forcer le serveur (et les intercepteurs) à ne pas utiliser le cache
        String url = SupabaseConfig.FASTAPI_URL + "/user/status/" + userId + "?t=" + System.currentTimeMillis();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cache-Control", "no-cache")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        // On vérifie le champ is_cheating
                        if (json.optBoolean("is_cheating", false)) {
                            runOnUiThread(() -> {
                                if (!isDialogShowing) {
                                    isDialogShowing = true; // Empêche les multiples dialogues
                                    stopAndFinalUploadAudio();
                                    showFraudDialog();
                                }
                            });
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
            @Override
            public void onFailure(Call call, IOException e) { /* Log error */ }
        });
    }
	private void showFraudDialog() {
		new androidx.appcompat.app.AlertDialog.Builder(this)
			.setTitle("⚠️ Quiz Interrompu")
			.setMessage("Le système de surveillance a détecté une irrégularité.")
			.setCancelable(false)
			.setPositiveButton("Fermer", (d, w) -> finish())
			.show();
	}
    private void startAudioChunkCycle() {
        if (!isProctoringActive) return;

        try {
            chunkRecorder = new MediaRecorder();
            chunkRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            chunkRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            chunkRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            chunkPath = getExternalFilesDir(null).getAbsolutePath() + "/audio_chunk.mp4";
            chunkRecorder.setOutputFile(chunkPath);
            chunkRecorder.prepare();
            chunkRecorder.start();

            // Après 5 secondes, on arrête ce morceau, on l'envoie et on recommence
            audioChunkHandler.postDelayed(() -> {
                stopAndUploadChunk();
                startAudioChunkCycle();
            }, 5000);

        } catch (Exception e) {
            Log.e("CEREBRO_AUDIO", "Erreur Chunk: " + e.getMessage());
        }
    }

    private void stopAndUploadChunk() {
        try {
            if (chunkRecorder != null) {
                chunkRecorder.stop();
                chunkRecorder.release();
                chunkRecorder = null;

                File file = new File(chunkPath);
                if (file.exists()) {
                    uploadChunkOnlyForAnalysis(file);
                }
            }
        } catch (Exception e) {
            Log.e("CEREBRO_AUDIO", "Stop Chunk Error: " + e.getMessage());
        }
    }

    private void uploadChunkOnlyForAnalysis(File file) {
        // IMPORTANT : Le chemin contient "temp_audio" pour que FastAPI ne le stocke pas
        String remotePath = userId + "/temp_audio/analysis.mp4";
        String url = SupabaseConfig.FASTAPI_URL + "/monitoring/upload";

        okhttp3.RequestBody requestBody = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file_path", remotePath)
                .addFormDataPart("file", "chunk.mp4",
                        okhttp3.RequestBody.create(okhttp3.MediaType.parse("audio/mp4"), file))
                .build();

        Request request = new Request.Builder().url(url).post(requestBody).build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                file.delete(); // On supprime le morceau local après envoi
            }
            @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {}
        });
    }
}