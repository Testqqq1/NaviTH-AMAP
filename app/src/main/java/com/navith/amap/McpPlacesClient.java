package com.navith.amap;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Strictly opt-in HTTPS fallback for the user-deployed NaviTH MCP bridge.
 * AMAP Native SDK remains the first choice, including in Android Auto.
 * No GPS, device ID, or key from AMAP Native is sent by this client.
 */
public final class McpPlacesClient {
    public static final class Place {
        public final String name, address;
        public final double latitude, longitude;
        Place(String name, String address, double latitude, double longitude) {
            this.name=name;
            this.address=address;
            this.latitude=latitude;
            this.longitude=longitude;
        }
    }
    public interface Callback { void complete(List<Place> places, String error); }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static long lastRequestAt;
    private McpPlacesClient() {}

    public static SharedPreferences settings(Context c) {
        return c.getSharedPreferences("navith_mcp",Context.MODE_PRIVATE);
    }
    public static boolean validBaseUrl(String base) {
        if (base == null || base.length()<10 || base.length()>300) return false;
        try {
            URI uri=URI.create(base);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null
                || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null
                || uri.getRawFragment()!=null || uri.getPort()==0) return false;
            String path=uri.getRawPath();
            return path==null || path.isEmpty() || "/".equals(path);
        }catch(Exception ignored){return false;}
    }
    public static boolean isConfigured(Context c) {
        SharedPreferences p=settings(c);
        return p.getBoolean("enabled",false)
            && validBaseUrl(p.getString("url",""))
            && p.getString("token","").length()>=24;
    }

    /**
     * Return false when disabled or throttled; callback is only invoked for an accepted request.
     */
    public static synchronized boolean search(Context context,String query,boolean geocode,Callback callback) {
        if(!isConfigured(context) || query==null || query.trim().length()<2 || query.length()>120) return false;
        long now=SystemClock.elapsedRealtime();
        if(now-lastRequestAt<1900L)return false;
        lastRequestAt=now;
        SharedPreferences p=settings(context);
        String base=p.getString("url","").replaceAll("/+$","");
        String token=p.getString("token","");
        String requested=query.trim();
        POOL.execute(()->{
            List<Place> found=new ArrayList<>();
            String error=null;
            HttpURLConnection conn=null;
            try {
                URL url=new URL(base+(geocode?"/v1/geocode":"/v1/places"));
                conn=(HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(4500);
                conn.setReadTimeout(16000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Accept","application/json");
                conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
                conn.setRequestProperty("Authorization","Bearer "+token);
                conn.setDoOutput(true);
                JSONObject body=new JSONObject().put("query",requested);
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream os=conn.getOutputStream()){os.write(bytes);}
                int code=conn.getResponseCode();
                if(code!=200)throw new IllegalStateException("HTTP "+code);
                StringBuilder text=new StringBuilder();
                try(InputStream stream=conn.getInputStream();
                    BufferedReader br=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                    String line;
                    while((line=br.readLine())!=null){
                        if(text.length()+line.length()>65536)throw new IllegalStateException("Response too large");
                        text.append(line);
                    }
                }
                JSONArray items=new JSONObject(text.toString()).optJSONArray("items");
                if(items!=null)for(int i=0;i<Math.min(items.length(),8);i++){
                    JSONObject item=items.optJSONObject(i);
                    if(item==null)continue;
                    double lat=item.optDouble("latitude",Double.NaN);
                    double lon=item.optDouble("longitude",Double.NaN);
                    if(!Double.isFinite(lat) || !Double.isFinite(lon)
                        || Math.abs(lat)>90 || Math.abs(lon)>180)continue;
                    String name=item.optString("name","");
                    String address=item.optString("address","");
                    if(name.isEmpty())name=address;
                    if(name.isEmpty())continue;
                    if(name.length()>120)name=name.substring(0,120);
                    if(address.length()>240)address=address.substring(0,240);
                    found.add(new Place(name,address,lat,lon));
                }
            }catch(Exception ignored){
                error="MCP bridge unavailable or results unsupported";
            }finally{
                if(conn!=null)conn.disconnect();
            }
            final String err=error;
            MAIN.post(()->callback.complete(found,err));
        });
        return true;
    }
}
