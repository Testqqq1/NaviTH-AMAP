package com.navith.amap;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import android.os.SystemClock;

/** Platform GPS/network fallback: no AMAP server required for a coordinate fix.
 * Foreground only; caller must stop() from onPause().
 */
final class FastLocationProvider {
    interface Callback {
        void onFix(Location fix, String source);
        void onMessage(String message);
    }
    private final Context context;
    private final LocationManager manager;
    private final Callback callback;
    private boolean started;
    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            deliver(location, location.getProvider() == null ? "Android" : location.getProvider(), false);
        }
    };

    FastLocationProvider(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.callback = callback;
    }

    private boolean hasFine() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
    private boolean hasCoarse() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
    private void deliver(Location fix, String source, boolean cached) {
        if (fix == null || !Double.isFinite(fix.getLatitude()) || !Double.isFinite(fix.getLongitude())) return;
        if (Math.abs(fix.getLatitude()) > 90 || Math.abs(fix.getLongitude()) > 180) return;
        // Cached positions must be very recent; never silently treat an old fix as live.
        long age = System.currentTimeMillis() - fix.getTime();
        if (cached && (age < -10000L || age > 30000L)) return;
        if (fix.hasAccuracy() && fix.getAccuracy() > 250f) return;
        callback.onFix(fix, cached ? source + " (ล่าสุด)" : source);
    }

    void start() {
        if (manager == null || started || (!hasCoarse() && !hasFine())) return;
        boolean any = false;
        try {
            if (hasFine() && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                deliver(manager.getLastKnownLocation(LocationManager.GPS_PROVIDER), "GPS", true);
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f,
                        listener, Looper.getMainLooper());
                any = true;
            }
        } catch (SecurityException | IllegalArgumentException ignored) { }
        try {
            if ((hasCoarse() || hasFine()) && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                deliver(manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER), "เครือข่าย", true);
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5f,
                        listener, Looper.getMainLooper());
                any = true;
            }
        } catch (SecurityException | IllegalArgumentException ignored) { }
        started = any;
        if (!any) callback.onMessage("เปิดตำแหน่งที่ตั้ง (Location) ในโทรศัพท์ และอนุญาตความแม่นยำสูงให้ NaviTH");
    }

    void stop() {
        if (manager != null) {
            try { manager.removeUpdates(listener); }
            catch (SecurityException ignored) { }
        }
        started = false;
    }
}
