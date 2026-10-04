import assert from 'node:assert/strict';
import {test} from 'node:test';
import {EXPANDED_METRICS,rangeHistory} from '../../server/public/debt-metrics.js';
import {PRESETS,canCompare,canUsePerPerson,parseView,viewQuery,changeView,comparisonCoverage,validSnapshot,measurementBasis,chartAxis,axisLabel,readoutValues,emptyChartState} from '../../server/public/debt-discovery.js';
const catalog=[
 {id:'totalDebt',unit:'dollars',frequency:'Daily'},
 {id:'population',unit:'people',frequency:'Monthly'},
 {id:'otherDebt',unit:'dollars',frequency:'Quarterly'},
 {id:'realMedianWeeklyEarnings',unit:'realDollars1982_84PerWeek',comparisonFamily:'real-weekly-earnings'},
 {id:'rentPriceIndex',unit:'index',comparisonFamily:'cpi-1982-84'},
 {id:'cpi',unit:'index',comparisonFamily:'cpi-1982-84'},
 {id:'homePriceIndex',unit:'index',comparisonFamily:'fhfa-1980-q1'},
 {id:'top1WealthShare',unit:'percent',comparisonFamily:'household-net-worth-share'},
 {id:'bottom50WealthShare',unit:'percent',comparisonFamily:'household-net-worth-share'},
 {id:'householdDebtServiceRatio',unit:'percent',comparisonFamily:'household-debt-service'},
 {id:'interestShareOfReceipts',unit:'percent',comparisonFamily:'interest-receipts-share'},
 {id:'fiscalYearReceipts',unit:'dollars',comparisonFamily:'federal-fiscal-year-flow'},
];
const m=id=>catalog.find(m=>m.id===id);
test('comparison semantics preserve old stock choices but separate price bases and denominators',()=>{
 assert.equal(canCompare(m('totalDebt'),m('otherDebt')),true);
 assert.equal(canCompare(m('rentPriceIndex'),m('cpi')),true);
 for(const [a,b]of [['rentPriceIndex','homePriceIndex'],['top1WealthShare','householdDebtServiceRatio'],['totalDebt','fiscalYearReceipts'],['totalDebt','realMedianWeeklyEarnings'],['totalDebt','totalDebt']])assert.equal(canCompare(m(a),m(b)),false);
 assert.equal(canCompare(m('top1WealthShare'),m('bottom50WealthShare')),true);
 assert.equal(canUsePerPerson(m('totalDebt')),true);
 for(const id of ['realMedianWeeklyEarnings','householdDebtServiceRatio','rentPriceIndex'])assert.equal(canUsePerPerson(m(id)),false);
});
test('each question has shareable, reloadable full state and explicit context',()=>{
 assert.equal(PRESETS.length,3);
 for(const preset of PRESETS){const view=parseView(`?question=${preset.id}`,catalog);assert.equal(view.question,preset.id);assert.deepEqual(parseView(viewQuery(view),catalog),view);assert.ok(preset.context.length>70);}
 const custom={selected:'otherDebt',compare:'totalDebt',period:'5y',perPerson:true,question:''};assert.deepEqual(parseView(viewQuery(custom),catalog),custom);
});
test('manual changes and conflicting URL fields clear the question instead of retaining its claim',()=>{
 const preset=parseView('?question=wealth',catalog);
 assert.equal(changeView(preset,{period:'all'},catalog).question,'');
 assert.equal(parseView('?question=wealth&metric=householdDebtServiceRatio',catalog).question,'');
 assert.equal(parseView('?question=wealth&compare=',catalog).question,'');
 assert.equal(changeView(preset,{question:''},catalog).selected,preset.selected);
});
test('untrusted links cannot enable invalid comparisons, population transforms or unknown values',()=>{
 const view=parseView('?metric=rentPriceIndex&compare=homePriceIndex&period=forever&perperson=1&question=__proto__',catalog);
 assert.deepEqual(view,{selected:'rentPriceIndex',compare:'',period:'1y',perPerson:false,question:''});
 assert.equal(parseView('?metric=totalDebt&metric=otherDebt',catalog).selected,'totalDebt');
 assert.equal(parseView('?metric=constructor',catalog).selected,'totalDebt');
});
test('comparison uses only exact shared observations without interpolating or mutating inputs',()=>{
 const a={history:[{date:'2024-01-01',value:1},{date:'2024-04-01',value:2},{date:'2024-07-01',value:3}]}, b={history:[{date:'2024-04-01',value:9},{date:'2024-07-01',value:8},{date:'2024-10-01',value:7}]};
 const original=JSON.stringify([a,b]),result=comparisonCoverage([a,b]);assert.deepEqual(result.window,{start:'2024-04-01',end:'2024-07-01'});assert.deepEqual(result.series.map(s=>s.history.map(p=>p.value)),[[2,3],[9,8]]);assert.equal(JSON.stringify([a,b]),original);
 assert.equal(comparisonCoverage([a,{history:[]}]).window,null);assert.deepEqual(comparisonCoverage([a,{history:[]}]).series.map(s=>s.history),[[],[]]);
 assert.equal(comparisonCoverage([a,{history:[{date:'2025-01-01',value:4}]}]).window,null);
 assert.deepEqual(comparisonCoverage([a]).series,[a]);
});
test('expanded snapshot requires every known metric exactly once, not merely the right length',()=>{
 const data={version:1,catalog:'expanded',metrics:catalog.map(m=>({...m,history:[]})),sources:[]};assert.equal(validSnapshot(data,catalog),true);
 assert.equal(validSnapshot({...data,metrics:[...data.metrics.slice(1),data.metrics[1]]},catalog),false);
 assert.equal(validSnapshot({...data,metrics:data.metrics.slice(1)},catalog),false);
 assert.equal(validSnapshot({...data,catalog:undefined},catalog),false);
});

