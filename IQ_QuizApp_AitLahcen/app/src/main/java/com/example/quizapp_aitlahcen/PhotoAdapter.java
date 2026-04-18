package com.example.quizapp_aitlahcen;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.List;
public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.ViewHolder> {
    private List<String> photoUrls;
    private String adminToken;

    // UN SEUL CONSTRUCTEUR : On force le passage du token
    public PhotoAdapter(List<String> photoUrls, String adminToken) {
        this.photoUrls = photoUrls;
        this.adminToken = adminToken;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Assure-toi que R.layout.item_photo existe bien
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String url = photoUrls.get(position);

        // Sécurité : Si l'URL ou le token est nul, on évite de lancer Glide
        if (url == null || adminToken == null) return;

        GlideUrl secureUrl = new GlideUrl(url, new LazyHeaders.Builder()
                .addHeader("apikey", SupabaseConfig.API_KEY)
                .addHeader("Authorization", "Bearer " + adminToken)
                .build());

        Glide.with(holder.itemView.getContext())
                .load(secureUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(400, 400) // 400px est souvent le "sweet spot" pour une galerie fluide
                .thumbnail(0.2f) // Légèrement plus haut pour que la prévisualisation soit moins floue
                .centerCrop() // Force un affichage propre dans le carré
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.stat_notify_error)
                .into(holder.ivPhoto);
    }

    @Override
    public int getItemCount() {
        return photoUrls != null ? photoUrls.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Vérifie bien que l'ID dans ton XML est ivPhotoItem
            ivPhoto = itemView.findViewById(R.id.ivPhotoItem);
        }
    }
}