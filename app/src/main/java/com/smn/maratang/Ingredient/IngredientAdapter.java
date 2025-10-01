package com.smn.maratang.Ingredient;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smn.maratang.R;

import java.util.List;

public class IngredientAdapter extends RecyclerView.Adapter<IngredientAdapter.ViewHolder> {
    private List<IngredientItem> ingredientList;

    public IngredientAdapter(List<IngredientItem> ingredientList) {
        this.ingredientList = ingredientList;
    }

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

        if (position == 0) {
            Drawable bg = holder.itm_ingredient_container.getBackground();

            if (bg != null) {
                bg = bg.mutate();
                DrawableCompat.setTint(
                    bg,
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.semiprimary_red)
                );
                holder.itm_ingredient_container.setBackground(bg);
            }
            holder.itm_ingredient_name.setTextColor(
                ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_red)
            );
        } else {
            Drawable bg = holder.itm_ingredient_container.getBackground();

            if (bg != null) {
                bg = bg.mutate();
                DrawableCompat.setTintList(bg, null);
                holder.itm_ingredient_container.setBackground(bg);
            }

            holder.itm_ingredient_name.setTextColor(
                ContextCompat.getColor(holder.itemView.getContext(), R.color.white)
            );
        }
    }

    @Override
    public int getItemCount() {
        return ingredientList != null ? ingredientList.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView itm_ingredient_name;
        FrameLayout itm_ingredient_container;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            itm_ingredient_name = itemView.findViewById(R.id.itm_ingredient_name);
            itm_ingredient_container = itemView.findViewById(R.id.itm_ingredient_container);
        }
    }
}
