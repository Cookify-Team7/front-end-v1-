package com.smn.maratang;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.io.InputStream;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.graphics.Outline;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * @brief Flask 서버(/health → /snapshot) 연동 모니터링 화면(자동 서버 탐색 버전)
 *        - 최초: 후보 BASE들에 대해 /health 점검 → 최초 성공을 채택
 *        - 정상 연결 후: /snapshot을 1초 주기로 폴링(캐시 우회)
 *        - 오류/503 시: 지수 백오프(500ms~5s) 후 재탐색
 * @note Manifest: INTERNET 권한 필수. HTTP(비TLS) 사용 시 cleartext 허용 필요.
 */
public class MonitoringActivity extends AppCompatActivity {
    private static final String TAG = "MonitoringActivity";

    // ── 자동 탐색 후보 BASE들(환경에 맞게 수정/추가하세요) ─────────────────────────────
    private static final String[] CANDIDATE_BASES = new String[] {
            "http://172.16.63.36:5003", // ← Flask 실제 바인딩 IP(로그 근거)
            "http://192.168.0.28:5003", // ← 이전 사용 IP
            "http://10.0.2.2:5003",     // ← 에뮬레이터(호스트 접근)
            "http://127.0.0.1:5003"     // ← (특수) 단말에 서버 띄운 경우
    };

    // 최종 선택된 BASE/엔드포인트(런타임에 결정)
    private String BASE = null;
    private String SNAPSHOT_URL = null;
    private String HEALTH_URL   = null;

    // 폴링/백오프 파라미터
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final long BACKOFF_MIN_MS   = 500L;
    private static final long BACKOFF_MAX_MS   = 5000L;

    // 헬스 탐색용 타임아웃 단축(빠른 실패)
    private static final int HEALTH_CONNECT_TIMEOUT_SEC = 3;
    private static final int HEALTH_READ_TIMEOUT_SEC    = 3;

    // UI
    private Button btn_monitoring_back;
    private FrameLayout view_monitoring_camera;
    private LinearLayout view_monitoring_not_connected;
    private RecyclerView rcv_monitoring_danger;
    private ImageView networkPreview;

    // 네트워크
    private OkHttpClient okHttpClient;  // 일반 폴링
    private OkHttpClient quickClient;   // 탐색/헬스(짧은 타임아웃)
    private Handler mainHandler;        // UI
    private Handler workerHandler;      // 스케줄
    private Runnable poller;
    private boolean isStreaming = false;

