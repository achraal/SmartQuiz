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

    private MediaRecorder mediaRecorder;
    private String audioPath;
    private boolean isRecording = false;

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
                bindCamera();
                startProctoring();
            } else {
                // Sinon, on demande les permissions manquantes
                ActivityCompat.requestPermissions(this, permissions, 101);
            }
        } else {
            Log.d("QUIZ_APP", "Mode Admin détecté : Caméra et Monitoring désactivés.");
        }

        /*if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCamera();
            startProctoring();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        }*/

        // 4. Lancement du Quiz
        loadQuestionFromServer();

        btnNext.setOnClickListener(v -> checkAnswerAndNext());
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
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = isFrontCamera ?
                        CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);
            } catch (Exception e) {
                Log.e("CameraX", "Erreur binding: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }
    /*private void startProctoring() {
        proctoringHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    // 1. On prend la photo
                    capturePhoto();

                    // 2. On lance l'audio s'il ne tourne pas déjà
                    if (!isRecording) {
                        startRecording();
                    }
                    // On boucle toutes les 5 secondes
                    proctoringHandler.postDelayed(this, PHOTO_INTERVAL);
                }
            }
        }, 5000);
    }
    private void capturePhoto() {
        if (isAdmin || imageCapture == null) return;

        File photoFile = new File(getExternalFilesDir(null),
                System.currentTimeMillis() + (isFrontCamera ? "_front.jpg" : "_back.jpg"));

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        uploadToSupabaseStorage(photoFile, isFrontCamera ? "front" : "back");
                        // On alterne pour la prochaine fois
                        isFrontCamera = !isFrontCamera;
                        //bindCamera();
                        runOnUiThread(() -> bindCamera());
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("CameraX", "Erreur capture: " + exception.getMessage());
                    }
                });
    }*/
    private void startProctoring() {
        if (!isRecording) startRecording();

        proctoringHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    // On prend UNE photo (le type est géré par isFrontCamera)
                    capturePhoto();

					checkFraudStatus();
                    // On boucle chaque seconde
                    proctoringHandler.postDelayed(this, PHOTO_INTERVAL);
                }
            }
        }, 2000);
    }

    private void capturePhoto() {
        if (isAdmin || imageCapture == null) return;

        String type = isFrontCamera ? "front" : "back";
        String localPath = System.currentTimeMillis() + "_" + type + ".jpg";
        File photoFile = new File(getExternalFilesDir(null), localPath);

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        uploadToSupabaseStorage(photoFile, type);

                        // ON ALTERNE ICI : Prépare la caméra opposée pour la SECONDE SUIVANTE
                        isFrontCamera = !isFrontCamera;
                        runOnUiThread(() -> bindCamera());
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("CameraX", "Erreur capture: " + exception.getMessage());
                        // En cas d'erreur, on essaie quand même de switcher pour ne pas bloquer le cycle
                        isFrontCamera = !isFrontCamera;
                        runOnUiThread(() -> bindCamera());
                    }
                });
    }
    private void uploadToSupabaseStorage(File file, String type) {
        // On garde la même structure de dossier pour que FastAPI sache où le mettre
        String remotePath = userId + "/" + sessionTimestamp + "/photos/" + type + "/" + file.getName();
        String url = SupabaseConfig.FASTAPI_URL + "/monitoring/upload";

        // Préparation du corps MultiPart (obligatoire pour envoyer des fichiers)
        okhttp3.RequestBody requestBody = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file_path", remotePath)
                .addFormDataPart("file", file.getName(),
                        okhttp3.RequestBody.create(okhttp3.MediaType.parse("image/jpeg"), file))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    Log.d("FastAPI", "Photo synchronisée via Backend");
                    file.delete(); // On supprime le fichier local après succès
                }
            }
            @Override
            public void onFailure(Call call, IOException e) { e.printStackTrace(); }
        });
    }
    /*private void startRecording() {
        if (isAdmin || isRecording) return;
        try {
            // Sécurité : Libérer si une instance existe déjà
            if (mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            // UN SEUL CHEMIN PROPRE EN .MP4
            audioPath = getExternalFilesDir(null).getAbsolutePath() + "/" + System.currentTimeMillis() + "_audio.mp4";
            mediaRecorder.setOutputFile(audioPath);

            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Log.d("QUIZ_APP", "Enregistrement démarré : " + audioPath);

            // On arrête l'audio après 8 secondes pour laisser du temps au processeur
            new Handler(Looper.getMainLooper()).postDelayed(this::stopAndUploadAudio, 8000);

        } catch (Exception e) {
            Log.e("QUIZ_APP", "Erreur MediaRecorder : " + e.getMessage());
            isRecording = false;
        }
    }
    private void stopAndUploadAudio() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e("QUIZ_APP", "Erreur lors du stop : " + e.getMessage());
            }

            mediaRecorder = null;
            isRecording = false;

            File audioFile = new File(audioPath);
            // On vérifie que le fichier n'est pas vide (plus de 500 octets)
            if (audioFile.exists() && audioFile.length() > 500) {
                uploadAudioToSupabase(audioFile);
            } else {
                Log.e("QUIZ_APP", "Fichier audio vide ou trop petit, abandon de l'envoi.");
            }
        }
    }*/
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
		String url = SupabaseConfig.FASTAPI_URL + "/user/status/" + userId;

		Request request = new Request.Builder().url(url).get().build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onResponse(Call call, Response response) throws IOException {
				if (response.isSuccessful()) {
					String json = response.body().string();
					// Si ton JSON contient is_cheating: true
					if (json.contains("\"is_cheating\":true")) {
						runOnUiThread(() -> {
							stopAndFinalUploadAudio(); // Arrêter l'audio proprement
							showFraudDialog();
						});
					}
				}
			}
			@Override
			public void onFailure(Call call, IOException e) {}
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
}