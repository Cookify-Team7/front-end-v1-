package com.smn.maratang.recipes;

import java.io.Serializable;
import java.util.List;

public class RecipeItem implements Serializable {
    public String title;
    public String imageUrl;
    public int stepsCount;
    public String time;
    public List<String> ingredients; // 이름 목록(간단)

    public RecipeItem(String title, String imageUrl, int stepsCount, String time, List<String> ingredients) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.stepsCount = stepsCount;
        this.time = time;
        this.ingredients = ingredients;
    }
}