    private long backoffMs = BACKOFF_MIN_MS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_monitoring);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ── 뷰 초기화 ──────────────────────────────────────────────────────────────
        view_monitoring_camera = findViewById(R.id.view_monitoring_camera);
        view_monitoring_not_connected = findViewById(R.id.view_monitoring_not_connected);
        btn_monitoring_back = findViewById(R.id.btn_monitoring_back);

        networkPreview = new ImageView(this);
        networkPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        final float radiusPx = 16f * getResources().getDisplayMetrics().density; // 16dp
        networkPreview.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        networkPreview.setClipToOutline(true);
        networkPreview.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> v.invalidateOutline());
        view_monitoring_camera.addView(
                networkPreview,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        btn_monitoring_back.setOnClickListener(v -> finish());

        // ── 네트워크 클라이언트/핸들러 ────────────────────────────────────────────
        mainHandler   = new Handler(Looper.getMainLooper());
        workerHandler = new Handler(Looper.getMainLooper()); // 필요 시 HandlerThread로 분리 가능
        okHttpClient  = buildClient();
        quickClient   = buildQuickClient();

        // ── 시작: 서버 자동 탐색 → 성공 시 BASE 확정 → 폴링 시작 ────────────────
        resolveServerThenStart();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!isStreaming) resolveServerThenStart();
    }

    @Override protected void onPause() {
        super.onPause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
    }

    // ─────────────────────────────────────────────────────────────
    // OkHttp 클라이언트
    // ─────────────────────────────────────────────────────────────
    private OkHttpClient buildClient() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor(message -> Log.d(TAG, message));
        log.setLevel(HttpLoggingInterceptor.Level.BASIC);
        return new OkHttpClient.Builder()
                .addInterceptor(log)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    private OkHttpClient buildQuickClient() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor(message -> Log.d(TAG, message));
        log.setLevel(HttpLoggingInterceptor.Level.BASIC);
        return new OkHttpClient.Builder()
                .addInterceptor(log)
                .connectTimeout(HEALTH_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(HEALTH_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // 서버 자동 탐색 → 폴링 시작
    // ─────────────────────────────────────────────────────────────
    private void resolveServerThenStart() {
        isStreaming = true;
        backoffMs = BACKOFF_MIN_MS;
        showDisconnected();
        tryResolveNextBase(0);
    }

    /** 후보 BASE들에 대해 /health 순차 탐색, 최초 성공을 채택 */
    private void tryResolveNextBase(int index) {
        if (!isStreaming) return;
        if (index >= CANDIDATE_BASES.length) {
            Log.w(TAG, "모든 후보 BASE 실패. 백오프 후 재탐색.");
            scheduleWithBackoff(this::resolveServerThenStart);
            return;
        }

        final String candidate = CANDIDATE_BASES[index];
        final String healthUrl = candidate + "/health";

        Request req = new Request.Builder()
                .url(healthUrl)
                .header("Cache-Control", "no-cache")
                .build();

        quickClient.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, java.io.IOException e) {
                Log.w(TAG, "헬스 탐색 실패: " + healthUrl + " / " + e.getMessage());
                tryResolveNextBase(index + 1);
            }
            @Override public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        Log.w(TAG, "헬스 탐색 비정상(" + r.code() + "): " + healthUrl);
                        tryResolveNextBase(index + 1);
                        return;
                    }
                    // 성공: BASE/엔드포인트 확정
                    BASE = candidate;
                    SNAPSHOT_URL = BASE + "/snapshot";
                    HEALTH_URL   = BASE + "/health";
                    Log.i(TAG, "연결 성공. BASE=" + BASE);
                    backoffMs = BACKOFF_MIN_MS;
                    mainHandler.post(() -> view_monitoring_not_connected.setVisibility(View.GONE));
                    startPollingSnapshots();
                }
            }
        });
    }

    /** 스냅샷 폴링 시작(즉시 1회 + 이후 1초 주기) */
    private void startPollingSnapshots() {
        if (SNAPSHOT_URL == null) {
            scheduleWithBackoff(this::resolveServerThenStart);
            return;
        }
        if (poller == null) {
            poller = new Runnable() {
                @Override public void run() {
                    if (!isStreaming) return;
                    requestSnapshotOnce();
                    workerHandler.postDelayed(this, POLL_INTERVAL_MS);
                }
            };
        }
        workerHandler.removeCallbacks(poller);
        workerHandler.post(poller);
    }

    /** 지수 백오프 스케줄 */
    private void scheduleWithBackoff(Runnable r) {
        if (!isStreaming) return;
        long delay = backoffMs;
        backoffMs = Math.min(backoffMs * 2, BACKOFF_MAX_MS);
        workerHandler.postDelayed(r, delay);
    }

    // ─────────────────────────────────────────────────────────────
    // /snapshot 1회 요청 → JPEG 디코드 → UI 반영
    // ─────────────────────────────────────────────────────────────
    private void requestSnapshotOnce() {
        try {
            if (SNAPSHOT_URL == null) {
                scheduleWithBackoff(this::resolveServerThenStart);
                return;
            }

            HttpUrl base = HttpUrl.parse(SNAPSHOT_URL);
            HttpUrl url = base.newBuilder()
                    .addQueryParameter("t", String.valueOf(System.currentTimeMillis())) // 캐시 우회
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .build();

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, java.io.IOException e) {
                    Log.w(TAG, "스냅샷 실패: " + e.getMessage());
                    showDisconnected();
                    scheduleWithBackoff(MonitoringActivity.this::resolveServerThenStart);
                }
                @Override public void onResponse(Call call, Response response) {
                    try (Response r = response) {
                        if (!r.isSuccessful() || r.body() == null) {
                            Log.w(TAG, "스냅샷 비정상: code=" + r.code());
                            showDisconnected();
                            scheduleWithBackoff(MonitoringActivity.this::resolveServerThenStart);
                            return;
                        }
                        try (InputStream is = r.body().byteStream()) {
                            final Bitmap bmp = BitmapFactory.decodeStream(is);
                            if (bmp != null) {
                                backoffMs = BACKOFF_MIN_MS; // 성공 시 백오프 초기화
                                showConnected(bmp);
                            } else {
                                Log.w(TAG, "스냅샷 디코딩 실패(null)");
                                showDisconnected();
                                scheduleWithBackoff(MonitoringActivity.this::resolveServerThenStart);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "스냅샷 처리 예외", e);
                        showDisconnected();
                        scheduleWithBackoff(MonitoringActivity.this::resolveServerThenStart);
                    }
                }
            });
        } catch (Exception ex) {
            Log.e(TAG, "스냅샷 요청 예외", ex);
            Toast.makeText(MonitoringActivity.this, "예외: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            showDisconnected();
            scheduleWithBackoff(this::resolveServerThenStart);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 연결 상태 UI
    // ─────────────────────────────────────────────────────────────
    private void showConnected(final Bitmap bmp) {
        mainHandler.post(() -> {
            networkPreview.setImageBitmap(bmp);
            view_monitoring_not_connected.setVisibility(View.GONE);
        });
    }

    private void showDisconnected() {
        mainHandler.post(() -> {
            view_monitoring_not_connected.setVisibility(View.VISIBLE);
        });
    }
}