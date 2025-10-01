package com.smn.maratang;

import android.animation.Animator; // Animator 임포트 추가
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
// import android.os.Handler; // Handler 더 이상 필요 없음
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LoadingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_loading);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ProgressBar loadingProgressBar = findViewById(R.id.loadingProgressBar);
        TextView textLoadingProgress = findViewById(R.id.text_loading_progress);

        ObjectAnimator progressAnimator = ObjectAnimator.ofInt(loadingProgressBar, "progress", 0, 100);
        progressAnimator.setDuration(1000); // 1 second
        progressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        // AnimatorUpdateListener를 추가하여 진행률을 TextView에 표시
        progressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int progress = (int) animation.getAnimatedValue();
                textLoadingProgress.setText(progress + "%");
            }
        });

        // 애니메이션 완료 리스너 추가
        progressAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                // 애니메이션 시작 시 할 작업 (선택 사항)
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                // 애니메이션이 끝나면 MainActivity로 이동
                Intent intent = new Intent(LoadingActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // 현재 Activity 종료
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                // 애니메이션 취소 시 할 작업 (선택 사항)
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
                // 애니메이션 반복 시 할 작업 (선택 사항)
            }
        });

        progressAnimator.start();

        // 기존의 Handler().postDelayed()는 제거됩니다.
        // new Handler().postDelayed(() -> {
        //     Intent intent = new Intent(LoadingActivity.this, MainActivity.class);
        //     startActivity(intent);
        //     finish();
        // }, 1000);
    }
}