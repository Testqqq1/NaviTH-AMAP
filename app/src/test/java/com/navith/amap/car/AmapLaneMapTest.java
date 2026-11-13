package com.navith.amap.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.car.app.navigation.model.LaneDirection;

import org.junit.Test;

import java.util.List;

public class AmapLaneMapTest {
    @Test
    public void recommendsOnlyTheFrontDirectionOfACombinedLane() {
        AmapLaneMap.Lane lane = AmapLaneMap.lane(2, 1); // ahead+left background, left recommended
        assertEquals(2, lane.directions.size());
        assertEquals(LaneDirection.SHAPE_STRAIGHT, lane.directions.get(0).shape);
        assertFalse(lane.directions.get(0).recommended);
        assertEquals(LaneDirection.SHAPE_NORMAL_LEFT, lane.directions.get(1).shape);
        assertTrue(lane.directions.get(1).recommended);
    }

    @Test
    public void nullFrontLaneIsNotRecommended() {
        AmapLaneMap.Lane lane = AmapLaneMap.lane(1, AmapLaneMap.ACTION_NULL);
        assertEquals(1, lane.directions.size());
        assertEquals(LaneDirection.SHAPE_NORMAL_LEFT, lane.directions.get(0).shape);
        assertFalse(lane.directions.get(0).recommended);
    }

    @Test
    public void matchingFrontLaneIsRecommended() {
        AmapLaneMap.Lane lane = AmapLaneMap.lane(3, 3);
        assertEquals(LaneDirection.SHAPE_NORMAL_RIGHT, lane.directions.get(0).shape);
        assertTrue(lane.directions.get(0).recommended);
    }

    @Test
    public void busLaneKeepsTheBackgroundArrowWhenRecommended() {
        AmapLaneMap.Lane lane = AmapLaneMap.lane(0, 21); // straight background, bus front
        assertEquals(1, lane.directions.size());
        assertEquals(LaneDirection.SHAPE_STRAIGHT, lane.directions.get(0).shape);
        assertTrue(lane.directions.get(0).recommended);
    }

    @Test
    public void unknownLaneActionDoesNotInventAnArrow() {
        AmapLaneMap.Lane lane = AmapLaneMap.lane(255, 255);
        assertEquals(LaneDirection.SHAPE_UNKNOWN, lane.directions.get(0).shape);
        assertFalse(lane.directions.get(0).recommended);
    }

    @Test
    public void encodeDecodeRoundTripAndCap() {
        String encoded = AmapLaneMap.encode(new int[] {0, 1, 3}, new int[] {0, 255, 3}, 3);
        assertEquals("0,1,3|0,255,3", encoded);
        List<AmapLaneMap.Lane> lanes = AmapLaneMap.decode(encoded);
        assertEquals(3, lanes.size());
        assertTrue(lanes.get(0).directions.get(0).recommended);
        assertFalse(lanes.get(1).directions.get(0).recommended);
        assertTrue(lanes.get(2).directions.get(0).recommended);
        int[] wide = new int[12];
        int[] front = new int[12];
        for (int i = 0; i < wide.length; i++) {
            wide[i] = 0;
            front[i] = 255;
        }
        assertEquals(AmapLaneMap.MAX_LANES, AmapLaneMap.decode(AmapLaneMap.encode(wide, front, 12)).size());
        assertTrue(AmapLaneMap.decode("").isEmpty());
        assertTrue(AmapLaneMap.decode("1,2|3").isEmpty());
        assertTrue(AmapLaneMap.decode(null).isEmpty());
    }
}
