package com.example.chat.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PrivateChatGeofenceStore {

    public static final String EXTRA_ID_SALA_ORIGEN = "ID_SALA_ORIGEN";
    public static final String EXTRA_SALA_LATITUD = "SALA_LATITUD";
    public static final String EXTRA_SALA_LONGITUD = "SALA_LONGITUD";
    public static final String EXTRA_SALA_RADIO = "SALA_RADIO";

    private static final String PREF_NAME = "PrivateChatGeofencePrefs";

    private PrivateChatGeofenceStore() {
    }

    public static synchronized void save(
            Context context,
            int userOneId,
            int userTwoId,
            String salaOrigenId,
            double latitud,
            double longitud,
            double radioMetros
    ) {
        if (context == null || userOneId <= 0 || userTwoId <= 0 || salaOrigenId == null || salaOrigenId.trim().isEmpty()) {
            return;
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key(userOneId, userTwoId, "id_sala"), salaOrigenId)
                .putFloat(key(userOneId, userTwoId, "latitud"), (float) latitud)
                .putFloat(key(userOneId, userTwoId, "longitud"), (float) longitud)
                .putFloat(key(userOneId, userTwoId, "radio"), (float) radioMetros)
                .apply();
    }

    public static synchronized void attachExtrasIfAvailable(Context context, Intent intent, int userOneId, int userTwoId) {
        if (context == null || intent == null || userOneId <= 0 || userTwoId <= 0) {
            return;
        }

        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String salaOrigenId = pref.getString(key(userOneId, userTwoId, "id_sala"), "");
        if (salaOrigenId == null || salaOrigenId.trim().isEmpty()) {
            return;
        }

        intent.putExtra(EXTRA_ID_SALA_ORIGEN, salaOrigenId);
        intent.putExtra(EXTRA_SALA_LATITUD, (double) pref.getFloat(key(userOneId, userTwoId, "latitud"), 0f));
        intent.putExtra(EXTRA_SALA_LONGITUD, (double) pref.getFloat(key(userOneId, userTwoId, "longitud"), 0f));
        intent.putExtra(EXTRA_SALA_RADIO, (double) pref.getFloat(key(userOneId, userTwoId, "radio"), 0f));
    }

    public static synchronized void remove(Context context, int userOneId, int userTwoId) {
        if (context == null || userOneId <= 0 || userTwoId <= 0) {
            return;
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(key(userOneId, userTwoId, "id_sala"))
                .remove(key(userOneId, userTwoId, "latitud"))
                .remove(key(userOneId, userTwoId, "longitud"))
                .remove(key(userOneId, userTwoId, "radio"))
                .apply();
    }

    public static synchronized List<Integer> getOtherUserIdsForSala(Context context, int currentUserId, String salaOrigenId) {
        List<Integer> result = new ArrayList<>();
        if (context == null || currentUserId <= 0 || salaOrigenId == null || salaOrigenId.trim().isEmpty()) {
            return result;
        }

        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String expectedSala = salaOrigenId.trim();
        String suffix = "_id_sala";

        for (Map.Entry<String, ?> entry : pref.getAll().entrySet()) {
            String prefKey = entry.getKey();
            Object value = entry.getValue();
            if (prefKey == null || !prefKey.endsWith(suffix) || !(value instanceof String)) {
                continue;
            }
            if (!expectedSala.equals(((String) value).trim())) {
                continue;
            }

            String pairKey = prefKey.substring(0, prefKey.length() - suffix.length());
            String[] parts = pairKey.split("_");
            if (parts.length != 2) {
                continue;
            }

            try {
                int userOneId = Integer.parseInt(parts[0]);
                int userTwoId = Integer.parseInt(parts[1]);
                if (userOneId == currentUserId && userTwoId > 0) {
                    result.add(userTwoId);
                } else if (userTwoId == currentUserId && userOneId > 0) {
                    result.add(userOneId);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return result;
    }

    private static String key(int userOneId, int userTwoId, String suffix) {
        int min = Math.min(userOneId, userTwoId);
        int max = Math.max(userOneId, userTwoId);
        return min + "_" + max + "_" + suffix;
    }
}
