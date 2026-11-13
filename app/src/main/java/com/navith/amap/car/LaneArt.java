package com.navith.amap.car;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.car.app.navigation.model.LaneDirection;

import java.util.List;

/** Small lane strip drawn from real AMAP lane arrows. The host requires an image when lanes are set. */
final class LaneArt {
    private LaneArt() {}

    static Bitmap draw(List<AmapLaneMap.Lane> lanes) {
        int count = Math.max(1, lanes.size());
        int cell = 68;
        int gap = 10;
        int width = count * cell + Math.max(0, count - 1) * gap;
        int height = 88;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (int i = 0; i < lanes.size(); i++) {
            drawLane(canvas, paint, lanes.get(i), i * (cell + gap), cell, height);
        }
        return bitmap;
    }

    private static void drawLane(Canvas canvas, Paint paint, AmapLaneMap.Lane lane,
                                 float left, float cell, float height) {
        int n = Math.max(1, lane.directions.size());
        float slot = cell / n;
        for (int i = 0; i < lane.directions.size(); i++) {
            AmapLaneMap.Direction direction = lane.directions.get(i);
            float cx = left + slot * i + slot / 2f;
            trace(canvas, paint, direction, cx, 12f, height - 12f, 0xFF102028,
                    direction.recommended ? 9f : 7f);
            trace(canvas, paint, direction, cx, 12f, height - 12f,
                    direction.recommended ? 0xFFFFFFFF : 0x99FFFFFF,
                    direction.recommended ? 4.5f : 3.5f);
        }
    }

    private static void trace(Canvas canvas, Paint paint, AmapLaneMap.Direction direction,
                              float cx, float top, float bottom, int color, float width) {
        paint.setColor(color);
        paint.setStrokeWidth(width);
        float mid = (top + bottom) / 2f;
        float reach = (bottom - top) * 0.38f;
        Path path = new Path();
        switch (direction.shape) {
            case LaneDirection.SHAPE_STRAIGHT:
                path.moveTo(cx, bottom);
                path.lineTo(cx, top);
                head(path, cx, top, 0f, -1f);
                break;
            case LaneDirection.SHAPE_NORMAL_LEFT:
            case LaneDirection.SHAPE_SLIGHT_LEFT:
                path.moveTo(cx + reach * 0.25f, bottom);
                path.lineTo(cx + reach * 0.25f, mid);
                path.lineTo(cx - reach, mid);
                head(path, cx - reach, mid, -1f, 0f);
                break;
            case LaneDirection.SHAPE_NORMAL_RIGHT:
            case LaneDirection.SHAPE_SLIGHT_RIGHT:
                path.moveTo(cx - reach * 0.25f, bottom);
                path.lineTo(cx - reach * 0.25f, mid);
                path.lineTo(cx + reach, mid);
                head(path, cx + reach, mid, 1f, 0f);
                break;
            case LaneDirection.SHAPE_SHARP_LEFT:
                path.moveTo(cx + reach * 0.35f, bottom);
                path.lineTo(cx + reach * 0.35f, mid);
                path.lineTo(cx - reach * 0.15f, bottom);
                head(path, cx - reach * 0.15f, bottom, -0.6f, 1f);
                break;
            case LaneDirection.SHAPE_SHARP_RIGHT:
                path.moveTo(cx - reach * 0.35f, bottom);
                path.lineTo(cx - reach * 0.35f, mid);
                path.lineTo(cx + reach * 0.15f, bottom);
                head(path, cx + reach * 0.15f, bottom, 0.6f, 1f);
                break;
            case LaneDirection.SHAPE_U_TURN_LEFT:
                path.moveTo(cx + reach * 0.4f, bottom);
                path.lineTo(cx + reach * 0.4f, top + reach * 0.3f);
                path.lineTo(cx - reach * 0.4f, top + reach * 0.3f);
                path.lineTo(cx - reach * 0.4f, mid);
                head(path, cx - reach * 0.4f, mid, 0f, 1f);
                break;
            case LaneDirection.SHAPE_U_TURN_RIGHT:
                path.moveTo(cx - reach * 0.4f, bottom);
                path.lineTo(cx - reach * 0.4f, top + reach * 0.3f);
                path.lineTo(cx + reach * 0.4f, top + reach * 0.3f);
                path.lineTo(cx + reach * 0.4f, mid);
                head(path, cx + reach * 0.4f, mid, 0f, 1f);
                break;
            default:
                path.addCircle(cx, mid, 5f, Path.Direction.CW);
                break;
        }
        canvas.drawPath(path, paint);
    }

    private static void head(Path path, float x, float y, float dx, float dy) {
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.01f) return;
        float ux = dx / len;
        float uy = dy / len;
        float px = -uy;
        float py = ux;
        float back = 11f;
        float side = 6.5f;
        path.moveTo(x, y);
        path.lineTo(x - ux * back + px * side, y - uy * back + py * side);
        path.moveTo(x, y);
        path.lineTo(x - ux * back - px * side, y - uy * back - py * side);
    }
}
