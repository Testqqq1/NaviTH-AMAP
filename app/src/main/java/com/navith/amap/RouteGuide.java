package com.navith.amap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turn-by-turn progress along a route polyline. Coordinates are used as given.
 * Outside China, AMAP Route Search returns WGS84, the same frame as device GPS,
 * so this class never applies a GCJ-02 shift.
 */
public final class RouteGuide {
    /** Perpendicular distance that means the driver has left the route. */
    public static final double OFF_ROUTE_METERS = 50.0;
    private static final double ARRIVE_METERS = 35.0;

    private RouteGuide() {}

    public static final class Leg {
        public final double[] lats;
        public final double[] lons;
        public final String road;
        public final String action;
        public final int durationSeconds;

        public Leg(double[] lats, double[] lons, String road, String action, int durationSeconds) {
            this.lats = lats;
            this.lons = lons;
            this.road = road == null ? "" : road;
            this.action = action == null ? "" : action;
            this.durationSeconds = Math.max(0, durationSeconds);
        }
    }

    public static final class Progress {
        public final boolean offRoute;
        public final boolean arrived;
        public final double crossTrackMeters;
        public final int iconType;
        public final String currentRoad;
        public final String nextRoad;
        public final int nextMeters;
        public final int remainingMeters;
        public final int remainingSeconds;
        public final double traveledMeters;
        /** Raw step action at the upcoming maneuver, used for the spoken phrase. */
        public final String action;

        Progress(boolean offRoute, boolean arrived, double crossTrackMeters, int iconType,
                 String currentRoad, String nextRoad, int nextMeters, int remainingMeters,
                 int remainingSeconds, double traveledMeters, String action) {
            this.offRoute = offRoute;
            this.arrived = arrived;
            this.crossTrackMeters = crossTrackMeters;
            this.iconType = iconType;
            this.currentRoad = currentRoad;
            this.nextRoad = nextRoad;
            this.nextMeters = nextMeters;
            this.remainingMeters = remainingMeters;
            this.remainingSeconds = remainingSeconds;
            this.traveledMeters = traveledMeters;
            this.action = action == null ? "" : action;
        }
    }

    public static final class Route {
        private final double[] lat;
        private final double[] lon;
        private final double[] cum;
        private final int[] legEnd;
        private final String[] road;
        private final String[] action;
        private final String destination;
        private final int totalDuration;

        private Route(double[] lat, double[] lon, double[] cum, int[] legEnd,
                      String[] road, String[] action, String destination, int totalDuration) {
            this.lat = lat;
            this.lon = lon;
            this.cum = cum;
            this.legEnd = legEnd;
            this.road = road;
            this.action = action;
            this.destination = destination == null ? "" : destination;
            this.totalDuration = Math.max(0, totalDuration);
        }

        public double lengthMeters() {
            return cum[cum.length - 1];
        }

        public Progress follow(double latitude, double longitude) {
            double[] hit = nearest(latitude, longitude);
            double cross = hit[0];
            double traveled = hit[1];
            double length = lengthMeters();
            double remaining = Math.max(0, length - traveled);
            boolean arrived = remaining <= ARRIVE_METERS;
            int leg = legAt(traveled);
            String current = road[leg];
            int maneuverLeg = maneuverLeg(leg);
            double maneuverAt = cum[legEnd[maneuverLeg]];
            int icon = iconForAction(action[maneuverLeg]);
            String next = maneuverLeg + 1 < road.length ? road[maneuverLeg + 1] : destination;
            if (next == null || next.trim().isEmpty()) next = destination.isEmpty() ? "จุดหมาย" : destination;
            int nextMeters = (int) Math.round(Math.max(0, maneuverAt - traveled));
            int remainMeters = (int) Math.round(remaining);
            int remainSeconds = totalDuration <= 0 || length <= 0 ? 0
                    : (int) Math.round(totalDuration * (remaining / length));
            return new Progress(cross > OFF_ROUTE_METERS, arrived, cross, icon, current, next,
                    nextMeters, remainMeters, remainSeconds, traveled, action[maneuverLeg]);
        }

