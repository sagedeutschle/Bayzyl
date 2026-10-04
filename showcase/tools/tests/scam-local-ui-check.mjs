import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync} from 'node:fs';
import {createLocalReceiptSession,combinedReceiptRows,receiptRows} from '../../prismet-site/pages/scam.js';
import {computeBill,pickStatement} from '../../prismet-site/pages/scam-calc.js';
const base=new URL('../../prismet-site/pages/scam-data/',import.meta.url);
const tax=JSON.parse(readFileSync(new URL('tax-2026.json',base)));
const treasury=pickStatement(JSON.parse(readFileSync(new URL('mts-snapshot.json',base))).rows);
const bill=computeBill({wages:82000,status:'single',stateCode:'OH'},tax);
const local={localIncome:{OH:{by:'place',base:'wages',note:'City wages',rates:{'3918000':{name:'Columbus',rate:.025}}}},counties:{'39049':'Franklin County'},sales:{year:2025,source:{name:'IRS',url:'https://www.irs.gov/'},bands:[0],states:{OH:{rate:.0575,local:'extra',amounts:[[814,900,1000,1100,1200,1300]]}}},property:{year:2024,sources:[]}};
const zipRows={'43201':[5349,440000,17,'3918000',100,.08],'43202':[4200,300000,65,'3918000',100,.08]};
function store(seed){const map=new Map(seed?[['prismet.scam.housing',seed]]:[]);return {getItem:k=>map.get(k)??null,setItem:(k,v)=>map.set(k,v),removeItem:k=>map.delete(k),map};}
function loader(url){if(url.includes('local-2026'))return Promise.resolve(local);if(url.includes('zip-local/'))return Promise.resolve(zipRows);return Promise.resolve({'43201':{c:'39049'},'43202':{c:'39049'}});}
const deferred=()=>{let resolve,reject;const promise=new Promise((y,n)=>{resolve=y;reject=n;});return {promise,resolve,reject};};
test('native ZIP default, explicit owner choice, same-ZIP recalculation and new-ZIP reset',async()=>{
 const storage=store(),s=createLocalReceiptSession({loadJSON:loader,storage});
 await s.load({bill,tax,zipCode:'43201'});assert.equal(s.view().result.owns,false);
 s.choose(true);assert.equal(s.view().result.property,5349);assert.equal(s.view().choice,true);
 assert.deepEqual(JSON.parse(storage.map.get('prismet.scam.housing')),{zip:'43201',owns:true});
 await s.load({bill:{...bill,wages:90000},tax,zipCode:'43201'});assert.equal(s.view().result.owns,true);
 s.invalidate('43202');assert.equal(s.view().phase,'idle');assert.equal(s.view().rows.length,0);assert.equal(storage.map.size,0);
 await s.load({bill,tax,zipCode:'43202'});assert.equal(s.view().choice,null);assert.equal(s.view().result.owns,true);
});
test('explicit housing choice restores only the matching ZIP, malformed and unavailable storage stay usable',async()=>{
 for(const [saved,zip,want]of [[JSON.stringify({zip:'43201',owns:true}),'43201',true],[JSON.stringify({zip:'43202',owns:false}),'43201',false],['not json','43201',false],[JSON.stringify({zip:'43201',owns:'true'}),'43201',false]]){
  const s=createLocalReceiptSession({loadJSON:loader,storage:store(saved)});await s.load({bill,tax,zipCode:zip});assert.equal(s.view().result.owns,want);
 }
 const storage={getItem(){throw Error('blocked');},setItem(){throw Error('blocked');},removeItem(){throw Error('blocked');}};
 const s=createLocalReceiptSession({loadJSON:loader,storage});await s.load({bill,tax,zipCode:'43201'});s.choose(true);assert.equal(s.view().result.owns,true);assert.equal(s.view().storageAvailable,false);
});
test('out-of-order ZIP and same-ZIP responses cannot replace the current bill',async()=>{
 const pending=[],s=createLocalReceiptSession({loadJSON:url=>{const d=deferred();pending.push({url,...d});return d.promise;}});
 const first=s.load({bill,tax,zipCode:'43201'});await Promise.resolve();const firstBatch=pending.splice(0);
 const newer={...bill,wages:91000},second=s.load({bill:newer,tax,zipCode:'43202'});await Promise.resolve();const secondBatch=pending.splice(0);
 for(const p of secondBatch)p.resolve(await loader(p.url));await second;
 assert.equal(s.view().zipCode,'43202');const saved=s.view().result;
 for(const p of firstBatch)p.resolve(await loader(p.url));await first;
 assert.equal(s.view().zipCode,'43202');assert.deepEqual(s.view().result,saved);
 const third=s.load({bill,tax,zipCode:'43202'});await Promise.resolve();const thirdBatch=pending.splice(0);s.invalidate('43203');
 for(const p of thirdBatch)p.resolve(await loader(p.url));await third;assert.equal(s.view().phase,'idle');assert.equal(s.view().rows.length,0);
});
test('missing or failed ZIP/local files suppress fake zero totals and preserve the first receipt',async()=>{
 const first=receiptRows(bill,treasury,tax,false),original=JSON.stringify(first);
 for(const kind of ['zip-error','tables-error']){
  const s=createLocalReceiptSession({loadJSON:url=>url.includes('zip-local/')?(kind==='zip-error'?Promise.reject(Error('offline')):kind==='zip-absent'?Promise.resolve({}):loader(url)):url.includes('local-2026')&&kind==='tables-error'?Promise.reject(Error('offline')):loader(url)});
  await s.load({bill,tax,zipCode:'43201'});const view=s.view();assert.equal(view.phase,'unavailable');assert.equal(view.result,null);assert.deepEqual(view.rows,[]);assert.ok(view.message.includes('not included'));
  assert.deepEqual(combinedReceiptRows(first,view),first);assert.equal(JSON.stringify(first),original);
 }
});
test('retry recovers after an independent failure without losing explicit choice',async()=>{
 let fail=true;const s=createLocalReceiptSession({loadJSON:url=>fail&&url.includes('zip-local/')?Promise.reject(Error('offline')):loader(url)});
 await s.load({bill,tax,zipCode:'43201'});s.choose(true);fail=false;await s.load({bill,tax,zipCode:'43201'});assert.equal(s.view().phase,'ready');assert.equal(s.view().result.owns,true);
});
test('county failure stays explicit while available city/sales/property data still works',async()=>{
 const s=createLocalReceiptSession({loadJSON:url=>url.includes('/zip/')?Promise.reject(Error('offline')):loader(url)});
 await s.load({bill,tax,zipCode:'43201'});assert.equal(s.view().phase,'ready');assert.equal(s.view().result.income,2050);assert.deepEqual(s.view().warnings,[]);
});
test('both receipts and exported rows follow percent mode without altering source native rows',async()=>{
 const s=createLocalReceiptSession({loadJSON:loader});await s.load({bill,tax,zipCode:'43201'});
 const dollar=s.view(false),percent=s.view(true),first=receiptRows(bill,treasury,tax,true),combined=combinedReceiptRows(first,percent);
 assert.ok(dollar.rows.some(r=>r.r==='$2,050'));assert.ok(percent.rows.some(r=>r.r==='2.5%'));
 assert.equal(percent.rows.filter(r=>r.k==='total').length,1);assert.equal(combined.filter(r=>r.k==='total').length,2);
 assert.equal(combined.filter(r=>r.t==='THE REST OF THE BILL').length,1);assert.deepEqual(combined.slice(0,first.length),first);
 assert.ok(!JSON.stringify(combined).includes('$2,050'));assert.ok(combined.some(r=>r.k==='note'&&r.t.includes('estimate, not a bill')));
});
test('local requests contain public prefixes only, never wages or a full ZIP in a query',async()=>{
 const urls=[],s=createLocalReceiptSession({loadJSON:url=>{urls.push(url);return loader(url);}});await s.load({bill,tax,zipCode:'43201'});
 assert.deepEqual(urls.sort(),['scam-data/local-2026.json','scam-data/zip-local/432.json','scam-data/zip/432.json'].sort());
});

