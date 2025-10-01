package com.smn.maratang.Ingredient;

public class IngredientItem {
    // 재료 이름을 저장하는 모델 클래스
    private String name;

    // 생성자
    public IngredientItem(String name) {
        this.name = name;
    }

    // 재료 이름 반환
    public String getName() {
        return name;
    }

    // 재료 이름 설정
    public void setName(String name) {
        this.name = name;
    }
}
