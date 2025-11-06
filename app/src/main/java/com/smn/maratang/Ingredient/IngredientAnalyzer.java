package com.smn.maratang.Ingredient;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smn.maratang.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OpenAI GPT 전용 식재료 분석기 (gpt-5 → 실패 시 gpt-4o 폴백) + 네트워크 재시도.
 */
public class IngredientAnalyzer {

    public interface Callback {
        void onResult(@NonNull List<IngredientItem> items);
        void onError(@NonNull Exception e);
    }

    private static final String TAG = "IngredientAnalyzer";

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static void analyzeWithMlkit(@NonNull Context context, Bitmap src, @NonNull Callback cb) {
        try {
            if (src == null) { cb.onResult(new ArrayList<>()); return; }
            EXEC.execute(() -> {
                try {
                    List<IngredientItem> result = analyzeWithOpenAI(src);
                    cb.onResult(result != null ? result : new ArrayList<>());
                } catch (Exception e) {
                    Log.e(TAG, "OpenAI analyze error", e);
                    cb.onError(e);
                }
            });
        } catch (Exception e) { cb.onError(e); }
    }

    private static List<IngredientItem> analyzeWithOpenAI(@NonNull Bitmap src) throws Exception {
        String apiKey = BuildConfig.GPT_KEY;
        if (apiKey == null || apiKey.isEmpty()) throw new IllegalStateException("GPT_KEY 미설정");

        String url = "https://api.openai.com/v1/chat/completions";
        Bitmap bmpLarge = scaleDown(src, 960);
        Bitmap bmpSmall = scaleDown(src, 640);

        // 1차: gpt-5 (큰 이미지)
        try {
            String body1 = buildOpenAIBody(bmpLarge, "gpt-5", false, 80);
            List<IngredientItem> r1 = requestOpenAI(url, body1, apiKey);
            if (r1 != null && !r1.isEmpty()) return r1;
            String body1s = buildOpenAIBody(bmpLarge, "gpt-5", true, 80);
            List<IngredientItem> r1s = requestOpenAI(url, body1s, apiKey);
            if (r1s != null && !r1s.isEmpty()) return r1s;
        } catch (IOException ioe) {
            Log.w(TAG, "gpt-5 네트워크 오류 재시도 예정: " + ioe.getMessage());
        } catch (RuntimeException re) {
            Log.w(TAG, "gpt-5 실패 → 폴백 시도: " + re.getMessage());
        }

        // 2차: gpt-4o (큰 이미지)
        try {
            String body2 = buildOpenAIBody(bmpLarge, "gpt-4o", false, 80);
            List<IngredientItem> r2 = requestOpenAI(url, body2, apiKey);
            if (r2 != null && !r2.isEmpty()) return r2;
            String body2s = buildOpenAIBody(bmpLarge, "gpt-4o", true, 80);
            List<IngredientItem> r2s = requestOpenAI(url, body2s, apiKey);
            if (r2s != null && !r2s.isEmpty()) return r2s;
        } catch (IOException ioe) {
            Log.w(TAG, "gpt-4o 네트워크 오류(큰 이미지) → 소형 이미지로 재시도: " + ioe.getMessage());
        }

        // 3차 재시도: gpt-4o strict + 작은 이미지 + 백오프(2회)
        int retries = 2;
        long backoff = 600; // ms
        for (int i=0;i<retries;i++) {
            try {
                String bodySmall = buildOpenAIBody(bmpSmall, "gpt-4o", true, 75);
                List<IngredientItem> rx = requestOpenAI(url, bodySmall, apiKey);
                if (rx != null && !rx.isEmpty()) return rx;
            } catch (IOException ioe) {
                Log.w(TAG, "재시도("+(i+1)+") 네트워크 오류: "+ioe.getMessage());
                try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                backoff *= 2; // 지수 백오프
                continue;
            }
        }

        return new ArrayList<>();
    }

