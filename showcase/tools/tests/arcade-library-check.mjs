import assert from 'node:assert/strict';
import { preserveImport } from '../../prismet-site/pages/arcade/import-backup.js';
import { catalog, byID } from '../../prismet-site/pages/arcade/catalog.js';
import { browserDailySeed, browserSettings, browserRoute, validateBrowserSave } from '../../prismet-site/pages/arcade/browser-saves.js';
import { needsBot, pauseRestored, scheduler } from '../../prismet-site/pages/arcade/controller-policy.js';
let checks=0;const check=(value,label)=>{assert.ok(value,label);checks++;};
check(catalog.length===23,'23 real facets');check(catalog.filter(x=>x.category!=='Lenses').length===20,'20 playable destinations');
check(!byID.constructor&&!byID.__proto__,'catalog rejects inherited properties');
for(const id of ['constructor','__proto__','missing'])assert.throws(()=>browserDailySeed(id,'2026-10-04'));checks+=3;
for(const q of ['mode=challenge','mode=daily&mode=free','seed=1&seed=2','mode=daily&date=2026-10-04&date=2026-10-05','date=2026-10-04','mode=daily&date=2026-02-30']){assert.throws(()=>browserSettings('snake',new URLSearchParams(q)));checks++;}
let sample, sampleEngine;
for(const entry of catalog.filter(x=>!x.portable&&x.category!=='Lenses')){
 const {default:engine}=await import(`../../prismet-site/pages/arcade/games/${entry.id}.js`);
 const state=engine.initial('42');check(engine.validate(state),`${entry.id} initial valid`);
 const view=engine.view(state);check(['grid','word','cards','cube','hex'].includes(view.kind),`${entry.id} renderer available`);
 check((!view.controls||Array.isArray(view.controls))&&(view.controls||[]).every(x=>typeof x.label==='string'&&x.action),`${entry.id} controls`);
 const save={browserVersion:1,gameID:entry.id,seed:'42',mode:'challenge',state,steps:0,elapsedMs:0,savedAt:'2026-10-04T01:02:03.000Z'};
 check(validateBrowserSave(save,engine),`${entry.id} save valid`);
 check(browserSettings(entry.id,new URL(browserRoute(save),'https://prismet.xyz').searchParams).seed==='42',`${entry.id} challenge reload`);
 const dailySeed=browserDailySeed(entry.id,'2026-10-04'),daily={...save,seed:dailySeed,mode:'daily',dailyDate:'2026-10-04',state:engine.initial(dailySeed)};
 check(validateBrowserSave(daily,engine),`${entry.id} daily valid`);
 check(browserSettings(entry.id,new URL(browserRoute(daily),'https://prismet.xyz').searchParams).dailyDate===daily.dailyDate,`${entry.id} daily reload`);
 if(entry.id==='snake'){
  sample=save;sampleEngine=engine;const running=engine.apply(state,{type:'start'}),paused=pauseRestored(entry.id,running,engine);
  check(running.running&&!paused.running,'restored snake pauses');check(JSON.stringify(paused.body)===JSON.stringify(running.body),'pause preserves snake board');
 }
 const human={chess:'white',reversi:'black',checkers:'dark','connect-four':'red',gomoku:'black','sea-battle':'host'};
 if(Object.hasOwn(human,entry.id)){
  const botState={...state,mode:'bot',currentPlayer:human[entry.id]};check(!needsBot(entry.id,botState,engine),`${entry.id} human turn not scheduled`);
  check(needsBot(entry.id,{...botState,currentPlayer:entry.id==='chess'?'black':entry.id==='reversi'?'white':entry.id==='checkers'?'light':entry.id==='connect-four'?'yellow':entry.id==='gomoku'?'white':'guest'},engine),`${entry.id} bot scheduled`);
 }
}
for(const changed of [{savedAt:'2026-10-04T24:00:00.000Z'},{savedAt:'2026-02-30T01:02:03.000Z'},{savedAt:'2026-10-04'},{extra:1},{gameID:'constructor'},{gameID:'__proto__'},{browserVersion:2},{elapsedMs:-1},{mode:'daily',dailyDate:'2026-10-04'},{state:{...sample.state,padding:'x'.repeat(262144)}}])check(!validateBrowserSave({...sample,...changed},sampleEngine),'invalid metadata/oversized save rejected');
let callbacks=[],runs=0;const timer=scheduler(fn=>{callbacks.push(fn);return callbacks.length;},()=>{});timer.schedule(()=>runs++);timer.cancel();callbacks[0]();check(runs===0,'cancel suppresses even queued callback');timer.schedule(()=>runs++);timer.schedule(()=>runs++);callbacks[1]();check(runs===0,'replacement suppresses stale callback');callbacks[2]();check(runs===1,'current callback runs once');
for(const failed of ['read','visible','destination']){const writes=[];const storage={read:()=>({ok:failed!=='read',value:'old destination'}),write:(key,value)=>{writes.push([key,value]);return !key.endsWith(failed);}};assert.throws(()=>preserveImport(storage,{old:true},'target','backup','unique'));check(writes.every(([key])=>key.startsWith('backup.')),'failed import never writes destination');}
const backups=new Map();preserveImport({read:()=>({ok:true,value:'old destination'}),write:(key,value)=>{backups.set(key,value);return true;}},{visible:true},'target','backup','unique');check(backups.size===2&&backups.get('backup.unique.destination')==='old destination','both sessions backed up before import');
console.log(`Arcade library: ${checks} assertions passed.`);