test('actual expanded catalog preserves all deep links and only allows meaningful financial comparisons',()=>{
 assert.equal(EXPANDED_METRICS.length,53);
 const find=id=>EXPANDED_METRICS.find(m=>m.id===id);
 for(const m of EXPANDED_METRICS){const selected=parseView(`?metric=${m.id}&period=all&perperson=1`,EXPANDED_METRICS);assert.equal(selected.selected,m.id);assert.equal(selected.perPerson,m.unit==='dollars');assert.deepEqual(parseView(viewQuery(selected),EXPANDED_METRICS),selected);}
 for(const [a,b,expected]of [['rentPriceIndex','cpi',true],['rentPriceIndex','homePriceIndex',false],['realMedianWeeklyEarnings','personalIncome',false],['top1WealthShare','bottom50WealthShare',true],['personalSavingRate','householdDebtServiceRatio',false],['creditCardDelinquencyRate','householdDebtServiceRatio',false],['netInterestOutlays','fiscalYearReceipts',true],['federalReceipts','fiscalYearReceipts',false]]){assert.ok(find(a)&&find(b),`${a} and ${b} exist`);assert.equal(canCompare(find(a),find(b)),expected,`${a} compared with ${b}`);}
 for(const p of PRESETS){const parsed=parseView(`?question=${p.id}`,EXPANDED_METRICS);assert.equal(parsed.question,p.id);assert.equal(parsed.selected,p.selected);}
});
test('overlapping calendar coverage does not invent a common observation date',()=>{
 const a={history:[{date:'2025-01-01',value:1},{date:'2025-07-01',value:2}]},b={history:[{date:'2025-04-01',value:3},{date:'2025-10-01',value:4}]};
 assert.deepEqual(comparisonCoverage([a,b]),{series:[{history:[]},{history:[]}],window:null});
});
test('a short period never borrows older observations from a missing comparison',()=>{
 const primary={history:rangeHistory([{date:'2024-09-30',value:5},{date:'2025-09-30',value:6}],'3m','2025-09-30')};
 const old={history:rangeHistory([{date:'2024-09-30',value:9}],'3m','2025-09-30')};
 assert.equal(primary.history.length,1);assert.equal(old.history.length,0);assert.deepEqual(comparisonCoverage([primary,old]).series.map(s=>s.history),[[],[]]);
});

