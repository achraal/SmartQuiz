package com.example.quizapp_aitlahcen;

import java.util.ArrayList;
import java.util.List;

public class Session {
    public String date;
    public List<String> frontPhotos = new ArrayList<>();
    public List<String> backPhotos = new ArrayList<>();
    public List<String> audios = new ArrayList<>();

    public Session(String date) {
        this.date = date;
    }
}