package com.example.quizapp_aitlahcen;

import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AdminActivity extends AppCompatActivity {
    private EditText etQuestion, etOptionA, etOptionB, etOptionC, etOptionD, etImageUrl;
    private Spinner spinnerCorrect;
    private Button btnSave, btnLogout;
    private String adminToken;
    private OkHttpClient client = new OkHttpClient();
    private RecyclerView rvStudents;
    private StudentAdapter adapter;
    private List<Student> studentList = new ArrayList<>();
    private Button btnViewLogs;
    // --- Section Avatar ---
    private Button btnRecordAvatar;
    private MediaRecorder recorder;
    private String audioPath;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        adminToken = getIntent().getStringExtra("TOKEN");

        // 1. Initialiser les vues du formulaire
        initFormViews();

        // 2. Initialiser le RecyclerView pour les étudiants
        rvStudents = findViewById(R.id.rvStudents);
        rvStudents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StudentAdapter(studentList, student -> {
            openMonitoring(student);
        });
        rvStudents.setAdapter(adapter);
        btnLogout = findViewById(R.id.btnLogout);

        btnViewLogs = findViewById(R.id.btnViewLogs); // Assure-toi de l'ajouter dans ton XML

        btnViewLogs.setOnClickListener(v -> {
            Intent intent = new Intent(this, FraudLogsActivity.class);
            intent.putExtra("TOKEN", adminToken);
            startActivity(intent);
        });
        // Setup de l'audio
        audioPath = getExternalCacheDir().getAbsolutePath() + "/admin_voice.3gp";
        btnRecordAvatar = findViewById(R.id.btnRecordAvatar);

        btnRecordAvatar.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    startRecording();
                    btnRecordAvatar.setText("Enregistrement...");
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    stopRecording();
                    btnRecordAvatar.setText("Maintenir pour parler");
                    sendAudioToBackend(); // Envoi auto après enregistrement
                    return true;
            }
            return false;
        });

        // 3. Charger les données
        loadStudents();

        btnSave.setOnClickListener(v -> {
            if (validateForm()) fetchLastLevelAndSave();
        });
        btnLogout.setOnClickListener(v -> {
            // 1. Effacer les SharedPreferences (si tu stockes le token)
            getSharedPreferences("AUTH_PREFS", MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();

            // 2. Retourner à l'écran de Login
            Intent intent = new Intent(this, Login.class);
            // On nettoie la pile d'activités pour éviter que l'admin revienne en arrière
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Dès que tu reviens du Monitoring, cette fonction s'exécute
        loadStudents();
    }

    private void initFormViews() {
        etQuestion = findViewById(R.id.etQuestion);
        etOptionA = findViewById(R.id.etOptionA);
        etOptionB = findViewById(R.id.etOptionB);
        etOptionC = findViewById(R.id.etOptionC);
        etOptionD = findViewById(R.id.etOptionD);
        etImageUrl = findViewById(R.id.etImageUrl);
        spinnerCorrect = findViewById(R.id.spinnerCorrect);
        btnSave = findViewById(R.id.btnSave);

        String[] options = {"A", "B", "C", "D"};
        ArrayAdapter<String> adapterSpinner = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCorrect.setAdapter(adapterSpinner);
    }

    private void fetchLastLevelAndSave() {
        // Correction : On appelle l'endpoint du QUIZ, pas des STUDENTS
        String url = SupabaseConfig.FASTAPI_URL + "/admin/quiz/last-level";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + adminToken) // Sécurité
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int nextLevel = 1;
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    // On parse l'objet simple retourné par FastAPI
                    JsonObject jsonObject = new Gson().fromJson(json, JsonObject.class);
                    nextLevel = jsonObject.get("last_level").getAsInt() + 1;
                }
                saveQuiz(nextLevel);
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("Admin", "Erreur fetch level", e);
                // Optionnel : par sécurité, on peut quand même tenter de sauvegarder au niveau 1
            }
        });
    }

    private void saveQuiz(int level) {
        QuizQuestion newQ = new QuizQuestion();
        newQ.question_text = etQuestion.getText().toString();
        newQ.option_a = etOptionA.getText().toString();
        newQ.option_b = etOptionB.getText().toString();
        newQ.option_c = etOptionC.getText().toString();
        newQ.option_d = etOptionD.getText().toString();
        newQ.correct_option = spinnerCorrect.getSelectedItem().toString();
        newQ.level_number = level;

        String imgUrl = etImageUrl.getText().toString().trim();
        if (!imgUrl.isEmpty()) newQ.image_url = imgUrl;

        String json = new Gson().toJson(newQ);
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"), json);

        Request request = new Request.Builder()
                .url(SupabaseConfig.FASTAPI_URL + "/admin/quiz/add")
                .addHeader("Authorization", "Bearer " + adminToken) // Sécurité
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(AdminActivity.this, "Niveau " + level + " ajouté !", Toast.LENGTH_SHORT).show();
                        clearForm();
                    });
                } else {
                    Log.e("Admin", "Erreur save: " + response.code());
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("Admin", "Erreur réseau", e);
            }
        });
    }

    private void loadStudents() {
        String url = SupabaseConfig.FASTAPI_URL + "/admin/students";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    Student[] students = new Gson().fromJson(json, Student[].class);

                    runOnUiThread(() -> {
                        studentList.clear();
                        if (students != null) {
                            studentList.addAll(Arrays.asList(students));
                        }
                        adapter.notifyDataSetChanged();
                    });
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("Admin", "Erreur chargement étudiants", e);
            }
        });
    }

    private void clearForm() {
        runOnUiThread(() -> {
            etQuestion.setText("");
            etOptionA.setText("");
            etOptionB.setText("");
            etOptionC.setText("");
            etOptionD.setText("");
            etImageUrl.setText("");
        });
    }
    private boolean validateForm() {
        if (etQuestion.getText().toString().trim().isEmpty() ||
                etOptionA.getText().toString().trim().isEmpty() ||
                etOptionB.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Veuillez remplir au moins la question et les options A et B", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
    private void openMonitoring(Student student) {
        Toast.makeText(this, "Monitoring de " + student.username, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MonitoringActivity.class);
        intent.putExtra("STUDENT_ID", student.id);
        intent.putExtra("STUDENT_NAME", student.username);
        intent.putExtra("TOKEN", adminToken); // On repasse le token pour les requêtes
        startActivity(intent);
    }
    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile(audioPath);
        try {
            recorder.prepare();
            recorder.start();
            isRecording = true;
        } catch (IOException e) {
            Log.e("Avatar", "prepare() failed");
        }
    }

    private void stopRecording() {
        if (isRecording) {
            try {
                recorder.stop();
                recorder.release();
            } catch (RuntimeException stopException) {
                // Gère le cas où l'appui est trop court
                Log.e("Avatar", "Recording too short");
            }
            recorder = null;
            isRecording = false;
        }
    }
    private void sendAudioToBackend() {
        java.io.File file = new java.io.File(audioPath);
        if (!file.exists()) return;

        okhttp3.RequestBody fileBody = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("audio/3gp"), file);

        okhttp3.MultipartBody requestBody = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("audio", "voice.3gp", fileBody)
                .addFormDataPart("user_id", "admin_achraf")
                .addFormDataPart("is_welcome", "true")
                .build();

        Request request = new Request.Builder()
                .url(SupabaseConfig.FASTAPI_URL + "/admin/generate-avatar")
                .addHeader("Authorization", "Bearer " + adminToken) // AJOUTE CETTE LIGNE
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(AdminActivity.this, "Erreur réseau avatar", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(AdminActivity.this, "Voix envoyée au GPU !", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}