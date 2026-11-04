package com.navith.amap.car;

import androidx.annotation.NonNull;
import android.os.Handler;
import android.os.Looper;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.model.SearchTemplate;
import androidx.car.app.model.Template;

import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.amap.api.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

/** Search through Android Auto's voice/keyboard-safe search UI, not a custom text field. */
final class NaviSearchScreen extends Screen implements PoiSearch.OnPoiSearchListener {
    private final NaviCarScreen owner;
    private final List<PoiItem> results = new ArrayList<>();
    private String term = "";
    private boolean loading = false;
    private boolean searched = false;
    private PoiSearch active;
    NaviSearchScreen(@NonNull CarContext c, NaviCarScreen owner) { super(c); this.owner=owner; }

    @NonNull @Override public Template onGetTemplate() {
        SearchTemplate.Builder b = new SearchTemplate.Builder(new SearchTemplate.SearchCallback() {
            @Override public void onSearchTextChanged(@NonNull String text) { term = text; }
            @Override public void onSearchSubmitted(@NonNull String text) { search(text); }
        }).setHeaderAction(Action.BACK).setSearchHint("ค้นหาสถานที่ ประเทศไทย")
                .setInitialSearchText(term).setShowKeyboardByDefault(!searched);
        if (loading) b.setLoading(true);
        else {
            ItemList.Builder list = new ItemList.Builder();
            for (PoiItem item : results) {
                if (item.getLatLonPoint()==null) continue;
                String title = item.getTitle()==null ? "สถานที่" : item.getTitle();
                String sub = item.getSnippet()==null ? "เลือกเป็นจุดหมาย" : item.getSnippet();
                list.addItem(new Row.Builder().setTitle(title).addText(sub)
                        .setOnClickListener(() -> {
                            LatLng dst = new LatLng(item.getLatLonPoint().getLatitude(),item.getLatLonPoint().getLongitude());
                            owner.onPlaceSelected(dst,title);
                            getScreenManager().pop();
                        }).build());
            }
            if (results.isEmpty() && searched) {
                list.addItem(new Row.Builder().setTitle("ไม่พบสถานที่ หรือบริการค้นหาไม่พร้อม").build());
            }
            b.setItemList(list.build());
        }
        return b.build();
    }
    private void search(String text) {
        term=text==null?"":text.trim();
        if (term.isEmpty()) return;
        loading=true;searched=true;results.clear();invalidate();
        try {
            PoiSearch.Query q = new PoiSearch.Query(term,"","");q.setPageSize(6);q.setPageNum(1);
            active = new PoiSearch(getCarContext(),q);
            active.setOnPoiSearchListener(this);
            active.searchPOIAsyn();
        } catch (Exception e) { loading=false;invalidate(); }
    }
    @Override public void onPoiSearched(PoiResult result, int errorCode) {
        new Handler(Looper.getMainLooper()).post(() -> {
            loading=false;results.clear();
            if (errorCode==1000 && result!=null && result.getPois()!=null) {
                for (PoiItem p: result.getPois()) { if (p.getLatLonPoint()!=null && results.size()<6) results.add(p); }
            }
            invalidate();
        });
    }
    @Override public void onPoiItemSearched(PoiItem item,int code) {}
}
