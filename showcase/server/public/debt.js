import {estimateClock} from './debt-estimate.js?v=20261004d';
import {EXPANDED_METRICS as METRICS,EXPANDED_GROUPS as GROUPS,UNIT_LABELS,formatValue,perPersonHistory,rangeHistory,csvForSeries} from './debt-metrics.js?v=20261004d';
import {PRESETS,canCompare,canUsePerPerson,parseView,viewQuery,changeView,comparisonCoverage,validSnapshot,measurementBasis,chartAxis,axisLabel,readoutValues,emptyChartState} from './debt-discovery.js?v=20261004d';
const $=id=>document.getElementById(id), esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const known=new Set(METRICS.map(m=>m.id));
const reducedMotion=matchMedia('(prefers-reduced-motion: reduce)');
const state={snapshot:null,selected:'totalDebt',compare:'',period:'1y',perPerson:false,question:'',pins:new Set(),query:'',paused:reducedMotion.matches,loading:false};
const headlineClock=estimateClock({now:()=>performance.now(),paused:state.paused});
let refreshedAt=null;
const officialDollars=new Intl.NumberFormat('en-US',{style:'currency',currency:'USD',minimumFractionDigits:2,maximumFractionDigits:2});
try{const p=JSON.parse(localStorage.getItem('prismet.debt.pins')||'[]');if(Array.isArray(p))state.pins=new Set(p.filter(id=>known.has(id)));}catch{}
Object.assign(state,parseView(location.search,METRICS));
const metrics=()=>state.snapshot?.metrics||METRICS.map(m=>({...m,status:'missing',value:null,history:[],observedAt:null,derived:!m.series}));
const metric=id=>metrics().find(m=>m.id===id), statusText=m=>m.status==='stale'?'Stale observation':m.status==='missing'?'Unavailable':m.sourceEstimated?'Official estimate':m.derived?'Derived':'Observed';
const dateText=d=>d||'No observation';
function safeURL(v){try{const u=new URL(v);return u.protocol==='https:'?u.href:'#';}catch{return '#';}}
function announce(t){$('live').textContent='';requestAnimationFrame(()=>$('live').textContent=t);}
function setTheme(v){document.documentElement.dataset.theme=v;$('theme').textContent=v==='dark'?'Light theme':'Dark theme';try{localStorage.setItem('prismet.debt.theme',v);}catch{}}
let theme;try{theme=localStorage.getItem('prismet.debt.theme');}catch{}setTheme(['light','dark'].includes(theme)?theme:matchMedia('(prefers-color-scheme: light)').matches?'light':'dark');
$('theme').addEventListener('click',()=>setTheme(document.documentElement.dataset.theme==='dark'?'light':'dark'));
function selectOptions(){
 const all=metrics(),s=metric(state.selected);
 $('metric-select').innerHTML=GROUPS.map(g=>`<optgroup label="${esc(g.label)}">${all.filter(m=>m.group===g.id).map(m=>`<option value="${m.id}">${esc(m.label)}</option>`).join('')}</optgroup>`).join('');$('metric-select').value=s.id;
 const alternatives=all.filter(m=>canCompare(s,m));if(!alternatives.some(m=>m.id===state.compare))state.compare='';
 $('compare-select').innerHTML='<option value="">No comparison</option>'+alternatives.map(m=>`<option value="${m.id}">${esc(m.label)}</option>`).join('');$('compare-select').value=state.compare;
 const canDivide=canUsePerPerson(s)&&metric('population').history.length>0;$('per-capita').disabled=!canDivide;$('per-capita').title=canDivide?'Divide by dated population observations':'Requires a dollar measure and population history';if(!canUsePerPerson(s))state.perPerson=false;$('per-capita').checked=state.perPerson;$('period').value=state.period;
}
function renderHeadline(){
 const projected=headlineClock.read(),debt=metric('totalDebt'),rate=metric('debtGrowthPerSecond');
 $('debt').textContent=formatValue(projected.value,'dollars');
 $('debt-estimate-label').textContent=projected.estimated?`Illustrative estimate since restart · ${state.paused?'paused':'ticking'}`:'Official balance · estimate unavailable';
 $('debt-date').textContent=debt.value===null?'Treasury unavailable. No balance substituted.':`Official Treasury balance: ${officialDollars.format(debt.value)} · ${dateText(debt.observedAt)}${debt.status==='stale'?' · stale observation':''}`;
 const window=rate.method==='fallback'?`${rate.windowDays}-day fallback rate; annual baseline unavailable`:rate.method==='trailingYear'?`trailing-year rate (${rate.windowDays} days)`:'historical rate';
 $('debt-estimate-method').textContent=projected.estimated?`Latest official balance + ${formatValue(rate.value,'dollarsPerSecond')} ${window} × running time since the illustration restarted. Illustration restarted ${refreshedAt}.${rate.status==='stale'?' Rate uses stale observations.':''} This is not a measured current balance. Charts and register remain observed values.`:'A valid published balance and growth rate are required to illustrate change. Charts and register remain observed values.';
}
function motionControl(){ $('motion').setAttribute('aria-pressed',String(state.paused));$('motion').textContent=state.paused?'Resume ticking & updates':'Pause ticking & updates'; }
function setPaused(value){state.paused=!!value;headlineClock.pause(state.paused);motionControl();renderHeadline();}
function overview(){const m=metric('totalDebt'),rate=metric('debtGrowthPerSecond');headlineClock.reset(m.value,rate.status==='missing'?null:rate.value);refreshedAt=new Date().toLocaleTimeString('en-US',{hour:'numeric',minute:'2-digit',second:'2-digit'});renderHeadline();for(const [id,k] of [['debt-person','debtPerCitizen'],['debt-gdp','debtToGDP'],['debt-rate','debtGrowthPerSecond']]){const v=metric(k);$(id).textContent=formatValue(v.value,v.unit);$(id).title=`${statusText(v)} · ${dateText(v.observedAt)}`;}}

