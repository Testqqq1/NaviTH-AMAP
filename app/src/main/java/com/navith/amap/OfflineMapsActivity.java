package com.navith.amap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.offlinemap.OfflineMapCity;
import com.amap.api.maps.offlinemap.OfflineMapManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * City-level offline map browser.
 * Availability is determined by the AMAP SDK/server/account, never hardcoded.
 * Offline map tiles do NOT enable offline routing or navigation.
 */
public final class OfflineMapsActivity extends Activity
        implements OfflineMapManager.OfflineMapDownloadListener {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private OfflineMapManager manager;
    private TextView status;
    private LinearLayout list;
    private EditText filter;
    private boolean loaded;
    private boolean alive = true;
    private final ArrayList<OfflineMapCity> available = new ArrayList<>();
    private String selected = "";

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(NaviLocale.wrap(base));
    }
    private String tr(String th,String en) {return NaviLocale.text(this,th,en);}
    private int dp(int d) {return (int)(d*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;
    }
    private TextView text(String value,int size,boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(getColor(R.color.nav_text));
        if (bold)t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.nav_surface));setContentView(root);
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10),dp(8),dp(18),dp(8));
        header.setBackgroundColor(getColor(R.color.nav_card));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));
        TextView back=text("‹",34,false);
        back.setGravity(Gravity.CENTER);
        header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        back.setContentDescription(tr("ย้อนกลับ","Back"));back.setOnClickListener(v->finish());
        header.addView(text(tr("แผนที่ออฟไลน์","Offline maps"),21,true));

        TextView explanation=text(tr(
            "ดาวน์โหลดแผนที่รายเมืองที่ AMAP เปิดให้บัญชีนี้เท่านั้น · ไม่ใช่การนำทางออฟไลน์",
            "Only cities available from your AMAP service can be downloaded. Offline maps are NOT offline navigation."),
            13,false);
        explanation.setTextColor(getColor(R.color.nav_secondary));
        explanation.setPadding(dp(18),dp(18),dp(18),dp(12));
        root.addView(explanation);

        filter=new EditText(this);
        filter.setSingleLine(true);
        filter.setHint(tr("ค้นหาเมืองจากรายการที่รองรับ","Filter available cities"));
        filter.setTextSize(15);
        filter.setPadding(dp(16),0,dp(12),0);
        filter.setBackground(shape(getColor(R.color.nav_card),16));
        LinearLayout.LayoutParams fparam=new LinearLayout.LayoutParams(-1,dp(52));
        fparam.setMargins(dp(16),0,dp(16),dp(8));
        root.addView(filter,fparam);
        filter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){refreshList();}
            @Override public void afterTextChanged(Editable e){}
        });

        status=text(tr("กำลังตรวจสอบบริการแผนที่ออฟไลน์…","Checking offline-map availability…"),13,false);
        status.setTextColor(getColor(R.color.nav_secondary));
        status.setPadding(dp(18),dp(8),dp(18),dp(8));
        root.addView(status);

        ScrollView scroll=new ScrollView(this);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16),dp(8),dp(16),dp(22));scroll.addView(list);

        LinearLayout footer=new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(16),dp(5),dp(16),dp(10));
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(62)));
        addFooterAction(footer,tr("รีเฟรช","Refresh"),()->refreshAvailability());
        addFooterAction(footer,tr("พักดาวน์โหลด","Pause"),()->{
            if(manager!=null){manager.pause();show(tr("พักรายการดาวน์โหลดแล้ว","Downloads paused"));}});
        addFooterAction(footer,tr("หยุดทั้งหมด","Stop all"),()->{
            if(manager!=null){manager.stop();show(tr("หยุดคิวดาวน์โหลดแล้ว","Download queue stopped"));}});
        if(!getSharedPreferences("navith_consent",MODE_PRIVATE).getBoolean("consent",false)){
            status.setText(tr("ต้องยอมรับการใช้ AMAP SDK ในหน้าแผนที่ก่อน",
                "Accept AMAP privacy consent on the map first."));
            return;
        }
        MapsInitializer.updatePrivacyShow(this,true,true);
        MapsInitializer.updatePrivacyAgree(this,true);
        MapsInitializer.updateCountryCode("TH");
        // Defer SDK startup until UI is drawn, and protect against restricted service.
        ui.post(this::initializeManager);
    }
    private void addFooterAction(LinearLayout container,String title,Runnable action){
        TextView b=text(title,13,true);b.setGravity(Gravity.CENTER);
        b.setTextColor(getColor(R.color.nav_primary));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);
        lp.setMargins(dp(3),0,dp(3),0);container.addView(b,lp);
        b.setBackground(shape(getColor(R.color.nav_card),14));
        b.setOnClickListener(v->action.run());
    }
    private void show(String msg){if(alive&&status!=null)status.setText(msg);}
    private void initializeManager(){
        if(!alive || manager!=null)return;
        try {
            manager=new OfflineMapManager(this,this);
            manager.setOnOfflineLoadedListener(()->ui.post(()->{
                if(!alive)return;
                loaded=true;refreshAvailability();
            }));
            refreshAvailability();
        }catch(Exception | LinkageError ex){
            show(tr("บริการ Offline Map ไม่พร้อมหรือไม่มีสิทธิ์ใน SDK/บัญชีนี้",
                "Offline-map service is unavailable or not authorized for this account."));
        }
    }
    private void refreshAvailability(){
        if(!alive || manager==null)return;
        try{
            List<OfflineMapCity> cities=manager.getOfflineMapCityList();
            available.clear();
            if(cities!=null)available.addAll(cities);
            refreshList();
            if(available.isEmpty()) {
                show(tr("ยังไม่มีรายการเมืองที่ AMAP ให้ดาวน์โหลด · ตรวจสิทธิ์บริการและอินเทอร์เน็ต",
                    "AMAP returned no downloadable cities. Check service entitlement and network."));
            }else {
                show(tr("มีรายการจาก AMAP ","AMAP lists ")+available.size()+
                    tr(" เมือง · เลือกดาวน์โหลดเฉพาะเมืองที่ต้องใช้"," cities. Select only areas you need."));
            }
        }catch(Exception ex){
            show(tr("อ่านรายการเมืองไม่ได้ • ตรวจสิทธิ์บริการออฟไลน์",
                "Could not load city list. Check offline-map entitlement."));
        }
    }
    private void refreshList(){
        if(list==null)return;
        list.removeAllViews();
        String query=filter==null?"":filter.getText().toString().trim().toLowerCase(Locale.ROOT);
        if(manager==null)return;
        List<OfflineMapCity> downloaded;
        try{downloaded=manager.getDownloadOfflineMapCityList();}
        catch(Exception e){downloaded=new ArrayList<>();}
        for(OfflineMapCity city:available){
            if(city==null||city.getCity()==null)continue;
            if(!query.isEmpty()&&!city.getCity().toLowerCase(Locale.ROOT).contains(query)
                && (city.getCode()==null||!city.getCode().contains(query)))continue;
            boolean installed=false;
            if(downloaded!=null)for(OfflineMapCity d:downloaded){
                if(d!=null && city.getCode()!=null && city.getCode().equals(d.getCode())) {
                    installed=true;break;
                }
            }
            final boolean isDownloaded=installed;
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(16),dp(10),dp(16),dp(10));
            row.setBackground(shape(getColor(R.color.nav_card),16));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin=dp(8);list.addView(row,lp);
            row.addView(text(city.getCity(),16,true));
            String meta=(city.getSize()>0?
                String.format(Locale.US,"%.1f MB",city.getSize()/1048576.0):tr("ไม่ทราบขนาด","Size unknown"));
            if(installed)meta+=" · "+tr("ดาวน์โหลดแล้ว","Installed");
            TextView sub=text(meta,12,false);sub.setTextColor(getColor(R.color.nav_secondary));
            row.addView(sub);
            row.setMinimumHeight(dp(64));
            row.setOnClickListener(v->chooseCity(city,isDownloaded));
        }
    }
    private void chooseCity(OfflineMapCity city,boolean installed){
        if(manager==null)return;
        String name=city.getCity();
        if(installed) {
            new AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage(tr("ลบข้อมูลแผนที่ออฟไลน์ของเมืองนี้?","Delete offline-map tiles for this city?"))
                .setNegativeButton(tr("ยกเลิก","Cancel"),null)
                .setPositiveButton(tr("ลบ","Delete"),(d,w)->{
                    if(!loaded){
                        show(tr("รอ AMAP ตรวจรายการให้เสร็จก่อนลบ",
                            "Wait until AMAP finishes validating downloaded maps."));
                        return;
                    }
                    try{manager.remove(name);}
                    catch(Exception e){show(tr("ลบไม่ได้","Could not delete city"));}
                }).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(tr("ดาวน์โหลด ","Download ")+name)
            .setMessage(tr(
                "ใช้ข้อมูลมือถือหรือ Wi-Fi ตามการเชื่อมต่อปัจจุบัน แผนที่ที่ดาวน์โหลดช่วยดูข้อมูลแผนที่โดยไม่ใช้อินเทอร์เน็ต แต่ไม่รวมการคำนวณเส้นทางออฟไลน์",
                "Download may use cellular data. Offline map tiles can be viewed without internet but do not include offline routing."))
            .setNegativeButton(tr("ยกเลิก","Cancel"),null)
            .setPositiveButton(tr("ดาวน์โหลด","Download"),(d,w)->{
                try{
                    selected=name;
                    if(city.getCode()!=null&&!city.getCode().isEmpty())manager.downloadByCityCode(city.getCode());
                    else manager.downloadByCityName(name);
                    show(tr("เริ่มดาวน์โหลด ","Started download: ")+name);
                }catch(Exception e){
                    show(tr("ดาวน์โหลดไม่ได้ อาจไม่มีสิทธิ์หรือไม่รองรับภูมิภาคนี้",
                            "Download unavailable: check account and regional support."));
                }
            }).show();
    }
    @Override public void onDownload(int state,int percent,String city){
        ui.post(()->{
            if(!alive)return;
            show((city==null?"":city)+" · "+tr("ดาวน์โหลด ","Download ")+
                Math.max(0,Math.min(100,percent))+"% "+
                tr(" (สถานะ "," (state ")+state+")");
            // Progress status depends on SDK; do not interpret arbitrary state as success.
            if(percent==100)refreshAvailability();
        });
    }
    @Override public void onCheckUpdate(boolean update,String city){
        ui.post(()->{if(alive)show((city==null?"":city)+
            (update?tr(" มีอัปเดต"," update available"):tr(" ไม่พบอัปเดต"," up to date")));});
    }
    @Override public void onRemove(boolean success,String city,String detail){
        ui.post(()->{
            if(!alive)return;
            show(success?tr("ลบข้อมูลแล้ว: ","Removed: ")+city:
                tr("ลบไม่สำเร็จ: ","Removal failed: ")+city);
            refreshAvailability();
        });
    }
    @Override protected void onDestroy(){
        alive=false;
        if(manager!=null){try{manager.destroy();}catch(Exception ignored){}manager=null;}
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
