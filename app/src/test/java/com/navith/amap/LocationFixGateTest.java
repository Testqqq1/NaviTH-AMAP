package com.navith.amap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationFixGateTest {
    private static final long ELAPSED_NOW = 3_600_000_000_000L; // 1 hour, in nanos
    private static final long WALL_NOW = 1_700_000_000_000L;

    @Test
    public void acceptsAFreshElapsedRealtimeFix() {
        long fixElapsed = ELAPSED_NOW - 4_000L * 1_000_000L;
        LocationFixGate.Verdict verdict = LocationFixGate.evaluate(
                ELAPSED_NOW, fixElapsed, WALL_NOW, WALL_NOW - 4_000L, 25f, true);
        assertTrue(verdict.accept);
        assertFalse(verdict.elapsedClockIgnored);
    }

    @Test
    public void rejectsTheOldFifteenSecondGateWhenTheFixIsStillRecent() {
        long fixElapsed = ELAPSED_NOW - 40_000L * 1_000_000L;
        LocationFixGate.Verdict verdict = LocationFixGate.evaluate(
                ELAPSED_NOW, fixElapsed, WALL_NOW, WALL_NOW - 40_000L, 80f, true);
        assertTrue(verdict.accept);
    }

    @Test
    public void fallsBackToWallClockWhenElapsedNanosIsActuallyWallClock() {
        // Some SDKs store currentTimeMillis * 1_000_000 in elapsedRealtimeNanos.
        long bogusElapsed = WALL_NOW * 1_000_000L;
        LocationFixGate.Verdict verdict = LocationFixGate.evaluate(
                ELAPSED_NOW, bogusElapsed, WALL_NOW, WALL_NOW - 2_000L, 40f, true);
        assertTrue(verdict.accept);
        assertTrue(verdict.elapsedClockIgnored);
    }

    @Test
    public void acceptsALiveCallbackWhenBothClocksAreUnset() {
        LocationFixGate.Verdict verdict = LocationFixGate.evaluate(
                ELAPSED_NOW, 0L, WALL_NOW, 0L, -1f, true);
        assertTrue(verdict.accept);
    }

    @Test
    public void rejectsACachedFixBothClocksCallAncient() {
        LocationFixGate.Verdict verdict = LocationFixGate.evaluate(
                ELAPSED_NOW, ELAPSED_NOW - 2L * 60L * 60L * 1_000_000_000L,
                WALL_NOW, WALL_NOW - 2L * 60L * 60L * 1000L,
                30f, false);
        assertFalse(verdict.accept);
    }

    @Test
    public void acceptsATenMinuteLastKnownFixAsAStartPoint() {
        long age = 9L * 60L * 1000L;
        LocationFixGate.Verdict verdict = LocationFixGate.evaluate(
                ELAPSED_NOW, ELAPSED_NOW - age * 1_000_000L,
                WALL_NOW, WALL_NOW - age, 120f, false);
        assertTrue(verdict.accept);
    }

    @Test
    public void rejectsPoorAccuracyEvenIfTheTimestampIsFresh() {
        LocationFixGate.Verdict verdict = LocationFixGate.evaluate(
                ELAPSED_NOW, ELAPSED_NOW - 1_000_000_000L,
                WALL_NOW, WALL_NOW - 1_000L, 450f, true);
        assertFalse(verdict.accept);
    }

    @Test
    public void acceptsUrbanAccuracyAboveTheOld100MeterCutoff() {
        LocationFixGate.Verdict verdict = LocationFixGate.evaluate(
                ELAPSED_NOW, ELAPSED_NOW - 1_000_000_000L,
                WALL_NOW, WALL_NOW - 1_000L, 180f, true);
        assertTrue(verdict.accept);
    }
}
