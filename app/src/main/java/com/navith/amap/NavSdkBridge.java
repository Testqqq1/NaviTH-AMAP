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
    /** Debug builds only. Start {@code NaviType.EMULATOR} after the next route success. */
    private static volatile boolean pendingEmulator;

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
        boolean fine=activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            ==PackageManager.PERMISSION_GRANTED;
        boolean coarse=activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            ==PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            show(activity,
                "ต้องอนุญาตตำแหน่ง",
                "การนำทางต้องเปิดสิทธิ์ตำแหน่งจากการตั้งค่า Android ก่อน ถ้ามีตัวเลือก ให้เลือกความแม่นยำสูง (Precise)");
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
                                if(pendingEmulator) startPendingEmulator(context);
                                break;
                            case "onCalculateRouteFailure":
                                pendingEmulator=false;
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
                                    int icon=asInt(info,"getIconType");
                                    NaviGuidanceStore.update(context,road,next,remaining,seconds,
                                        icon,exitLabel(info),asString(info,"getCurrentRoadName"));
                                }
                                break;
                            case "onServiceAreaUpdate":
                                if(args!=null && args.length>0 && args[0] instanceof Object[])
                                    publishTolls(context,(Object[])args[0]);
                                break;
                            case "onTrafficStatusUpdate":
                                publishTraffic(context);
                                break;
                            case "showLaneInfo":
                                if(args!=null) publishLanes(context,args);
                                break;
                            case "hideLaneInfo":
                                NaviGuidanceStore.clearLanes(context);
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

    /** Highway exit-sign text. Not a roundabout exit index; IconType has none. */
    private static String exitLabel(Object info) {
        try {
            Object exit=info.getClass().getMethod("getExitDirectionInfo").invoke(info);
            if(exit==null)return "";
            return com.navith.amap.car.AmapExitLabel.join(
                stringArray(exit,"getExitNameInfo"),stringArray(exit,"getDirectionInfo"));
        } catch(ReflectiveOperationException e) {
            return "";
        }
    }
    private static String[] stringArray(Object obj,String method) throws ReflectiveOperationException {
        Object value=obj.getClass().getMethod(method).invoke(obj);
        return value instanceof String[]?(String[])value:null;
    }

    /**
     * showLaneInfo(AMapLaneInfo) is the current callback. The deprecated
     * byte-array overload is accepted when that is what the SDK invokes.
     */
    private static void publishLanes(Context context,Object[] args) {
        if(args.length==1 && args[0]!=null) {
            int[] background=readIntArray(args[0],"backgroundLane");
            int[] front=readIntArray(args[0],"frontLane");
            if(background==null || front==null) {
                Log.d("NaviTH/Navigation","Lane callback has no background/front arrays");
                return;
            }
            int count=readIntField(args[0],"laneCount");
            int n=Math.min(background.length,front.length);
            if(count>0) n=Math.min(n,count);
            storeLanes(context,background,front,n);
            return;
        }
        if(args.length>=3 && args[1] instanceof byte[] && args[2] instanceof byte[]) {
            int[] background=unsigned((byte[])args[1]);
            int[] front=unsigned((byte[])args[2]);
            storeLanes(context,background,front,Math.min(background.length,front.length));
        }
    }
    private static void storeLanes(Context context,int[] background,int[] front,int count) {
        if(count<=0) {
            NaviGuidanceStore.clearLanes(context);
            return;
        }
        String encoded=com.navith.amap.car.AmapLaneMap.encode(background,front,count);
        if(encoded.isEmpty()) NaviGuidanceStore.clearLanes(context);
        else NaviGuidanceStore.setLanes(context,encoded);
    }
    private static int[] unsigned(byte[] raw) {
        int[] out=new int[raw.length];
        for(int i=0;i<raw.length;i++) out[i]=raw[i]&0xFF;
        return out;
    }
    private static Object readField(Object obj,String name) {
        try {return obj.getClass().getField(name).get(obj);}
        catch(ReflectiveOperationException ignored) {
            try {
                Field field=obj.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(obj);
            } catch(ReflectiveOperationException e) {return null;}
        }
    }
    private static int readIntField(Object obj,String name) {
        Object value=readField(obj,name);
        return value instanceof Number?((Number)value).intValue():-1;
    }
    private static int[] readIntArray(Object obj,String name) {
        Object value=readField(obj,name);
        if(value instanceof int[]) return (int[])value;
        if(value instanceof Integer[]) {
            Integer[] boxed=(Integer[])value;
            int[] out=new int[boxed.length];
            for(int i=0;i<boxed.length;i++) out[i]=boxed[i]==null?255:boxed[i];
            return out;
        }
        return null;
    }

    /** Toll gates only. Service areas and cameras stay off this panel. */
    private static void publishTolls(Context context, Object[] facilities) {
        int n=Math.min(facilities.length, 8);
        int[] types=new int[n];
        int[] distances=new int[n];
        String[] names=new String[n];
        int count=0;
        for(Object facility:facilities) {
            if(facility==null || count>=n) continue;
            types[count]=asInt(facility,"getType");
            distances[count]=asInt(facility,"getRemainDist");
            names[count]=asString(facility,"getName");
            count++;
        }
        if(count<n) {
            types=java.util.Arrays.copyOf(types,count);
            distances=java.util.Arrays.copyOf(distances,count);
            names=java.util.Arrays.copyOf(names,count);
        }
        NaviGuidanceStore.setTolls(context, com.navith.amap.car.CarHud.encodeTolls(
                com.navith.amap.car.CarHud.tolls(types,distances,names)));
    }

    private static void publishTraffic(Context context) {
        if(guidanceEngine==null) return;
        try {
            Object path=guidanceEngine.getClass().getMethod("getNaviPath").invoke(guidanceEngine);
            if(path==null) return;
            int total=asInt(path,"getAllLength");
            Object raw=path.getClass().getMethod("getTrafficStatuses").invoke(path);
            java.util.ArrayList<com.navith.amap.car.CarHud.Segment> segments=new java.util.ArrayList<>();
            if(raw instanceof List) {
                for(Object status:(List<?>)raw) {
                    if(status==null || segments.size()>=24) continue;
                    segments.add(new com.navith.amap.car.CarHud.Segment(
                            asInt(status,"getStatus"), asInt(status,"getLength")));
                }
            }
            NaviGuidanceStore.setTraffic(context, Math.max(0,total),
                    com.navith.amap.car.CarHud.encodeTraffic(segments));
        } catch(ReflectiveOperationException e) {
            Log.d("NaviTH/Navigation","Traffic status unavailable");
        }
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

    /**
     * Debug indoor navigation. Calculates a drive route on the shared AMAP engine
     * and starts {@code NaviType.EMULATOR} (2), which feeds the same listener the
     * car screen already watches. Release builds ignore the call.
     */
    public static void openEmulator(Activity activity, LatLng origin, LatLng destination) {
        if((activity.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)
            return;
        if(origin==null || destination==null) return;
        if(!isInstalled()) {
            show(activity,
                "Navigation SDK ยังไม่รวมอยู่ใน APK นี้",
                "นำทางจำลองต้องใช้ NaviTH Navigation Test APK");
            return;
        }
        if(!activity.getSharedPreferences(CONSENT, Context.MODE_PRIVATE).getBoolean("accepted", false)) {
            new AlertDialog.Builder(activity)
                .setTitle("อนุญาต AMAP Navigation SDK")
                .setMessage("โหมดจำลองจะขับตำแหน่งไปตามเส้นทางโดยไม่ใช้ GPS จริง เพื่อทดสอบการนำทางในอาคารและบน Android Auto")
                .setPositiveButton("ยินยอมและเริ่มจำลอง", (d, w) -> {
                    activity.getSharedPreferences(CONSENT, Context.MODE_PRIVATE).edit()
                        .putBoolean("accepted", true).apply();
                    startEmulator(activity, origin, destination);
                })
                .setNegativeButton("ยกเลิก", null)
                .show();
        } else startEmulator(activity, origin, destination);
    }

    private static void startEmulator(Activity activity, LatLng origin, LatLng destination) {
        try {
            Class<?> privacy = Class.forName("com.amap.api.navi.NaviSetting");
            privacy.getMethod("updatePrivacyShow", Context.class, boolean.class, boolean.class)
                .invoke(null, activity.getApplicationContext(), true, true);
            privacy.getMethod("updatePrivacyAgree", Context.class, boolean.class)
                .invoke(null, activity.getApplicationContext(), true);
            attachGuidanceListener(activity.getApplicationContext());
            if(guidanceEngine==null) {
                show(activity, "ยังเริ่มนำทางจำลองไม่ได้", "Navigation engine ไม่พร้อม");
                return;
            }
            Class<?> pointType=Class.forName("com.amap.api.navi.model.NaviLatLng");
            java.lang.reflect.Constructor<?> point=pointType.getConstructor(double.class, double.class);
            Object from=point.newInstance(origin.latitude, origin.longitude);
            Object to=point.newInstance(destination.latitude, destination.longitude);
            int strategy=10;
            try {
                strategy=Class.forName("com.amap.api.navi.enums.PathPlanningStrategy")
                    .getField("DRIVING_MULTIPLE_ROUTES_DEFAULT").getInt(null);
            } catch(ReflectiveOperationException ignored) { }
            pendingEmulator=true;
            NaviGuidanceStore.launching(activity.getApplicationContext());
            Object started=guidanceEngine.getClass()
                .getMethod("calculateDriveRoute", java.util.List.class, java.util.List.class, int.class)
                .invoke(guidanceEngine, java.util.Collections.singletonList(from),
                    java.util.Collections.singletonList(to), strategy);
            if(!(started instanceof Boolean) || !((Boolean)started)) {
                pendingEmulator=false;
                NaviGuidanceStore.failed(activity.getApplicationContext(),"คำนวณเส้นทางจำลองไม่สำเร็จ");
            }
        } catch(Throwable failure) {
            pendingEmulator=false;
            Log.e("NaviTH/Navigation", "Emulator navigation failed", failure);
            NaviGuidanceStore.failed(activity.getApplicationContext(),"เริ่มนำทางจำลองไม่สำเร็จ");
            show(activity, "ยังเริ่มนำทางจำลองไม่ได้",
                "ตรวจ Navigation SDK แล้วลองอีกครั้ง");
        }
    }

    /** NaviType.EMULATOR == 2. Called from the route-success callback. */
    private static void startPendingEmulator(Context context) {
        pendingEmulator=false;
        if(guidanceEngine==null) return;
        try {
            guidanceEngine.getClass().getMethod("startNavi", int.class).invoke(guidanceEngine, 2);
        } catch(Throwable failure) {
            Log.e("NaviTH/Navigation", "startNavi(EMULATOR) failed", failure);
            NaviGuidanceStore.failed(context, "เริ่มนำทางจำลองไม่สำเร็จ");
        }
    }

    /** Exit the official component; never fake the navigation state. */
    public static void stopNavigation(Activity activity) {
        pendingEmulator=false;
        boolean engineStopped=false;
        if(guidanceEngine!=null) {
            try {
                guidanceEngine.getClass().getMethod("stopNavi").invoke(guidanceEngine);
                engineStopped=true;
            } catch(Throwable ignored) { }
        }
        if(!isInstalled())return;
        try {
            Class<?> page=Class.forName("com.amap.api.navi.AmapNaviPage");
            Object singleton=page.getMethod("getInstance").invoke(null);
            page.getMethod("exitRouteActivity").invoke(singleton);
            NaviGuidanceStore.stopped(activity.getApplicationContext());
        }catch(Throwable e){
            Log.e("NaviTH/Navigation","Cannot exit official component",e);
            if(engineStopped) NaviGuidanceStore.stopped(activity.getApplicationContext());
            else show(activity,"หยุดนำทางไม่ได้","กรุณากลับไปที่หน้าของ AMAP Navigation และกดยุติการนำทาง");
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
