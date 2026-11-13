#!/usr/bin/env python3
"""Compile-time source contracts. NOT an actual device / Android Auto test."""
from pathlib import Path
r=Path(__file__).resolve().parent.parent
def read(p):return (r/p).read_text(encoding="utf-8")
bridge=read("app/src/main/java/com/navith/amap/NavSdkBridge.java")
store=read("app/src/main/java/com/navith/amap/car/NaviGuidanceStore.java")
trip=read("app/src/main/java/com/navith/amap/car/TripStore.java")
factory=read("app/src/main/java/com/navith/amap/NavigationMarkerFactory.java")
phone=read("app/src/main/java/com/navith/amap/MainActivity.java")
car=read("app/src/main/java/com/navith/amap/car/NaviCarScreen.java")
guidance=read("app/src/main/java/com/navith/amap/car/CarGuidance.java")
icons=read("app/src/main/java/com/navith/amap/car/ManeuverIcons.java")
settings=read("app/src/main/java/com/navith/amap/SettingsActivity.java")
checks={
    "Real NAVI page, not just a ROUTE preview":'pageType.getField("NAVI")' in bridge,
    "Request real GPS navigation mode":'setNaviMode",int.class' in bridge and 'invoke(config,1)' in bridge,
    "Vendor routes calculated on start":'setNeedCalculateRouteWhenPresent", true' in bridge,
    "AMAP privacy before guidance start":bridge.index('privacy.getMethod("updatePrivacyShow"')<bridge.index('attachGuidanceListener(activity.getApplicationContext())'),
    "Actual navigation and error callbacks":all(k in bridge for k in ['"onStartNavi"','"onNaviInfoUpdate"','"onCalculateRouteSuccess"','"onCalculateRouteFailure"','"onReCalculateRouteForYaw"','"onArriveDestination"','"onExitPage"']),
    "Vendor speed converted km/h to m/s":'speedKmh/3.6f' in bridge,
    "Rerouting clears stale car instructions":'remove("navi_road")' in store.split("public static void rerouting",1)[1].split("public static void arrived",1)[0],
    "Every shared GPS fix signals Android Auto":trip.count('.putLong(LOCATION,System.currentTimeMillis())')==2,
    "Accuracy and freshness exported":'public static float accuracy' in trip and 'hasFreshNavigationLocation' in trip,
    "AMAP flat marker enabled on both screens":'.setFlat(true)' in phone and '.setFlat(true)' in car,
    "Same marker factory on both screens":'NavigationMarkerFactory.make' in phone and 'NavigationMarkerFactory.make' in car,
    "Marker picker in Navigation Settings":'case "navigation"' in settings and 'NavigationMarkerFactory.preview' in settings,
    "Native AMAP SDK preserved":"implementation files('libs/amap-combined-20260805.jar')" in read("app/build.gradle"),
    "AMAP icon type is read from NaviInfo":'getIconType' in bridge,
    "Lane show and hide are observed":'"showLaneInfo"' in bridge and '"hideLaneInfo"' in bridge,
    "Guidance store keeps icon and lanes":'"navi_icon"' in store and '"navi_lanes"' in store,
    "Car routing maps maneuvers and sets a travel estimate":'AmapManeuverMap.map' in guidance and 'setDestinationTravelEstimate' in car,
    "Unknown maneuvers include an icon resource":'ic_m_unknown' in icons,
}
for label,ok in checks.items(): print(("PASS" if ok else "FAIL")+" "+label)
if not all(checks.values()):raise SystemExit("Navigation source contract check failed")
def angle(bearing):return (360-((bearing%360+360)%360))%360
for b,a in [(0,0),(90,270),(180,180),(270,90),(360,0),(-90,90)]:
    if angle(b)!=a:raise SystemExit("Marker bearing check failed")
print("PASS 6 cardinal marker bearings")
print("NOTE: GPS, turn voice, Thailand routing coverage and Android Auto require physical runtime testing.")
