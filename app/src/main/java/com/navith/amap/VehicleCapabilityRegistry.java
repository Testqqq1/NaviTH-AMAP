package com.navith.amap;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The selection a person makes is separate from a routing provider's actual capability.
 * Unsupported profiles are saved so the UI reflects the selection, but NEVER silently
 * routed as an ordinary car (important for clearance/weight/one-way restrictions).
 */
public final class VehicleCapabilityRegistry {
    public static final String CAR="car", EV="ev", TRUCK="truck",
        MOTORCYCLE="motorcycle", BUS="bus";
    private VehicleCapabilityRegistry() {}

    public static String selected(Context c) {
        SharedPreferences p=c.getSharedPreferences("navith_view",Context.MODE_PRIVATE);
        String profile=p.getString("vehicle_profile",CAR);
        return known(profile)?profile:CAR;
    }
    public static void select(Context c,String profile) {
        if(known(profile))c.getSharedPreferences("navith_view",Context.MODE_PRIVATE)
            .edit().putString("vehicle_profile",profile).apply();
    }
    public static boolean known(String p) {
        return CAR.equals(p)||EV.equals(p)||TRUCK.equals(p)
            ||MOTORCYCLE.equals(p)||BUS.equals(p);
    }
    public static boolean drivingSupported(Context c) {
        return CAR.equals(selected(c));
    }
    public static boolean navigationSupported(Context c) {
        // This class reports *vehicle* capability only. SDK/key and guidance
        // checks must also pass separately before starting navigation.
        return drivingSupported(c);
    }
    public static String label(Context c,String p) {
        boolean th=NaviLocale.thai(c);
        switch(p) {
            case EV:return th?"รถไฟฟ้า":"Electric car";
            case TRUCK:return th?"รถบรรทุก":"Truck";
            case MOTORCYCLE:return th?"มอเตอร์ไซค์":"Motorcycle";
            case BUS:return th?"รถบัส":"Bus";
            default:return th?"รถยนต์":"Car";
        }
    }
    public static String availability(Context c) {
        if(drivingSupported(c))
            return NaviLocale.text(c,"พร้อมคำนวณเส้นทางรถยนต์ เมื่อผู้ให้บริการพร้อม",
                "Car routing requires a configured and available provider");
        return NaviLocale.text(c,
            "บันทึกประเภทแล้ว • ยังไม่มี API เส้นทางเฉพาะสำหรับรถประเภทนี้ จึงปิดการคำนวณเส้นทางเพื่อความปลอดภัย",
            "Selection saved • Vehicle-specific routing is not connected; routing is disabled for safety.");
    }
}
