package com.navith.amap;

/**
 * Decides whether a location sample is usable as a route origin.
 *
 * Some phones and the AMAP location client put wall-clock millis into
 * {@code Location.getElapsedRealtimeNanos()}, or leave that field stale while
 * still delivering a callback. Comparing that field directly to
 * {@code elapsedRealtimeNanos()} then rejects a fix the system just handed us.
 * A plausible elapsed age wins; otherwise the wall clock is used. A live
 * callback whose clocks are both nonsense is accepted, because the provider
 * just invoked the listener.
 */
public final class LocationFixGate {
    /** Live updates older than this are not a navigation origin. */
    public static final long LIVE_MAX_AGE_MS = 180_000L;
    /** Last-known fixes may start a route, but not if they are this old. */
    public static final long CACHE_MAX_AGE_MS = 600_000L;
    /** Ages outside this window mean the clock field is not a real age. */
    public static final long ABSURD_AGE_MS = 24L * 60L * 60L * 1000L;
    public static final float MAX_ACCURACY_METERS = 300f;

    private LocationFixGate() {}

    public static final class Verdict {
        public final boolean accept;
        public final long ageMs;
        /** True when elapsedRealtimeNanos was ignored because it was not a real age. */
        public final boolean elapsedClockIgnored;

        Verdict(boolean accept, long ageMs, boolean elapsedClockIgnored) {
            this.accept = accept;
            this.ageMs = ageMs;
            this.elapsedClockIgnored = elapsedClockIgnored;
        }
    }

    public static Verdict evaluate(long elapsedNowNanos, long fixElapsedNanos,
                                   long wallNowMs, long fixWallMs,
                                   float accuracyMeters, boolean liveCallback) {
        if (Float.isFinite(accuracyMeters) && accuracyMeters > MAX_ACCURACY_METERS) {
            return new Verdict(false, -1L, false);
        }
        Long elapsedAge = plausibleElapsedAge(elapsedNowNanos, fixElapsedNanos);
        Long wallAge = plausibleWallAge(wallNowMs, fixWallMs);
        long limit = liveCallback ? LIVE_MAX_AGE_MS : CACHE_MAX_AGE_MS;
        if (elapsedAge != null && elapsedAge <= limit) {
            return new Verdict(true, Math.max(0L, elapsedAge), false);
        }
        if (wallAge != null && wallAge <= limit) {
            return new Verdict(true, Math.max(0L, wallAge), elapsedAge == null && fixElapsedNanos > 0L);
        }
        if (liveCallback && elapsedAge == null && wallAge == null) {
            return new Verdict(true, 0L, fixElapsedNanos > 0L);
        }
        return new Verdict(false, elapsedAge != null ? elapsedAge : (wallAge == null ? -1L : wallAge), false);
    }

    private static Long plausibleElapsedAge(long elapsedNowNanos, long fixElapsedNanos) {
        if (fixElapsedNanos <= 0L || elapsedNowNanos <= 0L) return null;
        long age = (elapsedNowNanos - fixElapsedNanos) / 1_000_000L;
        if (age < -5_000L || age > ABSURD_AGE_MS) return null;
        return age;
    }

    private static Long plausibleWallAge(long wallNowMs, long fixWallMs) {
        if (fixWallMs <= 0L || wallNowMs <= 0L) return null;
        long age = wallNowMs - fixWallMs;
        if (age < -60_000L || age > ABSURD_AGE_MS) return null;
        return age;
    }
}
