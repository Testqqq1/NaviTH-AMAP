package com.navith.amap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import org.junit.Test;

public class VoiceCueTest {
    @Test
    public void cityThresholdsSpeakOnceEach() {
        HashSet<String> spoken = new HashSet<>();
        assertNull(VoiceCue.consider("left|สุขุมวิท", 2, "สุขุมวิท", "左转", 400, 8f, spoken));
        VoiceCue.Cue first = VoiceCue.consider("left|สุขุมวิท", 2, "สุขุมวิท", "左转", 280, 8f, spoken);
        assertTrue(first.phrase.contains("อีก 300 เมตร"));
        assertTrue(first.phrase.contains("เลี้ยวซ้าย"));
        assertTrue(first.phrase.contains("ถนนสุขุมวิท"));
        spoken.addAll(first.marks);
        assertNull(VoiceCue.consider("left|สุขุมวิท", 2, "สุขุมวิท", "左转", 250, 8f, spoken));
        VoiceCue.Cue second = VoiceCue.consider("left|สุขุมวิท", 2, "สุขุมวิท", "左转", 90, 8f, spoken);
        assertTrue(second.phrase.contains("อีก 100 เมตร"));
        spoken.addAll(second.marks);
        VoiceCue.Cue now = VoiceCue.consider("left|สุขุมวิท", 2, "สุขุมวิท", "左转", 20, 8f, spoken);
        assertEquals("เลี้ยวซ้ายตอนนี้ เข้า ถนนสุขุมวิท", now.phrase);
        spoken.addAll(now.marks);
        assertNull(VoiceCue.consider("left|สุขุมวิท", 2, "สุขุมวิท", "左转", 10, 8f, spoken));
    }

    @Test
    public void highwayAndSpeedUseTheLongerBands() {
        assertEquals(1000, VoiceCue.bands(5f, "ทางด่วนบางนา")[0]);
        assertEquals(1000, VoiceCue.bands(20f, "สุขุมวิท")[0]);
        assertEquals(300, VoiceCue.bands(8f, "สุขุมวิท")[0]);
        VoiceCue.Cue cue = VoiceCue.consider("k", 65, "ทางด่วน", "", 900, 5f, new HashSet<>());
        assertTrue(cue.phrase.startsWith("อีก 1 กิโลเมตร ชิดซ้าย"));
    }

    @Test
    public void aNewManeuverSpeaksAgain() {
        HashSet<String> spoken = new HashSet<>();
        VoiceCue.Cue left = VoiceCue.consider("2|สุขุมวิท", 2, "สุขุมวิท", "", 80, 5f, spoken);
        spoken.addAll(left.marks);
        VoiceCue.Cue right = VoiceCue.consider("3|พระราม", 3, "พระรามที่ 4", "", 80, 5f, spoken);
        assertTrue(right.phrase.contains("เลี้ยวขวา"));
    }

    @Test
    public void roundaboutExitUturnAndFixedPhrases() {
        assertEquals("เข้าวงเวียน ออกทางออกที่ 2",
                VoiceCue.verb(RouteGuide.AmapIcons.ENTER_ROUNDABOUT, "进入环岛 出口2"));
        assertTrue(VoiceCue.phrase(RouteGuide.AmapIcons.U_TURN_LEFT, "", "", 200, false).contains("กลับรถ"));
        assertTrue(VoiceCue.phrase(RouteGuide.AmapIcons.KEEP_RIGHT, "ทางด่วน", "", 0, true).contains("ชิดขวาตอนนี้"));
        assertEquals("กำลังคำนวณเส้นทางใหม่", VoiceCue.reroute());
        assertEquals("ถึงจุดหมายแล้ว", VoiceCue.arrival());
    }
}
