#!/usr/bin/env python3
"""Source & resource acceptance contracts; *not* road-navigation certification."""
from pathlib import Path
from xml.etree import ElementTree as ET
root=Path("app/src/main")
code=(root/"java/com/navith/amap/MainActivity.java").read_text()
settings=(root/"java/com/navith/amap/SettingsActivity.java").read_text()
vehicles=(root/"java/com/navith/amap/VehicleCapabilityRegistry.java").read_text()
scene=(root/"java/com/navith/amap/NavigationUiState.java").read_text()
diag=(root/"java/com/navith/amap/NavigationDiagnostics.java").read_text()
marker=(root/"java/com/navith/amap/NavigationMarkerFactory.java").read_text()
layout=root/"res/layout/activity_main.xml"
doc=ET.parse(layout).getroot()
attr="{http://schemas.android.com/apk/res/android}"
ids={node.get(attr+"id","") for node in doc.iter()}
required={"activeNavTop","activeNavBottom","activeNavPhase","activeNavInstruction",
    "activeNavDistance","activeNavEta","activeNavRemaining","activeNavStop",
    "activeNavRecenter","homeQuickDrive","homeTravelModes","homeSecondaryModes",
    "homeShortcutRow","homeBottomNav"}
assert all("@+id/"+id in ids for id in required), "Active navigation or home IDs missing"
checks={
    "vehicle choices are persisted":'VehicleCapabilityRegistry.select(this,value)' in settings,
    "no vehicle fallback":'VehicleCapabilityRegistry.drivingSupported(this)' in code and 'return CAR;' in vehicles,
    "unsupported vehicle status inline":"VehicleCapabilityRegistry.availability(this)" in settings,
    "navigation test scene":"showActiveNavigation(true)" in code and "showActiveNavigation(false)" in code,
    "navigation transitions":"guidanceScene.rerouting()" in code and "guidanceScene.rerouted()" in code,
    "no full-route camera during reroute":'if(!mapboxGuiding)' in code and "newLatLngBounds(bounds.build()" in code,
    "minimal marker default":'settings.getInt("vehicle_icon",MINIMAL_ARROW)' in marker,
    "cached search with limit":'size()>20' in code and "poiCache.get(" in code,
    "throttled voice UI":'lastGuidanceUiFrame>=500L' in code,
    "privacy safe telemetry":"getSharedPreferences(PREFS" in diag and "getLatitude()" not in diag,
    "settings safe area":"WindowInsets.Type.systemBars()" in settings,
    "compact home":"homeOpen?View.VISIBLE:View.GONE" in code,
}
for name,ok in checks.items():print(("PASS" if ok else "FAIL")+" "+name)
assert all(checks.values()),"v1.2 feature contract failed"
names=["car","bus","ride","walk","bike","moto","route","explore",
       "favorite","layers","settings","home","profile","search"]
for name in names:
    file=root/f"res/drawable/nav_min_{name}.xml"
    vector=ET.parse(file).getroot()
    assert vector.tag=="vector" and len(list(vector))>=1, f"Invalid vector {name}"
print("PASS: 14 original vector drawables parsed")
print("Note: CI build, provider credentials, mock GPS, device UX and road tests remain necessary.")
