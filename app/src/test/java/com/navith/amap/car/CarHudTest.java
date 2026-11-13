package com.navith.amap.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CarHudTest {
    @Test
    public void formatsSpeedAndTollDistance() {
        assertEquals(72, CarHud.kmh(20f));
        assertEquals(0, CarHud.kmh(0f));
        assertEquals(-1, CarHud.kmh(-1f));
        assertEquals(-1, CarHud.kmh(Float.NaN));
        assertEquals(-1, CarHud.kmh(200f));
        assertEquals("700 m", CarHud.formatDistance(700));
        assertEquals("7 km", CarHud.formatDistance(7000));
        assertEquals("7.5 km", CarHud.formatDistance(7500));
        assertEquals("12 km", CarHud.formatDistance(12300));
    }

    @Test
    public void keepsOnlyTheTwoNearestTollGates() {
        List<CarHud.Toll> tolls = CarHud.tolls(
                new int[] {0, CarHud.FACILITY_TOLL, CarHud.FACILITY_TOLL, CarHud.FACILITY_TOLL},
                new int[] {500, 18000, 7000, 40000},
                new String[] {"Rest", "Far", "Near", "Farther"});
        assertEquals(2, tolls.size());
        assertEquals(7000, tolls.get(0).meters);
        assertEquals("Near", tolls.get(0).name);
        assertEquals(18000, tolls.get(1).meters);
        List<CarHud.Toll> roundTrip = CarHud.decodeTolls(CarHud.encodeTolls(tolls));
        assertEquals(2, roundTrip.size());
        assertEquals(7000, roundTrip.get(0).meters);
        assertEquals("Near", roundTrip.get(0).name);
        assertTrue(CarHud.decodeTolls("bad").isEmpty());
    }

    @Test
    public void progressAndTrafficRoundTrip() {
        assertEquals(0.65f, CarHud.traveledFraction(4200, 12000), 0.001f);
        assertEquals(0f, CarHud.traveledFraction(15000, 12000), 0.001f);
        assertEquals(1f, CarHud.traveledFraction(0, 12000), 0.001f);
        assertTrue(Float.isNaN(CarHud.traveledFraction(100, 0)));
        List<CarHud.Segment> segments = Arrays.asList(
                new CarHud.Segment(CarHud.TRAFFIC_SMOOTH, 9000),
                new CarHud.Segment(CarHud.TRAFFIC_SLOW, 4000),
                new CarHud.Segment(CarHud.TRAFFIC_JAM, 3000));
        List<CarHud.Segment> decoded = CarHud.decodeTraffic(CarHud.encodeTraffic(segments));
        assertEquals(3, decoded.size());
        assertEquals(CarHud.TRAFFIC_JAM, decoded.get(2).status);
        assertEquals(0xFFF5C518, CarHud.trafficColor(CarHud.TRAFFIC_SLOW));
        assertEquals(0xFF3DDC6A, CarHud.trafficColor(CarHud.TRAFFIC_UNKNOWN));
        assertTrue(CarHud.decodeTraffic("9:10").isEmpty());
    }

    @Test
    public void overlaysStayInsideTheVisibleArea() {
        CarHud.Box visible = new CarHud.Box(280, 36, 1000, 700);
        CarHud.Frame frame = CarHud.place(1024, 768, visible, true);
        assertTrue(visible.contains(frame.speed));
        assertTrue(visible.contains(frame.tollPrimary));
        assertTrue(visible.contains(frame.tollSecondary));
        assertTrue(visible.contains(frame.progress));
        assertTrue(visible.contains(frame.road));
        assertTrue(frame.speed.left > visible.centerX());
        assertTrue(frame.road.centerX() > visible.left && frame.road.centerX() < visible.right);
        assertTrue(frame.progress.top > frame.tollSecondary.top);
    }
}
