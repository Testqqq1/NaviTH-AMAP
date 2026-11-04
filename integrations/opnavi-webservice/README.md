# NaviTH × AMAP Global Web Services (server-side integration)

**Do not use the pasted `mapsplatform.opnavi.com/v1/*` paths:** that host serves developer documentation, not the verified REST endpoints below. For AMAP Global (overseas), use the official Singapore service hostname `https://sg-restapi.opnavi.com`.

## Verified endpoints

| Capability | Official HTTPS endpoint | Request |
|---|---|---|
| Geocode | /v3/geocode/geo | GET |
| Reverse geocode | /v3/geocode/regeo | GET |
| Autocomplete | /v3/assistant/inputtips | GET |
| Place text search | /v3/place/text | GET |
| Place nearby | /v3/place/around | GET |
| Driving route | /v3/direction/driving | GET |
| Route matrix | /v5/distance/matrix | POST |
| Time zone | /v5/timezone | GET |

**Coordinates:** All URL/query coordinates are **longitude,latitude** (WGS84 for overseas). This reverses the order of AMAP Java `LatLng(latitude, longitude)`. The code converts explicitly.

**Key and entitlements:** Dedicated **Web Service API key with overseas permission** required to make live requests. AMAP's docs require requesting overseas authorization through support. Never expose a Web Service secret in an Android APK or JavaScript browser page. Store `OPNAVI_WEB_SERVICE_KEY` on a private backend, not in Git, Gradle, Manifest or .env committed to source. Use `NAVITH_CITY_ADCODE` for overseas text and autocomplete (provider citycode/adcode is not the city name). There are no real network calls in CI.

## Test without network/keys

```bash
node --test integrations/opnavi-webservice/client.test.mjs
```

Set `NAVITH_SEARCH_PROVIDER=opnavi`, `OPNAVI_WEB_SERVICE_KEY`, `NAVITH_CITY_ADCODE` and `NAVITH_BRIDGE_TOKEN` to serve the existing Android bridge endpoints `/v1/geocode` and `/v1/places` using verified Opnavi Global. Otherwise the existing `mcp` provider is unchanged. Configure the app's existing **opt-in** HTTPS fallback using Settings → Advanced → AMAP MCP. No traffic is sent until user configures and enables the bridge. Place suggestions may not have valid coordinates and will not be treated as routable destinations until resolved.

The bridge also exposes optional authenticated service endpoints `/v1/autocomplete`, `/v1/nearby`, `/v1/directions`, `/v1/matrix`, `/v1/reverse`, `/v1/timezone` when the `opnavi` provider is selected. These endpoints are for a future app UI/backend and **are not yet called by NaviTH Android**. A TLS reverse proxy and explicit consent are required before network deployment.

## Services intentionally not copied from the examples

- **Dynamic Maps / web:** Official script is `https://sg-webapi.opnavi.com/maps?v=2.0&key=...` and constructor `new AMap.Map(...)`. See `web-map.example.html`. It is **not** `google.maps.Map`. Browser map requires a separate JS key and securityJsCode, never the REST key.
- **Geolocation:** Official endpoint is `http://sg-apilocate.opnavi.com/position` using Wi-Fi/cell network identifiers. It is not the guessed JSON POST endpoint. Do **not** transmit IMEI, BSSID, MAC or cellular identifiers to reproduce the example. NaviTH already uses Android native Location SDK / GPS fallback. Keep that.
- **Mobile navigation:** The example `NavigationOptions.builder()/MapNavigation.startNavigation` is not the documented AMAP Android API. NaviTH integrates the vendor `AmapNaviPage` and `AMapNaviListener` through its separate Navigation Test build.
- **Tracking / Two-Wheeler:** An arbitrary `wss://mapsplatform.opnavi.com/v1/tracking` is not documented as a supported service. No websocket launched or vehicle tracking enabled.

## Official documents (verified 2026-11-04)

- https://mapsplatform.opnavi.com/api/web-service/guide/georegeo
- https://mapsplatform.opnavi.com/api/web-service/guide/autocomplete
- https://mapsplatform.opnavi.com/api/web-service/guide/searchs
- https://mapsplatform.opnavi.com/api/web-service/guide/routes
- https://mapsplatform.opnavi.com/api/web-service/guide/geolocation
- https://mapsplatform.opnavi.com/api/web-service/guide/timezone
- https://mapsplatform.opnavi.com/api/maps-javascript-api/add-a-map
- https://mapsplatform.opnavi.com/api/navigation-sdk-for-android/guide/navi-component/advanced-functions
