/**
 * AMAP Global (Opnavi) Web Service client. SERVER-SIDE ONLY.
 * Official endpoint host: sg-restapi.opnavi.com (not mapsplatform.opnavi.com/v1).
 * All geographic input to these REST endpoints uses longitude,latitude (WGS84 overseas).
 * Live requests require a separately authorized overseas Web Service API key.
 */
export const BASE = 'https://sg-restapi.opnavi.com';
const ALLOWED = new Set([
  '/v3/geocode/geo','/v3/geocode/regeo','/v3/assistant/inputtips',
  '/v3/place/text','/v3/place/around','/v3/direction/driving',
  '/v5/distance/matrix','/v5/timezone'
]);
export function coords(input) {
  if (!input || typeof input !== 'object') throw new TypeError('Coordinate object required');
  const latitude = Number(input.latitude), longitude = Number(input.longitude);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) ||
      Math.abs(latitude) > 90 || Math.abs(longitude) > 180)
    throw new RangeError('Invalid WGS84 coordinates');
  return longitude.toFixed(6)+','+latitude.toFixed(6);
}
function text(value,name,max=180) {
  if(typeof value!=='string' || !value.trim() || value.length>max)
    throw new TypeError('Invalid '+name);
  return value.trim();
}
function adminCode(value) {
  // Required for overseas address geocode and autocomplete.
  const v=text(value,'city/adcode',32);
  if(!/^\d{3,12}$/.test(v)) throw new TypeError('Expected numeric AMAP citycode/adcode');
  return v;
}
export function parsePoint(loc) {
  if(typeof loc!=='string')return null;
  const list=loc.split(',');
  if(list.length!==2)return null;
  const lon=Number(list[0]),lat=Number(list[1]);
  if(!Number.isFinite(lat)||!Number.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return null;
  return {latitude:lat,longitude:lon};
}
function resultPoint(row) {
  const xy=parsePoint(row?.location);
  if(!xy)return null;
  const name=String(row.name||row.formatted_address||row.address||'').slice(0,160);
  const address=String(row.formatted_address||row.address||'').slice(0,250);
  return {...xy,name:name||address||'Unknown place',address,id:String(row.id||'').slice(0,80)};
}
function asArray(value) {return Array.isArray(value)?value:value?[value]:[];}
export class OpnaviWebService {
  constructor({key=process.env.OPNAVI_WEB_SERVICE_KEY,fetchImpl=fetch,timeoutMs=14000}={}) {
    this.key=text(key,'OPNAVI_WEB_SERVICE_KEY',240);
    this.fetchImpl=fetchImpl;
    this.timeoutMs=timeoutMs;
  }
  async request(path,params={},method='GET') {
    if(!ALLOWED.has(path))throw new TypeError('Unsupported Opnavi endpoint');
    const url=new URL(path,BASE);
    for(const [k,v] of Object.entries({...params,key:this.key}))
      if(v!==undefined && v!==null)url.searchParams.set(k,String(v));
    const controller=new AbortController();
    const timeout=setTimeout(()=>controller.abort(),this.timeoutMs);
    try {
      const response=await this.fetchImpl(url.toString(),{
        method,signal:controller.signal,redirect:'error',
        headers:{Accept:'application/json'}
      });
      if(!response?.ok)throw new Error('Opnavi service unavailable');
      const raw=await response.text();
      if(raw.length>1_000_000)throw new Error('Opnavi response too large');
      const data=JSON.parse(raw);
      if(String(data?.status)!=='1')
        throw new Error('Opnavi request rejected or outside entitled area');
      return data;
    } finally {clearTimeout(timeout);}
  }
  async geocode({address,city,langCode='th'}) {
    const data=await this.request('/v3/geocode/geo',{
      address:text(address,'address'),city:adminCode(city),langCode
    });
    return asArray(data.geocodes).map(resultPoint).filter(Boolean).slice(0,8);
  }
  async reverse({location,langCode='th'}) {
    const data=await this.request('/v3/geocode/regeo',{
      location:coords(location),langCode,extensions:'base'
    });
    return {address:String(data.regeocode?.formatted_address||'').slice(0,500),
      ...location};
  }
  async autocomplete({query,city,langCode='th'}) {
    const data=await this.request('/v3/assistant/inputtips',{
      keywords:text(query,'query',120),city:adminCode(city),langCode
    });
    return asArray(data.tips).slice(0,10).map(row=>({
      name:String(row.name||'').slice(0,160),id:String(row.id||'').slice(0,80),
      district:String(row.district||'').slice(0,140),
      location:parsePoint(row.location)
    })).filter(row=>row.name);
  }
  async places({query,city,langCode='th',limit=10}) {
    const data=await this.request('/v3/place/text',{
      keywords:text(query,'query',120),city:adminCode(city),
      langCode,offset:Math.max(1,Math.min(20,Math.round(limit)))
    });
    return asArray(data.pois).map(resultPoint).filter(Boolean).slice(0,20);
  }
  async nearby({query,location,radius=3000,langCode='th'}) {
    const data=await this.request('/v3/place/around',{
      keywords:text(query,'query',120),location:coords(location),
      radius:Math.max(100,Math.min(50000,Math.round(radius))),langCode,offset:10
    });
    return asArray(data.pois).map(resultPoint).filter(Boolean).slice(0,10);
  }
  async driving({origin,destination}) {
    const data=await this.request('/v3/direction/driving',{
      origin:coords(origin),destination:coords(destination),extensions:'base'
    });
    return asArray(data.route?.paths).slice(0,4).map(path=>({
      meters:Number(path.distance)||0,seconds:Number(path.duration)||0,
      steps:asArray(path.steps).slice(0,150)
        .map(step=>({instruction:String(step.instruction||'').slice(0,300)}))
    }));
  }
  async matrix({origins,destinations}) {
    if(!Array.isArray(origins)||!Array.isArray(destinations)||
      origins.length===0||destinations.length===0||
      origins.length>10||destinations.length>10)
      throw new RangeError('Matrix supports 1–10 points per dimension in NaviTH');
    // Official documentation specifies POST with URL-encoded query parameters.
    const data=await this.request('/v5/distance/matrix',{
      origins:origins.map(coords).join(';'),destinations:destinations.map(coords).join(';')
    },'POST');
    return {routes:data.routes||[],successElements:data.successElements||0,
      failedElements:data.failedElements||0};
  }
  async timeZone({location,timestamp=Math.floor(Date.now()/1000)}) {
    if(!Number.isInteger(timestamp)||timestamp<0)throw new TypeError('Unix seconds required');
    const data=await this.request('/v5/timezone',{
      location:coords(location),time_type:1,time:timestamp
    });
    return {id:data.time_zone_id||'',rawOffset:Number(data.rawoffset)||0,
      dstOffset:Number(data.dstoffset)||0,localTime:data.time||''};
  }
}
