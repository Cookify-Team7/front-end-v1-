package com.smn.maratang;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// ✅ 올바른 패키지 (Google AI Java/Kotlin SDK)
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;

// ✅ Guava 얇은 모듈 (ListenableFuture 인터페이스 제공)
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

public class AiChatActivity extends AppCompatActivity {
    private Button btn_ai_back;         // 뒤로가기 버튼
    private Button btn_ai_send;         // 메시지 전송 버튼
    private EditText edit_ai_chatbot;   // 메시지 입력창

    private RecyclerView recyclerViewChat;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages;

    // ✅ Java에서 suspend 함수를 직접 못 쓰므로 Futures 래퍼 사용
    private GenerativeModelFutures model;

    // BuildConfig에 주입된 Gemini API 키 (Gradle의 buildConfigField로 전달됨)
    private static final String API_KEY = BuildConfig.GEMINI_API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ai_chat);

        // 시스템 인셋 처리: 레이아웃에 R.id.main 이 있어야 합니다.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ---------- UI 초기화 ----------
        initButtons();
        edit_ai_chatbot = findViewById(R.id.edit_ai_chatbot);

        // ⚠️ 아래 id는 실제 XML의 RecyclerView id와 일치해야 합니다.
        //    프로젝트에 따라 recyclerview_ai_chat / recycler_ai_chat 중 하나일 수 있음(확실하지 않음).
        recyclerViewChat = findViewById(R.id.recycler_ai_chat);
        messages = new ArrayList<>();
        chatAdapter = new ChatAdapter(messages);
        recyclerViewChat.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChat.setAdapter(chatAdapter);

        // ---------- 모델 준비 ----------
        // 모델명은 계정/키 권한에 따라 다릅니다(확실하지 않음). 일반적으로 "gemini-1.5-flash" 또는 "gemini-1.5-pro" 등을 사용.
        GenerativeModel gm = new GenerativeModel("gemini-1.5-flash", API_KEY); // 필요 시 모델명 교체
        model = GenerativeModelFutures.from(gm); // ✅ Java용 Futures 래퍼

        // ---------- 이벤트 바인딩 ----------
        btn_ai_back.setOnClickListener(v -> finish());

        btn_ai_send.setOnClickListener(v -> {
            String userMessage = edit_ai_chatbot.getText().toString().trim();
            if (userMessage.isEmpty()) return;

            // 1) 사용자 메시지 즉시 표시
            messages.add(new ChatMessage(userMessage, true));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerViewChat.scrollToPosition(messages.size() - 1);
            edit_ai_chatbot.setText("");

            // 2) 프롬프트를 Content로 래핑 (✅ 정식 Builder 사용: new Content.Builder())
            Content content = new Content.Builder()
                    .addText(userMessage)   // 텍스트 파트 추가
                    .build();

            // 3) 비동기 호출 (내부적으로 스레드 처리됨)
            ListenableFuture<GenerateContentResponse> future = model.generateContent(content);

            // 4) 완료 리스너: UI 스레드에서 실행되도록 Executor를 runOnUiThread로 래핑
            future.addListener(() -> {
                try {
                    String aiResponse = future.get().getText(); // 완료 후 즉시 결과 접근

                    runOnUiThread(() -> {
                        messages.add(new ChatMessage(aiResponse, false));
                        chatAdapter.notifyItemInserted(messages.size() - 1);
                        recyclerViewChat.scrollToPosition(messages.size() - 1);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        messages.add(new ChatMessage("Error: " + e.getMessage(), false));
                        chatAdapter.notifyItemInserted(messages.size() - 1);
                        recyclerViewChat.scrollToPosition(messages.size() - 1);
                    });
                }
            }, command -> runOnUiThread(command));
        });
    }

    /**
     * @brief 버튼 객체 초기화
     */
    private void initButtons() {
        btn_ai_back = findViewById(R.id.btn_ai_back);
        btn_ai_send = findViewById(R.id.btn_ai_send);
    }
}
