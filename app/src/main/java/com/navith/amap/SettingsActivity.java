package com.navith.amap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.File;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.navith.amap.car.TripStore;
import com.amap.api.maps.model.LatLng;

import java.util.Locale;

/** User-facing full-screen settings, separate from the map controls. */
public final class SettingsActivity extends Activity {
    private static final int PICK_MARKER=830;
    private SharedPreferences prefs;
    private LinearLayout content;
    private int primary, secondary, accent;
    private String tx(String th, String en) { return NaviLocale.text(this,th,en); }

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(NaviLocale.wrap(base)); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("navith_view", MODE_PRIVATE);
        primary=getColor(R.color.nav_text);
        secondary=getColor(R.color.nav_secondary);
        accent=getColor(R.color.nav_primary);
        build();
    }
    private int dp(float x) { return (int)(x*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable surface(int color, int radius) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;
    }
    private TextView label(String title, int sp, int color, boolean bold) {
        TextView tv=new TextView(this);
        tv.setText(title);tv.setTextSize(sp);tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }
    private void build() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.nav_surface));
        setContentView(root);
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(getColor(R.color.nav_card));
        header.setPadding(dp(12),dp(8),dp(16),dp(8));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));
        TextView back=label("‹",34,primary,false);
        back.setGravity(Gravity.CENTER);back.setContentDescription(tx("ย้อนกลับ","Back"));
        header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        back.setOnClickListener(v->finish());
        TextView title=label(tx("ตั้งค่า","Settings"),22,primary,true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1f);
        titleLp.leftMargin=dp(8);header.addView(title,titleLp);
        TextView brand=label("NaviTH Maps",12,secondary,false);
        header.addView(brand);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),dp(12),dp(16),dp(32));
        scroll.addView(content);
        section(tx("การแสดงผล","Appearance"));
        groupStart();
        line(tx("มุมมองแผนที่","Map view"),tx("2D หรือ 3D","2D or 3D"),
            prefs.getBoolean("view3d",true)?"3D":"2D",()->pickMode());
        line(tx("ธีม","Theme"),tx("ตามเวลาหรือเลือกเอง","Automatic or manual"),
            themeName(),()->chooseTheme());
        line(tx("ซูมเริ่มต้น","Default zoom"),tx("ใช้เมื่อกดตำแหน่งของฉัน","Used when locating"),
            String.format(Locale.US,"%.0f",prefs.getFloat("zoom",16f)),()->pickZoom());
        section(tx("ชั้นข้อมูลแผนที่","Map layers"));
        groupStart();
        toggle(tx("แสดงชื่อสถานที่","Base map labels"),
            tx("ซ่อนเพื่อให้เส้นทางอ่านง่ายขึ้น","Hide for a quieter map"),"labels",false);
        toggle(tx("การจราจรสด","Live traffic"),
            tx("ขึ้นกับข้อมูลพื้นที่ของ AMAP","AMAP regional coverage varies"),"traffic",true);
        toggle(tx("อาคาร 3D","3D buildings"),tx("ขึ้นกับข้อมูลและระดับซูม","Depends on map coverage and zoom"),
            "buildings",true);
        line(tx("ประเภทแผนที่","Map type"),tx("มาตรฐาน / ดาวเทียม","Normal / satellite"),
            "satellite".equals(prefs.getString("theme","auto"))?tx("ดาวเทียม","Satellite"):tx("มาตรฐาน","Normal"),()->chooseTheme());
        section(tx("การวางเส้นทาง","Route planning"));
        groupStart();
        toggle(tx("เลี่ยงค่าผ่านทาง","Avoid tolls"),null,"avoid_tolls",false);
        toggle(tx("เลี่ยงทางด่วน","Avoid highways"),null,"avoid_highway",false);
        toggle(tx("เลี่ยงรถติด","Avoid congestion"),tx("เมื่อ AMAP มีข้อมูล","When AMAP data is available"),
            "avoid_traffic",true);
        line(tx("หน่วยความเร็ว","Speed units"),null,
            prefs.getBoolean("mph",false)?"mph":"km/h",
            ()->{prefs.edit().putBoolean("mph",!prefs.getBoolean("mph",false)).apply();build();});
        section(tx("ตำแหน่งและความเป็นส่วนตัว","Location & privacy"));
        groupStart();
        toggle(tx("แสดงตำแหน่งของฉัน","Show my location"),
            tx("ซ่อนสัญลักษณ์เท่านั้น ไม่ปิด GPS","Hide map marker; GPS remains enabled"),
            "show_location",true);
        line(tx("จุดจอดรถล่าสุด","Last parking position"),tx("พิกัดเมื่อสิ้นสุดการเชื่อมต่อจอรถ","Last connected car position"),"›",()->parking());
        section(tx("ภาษาและการเข้าถึง","Language & accessibility"));
        groupStart();
        line(tx("ภาษาแอป","App language"),tx("ชื่อสถานที่ขึ้นกับข้อมูลแผนที่","Map labels depend on provider"),
            NaviLocale.thai(this)?"ไทย":"English",()->chooseLanguage());
        section(tx("ขั้นสูง","Advanced"));
        groupStart();
        line(tx("โมเดลตำแหน่ง","Position marker"),
            tx("รถ / ลูกศร / รูปส่วนตัว (2D)","Car / arrow / personal image (2D)"),"›",
            ()->chooseMarker());
        line(tx("รายละเอียดการรองรับ","Feature availability"),
            tx("ดูขีดจำกัดของ Android Auto และ AMAP","Android Auto & AMAP capabilities"),"›",
            ()->new AlertDialog.Builder(this).setTitle(tx("สถานะ NaviTH","NaviTH status"))
                .setMessage(tx("การนำทางเลี้ยวต่อเลี้ยว, แผนที่ออฟไลน์หลายประเทศ, ระบบเตือนเลน, กล้องจับความเร็ว, EV routing และรายงานเหตุการณ์ ยังไม่มีใน SDK ชุดนี้\n\nหน้าจอรถเป็นเพียงแผนที่และตัวอย่างเส้นทาง จึงไม่ใช่ระบบนำทางจริง",
                    "Turn-by-turn navigation, multi-country offline routing, lane guidance, safety cameras, EV routing and incident reporting are not yet available. Android Auto provides a map and route preview only."))
                .setPositiveButton(tx("เข้าใจแล้ว","OK"),null).show());
        TextView note=label(tx("NaviTH Maps • แผนที่โดย AMAP","NaviTH Maps • Powered by AMAP"),12,secondary,false);
        note.setGravity(Gravity.CENTER);note.setPadding(0,dp(18),0,dp(12));
        content.addView(note);
    }
    private void section(String s) {
        TextView title=label(s,14,accent,true);
        title.setPadding(dp(8),dp(20),0,dp(9));
        content.addView(title);
    }
    private LinearLayout lastGroup;
    private void groupStart() {
        lastGroup=new LinearLayout(this);
        lastGroup.setOrientation(LinearLayout.VERTICAL);
        lastGroup.setBackground(surface(getColor(R.color.nav_card),20));
        lastGroup.setPadding(dp(14),dp(4),dp(14),dp(4));
        content.addView(lastGroup);
    }
    private void line(String title,String subtitle,String value,Runnable action) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4),dp(10),dp(4),dp(10));
        row.setMinimumHeight(dp(64));
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left,new LinearLayout.LayoutParams(0,-2,1f));
        left.addView(label(title,16,primary,true));
        if(subtitle!=null) {
            TextView small=label(subtitle,12,secondary,false);small.setPadding(0,dp(2),0,0);
            left.addView(small);
        }
        TextView v=label(value+"  ›",13,secondary,false);
        v.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        row.addView(v);row.setOnClickListener(w->action.run());
        row.setFocusable(true);row.setContentDescription(title+" "+value);
        lastGroup.addView(row);divider();
    }
    private void toggle(String title,String subtitle,String key,boolean def) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4),dp(8),dp(2),dp(8));row.setMinimumHeight(dp(64));
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left,new LinearLayout.LayoutParams(0,-2,1f));
        left.addView(label(title,16,primary,true));
        if(subtitle!=null)left.addView(label(subtitle,12,secondary,false));
        Switch sw=new Switch(this);sw.setButtonTintList(null);
        sw.setChecked(prefs.getBoolean(key,def));
        sw.setOnCheckedChangeListener((b,c)->prefs.edit().putBoolean(key,c).apply());
        row.addView(sw);
        row.setOnClickListener(w->sw.setChecked(!sw.isChecked()));
        lastGroup.addView(row);divider();
    }
    private void divider() {
        View line=new View(this);line.setBackgroundColor(getColor(R.color.nav_border));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));
        lastGroup.addView(line,lp);
    }
    private String themeName() {
        String t=prefs.getString("theme","auto");
        if("night".equals(t))return tx("กลางคืน","Night");
        if("day".equals(t))return tx("กลางวัน","Day");
        if("satellite".equals(t))return tx("ดาวเทียม","Satellite");
        return tx("อัตโนมัติ","Automatic");
    }
    private void chooseTheme() {
        String[] keys={"auto","day","night","satellite"};
        String[] names={tx("อัตโนมัติ","Automatic"),tx("กลางวัน","Day"),tx("กลางคืน","Night"),tx("ดาวเทียม","Satellite")};
        new AlertDialog.Builder(this).setTitle(tx("ธีมแผนที่","Map theme")).setItems(names,(d,i)->{
            prefs.edit().putString("theme",keys[i]).apply();build();
        }).show();
    }
    private void pickMode() {
        String[] opts={"2D","3D"};
        new AlertDialog.Builder(this).setTitle(tx("มุมมองแผนที่","Map view")).setItems(opts,(d,i)->{
            prefs.edit().putBoolean("view3d",i==1).apply();build();
        }).show();
    }
    private void pickZoom() {
        String[] opts={"10", "13", "16", "18"};float[] levels={10f,13f,16f,18f};
        new AlertDialog.Builder(this).setTitle(tx("ซูมเริ่มต้น","Default zoom")).setItems(opts,(d,i)->{
            prefs.edit().putFloat("zoom",levels[i]).apply();build();
        }).show();
    }
    private void chooseLanguage() {
        String[] opts={"ไทย","English"};
        new AlertDialog.Builder(this).setTitle(tx("ภาษาแอป","App language")).setItems(opts,(d,i)->{
            String value=i==0?"th":"en";
            if(!value.equals(NaviLocale.language(this))) {
                prefs.edit().putString("language",value).apply();
                recreate();
            }
        }).show();
    }
    private void parking() {
        LatLng l=TripStore.parkedLocation(this);
        if(l==null){
            Toast.makeText(this,tx("ยังไม่มีจุดจอดที่บันทึก","No parking position saved"),Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle(tx("จุดจอดล่าสุด (ประมาณ)","Last connected position"))
            .setMessage(String.format(Locale.US,"%.6f, %.6f",l.latitude,l.longitude))
            .setPositiveButton(tx("รับทราบ","OK"),null).show();
    }
    private void chooseMarker() {
        String[] opts={tx("รถเก๋ง","Sedan"),tx("แฮทช์แบ็ค","Hatchback"),
            tx("รถตู้","Van"),"SUV",tx("กระบะ","Pickup"),tx("รถสปอร์ต","Sports car"),
            tx("ลูกศร","Arrow"),tx("เลือกรูปของฉัน","Choose my image")};
        new AlertDialog.Builder(this).setTitle(tx("รูปตำแหน่งบนแผนที่ (2D)","Position marker (2D)"))
            .setItems(opts,(d,i)->{
                if(i==7){
                    Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(pick,PICK_MARKER);
                }else{
                    prefs.edit().putInt("vehicle_icon",i).apply();
                    build();
                }
            }).show();
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_MARKER || result!=RESULT_OK || data==null || data.getData()==null)return;
        try {
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inJustDecodeBounds=true;
            try(InputStream in=getContentResolver().openInputStream(data.getData())){
                BitmapFactory.decodeStream(in,null,options);
            }
            if(options.outWidth<=0 || options.outHeight<=0)throw new IllegalArgumentException("Invalid image");
            options.inJustDecodeBounds=false;
            options.inSampleSize=1;
            while(Math.max(options.outWidth/options.inSampleSize, options.outHeight/options.inSampleSize)>512)
                options.inSampleSize*=2;
            Bitmap bitmap;
            try(InputStream in=getContentResolver().openInputStream(data.getData())){
                bitmap=BitmapFactory.decodeStream(in,null,options);
            }
            if(bitmap==null)throw new IllegalArgumentException("Unable to decode");
            Bitmap small=Bitmap.createScaledBitmap(bitmap,96,96,true);
            try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"navith_marker.png"))){
                small.compress(Bitmap.CompressFormat.PNG,100,out);
            }
            if(bitmap!=small)bitmap.recycle();
            small.recycle();
            prefs.edit().putInt("vehicle_icon",7).apply();
            Toast.makeText(this,tx("บันทึกรูปตำแหน่งแล้ว","Position image saved"),Toast.LENGTH_SHORT).show();
        }catch(Exception e){
            Toast.makeText(this,tx("เปิดรูปไม่สำเร็จ","Could not open image"),Toast.LENGTH_SHORT).show();
        }
    }
}
