package com.navith.amap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.route.DrivePath;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.DriveStep;
import com.amap.api.services.route.RouteSearch;
import com.navith.amap.car.NaviGuidanceStore;
import com.navith.amap.car.TripStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Keeps turn-by-turn alive with the screen off. Owns the route follower,
 * the navigation notification, and Thai voice prompts.
 */
public final class GuidanceService extends Service implements RouteSearch.OnRouteSearchListener {
    private static final int NOTE_ID = 42;
    private static final String CHANNEL = "navith_guidance";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable emulatorTick = this::emulatorStep;
    private final HashSet<String> spoken = new HashSet<>();
    private final LocationListener fixes = this::onFix;
    private RouteGuide.Route route;
    private GuidanceLaunch.Script script;
    private RouteSearch routeSearch;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean thaiMissing;
    private boolean guiding;
    private boolean reroutePending;
    private boolean rerouteSpoken;
    private int offRouteStreak;
    private double emulatorMeters;
    private long emulatorTickAt;
    private int lastNext = -1;
    private String lastKey = "";
    private LocationManager locations;
    private AudioFocusRequest focus;

    private void emulatorStep() {
        if (!guiding || route == null || script == null || !script.emulator) return;
        long now = SystemClock.elapsedRealtime();
        double dt = emulatorTickAt == 0 ? 1.0 : Math.min(2.0, (now - emulatorTickAt) / 1000.0);
        emulatorTickAt = now;
        emulatorMeters += 20.0 * dt;
        if (emulatorMeters >= route.lengthMeters()) {
            arrive();
            return;
        }
        double[] pose = route.poseAt(emulatorMeters);
        Location fix = new Location("จำลอง");
        fix.setLatitude(pose[0]);
        fix.setLongitude(pose[1]);
        fix.setTime(System.currentTimeMillis());
        fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        fix.setAccuracy(5f);
        fix.setSpeed(20f);
        fix.setBearing((float) pose[2]);
        TripStore.setLocation(this, fix);
        follow(pose[0], pose[1], 20f);
        main.postDelayed(emulatorTick, 1000);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);
        MapsInitializer.updateCountryCode("TH");
        MapsInitializer.initRegionLanguageType(NaviLocale.thai(this) ? 7 : 2);
        ensureChannel();
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) return;
            int lang = tts.setLanguage(new Locale("th", "TH"));
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                thaiMissing = true;
                tts.setLanguage(Locale.getDefault());
                getSharedPreferences("navith_trip", MODE_PRIVATE).edit()
                        .putBoolean("voice_thai_missing", true).apply();
            }
            AudioAttributes attrs = speechAttrs();
            tts.setAudioAttributes(attrs);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}
                @Override public void onDone(String utteranceId) { abandonFocus(); }
                @Override public void onError(String utteranceId) { abandonFocus(); }
            });
            ttsReady = true;
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification starting = note(getString(R.string.app_name), "กำลังนำทาง");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, starting, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTE_ID, starting);
        }
        if (intent != null && GuidanceLaunch.ACTION_STOP.equals(intent.getAction())) {
            finish(false);
            return START_NOT_STICKY;
        }
        script = GuidanceLaunch.read(this);
        if (script == null || script.legs.isEmpty()) {
            finish(false);
            return START_NOT_STICKY;
        }
        route = RouteGuide.compile(script.legs, script.destination, script.durationSeconds);
        if (route == null) {
            finish(false);
            return START_NOT_STICKY;
        }
        guiding = true;
        spoken.clear();
        offRouteStreak = 0;
        reroutePending = false;
        NaviGuidanceStore.started(this);
        if (script.emulator) {
            emulatorMeters = 0;
            emulatorTickAt = 0;
            main.removeCallbacks(emulatorTick);
            main.post(emulatorTick);
        } else {
            listen();
            LatLng here = TripStore.location(this);
            if (here != null) follow(here.latitude, here.longitude, TripStore.speedMps(this));
        }
        return START_NOT_STICKY;
    }

    private void listen() {
        locations = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locations == null) return;
        try {
            if (locations.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, fixes, Looper.getMainLooper());
            if (locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, fixes, Looper.getMainLooper());
            if (Build.VERSION.SDK_INT >= 31)
                locations.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 1000L, 1f, fixes, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException ignored) { }
    }

    private void onFix(Location fix) {
        if (!guiding || script == null || script.emulator || fix == null) return;
        LocationFixGate.Verdict verdict = LocationFixGate.evaluate(
                SystemClock.elapsedRealtimeNanos(), fix.getElapsedRealtimeNanos(),
                System.currentTimeMillis(), fix.getTime(),
                fix.hasAccuracy() ? fix.getAccuracy() : -1f, true);
        if (!verdict.accept) return;
        TripStore.setLocation(this, fix);
        follow(fix.getLatitude(), fix.getLongitude(), fix.hasSpeed() ? fix.getSpeed() : -1f);
    }

    private void follow(double lat, double lon, float speed) {
        if (!guiding || route == null) return;
        RouteGuide.Progress progress = route.follow(lat, lon);
        if (progress.arrived) {
            speak(VoiceCue.arrival());
            arrive();
            return;
        }
        if (progress.offRoute && (script == null || !script.emulator)) {
            offRouteStreak++;
            if (offRouteStreak >= 3 && !reroutePending) {
                reroutePending = true;
                if (!rerouteSpoken) {
                    rerouteSpoken = true;
                    speak(VoiceCue.reroute());
                }
                NaviGuidanceStore.rerouting(this);
                postNote("กำลังคำนวณเส้นทางใหม่", "");
                requestReroute(lat, lon);
            }
            return;
        }
        offRouteStreak = 0;
        rerouteSpoken = false;
        String key = progress.iconType + "|" + progress.nextRoad;
        if (!key.equals(lastKey) || (lastNext >= 0 && progress.nextMeters > lastNext + 80)) {
            spoken.clear();
            lastKey = key;
        }
        lastNext = progress.nextMeters;
        VoiceCue.Cue cue = VoiceCue.consider(key, progress.iconType, progress.nextRoad,
                progress.action, progress.nextMeters, speed, spoken);
        if (cue != null) {
            spoken.addAll(cue.marks);
            speak(cue.phrase);
        }
        int next = Math.min(99999, Math.max(0, progress.nextMeters));
        int left = Math.min(499000, Math.max(0, progress.remainingMeters));
        int seconds = Math.min(172000, Math.max(0, progress.remainingSeconds));
        String road = progress.nextRoad == null || progress.nextRoad.isEmpty() ? "จุดหมาย" : progress.nextRoad;
        NaviGuidanceStore.update(this, road, next, left, seconds, progress.iconType, "", progress.currentRoad);
        postNote(VoiceCue.banner(progress.iconType, road, next), progress.currentRoad);
    }

    private void requestReroute(double lat, double lon) {
        if (script == null) return;
        try {
            if (routeSearch == null) {
                routeSearch = new RouteSearch(this);
                routeSearch.setRouteSearchListener(this);
            }
            routeSearch.calculateDriveRouteAsyn(new RouteSearch.DriveRouteQuery(
                    new RouteSearch.FromAndTo(new LatLonPoint(lat, lon),
                            new LatLonPoint(script.destLat, script.destLon)),
                    RouteSearch.DRIVING_SINGLE_DEFAULT, null, null, ""));
        } catch (Exception e) {
            reroutePending = false;
        }
    }

    @Override public void onDriveRouteSearched(DriveRouteResult result, int code) {
        main.post(() -> {
            reroutePending = false;
            if (!guiding || code != 1000 || result == null || result.getPaths() == null || result.getPaths().isEmpty())
                return;
            DrivePath path = result.getPaths().get(0);
            List<RouteGuide.Leg> legs = legsOf(path);
            RouteGuide.Route next = RouteGuide.compile(legs, script.destination, Math.round(path.getDuration()));
            if (next == null) return;
            route = next;
            script = new GuidanceLaunch.Script(script.destination, script.destLat, script.destLon,
                    Math.round(path.getDuration()), script.emulator, legs);
            spoken.clear();
            lastKey = "";
            List<LatLng> points = new ArrayList<>();
            if (path.getSteps() != null) {
                for (DriveStep step : path.getSteps()) {
                    if (step.getPolyline() == null) continue;
                    for (LatLonPoint p : step.getPolyline())
                        points.add(new LatLng(p.getLatitude(), p.getLongitude()));
                }
            }
            TripStore.setRoute(this, points, path.getDistance(), path.getDuration(), "รถยนต์");
            NaviGuidanceStore.started(this);
            LatLng here = TripStore.location(this);
            if (here != null) follow(here.latitude, here.longitude, TripStore.speedMps(this));
        });
    }

    @Override public void onWalkRouteSearched(com.amap.api.services.route.WalkRouteResult result, int code) {}
    @Override public void onBusRouteSearched(com.amap.api.services.route.BusRouteResult result, int code) {}
    @Override public void onRideRouteSearched(com.amap.api.services.route.RideRouteResult result, int code) {}

    public static List<RouteGuide.Leg> legsOf(DrivePath path) {
        List<RouteGuide.Leg> legs = new ArrayList<>();
        if (path == null || path.getSteps() == null) return legs;
        for (DriveStep step : path.getSteps()) {
            if (step.getPolyline() == null || step.getPolyline().size() < 2) continue;
            double[] lats = new double[step.getPolyline().size()];
            double[] lons = new double[step.getPolyline().size()];
            for (int i = 0; i < step.getPolyline().size(); i++) {
                lats[i] = step.getPolyline().get(i).getLatitude();
                lons[i] = step.getPolyline().get(i).getLongitude();
            }
            legs.add(new RouteGuide.Leg(lats, lons, step.getRoad(), step.getAction(),
                    Math.round(step.getDuration())));
        }
        return legs;
    }

    private void speak(String phrase) {
        if (phrase == null || phrase.isEmpty() || GuidanceLaunch.voiceMuted(this) || !ttsReady || tts == null)
            return;
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(speechAttrs())
                    .build();
            audio.requestAudioFocus(focus);
        }
        tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "navith-" + SystemClock.uptimeMillis());
    }

    private void abandonFocus() {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
    }

    private static AudioAttributes speechAttrs() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    private void arrive() {
        if (!guiding) return;
        guiding = false;
        speak(VoiceCue.arrival());
        NaviGuidanceStore.arrived(this);
        postNote(VoiceCue.arrival(), "");
        main.postDelayed(() -> finish(true), 1600);
    }

    private void finish(boolean arrived) {
        guiding = false;
        main.removeCallbacks(emulatorTick);
        if (locations != null) {
            try { locations.removeUpdates(fixes); } catch (SecurityException ignored) {}
        }
        if (!arrived) NaviGuidanceStore.stopped(this);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void ensureChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "การนำทาง", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("เสียงและสถานะนำทางของ NaviTH");
        manager.createNotificationChannel(channel);
    }

    private void postNote(String title, String body) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTE_ID, note(title, thaiMissing ? "ติดตั้งเสียงภาษาไทยในการตั้งค่า" : body));
    }

    private Notification note(String title, String body) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(title == null || title.isEmpty() ? "NaviTH" : title)
                .setContentText(body == null ? "" : body)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(content)
                .setCategory(Notification.CATEGORY_NAVIGATION);
        if (thaiMissing) {
            Intent install = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent action = PendingIntent.getActivity(this, 2, install,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null, "ติดตั้งเสียงไทย", action).build());
        }
        return builder.build();
    }

    @Override public void onDestroy() {
        main.removeCallbacks(emulatorTick);
        if (locations != null) {
            try { locations.removeUpdates(fixes); } catch (SecurityException ignored) {}
        }
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
