package com.example.chat.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.chat.models.PrivateChatHistoryItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PrivateChatHistoryStore {

    public static final long TTL_MS = 2L * 60L * 60L * 1000L;

    private static final String PREF_NAME = "PrivateChatHistoryPrefs";
    private static final String KEY_PREFIX = "history_user_";

    private PrivateChatHistoryStore() {
    }

    public static synchronized void touchChat(Context context, int currentUserId, int otherUserId, String otherUserName) {
        if (context == null || currentUserId <= 0 || otherUserId <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        List<PrivateChatHistoryItem> items = getItemsWithoutPersist(context, currentUserId, now);

        boolean updated = false;
        String safeName = (otherUserName == null || otherUserName.trim().isEmpty())
                ? "Usuario " + otherUserId
                : otherUserName.trim();

        for (int i = 0; i < items.size(); i++) {
            PrivateChatHistoryItem item = items.get(i);
            if (item.getOtherUserId() == otherUserId) {
                items.set(i, new PrivateChatHistoryItem(otherUserId, safeName, now));
                updated = true;
                break;
            }
        }

        if (!updated) {
            items.add(new PrivateChatHistoryItem(otherUserId, safeName, now));
        }

        saveItems(context, currentUserId, items);
    }

    public static synchronized List<PrivateChatHistoryItem> getActiveHistory(Context context, int currentUserId) {
        if (context == null || currentUserId <= 0) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        List<PrivateChatHistoryItem> activeItems = getItemsWithoutPersist(context, currentUserId, now);
        saveItems(context, currentUserId, activeItems);
        return activeItems;
    }

    private static List<PrivateChatHistoryItem> getItemsWithoutPersist(Context context, int currentUserId, long now) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = pref.getString(getKey(currentUserId), "[]");

        List<PrivateChatHistoryItem> result = new ArrayList<>();

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;

                int otherUserId = obj.optInt("otherUserId", -1);
                String otherUserName = obj.optString("otherUserName", "");
                long lastInteractionMs = obj.optLong("lastInteractionMs", 0L);

                if (otherUserId <= 0 || lastInteractionMs <= 0) {
                    continue;
                }

                if (now - lastInteractionMs >= TTL_MS) {
                    continue;
                }

                if (otherUserName == null || otherUserName.trim().isEmpty()) {
                    otherUserName = "Usuario " + otherUserId;
                }

                result.add(new PrivateChatHistoryItem(otherUserId, otherUserName.trim(), lastInteractionMs));
            }
        } catch (Exception ignored) {
            // Si el JSON estuviera corrupto, devolvemos lista vacia y se reescribe limpia.
        }

        result.sort((a, b) -> Long.compare(b.getLastInteractionMs(), a.getLastInteractionMs()));
        return result;
    }

    private static void saveItems(Context context, int currentUserId, List<PrivateChatHistoryItem> items) {
        JSONArray array = new JSONArray();

        for (PrivateChatHistoryItem item : items) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("otherUserId", item.getOtherUserId());
                obj.put("otherUserName", item.getOtherUserName());
                obj.put("lastInteractionMs", item.getLastInteractionMs());
                array.put(obj);
            } catch (Exception ignored) {
            }
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(getKey(currentUserId), array.toString())
                .apply();
    }

    private static String getKey(int currentUserId) {
        return KEY_PREFIX + currentUserId;
    }
}
