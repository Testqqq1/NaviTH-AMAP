package com.navith.amap.car;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure layout and formatting for the overlays drawn on the car map surface.
 * AMAP facility type 1 is a toll gate; type 0 is a service area. Thailand
 * often sends neither, and the panel stays hidden when the list is empty.
 */
public final class CarHud {
    /** {@code AMapServiceAreaInfo.getType()} value for a toll gate. */
    public static final int FACILITY_TOLL = 1;
    public static final int TRAFFIC_UNKNOWN = 0;
    public static final int TRAFFIC_SMOOTH = 1;
    public static final int TRAFFIC_SLOW = 2;
    public static final int TRAFFIC_JAM = 3;
    public static final int TRAFFIC_VERY_JAM = 4;

    private CarHud() {}

    public static final class Toll {
        public final int meters;
        public final String name;

        public Toll(int meters, String name) {
            this.meters = meters;
            this.name = name == null ? "" : name;
        }
    }

    public static final class Segment {
        public final int status;
        public final int length;

        public Segment(int status, int length) {
            this.status = status;
            this.length = length;
        }
    }

    public static final class Box {
        public final int left, top, right, bottom;

        public Box(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int width() { return right - left; }
        public int height() { return bottom - top; }
        public int centerX() { return (left + right) / 2; }
        public boolean contains(Box other) {
            return other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom;
        }
    }

    public static final class Frame {
        public final Box speed;
        public final Box tollPrimary;
        public final Box tollSecondary;
        public final Box progress;
        public final Box road;

        Frame(Box speed, Box tollPrimary, Box tollSecondary, Box progress, Box road) {
            this.speed = speed;
            this.tollPrimary = tollPrimary;
            this.tollSecondary = tollSecondary;
            this.progress = progress;
            this.road = road;
        }
    }

    /** @return km/h, or -1 when the sample is missing or absurd */
    public static int kmh(float metersPerSecond) {
        if (!Float.isFinite(metersPerSecond) || metersPerSecond < 0f || metersPerSecond > 90f) return -1;
        return Math.round(metersPerSecond * 3.6f);
    }

    /** "700 m" or "7 km". Whole kilometres drop the decimal. */
    public static String formatDistance(int meters) {
        if (meters < 0) return "";
        if (meters < 950) return meters + " m";
        int tenths = (meters + 50) / 100;
        if (tenths % 10 == 0 || tenths >= 100) return (tenths / 10) + " km";
        return (tenths / 10) + "." + (tenths % 10) + " km";
    }

    public static int trafficColor(int status) {
        switch (status) {
            case TRAFFIC_SLOW: return 0xFFF5C518;
            case TRAFFIC_JAM: return 0xFFE24B4B;
            case TRAFFIC_VERY_JAM: return 0xFF8E1B1B;
            case TRAFFIC_SMOOTH:
            case TRAFFIC_UNKNOWN:
            default: return 0xFF3DDC6A;
        }
    }

    /** Fraction of the route already driven, or NaN when the total is unknown. */
    public static float traveledFraction(int remainingMeters, int totalMeters) {
        if (totalMeters <= 0 || remainingMeters < 0) return Float.NaN;
        int traveled = totalMeters - remainingMeters;
        if (traveled < 0) traveled = 0;
        if (traveled > totalMeters) traveled = totalMeters;
        return traveled / (float) totalMeters;
    }

    /** Keep toll gates only, nearest first, at most two. */
    public static List<Toll> tolls(int[] types, int[] distances, String[] names) {
        List<Toll> found = new ArrayList<>();
        if (types == null || distances == null) return found;
        int n = Math.min(types.length, distances.length);
        for (int i = 0; i < n; i++) {
            if (types[i] != FACILITY_TOLL || distances[i] < 0 || distances[i] > 500000) continue;
            String name = names != null && i < names.length && names[i] != null ? names[i].trim() : "";
            found.add(new Toll(distances[i], name));
        }
        Collections.sort(found, (a, b) -> Integer.compare(a.meters, b.meters));
        if (found.size() > 2) return new ArrayList<>(found.subList(0, 2));
        return found;
    }

