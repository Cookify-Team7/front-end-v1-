package com.smn.maratang.Dangerouses;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smn.maratang.R;

import java.util.List;

public class DangerAdapter extends RecyclerView.Adapter<DangerAdapter.ViewHolder> {
    private List<DangerItem> items;

    public DangerAdapter(List<DangerItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.itm_danger, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DangerItem item = items.get(position);
        holder.tvTitle.setText(item.getTitle());           // 연기 감지 등
        holder.tvTimestamp.setText(item.getTimestamp());   // ex. "2분 전"
        holder.tvRiskLevel.setText(item.getRiskLevel());   // Low/Medium/High
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvTimestamp;
        TextView tvRiskLevel;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle      = itemView.findViewById(R.id.itm_dangerouses_title);
            tvTimestamp  = itemView.findViewById(R.id.itm_dangerouses_time);
            tvRiskLevel  = itemView.findViewById(R.id.itm_dangerouses_level);
        }
    }
}
