package com.smn.maratang;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

public interface WebcamService {

    @GET("snapshot")
    Call<ResponseBody> getSnapshot(); // JPEG 바이너리 응답
}
