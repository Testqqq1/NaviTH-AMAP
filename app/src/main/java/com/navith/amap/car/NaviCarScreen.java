package com.navith.amap.car;

import android.Manifest;
import android.app.Presentation;
import android.content.Context;
import android.content.res.Configuration;
import com.amap.api.maps.model.CameraPosition;
import com.navith.amap.NaviLocale;
import com.navith.amap.MapStyleImporter;
import com.amap.api.maps.model.CustomMapStyleOptions;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.AppManager;
import androidx.car.app.navigation.model.MapWithContentTemplate;
import androidx.car.app.navigation.model.MessageInfo;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.navigation.model.RoutingInfo;
import androidx.car.app.navigation.model.Step;
import androidx.car.app.navigation.model.Maneuver;
import androidx.car.app.model.Distance;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.BitmapDescriptor;
import com.navith.amap.NavigationMarkerFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.Circle;
import com.amap.api.maps.model.CircleOptions;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DrivePath;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.DriveStep;
import com.amap.api.services.route.RideRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkRouteResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Android Auto map-and-route-PREVIEW screen.
 * Not turn-by-turn navigation. Do NOT synthesize maneuvers/lanes from preview geometry.
 * AMap MapView on a virtual-display Surface is experimental and must be tested on DHU.
 */
