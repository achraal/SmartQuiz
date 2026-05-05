package com.example.quizapp_aitlahcen;

import java.util.List;

// Crée ce fichier : MonitoringDataResponse.java
public class MonitoringFullResponse {
    public List<MonitoringRecord> gps_points;
    public List<SessionData> sessions;

    public StudentInfo student_info;

    public static class StudentInfo {
        public boolean is_cheating;
        public String fraud_reason;
        public String last_fraud_detected_at;
    }
}