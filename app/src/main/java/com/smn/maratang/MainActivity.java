package com.smn.maratang;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity { ;
    private static final int REQUEST_CAMERA_PERMISSION = 100;   // 카메라 권한 요청 코드
    private LinearLayout btn_main_connect;    // "카메라 연결하기" 버튼
    private LinearLayout btn_main_monitoring, btn_main_ingredient, btn_main_ai_chat;    //모드 선택 버튼

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 메인 화면의 카메라 연결 버튼, 모드 선택 버튼 초기화
        initButtons();

        // 버튼 클릭 리스너 설정
        // "안전 모니터링 시작" 버튼 클릭 리스너
        btn_main_monitoring.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //MonitoringActivity로 이동
                startActivity(new Intent(MainActivity.this, MonitoringActivity.class));
            }
        });

        // "재료 인식" 버튼 클릭 리스너
        btn_main_ingredient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //IngredientActivity로 이동
                startActivity(new Intent(MainActivity.this, IngredientActivity.class));
            }
        });

        // "AI 챗" 버튼 클릭 리스너
        btn_main_ai_chat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //AiChatActivity로 이동
                startActivity(new Intent(MainActivity.this, AiChatActivity.class));
            }
        });

        // "카메라 연결하기" 버튼 클릭 리스너
        btn_main_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION
                    );
                } else {
                    showCameraListPopup();
                }
            }
        });
    }


    /**
     * @breif 메인 화면의 카메라 연결 버튼, 모드 선택 버튼 초기화
     */
    private void initButtons() {
        btn_main_connect = findViewById(R.id.btn_main_connect); // "카메라 연결하기" 버튼

        btn_main_monitoring = findViewById(R.id.btn_main_monitoring); // "안전 모니터링 시작" 버튼
        btn_main_ingredient = findViewById(R.id.btn_main_ingredient); // "재료 인식" 버튼
        btn_main_ai_chat = findViewById(R.id.btn_main_ai_chat); // "AI 챗" 버튼
    }

    /**
     * @breif 카메라 목록을 팝업으로 표시하는 메소드
     */
    private void showCameraListPopup() {
        // TODO: 카메라 목록을 팝업으로 표시하는 로직 구현
    }

    /**
     * @breif 카메라 권한 요청 결과를 처리하는 메소드
     * @param requestCode 요청 코드
     * @param permissions 요청한 권한 목록
     * @param grantResults 권한 요청 결과
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 카메라 권한 요청 결과 처리
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            // 권한이 허용되었는지 확인
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showCameraListPopup();
            }
            // 권한이 거부된 경우
            else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}