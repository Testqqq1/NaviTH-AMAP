package com.navith.amap.car;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/** Home, work and recent places saved on the phone and shown in the car. */
public final class SavedPlaces {
    private SavedPlaces() {}

    public static JSONObject shortcut(Context context, String key) {
        try {
            return new JSONObject(context.getSharedPreferences("navith_places", Context.MODE_PRIVATE)
                    .getString(key, ""));
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONArray list(Context context, String key) {
        try {
            return new JSONArray(context.getSharedPreferences("navith_places", Context.MODE_PRIVATE)
                    .getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void remember(Context context, double lat, double lon, String name) {
        if (name == null || name.isEmpty() || !Double.isFinite(lat) || !Double.isFinite(lon)) return;
        JSONArray old = list(context, "recents");
        JSONArray result = new JSONArray();
        result.put(record(lat, lon, name));
        for (int i = 0; i < old.length() && result.length() < 20; i++) {
            JSONObject item = old.optJSONObject(i);
            if (item == null) continue;
            if (Math.abs(item.optDouble("lat", 999) - lat) < 0.00001
                    && Math.abs(item.optDouble("lon", 999) - lon) < 0.00001) continue;
            result.put(item);
        }
        context.getSharedPreferences("navith_places", Context.MODE_PRIVATE)
                .edit().putString("recents", result.toString()).apply();
    }

    public static JSONObject record(double lat, double lon, String name) {
        JSONObject item = new JSONObject();
        try {
            item.put("title", name);
            item.put("lat", lat);
            item.put("lon", lon);
        } catch (Exception ignored) { }
        return item;
    }
}
