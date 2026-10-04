// Pure explorer state: no fetches, browser globals, inferred values or rebasing.
export const PRESETS=Object.freeze([
 {id:'wealth',label:'Who holds the wealth?',selected:'top1WealthShare',compare:'bottom50WealthShare',period:'5y',context:'Share of aggregate net worth held by wealth-ranked households: the top 1% and bottom 50% are unequal population groups. The remaining 49% is not plotted. A share can fall while dollar wealth rises; group membership can change. These official estimates combine surveys and financial accounts, with interpolation and forecasts between survey observations.'},
 {id:'payments',label:'How heavy are household payments?',selected:'householdDebtServiceRatio',compare:'',period:'5y',context:'Required household debt payments as a percentage of aggregate disposable personal income. Mortgage payments include bundled escrow. This is an economy-wide ratio, not a typical household’s bill or an individual borrower’s debt-to-income ratio. The current credit-bureau method begins in 2005.'},
 {id:'interest',label:'How much revenue goes to interest?',selected:'interestShareOfReceipts',compare:'',period:'5y',context:'Federal net interest outlays divided by federal receipts for the identical fiscal year, multiplied by 100. Both inputs must have the same fiscal-year date and receipts must be positive. This compares scale; it does not mean a particular dollar of revenue is earmarked for interest.'},
]);
const defaults={selected:'totalDebt',compare:'',period:'1y',perPerson:false,question:''};
const periods=new Set(['3m','1y','5y','all']);
function legacyBasis(m){const frequency=(m.frequency||'').toLowerCase();if(frequency.includes('annual rate'))return 'annual-rate';if(frequency.includes('fiscal year'))return 'fiscal-year';if(['monthlyReceipts','monthlyOutlays','monthlyDeficit'].includes(m.id))return 'monthly-flow';return 'level';}
export function canCompare(a,b){if(!a||!b||a.id===b.id||a.unit!==b.unit)return false;if(a.comparisonFamily||b.comparisonFamily)return !!a.comparisonFamily&&a.comparisonFamily===b.comparisonFamily;return legacyBasis(a)===legacyBasis(b);}
export function canUsePerPerson(m){return m?.unit==='dollars';}
function normalize(view,catalog){const find=id=>catalog.find(m=>m.id===id),result={...defaults,...view};if(!find(result.selected))result.selected=defaults.selected;if(!canCompare(find(result.selected),find(result.compare)))result.compare='';if(!periods.has(result.period))result.period='1y';result.perPerson=!!result.perPerson&&canUsePerPerson(find(result.selected));const p=PRESETS.find(p=>p.id===result.question);if(!p||p.selected!==result.selected||p.compare!==result.compare||p.period!==result.period||result.perPerson)result.question='';return result;}
export function parseView(search,catalog){const params=new URLSearchParams(search),one=key=>params.getAll(key).length===1?params.get(key):null;const preset=PRESETS.find(p=>p.id===one('question'));const view={...defaults,...(preset?{selected:preset.selected,compare:preset.compare,period:preset.period,question:preset.id}:{})};for(const [key,field]of [['metric','selected'],['compare','compare'],['period','period']]){const value=one(key);if(value!==null)view[field]=value;}if(one('perperson')!==null)view.perPerson=one('perperson')==='1';return normalize(view,catalog);}
export function viewQuery(view){const params=new URLSearchParams({metric:view.selected,compare:view.compare,period:view.period,perperson:view.perPerson?'1':'0'});if(view.question)params.set('question',view.question);return `?${params}`;}
export function changeView(view,patch,catalog){return normalize({...view,...patch,question:patch.question??''},catalog);}
export function comparisonCoverage(series){if(series.length<2)return {series,window:null};const dates=new Set(series[0].history.map(p=>p.date));for(const s of series.slice(1)){const available=new Set(s.history.map(p=>p.date));for(const date of dates)if(!available.has(date))dates.delete(date);}const common=[...dates].sort();return {series:series.map(s=>({...s,history:s.history.filter(p=>dates.has(p.date))})),window:common.length?{start:common[0],end:common.at(-1)}:null};}
export function validSnapshot(data,catalog){const known=new Set(catalog.map(m=>m.id));return data?.version===1&&data.catalog==='expanded'&&Array.isArray(data.metrics)&&data.metrics.length===known.size&&new Set(data.metrics.map(m=>m?.id)).size===known.size&&Array.isArray(data.sources)&&data.metrics.every(m=>m&&known.has(m.id)&&Array.isArray(m.history));}

