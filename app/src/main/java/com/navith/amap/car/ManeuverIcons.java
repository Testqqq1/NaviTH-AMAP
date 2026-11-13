package com.navith.amap.car;

import android.content.Context;
import android.util.Log;

import androidx.car.app.model.CarIcon;
import androidx.car.app.navigation.model.Maneuver;
import androidx.core.graphics.drawable.IconCompat;

import com.navith.amap.R;

/** Glyph for each maneuver type we publish. Hosts draw their own art when no icon is set. */
final class ManeuverIcons {
    private ManeuverIcons() {}

    static CarIcon icon(Context context, int type) {
        try {
            return new CarIcon.Builder(IconCompat.createWithResource(context, res(type))).build();
        } catch (RuntimeException ex) {
            Log.w("NaviTH-Car", "Maneuver icon unavailable", ex);
            return null;
        }
    }

    static int res(int type) {
        switch (type) {
            case Maneuver.TYPE_STRAIGHT:
                return R.drawable.ic_m_straight;
            case Maneuver.TYPE_TURN_SLIGHT_LEFT:
                return R.drawable.ic_m_slight_left;
            case Maneuver.TYPE_TURN_NORMAL_LEFT:
                return R.drawable.ic_m_left;
            case Maneuver.TYPE_TURN_SHARP_LEFT:
                return R.drawable.ic_m_sharp_left;
            case Maneuver.TYPE_TURN_SLIGHT_RIGHT:
                return R.drawable.ic_m_slight_right;
            case Maneuver.TYPE_TURN_NORMAL_RIGHT:
                return R.drawable.ic_m_right;
            case Maneuver.TYPE_TURN_SHARP_RIGHT:
                return R.drawable.ic_m_sharp_right;
            case Maneuver.TYPE_U_TURN_LEFT:
                return R.drawable.ic_m_uturn_left;
            case Maneuver.TYPE_U_TURN_RIGHT:
                return R.drawable.ic_m_uturn_right;
            case Maneuver.TYPE_KEEP_LEFT:
                return R.drawable.ic_m_keep_left;
            case Maneuver.TYPE_KEEP_RIGHT:
                return R.drawable.ic_m_keep_right;
            case Maneuver.TYPE_ROUNDABOUT_ENTER_CCW:
            case Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW:
            case Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE:
                return R.drawable.ic_m_round_enter_ccw;
            case Maneuver.TYPE_ROUNDABOUT_ENTER_CW:
            case Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW:
            case Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE:
                return R.drawable.ic_m_round_enter_cw;
            case Maneuver.TYPE_ROUNDABOUT_EXIT_CCW:
            case Maneuver.TYPE_ROUNDABOUT_EXIT_CW:
                return R.drawable.ic_m_round_exit;
            case Maneuver.TYPE_FERRY_BOAT:
            case Maneuver.TYPE_FERRY_BOAT_LEFT:
            case Maneuver.TYPE_FERRY_BOAT_RIGHT:
                return R.drawable.ic_m_ferry;
            case Maneuver.TYPE_DESTINATION:
            case Maneuver.TYPE_DESTINATION_STRAIGHT:
            case Maneuver.TYPE_DESTINATION_LEFT:
            case Maneuver.TYPE_DESTINATION_RIGHT:
                return R.drawable.ic_m_destination;
            case Maneuver.TYPE_UNKNOWN:
            default:
                return R.drawable.ic_m_unknown;
        }
    }
}
