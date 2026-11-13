package com.navith.amap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides when to speak a Thai guidance prompt, and builds the sentence.
 * Each maneuver/threshold pair is spoken once. Faster travel uses the
 * longer highway distances.
 */
public final class VoiceCue {
    public static final int NOW_METERS = 40;
    private static final Pattern EXIT_NUMBER = Pattern.compile("(\\d{1,2})");

    private VoiceCue() {}

    public static final class Cue {
        public final String phrase;
        /** Tokens the caller must remember so this prompt is not repeated. */
        public final List<String> marks;

        Cue(String phrase, List<String> marks) {
            this.phrase = phrase;
            this.marks = marks;
        }
    }

    public static int[] bands(float speedMps, String road) {
        if (fast(speedMps, road)) return new int[]{1000, 500, 200, NOW_METERS};
        return new int[]{300, 100, NOW_METERS};
    }

    public static boolean fast(float speedMps, String road) {
        if (speedMps >= 16f) return true;
        if (road == null) return false;
        String text = road.toLowerCase(Locale.ROOT);
        return text.contains("ทางด่วน") || text.contains("มอเตอร์เวย์")
                || text.contains("highway") || text.contains("motorway") || text.contains("express");
    }

    /**
     * @param spoken tokens already announced for the current trip
     * @return the one prompt to speak now, or null
     */
    public static Cue consider(String maneuverKey, int icon, String road, String action,
                               int nextMeters, float speedMps, Set<String> spoken) {
        if (maneuverKey == null || nextMeters < 0 || spoken == null) return null;
        int[] bands = bands(speedMps, road);
        int chosen = -1;
        for (int band : bands) {
            if (nextMeters <= band) chosen = band;
        }
        if (chosen < 0) return null;
        String token = maneuverKey + "|" + chosen;
        if (spoken.contains(token)) return null;
        boolean now = chosen <= NOW_METERS;
        List<String> marks = new ArrayList<>();
        for (int band : bands) {
            if (band >= chosen) marks.add(maneuverKey + "|" + band);
        }
        return new Cue(phrase(icon, road, action, now ? 0 : chosen, now), marks);
    }

    public static String phrase(int icon, String road, String action, int meters, boolean now) {
        String verb = verb(icon, action);
        String into = into(road);
        if (now) return verb + "ตอนนี้" + into;
        String distance = meters >= 1000
                ? "อีก " + (meters / 1000) + " กิโลเมตร "
                : "อีก " + meters + " เมตร ";
        return distance + verb + into;
    }

    public static String reroute() { return "กำลังคำนวณเส้นทางใหม่"; }

    public static String arrival() { return "ถึงจุดหมายแล้ว"; }

    public static String banner(int icon, String road, int meters) {
        if (meters <= NOW_METERS) return phrase(icon, road, "", 0, true);
        int shown = meters >= 1000 ? (meters / 1000) * 1000 : (meters >= 100 ? (meters / 50) * 50 : meters);
        return phrase(icon, road, "", shown, false);
    }

    static String verb(int icon, String action) {
        int exit = exitNumber(icon, action);
        switch (icon) {
            case RouteGuide.AmapIcons.LEFT: return "เลี้ยวซ้าย";
            case RouteGuide.AmapIcons.RIGHT: return "เลี้ยวขวา";
            case RouteGuide.AmapIcons.SLIGHT_LEFT: return "เบี่ยงซ้าย";
            case RouteGuide.AmapIcons.SLIGHT_RIGHT: return "เบี่ยงขวา";
            case RouteGuide.AmapIcons.SHARP_LEFT: return "เลี้ยวซ้ายหักศอก";
            case RouteGuide.AmapIcons.SHARP_RIGHT: return "เลี้ยวขวาหักศอก";
            case RouteGuide.AmapIcons.U_TURN_LEFT: return "กลับรถ";
            case RouteGuide.AmapIcons.U_TURN_RIGHT: return "กลับรถทางขวา";
            case RouteGuide.AmapIcons.KEEP_LEFT: return "ชิดซ้าย";
            case RouteGuide.AmapIcons.KEEP_RIGHT: return "ชิดขวา";
            case RouteGuide.AmapIcons.ENTER_ROUNDABOUT:
                return exit > 0 ? "เข้าวงเวียน ออกทางออกที่ " + exit : "เข้าวงเวียน";
            case RouteGuide.AmapIcons.EXIT_ROUNDABOUT:
                return exit > 0 ? "ออกจากวงเวียน ทางออกที่ " + exit : "ออกจากวงเวียน";
            case RouteGuide.AmapIcons.TOLL: return "เข้าด่านเก็บเงิน";
            case RouteGuide.AmapIcons.TUNNEL: return "เข้าอุโมงค์";
            case RouteGuide.AmapIcons.DESTINATION: return "ถึงจุดหมาย";
            default: return "ตรงไป";
        }
    }

    static int exitNumber(int icon, String action) {
        if (icon != RouteGuide.AmapIcons.ENTER_ROUNDABOUT && icon != RouteGuide.AmapIcons.EXIT_ROUNDABOUT)
            return 0;
        if (action == null) return 0;
        Matcher matcher = EXIT_NUMBER.matcher(action);
        if (!matcher.find()) return 0;
        int value = Integer.parseInt(matcher.group(1));
        return value >= 1 && value <= 20 ? value : 0;
    }

    private static String into(String road) {
        if (road == null) return "";
        String name = road.trim();
        if (name.isEmpty() || name.equals("จุดหมาย")) return "";
        boolean latin = name.matches(".*[A-Za-z].*");
        if (!latin && !name.startsWith("ถนน") && !name.startsWith("ทาง")) name = "ถนน" + name;
        return " เข้า " + name;
    }
}
