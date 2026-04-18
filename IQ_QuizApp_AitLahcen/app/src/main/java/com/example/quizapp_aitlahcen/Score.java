package com.example.quizapp_aitlahcen;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class Score extends AppCompatActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private LatLng pendingLatLng;
    private TextView tvFinalScore, tvIqComment, tvLocationDisplay;
    private Button btnBackToMenu;

    private String userId, userToken;
    private OkHttpClient client = new OkHttpClient();
    private FusedLocationProviderClient fusedLocationClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_score);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvFinalScore = findViewById(R.id.tvFinalScore);
        tvIqComment = findViewById(R.id.tvIqComment);
        tvLocationDisplay = findViewById(R.id.tvLocationDisplay);
        btnBackToMenu = findViewById(R.id.btnBackToMenu);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        int scoreObtenu = getIntent().getIntExtra("FINAL_SCORE", 0);
        userId = getIntent().getStringExtra("USER_ID");
        userToken = getIntent().getStringExtra("TOKEN");

        tvFinalScore.setText(scoreObtenu + " / 30");

        // UI : Couleurs et commentaires
        updateCommentUI(scoreObtenu);

        // --- ÉTAPE CLÉ : Récupérer la localisation et l'envoyer ---
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fetchAndHandleLocation();

        // Mise à jour du profil (Best Score)
        if (userId != null && userToken != null) {
            updateScoreInSupabase(scoreObtenu);
        }

        btnBackToMenu.setOnClickListener(v -> {
            // On crée l'Intent vers ton activité de bienvenue (ex: WelcomeActivity)
            Intent intent = new Intent(Score.this, WelcomeActivity.class);

            // TRÈS IMPORTANT : On nettoie la pile d'activités pour éviter que l'utilisateur
            // ne revienne sur la page de score en appuyant sur le bouton "Retour" du téléphone.
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            // Si tu veux garder l'utilisateur connecté, n'oublie pas de repasser le token
            intent.putExtra("TOKEN", userToken);
            intent.putExtra("USER_ID", userId);

            startActivity(intent);
            finish(); // Ferme l'activité Score
        });
    }

    private void updateCommentUI(int score) {
        if (score >= 25) {
            tvIqComment.setText("Niveau Génie !");
            tvIqComment.setTextColor(android.graphics.Color.parseColor("#10B981"));
        } else if (score >= 15) {
            tvIqComment.setText("Très bon score !");
            tvIqComment.setTextColor(android.graphics.Color.parseColor("#4F46E5"));
        } else {
            tvIqComment.setText("Continue à t'entraîner !");
            tvIqComment.setTextColor(android.graphics.Color.parseColor("#EF4444"));
        }
    }

    private void fetchAndHandleLocation() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    // On utilise la méthode qui fait le Geocoding ET la sauvegarde
                    displayAndSaveFinalLocation(location.getLatitude(), location.getLongitude());
                } else {
                    tvLocationDisplay.setText("Position : Non détectée");
                }
            });
        }
    }

    private void displayAndSaveFinalLocation(double lat, double lon) {
        LatLng userPos = new LatLng(lat, lon);
        this.pendingLatLng = userPos; // On sauvegarde pour onMapReady

        // Mise à jour de la Carte
        runOnUiThread(() -> {
            if (mMap != null) {
                updateMapWithPosition(userPos);
            }
        });

        new Thread(() -> {
            try {
                android.location.Geocoder geocoder = new android.location.Geocoder(this, Locale.getDefault());
                List<android.location.Address> addresses = geocoder.getFromLocation(lat, lon, 1);

                String addressStr;
                if (addresses != null && !addresses.isEmpty()) {
                    // IMPORTANT : Nettoyer l'adresse (enlever les retours à la ligne)
                    addressStr = addresses.get(0).getAddressLine(0).replace("\n", " ");
                } else {
                    addressStr = "Lat: " + lat + ", Lon: " + lon;
                }

                runOnUiThread(() -> tvLocationDisplay.setText(addressStr));
                sendFinalMonitoring(lat, lon, addressStr);

            } catch (IOException e) {
                sendFinalMonitoring(lat, lon, "Erreur Geocode");
            }
        }).start();
    }

    private void sendFinalMonitoring(double lat, double lon, String address) {
        if (userId == null) {
            android.util.Log.e("DEBUG", "UserId est null, abandon de l'envoi monitoring");
            return;
        }
        String url = SupabaseConfig.FASTAPI_URL + "/monitoring/location";
        String json = "{"
                + "\"user_id\":\"" + userId + "\","
                + "\"latitude\":" + lat + ","
                + "\"longitude\":" + lon + ","
                + "\"address\":\"" + address.replace("\"", "\\\"") + "\""
                + "}";

        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { e.printStackTrace(); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // Succès silencieux en base
            }
        });
    }

    private void updateScoreInSupabase(int score) {
        String url = SupabaseConfig.FASTAPI_URL + "/quiz/submit";
        String json = "{\"user_id\":\"" + userId + "\", \"score\":" + score + "}";
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { e.printStackTrace(); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(Score.this, "Score synchronisé !", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
    // Méthode helper pour la map
    private void updateMapWithPosition(LatLng pos) {
        mMap.clear(); // Évite de doubler les marqueurs
        mMap.addMarker(new MarkerOptions().position(pos).title("Votre position"));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);

        // Si la position a été trouvée AVANT que la map ne soit prête
        if (pendingLatLng != null) {
            updateMapWithPosition(pendingLatLng);
        }
    }
}