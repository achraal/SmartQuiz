package com.example.quizapp_aitlahcen;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    private List<Session> sessions;
    private String adminToken;

    public SessionAdapter(List<Session> sessions, String adminToken) {
        this.sessions = sessions;
        this.adminToken = adminToken;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        Session session = sessions.get(position);

        // 1. Formatage du titre (Ex: 2026-04-18_12-36 -> Session du 18/04/2026 à 12:36)
        String cleanName = session.date.replace("_", " à ").replace("-", "/");
        holder.tvSessionDate.setText("Session du " + cleanName);

        // 2. Configuration du RecyclerView pour les photos FRONT (Caméra)
        holder.rvFrontPhotos.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
        PhotoAdapter frontAdapter = new PhotoAdapter(session.frontPhotos, adminToken);
        holder.rvFrontPhotos.setAdapter(frontAdapter);

        // 3. Configuration du RecyclerView pour les photos BACK (Captures d'écran)
        holder.rvBackPhotos.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
        PhotoAdapter backAdapter = new PhotoAdapter(session.backPhotos, adminToken);
        holder.rvBackPhotos.setAdapter(backAdapter);

        // 4. Affichage des compteurs
        holder.tvFrontCount.setText("Caméra Frontale (" + session.frontPhotos.size() + ")");
        holder.tvBackCount.setText("Captures d'écran (" + session.backPhotos.size() + ")");
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    public static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView tvSessionDate, tvFrontCount, tvBackCount;
        RecyclerView rvFrontPhotos, rvBackPhotos;

        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSessionDate = itemView.findViewById(R.id.tvSessionDate);
            tvFrontCount = itemView.findViewById(R.id.tvFrontCount);
            tvBackCount = itemView.findViewById(R.id.tvBackCount);
            rvFrontPhotos = itemView.findViewById(R.id.rvFrontPhotos);
            rvBackPhotos = itemView.findViewById(R.id.rvBackPhotos);
        }
    }
}