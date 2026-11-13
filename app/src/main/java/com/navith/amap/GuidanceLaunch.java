package com.navith.amap;

import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Hands a Route Search path to the foreground guidance service. */
public final class GuidanceLaunch {
    public static final String ACTION_STOP = "com.navith.amap.GUIDANCE_STOP";
    static final String FILE = "guidance-route.txt";

    private GuidanceLaunch() {}

    public static final class Script {
        public final String destination;
        public final double destLat;
        public final double destLon;
        public final int durationSeconds;
        public final boolean emulator;
        public final List<RouteGuide.Leg> legs;

        Script(String destination, double destLat, double destLon, int durationSeconds,
               boolean emulator, List<RouteGuide.Leg> legs) {
            this.destination = destination == null ? "" : destination;
            this.destLat = destLat;
            this.destLon = destLon;
            this.durationSeconds = durationSeconds;
            this.emulator = emulator;
            this.legs = legs;
        }
    }

    public static void start(Context context, List<RouteGuide.Leg> legs, String destination,
                             double destLat, double destLon, int durationSeconds, boolean emulator) {
        write(context, new Script(destination, destLat, destLon, durationSeconds, emulator, legs));
        Intent intent = new Intent(context, GuidanceService.class);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, GuidanceService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static boolean voiceMuted(Context context) {
        return context.getSharedPreferences("navith_trip", Context.MODE_PRIVATE)
                .getBoolean("voice_muted", false);
    }

    public static void setVoiceMuted(Context context, boolean muted) {
        context.getSharedPreferences("navith_trip", Context.MODE_PRIVATE)
                .edit().putBoolean("voice_muted", muted).apply();
    }

    static void write(Context context, Script script) {
        StringBuilder text = new StringBuilder();
        text.append("v1\n");
        text.append(clean(script.destination)).append('\n');
        text.append(String.format(Locale.US, "%.6f\n%.6f\n", script.destLat, script.destLon));
        text.append(script.durationSeconds).append('\n');
        text.append(script.emulator ? "1\n" : "0\n");
        for (RouteGuide.Leg leg : script.legs) {
            if (leg == null || leg.lats == null) continue;
            text.append(clean(leg.road)).append('\t')
                    .append(clean(leg.action)).append('\t')
                    .append(leg.durationSeconds).append('\t');
            int n = Math.min(leg.lats.length, leg.lons.length);
            for (int i = 0; i < n; i++) {
                if (i > 0) text.append(';');
                text.append(String.format(Locale.US, "%.6f,%.6f", leg.lats[i], leg.lons[i]));
            }
            text.append('\n');
        }
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), FILE))) {
            out.write(text.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    static Script read(Context context) {
        try {
            File file = new File(context.getFilesDir(), FILE);
            byte[] raw = java.nio.file.Files.readAllBytes(file.toPath());
            String[] lines = new String(raw, StandardCharsets.UTF_8).split("\n", -1);
            if (lines.length < 6 || !"v1".equals(lines[0])) return null;
            String destination = lines[1];
            double lat = Double.parseDouble(lines[2]);
            double lon = Double.parseDouble(lines[3]);
            int duration = Integer.parseInt(lines[4]);
            boolean emulator = "1".equals(lines[5]);
            List<RouteGuide.Leg> legs = new ArrayList<>();
            for (int i = 6; i < lines.length; i++) {
                if (lines[i].isEmpty()) continue;
                String[] parts = lines[i].split("\t", 4);
                if (parts.length < 4) continue;
                String[] pairs = parts[3].split(";");
                double[] lats = new double[pairs.length];
                double[] lons = new double[pairs.length];
                int n = 0;
                for (String pair : pairs) {
                    String[] xy = pair.split(",");
                    if (xy.length != 2) continue;
                    lats[n] = Double.parseDouble(xy[0]);
                    lons[n] = Double.parseDouble(xy[1]);
                    n++;
                }
                if (n < 2) continue;
                legs.add(new RouteGuide.Leg(java.util.Arrays.copyOf(lats, n),
                        java.util.Arrays.copyOf(lons, n), parts[0], parts[1],
                        Integer.parseInt(parts[2])));
            }
            return new Script(destination, lat, lon, duration, emulator, legs);
        } catch (Exception e) {
            return null;
        }
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\t', ' ').replace('\n', ' ').trim();
    }
}