        /** Position and bearing (degrees clockwise from north) at a distance along the route. */
        public double[] poseAt(double meters) {
            double along = Math.max(0, Math.min(lengthMeters(), meters));
            int seg = segmentAt(along);
            double span = cum[seg + 1] - cum[seg];
            double t = span < 0.01 ? 0 : (along - cum[seg]) / span;
            double latHere = lat[seg] + (lat[seg + 1] - lat[seg]) * t;
            double lonHere = lon[seg] + (lon[seg + 1] - lon[seg]) * t;
            double ahead = Math.min(lengthMeters(), along + 12);
            int seg2 = segmentAt(ahead);
            double span2 = cum[seg2 + 1] - cum[seg2];
            double t2 = span2 < 0.01 ? 0 : (ahead - cum[seg2]) / span2;
            double lat2 = lat[seg2] + (lat[seg2 + 1] - lat[seg2]) * t2;
            double lon2 = lon[seg2] + (lon[seg2 + 1] - lon[seg2]) * t2;
            double bearing = bearing(latHere, lonHere, lat2, lon2);
            return new double[]{latHere, lonHere, bearing};
        }

        private int legAt(double traveled) {
            for (int i = 0; i < legEnd.length; i++) {
                if (traveled <= cum[legEnd[i]] + 0.5) return i;
            }
            return legEnd.length - 1;
        }

        private int maneuverLeg(int from) {
            for (int i = from; i < action.length; i++) {
                int icon = iconForAction(action[i]);
                if (icon != AmapIcons.STRAIGHT || i == action.length - 1) return i;
            }
            return action.length - 1;
        }

        private int segmentAt(double along) {
            for (int i = 0; i < cum.length - 1; i++) {
                if (along <= cum[i + 1]) return i;
            }
            return cum.length - 2;
        }

        private double[] nearest(double latitude, double longitude) {
            double bestCross = Double.MAX_VALUE;
            double bestAlong = 0;
            for (int i = 0; i < lat.length - 1; i++) {
                double span = cum[i + 1] - cum[i];
                if (span < 0.5) continue;
                double lat0 = lat[i];
                double mx = metersPerLon(lat0);
                double my = metersPerLat();
                double ax = 0;
                double ay = 0;
                double bx = (lon[i + 1] - lon[i]) * mx;
                double by = (lat[i + 1] - lat[i]) * my;
                double px = (longitude - lon[i]) * mx;
                double py = (latitude - lat[i]) * my;
                double len2 = bx * bx + by * by;
                double t = len2 <= 0 ? 0 : (px * bx + py * by) / len2;
                if (t < 0) t = 0;
                else if (t > 1) t = 1;
                double dx = px - bx * t;
                double dy = py - by * t;
                double cross = Math.sqrt(dx * dx + dy * dy);
                if (cross < bestCross) {
                    bestCross = cross;
                    bestAlong = cum[i] + span * t;
                }
            }
            return new double[]{bestCross, bestAlong};
        }
    }

    public static Route compile(List<Leg> legs, String destination, int totalDurationSeconds) {
        if (legs == null || legs.isEmpty()) return null;
        ArrayList<Double> lats = new ArrayList<>();
        ArrayList<Double> lons = new ArrayList<>();
        ArrayList<Double> cum = new ArrayList<>();
        ArrayList<Integer> legEnd = new ArrayList<>();
        ArrayList<String> roads = new ArrayList<>();
        ArrayList<String> actions = new ArrayList<>();
        for (Leg leg : legs) {
            if (leg == null || leg.lats == null || leg.lons == null) continue;
            int n = Math.min(leg.lats.length, leg.lons.length);
            if (n < 2) continue;
            for (int i = 0; i < n; i++) {
                if (!Double.isFinite(leg.lats[i]) || !Double.isFinite(leg.lons[i])) continue;
                if (!lats.isEmpty()) {
                    double prevLat = lats.get(lats.size() - 1);
                    double prevLon = lons.get(lons.size() - 1);
                    if (Math.abs(prevLat - leg.lats[i]) < 1e-7 && Math.abs(prevLon - leg.lons[i]) < 1e-7)
                        continue;
                }
                double dist = lats.isEmpty() ? 0 : cum.get(cum.size() - 1)
                        + meters(lats.get(lats.size() - 1), lons.get(lons.size() - 1), leg.lats[i], leg.lons[i]);
                lats.add(leg.lats[i]);
                lons.add(leg.lons[i]);
                cum.add(dist);
            }
            if (lats.size() < 2) continue;
            legEnd.add(lats.size() - 1);
            roads.add(leg.road);
            actions.add(leg.action);
        }
        if (legEnd.isEmpty() || lats.size() < 2 || cum.get(cum.size() - 1) < 1) return null;
        return new Route(toArray(lats), toArray(lons), toArray(cum), toInts(legEnd),
                roads.toArray(new String[0]), actions.toArray(new String[0]),
                destination, totalDurationSeconds);
    }