test('the session integrates corrected native golden rows verbatim in both receipt modes',async()=>{
 const golden=JSON.parse(readFileSync(new URL('./fixtures/uncle-scam-local-golden.json',import.meta.url)));
 assert.equal(golden.cases.length,17);
 const localData=JSON.parse(readFileSync(new URL('local-2026.json',base)));
 for(const example of golden.cases){
  const i=example.input,calculated=computeBill({wages:i.wages,status:i.status,stateCode:i.state},tax);
  const loadJSON=async url=>url.includes('local-2026')?localData:url.includes('/zip/')?{[i.zip]:{c:i.county}}:JSON.parse(readFileSync(new URL(`zip-local/${i.zip.slice(0,3)}.json`,base)));
  const s=createLocalReceiptSession({loadJSON});await s.load({bill:calculated,tax,zipCode:i.zip});if(typeof i.owns==='boolean')s.choose(i.owns);
  assert.equal(s.view().phase,'ready',`${i.zip} native receipt remains reachable`);
  const native=rows=>rows.map(row=>[row.k,row.t??row.l??'',row.r??'',row.c??'']);
  assert.deepEqual(native(s.view(false).rows),example.rows,`${i.zip} dollar DOM input`);assert.deepEqual(native(s.view(true).rows),example.percentRows,`${i.zip} percent DOM input`);
  assert.equal(s.view().result.incomplete,example.answer.incomplete);assert.equal(s.view().result.lowerBound,example.answer.lowerBound);
 }
});
test('DOM and image renderers retain both totals, native row text and disclosure notes',async()=>{
 const {drawDom,drawCanvas}=await import('../../prismet-site/pages/scam.js');
 class Node { constructor(tag){this.tag=tag;this.children=[];this.style={};this.attrs={};this.text='';}set textContent(t){this.text=String(t);this.children=[];}get textContent(){return this.text+this.children.map(c=>c.textContent??String(c)).join('');}append(...children){this.children.push(...children);}setAttribute(k,v){this.attrs[k]=v;} }
 const painted=[];const ctx={measureText:t=>({width:String(t).length*6}),scale(){},fillRect(){},save(){},restore(){},setLineDash(){},beginPath(){},moveTo(){},lineTo(){},stroke(){},translate(){},rotate(){},strokeRect(){},fillText(t){painted.push(t);}};
 const previous=globalThis.document;
 globalThis.document={createElement:tag=>tag==='canvas'?{getContext:()=>ctx}:new Node(tag)};
 try{
  const s=createLocalReceiptSession({loadJSON:loader});await s.load({bill,tax,zipCode:'43201'});const v=s.view(),target=new Node('div');drawDom(v.rows,target);
  assert.deepEqual(target.children.map(n=>n.textContent),v.rows.map(r=>r.k==='row'||r.k==='total'?r.l+r.r:r.t||''));
  assert.equal(target.children.filter(n=>n.className?.includes('r-total')).length,1);
  const all=combinedReceiptRows(receiptRows(bill,treasury,tax,false),v),canvas=drawCanvas(all);
  assert.equal(canvas.width,800);assert.ok(canvas.height>1000);assert.ok(painted.includes('TOTAL'));assert.ok(painted.includes('ALL IN'));assert.ok(painted.join(' ').includes('estimate, not a bill'));
 }finally{if(previous===undefined)delete globalThis.document;else globalThis.document=previous;}
});

