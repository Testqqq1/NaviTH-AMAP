#!/usr/bin/env python3
"""NaviTH immersive navigation UI contract check; not GPS/road validation."""
from pathlib import Path
from xml.etree import ElementTree as ET

root=Path("app/src/main")
layout=ET.parse(root/"res/layout/activity_main.xml").getroot()
android="{http://schemas.android.com/apk/res/android}"
ids=[v.attrib.get(android+"id","").replace("@+id/","")
     for v in layout.iter() if v.attrib.get(android+"id")]
assert len(ids)==len(set(ids)), "Duplicate ids in activity_main"
required={"activeNavTop","activeNavBottom","activeNavPhase","activeNavManeuver",
    "activeNavDistance","activeNavInstruction","activeNavSpeedPanel",
    "activeNavLeftControls","activeNavRecenter","activeNavSound",
    "activeNavSpeed","activeNavSpeedUnit","activeNavEta","activeNavRemaining",
    "activeNavArrival","activeNavStreet","activeNavOverview","activeNavStop",
    "mainMapControls"}
assert required.issubset(set(ids)), f"Missing: {required-set(ids)}"
shapes=["nav_guidance_panel","nav_guidance_round"]
vectors=["nav_guidance_turn_right","nav_guidance_turn_left",
         "nav_guidance_straight","nav_guidance_uturn",
         "nav_guidance_icon_sound","nav_guidance_icon_mute",
         "nav_guidance_icon_center"]
for name in shapes+vectors:
    file=root/"res/drawable"/(name+".xml")
    elem=ET.parse(file).getroot()
    assert elem.tag==("shape" if name in shapes else "vector"),name

main=(root/"java/com/navith/amap/MainActivity.java").read_text()
client=(root/"java/com/navith/amap/MapboxDirectionsClient.java").read_text()
marker=(root/"java/com/navith/amap/NavigationMarkerFactory.java").read_text()
checks={
 "actual Night map when guiding":'mapboxGuiding ? AMap.MAP_TYPE_NIGHT' in main,
 "restores selected map style":'applyMapPreferences(); // restores user-selected' in main,
 "car displayed lower on map":'map.setPointToCenter(mapView.getWidth()/2' in main,
 "recenter UI":'activeNavRecenter' in main and 'recenter()' in main,
 "real maneuver modifier":'maneuver.optString("modifier","")' in client,
 "direction vectors":'nav_guidance_turn_right' in main and 'nav_guidance_turn_left' in main,
 "route line has double stroke":'activeRouteCasing=map.addPolyline(' in main,
 "old white map controls hidden":'R.id.mainMapControls' in main,
 "side sound toggle wired":'toggleGuidanceSound()' in main,
 "cancel guidance on pause":'if(mapboxGuiding)stopMapboxGuidance();' in main,
 "footer uses real route duration":'updateGuidanceTrip(selectedMapboxRoute.meters,selectedMapboxRoute.seconds)' in main,
 "foreground test is clear":'TEST' in main and 'not the AMap Navigation SDK' in main,
 "distinct guidance car":'GUIDANCE_CAR=10' in marker,
}
for name,ok in checks.items():print(("PASS" if ok else "FAIL")+" "+name)
assert all(checks.values()),"Immersive guidance contract failed"
print("Contract pass does not establish runtime GPS/voice/road safety.")
