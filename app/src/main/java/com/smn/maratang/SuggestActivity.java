package com.smn.maratang;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.smn.maratang.Ingredient.IngredientAdapter;
import com.smn.maratang.Ingredient.IngredientItem;
import com.smn.maratang.recipes.RecipeAdapter;
import com.smn.maratang.recipes.RecipeItem;
import com.smn.maratang.recipes.RecipeSuggester;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SuggestActivity extends AppCompatActivity {     // 챗봇 입력창

    private RecyclerView recycler_suggest_ingredients;
    private IngredientAdapter ingredientAdapter;
    private final List<IngredientItem> ingredientList = new ArrayList<>();

    private RecyclerView recycler_suggest_recipes;
    private RecipeAdapter recipeAdapter;
    private final List<RecipeItem> recipeList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_suggest);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        recycler_suggest_ingredients = findViewById(R.id.recycler_suggest_ingredients);
        recycler_suggest_recipes = findViewById(R.id.recycler_suggest_recipes);
        setupFirstRecycler();
        setupSecondRecycler();

        // 인식 결과 수신 및 반영
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("recognized_ingredients")) {
            java.util.ArrayList<IngredientItem> received = (java.util.ArrayList<IngredientItem>) intent.getSerializableExtra("recognized_ingredients");
            if (received != null && !received.isEmpty()) {
                ingredientList.clear();
                ingredientList.addAll(received);
                ingredientAdapter.notifyDataSetChanged();
            }
        }

        if (ingredientList.isEmpty()) {
            ingredientList.add(new IngredientItem("인식된 재료가 없습니다.", "", ""));
            ingredientAdapter.notifyDataSetChanged();
        } else {
            // 재료 기반 레시피 조회
            requestRecipes();
        }

        // 뒤로가기 버튼: 이전 화면으로 이동
        Button btnBack = findViewById(R.id.btn_suggest_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    private void setupFirstRecycler() {
        recycler_suggest_ingredients.setLayoutManager(new LinearLayoutManager(this));
        ingredientAdapter = new IngredientAdapter(ingredientList);
        recycler_suggest_ingredients.setAdapter(ingredientAdapter);
    }

    private void setupSecondRecycler() {
        recycler_suggest_recipes.setLayoutManager(new GridLayoutManager(this, 2));
        recipeAdapter = new RecipeAdapter(recipeList);
        recycler_suggest_recipes.setAdapter(recipeAdapter);
    }

    private void requestRecipes() {
        // 이름만 추출
        List<String> names = ingredientList.stream().map(IngredientItem::getName).collect(Collectors.toList());
        RecipeSuggester.suggest(names, new RecipeSuggester.Callback() {
            @Override public void onResult(List<RecipeItem> items) {
                runOnUiThread(() -> {
                    recipeList.clear();
                    recipeList.addAll(items);
                    recipeAdapter.notifyDataSetChanged();
                });
            }
            @Override public void onError(Exception e) {
                runOnUiThread(() -> {
                    recipeList.clear();
                    recipeAdapter.notifyDataSetChanged();
                });
            }
        });
    }
}