package com.smn.maratang.recipes.detail;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.smn.maratang.R;
import com.smn.maratang.recipes.RecipeCookingActivity;
import com.smn.maratang.recipes.RecipeDetailFetcher;
import com.smn.maratang.recipes.RecipeItem;

import java.util.ArrayList;
import java.util.List;

public class RecipeDetailActivity extends AppCompatActivity {
    private SimpleTextAdapter stepsAdapter;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_recipe_detail);

        RecipeItem item = (RecipeItem) getIntent().getSerializableExtra("recipe");
        ImageView iv = findViewById(R.id.iv_detail_photo);
        TextView tvTitle = findViewById(R.id.tv_detail_title);
        TextView tvMeta = findViewById(R.id.tv_detail_meta);
        TextView tvPageTitle = findViewById(R.id.et_recipe_title);
        Button btnBack = findViewById(R.id.btn_recipe_back);
        ImageButton btnBookmark = findViewById(R.id.btn_bookmark);
        LinearLayout btn_recipe_start = findViewById(R.id.btn_recipe_start);



        if (item != null) {
            if (tvPageTitle != null) tvPageTitle.setText(item.title);
            tvTitle.setText(item.title);
            tvMeta.setText(item.stepsCount + "단계 • " + item.time);
            String finalUrl = safeUrl(item.title, item.imageUrl);
            Log.d("RecipeDetail","load url=" + finalUrl);
            Glide.with(iv.getContext()).clear(iv);
            Glide.with(iv.getContext())
                    .load(finalUrl)
                    .placeholder(R.drawable.img_main)
                    .error(R.drawable.img_main)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .centerCrop()
                    .into(iv);
        } else {
            iv.setImageResource(R.drawable.img_main);
        }

        // 뒤로가기
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // 시작 버튼(LinearLayout) 클릭 시 토스트 표시
        View btnStart = findViewById(R.id.btn_recipe_start);
        if (btnStart != null) {
            btnStart.setOnClickListener(v -> {
                String msg = (item != null && item.title != null && !item.title.trim().isEmpty())
                        ? item.title + " 요리를 시작합니다!"
                        : "요리를 시작합니다!";
                Toast.makeText(RecipeDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
            });
        }

        tvTitle.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        tvMeta.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        RecyclerView rvIngs = findViewById(R.id.recycler_detail_ingredients);
        rvIngs.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvIngs.setAdapter(new SimpleChipAdapter(item!=null && item.ingredients!=null? item.ingredients : new ArrayList<>()));

        RecyclerView rvSteps = findViewById(R.id.recycler_detail_steps);
        rvSteps.setLayoutManager(new LinearLayoutManager(this));
        stepsAdapter = new SimpleTextAdapter(new ArrayList<>());
        rvSteps.setAdapter(stepsAdapter);

        btnBookmark.setOnClickListener(v -> v.setSelected(!v.isSelected()));

        // GPT로 단계 정보 가져오기
        if (item != null) {
            RecipeDetailFetcher.fetchSteps(item.title, new RecipeDetailFetcher.Callback() {
                @Override public void onResult(List<String> steps) {
                    runOnUiThread(() -> {
                        stepsAdapter.data.clear();
                        stepsAdapter.data.addAll(steps);
                        stepsAdapter.notifyDataSetChanged();
                    });
                }
                @Override public void onError(Exception e) { /* 무시하고 비워둠 */ }
            });
        }

        btn_recipe_start.setOnClickListener(v -> {
            Intent intent = new Intent(RecipeDetailActivity.this, RecipeCookingActivity.class);
            intent.putExtra("recipe", item);
            startActivity(intent);
        });
    }

    private String safeUrl(String title, String url) {
        if (url != null) {
            String u = url.trim();
            if (u.startsWith("http://") || u.startsWith("https://")) return u;
        }
        String q = (title==null||title.isEmpty())? "food" : title;
        try { q = java.net.URLEncoder.encode(q, "UTF-8"); } catch (Exception ignored) {}
        return "https://source.unsplash.com/800x600/?" + q;
    }

    // 간단한 어댑터들
    static class SimpleChipAdapter extends RecyclerView.Adapter<TextVH> {
        private final List<String> data; SimpleChipAdapter(List<String> d){data=d;}
        @NonNull @Override public TextVH onCreateViewHolder(@NonNull ViewGroup p, int v){
            return new TextVH(LayoutInflater.from(p.getContext()).inflate(R.layout.itm_chip_circle,p,false));
        }
        @Override public void onBindViewHolder(@NonNull TextVH h,int i){h.tv.setText(data.get(i));}
        @Override public int getItemCount(){return data.size();}
    }
    static class SimpleTextAdapter extends RecyclerView.Adapter<TextVH> {
        final List<String> data; SimpleTextAdapter(List<String> d){data=d;}
        @NonNull @Override public TextVH onCreateViewHolder(@NonNull ViewGroup p,int v){
            return new TextVH(LayoutInflater.from(p.getContext()).inflate(R.layout.itm_step_text,p,false));
        }
        @Override public void onBindViewHolder(@NonNull TextVH h,int i){h.tv.setText(data.get(i));}
        @Override public int getItemCount(){return data.size();}
    }
    static class TextVH extends RecyclerView.ViewHolder { TextView tv; TextVH(View v){super(v); tv=v.findViewById(R.id.tv_text);} }
}
