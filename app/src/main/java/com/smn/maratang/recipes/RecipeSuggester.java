package com.smn.maratang.recipes;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smn.maratang.BuildConfig;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
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

/**
 * OpenAI GPT API로 재료 목록을 전달해 만들 수 있는 요리 추천을 가져온다.
 * item_recipes.xml 구조에 맞춰 title/image/steps/time을 채운다.
 */
public class RecipeSuggester {
    public interface Callback { void onResult(List<RecipeItem> items); void onError(Exception e); }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .build();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String TAG = "RecipeSuggester";

    public static void suggest(List<String> ingredients, Callback cb) {
        EXEC.execute(() -> {
            long tStart = System.currentTimeMillis();
            String correlationId = UUID.randomUUID().toString();
            Log.d(TAG, logPrefix(correlationId) + "suggest() start ingredients=" + ingredients + " size=" + ingredients.size());
            try {
                String apiKey = BuildConfig.GPT_KEY;
                if (apiKey == null || apiKey.isEmpty()) throw new IllegalStateException("GPT_KEY 미설정");
                String url = "https://api.openai.com/v1/chat/completions";

                String sys = "당신은 요리 추천 도우미입니다. 입력 재료만 이용 가능한 한국 요리를 추천하고, 각 항목에 제목, 대표 이미지 URL(https 스키마의 공개 접근 가능한 실제 이미지), 단계 수, 소요 시간을 제공합니다. 허위/로컬/데이터URL 금지.";
                String user = "재료:" + Arrays.toString(ingredients.toArray()) + "\nJSON 객체만 반환: {\\\"recipes\\\":[{\\\"title\\\":,\\\"imageUrl\\\":,\\\"stepsCount\\\":,\\\"time\\\":,\\\"ingredients\\\":[...]},...]} 추가 텍스트 금지.";

                JsonObject root = new JsonObject();
                root.addProperty("model", "gpt-4o");
                JsonObject rfmt = new JsonObject(); rfmt.addProperty("type","json_object"); root.add("response_format", rfmt);
                JsonArray messages = new JsonArray();
                JsonObject s = new JsonObject(); s.addProperty("role","system"); s.addProperty("content", sys); messages.add(s);
                JsonObject u = new JsonObject(); u.addProperty("role","user"); u.addProperty("content", user); messages.add(u);
                root.add("messages", messages);

                String requestJson = new Gson().toJson(root);
                Log.d(TAG, logPrefix(correlationId) + "Request JSON length=" + requestJson.length());
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, logPrefix(correlationId) + "Request JSON snippet=" + safeSnippet(requestJson));
                }

                Request req = new Request.Builder().url(url)
                        .addHeader("Authorization","Bearer " + apiKey)
                        .addHeader("Content-Type","application/json")
                        .post(RequestBody.create(requestJson, JSON)).build();

                try (Response res = HTTP.newCall(req).execute()) {
                    long tNet = System.currentTimeMillis();
                    String body = res.body()!=null? res.body().string():"";
                    Log.d(TAG, logPrefix(correlationId) + "HTTP status=" + res.code() + " took=" + (tNet - tStart) + "ms bodyLength=" + body.length());
                    if (BuildConfig.DEBUG) {
                        Log.v(TAG, logPrefix(correlationId) + "Response body snippet=" + safeSnippet(body));
                    }
                    if (!res.isSuccessful()) {
                        Log.e(TAG, logPrefix(correlationId) + "OpenAI 실패 code=" + res.code() + " rawBody=" + safeSnippet(body));
                        throw new RuntimeException("OpenAI 실패:"+res.code());
                    }
                    long tParseStart = System.currentTimeMillis();
                    List<RecipeItem> out = parse(body, correlationId);
                    long tParseEnd = System.currentTimeMillis();
                    Log.d(TAG, logPrefix(correlationId) + "Parsed items=" + out.size() + " parseTime=" + (tParseEnd - tParseStart) + "ms total=" + (tParseEnd - tStart) + "ms");
                    cb.onResult(out);
                }
            } catch (Exception e) {
                Log.e(TAG, logPrefix(correlationId) + "error msg=" + e.getMessage(), e);
                cb.onError(e);
            }
        });
    }

    // 기존 parse를 확장하여 단계별 로깅
    private static List<RecipeItem> parse(String resp, String correlationId) {
        List<RecipeItem> out = new ArrayList<>();
        try {
            JsonParser parser = new JsonParser();
            JsonObject root = parser.parse(resp).getAsJsonObject();
            JsonObject msg = root.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            String content = msg.get("content").getAsString();
            Log.d(TAG, logPrefix(correlationId) + "content length=" + (content!=null?content.length():0));
            JsonObject obj = parser.parse(content).getAsJsonObject();
            JsonArray arr = obj.getAsJsonArray("recipes");
            if (arr == null) {
                Log.w(TAG, logPrefix(correlationId) + "recipes 배열 없음");
                return out;
            }
            int idx = 0;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    Log.w(TAG, logPrefix(correlationId) + "element " + idx + " not object" );
                    idx++;
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                String title = getString(o, "title");
                String image = getString(o, "imageUrl");
                if ((image == null || image.isEmpty()) && o.has("image")) image = getString(o, "image");
                int steps = o.has("stepsCount") && o.get("stepsCount").isJsonPrimitive()? o.get("stepsCount").getAsInt() : 0;
                String time = getString(o, "time");
                List<String> ings = new ArrayList<>();
                if (o.has("ingredients") && o.get("ingredients").isJsonArray()) {
                    for (JsonElement ie : o.getAsJsonArray("ingredients")) ings.add(ie.getAsString());
                }
                String finalImage = safeImageUrl(title, image);
                out.add(new RecipeItem(title!=null?title:"요리", finalImage, steps, time!=null?time:"", ings));
                Log.d(TAG, logPrefix(correlationId) + "Parsed recipe idx=" + idx + " title=" + title + " stepsCount=" + steps + " ingredients=" + ings.size());
                idx++;
            }
        } catch (Exception e) {
            Log.e(TAG, logPrefix(correlationId) + "parse 오류: " + e.getMessage(), e);
        }
        return out;
    }

    private static String safeSnippet(String s) {
        if (s == null) return "null";
        int max = 500; // 로그 과다 방지
        String cut = s.length() > max ? s.substring(0, max) + "..." : s;
        // 개행 치환으로 단일 라인 유지
        return cut.replace('\n',' ');
    }

    private static String logPrefix(String correlationId) { return "[req=" + correlationId + "] "; }

    private static String getString(JsonObject o, String key) {
        try { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null; }
        catch (Exception e) { return null; }
    }

    // GPT가 유효한 URL을 제공하지 않으면 Unsplash 대체 이미지로 보정
    private static String safeImageUrl(String title, String url) {
        if (url != null) {
            String u = url.trim();
            if (u.startsWith("http://") || u.startsWith("https://")) return u;
        }
        String q = (title==null||title.isEmpty())? "food" : title;
        try { q = URLEncoder.encode(q, "UTF-8"); } catch (Exception ignored) {}
        return "https://source.unsplash.com/600x600/?" + q;
    }
}
