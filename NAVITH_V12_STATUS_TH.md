# NaviTH Maps v1.2 — Implementation & release gate
Date: 2026-11-05
Branch: `feat/navith-v12-navigation-foundation` based on `feat/mapbox-thailand-routing`

## Delivered and compile-tested only when CI shows green
- Vehicle profile selector now **persists** car / EV / truck / motorcycle / bus.
  The profiles unsupported by configured routing providers are clearly labeled; they
  cannot quietly use a regular driving route. Walking and cycling still use
  their own route mode and Mapbox availability checks.
- Dedicated foreground-only **experimental** Active Navigation chrome:
  instruction banner, distance, approximate duration and distance, recenter,
  explicit stop. A UI state machine separates preview / start / guidance /
  rerouting / arrival / error.
- Unify Home quick actions and tabs with NaviTH-owned Android vector drawables,
  no emoji graphics. Minimal blue arrow becomes marker default for new installs.
- Compact Home sheet by default; expand shortcuts and secondary actions only
  after explicit interaction; landscape stays compact.
- Fix Settings safe area on Android 15+ by handling system bar insets.
- Session-only bounded AMAP POI cache (20 queries), quicker debounce (260 ms)
  and stale-query dismissal already present.
- Throttle test guidance text updates, avoid whole-route camera zoom during reroute.
- Privacy-conscious on-device diagnostics for Mapbox request latency,
  success/failure counters, reroute counts and GPS accuracy. No coordinates,
  searches or access tokens stored in diagnostic preferences.
- Add `tools/check_v12_navigation.py` to the Android CI contract checks.

## Explicitly NOT complete / production-ready
- Patch 03: **Full Mapbox Navigation SDK v3** with official matched route progress.
  The app still uses an explicitly labeled Mapbox Directions + Android GPS/TTS
  TEST ONLY, never certified turn-by-turn driving guidance.
- Patch 04: official Mapbox map-matching and high-accuracy fusion (not connected).
- Patch 07: natural assistant-quality voice / ducking and locale fallback require
  official SDK voice integration, audio testing and licensing review.
- Patch 08: background guidance and foreground service not yet implemented.
  The prototype intentionally stops when the app goes to background.
- Patch 09: Android Auto active navigation requires official SDK and car tests;
  current Car App screen remains route-preview only.
- Patch 10: offline turn-by-turn requires license-compliant region data,
  downloads/cache controls and provider support; existing AMAP offline city
  view is not a guarantee of offline Thailand routing.
- Patch 11/12: UI/route diagnostic foundation exists but device benchmark,
  Thai roads, failure-mode and Android Auto/DHU acceptance tests are outstanding.
- EV charging-aware route, truck dimensions, bus restrictions, motorcycle routing,
  transit and ride-hailing are NOT enabled without a suitable provider.

## Safely enabling future real Navigation engine
1. Choose a Mapbox Navigation SDK version documented for the target Android
   compile/target SDK. Public `pk...` token is used at runtime.
2. Put Mapbox Maven download secret with `DOWNLOADS:READ` only into build
   secrets (`MAPBOX_DOWNLOADS_TOKEN`); never ship it in an APK.
3. Add Mapbox Maven artifact integration to a **separate feature build**,
   compile and run the official Navigation API on Android before any UI claims.
4. Replace hand-written progress/GPS/voice prototype with SDK
   RouteProgressObserver, location/map matching, rerouting and voice.
5. Use exactly one Navigation Session for phone and Android Auto.
6. Check current terms for combining AMAP rendered tiles with Mapbox Directions
   geometry, attribution and usage billing before commercial deployment.
7. Stage GPS simulation, permission revocation, stale location, tunnel/overpass,
   low battery and orientation tests; follow with safe passenger validation.

## Validation
CI step `Verify v1.2 navigation foundation` + Gradle assembleDebug.
**Green Build != on-road acceptance.** No live location, token or customer data
is needed for contract tests.

Source docs:
- https://docs.mapbox.com/android/navigation/guides/install/
- https://docs.mapbox.com/android/navigation/releases/
- https://docs.mapbox.com/api/navigation/directions/
