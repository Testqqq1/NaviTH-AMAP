#!/usr/bin/env python3
"""Source-level checks; does NOT replace Android compilation or device tests."""
from pathlib import Path
app=Path("app/src/main/java/com/navith/amap")
main=(app/"MainActivity.java").read_text()
client=(app/"MapboxDirectionsClient.java").read_text()
gradle=Path("app/build.gradle").read_text()
workflow=Path(".github/workflows/build-android.yml").read_text()
checks={
 "Opt-in Mapbox token from CI/local settings":"MAPBOX_PUBLIC_TOKEN" in gradle and "MAPBOX_PUBLIC_TOKEN" in workflow,
 "Mapbox optional (preserves AMap without key)":"MapboxDirectionsClient.configured(this)" in main,
 "User consent before Mapbox network call":"MapboxDirectionsClient.consented(this)" in main and 'consented(context)' in client,
 "Directions v5, real geometry and steps":"api.mapbox.com/directions/v5/" in client and 'geometries=geojson' in client and 'steps=true' in client,
 "Road profile":"driving-traffic" in client,
 "Fresh GPS required for navigation":"hasFreshAccurateFix()" in main and "updateMapboxGuidance(fix)" in main,
 "Graceful stop in background":"if(mapboxGuiding)stopMapboxGuidance();" in main,
 "No export of REST prototype to Car guidance":"NaviGuidanceStore.update(this" not in main,
 "Thai instruction language":"language=th" in client,
 "Mapbox attribution":"Powered by Mapbox" in main,
}
for name,ok in checks.items():print(("PASS" if ok else "FAIL")+" "+name)
assert all(checks.values()),"Source contract failed"
print("Note: Build and real-device testing are still required.")
