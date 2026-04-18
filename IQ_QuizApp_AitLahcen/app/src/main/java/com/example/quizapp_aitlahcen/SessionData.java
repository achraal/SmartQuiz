package com.example.quizapp_aitlahcen;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class SessionData {
    public String date;

    @SerializedName("front_photos")
    public List<String> frontPhotos;

    @SerializedName("back_photos")
    public List<String> backPhotos;

    public List<String> audios; // Ici pas besoin si FastAPI envoie déjà "audios"
}