final class NaviCarScreen extends Screen implements SurfaceCallback, RouteSearch.OnRouteSearchListener {
    private static final String TAG="NaviTH-Car";
    private static final LatLng BANGKOK=new LatLng(13.7563,100.5018);
    private final SharedPreferences prefs;
    private final SharedPreferences viewPrefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes;
    private final SharedPreferences.OnSharedPreferenceChangeListener viewChanges;
    private VirtualDisplay virtualDisplay;
    private Presentation presentation;
    private MapView mapView;
    private AMap map;
    private Marker carLocationMarker;
    private Circle carAccuracyRing;
    private BitmapDescriptor carArrowIcon;
    private float smoothCarHeading=Float.NaN;
    private float smoothCarMarkerHeading=Float.NaN;
    private RouteSearch routeSearch;
    private LocationManager locationManager;
    private final LocationListener locationListener = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location loc) {
            if (loc.hasAccuracy() && loc.getAccuracy() > 150f) return;
            TripStore.setLocation(getCarContext(),loc);
        }
    };
    private boolean routing=false;
    private final Handler sharedUiHandler=new Handler(Looper.getMainLooper());
    private long lastTemplateInvalidate=0L;
    private boolean followGps=true;
    private long lastCarCameraUpdate=0L;
    private LatLng requestedDestination;

    NaviCarScreen(@NonNull CarContext c) {
        super(c);
        prefs=TripStore.prefs(c);
        viewPrefs=c.getSharedPreferences("navith_view",Context.MODE_PRIVATE);
        viewChanges=(sp,key)->sharedUiHandler.post(()->{
            if("vehicle_icon".equals(key) || "marker_perspective".equals(key)){
                carArrowIcon=null;
                if(carLocationMarker!=null)carLocationMarker.setIcon(carMarkerIcon());
            }
            applyCarMapStyle();
        });
        viewPrefs.registerOnSharedPreferenceChangeListener(viewChanges);
        changes=(shared,key) -> {
            if (TripStore.DESTINATION.equals(key) || TripStore.ROUTE.equals(key) || TripStore.LOCATION.equals(key) || NaviGuidanceStore.EVENT.equals(key)) {
                sharedUiHandler.post(() -> {
                    boolean gpsTick=TripStore.LOCATION.equals(key);
                    if (gpsTick || NaviGuidanceStore.EVENT.equals(key)) updateLocationMarker();
                    else updateMap();
                    // Avoid rebuilding the Car App template for every GPS callback.
                    long now=android.os.SystemClock.elapsedRealtime();
                    if(!gpsTick || now-lastTemplateInvalidate>=1100L) {
                        lastTemplateInvalidate=now;
                        invalidate();
                    }
                    if (TripStore.DESTINATION.equals(key) && TripStore.destination(getCarContext())!=null) calculateCarRoute();
                });
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(changes);
        getLifecycle().addObserver((LifecycleEventObserver)(source,event)->{
            if (event == Lifecycle.Event.ON_DESTROY) {
                prefs.unregisterOnSharedPreferenceChangeListener(changes);
                viewPrefs.unregisterOnSharedPreferenceChangeListener(viewChanges);
                // This is an estimated last-connected position, not verified parking.
                if (TripStore.hasRecentLocation(getCarContext()))
                    TripStore.saveParking(getCarContext(), TripStore.location(getCarContext()));
                if (locationManager != null) try {locationManager.removeUpdates(locationListener);}catch(SecurityException ignored){}
                releasePresentation();
            }
        });
        c.getCarService(AppManager.class).setSurfaceCallback(this);
        // Start only if phone granted location. No background permission is requested.
        if (hasLocationPermission()) try {
            locationManager=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                // Use both providers: network gives a quick initial fix, GPS refines it.
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        2500L,10f,locationListener,Looper.getMainLooper());
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        1000L,2f,locationListener,Looper.getMainLooper());
            }
        } catch (SecurityException e) { Log.w(TAG,"Location permission not available in car session",e); }
    }

    @NonNull @Override public Template onGetTemplate() {
        if (!TripStore.consented(getCarContext())) {
            return new MessageTemplate.Builder("เปิด NaviTH บนมือถือเพื่ออ่านและยอมรับนโยบาย AMAP ก่อน")
                    .setTitle("ต้องยอมรับการใช้แผนที่").build();
        }
        if (!hasLocationPermission()) {
            return new MessageTemplate.Builder("อนุญาตตำแหน่งในแอป NaviTH บนโทรศัพท์ แล้วเชื่อม Android Auto อีกครั้ง")
                    .setTitle("ต้องเปิด GPS บนมือถือ").build();
        }
        String dest=TripStore.destinationName(getCarContext());
        String summary=TripStore.summary(getCarContext());
        String message = dest.isEmpty() ? tr("ค้นหาสถานที่จากปุ่มด้านบน หรือเลือกปลายทางจากมือถือ",
                       "Search or select a destination from the phone") :
                (routing ? tr("กำลังคำนวณเส้นทาง...","Calculating route…") :
                   (summary.isEmpty()?tr("เลือก: ","Selected: ")+dest+
                      tr(" · รอผลการคำนวณเส้นทาง"," · Waiting for route")
                      :tr("ไป ","To ")+dest+" · "+summary));
        Action search=new Action.Builder().setTitle(tr("ค้นหา","Search")).setOnClickListener(() ->
                getScreenManager().push(new NaviSearchScreen(getCarContext(),this))).build();
        Action locate=new Action.Builder().setTitle(tr("ตำแหน่ง","Locate"))
            .setOnClickListener(()->recenterCarMap()).build();
        Action view=new Action.Builder().setTitle(viewPrefs.getBoolean("view3d",true)?"2D":"3D")
            .setOnClickListener(()->{
                viewPrefs.edit().putBoolean("view3d",!viewPrefs.getBoolean("view3d",true)).apply();
                applyCarMapStyle();
                invalidate();
            }).build();
        // Keep a maximum of three primary car actions to avoid driver distraction.
        ActionStrip.Builder actionBar=new ActionStrip.Builder()
            .addAction(search).addAction(locate);
        if (dest.isEmpty()) actionBar.addAction(view);
        else actionBar.addAction(new Action.Builder()
            .setTitle(tr("ล้าง","Clear"))
            .setOnClickListener(()->TripStore.clearDestination(getCarContext())).build());
        NaviGuidanceStore.Snapshot guidance=NaviGuidanceStore.fresh(getCarContext());
        // A genuine vendor NaviInfo callback is the ONLY source of turn-by-turn
        // guidance. The normal RouteSearch polyline remains a preview.
        if (guidance != null && !dest.isEmpty()) {
            followGps=true;
            Step step=new Step.Builder(tr("ไปยัง ","Continue to ")+guidance.nextRoad)
                .setRoad(guidance.nextRoad)
                .setManeuver(new Maneuver.Builder(Maneuver.TYPE_UNKNOWN).build())
                .build();
            RoutingInfo actual=new RoutingInfo.Builder()
                .setCurrentStep(step,Distance.create(guidance.nextMeters,Distance.UNIT_METERS))
                .build();
            NavigationTemplate.Builder nav=new NavigationTemplate.Builder()
                .setActionStrip(actionBar.build()).setNavigationInfo(actual);
            if(getCarContext().getCarAppApiLevel()>=2) {
                nav.setMapActionStrip(new ActionStrip.Builder().addAction(Action.PAN).build());
            }
            return nav.build();
        }
        if (getCarContext().getCarAppApiLevel()>=7) {
            Pane pane=new Pane.Builder().addRow(new Row.Builder().setTitle(
                    dest.isEmpty()?tr("ค้นหาจุดหมาย","Choose a destination"):dest)
                    .addText(message).build()).build();
            PaneTemplate content=new PaneTemplate.Builder(pane).setTitle("NaviTH Maps").build();
            return new MapWithContentTemplate.Builder().setContentTemplate(content)
                    .setActionStrip(actionBar.build()).build();
        }
        NavigationTemplate.Builder preview=new NavigationTemplate.Builder()
                .setActionStrip(actionBar.build())
                .setNavigationInfo(new MessageInfo.Builder(
                    tr("NaviTH · ดูเส้นทาง","NaviTH · Route preview")).setText(message).build());
        if(getCarContext().getCarAppApiLevel()>=2) {
            preview.setMapActionStrip(new ActionStrip.Builder().addAction(Action.PAN).build());
        }
        return preview.build();
    }

    private boolean hasLocationPermission() {
        return getCarContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
                || getCarContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }

    void onPlaceSelected(LatLng point,String name) {
        TripStore.setDestination(getCarContext(),point,name); // Preference listener triggers route preview.
    }

    private void calculateCarRoute() {
        LatLng dst=TripStore.destination(getCarContext());
        LatLng from=TripStore.location(getCarContext());
        // A cached location is not safe to treat as live while driving; route preview only.
        if (dst==null || from==null || !TripStore.hasRecentLocation(getCarContext())) {
            routing=false;invalidate();return;
        }
        try {
            if (routeSearch == null) {routeSearch=new RouteSearch(getCarContext());routeSearch.setRouteSearchListener(this);}
            routing=true;requestedDestination=dst;invalidate();
            RouteSearch.FromAndTo f=new RouteSearch.FromAndTo(
                    new LatLonPoint(from.latitude,from.longitude),
                    new LatLonPoint(dst.latitude,dst.longitude));
            routeSearch.calculateDriveRouteAsyn(new RouteSearch.DriveRouteQuery(
                    f,routeStrategy(),null,null,""));
        } catch (Exception e) { routing=false;Log.w(TAG,"AMAP preview routing unavailable",e);invalidate(); }
    }
    @Override public void onDriveRouteSearched(DriveRouteResult result,int error) {
        new Handler(Looper.getMainLooper()).post(() -> {
            routing=false;
            LatLng dest=TripStore.destination(getCarContext());
            if (requestedDestination==null || dest==null || !requestedDestination.equals(dest)) {invalidate();return;}
            if (error==1000 && result!=null && result.getPaths()!=null && !result.getPaths().isEmpty()) {
                DrivePath path=result.getPaths().get(0);
                List<LatLng> points=new ArrayList<>();
                if (path.getSteps()!=null) for (DriveStep step:path.getSteps()) if (step.getPolyline()!=null)
                    for(LatLonPoint p:step.getPolyline()) points.add(new LatLng(p.getLatitude(),p.getLongitude()));
                TripStore.setRoute(getCarContext(),points,path.getDistance(),path.getDuration(),"รถยนต์");
            }
            updateMap();invalidate();
        });
    }
    @Override public void onWalkRouteSearched(WalkRouteResult result,int code) {}
    @Override public void onBusRouteSearched(BusRouteResult result,int code) {}
    @Override public void onRideRouteSearched(RideRouteResult result,int code) {}

    @Override public void onSurfaceAvailable(@NonNull SurfaceContainer surface) {
        releasePresentation();
        if (!TripStore.consented(getCarContext()) || surface.getSurface()==null
                || surface.getWidth()<=0 || surface.getHeight()<=0 || surface.getDpi()<=0) return;
        try {
            // Use AMAP with the host-owned Surface. Unlike a cast, this surface is sized by the car host.
            MapsInitializer.updatePrivacyShow(getCarContext(),true,true);
            MapsInitializer.updatePrivacyAgree(getCarContext(),true);
            MapsInitializer.updateCountryCode("TH");
            MapsInitializer.initRegionLanguageType(NaviLocale.thai(getCarContext()) ? 7 : 2);
            DisplayManager manager=(DisplayManager)getCarContext().getSystemService(Context.DISPLAY_SERVICE);
            virtualDisplay=manager.createVirtualDisplay("NaviTH-AMAP-AA",surface.getWidth(),
                    surface.getHeight(),surface.getDpi(),surface.getSurface(),
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
            presentation=new Presentation(getCarContext(),virtualDisplay.getDisplay());
            mapView=new MapView(presentation.getContext());
            mapView.onCreate(null);
            presentation.setContentView(mapView);
            presentation.show();
            mapView.onResume();
            map=mapView.getMap();
            applyCarMapStyle();
            map.getUiSettings().setZoomControlsEnabled(false);
            map.getUiSettings().setCompassEnabled(false);
            updateMap();
            applyCarMapStyle();
        } catch (Exception e) {
            Log.e(TAG,"MapView on virtual display needs real AA/DHU validation",e);
            releasePresentation();
        }
    }

    private String tr(String th,String en) {return NaviLocale.text(getCarContext(),th,en);}

    private int routeStrategy() {
        boolean toll=viewPrefs.getBoolean("avoid_tolls",false);
        boolean highway=viewPrefs.getBoolean("avoid_highway",false);
        boolean jam=viewPrefs.getBoolean("avoid_traffic",true);
        if (toll && highway && jam) return RouteSearch.DRIVING_SINGLE_NO_HIGHWAY_SAVE_MONEY_AVOID_CONGESTION;
        if (toll && highway) return RouteSearch.DRIVING_SINGLE_NO_HIGHWAY_SAVE_MONEY;
        if (toll && jam) return RouteSearch.DRIVING_SINGLE_SAVE_MONEY_AVOID_CONGESTION;
        if (highway) return RouteSearch.DRIVING_SINGLE_NO_HIGHWAY;
        if (toll) return RouteSearch.DRIVING_SINGLE_SAVE_MONEY;
        return jam ? RouteSearch.DRIVING_SINGLE_AVOID_CONGESTION : RouteSearch.DRIVING_SINGLE_DEFAULT;
    }

    private void applyCarMapStyle() {
        if (map == null) return;
        String theme=viewPrefs.getString("theme","auto");
        boolean dark=(getCarContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        int type="satellite".equals(theme) ? AMap.MAP_TYPE_SATELLITE :
                ("night".equals(theme) || ("auto".equals(theme) && dark))
                    ? AMap.MAP_TYPE_NIGHT : AMap.MAP_TYPE_NORMAL;
        map.setMapType(type);
        if(MapStyleImporter.installed(getCarContext()) && type==AMap.MAP_TYPE_NORMAL) {
            java.io.File dir=MapStyleImporter.directory(getCarContext());
            CustomMapStyleOptions style=new CustomMapStyleOptions().setEnable(true)
                .setStyleDataPath(new java.io.File(dir,"style.data").getAbsolutePath());
            java.io.File extra=new java.io.File(dir,"style_extra.data");
            java.io.File textures=new java.io.File(dir,"textures.zip");
            if(extra.exists())style.setStyleExtraPath(extra.getAbsolutePath());
            if(textures.exists())style.setStyleTexturePath(textures.getAbsolutePath());
            map.setCustomMapStyle(style);
        } else map.setCustomMapStyle(new CustomMapStyleOptions().setEnable(false));
        map.setTrafficEnabled(viewPrefs.getBoolean("traffic",true));
        boolean third=viewPrefs.getBoolean("view3d",true);
        map.showBuildings(third && viewPrefs.getBoolean("buildings",true));
        map.showMapText(viewPrefs.getBoolean("labels",true));
        map.moveCamera(CameraUpdateFactory.newCameraPosition(
                new CameraPosition.Builder(map.getCameraPosition()).tilt(third ? 55f : 0f).build()));
    }

    private void recenterCarMap() {
        followGps=true;
        if(map==null)return;
        LatLng location=TripStore.location(getCarContext());
        if(location==null || !TripStore.hasRecentLocation(getCarContext()))return;
        float zoom=Math.max(15f,Math.min(18f,viewPrefs.getFloat("zoom",16f)));
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(location,zoom));
    }

    /** Same AMAP-ground-aligned 2.5D artwork as the handset. */
    private BitmapDescriptor carMarkerIcon() {
        if(carArrowIcon==null)
            carArrowIcon=NavigationMarkerFactory.make(getCarContext(),viewPrefs);
        return carArrowIcon;
    }

    private void updateLocationMarker() {
        if (map==null) return;
        LatLng at=TripStore.location(getCarContext());
        if (at==null) return;
        if (carLocationMarker==null) {
            carLocationMarker=map.addMarker(new MarkerOptions().position(at)
                .title(tr("ตำแหน่งของฉัน","My location"))
                .anchor(0.5f,0.5f).setFlat(true)
                .icon(carMarkerIcon()).zIndex(8f));
        } else carLocationMarker.setPosition(at);
        float heading=TripStore.heading(getCarContext());
        if(heading>=0f) {
            if(!Float.isFinite(smoothCarMarkerHeading))smoothCarMarkerHeading=heading;
            else {
                float delta=((heading-smoothCarMarkerHeading+540f)%360f)-180f;
                smoothCarMarkerHeading=(smoothCarMarkerHeading+
                    Math.max(-28f,Math.min(28f,delta*0.40f))+360f)%360f;
            }
            carLocationMarker.setRotateAngle(
                NavigationMarkerFactory.angleForBearing(smoothCarMarkerHeading));
        }
        boolean fresh=TripStore.hasFreshNavigationLocation(getCarContext());
        float accuracy=TripStore.accuracy(getCarContext());
        carLocationMarker.setAlpha(fresh && (accuracy<0f||accuracy<=80f)?1f:0.55f);
        if(accuracy>=10f && accuracy<300f && fresh) {
            if(carAccuracyRing==null)carAccuracyRing=map.addCircle(
                new CircleOptions().center(at).radius(accuracy)
                    .fillColor(0x17087F77).strokeColor(0x66087F77)
                    .strokeWidth(2f).zIndex(0.1f));
            else {carAccuracyRing.setCenter(at);carAccuracyRing.setRadius(accuracy);}
        }else if(carAccuracyRing!=null){carAccuracyRing.remove();carAccuracyRing=null;}
        // Follow only a fresh actual GPS fix; avoid repeated full-route redraws.
        // Pan/zoom temporarily disables follow until user taps the Locate action.
        if (followGps && fresh && (accuracy<0f || accuracy<=120f)) {
            long now=android.os.SystemClock.elapsedRealtime();
            if(now-lastCarCameraUpdate>900L) {
                lastCarCameraUpdate=now;
                float zoom=Math.max(15f,Math.min(19f,viewPrefs.getFloat("zoom",16f)));
                boolean inDrive=NaviGuidanceStore.fresh(getCarContext())!=null;
                CameraPosition.Builder camera=new CameraPosition.Builder().target(at)
                        .zoom(inDrive?Math.max(17f,zoom):zoom)
                        .tilt(viewPrefs.getBoolean("view3d",true)?55f:0f);
                if(inDrive && heading>=0f && viewPrefs.getBoolean("view3d",true)){
                    if(!Float.isFinite(smoothCarHeading))smoothCarHeading=heading;
                    else{
                        float difference=((heading-smoothCarHeading+540f)%360f)-180f;
                        smoothCarHeading=(smoothCarHeading+
                            Math.max(-26f,Math.min(26f,difference*0.35f))+360f)%360f;
                    }
                    camera.bearing(smoothCarHeading);
                }
                else camera.bearing(map.getCameraPosition().bearing);
                map.moveCamera(CameraUpdateFactory.newCameraPosition(camera.build()));
            }
        }
    }

    private void updateMap() {
        if (map==null) return;
        map.clear();
        carLocationMarker=null;
        carAccuracyRing=null;
        LatLng from=TripStore.location(getCarContext());
        LatLng dest=TripStore.destination(getCarContext());
        List<LatLng> path=TripStore.route(getCarContext());
        if (from!=null) {
            carLocationMarker=map.addMarker(new MarkerOptions().position(from)
                .title(tr("ตำแหน่งของฉัน","My location")).anchor(0.5f,0.5f)
                .setFlat(true).icon(carMarkerIcon()).zIndex(8f));
            float angle=TripStore.heading(getCarContext());
            if(angle>=0f)carLocationMarker.setRotateAngle(
                NavigationMarkerFactory.angleForBearing(angle));
        }
        if (dest!=null) map.addMarker(new MarkerOptions().position(dest).title(TripStore.destinationName(getCarContext())));
        if (path.size()>=2) {
            map.addPolyline(new PolylineOptions().addAll(path).width(5f).color(0xff087f73).zIndex(2));
            LatLngBounds.Builder bounds=new LatLngBounds.Builder();
            for(LatLng p:path) bounds.include(p);
            if (from!=null) bounds.include(from);
            if (dest!=null) bounds.include(dest);
            try { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),80)); }
            catch (Exception ignored) { map.moveCamera(CameraUpdateFactory.newLatLngZoom(dest!=null?dest:BANGKOK,12)); }
        } else map.moveCamera(CameraUpdateFactory.newLatLngZoom(dest!=null?dest:(from!=null?from:BANGKOK),14));
    }
    @Override public void onSurfaceDestroyed(@NonNull SurfaceContainer s) { releasePresentation(); }
    @Override public void onScroll(float dx,float dy) { followGps=false; if (map!=null) map.moveCamera(CameraUpdateFactory.scrollBy(dx,dy)); }
    @Override public void onScale(float x,float y,float factor) {
        followGps=false;
        if(map!=null && factor>0) map.moveCamera(CameraUpdateFactory.zoomBy((float)(Math.log(factor)/Math.log(2))));
    }
    @Override public void onVisibleAreaChanged(@NonNull Rect area) { /* host overlays are outside our map view */ }
    private void releasePresentation() {
        map=null;
        carLocationMarker=null;
        carAccuracyRing=null;
        if(mapView!=null){try{mapView.onPause();mapView.onDestroy();}catch(Exception ignored){} mapView=null;}
        if(presentation!=null){try{presentation.dismiss();}catch(Exception ignored){}presentation=null;}
        if(virtualDisplay!=null){try{virtualDisplay.release();}catch(Exception ignored){}virtualDisplay=null;}
    }
}
