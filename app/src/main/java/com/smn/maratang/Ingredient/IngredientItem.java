package com.smn.maratang.Ingredient;

import java.io.Serializable;

public class IngredientItem implements Serializable {
    private static final long serialVersionUID = 1L;

    // 재료 이름을 저장하는 모델 클래스
    private String name;
    private String count; // 수량(문자열로 자유 입력)
    private String unit;  // 단위(개, g 등)

    // 생성자
    public IngredientItem(String name) {
        this(name, null, null);
    }

    public IngredientItem(String name, String count, String unit) {
        this.name = name;
        this.count = count;
        this.unit = unit;
    }

    // 재료 이름 반환
    public String getName() {
        return name;
    }

    // 재료 이름 설정
    public void setName(String name) {
        this.name = name;
    }

    public String getCount() {
        return count;
    }

    public void setCount(String count) {
        this.count = count;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    // 표시용 합성 텍스트("이름 수량 단위" 형태)
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        if (name != null) sb.append(name);
        if (count != null && !count.isEmpty()) sb.append(" ").append(count);
        if (unit != null && !unit.isEmpty()) sb.append(" ").append(unit);
        return sb.toString();
    }
}