export function measurementBasis(m){if(['dollars','dollarsPerPerson','dollarsPerSecond'].includes(m.unit))return 'Nominal · not adjusted for inflation';if(m.unit==='realDollars1982_84PerWeek')return 'Real · 1982–84 purchasing power';if(m.unit==='index')return `Index${m.basis?` · ${m.basis}`:''}`;return '';}

// Fit reported values to legible ticks. A nonnegative percentage never acquires a
// negative floor from padding; genuine negative observations remain visible.
export function chartAxis(series){
 const values=series.flatMap(s=>s.history.map(p=>p.value)).filter(Number.isFinite);
 if(!values.length)return null;
 const lo=Math.min(...values),hi=Math.max(...values),padding=(hi-lo)*.1||Math.max(Math.abs(hi)*.025,1);
 let lower=lo-padding,upper=hi+padding;
 if(series.every(s=>s.unit==='percent')&&lo>=0)lower=Math.max(0,lower);
 const rough=(upper-lower)/4,power=10**Math.floor(Math.log10(rough));
 const step=([1,2,2.5,5,10].find(n=>n*power>=rough)||10)*power;
 const clean=n=>Number(n.toPrecision(12));
 const min=clean(Math.floor(lower/step)*step),max=clean(Math.ceil(upper/step)*step);
 const ticks=Array.from({length:Math.round((max-min)/step)+1},(_,i)=>clean(min+i*step));
 return {min,max,step,ticks};
}
export function axisLabel(value,axis,unit){
 const magnitude=Math.max(Math.abs(axis.min),Math.abs(axis.max));
 const [scale,suffix]=unit==='percent'?[1,'%']:magnitude>=1e12?[1e12,'T']:magnitude>=1e9?[1e9,'B']:magnitude>=1e6?[1e6,'M']:magnitude>=1e3?[1e3,'K']:[1,''];
 const step=axis.step/scale;let decimals=0;
 while(decimals<12&&Math.abs(Number(step.toFixed(decimals))-step)>Math.abs(step)*1e-9)decimals++;
 const amount=new Intl.NumberFormat('en-US',{minimumFractionDigits:decimals,maximumFractionDigits:decimals}).format(value/scale);
 const currency=unit.startsWith('dollars')||unit==='realDollars1982_84PerWeek';
 return `${currency?'$':''}${amount}${suffix}`;
}
export function readoutValues(series,date){return series.map(s=>({label:s.label,unit:s.unit,status:s.status,value:s.history.find(p=>p.date===date)?.value??null}));}
export function emptyChartState({loaded,loading=false,sourceSeries,rangedSeries,compared}){
 if(loading&&!loaded)return {kind:'loading',message:'Gathering published history…'};
 if(!loaded)return {kind:'unavailable',message:'The figures did not load. Nothing is substituted. Use Refresh sources to try again in a minute.'};
 if(sourceSeries.some(s=>!s.history.length))return {kind:'unavailable',message:'Published history is unavailable for one or more selected measures. Nothing is substituted. Use Refresh sources to try again, or choose an available measure.'};
 if(rangedSeries.some(s=>!s.history.length))return {kind:'period',message:'No observations for one or more selected measures in this period. Try a longer period.'};
 if(compared)return {kind:'overlap',message:'No matching reported dates in this period. Try a longer period or remove the comparison. No values are interpolated or backfilled.'};
 return {kind:'period',message:'No observations in this period. Try a longer period.'};
}
