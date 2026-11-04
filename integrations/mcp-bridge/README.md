# NaviTH Web Service → MCP bridge

Connects the **pinned** `masx200/amap-maps-mcp-server` source to NaviTH through MCP STDIO, with an optional authenticated HTTPS endpoint for an Android device.

## Install on a computer/server with Node.js 20+

```bash
git submodule update --init --recursive third_party/mcp/amap-maps-mcp-server
cd third_party/mcp/amap-maps-mcp-server
npm install --omit=dev --ignore-scripts
cd ../../..
node integrations/mcp-bridge/smoke.mjs
```

The smoke test only does MCP initialize + tools/list using a dummy key; it **never calls live AMAP Web Services**.

## Launch

Set these **secrets in the process environment** (not in Git):

- `AMAP_MAPS_API_KEY`: dedicated AMap **Web Service** key (not the Android signing/SHA-1 key)
- `NAVITH_BRIDGE_TOKEN`: random token of 24+ characters for the Android app
- `NAVITH_BRIDGE_PORT` optional (default `3088`)

```bash
node integrations/mcp-bridge/server.mjs
```

The bridge binds to `127.0.0.1` by default. Put an HTTPS reverse proxy with TLS, access control, and rate limits in front before allowing phone access. To bind remotely you must additionally set `NAVITH_ALLOW_REMOTE=1`; this is **not recommended without TLS and firewalling**. Never expose the upstream `build/streamable-http.js` directly to the Internet: upstream allows unauthenticated access if misconfigured and logs HTTP auth tokens.

Android Settings → External MCP → enter the **HTTPS bridge base URL** and access token, opt in to network fallback. It will be used only when AMAP Native Search/Geocode fails. No location or query is sent to the bridge unless it has been configured/enabled; all requests use HTTPS. Server response format:

```json
{"items":[{"name":"Example","address":"Example","latitude":13.75,"longitude":100.5}],"source":"AMap-WebService-MCP"}
```

Endpoints: `GET /health`, `POST /v1/places`, `POST /v1/geocode`. They are bridged using the actual MCP `tools/call` protocol, **not** by bypassing the MCP server. The POI search API omits coordinates, so the bridge resolves up to five POI IDs using additional MCP detail calls (consumes quota). If no result or service coverage outside China, it may return an empty list.

Known limits: WebService may have different geographic coverage than Native AMAP Global; this is a **server required** feature, not embedded in the APK. Routes, live guidance, offline navigation, and Android Auto are unaffected. App-private token storage is suitable for personal/test use, not a public multiuser release.
