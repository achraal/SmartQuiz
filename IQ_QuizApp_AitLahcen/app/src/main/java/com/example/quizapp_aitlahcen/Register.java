package com.example.quizapp_aitlahcen;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.*;
import java.io.IOException;
import com.google.gson.JsonObject;

public class Register extends AppCompatActivity {

    private EditText etUsername, etEmail, etPassword;
    private Button btnRegister;
    private OkHttpClient client = new OkHttpClient();
    private TextView tvBackToLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
        btnRegister.setOnClickListener(v -> handleRegister());

        // 3. Ajouter le clic pour retourner au Login
        tvBackToLogin.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(Register.this, Login.class);
            startActivity(intent);
            finish(); // Optionnel : ferme l'écran d'inscription
        });
    }

    private void handleRegister() {
        String email = etEmail.getText().toString();
        String password = etPassword.getText().toString();
        String username = etUsername.getText().toString();

        // 1. On prépare le JSON pour Supabase Auth
        // On inclut le username dans "data" pour que le Trigger SQL (qu'on a vu avant) le récupère
        String json = "{"
                + "\"email\":\"" + email + "\","
                + "\"password\":\"" + password + "\","
                + "\"data\": {\"username\":\"" + username + "\"}"
                + "}";

        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        // 2. Requête vers l'endpoint d'inscription de Supabase
        Request request = new Request.Builder()
                .url(SupabaseConfig.FASTAPI_URL + "/auth/register") // ON CIBLE FASTAPI
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(Register.this, "Erreur réseau", Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(Register.this, "Inscription réussie !", Toast.LENGTH_LONG).show();

                        // 1. On crée l'Intent pour aller vers LoginActivity
                        android.content.Intent intent = new android.content.Intent(Register.this, Login.class);

                        // 2. On lance l'activité
                        startActivity(intent);

                        // 3. On ferme l'activité Register pour ne pas revenir dessus avec le bouton "Retour"
                        finish();
                    });
                } else {
                    String errorBody = response.body().string();
                    runOnUiThread(() -> Toast.makeText(Register.this, "Erreur : " + errorBody, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}