test('measurement basis makes nominal money explicit without mislabeling real dollars or indexes',()=>{
 for(const unit of ['dollars','dollarsPerPerson','dollarsPerSecond'])assert.equal(measurementBasis({unit}),'Nominal · not adjusted for inflation');
 assert.equal(measurementBasis({unit:'realDollars1982_84PerWeek'}),'Real · 1982–84 purchasing power');
 assert.equal(measurementBasis({unit:'index',basis:'1980 Q1 = 100'}),'Index · 1980 Q1 = 100');
 assert.equal(measurementBasis({unit:'percent'}),'');
});

test('chart axes use round ticks, consistent labels and never clip real negative percentages',()=>{
 const wealth=chartAxis([{unit:'percent',history:[{value:2.3},{value:32.5}]}]);
 assert.equal(wealth.min,0);assert.ok(wealth.max>=32.5);assert.deepEqual(wealth.ticks,[0,10,20,30,40]);
 assert.deepEqual(wealth.ticks.map(n=>axisLabel(n,wealth,'percent')),['0%','10%','20%','30%','40%']);
 const interest=chartAxis([{unit:'percent',history:[{value:12},{value:19}]}]);
 assert.deepEqual(interest.ticks.map(n=>axisLabel(n,interest,'percent')),['10.0%','12.5%','15.0%','17.5%','20.0%']);
 const negative=chartAxis([{unit:'percent',history:[{value:-3},{value:5}]}]);assert.ok(negative.min<=-3);assert.ok(negative.max>=5);
 const constant=chartAxis([{unit:'percent',history:[{value:0}]}]);assert.equal(constant.min,0);assert.ok(constant.max>0);assert.ok(constant.ticks.every(Number.isFinite));
 const dollars=chartAxis([{unit:'dollars',history:[{value:1e12},{value:4e12}]}]);assert.ok(dollars.ticks.map(n=>axisLabel(n,dollars,'dollars')).every(label=>/^\$[\d,.]+T$/.test(label)));
 assert.equal(chartAxis([{unit:'percent',history:[]}]),null);
});
test('comparison readout preserves legend order and reports exact-date values for both measures',()=>{
 const series=[{label:'Top 1%',unit:'percent',status:'ok',history:[{date:'2026-04-01',value:32.5}]},{label:'Bottom 50%',unit:'percent',status:'stale',history:[{date:'2026-04-01',value:2.5}]}];
 assert.deepEqual(readoutValues(series,'2026-04-01').map(p=>[p.label,p.value,p.status]),[['Top 1%',32.5,'ok'],['Bottom 50%',2.5,'stale']]);
 assert.deepEqual(readoutValues(series,'2026-07-01').map(p=>p.value),[null,null]);
 assert.equal(readoutValues([series[0]],'2026-04-01').length,1);
});
test('empty charts distinguish fetch failure, unavailable sources, short periods and incompatible dates',()=>{
 const full=[{history:[{date:'2026-04-01',value:2}]}],empty=[{history:[]}];
 assert.equal(emptyChartState({loaded:false,loading:true,sourceSeries:empty,rangedSeries:empty,compared:false}).kind,'loading');
 const failure=emptyChartState({loaded:false,loading:false,sourceSeries:empty,rangedSeries:empty,compared:false});assert.equal(failure.kind,'unavailable');assert.ok(!failure.message.includes('comparison'));
 assert.equal(emptyChartState({loaded:true,sourceSeries:empty,rangedSeries:empty,compared:true}).kind,'unavailable');
 assert.equal(emptyChartState({loaded:true,sourceSeries:full,rangedSeries:empty,compared:false}).kind,'period');
 assert.equal(emptyChartState({loaded:true,sourceSeries:full,rangedSeries:full,compared:true}).kind,'overlap');
});
