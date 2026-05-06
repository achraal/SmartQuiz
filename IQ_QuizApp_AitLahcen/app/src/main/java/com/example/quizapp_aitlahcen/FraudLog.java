package com.example.quizapp_aitlahcen;

public class FraudLog {
    public String id;
    public String type;
    public String reason;
    public String created_at;
    public Profile profiles; // Correspond à la jointure profiles(username)

    public class Profile {
        public String username;
    }
}