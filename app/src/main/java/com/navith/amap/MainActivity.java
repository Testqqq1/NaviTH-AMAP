package com.navith.amap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import java.util.Map;
import java.util.LinkedHashMap;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.transition.TransitionManager;
import android.transition.AutoTransition;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.widget.Switch;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.widget.RadioButton;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.Circle;
import com.amap.api.maps.model.CircleOptions;
import com.amap.api.maps.model.CustomMapStyleOptions;
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
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.GeocodeQuery;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.geocoder.RegeocodeAddress;
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
 * NaviTH v0.5: AMAP map, search, route preview and Android Auto route-preview surface.
 * Not turn-by-turn navigation; road-level guidance requires a Navigation SDK.
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
    private Circle accuracyRing;
    private final List<Marker> searchMapMarkers=new ArrayList<>();
    private final List<PoiItem> searchPoiResults=new ArrayList<>();
    private final List<DrivePath> driveAlternatives=new ArrayList<>();
    private Polyline routeLine;
    private static final int PICK_MARKER_IMAGE = 3001;
    private boolean initialized = false;
    private boolean locationRequested = false;
    private PoiSearch activePoiSearch;
    private RouteSearch routeSearch;
    private GeocodeSearch geocodeSearch;
    private LatLng waitingReversePoint;

    private EditText searchInput;
    private TextView statusText;
    private TextView gpsStatus;
    private TextView searchBarTitle;
    private TextView searchResultsHeading;
    private LinearLayout searchOverlay;
    private LinearLayout routeExpanded;
    private final Handler searchHandler=new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private boolean routeOpen=false;
    private String activeLanguage="th";
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

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(NaviLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        activeLanguage=NaviLocale.language(this);
        if (getSharedPreferences("navith_consent", MODE_PRIVATE).getBoolean(CONSENT_PREFS, false)) {
            initializeApp(state);
        } else {
            showPrivacyConsent(state);
        }
    }

    private void showPrivacyConsent(Bundle state) {
        new AlertDialog.Builder(this)
            .setTitle(tr("ขอความยินยอมใช้แผนที่","Map service privacy consent"))
            .setMessage(tr(
                "NaviTH ใช้ AMAP Maps, Search และ Location SDK ของผู้ให้บริการภายนอก เพื่อแสดงแผนที่ ค้นหาสถานที่ และคำนวณเส้นทาง ข้อมูลอุปกรณ์ เครือข่าย และตำแหน่งอาจถูกส่งให้ AMAP ตามการใช้บริการ\n\nแอปจะขอสิทธิ์ตำแหน่งของ Android แยกต่างหาก ใช้เฉพาะขณะเปิดแอป ไม่ขอตำแหน่งเบื้องหลัง\n\nอ่าน PRIVACY.md และนโยบายที่ mapsplatform.opnavi.com ก่อนยอมรับ",
                "NaviTH uses third-party AMAP Maps, Search and Location SDKs to display maps, find places and preview routes. Device, network and location data may be sent to AMAP as part of these services.\n\nAndroid location permission is requested separately. The app uses location only while in the foreground and does not request background location.\n\nPlease review PRIVACY.md and the AMAP privacy policy at mapsplatform.opnavi.com before accepting."
            ))
            .setCancelable(false)
            .setPositiveButton(tr("ยอมรับและเปิดแผนที่","Accept and open map"),(d,w)->{
                getSharedPreferences("navith_consent",MODE_PRIVATE).edit()
                    .putBoolean(CONSENT_PREFS,true).apply();
                initializeApp(state);
            })
            .setNegativeButton(tr("ไม่ยอมรับ","Decline"),(d,w)->showDeclinedScreen(state))
            .show();
    }
    private void showDeclinedScreen(Bundle state) {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(getColor(R.color.nav_card));
        root.setPadding(dp(28),dp(28),dp(28),dp(28));
        TextView label=new TextView(this);
        label.setText(tr("NaviTH Maps\n\nคุณยังไม่ได้ยินยอมให้ใช้ AMAP SDK\nต้องยอมรับก่อนเปิดแผนที่",
            "NaviTH Maps\n\nAMAP SDK consent is required before the map can be opened"));
        label.setTextSize(18);
        label.setTextColor(getColor(R.color.nav_text));
        label.setGravity(Gravity.CENTER);
        root.addView(label);
        Button retry=new Button(this);
        retry.setText(tr("อ่านรายละเอียดอีกครั้ง","Review privacy details"));
        retry.setOnClickListener(v->showPrivacyConsent(state));
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
        MapsInitializer.initRegionLanguageType(NaviLocale.thai(this) ? 7 : 2); // Thai or English where available.
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
        applyMapPreferences();

        searchInput = findViewById(R.id.searchInput);
        statusText = findViewById(R.id.statusText);
        destinationText = findViewById(R.id.destinationText);
        routeSummary = findViewById(R.id.routeSummary);
        gpsStatus = findViewById(R.id.gpsStatus);
        searchBarTitle = findViewById(R.id.searchBarTitle);
        searchResultsHeading = findViewById(R.id.searchResultsHeading);
        searchOverlay = findViewById(R.id.searchOverlay);
        routeExpanded = findViewById(R.id.routeExpanded);
        resultsPanel = findViewById(R.id.resultsPanel);
        resultsList = findViewById(R.id.resultsList);
        routePanel = findViewById(R.id.routePanel);
        modeGroup = findViewById(R.id.modeGroup);

        findViewById(R.id.searchBarButton).setOnClickListener(v -> openSearch());
        findViewById(R.id.searchBack).setOnClickListener(v -> closeSearch());
        findViewById(R.id.searchClear).setOnClickListener(v -> searchInput.setText(""));
        searchInput.setOnEditorActionListener((v, id, event) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                searchPlaces();
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count) {
                if(pendingSearch!=null)searchHandler.removeCallbacks(pendingSearch);
                if(text.length()==0) { renderSavedPlaces(); return; }
                String query=text.toString().trim();
                if(query.length()<2) {renderSavedPlaces();return;}
                searchResultsHeading.setText(getString(R.string.search_results));
                pendingSearch=()->searchPlaces();
                searchHandler.postDelayed(pendingSearch,420L);
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        findViewById(R.id.menuButton).setOnClickListener(v ->
                startActivity(new Intent(this,SettingsActivity.class)));
        findViewById(R.id.locationButton).setOnClickListener(v -> recenter());
        findViewById(R.id.mapTypeButton).setOnClickListener(v -> showLayers());
        findViewById(R.id.routeButton).setOnClickListener(v -> calculateRoute());
        findViewById(R.id.clearButton).setOnClickListener(v -> clearDestination());
        findViewById(R.id.routeSheetHeader).setOnClickListener(v -> toggleRouteExpanded());
        findViewById(R.id.sheetGrabber).setOnClickListener(v -> toggleRouteExpanded());
        findViewById(R.id.routeOptionsButton).setOnClickListener(v -> chooseRouteOptions());
        findViewById(R.id.favoriteButton).setOnClickListener(v -> toggleFavorite());
        View startNavigation=findViewById(R.id.startNavigationButton);
        startNavigation.setVisibility(NavSdkBridge.isInstalled() ? View.VISIBLE : View.GONE);
        startNavigation.setOnClickListener(v -> {
            if(destination!=null)NavSdkBridge.openRoute(this,destination,destinationName);
        });
        findViewById(R.id.addressLookupButton).setOnClickListener(v -> {
            if(destination!=null)reverseSelectedAddress(destination);
        });
        modeGroup.setOnCheckedChangeListener((g,id) -> {
            clearRouteLine();
            driveAlternatives.clear();
            LinearLayout alt=findViewById(R.id.routeAlternatives);
            if(alt!=null)alt.removeAllViews();
            if (destination != null) {
                routeSummary.setText(getString(R.string.view_route));
                ((Button)findViewById(R.id.routeButton)).setText(R.string.view_route);
            }
        });
        map.setOnMapTouchListener(e -> {
            if(e.getAction()==MotionEvent.ACTION_DOWN)followLocation=false;
        });
        map.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
            @Override public void onCameraChange(CameraPosition pos) {}
            @Override public void onCameraChangeFinish(CameraPosition pos) {
                // Group previews only when the camera is stationary.
                updateSearchMarkers();
            }
        });

        map.setOnMapLongClickListener(latLng -> {
            chooseDestination(latLng,tr("จุดที่เลือกบนแผนที่","Selected map location"));
            reverseSelectedAddress(latLng);
        });
        map.setOnMarkerClickListener(marker -> {
            if (marker.equals(destinationMarker)) {
                routePanel.setVisibility(View.VISIBLE);
                marker.showInfoWindow();
                return true;
            }
            Object tag = marker.getObject();
            if (tag instanceof MapCluster) {
                MapCluster cluster=(MapCluster)tag;
                if(cluster.places.size()==1) {
                    PoiItem item=cluster.places.get(0);
                    chooseDestination(toLatLng(item.getLatLonPoint()),item.getTitle());
                } else {
                    followLocation=false;
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(cluster.center,
                        Math.min(19f,map.getCameraPosition().zoom+2f)));
                }
                return true;
            }
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
            status(tr("เริ่มบริการค้นหาเส้นทางไม่ได้: ","Route service unavailable: ")+e.getMessage());
        }

        // Search SDK must be initialized only after user has accepted AMAP privacy terms.
        try { setupGeocodeSearch(); }
        catch(Exception ex) {
            status(tr("บริการแปลงที่อยู่ยังไม่พร้อม","Geocoding service unavailable"));
        }

        if ("YOUR_AMAP_KEY".equals(readApiKey())) {
            status(tr("ไม่มี AMAP API Key","Missing AMAP API Key"));
        } else {
            status(tr("แตะค้นหาหรือแตะค้างเพื่อเลือกจุดหมาย","Tap search or long-press a destination"));
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
            else status(tr("ยังไม่ได้อนุญาตตำแหน่ง","Location permission not granted"));
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
        if (myLocation == null) status(tr("กำลังค้นหา GPS จากโทรศัพท์","Locating with device GPS"));
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
            if (myLocation == null) status(tr("AMAP ไม่พร้อม • รอ GPS จาก Android","AMAP unavailable; waiting for device GPS"));
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
        if (gpsStatus != null) {
            float speedKmh = fix.hasSpeed() ? fix.getSpeed() * 3.6f : 0f;
            boolean miles = getViewPrefs().getBoolean("mph", false);
            String units=miles ? " mph" : " km/h";
            String prefix=accuracy>80f?"⚠ ":"◎ ";
            gpsStatus.setText(prefix+"±"+Math.round(accuracy)+ (NaviLocale.thai(this)?" ม.":" m")+"  ·  "
                    + Math.round(miles ? speedKmh / 1.609344f : speedKmh) + units);
            gpsStatus.setContentDescription(getString(R.string.my_position) + " ±"
                    + Math.round(accuracy) + (NaviLocale.thai(this) ? " เมตร" : " meters"));
        }
        if (first) status(tr("พบพิกัดจาก ","Location from ")+source);
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
        if(fix!=null && fix.hasAccuracy() && fix.getAccuracy()>0){
            double radius=Math.min(250.0,Math.max(5.0,fix.getAccuracy()));
            if(accuracyRing==null){
                accuracyRing=map.addCircle(new CircleOptions().center(myLocation).radius(radius)
                    .fillColor(0x132B83D5).strokeColor(0x552B83D5)
                    .strokeWidth(dp(1)).zIndex(0.1f));
            }else{
                accuracyRing.setCenter(myLocation);
                accuracyRing.setRadius(radius);
            }
        }
        updateLocationMarkerVisibility();
    }

    private void onLocated(AMapLocation loc) {
        runOnUiThread(() -> {
            if (isFinishing() || !initialized) return;
            if (loc != null && loc.getErrorCode() == 0) {
                acceptLocation(loc, "AMAP");
            } else if (myLocation == null) {
                int code = loc == null ? -1 : loc.getErrorCode();
                if (code == 4) status(tr("AMAP เชื่อมต่อเซิร์ฟเวอร์ไม่สำเร็จ (4) • ใช้ GPS ของเครื่อง","AMAP location server unavailable (4); using device GPS"));
                else if (code == 7) status(tr("AMAP Key/SHA1 ไม่ตรง (7) • ใช้ GPS ของเครื่อง","AMAP Key/SHA1 mismatch (7); device GPS remains available"));
                else status(tr("AMAP ตำแหน่งผิดพลาด (","AMAP location error (")+code+tr(") • รอ GPS ของเครื่อง","); waiting for device GPS"));
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
        searchResultsHeading.setText(getString(R.string.search_results));
        resultsList.removeAllViews();
        TextView busy=new TextView(this);
        busy.setText(NaviLocale.text(this,"กำลังค้นหา…","Searching…"));
        busy.setTextColor(getColor(R.color.nav_secondary));
        busy.setPadding(dp(14),dp(18),dp(14),dp(18));
        resultsList.addView(busy);
        resultsPanel.setVisibility(View.VISIBLE);
        try {
            // Create a new PoiSearch every time, so old results aren't coupled to the new query.
            PoiSearch.Query q = new PoiSearch.Query(term, "", "");
            q.setPageSize(10);
            q.setPageNum(1);
            PoiSearch search = new PoiSearch(this, q);
            search.setOnPoiSearchListener(new PoiSearch.OnPoiSearchListener() {
                @Override public void onPoiSearched(PoiResult result,int code) {
                    renderPoiResults(term,result,code);
                }
                @Override public void onPoiItemSearched(PoiItem item,int code) {}
            });
            activePoiSearch = search;
            search.searchPOIAsyn();
        } catch (Exception e) {
            status(tr("ค้นหาไม่สำเร็จ: ","Search failed: ")+e.getMessage());
        }
    }

    @Override public void onPoiSearched(PoiResult result, int code) {
        renderPoiResults(searchInput.getText().toString().trim(),result,code);
    }

    private void renderPoiResults(String term,PoiResult result,int code) {
        runOnUiThread(() -> {
            if(!initialized || isFinishing() || searchOverlay.getVisibility()!=View.VISIBLE) return;
            if(!term.equals(searchInput.getText().toString().trim())) return;
            resultsList.removeAllViews();
            if(code==1000 && (result==null || result.getPois()==null || result.getPois().isEmpty())) {
                // Only geocode when keyword POI search found nothing. Do not double-bill
                // every keystroke or call another API after an auth/network error.
                searchAddressFallback(term);
                return;
            }
            if(code!=1000 && McpPlacesClient.isConfigured(this)) {
                showExternalSearchResults(term,false);
                return;
            }
            if(code!=1000 || result==null || result.getPois()==null || result.getPois().isEmpty()) {
                TextView msg=new TextView(this);
                msg.setPadding(dp(16),dp(20),dp(16),dp(20));
                msg.setTextColor(getColor(R.color.nav_secondary));
                msg.setText(code==1000?NaviLocale.text(this,"ไม่พบสถานที่ ลองเปลี่ยนคำค้นหา","No places found")
                      :NaviLocale.text(this,"ค้นหาไม่สำเร็จ • รหัส ","Search failed • code ")+code);
                resultsList.addView(msg);
                return;
            }
            searchPoiResults.clear();
            int count=0;
            for(PoiItem item:result.getPois()){
                if(item.getLatLonPoint()==null)continue;
                searchPoiResults.add(item);
                addResultRow(item);
                if(++count==10)break;
            }
            updateSearchMarkers();
        });
    }

    private void addResultRow(PoiItem item) {
        addResultItem(item.getTitle(),item.getSnippet(),toLatLng(item.getLatLonPoint()),false);
    }

    private void addResultItem(String name,String description,LatLng position,boolean favorite) {
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12),dp(6),dp(10),dp(6));
        row.setMinimumHeight(dp(64));
        TextView dot=new TextView(this);
        dot.setText(favorite?"★":"⌖");
        dot.setTextSize(22);
        dot.setGravity(Gravity.CENTER);
        dot.setTextColor(getColor(R.color.nav_primary));
        row.addView(dot,new LinearLayout.LayoutParams(dp(46),dp(48)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        TextView label=new TextView(this);
        label.setText(name);label.setTextColor(getColor(R.color.nav_text));
        label.setTextSize(16);label.setMaxLines(1);label.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(label);
        if(description!=null && !description.isEmpty()) {
            TextView sub=new TextView(this);sub.setText(description);
            sub.setTextSize(12);sub.setTextColor(getColor(R.color.nav_secondary));
            sub.setMaxLines(2);sub.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(sub);
        }
        row.setOnClickListener(v->chooseDestination(position,name));
        resultsList.addView(row);
        View divider=new View(this);
        divider.setBackgroundColor(getColor(R.color.nav_border));
        resultsList.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }

    @Override
    public void onPoiItemSearched(PoiItem poi, int code) {
        // Reserved for future POI detail lookup.
    }

    private void setupGeocodeSearch() throws Exception {
        if(geocodeSearch!=null)return;
        geocodeSearch=new GeocodeSearch(this);
        geocodeSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
            @Override public void onGeocodeSearched(GeocodeResult result,int code) {
                runOnUiThread(()->renderGeocoding(result,code));
            }
            @Override public void onRegeocodeSearched(RegeocodeResult result,int code) {
                runOnUiThread(()->renderReverseGeocoding(result,code));
            }
        });
    }

    private void searchAddressFallback(String term) {
        if(searchOverlay.getVisibility()!=View.VISIBLE || !term.equals(searchInput.getText().toString().trim()))
            return;
        try {
            setupGeocodeSearch();
            // This is a secondary structured-address lookup, not a POI replacement.
            GeocodeQuery query=new GeocodeQuery(term,"");
            geocodeSearch.getFromLocationNameAsyn(query);
            TextView msg=new TextView(this);
            msg.setText(tr("ไม่พบชื่อสถานที่ กำลังลองค้นหาที่อยู่…",
                "No POIs found. Trying structured address…"));
            msg.setPadding(dp(12),dp(18),dp(12),dp(18));
            msg.setTextColor(getColor(R.color.nav_secondary));
            resultsList.addView(msg);
        }catch(Exception e){
            showExternalSearchResults(term,true);
        }
    }

    private void showEmptyAddressResults() {
        if(resultsList==null || searchOverlay.getVisibility()!=View.VISIBLE)return;
        resultsList.removeAllViews();
        TextView hint=new TextView(this);
        hint.setText(tr("ไม่พบสถานที่หรือที่อยู่ ลองเพิ่มเขต จังหวัด หรือประเทศ",
            "No matching places or addresses. Add city, province or country."));
        hint.setTextColor(getColor(R.color.nav_secondary));
        hint.setTextSize(14);
        hint.setPadding(dp(14),dp(20),dp(14),dp(20));
        resultsList.addView(hint);
    }

    private void renderGeocoding(GeocodeResult result,int code) {
        if(!initialized || isFinishing() || searchOverlay.getVisibility()!=View.VISIBLE)return;
        String query=result!=null && result.getGeocodeQuery()!=null
            ? result.getGeocodeQuery().getLocationName() : searchInput.getText().toString().trim();
        if(query==null || !query.equals(searchInput.getText().toString().trim()))return;
        resultsList.removeAllViews();
        if(code!=1000 || result==null || result.getGeocodeAddressList()==null ||
            result.getGeocodeAddressList().isEmpty()) {
            showExternalSearchResults(query,true);
            return;
        }
        int count=0;
        for(GeocodeAddress address:result.getGeocodeAddressList()){
            if(address==null || address.getLatLonPoint()==null)continue;
            String title=address.getFormatAddress();
            if(title==null || title.trim().isEmpty())title=query;
            addResultItem(title,
                tr("ผลค้นหาที่อยู่ · ตรวจสอบหมุดก่อนเลือก","Address match · verify location"),
                toLatLng(address.getLatLonPoint()),false);
            if(++count>=8)break;
        }
        if(count==0)showEmptyAddressResults();
    }

    private void showExternalSearchResults(String term, boolean geocode) {
        if(searchOverlay.getVisibility()!=View.VISIBLE ||
            !term.equals(searchInput.getText().toString().trim()))return;
        if(!McpPlacesClient.isConfigured(this)) {
            showEmptyAddressResults();return;
        }
        resultsList.removeAllViews();
        TextView loading=new TextView(this);
        loading.setText(tr("กำลังค้นหาจากบริการสำรอง…","Searching optional service…"));
        loading.setTextSize(13);
        loading.setTextColor(getColor(R.color.nav_secondary));
        loading.setPadding(dp(14),dp(15),dp(14),dp(15));
        resultsList.addView(loading);
        boolean started=McpPlacesClient.search(this,term,geocode,(places,error)->{
            if(isFinishing() || !initialized || 
               searchOverlay.getVisibility()!=View.VISIBLE ||
               !term.equals(searchInput.getText().toString().trim()))return;
            resultsList.removeAllViews();
            int n=0;
            for(McpPlacesClient.Place place:places){
                addResultItem(place.name,
                    place.address.isEmpty()?tr("AMAP MCP (ผลสำรอง)","AMAP MCP fallback"):place.address,
                    new LatLng(place.latitude,place.longitude),false);
                if(++n==8)break;
            }
            if(n==0){
                TextView hint=new TextView(this);
                hint.setText(error!=null?tr("บริการ MCP ไม่พร้อม • ตรวจ URL/Token",
                    "MCP unavailable • check URL/token"):
                    tr("ไม่พบข้อมูลเพิ่มเติมในพื้นที่นี้","No additional results in this region"));
                hint.setTextColor(getColor(R.color.nav_secondary));
                hint.setPadding(dp(14),dp(18),dp(14),dp(18));
                resultsList.addView(hint);
            }
        });
        if(!started)showEmptyAddressResults();
    }

    private void reverseSelectedAddress(LatLng point) {
        if(point==null)return;
        TextView addressLine=findViewById(R.id.addressResult);
        if(addressLine!=null){
            addressLine.setText(tr("กำลังค้นหาที่อยู่…","Looking up address…"));
            addressLine.setVisibility(View.VISIBLE);
        }
        try {
            setupGeocodeSearch();
            waitingReversePoint=point;
            RegeocodeQuery req=new RegeocodeQuery(
                new LatLonPoint(point.latitude,point.longitude),200f,GeocodeSearch.AMAP);
            geocodeSearch.getFromLocationAsyn(req);
        } catch(Exception e) {
            if(addressLine!=null)addressLine.setText(
                tr("บริการค้นหาที่อยู่ไม่พร้อม","Address lookup unavailable"));
        }
    }

    private void renderReverseGeocoding(RegeocodeResult result,int code) {
        if(!initialized || isFinishing() || destination==null || waitingReversePoint==null)return;
        if(!destination.equals(waitingReversePoint))return;
        if(result==null || result.getRegeocodeQuery()==null ||
           result.getRegeocodeQuery().getPoint()==null)return;
        LatLonPoint queried=result.getRegeocodeQuery().getPoint();
        if(Math.abs(queried.getLatitude()-destination.latitude)>0.000001 ||
           Math.abs(queried.getLongitude()-destination.longitude)>0.000001)return;
        TextView addressLine=findViewById(R.id.addressResult);
        if(addressLine==null)return;
        if(code==1000 && result.getRegeocodeAddress()!=null) {
            String formatted=result.getRegeocodeAddress().getFormatAddress();
            if(formatted!=null && !formatted.trim().isEmpty()){
                addressLine.setText(formatted);
                addressLine.setVisibility(View.VISIBLE);
                return;
            }
        }
        addressLine.setText(tr("ไม่มีข้อมูลที่อยู่สำหรับพิกัดนี้",
            "No address available for this coordinate"));
        addressLine.setVisibility(View.VISIBLE);
    }

    private void chooseDestination(LatLng point, String label) {
        clearSearchMarkers();
        destination = point;
        destinationName = label;
        waitingReversePoint = null;
        TextView addressLine = findViewById(R.id.addressResult);
        if(addressLine != null) {
            addressLine.setVisibility(View.GONE);
            addressLine.setText("");
        }
        driveAlternatives.clear();
        LinearLayout alternativeRows=findViewById(R.id.routeAlternatives);
        if(alternativeRows!=null)alternativeRows.removeAllViews();
        rememberPlace(point,label);
        closeSearch();
        searchBarTitle.setText(label);
        TripStore.setDestination(this, point, label);
        clearRouteLine();
        if (destinationMarker != null) destinationMarker.remove();
        destinationMarker = map.addMarker(new MarkerOptions().position(point)
                .title(label)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        destinationMarker.showInfoWindow();
        destinationText.setText(label);
        routeSummary.setText(tr("เลือกจุดหมายแล้ว • ดูเส้นทาง","Destination selected · View route"));
        routePanel.setVisibility(View.VISIBLE);
        routeExpanded.setVisibility(View.GONE);
        routeOpen=false;
        statusText.setVisibility(View.GONE);
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15));
        ((Button)findViewById(R.id.routeButton)).setText(R.string.view_route);
        status(tr("เลือกจุดหมายแล้ว: ","Destination: ")+label);
    }

    private void calculateRoute() {
        if (destination == null) {
            status(tr("เลือกจุดหมายก่อน","Choose a destination first"));
            return;
        }
        if (myLocation == null) {
            status(tr("ต้องมีพิกัดปัจจุบัน • กดตำแหน่งของฉัน","Current location needed; tap My location"));
            if (hasLocationPermission()) locateOnce();
            else { locationRequested = false; askForLocation(); }
            return;
        }
        if (routeSearch == null) {
            status(tr("บริการคำนวณเส้นทางยังไม่พร้อม","Route service unavailable"));
            return;
        }
        clearRouteLine();
        TripStore.clearRoute(this);
        routeSummary.setText(tr("กำลังคำนวณเส้นทาง…","Calculating route…"));
        try {
            RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(
                    toPoint(myLocation), toPoint(destination));
            if (modeGroup.getCheckedRadioButtonId() == R.id.modeWalk) {
                RouteSearch.WalkRouteQuery query = new RouteSearch.WalkRouteQuery(
                        fromAndTo, RouteSearch.WALK_DEFAULT);
                routeSearch.calculateWalkRouteAsyn(query);
            } else {
                RouteSearch.DriveRouteQuery query = new RouteSearch.DriveRouteQuery(
                        fromAndTo, routeStrategy(), null, null, "");
                routeSearch.calculateDriveRouteAsyn(query);
            }
        } catch (Exception e) {
            routeSummary.setText(tr("คำนวณไม่ได้: ","Routing failed: ")+e.getMessage());
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
            showDriveRoutes(result.getPaths());
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


    private void showDriveRoutes(List<DrivePath> paths) {
        driveAlternatives.clear();
        LinearLayout container=findViewById(R.id.routeAlternatives);
        if(container==null)return;
        container.removeAllViews();
        for(int i=0;i<paths.size()&&i<3;i++)driveAlternatives.add(paths.get(i));
        if(driveAlternatives.isEmpty())return;
        for(int i=0;i<driveAlternatives.size();i++){
            final int index=i;
            DrivePath p=driveAlternatives.get(i);
            String label=NaviLocale.text(this,"เส้นทาง ","Route ")+(i+1)+"  ·  "+
                String.format(java.util.Locale.US,"%.1f km",p.getDistance()/1000f)+"  ·  "+
                Math.max(1L,Math.round(p.getDuration()/60f))+
                NaviLocale.text(this," นาที"," min");
            if(p.getTolls()>0)label+="  ·  "+NaviLocale.text(this,"ค่าผ่านทาง ~","Toll ~")
                +String.format(java.util.Locale.US,"%.0f",p.getTolls());
            TextView row=new TextView(this);
            row.setText(label);
            row.setTextColor(getColor(R.color.nav_primary));
            row.setTextSize(14);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(48));
            row.setPadding(dp(12),dp(6),dp(12),dp(6));
            row.setOnClickListener(v->displayDriveRoute(index));
            container.addView(row);
        }
        displayDriveRoute(0);
    }
    private void displayDriveRoute(int index) {
        if(index<0||index>=driveAlternatives.size())return;
        DrivePath path=driveAlternatives.get(index);
        List<LatLng> points=new ArrayList<>();
        if(path.getSteps()!=null)
            for(DriveStep step:path.getSteps())
                appendPolyline(points,step.getPolyline());
        drawRoute(points,path.getDistance(),path.getDuration(),
            NaviLocale.text(this,"รถยนต์","Driving"));
        if(path.getTolls()>0){
            routeSummary.setText(routeSummary.getText()+" · "+NaviLocale.text(this,
                "ผ่านทาง ~","Toll ~")+String.format(java.util.Locale.US,"%.0f",path.getTolls()));
        }
    }

    private void drawRoute(List<LatLng> points, float distanceMeters, long seconds, String mode) {
        clearRouteLine();
        if (points.size() < 2) {
            routeSummary.setText(tr("ไม่มีข้อมูลแนวเส้นทาง","No route geometry"));
            return;
        }
        routeLine = map.addPolyline(new PolylineOptions().addAll(points)
                .width(dp(5)).color(getColor(R.color.nav_route)).zIndex(2));
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (LatLng p : points) bounds.include(p);
        if (myLocation != null) bounds.include(myLocation);
        if (destination != null) bounds.include(destination);
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(84)));
        String km = String.format(Locale.US,"%.1f",distanceMeters/1000f)
            +NaviLocale.text(this," กม."," km");
        String time = Math.max(1L, Math.round(seconds / 60f))
            + NaviLocale.text(this," นาที"," min");
        String eta=new SimpleDateFormat("HH:mm",NaviLocale.thai(this)
            ? new Locale("th","TH"):Locale.ENGLISH)
            .format(new Date(System.currentTimeMillis()+Math.max(0L,seconds)*1000L));
        TextView etaText=findViewById(R.id.etaText);
        if(etaText!=null)etaText.setText(NaviLocale.text(this,"คาดว่าจะถึงประมาณ ","Estimated arrival ")+eta
            +NaviLocale.text(this," • ตัวอย่างเส้นทาง ยังไม่มีเสียงนำทาง"," • Route preview, no turn-by-turn guidance"));
        routeSummary.setText(mode + "  •  " + km + "  •  " + time);
        ((Button)findViewById(R.id.routeButton)).setText(R.string.change_route);
        TripStore.setRoute(this, points, distanceMeters, seconds, mode);
        status(tr("ดูเส้นทางไป ","Route preview to ")+destinationName);
    }

    private void routeFailed(int code) {
        routeSummary.setText(tr("ไม่พบเส้นทาง (","No route (")+code+tr(") • ตรวจสอบพื้นที่บริการ",") · Check coverage"));
        status(tr("อาจไม่มีข้อมูลเส้นทางในพื้นที่นี้","Route data may be unavailable here"));
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
        searchPoiResults.clear();
        clearSearchMarkers();
        TripStore.clearDestination(this);
        if (destinationMarker != null) {
            destinationMarker.remove();
            destinationMarker = null;
        }
        routePanel.setVisibility(View.GONE);
        driveAlternatives.clear();
        LinearLayout alt=findViewById(R.id.routeAlternatives);
        if(alt!=null)alt.removeAllViews();
        routeExpanded.setVisibility(View.GONE);
        routeOpen=false;
        searchBarTitle.setText(R.string.search_placeholder);
        statusText.setVisibility(View.VISIBLE);
        resultsPanel.setVisibility(View.VISIBLE);
        status(NaviLocale.text(this,"แตะเพื่อค้นหาจุดหมาย","Tap search to find a destination"));
    }


    private String tr(String th,String en) {return NaviLocale.text(this,th,en);}

    private SharedPreferences getViewPrefs() {
        return getSharedPreferences("navith_view", MODE_PRIVATE);
    }

    private int routeStrategy() {
        SharedPreferences p = getViewPrefs();
        boolean toll = p.getBoolean("avoid_tolls", false);
        boolean highway = p.getBoolean("avoid_highway", false);
        boolean congestion = p.getBoolean("avoid_traffic", true);
        if (toll && highway && congestion) return RouteSearch.DRIVING_SINGLE_NO_HIGHWAY_SAVE_MONEY_AVOID_CONGESTION;
        if (toll && highway) return RouteSearch.DRIVING_SINGLE_NO_HIGHWAY_SAVE_MONEY;
        if (toll && congestion) return RouteSearch.DRIVING_SINGLE_SAVE_MONEY_AVOID_CONGESTION;
        if (highway) return RouteSearch.DRIVING_SINGLE_NO_HIGHWAY;
        if (toll) return RouteSearch.DRIVING_SINGLE_SAVE_MONEY;
        return congestion ? RouteSearch.DRIVING_SINGLE_AVOID_CONGESTION : RouteSearch.DRIVING_SINGLE_DEFAULT;
    }

    private void applyMapPreferences() {
        if (map == null) return;
        SharedPreferences p = getViewPrefs();
        String theme = p.getString("theme", "auto");
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int type = "satellite".equals(theme) ? AMap.MAP_TYPE_SATELLITE
                : ("night".equals(theme) || ("auto".equals(theme) && dark))
                    ? AMap.MAP_TYPE_NIGHT : AMap.MAP_TYPE_NORMAL;
        map.setMapType(type);
        if(MapStyleImporter.installed(this) && type==AMap.MAP_TYPE_NORMAL) {
            java.io.File dir=MapStyleImporter.directory(this);
            CustomMapStyleOptions style=new CustomMapStyleOptions()
                .setEnable(true)
                .setStyleDataPath(new java.io.File(dir,"style.data").getAbsolutePath());
            java.io.File extra=new java.io.File(dir,"style_extra.data");
            java.io.File textures=new java.io.File(dir,"textures.zip");
            if(extra.exists())style.setStyleExtraPath(extra.getAbsolutePath());
            if(textures.exists())style.setStyleTexturePath(textures.getAbsolutePath());
            map.setCustomMapStyle(style);
        } else map.setCustomMapStyle(new CustomMapStyleOptions().setEnable(false));
        map.setTrafficEnabled(p.getBoolean("traffic", true));
        boolean threed = p.getBoolean("view3d", true);
        map.showBuildings(threed && p.getBoolean("buildings",true));
        map.showMapText(p.getBoolean("labels",false));
        CameraPosition current = map.getCameraPosition();
        map.moveCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder(current).tilt(threed ? 55f : 0f).build()));
        // Map controls stay in the two-button surface; individual layers are edited in a sheet.
        if (myMarker != null) myMarker.setIcon(currentMarkerIcon());
    }

    private BitmapDescriptor currentMarkerIcon() {
        int kind = getViewPrefs().getInt("vehicle_icon", 6);
        if (kind == 7) {
            File file = new File(getFilesDir(), "navith_marker.png");
            if (file.exists()) {
                Bitmap b = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (b != null) return BitmapDescriptorFactory.fromBitmap(b);
            }
        }
        String[] icons = {"🚗", "🚙", "🚐", "🚙", "🛻", "🏎", "➤"};
        String glyph = icons[Math.max(0, Math.min(6, kind))];
        TextView iconView = new TextView(this);
        iconView.setText(glyph);
        iconView.setTextSize(18f);
        iconView.setTextColor(Color.rgb(8,127,118));
        iconView.setTypeface(null, Typeface.BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconView.setPadding(dp(3), dp(3), dp(3), dp(3));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.rgb(8,127,118));
        iconView.setBackground(bg);
        return BitmapDescriptorFactory.fromView(iconView);
    }

    private void chooseMapTheme() {
        String[] labels = {"อัตโนมัติตามเครื่อง", "กลางวัน", "กลางคืน", "ดาวเทียม"};
        String[] values = {"auto", "day", "night", "satellite"};
        String selected = getViewPrefs().getString("theme", "auto");
        int current = java.util.Arrays.asList(values).indexOf(selected);
        new AlertDialog.Builder(this).setTitle("ธีมแผนที่")
            .setSingleChoiceItems(labels, current, (dialog,which) -> {
                getViewPrefs().edit().putString("theme", values[which]).apply();
                dialog.dismiss();
                applyMapPreferences();
            }).setNegativeButton("ยกเลิก", null).show();
    }

    private void showMapSettings() {
        SharedPreferences p = getViewPrefs();
        String[] items = {
            "มุมมอง: " + (p.getBoolean("view3d", true) ? "3D" : "2D"),
            "ธีม: " + p.getString("theme", "auto"),
            "จราจรสด: " + (p.getBoolean("traffic", true) ? "เปิด" : "ปิด"),
            "ซูมเริ่มต้น: " + p.getFloat("zoom", 16f),
            "ไอคอนรถ / ลูกศร / รูปของฉัน",
            "ตั้งค่าหลีกเลี่ยงเส้นทาง",
            "ความเร็ว: " + (p.getBoolean("mph", false) ? "mph" : "km/h"),
            "บันทึกที่จอดรถ ณ ตำแหน่งนี้",
            "ดูตำแหน่งที่จอดล่าสุด",
            "รายละเอียดระบบนำทางขั้นสูง"
        };
        new AlertDialog.Builder(this).setTitle("ตั้งค่า NaviTH")
            .setItems(items, (d,index)-> {
                switch(index) {
                    case 0:
                        p.edit().putBoolean("view3d", !p.getBoolean("view3d", true)).apply();
                        applyMapPreferences(); break;
                    case 1: chooseMapTheme(); break;
                    case 2:
                        p.edit().putBoolean("traffic", !p.getBoolean("traffic", true)).apply();
                        applyMapPreferences(); break;
                    case 3: chooseDefaultZoom(); break;
                    case 4: chooseVehicleIcon(); break;
                    case 5: chooseRouteOptions(); break;
                    case 6:
                        p.edit().putBoolean("mph", !p.getBoolean("mph", false)).apply();
                        status("ตั้งค่าหน่วยความเร็วแล้ว"); break;
                    case 7:
                        if (myLocation != null && TripStore.hasRecentLocation(this)) {
                            TripStore.saveParking(this, myLocation);
                            status("บันทึกตำแหน่งที่จอดรถแล้ว");
                        } else status("ยังไม่มี GPS ที่เป็นปัจจุบันสำหรับบันทึกที่จอด"); break;
                    case 8: showParking(); break;
                    default:
                        new AlertDialog.Builder(this).setTitle("ระบบนำทางขั้นสูง")
                            .setMessage("เวอร์ชันนี้รองรับแผนที่ 2D/3D ตามข้อมูลที่มี, ตำแหน่ง GPS, จราจรสด, ค้นหาสถานที่, การดูตัวอย่างเส้นทาง และ Android Auto Preview\n\nยังไม่มีการนำทางเลี้ยวต่อเลี้ยว, แนะนำช่องจราจร, แจ้งกล้องจับความเร็ว, EV routing หรือ Offline Navigation หลายประเทศ ต้องใช้ Navigation SDK และข้อมูลที่ได้รับสิทธิ์เพิ่มเติม")
                            .setPositiveButton("ตกลง", null).show();
                }
            }).setNegativeButton("ปิด", null).show();
    }

    private void chooseDefaultZoom() {
        String[] items = {"10 • ภาพรวมเมือง", "13 • ระดับย่าน", "16 • ระดับถนน", "18 • รายละเอียดถนน"};
        final float[] levels = {10f, 13f, 16f, 18f};
        new AlertDialog.Builder(this).setTitle("ซูมเริ่มต้นเมื่อกด GPS")
            .setItems(items, (d,which)->{
                getViewPrefs().edit().putFloat("zoom", levels[which]).apply();
                if (map != null && myLocation != null)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, levels[which]));
            }).show();
    }

    private void chooseVehicleIcon() {
        String[] types = {"รถเก๋ง 🚗", "แฮทช์แบ็ค 🚙", "รถตู้ 🚐", "SUV 🚙",
                "รถกระบะ 🛻", "รถสปอร์ต 🏎", "ลูกศร ➤", "เลือกรูปของคุณเอง..."};
        new AlertDialog.Builder(this).setTitle("ไอคอนตำแหน่งบนแผนที่ (2D)")
            .setSingleChoiceItems(types, getViewPrefs().getInt("vehicle_icon", 6), (d,index)->{
                d.dismiss();
                if (index == 7) {
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.setType("image/*");
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(pick, PICK_MARKER_IMAGE);
                } else {
                    getViewPrefs().edit().putInt("vehicle_icon", index).apply();
                    if (myMarker != null) myMarker.setIcon(currentMarkerIcon());
                }
            }).setNegativeButton("ปิด", null).show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_MARKER_IMAGE || result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            android.net.Uri uri = data.getData();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(stream, null, options);
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) throw new IllegalArgumentException("รูปไม่รองรับ");
            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth/options.inSampleSize,
                    options.outHeight/options.inSampleSize) > 512) options.inSampleSize *= 2;
            Bitmap b;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(stream, null, options);
            }
            if (b == null) throw new IllegalArgumentException("ไม่สามารถอ่านรูป");
            Bitmap scaled = Bitmap.createScaledBitmap(b, 96, 96, true);
            try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "navith_marker.png"))) {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            if (b != scaled) b.recycle();
            scaled.recycle();
            getViewPrefs().edit().putInt("vehicle_icon", 7).apply();
            if (myMarker != null) myMarker.setIcon(currentMarkerIcon());
            status("เปลี่ยนเป็นรูปตำแหน่งของคุณแล้ว");
        } catch (Exception e) {
            status("เพิ่มรูปไม่ได้ • ลองเลือกรูป PNG/JPG ขนาดเล็ก");
        }
    }

    private void chooseRouteOptions() {
        SharedPreferences p = getViewPrefs();
        String[] choices={tr("เลี่ยงค่าผ่านทาง","Avoid tolls"),
            tr("เลี่ยงทางด่วน/มอเตอร์เวย์","Avoid highways"),
            tr("เลี่ยงรถติดเมื่อมีข้อมูล","Avoid congestion when available")};
        String[] keys = {"avoid_tolls","avoid_highway","avoid_traffic"};
        boolean[] checked = {p.getBoolean(keys[0],false),p.getBoolean(keys[1],false),
                p.getBoolean(keys[2],true)};
        new AlertDialog.Builder(this).setTitle(tr("ตัวเลือกเส้นทาง","Route preferences"))
            .setMultiChoiceItems(choices, checked, (dialog,index,on) -> {
                p.edit().putBoolean(keys[index], on).apply();
            }).setPositiveButton("บันทึก", (d,w)->{
                status(tr("ตั้งค่าเส้นทางแล้ว • ดูเส้นทางอีกครั้ง","Route options saved · Recalculate to apply"));
            }).show();
    }

    private void showParking() {
        LatLng parked = TripStore.parkedLocation(this);
        if (parked == null) {
            new AlertDialog.Builder(this).setMessage("ยังไม่มีพิกัดที่จอดรถ").setPositiveButton("ปิด",null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("ที่จอดรถล่าสุด")
            .setMessage(String.format(Locale.US, "พิกัด %.6f, %.6f\nบันทึกโดยแอปในเครื่องเท่านั้น", parked.latitude, parked.longitude))
            .setPositiveButton("แสดงบนแผนที่", (d,w)->{
                chooseDestination(parked, "ที่จอดรถล่าสุด");
            }).setNegativeButton("ปิด",null).show();
    }



    private static final class MapCluster {
        final List<PoiItem> places = new ArrayList<>();
        LatLng center;
    }
    private void clearSearchMarkers() {
        for(Marker m:searchMapMarkers)if(m!=null)m.remove();
        searchMapMarkers.clear();
    }
    private float placeColor(PoiItem item) {
        String n=item.getTitle()==null?"":item.getTitle().toLowerCase(java.util.Locale.ROOT);
        if(n.contains("hospital")||n.contains("โรงพยาบาล"))return BitmapDescriptorFactory.HUE_RED;
        if(n.contains("restaurant")||n.contains("อาหาร")||n.contains("ร้าน"))return BitmapDescriptorFactory.HUE_ORANGE;
        if(n.contains("hotel")||n.contains("โรงแรม"))return BitmapDescriptorFactory.HUE_VIOLET;
        if(n.contains("park")||n.contains("สวน")||n.contains("วัด"))return BitmapDescriptorFactory.HUE_GREEN;
        return BitmapDescriptorFactory.HUE_AZURE;
    }
    private BitmapDescriptor clusterIcon(int count) {
        TextView marker=new TextView(this);
        marker.setText(Integer.toString(count));
        marker.setTextColor(Color.WHITE);marker.setTextSize(14);
        marker.setGravity(Gravity.CENTER);
        marker.setTypeface(null,Typeface.BOLD);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(getColor(R.color.nav_primary));
        bg.setCornerRadius(dp(22));
        marker.setBackground(bg);
        marker.setWidth(dp(42));marker.setHeight(dp(42));
        return BitmapDescriptorFactory.fromView(marker);
    }
    private void updateSearchMarkers() {
        if(map==null)return;
        if(destination!=null || searchPoiResults.isEmpty()
                || !getViewPrefs().getBoolean("show_poi_results",true)){
            clearSearchMarkers();return;
        }
        clearSearchMarkers();
        float zoom=map.getCameraPosition().zoom;
        if(zoom<10.5f)return;
        int cell=dp(zoom<14f?84:62);
        Map<String,MapCluster> clusters=new LinkedHashMap<>();
        try{
            for(PoiItem p:searchPoiResults){
                if(p.getLatLonPoint()==null)continue;
                LatLng pos=toLatLng(p.getLatLonPoint());
                Point pixel=map.getProjection().toScreenLocation(pos);
                String key=(pixel.x/cell)+":"+(pixel.y/cell);
                MapCluster c=clusters.get(key);
                if(c==null){c=new MapCluster();c.center=pos;clusters.put(key,c);}
                c.places.add(p);
            }
            for(MapCluster c:clusters.values()){
                PoiItem first=c.places.get(0);
                MarkerOptions opt=new MarkerOptions().position(c.center)
                  .title(c.places.size()>1?c.places.size()+" places":first.getTitle())
                  .icon(c.places.size()>1?clusterIcon(c.places.size())
                      :BitmapDescriptorFactory.defaultMarker(placeColor(first)));
                Marker marker=map.addMarker(opt);
                marker.setObject(c);
                searchMapMarkers.add(marker);
            }
        }catch(Exception e){clearSearchMarkers();}
    }

    private void toggleRouteExpanded() {
        routeOpen=!routeOpen;
        AutoTransition transition=new AutoTransition();
        transition.setDuration(170);
        TransitionManager.beginDelayedTransition(routePanel,transition);
        routeExpanded.setVisibility(routeOpen?View.VISIBLE:View.GONE);
        updateFavoriteButton();
    }

    private void openSearch() {
        searchOverlay.setAlpha(0f);
        searchOverlay.setVisibility(View.VISIBLE);
        searchOverlay.animate().alpha(1f).setDuration(150).start();
        searchInput.setText("");
        renderSavedPlaces();
        searchInput.requestFocus();
        searchInput.postDelayed(()->{
            if(searchOverlay.getVisibility()==View.VISIBLE) {
                InputMethodManager k=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if(k!=null)k.showSoftInput(searchInput,InputMethodManager.SHOW_IMPLICIT);
            }
        },150);
    }
    private void closeSearch() {
        if(searchOverlay==null)return;
        searchOverlay.setVisibility(View.GONE);
        if(pendingSearch!=null)searchHandler.removeCallbacks(pendingSearch);
        hideKeyboard();
        if(destination==null)updateSearchMarkers();
    }

    private JSONArray saved(String key) {
        try {return new JSONArray(getSharedPreferences("navith_places",MODE_PRIVATE).getString(key,"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    private JSONObject recordPlace(LatLng pt,String name) {
        JSONObject o=new JSONObject();
        try{o.put("title",name);o.put("lat",pt.latitude);o.put("lon",pt.longitude);}
        catch(Exception ignored){}
        return o;
    }
    private boolean samePlace(JSONObject o,LatLng pt) {
        return o!=null && pt!=null && Math.abs(o.optDouble("lat",999)-pt.latitude)<0.00001
                && Math.abs(o.optDouble("lon",999)-pt.longitude)<0.00001;
    }
    private void rememberPlace(LatLng point,String name) {
        if(point==null || name==null || name.isEmpty())return;
        JSONArray old=saved("recents"), result=new JSONArray();
        result.put(recordPlace(point,name));
        for(int i=0;i<old.length()&&result.length()<20;i++){
            JSONObject p=old.optJSONObject(i);
            if(p!=null&&!samePlace(p,point))result.put(p);
        }
        getSharedPreferences("navith_places",MODE_PRIVATE).edit()
            .putString("recents",result.toString()).apply();
    }
    private boolean isFavorite(LatLng pt) {
        JSONArray a=saved("favorites");
        for(int i=0;i<a.length();i++)if(samePlace(a.optJSONObject(i),pt))return true;
        return false;
    }
    private void toggleFavorite() {
        if(destination==null)return;
        boolean was=isFavorite(destination);
        JSONArray old=saved("favorites"),now=new JSONArray();
        if(!was)now.put(recordPlace(destination,destinationName));
        for(int i=0;i<old.length()&&now.length()<40;i++){
            JSONObject p=old.optJSONObject(i);
            if(p!=null&&!samePlace(p,destination))now.put(p);
        }
        getSharedPreferences("navith_places",MODE_PRIVATE).edit()
            .putString("favorites",now.toString()).apply();
        updateFavoriteButton();
    }
    private void updateFavoriteButton() {
        TextView b=findViewById(R.id.favoriteButton);
        if(b!=null)b.setText(isFavorite(destination)
            ? NaviLocale.text(this,"★  บันทึกไว้ในสถานที่โปรด","★  Saved to favorites")
            : NaviLocale.text(this,"☆  บันทึกเป็นสถานที่โปรด","☆  Add to favorites"));
    }
    private void renderSavedPlaces() {
        if(resultsList==null)return;
        resultsPanel.setVisibility(View.VISIBLE);
        resultsList.removeAllViews();
        String term=searchInput.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        searchResultsHeading.setText(term.isEmpty()?R.string.recent_places:R.string.search_results);
        int count=0;
        for(String key:new String[]{"favorites","recents"}){
            JSONArray places=saved(key);
            if(places.length()==0)continue;
            TextView section=new TextView(this);
            section.setText("favorites".equals(key)?R.string.favorite_places:R.string.recent_places);
            section.setTextSize(13);
            section.setTextColor(getColor(R.color.nav_secondary));
            section.setPadding(dp(12),dp(16),0,dp(7));
            boolean displayed=false;
            for(int i=0;i<Math.min(places.length(),10);i++){
                JSONObject p=places.optJSONObject(i);
                if(p==null)continue;
                String name=p.optString("title","");
                if(!name.toLowerCase(java.util.Locale.ROOT).contains(term))continue;
                double lat=p.optDouble("lat",Double.NaN),lon=p.optDouble("lon",Double.NaN);
                if(!Double.isFinite(lat)||!Double.isFinite(lon))continue;
                if(!displayed){resultsList.addView(section);displayed=true;}
                addResultItem(name,"favorites".equals(key)?getString(R.string.favorite_places)
                    :getString(R.string.recent_places),new LatLng(lat,lon),"favorites".equals(key));
                count++;
            }
        }
        if(count==0){
            TextView empty=new TextView(this);
            empty.setText(term.isEmpty()?R.string.search_empty:
                R.string.gps_searching);
            empty.setTextSize(14);empty.setTextColor(getColor(R.color.nav_secondary));
            empty.setPadding(dp(18),dp(20),dp(18),dp(20));
            resultsList.addView(empty);
        }
    }
    private void updateLocationMarkerVisibility() {
        boolean visible=getViewPrefs().getBoolean("show_location",true);
        if(myMarker!=null)myMarker.setVisible(visible);
        if(accuracyRing!=null)accuracyRing.setVisible(visible);
        if(gpsStatus!=null) {
            if(!hasLocationPermission())gpsStatus.setText(R.string.gps_permission);
            else if(myLocation==null)gpsStatus.setText(R.string.gps_searching);
        }
    }
    private void addLayerSwitch(LinearLayout container,String label,String key,boolean defaultValue) {
        Switch sw=new Switch(this);
        sw.setText(label);sw.setTextSize(16);
        sw.setTextColor(getColor(R.color.nav_text));sw.setMinHeight(dp(54));
        sw.setPadding(dp(3),dp(6),dp(3),dp(6));
        sw.setChecked(getViewPrefs().getBoolean(key,defaultValue));
        sw.setOnCheckedChangeListener((button,enabled)->{
            getViewPrefs().edit().putBoolean(key,enabled).apply();
            applyMapPreferences();
        });
        container.addView(sw);
    }
    private void showLayers() {
        Dialog dialog=new Dialog(this);
        LinearLayout sheet=new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20),dp(16),dp(20),dp(22));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(getColor(R.color.nav_card));
        bg.setCornerRadii(new float[]{dp(24),dp(24),dp(24),dp(24),0,0,0,0});
        sheet.setBackground(bg);
        TextView title=new TextView(this);
        title.setText(R.string.layers_title);title.setTextSize(21);
        title.setTextColor(getColor(R.color.nav_text));title.setTypeface(null,Typeface.BOLD);
        title.setPadding(0,0,0,dp(12));
        sheet.addView(title);
        addLayerSwitch(sheet,getString(R.string.view_labels),"labels",false);
        addLayerSwitch(sheet,getString(R.string.view_traffic),"traffic",true);
        addLayerSwitch(sheet,getString(R.string.view_3d),"buildings",true);
        addLayerSwitch(sheet,NaviLocale.text(this,"มุมมองเอียง 3D","3D camera"),"view3d",true);
        TextView note=new TextView(this);
        note.setText(NaviLocale.text(this,
            "เส้นทางจักรยานและชั้นขนส่งสาธารณะต้องใช้ข้อมูลเพิ่มเติม",
            "Cycling and transit layers need additional data"));
        note.setTextSize(12);note.setTextColor(getColor(R.color.nav_secondary));
        note.setPadding(0,dp(12),0,dp(12));sheet.addView(note);
        TextView settings=new TextView(this);
        settings.setText(NaviLocale.text(this,"การตั้งค่าทั้งหมด     ›","All settings     ›"));
        settings.setTextColor(getColor(R.color.nav_primary));settings.setTextSize(16);
        settings.setTypeface(null,Typeface.BOLD);settings.setGravity(Gravity.CENTER_VERTICAL);
        settings.setMinHeight(dp(52));sheet.addView(settings);
        settings.setOnClickListener(v->{
            dialog.dismiss();
            startActivity(new Intent(this,SettingsActivity.class));
        });
        dialog.setContentView(sheet);
        Window window=dialog.getWindow();
        if(window!=null)window.setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
        window=dialog.getWindow();
        if(window!=null){
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(-1,-2);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp=window.getAttributes();
            lp.dimAmount=.25f;window.setAttributes(lp);
        }
    }

    @Override public void onBackPressed(){
        if(searchOverlay!=null && searchOverlay.getVisibility()==View.VISIBLE){
            closeSearch();return;
        }
        if(routeOpen){toggleRouteExpanded();return;}
        super.onBackPressed();
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
        if (initialized && !NaviLocale.language(this).equals(activeLanguage)) {
            recreate();return;
        }
        if (mapView != null) mapView.onResume();
        if (initialized && map != null) {
            applyMapPreferences();
            updateLocationMarkerVisibility();
        }
        if (initialized && hasLocationPermission()) locateOnce();
    }

    @Override protected void onPause() {
        if (platformLocation != null) platformLocation.stop();
        if (locationClient != null) locationClient.stopLocation();
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
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
