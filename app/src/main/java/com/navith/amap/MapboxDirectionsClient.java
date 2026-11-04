package com.navith.amap;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.amap.api.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Mapbox Directions v5 networking only. No Mapbox Navigation SDK is bundled.
 * No approximate/fabricated routes: all geometry and maneuvers originate from
 * a successful Mapbox response. Calls must follow the separate Mapbox consent.
 */
public final class MapboxDirectionsClient {
    private static final String BASE = "https://api.mapbox.com/directions/v5/mapbox/";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static final class Step {
        public final LatLng point;
        public final String instruction;
        public final String type;
        public final double distance;
        Step(LatLng p, String text, String kind, double meters) {
            point=p;instruction=text;type=kind;distance=meters;
        }
    }
    public static final class Route {
        public final List<LatLng> geometry;
        public final List<Step> steps;
        public final float meters;
        public final long seconds;
        Route(List<LatLng> geometry, List<Step> steps, float meters, long seconds) {
            this.geometry=Collections.unmodifiableList(geometry);
            this.steps=Collections.unmodifiableList(steps);
            this.meters=meters;this.seconds=seconds;
        }
    }
    public interface Callback {
        void success(List<Route> routes);
        void failure(String message);
    }
    private MapboxDirectionsClient() {}

    public static boolean configured(Context c) {
        String token=c.getString(R.string.mapbox_access_token);
        return token!=null && token.trim().startsWith("pk.") && token.trim().length()>20;
    }
    public static boolean consented(Context c) {
        return c.getSharedPreferences("mapbox_consent",Context.MODE_PRIVATE)
                .getBoolean("accepted",false);
    }

    public static void request(Context context, LatLng start, LatLng end,
                               boolean walking, Callback callback) {
        request(context,start,end,walking?"walking":"driving-traffic",callback);
    }

    public static void request(Context context, LatLng start, LatLng end,
                               String travelMode, Callback callback) {
        final String token=context.getString(R.string.mapbox_access_token).trim();
        if (!configured(context)) {
            MAIN.post(()->callback.failure("MAPBOX_PUBLIC_TOKEN is not configured"));
            return;
        }
        if (!consented(context)) {
            MAIN.post(()->callback.failure("Mapbox privacy consent required"));
            return;
        }
        if (!valid(start) || !valid(end)) {
            MAIN.post(()->callback.failure("Invalid route coordinates"));
            return;
        }
        new Thread(()->{
            HttpURLConnection conn=null;
            try {
                String coordinates=String.format(Locale.US,"%.6f,%.6f;%.6f,%.6f",
                    start.longitude,start.latitude,end.longitude,end.latitude);
                String profile="walking".equals(travelMode)?"walking":
                    "cycling".equals(travelMode)?"cycling":
                    "driving".equals(travelMode)?"driving":"driving-traffic";
                String url=BASE+profile+"/"+coordinates+"?steps=true&geometries=geojson"
                    +"&overview=full&alternatives=true&language=th&voice_units=metric";
                if(profile.startsWith("driving")) {
                    android.content.SharedPreferences prefs=context.getSharedPreferences(
                        "navith_view",Context.MODE_PRIVATE);
                    List<String> exclude=new ArrayList<>();
                    if(prefs.getBoolean("avoid_tolls",false))exclude.add("toll");
                    if(prefs.getBoolean("avoid_highway",false))exclude.add("motorway");
                    if(!exclude.isEmpty())url+="&exclude="+String.join(",",exclude);
                }
                url+="&access_token="+URLEncoder.encode(token,"UTF-8");
                conn=(HttpURLConnection)new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(false);
                int code=conn.getResponseCode();
                if(code!=200)throw new IOException("Mapbox HTTP "+code);
                StringBuilder body=new StringBuilder();
                try(InputStream input=conn.getInputStream();
                    BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))) {
                    char[] buffer=new char[8192];
                    int count;
                    while((count=reader.read(buffer))!=-1) {
                        body.append(buffer,0,count);
                        if(body.length()>4_000_000)throw new IOException("Response too large");
                    }
                }
                JSONObject payload=new JSONObject(body.toString());
                if(!"Ok".equals(payload.optString("code")))
                    throw new IOException("Mapbox: "+payload.optString("code","NoRoute"));
                JSONArray routes=payload.optJSONArray("routes");
                if(routes==null || routes.length()==0)throw new IOException("No route found");
                List<Route> parsed=new ArrayList<>();
                for(int r=0;r<Math.min(3,routes.length());r++){
                    JSONObject candidate=routes.getJSONObject(r);
                    JSONArray coords=candidate.getJSONObject("geometry").getJSONArray("coordinates");
                    List<LatLng> geometry=new ArrayList<>();
                    for(int i=0;i<coords.length() && i<30000;i++){
                        JSONArray xy=coords.getJSONArray(i);
                        LatLng p=new LatLng(xy.getDouble(1),xy.getDouble(0));
                        if(valid(p))geometry.add(p);
                    }
                    if(geometry.size()<2)continue;
                    List<Step> steps=new ArrayList<>();
                    JSONArray legs=candidate.optJSONArray("legs");
                    if(legs!=null)for(int l=0;l<legs.length();l++) {
                        JSONArray maneuvers=legs.getJSONObject(l).optJSONArray("steps");
                        if(maneuvers==null)continue;
                        for(int s=0;s<maneuvers.length();s++){
                            JSONObject step=maneuvers.getJSONObject(s);
                            JSONObject maneuver=step.optJSONObject("maneuver");
                            if(maneuver==null)continue;
                            JSONArray xy=maneuver.optJSONArray("location");
                            if(xy==null || xy.length()<2)continue;
                            LatLng point=new LatLng(xy.getDouble(1),xy.getDouble(0));
                            if(!valid(point))continue;
                            String instruction=maneuver.optString("instruction","");
                            steps.add(new Step(point,instruction,
                                maneuver.optString("type",""),step.optDouble("distance",0)));
                        }
                    }
                    parsed.add(new Route(geometry,steps,
                        (float)candidate.getDouble("distance"),
                        Math.max(0L,Math.round(candidate.getDouble("duration")))));
                }
                if(parsed.isEmpty())throw new IOException("Mapbox returned no usable geometry");
                MAIN.post(()->callback.success(parsed));
            }catch(Exception e){
                String message=e.getMessage()==null?"Mapbox request failed":e.getMessage();
                MAIN.post(()->callback.failure(message));
            }finally{if(conn!=null)conn.disconnect();}
        },"navith-mapbox-directions").start();
    }

    private static boolean valid(LatLng p) {
        return p!=null && Double.isFinite(p.latitude) && Double.isFinite(p.longitude)
            && Math.abs(p.latitude)<=90 && Math.abs(p.longitude)<=180;
    }
}
