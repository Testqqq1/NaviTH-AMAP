# NaviTH v0.7 — AMap APK reference and Android Auto integration

## Reference inspected (static only)

User-supplied `Amap_V17.03.0.1180_android_C3060_(Build2611021635LEPQ86Tfi4-64).apk` (172 MB compressed). Static manifest metadata:

- Package: `com.autonavi.minimap`
- Version: `17.03.0.1180`
- minSdk 21, targetSdk 35
- Declares Huawei HiCar (`huawei.intent.category.hicar.MAP`) and UCar (`com.ucar.intent.action.UCAR`) integrations.
- **No `androidx.car.app.CarAppService` or `androidx.car.app.category.NAVIGATION` was found in its manifest.** Android Auto compatibility is **not established** by this APK.
- Reviewed only metadata and resource names. No proprietary code, images, icons, map data, or other internal app assets were copied into NaviTH.

## Implemented in NaviTH

The Android app continues using its original AMAP Native Maps/Search/Location classes. For real navigation, use GitHub Actions **Build NaviTH with AMAP Navigation (experimental)**, which replaces the original JAR with verified Navigation+Maps SDK.

1. Android Auto app is an actual `CarAppService` and uses the host-owned Surface via `VirtualDisplay` + `MapView`. The same saved destination and route are shown on the car, not a screen-mirroring hack.
2. Search with the host-safe search template, favorites, recent places, and optional external MCP fallback.
3. Simplified map UI, traffic/layers and user-selectable 2D/3D.
4. GPS follow on car screen during navigation, disabled when the driver pans/zooms, reenabled by Locate button. Full route isn't redrawn for every GPS fix.
5. AMap Navigation component's *real* `AMapNaviListener.onStartNavi`, `onNaviInfoUpdate`, `onArriveDestination` events are observed inside the phone app and published via `NaviGuidanceStore`. When valid and fresh (15 seconds max), Android Auto shows a Navigation template containing the next road and distance from actual `NaviInfo`; old or unavailable data is never rendered as real guidance.
6. `RoutingInfo` currently uses `Maneuver.TYPE_UNKNOWN` because no validated conversion from vendor turn-icon codes to Android Auto maneuver types exists yet. Lane imagery, junction 3D views, directions and voice on the car must be validated on real Android Auto.

## Important limitations

- Success in GitHub Actions confirms compilation, **not** Android Auto startup, renderer compatibility, guided routing in Thailand, GPS accuracy, or app approval.
- The AMap APK's Huawei HiCar/UCar support is not Google Android Auto. NaviTH does not use or modify those proprietary integrations.
- Google Android Auto requires an Android for Cars host, correct category/permissions, and a permitted installation/distribution channel. A sideloaded debug APK may not appear on an ordinary Android Auto launcher, even if it compiles.
- AMAP Global Navigation SDK must have valid permissions for the app's Android signing certificate SHA-1 and `com.navith.amap`. Coverage, voice and lane information in Thailand must be verified.
- Phone Navigation SDK component and car map do not share a view; they are separate surfaces that consume the same destination and real guidance events. Rendering and smoothness must be tested on DHU.
- Native SDK live road guidance does not make the current app a production-certified road navigation system. Do not rely on this preview while driving.

## Next functional verification

1. Build Navigation Test APK and install on a test Android phone.
2. Grant location permission, accept AMAP Maps and Navigation consents, select destination and start navigation in AMap component.
3. Connect Android Auto via developer desktop head unit or authorized test host. Verify it shows NaviTH Maps, route and live guidance, only while `onNaviInfoUpdate` is received.
4. Test map following, pan mode, 2D/3D, day/night, route clearance and guidance expiry.
5. Check Logcat `NaviTH/Navigation` errors when the vendor singleton/listener is unavailable.
6. Independently verify traffic, maneuvers/lane handling, Thai voice, offline data and road coverage before production/distribution.

Docs:
- https://developer.android.com/training/cars/apps/navigation
- https://developer.android.com/training/cars/apps/library/draw-maps
- https://mapsplatform.opnavi.com/api/navigation-sdk-for-android/guide/navigation-map/navi-info
