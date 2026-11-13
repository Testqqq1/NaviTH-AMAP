package com.navith.amap.car;

import androidx.car.app.model.CarIcon;
import androidx.car.app.navigation.model.Maneuver;

/**
 * Maps AMAP Navigation {@code IconType} integers onto Car App Library maneuver
 * types. Values match the official IconType table (NONE=0 … FERRY=46,
 * MERGE_LEFT=65, MERGE_RIGHT=66). No Navigation SDK types are referenced, so
 * the maps-only APK still compiles.
 *
 * <p>AMAP's mobile NaviInfo does not include a roundabout exit index. Enter
 * icons stay {@code TYPE_ROUNDABOUT_ENTER_*} unless the caller supplies an
 * exit number from a real SDK field. Directional "standard small roundabout"
 * icons keep that direction in the cue instead of inventing an exit number.
 * {@code MERGE_LEFT}/{@code MERGE_RIGHT} are documented as 靠左/靠右, so they
 * map to keep-left/keep-right rather than a merge.
 */
public final class AmapManeuverMap {
    public static final int ICON_NONE = 0;
    public static final int ICON_DEFAULT = 1;
    public static final int ICON_LEFT = 2;
    public static final int ICON_RIGHT = 3;
    public static final int ICON_LEFT_FRONT = 4;
    public static final int ICON_RIGHT_FRONT = 5;
    public static final int ICON_LEFT_BACK = 6;
    public static final int ICON_RIGHT_BACK = 7;
    public static final int ICON_LEFT_TURN_AROUND = 8;
    public static final int ICON_STRAIGHT = 9;
    public static final int ICON_ARRIVED_WAYPOINT = 10;
    public static final int ICON_ENTER_ROUNDABOUT = 11;
    public static final int ICON_OUT_ROUNDABOUT = 12;
    public static final int ICON_ARRIVED_SERVICE_AREA = 13;
    public static final int ICON_ARRIVED_TOLLGATE = 14;
    public static final int ICON_ARRIVED_DESTINATION = 15;
    public static final int ICON_ARRIVED_TUNNEL = 16;
    public static final int ICON_ENTRY_LEFT_RING = 17;
    public static final int ICON_LEAVE_LEFT_RING = 18;
    public static final int ICON_U_TURN_RIGHT = 19;
    public static final int ICON_SPECIAL_CONTINUE = 20;
    public static final int ICON_ENTRY_RING_LEFT = 21;
    public static final int ICON_ENTRY_RING_RIGHT = 22;
    public static final int ICON_ENTRY_RING_CONTINUE = 23;
    public static final int ICON_ENTRY_RING_UTURN = 24;
    public static final int ICON_ENTRY_LEFT_RING_LEFT = 25;
    public static final int ICON_ENTRY_LEFT_RING_RIGHT = 26;
    public static final int ICON_ENTRY_LEFT_RING_CONTINUE = 27;
    public static final int ICON_ENTRY_LEFT_RING_UTURN = 28;
    public static final int ICON_FERRY = 46;
    public static final int ICON_LOW_TRAFFIC_CROSS = 53;
    public static final int ICON_LOW_CROSS = 54;
    public static final int ICON_MERGE_LEFT = 65;
    public static final int ICON_MERGE_RIGHT = 66;

    /** Largest exit index accepted from a caller. Larger values are ignored. */
    public static final int MAX_ROUNDABOUT_EXIT = 20;
    private static final int CUE_LIMIT = 140;
    private static final int ROAD_LIMIT = 72;

    private AmapManeuverMap() {}

    public static final class Spec {
        public final int amapIcon;
        public final int type;
        /** 1-based when {@link #type} requires a roundabout exit; otherwise 0. */
        public final int exitNumber;
        public final String cueTh;
        public final String cueEn;
        /** False when {@code amapIcon} is not a documented IconType value. */
        public final boolean known;

        Spec(int amapIcon, int type, int exitNumber, String cueTh, String cueEn, boolean known) {
            this.amapIcon = amapIcon;
            this.type = type;
            this.exitNumber = exitNumber;
            this.cueTh = cueTh;
            this.cueEn = cueEn;
            this.known = known;
        }
    }

    public static Spec map(int amapIcon) {
        return map(amapIcon, 0);
    }

