package com.example.chat.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit = null;

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
