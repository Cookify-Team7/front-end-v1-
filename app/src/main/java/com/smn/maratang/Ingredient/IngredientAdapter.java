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
        String amountText = "";
        if (item.getCount() != null && !item.getCount().isEmpty()) amountText += item.getCount();
        if (item.getUnit() != null && !item.getUnit().isEmpty()) amountText += (amountText.isEmpty()? "" : " ") + item.getUnit();
        holder.itm_ingredient_amount.setText(amountText);
        // 동적 색상/배경 틴트 적용 제거: 레이아웃의 기본 스타일만 사용
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
