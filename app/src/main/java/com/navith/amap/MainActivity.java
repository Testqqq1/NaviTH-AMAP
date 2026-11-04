package com.navith.amap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.location.Location;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DrivePath;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.DriveStep;
import com.amap.api.services.route.RideRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkPath;
import com.amap.api.services.route.WalkRouteResult;
import com.amap.api.services.route.WalkStep;

import com.navith.amap.car.TripStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * NaviTH v0.1: foreground-only AMAP map, POI lookup, and route preview.
 * It is NOT turn-by-turn navigation and does not support Android Auto.
 */
public final class MainActivity extends Activity implements
        PoiSearch.OnPoiSearchListener, RouteSearch.OnRouteSearchListener {

    private static final int REQ_LOCATION = 101;
    private static final LatLng DEFAULT_CENTER = new LatLng(13.7563, 100.5018); // Bangkok, not the user's location.
    private static final String CONSENT_PREFS = "consent";

    private MapView mapView;
    private AMap map;
    private AMapLocationClient locationClient;
    private FastLocationProvider platformLocation;
    private LatLng myLocation;
    private long lastLocationFixUptime = 0L;
    private long lastSharedFixUptime = 0L;
    private float lastLocationAccuracy = Float.MAX_VALUE;
    private boolean followLocation = true;
    private TextView speedText;
    private LatLng destination;
    private String destinationName = "";
    private Marker destinationMarker;
    private Marker myMarker;
    private Polyline routeLine;
    private boolean satellite = false;
    private boolean initialized = false;
    private boolean locationRequested = false;
    private PoiSearch activePoiSearch;
    private RouteSearch routeSearch;

    private EditText searchInput;
    private TextView statusText;
    private TextView destinationText;
    private TextView routeSummary;
    private LinearLayout resultsPanel;
    private LinearLayout resultsList;
    private LinearLayout routePanel;
    private RadioGroup modeGroup;
    private final SharedPreferences.OnSharedPreferenceChangeListener tripChanges = (p,key) -> {
        if (!TripStore.DESTINATION.equals(key) || !initialized) return;
        runOnUiThread(() -> {
            if (!initialized || isFinishing()) return;
            LatLng remote=TripStore.destination(this);
            if (remote == null) { if (destination!=null) clearDestination(); }
            else if (destination==null || !destination.equals(remote))
                chooseDestination(remote,TripStore.destinationName(this));
        });
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getSharedPreferences("navith_consent", MODE_PRIVATE).getBoolean(CONSENT_PREFS, false)) {
            initializeApp(state);
        } else {
            showPrivacyConsent(state);
        }
    }

    private void showPrivacyConsent(Bundle state) {
        new AlertDialog.Builder(this)
                .setTitle("ขอความยินยอมใช้บริการแผนที่")
                .setMessage("NaviTH ใช้ AMAP Maps, Search และ Location SDK ของผู้ให้บริการภายนอก " +
                        "เพื่อแสดงแผนที่ ค้นหาสถานที่ และคำนวณเส้นทาง ข้อมูลอุปกรณ์/เครือข่าย " +
                        "และตำแหน่งอาจถูกส่งให้ AMAP ตามการใช้งานบริการ\n\n" +
                        "ตำแหน่งจะขออนุญาตจาก Android แยกอีกครั้ง ใช้เฉพาะขณะเปิดแอป " +
                        "และไม่ขอสิทธิ์ตำแหน่งเบื้องหลัง\n\n" +
                        "อ่านรายละเอียดเพิ่มเติมได้ในเอกสาร PRIVACY.md และนโยบายของ AMAP " +
                        "(mapsplatform.opnavi.com) ก่อนยอมรับ")
                .setCancelable(false)
                .setPositiveButton("ยอมรับและเปิดแผนที่", (d, w) -> {
                    getSharedPreferences("navith_consent", MODE_PRIVATE).edit().putBoolean(CONSENT_PREFS, true).apply();
                    initializeApp(state);
                })
                .setNegativeButton("ไม่ยอมรับ", (d, w) -> showDeclinedScreen(state))
                .show();
    }

    private void showDeclinedScreen(Bundle state) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        TextView label = new TextView(this);
        label.setText("NaviTH\n\nคุณยังไม่ได้อนุญาตให้ใช้ AMAP SDK\nแผนที่จะไม่เริ่มทำงานจนกว่าจะยอมรับ");
        label.setTextSize(18);
        label.setTextColor(Color.rgb(10, 48, 53));
        label.setGravity(Gravity.CENTER);
        root.addView(label);
        Button retry = new Button(this);
        retry.setText("ดูรายละเอียดอีกครั้ง");
        retry.setOnClickListener(v -> showPrivacyConsent(state));
        root.addView(retry);
        setContentView(root);
    }

    private void initializeApp(Bundle state) {
        if (initialized) return;
        initialized = true;
        TripStore.prefs(this).registerOnSharedPreferenceChangeListener(tripChanges);

        // Privacy callbacks must precede MapView / Search / Location SDK instantiation.
        MapsInitializer.updatePrivacyShow(getApplicationContext(), true, true);
        MapsInitializer.updatePrivacyAgree(getApplicationContext(), true);
        AMapLocationClient.updatePrivacyShow(getApplicationContext(), true, true);
        AMapLocationClient.updatePrivacyAgree(getApplicationContext(), true);
        MapsInitializer.updateCountryCode("TH");
        MapsInitializer.initRegionLanguageType(7); // Thai label support (subject to data coverage).
        MapsInitializer.loadWorldVectorMap(true);

        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(state);
        mapView.onResume(); // Consent can be accepted after the Activity already resumed.
        map = mapView.getMap();
        map.setMapType(AMap.MAP_TYPE_NORMAL);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setScaleControlsEnabled(true);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 12.5f));

        searchInput = findViewById(R.id.searchInput);
        statusText = findViewById(R.id.statusText);
        destinationText = findViewById(R.id.destinationText);
        routeSummary = findViewById(R.id.routeSummary);
        speedText = findViewById(R.id.speedText);
        resultsPanel = findViewById(R.id.resultsPanel);
        resultsList = findViewById(R.id.resultsList);
        routePanel = findViewById(R.id.routePanel);
        modeGroup = findViewById(R.id.modeGroup);

        findViewById(R.id.searchButton).setOnClickListener(v -> searchPlaces());
        searchInput.setOnEditorActionListener((v, id, event) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                searchPlaces();
                return true;
            }
            return false;
        });
        findViewById(R.id.locationButton).setOnClickListener(v -> recenter());
        findViewById(R.id.mapTypeButton).setOnClickListener(v -> {
            satellite = !satellite;
            map.setMapType(satellite ? AMap.MAP_TYPE_SATELLITE : AMap.MAP_TYPE_NORMAL);
            status(satellite ? "แสดงแผนที่ดาวเทียม" : "แสดงแผนที่ปกติ");
        });
        findViewById(R.id.routeButton).setOnClickListener(v -> calculateRoute());
        findViewById(R.id.clearButton).setOnClickListener(v -> clearDestination());
        modeGroup.setOnCheckedChangeListener((g, id) -> {
            clearRouteLine();
            if (destination != null) routeSummary.setText("กดแสดงเส้นทางเพื่อคำนวณอีกครั้ง");
        });

        map.setOnMapLongClickListener(latLng -> chooseDestination(latLng, "ตำแหน่งที่เลือกบนแผนที่"));
        map.setOnMarkerClickListener(marker -> {
            if (marker.equals(destinationMarker)) {
                routePanel.setVisibility(View.VISIBLE);
                marker.showInfoWindow();
                return true;
            }
            Object tag = marker.getObject();
            if (tag instanceof PoiItem) {
                PoiItem poi = (PoiItem) tag;
                if (poi.getLatLonPoint() != null) {
                    chooseDestination(toLatLng(poi.getLatLonPoint()), poi.getTitle());
                    return true;
                }
            }
            return false;
        });

        try {
            routeSearch = new RouteSearch(this);
            routeSearch.setRouteSearchListener(this);
        } catch (Exception e) {
            status("เริ่มบริการค้นหาเส้นทางไม่ได้: " + e.getMessage());
        }

        if ("YOUR_AMAP_KEY".equals(readApiKey())) {
            status("ยังไม่มี AMAP API Key: ใส่ AMAP_KEY ก่อน Build เพื่อโหลดแผนที่จริง");
        } else {
            status("พร้อมค้นหา • แตะค้างบนแผนที่เพื่อเลือกจุดหมาย");
        }
        askForLocation();
    }

    private String readApiKey() {
        try {
            return getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_META_DATA).metaData.getString("com.amap.api.v2.apikey", "");
        } catch (Exception e) {
            return "";
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void askForLocation() {
        if (hasLocationPermission()) {
            locateOnce();
        } else if (!locationRequested) {
            locationRequested = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQ_LOCATION) {
            if (hasLocationPermission()) locateOnce();
            else status("ไม่ได้อนุญาตตำแหน่ง • ยังค้นหาสถานที่ได้ แต่ต้องเปิด GPS เพื่อคำนวณจากตำแหน่งปัจจุบัน");
        }
    }

    private void locateOnce() {
        if (!initialized || !hasLocationPermission()) return;
        if (platformLocation == null) {
            platformLocation = new FastLocationProvider(this, new FastLocationProvider.Callback() {
                @Override public void onFix(Location fix, String source) {
                    runOnUiThread(() -> acceptLocation(fix, source));
                }
                @Override public void onMessage(String message) {
                    runOnUiThread(() -> { if (myLocation == null) status(message); });
                }
            });
        }
        // Native GPS and network providers do not depend on AMAP's positioning server.
        platformLocation.start();
        if (myLocation == null) status("กำลังค้นหา GPS ของโทรศัพท์ • รับพิกัดได้แม้ AMAP Network ล้มเหลว");
        try {
            if (locationClient == null) {
                locationClient = new AMapLocationClient(getApplicationContext());
                AMapLocationClientOption options = new AMapLocationClientOption();
                options.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                options.setOnceLocation(true);
                options.setOnceLocationLatest(false);
                options.setGpsFirst(true);
                options.setGpsFirstTimeout(3000L);
                options.setNeedAddress(false);
                locationClient.setLocationOption(options);
                locationClient.setLocationListener(this::onLocated);
            }
            locationClient.startLocation();
        } catch (Exception e) {
            if (myLocation == null) status("บริการ AMAP ไม่พร้อม • รอ GPS จาก Android");
        }
    }

    private void acceptLocation(Location fix, String source) {
        if (!initialized || isFinishing() || fix == null) return;
        if (!Double.isFinite(fix.getLatitude()) || !Double.isFinite(fix.getLongitude())) return;
        if (Math.abs(fix.getLatitude()) > 90 || Math.abs(fix.getLongitude()) > 180) return;
        float accuracy = fix.hasAccuracy() ? fix.getAccuracy() : 150f;
        if (accuracy > 250f) return;
        long now = SystemClock.elapsedRealtime();
        if (lastLocationFixUptime != 0 && now - lastLocationFixUptime < 12000L
                && accuracy > lastLocationAccuracy * 2.0f) return;
        boolean first = myLocation == null;
        myLocation = new LatLng(fix.getLatitude(), fix.getLongitude());
        lastLocationFixUptime = now;
        lastLocationAccuracy = accuracy;
        if (now - lastSharedFixUptime > 2500L || first) {
            TripStore.setLocation(this, myLocation);
            lastSharedFixUptime = now;
        }
        updatePositionMarker(fix);
        if (followLocation && map != null) {
            float zoom = first ? getViewPrefs().getFloat("zoom", 16f) : map.getCameraPosition().zoom;
            if (first) map.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, zoom));
            else map.moveCamera(CameraUpdateFactory.newLatLng(myLocation));
        }
        if (speedText != null) {
            float speedKmh = fix.hasSpeed() ? fix.getSpeed() * 3.6f : 0f;
            boolean miles = getViewPrefs().getBoolean("mph", false);
            speedText.setText("● " + source + " • ±" + Math.round(accuracy) + " ม.  |  "
                    + Math.round(miles ? speedKmh / 1.609344f : speedKmh) + (miles ? " mph" : " km/h"));
        }
        if (first) status("พบพิกัดแล้วจาก " + source + " • ค้นหาปลายทางได้");
    }

    private void updatePositionMarker(Location fix) {
        if (map == null || myLocation == null) return;
        if (myMarker == null) {
            myMarker = map.addMarker(new MarkerOptions().position(myLocation)
                    .title("ตำแหน่งปัจจุบัน").icon(currentMarkerIcon()));
        } else {
            myMarker.setPosition(myLocation);
        }
        if (fix != null && fix.hasBearing() && fix.hasSpeed() && fix.getSpeed() > 2f) {
            myMarker.setRotateAngle(360f - fix.getBearing());
        }
    }

    private void onLocated(AMapLocation loc) {
        runOnUiThread(() -> {
            if (isFinishing() || !initialized) return;
            if (loc != null && loc.getErrorCode() == 0) {
                acceptLocation(loc, "AMAP");
            } else if (myLocation == null) {
                int code = loc == null ? -1 : loc.getErrorCode();
                if (code == 4) status("AMAP ติดต่อเซิร์ฟเวอร์ตำแหน่งไม่ได้ (รหัส 4) • กำลังใช้ GPS ของโทรศัพท์แทน");
                else if (code == 7) status("AMAP API Key/SHA1 ไม่ตรง (รหัส 7) • GPS ของ Android ยังใช้ได้");
                else status("AMAP ตำแหน่งผิดพลาด (" + code + ") • รอ GPS ของโทรศัพท์");
            }
        });
    }

    private void recenter() {
        followLocation = true;
        if (myLocation != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation,
                    Math.max(map.getCameraPosition().zoom, 16f)));
        } else {
            if (!hasLocationPermission()) {
                locationRequested = false;
                askForLocation();
            } else locateOnce();
        }
    }

    private void searchPlaces() {
        String term = searchInput.getText().toString().trim();
        if (TextUtils.isEmpty(term)) {
            searchInput.setError("กรอกคำค้นหาก่อน");
            return;
        }
        hideKeyboard();
        resultsPanel.setVisibility(View.GONE);
        status("กำลังค้นหา “" + term + "”…");
        try {
            // Create a new PoiSearch every time, so old results aren't coupled to the new query.
            PoiSearch.Query q = new PoiSearch.Query(term, "", "");
            q.setPageSize(10);
            q.setPageNum(1);
            PoiSearch search = new PoiSearch(this, q);
            search.setOnPoiSearchListener(this);
            activePoiSearch = search;
            search.searchPOIAsyn();
        } catch (Exception e) {
            status("ค้นหาไม่ได้: " + e.getMessage());
        }
    }

    @Override
    public void onPoiSearched(PoiResult result, int code) {
        runOnUiThread(() -> {
            if (!initialized || isFinishing()) return;
            if (code != 1000 || result == null || result.getPois() == null || result.getPois().isEmpty()) {
                resultsPanel.setVisibility(View.GONE);
                status(code == 1000 ? "ไม่พบสถานที่ ลองเพิ่มจังหวัดหรือคำค้นหา" : "ค้นหาล้มเหลว (รหัส " + code + ")");
                return;
            }
            resultsList.removeAllViews();
            int count = 0;
            for (PoiItem item : result.getPois()) {
                if (item.getLatLonPoint() == null) continue;
                ++count;
                addResultRow(item);
                if (count == 6) break; // Limit height of overlay so map remains usable.
            }
            resultsPanel.setVisibility(View.VISIBLE);
            status("พบสถานที่ " + result.getPois().size() + " รายการ • เลือกจากผลค้นหาด้านบน");
        });
    }

    private void addResultRow(PoiItem item) {
        TextView row = new TextView(this);
        String address = item.getSnippet() == null ? "" : item.getSnippet();
        row.setText(item.getTitle() + (address.isEmpty() ? "" : "\n" + address));
        row.setTextSize(14);
        row.setTextColor(Color.rgb(12, 50, 56));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(12), dp(8));
        row.setMinHeight(dp(52));
        row.setOnClickListener(v -> {
            chooseDestination(toLatLng(item.getLatLonPoint()), item.getTitle());
            resultsPanel.setVisibility(View.GONE);
        });
        resultsList.addView(row);
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(237, 240, 242));
        resultsList.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    @Override
    public void onPoiItemSearched(PoiItem poi, int code) {
        // Reserved for future POI detail lookup.
    }

    private void chooseDestination(LatLng point, String label) {
        destination = point;
        destinationName = label;
        TripStore.setDestination(this, point, label);
        clearRouteLine();
        if (destinationMarker != null) destinationMarker.remove();
        destinationMarker = map.addMarker(new MarkerOptions().position(point)
                .title(label)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        destinationMarker.showInfoWindow();
        destinationText.setText(label);
        routeSummary.setText("ระบุตำแหน่งแล้ว • กดแสดงเส้นทาง");
        routePanel.setVisibility(View.VISIBLE);
        resultsPanel.setVisibility(View.GONE);
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15));
        status("เลือกจุดหมายแล้ว: " + label);
    }

    private void calculateRoute() {
        if (destination == null) {
            status("เลือกจุดหมายก่อน");
            return;
        }
        if (myLocation == null) {
            status("ต้องมีตำแหน่งปัจจุบันก่อน • กด ◎ GPS");
            if (hasLocationPermission()) locateOnce();
            else { locationRequested = false; askForLocation(); }
            return;
        }
        if (routeSearch == null) {
            status("บริการคำนวณเส้นทางยังไม่พร้อม");
            return;
        }
        clearRouteLine();
        TripStore.clearRoute(this);
        routeSummary.setText("กำลังคำนวณเส้นทาง…");
        try {
            RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(
                    toPoint(myLocation), toPoint(destination));
            if (modeGroup.getCheckedRadioButtonId() == R.id.modeWalk) {
                RouteSearch.WalkRouteQuery query = new RouteSearch.WalkRouteQuery(
                        fromAndTo, RouteSearch.WALK_DEFAULT);
                routeSearch.calculateWalkRouteAsyn(query);
            } else {
                RouteSearch.DriveRouteQuery query = new RouteSearch.DriveRouteQuery(
                        fromAndTo, RouteSearch.DRIVING_SINGLE_DEFAULT, null, null, "");
                routeSearch.calculateDriveRouteAsyn(query);
            }
        } catch (Exception e) {
            routeSummary.setText("คำนวณไม่ได้: " + e.getMessage());
        }
    }

    @Override
    public void onDriveRouteSearched(DriveRouteResult result, int code) {
        runOnUiThread(() -> {
            if (!initialized || isFinishing() || destination == null) return;
            if (code != 1000 || result == null || result.getPaths() == null || result.getPaths().isEmpty()) {
                routeFailed(code);
                return;
            }
            DrivePath path = result.getPaths().get(0);
            List<LatLng> points = new ArrayList<>();
            for (DriveStep step : path.getSteps()) appendPolyline(points, step.getPolyline());
            drawRoute(points, path.getDistance(), path.getDuration(), "รถยนต์");
        });
    }

    @Override
    public void onWalkRouteSearched(WalkRouteResult result, int code) {
        runOnUiThread(() -> {
            if (!initialized || isFinishing() || destination == null) return;
            if (code != 1000 || result == null || result.getPaths() == null || result.getPaths().isEmpty()) {
                routeFailed(code);
                return;
            }
            WalkPath path = result.getPaths().get(0);
            List<LatLng> points = new ArrayList<>();
            for (WalkStep step : path.getSteps()) appendPolyline(points, step.getPolyline());
            drawRoute(points, path.getDistance(), path.getDuration(), "เดินเท้า");
        });
    }

    @Override public void onBusRouteSearched(BusRouteResult result, int code) { }
    @Override public void onRideRouteSearched(RideRouteResult result, int code) { }

    private void drawRoute(List<LatLng> points, float distanceMeters, long seconds, String mode) {
        clearRouteLine();
        if (points.size() < 2) {
            routeSummary.setText("ไม่พบข้อมูลแนวเส้นทาง");
            return;
        }
        routeLine = map.addPolyline(new PolylineOptions().addAll(points)
                .width(dp(7)).color(Color.rgb(0, 140, 127)).zIndex(2));
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (LatLng p : points) bounds.include(p);
        if (myLocation != null) bounds.include(myLocation);
        if (destination != null) bounds.include(destination);
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(84)));
        String km = String.format(Locale.forLanguageTag("th-TH"), "%.1f กม.", distanceMeters / 1000f);
        String time = Math.max(1L, Math.round(seconds / 60f)) + " นาที";
        routeSummary.setText(mode + "  •  " + km + "  •  ประมาณ " + time + "\n" +
                "แสดงแนวเส้นทางเท่านั้น ยังไม่มีเสียงนำทางเลี้ยวต่อเลี้ยว");
        TripStore.setRoute(this, points, distanceMeters, seconds, mode);
        status("แสดงเส้นทางไปยัง " + destinationName + " แล้ว");
    }

    private void routeFailed(int code) {
        routeSummary.setText("ไม่พบเส้นทาง (รหัส " + code + ") • ตรวจสอบพื้นที่บริการของ AMAP");
        status("AMAP อาจยังไม่มีข้อมูลเส้นทางสำหรับจุดนี้");
    }

    private void appendPolyline(List<LatLng> output, List<LatLonPoint> path) {
        if (path == null) return;
        for (LatLonPoint p : path) if (p != null) output.add(toLatLng(p));
    }

    private void clearRouteLine() {
        // Only remove the phone-side visual; the shared route is explicitly reset when needed.
        if (routeLine != null) {
            routeLine.remove();
            routeLine = null;
        }
    }

    private void clearDestination() {
        clearRouteLine();
        destination = null;
        destinationName = "";
        TripStore.clearDestination(this);
        if (destinationMarker != null) {
            destinationMarker.remove();
            destinationMarker = null;
        }
        routePanel.setVisibility(View.GONE);
        resultsPanel.setVisibility(View.GONE);
        status("เลือกจุดหมายใหม่โดยค้นหาหรือแตะค้างบนแผนที่");
    }

    private void hideKeyboard() {
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
    }

    private void status(String text) {
        if (statusText != null) statusText.setText(text);
        else Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private static LatLonPoint toPoint(LatLng p) { return new LatLonPoint(p.latitude, p.longitude); }
    private static LatLng toLatLng(LatLonPoint p) { return new LatLng(p.getLatitude(), p.getLongitude()); }

    @Override protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        if (initialized && hasLocationPermission()) locateOnce();
    }

    @Override protected void onPause() {
        if (platformLocation != null) platformLocation.stop();
        if (locationClient != null) locationClient.stopLocation();
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        TripStore.prefs(this).unregisterOnSharedPreferenceChangeListener(tripChanges);
        if (locationClient != null) {
            locationClient.stopLocation();
            locationClient.onDestroy();
        }
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (mapView != null) mapView.onSaveInstanceState(out);
        super.onSaveInstanceState(out);
    }
}
