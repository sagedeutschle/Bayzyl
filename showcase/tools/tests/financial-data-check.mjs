import test from 'node:test';
import assert from 'node:assert/strict';
import { createDebtService } from '../../server/debt-data.js';
import { formatValue } from '../../server/public/debt-metrics.js';

// Synthetic observations exercise real parsing, scaling, derivation and cache behavior.
// Only the network boundary is substituted. No fixture is a public economic value.
const newIDs=['realMedianWeeklyEarnings','rentPriceIndex','homePriceIndex','householdDebtServiceRatio','creditCardDelinquencyRate','personalSavingRate','bottom50WealthShare','top1WealthShare','fiscalYearReceipts','interestShareOfReceipts'];
const raw={LES1252881600Q:[['2026-04-01',380]],USSTHPI:[['2026-04-01',650]],TDSP:[['2026-04-01',11.2]],DRCCLACBS:[['2026-04-01',3.1]],PSAVERT:[['2026-08-01',4]],WFRBSB50215:[['2026-04-01',2.5]],WFRBST01134:[['2026-04-01',31]],FYFR:[['2025-09-30',5000000]],FYOINT:[['2025-09-30',1000000]]};
function fixture(series=raw,{fail=new Set(),onCall=()=>{}}={}) {
  return async(url,options)=>{
    const u=new URL(url),id=u.searchParams.get('id');onCall(u);
    if(fail.has(id))throw new Error('Synthetic source outage');
    if(u.hostname==='fred.stlouisfed.org')return new Response(`observation_date,${id}\n${(series[id]||[]).map(p=>p.join(',')).join('\n')}\n`);
    if(u.hostname==='api.bls.gov'){
      const requested=JSON.parse(options.body).seriesid;
      return Response.json({status:'REQUEST_SUCCEEDED',Results:{series:requested.includes('CUUR0000SEHA')?[{seriesID:'CUUR0000SEHA',data:[{year:'2026',period:'M08',value:'420.5'},{year:'2026',period:'M13',value:'999'}]}]:[]}});
    }
    return Response.json({data:[]});
  };
}
const now=()=>Date.parse('2026-10-04T12:00:00Z');
const byID=s=>Object.fromEntries(s.metrics.map(m=>[m.id,m]));

test('opt-in expansion exposes normalized source values and protects real/index/ratio semantics',async()=>{
  const s=await createDebtService({fetchImpl:fixture(),now}).getSnapshot({expanded:true}),m=byID(s);
  assert.equal(s.metrics.length,53);assert.ok(newIDs.every(id=>m[id]));assert.equal(s.groups.length,9);
  assert.equal(s.groups.find(g=>g.id==='household').label,'Household Finances');
  assert.equal(m.realMedianWeeklyEarnings.value,380);assert.equal(m.realMedianWeeklyEarnings.unit,'realDollars1982_84PerWeek');
  assert.equal(formatValue(380,m.realMedianWeeklyEarnings.unit),'$380');
  assert.equal(m.rentPriceIndex.value,420.5);assert.equal(m.rentPriceIndex.observedAt,'2026-08-01');
  assert.equal(m.fiscalYearReceipts.value,5e12);assert.equal(m.householdDebtServiceRatio.value,11.2);
  assert.equal(m.bottom50WealthShare.value,2.5);assert.equal(m.top1WealthShare.value,31);
  assert.equal(m.bottom50WealthShare.comparisonFamily,m.top1WealthShare.comparisonFamily);
  assert.notEqual(m.householdDebtServiceRatio.comparisonFamily,m.personalSavingRate.comparisonFamily);
  assert.notEqual(m.homePriceIndex.comparisonFamily,m.rentPriceIndex.comparisonFamily);
  assert.equal(m.rentPriceIndex.comparisonFamily,m.cpi.comparisonFamily);
  assert.equal(m.bottom50WealthShare.estimated,true);assert.equal(m.bottom50WealthShare.derived,false);
});

