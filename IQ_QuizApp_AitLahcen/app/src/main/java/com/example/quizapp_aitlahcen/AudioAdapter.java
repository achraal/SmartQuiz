package com.example.quizapp_aitlahcen;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.AudioViewHolder> {

    private List<String> audioUrls;
    private OnAudioClickListener listener;

    public interface OnAudioClickListener {
        void onPlayClick(String url, SeekBar seekBar, Button btnSpeed, ImageButton btnPlay);
    }

    public AudioAdapter(List<String> audioUrls, OnAudioClickListener listener) {
        this.audioUrls = audioUrls;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AudioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_audio, parent, false);
        return new AudioViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AudioViewHolder holder, int position) {
        String url = audioUrls.get(position);

        // Extraction et formatage du nom pour l'affichage
        String fileName = url.substring(url.lastIndexOf("/") + 1);
        String cleanName = fileName.replace("_full_session.mp4", "")
                .replace("_", " à ")
                .replace("-", "/");

        holder.tvAudioName.setText("Session du " + cleanName);

        // C'est ICI qu'on envoie tous les éléments à l'Activity
        holder.btnPlay.setOnClickListener(v ->
                listener.onPlayClick(url, holder.seekBar, holder.btnSpeed, holder.btnPlay)
        );
    }

    @Override
    public int getItemCount() {
        return audioUrls.size();
    }

    static class AudioViewHolder extends RecyclerView.ViewHolder {
        TextView tvAudioName;
        ImageButton btnPlay;
        SeekBar seekBar;    // Ajouté
        Button btnSpeed;    // Ajouté

        public AudioViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAudioName = itemView.findViewById(R.id.tvAudioName);
            btnPlay = itemView.findViewById(R.id.btnPlayAudio);
            seekBar = itemView.findViewById(R.id.audioSeekBar); // ID du XML
            btnSpeed = itemView.findViewById(R.id.btnSpeed);    // ID du XML
        }
    }
}