# NaviTH Android Auto + MCP Integration / สถานะเชื่อมต่อ

Update: 2026-11-04. Source: `Testqqq1/NaviTH-AMAP`.

## What runs in Android Auto

The **same Android APK** has a CarAppService for Android Auto projection (phone connects to the head unit). The app already shares destination, preview route, GPS source and map preferences between phone and car via `TripStore`, and draws an AMAP map on the host-owned Surface.

Improvements in this integration:
- Car `SearchTemplate` displays **Recent** / **Favorite** destinations from the phone.
- Car and phone search AMAP Native Search first; the optional user-configured external MCP bridge is called **only after Native search fails/returns no POI**.
- Selecting a result from either screen sends it to `TripStore` so Android Auto can draw a destination/route preview.
- Car map has Search / Locate / 2D↔3D / Clear route (when a destination exists), supports day/night/satellite and 3D settings shared from phone, and tracks device network+GPS for a quicker first location.
- Map markers/route previews are drawn using the AMAP Native SDK, **not a WebView**.
- Still uses host-rendered templates and handles car restrictions; it is not a 1:1 pixel mirror of the phone.

## Real-car installation: Google Play is required for Car App Library

**Important:** A locally sideloaded GitHub debug APK may run on the phone or
Android Auto Desktop Head Unit (DHU), but **Android Auto's Developer Settings →
Unknown sources does NOT exempt apps built with Android for Cars App Library**.
Google's documented path to test on actual vehicle displays is **Google Play
Internal App Sharing** or an **Internal Test Track**. Merely plugging in USB
after sideloading may leave NaviTH absent from the car launcher.

- Source: https://developer.android.com/training/cars/testing
- Play Internal App Sharing accepts *debuggable* APKs and signs them again with
  an **Internal test certificate**, see:
  https://support.google.com/googleplay/android-developer/answer/9844679

### Special AMAP SHA-1 requirement

The AMAP Android Key is restricted by Android package + signing-certificate SHA-1.
Play Internal App Sharing **re-signs the APK with a different certificate**.
If the APK continues to contain the key for the GitHub demo keystore, AMAP
may fail with an invalid signature/key even when the Android Auto UI launches.

1. With an authorized Play Console account, first upload a disposable test APK
   to Internal App Sharing to generate an Internal test certificate.
2. In Play Console → Internal testing → Internal app sharing → Internal test
   certificate, copy the certificate **SHA-1**.
3. In the AMAP Android developer console, create a dedicated Android Key
   for package `com.navith.amap` with this SHA-1 and SDK entitlements.
4. In GitHub repository Actions Secrets, add **`AMAP_KEY_PLAY`** containing
   that Android Key. Do not paste credentials into Git history or issues.
5. GitHub Actions → **Build NaviTH for Play Internal App Sharing** →
   Run workflow. Download `NaviTH-Play-Internal-Sharing-APK`.
   This workflow does **not** upload anything to Google Play automatically.
6. Upload this APK manually to Internal App Sharing, get the Play testing link,
   and install from that Play link on your connected phone.
7. If the debug APK was already installed directly, you may need to uninstall
   it first due to the different signing certificate; this removes local data.

If the user does not have access to Play Console, Android Auto **DHU
testing** is the supported immediate test; real-car discovery is not assured.

## What is NOT completed

- **Turn-by-turn guidance on Android Auto** has not been implemented. `NavSdkBridge` opens the official AMAP Navigation route planner **on the phone only**, in the experimental Navigation APK. It does NOT emit verified maneuvers to Car App `NavigationTemplate` and does not provide Android Auto navigation voice guidance yet.
- The Navigation SDK account's coverage/permissions for Thailand, lane-level images and Thai TTS have not been confirmed on a real phone or head unit.
- Surface rendering uses an AMap MapView attached to a virtual display; this must be validated on Android Auto **Desktop Head Unit (DHU)** and on actual car hardware.
- Do not use this preview as a primary driving navigation system.

## Reference repositories

| Group | Actual integration |
|---|---|
| 9 AMap-Web repos (TypeScript/JavaScript, 3D layers, AI Skills, Map benchmark) | Pinned submodules / developer reference / workspace CI |
| best-flutter/flutter_amap | Pinned historical Flutter reference, **not** an Android dependency |
| masx200/amap-maps-mcp-server | Pinned Node.js MCP server, wired via `integrations/mcp-bridge` to optional HTTPS Android + Android Auto search fallback |
| Official AMAP Native Navigation SDK | Separate experimental Android GitHub Actions workflow; does not reuse Flutter/web libraries |

**No direct runtime coupling** exists between the 9 JavaScript repos and Android Auto; they are different SDK types. Installing all 11 as Gradle dependencies would be unsafe or nonfunctional.

## How to configure OPTIONAL MCP search

1. Create a separate **AMAP Web Service API Key**. The existing `AMAP_KEY` for signed Android APK is NOT a Web Service key.
2. On a Node.js 20+ server, install pinned MCP server dependencies and run `integrations/mcp-bridge/server.mjs`, as described in [integrations/mcp-bridge/README.md](integrations/mcp-bridge/README.md).
3. Put HTTPS with valid TLS in front of the localhost bridge and provision a random `NAVITH_BRIDGE_TOKEN`. Do **not** publicly expose upstream HTTP server.
4. On phone: **NaviTH → Settings → External search service → Connect AMAP MCP**. Enter HTTPS base URL and access token, opt in.
5. If Native AMAP POI/geocoding returns no results and the optional service is available, NaviTH shows MCP results on both the phone and Android Auto. Results must include valid coordinates; missing/unsupported regional data produces no result.

No remote server or Web Service credential can be provisioned automatically by just adding a Git Submodule, and GitHub Actions cannot host a persistent server. If the user has not set up a bridge, the app continues to use AMAP Native SDK.

## Verification

- `Build NaviTH Android APK`: compile/test the Maps-only Android Auto preview APK.
- `Build NaviTH with AMAP Navigation (experimental)`: compile the official combined Navigation SDK; guided navigation remains **phone only**.
- `Test NaviTH MCP bridge (no live API calls)`: verify STDIO handshake, tool list, local HTTP health and token enforcement with a dummy key; **no live AMAP quota spent**.
- `Prepare NaviTH map reference workspace`: verify and snapshot all 11 source repos.

### Android Auto device tests to do

1. Install APK on phone with a matching debug signing key; grant Precise Location and accept the AMAP privacy prompt.
2. For DHU, install the GitHub APK on the phone and connect to the desktop head unit. For a real vehicle, install from a Google Play Internal App Sharing / Internal Testing link; Unknown sources does not grant Car App Library sideloaded apps trusted installation status.
3. Select a destination on the phone, inspect whether car map shows that destination and a *preview* route.
4. On car, open Search, verify recent/favorites and normal search; test location, 2D/3D, day/night and scaling.
5. Only if you deployed HTTPS MCP: deliberately choose an AMAP Native query with no results, and verify the secondary results or explicit error.
6. Disconnect / reconnect, confirm no leaked sessions, app crash or frozen Surface.
