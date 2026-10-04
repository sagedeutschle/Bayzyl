import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createDebtService, parseFredCSV, parseBLS, parseTreasury, number, deriveHistory } from '../../server/debt-data.js';
import { METRICS, GROUPS, perPersonHistory, rangeHistory, csvForSeries } from '../../server/public/debt-metrics.js';

// Synthetic contract fixtures; never served as economic observations.
const DATE='2026-10-04T12:00:00Z';
const debtRows=[{record_date:'2026-10-01',tot_pub_debt_out_amt:'40000000000000',debt_held_public_amt:'32000000000000',intragov_hold_amt:'8000000000000'}, {record_date:'2026-09-01',tot_pub_debt_out_amt:'39740800000000',debt_held_public_amt:'31800000000000',intragov_hold_amt:'7940800000000'}];
function fixtureFetch(failing=new Set(), onCall=()=>{}) {
  return async raw=>{
    const url=new URL(raw);const id=url.searchParams.get('id')||url.pathname.split('/').at(-1);
    onCall(id);
    if(failing.has(id))throw new Error('Fixture outage');
    if(url.hostname==='fred.stlouisfed.org'){
      const values={GDP:30000,POPTHM:340000,FGRECPT:6000,FGEXPND:8000,FDHBFIN:9000};
      return new Response(`observation_date,${id}\n2026-04-01,${values[id]??123}\n2026-07-01,${values[id]??124}\n2026-09-01,${values[id]??125}\n`);
    }
    if(url.hostname==='api.bls.gov')return Response.json({status:'REQUEST_SUCCEEDED',Results:{series:METRICS.filter(m=>/^(LNS|CUUR)/.test(m.series||'')).map(m=>({seriesID:m.series,data:[{year:'2026',period:'M09',value:'100'},{year:'2026',period:'M08',value:'99'}]}))}});
    let data=[];
    if(id==='debt_to_penny')data=debtRows;
    if(id==='avg_interest_rates')data=[{record_date:'2026-08-31',security_desc:'Total Interest-bearing Debt',avg_interest_rate_amt:'3.5'}];
    if(id==='operating_cash_balance')data=[{record_date:'2026-10-01',account_type:'Treasury General Account (TGA) Closing Balance',close_today_bal:'null',open_today_bal:'900000'}];
    if(id==='gold_reserve')data=Array.from({length:8},(_,i)=>({record_date:'2026-08-31',src_line_nbr:String(i+1),fine_troy_ounce_qty:'10',book_value_amt:'420'}));
    if(id==='debt_subject_to_limit')data=[['Debt Held by the Public',32000000],['Intragovernmental Holdings',8000000],['Debt Not Subject to Limit',200000],['Other Debt Subject to Limit',100],['Statutory Debt Limit',41000000]].map(([debt_catg,v])=>({record_date:'2026-10-01',debt_catg,close_today_bal:String(v)}));
    return Response.json({data});
  };
}
test('43 unique native IDs in nine groups, no missing definitions',()=>{
  assert.equal(METRICS.length,43);assert.equal(new Set(METRICS.map(m=>m.id)).size,43);assert.equal(GROUPS.length,9);
  for(const m of METRICS)assert.ok(GROUPS.some(g=>g.id===m.group));
  for(const id of ['receiptsPerCitizen','spendingPerCitizen','deficitPerCitizen'])assert.match(METRICS.find(m=>m.id===id).frequency,/annual rate/,'Derived flows stay distinct from stock measures in comparisons');
});
test('numbers reject null, empty, malformed and non-finite values',()=>{
  for(const v of ['null','','.',null,'1junk','Infinity'])assert.equal(number(v),null);
  assert.equal(number('-1,234.5'),-1234.5);assert.equal(number('0'),0);
});
test('FRED parses missing observations, dates and scales with exact header',()=>{
  assert.deepEqual(parseFredCSV('observation_date,GDP\n2026-02-30,7\n2026-01-01,.\n2026-04-01,2.5\n','GDP',1e9),[{date:'2026-04-01',value:2.5e9}]);
  assert.throws(()=>parseFredCSV('<html>unavailable</html>','GDP'));
});
test('BLS skips annual M13 and normalizes persons',()=>{
  const j={status:'REQUEST_SUCCEEDED',Results:{series:[{seriesID:'LNS11000000',data:[{year:'2026',period:'M13',value:'99'},{year:'2026',period:'M09',value:'3'}]}]}};
  assert.deepEqual(parseBLS(j).LNS11000000,[{date:'2026-09-01',value:3000}]);
  assert.throws(()=>parseBLS({status:'REQUEST_NOT_PROCESSED'}));
});
test('cash selects closing account, supports modern field layout, dollars once',()=>{
  const data=[{record_date:'2026-10-01',account_type:'Treasury General Account (TGA) Closing Balance',close_today_bal:'null',open_today_bal:'15'}, {record_date:'2026-10-01',account_type:'Total TGA Deposits (Table II)',open_today_bal:'999'}];
  assert.equal(parseTreasury('treasury-cash',{data}).treasuryGeneralAccount[0].value,15e6);
});
test('gold requires complete eight-line observation, not partial boundary',()=>{
  const data=Array.from({length:8},(_,i)=>({record_date:'2026-09-30',src_line_nbr:String(i+1),fine_troy_ounce_qty:'2',book_value_amt:'84'}));
  assert.equal(parseTreasury('treasury-gold',{data}).goldReserveBookValue[0].value,672);
  assert.equal(parseTreasury('treasury-gold',{data:data.slice(1)}).goldReserveOunces.length,0);
});
test('debt subject to limit subtracts exclusions and does not include ceiling',async()=>{
  const j=await(await fixtureFetch()('https://api.fiscaldata.treasury.gov/debt_subject_to_limit')).json();
  assert.equal(parseTreasury('treasury-limit',j).debtSubjectToLimit[0].value,39_800_100_000_000);
});
test('derived joins never look forward and disclose every input date',()=>{
  const a={id:'debt',maxAgeDays:10,history:[{date:'2026-01-01',value:100},{date:'2026-03-01',value:200}]};
  const b={id:'population',maxAgeDays:100,history:[{date:'2026-02-01',value:2},{date:'2026-04-01',value:4}]};
  assert.deepEqual(deriveHistory([a,b],([x,y])=>x/y),[{date:'2026-03-01',value:100,inputDates:{debt:'2026-03-01',population:'2026-02-01'}}]);
});
test('full contract normalizes money, dates, ratios, growth and input sources',async()=>{
  const s=await createDebtService({fetchImpl:fixtureFetch(),now:()=>Date.parse(DATE)}).getSnapshot();
  const m=Object.fromEntries(s.metrics.map(m=>[m.id,m]));
  assert.equal(s.metrics.length,43);assert.equal(s.groups.length,9);assert.equal(m.totalDebt.value,40e12);
  assert.equal(m.treasuryGeneralAccount.value,900e9);assert.equal(m.foreignHeldFederalDebt.value,9e12);
  assert.equal(m.population.value,340e6);assert.equal(m.debtPerCitizen.value,40e12/340e6);
  assert.equal(m.debtToGDP.value,40e12/30e12*100);assert.equal(m.debtGrowthPerSecond.value,100000);
  assert.equal(m.deficitPerCitizen.value,2e12/340e6);assert.equal(m.debtPerCitizen.inputDates.population,'2026-09-01');
  assert.ok(m.studentLoanDebt.note.includes('discontinued'));assert.equal(m.totalDebt.estimated,false);
});
test('one FRED failure retains only its last-good data; siblings refresh',async()=>{
  let stamp=Date.parse(DATE),calls=0;const failing=new Set();
  const service=createDebtService({fetchImpl:fixtureFetch(failing,()=>calls++),now:()=>stamp});
  const first=await service.getSnapshot();failing.add('GDP');stamp+=61000;
  const second=await service.getSnapshot({force:true});const m=Object.fromEntries(second.metrics.map(m=>[m.id,m]));
  assert.equal(m.gdp.status,'stale');assert.equal(m.gdp.value,30e12);assert.equal(m.population.status,'ok');assert.equal(m.debtToGDP.status,'stale');
  assert.equal(m.gdp.fetchedAt,first.fetchedAt);assert.equal(second.sources.find(s=>s.id==='GDP').status,'stale');assert.equal(calls,58);
});
test('empty initial service exposes null and empty histories, never fabricated values',async()=>{
  const s=await createDebtService({fetchImpl:async()=>{throw new Error('offline');},now:()=>Date.parse(DATE)}).getSnapshot();
  assert.equal(s.status,'unavailable');assert.ok(s.metrics.every(m=>m.value===null && m.history.length===0 && m.status==='missing'));
});
test('future-dated responses cannot replace an existing valid observation',async()=>{
  let stamp=Date.parse(DATE),future=false;const base=fixtureFetch();
  const service=createDebtService({now:()=>stamp,fetchImpl:async(url,options)=>{
    if(future&&String(url).includes('id=GDP'))return new Response('observation_date,GDP\n2099-01-01,99999\n');
    return base(url,options);
  }});
  await service.getSnapshot();future=true;stamp+=61000;
  const s=await service.getSnapshot({force:true}),gdp=s.metrics.find(m=>m.id==='gdp');
  assert.equal(gdp.value,30e12);assert.equal(gdp.status,'stale');assert.equal(gdp.observedAt,'2026-09-01');
});
test('partial Treasury field outage does not erase valid sibling metrics',async()=>{
  const base=fixtureFetch();const service=createDebtService({now:()=>Date.parse(DATE),fetchImpl:async(url,options)=>{
    if(String(url).includes('debt_to_penny'))return Response.json({data:debtRows.map(r=>({...r,debt_held_public_amt:'null'}))});
    return base(url,options);
  }});
  const s=await service.getSnapshot(),m=Object.fromEntries(s.metrics.map(m=>[m.id,m]));
  assert.equal(m.totalDebt.value,40e12);assert.equal(m.debtHeldByPublic.value,null);assert.equal(m.debtHeldByPublic.status,'missing');assert.notEqual(s.sources.find(s=>s.id==='treasury-debt').status,'ok');
});
test('TTL, forced minimum interval and concurrent calls coalesce',async()=>{
  let stamp=Date.parse(DATE),calls=0;const service=createDebtService({fetchImpl:fixtureFetch(new Set(),()=>calls++),now:()=>stamp});
  const [a,b]=await Promise.all([service.getSnapshot(),service.getSnapshot({force:true})]);assert.equal(a,b);assert.equal(calls,29);
  stamp+=59000;await service.getSnapshot({force:true});assert.equal(calls,29);
  stamp+=2000;await service.getSnapshot();assert.equal(calls,29);await service.getSnapshot({force:true});assert.equal(calls,58);
});
test('hung source has a bounded timeout and does not block remaining metric statuses',async()=>{
  const service=createDebtService({fetchImpl:()=>new Promise(()=>{}),timeoutMs:4,now:()=>Date.parse(DATE)});
  const start=Date.now();const s=await service.getSnapshot();assert.equal(s.status,'unavailable');assert.ok(Date.now()-start<1500);
});
test('source requests never exceed four simultaneous fetches',async()=>{
  let active=0,peak=0;const base=fixtureFetch();
  await createDebtService({fetchImpl:async(...args)=>{active++;peak=Math.max(peak,active);await new Promise(r=>setTimeout(r,2));try{return await base(...args);}finally{active--;}}}).getSnapshot();
  assert.ok(peak<=4);assert.ok(peak>1);
});
test('per-person chart and range filters use actual dated points, CSV retains provenance',()=>{
  const h=[{date:'2025-01-01',value:100},{date:'2026-01-01',value:200}];
  assert.equal(perPersonHistory(h,[{date:'2025-12-01',value:10}]).length,1);
  assert.equal(perPersonHistory(h,[{date:'2025-06-01',value:10}]).length,0);
  assert.equal(rangeHistory(h,'3m','2026-02-01').length,1);
  const csv=csvForSeries([{label:'A "quoted" name',unit:'dollars',status:'stale',sourceUrl:'https://example.test',history:h}]);assert.ok(csv.includes('A ""quoted"" name'));assert.ok(csv.includes('"stale"'));assert.ok(csv.includes('2025-01-01'));
});
test('dashboard exposes essential controls and same-origin service without inline handlers',async()=>{
  const html=await readFile(new URL('../../server/public/debt.html',import.meta.url),'utf8');
  for(const id of ['refresh','motion','announce','metric-search','metric-select','compare-select','period','per-capita','expand-chart','export-csv','register','sources'])assert.ok(html.includes(`id="${id}"`),id);
  assert.ok(!/on(click|change)=/.test(html));assert.ok(html.includes('type="module"'));
});
