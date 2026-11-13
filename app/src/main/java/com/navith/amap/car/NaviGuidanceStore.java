package com.navith.amap.car;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single-process, opt-in read-only handoff of REAL AMap Navigation SDK callbacks
 * from the phone navigation component to Android Auto.
 *
 * Thailand guidance is published here by RouteGuide from a Route Search path.
 * Stale guidance is discarded.
 */
public final class NaviGuidanceStore {
    public static final String EVENT = "navi_guidance";
    private NaviGuidanceStore() {}
    private static SharedPreferences prefs(Context c) { return TripStore.prefs(c); }

    private static void phase(Context c, String name, boolean running, String message) {
        prefs(c).edit().putString("navi_phase",name)
            .putBoolean("navi_running",running)
            .putString("navi_message",message==null?"":message)
            .putLong("navi_phase_at",System.currentTimeMillis())
            .putLong(EVENT,System.currentTimeMillis()).apply();
    }
    public static String phase(Context c) {
        return prefs(c).getString("navi_phase","idle");
    }
    public static String message(Context c) {
        return prefs(c).getString("navi_message","");
    }
    public static boolean isNavigating(Context c) {
        return prefs(c).getBoolean("navi_running",false);
    }
    public static void launching(Context c) {
        prefs(c).edit().remove("navi_road").remove("navi_next_m")
            .remove("navi_left_m").remove("navi_left_s")
            .remove("navi_icon").remove("navi_exit").remove("navi_lanes")
            .remove("navi_cur_road").remove("navi_tolls").remove("navi_traffic").remove("navi_route_m")
            .putLong("navi_updated",0L).apply();
        phase(c,"planning",false,"กำลังเตรียมนำทาง");
    }
    public static void planned(Context c) {
        if(isNavigating(c)) phase(c,"navigating",true,"พร้อมนำทางต่อ");
        else phase(c,"ready",false,"คำนวณเส้นทางสำเร็จ");
    }
    public static void started(Context c) {
        phase(c,"navigating",true,"เริ่มนำทางด้วย GPS");
    }
    public static void rerouting(Context c) {
        if(!isNavigating(c))return;
        // Never leave the former turn on Android Auto while SDK recalculates.
        prefs(c).edit().remove("navi_road").remove("navi_next_m")
            .remove("navi_left_m").remove("navi_left_s")
            .remove("navi_icon").remove("navi_exit").remove("navi_lanes")
            .remove("navi_cur_road").remove("navi_tolls").remove("navi_traffic").remove("navi_route_m")
            .putLong("navi_updated",0L).apply();
        phase(c,"rerouting",true,"กำลังคำนวณเส้นทางใหม่");
    }
    public static void arrived(Context c) {
        phase(c,"arrived",false,"ถึงจุดหมายแล้ว");
    }
    public static void failed(Context c, String reason) {
        phase(c,"error",false,reason==null?"ไม่สามารถคำนวณเส้นทาง":reason.substring(0,Math.min(120,reason.length())));
    }
    public static void stopped(Context c) {
        phase(c,"idle",false,"");
        prefs(c).edit().remove("navi_road").remove("navi_next_m")
            .remove("navi_left_m").remove("navi_left_s")
            .remove("navi_icon").remove("navi_exit").remove("navi_lanes")
            .remove("navi_cur_road").remove("navi_tolls").remove("navi_traffic").remove("navi_route_m")
            .putLong("navi_updated",0L).apply();
    }
    /** Called only for NaviInfo from the vendor navigation engine while active. */
    public static void update(Context c,String nextRoad,int nextMeters,int leftMeters,int leftSeconds,
                              int iconType,String exitLabel,String currentRoad) {
        if (!prefs(c).getBoolean("navi_running",false))return;
        if(nextRoad==null || nextRoad.trim().isEmpty() || nextMeters<0 ||
            nextMeters>100000 || leftMeters<0 || leftMeters>500000 ||
            leftSeconds<0 || leftSeconds>172800) return;
        SharedPreferences p=prefs(c);
        int previousNext=p.contains("navi_next_m")?p.getInt("navi_next_m",-1):-1;
        // Distance jumping back up means the previous maneuver was passed.
        boolean passedManeuver=previousNext>=0 && nextMeters>previousNext+80;
        String exit=exitLabel==null?"":exitLabel.trim();
        if(exit.length()>80) exit=exit.substring(0,80);
        String current=currentRoad==null?"":currentRoad.trim();
        if(current.length()>80) current=current.substring(0,80);
        android.content.SharedPreferences.Editor editor=p.edit()
            .putString("navi_road",nextRoad.substring(0,Math.min(120,nextRoad.length())))
            .putString("navi_cur_road",current)
            .putInt("navi_next_m",nextMeters)
            .putInt("navi_left_m",leftMeters)
            .putInt("navi_left_s",leftSeconds)
            .putInt("navi_icon",iconType)
            .putString("navi_exit",exit)
            .putLong("navi_updated",System.currentTimeMillis())
            .putLong(EVENT,System.currentTimeMillis());
        if(passedManeuver) editor.remove("navi_lanes");
        editor.apply();
    }
    /**
     * Lane arrows from showLaneInfo. Stored even if navigation has not flipped
     * to running yet, because the callback can arrive before onStartNavi.
     * launching/stopped/rerouting clear them. The car screen only reads lanes
     * from a fresh navigating snapshot.
     */
    public static void setLanes(Context c,String encoded) {
        if(encoded==null || encoded.length()>400)return;
        prefs(c).edit().putString("navi_lanes",encoded)
            .putLong(EVENT,System.currentTimeMillis()).apply();
    }
    public static void clearLanes(Context c) {
        prefs(c).edit().remove("navi_lanes")
            .putLong(EVENT,System.currentTimeMillis()).apply();
    }
    /** Encoded toll gates from onServiceAreaUpdate. Empty hides the panel. */
    public static void setTolls(Context c, String encoded) {
        if(encoded==null || encoded.length()>300) return;
        prefs(c).edit().putString("navi_tolls", encoded)
            .putLong(EVENT, System.currentTimeMillis()).apply();
    }
    /** Traffic segments plus the full route length, from the navigation path. */
    public static void setTraffic(Context c, int totalMeters, String encoded) {
        if(encoded==null || encoded.length()>600 || totalMeters<0 || totalMeters>2000000) return;
        prefs(c).edit().putString("navi_traffic", encoded).putInt("navi_route_m", totalMeters)
            .putLong(EVENT, System.currentTimeMillis()).apply();
    }
    public static Snapshot fresh(Context c) {
        SharedPreferences p=prefs(c);
        long age=System.currentTimeMillis()-p.getLong("navi_updated",0L);
        if(!p.getBoolean("navi_running",false) || age<0 || age>15000)return null;
        String road=p.getString("navi_road","");
        if(road==null || road.isEmpty())return null;
        String exit=p.getString("navi_exit","");
        String lanes=p.getString("navi_lanes","");
        String current=p.getString("navi_cur_road","");
        String tolls=p.getString("navi_tolls","");
        String traffic=p.getString("navi_traffic","");
        return new Snapshot(road,p.getInt("navi_next_m",-1),
            p.getInt("navi_left_m",-1),p.getInt("navi_left_s",-1),
            p.getInt("navi_icon",-1),exit==null?"":exit,lanes==null?"":lanes,
            current==null?"":current,tolls==null?"":tolls,traffic==null?"":traffic,
            p.getInt("navi_route_m",0));
    }
    public static final class Snapshot {
        public final String nextRoad;
        public final int nextMeters,remainingMeters,remainingSeconds;
        /** AMAP IconType, or -1 when the callback did not include one. */
        public final int iconType;
        public final String exitLabel;
        public final String lanes;
        public final String currentRoad;
        public final String tolls;
        public final String traffic;
        public final int routeMeters;
        private Snapshot(String road,int next,int remain,int seconds,int icon,String exit,String laneText,
                         String current,String tollText,String trafficText,int routeMeters) {
            nextRoad=road;nextMeters=next;remainingMeters=remain;remainingSeconds=seconds;
            iconType=icon;exitLabel=exit;lanes=laneText;
            currentRoad=current;tolls=tollText;traffic=trafficText;this.routeMeters=routeMeters;
        }
    }
}
