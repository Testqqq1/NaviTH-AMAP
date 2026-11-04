package com.navith.amap;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.util.Locale;

/**
 * Local, privacy-minimizing diagnostics. Never persist raw coordinates, routes,
 * search terms, voice transcripts or tokens. All measurements are approximate.
 */
public final class NavigationDiagnostics {
    private static final String PREFS="navith_diagnostics";
    private static final String START="route_start_elapsed";
    private NavigationDiagnostics() {}
    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    public static void routeStart(Context c){
        prefs(c).edit().putLong(START,SystemClock.elapsedRealtime()).apply();
    }
    public static void routeResult(Context c,boolean ok) {
        SharedPreferences p=prefs(c);
        long start=p.getLong(START,0L),now=SystemClock.elapsedRealtime();
        long ms=start>0&&now>=start?now-start:-1L;
        SharedPreferences.Editor edit=p.edit()
            .putLong("route_start_elapsed",0L)
            .putLong("last_route_ms",ms);
        if(ok)edit.putInt("route_success",p.getInt("route_success",0)+1);
        else edit.putInt("route_fail",p.getInt("route_fail",0)+1);
        edit.apply();
    }
    public static void gpsAccuracy(Context c,float meters){
        if(!Float.isFinite(meters)||meters<=0f)return;
        prefs(c).edit().putFloat("last_accuracy_m",meters)
            .putLong("gps_at",System.currentTimeMillis()).apply();
    }
    public static void reroute(Context c){
        SharedPreferences p=prefs(c);
        p.edit().putInt("reroute_count",p.getInt("reroute_count",0)+1).apply();
    }
    public static String summary(Context c){
        SharedPreferences p=prefs(c);
        long elapsed=p.getLong("last_route_ms",-1L);
        float accuracy=p.getFloat("last_accuracy_m",-1f);
        return String.format(Locale.US,
            "Routing: %s\nSuccessful: %d   Failed: %d\nReroute attempts: %d\nGPS accuracy: %s\n\n"+
            "Stored only on device: aggregate counts, timing and accuracy. No GPS coordinates or search history.",
            elapsed<0?"not measured":elapsed+" ms",p.getInt("route_success",0),
            p.getInt("route_fail",0),p.getInt("reroute_count",0),
            accuracy<0?"not measured":String.format(Locale.US,"±%.0f m",accuracy));
    }
    public static void clear(Context c){prefs(c).edit().clear().apply();}
}
