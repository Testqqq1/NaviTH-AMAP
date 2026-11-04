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
import android.widget.EditText;
import android.text.InputType;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import com.navith.amap.car.TripStore;
import com.amap.api.maps.model.LatLng;

import java.util.Locale;

/** User-facing full-screen settings, separate from the map controls. */
public final class SettingsActivity extends Activity {
    private static final int PICK_MARKER=830;
    private static final int PICK_MAP_STYLE=831;
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
    private String openCategory=null;

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
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(tx("ย้อนกลับ","Back"));
        header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        back.setOnClickListener(v->navigateBack());
        TextView title=label(openCategory==null?tx("ตั้งค่า","Settings"):categoryTitle(openCategory),
            22,primary,true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1f);
        titleLp.leftMargin=dp(8);
        header.addView(title,titleLp);
        TextView brand=label("NaviTH Maps",12,secondary,false);
        header.addView(brand);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),dp(14),dp(16),dp(32));
        scroll.addView(content);
        if(openCategory==null) {
            TextView hint=label(tx("ปรับแต่งตามการเดินทางของคุณ",
                "Make NaviTH work for your journey"),14,secondary,false);
            hint.setPadding(dp(8),dp(2),dp(8),dp(16));
            content.addView(hint);
            hubCard("map",tx("แผนที่และการแสดงผล","Map & appearance"),
                tx("มุมมอง · ชั้นข้อมูล · ธีม","View · Layers · Theme"),"◈");
            hubCard("navigation",tx("การนำทาง","Navigation"),
                tx("ประเภทเส้นทาง · ความเร็ว · เสียง","Route preferences · Speed · Voice"),"➤");
            hubCard("offline",tx("แผนที่ออฟไลน์","Offline maps"),
                tx("ดาวน์โหลดพื้นที่ · จัดการข้อมูล","Download areas · Manage data"),"⇩");
            hubCard("privacy",tx("ความเป็นส่วนตัวและตำแหน่ง","Location & privacy"),
                tx("สิทธิ์ตำแหน่ง · สถานที่ที่บันทึก","Permissions · Saved places"),"◎");
            hubCard("advanced",tx("ขั้นสูงและนักพัฒนา","Advanced & developer"),
                tx("AMAP MCP · Map Style · การวินิจฉัย","AMAP MCP · Map Style · Diagnostics"),"⚙");
        } else {
            renderCategory(openCategory);
        }
        TextView note=label(tx("NaviTH Maps • แผนที่โดย AMAP",
            "NaviTH Maps • Powered by AMAP"),12,secondary,false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0,dp(20),0,dp(12));
        content.addView(note);
    }

    private String categoryTitle(String name) {
        switch(name) {
            case "map": return tx("แผนที่และการแสดงผล","Map & appearance");
            case "navigation": return tx("การนำทาง","Navigation");
            case "offline": return tx("แผนที่ออฟไลน์","Offline maps");
            case "privacy": return tx("ความเป็นส่วนตัวและตำแหน่ง","Location & privacy");
            case "advanced": return tx("ขั้นสูงและนักพัฒนา","Advanced & developer");
            default: return tx("ตั้งค่า","Settings");
        }
    }
    private void navigateBack() {
        if(openCategory!=null) {openCategory=null;build();}
        else finish();
    }
    @Override public void onBackPressed() {
        if(openCategory!=null){openCategory=null;build();return;}
        super.onBackPressed();
    }

    private void hubCard(String key,String title,String description,String symbol) {
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14),dp(12),dp(14),dp(12));
        row.setMinimumHeight(dp(84));
        row.setBackground(surface(getColor(R.color.nav_card),20));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(10);content.addView(row,params);
        TextView symbolView=label(symbol,25,accent,true);
        symbolView.setGravity(Gravity.CENTER);
        symbolView.setBackground(surface(getColor(R.color.nav_soft_accent),14));
        row.addView(symbolView,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(0,-2,1f);
        labelParams.leftMargin=dp(14);row.addView(labels,labelParams);
        labels.addView(label(title,16,primary,true));
        TextView desc=label(description,14,secondary,false);
        desc.setPadding(0,dp(3),0,0);labels.addView(desc);
        TextView arrow=label("›",25,secondary,false);arrow.setGravity(Gravity.CENTER);
        row.addView(arrow,new LinearLayout.LayoutParams(dp(30),dp(48)));
        row.setClickable(true);row.setFocusable(true);
        row.setContentDescription(title+". "+description);
        row.setOnClickListener(v->{openCategory=key;build();});
    }

    private void renderCategory(String key) {
        switch(key) {
            case "map":
                section(tx("รูปแบบแผนที่","Map appearance"));groupStart();
                line(tx("มุมมองแผนที่","Map view"),null,
                    prefs.getBoolean("view3d",true)?"3D":"2D",()->pickMode());
                line(tx("ธีมแผนที่","Map theme"),null,themeName(),()->chooseTheme());
                line(tx("ประเภทแผนที่","Map type"),null,
                    "satellite".equals(prefs.getString("theme","auto"))?
                    tx("ดาวเทียม","Satellite"):tx("มาตรฐาน","Normal"),()->chooseTheme());
                section(tx("ชั้นข้อมูล","Layers"));groupStart();
                toggle(tx("แสดงชื่อสถานที่","Map labels"),
                    tx("ขึ้นอยู่กับข้อมูลของ AMAP","Depends on AMAP map data"),"labels",true);
                toggle(tx("การจราจรสด","Live traffic"),
                    tx("เมื่อ AMAP มีข้อมูลในพื้นที่","Where AMAP provides coverage"),"traffic",true);
                toggle(tx("อาคาร 3D","3D buildings"),null,"buildings",true);
                break;
            case "navigation":
                section(tx("การวางเส้นทาง","Route preferences"));groupStart();
                toggle(tx("หลีกเลี่ยงค่าผ่านทาง","Avoid tolls"),null,"avoid_tolls",false);
                toggle(tx("หลีกเลี่ยงทางด่วน","Avoid highways"),null,"avoid_highway",false);
                toggle(tx("หลีกเลี่ยงรถติด","Avoid congestion"),
                    tx("เมื่อผู้ให้บริการรองรับ","When supported by provider"),"avoid_traffic",true);
                line(tx("หน่วยความเร็ว","Speed units"),null,
                    prefs.getBoolean("mph",false)?"mph":"km/h",
                    ()->{prefs.edit().putBoolean("mph",!prefs.getBoolean("mph",false)).apply();build();});
                section(tx("ไอคอนและมุมกล้อง","Marker & camera"));groupStart();
                line(tx("ไอคอนตำแหน่ง","Position icon"),
                    tx("ใช้เหมือนกันบนมือถือและ Android Auto","Shared by phone and Android Auto"),
                    NavigationMarkerFactory.label(this,prefs.getInt("vehicle_icon",6)),
                    ()->chooseMarker());
                line(tx("มุมมองแผนที่","Map perspective"),null,
                    prefs.getBoolean("view3d",true)?"3D":"2D",()->pickMode());
                toggle(tx("ความลึกและเงาของไอคอน","Marker depth and shadow"),
                    tx("ภาพเวกเตอร์ 2.5D ติดระนาบแผนที่ ไม่ใช่โมเดล 3D",
                       "Ground-aligned 2.5D rendering, not a 3D mesh"),
                    "marker_perspective",true);
                section(tx("เสียงและการนำทางจริง","Voice & guidance"));groupStart();
                line(tx("สถานะระบบนำทาง","Navigation availability"),
                    tx("การนำทางจริงต้องใช้ Navigation Test SDK",
                       "Turn-by-turn requires the Navigation Test SDK"),
                    NavSdkBridge.isInstalled()?tx("รุ่นทดลอง","Test only"):tx("ดูเส้นทาง","Preview"),
                    ()->showNavigationDetails());
                break;
            case "offline":
                section(tx("พื้นที่ดาวน์โหลด","Download areas"));groupStart();
                line(tx("จัดการแผนที่ออฟไลน์","Manage offline maps"),
                    tx("รายการเมืองขึ้นกับสิทธิ์ AMAP ไม่รวมการนำทางออฟไลน์",
                       "City availability depends on AMAP; no offline routing"),
                    "›",()->startActivity(new Intent(this,OfflineMapsActivity.class)));
                break;
            case "privacy":
                section(tx("การอนุญาตและตำแหน่ง","Permissions & location"));groupStart();
                line(tx("สิทธิ์ตำแหน่ง","Location permission"),
                    tx("เปลี่ยนได้ในการตั้งค่า Android","Manage in Android settings"),
                    "›",()->openAppPermissions());
                toggle(tx("แสดงตำแหน่งของฉัน","Show my location"),
                    tx("ปิดเฉพาะสัญลักษณ์ ไม่ได้ปิด GPS","Hides marker only; does not stop GPS"),
                    "show_location",true);
                line(tx("ตำแหน่งจอดล่าสุด","Last parking position"),
                    tx("พิกัดสุดท้ายที่จอรถเชื่อมต่อ","Last connected car position"),
                    "›",()->parking());
                section(tx("ข้อมูลในเครื่อง","On-device history"));groupStart();
                line(tx("ล้างสถานที่ล่าสุด","Clear recent places"),
                    tx("ไม่ลบรายการโปรดหรือบ้าน/ที่ทำงาน","Keeps favorites, Home and Work"),
                    "›",()->clearRecentHistory());
                line(tx("ภาษาแอป","App language"),null,
                    NaviLocale.thai(this)?"ไทย":"English",()->chooseLanguage());
                break;
            case "advanced":
                section(tx("เครื่องมือสำหรับนักพัฒนา","Developer tools"));groupStart();
                line(tx("AMAP MCP","AMAP MCP"),tx("ค้นหาสำรองผ่านเซิร์ฟเวอร์ HTTPS",
                    "Optional HTTPS search fallback"),
                    McpPlacesClient.isConfigured(this)?tx("ตั้งค่าแล้ว","Configured"):
                    tx("ยังไม่ตั้งค่า","Not configured"),()->configureMcp());
                line(tx("AMAP Map Style","AMAP Map Style"),
                    tx("นำเข้าสไตล์จาก AMAP Console","Import from AMAP Console"),
                    MapStyleImporter.installed(this)?tx("ติดตั้งแล้ว","Installed"):"›",
                    ()->chooseStyleImport());
                section(tx("การแสดงผลขั้นสูง","Advanced appearance"));groupStart();
                line(tx("ซูมเริ่มต้น","Default zoom"),null,
                    String.format(Locale.US,"%.0f",prefs.getFloat("zoom",16f)),()->pickZoom());
                line(tx("ข้อมูลระบบ","System diagnostics"),
                    tx("เวอร์ชัน SDK และข้อจำกัด","SDK availability and limits"),
                    "›",()->showNavigationDetails());
                break;
            default: break;
        }
    }

    private void openAppPermissions() {
        Intent settings=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        settings.setData(android.net.Uri.fromParts("package",getPackageName(),null));
        startActivity(settings);
    }
    private void clearRecentHistory() {
        new AlertDialog.Builder(this)
            .setTitle(tx("ล้างสถานที่ล่าสุด?","Clear recent places?"))
            .setMessage(tx("ลบเฉพาะประวัติการค้นหาที่บันทึกในเครื่อง ไม่ลบสถานที่โปรดหรือบ้าน/ที่ทำงาน",
                "Only locally saved recent destinations will be cleared. Favorites, Home and Work remain."))
            .setNegativeButton(tx("ยกเลิก","Cancel"),null)
            .setPositiveButton(tx("ล้าง","Clear"),(d,w)->{
                getSharedPreferences("navith_places",MODE_PRIVATE).edit()
                    .remove("recents").apply();
                Toast.makeText(this,tx("ล้างรายการล่าสุดแล้ว","Recent places cleared"),
                    Toast.LENGTH_SHORT).show();
            }).show();
    }
    private void showNavigationDetails() {
        new AlertDialog.Builder(this)
            .setTitle(tx("สถานะระบบ NaviTH","NaviTH capabilities"))
            .setMessage(NavSdkBridge.isInstalled() ?
                tx("APK นี้มี AMAP Navigation SDK รุ่นทดลอง แต่ต้องทดสอบสิทธิ์ API Key, เสียงและข้อมูลถนนในประเทศไทยก่อนใช้จริง\n\nAndroid Auto ยังอยู่ระหว่างทดสอบและไม่ใช่รุ่น Production",
                   "This APK includes experimental AMAP Navigation. Verify API rights, voice and road coverage in your region. Android Auto is not production-ready.") :
                tx("รุ่นนี้รองรับแผนที่ ค้นหา และดูเส้นทางเท่านั้น การนำทางเลี้ยวต่อเลี้ยวต้องใช้ APK Navigation Test แยกต่างหาก\n\nAndroid Auto ยังเป็นรุ่นทดลอง",
                   "This build supports maps, search and route preview only. Turn-by-turn requires the separate Navigation Test APK. Android Auto remains experimental."))
            .setPositiveButton(tx("เข้าใจแล้ว","OK"),null).show();
    }

    private void section(String s) {
        TextView title=label(s,16,accent,true);
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
        left.addView(label(title,16,primary,false));
        if(subtitle!=null) {
            TextView small=label(subtitle,14,secondary,false);small.setPadding(0,dp(2),0,0);
            left.addView(small);
        }
        TextView v=label(value==null?"›":(value.equals("›")?"›":value+"  ›"),14,secondary,false);
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
        if(subtitle!=null)left.addView(label(subtitle,14,secondary,false));
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
        line.setAlpha(.32f);
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
    private void chooseStyleImport() {
        String[] options={tx("นำเข้า ZIP จาก AMAP Console","Import offline ZIP"),
            tx("รีเซ็ตเป็นสไตล์เริ่มต้น","Reset to default style")};
        new AlertDialog.Builder(this).setTitle(tx("ปรับสีและองค์ประกอบแผนที่","Map styling"))
            .setMessage(tx("การลดสีหรือเลือกให้แสดงชื่อเฉพาะบางหมวดต้องมีสไตล์แผนที่ที่เผยแพร่จาก AMAP Console ก่อน",
                "Granular colors and POI filtering require a published style from AMAP Console."))
            .setItems(options,(d,i)->{
                if(i==1) {
                    MapStyleImporter.clear(this);
                    Toast.makeText(this,tx("คืนค่าสไตล์เริ่มต้นแล้ว","Reset to default"),Toast.LENGTH_SHORT).show();
                    build();
                }else {
                    Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.setType("application/zip");
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(pick,PICK_MAP_STYLE);
                }
            }).show();
    }

    /**
     * Connection settings only. No automatic communication takes place until
     * enabled and native search fails; never record credentials in logs.
     */
    private void configureMcp() {
        SharedPreferences p=McpPlacesClient.settings(this);
        LinearLayout form=new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20),dp(8),dp(20),dp(4));
        TextView desc=label(tx(
            "MCP ต้องทำงานบนเซิร์ฟเวอร์ HTTPS ของคุณเองและใช้ Web Service Key แยกจาก AMAP Android SDK\\nคำค้นหาจะถูกส่งไปเซิร์ฟเวอร์นี้เฉพาะเมื่อตั้งค่าและเปิดใช้งาน",
            "Requires your own HTTPS MCP bridge with a separate AMAP Web Service key. Search terms are sent to this server only if you opt in."),
            13,secondary,false);
        desc.setPadding(0,0,0,dp(14));
        form.addView(desc);
        EditText endpoint=new EditText(this);
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setSingleLine(true);
        endpoint.setHint("https://maps.example.com");
        endpoint.setText(p.getString("url",""));
        endpoint.setTextSize(15);
        endpoint.setContentDescription(tx("ที่อยู่ HTTPS เซิร์ฟเวอร์ MCP","MCP bridge HTTPS URL"));
        form.addView(endpoint,new LinearLayout.LayoutParams(-1,dp(54)));
        EditText token=new EditText(this);
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setHint(tx("โทเค็นเข้าถึงเซิร์ฟเวอร์","Bridge access token"));
        token.setSingleLine(true);
        token.setTextSize(15);
        token.setContentDescription(tx("โทเค็น","Access token"));
        form.addView(token,new LinearLayout.LayoutParams(-1,dp(54)));
        Switch enabled=new Switch(this);
        enabled.setText(tx("เปิดใช้ค้นหาสำรองเมื่อ AMAP Native ไม่พบผลลัพธ์",
            "Enable fallback only when Native AMAP returns no results"));
        enabled.setTextSize(13);
        enabled.setChecked(p.getBoolean("enabled",false));
        enabled.setMinHeight(dp(60));
        form.addView(enabled);
        new AlertDialog.Builder(this)
            .setTitle(tx("เชื่อมต่อ AMAP MCP","AMAP MCP connection"))
            .setView(form)
            .setPositiveButton(tx("บันทึก","Save"),(dialog,which)->{
                String url=endpoint.getText().toString().trim();
                String newToken=token.getText().toString();
                if(!McpPlacesClient.validBaseUrl(url) || 
                    (newToken.length()<24 && p.getString("token","").length()<24)) {
                    p.edit().putBoolean("enabled",false).apply();
                    Toast.makeText(this,tx(
                        "ต้องเป็น HTTPS URL ที่ถูกต้องและ Token อย่างน้อย 24 ตัวอักษร",
                        "Valid HTTPS URL and token of 24+ characters required"),
                        Toast.LENGTH_LONG).show();
                }else{
                    SharedPreferences.Editor edit=p.edit().putString("url",url);
                    if(!newToken.isEmpty())edit.putString("token",newToken);
                    edit.putBoolean("enabled",enabled.isChecked()).apply();
                    Toast.makeText(this,tx(
                        "บันทึกแล้ว (ยังไม่ได้ตรวจการเชื่อมต่อจริง)",
                        "Saved (live connection not yet verified)"),
                        Toast.LENGTH_SHORT).show();
                }
                build();
            })
            .setNeutralButton(tx("ตัดการเชื่อมต่อ","Disconnect"),(dialog,which)->{
                p.edit().clear().apply();
                build();
            })
            .setNegativeButton(tx("ยกเลิก","Cancel"),null)
            .show();
    }

    /** Preview the same AMAP-ground bitmap that the map uses (no emoji/stock art). */
    private void chooseMarker() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout all=new LinearLayout(this);
        all.setOrientation(LinearLayout.VERTICAL);
        all.setPadding(dp(16),dp(8),dp(16),dp(12));
        scroll.addView(all);
        TextView intro=label(tx("แตะเพื่อเลือก ไอคอนจะปรับตามมุมเอียงของแผนที่",
            "Select a style. The icon follows the map's tilt and heading."),
            14,secondary,false);
        intro.setPadding(0,0,0,dp(8));
        all.addView(intro);
        final AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(tx("ไอคอนนำทาง","Navigation icon"))
            .setView(scroll)
            .setNegativeButton(tx("ปิด","Close"),null)
            .create();
        final int selected=prefs.getInt("vehicle_icon",6);
        for(int kind:new int[]{6,8,5,0,1,3,2,4,9,7}){
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(72));
            row.setPadding(dp(8),dp(5),dp(8),dp(5));
            if(kind==selected)row.setBackground(surface(getColor(R.color.nav_soft_accent),16));
            ImageView preview=new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            preview.setImageBitmap(NavigationMarkerFactory.preview(this,kind,
                prefs.getBoolean("marker_perspective",true)));
            row.addView(preview,new LinearLayout.LayoutParams(dp(64),dp(64)));
            TextView item=label(NavigationMarkerFactory.label(this,kind),16,primary,
                kind==selected);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
            lp.leftMargin=dp(12);row.addView(item,lp);
            TextView selection=label(kind==selected?"✓":"",18,accent,true);
            row.addView(selection,new LinearLayout.LayoutParams(dp(28),dp(48)));
            row.setClickable(true);row.setFocusable(true);
            row.setContentDescription(NavigationMarkerFactory.label(this,kind));
            row.setOnClickListener(v->{
                if(kind==NavigationMarkerFactory.OWN_IMAGE) {
                    Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(pick,PICK_MARKER);
                } else {
                    prefs.edit().putInt("vehicle_icon",kind).apply();
                    build();
                }
                dialog.dismiss();
            });
            all.addView(row);
        }
        dialog.show();
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null)return;
        if(request==PICK_MAP_STYLE) {
            android.net.Uri uri=data.getData();
            new Thread(()->{
                try{
                    MapStyleImporter.install(this,uri);
                    runOnUiThread(()->{
                        Toast.makeText(this,tx("นำเข้าสไตล์สำเร็จ","Map style imported"),Toast.LENGTH_SHORT).show();
                        build();
                    });
                }catch(Exception ex){
                    runOnUiThread(()->Toast.makeText(this,
                        tx("ไฟล์ไม่ถูกต้อง ต้องเป็น ZIP จาก AMAP Console","Invalid ZIP: use a published AMAP style"),
                        Toast.LENGTH_LONG).show());
                }
            }).start();
            return;
        }
        if(request!=PICK_MARKER)return;
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