    /**
     * AMAP {@code DriveStep.getAction()} values, in Chinese or English.
     * Longer phrases are matched first so "向左前方" is not read as a plain left turn.
     */
    public static int iconForAction(String action) {
        if (action == null) return AmapIcons.STRAIGHT;
        String text = action.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.contains("直行") || text.contains("straight")
                || text.contains("continue") || text.equals("none")) return AmapIcons.STRAIGHT;
        if (text.contains("到达") || text.contains("arriv") || text.contains("destinat"))
            return AmapIcons.DESTINATION;
        if (text.contains("调头") || text.contains("u-turn") || text.contains("uturn") || text.contains("u turn")) {
            if (text.contains("右") || text.contains("right")) return AmapIcons.U_TURN_RIGHT;
            return AmapIcons.U_TURN_LEFT;
        }
        if (text.contains("环岛") || text.contains("roundabout")) {
            if (text.contains("离开") || text.contains("驶出") || text.contains("exit") || text.contains("leave"))
                return AmapIcons.EXIT_ROUNDABOUT;
            return AmapIcons.ENTER_ROUNDABOUT;
        }
        if (text.contains("向左前方") || text.contains("slight left") || text.contains("bear left"))
            return AmapIcons.SLIGHT_LEFT;
        if (text.contains("向右前方") || text.contains("slight right") || text.contains("bear right"))
            return AmapIcons.SLIGHT_RIGHT;
        if (text.contains("向左后方") || text.contains("sharp left")) return AmapIcons.SHARP_LEFT;
        if (text.contains("向右后方") || text.contains("sharp right")) return AmapIcons.SHARP_RIGHT;
        if (text.contains("靠左") || text.contains("keep left")) return AmapIcons.KEEP_LEFT;
        if (text.contains("靠右") || text.contains("keep right")) return AmapIcons.KEEP_RIGHT;
        if (text.contains("收费") || text.contains("toll")) return AmapIcons.TOLL;
        if (text.contains("隧道") || text.contains("tunnel")) return AmapIcons.TUNNEL;
        if (text.contains("左转") || text.equals("left") || text.contains("turn left")) return AmapIcons.LEFT;
        if (text.contains("右转") || text.equals("right") || text.contains("turn right")) return AmapIcons.RIGHT;
        return AmapIcons.STRAIGHT;
    }

    /** Route Search TMC status text to the 0–4 scale used by the car progress bar. */
    public static int trafficStatus(String status) {
        if (status == null) return 0;
        String text = status.toLowerCase(Locale.ROOT);
        if (text.contains("严重") || text.contains("very")) return 4;
        if (text.contains("拥堵") || text.contains("拥挤") || text.contains("jam") || text.contains("congest"))
            return 3;
        if (text.contains("缓") || text.contains("slow")) return 2;
        if (text.contains("畅") || text.contains("smooth")) return 1;
        return 0;
    }

    public static double meters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dLat = p2 - p1;
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
    }

    private static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double y = Math.sin(Math.toRadians(lon2 - lon1)) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(lon2 - lon1));
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private static double metersPerLat() { return 111320.0; }
    private static double metersPerLon(double lat) { return 111320.0 * Math.cos(Math.toRadians(lat)); }
    private static double[] toArray(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < values.size(); i++) out[i] = values.get(i);
        return out;
    }
    private static int[] toInts(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) out[i] = values.get(i);
        return out;
    }

    /** Icon numbers shared with {@code AmapManeuverMap}. Kept here so tests stay free of Android. */
    public static final class AmapIcons {
        public static final int LEFT = 2;
        public static final int RIGHT = 3;
        public static final int SLIGHT_LEFT = 4;
        public static final int SLIGHT_RIGHT = 5;
        public static final int SHARP_LEFT = 6;
        public static final int SHARP_RIGHT = 7;
        public static final int U_TURN_LEFT = 8;
        public static final int STRAIGHT = 9;
        public static final int ENTER_ROUNDABOUT = 11;
        public static final int EXIT_ROUNDABOUT = 12;
        public static final int TOLL = 14;
        public static final int DESTINATION = 15;
        public static final int TUNNEL = 16;
        public static final int U_TURN_RIGHT = 19;
        public static final int KEEP_LEFT = 65;
        public static final int KEEP_RIGHT = 66;
        private AmapIcons() {}
    }
}
