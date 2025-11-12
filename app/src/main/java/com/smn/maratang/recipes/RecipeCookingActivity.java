package com.smn.maratang.recipes;

import static android.view.View.GONE;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.hardware.Camera;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.smn.maratang.R;

import java.util.ArrayList;
import java.util.List;

public class RecipeCookingActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    // UI: 카메라/상태
    private FrameLayout view_monitoring_ai;
    private LinearLayout view_monitoring_not_connected;
    private TextureView cameraTextureView;

    // 카메라 상태
    private Camera camera;
    private boolean isPreviewRunning = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int cameraOpenRetry = 0;
    private static final int MAX_OPEN_RETRY = 3;
    private static final int REQ_CAMERA = 1201;

    // UI: 단계 진행
    private ProgressBar progressBar;
    private TextView progressStep;
    private TextView tvStepContent;
    private TextView tvStartPrompt;
    private LinearLayout viewCenterContainer;
    private Button btnStart;
    private Button btnPrev, btnNext;
    private LinearLayout stepNavContainer;

    // 데이터
    private RecipeItem recipeItem;
    private final List<String> steps = new ArrayList<>();
    private int currentStep = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_recipe_cooking);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        initCameraView();
        initListeners();
        bindIntentData();
    }

    private void initViews() {
        // 상단 뒤로가기
        Button btnBack = findViewById(R.id.btn_suggest_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // 카메라 관련
        view_monitoring_ai = findViewById(R.id.view_monitoring_ai);
        view_monitoring_not_connected = findViewById(R.id.view_monitoring_not_connected);

        // 단계 관련
        progressBar = findViewById(R.id.progressBar);
        progressStep = findViewById(R.id.progressStep);
        tvStepContent = findViewById(R.id.unstart_name); // 단계 텍스트 재사용
        tvStartPrompt = findViewById(R.id.unstart_prompt);
        viewCenterContainer = findViewById(R.id.unstart);
        btnStart = findViewById(R.id.btn_start);
        btnPrev = findViewById(R.id.btn_prev_step);
        btnNext = findViewById(R.id.btn_next_step);
        stepNavContainer = findViewById(R.id.step_nav_container);

        progressBar.setVisibility(GONE);
        progressStep.setVisibility(GONE);
        stepNavContainer.setVisibility(GONE);
    }

    private void initCameraView() {
        if (cameraTextureView != null) return;
        cameraTextureView = new TextureView(this);
        cameraTextureView.setSurfaceTextureListener(this);
        if (view_monitoring_ai != null) {
            view_monitoring_ai.setClipToOutline(true);
            view_monitoring_ai.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    int w = view.getWidth();
                    int h = view.getHeight();
                    float r = dpToPx(16);
                    if (w > 0 && h > 0) outline.setRoundRect(0, 0, w, h, r);
                }
            });
            view_monitoring_ai.addView(cameraTextureView, 0,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
        }
        ensureCameraPermissionAndStart();
    }

    private void initListeners() {
        if (btnStart != null) {
            btnStart.setOnClickListener(v -> onStartCooking());
        }
        if (btnPrev != null) btnPrev.setOnClickListener(v -> moveStep(-1));
        if (btnNext != null) btnNext.setOnClickListener(v -> moveStep(1));
    }

    private void bindIntentData() {
        recipeItem = (RecipeItem) getIntent().getSerializableExtra("recipe");
        if (recipeItem != null && tvStepContent != null) {
            tvStepContent.setText(recipeItem.title);
        }
    }

    private void onStartCooking() {
        if (recipeItem == null) return;
        btnStart.setVisibility(GONE);
        tvStartPrompt.setVisibility(GONE);
        progressBar.setVisibility(View.VISIBLE);
        progressStep.setVisibility(View.VISIBLE);
        // 단계 불러오기
        RecipeDetailFetcher.fetchSteps(recipeItem.title, new RecipeDetailFetcher.Callback() {
            @Override public void onResult(List<String> list) {
                runOnUiThread(() -> {
                    steps.clear();
                    steps.addAll(list);
                    currentStep = 0;
                    if (steps.isEmpty()) {
                        tvStepContent.setText(R.string.recycler_view_ingredient_null);
                        progressBar.setVisibility(GONE);
                        progressStep.setVisibility(GONE);
                    } else {
                        progressBar.setMax(steps.size());
                        stepNavContainer.setVisibility(View.VISIBLE);
                        viewCenterContainer.setVisibility(View.VISIBLE);
                        updateStepUI();
                    }
                });
            }
            @Override public void onError(Exception e) {
                runOnUiThread(() -> {
                    tvStepContent.setText("단계를 불러오지 못했습니다.");
                    progressBar.setVisibility(GONE);
                    progressStep.setVisibility(GONE);
                });
            }
        });
    }

    private void moveStep(int delta) {
        int newIndex = currentStep + delta;
        if (newIndex < 0 || newIndex >= steps.size()) return;
        currentStep = newIndex;
        updateStepUI();
    }

    private void updateStepUI() {
        if (steps.isEmpty()) return;
        tvStepContent.setText(steps.get(currentStep));
        progressStep.setText(getString(R.string.cooking_step_progress, currentStep + 1, steps.size()));
        progressBar.setProgress(currentStep + 1);
        btnPrev.setEnabled(currentStep > 0);
        btnNext.setEnabled(currentStep < steps.size() - 1);
    }

    private float dpToPx(float dp) { return dp * getResources().getDisplayMetrics().density; }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureCameraPermissionAndStart() {
        if (hasCameraPermission()) {
            if (cameraTextureView != null && cameraTextureView.isAvailable()) {
                startCameraPreviewWithTexture();
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void startCameraPreviewWithTexture() {
        try {
            if (cameraTextureView == null) return;
            if (cameraTextureView.getSurfaceTexture() == null) {
                mainHandler.postDelayed(this::startCameraPreviewWithTexture, 100);
                return;
            }
            if (camera == null) {
                int count = Camera.getNumberOfCameras();
                if (count <= 0) throw new RuntimeException("No camera available");
                camera = Camera.open(0);
            }
            camera.setDisplayOrientation(90);
            camera.setPreviewTexture(cameraTextureView.getSurfaceTexture());
            camera.startPreview();
            isPreviewRunning = true;
            cameraOpenRetry = 0;
            if (view_monitoring_not_connected != null) view_monitoring_not_connected.setVisibility(GONE);
        } catch (Exception e) {
            if (cameraOpenRetry < MAX_OPEN_RETRY) {
                cameraOpenRetry++;
                mainHandler.postDelayed(this::startCameraPreviewWithTexture, 200);
            } else {
                if (view_monitoring_not_connected != null) view_monitoring_not_connected.setVisibility(View.VISIBLE);
            }
        }
    }

    private void releaseCamera() {
        try {
            if (camera != null) {
                if (isPreviewRunning) camera.stopPreview();
                camera.release();
            }
        } catch (Exception ignored) { }
        finally {
            camera = null;
            isPreviewRunning = false;
            cameraOpenRetry = 0;
        }
    }

    @Override protected void onResume() { super.onResume(); ensureCameraPermissionAndStart(); }
    @Override protected void onPause() { super.onPause(); releaseCamera(); }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (cameraTextureView != null && cameraTextureView.isAvailable()) {
                    startCameraPreviewWithTexture();
                }
            } else {
                if (view_monitoring_not_connected != null) view_monitoring_not_connected.setVisibility(View.VISIBLE);
            }
        }
    }

    // TextureView.SurfaceTextureListener
    @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surface, int width, int height) { if (hasCameraPermission()) startCameraPreviewWithTexture(); }
    @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture surface, int width, int height) { }
    @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture surface) { releaseCamera(); return true; }
    @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surface) { }
}