test('interest share joins exact fiscal dates, omits gaps and rejects zero/negative receipts',async()=>{
  const series={...raw,FYOINT:[['2020-06-30',5],['2021-09-30',10],['2022-09-30',20],['2023-09-30',30],['2024-09-30',40],['2025-09-30',50]],FYFR:[['2020-09-30',100],['2022-09-30',200],['2023-09-30',0],['2024-09-30',-100],['2025-09-29',500]]};
  const m=byID(await createDebtService({fetchImpl:fixture(series),now}).getSnapshot({expanded:true})).interestShareOfReceipts;
  assert.ok(m, 'expanded fiscal ratio must exist');
  assert.deepEqual(m.history,[{date:'2022-09-30',value:10,inputDates:{netInterestOutlays:'2022-09-30',fiscalYearReceipts:'2022-09-30'}}]);
  assert.equal(m.status,'stale');assert.equal(m.observedAt,'2022-09-30');assert.equal(m.sources.length,2);
});

test('missing exact fiscal pair stays null instead of substituting annual-rate receipts',async()=>{
  const series={...raw,FYFR:[['2025-09-29',500]],FGRECPT:[['2025-09-30',5]]};
  const m=byID(await createDebtService({fetchImpl:fixture(series),now}).getSnapshot({expanded:true})).interestShareOfReceipts;
  assert.ok(m, 'expanded fiscal ratio must exist');
  assert.equal(m.value,null);assert.equal(m.status,'missing');assert.deepEqual(m.history,[]);
});

test('concurrent catalogs share one refresh while legacy IDs, groups and source statuses remain isolated',async()=>{
  let calls=0;const service=createDebtService({fetchImpl:fixture({...raw,FYOINT:[]},{onCall:()=>calls++}),now});
  const [old,expanded]=await Promise.all([service.getSnapshot(),service.getSnapshot({expanded:true})]);
  assert.equal(old.version,1);assert.equal(old.metrics.length,43);assert.equal(expanded.metrics.length,53);assert.equal(calls,37);
  assert.equal(old.status,'unavailable');assert.equal(expanded.status,'partial');
  assert.equal(old.groups.find(g=>g.id==='household').label,'Consumer & Household Debt');
  assert.ok(old.groups.flatMap(g=>g.metricIds).every(id=>!newIDs.includes(id)));
  assert.ok(old.sources.every(s=>!Object.keys(raw).filter(id=>id!=='FYOINT').includes(s.id)&&s.id!=='CUUR0000SEHA'));
  assert.equal(old.sources.length,34);assert.equal(expanded.sources.length,43);
  await service.getSnapshot({expanded:true,force:true});assert.equal(calls,37);
  old.metrics[0].history.push({date:'1900-01-01',value:123});old.groups[0].metricIds.length=0;expanded.metrics[0].label='caller mutation';old.sources[0].label='changed source';
  const next=await service.getSnapshot();assert.deepEqual(next.metrics[0].history,[]);assert.notEqual(next.metrics[0].label,'caller mutation');assert.ok(next.groups[0].metricIds.length);assert.notEqual(next.sources[0].label,'changed source');
});

test('new source outage retains dated last good values and propagates stale to exact fiscal ratio',async()=>{
  let stamp=now();const fail=new Set();const service=createDebtService({fetchImpl:fixture(raw,{fail}),now:()=>stamp});
  const first=await service.getSnapshot({expanded:true});fail.add('FYFR');stamp+=61000;
  const second=await service.getSnapshot({expanded:true,force:true}),m=byID(second);
  assert.ok(m.fiscalYearReceipts, 'expanded fiscal receipts must exist');
  assert.equal(m.fiscalYearReceipts.status,'stale');assert.equal(m.fiscalYearReceipts.fetchedAt,first.fetchedAt);
  assert.equal(m.interestShareOfReceipts.value,20);assert.equal(m.interestShareOfReceipts.status,'stale');
  assert.equal(m.interestShareOfReceipts.fetchedAt,first.fetchedAt);assert.equal(m.personalSavingRate.status,'ok');
  assert.deepEqual(m.interestShareOfReceipts.inputDates,{netInterestOutlays:'2025-09-30',fiscalYearReceipts:'2025-09-30'});
});

// Period-start dating plus quarterly release lag must not mark a normal DFA/DSR release late.
test('slow quarterly household releases remain current through their normal reporting lag',async()=>{
  const s=await createDebtService({fetchImpl:fixture(),now:()=>Date.parse('2026-11-15')}).getSnapshot({expanded:true}),m=byID(s);
  for(const id of ['householdDebtServiceRatio','bottom50WealthShare','top1WealthShare'])assert.equal(m[id].status,'ok',id);
  assert.equal(m.gdp.maxAgeDays,200,'legacy cadence remains unchanged');
});
