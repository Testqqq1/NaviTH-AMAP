# AMap MCP Server — Reference Integration

**Source:** https://github.com/masx200/amap-maps-mcp-server  
**Pinned Git commit:** `027fbc323dbbc373533ffb8ae045363f8098e2b0` (branch `main`).

This repository is included as a pinned **Git submodule** under
`third_party/mcp/amap-maps-mcp-server`. This is a Node.js Model Context Protocol
server for the AMap **Web Service API**, not an Android Java/AMap Navigation SDK.
No JavaScript dependencies are installed by the Android workflow and it is not
part of the NaviTH APK.

## Fetch

From the NaviTH project root:

```bash
git submodule update --init --recursive third_party/mcp/amap-maps-mcp-server
# or all reference projects:
bash tools/install-map-references.sh
```

## Optional MCP client setup (on a secure development computer/server)

The upstream README documents the npm package `@masx200/amap-maps-mcp-server`.
An MCP host supporting STDIO can launch it with:

```json
{
  "mcpServers": {
    "amap-maps": {
      "command": "npx",
      "args": ["-y", "@masx200/amap-maps-mcp-server"],
      "env": {
        "AMAP_MAPS_API_KEY": "YOUR_AMAP_WEB_SERVICE_KEY"
      }
    }
  }
}
```

**Do not commit real API keys.** Prefer your MCP host's environment/secret
injection over a shared JSON config. This requires an **AMAP Web Service key**,
not the Android package/SHA-1-bound AMAP_KEY stored for the Android APK.

The upstream also supports Streamable HTTP (default port 3000) through
`build/streamable-http.js`. If ever deployed, restrict the network listener,
set a strong `HTTP_API_TOKEN`, and add TLS/authentication at the proxy.
No HTTP server, background service or port is started by adding the submodule.

## Why keep it isolated?

- Compatible with MCP hosts/Node.js, not directly with Android Auto.
- Exposes services such as address/geocoding, POIs, routing and distance,
  subject to provider quotas and country coverage.
- It does not add offline navigation, GPS, voice guidance or 3D lane guidance
  to the current NaviTH app.
- Check the upstream LICENSE and package metadata before distributing a
  modified version; the `LICENSE` file references Mulan PSL v2, while
  package.json declares ISC. Do not assume the two are interchangeable.
- Security: audit upstream changes before updating the pinned SHA or using
  this server with live credentials.

## Build verification

`.github/workflows/amap-web-workspace.yml` checks its folder,
`package.json`, `README.md`, and `LICENSE`, and archives it together with
the existing 10 nonempty map reference repositories. It **does not** install
dependencies, execute the MCP server or change the Android app build.
