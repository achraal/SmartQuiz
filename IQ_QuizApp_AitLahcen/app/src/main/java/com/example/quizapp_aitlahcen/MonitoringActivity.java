package com.example.quizapp_aitlahcen;

import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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

    // --- PARTIE SESSIONS ET PHOTOS ---

    /*private void loadSessionsAndPhotos() {
        String listUrl = SupabaseConfig.BASE_URL + "/storage/v1/object/list/monitoring";
        String jsonBody = "{\"prefix\": \"" + studentId + "/\"}";

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"), jsonBody);

        Request request = new Request.Builder()
                .url(listUrl)
                .addHeader("apikey", SupabaseConfig.API_KEY)
                .addHeader("Authorization", "Bearer " + adminToken)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    StorageFile[] folders = new Gson().fromJson(response.body().string(), StorageFile[].class);
                    if (folders != null) {
                        runOnUiThread(() -> sessionList.clear());
                        for (StorageFile folder : folders) {
                            // On cherche les dossiers de session (id null)
                            if (folder.id == null && !folder.name.equals(".emptyFolderPlaceholder")) {
                                Session session = new Session(folder.name);
                                runOnUiThread(() -> {
                                    sessionList.add(session);
                                    sessionAdapter.notifyDataSetChanged();
                                });
                                // On charge les photos pour cette session précise
                                fetchPhotosForType(session, "front");
                                fetchPhotosForType(session, "back");
                            }
                        }
                    }
                }
            }
            @Override public void onFailure(Call call, IOException e) { Log.e("STORAGE", "Erreur sessions", e); }
        });
    }*/
    // --- PARTIE CARTE ET GPS ---
    /*private void loadMonitoringData() {
        String url = SupabaseConfig.BASE_URL + "/rest/v1/monitoring?user_id=eq." + studentId + "&select=*";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.API_KEY)
                .addHeader("Authorization", "Bearer " + adminToken)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    MonitoringRecord[] records = new Gson().fromJson(response.body().string(), MonitoringRecord[].class);
                    if (records != null && records.length > 0) {
                        runOnUiThread(() -> {
                            for (MonitoringRecord r : records) {
                                LatLng pos = new LatLng(r.latitude, r.longitude);
                                mMap.addMarker(new MarkerOptions().position(pos).title("Point de passage"));
                            }
                            MonitoringRecord last = records[records.length - 1];
                            tvAddress.setText("Dernière adresse : " + last.address);
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(last.latitude, last.longitude), 15f));
                        });
                    }
                }
            }
            @Override public void onFailure(Call call, IOException e) { Log.e("MAP", "Erreur GPS", e); }
        });
    }*/
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

    /*private void fetchUserAudios() {
        String listUrl = SupabaseConfig.BASE_URL + "/storage/v1/object/list/monitoring";
        String jsonBody = "{\"prefix\": \"" + studentId + "/\"}";

        okhttp3.RequestBody body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), jsonBody);
        Request request = new Request.Builder().url(listUrl).addHeader("apikey", SupabaseConfig.API_KEY).addHeader("Authorization", "Bearer " + adminToken).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    StorageFile[] sessions = new Gson().fromJson(response.body().string(), StorageFile[].class);
                    if (sessions != null) {
                        runOnUiThread(() -> audioUrls.clear());
                        for (StorageFile session : sessions) {
                            if (session.id == null && !session.name.equals(".emptyFolderPlaceholder")) {
                                fetchAudioInSpecificFolder(session.name);
                            }
                        }
                    }
                }
            }
            @Override public void onFailure(Call call, IOException e) { Log.e("AUDIO", "Fail sessions", e); }
        });
    }*/

    /*private void fetchAudioInSpecificFolder(String sessionFolderName) {
        String fullAudioPath = studentId + "/" + sessionFolderName + "/audios/";
        String jsonBody = "{\"prefix\": \"" + fullAudioPath + "\"}";
        okhttp3.RequestBody body = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), jsonBody);
        Request request = new Request.Builder().url(SupabaseConfig.BASE_URL + "/storage/v1/object/list/monitoring").addHeader("apikey", SupabaseConfig.API_KEY).addHeader("Authorization", "Bearer " + adminToken).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    StorageFile[] files = new Gson().fromJson(response.body().string(), StorageFile[].class);
                    if (files != null) {
                        String authBaseUrl = SupabaseConfig.BASE_URL + "/storage/v1/object/authenticated/monitoring/";
                        for (StorageFile file : files) {
                            if (file.id != null && file.name.contains("full_session")) {
                                String finalUrl = authBaseUrl + fullAudioPath + file.name;
                                runOnUiThread(() -> {
                                    if (!audioUrls.contains(finalUrl)) {
                                        audioUrls.add(finalUrl);
                                        audioAdapter.notifyDataSetChanged();
                                    }
                                });
                            }
                        }
                    }
                }
            }
            @Override public void onFailure(Call call, IOException e) { e.printStackTrace(); }
        });
    }*/

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
}