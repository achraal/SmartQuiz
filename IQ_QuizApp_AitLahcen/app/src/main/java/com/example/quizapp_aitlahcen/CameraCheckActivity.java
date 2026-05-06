package com.example.quizapp_aitlahcen;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraCheckActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView tvStatus;
    private Button btnGo;

    private ExecutorService cameraExecutor;
    private FaceDetector detector;

    private boolean isFaceDetected = false;
    private boolean isLuminosityOk = false;
    private String userId;
    private float currentPitch = 0f;
    private float currentYaw = 0f;
    private float currentLuma = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_check);

        userId = getIntent().getStringExtra("USER_ID");
        previewView = findViewById(R.id.previewView);
        tvStatus = findViewById(R.id.tvStatus);
        btnGo = findViewById(R.id.btnGo);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Configuration ML Kit pour une détection précise
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        detector = FaceDetection.getClient(options);

        startCamera();

        btnGo.setOnClickListener(v -> {
            // On désactive le bouton pour éviter les doubles clics
            btnGo.setEnabled(false);
            tvStatus.setText("Calibration en cours...");

            // On envoie les VRAIES valeurs capturées
            sendCalibrationToBackend(currentPitch, currentYaw, currentLuma);
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Analyse en temps réel
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    analyzeImage(image);
                });

                // Sélection caméra frontale
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("Cerebro", "Erreur CameraX", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(ImageProxy imageProxy) {
        @SuppressWarnings("UnsafeOptInUsageError")
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            // 1. Calcul de la luminosité
            double luma = calculateLuma(imageProxy);
            isLuminosityOk = luma > 40; // Seuil arbitraire

            // 2. Détection du visage avec ML Kit
            InputImage image = InputImage.fromMediaImage(mediaImage,
                    imageProxy.getImageInfo().getRotationDegrees());

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (faces.size() == 1) {
                            com.google.mlkit.vision.face.Face face = faces.get(0);
                            currentPitch = face.getHeadEulerAngleX();
                            currentYaw = face.getHeadEulerAngleY();
                            currentLuma = (float) luma;
                            isFaceDetected = true;
                        } else {
                            isFaceDetected = false;
                        }
                        updateUI(luma);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Cerebro", "ML Kit Error: " + e.getMessage());
                        isFaceDetected = false;
                        updateUI(luma);
                    })
                    .addOnCompleteListener(task -> {
                        // CRUCIAL : Libère l'image pour que CameraX puisse envoyer la suivante
                        imageProxy.close();
                    });
        }
    }
    private void sendCalibrationToBackend(float pitch, float yaw, float luma) {
        Log.d("Cerebro_Debug", "Envoi calibration - Pitch: " + pitch + " | Yaw: " + yaw);

        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

        // Construction du JSON avec les valeurs réelles
        String json = String.format(java.util.Locale.US,
                "{\"ref_pitch\": %.4f, \"ref_yaw\": %.4f, \"ref_brightness\": %.4f}",
                pitch, yaw, luma);

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                json, okhttp3.MediaType.parse("application/json; charset=utf-8"));

        // Utilisation de BuildConfig.FASTAPI_URL défini dans ton gradle
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(BuildConfig.FASTAPI_URL + "/monitoring/calibrate/" + userId)
                .post(body)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    btnGo.setEnabled(true);
                    Toast.makeText(CameraCheckActivity.this, "Erreur serveur : " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Intent intent = new Intent(CameraCheckActivity.this, QuizActivity.class);
                        intent.putExtras(getIntent().getExtras());
                        startActivity(intent);
                        finish();
                    });
                } else {
                    runOnUiThread(() -> {
                        btnGo.setEnabled(true);
                        Log.e("Cerebro_Error", "Erreur API: " + response.code());
                    });
                }
            }
        });
    }

    private double calculateLuma(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        java.nio.ByteBuffer buffer = plane.getBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        long sum = 0;
        for (byte b : data) { sum += (b & 0xFF); }
        return (double) sum / data.length;
    }

    private void updateUI(double luma) {
        runOnUiThread(() -> {
            // 1. Check Luminosité
            if (!isLuminosityOk) {
                tvStatus.setText("Éclairage insuffisant (" + (int)luma + ")");
                btnGo.setVisibility(View.GONE);
                return;
            }

            // 2. Check Présence du visage
            if (!isFaceDetected) {
                tvStatus.setText("Visage invisible ou caché");
                btnGo.setVisibility(View.GONE);
                return;
            }

            // 3. Check Pose du visage (Rotation & Inclinaison)
            // currentPitch: > 20 (baisse la tête), < -20 (lève la tête)
            // currentYaw: > 20 (tourne à droite), < -20 (tourne à gauche)

            boolean isLookingStraight = Math.abs(currentPitch) < 20 && Math.abs(currentYaw) < 20;

            if (!isLookingStraight) {
                if (Math.abs(currentYaw) >= 20) {
                    tvStatus.setText("Regardez bien l'écran (ne tournez pas la tête)");
                } else {
                    tvStatus.setText("Tenez votre téléphone bien en face");
                }
                btnGo.setVisibility(View.GONE);
            } else {
                // Tout est OK
                tvStatus.setText("Position parfaite ! Cliquez pour calibrer");
                btnGo.setVisibility(View.VISIBLE);
                btnGo.setEnabled(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        detector.close();
    }
}