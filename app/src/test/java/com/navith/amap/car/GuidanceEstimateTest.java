package com.navith.amap.car;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class GuidanceEstimateTest {
    @Test
    public void arrivalIsNowPlusRemainingTime() {
        long now = 1_700_000_000_000L;
        GuidanceEstimate estimate = GuidanceEstimate.of(1540, 90, now);
        assertEquals(1540, estimate.remainingMeters);
        assertEquals(90L, estimate.remainingSeconds);
        assertEquals(now + 90_000L, estimate.arrivalEpochMillis);
    }

    @Test
    public void zeroRemainingArrivesAtTheGivenInstant() {
        GuidanceEstimate estimate = GuidanceEstimate.of(0, 0, 5_000L);
        assertEquals(0, estimate.remainingMeters);
        assertEquals(0L, estimate.remainingSeconds);
        assertEquals(5_000L, estimate.arrivalEpochMillis);
    }

    @Test
    public void rejectsNegativeInputsAndOverflow() {
        assertNull(GuidanceEstimate.of(-1, 10, 1_000L));
        assertNull(GuidanceEstimate.of(10, -1, 1_000L));
        assertNull(GuidanceEstimate.of(10, 10, -1L));
        assertNull(GuidanceEstimate.of(1, 1, Long.MAX_VALUE));
    }
}
