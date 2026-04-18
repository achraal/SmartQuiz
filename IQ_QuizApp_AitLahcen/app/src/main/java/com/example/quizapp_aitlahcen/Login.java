package com.example.quizapp_aitlahcen;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.*;
import java.io.IOException;
import com.google.gson.JsonObject;

public class Login extends AppCompatActivity {
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvGoToRegister;
    private OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmailLogin);
        etPassword = findViewById(R.id.etPasswordLogin);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> handleLogin());
        // 1. Initialisation du lien vers Register
        tvGoToRegister = findViewById(R.id.tvGoToRegister);

        btnLogin.setOnClickListener(v -> handleLogin());

        // 2. Action au clic
        tvGoToRegister.setOnClickListener(v -> {
            Intent intent = new Intent(Login.this, Register.class); // Assurez-vous que votre classe s'appelle bien Register
            startActivity(intent);
        });
    }

    private void handleLogin() {
        String email = etEmail.getText().toString();
        String password = etPassword.getText().toString();

        String json = "{\"email\":\"" + email + "\", \"password\":\"" + password + "\"}";
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        // Endpoint pour se connecter et obtenir un jeton (token)
        Request request = new Request.Builder()
                .url(SupabaseConfig.FASTAPI_URL + "/auth/login") // ON CIBLE FASTAPI
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    JsonObject jsonObject = new com.google.gson.Gson().fromJson(responseData, JsonObject.class);

                    String token = jsonObject.get("access_token").getAsString();
                    JsonObject userObject = jsonObject.get("user").getAsJsonObject();
                    String userId = userObject.get("id").getAsString();
                    String userEmail = userObject.get("email").getAsString(); // On récupère l'email

                    runOnUiThread(() -> {
                        Toast.makeText(Login.this, "Connexion réussie !", Toast.LENGTH_SHORT).show();

                        Intent intent;
                        // Correction de l'aiguillage : Admin vs Étudiant
                        if (userEmail.equalsIgnoreCase("admin@emsi.ma")) {
                            intent = new Intent(Login.this, AdminActivity.class);
                        } else {
                            intent = new Intent(Login.this, WelcomeActivity.class);
                            intent.putExtra("USER_ID", userId); // L'admin n'a pas forcément besoin de son ID pour le quiz
                        }

                        intent.putExtra("TOKEN", token); // On passe le token dans les deux cas
                        startActivity(intent);
                        finish();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(Login.this, "Échec de connexion", Toast.LENGTH_SHORT).show());
                }
            }
            @Override
            public void onFailure(Call call, IOException e) { e.printStackTrace(); }
        });
    }
}