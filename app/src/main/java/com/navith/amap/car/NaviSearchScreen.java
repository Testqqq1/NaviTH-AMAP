package com.navith.amap.car;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.constraints.ConstraintManager;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.model.SearchTemplate;
import androidx.car.app.model.Template;
import androidx.lifecycle.Lifecycle;

import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.navith.amap.McpPlacesClient;
import com.navith.amap.NaviLocale;
import com.navith.amap.RouteGuide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Android Auto search. The host supplies the keyboard and, when it offers it,
 * voice input through {@link SearchTemplate.SearchCallback}. Results are biased
 * to the current fix and still fall back to an unbounded query so a Thai place
 * outside that radius can be found.
 */
final class NaviSearchScreen extends Screen implements PoiSearch.OnPoiSearchListener {
    private final NaviCarScreen owner;
    private final List<Entry> results = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String launchQuery;
    private String term = "";
    private boolean loading = false, searched = false, widened = false, launched;
    private String error = "";
    private PoiSearch active;

    private static final class Entry {
        final LatLng position;
        final String title, subtitle;
        Entry(LatLng p, String t, String s) { position = p; title = t; subtitle = s; }
    }

    NaviSearchScreen(@NonNull CarContext c, NaviCarScreen owner) { this(c, owner, ""); }

    NaviSearchScreen(@NonNull CarContext c, NaviCarScreen owner, String initialQuery) {
        super(c);
        this.owner = owner;
        this.launchQuery = initialQuery == null ? "" : initialQuery.trim();
        this.term = launchQuery;
    }

    private String tr(String th, String en) { return NaviLocale.text(getCarContext(), th, en); }

