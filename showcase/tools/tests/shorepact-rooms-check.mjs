import {createServer} from 'node:http';
import {once} from 'node:events';
import {randomBytes,createHmac} from 'node:crypto';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {WebSocket}=createRequire(new URL('../../server/package.json',import.meta.url))('ws');
const key=randomBytes(32);process.env.SHOREPACT_SITE_KEY=key.toString('base64');
delete process.env.SHOREPACT_PUBLIC_PLAY;delete process.env.SHOREPACT_ROOM_MACHINE;
const {serveShorepact,shorepactRooms}=await import('../../server/shorepact.js');
const r=shorepactRooms(),server=createServer((q,s)=>serveShorepact(q,s,new URL(q.url,'http://localhost')));
server.on('upgrade',(q,s,h)=>r.upgrade(q,s,h));server.listen(0,'127.0.0.1');await once(server,'listening');
const base='http://127.0.0.1:'+server.address().port+'/shorepact/play/',origin='https://prismet.xyz';
const ws=(o,c)=>new WebSocket(base.replace('http:','ws:')+'socket',{origin:o,headers:c?{cookie:c}:{}});
const sockets=[];const next=c=>once(c,'message').then(([m])=>JSON.parse(m));
try{
 assert.match(await (await fetch(base)).text(),/Playtest pass/);assert.equal((await fetch(base+'config.js')).status,403);
 const pass=createHmac('sha256',key).update('invitation:v1').digest('base64url').slice(0,22);
 const post=(p)=>fetch(base+'?frame_id=123',{method:'POST',redirect:'manual',headers:{origin},body:new URLSearchParams({pass:p})});
 assert.equal((await post('wrong')).status,403);
 const allow=await post(pass);assert.equal(allow.status,303);assert.equal(allow.headers.get('location'),'./?frame_id=123');const c=allow.headers.get('set-cookie');assert.match(c,/HttpOnly/);
 const blocked=ws(origin);const [_,denial]=await once(blocked,'unexpected-response');assert.equal(denial.statusCode,403);blocked.terminate();blocked.on('error',()=>{});
 for(const mode of ['create','quick']){
  const a=ws(origin,c.split(';')[0]);sockets.push(a);await once(a,'open');let wait=next(a);a.send(JSON.stringify({type:'hello',protocol:1,seats:2,mode}));const room=await wait;
  const b=ws('shorepact://bundle');sockets.push(b);await once(b,'open');const both=Promise.all([next(a),next(b)]);
  b.send(JSON.stringify({type:'hello',protocol:1,seats:2,mode:mode==='quick'?mode:'join',code:room.code}));const [one,two]=await both;assert.ok(one.started&&two.started);assert.equal(one.seat,0);assert.equal(two.seat,1);
  wait=next(a);b.send(JSON.stringify({type:'data',to:0,from:0,body:{kind:'move',action:'test'}}));assert.equal((await wait).from,1);
  a.close();b.close();
 }
 console.log('Shorepact: access gate, redirect, protected assets, private and random matching across a web and native origin, seat ownership passed.');
}finally{for(const s of sockets)s.terminate();r.close();server.closeAllConnections();server.close()}
