package com.navith.amap;

/**
 * Human-readable Thai for AMAP Navigation route errors.
 * Code 3 is documented as "起点错误 / 请选择国内坐标点": the start is outside
 * the domestic navigation area. The Android Navigation SDK 11.2.1001 does not
 * calculate a driving route in Thailand.
 */
public final class NaviRouteErrors {
    private NaviRouteErrors() {}

    public static String thai(int code) {
        switch (code) {
            case 2:
                return "เครือข่ายขัดข้อง ลองคำนวณเส้นทางอีกครั้ง";
            case 3:
                return "จุดเริ่มต้นอยู่นอกพื้นที่นำทางของ AMAP (รองรับเฉพาะในจีน) จึงใช้เส้นทางจากแผนที่แทน";
            case 6:
                return "จุดหมายอยู่นอกพื้นที่นำทางของ AMAP (รองรับเฉพาะในจีน)";
            case 10:
                return "ไม่พบถนนใกล้จุดเริ่มต้น ลองขยับจุดเริ่ม";
            case 11:
                return "ไม่พบถนนใกล้จุดหมาย ลองขยับจุดหมาย";
            case 25:
                return "จุดเริ่มหรือจุดหมายอยู่นอกแผ่นดินจีน AMAP Navigation จึงคำนวณไม่ได้";
            default:
                return "นำทางของ AMAP คำนวณเส้นทางไม่ได้ (รหัส " + code + ")";
        }
    }
}
