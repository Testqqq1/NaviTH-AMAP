import test from 'node:test';
import assert from 'node:assert/strict';
import {OpnaviWebService,coords,parsePoint,BASE} from './client.mjs';
const calls=[];
const mock=async(url,opts)=>{
  calls.push({url:new URL(url),method:opts.method});
  const p=new URL(url).pathname;
  const obj=p.endsWith('geo')?{geocodes:[{formatted_address:'Bangkok',location:'100.501800,13.756300'}]}:
    p.includes('inputtips')?{tips:[{name:'Bangkok Cafe',location:'100.501800,13.756300'}]}:
    p.includes('place')?{pois:[{name:'Example',location:'100.501800,13.756300'}]}:
    p.includes('driving')?{route:{paths:[{distance:'1200',duration:'240',steps:[{instruction:'Continue'}]}]}}:
    p.includes('matrix')?{routes:[],successElements:0,failedElements:0}:
    p.includes('timezone')?{time_zone_id:'Asia/Bangkok',rawoffset:25200,dstoffset:0,time:'2026-11-04T23:00:00'}:
    {regeocode:{formatted_address:'Bangkok, Thailand'}};
  return {ok:true,text:async()=>JSON.stringify({status:'1',...obj})};
};
const s=new OpnaviWebService({key:'NOT_A_REAL_KEY',fetchImpl:mock});
test('geo coordinates are longitude,latitude WGS84',()=>{
  assert.equal(coords({latitude:13.7563,longitude:100.5018}),'100.501800,13.756300');
  assert.deepEqual(parsePoint('100.501800,13.756300'),{latitude:13.7563,longitude:100.5018});
  assert.throws(()=>coords({latitude:95,longitude:10}));
});
test('Geocode uses verified host and mandatory city adcode',async()=>{
  const data=await s.geocode({address:'Bangkok, Thailand',city:'764000000'});
  assert.equal(data[0].latitude,13.7563);
  assert.equal(calls.at(-1).url.host,'sg-restapi.opnavi.com');
  assert.equal(calls.at(-1).url.pathname,'/v3/geocode/geo');
  assert.equal(calls.at(-1).url.searchParams.get('city'),'764000000');
  await assert.rejects(s.geocode({address:'Bangkok'}),/city\/adcode/);
});
test('Autocomplete and places use official endpoints',async()=>{
  assert.equal((await s.autocomplete({query:'cafe',city:'764000000'}))[0].name,'Bangkok Cafe');
  assert.equal(calls.at(-1).url.pathname,'/v3/assistant/inputtips');
  assert.equal((await s.places({query:'cafe',city:'764000000'}))[0].longitude,100.5018);
  assert.equal(calls.at(-1).url.pathname,'/v3/place/text');
});
test('Route, matrix, timezone use verified method/path/units',async()=>{
  const origin={latitude:13.7563,longitude:100.5018};
  const destination={latitude:13.7367,longitude:100.5311};
  assert.equal((await s.driving({origin,destination}))[0].meters,1200);
  assert.equal(calls.at(-1).url.pathname,'/v3/direction/driving');
  await s.matrix({origins:[origin],destinations:[destination]});
  assert.equal(calls.at(-1).url.pathname,'/v5/distance/matrix');
  assert.equal(calls.at(-1).method,'POST');
  assert.equal((await s.timeZone({location:origin,timestamp:1793800000})).rawOffset,25200);
  assert.equal(calls.at(-1).url.pathname,'/v5/timezone');
  assert.equal(calls.at(-1).url.searchParams.get('time'),'1793800000');
});
test('Never allow arbitrary remote endpoint and detect provider failure',async()=>{
  assert.equal(BASE,'https://sg-restapi.opnavi.com');
  await assert.rejects(s.request('https://example.com/'),/Unsupported/);
  const bad=new OpnaviWebService({key:'TEST_ONLY',fetchImpl:async()=>({ok:true,text:async()=>JSON.stringify({status:0,info:'forbidden'})})});
  await assert.rejects(bad.places({query:'food',city:'764000000'}),/rejected/);
});
