package com.smn.maratang;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.widget.TextView;
import android.widget.Toast;

import android.hardware.Camera;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 100;   // 카메라 권한 요청 코드
    private TextView btn_main_detect_ingredient;
    private LinearLayout main_button_saved_recipe, main_button_menu;    //모드 선택 버튼

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
        btn_main_detect_ingredient.setOnClickListener(v -> {
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
        });

        main_button_saved_recipe.setOnClickListener(v ->
                Log.d("MainActivity", "Navigation to SavedRecipeActivity")
        );

        main_button_menu.setOnClickListener(v ->
                Log.d("MainActivity", "Navigation to MenuActivity")
        );
    }

    // 버튼, 모드 선택 뷰 초기화
    private void initButtons() {
        btn_main_detect_ingredient = findViewById(R.id.btn_main_detect_ingredient);
        main_button_saved_recipe = findViewById(R.id.main_button_saved_recipe);
        main_button_menu = findViewById(R.id.main_button_menu);
    }

    // 전면/후면 각각 첫 번째 카메라만 노출하여 중복 제거
    private void showCameraListPopup() {
        int count = Camera.getNumberOfCameras();
        if (count <= 0) {
            Toast.makeText(this, "사용 가능한 카메라가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        Integer frontId = null, backId = null;
        for (int i = 0; i < count; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && frontId == null) {
                frontId = i;
            } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK && backId == null) {
                backId = i;
            }
        }
        // 표시할 라벨/ID 구성
        String[] labels;
        int[] ids;
        if (frontId != null && backId != null) {
            labels = new String[]{"후면 카메라", "전면 카메라"};
            ids = new int[]{backId, frontId};
        } else if (backId != null) {
            labels = new String[]{"후면 카메라"};
            ids = new int[]{backId};
        } else if (frontId != null) {
            labels = new String[]{"전면 카메라"};
            ids = new int[]{frontId};
        } else {
            Toast.makeText(this, "사용 가능한 카메라가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("카메라 선택")
                .setItems(labels, (dialog, which) -> {
                    int cameraId = ids[which];
                    Intent intent = new Intent(MainActivity.this, IngredientActivity.class);
                    intent.putExtra("camera_index", cameraId);
                    startActivity(intent);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // 카메라 권한 요청 결과 처리
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showCameraListPopup();
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}