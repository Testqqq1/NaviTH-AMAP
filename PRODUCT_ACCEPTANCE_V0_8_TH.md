# NaviTH v0.8 — Product UX Acceptance / เกณฑ์ตรวจรับ

**สถานะ:** Candidate for internal UX test only. **Not Production approved.**  
**Target:** NaviTH Maps v0.8.0 UX Redesign Preview, Android Native app & Android Auto.

## Product principle
แผนที่คือหน้าจอหลัก 3 ระดับ: ด้านบนค้นหา, ตรงกลางแผนที่, ด้านล่างเริ่มเดินทาง  
AMAP SDK, RouteSearch, Navigation Test SDK และ Android Auto AppService ไม่ได้ถูกเปลี่ยน/ถอดถอน

## UX changes
- Search Bar 56dp, voice search requires a separate user tap and consent; result overlay uses existing asynchronous search, saved recent/favorites
- Two floating map controls: GPS and Layers
- Home/Work/Recent journey panel: collapsed by default, tap/swipe to expand, saved destinations in app-private local storage
- GPS chip hidden when accuracy is good and not navigating; no permanent 0 km/h banner
- Route sheet: destination + summary first, optional alternatives/ETA/avoidance in expanded section
- Navigation Test APK displays a navigation action **only after route preview exists**, not in Maps-only APK
- Settings root now has 5 navigable groups: Map, Navigation, Offline, Privacy, Advanced
- AMAP MCP and Map Style moved to Advanced; avoid showing developer settings at root
- Visual tokens: Primary #087F77, Accent #DDF3F0, Background #F7F9FA, Text #172B32, Border #B8CDD0
- Touch target: 48dp minimum; Thai/English UI strings are localized where implemented; System sans-serif Thai fallback
- Android Auto simplified to three primary actions with host templates and protected GPS guidance

## Acceptance tests on an Android test device
- Fresh install, privacy dialog, deny/retry, permission denied/retry, rotate, background/return
- Tap Home and Work unset → search with clear instruction → select a place → save shortcut in expanded route sheet → reopen via shortcut
- Swipe or tap journey sheet; verify card never covers search
- Search Thai/English, slow/no internet, zero POIs, geocode fallback, retry, recent/favorites
- Voice search: no provider / cancel / success / privacy explanation
- Select location, compute drive/walk, failed route, alternatives, arrival estimate, clear/reset
- Maps-only build **must not show Start navigation**
- Navigation Test build **must only show Start navigation after valid drive preview**, and must request consent before vendor SDK activation
- Check Settings back navigation, color contrast in light/dark, font scaling 1.5x, screen reader semantics
- Android Auto DHU: Search, Locate, Map gesture, destination sync, 2D/3D and actual guidance when SDK sends fresh NaviInfo
- Regression: GPS fallback, privacy, offline map availability handling, AMAP API Key/SHA-1, data persistence

## Production blockers (do not advertise as production features)
- Android Auto Car App Library host acceptance and installation channel not validated on real car
- Turn-by-turn Thailand coverage, lane data, voice, and safety warnings not validated on real device
- Map label saturation, per-category POI visibility and zoom-specific labels require an AMAP Console Custom Map Style; native showMapText is all-or-nothing
- Smart suggestions depend on AMAP POI data, not guaranteed worldwide; voice depends on an installed speech provider
- Offline map download is distinct from offline navigation and depends on AMAP account entitlement
- Accessibility tests (TalkBack, font scale, contrast measured on all screens) and real Android Auto host UX remain outstanding

## Builds
- `Build NaviTH Android APK`: maps-only, route preview.
- `Build NaviTH with AMAP Navigation (experimental)`: vendor SDK combined build.
- Public demo-signing JKS is for private testing only. Never submit demo-signed app as production.
