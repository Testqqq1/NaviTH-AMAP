package com.navith.amap.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.amap.api.maps.model.LatLng;
import com.navith.amap.car.AmapLaneMap;
import com.navith.amap.car.AmapManeuverMap;
import com.navith.amap.car.NaviGuidanceStore;
import com.navith.amap.car.TripStore;

/**
 * Debug-only injector for the Desktop Head Unit. It writes the same
 * {@link NaviGuidanceStore} fields the AMAP Navigation callbacks write, so the
 * real NavigationTemplate can be screenshotted without a live route.
 * This class lives in src/debug and is not in the release APK.
 */
public final class GuidancePreviewReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String scene = intent == null ? "left" : intent.getStringExtra("scene");
        if (scene == null || scene.isEmpty()) scene = "left";
        context.getSharedPreferences("navith_consent", Context.MODE_PRIVATE)
                .edit().putBoolean("consent", true).commit();
        context.getSharedPreferences("navith_view", Context.MODE_PRIVATE)
                .edit().putString("language", "th").commit();
        TripStore.setDestination(context, new LatLng(13.7460, 100.5348), "สยามพารากอน");
        TripStore.setLocation(context, new LatLng(13.7563, 100.5018));
        switch (scene) {
            case "right":
                drive(context, AmapManeuverMap.ICON_RIGHT, "พระรามที่ 4", 90, 2100, 360, "");
                lanes(context, new int[] {0, 3}, new int[] {255, 3});
                break;
            case "roundabout":
                drive(context, AmapManeuverMap.ICON_ENTRY_LEFT_RING_LEFT, "ราชดำริ", 240, 8000, 1100, "");
                NaviGuidanceStore.clearLanes(context);
                break;
            case "keep":
                drive(context, AmapManeuverMap.ICON_MERGE_LEFT, "ทางพิเศษเฉลิมมหานคร", 400, 15000, 900, "บางนา");
                lanes(context, new int[] {0, 0}, new int[] {0, 255});
                break;
            case "arrive":
                NaviGuidanceStore.stopped(context);
                NaviGuidanceStore.arrived(context);
                break;
            case "left":
            default:
                drive(context, AmapManeuverMap.ICON_LEFT, "สุขุมวิท", 180, 4200, 720, "");
                lanes(context, new int[] {0, 2, 3}, new int[] {255, 1, 255});
                break;
        }
    }

    private static void drive(Context context, int icon, String road, int nextMeters,
                              int leftMeters, int leftSeconds, String exit) {
        NaviGuidanceStore.started(context);
        NaviGuidanceStore.update(context, road, nextMeters, leftMeters, leftSeconds, icon, exit);
    }

    private static void lanes(Context context, int[] background, int[] front) {
        NaviGuidanceStore.setLanes(context, AmapLaneMap.encode(background, front, background.length));
    }
}
