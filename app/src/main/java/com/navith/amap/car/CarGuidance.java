package com.navith.amap.car;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.car.app.model.CarIcon;
import androidx.car.app.model.DateTimeWithZone;
import androidx.car.app.model.Distance;
import androidx.car.app.navigation.model.Lane;
import androidx.car.app.navigation.model.LaneDirection;
import androidx.car.app.navigation.model.Maneuver;
import androidx.car.app.navigation.model.RoutingInfo;
import androidx.car.app.navigation.model.Step;
import androidx.car.app.navigation.model.TravelEstimate;
import androidx.core.graphics.drawable.IconCompat;

import com.navith.amap.NaviLocale;

import java.util.List;
import java.util.TimeZone;

/**
 * Builds the car navigation template from a fresh AMAP {@code NaviInfo} snapshot.
 * The following maneuver is not shown: NaviInfo has no next-next icon, and
 * guessing it from path step indexes is easy to get wrong.
 */
final class CarGuidance {
    private static final String TAG = "NaviTH-Car";
    private CarGuidance() {}

    static RoutingInfo routing(Context context, NaviGuidanceStore.Snapshot guidance) {
        try {
            return build(context, guidance, true);
        } catch (RuntimeException ex) {
            Log.w(TAG, "Lane or maneuver template rejected; showing the turn without lanes", ex);
            try {
                return build(context, guidance, false);
            } catch (RuntimeException fatal) {
                Log.w(TAG, "Falling back to a basic continue instruction", fatal);
                return basic(context, guidance);
            }
        }
    }

    /** onArriveDestination is a real SDK callback, so the car shows arrival rather than the last turn. */
    static RoutingInfo arrival(Context context, String destinationName) {
        AmapManeuverMap.Spec spec = AmapManeuverMap.map(AmapManeuverMap.ICON_ARRIVED_DESTINATION);
        CarIcon icon = ManeuverIcons.icon(context, spec.type);
        Step.Builder step = new Step.Builder(AmapManeuverMap.cue(spec, destinationName, "", NaviLocale.thai(context)))
                .setManeuver(AmapManeuverMap.build(spec, icon));
        String road = AmapManeuverMap.road(destinationName);
        if (!road.isEmpty()) step.setRoad(road);
        return new RoutingInfo.Builder()
                .setCurrentStep(step.build(), Distance.create(0, Distance.UNIT_METERS))
                .build();
    }

    static TravelEstimate arrivedEstimate() {
        GuidanceEstimate estimate = GuidanceEstimate.of(0, 0, System.currentTimeMillis());
        if (estimate == null) return null;
        try {
            return new TravelEstimate.Builder(
                    Distance.create(0, Distance.UNIT_METERS),
                    DateTimeWithZone.create(estimate.arrivalEpochMillis, TimeZone.getDefault()))
                    .setRemainingTimeSeconds(0)
                    .build();
        } catch (RuntimeException ex) {
            Log.w(TAG, "Arrival estimate unavailable", ex);
            return null;
        }
    }

    static TravelEstimate travelEstimate(NaviGuidanceStore.Snapshot guidance) {
        GuidanceEstimate estimate = GuidanceEstimate.of(
                guidance.remainingMeters, guidance.remainingSeconds, System.currentTimeMillis());
        if (estimate == null) return null;
        try {
            return new TravelEstimate.Builder(
                    Distance.create(estimate.remainingMeters, Distance.UNIT_METERS),
                    DateTimeWithZone.create(estimate.arrivalEpochMillis, TimeZone.getDefault()))
                    .setRemainingTimeSeconds(estimate.remainingSeconds)
                    .build();
        } catch (RuntimeException ex) {
            Log.w(TAG, "Travel estimate unavailable", ex);
            return null;
        }
    }

    private static RoutingInfo build(Context context, NaviGuidanceStore.Snapshot guidance, boolean withLanes) {
        boolean thai = NaviLocale.thai(context);
        // Exit index stays 0 until a documented SDK field actually provides one.
        AmapManeuverMap.Spec spec = AmapManeuverMap.map(guidance.iconType);
        CarIcon icon = spec.exitNumber >= 1 ? null : ManeuverIcons.icon(context, spec.type);
        Maneuver maneuver = AmapManeuverMap.build(spec, icon);
        Step.Builder step = new Step.Builder(
                AmapManeuverMap.cue(spec, guidance.nextRoad, guidance.exitLabel, thai))
                .setManeuver(maneuver);
        String road = AmapManeuverMap.road(guidance.nextRoad);
        if (!road.isEmpty()) step.setRoad(road);
        if (withLanes) attachLanes(step, guidance.lanes);
        return new RoutingInfo.Builder()
                .setCurrentStep(step.build(),
                        Distance.create(Math.max(0, guidance.nextMeters), Distance.UNIT_METERS))
                .build();
    }

    /**
     * RoutingInfo requires a lanes image whenever lane data is set. If either
     * cannot be built, no lanes are attached.
     */
    private static void attachLanes(Step.Builder step, String encoded) {
        List<AmapLaneMap.Lane> lanes = AmapLaneMap.decode(encoded);
        if (lanes.isEmpty()) return;
        Bitmap art = LaneArt.draw(lanes);
        CarIcon image = new CarIcon.Builder(IconCompat.createWithBitmap(art)).build();
        for (AmapLaneMap.Lane lane : lanes) {
            Lane.Builder builder = new Lane.Builder();
            for (AmapLaneMap.Direction direction : lane.directions) {
                builder.addDirection(LaneDirection.create(direction.shape, direction.recommended));
            }
            step.addLane(builder.build());
        }
        step.setLanesImage(image);
    }

    private static RoutingInfo basic(Context context, NaviGuidanceStore.Snapshot guidance) {
        CarIcon icon = ManeuverIcons.icon(context, Maneuver.TYPE_UNKNOWN);
        Maneuver.Builder maneuver = new Maneuver.Builder(Maneuver.TYPE_UNKNOWN);
        if (icon != null) maneuver.setIcon(icon);
        Step step = new Step.Builder(NaviLocale.text(context, "ตามเส้นทาง", "Continue"))
                .setManeuver(maneuver.build())
                .build();
        return new RoutingInfo.Builder()
                .setCurrentStep(step, Distance.create(Math.max(0, guidance.nextMeters), Distance.UNIT_METERS))
                .build();
    }
}
