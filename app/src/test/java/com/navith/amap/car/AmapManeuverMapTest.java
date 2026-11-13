package com.navith.amap.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.car.app.navigation.model.Maneuver;

import org.junit.Test;

import java.util.HashSet;

public class AmapManeuverMapTest {
    @Test
    public void mapsOfficialTurnIcons() {
        assertType(AmapManeuverMap.ICON_LEFT, Maneuver.TYPE_TURN_NORMAL_LEFT);
        assertType(AmapManeuverMap.ICON_RIGHT, Maneuver.TYPE_TURN_NORMAL_RIGHT);
        assertType(AmapManeuverMap.ICON_LEFT_FRONT, Maneuver.TYPE_TURN_SLIGHT_LEFT);
        assertType(AmapManeuverMap.ICON_RIGHT_FRONT, Maneuver.TYPE_TURN_SLIGHT_RIGHT);
        assertType(AmapManeuverMap.ICON_LEFT_BACK, Maneuver.TYPE_TURN_SHARP_LEFT);
        assertType(AmapManeuverMap.ICON_RIGHT_BACK, Maneuver.TYPE_TURN_SHARP_RIGHT);
        assertType(AmapManeuverMap.ICON_LEFT_TURN_AROUND, Maneuver.TYPE_U_TURN_LEFT);
        assertType(AmapManeuverMap.ICON_U_TURN_RIGHT, Maneuver.TYPE_U_TURN_RIGHT);
        assertType(AmapManeuverMap.ICON_STRAIGHT, Maneuver.TYPE_STRAIGHT);
        assertType(AmapManeuverMap.ICON_SPECIAL_CONTINUE, Maneuver.TYPE_STRAIGHT);
        assertType(AmapManeuverMap.ICON_NONE, Maneuver.TYPE_STRAIGHT);
        assertType(AmapManeuverMap.ICON_DEFAULT, Maneuver.TYPE_STRAIGHT);
        assertType(AmapManeuverMap.ICON_MERGE_LEFT, Maneuver.TYPE_KEEP_LEFT);
        assertType(AmapManeuverMap.ICON_MERGE_RIGHT, Maneuver.TYPE_KEEP_RIGHT);
        assertNotEquals(Maneuver.TYPE_MERGE_LEFT,
                AmapManeuverMap.map(AmapManeuverMap.ICON_MERGE_LEFT).type);
        assertType(AmapManeuverMap.ICON_FERRY, Maneuver.TYPE_FERRY_BOAT);
        assertType(AmapManeuverMap.ICON_ARRIVED_DESTINATION, Maneuver.TYPE_DESTINATION);
        assertType(AmapManeuverMap.ICON_ARRIVED_TOLLGATE, Maneuver.TYPE_STRAIGHT);
        assertType(AmapManeuverMap.ICON_ARRIVED_TUNNEL, Maneuver.TYPE_STRAIGHT);
        assertType(AmapManeuverMap.ICON_ARRIVED_SERVICE_AREA, Maneuver.TYPE_STRAIGHT);
        assertType(AmapManeuverMap.ICON_ARRIVED_WAYPOINT, Maneuver.TYPE_STRAIGHT);
        assertType(AmapManeuverMap.ICON_LOW_CROSS, Maneuver.TYPE_STRAIGHT);
        assertType(29, Maneuver.TYPE_STRAIGHT); // CROSSWALK, walk/cycle icon
    }

    @Test
    public void mapsRoundaboutsWithoutInventingAnExitNumber() {
        assertType(AmapManeuverMap.ICON_ENTER_ROUNDABOUT, Maneuver.TYPE_ROUNDABOUT_ENTER_CCW);
        assertType(AmapManeuverMap.ICON_OUT_ROUNDABOUT, Maneuver.TYPE_ROUNDABOUT_EXIT_CCW);
        assertType(AmapManeuverMap.ICON_ENTRY_LEFT_RING, Maneuver.TYPE_ROUNDABOUT_ENTER_CW);
        assertType(AmapManeuverMap.ICON_LEAVE_LEFT_RING, Maneuver.TYPE_ROUNDABOUT_EXIT_CW);
        assertType(AmapManeuverMap.ICON_ENTRY_RING_LEFT, Maneuver.TYPE_ROUNDABOUT_ENTER_CCW);
        assertType(AmapManeuverMap.ICON_ENTRY_RING_RIGHT, Maneuver.TYPE_ROUNDABOUT_ENTER_CCW);
        assertType(AmapManeuverMap.ICON_ENTRY_LEFT_RING_LEFT, Maneuver.TYPE_ROUNDABOUT_ENTER_CW);
        assertType(AmapManeuverMap.ICON_ENTRY_LEFT_RING_UTURN, Maneuver.TYPE_ROUNDABOUT_ENTER_CW);
        assertTrue(AmapManeuverMap.map(AmapManeuverMap.ICON_ENTRY_RING_LEFT).cueEn.contains("left"));
        assertTrue(AmapManeuverMap.map(AmapManeuverMap.ICON_ENTRY_LEFT_RING_RIGHT).cueTh.contains("ขวา"));
    }

