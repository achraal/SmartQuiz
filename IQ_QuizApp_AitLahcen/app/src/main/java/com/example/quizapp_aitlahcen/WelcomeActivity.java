package com.example.quizapp_aitlahcen;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    private String userToken, userId;
    private Button btnLogout, btnStartQuiz;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Récupération des infos de session
        userToken = getIntent().getStringExtra("TOKEN");
        userId = getIntent().getStringExtra("USER_ID");
        btnLogout = findViewById(R.id.btnLogout);

        btnStartQuiz = findViewById(R.id.btnStartQuiz);

        btnStartQuiz.setOnClickListener(v -> {
            // MODIFICATION : On ne lance plus QuizActivity directement.
            // On lance d'abord CameraCheckActivity pour valider l'environnement.
            Intent intent = new Intent(WelcomeActivity.this, CameraCheckActivity.class);

            // On transmet les informations de session indispensables
            intent.putExtra("TOKEN", userToken);
            intent.putExtra("USER_ID", userId);

            startActivity(intent);
        });
        btnLogout.setOnClickListener(v -> {
            // 1. Créer l'Intent vers Login
            Intent intent = new Intent(WelcomeActivity.this, Login.class);

            // 2. Nettoyer TOUTE la pile d'activités (Important pour la sécurité)
            // FLAG_ACTIVITY_NEW_TASK et FLAG_ACTIVITY_CLEAR_TASK effacent l'historique
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(intent);

            // 3. Fermer l'activité actuelle
            finish();
        });
    }
}