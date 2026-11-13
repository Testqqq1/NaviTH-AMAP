package com.navith.amap;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import java.io.File;

/**
 * NaviTH-owned AMAP marker artwork. No Google/MapLibre marker types and no
 * assets copied from a commercial navigation APK. All icons are north-up
 * bitmaps intended for AMAP MarkerOptions.setFlat(true).
 *
 * The perspective setting adds painted chassis volume/highlights (2.5D).
 * AMAP projects the flat marker onto its tilted map plane. This is not an
 * imported 3D mesh; the Navigation SDK owns any true 3D car renderer.
 */
public final class NavigationMarkerFactory {
    private static final int SIZE=144;
    public static final int ARROW=6, OWN_IMAGE=7, MINIMAL_ARROW=8, POSITION_3D=9;
    private NavigationMarkerFactory() {}

    public static BitmapDescriptor make(Context context, SharedPreferences settings) {
        Bitmap b=preview(context,settings.getInt("vehicle_icon",ARROW),
            settings.getBoolean("marker_perspective",true));
        BitmapDescriptor descriptor=BitmapDescriptorFactory.fromBitmap(b);
        b.recycle();
        return descriptor;
    }

    public static Bitmap preview(Context context,int kind,boolean perspective) {
        Bitmap b=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);
        c.save();
        c.translate(SIZE/2f,SIZE/2f);
        // Source geometry uses +/- 48 world pixels; exact center stays at
        // the antenna/GPS coordinate, including when camera pitch changes.
        drawShadow(c);
        if(kind==OWN_IMAGE && drawOwnImage(context,c)) { /* owned bitmap */ }
        else if(kind==ARROW || kind==MINIMAL_ARROW)
            drawArrow(c,kind==MINIMAL_ARROW,perspective);
        else drawCar(c,kind==POSITION_3D?3:Math.max(0,Math.min(kind,5)),
            perspective || kind==POSITION_3D);
        c.restore();
        return b;
    }

    public static String label(Context c, int kind) {
        switch(kind){
            case 0:return NaviLocale.text(c,"รถเก๋ง","Sedan");
            case 1:return NaviLocale.text(c,"แฮทช์แบ็ค","Hatchback");
            case 2:return NaviLocale.text(c,"รถตู้","Van");
            case 3:return "SUV";
            case 4:return NaviLocale.text(c,"รถกระบะ","Pickup");
            case 5:return NaviLocale.text(c,"รถสปอร์ต","Sports car");
            case 7:return NaviLocale.text(c,"รูปส่วนตัว","My image");
            case 8:return NaviLocale.text(c,"ลูกศรมินิมอล","Minimal arrow");
            case 9:return NaviLocale.text(c,"รถมุมมอง 3D","Perspective car");
            default:return NaviLocale.text(c,"ลูกศรมาตรฐาน","Standard arrow");
        }
    }

    /** North-up compass ring. Leave its marker rotation at 0 so N tracks map north. */
    public static Bitmap compassRing() {
        int size=280;
        Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        float c=size/2f;
        canvas.drawCircle(c,c,118f,fill(0x66101824));
        canvas.drawCircle(c,c,112f,outline(0xEEFFFFFF,7f));
        canvas.drawCircle(c,c,86f,outline(0x66FFFFFF,3f));
        Paint tick=outline(0xFFFFFFFF,4f);
        for(int i=0;i<72;i++){
            canvas.save();
            canvas.rotate(i*5f,c,c);
            canvas.drawLine(c,c-112f,c,c-(i%6==0?96f:104f),tick);
            canvas.restore();
        }
        Paint label=fill(0xFFFFFFFF);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(28f);
        label.setFakeBoldText(true);
        canvas.drawText("N",c,c-96f,label);
        label.setColor(0xFFD0D6DC);
        canvas.drawText("E",c+104f,c+10f,label);
        canvas.drawText("S",c,c+112f,label);
        canvas.drawText("W",c-104f,c+10f,label);
        return bitmap;
    }

    /** Perspective car used on the car surface, nose toward bitmap-up (north when rotation is 0). */
    public static Bitmap drivingCar() {
        Bitmap bitmap=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        canvas.translate(SIZE/2f,SIZE/2f);
        drawShadow(canvas);
        drawCar(canvas,3,true);
        return bitmap;
    }

    public static float angleForBearing(float bearing) {
        if(!Float.isFinite(bearing))return 0f;
        // AMAP's marker rotation uses the inverse clockwise convention.
        return (360f - ((bearing%360f+360f)%360f))%360f;
    }

    private static Paint fill(int color) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);p.setStyle(Paint.Style.FILL);
        return p;
    }
    private static Paint outline(int color,float width){
        Paint p=fill(color);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);return p;
    }
    private static void drawShadow(Canvas c){
        c.drawOval(new RectF(-34,-27,34,46),fill(0x33051624));
        c.drawOval(new RectF(-27,-31,27,42),fill(0x20051624));
    }
    private static Path polygon(float... coords) {
        Path p=new Path();p.moveTo(coords[0],coords[1]);
        for(int i=2;i<coords.length;i+=2)p.lineTo(coords[i],coords[i+1]);
        p.close();return p;
    }
    private static void drawArrow(Canvas c,boolean minimal,boolean perspective){
        Path p=polygon(0,-47,35,42,0,25,-35,42);
        if(!minimal && perspective){
            c.drawPath(p,outline(Color.WHITE,9f));
            c.drawPath(p,fill(0xff087F77));
            c.drawPath(polygon(0,-43,0,22,-25,33),fill(0xff0B5B59));
            c.drawPath(polygon(0,-43,25,33,0,22),fill(0xff27C5B6));
        }else{
            c.drawPath(p,outline(Color.WHITE,minimal?8f:10f));
            c.drawPath(p,fill(minimal?0xff172B32:0xff087F77));
            if(!minimal)c.drawPath(polygon(0,-34,10,18,0,12,-10,18),fill(0xff8AE9DE));
        }
    }
    private static void drawCar(Canvas c,int kind,boolean perspective){
        final boolean van=kind==2,pickup=kind==4,sport=kind==5,
            suv=kind==3,hatch=kind==1;
        float half=suv||pickup?30f:(sport?26f:27f);
        float top=sport?-39f:(van?-46f:-43f);
        float bottom=hatch?34f:(van?47f:43f);
        // Tire edges are placed at their actual sides, even on tilted maps.
        c.drawRoundRect(new RectF(-half-9,-24,-half+1,3),5f,5f,fill(0xff152F34));
        c.drawRoundRect(new RectF(half-1,-24,half+9,3),5f,5f,fill(0xff152F34));
        c.drawRoundRect(new RectF(-half-9,18,-half+1,37),5f,5f,fill(0xff152F34));
        c.drawRoundRect(new RectF(half-1,18,half+9,37),5f,5f,fill(0xff152F34));
        Path chassis=polygon(-half+7,top,half-7,top,half,top+9,
            half,bottom-9,half-7,bottom,-half+7,bottom,-half,bottom-9,-half,top+9);
        c.drawPath(chassis,outline(Color.WHITE,5f));
        c.drawPath(chassis,fill(0xff087F77));
        if(perspective){
            c.drawPath(polygon(-half+7,top,0,top-1,0,bottom,
                -half+4,bottom-7,-half,top+9),fill(0xff0C5754));
            c.drawPath(polygon(0,top-1,half-7,top,half,top+9,
                half-4,bottom-7,0,bottom),fill(0xff23B1A8));
        }
        final float roofTop=van?-25f:(sport?-14f:-21f);
        final float roofEnd=pickup?10f:(hatch?19f:25f);
        float roofWidth=sport?17f:(van||suv?24f:20f);
        c.drawRoundRect(new RectF(-roofWidth,roofTop,roofWidth,roofEnd),
            sport?11f:9f,sport?11f:9f,fill(0xff1E6770));
        c.drawPath(polygon(-roofWidth+3,roofTop+2,roofWidth-3,roofTop+2,
            roofWidth-6,roofTop+14,-roofWidth+6,roofTop+14),fill(0xffBDEBF0));
        c.drawPath(polygon(-roofWidth+5,roofEnd-12,roofWidth-5,roofEnd-12,
            roofWidth-3,roofEnd-3,-roofWidth+3,roofEnd-3),fill(0xff94C6D0));
        if(pickup){
            c.drawRoundRect(new RectF(-half+6,19,half-6,bottom-6),3f,3f,fill(0xff234B52));
            c.drawRect(-half+9,23,half-9,bottom-10,fill(0xff5EAFA9));
        }
        c.drawRoundRect(new RectF(-half+9,top+2,-half+2,top+9),2f,2f,fill(0xffFBE5A0));
        c.drawRoundRect(new RectF(half-9,top+2,half-2,top+9),2f,2f,fill(0xffFBE5A0));
        c.drawRoundRect(new RectF(-half+9,bottom-7,-half+2,bottom-3),2f,2f,fill(0xffE96B6B));
        c.drawRoundRect(new RectF(half-9,bottom-7,half-2,bottom-3),2f,2f,fill(0xffE96B6B));
    }
    private static boolean drawOwnImage(Context context,Canvas c){
        File f=new File(context.getFilesDir(),"navith_marker.png");
        if(!f.exists())return false;
        BitmapFactory.Options o=new BitmapFactory.Options();
        o.inSampleSize=1;
        Bitmap image=BitmapFactory.decodeFile(f.getAbsolutePath(),o);
        if(image==null)return false;
        try {
            c.drawCircle(0,0,47f,fill(Color.WHITE));
            c.drawBitmap(image,null,new RectF(-42,-42,42,42),fill(Color.WHITE));
            c.drawCircle(0,0,44f,outline(0xff087F77,4f));
        } finally {image.recycle();}
        return true;
    }
}
