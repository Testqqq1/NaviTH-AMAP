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
import java.lang.reflect.Proxy;
import android.util.Log;
import com.navith.amap.car.NaviGuidanceStore;
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
    private static Object guidanceListener;
    private static Object guidanceEngine;

    public static boolean isInstalled() {
        try {
            ClassLoader loader=NavSdkBridge.class.getClassLoader();
            Class.forName("com.amap.api.navi.AMapNavi",false,loader);
            Class.forName("com.amap.api.navi.AmapNaviPage",false,loader);
            Class.forName("com.amap.api.navi.AmapNaviParams",false,loader);
            Class.forName("com.amap.api.maps.model.Poi",false,loader);
            return true;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    public static void openRoute(Activity activity, LatLng destination, String name) {
        openRoute(activity,destination,name,null);
    }
    public static void openRoute(Activity activity, LatLng destination, String name, String poiId) {
        if (destination == null || !Double.isFinite(destination.latitude) ||
            !Double.isFinite(destination.longitude) ||
            Math.abs(destination.latitude)>90 || Math.abs(destination.longitude)>180)return;
        if (!isInstalled()) {
            show(activity,
                "Navigation SDK ยังไม่รวมอยู่ใน APK นี้",
                "ติดตั้ง NaviTH Navigation Test APK ที่ Build ด้วยชุด AMAP Navigation SDK แทนรุ่น Maps-only");
            return;
        }
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            show(activity,
                "ต้องอนุญาตตำแหน่ง",
                "การนำทางด้วย GPS ต้องเปิดสิทธิ์ตำแหน่งแบบแม่นยำจากการตั้งค่า Android ก่อน");
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
                    tryStart(activity, destination, name, poiId);
                })
                .setNegativeButton("ยกเลิก", null)
                .show();
        } else tryStart(activity, destination, name, poiId);
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

    /**
     * The AMAP component owns actual navigation. Observe its singleton in-process;
     * do not start a second navigation engine on the car screen.
     * If reflection fails on a particular SDK build, car stays in route-preview mode.
     */
    private static synchronized void attachGuidanceListener(Context context) {
        if(guidanceListener!=null)return;
        try {
            Class<?> engineType=Class.forName("com.amap.api.navi.AMapNavi");
            Class<?> callbackType=Class.forName("com.amap.api.navi.AMapNaviListener");
            Object engine=engineType.getMethod("getInstance",Context.class)
                    .invoke(null,context.getApplicationContext());
            Object listener=Proxy.newProxyInstance(callbackType.getClassLoader(),
                new Class<?>[]{callbackType},(proxy,method,args)->{
                    String callback=method.getName();
                    if("hashCode".equals(callback))return System.identityHashCode(proxy);
                    if("equals".equals(callback))return args!=null && proxy==args[0];
                    if("toString".equals(callback))return "NaviTH-AMAP-NavListener";
                    try {
                        switch(callback) {
                            case "onInitNaviFailure":
                                NaviGuidanceStore.failed(context,"เริ่ม Navigation Engine ไม่สำเร็จ");
                                break;
                            case "onCalculateRouteSuccess":
                                NaviGuidanceStore.planned(context);
                                break;
                            case "onCalculateRouteFailure":
                                int code=(args!=null && args.length>0 && args[0]!=null)
                                    ?asInt(args[0],"getErrorCode"):-1;
                                NaviGuidanceStore.failed(context,"คำนวณเส้นทางไม่สำเร็จ · AMAP "+code);
                                break;
                            case "onReCalculateRouteForYaw":
                            case "onReCalculateRouteForTrafficJam":
                                NaviGuidanceStore.rerouting(context);
                                break;
                            case "onStartNavi":
                                NaviGuidanceStore.started(context);
                                break;
                            case "onNaviInfoUpdate":
                                if(args!=null && args.length>0 && args[0]!=null) {
                                    Object info=args[0];
                                    String road=asString(info,"getNextRoadName");
                                    int next=asInt(info,"getCurStepRetainDistance");
                                    int remaining=asInt(info,"getPathRetainDistance");
                                    int seconds=asInt(info,"getPathRetainTime");
                                    NaviGuidanceStore.update(context,road,next,remaining,seconds);
                                }
                                break;
                            case "onArriveDestination":
                                NaviGuidanceStore.arrived(context);
                                break;
                            case "onEndEmulatorNavi":
                            case "onStopNavi":
                                NaviGuidanceStore.stopped(context);
                                break;
                            case "onLocationChange":
                                if(args!=null && args.length>0 && args[0]!=null) {
                                    shareNavigationPosition(context,args[0]);
                                }
                                break;
                        }
                    } catch(Exception ex) {
                        Log.w("NaviTH/Navigation","AMAP guidance callback unavailable",ex);
                    }
                    return null;
                });
            engineType.getMethod("addAMapNaviListener",callbackType)
                .invoke(engine,listener);
            guidanceListener=listener;guidanceEngine=engine;
        } catch(Throwable e) {
            Log.w("NaviTH/Navigation","Cannot attach vendor guidance; preview only",e);
        }
    }
    private static String asString(Object obj,String name) {
        try {Object v=obj.getClass().getMethod(name).invoke(obj);return v==null?"":v.toString();}
        catch(Exception e){return "";}
    }
    private static int asInt(Object obj,String name) {
        try {Object v=obj.getClass().getMethod(name).invoke(obj);return v instanceof Number?((Number)v).intValue():-1;}
        catch(Exception e){return -1;}
    }

    private static void shareNavigationPosition(Context context,Object location) {
        try {
            Object point=location.getClass().getMethod("getCoord").invoke(location);
            if(point==null)return;
            double lat=((Number)point.getClass().getMethod("getLatitude").invoke(point)).doubleValue();
            double lon=((Number)point.getClass().getMethod("getLongitude").invoke(point)).doubleValue();
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return;
            android.location.Location fix=new android.location.Location("amap_navigation");
            fix.setLatitude(lat);fix.setLongitude(lon);
            long now=System.currentTimeMillis();
            Object time=location.getClass().getMethod("getTime").invoke(location);
            long stamp=time instanceof Number?((Number)time).longValue():now;
            if(stamp>0L && Math.abs(now-stamp)>30000L)return;
            fix.setTime(stamp>0L?stamp:now);
            float accuracy=((Number)location.getClass().getMethod("getAccuracy")
                .invoke(location)).floatValue();
            if(Float.isFinite(accuracy) && accuracy>0 && accuracy<500f)
                fix.setAccuracy(accuracy);
            // AMAP Navigation reports km/h; Android Location expects m/s.
            float speedKmh=((Number)location.getClass().getMethod("getSpeed")
                .invoke(location)).floatValue();
            if(Float.isFinite(speedKmh) && speedKmh>=0 && speedKmh<350f)
                fix.setSpeed(speedKmh/3.6f);
            float bearing=((Number)location.getClass().getMethod("getBearing")
                .invoke(location)).floatValue();
            int type=((Number)location.getClass().getMethod("getLocationType")
                .invoke(location)).intValue();
            if(type==0 && Float.isFinite(bearing) && bearing>=0f &&
                bearing<=360f && fix.hasSpeed() && fix.getSpeed()>1.5f)
                fix.setBearing(bearing);
            com.navith.amap.car.TripStore.setLocation(context,fix);
        }catch(ReflectiveOperationException ex){
            Log.d("NaviTH/Navigation","Navi SDK location callback not available");
        }
    }

    /** Exit the official component; never fake the navigation state. */
    public static void stopNavigation(Activity activity) {
        if(!isInstalled())return;
        try {
            Class<?> page=Class.forName("com.amap.api.navi.AmapNaviPage");
            Object singleton=page.getMethod("getInstance").invoke(null);
            page.getMethod("exitRouteActivity").invoke(singleton);
            NaviGuidanceStore.stopped(activity.getApplicationContext());
        }catch(Throwable e){
            Log.e("NaviTH/Navigation","Cannot exit official component",e);
            show(activity,"หยุดนำทางไม่ได้","กรุณากลับไปที่หน้าของ AMAP Navigation และกดยุติการนำทาง");
        }
    }

    private static void tryStart(Activity activity, LatLng destination, String name, String poiId) {
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
            attachGuidanceListener(activity.getApplicationContext());
            Class<?> poiType = Class.forName("com.amap.api.maps.model.Poi");
            Constructor<?> poiConstructor = poiType.getConstructor(
                String.class, LatLng.class, String.class);
            Object target = poiConstructor.newInstance(
                (name == null || name.trim().isEmpty()) ? "จุดหมายปลายทาง" : name,
                destination, poiId==null || poiId.trim().isEmpty() ? null : poiId.trim());

            Class<?> navType = Class.forName("com.amap.api.navi.AmapNaviType");
            Object driving = navType.getField("DRIVER").get(null);
            Class<?> pageType = Class.forName("com.amap.api.navi.AmapPageType");
            // NAVI opens the vendor's turn-by-turn activity, not its ROUTE preview.
            Object planRoute = pageType.getField("NAVI").get(null);
            Class<?> params = Class.forName("com.amap.api.navi.AmapNaviParams");
            Constructor<?> constructor = params.getConstructor(
                poiType, List.class, poiType, navType, pageType);
            // No start POI means 'My Location' per vendor Navigation docs.
            Object config = constructor.newInstance(null, null, target, driving, planRoute);
            // Only SDK-supported settings are enabled, using the vendor's own
            // navigation component; data availability is determined by AMAP.
            setOptionalBoolean(params, config, "setUseInnerVoice", true);
            setOptionalBoolean(params, config, "setTrafficEnabled", true);
            setOptionalBoolean(params, config, "setNeedCalculateRouteWhenPresent", true);
            setOptionalBoolean(params, config, "setNeedDestroyDriveManagerInstanceWhenNaviExit", true);
            android.content.SharedPreferences views=activity.getSharedPreferences("navith_view",Context.MODE_PRIVATE);
            // Documented SDK mapping: 1=north-up, 2=car-heading-up.
            try { params.getMethod("setCarDirectionMode",Context.class,int.class)
                .invoke(config,activity.getApplicationContext(),views.getBoolean("view3d",true)?2:1); }
            catch(NoSuchMethodException ignored) { /* version-dependent */ }
            try { params.getMethod("setScaleAutoChangeEnable",Context.class,boolean.class)
                .invoke(config,activity.getApplicationContext(),true); }
            catch(NoSuchMethodException ignored) { /* SDK controls its own zoom */ }
            try {
                String theme=views.getString("theme","auto");
                int mode="day".equals(theme)?1:("night".equals(theme)?2:0);
                params.getMethod("setDayAndNightMode",Context.class,int.class)
                    .invoke(config,activity.getApplicationContext(),mode);
            }catch(NoSuchMethodException ignored) { }
            try { params.getMethod("setNaviMode",int.class).invoke(config,1); }
            catch(NoSuchMethodException ignored){ /* default is GPS navigation */ }
            setOptionalBoolean(params, config, "setShowExitNaviDialog", true);
            setOptionalBoolean(params, config, "setShowCrossImage", true);
            setOptionalBoolean(params, config, "setSecondActionVisible", true);
            setOptionalBoolean(params, config, "setMultipleRouteNaviMode", true);

            Class<?> page = Class.forName("com.amap.api.navi.AmapNaviPage");
            Object singleton = page.getMethod("getInstance").invoke(null);
            Class<?> callback = Class.forName("com.amap.api.navi.INaviInfoCallback");
            Object pageCallback=Proxy.newProxyInstance(callback.getClassLoader(),
                new Class<?>[]{callback},(proxy,method,args)->{
                    switch(method.getName()){
                        case "onExitPage":
                            NaviGuidanceStore.stopped(activity.getApplicationContext());break;
                        case "onGetNavigationText":
                            // AMAP's own voice engine handles announcements; don't duplicate TTS.
                            break;
                        case "onLocationChange":
                            if(args!=null && args.length>0 && args[0]!=null)
                                shareNavigationPosition(activity.getApplicationContext(),args[0]);
                            break;
                        case "hashCode":return System.identityHashCode(proxy);
                        case "equals":return args!=null && proxy==args[0];
                        case "toString":return "NaviTH-PageCallback";
                    }
                    return null;
                });
            NaviGuidanceStore.launching(activity.getApplicationContext());
            page.getMethod("showRouteActivity", Context.class, params, callback)
                .invoke(singleton, activity, config, pageCallback);
        } catch (Throwable failure) {
            NaviGuidanceStore.failed(activity.getApplicationContext(),"เปิด Navigation SDK ไม่สำเร็จ");
            android.util.Log.e("NaviTH/Navigation", "Navigation start failed", failure);
            show(activity, "ยังเริ่มการนำทางไม่ได้",
                "ตรวจสิทธิ์ AMAP Navigation ของ API Key, SHA-1, ความพร้อมของพื้นที่และบริการ SDK " +
                "หากยังใช้ไม่ได้ ให้กลับไปใช้ตัวอย่างเส้นทางบน NaviTH Maps");
        }
    }
}
