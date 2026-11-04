# NaviTH Maps 0.9 — Navigation Repair & Marker Customization

Status: **Internal test preview — NOT Production approved**.

## Navigation engine repair
- The Navigation Test APK invokes `AmapPageType.NAVI`, not the route planner screen `ROUTE`.
- Passes real AMAP POI ID when provided by search; long-press destinations use lat/lon.
- Requests real GPS mode (`setNaviMode(1)`) and AMAP native route calculation on start (`setNeedCalculateRouteWhenPresent(true)`).
- Navigation listener records **planning / ready / navigating / rerouting / arrived / error / idle**. On vendor off-route callback the old turn is cleared and SDK recalculates its route internally.
- GPS coordinates, bearing, accuracy and speed reach phone and Android Auto. AMapNaviLocation speed is km/h; Android speed expects m/s, so convert on handoff.
- GPS fix writes `TripStore.LOCATION` event, which refreshes Android Auto's marker (previously missing).
- Exiting the SDK component calls the vendor page exit method; no fake navigation session is created from a preview.
- Voice instructions are owned by AMAP's native navigation component (avoid duplicated TTS).

## User-selected navigation markers
- Settings → Navigation → Position icon, with an actual preview image for each option.
- Standard arrow, minimal arrow, sedan, hatchback, van, SUV, pickup, sports car, perspective-style car and user's own image.
- Stores preference in `navith_view`, shared with the Android Auto map.
- Uses AMAP `MarkerOptions.setFlat(true)` to attach image to the map plane, and inverse-normalized marker angle from actual GPS bearing.
- 2D vs 3D map perspective; car illustration is **2.5D rasterized vector painting**, not a 3D car mesh. The vendor's separate fullscreen Navigation Component uses its own car artwork. To customize that view too, future work must integrate `AMapNaviViewOptions.setCarBitmap` (or a custom navigation view).
- Inaccurate/stale GPS: accuracy circle and subdued marker, no falsely confident heading camera. Smoothing is throttled.

## CI / deterministic checks
Both workflows run `python3 tools/check_navigation_contracts.py` after checkout. Tests confirm wiring and marker math; **passing them does not prove vendor API rights, real GPS routing or Android Auto host compatibility**.

## Manual acceptance tests needed before production
1. Fresh install with privacy prompt, precise location denied/accepted; location off, network off and indoor low-accuracy handling.
2. Select Thai POI with real AMAP POI ID; select map long-press; compute live route with distance, ETA and alternatives.
3. Click **Start navigation** on the Navigation Test APK and verify fullscreen AMAP NAVI opens and real `onStartNavi` / `onNaviInfoUpdate` callbacks arrive.
4. Simulated location safely deviates from calculated route; verify rerouting, old turn disappears, correct new route appears. No risky physical driving tests.
5. Arrive/exit → guidance cleared, normal map restored, restart a new destination.
6. Check speed, bearing and accuracy at 0°, 90°, 180° and 270°; flat marker and heading-up camera in 2D/3D; test poor accuracy radius. Try all icons, restart app.
7. Connect Android Auto via a supported permitted testing host, check live marker GPS refresh, camera follow, destination synchronization, action count and turn card **only with real SDK guidance**.
8. Verify speech language Thai and navigation coverage; AMap global routing, POI and lane coverage varies by region and API key entitlement.
9. Verify SHA1 signing/API key for actual device; build demo signing key is **not for production**.
10. Check TalkBack, large font, portrait/landscape, slow/no connectivity and Android 8–16.

## Important limitations
- Maps-only APK remains route-preview only, with no fake turn-by-turn.
- Android Auto integration remains experimental; a working debug APK is not evidence of host approval or real head-unit functionality.
- Real 3D model cars and AMAP Navigation Component-owned car customization are not implemented.
- AMAP vendor ~93 MB ZIP delivery sometimes times out; CI uses official checksum and HTTP resume with no untrusted mirror.

References:
https://mapsplatform.opnavi.com/api/navigation-sdk-for-android/guide/navi-component/basic-functions
https://mapsplatform.opnavi.com/api/navigation-sdk-for-android/guide/custom-ui/custom-annotation
https://mapsplatform.opnavi.com/api/navigation-sdk-for-android/guide/navigation-map/navi-info