    /**
     * @param roundaboutExitNumber 1-based exit index when a real SDK field
     *        provides one. Zero, negative, and values above
     *        {@link #MAX_ROUNDABOUT_EXIT} are ignored. Applied only to
     *        roundabout-enter icons.
     */
    public static Spec map(int amapIcon, int roundaboutExitNumber) {
        switch (amapIcon) {
            case ICON_NONE:
            case ICON_DEFAULT:
            case ICON_SPECIAL_CONTINUE:
                return fixed(amapIcon, Maneuver.TYPE_STRAIGHT, "ตามเส้นทาง", "Continue");
            case ICON_LEFT:
                return fixed(amapIcon, Maneuver.TYPE_TURN_NORMAL_LEFT, "เลี้ยวซ้าย", "Turn left");
            case ICON_RIGHT:
                return fixed(amapIcon, Maneuver.TYPE_TURN_NORMAL_RIGHT, "เลี้ยวขวา", "Turn right");
            case ICON_LEFT_FRONT:
                return fixed(amapIcon, Maneuver.TYPE_TURN_SLIGHT_LEFT, "เลี้ยวซ้ายเล็กน้อย", "Bear left");
            case ICON_RIGHT_FRONT:
                return fixed(amapIcon, Maneuver.TYPE_TURN_SLIGHT_RIGHT, "เลี้ยวขวาเล็กน้อย", "Bear right");
            case ICON_LEFT_BACK:
                return fixed(amapIcon, Maneuver.TYPE_TURN_SHARP_LEFT, "เลี้ยวซ้ายหักศอก", "Sharp left");
            case ICON_RIGHT_BACK:
                return fixed(amapIcon, Maneuver.TYPE_TURN_SHARP_RIGHT, "เลี้ยวขวาหักศอก", "Sharp right");
            case ICON_LEFT_TURN_AROUND:
                return fixed(amapIcon, Maneuver.TYPE_U_TURN_LEFT, "กลับรถทางซ้าย", "U-turn left");
            case ICON_U_TURN_RIGHT:
                return fixed(amapIcon, Maneuver.TYPE_U_TURN_RIGHT, "กลับรถทางขวา", "U-turn right");
            case ICON_STRAIGHT:
                return fixed(amapIcon, Maneuver.TYPE_STRAIGHT, "ตรงไป", "Go straight");
            case ICON_ARRIVED_WAYPOINT:
                return fixed(amapIcon, Maneuver.TYPE_STRAIGHT, "ถึงจุดผ่าน", "Waypoint");
            case ICON_ENTER_ROUNDABOUT:
                return ring(amapIcon, Maneuver.TYPE_ROUNDABOUT_ENTER_CCW, "เข้าวงเวียน", "Enter the roundabout", roundaboutExitNumber);
            case ICON_OUT_ROUNDABOUT:
                return fixed(amapIcon, Maneuver.TYPE_ROUNDABOUT_EXIT_CCW, "ออกจากวงเวียน", "Exit the roundabout");
            case ICON_ARRIVED_SERVICE_AREA:
                return fixed(amapIcon, Maneuver.TYPE_STRAIGHT, "จุดพักรถ", "Service area");
            case ICON_ARRIVED_TOLLGATE:
                return fixed(amapIcon, Maneuver.TYPE_STRAIGHT, "ด่านเก็บเงิน", "Toll gate");
            case ICON_ARRIVED_DESTINATION:
                return fixed(amapIcon, Maneuver.TYPE_DESTINATION, "ถึงจุดหมาย", "Arrive");
            case ICON_ARRIVED_TUNNEL:
                return fixed(amapIcon, Maneuver.TYPE_STRAIGHT, "อุโมงค์", "Tunnel");
            case ICON_ENTRY_LEFT_RING:
                return ring(amapIcon, Maneuver.TYPE_ROUNDABOUT_ENTER_CW, "เข้าวงเวียน", "Enter the roundabout", roundaboutExitNumber);
            case ICON_LEAVE_LEFT_RING:
                return fixed(amapIcon, Maneuver.TYPE_ROUNDABOUT_EXIT_CW, "ออกจากวงเวียน", "Exit the roundabout");
            case ICON_ENTRY_RING_LEFT:
                return ring(amapIcon, Maneuver.TYPE_ROUNDABOUT_ENTER_CCW, "เข้าวงเวียนแล้วเลี้ยวซ้าย", "Roundabout, turn left", roundaboutExitNumber);
            case ICON_ENTRY_RING_RIGHT:
                return ring(amapIcon, Maneuver.TYPE_ROUNDABOUT_ENTER_CCW, "เข้าวงเวียนแล้วเลี้ยวขวา", "Roundabout, turn right", roundaboutExitNumber);
            case ICON_ENTRY_RING_CONTINUE:
                return ring(amapIcon, Maneuver.TYPE_ROUNDABOUT_ENTER_CCW, "เข้าวงเวียนแล้วตรงไป", "Roundabout, go straight", roundaboutExitNumber);
            case ICON_ENTRY_RING_UTURN:
                return ring(amapIcon, Maneuver.TYPE_ROUNDABOUT_ENTER_CCW, "เข้าวงเวียนแล้วกลับรถ", "Roundabout, make a U-turn", roundaboutExitNumber);
            case ICON_ENTRY_LEFT_RING_LEFT:
                return ring(amapIcon, Maneuver.TYPE_ROUNDABOUT_ENTER_CW, "เข้าวงเวียนแล้วเลี้ยวซ้าย", "Roundabout, turn left", roundaboutExitNumber);
            case ICON_ENTRY_LEFT_RING_RIGHT:
                return ring(amapIcon, Maneuver.TYPE_ROUNDABOUT_ENTER_CW, "เข้าวงเวียนแล้วเลี้ยวขวา", "Roundabout, turn right", roundaboutExitNumber);
            case ICON_ENTRY_LEFT_RING_CONTINUE:
                return ring(amapIcon, Maneuver.TYPE_ROUNDABOUT_ENTER_CW, "เข้าวงเวียนแล้วตรงไป", "Roundabout, go straight", roundaboutExitNumber);
            case ICON_ENTRY_LEFT_RING_UTURN:
                return ring(amapIcon, Maneuver.TYPE_ROUNDABOUT_ENTER_CW, "เข้าวงเวียนแล้วกลับรถ", "Roundabout, make a U-turn", roundaboutExitNumber);
            case ICON_FERRY:
                return fixed(amapIcon, Maneuver.TYPE_FERRY_BOAT, "ขึ้นเรือเฟอร์รี่", "Take the ferry");
            case ICON_LOW_TRAFFIC_CROSS:
            case ICON_LOW_CROSS:
                return fixed(amapIcon, Maneuver.TYPE_STRAIGHT, "ตรงผ่านทางแยก", "Go through the junction");
            case ICON_MERGE_LEFT:
                return fixed(amapIcon, Maneuver.TYPE_KEEP_LEFT, "ชิดซ้าย", "Keep left");
            case ICON_MERGE_RIGHT:
                return fixed(amapIcon, Maneuver.TYPE_KEEP_RIGHT, "ชิดขวา", "Keep right");
            default:
                if (amapIcon >= 29 && amapIcon <= 52) {
                    return fixed(amapIcon, Maneuver.TYPE_STRAIGHT, "ตรงไป", "Continue");
                }
                return new Spec(amapIcon, Maneuver.TYPE_UNKNOWN, 0, "ตามเส้นทาง", "Continue", false);
        }
    }

