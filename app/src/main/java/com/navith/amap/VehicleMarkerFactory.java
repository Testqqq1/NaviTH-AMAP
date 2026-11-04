package com.navith.amap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * Top-down, map-aligned location glyphs. These bitmaps are deliberately drawn
 * upright (north-up); AMAP's flat marker applies the map camera's perspective,
 * and setRotateAngle applies device bearing when it is reliable.
 *
 * IDs 0..7 preserve existing vehicle_icon preferences and imported images.
 */
public final class VehicleMarkerFactory {
    public static final int CUSTOM = 7;
    private static final int TEAL = Color.rgb(8, 127, 118);
    private static final int DARK = Color.rgb(22, 55, 62);
    private static final int WHITE = Color.WHITE;
    private VehicleMarkerFactory() {}

    public static String[] titles(Context c) {
        boolean thai = NaviLocale.thai(c);
        return thai ? new String[]{
            "รถเก๋ง", "รถแฮทช์แบ็ค", "รถตู้", "รถ SUV",
            "รถกระบะ", "รถสปอร์ต", "ลูกศรนำทาง", "รูปภาพของฉัน"
        } : new String[]{
            "Sedan", "Hatchback", "Van", "SUV",
            "Pickup truck", "Sports car", "Navigation arrow", "My image"
        };
    }

