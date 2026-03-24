package com.example.chat;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit = null;
    // En RetrofitClient.java
    //private static final String BASE_URL = "http://10.0.2.2:8080/api/";

    public static final String BASE_URL = "https://back-chatroom-qr-production.up.railway.app/api/";

    public static ChatApiServices getChatApiServices() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ChatApiServices.class);
    }
}
