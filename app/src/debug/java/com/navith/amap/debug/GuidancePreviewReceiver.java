package com.navith.amap.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.amap.api.maps.model.LatLng;
import com.navith.amap.car.AmapLaneMap;
import com.navith.amap.car.AmapManeuverMap;
import com.navith.amap.car.NaviGuidanceStore;
import com.navith.amap.car.TripStore;

import java.util.Arrays;
import java.util.List;

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
                .edit().putString("language", "th")
                .putString("theme", "night")
                .putBoolean("view3d", true)
                .putBoolean("traffic", false)
                .commit();
        placeCar(context);
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

    /** A short Sukhumvit corridor so the car surface has a real polyline and heading. */
    private static void placeCar(Context context) {
        List<LatLng> path = Arrays.asList(
                new LatLng(13.7304, 100.5696),
                new LatLng(13.7332, 100.5652),
                new LatLng(13.7364, 100.5604),
                new LatLng(13.7398, 100.5548),
                new LatLng(13.7432, 100.5476),
                new LatLng(13.7460, 100.5348));
        Location fix = new Location("debug_preview");
        fix.setLatitude(path.get(0).latitude);
        fix.setLongitude(path.get(0).longitude);
        fix.setAccuracy(12f);
        fix.setSpeed(9f);
        fix.setBearing(305f);
        fix.setTime(System.currentTimeMillis());
        TripStore.setLocation(context, fix);
        TripStore.setDestination(context, path.get(path.size() - 1), "สยามพารากอน");
        TripStore.setRoute(context, path, 4200f, 720, "รถยนต์");
    }
}
