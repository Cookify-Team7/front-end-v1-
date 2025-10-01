package com.smn.maratang;

import static android.view.View.VISIBLE;

import android.content.Intent;
import android.graphics.Rect;
import android.util.TypedValue;
import androidx.annotation.NonNull;

import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.graphics.Outline;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;

import com.smn.maratang.Ingredient.IngredientAdapter;
import com.smn.maratang.Ingredient.IngredientItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IngredientActivity extends AppCompatActivity {
    private Button btn_ingredient_back; // 뒤로가기 버튼
    private FrameLayout view_ingredient;    // 카메라 미리보기 View
    private LinearLayout view_ingredient_not_connected; // 카메라 미연결 안내 문구
    private Button btn_ingredient_dectect; // "재료 인식하기" 버튼
    private RecyclerView rcv_monitoring_ingredients;    // 인식된 재료 RecyclerView
    private IngredientAdapter ingredientAdapter; // 재료 어댑터
    private List<IngredientItem> ingredientList;    // 재료 리스트
    private LinearLayout btn_ingredient_suggest;  // "Recipe" 버튼

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

        // 액티비티 레이아웃 초기화
        initViews();

        // 버튼 초기화
        initButtons();

        // RecyclerView 및 어댑터 초기화
        initAdapter();

        // 뒤로가기 버튼 클릭 시 액티비티 종료
        btn_ingredient_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 레시피 추천 화면으로 이동하는 버튼 클릭 이벤트
        btn_ingredient_suggest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // SuggestActivity로 이동
                Intent intent = new Intent(IngredientActivity.this, SuggestActivity.class);
                startActivity(intent);
            }
        });

        // 첫 번째 아이템에 " + " 추가 (재료 추가용)
        addIngredient(" + ");

        // 카메라 연결 임시 메소드 호출
        tempCameraConnect();

        // 재료 인식 버튼 클릭 시 재료 리스트에 랜덤 재료 추가 및 레시피 추천 버튼 표시
        btn_ingredient_dectect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // "재료 인식하기" 버튼 숨김
                btn_ingredient_suggest.setVisibility(VISIBLE);

                // 임의의 재료를 재료 리스트에 추가
                tempIngredient();
            }
        });
    }

    /**
     * @brief 뷰 컴포넌트 초기화 메서드
     */
    private void initViews() {
        view_ingredient = findViewById(R.id.view_ingredient);
        view_ingredient_not_connected = findViewById(R.id.view_ingredient_not_connected);
    }

    /**
     * @brief 버튼 초기화 메서드
     */
    private void initButtons() {
        btn_ingredient_back = findViewById(R.id.btn_ingredient_back);
        btn_ingredient_dectect = findViewById(R.id.btn_ingredient_dectect);
        btn_ingredient_suggest = findViewById(R.id.btn_ingredient_suggest);
    }

    /**
     * @brief RecyclerView 및 어댑터 초기화 메서드
     */
    private void initAdapter() {
        // RecyclerView 초기화
        rcv_monitoring_ingredients = findViewById(R.id.rcv_monitoring_ingredients);

        // 재료 리스트와 어댑터 초기화
        ingredientList = new ArrayList<>();
        ingredientAdapter = new IngredientAdapter(ingredientList);

        // FlexboxLayoutManager 설정: 가로 방향, 래핑 허용
        FlexboxLayoutManager flexLayoutManager = new FlexboxLayoutManager(this);
        flexLayoutManager.setFlexDirection(FlexDirection.ROW);
        flexLayoutManager.setFlexWrap(FlexWrap.WRAP);
        rcv_monitoring_ingredients.setLayoutManager(flexLayoutManager);
        rcv_monitoring_ingredients.setAdapter(ingredientAdapter);

        // 행간(줄 간격) 8dp 설정
        int rowSpacing = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        rcv_monitoring_ingredients.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.bottom = rowSpacing;
            }
        });
    }

    /**
     * @brief 임시 카메라 연결 메서드
     * 2초 후에 후면 카메라 미리보기를 추가하고, 연결 안내 문구를 숨김
     */
    private void tempCameraConnect() {
        // 2초 후 카메라 연결 및 미리보기 설정
        Handler handlerCamera = new Handler(Looper.getMainLooper());
        handlerCamera.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 카메라 연결 안내 문구 숨김
                view_ingredient_not_connected.setVisibility(View.GONE);

                // 후면 카메라 미리보기 SurfaceView 추가
                try {
                    Camera camera = Camera.open(); // 기본 후면 카메라 오픈
                    SurfaceView surfaceView = new SurfaceView(IngredientActivity.this);
                    // ───── 카메라 미리보기 둥근 모서리 적용 (API 21+) ─────
                    // 16dp 반경을 px로 변환
                    final float radiusPx = 16f * getResources().getDisplayMetrics().density;

                    // 둥근 외곽선 제공자 설정
                    surfaceView.setOutlineProvider(new ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, Outline outline) {
                            // 뷰의 현재 크기에 맞춰 모서리를 둥글게 마스킹
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
                        }
                    });
                    // 외곽선 기준으로 잘라내기 활성화
                    surfaceView.setClipToOutline(true);

                    // 크기 변경 시 외곽선 재계산
                    surfaceView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                        @Override
                        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                   int oldLeft, int oldTop, int oldRight, int oldBottom) {
                            v.invalidateOutline();
                        }
                    });
                    view_ingredient.addView(surfaceView, 0);
                    SurfaceHolder holder = surfaceView.getHolder();
                    holder.addCallback(new SurfaceHolder.Callback() {
                        @Override
                        public void surfaceCreated(SurfaceHolder holder) {
                            try {
                                // 카메라 미리보기 디스플레이 설정 및 시작
                                camera.setPreviewDisplay(holder);
                                camera.startPreview();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                            // 미리보기 변경 시 처리 (현재 미구현)
                        }

                        @Override
                        public void surfaceDestroyed(SurfaceHolder holder) {
                            // 미리보기 중지 및 카메라 자원 해제
                            camera.stopPreview();
                            camera.release();
                        }
                    });

                    // 재료 인식 버튼 보이기 및 최상위로 위치시킴
                    btn_ingredient_dectect.setVisibility(VISIBLE);
                    btn_ingredient_dectect.bringToFront();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 2000L); // 2초 지연
    }

    /**
     * @brief 임시로 재료를 추가하는 메서드
     * 0.3초 간격으로 5개의 랜덤 재료를 추가
     */
    private void tempIngredient() {
        Handler handlerRecyclerView = new Handler(Looper.getMainLooper());
        // 0.3초 간격으로 5개의 재료를 추가
        for (int i = 0; i < 5; i++) {
            handlerRecyclerView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // 랜덤 재료 추가
                    addIngredient(randomIngredient());
                }
            }, 300L * (i + 1)); // 300ms, 600ms, 900ms, ...
        }
    }

    /**
     * @brief 재료 리스트에 중복 없이 재료 추가하는 메서드
     * @param ingredient 추가할 재료 이름 문자열
     */
    private void addIngredient(String ingredient) {
        // 중복 검사(이미 리스트에 있으면 추가하지 않음)
        if (ingredientList != null && ingredientAdapter != null) {
            for (IngredientItem existing : ingredientList) {
                if (existing.getName().equals(ingredient)) {
                    return;
                }
            }

            // 리스트에 항목 추가
            ingredientList.add(new IngredientItem(ingredient));
            // 어댑터에 삽입 알림
            ingredientAdapter.notifyItemInserted(ingredientList.size() - 1);
            // 새 항목으로 스크롤 이동
            rcv_monitoring_ingredients.scrollToPosition(ingredientList.size() - 1);
        }
    }

    /**
     * @brief 임의의 재료 이름을 랜덤으로 반환하는 메서드
     * @return 랜덤으로 선택된 재료 이름 문자열 (이모지 포함)
     */
    private String randomIngredient() {
        // 재료의 이름 양식은: "재료 이름" + "재료 이모지"
        String[] ingredients = {
            "🍅 토마토", "🧅 양파", "🥕 당근", "🥔 감자", "🥦 브로콜리",
            "🌱 시금치", "🌶️ 피망", "🧄 마늘", "🫚 생강", "🥒 오이"
        };

        // 0부터 ingredients.length-1 사이의 랜덤 인덱스 생성
        int randomIndex = (int) (Math.random() * ingredients.length);

        // 랜덤 인덱스에 해당하는 재료 반환
        return ingredients[randomIndex];
    }
}