    private static Paint paint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(color);
        return p;
    }

    private static void oval(Canvas c, Paint p, float l, float t, float r, float b) {
        c.drawOval(new RectF(l,t,r,b),p);
    }
    private static void round(Canvas c, Paint p, float l,float t,float r,float b,float radius) {
        c.drawRoundRect(new RectF(l,t,r,b),radius,radius,p);
    }

    public static Bitmap create(Context context, int choice, Bitmap custom) {
        // HD rasterization avoids jagged edges on Android devices with dense displays.
        int pixels = Math.max(112, Math.round(76 * context.getResources().getDisplayMetrics().density));
        Bitmap result = Bitmap.createBitmap(pixels,pixels,Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(result);
        float scale = pixels/128f;
        c.scale(scale,scale);
        Paint p = paint(0x22072E35);
        oval(c,p,18,33,110,105);
        p.setColor(0x3A36B8AA);
        oval(c,p,16,25,112,96);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        p.setColor(0xAA087F76);
        oval(c,p,16,25,112,96);
        p.setStyle(Paint.Style.FILL);

        if (choice == CUSTOM && custom != null) {
            p.setColor(WHITE);
            p.setShadowLayer(4f,0,3f,0x55000000);
            round(c,p,37,29,91,91,13);
            p.clearShadowLayer();
            c.drawBitmap(custom,null,new RectF(40,32,88,88),paint(WHITE));
            return result;
        }
        if (choice < 0 || choice > 6) choice = 6;
        if (choice == 6) {
            // Navigation chevron: white halo with unmistakable teal heading.
            p.setColor(WHITE);
            p.setShadowLayer(5f,0,3f,0x66000000);
            oval(c,p,34,28,94,88);
            p.clearShadowLayer();
            p.setColor(TEAL);
            Path arrow = new Path();
            arrow.moveTo(64,34); arrow.lineTo(87,81);
            arrow.lineTo(64,70); arrow.lineTo(41,81);
            arrow.close();
            c.drawPath(arrow,p);
            p.setColor(WHITE);
            Path notch = new Path();
            notch.moveTo(64,54); notch.lineTo(70,70);
            notch.lineTo(64,67); notch.lineTo(58,70);
            notch.close();
            c.drawPath(notch,p);
            return result;
        }

        // Stylized vehicle silhouette, seen from overhead. Details deliberately
        // vary by vehicle class but stay legible at a 3D map's typical zoom.
        float w = (choice==2?42:choice==3?45:choice==4?43:choice==5?38:40);
        float h = (choice==2?73:choice==3?68:choice==4?70:choice==5?59:choice==1?60:64);
        float left=64-w/2f, top=60-h/2f, right=64+w/2f, bottom=60+h/2f;
        p.setColor(0x60082428);
        round(c,p,left+2,top+5,right+2,bottom+5,11);
        p.setColor(DARK);
        round(c,p,left-5,top+10,left+2,top+25,3);
        round(c,p,right-2,top+10,right+5,top+25,3);
        round(c,p,left-5,bottom-26,left+2,bottom-11,3);
        round(c,p,right-2,bottom-26,right+5,bottom-11,3);
        int body = choice==5 ? 0xFF16619B : choice==4 ? 0xFF257D89 : TEAL;
        p.setColor(body);
        p.setShadowLayer(3f,0,2f,0x55000000);
        round(c,p,left,top,right,bottom,choice==5?15:9);
        p.clearShadowLayer();
        p.setColor(0xFFD6F3F2);
        if(choice==2) {
            round(c,p,left+5,top+14,right-5,bottom-11,5);
            p.setColor(0xFF6B9EA3);
            round(c,p,left+7,top+17,right-7,top+25,2);
        } else if(choice==4) {
            round(c,p,left+6,top+10,right-6,top+36,5);
            p.setColor(0xFFB5DDDA);
            round(c,p,left+6,bottom-25,right-6,bottom-8,3);
            p.setColor(0xFF4B858B);
            round(c,p,left+9,bottom-22,right-9,bottom-11,2);
        } else {
            round(c,p,left+6,top+15,right-6,bottom-12,choice==5?9:6);
            p.setColor(0xFF60969B);
            round(c,p,left+8,top+17,right-8,top+29,3);
            if(choice!=1)round(c,p,left+8,bottom-24,right-8,bottom-14,3);
        }
        p.setColor(WHITE);
        round(c,p,left+5,top+2,left+15,top+6,2);
        round(c,p,right-15,top+2,right-5,top+6,2);
        p.setColor(0xFFFF7971);
        round(c,p,left+5,bottom-5,left+15,bottom-2,2);
        round(c,p,right-15,bottom-5,right-5,bottom-2,2);
        if(choice==5) {
            p.setColor(0xAAFFFFFF);
            round(c,p,61,top+36,67,bottom-12,2);
        }
        return result;
    }

    public static Bitmap loadCustom(Context context) {
        File f = new File(context.getFilesDir(),"navith_marker.png");
        if (!f.isFile()) return null;
        return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    /** The same visual picker is shared by the map marker and Settings. */
    public static void showPicker(Activity a, SharedPreferences prefs,
                                  Runnable onChanged, Runnable onImport) {
        String[] labels=titles(a);
        ScrollView scroll=new ScrollView(a);
        LinearLayout list=new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(12*a.getResources().getDisplayMetrics().density);
        list.setPadding(pad,pad,pad,pad);
        scroll.addView(list);
        AlertDialog dialog=new AlertDialog.Builder(a)
            .setTitle(NaviLocale.text(a,"เลือกไอคอนบนแผนที่","Choose map marker"))
            .setView(scroll)
            .setNegativeButton(NaviLocale.text(a,"ปิด","Close"),null)
            .create();
        int selected=prefs.getInt("vehicle_icon",6);
        Bitmap custom=loadCustom(a);
        for(int i=0;i<labels.length;i++){
            final int id=i;
            LinearLayout row=new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad,pad/2,pad,pad/2);
            row.setMinimumHeight(Math.round(60*a.getResources().getDisplayMetrics().density));
            ImageView preview=new ImageView(a);
            preview.setImageBitmap(create(a,i,custom));
            int previewSize=Math.round(50*a.getResources().getDisplayMetrics().density);
            row.addView(preview,new LinearLayout.LayoutParams(previewSize,previewSize));
            TextView text=new TextView(a);
            text.setText(labels[i]+(selected==i?"  ✓":""));
            text.setTextSize(16);
            text.setTextColor(a.getColor(R.color.nav_text));
            LinearLayout.LayoutParams textLp=new LinearLayout.LayoutParams(0,-2,1);
            textLp.leftMargin=pad;
            row.addView(text,textLp);
            row.setContentDescription(labels[i]);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v->{
                dialog.dismiss();
                if(id==CUSTOM) onImport.run();
                else {
                    prefs.edit().putInt("vehicle_icon",id).apply();
                    onChanged.run();
                }
            });
            list.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        dialog.show();
    }
}
