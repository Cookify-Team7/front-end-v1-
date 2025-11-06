package com.smn.maratang.recipes;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.smn.maratang.R;

import java.util.List;

public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.VH> {
    private final List<RecipeItem> items;

    public RecipeAdapter(List<RecipeItem> items) {
        this.items = items;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.itm_recipes, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        RecipeItem it = items.get(pos);
        h.tvTitle.setText(it.title);
        h.tvSteps.setText(h.itemView.getContext().getString(R.string.steps_format, it.stepsCount));
        h.tvTime.setText(it.time);
        // 이미지 로딩: URL이 비어있지 않을 때만 시도, placeholder/error 처리
        if (it.imageUrl != null && !it.imageUrl.trim().isEmpty()) {
            Glide.with(h.iv.getContext())
                    .load(it.imageUrl)
                    .placeholder(android.R.color.darker_gray)
                    .error(android.R.color.darker_gray)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .centerCrop()
                    .into(h.iv);
        } else {
            h.iv.setImageResource(android.R.color.darker_gray);
        }
        h.itemView.setOnClickListener(v -> {
            Context ctx = v.getContext();
            Intent i = new Intent(ctx, com.smn.maratang.recipes.detail.RecipeDetailActivity.class);
            i.putExtra("recipe", it);
            ctx.startActivity(i);
        });
    }

    @Override public int getItemCount() { return items != null ? items.size() : 0; }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv;
        TextView tvTitle, tvSteps, tvTime;
        VH(@NonNull View itemView) {
            super(itemView);
            iv = itemView.findViewById(R.id.iv_recipe_photo);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSteps = itemView.findViewById(R.id.tv_steps);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}