    public static String encodeTolls(List<Toll> tolls) {
        if (tolls == null || tolls.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        int count = Math.min(2, tolls.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) text.append(';');
            Toll toll = tolls.get(i);
            text.append(toll.meters).append('|').append(toll.name.replace('|', ' ').replace(';', ' '));
        }
        return text.toString();
    }

    public static List<Toll> decodeTolls(String encoded) {
        List<Toll> tolls = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return tolls;
        for (String part : encoded.split(";", -1)) {
            int bar = part.indexOf('|');
            if (bar <= 0) return new ArrayList<>();
            try {
                int meters = Integer.parseInt(part.substring(0, bar));
                if (meters < 0) return new ArrayList<>();
                tolls.add(new Toll(meters, part.substring(bar + 1)));
            } catch (NumberFormatException bad) {
                return new ArrayList<>();
            }
            if (tolls.size() == 2) break;
        }
        return tolls;
    }

    public static String encodeTraffic(List<Segment> segments) {
        if (segments == null || segments.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        int count = Math.min(24, segments.size());
        for (int i = 0; i < count; i++) {
            Segment segment = segments.get(i);
            if (segment.length <= 0) continue;
            if (text.length() > 0) text.append(',');
            int status = segment.status < 0 || segment.status > 4 ? TRAFFIC_UNKNOWN : segment.status;
            text.append(status).append(':').append(segment.length);
        }
        return text.toString();
    }

    public static List<Segment> decodeTraffic(String encoded) {
        List<Segment> segments = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return segments;
        for (String part : encoded.split(",", -1)) {
            int colon = part.indexOf(':');
            if (colon <= 0) return new ArrayList<>();
            try {
                int status = Integer.parseInt(part.substring(0, colon));
                int length = Integer.parseInt(part.substring(colon + 1));
                if (length <= 0 || status < 0 || status > 4) return new ArrayList<>();
                segments.add(new Segment(status, length));
            } catch (NumberFormatException bad) {
                return new ArrayList<>();
            }
        }
        return segments;
    }

    /**
     * Place overlays inside {@code visible}, which is the region the host has
     * not covered. Coordinates are surface pixels.
     */
    public static Frame place(int surfaceWidth, int surfaceHeight, Box visible, boolean showSecondaryToll) {
        Box area = visible != null && visible.width() > 80 && visible.height() > 80
                ? visible : new Box(0, 0, Math.max(surfaceWidth, 1), Math.max(surfaceHeight, 1));
        int margin = Math.max(12, Math.min(area.width(), area.height()) / 28);
        int speed = Math.max(72, Math.min(120, area.width() / 8));
        Box speedRect = new Box(
                area.right - margin - speed,
                area.top + margin,
                area.right - margin,
                area.top + margin + speed);
        int tollWidth = Math.min(area.width() / 2, speed * 3);
        int tollHeight = Math.max(52, speed * 2 / 3);
        Box toll1 = new Box(
                speedRect.right - tollWidth,
                speedRect.bottom + margin,
                speedRect.right,
                speedRect.bottom + margin + tollHeight);
        Box toll2 = showSecondaryToll ? new Box(
                toll1.left, toll1.bottom + margin / 2, toll1.right, toll1.bottom + margin / 2 + tollHeight)
                : new Box(0, 0, 0, 0);
        int barWidth = Math.max(14, speed / 6);
        int barTop = (showSecondaryToll ? toll2.bottom : toll1.bottom) + margin;
        int barBottom = area.bottom - margin - Math.max(44, speed / 2);
        if (barBottom - barTop < 48) {
            barTop = area.top + area.height() / 3;
            barBottom = area.bottom - margin - 48;
        }
        Box progress = new Box(area.right - margin - barWidth, barTop, area.right - margin, barBottom);
        int roadWidth = Math.min(area.width() - margin * 2, Math.max(160, area.width() / 2));
        int roadHeight = Math.max(40, speed / 2);
        int roadLeft = area.centerX() - roadWidth / 2;
        Box road = new Box(roadLeft, area.bottom - margin - roadHeight, roadLeft + roadWidth, area.bottom - margin);
        return new Frame(speedRect, toll1, toll2, progress, road);
    }

    public static String tollTitle(boolean thai) {
        return thai ? "ด่านเก็บเงิน" : "Toll Booth";
    }
}
