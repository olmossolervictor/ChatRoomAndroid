package com.example.chat.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Map;

public final class PrivateChatClosureStore {

    private static final String PREF_NAME = "PrivateChatClosurePrefs";
    private static final long RETENTION_MS = 30L * 60L * 1000L;

    private PrivateChatClosureStore() {
    }

    public static synchronized void closeForThirtyMinutes(
            Context context,
            int userOneId,
            int userTwoId,
            String salaOrigenId,
            String message
    ) {
        if (context == null || userOneId <= 0 || userTwoId <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        try {
            JSONObject json = new JSONObject();
            json.put("salaOrigenId", salaOrigenId == null ? "" : salaOrigenId.trim());
            json.put("message", message == null ? "" : message.trim());
            json.put("closedAtMs", now);
            json.put("expiresAtMs", now + RETENTION_MS);

            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(key(userOneId, userTwoId), json.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static synchronized Closure getActiveClosure(Context context, int userOneId, int userTwoId) {
        if (context == null || userOneId <= 0 || userTwoId <= 0) {
            return null;
        }

        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String storageKey = key(userOneId, userTwoId);
        String raw = pref.getString(storageKey, "");
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject json = new JSONObject(raw);
            long expiresAtMs = json.optLong("expiresAtMs", 0L);
            if (expiresAtMs <= System.currentTimeMillis()) {
                pref.edit().remove(storageKey).apply();
                return null;
            }

            return new Closure(
                    json.optString("salaOrigenId", ""),
                    json.optString("message", ""),
                    json.optLong("closedAtMs", 0L),
                    expiresAtMs
            );
        } catch (Exception ignored) {
            pref.edit().remove(storageKey).apply();
            return null;
        }
    }

    public static synchronized boolean isExpired(Context context, int userOneId, int userTwoId) {
        if (context == null || userOneId <= 0 || userTwoId <= 0) {
            return false;
        }

        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String storageKey = key(userOneId, userTwoId);
        String raw = pref.getString(storageKey, "");
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }

        try {
            JSONObject json = new JSONObject(raw);
            long expiresAtMs = json.optLong("expiresAtMs", 0L);
            boolean expired = expiresAtMs > 0 && expiresAtMs <= System.currentTimeMillis();
            if (expired) {
                pref.edit().remove(storageKey).apply();
            }
            return expired;
        } catch (Exception ignored) {
            pref.edit().remove(storageKey).apply();
            return true;
        }
    }

    public static synchronized void remove(Context context, int userOneId, int userTwoId) {
        if (context == null || userOneId <= 0 || userTwoId <= 0) {
            return;
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(key(userOneId, userTwoId))
                .apply();
    }

    public static synchronized void removeExpired(Context context) {
        if (context == null) return;

        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, ?> entry : pref.getAll().entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof String)) continue;
            try {
                JSONObject json = new JSONObject((String) value);
                long expiresAtMs = json.optLong("expiresAtMs", 0L);
                if (expiresAtMs > 0 && expiresAtMs <= now) {
                    editor.remove(entry.getKey());
                }
            } catch (Exception ignored) {
                editor.remove(entry.getKey());
            }
        }

        editor.apply();
    }

    private static String key(int userOneId, int userTwoId) {
        int min = Math.min(userOneId, userTwoId);
        int max = Math.max(userOneId, userTwoId);
        return min + "_" + max;
    }

    public static final class Closure {
        private final String salaOrigenId;
        private final String message;
        private final long closedAtMs;
        private final long expiresAtMs;

        private Closure(String salaOrigenId, String message, long closedAtMs, long expiresAtMs) {
            this.salaOrigenId = salaOrigenId;
            this.message = message;
            this.closedAtMs = closedAtMs;
            this.expiresAtMs = expiresAtMs;
        }

        public String getSalaOrigenId() {
            return salaOrigenId;
        }

        public String getMessage() {
            return message;
        }

        public long getClosedAtMs() {
            return closedAtMs;
        }

        public long getExpiresAtMs() {
            return expiresAtMs;
        }
    }
}
