package com.navith.amap.car;

/**
 * Remaining-trip math for {@code TravelEstimate}. Pure and clock-injected so
 * the arrival time can be tested without Android.
 */
public final class GuidanceEstimate {
    public final int remainingMeters;
    public final long remainingSeconds;
    public final long arrivalEpochMillis;

    private GuidanceEstimate(int remainingMeters, long remainingSeconds, long arrivalEpochMillis) {
        this.remainingMeters = remainingMeters;
        this.remainingSeconds = remainingSeconds;
        this.arrivalEpochMillis = arrivalEpochMillis;
    }

    /**
     * @return null when the inputs cannot be a travel estimate (negative
     *         distance or time, or an arrival instant that does not fit in a long)
     */
    public static GuidanceEstimate of(int remainingMeters, int remainingSeconds, long nowEpochMillis) {
        if (remainingMeters < 0 || remainingSeconds < 0 || nowEpochMillis < 0) return null;
        long extraMillis = remainingSeconds * 1000L;
        if (nowEpochMillis > Long.MAX_VALUE - extraMillis) return null;
        return new GuidanceEstimate(remainingMeters, remainingSeconds, nowEpochMillis + extraMillis);
    }
}
