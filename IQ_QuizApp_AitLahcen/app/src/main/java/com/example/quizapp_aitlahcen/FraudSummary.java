package com.example.quizapp_aitlahcen;

import java.util.List;

public class FraudSummary {
    public String user_id;
    public String username;
    public int log_count;
    public String last_fraud;
    public List<FraudLog> logs; // La liste des détails pour cet utilisateur
}
