package com.navith.amap.car;

import androidx.car.app.navigation.model.LaneDirection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Maps AMAP {@code LaneAction} codes from {@code AMapLaneInfo.backgroundLane}
 * and {@code frontLane} to Car App lane directions.
 *
 * <p>{@code frontLane} value 255 ({@code LANE_ACTION_NULL}) means that lane is
 * not recommended. Other front values are the direction the driver should use.
 * Codes that are not a turn (bus, tidal, variable, empty) do not invent an arrow.
 */
public final class AmapLaneMap {
    public static final int ACTION_NULL = 255;
    /** Car App hosts get crowded past this; extra lanes are dropped, not guessed. */
    public static final int MAX_LANES = 10;

    private static final int STRAIGHT = LaneDirection.SHAPE_STRAIGHT;
    private static final int LEFT = LaneDirection.SHAPE_NORMAL_LEFT;
    private static final int RIGHT = LaneDirection.SHAPE_NORMAL_RIGHT;
    private static final int U_LEFT = LaneDirection.SHAPE_U_TURN_LEFT;
    private static final int U_RIGHT = LaneDirection.SHAPE_U_TURN_RIGHT;
    private static final int UNKNOWN = LaneDirection.SHAPE_UNKNOWN;
    private static final int[] ORDER = {STRAIGHT, LEFT, RIGHT, U_LEFT, U_RIGHT};

    private AmapLaneMap() {}

    public static final class Direction {
        public final int shape;
        public final boolean recommended;

        Direction(int shape, boolean recommended) {
            this.shape = shape;
            this.recommended = recommended;
        }
    }

    public static final class Lane {
        public final List<Direction> directions;

        Lane(List<Direction> directions) {
            this.directions = directions;
        }
    }

    public static String encode(int[] background, int[] front, int count) {
        if (background == null || front == null || count <= 0) return "";
        int n = Math.min(count, Math.min(background.length, front.length));
        if (n > MAX_LANES) n = MAX_LANES;
        if (n <= 0) return "";
        StringBuilder left = new StringBuilder();
        StringBuilder right = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                left.append(',');
                right.append(',');
            }
            left.append(background[i]);
            right.append(front[i]);
        }
        return left.append('|').append(right).toString();
    }

    public static List<Lane> decode(String encoded) {
        List<Lane> lanes = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return lanes;
        String[] sides = encoded.split("\\|", -1);
        if (sides.length != 2) return lanes;
        int[] background = parse(sides[0]);
        int[] front = parse(sides[1]);
        if (background == null || front == null || background.length == 0
                || background.length != front.length || background.length > MAX_LANES) {
            return lanes;
        }
        for (int i = 0; i < background.length; i++) lanes.add(lane(background[i], front[i]));
        return lanes;
    }

    private static int[] parse(String side) {
        if (side.isEmpty()) return new int[0];
        String[] parts = side.split(",", -1);
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException bad) {
                return null;
            }
        }
        return values;
    }

    static Lane lane(int background, int front) {
        int[] backgroundShapes = shapes(background);
        int[] frontShapes = front == ACTION_NULL ? new int[0] : shapes(front);
        boolean frontHasTurn = hasTurn(frontShapes);
        LinkedHashSet<Integer> shown = new LinkedHashSet<>();
        addTurns(shown, backgroundShapes);
        if (front != ACTION_NULL && frontHasTurn) addTurns(shown, frontShapes);
        List<Direction> directions = new ArrayList<>();
        if (shown.isEmpty()) {
            boolean recommended = front != ACTION_NULL;
            directions.add(new Direction(UNKNOWN, recommended));
        } else {
            for (int shape : shown) {
                boolean recommended = front != ACTION_NULL
                        && (!frontHasTurn || contains(frontShapes, shape));
                directions.add(new Direction(shape, recommended));
            }
        }
        return new Lane(directions);
    }

    private static void addTurns(LinkedHashSet<Integer> into, int[] shapes) {
        for (int shape : ORDER) if (contains(shapes, shape)) into.add(shape);
    }

    private static boolean hasTurn(int[] shapes) {
        for (int shape : shapes) {
            for (int known : ORDER) if (shape == known) return true;
        }
        return false;
    }

    private static boolean contains(int[] shapes, int shape) {
        for (int candidate : shapes) if (candidate == shape) return true;
        return false;
    }

    /** LaneAction → arrow shapes. Empty means "no turn arrow" (bus, null, unknown). */
    static int[] shapes(int action) {
        switch (action) {
            case 0: // LANE_ACTION_AHEAD
            case 13: // LANE_ACTION_LEFT_IN_AHEAD (straight + extension)
                return new int[] {STRAIGHT};
            case 1: // LANE_ACTION_LEFT
                return new int[] {LEFT};
            case 2: // LANE_ACTION_AHEAD_LEFT
                return new int[] {STRAIGHT, LEFT};
            case 3: // LANE_ACTION_RIGHT
                return new int[] {RIGHT};
            case 4: // LANE_ACTION_AHEAD_RIGHT
                return new int[] {STRAIGHT, RIGHT};
            case 5: // LANE_ACTION_LU_TURN
                return new int[] {U_LEFT};
            case 6: // LANE_ACTION_LEFT_RIGHT
                return new int[] {LEFT, RIGHT};
            case 7: // LANE_ACTION_AHEAD_LEFT_RIGHT
                return new int[] {STRAIGHT, LEFT, RIGHT};
            case 8: // LANE_ACTION_RU_TURN
                return new int[] {U_RIGHT};
            case 9: // LANE_ACTION_AHEAD_LU_TURN
                return new int[] {STRAIGHT, U_LEFT};
            case 10: // LANE_ACTION_AHEAD_RU_TURN
                return new int[] {STRAIGHT, U_RIGHT};
            case 11: // LANE_ACTION_LEFT_LU_TURN
            case 14: // LANE_ACTION_LEFT_IN_LEFT_LU_TURN
                return new int[] {LEFT, U_LEFT};
            case 12: // LANE_ACTION_RIGHT_RU_TURN
                return new int[] {RIGHT, U_RIGHT};
            case 16: // LANE_ACTION_AHEAD_LEFT_LU_TURN
                return new int[] {STRAIGHT, LEFT, U_LEFT};
            case 17: // LANE_ACTION_RIGHT_RU_TURN_EX: 右转+左掉头
                return new int[] {RIGHT, U_LEFT};
            case 18: // LANE_ACTION_LEFT_RU_TURN: 左转+右转+左掉头
                return new int[] {LEFT, RIGHT, U_LEFT};
            case 19: // LANE_ACTION_AHEAD_RIGHT_RU_TURN: 直行+右转+左掉头
                return new int[] {STRAIGHT, RIGHT, U_LEFT};
            case 20: // LANE_ACTION_LEFT_LU_TURN_EX: 左转+右掉头
                return new int[] {LEFT, U_RIGHT};
            default:
                return new int[0];
        }
    }
}
