package com.navith.amap.car;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Collections;
import java.util.List;

/** Speed, toll, progress and road-name overlays drawn inside the car surface. */
final class CarSurfaceHud extends View {
    private boolean night = true;
    private int kmh = -1;
    private List<CarHud.Toll> tolls = Collections.emptyList();
    private List<CarHud.Segment> traffic = Collections.emptyList();
    private float fraction = Float.NaN;
    private String road = "";
    private String tollLabel = "Toll Booth";
    private CarHud.Box visible;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    CarSurfaceHud(Context context) {
        super(context);
        setWillNotDraw(false);
        text.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
    }

    void show(boolean night, int kmh, List<CarHud.Toll> tolls, List<CarHud.Segment> traffic,
              float fraction, String road, String tollLabel, CarHud.Box visible) {
        this.night = night;
        this.kmh = kmh;
        this.tolls = tolls == null ? Collections.emptyList() : tolls;
        this.traffic = traffic == null ? Collections.emptyList() : traffic;
        this.fraction = fraction;
        this.road = road == null ? "" : road;
        this.tollLabel = tollLabel == null || tollLabel.isEmpty() ? "Toll Booth" : tollLabel;
        this.visible = visible;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        CarHud.Frame frame = CarHud.place(getWidth(), getHeight(), visible, tolls.size() > 1);
        if (kmh >= 0) drawSpeed(canvas, frame.speed);
        if (!tolls.isEmpty()) drawToll(canvas, frame.tollPrimary, tolls.get(0));
        if (tolls.size() > 1) drawToll(canvas, frame.tollSecondary, tolls.get(1));
        drawProgress(canvas, frame.progress);
        if (!road.isEmpty()) drawRoad(canvas, frame.road);
    }

    private void drawSpeed(Canvas canvas, CarHud.Box box) {
        float cx = (box.left + box.right) / 2f;
        float cy = (box.top + box.bottom) / 2f;
        float radius = Math.min(box.width(), box.height()) / 2f;
        fill.setColor(night ? 0xE6101824 : 0xF2FFFFFF);
        canvas.drawCircle(cx, cy, radius, fill);
        stroke.setStrokeWidth(Math.max(3f, radius / 14f));
        stroke.setColor(night ? 0xFF8FD0FF : 0xFF1B4FBF);
        canvas.drawCircle(cx, cy, radius - stroke.getStrokeWidth(), stroke);
        text.setColor(night ? 0xFFFFFFFF : 0xFF102018);
        text.setFakeBoldText(true);
        text.setTextSize(radius * 0.72f);
        canvas.drawText(Integer.toString(kmh), cx, cy + radius * 0.08f, text);
        text.setFakeBoldText(false);
        text.setTextSize(radius * 0.32f);
        canvas.drawText("km/h", cx, cy + radius * 0.48f, text);
    }

    private void drawToll(Canvas canvas, CarHud.Box box, CarHud.Toll toll) {
        float radius = box.height() * 0.28f;
        fill.setColor(0xF21B4FBF);
        canvas.drawRoundRect(new RectF(box.left, box.top, box.right, box.bottom), radius, radius, fill);
        text.setColor(0xFFFFFFFF);
        text.setFakeBoldText(true);
        text.setTextSize(box.height() * 0.30f);
        canvas.drawText(tollLabel, (box.left + box.right) / 2f, box.top + box.height() * 0.42f, text);
        text.setFakeBoldText(false);
        text.setTextSize(box.height() * 0.34f);
        canvas.drawText(CarHud.formatDistance(toll.meters), (box.left + box.right) / 2f,
                box.top + box.height() * 0.82f, text);
    }

    private void drawProgress(Canvas canvas, CarHud.Box box) {
        List<CarHud.Segment> parts = traffic.isEmpty()
                ? Collections.singletonList(new CarHud.Segment(CarHud.TRAFFIC_SMOOTH, 1))
                : traffic;
        int total = 0;
        for (CarHud.Segment segment : parts) total += segment.length;
        if (total <= 0) return;
        float top = box.top;
        float height = box.height();
        float drawn = 0f;
        // Route order is start → destination. The bar grows upward, start at the bottom.
        for (int i = parts.size() - 1; i >= 0; i--) {
            CarHud.Segment segment = parts.get(i);
            float h = height * segment.length / (float) total;
            fill.setColor(CarHud.trafficColor(segment.status));
            canvas.drawRect(box.left, top + drawn, box.right, top + drawn + h, fill);
            drawn += h;
        }
        if (!Float.isNaN(fraction)) {
            float y = box.bottom - height * Math.max(0f, Math.min(1f, fraction));
            fill.setColor(0xFFFFFFFF);
            canvas.drawCircle((box.left + box.right) / 2f, y, Math.max(7f, box.width() * 0.7f), fill);
            fill.setColor(0xFF102018);
            canvas.drawCircle((box.left + box.right) / 2f, y, Math.max(4f, box.width() * 0.38f), fill);
        }
    }

    private void drawRoad(Canvas canvas, CarHud.Box box) {
        fill.setColor(night ? 0xE6101824 : 0xF2FFFFFF);
        float radius = box.height() / 2f;
        canvas.drawRoundRect(new RectF(box.left, box.top, box.right, box.bottom), radius, radius, fill);
        text.setColor(night ? 0xFFFFFFFF : 0xFF102018);
        text.setFakeBoldText(true);
        text.setTextSize(box.height() * 0.46f);
        String label = road;
        float max = box.width() - box.height();
        while (label.length() > 1 && text.measureText(label) > max) {
            label = label.substring(0, label.length() - 1);
        }
        canvas.drawText(label, (box.left + box.right) / 2f, box.top + box.height() * 0.68f, text);
    }
}
