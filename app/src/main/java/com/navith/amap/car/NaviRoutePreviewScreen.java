package com.navith.amap.car;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate;
import androidx.lifecycle.Lifecycle;

import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DrivePath;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.RideRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkRouteResult;
import com.navith.amap.GuidanceLaunch;
import com.navith.amap.GuidanceService;
import com.navith.amap.NaviLocale;

import java.util.Locale;

/** Route Search preview on the car, then our own guidance. No AMAP navigation page. */
final class NaviRoutePreviewScreen extends Screen implements RouteSearch.OnRouteSearchListener {
    private final LatLng destination;
    private final String name;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean loading = true;
    private boolean started;
    private String summary = "";
    private String error = "";
    private DrivePath path;

    NaviRoutePreviewScreen(@NonNull CarContext context, LatLng destination, String name) {
        super(context);
        this.destination = destination;
        this.name = name == null || name.isEmpty() ? "จุดหมาย" : name;
        getLifecycle().addObserver((androidx.lifecycle.LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_START) search();
        });
    }

    private String tr(String th, String en) { return NaviLocale.text(getCarContext(), th, en); }

    private void search() {
        if (path != null || !error.isEmpty()) return;
        LatLng from = TripStore.location(getCarContext());
        if (from == null) {
            loading = false;
            error = tr("ยังไม่มีตำแหน่งรถ", "The car does not have a location yet");
            invalidate();
            return;
        }
        try {
            MapsInitializer.updatePrivacyShow(getCarContext(), true, true);
            MapsInitializer.updatePrivacyAgree(getCarContext(), true);
            RouteSearch search = new RouteSearch(getCarContext());
            search.setRouteSearchListener(this);
            search.calculateDriveRouteAsyn(new RouteSearch.DriveRouteQuery(
                    new RouteSearch.FromAndTo(
                            new LatLonPoint(from.latitude, from.longitude),
                            new LatLonPoint(destination.latitude, destination.longitude)),
                    RouteSearch.DRIVING_SINGLE_DEFAULT, null, null, ""));
        } catch (Exception e) {
            loading = false;
            error = tr("คำนวณเส้นทางไม่ได้", "Could not calculate a route");
            invalidate();
        }
    }

    @NonNull @Override public Template onGetTemplate() {
        RoutePreviewNavigationTemplate.Builder builder = new RoutePreviewNavigationTemplate.Builder()
                .setTitle(name)
                .setHeaderAction(Action.BACK);
        if (loading) return builder.setLoading(true).build();
        if (!error.isEmpty() || path == null) {
            return builder.setItemList(new ItemList.Builder().addItem(new Row.Builder()
                    .setTitle(error.isEmpty() ? tr("ไม่พบเส้นทาง", "No route") : error).build()).build()).build();
        }
        builder.setItemList(new ItemList.Builder().setSelectedIndex(0).addItem(new Row.Builder()
                .setTitle(tr("เส้นทางขับรถ", "Driving route"))
                .addText(summary)
                .build()).build());
        builder.setNavigateAction(new Action.Builder()
                .setTitle(tr("เริ่มนำทาง", "Start navigation"))
                .setOnClickListener(this::startGuidance)
                .build());
        return builder.build();
    }

    private void startGuidance() {
        if (started || path == null) return;
        started = true;
        TripStore.setDestination(getCarContext(), destination, name);
        java.util.List<LatLng> points = new java.util.ArrayList<>();
        if (path.getSteps() != null) {
            for (com.amap.api.services.route.DriveStep step : path.getSteps()) {
                if (step.getPolyline() == null) continue;
                for (LatLonPoint point : step.getPolyline())
                    points.add(new LatLng(point.getLatitude(), point.getLongitude()));
            }
        }
        TripStore.setRoute(getCarContext(), points, path.getDistance(), path.getDuration(), "รถยนต์");
        SavedPlaces.remember(getCarContext(), destination.latitude, destination.longitude, name);
        GuidanceLaunch.start(getCarContext(), GuidanceService.legsOf(path), name,
                destination.latitude, destination.longitude, Math.round(path.getDuration()), false);
        getScreenManager().popToRoot();
    }

    @Override public void onDriveRouteSearched(DriveRouteResult result, int code) {
        main.post(() -> {
            if (getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) return;
            loading = false;
            if (code == 1000 && result != null && result.getPaths() != null && !result.getPaths().isEmpty()) {
                path = result.getPaths().get(0);
                summary = String.format(Locale.US, "%.1f", path.getDistance() / 1000f)
                        + tr(" กม. · ", " km · ")
                        + Math.max(1, Math.round(path.getDuration() / 60f))
                        + tr(" นาที", " min");
            } else {
                error = tr("ไม่พบเส้นทางในพื้นที่นี้", "No driving route in this area");
            }
            invalidate();
        });
    }

    @Override public void onWalkRouteSearched(WalkRouteResult result, int code) {}
    @Override public void onBusRouteSearched(BusRouteResult result, int code) {}
    @Override public void onRideRouteSearched(RideRouteResult result, int code) {}
}