    private int listLimit() {
        try {
            return Math.max(2, Math.min(6, getCarContext().getCarService(ConstraintManager.class)
                    .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)));
        } catch (RuntimeException e) {
            return 6;
        }
    }

    @NonNull @Override public Template onGetTemplate() {
        if (!launched && launchQuery.length() >= 2) {
            launched = true;
            search(launchQuery);
        }
        SearchTemplate.Builder builder = new SearchTemplate.Builder(new SearchTemplate.SearchCallback() {
            @Override public void onSearchTextChanged(@NonNull String text) { term = text; }
            @Override public void onSearchSubmitted(@NonNull String text) { search(text); }
        }).setHeaderAction(Action.BACK)
                .setSearchHint(tr("ค้นหาสถานที่หรือที่อยู่", "Search places or addresses"))
                .setInitialSearchText(term)
                .setShowKeyboardByDefault(!searched && launchQuery.isEmpty());
        if (loading) return builder.setLoading(true).build();
        ItemList.Builder items = new ItemList.Builder();
        List<Entry> shown = searched ? results : savedPlaces();
        int limit = listLimit();
        int count = 0;
        for (Entry entry : shown) {
            if (count >= limit) break;
            count++;
            items.addItem(new Row.Builder()
                    .setTitle(entry.title)
                    .addText(entry.subtitle.isEmpty() ? tr("เลือกเป็นจุดหมาย", "Choose destination") : entry.subtitle)
                    .setOnClickListener(() -> getScreenManager().push(
                            new NaviRoutePreviewScreen(getCarContext(), entry.position, entry.title)))
                    .build());
        }
        if (count == 0) {
            String message = !searched
                    ? tr("ค้นหาจุดหมาย หรือเลือกบ้าน ที่ทำงาน หรือสถานที่ล่าสุด",
                    "Search, or choose Home, Work or a recent place")
                    : !error.isEmpty() ? error : tr("ไม่พบสถานที่ในพื้นที่นี้", "No results in this region");
            items.addItem(new Row.Builder().setTitle(message).build());
        }
        return builder.setItemList(items.build()).build();
    }

    private List<Entry> savedPlaces() {
        List<Entry> out = new ArrayList<>();
        addShortcut(out, "home", tr("บ้าน", "Home"));
        addShortcut(out, "work", tr("ที่ทำงาน", "Work"));
        JSONArray recents = SavedPlaces.list(getCarContext(), "recents");
        for (int i = 0; i < recents.length() && out.size() < listLimit(); i++) {
            JSONObject value = recents.optJSONObject(i);
            addJson(out, value, tr("สถานที่ล่าสุด", "Recent place"));
        }
        return out;
    }

    private void addShortcut(List<Entry> out, String key, String label) {
        addJson(out, SavedPlaces.shortcut(getCarContext(), key), label);
    }

    private void addJson(List<Entry> out, JSONObject value, String label) {
        if (value == null || out.size() >= listLimit()) return;
        double lat = value.optDouble("lat", Double.NaN);
        double lon = value.optDouble("lon", Double.NaN);
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return;
        String name = value.optString("title", "");
        if (name.isEmpty()) return;
        out.add(new Entry(new LatLng(lat, lon), name, label + distanceSuffix(lat, lon)));
    }

    private void search(String text) {
        term = text == null ? "" : text.trim();
        if (term.length() < 2) return;
        loading = true;
        searched = true;
        widened = false;
        results.clear();
        error = "";
        invalidate();
        runQuery(true);
    }

    private void runQuery(boolean bias) {
        try {
            MapsInitializer.updatePrivacyShow(getCarContext(), true, true);
            MapsInitializer.updatePrivacyAgree(getCarContext(), true);
            MapsInitializer.updateCountryCode("TH");
            MapsInitializer.initRegionLanguageType(NaviLocale.thai(getCarContext()) ? 7 : 2);
            PoiSearch.Query query = new PoiSearch.Query(term, "", "");
            query.setPageSize(listLimit());
            query.setPageNum(1);
            active = new PoiSearch(getCarContext(), query);
            LatLng here = TripStore.location(getCarContext());
            if (bias && here != null) {
                active.setBound(new PoiSearch.SearchBound(
                        new LatLonPoint(here.latitude, here.longitude), 50000, true));
            }
            active.setOnPoiSearchListener(this);
            active.searchPOIAsyn();
        } catch (Exception e) {
            fallback(term);
        }
    }

    @Override public void onPoiSearched(PoiResult result, int code) {
        main.post(() -> {
            if (getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) return;
            results.clear();
            if (code == 1000 && result != null && result.getPois() != null) {
                for (PoiItem item : result.getPois()) {
                    if (item.getLatLonPoint() == null || results.size() >= listLimit()) continue;
                    double lat = item.getLatLonPoint().getLatitude();
                    double lon = item.getLatLonPoint().getLongitude();
                    String snippet = item.getSnippet() == null ? "" : item.getSnippet();
                    results.add(new Entry(
                            new LatLng(lat, lon),
                            item.getTitle() == null ? tr("สถานที่", "Place") : item.getTitle(),
                            snippet + distanceSuffix(lat, lon)));
                }
            }
            if (results.isEmpty() && !widened) {
                widened = true;
                runQuery(false);
                return;
            }
            if (results.isEmpty()) fallback(term);
            else {
                loading = false;
                invalidate();
            }
        });
    }

    private String distanceSuffix(double lat, double lon) {
        LatLng here = TripStore.location(getCarContext());
        if (here == null) return "";
        double meters = RouteGuide.meters(here.latitude, here.longitude, lat, lon);
        String text = meters < 950
                ? Math.round(meters) + tr(" ม.", " m")
                : String.format(java.util.Locale.US, "%.1f", meters / 1000.0) + tr(" กม.", " km");
        return (text.isEmpty() ? "" : " · " + text);
    }

    private void fallback(String query) {
        if (!query.equals(term)) {
            loading = false;
            invalidate();
            return;
        }
        boolean started = McpPlacesClient.search(getCarContext(), query, false, (places, failure) -> {
            if (!query.equals(term) || getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) return;
            results.clear();
            for (McpPlacesClient.Place place : places) {
                if (results.size() >= listLimit()) break;
                results.add(new Entry(new LatLng(place.latitude, place.longitude), place.name,
                        place.address + distanceSuffix(place.latitude, place.longitude)));
            }
            error = failure == null ? "" : tr("บริการค้นหาสำรองไม่พร้อม", "Optional search service unavailable");
            loading = false;
            invalidate();
        });
        if (!started) {
            loading = false;
            error = tr("ไม่พบผลลัพธ์จาก AMAP", "AMAP returned no places");
            invalidate();
        }
    }

    @Override public void onPoiItemSearched(PoiItem item, int code) {}
}
