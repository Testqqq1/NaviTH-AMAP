#!/usr/bin/env python3
"""NaviTH original Android UI contract checks; compile/device QA remain mandatory."""
from pathlib import Path
import xml.etree.ElementTree as ET
layout=Path("app/src/main/res/layout/activity_main.xml")
root=ET.parse(layout).getroot()
a="{http://schemas.android.com/apk/res/android}"
identifiers=[node.attrib.get(a+"id","") for node in root.iter()]
names=[x[5:] for x in identifiers if x.startswith("@+id/")]
assert len(names)==len(set(names)), "Duplicate Android UI IDs"
needed={"topSearchBar","homeSearchButton","homePanel","routePanel","homeGrabber",
"homeQuickDrive","homeQuickWalk","homeQuickCycle","homeQuickTransit","homeQuickRide",
"homeQuickMoto","homeQuickSettings","homeQuickSaved","zoomPlusButton","zoomMinusButton",
"tabHome","tabExplore","tabTrips","tabSaved","tabMine","modeCycle",
"locationButton","mapTypeButton","startNavigationButton","searchOverlay"}
assert needed.issubset(set(names)), "Missing ids: "+str(needed-set(names))
main=Path("app/src/main/java/com/navith/amap/MainActivity.java").read_text()
settings=Path("app/src/main/java/com/navith/amap/SettingsActivity.java").read_text()
client=Path("app/src/main/java/com/navith/amap/MapboxDirectionsClient.java").read_text()
checks={
    "quick modes have click actions":all("R.id."+n in main for n in needed if n.startswith("homeQuick") or n.startswith("tab")),
    "zoom controls wired": "zoomMap(1f)" in main and "zoomMap(-1f)" in main,
    "search bar preference applied":"home_search_bottom" in main and "home_search_bottom" in settings,
    "screen orientation preference applied":"screen_orientation" in main and "screen_orientation" in settings,
    "camera direction preference applied":"camera_direction" in main and "camera_direction" in settings,
    "battery-aware screen policy":"battery_aware" in main and "battery_aware" in settings,
    "personal marker choice is actionable":'PICK_MARKER' in settings and 'marker_custom_hint' in settings,
    "unsupported provider dialogs":'showUnavailableTravelMode' in main and '"vehicle_profile".equals(key)' in settings,
    "Thai cycling directions":"cycling" in client and 'language=th' in client,
    "avoid toll and motorway preferences":"avoid_tolls" in client and "avoid_highway" in client,
    "voice style applied":"voice_style" in main and "voice_style" in settings,
    "Mapbox attribution present":"Powered by Mapbox" in main,
}
for name,ok in checks.items(): print(("PASS" if ok else "FAIL")+" "+name)
assert all(checks.values())
print("Static UI checks passed. Device and visual QA are still required.")
