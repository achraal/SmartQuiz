package com.example.quizapp_aitlahcen;

public class SupabaseConfig {
    //public static final String API_KEY = "sb_publishable_7XTv6A9AJVhpjHSEss1d1w_ZVANsqm3";
    public static final String BASE_URL = BuildConfig.SUPABASE_URL;
    public static final String API_KEY = BuildConfig.SUPABASE_KEY;
    public static final String FASTAPI_URL = BuildConfig.FASTAPI_URL;

    // Headers obligatoires pour chaque appel
    public static final String AUTH_HEADER = "Authorization";
    public static final String API_KEY_HEADER = "apikey";
}