test('a capped renter median does not describe the renter total as capped',async()=>{
 const s=createLocalReceiptSession({loadJSON:url=>url.includes('zip-local/')?Promise.resolve({'43201':[10001,900000,17,'3918000',100,.08]}):loader(url)});
 await s.load({bill,tax,zipCode:'43201'});assert.equal(s.view().result.lowerBound,false);assert.ok(!s.view().warnings.some(w=>w.includes('total counts')));
 s.choose(true);assert.equal(s.view().result.lowerBound,true);assert.deepEqual(s.view().warnings,[]);assert.deepEqual(s.view(true).warnings,[]);assert.ok(s.view(true).rows.some(row=>row.r?.startsWith('over ')));
});
test('malformed ZIP records are unavailable rather than a zero or nonfinite local bill',async()=>{
 for(const row of [[],[NaN],[1,2,101],[1,2,50,'not-a-place'],[1,2,50,null,Infinity]]){
  const s=createLocalReceiptSession({loadJSON:url=>url.includes('zip-local/')?Promise.resolve({'43201':row}):loader(url)});await s.load({bill,tax,zipCode:'43201'});assert.equal(s.view().phase,'unavailable');assert.equal(s.view().result,null);
 }
});

test('a successfully loaded shard without this ZIP renders native unknown coverage without retry',async()=>{
 const s=createLocalReceiptSession({loadJSON:url=>url.includes('zip-local/')?Promise.resolve({}):loader(url)});
 await s.load({bill,tax,zipCode:'43299'});const v=s.view();assert.equal(v.phase,'ready');assert.equal(v.result.incomeStatus,'zipUnknown');assert.equal(v.result.salesState,814);assert.equal(v.result.incomplete,true);assert.equal(v.retryable,false);assert.ok(v.rows.some(row=>row.r==='not on file'));assert.ok(v.coverage.includes('Some lines'));
});
test('only a failed county lookup needed by the selected tax table adds a retry warning',async()=>{
 const localData=JSON.parse(readFileSync(new URL('local-2026.json',base)));
 for(const [stateCode,expected]of [['OH',false],['MD',true]]){
  const b=computeBill({wages:82000,status:'single',stateCode},tax);
  const s=createLocalReceiptSession({loadJSON:url=>url.includes('local-2026')?Promise.resolve(localData):url.includes('/zip/')?Promise.reject(Error('offline')):loader(url)});
  await s.load({bill:b,tax,zipCode:'43201'});assert.equal(s.view().warnings.length,expected?1:0);assert.equal(s.view().retryable,expected);
 }
});