    /** Official IconType codes, including walk/cycle icons the drive SDK may still emit. */
    public static int[] documentedIcons() {
        int[] icons = new int[57];
        for (int i = 0; i <= 54; i++) icons[i] = i;
        icons[55] = ICON_MERGE_LEFT;
        icons[56] = ICON_MERGE_RIGHT;
        return icons;
    }

    public static Maneuver build(Spec spec, CarIcon icon) {
        Maneuver.Builder builder = new Maneuver.Builder(spec.type);
        if (spec.exitNumber >= 1) builder.setRoundaboutExitNumber(spec.exitNumber);
        if (icon != null) builder.setIcon(icon);
        return builder.build();
    }

    public static String cue(Spec spec, String road, String exitLabel, boolean thai) {
        String verb = thai ? spec.cueTh : spec.cueEn;
        String onto = clean(road, ROAD_LIMIT);
        String exit = clean(exitLabel, AmapExitLabel.MAX_LENGTH);
        StringBuilder text = new StringBuilder(verb);
        if (!onto.isEmpty()) text.append(" · ").append(onto);
        if (!exit.isEmpty()) text.append(" · ").append(exit);
        return clean(text.toString(), CUE_LIMIT);
    }

    public static String road(String road) {
        return clean(road, ROAD_LIMIT);
    }

    private static Spec fixed(int icon, int type, String cueTh, String cueEn) {
        return new Spec(icon, type, 0, cueTh, cueEn, true);
    }

    private static Spec ring(int icon, int enterType, String cueTh, String cueEn, int exitNumber) {
        int number = exitNumber >= 1 && exitNumber <= MAX_ROUNDABOUT_EXIT ? exitNumber : 0;
        int type = enterType;
        if (number >= 1) {
            type = enterType == Maneuver.TYPE_ROUNDABOUT_ENTER_CW
                    ? Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW
                    : Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW;
        }
        return new Spec(icon, type, number, cueTh, cueEn, true);
    }

    static String clean(String raw, int maxCodePoints) {
        if (raw == null || raw.isEmpty() || maxCodePoints <= 0) return "";
        StringBuilder kept = new StringBuilder();
        raw.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || cp == '\t') kept.append(' ');
            else if (cp >= 32) kept.appendCodePoint(cp);
        });
        String collapsed = kept.toString().trim().replaceAll(" +", " ");
        if (collapsed.codePointCount(0, collapsed.length()) <= maxCodePoints) return collapsed;
        return collapsed.substring(0, collapsed.offsetByCodePoints(0, maxCodePoints)).trim();
    }
}
