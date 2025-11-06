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

    public static void suggest(List<String> ingredients, Callback cb) {
        EXEC.execute(() -> {
            try {
                String apiKey = BuildConfig.GPT_KEY;
                if (apiKey == null || apiKey.isEmpty()) throw new IllegalStateException("GPT_KEY 미설정");
                String url = "https://api.openai.com/v1/chat/completions";

                String sys = "당신은 요리 추천 도우미입니다. 입력 재료만 이용 가능한 한국 요리를 추천하고, 각 항목에 제목, 대표 이미지 URL(https 스키마의 공개 접근 가능한 실제 이미지), 단계 수, 소요 시간을 제공합니다. 허위/로컬/데이터URL 금지.";
                String user = "재료:" + Arrays.toString(ingredients.toArray()) + "\nJSON 객체만 반환: {\"recipes\":[{\"title\":,\"imageUrl\":,\"stepsCount\":,\"time\":,\"ingredients\":[...]},...]} 추가 텍스트 금지.";

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
                    List<RecipeItem> out = parse(body);
                    cb.onResult(out);
                }
            } catch (Exception e) { Log.e("RecipeSuggester","error", e); cb.onError(e); }
        });
    }

    private static List<RecipeItem> parse(String resp) {
        List<RecipeItem> out = new ArrayList<>();
        try {
            JsonParser parser = new JsonParser();
            JsonObject root = parser.parse(resp).getAsJsonObject();
            JsonObject msg = root.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            String content = msg.get("content").getAsString();
            JsonObject obj = parser.parse(content).getAsJsonObject();
            JsonArray arr = obj.getAsJsonArray("recipes");
            if (arr == null) return out;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
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
            }
        } catch (Exception ignore) {}
        return out;
    }

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