function updatePin(){const p=state.pins.has(state.selected);$('pin-current').setAttribute('aria-pressed',String(p));$('pin-current').textContent=p?'Unpin metric':'Pin metric';}
function togglePin(id){const restore=document.activeElement?.dataset?.pin;if(state.pins.has(id))state.pins.delete(id);else state.pins.add(id);try{localStorage.setItem('prismet.debt.pins',JSON.stringify([...state.pins]));}catch{announce('Pins last for this visit. Browser storage is unavailable.');}renderRegister();updatePin();if(restore)$('register').querySelector(`[data-pin="${restore}"]`)?.focus();}
function renderRegister(){const all=metrics(),q=state.query.toLowerCase().trim();let count=0;
 $('pins').innerHTML=[...state.pins].map(id=>`<button type="button" data-select="${id}">★ ${esc(metric(id).label)}</button>`).join('');
 $('metric-groups').innerHTML=GROUPS.map((g,i)=>{const found=all.filter(m=>m.group===g.id&&`${m.label} ${g.label} ${m.note}`.toLowerCase().includes(q));count+=found.length;if(!found.length)return '';
 return `<section class="metric-group" aria-labelledby="group-${g.id}"><div class="group-head"><span>${String(i+1).padStart(2,'0')}</span><h3 id="group-${g.id}">${esc(g.label)}</h3></div>${found.map(m=>`<div class="metric-row${m.id===state.selected?' current':''}"><button class="metric-open" type="button" data-select="${m.id}" aria-label="Explore ${esc(m.label)}">${esc(m.label)}</button><span class="metric-value">${esc(formatValue(m.value,m.unit,{compact:true}))}</span><span class="metric-meta"><span class="state-${m.status}">${esc(statusText(m))}${m.derived&&m.status!=='ok'?' · derived':''}</span><span>${esc(dateText(m.observedAt))} · ${esc(m.frequency)}</span></span><button class="pin" type="button" data-pin="${m.id}" aria-label="${state.pins.has(m.id)?'Unpin':'Pin'} ${esc(m.label)}" aria-pressed="${state.pins.has(m.id)}">${state.pins.has(m.id)?'★':'☆'}</button></div>`).join('')}</section>`;}).join('');
 $('register-count').textContent=`${count} of ${METRICS.length} measures. Select any row to explore its history.`;$('search-empty').hidden=count>0;
}
function displaySeries(align=true){const series=[state.selected,state.compare].filter(Boolean).map(id=>{const m=metric(id);let h=m.history||[],unit=m.unit,label=m.label;if(state.perPerson){h=perPersonHistory(h,metric('population').history);unit='dollarsPerPerson';label+=' · per person';}const end=metric(state.selected).history.at(-1)?.date||new Date().toISOString().slice(0,10);return {...m,label,unit,derived:m.derived||state.perPerson,estimated:m.estimated||state.perPerson,status:state.perPerson&&metric('population').status==='stale'&&m.status==='ok'?'stale':m.status,history:rangeHistory(h,state.period,end)};});return align?comparisonCoverage(series).series:series;}
function drawChart(container,readout,series){const all=series.flatMap(s=>s.history);container.replaceChildren();
 if(!all.length){
  const sourceSeries=[state.selected,state.compare,...(state.perPerson?['population']:[])].filter(Boolean).map(metric);
  const empty=emptyChartState({loaded:!!state.snapshot,loading:state.loading,sourceSeries,rangedSeries:displaySeries(false),compared:!!state.compare});
  const p=document.createElement('p');p.className='empty';p.textContent=empty.message;
  if(empty.kind==='unavailable'){const button=document.createElement('button');button.type='button';button.className='quiet';button.textContent='Refresh sources';button.disabled=state.loading;button.addEventListener('click',()=>load(true));p.append(button);}
  container.append(p);$(readout).textContent='No observation available.';return null;
 }
 const w=Math.max(300,container.clientWidth||900),h=Math.max(240,container.clientHeight||330),left=w<500?72:88,right=18,top=24,bottom=38;
 const points=all.map(p=>[Date.parse(p.date),p.value]),tmin=Math.min(...points.map(p=>p[0])),tmax=Math.max(...points.map(p=>p[0]));
 const axis=chartAxis(series),{min,max}=axis;
 const x=t=>left+(t-tmin)/(tmax-tmin||1)*(w-left-right),y=v=>top+(max-v)/(max-min)*(h-top-bottom);
 const ticks=axis.ticks.map(v=>{const py=y(v);return `<line class="grid-line" x1="${left}" x2="${w-right}" y1="${py}" y2="${py}"/><text x="${left-10}" y="${py+4}" text-anchor="end">${esc(axisLabel(v,axis,series[0].unit))}</text>`;}).join('');
 const paths=series.map((s,i)=>`<polyline class="series-line${i?' compare':''}" points="${s.history.map(p=>`${x(Date.parse(p.date))},${y(p.value)}`).join(' ')}"/>${(s.history.length===1||s.history.length<30&&w>=500)?s.history.map(p=>`<circle class="point${i?' compare':''}" cx="${x(Date.parse(p.date))}" cy="${y(p.value)}" r="3"/>`).join(''):''}`).join('');const date=t=>new Date(t).toISOString().slice(0,10);
 container.innerHTML=`<svg viewBox="0 0 ${w} ${h}" role="img" tabindex="0" aria-label="${esc(series.map(s=>s.label).join(' compared with '))}. ${all.length} published points. Use left and right arrows to inspect." aria-describedby="${readout}"><title>${esc(series[0].label)}: published history</title>${ticks}${paths}<text x="${left}" y="${h-8}">${date(tmin)}</text><text x="${w-right}" y="${h-8}" text-anchor="end">${date(tmax)}</text></svg>`;
 const svg=container.querySelector('svg'),primary=series[0].history.length?series[0]:series.find(s=>s.history.length);let index=primary.history.length-1;
 function read(i){
  index=Math.max(0,Math.min(primary.history.length-1,i));const p=primary.history[index],output=$(readout);output.textContent=p.date;
  readoutValues(series,p.date).forEach((value,i)=>{const span=document.createElement('span');span.className=`readout-series${i?' compare':''}`;span.textContent=`${value.label}: ${formatValue(value.value,value.unit)}${value.status==='stale'?' · stale series':''}`;output.append(' · ',span);});
 }
 svg.addEventListener('keydown',e=>{const k=e['key'];if(['ArrowLeft','ArrowRight','Home','End'].includes(k)){e.preventDefault();read(k==='Home'?0:k==='End'?primary.history.length-1:index+(k==='ArrowLeft'?-1:1));announce($(readout).textContent);}});
 svg.addEventListener('pointermove',e=>{const b=svg.getBoundingClientRect(),px=(e.clientX-b.left)*w/b.width,d=tmin+(px-left)/(w-left-right)*(tmax-tmin);let best=0;for(let i=1;i<primary.history.length;i++)if(Math.abs(Date.parse(primary.history[i].date)-d)<Math.abs(Date.parse(primary.history[best].date)-d))best=i;read(best);});read(index);return axis;
}
function renderDetail(series){
 $('metric-detail').innerHTML=series.map(m=>`<div class="metric-method"><h3>${esc(m.label)}</h3><p>${esc(statusText(m))} · ${esc(m.frequency)} · ${esc(UNIT_LABELS[m.unit])}. ${esc(m.note||'Latest published observations; values may be revised by the source.')}</p><p>${m.sourceUrl?`<a href="${esc(safeURL(m.sourceUrl))}" target="_blank" rel="noopener">${esc(m.source||'Official source')} ↗</a>`:'Calculated from official observations'} · Observation: ${esc(dateText(m.observedAt))}${m.fetchedAt?` · Retrieved: ${esc(m.fetchedAt.slice(0,16).replace('T',' '))} UTC`:''}</p>${m.inputDates?`<p>Input dates: ${Object.entries(m.inputDates).map(([id,d])=>`${esc(metric(id)?.label||id)}: ${esc(d)}`).join(' · ')}</p>`:''}${m.sources?`<p>Calculation inputs: ${m.sources.map(s=>`<a href="${esc(safeURL(s.url))}" target="_blank" rel="noopener">${esc(metric(s.id)?.label||s.id)}</a>`).join(' · ')}</p>`:''}${state.perPerson?'<p>Population is matched at or before each chart date. Exact input dates are included in the CSV.</p>':''}</div>`).join('');
 $('chart-table').innerHTML=series.map(m=>`<table><caption>${esc(m.label)} · ${esc(UNIT_LABELS[m.unit])} · ${m.history.length} observations</caption><thead><tr><th scope="col">Observation date</th><th scope="col">Value</th></tr></thead><tbody>${m.history.map(p=>`<tr><td>${esc(p.date)}</td><td>${esc(formatValue(p.value,m.unit))}</td></tr>`).join('')||'<tr><td colspan="2">No observations in this period.</td></tr>'}</tbody></table>`).join('');
}
function renderDiscovery(series){
 const preset=PRESETS.find(p=>p.id===state.question);
 document.querySelectorAll('[data-question]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.question===state.question)));
 $('clear-question').hidden=!preset;
 $('question-context').hidden=!preset;$('question-context').textContent=preset?.context||'';
 $('measurement-context').innerHTML=series.map(m=>`<div><strong>${esc(m.label)}</strong><p>${esc(UNIT_LABELS[m.unit]||m.unit)} · ${esc(m.frequency)}${measurementBasis(m)?` · ${esc(measurementBasis(m))}`:''}${m.basis&&m.unit!=='index'?` · ${esc(m.basis)}`:''}${m.seasonalAdjustment?` · ${esc(m.seasonalAdjustment)}`:''}</p><p>${esc(statusText(m))} · Latest reported period: ${esc(dateText(m.observedAt))}${m.sourceUrl?` · <a href="${esc(safeURL(m.sourceUrl))}" target="_blank" rel="noopener">${esc(m.source||'Source')} ↗</a>`:''}</p>${!preset&&m.note?`<p>${esc(m.note)}</p>`:''}</div>`).join('');
}
function renderChart(){const selected=metric(state.selected),series=displaySeries();$('chart-title').textContent=selected.label;
 renderDiscovery(series);
 $('chart-legend').innerHTML=series.map((s,i)=>`<span class="legend-key${i?' compare':''}">${esc(s.label)} <span class="muted">· ${esc(statusText(s))}</span></span>`).join('');const axis=drawChart($('chart'),'chart-readout',series);
 const first=series[0].history[0],last=series[0].history.at(-1);
 $('chart-caption').textContent=`${first?`${series[0].history.length} ${state.compare?'matching reported dates':'observations'} · ${first.date} to ${last.date}. `:'No plotted observations. '}${selected.frequency}. ${UNIT_LABELS[series[0].unit]}. ${state.compare?'Only dates present in both series are plotted and exported; no interpolation or backfilling.':'Range ends at the selected series’ latest observation.'} ${axis?`Vertical axis fits the displayed range${axis.min!==0?' and does not start at zero':''}. `:''}Lines connect published values; this explorer adds no projected points. Period dates are not publication dates.${state.compare?' Reporting frequencies and seasonal adjustments may differ; see the measurement notes.':''}`;
 $('export-csv').disabled=!series.some(s=>s.history.length);$('expand-chart').disabled=!series.some(s=>s.history.length);renderDetail(series);updatePin();if($('chart-dialog').open){$('dialog-title').textContent=selected.label;drawChart($('dialog-chart'),'dialog-readout',series);}
}
function syncURL(){try{const u=new URL(location.href);u.search=viewQuery(state);history.replaceState(null,'',u);}catch{}}
function updateView(patch){Object.assign(state,changeView(state,patch,METRICS));selectOptions();renderChart();renderRegister();syncURL();}
function renderSources(){$('source-list').innerHTML=(state.snapshot?.sources||[]).map(s=>`<div class="source-row"><a href="${esc(safeURL(s.url))}" target="_blank" rel="noopener">${esc(s.label)} ↗</a><span class="source-state ${s.status}">${s.status==='ok'?'Responded':s.status==='stale'?'Cached · refresh failed':'Unavailable'}</span>${s.error?`<small>${esc(s.error)}${s.status==='stale'?'. Last valid observations retained.':''}</small>`:''}</div>`).join('');}
function choose(id,scroll=false){if(!known.has(id))return;updateView({selected:id,compare:''});if(scroll){$('chart-title').scrollIntoView({behavior:reducedMotion.matches?'instant':'smooth',block:'start'});$('metric-select').focus({preventScroll:true});}}
async function load(force=false){if(state.loading)return;state.loading=true;$('refresh').disabled=true;$('refresh').textContent='Refreshing…';$('status').textContent='Gathering official observations. Sources refresh independently.';renderChart();
 try{const r=await fetch(`/api/debt?catalog=expanded${force?'&refresh=1':''}`,{signal:AbortSignal.timeout(100000),headers:{Accept:'application/json'}});if(!r.ok)throw new Error(r.status===429?(force?'Refresh limit reached. Try again in a minute.':'The data service is busy. Try again in a minute.'):`Data service returned HTTP ${r.status}.`);const data=await r.json();
 if(!validSnapshot(data,METRICS))throw new Error('Unexpected data-service response.');
 if(state.paused&&!force&&state.snapshot){$('status').textContent='Ticking and automatic updates are paused. Manual refresh remains available.';return;}
 state.snapshot=data;overview();selectOptions();renderChart();renderRegister();renderSources();const ok=data.metrics.filter(m=>m.status==='ok').length,stale=data.metrics.filter(m=>m.status==='stale').length,missing=data.metrics.filter(m=>m.status==='missing').length;
 $('status').textContent=`${ok} current · ${stale} stale · ${missing} unavailable. Checked ${new Date(data.fetchedAt).toLocaleTimeString('en-US',{hour:'numeric',minute:'2-digit'})}.${state.paused?' Automatic updates paused.':''}`;if(force)announce(`Sources refreshed. ${ok} current, ${stale} stale and ${missing} unavailable measures.`);
 }catch(e){$('status').textContent=`${e.message} ${state.snapshot?'Previously loaded observations remain visible with their original dates.':'No fallback figures substituted.'}`;announce($('status').textContent);}finally{state.loading=false;$('refresh').disabled=false;$('refresh').textContent='Refresh sources';renderChart();}}
$('question-buttons').innerHTML=PRESETS.map(p=>`<button type="button" data-question="${p.id}" aria-pressed="false">${esc(p.label)}</button>`).join('');
$('question-buttons').addEventListener('click',event=>{const button=event.target.closest('[data-question]'),preset=PRESETS.find(p=>p.id===button?.dataset.question);if(preset){updateView({selected:preset.selected,compare:preset.compare,period:preset.period,perPerson:false,question:preset.id});announce(`${preset.label} Chart and measurement notes updated.`);}});
$('clear-question').addEventListener('click',()=>{updateView({question:''});$('metric-select').focus();announce('Question cleared. Current chart remains available for manual exploration.');});
addEventListener('popstate',()=>{Object.assign(state,parseView(location.search,METRICS));selectOptions();renderChart();renderRegister();});
document.querySelectorAll('[data-metric-count]').forEach(element=>element.textContent=METRICS.length);
$('group-count').textContent=`${GROUPS.length} perspectives`;
$('refresh').addEventListener('click',()=>load(true));
$('motion').addEventListener('click',()=>{setPaused(!state.paused);announce(state.paused?'Estimate ticking and automatic updates paused. Manual refresh remains available.':'Estimate ticking and automatic updates resumed. Paused time is excluded.');});
reducedMotion.addEventListener('change',event=>{if(event.matches){setPaused(true);announce('Reduced motion enabled. Estimate ticking and automatic updates paused.');}});
$('announce').addEventListener('click',()=>{const m=metric(state.selected),estimate=headlineClock.read();announce(`${m.id==='totalDebt'&&estimate.estimated?`Illustrative estimate: ${formatValue(estimate.value,'dollars')}. `:''}${m.label}: ${formatValue(m.value,m.unit)}. ${statusText(m)}. Observation ${dateText(m.observedAt)}. ${m.note}`);});
$('metric-select').addEventListener('change',e=>choose(e.target.value));$('compare-select').addEventListener('change',e=>{updateView({compare:e.target.value});});$('period').addEventListener('change',e=>{updateView({period:e.target.value});});$('per-capita').addEventListener('change',e=>{updateView({perPerson:e.target.checked});});$('metric-search').addEventListener('input',e=>{state.query=e.target.value;renderRegister();});$('pin-current').addEventListener('click',()=>togglePin(state.selected));
$('register').addEventListener('click',e=>{const p=e.target.closest('[data-pin]'),s=e.target.closest('[data-select]');if(p)togglePin(p.dataset.pin);else if(s)choose(s.dataset.select,true);});
$('expand-chart').addEventListener('click',()=>{$('dialog-title').textContent=metric(state.selected).label;$('chart-dialog').showModal();drawChart($('dialog-chart'),'dialog-readout',displaySeries());$('close-chart').focus();});$('close-chart').addEventListener('click',()=>$('chart-dialog').close());$('chart-dialog').addEventListener('close',()=>$('expand-chart').focus());
$('export-csv').addEventListener('click',()=>{const b=new Blob([csvForSeries(displaySeries())],{type:'text/csv;charset=utf-8'}),u=URL.createObjectURL(b),a=document.createElement('a');a.href=u;a.download=`prismet-${state.selected}-${state.period}${state.perPerson?'-per-person':''}.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(u),1000);});
let resizeTimer;new ResizeObserver(()=>{clearTimeout(resizeTimer);resizeTimer=setTimeout(renderChart,100);}).observe($('chart'));
setInterval(()=>{if(!state.paused&&!document.hidden)load();},6*60*60*1000);
setInterval(()=>{if(!state.paused&&!document.hidden&&state.snapshot)renderHeadline();},1000);
motionControl();selectOptions();renderRegister();renderChart();syncURL();load();