    private static List<IngredientItem> requestOpenAI(String url, String body, String apiKey) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();
        try (Response res = HTTP.newCall(req).execute()) {
            int code = res.code();
            String resp = res.body() != null ? res.body().string() : "";
            Log.d(TAG, "OpenAI HTTP code=" + code);
            if (!res.isSuccessful()) {
                Log.e(TAG, "OpenAI error body=" + resp);
                throw new RuntimeException("OpenAI API 실패: " + code);
            }
            return parseOpenAIResponse(resp);
        }
    }

    private static String buildOpenAIBody(Bitmap src, String model, boolean strict, int jpegQuality) {
        String sys = "당신은 사진 속 식재료만 식별하는 비전 분석기입니다. 이름은 반드시 한국어 표준 식재료명으로 출력하고, 확실하지 않으면 제외합니다.";
        String allowed = "[토마토, 양파, 대파, 상추, 배추, 양배추, 당근, 오이, 가지, 감자, 고구마, 마늘, 생강, 파프리카, 고추, 브로콜리, 콜리플라워, 시금치, 케일, 바나나, 사과, 배, 딸기, 블루베리, 포도]";
        String suffix = strict ? " 반드시 JSON 객체만 반환하고 다른 텍스트는 절대 포함하지 마세요." : "";
        String user = "사진 속 식재료만 인식해 JSON 객체로만 반환하세요: {\"items\":[{name,quantity,unit}, ...]}. name=한국어 표준명, quantity=숫자 또는 문자열, unit=개,g,쪽 등. 확실하지 않으면 제외. 결과 없으면 items는 빈 배열. 설명/코드펜스 금지. 가능한 경우 다음 목록 내에서 이름을 선택: " + allowed + "." + suffix;

        // base64 data URL
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        src.compress(Bitmap.CompressFormat.JPEG, Math.max(50, Math.min(95, jpegQuality)), baos);
        String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        String dataUrl = "data:image/jpeg;base64," + b64;

        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("max_tokens", 300);

        JsonObject rfmt = new JsonObject();
        rfmt.addProperty("type", "json_object");
        root.add("response_format", rfmt);

        JsonArray messages = new JsonArray();
        JsonObject sysObj = new JsonObject();
        sysObj.addProperty("role", "system");
        sysObj.addProperty("content", sys);
        messages.add(sysObj);

        JsonObject userObj = new JsonObject();
        userObj.addProperty("role", "user");
        JsonArray contents = new JsonArray();
        JsonObject t = new JsonObject(); t.addProperty("type", "text"); t.addProperty("text", user); contents.add(t);
        JsonObject im = new JsonObject(); im.addProperty("type", "image_url");
        JsonObject imUrl = new JsonObject(); imUrl.addProperty("url", dataUrl); im.add("image_url", imUrl);
        contents.add(im);
        userObj.add("content", contents);
        messages.add(userObj);
        root.add("messages", messages);

        return new Gson().toJson(root);
    }

    private static Bitmap scaleDown(Bitmap src, int maxSide) {
        int w = src.getWidth(); int h = src.getHeight();
        int max = Math.max(w, h);
        if (max <= maxSide) return src;
        float ratio = (float) maxSide / max;
        int nw = Math.round(w * ratio); int nh = Math.round(h * ratio);
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private static List<IngredientItem> parseOpenAIResponse(String resp) {
        try {
            JsonObject root = new JsonParser().parse(resp).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) return new ArrayList<>();
            JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (msg == null) return new ArrayList<>();
            String content = msg.get("content").getAsString();
            if (content == null) return new ArrayList<>();

            JsonObject obj = new JsonParser().parse(content).getAsJsonObject();
            JsonArray items = obj.has("items") ? obj.getAsJsonArray("items") : null;
            if (items == null) return new ArrayList<>();

            List<IngredientItem> out = new ArrayList<>();
            for (JsonElement el : items) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString().trim() : null;
                if (name == null || name.isEmpty()) continue;
                String quantityStr = "";
                if (o.has("quantity") && !o.get("quantity").isJsonNull()) {
                    JsonElement q = o.get("quantity");
                    quantityStr = q.isJsonPrimitive() ? q.getAsJsonPrimitive().getAsString() : q.toString();
                }
                String unit = o.has("unit") && !o.get("unit").isJsonNull() ? o.get("unit").getAsString().trim() : "";
                String std = normalizeToKorean(name);
                out.add(new IngredientItem(std, quantityStr, unit));
            }
            return out;
        } catch (Exception e) {
            Log.e(TAG, "parseOpenAIResponse error", e);
            return new ArrayList<>();
        }
    }

    private static String normalizeToKorean(String raw) {
        if (raw == null) return "";
        String k = raw.toLowerCase(Locale.US).trim();
        Map<String, String> map = new HashMap<>();
        String[][] pairs = new String[][]{
                {"tomato","토마토"},{"cherry tomato","토마토"},
                {"onion","양파"},{"shallot","양파"},
                {"spring onion","대파"},{"green onion","대파"},{"scallion","대파"},{"leek","대파"},
                {"cucumber","오이"},{"zucchini","애호박"},{"courgette","애호박"},
                {"lettuce","상추"},{"romaine","상추"},{"cabbage","양배추"},{"napa cabbage","배추"},
                {"carrot","당근"},{"radish","무"},{"daikon","무"},
                {"garlic","마늘"},{"ginger","생강"},
                {"bell pepper","파프리카"},{"pepper","고추"},{"chili","고추"},{"chilli","고추"},
                {"eggplant","가지"},{"aubergine","가지"},
                {"potato","감자"},{"sweet potato","고구마"},
                {"broccoli","브로콜리"},{"cauliflower","콜리플라워"},
                {"spinach","시금치"},{"kale","케일"}
        };
        for (String[] p : pairs) map.put(p[0], p[1]);
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (k.contains(e.getKey())) return e.getValue();
        }
        return raw;
    }
}
