package com.example.quizapp_aitlahcen;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FraudLogsActivity extends AppCompatActivity {

    private RecyclerView rvFraudLogs;
    private FraudSummaryAdapter adapter;
    private List<FraudSummary> summaryList = new ArrayList<>();
    private String adminToken;
    private OkHttpClient client = new OkHttpClient();
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fraud_logs);

        adminToken = getIntent().getStringExtra("TOKEN");

        rvFraudLogs = findViewById(R.id.rvFraudLogs);
        rvFraudLogs.setLayoutManager(new LinearLayoutManager(this));

        adapter = new FraudSummaryAdapter(summaryList);
        rvFraudLogs.setAdapter(adapter);

        btnBack = findViewById(R.id.btnBack);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // On demande à l'adapter de revenir en arrière
                if (adapter.goBack()) {
                    // Si l'adapter a réussi à revenir (il était en mode détail)
                    // On remet le titre à jour
                    TextView tvTitle = findViewById(R.id.tvTitle);
                    tvTitle.setText("Tableau de Fraude");
                } else {
                    // Si l'adapter était déjà sur la liste principale,
                    // on désactive ce callback et on déclenche le retour standard pour quitter
                    this.setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        loadLogs();
    }

    private void loadLogs() {
        // Appelle le nouvel endpoint qui groupe par utilisateur
        String url = SupabaseConfig.FASTAPI_URL + "/admin/fraud-summary";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + adminToken)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("FraudLogs", "Erreur réseau", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    // On parse maintenant un tableau de FraudSummary
                    FraudSummary[] summaries = new Gson().fromJson(json, FraudSummary[].class);

                    runOnUiThread(() -> {
                        if (summaries != null) {
                            summaryList.clear();
                            summaryList.addAll(Arrays.asList(summaries));
                            adapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        });
    }
}