    @Test
    public void appliesARealRoundaboutExitNumberOnlyToEnterIcons() {
        AmapManeuverMap.Spec ccw = AmapManeuverMap.map(AmapManeuverMap.ICON_ENTER_ROUNDABOUT, 2);
        assertEquals(Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW, ccw.type);
        assertEquals(2, ccw.exitNumber);
        Maneuver built = AmapManeuverMap.build(ccw, null);
        assertEquals(2, built.getRoundaboutExitNumber());

        AmapManeuverMap.Spec cw = AmapManeuverMap.map(AmapManeuverMap.ICON_ENTRY_LEFT_RING_LEFT, 1);
        assertEquals(Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW, cw.type);
        assertEquals(1, AmapManeuverMap.build(cw, null).getRoundaboutExitNumber());

        AmapManeuverMap.Spec leaving = AmapManeuverMap.map(AmapManeuverMap.ICON_OUT_ROUNDABOUT, 3);
        assertEquals(Maneuver.TYPE_ROUNDABOUT_EXIT_CCW, leaving.type);
        assertEquals(0, leaving.exitNumber);

        AmapManeuverMap.Spec turn = AmapManeuverMap.map(AmapManeuverMap.ICON_LEFT, 2);
        assertEquals(Maneuver.TYPE_TURN_NORMAL_LEFT, turn.type);
        assertEquals(0, turn.exitNumber);

        AmapManeuverMap.Spec tooLarge = AmapManeuverMap.map(
                AmapManeuverMap.ICON_ENTER_ROUNDABOUT, AmapManeuverMap.MAX_ROUNDABOUT_EXIT + 1);
        assertEquals(Maneuver.TYPE_ROUNDABOUT_ENTER_CCW, tooLarge.type);
        assertEquals(0, tooLarge.exitNumber);
        AmapManeuverMap.Spec atCap = AmapManeuverMap.map(
                AmapManeuverMap.ICON_ENTRY_LEFT_RING, AmapManeuverMap.MAX_ROUNDABOUT_EXIT);
        assertEquals(AmapManeuverMap.MAX_ROUNDABOUT_EXIT, atCap.exitNumber);
    }

    @Test
    public void unknownCodesFallBackWithoutClaimingATurn() {
        for (int code : new int[] {-1, 55, 64, 67, 99, 255}) {
            AmapManeuverMap.Spec spec = AmapManeuverMap.map(code);
            assertFalse(spec.known);
            assertEquals(Maneuver.TYPE_UNKNOWN, spec.type);
            assertEquals(0, spec.exitNumber);
            assertEquals(Maneuver.TYPE_UNKNOWN, AmapManeuverMap.build(spec, null).getType());
            assertEquals("Continue", spec.cueEn);
        }
    }

    @Test
    public void everyDocumentedIconIsKnownAndBuilds() {
        HashSet<Integer> seen = new HashSet<>();
        for (int icon : AmapManeuverMap.documentedIcons()) {
            assertTrue(seen.add(icon));
            AmapManeuverMap.Spec spec = AmapManeuverMap.map(icon);
            assertTrue("icon " + icon, spec.known);
            assertEquals(icon, spec.amapIcon);
            Maneuver maneuver = AmapManeuverMap.build(spec, null);
            assertEquals(spec.type, maneuver.getType());
            assertNotEquals(Maneuver.TYPE_DEPART, spec.type);
        }
        assertEquals(57, seen.size());
    }

    @Test
    public void cueIncludesRoadAndExitWithoutGrowingWithoutBound() {
        AmapManeuverMap.Spec left = AmapManeuverMap.map(AmapManeuverMap.ICON_LEFT);
        String thai = AmapManeuverMap.cue(left, "สุขุมวิท", "ทางด่วน", true);
        assertTrue(thai.startsWith("เลี้ยวซ้าย"));
        assertTrue(thai.contains("สุขุมวิท"));
        assertTrue(thai.contains("ทางด่วน"));
        String english = AmapManeuverMap.cue(left, "Sukhumvit", "Bang Na", false);
        assertTrue(english.startsWith("Turn left"));
        assertTrue(english.contains("Sukhumvit"));
        assertTrue(english.contains("Bang Na"));
        assertEquals("Turn left", AmapManeuverMap.cue(left, "  ", null, false));
        String longRoad = "ถนน".repeat(80);
        String limited = AmapManeuverMap.cue(left, longRoad, null, true);
        assertTrue(limited.codePointCount(0, limited.length()) <= 140);
        assertTrue(limited.startsWith("เลี้ยวซ้าย"));
        assertEquals("Sukhumvit", AmapManeuverMap.road("  Sukhumvit\n"));
    }

    private static void assertType(int icon, int type) {
        AmapManeuverMap.Spec spec = AmapManeuverMap.map(icon);
        assertEquals(icon, spec.amapIcon);
        assertEquals(type, spec.type);
        assertTrue(spec.known);
        assertEquals(0, spec.exitNumber);
        Maneuver maneuver = AmapManeuverMap.build(spec, null);
        assertEquals(type, maneuver.getType());
        assertEquals(0, maneuver.getRoundaboutExitNumber());
    }
}
