import {createDecipheriv, createHmac, timingSafeEqual} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {gunzipSync} from 'node:zlib';
import {extname} from 'node:path';
import {WebSocketServer} from 'ws';
import {Broker, maxFrameBytes} from './shorepact-broker.js';

const root='/shorepact/play/', day=86400;
const fly='prismet-site-restless-horizon-217.fly.dev';
const webOrigins=new Set(['https://prismet.xyz','https://www.prismet.xyz','https://'+fly]);
const native=new Set(['shorepact://bundle','https://appassets.androidplatform.net']);
const discordID=process.env.SHOREPACT_DISCORD_APP_ID;
if (/^\d{15,22}$/.test(discordID||'')) webOrigins.add('https://'+discordID+'.discordsays.com');
const key=Buffer.from(process.env.SHOREPACT_SITE_KEY||'','base64');
const publicPlay=process.env.SHOREPACT_PUBLIC_PLAY==='1';
const mac=(s)=>createHmac('sha256',key).update(s).digest('base64url');
const equal=(a,b)=>{const x=Buffer.from(a),y=Buffer.from(b);return x.length===y.length&&timingSafeEqual(x,y)};
function hasPass(req){
  if(publicPlay)return true;
  if(key.length!==32)return false;
  const token=/(?:^|;\s*)__Secure-shorepact=([0-9]+)\.([A-Za-z0-9_-]{43})(?:;|$)/.exec(req.headers.cookie||'');
  return !!token && Number(token[1])>Date.now()/1000 && equal(token[2],mac('session:'+token[1]));
}
const csp="default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' wss://prismet.xyz wss://www.prismet.xyz wss://"+fly+" wss://*.discordsays.com; frame-ancestors 'self' https://discord.com https://*.discord.com https://*.discordapp.com; object-src 'none'; form-action 'self'; base-uri 'self'";
const headers={'Content-Security-Policy':csp,'Referrer-Policy':'same-origin','X-Content-Type-Options':'nosniff','Strict-Transport-Security':'max-age=31536000','Cache-Control':'private, no-store','Vary':'Cookie, Accept-Encoding'};
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.wasm':'application/wasm','.webp':'image/webp','.json':'application/json','.txt':'text/plain; charset=utf-8','.svg':'image/svg+xml'};
let bundle;
function readPackage(){
  if(bundle)return bundle;
  const blob=readFileSync(new URL('./data/shorepact.pack',import.meta.url));
  const dc=createDecipheriv('aes-256-gcm',key,blob.subarray(0,12));dc.setAuthTag(blob.subarray(12,28));
  const body=Buffer.concat([dc.update(blob.subarray(28)),dc.final()]), boundary=4+body.readUInt32BE(0);
  const files=new Map(JSON.parse(body.toString('utf8',4,boundary)).map(([p,o,n,z])=>[p,[body.subarray(boundary+o,boundary+o+n),z]]));
  return bundle=files;
}
const gate=(invalid=false)=>`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Shorepact · Playtest</title><style>*{box-sizing:border-box}body{margin:0;background:#182e32;color:#fff1d5;font:18px/1.55 Georgia,serif;min-height:100svh;display:grid;place-items:center;padding:1.5rem}main{max-width:29rem;border:1px solid #a88f63;background:#1c393c;padding:clamp(1.6rem,7vw,3rem)}small{color:#f1ca88;letter-spacing:.19em}h1{font-size:clamp(2.5rem,8vw,4rem);line-height:1.05;margin:.55em 0}input,button{font:inherit;padding:.65em;width:100%;margin:.3em 0 .7em}button{background:#efc885;color:#172f32;border:0;font-weight:bold}a{color:#f1ca88}label{display:block}</style><main><small>SHOREPACT · PLAYTEST</small><h1>A seat at the table.</h1><p>Enter your playtest invitation to open Shorepact. You can create a lobby or find other players once you’re inside.</p>${invalid?'<p role="alert">That invitation wasn’t recognized. Check the pass and try again.</p>':''}<form method="post"><label for="pass">Playtest pass</label><input type="password" name="pass" id="pass" autocomplete="off" maxlength="80" required><button>Enter the playtest</button></form><details><summary>Which code do I need?</summary><p>Ask your playtest organizer for the invitation pass. The separate eight-character lobby code is used inside the game.</p></details></main></html>`;
const limits=new Map();
function addr(req){return (process.env.FLY_APP_NAME&&req.headers['fly-client-ip'])||req.socket.remoteAddress||'unknown'}
function limited(req,action,max){
  const k=action+addr(req),now=Date.now();let b=limits.get(k);
  if(!b||b.until<=now){if(!b&&limits.size>=4096){for(const [a,v] of limits)if(v.until<=now)limits.delete(a);if(limits.size>=4096)return true}limits.set(k,b={n:0,until:now+60000})}
  return ++b.n>max;
}
export async function serveShorepact(req,res,url){
  function send(status,body='',extra={}){res.writeHead(status,{...headers,...extra});res.end(req.method==='HEAD'?'':body)}
  if(limited(req,'asset',600))return send(429,'Too many requests. Try again shortly.',{'Retry-After':'60'});
  if(url.pathname===root.slice(0,-1))return send(308,'',{Location:root+url.search});
  if(url.pathname===root+'socket')return send(426,'WebSocket connection required.',{Upgrade:'websocket'});
  const file=url.pathname.slice(root.length)||'index.html';
  if(req.method==='POST'&&url.pathname===root){
    if(key.length!==32)return send(503,'Playtest temporarily unavailable.');
    if(limited(req,'pass',12))return send(429,'Too many attempts. Try again in a minute.',{'Retry-After':'60'});
    const origin=req.headers.origin;
    if(!webOrigins.has(origin))return send(403,'Invalid origin.');
    if(!(req.headers['content-type']||'').startsWith('application/x-www-form-urlencoded'))return send(415,'Use the playtest form.');
    let data='';try{for await(const chunk of req){if(chunk.length+data.length>512)return send(413,'Request too large.');data+=chunk}}catch{return send(400)}
    if(!equal(new URLSearchParams(data).get('pass')||'',mac('invitation:v1').slice(0,22)))return send(403,gate(true),{'Content-Type':types['.html']});
    const exp=Math.floor(Date.now()/1000)+7*day;
    const cookiePath=origin.endsWith('.discordsays.com')?'/':root;
    return send(303,'',{'Set-Cookie':'__Secure-shorepact='+exp+'.'+mac('session:'+exp)+'; Path='+cookiePath+'; HttpOnly; Secure; SameSite=None; Partitioned; Max-Age='+7*day,Location:'./'+url.search});
  }
  if(!['HEAD','GET'].includes(req.method))return send(405,'',{Allow:'GET, HEAD'});
  if(!hasPass(req)){
    if(file==='index.html')return send(200,gate(),{'Content-Type':types['.html']});
    return send(403,'A playtest invitation is required.');
  }
  if(key.length!==32)return send(503,'Playtest temporarily unavailable.');
  let asset;try{asset=readPackage().get(file)}catch{return send(503,'Playtest temporarily unavailable.')}
  if(!asset)return send(404,'Asset not found.');
  let [buf,compressed]=asset;const extra={'Content-Type':types[extname(file)]||'application/octet-stream'};
  if(compressed){if(/\bgzip\b/.test(req.headers['accept-encoding']||''))extra['Content-Encoding']='gzip';else buf=gunzipSync(buf)}
  extra['Content-Length']=buf.length;return send(200,buf,extra);
}
export function shorepactRooms(){
  const broker=new Broker(),wss=new WebSocketServer({noServer:true,maxPayload:maxFrameBytes,perMessageDeflate:false});
  const active=new Map();
  const timer=setInterval(()=>{
    broker.sweep(); const now=Date.now();
    for (const [id,budget] of limits) if (budget.until<=now) limits.delete(id);
  },15000);timer.unref();
  return {upgrade(req,socket,head){
    const abort=(status,extra='')=>socket.end('HTTP/1.1 '+status+'\r\n'+extra+'Connection: close\r\nContent-Length: 0\r\n\r\n');
    const origin=req.headers.origin;
    if(!native.has(origin)&&!(webOrigins.has(origin)&&hasPass(req)))return abort('403 Forbidden');
    const primary=process.env.SHOREPACT_ROOM_MACHINE;
    if(primary&&process.env.FLY_MACHINE_ID&&process.env.FLY_MACHINE_ID!==primary)return abort('307 Temporary Redirect','fly-replay: instance='+primary+'\r\n');
    const ip=addr(req);
    if(limited(req,'room',120)||(active.get(ip)||0)>=32)return abort('429 Too Many Requests');
    wss.handleUpgrade(req,socket,head,client=>{
      active.set(ip,(active.get(ip)||0)+1);
      const p=broker.attach({send:s=>{if(client.readyState!==1||client.bufferedAmount>2*maxFrameBytes)throw Error('slow peer');client.send(s)},close:(c,r)=>client.close(c,r)});
      client.on('message',(d,b)=>broker.receive(p,b?null:d.toString()));
      client.on('error',()=>broker.detach(p));client.on('close',()=>{broker.detach(p);const n=active.get(ip)-1;if(n)active.set(ip,n);else active.delete(ip)});
    });
  },close(){clearInterval(timer);for(const c of wss.clients)c.terminate();wss.close()}}
}
