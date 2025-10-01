package com.smn.maratang;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SuggestActivity extends AppCompatActivity {
    private Button btn_suggest_back;                                                // 뒤로가기 버튼
    private Button btn_suggest_option1, btn_suggest_option2, btn_suggest_option3;   // 추천 옵션 버튼
    private String suggestOption1, suggestOption2, suggestOption3;                  // 추천 문구
    private EditText edit_suggest_chatbot;                                          // 챗봇 입력창

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

        // 버튼 초기화
        initButtons();

        // 챗봇 입력창 초기화
        edit_suggest_chatbot = findViewById(R.id.edit_suggest_chatbot);

        // 뒤로가기 버튼 클릭 시
        btn_suggest_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 현재 액티비티 종료
                finish();
            }
        });
    }


    /** * @breif 메서드는 추천 옵션 버튼을 초기화
     * 각 버튼에 추천 문구를 설정하고, 중복 없이 세 가지 옵션을 선택하도록 리스트를 섞음
     */
    private void initButtons() {
        // 버튼 초기화
        btn_suggest_back = findViewById(R.id.btn_suggest_back);
        btn_suggest_option1 = findViewById(R.id.btn_suggest_option1);
        btn_suggest_option2 = findViewById(R.id.btn_suggest_option2);
        btn_suggest_option3 = findViewById(R.id.btn_suggest_option3);

        // 중복 없이 세 가지 옵션을 선택하도록 리스트를 섞음
        List<String> list = getSuggestionsList();
        Collections.shuffle(list);
        suggestOption1 = list.get(0);
        suggestOption2 = list.get(1);
        suggestOption3 = list.get(2);

        // 각 버튼에 추천 문구 설정
        btn_suggest_option1.setText(suggestOption1);
        btn_suggest_option2.setText(suggestOption2);
        btn_suggest_option3.setText(suggestOption3);
    }

    /**
     * @breif 메서드는 추천 문구를 담은 리스트를 반환
     *
     * @return 추천 문구 리스트
     */
    private List<String> getSuggestionsList() {
        return new ArrayList<>(Arrays.asList(
                "파스타 면 3인분 몇분 정도\n삶아야 해?",
                "10분안에 만들\n수 있는 간식 추천해줘",
                "오늘 저녁으로 간단하게 \n 만들 수 있는 요리 추천해줘",
                "달달한 디저트 만드는\n법을 알려줘",
                "다이어트용 샐러드\n레시피 알려줘",
                "비건 요리 추천해줘",
                "매운 음식 추천해줘",
                "서구권 음식 레시피 알려줘",
                "한국 전통 음식\n레시피 알려줘",
                "이탈리안 요리 추천해줘"
        ));
    }
}