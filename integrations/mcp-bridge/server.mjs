import { createServer } from 'node:http';
import { timingSafeEqual } from 'node:crypto';
import { McpStdioClient } from './rpc.mjs';
import { OpnaviWebService } from '../opnavi-webservice/client.mjs';

const env = process.env;
const auth = env.NAVITH_BRIDGE_TOKEN || '';
const provider = (env.NAVITH_SEARCH_PROVIDER || 'mcp').toLowerCase();
if (!['mcp','opnavi'].includes(provider)) {
  console.error('NAVITH_SEARCH_PROVIDER must be mcp or opnavi'); process.exit(2);
}
const key = provider==='opnavi' ? env.OPNAVI_WEB_SERVICE_KEY : env.AMAP_MAPS_API_KEY;
if (auth.length < 24 || !key) {
  console.error('Set NAVITH_BRIDGE_TOKEN (24+ characters) and the provider Web Service key as environment secrets');
  process.exit(2);
}
let mcp = null, opnavi = null;
if (provider==='mcp') {
  const root = new URL('../../', import.meta.url).pathname;
  mcp = new McpStdioClient({ key, root });
  try { await mcp.connect(); } catch {
    console.error('Cannot start pinned AMap MCP source; install its Node dependencies first');
    mcp.close(); process.exit(2);
  }
} else {
  if (!/^\d{3,12}$/.test(env.NAVITH_CITY_ADCODE || '')) {
    console.error('Set overseas AMAP NAVITH_CITY_ADCODE (numeric city/adcode) before starting the REST bridge');
    process.exit(2);
  }
  opnavi = new OpnaviWebService({key});
}
const host = env.NAVITH_BRIDGE_HOST || '127.0.0.1';
if (host !== '127.0.0.1' && host !== '::1' && env.NAVITH_ALLOW_REMOTE !== '1') {
  console.error('Remote bind requires NAVITH_ALLOW_REMOTE=1 and a TLS reverse proxy');
  process.exit(2);
}
const port = Number(env.NAVITH_BRIDGE_PORT || 3088);
const limits = new Map();
function authorized(req) {
  const supplied = (req.headers.authorization || '').replace(/^Bearer /i, '');
  const a = Buffer.from(supplied), b = Buffer.from(auth);
  return a.length === b.length && timingSafeEqual(a, b);
}
function reply(res, code, payload) {
  const data = JSON.stringify(payload);
  res.writeHead(code, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store', 'x-content-type-options': 'nosniff'
  });
  res.end(data);
}
async function bodyOf(req) {
  let data = '';
  for await (const chunk of req) {
    data += chunk;
    if (data.length > 8192) throw new Error('Request too large');
  }
  const obj = JSON.parse(data);
  if (!obj || Array.isArray(obj) || typeof obj !== 'object') throw new Error('Invalid request');
  return obj;
}
function locationPoint(raw, name, address) {
  if (typeof raw !== 'string') return null;
  const parts = raw.split(',');
  if (parts.length !== 2) return null;
  const [lon, lat] = parts.map(Number);
  if (!Number.isFinite(lon) || !Number.isFinite(lat) ||
      Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
  return { name: String(name || address || '').slice(0, 120),
    address: String(address || '').slice(0, 240), latitude: lat, longitude: lon };
}
async function places(query, city = '') {
  const result = await mcp.call('maps_text_search', { keywords: query, city });
  const found = (Array.isArray(result.pois) ? result.pois : []).slice(0, 5);
  const points = [];
  // The upstream text search omits POI coordinates; detail calls are required.
  for (const poi of found) {
    if (!poi.id || typeof poi.id !== 'string') continue;
    try {
      const detail = await mcp.call('maps_search_detail', { id: poi.id });
      const point = locationPoint(detail.location, detail.name || poi.name, detail.address || poi.address);
      if (point) points.push(point);
    } catch { /* one missing POI must not discard the rest */ }
  }
  return points;
}
async function geocode(query) {
  const result = await mcp.call('maps_geo', { address: query });
  const list = Array.isArray(result.return) ? result.return : [];
  return list.slice(0, 8).map(x => locationPoint(x.location,
    [x.province,x.city,x.district,x.street,x.number].filter(v=>typeof v==='string').join(' ') || query,
    [x.province,x.city,x.district,x.street,x.number].filter(v=>typeof v==='string').join(' ')))
    .filter(Boolean);
}
const server = createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') return reply(res, 200, {
    status: 'ready', protocol: provider==='mcp'?'MCP-STDIO':'OPNAVI-REST',
    tools: mcp?.tools?.length || 0
  });
  if (!authorized(req)) return reply(res, 401, { error: 'unauthorized' });
  const ip = req.socket.remoteAddress || 'unknown', now = Date.now();
  const counter = limits.get(ip) || { n: 0, start: now };
  if (now - counter.start > 60000) { counter.start = now; counter.n = 0; }
  if (++counter.n > 24) return reply(res, 429, { error: 'rate_limited' });
  limits.set(ip, counter);
  const mcpPaths = ['/v1/places','/v1/geocode'];
  const restPaths = [...mcpPaths,'/v1/autocomplete','/v1/nearby',
    '/v1/directions','/v1/matrix','/v1/reverse','/v1/timezone'];
  if (req.method!=='POST' || !(provider==='mcp'?mcpPaths:restPaths).includes(req.url))
    return reply(res,404,{error:'not_found'});
  try {
    const body=await bodyOf(req);
    if(provider==='mcp') {
      const query=body.query;
      if(typeof query!=='string' || query.trim().length<2 || query.length>120)
        return reply(res,400,{error:'invalid_query'});
      const city=typeof body.city==='string'?body.city.slice(0,80):'';
      const items=req.url==='/v1/places'?
        await places(query.trim(),city):await geocode(query.trim());
      return reply(res,200,{items,source:'AMap-WebService-MCP',offline:false});
    }
    const city = typeof body.city==='string' && body.city ?
      body.city : env.NAVITH_CITY_ADCODE;
    const query=body.query;
    const useQuery=()=>{
      if(typeof query!=='string'||query.trim().length<2||query.length>120)
        throw new TypeError('Invalid query');
      return query.trim();
    };
    switch(req.url) {
      case '/v1/geocode':
        return reply(res,200,{items:await opnavi.geocode({address:useQuery(),city}),
          source:'AMAP-Global-WebService',offline:false});
      case '/v1/places':
        return reply(res,200,{items:await opnavi.places({query:useQuery(),city}),
          source:'AMAP-Global-WebService',offline:false});
      case '/v1/autocomplete':
        return reply(res,200,{suggestions:await opnavi.autocomplete({query:useQuery(),city}),
          source:'AMAP-Global-WebService',offline:false});
      case '/v1/nearby':
        return reply(res,200,{items:await opnavi.nearby({
          query:useQuery(),location:body.location,radius:body.radius}),
          source:'AMAP-Global-WebService',offline:false});
      case '/v1/reverse':
        return reply(res,200,{result:await opnavi.reverse({location:body.location}),
          source:'AMAP-Global-WebService',offline:false});
      case '/v1/directions':
        return reply(res,200,{routes:await opnavi.driving({
          origin:body.origin,destination:body.destination}),
          source:'AMAP-Global-WebService',offline:false});
      case '/v1/matrix':
        return reply(res,200,{matrix:await opnavi.matrix({
          origins:body.origins,destinations:body.destinations}),
          source:'AMAP-Global-WebService',offline:false});
      case '/v1/timezone':
        return reply(res,200,{timezone:await opnavi.timeZone({
          location:body.location,timestamp:body.timestamp}),
          source:'AMAP-Global-WebService',offline:false});
    }
  } catch (error) {
    if(error instanceof TypeError || error instanceof RangeError)
      return reply(res,400,{error:'invalid_request'});
    return reply(res,502,{error:'upstream_unavailable_or_unsupported'});
  }
});
server.requestTimeout = 30000;
server.headersTimeout = 10000;
server.listen(port, host, () => console.log('NaviTH MCP bridge listening on loopback port ' + port));
for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => {
  server.close(); mcp?.close();
});
