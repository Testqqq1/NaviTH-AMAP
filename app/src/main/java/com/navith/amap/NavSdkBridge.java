package com.navith.amap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;

import com.amap.api.maps.model.LatLng;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Optional AMAP Navigation 11.2.1001 bridge. The default Maps-only APK must
 * compile without Navigation classes; the separate Navigation CI build swaps
 * in AMAP's official COMBINED Map+Navi+Search+Location JAR and matching SOs.
 *
 * Reflective calls are intentionally limited to the vendor-documented
 * Navigation component; they never fabricate turns, guidance or routes.
 */
public final class NavSdkBridge {
    private static final String CONSENT = "navith_navi_consent";
    private NavSdkBridge() {}

    public static boolean isInstalled() {
        try {
            Class.forName("com.amap.api.navi.AMapNavi");
            Class.forName("com.amap.api.navi.AmapNaviPage");
            Class.forName("com.amap.api.navi.AmapNaviParams");
            Class.forName("com.amap.api.maps.model.Poi");
            return true;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    public static void openRoute(Activity activity, LatLng destination, String name) {
        if (destination == null) return;
        if (!isInstalled()) {
            show(activity,
                "Navigation SDK ยังไม่รวมอยู่ใน APK นี้",
                "ติดตั้ง NaviTH Navigation Test APK ที่ Build ด้วยชุด AMAP Navigation SDK แทนรุ่น Maps-only");
            return;
        }
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            show(activity,
                "ต้องอนุญาตตำแหน่ง",
                "กรุณาอนุญาตตำแหน่งแบบแม่นยำก่อนใช้งานการนำทาง");
            return;
        }
        if (!activity.getSharedPreferences(CONSENT, Context.MODE_PRIVATE).getBoolean("accepted", false)) {
            new AlertDialog.Builder(activity)
                .setTitle("อนุญาต AMAP Navigation SDK")
                .setMessage(
                    "โหมดนำทางจะใช้บริการ AMAP Navigation เพิ่มจากแผนที่ ค้นหาและระบุตำแหน่งที่ได้ยินยอมแล้ว " +
                    "ข้อมูลตำแหน่งแบบต่อเนื่อง อุปกรณ์ เครือข่าย เส้นทาง และคำค้นหาอาจถูกส่งไปยังผู้ให้บริการตามนโยบาย AMAP\n\n" +
                    "ควรทดสอบความถูกต้องของถนนและเลนในประเทศไทยก่อนใช้นำทางบนถนนจริง " +
                    "แอปนี้ยังไม่ได้ผ่านการทดสอบบน Android Auto\n\n" +
                    "ดูรายละเอียดนโยบายได้ที่ mapsplatform.opnavi.com")
                .setPositiveButton("ยินยอมและเปิดหน้านำทาง", (d, w) -> {
                    activity.getSharedPreferences(CONSENT, Context.MODE_PRIVATE).edit()
                        .putBoolean("accepted", true).apply();
                    tryStart(activity, destination, name);
                })
                .setNegativeButton("ยกเลิก", null)
                .show();
        } else tryStart(activity, destination, name);
    }

    private static void show(Activity activity, String title, String message) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity).setTitle(title).setMessage(message)
            .setPositiveButton("ตกลง", null).show();
    }

    private static void setOptionalBoolean(Class<?> type, Object config, String method, boolean value) {
        try {
            type.getMethod(method, boolean.class).invoke(config, value);
        } catch (ReflectiveOperationException ignored) {
            // Settings vary by Navigation SDK release.
        }
    }

    private static void tryStart(Activity activity, LatLng destination, String name) {
        try {
            // Privacy calls must precede creating a Navigation SDK singleton/page.
            // NaviSetting is the documented AMAP Navigation 8.1+ API holder.
            Class<?> privacy = Class.forName("com.amap.api.navi.NaviSetting");
            Method show = privacy.getMethod("updatePrivacyShow",
                Context.class, boolean.class, boolean.class);
            Method agree = privacy.getMethod("updatePrivacyAgree",
                Context.class, boolean.class);
            show.invoke(null, activity.getApplicationContext(), true, true);
            agree.invoke(null, activity.getApplicationContext(), true);
            try {
                privacy.getMethod("updateCountryCode", String.class).invoke(null, "TH");
            } catch (NoSuchMethodException ignored) {
                // Older vendor SDK packages do not expose this method.
            }

            // Manifest already carries AMAP_KEY; no hardcoded credential here.
            Class<?> poiType = Class.forName("com.amap.api.maps.model.Poi");
            Constructor<?> poiConstructor = poiType.getConstructor(
                String.class, LatLng.class, String.class);
            Object target = poiConstructor.newInstance(
                (name == null || name.trim().isEmpty()) ? "จุดหมายปลายทาง" : name,
                destination, null);

            Class<?> navType = Class.forName("com.amap.api.navi.AmapNaviType");
            Object driving = navType.getField("DRIVER").get(null);
            Class<?> pageType = Class.forName("com.amap.api.navi.AmapPageType");
            Object planRoute = pageType.getField("ROUTE").get(null);
            Class<?> params = Class.forName("com.amap.api.navi.AmapNaviParams");
            Constructor<?> constructor = params.getConstructor(
                poiType, List.class, poiType, navType, pageType);
            // No start POI means 'My Location' per vendor Navigation docs.
            Object config = constructor.newInstance(null, null, target, driving, planRoute);
            // Only SDK-supported settings are enabled, using the vendor's own
            // navigation component; data availability is determined by AMAP.
            setOptionalBoolean(params, config, "setUseInnerVoice", true);
            setOptionalBoolean(params, config, "setTrafficEnabled", true);
            setOptionalBoolean(params, config, "setShowCrossImage", true);
            setOptionalBoolean(params, config, "setSecondActionVisible", true);
            setOptionalBoolean(params, config, "setMultipleRouteNaviMode", true);

            Class<?> page = Class.forName("com.amap.api.navi.AmapNaviPage");
            Object singleton = page.getMethod("getInstance").invoke(null);
            Class<?> callback = Class.forName("com.amap.api.navi.INaviInfoCallback");
            page.getMethod("showRouteActivity", Context.class, params, callback)
                .invoke(singleton, activity, config, null);
        } catch (Throwable failure) {
            android.util.Log.e("NaviTH/Navigation", "Navigation start failed", failure);
            show(activity, "ยังเริ่มการนำทางไม่ได้",
                "ตรวจสิทธิ์ AMAP Navigation ของ API Key, SHA-1, ความพร้อมของพื้นที่และบริการ SDK " +
                "หากยังใช้ไม่ได้ ให้กลับไปใช้ตัวอย่างเส้นทางบน NaviTH Maps");
        }
    }
}
