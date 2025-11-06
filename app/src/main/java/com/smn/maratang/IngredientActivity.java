package com.smn.maratang;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;

import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;

import com.smn.maratang.Ingredient.IngredientAdapter;
import com.smn.maratang.Ingredient.IngredientItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;
import android.view.TextureView;
import android.view.Surface;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.content.res.ColorStateList;

import com.google.android.material.button.MaterialButton;
import com.smn.maratang.Ingredient.IngredientAnalyzer;

public class IngredientActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {
    private FrameLayout view_ingredient_camera;
    private TextView btn_ingredient_detect_ingredient, btn_ingredient_edit;
    private RecyclerView recycler_ingredient_ingredients;
    private LinearLayout view_ingredient_camera_not_connected, btn_ingredient_suggest, ingredient_button_add_ingredient;

    // Camera/preview
    private SurfaceView cameraSurfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private boolean isPreviewRunning = false;
    private boolean isFrozen = false;

    // Recycler
    private IngredientAdapter ingredientAdapter;
    private final List<IngredientItem> ingredientList = new ArrayList<>();

    private static final int REQ_CAMERA = 1001;
    private TextureView cameraTextureView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int cameraOpenRetry = 0;
    private static final int MAX_OPEN_RETRY = 3;
    private boolean isListVisible = false;
    private boolean editMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ingredient);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 버튼 초기화
        initButtons();

        // RecyclerView 구성
        setupRecycler();
        // 초기에는 숨김
        recycler_ingredient_ingredients.setVisibility(View.GONE);
        isListVisible = false;

        // 카메라 프리뷰용 TextureView 추가 및 권한 확인
        setupCameraTexture();
        ensureCameraPermissionAndStart();

        // 레시피 추천 화면으로 이동하는 버튼 클릭 이벤트
        btn_ingredient_suggest.setOnClickListener(v -> {
            Intent intent = new Intent(IngredientActivity.this, SuggestActivity.class);
            // 인식된 재료 전달(Serializable)
            if (ingredientList != null && !ingredientList.isEmpty()) {
                intent.putExtra("recognized_ingredients", new java.util.ArrayList<>(ingredientList));
            }
            startActivity(intent);
        });

        // 재료 추가 바텀시트
        ingredient_button_add_ingredient.setOnClickListener(v -> showAddIngredientBottomSheet());

        // 재료 인식(프리뷰 일시정지/재개) 토글 버튼
        btn_ingredient_detect_ingredient.setOnClickListener(v -> {
            toggleFreezePreview();
            // 리스트 표시 토글: 최초 클릭 시 보이도록
            if (!isListVisible) {
                recycler_ingredient_ingredients.setVisibility(View.VISIBLE);
                isListVisible = true;
            }
            // 정지된 시점에만 분석 수행
            if (isFrozen) {
                captureAndAnalyzeFrame();
            }
        });

        // 편집 버튼: 편집 모드 토글
        btn_ingredient_edit.setOnClickListener(v -> toggleEditMode());

        // 어댑터 아이템 클릭 리스너: 편집 모드일 때만 동작
        ingredientAdapter.setOnItemClickListener((position, item) -> {
            if (!editMode) return;
            showEditIngredientBottomSheet(position, item);
        });
    }

    // 버튼 초기화 메서드
    private void initButtons() {
        view_ingredient_camera = findViewById(R.id.view_ingredient_camera);
        btn_ingredient_detect_ingredient = findViewById(R.id.btn_ingredient_detect_ingredient);
        btn_ingredient_edit = findViewById(R.id.btn_ingredient_edit);
        recycler_ingredient_ingredients = findViewById(R.id.recycler_ingredient_ingredients);
        view_ingredient_camera_not_connected = findViewById(R.id.view_ingredient_camera_not_connected);
        btn_ingredient_suggest = findViewById(R.id.btn_ingredient_suggest);
        ingredient_button_add_ingredient = findViewById(R.id.ingredient_button_add_ingredient);
    }

    // Recycler 설정: LinearLayoutManager(세로) 사용 + 배경 투명
    private void setupRecycler() {
        recycler_ingredient_ingredients.setLayoutManager(new LinearLayoutManager(this));
        ingredientAdapter = new IngredientAdapter(ingredientList);
        recycler_ingredient_ingredients.setAdapter(ingredientAdapter);
        recycler_ingredient_ingredients.setBackgroundColor(Color.TRANSPARENT);
        // 초기 더미 데이터 추가 제거: 분석 결과만 표시
    }

    // TextureView를 사용하여 모서리 반경 적용 가능하도록 설정
    private void setupCameraTexture() {
        if (cameraTextureView != null) return;
        cameraTextureView = new TextureView(this);
        cameraTextureView.setSurfaceTextureListener(this);
        // 고정 라운드 사각형 outline (배경 의존 X)
        view_ingredient_camera.setClipToOutline(true);
        view_ingredient_camera.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                float r = dpToPx(16); // 16dp 라운드
                if (w > 0 && h > 0) {
                    outline.setRoundRect(0, 0, w, h, r);
                }
            }
        });
        view_ingredient_camera.addView(cameraTextureView, 0,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureCameraPermissionAndStart() {
        if (hasCameraPermission()) {
            if (cameraTextureView != null && cameraTextureView.isAvailable()) {
                startCameraPreviewWithTexture();
            }
            // Texture가 아직 준비 안 됐으면 onSurfaceTextureAvailable에서 시작
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void startCameraPreviewWithTexture() {
        try {
            if (cameraTextureView == null) return;
            if (cameraTextureView.getSurfaceTexture() == null) {
                // SurfaceTexture 준비가 늦는 경우 약간 지연 후 재시도
                mainHandler.postDelayed(this::startCameraPreviewWithTexture, 100);
                return;
            }
            if (camera == null) {
                int index = getIntent() != null ? getIntent().getIntExtra("camera_index", 0) : 0;
                int count = Camera.getNumberOfCameras();
                if (count <= 0) throw new RuntimeException("No camera available");
                if (index < 0 || index >= count) index = 0;
                camera = Camera.open(index);
            }
            camera.setDisplayOrientation(90);
            camera.setPreviewTexture(cameraTextureView.getSurfaceTexture());
            camera.startPreview();
            isPreviewRunning = true;
            isFrozen = false;
            cameraOpenRetry = 0; // 성공 시 리셋
            if (view_ingredient_camera_not_connected != null) {
                view_ingredient_camera_not_connected.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            if (cameraOpenRetry < MAX_OPEN_RETRY) {
                cameraOpenRetry++;
                mainHandler.postDelayed(this::startCameraPreviewWithTexture, 200);
            } else {
                if (view_ingredient_camera_not_connected != null) {
                    view_ingredient_camera_not_connected.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void toggleFreezePreview() {
        if (camera == null) return;
        try {
            if (!isFrozen) {
                if (isPreviewRunning) {
                    camera.stopPreview();
                    isPreviewRunning = false;
                }
                isFrozen = true;
                btn_ingredient_detect_ingredient.setText(R.string.ingredient_edit);
            } else {
                camera.startPreview();
                isPreviewRunning = true;
                isFrozen = false;
                btn_ingredient_detect_ingredient.setText(R.string.main_button_detect_ingredient);
            }
        } catch (RuntimeException e) {
            // TextureView 기반으로 복구
            startCameraPreviewWithTexture();
        }
    }

    private void releaseCamera() {
        try {
            if (camera != null) {
                if (isPreviewRunning) {
                    camera.stopPreview();
                }
                camera.release();
            }
        } catch (Exception ignored) {
        } finally {
            camera = null;
            isPreviewRunning = false;
            isFrozen = false;
            cameraOpenRetry = 0; // 초기화
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureCameraPermissionAndStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (cameraTextureView != null && cameraTextureView.isAvailable()) {
                    startCameraPreviewWithTexture();
                }
            } else {
                if (view_ingredient_camera_not_connected != null) {
                    view_ingredient_camera_not_connected.setVisibility(View.VISIBLE);
                }
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void captureAndAnalyzeFrame() {
        try {
            if (cameraTextureView == null || !cameraTextureView.isAvailable()) {
                Toast.makeText(this, "카메라 프리뷰가 준비되지 않았습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            int w = Math.max(64, view_ingredient_camera.getWidth());
            int h = Math.max(64, view_ingredient_camera.getHeight());
            Bitmap full = cameraTextureView.getBitmap(w, h);
            if (full == null) {
                Toast.makeText(this, "프레임 캡처 실패", Toast.LENGTH_SHORT).show();
                return;
            }
            // 리스트에 '인식 중' 표시
            ingredientList.clear();
            ingredientList.add(new com.smn.maratang.Ingredient.IngredientItem("인식 중", "", ""));
            ingredientAdapter.notifyDataSetChanged();

            IngredientAnalyzer.analyzeWithMlkit(this, full, new IngredientAnalyzer.Callback() {
                @Override
                public void onResult(@NonNull java.util.List<com.smn.maratang.Ingredient.IngredientItem> results) {
                    runOnUiThread(() -> {
                        ingredientList.clear();
                        if (results != null && !results.isEmpty()) {
                            ingredientList.addAll(results);
                        } else {
                            ingredientList.add(new com.smn.maratang.Ingredient.IngredientItem("인식된 재료가 없습니다.", "", ""));
                        }
                        ingredientAdapter.notifyDataSetChanged();
                    });
                }

                @Override
                public void onError(@NonNull Exception e) {
                    runOnUiThread(() -> {
                        ingredientList.clear();
                        ingredientList.add(new com.smn.maratang.Ingredient.IngredientItem("인식된 재료가 없습니다.", "", ""));
                        ingredientAdapter.notifyDataSetChanged();
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "분석 중 오류 발생", Toast.LENGTH_SHORT).show();
        }
    }

    // 재료 추가 바텀시트 표시
    private void showAddIngredientBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottomsheet_add_ingredient, null);
        dialog.setContentView(sheet);

        EditText etName = sheet.findViewById(R.id.et_ingredient_name);
        EditText etCount = sheet.findViewById(R.id.et_ingredient_count);
        EditText etUnit = sheet.findViewById(R.id.et_ingredient_unit);
        View btnAdd = sheet.findViewById(R.id.btn_save);

        // 버튼 색상 틴트를 코드에서도 강제 적용(@color/brown)
        if (btnAdd instanceof MaterialButton) {
            ((MaterialButton) btnAdd).setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brown)));
        } else {
            btnAdd.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brown)));
        }

        btnAdd.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String cnt = etCount.getText() != null ? etCount.getText().toString().trim() : "";
            String unit = etUnit.getText() != null ? etUnit.getText().toString().trim() : "";

            if (name.isEmpty()) {
                etName.setError("재료 이름을 입력하세요");
                return;
            }

            // 기존에 '인식된 재료가 없습니다.' placeholder만 있는 경우 제거
            if (ingredientList.size() == 1 && "인식된 재료가 없습니다.".equals(ingredientList.get(0).getName())) {
                ingredientList.clear();
            }

            ingredientList.add(new com.smn.maratang.Ingredient.IngredientItem(name, cnt, unit));
            ingredientAdapter.notifyItemInserted(ingredientList.size() - 1);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void toggleEditMode() {
        editMode = !editMode;
        ingredientAdapter.setEditMode(editMode);
        btn_ingredient_edit.setText(getString(editMode ? R.string.ingredient_edit_done : R.string.ingredient_edit_start));
        Toast.makeText(this, editMode? "편집 모드가 활성화되었습니다." : "편집 모드가 종료되었습니다.", Toast.LENGTH_SHORT).show();
    }

    private void showEditIngredientBottomSheet(int position, com.smn.maratang.Ingredient.IngredientItem item) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottomsheet_add_ingredient, null);
        dialog.setContentView(sheet);

        EditText etName = sheet.findViewById(R.id.et_ingredient_name);
        EditText etCount = sheet.findViewById(R.id.et_ingredient_count);
        EditText etUnit = sheet.findViewById(R.id.et_ingredient_unit);
        View btnSave = sheet.findViewById(R.id.btn_save);

        // 기존 값 채우기
        etName.setText(item.getName());
        etCount.setText(item.getCount());
        etUnit.setText(item.getUnit());

        // 저장 버튼 색 유지
        if (btnSave instanceof MaterialButton) {
            ((MaterialButton) btnSave).setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brown)));
        } else {
            btnSave.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brown)));
        }

        // 저장: 값 업데이트
        btnSave.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String cnt = etCount.getText() != null ? etCount.getText().toString().trim() : "";
            String unit = etUnit.getText() != null ? etUnit.getText().toString().trim() : "";
            if (name.isEmpty()) { etName.setError("재료 이름을 입력하세요"); return; }
            ingredientList.set(position, new com.smn.maratang.Ingredient.IngredientItem(name, cnt, unit));
            ingredientAdapter.notifyItemChanged(position);
            dialog.dismiss();
        });

        // 길게 눌러 삭제 제공 대신, Alert 버튼 추가(옵션): 삭제
        sheet.setOnLongClickListener(v -> { return true; });

        // 별도 삭제 다이얼로그 제공
        new Handler(Looper.getMainLooper()).post(() -> {
            // 편의상 바텀시트 내부에서 삭제 옵션을 다이얼로그로 제공
            TextView title = new TextView(this); // unused placeholder suppress
        });

        // 바텀시트에 삭제 버튼이 없으므로, 롱클릭이나 추가 버튼을 쓰지 않고 Context 메뉴 대신 간단 Alert 제공
        dialog.setOnShowListener(d -> {
            // 바텀시트가 뜬 후, 다이얼로그 아웃사이드에서 삭제 메뉴를 띄우지 않음. 필요 시 아래 코드로 별도 삭제 버튼을 만들어 넣을 수 있음.
        });

        // 바텀시트 외부에서 삭제 기능을 호출할 수 있도록 롱클릭 대신 간단한 AlertDialog를 옵션 버튼으로 대체할 수도 있음
        // 여기서는 간단성을 위해, 항목을 길게 눌러 삭제하는 UX 대신, 바텀시트가 떠있는 동안 삭제를 수행할 수 있도록 별도 메뉴를 제공하지 않음.
        // 필요 시 bottomsheet_add_ingredient.xml에 삭제 버튼을 추가하는 확장도 가능.

        dialog.show();
    }

    // TextureView 콜백 구현
    @Override
    public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surface, int width, int height) {
        if (hasCameraPermission()) startCameraPreviewWithTexture();
    }

    @Override
    public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture surface, int width, int height) {
        // 필요시 프리뷰 리스타트
    }

    @Override
    public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture surface) {
        releaseCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surface) { }
}