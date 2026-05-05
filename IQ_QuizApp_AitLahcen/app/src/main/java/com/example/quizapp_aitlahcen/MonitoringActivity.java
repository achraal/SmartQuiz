package com.example.quizapp_aitlahcen;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MonitoringActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private String studentId, studentName, adminToken;
    private TextView tvStudentTitle, tvAddress;

    // Système de Sessions (Photos)
    private RecyclerView rvSessions;
    private SessionAdapter sessionAdapter;
    private List<Session> sessionList = new ArrayList<>();

    // Système Audio
    private RecyclerView rvAudios;
    private AudioAdapter audioAdapter;
    private List<String> audioUrls = new ArrayList<>();

    private OkHttpClient client = new OkHttpClient();
    private MediaPlayer mediaPlayer;
    private android.os.Handler seekHandler = new android.os.Handler();
    private float currentSpeed = 1.0f;
    private String currentPlayingUrl = "";
    private TextView tvFraudReason, tvFraudDate, tvFraudStatus;
    private CardView cardFraud;
    Button btnResetFraud;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitoring);

        studentId = getIntent().getStringExtra("STUDENT_ID");
        studentName = getIntent().getStringExtra("STUDENT_NAME");
        adminToken = getIntent().getStringExtra("TOKEN");

        tvStudentTitle = findViewById(R.id.tvStudentTitle);
        tvAddress = findViewById(R.id.tvAddress);
        tvStudentTitle.setText("Suivi de : " + studentName);

        rvSessions = findViewById(R.id.rvPhotos);
        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        sessionAdapter = new SessionAdapter(sessionList, adminToken);
        rvSessions.setAdapter(sessionAdapter);

        rvAudios = findViewById(R.id.rvAudios);
        rvAudios.setLayoutManager(new LinearLayoutManager(this));

        cardFraud = findViewById(R.id.cardFraud);
        tvFraudReason = findViewById(R.id.tvFraudReason); // Ajoute cette ligne car tu as déjà tvFraudDate
        tvFraudDate = findViewById(R.id.tvFraudDate);
        tvFraudStatus = findViewById(R.id.tvFraudStatus);
        btnResetFraud = findViewById(R.id.btnResetFraud);

        btnResetFraud.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Autoriser le repassage")
                    .setMessage("Voulez-vous vraiment effacer l'historique de fraude pour " + studentName + " ?")
                    .setPositiveButton("Oui, réinitialiser", (dialog, which) -> resetFraudStatus(studentId))
                    .setNegativeButton("Annuler", null)
                    .setIcon(android.R.drawable.ic_dialog_alert) // Optionnel : ajoute une petite icône
                    .show();
        });

        // Callback vers playAudio
        audioAdapter = new AudioAdapter(audioUrls, (url, seekBar, btnSpeed, btnPlay) -> {
            playAudio(url, seekBar, btnSpeed, btnPlay);
        });
        rvAudios.setAdapter(audioAdapter);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        loadAllData();

        //fetchUserAudios();
    }
    private void loadAllData() {
        String url = SupabaseConfig.FASTAPI_URL + "/admin/monitoring/" + studentId;
        Log.d("DEBUG_API", "Appel FastAPI : " + url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + adminToken)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d("DEBUG_API", "Code réponse HTTP : " + response.code());

                if (response.isSuccessful()) {
                    String json = response.body().string();
                    Log.d("DEBUG_API", "JSON brut reçu : " + json);

                    // Tentative de parsing GSON
                    MonitoringFullResponse data = new Gson().fromJson(json, MonitoringFullResponse.class);

                    runOnUiThread(() -> {
                        if (data == null) {
                            Log.e("DEBUG_API", "ERREUR : data est null après parsing GSON !");
                            return;
                        }

                        // 1. Update GPS
                        if (data.gps_points != null) {
                            Log.d("DEBUG_API", "Points GPS trouvés : " + data.gps_points.size());
                            updateMap(data.gps_points);
                        } else {
                            Log.w("DEBUG_API", "Aucun point GPS dans la réponse.");
                        }

                        // 2. Update Sessions
                        sessionList.clear();
                        audioUrls.clear();

                        if (data.sessions != null) {
                            Log.d("DEBUG_API", "Nombre de sessions reçues : " + data.sessions.size());

                            for (SessionData s : data.sessions) {
                                Session session = new Session(s.date);

                                // Vérification précise des photos front
                                if (s.frontPhotos != null) {
                                    session.frontPhotos.addAll(s.frontPhotos);
                                    Log.d("DEBUG_API", "Session " + s.date + " : " + s.frontPhotos.size() + " photos front");
                                } else {
                                    Log.w("DEBUG_API", "Session " + s.date + " : frontPhotos est NULL");
                                }

                                // Vérification précise des photos back
                                if (s.backPhotos != null) {
                                    session.backPhotos.addAll(s.backPhotos);
                                    Log.d("DEBUG_API", "Session " + s.date + " : " + s.backPhotos.size() + " photos back");
                                }

                                sessionList.add(session);

                                // Vérification des audios
                                if (s.audios != null) {
                                    audioUrls.addAll(s.audios);
                                    Log.d("DEBUG_API", "Session " + s.date + " : " + s.audios.size() + " audios ajoutés");
                                }
                            }
                        } else {
                            Log.e("DEBUG_API", "La liste 'sessions' est NULL dans l'objet data.");
                        }

                        Log.d("DEBUG_FRAUDE", "is_cheating: " + (data.student_info != null ? data.student_info.is_cheating : "null"));

                        // Affichage des détails de fraude dans l'en-tête ou une section dédiée
                        if (data.student_info != null && data.student_info.is_cheating) {
                            // --- ÉTAT : FRAUDE DÉTECTÉE ---
                            cardFraud.setCardBackgroundColor(Color.parseColor("#FEEBEE")); // Rouge clair
                            tvFraudStatus.setText("⚠️ FRAUDE DÉTECTÉE");
                            tvFraudStatus.setTextColor(Color.parseColor("#C62828"));

                            tvFraudReason.setVisibility(View.VISIBLE);
                            tvFraudReason.setText("Raison : " + data.student_info.fraud_reason);
                            tvFraudReason.setTextColor(Color.parseColor("#C62828"));

                            tvFraudDate.setVisibility(View.VISIBLE);
                            tvFraudDate.setText("Détecté le : " + data.student_info.last_fraud_detected_at);
                            tvFraudDate.setTextColor(Color.parseColor("#C62828"));

                        } else {
                            // --- ÉTAT : AUCUNE FRAUDE (ou réinitialisé) ---
                            cardFraud.setCardBackgroundColor(Color.parseColor("#E8F5E9")); // Vert clair
                            tvFraudStatus.setText("✅ AUCUNE FRAUDE DÉTECTÉE");
                            tvFraudStatus.setTextColor(Color.parseColor("#2E7D32"));

                            // On cache les détails inutiles en cas de succès
                            tvFraudReason.setVisibility(View.GONE);
                            tvFraudDate.setVisibility(View.GONE);
                        }

                        // Notification des changements
                        Log.d("DEBUG_API", "Mise à jour des adapters (Sessions: " + sessionList.size() + ")");
                        sessionAdapter.notifyDataSetChanged();
                        audioAdapter.notifyDataSetChanged();
                    });
                } else {
                    Log.e("DEBUG_API", "Réponse KO : " + response.message());
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("DEBUG_API", "Échec critique de l'appel réseau : " + e.getMessage());
            }
        });
    }
    private void updateMap(List<MonitoringRecord> records) {
        if (records == null || records.isEmpty()) return;
        mMap.clear();
        for (MonitoringRecord r : records) {
            LatLng pos = new LatLng(r.latitude, r.longitude);
            mMap.addMarker(new MarkerOptions().position(pos).title("Point de passage"));
        }
        MonitoringRecord last = records.get(records.size() - 1);
        tvAddress.setText("Dernière adresse : " + last.address);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(last.latitude, last.longitude), 15f));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    // --- PARTIE AUDIO ---

    public void playAudio(String url, SeekBar seekBar, Button btnSpeed, ImageButton btnPlay) {
        // 1. GESTION PAUSE/REPRISE (Si c'est le même audio)
        if (url.equals(currentPlayingUrl) && mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlay.setImageResource(android.R.drawable.ic_media_play);
            } else {
                mediaPlayer.start();
                btnPlay.setImageResource(android.R.drawable.ic_media_pause);
                updateSeekBar(seekBar);
            }
            return;
        }
        btnSpeed.setOnClickListener(v -> {
            // Cycle de vitesse : 1.0 -> 1.5 -> 2.0 -> 0.5 -> 1.0
            if (currentSpeed == 1.0f) {
                currentSpeed = 1.5f;
            } else if (currentSpeed == 1.5f) {
                currentSpeed = 2.0f;
            } else if (currentSpeed == 2.0f) {
                currentSpeed = 0.5f;
            } else {
                currentSpeed = 1.0f;
            }

            // 1. Mettre à jour le texte du bouton
            btnSpeed.setText(currentSpeed + "x");

            // 2. Appliquer la vitesse au MediaPlayer en temps réel
            applySpeed(currentSpeed);
        });

        // 2. NOUVEL AUDIO (ou changement de piste)
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
            seekHandler.removeCallbacksAndMessages(null);
        }

        currentPlayingUrl = url; // On enregistre l'URL actuelle
        mediaPlayer = new MediaPlayer();

        // Configurer le bouton de vitesse IMMÉDIATEMENT
        btnSpeed.setOnClickListener(v -> {
            if (currentSpeed == 1.0f) currentSpeed = 1.5f;
            else if (currentSpeed == 1.5f) currentSpeed = 2.0f;
            else if (currentSpeed == 2.0f) currentSpeed = 0.5f;
            else currentSpeed = 1.0f;

            btnSpeed.setText(currentSpeed + "x");
            setAudioSpeed(currentSpeed); // Applique le changement en temps réel
        });

        try {
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("apikey", SupabaseConfig.API_KEY);
            headers.put("Authorization", "Bearer " + adminToken);

            mediaPlayer.setDataSource(this, android.net.Uri.parse(url), headers);
            mediaPlayer.prepareAsync();

            mediaPlayer.setOnPreparedListener(mp -> {
                // Appliquer la vitesse enregistrée au lancement
                applySpeed(currentSpeed);
                btnSpeed.setText(currentSpeed + "x");
                //setAudioSpeed(currentSpeed);
                btnSpeed.setText(currentSpeed + "x");

                seekBar.setMax(mp.getDuration());
                mp.start();
                btnPlay.setImageResource(android.R.drawable.ic_media_pause);
                updateSeekBar(seekBar);
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlay.setImageResource(android.R.drawable.ic_media_play);
                seekBar.setProgress(0);
                currentPlayingUrl = ""; // Reset quand c'est fini
                seekHandler.removeCallbacksAndMessages(null);
            });

            // SeekBar listener
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(progress);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

        } catch (IOException e) {
            Log.e("AUDIO", "Erreur de lecture", e);
            Toast.makeText(this, "Erreur lors du chargement de l'audio", Toast.LENGTH_SHORT).show();
        }
    }

    private void setAudioSpeed(float speed) {
        if (mediaPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
            } catch (Exception e) { Log.e("AUDIO", "Speed error"); }
        }
    }

    private void updateSeekBar(SeekBar seekBar) {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            seekBar.setProgress(mediaPlayer.getCurrentPosition());
            seekHandler.postDelayed(() -> updateSeekBar(seekBar), 100);
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        seekHandler.removeCallbacksAndMessages(null);
    }
    private void applySpeed(float speed) {
        if (mediaPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // On récupère les paramètres actuels, on change la vitesse, et on réinjecte
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
            } catch (Exception e) {
                Log.e("AUDIO", "Erreur lors du changement de vitesse : " + e.getMessage());
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.w("AUDIO", "Le changement de vitesse nécessite Android 6.0 (Marshmallow) ou plus.");
        }
    }
    private void resetFraudStatus(String studentId) {
        // 1. URL de ton endpoint FastAPI (celui qu'on a créé ensemble)
        String url = SupabaseConfig.FASTAPI_URL + "/admin/reset-fraud/" + studentId;

        // 2. Préparation de la requête POST (ou PATCH selon ton backend)
        // On utilise un RequestBody vide car les paramètres sont dans l'URL
        okhttp3.RequestBody emptyBody = okhttp3.RequestBody.create(null, new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + adminToken)
                .post(emptyBody)
                .build();

        Log.d("DEBUG_FRAUDE", "Tentative de réinitialisation pour : " + studentId);

        // 3. Appel asynchrone
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        // --- MISE À JOUR VISUELLE IMMÉDIATE ---
                        // On repasse la carte en mode "Succès"
                        cardFraud.setCardBackgroundColor(Color.parseColor("#E8F5E9")); // Vert clair
                        tvFraudStatus.setText("✅ ACCÈS RÉTABLI (Repassage autorisé)");
                        tvFraudStatus.setTextColor(Color.parseColor("#2E7D32"));

                        // On cache les détails de l'ancienne fraude
                        tvFraudReason.setVisibility(View.GONE);
                        tvFraudDate.setVisibility(View.GONE);

                        // On désactive le bouton pour éviter les doubles clics
                        btnResetFraud.setEnabled(false);
                        btnResetFraud.setAlpha(0.5f);

                        Toast.makeText(MonitoringActivity.this,
                                "L'étudiant peut maintenant repasser son quiz.",
                                Toast.LENGTH_LONG).show();
                    });
                } else {
                    Log.e("DEBUG_FRAUDE", "Erreur serveur : " + response.code());
                    runOnUiThread(() ->
                            Toast.makeText(MonitoringActivity.this, "Erreur lors de la réinitialisation", Toast.LENGTH_SHORT).show()
                    );
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("DEBUG_FRAUDE", "Échec réseau : " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(MonitoringActivity.this, "Connexion impossible au serveur", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
}