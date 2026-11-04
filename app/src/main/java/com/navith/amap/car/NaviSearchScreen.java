package com.navith.amap.car;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.model.SearchTemplate;
import androidx.car.app.model.Template;

import com.amap.api.maps.model.LatLng;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.navith.amap.McpPlacesClient;
import com.navith.amap.NaviLocale;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Android Auto-safe search UI. Host handles voice/keyboard restrictions;
 * 6 recent/favorite destinations or search results maximum.
 * Optional MCP fallback requires explicit configuration on the phone.
 */
final class NaviSearchScreen extends Screen implements PoiSearch.OnPoiSearchListener {
    private static final int MAX_RESULTS=6;
    private final NaviCarScreen owner;
    private final List<Entry> results=new ArrayList<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private String term="";
    private boolean loading=false, searched=false, fromMcp=false;
    private String error="";
    private PoiSearch active;

    private static final class Entry {
        final LatLng position;
        final String title, subtitle;
        Entry(LatLng p,String t,String s){position=p;title=t;subtitle=s;}
    }
    NaviSearchScreen(@NonNull CarContext c,NaviCarScreen owner) {
        super(c);this.owner=owner;
    }
    private String tr(String th,String en){return NaviLocale.text(getCarContext(),th,en);}
    @NonNull @Override public Template onGetTemplate() {
        SearchTemplate.Builder b=new SearchTemplate.Builder(new SearchTemplate.SearchCallback(){
            @Override public void onSearchTextChanged(@NonNull String text){term=text;}
            @Override public void onSearchSubmitted(@NonNull String text){search(text);}
        }).setHeaderAction(Action.BACK)
            .setSearchHint(tr("ค้นหาสถานที่หรือที่อยู่","Search places or addresses"))
            .setInitialSearchText(term)
            .setShowKeyboardByDefault(!searched);
        if(loading){b.setLoading(true);return b.build();}
        ItemList.Builder items=new ItemList.Builder();
        List<Entry> shown=searched?results:savedPlaces();
        for(Entry e:shown){
            items.addItem(new Row.Builder()
                .setTitle(e.title)
                .addText(e.subtitle.isEmpty() ?
                    tr("เลือกเป็นจุดหมาย","Choose destination") : e.subtitle)
                .setOnClickListener(()->{
                    owner.onPlaceSelected(e.position,e.title);
                    getScreenManager().pop();
                }).build());
        }
        if(shown.isEmpty()){
            String message=!searched?tr("ค้นหาจุดหมาย หรือเลือกสถานที่ล่าสุดจากโทรศัพท์",
                                            "Search or choose a saved place on your phone")
                : !error.isEmpty()?error:tr("ไม่พบสถานที่ในพื้นที่นี้","No results in this region");
            items.addItem(new Row.Builder().setTitle(message).build());
        }
        b.setItemList(items.build());
        return b.build();
    }
    private List<Entry> savedPlaces() {
        List<Entry> out=new ArrayList<>();
        android.content.SharedPreferences p=getCarContext().getSharedPreferences("navith_places",Context.MODE_PRIVATE);
        for(String key:new String[]{"favorites","recents"}){
            JSONArray values;
            try{values=new JSONArray(p.getString(key,"[]"));}catch(Exception e){continue;}
            for(int i=0;i<Math.min(values.length(),MAX_RESULTS) && out.size()<MAX_RESULTS;i++){
                JSONObject v=values.optJSONObject(i);
                if(v==null)continue;
                double lat=v.optDouble("lat",Double.NaN),lon=v.optDouble("lon",Double.NaN);
                if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)continue;
                String name=v.optString("title","");
                if(name.isEmpty())continue;
                boolean duplicate=false;
                for(Entry e:out)if(Math.abs(e.position.latitude-lat)<0.00001 &&
                     Math.abs(e.position.longitude-lon)<0.00001){duplicate=true;break;}
                if(!duplicate)out.add(new Entry(new LatLng(lat,lon),name,
                    "favorites".equals(key)?tr("★ สถานที่โปรด","★ Favorite"):
                        tr("สถานที่ล่าสุด","Recent place")));
            }
        }
        return out;
    }
    private void search(String text){
        term=text==null?"":text.trim();
        if(term.length()<2)return;
        loading=true;searched=true;fromMcp=false;results.clear();error="";
        invalidate();
        try {
            PoiSearch.Query q=new PoiSearch.Query(term,"","");
            q.setPageSize(MAX_RESULTS);q.setPageNum(1);
            active=new PoiSearch(getCarContext(),q);
            active.setOnPoiSearchListener(this);
            active.searchPOIAsyn();
        }catch(Exception e){fallback(term);}
    }
    @Override public void onPoiSearched(PoiResult result,int code){
        main.post(()->{
            if(getLifecycle().getCurrentState()==androidx.lifecycle.Lifecycle.State.DESTROYED)return;
            results.clear();
            if(code==1000 && result!=null && result.getPois()!=null) {
                for(PoiItem p:result.getPois()){
                    if(p.getLatLonPoint()==null || results.size()>=MAX_RESULTS)continue;
                    results.add(new Entry(
                        new LatLng(p.getLatLonPoint().getLatitude(),p.getLatLonPoint().getLongitude()),
                        p.getTitle()==null?tr("สถานที่","Place"):p.getTitle(),
                        p.getSnippet()==null?"":p.getSnippet()));
                }
            }
            if(results.isEmpty())fallback(term);
            else {loading=false;invalidate();}
        });
    }
    private void fallback(String query) {
        if(!query.equals(term)){loading=false;invalidate();return;}
        boolean started=McpPlacesClient.search(getCarContext(),query,false,(places,failure)->{
            if(!query.equals(term) ||
                getLifecycle().getCurrentState()==androidx.lifecycle.Lifecycle.State.DESTROYED)return;
            results.clear();fromMcp=true;
            for(McpPlacesClient.Place p:places){
                if(results.size()>=MAX_RESULTS)break;
                results.add(new Entry(new LatLng(p.latitude,p.longitude),p.name,p.address));
            }
            error=failure==null ? "" : tr("บริการค้นหาสำรองไม่พร้อม",
                                              "Optional search service unavailable");
            loading=false;invalidate();
        });
        if(!started){
            loading=false;
            error=tr("ไม่พบผลลัพธ์จาก AMAP • เลือกสถานที่จากมือถือ",
                     "AMAP returned no places. Try selecting on phone.");
            invalidate();
        }
    }
    @Override public void onPoiItemSearched(PoiItem item,int code){}
}
