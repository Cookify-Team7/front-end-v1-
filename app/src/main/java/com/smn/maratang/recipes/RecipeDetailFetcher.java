package com.smn.maratang.recipes;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smn.maratang.BuildConfig;

import java.util.ArrayList;
import java.util.List;
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

    public static void fetchSteps(String title, Callback cb) {
        EXEC.execute(() -> {
            try {
                String apiKey = BuildConfig.GPT_KEY;
                if (apiKey == null || apiKey.isEmpty()) throw new IllegalStateException("GPT_KEY 미설정");
                String url = "https://api.openai.com/v1/chat/completions";
                String sys = "당신은 전문 레시피 작성가입니다. 사용자의 요청에 따라 한국어로 단계별 요리법을 간결히 작성합니다.";
                String user = "요리 제목: " + title + "\n아래 JSON 객체만 반환: {\"steps\":[\"1단계 지시\", \"2단계 지시\", ...]} 추가 텍스트 금지.";

                JsonObject root = new JsonObject();
                root.addProperty("model", "gpt-4o");
                JsonObject rfmt = new JsonObject(); rfmt.addProperty("type","json_object"); root.add("response_format", rfmt);
                JsonArray messages = new JsonArray();
                JsonObject s = new JsonObject(); s.addProperty("role","system"); s.addProperty("content", sys); messages.add(s);
                JsonObject u = new JsonObject(); u.addProperty("role","user"); u.addProperty("content", user); messages.add(u);
                root.add("messages", messages);

                Request req = new Request.Builder().url(url)
                        .addHeader("Authorization","Bearer " + apiKey)
                        .addHeader("Content-Type","application/json")
                        .post(RequestBody.create(new Gson().toJson(root), JSON)).build();
                try (Response res = HTTP.newCall(req).execute()) {
                    String body = res.body()!=null? res.body().string():"";
                    if (!res.isSuccessful()) throw new RuntimeException("OpenAI 실패:"+res.code()+" "+body);
                    List<String> out = parse(body);
                    cb.onResult(out);
                }
            } catch (Exception e) { Log.e("RecipeDetailFetcher","error", e); cb.onError(e); }
        });
    }

    private static List<String> parse(String resp) {
        List<String> out = new ArrayList<>();
        try {
            JsonParser parser = new JsonParser();
            JsonObject root = parser.parse(resp).getAsJsonObject();
            JsonObject msg = root.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            String content = msg.get("content").getAsString();
            JsonObject obj = parser.parse(content).getAsJsonObject();
            JsonArray arr = obj.getAsJsonArray("steps");
            if (arr == null) return out;
            for (int i=0;i<arr.size();i++) out.add(arr.get(i).getAsString());
        } catch (Exception ignore) {}
        return out;
    }
}
