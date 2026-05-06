package com.example.quizapp_aitlahcen;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class FraudSummaryAdapter extends RecyclerView.Adapter<FraudSummaryAdapter.ViewHolder> {

    private List<FraudSummary> fullList;
    private List<FraudLog> displayLogs = new ArrayList<>();
    private boolean isDetailMode = false;

    public FraudSummaryAdapter(List<FraudSummary> summaries) {
        this.fullList = summaries;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fraud_summary, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (!isDetailMode) {
            // MODE RÉSUMÉ : Liste des étudiants
            final FraudSummary item = fullList.get(position);
            holder.tvUsername.setText(item.username);
            holder.tvAlertCount.setText(item.log_count + " alerte(s) détectée(s)");
            holder.btnViewMore.setText("Voir >");

            // On passe la vue 'v' pour pouvoir accéder au contexte et au titre
            holder.itemView.setOnClickListener(v -> showUserDetails(item, v));
        } else {
            // MODE DÉTAIL : Liste des logs d'un étudiant spécifique
            FraudLog log = displayLogs.get(position);
            holder.tvUsername.setText(log.type.toUpperCase());
            // On affiche la raison et la date
            holder.tvAlertCount.setText(log.reason + "\n" + log.created_at);
            holder.btnViewMore.setText("");

            holder.itemView.setOnClickListener(null);
        }
    }

    private void showUserDetails(FraudSummary item, View v) {
        this.displayLogs = item.logs;
        this.isDetailMode = true;

        // Mise à jour du titre dans l'activité via le contexte de la vue cliquée
        if (v.getContext() instanceof FraudLogsActivity) {
            TextView title = ((FraudLogsActivity) v.getContext()).findViewById(R.id.tvTitle);
            if (title != null) {
                title.setText("Fraudes : " + item.username);
            }
        }

        notifyDataSetChanged();
    }

    public boolean goBack() {
        if (isDetailMode) {
            isDetailMode = false;
            displayLogs.clear();
            notifyDataSetChanged();
            return true;
        }
        return false;
    }

    @Override
    public int getItemCount() {
        return isDetailMode ? displayLogs.size() : (fullList != null ? fullList.size() : 0);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUsername, tvAlertCount, btnViewMore;
        ViewHolder(View v) {
            super(v);
            tvUsername = v.findViewById(R.id.tvUsername);
            tvAlertCount = v.findViewById(R.id.tvAlertCount);
            btnViewMore = v.findViewById(R.id.btnViewMore);
        }
    }
}