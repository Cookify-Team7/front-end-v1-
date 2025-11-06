package com.smn.maratang.Ingredient;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smn.maratang.R;

import java.util.List;

public class IngredientAdapter extends RecyclerView.Adapter<IngredientAdapter.ViewHolder> {
    private final List<IngredientItem> ingredientList;
    public interface OnItemClickListener { void onItemClick(int position, IngredientItem item); }
    private OnItemClickListener clickListener;
    private boolean editMode = false;

    public IngredientAdapter(List<IngredientItem> ingredientList) {
        this.ingredientList = ingredientList;
    }

    public void setOnItemClickListener(OnItemClickListener l){ this.clickListener = l; }
    public void setEditMode(boolean enabled){ this.editMode = enabled; notifyDataSetChanged(); }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.itm_ingredient, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IngredientItem item = ingredientList.get(position);
        holder.itm_ingredient_name.setText(item.getName());
        String amountText = "";
        if (item.getCount() != null && !item.getCount().isEmpty()) amountText += item.getCount();
        if (item.getUnit() != null && !item.getUnit().isEmpty()) amountText += (amountText.isEmpty()? "" : " ") + item.getUnit();
        holder.itm_ingredient_amount.setText(amountText);
        // 편집 모드 시 약간의 시각적 힌트(투명도)와 클릭 가능
        holder.itemView.setAlpha(editMode ? 0.92f : 1f);
        if (editMode && clickListener != null) {
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    clickListener.onItemClick(pos, ingredientList.get(pos));
                }
            });
        } else {
            holder.itemView.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return ingredientList != null ? ingredientList.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView itm_ingredient_name;
        TextView itm_ingredient_amount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            itm_ingredient_name = itemView.findViewById(R.id.itm_ingredient_name);
            itm_ingredient_amount = itemView.findViewById(R.id.itm_ingredient_amount);
        }
    }
}
