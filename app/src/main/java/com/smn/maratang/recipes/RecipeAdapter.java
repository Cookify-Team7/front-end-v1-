package com.smn.maratang.recipes;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.smn.maratang.R;

import java.net.URLEncoder;
import java.util.List;

import androidx.annotation.Nullable;
import com.bumptech.glide.load.engine.GlideException;

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
        // 안전한 URL로 보정
        String finalUrl = safeUrl(it.title, it.imageUrl);
        Log.d("RecipeAdapter","load url=" + finalUrl);
        Glide.with(h.iv.getContext()).clear(h.iv);
        Glide.with(h.iv.getContext())
                .load(finalUrl)
                .placeholder(R.drawable.img_main)
                .error(R.drawable.img_main)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .centerCrop()
                .into(h.iv);

        h.itemView.setOnClickListener(v -> {
            Context ctx = v.getContext();
            Intent i = new Intent(ctx, com.smn.maratang.recipes.detail.RecipeDetailActivity.class);
            i.putExtra("recipe", it);
            ctx.startActivity(i);
        });
    }

    private String safeUrl(String title, String url) {
        if (url != null) {
            String u = url.trim();
            if (u.startsWith("http://") || u.startsWith("https://")) return u;
        }
        String q = (title==null||title.isEmpty())? "food" : title;
        try { q = URLEncoder.encode(q, "UTF-8"); } catch (Exception ignored) {}
        return "https://source.unsplash.com/600x600/?" + q;
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
