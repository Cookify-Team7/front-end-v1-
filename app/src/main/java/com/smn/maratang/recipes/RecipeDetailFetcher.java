package com.smn.maratang.recipes;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smn.maratang.BuildConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RecipeDetailFetcher {
    public interface Callback { void onResult(List<String> steps); void onError(Exception e); }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .build();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String TAG = "RecipeDetailFetcher";

    public static void fetchSteps(String title, Callback cb) {
        EXEC.execute(() -> {
            long tStart = System.currentTimeMillis();
            String correlationId = UUID.randomUUID().toString();
            Log.d(TAG, prefix(correlationId) + "fetchSteps() start title='" + title + "'");
            try {
                String apiKey = BuildConfig.GPT_KEY;
                if (apiKey == null || apiKey.isEmpty()) throw new IllegalStateException("GPT_KEY 미설정");
                String url = "https://api.openai.com/v1/chat/completions";
                String sys = "당신은 전문 레시피 작성가입니다. 사용자의 요청에 따라 한국어로 단계별 요리법을 간결히 작성합니다.";
                String user = "요리 제목: " + title + "\n아래 JSON 객체만 반환: {\\\"steps\\\":[\\\"1단계 지시\\\", \\\"2단계 지시\\\", ...]} 추가 텍스트 금지.";

                JsonObject root = new JsonObject();
                root.addProperty("model", "gpt-4o");
                JsonObject rfmt = new JsonObject(); rfmt.addProperty("type","json_object"); root.add("response_format", rfmt);
                com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
                JsonObject s = new JsonObject(); s.addProperty("role","system"); s.addProperty("content", sys); messages.add(s);
                JsonObject u = new JsonObject(); u.addProperty("role","user"); u.addProperty("content", user); messages.add(u);
                root.add("messages", messages);

                String requestJson = new Gson().toJson(root);
                Log.d(TAG, prefix(correlationId) + "Request JSON length=" + requestJson.length());
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, prefix(correlationId) + "Request JSON snippet=" + snippet(requestJson));
                }

                Request req = new Request.Builder().url(url)
                        .addHeader("Authorization","Bearer " + apiKey)
                        .addHeader("Content-Type","application/json")
                        .post(RequestBody.create(requestJson, JSON)).build();
                try (Response res = HTTP.newCall(req).execute()) {
                    long tNet = System.currentTimeMillis();
                    String body = res.body()!=null? res.body().string():"";
                    Log.d(TAG, prefix(correlationId) + "HTTP status=" + res.code() + " took=" + (tNet - tStart) + "ms bodyLength=" + body.length());
                    if (BuildConfig.DEBUG) Log.v(TAG, prefix(correlationId) + "Response body snippet=" + snippet(body));
                    if (!res.isSuccessful()) {
                        Log.e(TAG, prefix(correlationId) + "OpenAI 실패 code=" + res.code() + " rawBody=" + snippet(body));
                        throw new RuntimeException("OpenAI 실패:"+res.code());
                    }
                    long tParseStart = System.currentTimeMillis();
                    List<String> out = parse(body, correlationId);
                    long tParseEnd = System.currentTimeMillis();
                    Log.d(TAG, prefix(correlationId) + "Parsed steps=" + out.size() + " parseTime=" + (tParseEnd - tParseStart) + "ms total=" + (tParseEnd - tStart) + "ms");
                    cb.onResult(out);
                }
            } catch (Exception e) {
                Log.e(TAG, prefix(correlationId) + "error msg=" + e.getMessage(), e);
                cb.onError(e);
            }
        });
    }

    private static List<String> parse(String resp, String correlationId) {
        List<String> out = new ArrayList<>();
        try {
            JsonParser parser = new JsonParser();
            JsonObject root = parser.parse(resp).getAsJsonObject();
            JsonObject msg = root.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            String content = msg.get("content").getAsString();
            Log.d(TAG, prefix(correlationId) + "content length=" + (content!=null?content.length():0));
            JsonObject obj = parser.parse(content).getAsJsonObject();
            JsonArray arr = obj.getAsJsonArray("steps");
            if (arr == null) {
                Log.w(TAG, prefix(correlationId) + "steps 배열 없음");
                return out;
            }
            for (int i=0;i<arr.size();i++) {
                String step = arr.get(i).getAsString();
                out.add(step);
                if (i < 3) { // 앞부분 일부만 로그
                    Log.d(TAG, prefix(correlationId) + "step["+i+"]=" + step);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, prefix(correlationId) + "parse 오류: " + e.getMessage(), e);
        }
        return out;
    }

    private static String snippet(String s) {
        if (s == null) return "null";
        int max = 500;
        String cut = s.length() > max ? s.substring(0, max) + "..." : s;
        return cut.replace('\n',' ');
    }
    private static String prefix(String id) { return "[req=" + id + "] "; }
}
