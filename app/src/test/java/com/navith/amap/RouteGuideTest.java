package com.navith.amap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class RouteGuideTest {
    @Test
    public void mapsChineseAndEnglishActionsWithoutConfusingASlightTurn() {
        assertEquals(RouteGuide.AmapIcons.SLIGHT_LEFT, RouteGuide.iconForAction("向左前方行驶"));
        assertEquals(RouteGuide.AmapIcons.LEFT, RouteGuide.iconForAction("左转"));
        assertEquals(RouteGuide.AmapIcons.RIGHT, RouteGuide.iconForAction("turn right"));
        assertEquals(RouteGuide.AmapIcons.KEEP_LEFT, RouteGuide.iconForAction("靠左"));
        assertEquals(RouteGuide.AmapIcons.U_TURN_RIGHT, RouteGuide.iconForAction("右转调头"));
        assertEquals(RouteGuide.AmapIcons.ENTER_ROUNDABOUT, RouteGuide.iconForAction("进入环岛"));
        assertEquals(RouteGuide.AmapIcons.DESTINATION, RouteGuide.iconForAction("到达目的地"));
        assertEquals(RouteGuide.AmapIcons.STRAIGHT, RouteGuide.iconForAction("直行"));
    }

    @Test
    public void followsABangkokPolylineInWgs84WithoutShiftingIt() {
        RouteGuide.Route route = bangkok();
        RouteGuide.Progress atStart = route.follow(13.70, 100.50);
        assertFalse(atStart.offRoute);
        assertTrue(atStart.crossTrackMeters < 5);
        assertEquals(RouteGuide.AmapIcons.LEFT, atStart.iconType);
        assertEquals("พระรามที่ 4", atStart.nextRoad);
        assertTrue(atStart.nextMeters > 800);
        RouteGuide.Progress later = route.follow(13.70, 100.505);
        assertTrue(later.nextMeters < atStart.nextMeters);
        assertTrue(later.traveledMeters > atStart.traveledMeters);
    }

    @Test
    public void skipsStraightSegmentsUntilTheRealTurn() {
        RouteGuide.Leg straight = leg(13.70, 100.50, 13.70, 100.505, "สุขุมวิท", "直行");
        RouteGuide.Leg left = leg(13.70, 100.505, 13.70, 100.510, "สุขุมวิท", "左转");
        RouteGuide.Leg next = leg(13.70, 100.510, 13.705, 100.510, "พระรามที่ 4", "到达目的地");
        RouteGuide.Route route = RouteGuide.compile(Arrays.asList(straight, left, next), "สมุทรปราการ", 600);
        RouteGuide.Progress progress = route.follow(13.70, 100.50);
        assertEquals(RouteGuide.AmapIcons.LEFT, progress.iconType);
        assertEquals("พระรามที่ 4", progress.nextRoad);
        assertTrue(progress.nextMeters > RouteGuide.meters(13.70, 100.50, 13.70, 100.505));
    }

    @Test
    public void marksFiftyMetersOffTheLineAsOffRoute() {
        RouteGuide.Route route = bangkok();
        double north = 60.0 / 111320.0;
        RouteGuide.Progress off = route.follow(13.70 + north, 100.505);
        assertTrue(off.crossTrackMeters > RouteGuide.OFF_ROUTE_METERS);
        assertTrue(off.offRoute);
        RouteGuide.Progress near = route.follow(13.70 + 20.0 / 111320.0, 100.505);
        assertFalse(near.offRoute);
        assertTrue(near.crossTrackMeters < RouteGuide.OFF_ROUTE_METERS);
    }

    @Test
    public void advancesToTheNextManeuverAfterTheCorner() {
        RouteGuide.Route route = bangkok();
        RouteGuide.Progress after = route.follow(13.702, 100.51);
        assertEquals(RouteGuide.AmapIcons.DESTINATION, after.iconType);
        assertEquals("สมุทรปราการ", after.nextRoad);
        assertEquals("พระรามที่ 4", after.currentRoad);
    }

    @Test
    public void arrivesNearTheEndAndPlacesTheEmulatorOnTheLine() {
        RouteGuide.Route route = bangkok();
        double[] pose = route.poseAt(0);
        assertEquals(13.70, pose[0], 0.00001);
        assertEquals(100.50, pose[1], 0.00001);
        assertEquals(90, pose[2], 15);
        RouteGuide.Progress end = route.follow(13.71, 100.51);
        assertTrue(end.arrived);
        assertFalse(end.offRoute);
    }

    @Test
    public void explainsNaviError3InThai() {
        String text = NaviRouteErrors.thai(3);
        assertTrue(text.contains("จีน"));
        assertTrue(text.contains("จุดเริ่มต้น"));
        assertFalse(text.contains("AMAP 3"));
        assertTrue(NaviRouteErrors.thai(6).contains("จุดหมาย"));
    }

    @Test
    public void mapsTrafficWords() {
        assertEquals(1, RouteGuide.trafficStatus("畅通"));
        assertEquals(3, RouteGuide.trafficStatus("拥堵"));
        assertEquals(4, RouteGuide.trafficStatus("严重拥堵"));
        assertEquals(2, RouteGuide.trafficStatus("slow"));
    }

    private static RouteGuide.Route bangkok() {
        RouteGuide.Leg east = leg(13.70, 100.50, 13.70, 100.51, "สุขุมวิท", "左转");
        RouteGuide.Leg north = leg(13.70, 100.51, 13.71, 100.51, "พระรามที่ 4", "到达目的地");
        return RouteGuide.compile(Arrays.asList(east, north), "สมุทรปราการ", 900);
    }

    private static RouteGuide.Leg leg(double lat1, double lon1, double lat2, double lon2, String road, String action) {
        return new RouteGuide.Leg(new double[]{lat1, lat2}, new double[]{lon1, lon2}, road, action, 60);
    }
}
