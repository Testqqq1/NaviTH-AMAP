package com.navith.amap.car;

import android.content.Context;
import android.content.SharedPreferences;

import com.amap.api.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Minimal shared state between the handheld and projected experiences.
 *  Coordinates, route summaries and small polyline only. Nothing is sent to a server by this class.
 */
public final class TripStore {
    public static final String PREFS = "navith_trip";
    public static final String DESTINATION = "destination";
    public static final String ROUTE = "route";
    public static final String LOCATION = "location";
    private TripStore() {}
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static boolean consented(Context c) {
        return c.getSharedPreferences("navith_consent", Context.MODE_PRIVATE).getBoolean("consent", false);
    }
    public static LatLng location(Context c) { return point(c, "lat", "lon"); }
    public static LatLng destination(Context c) { return point(c, "dest_lat", "dest_lon"); }
    public static String destinationName(Context c) { return prefs(c).getString("dest_name", ""); }
    public static String summary(Context c) { return prefs(c).getString("summary", ""); }

    private static LatLng point(Context c, String la, String lo) {
        SharedPreferences p = prefs(c);
        if (!p.contains(la) || !p.contains(lo)) return null;
        try { return new LatLng(Double.parseDouble(p.getString(la, "")), Double.parseDouble(p.getString(lo,""))); }
        catch (NumberFormatException e) { return null; }
    }
    public static void setLocation(Context c, LatLng l) {
        if (l != null) prefs(c).edit().putString("lat",Double.toString(l.latitude))
                .putString("lon",Double.toString(l.longitude)).putLong("location_time",System.currentTimeMillis()).apply();
    }
    public static boolean hasRecentLocation(Context c) {
        return location(c) != null && System.currentTimeMillis()-prefs(c).getLong("location_time",0L) < 120_000;
    }
    public static void setDestination(Context c, LatLng d, String name) {
        if (d == null) return;
        prefs(c).edit().putString("dest_lat",Double.toString(d.latitude))
                .putString("dest_lon",Double.toString(d.longitude))
                .putString("dest_name",name == null ? "จุดหมาย" : name)
                .remove("route_points").remove("summary").putLong(DESTINATION,System.currentTimeMillis()).apply();
    }
    public static void clearDestination(Context c) {
        prefs(c).edit().remove("dest_lat").remove("dest_lon").remove("dest_name")
                .remove("route_points").remove("summary").putLong(DESTINATION,System.currentTimeMillis()).apply();
    }
    public static void clearRoute(Context c) {
        prefs(c).edit().remove("route_points").remove("summary").putLong(ROUTE,System.currentTimeMillis()).apply();
    }
    public static void setRoute(Context c, List<LatLng> points, float meters, long seconds, String mode) {
        if (points == null || points.size() < 2) return;
        StringBuilder path = new StringBuilder();
        int stride = Math.max(1, (points.size()+499)/500); // Avoid unbounded preferences data.
        for (int i = 0; i < points.size(); i+=stride) {
            LatLng p=points.get(i);
            path.append(String.format(Locale.US,"%.6f,%.6f;",p.latitude,p.longitude));
        }
        LatLng last=points.get(points.size()-1);
        path.append(String.format(Locale.US,"%.6f,%.6f",last.latitude,last.longitude));
        String s = (mode == null ? "รถยนต์" : mode) + " · " + String.format(Locale.US,"%.1f กม.",meters/1000f)
                + " · " + Math.max(1,Math.round(seconds/60f)) + " นาที";
        prefs(c).edit().putString("route_points",path.toString()).putString("summary",s)
                .putLong(ROUTE,System.currentTimeMillis()).apply();
    }
    public static List<LatLng> route(Context c) {
        List<LatLng> result=new ArrayList<>();
        String raw=prefs(c).getString("route_points","");
        if (raw == null || raw.isEmpty()) return result;
        for (String part : raw.split(";")) {
            String[] xy=part.split(",");
            if (xy.length==2) try {result.add(new LatLng(Double.parseDouble(xy[0]),Double.parseDouble(xy[1])));}
            catch (NumberFormatException ignored) {}
        }
        return result;
    }
}
