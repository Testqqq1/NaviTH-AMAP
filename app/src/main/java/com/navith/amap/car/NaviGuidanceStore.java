package com.navith.amap.car;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single-process, opt-in read-only handoff of REAL AMap Navigation SDK callbacks
 * from the phone navigation component to Android Auto.
 *
 * RouteSearch previews never populate this store. Stale guidance is discarded.
 */
public final class NaviGuidanceStore {
    public static final String EVENT = "navi_guidance";
    private NaviGuidanceStore() {}
    private static SharedPreferences prefs(Context c) { return TripStore.prefs(c); }

    public static void started(Context c) {
        prefs(c).edit().putBoolean("navi_running",true)
            .remove("navi_road").remove("navi_next_m").remove("navi_left_m")
            .remove("navi_left_s").putLong("navi_updated",0L)
            .putLong(EVENT,System.currentTimeMillis()).apply();
    }
    public static void stopped(Context c) {
        prefs(c).edit().putBoolean("navi_running",false)
            .remove("navi_road").remove("navi_next_m").remove("navi_left_m")
            .remove("navi_left_s").putLong("navi_updated",0L)
            .putLong(EVENT,System.currentTimeMillis()).apply();
    }
    /** Called only for NaviInfo from the vendor navigation engine while active. */
    public static void update(Context c,String nextRoad,int nextMeters,int leftMeters,int leftSeconds) {
        if (!prefs(c).getBoolean("navi_running",false))return;
        if(nextRoad==null || nextRoad.trim().isEmpty() || nextMeters<0 ||
            nextMeters>100000 || leftMeters<0 || leftMeters>500000 ||
            leftSeconds<0 || leftSeconds>172800) return;
        prefs(c).edit().putString("navi_road",nextRoad.substring(0,Math.min(120,nextRoad.length())))
            .putInt("navi_next_m",nextMeters)
            .putInt("navi_left_m",leftMeters)
            .putInt("navi_left_s",leftSeconds)
            .putLong("navi_updated",System.currentTimeMillis())
            .putLong(EVENT,System.currentTimeMillis()).apply();
    }
    public static Snapshot fresh(Context c) {
        SharedPreferences p=prefs(c);
        long age=System.currentTimeMillis()-p.getLong("navi_updated",0L);
        if(!p.getBoolean("navi_running",false) || age<0 || age>15000)return null;
        String road=p.getString("navi_road","");
        if(road==null || road.isEmpty())return null;
        return new Snapshot(road,p.getInt("navi_next_m",-1),
            p.getInt("navi_left_m",-1),p.getInt("navi_left_s",-1));
    }
    public static final class Snapshot {
        public final String nextRoad;
        public final int nextMeters,remainingMeters,remainingSeconds;
        private Snapshot(String road,int next,int remain,int seconds) {
            nextRoad=road;nextMeters=next;remainingMeters=remain;remainingSeconds=seconds;
        }
    }
}
