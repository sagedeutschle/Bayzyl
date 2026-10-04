import { METRICS, GROUPS, atOrBefore } from './public/debt-metrics.js';

const TREASURY='https://api.fiscaldata.treasury.gov/services/api/fiscal_service/';
const BLS='https://api.bls.gov/publicAPI/v2/timeseries/data/';
const DAY=86400000;
const BLS_IDS=METRICS.filter(m=>/^(LNS|CUUR)/.test(m.series||'')).map(m=>m.series);
const FRED=METRICS.filter(m=>m.series && !m.series.startsWith('treasury-') && !BLS_IDS.includes(m.series));
const treasuryDatasets={
  'treasury-debt':['v2/accounting/od/debt_to_penny','Debt to the Penny','https://fiscaldata.treasury.gov/datasets/debt-to-the-penny/'],
  'treasury-interest':['v2/accounting/od/avg_interest_rates','Treasury average interest rates','https://fiscaldata.treasury.gov/datasets/average-interest-rates-treasury-securities/'],
  'treasury-cash':['v1/accounting/dts/operating_cash_balance','Treasury General Account','https://fiscaldata.treasury.gov/datasets/daily-treasury-statement/'],
  'treasury-gold':['v2/accounting/od/gold_reserve','U.S. Treasury gold reserve','https://fiscaldata.treasury.gov/datasets/status-report-government-gold-reserve/'],
  'treasury-limit':['v1/accounting/dts/debt_subject_to_limit','Debt subject to limit','https://fiscaldata.treasury.gov/datasets/daily-treasury-statement/'],
};
export const SOURCE_DEFINITIONS=[
  ...Object.entries(treasuryDatasets).map(([id,[,label,url]])=>({id,label,url})),
  ...FRED.map(m=>({id:m.series,label:`FRED · ${m.series}`,url:`https://fred.stlouisfed.org/series/${m.series}`})),
  ...BLS_IDS.map(id=>({id,label:`BLS · ${id}`,url:`https://data.bls.gov/timeseries/${id}`})),
];

export function number(value) {
  if(typeof value!=='string' && typeof value!=='number') return null;
  const raw=String(value).trim();
  // Accept finite decimal/scientific source values, with strict thousands grouping.
  if(!/^[+-]?(?:(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(raw)) return null;
  const result=Number(raw.replaceAll(',',''));
  return Number.isFinite(result)?result:null;
}
export function validDate(value) {
  return typeof value==='string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value+'T00:00:00Z').toISOString().slice(0,10)===value;
}
export function cleanHistory(points) {
  const byDate=new Map();
  for(const point of points) if(validDate(point.date)&&Number.isFinite(point.value)) byDate.set(point.date,point);
  return [...byDate.values()].sort((a,b)=>a.date.localeCompare(b.date)).slice(-3000);
}
export function parseFredCSV(text, series, scale=1) {
  const lines=text.trim().split(/\r?\n/);
  const headers=lines.shift()?.replace(/^\uFEFF/,'').split(',');
  if(!headers || !['DATE','observation_date'].includes(headers[0]) || headers[1]!==series) throw new Error('Unexpected series format');
  return cleanHistory(lines.map(line=>{const [date,raw]=line.split(',');const v=number(raw);return {date,value:v===null?null:v*scale};}));
}
export function parseBLS(json) {
  if(json.status!=='REQUEST_SUCCEEDED') throw new Error('BLS request unavailable');
  const result={};
  for(const s of json.Results?.series||[]) {
    const def=METRICS.find(m=>m.series===s.seriesID);
    if(!def) continue;
    result[s.seriesID]=cleanHistory((s.data||[]).flatMap(p=>{
      if(!/^M(0[1-9]|1[0-2])$/.test(p.period)) return [];
      const v=number(p.value);
      return [{date:`${p.year}-${p.period.slice(1)}-01`,value:v===null?null:v*def.scale}];
    }));
  }
  return result;
}
export function parseTreasury(id, json) {
  if(!Array.isArray(json.data)) throw new Error('Unexpected Treasury format');
  const rows=json.data;
  const direct=(field,scale=1,filter=()=>true)=>cleanHistory(rows.filter(filter).map(r=>({date:r.record_date,value:number(r[field])===null?null:number(r[field])*scale})));
  if(id==='treasury-debt') return {totalDebt:direct('tot_pub_debt_out_amt'),debtHeldByPublic:direct('debt_held_public_amt'),intragovernmentalHoldings:direct('intragov_hold_amt')};
  if(id==='treasury-interest') return {averageInterestRate:direct('avg_interest_rate_amt',1,r=>r.security_desc==='Total Interest-bearing Debt')};
  if(id==='treasury-cash') return {treasuryGeneralAccount:cleanHistory(rows.filter(r=>r.account_type==='Treasury General Account (TGA) Closing Balance').map(r=>({date:r.record_date,value:(number(r.close_today_bal)??number(r.open_today_bal))===null?null:(number(r.close_today_bal)??number(r.open_today_bal))*1e6})))};
  const days=new Map();
  for(const r of rows){if(!days.has(r.record_date))days.set(r.record_date,[]);days.get(r.record_date).push(r);}
  if(id==='treasury-gold') {
    const ounces=[],book=[];
    for(const [date,rs] of days) {
      // Eight published detail lines; never sum a truncated page boundary.
      if(rs.length!==8 || new Set(rs.map(r=>r.src_line_nbr)).size!==8) continue;
      const o=rs.map(r=>number(r.fine_troy_ounce_qty)),b=rs.map(r=>number(r.book_value_amt));
      if(o.some(v=>v===null)||b.some(v=>v===null))continue;
      ounces.push({date,value:o.reduce((a,b)=>a+b,0)});book.push({date,value:b.reduce((a,b)=>a+b,0)});
    }
    return {goldReserveOunces:cleanHistory(ounces),goldReserveBookValue:cleanHistory(book)};
  }
  if(id==='treasury-limit') {
    const points=[];
    for(const [date,rs] of days) {
      const categories=new Set(rs.map(r=>r.debt_catg));
      // A full page can end partway through its oldest date; never sum that boundary.
      if(rows.length>=5000 && date===rows.at(-1)?.record_date)continue;
      if(!['Debt Held by the Public','Intragovernmental Holdings'].every(c=>categories.has(c)))continue;
      let total=0,valid=true;
      for(const r of rs) {
        if(r.debt_catg==='Statutory Debt Limit')continue;
        if(!['Debt Held by the Public','Intragovernmental Holdings','Debt Not Subject to Limit','Other Debt Subject to Limit'].includes(r.debt_catg))continue;
        const v=number(r.close_today_bal)??number(r.open_today_bal);
        if(v===null){valid=false;break;}
        total+=v*(r.debt_catg==='Debt Not Subject to Limit'?-1:1)*1e6;
      }
      if(valid&&total>0)points.push({date,value:total});
    }
    return {debtSubjectToLimit:cleanHistory(points)};
  }
  throw new Error('Unknown Treasury dataset');
}

export function deriveHistory(inputs, calculate) {
  const [primary,...rest]=inputs;
  if(!primary?.history.length)return [];
  return primary.history.flatMap(p=>{
    const points=[p,...rest.map(m=>atOrBefore(m.history,p.date))];
    if(points.some(v=>!v))return [];
    // Do not carry any observation more than its publication cadence allows.
    if(points.some((v,i)=>Date.parse(p.date)-Date.parse(v.date)>inputs[i].maxAgeDays*DAY))return [];
    const value=calculate(points.map(p=>p.value));
    return Number.isFinite(value)?[{date:p.date,value,inputDates:Object.fromEntries(inputs.map((m,i)=>[m.id,points[i].date]))}]:[];
  });
}

async function boundedText(response) {
  if(!response.ok)throw new Error(`HTTP ${response.status}`);
  if(Number(response.headers?.get?.('content-length'))>8_000_000)throw new Error('Response too large');
  if(!response.body?.getReader){const text=await response.text();if(text.length>8_000_000)throw new Error('Response too large');return text;}
  const reader=response.body.getReader();let total=0;const decoder=new TextDecoder();let result='';
  try{for(;;){const {value,done}=await reader.read();if(done)break;total+=value.byteLength;if(total>8_000_000)throw new Error('Response too large');result+=decoder.decode(value,{stream:true});}return result+decoder.decode();}finally{await reader.cancel().catch(()=>{});}
}

export function createDebtService({fetchImpl=globalThis.fetch,now=Date.now,cacheMs=6*60*60*1000,timeoutMs=10000}={}) {
  const lastGood=new Map();let cached=null,inflight=null,lastAttempt=-Infinity;
  const time=()=>{const value=now();return value instanceof Date?value.getTime():Number(value);};
  async function request(url,options={}) {
    const controller=new AbortController();let timer;
    const work=(async()=>boundedText(await fetchImpl(url,{...options,signal:controller.signal,headers:{Accept:options.method?'application/json':'text/csv, application/json',...options.headers}})))();
    try{return await Promise.race([work,new Promise((_,reject)=>{timer=setTimeout(()=>{controller.abort();reject(new Error('Source timed out'));},timeoutMs);})]);}
    finally{clearTimeout(timer);}
  }
  async function refresh() {
    const stamp=time(),fetchedAt=new Date(stamp).toISOString(),end=fetchedAt.slice(0,10);
    const since=new Date(stamp-6*365.25*DAY).toISOString().slice(0,10);
    const histories={},sourceStates=new Map();
    function accept(sourceId,values,error=null) {
      const def=SOURCE_DEFINITIONS.find(d=>d.id===sourceId);
      if(values) values=Object.fromEntries(Object.entries(values).map(([id,h])=>[id,h.filter(p=>p.date<=end)]));
      const valid=values && Object.values(values).some(h=>h.length);
      if(valid) {
        // Store per metric so one missing field does not erase a successful sibling.
        for(const [id,h] of Object.entries(values)) if(h.length)lastGood.set(id,{history:h.filter(p=>p.date<=end),fetchedAt});
      }
      const ids=METRICS.filter(m=>m.series===sourceId).map(m=>m.id);
      let hasOld=false,hasNew=false,hasMissing=false;
      for(const id of ids) {
        const fresh=values?.[id]?.filter(p=>p.date<=end)||[],prior=lastGood.get(id);
        if(fresh.length){histories[id]={history:fresh,fetchedAt,failed:false};hasNew=true;}
        else if(prior?.history.length){histories[id]={...prior,failed:true};hasOld=true;}
        else hasMissing=true;
      }
      sourceStates.set(sourceId,{...def,status:hasNew&&!hasOld&&!hasMissing?'ok':hasOld?'stale':'missing',fetchedAt:hasNew?fetchedAt:null,error:error?.message||(hasNew&&!hasOld&&!hasMissing?null:'One or more series returned no valid observations')});
    }
    const jobs=Object.entries(treasuryDatasets).map(([id,[path]])=>async()=>{
      const url=new URL(TREASURY+path);
      const filters=[`record_date:gte:${id==='treasury-limit'?new Date(stamp-365.25*DAY).toISOString().slice(0,10):since}`];
      if(id==='treasury-interest')filters.push('security_desc:eq:Total Interest-bearing Debt');
      if(id==='treasury-cash')filters.push('account_type:eq:Treasury General Account (TGA) Closing Balance');
      url.searchParams.set('filter',filters.join(','));url.searchParams.set('sort','-record_date');url.searchParams.set('page[size]','5000');
      try{accept(id,parseTreasury(id,JSON.parse(await request(url.toString()))));}catch(error){accept(id,null,error);}
    });
    for(const m of FRED)jobs.push(async()=>{
      try{const csv=await request(`https://fred.stlouisfed.org/graph/fredgraph.csv?id=${m.series}&cosd=${since}&coed=${end}`);accept(m.series,{[m.id]:parseFredCSV(csv,m.series,m.scale)});}catch(error){accept(m.series,null,error);}
    });
    jobs.push(async()=>{
      try{
        const parsed=parseBLS(JSON.parse(await request(BLS,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({seriesid:BLS_IDS,startyear:String(new Date(stamp).getUTCFullYear()-6),endyear:String(new Date(stamp).getUTCFullYear())})})));
        for(const id of BLS_IDS){const m=METRICS.find(m=>m.series===id);accept(id,{[m.id]:parsed[id]||[]});}
      }catch(error){for(const id of BLS_IDS)accept(id,null,error);}
    });
    let next=0;await Promise.all(Array.from({length:4},async()=>{while(next<jobs.length)await jobs[next++]();}));
    const metrics=METRICS.map(def=>{
      const entry=histories[def.id],history=entry?.history||[],last=history.at(-1),source=SOURCE_DEFINITIONS.find(s=>s.id===def.series);
      const old=last && stamp-Date.parse(last.date)>def.maxAgeDays*DAY;
      return {...def,source:source?.label||'Calculated from official observations',sourceUrl:source?.url||null,value:last?.value??null,observedAt:last?.date??null,derived:!def.series,estimated:!def.series,status:last?(entry.failed||old?'stale':'ok'):'missing',history,fetchedAt:entry?.fetchedAt??null,note:def.note};
    });
    const map=Object.fromEntries(metrics.map(m=>[m.id,m]));
    function derived(id,inputIds,calculate) {
      const m=map[id],inputs=inputIds.map(id=>map[id]);
      m.history=deriveHistory(inputs,calculate);const last=m.history.at(-1);
      m.value=last?.value??null;m.observedAt=last?.date??null;m.inputDates=last?.inputDates||{};
      m.sourceUrl=inputs.find(i=>i.sourceUrl)?.sourceUrl??null;
      m.sources=inputs.map(i=>({id:i.id,url:i.sourceUrl}));
      m.status=last?(inputs.some(i=>i.status!=='ok')||stamp-Date.parse(last.date)>m.maxAgeDays*DAY?'stale':'ok'):'missing';
      m.fetchedAt=inputs.map(i=>i.fetchedAt).filter(Boolean).sort()[0]||null;
    }
    derived('debtPerCitizen',['totalDebt','population'],([a,b])=>b>0?a/b:NaN);
    derived('debtToGDP',['totalDebt','gdp'],([a,b])=>b>0?a/b*100:NaN);
    derived('receiptsPerCitizen',['federalReceipts','population'],([a,b])=>b>0?a/b:NaN);
    derived('spendingPerCitizen',['federalSpending','population'],([a,b])=>b>0?a/b:NaN);
    derived('deficitPerCitizen',['federalSpending','federalReceipts','population'],([a,b,c])=>c>0?(a-b)/c:NaN);
    const debt=map.totalDebt,rate=map.debtGrowthPerSecond;
    rate.history=debt.history.flatMap((p,i)=>{
      const target=new Date(Date.parse(p.date)-365*DAY).toISOString().slice(0,10);
      const yearAgo=atOrBefore(debt.history,target);
      // Match the native fallback on at most 31 recent observations, never call it annual.
      const older=yearAgo||debt.history[Math.max(0,i-30)];
      const windowDays=(Date.parse(p.date)-Date.parse(older.date))/DAY;
      const value=(p.value-older.value)/(windowDays*86400);
      return windowDays>0&&Number.isFinite(value)?[{date:p.date,value,method:yearAgo?'trailingYear':'fallback',windowDays,inputDates:{start:older.date,end:p.date}}]:[];
    });
    const last=rate.history.at(-1);Object.assign(rate,{value:last?.value??null,observedAt:last?.date??null,inputDates:last?.inputDates||{},method:last?.method??null,windowDays:last?.windowDays??null,sourceUrl:debt.sourceUrl,status:last?debt.status:'missing',fetchedAt:debt.fetchedAt});
    // Publish one calculation method per series; omitted dates are never filled with zero.
    if(last)rate.history=rate.history.filter(point=>point.method===last.method);
    if(last?.method==='trailingYear')rate.note+=' History excludes shorter fallback windows; each point uses its actual elapsed interval and disclosed input dates.';
    if(last?.method==='fallback'){
      rate.frequency=`${last.windowDays}-day fallback change`;
      rate.note=`365-day baseline unavailable. Estimated average change over ${last.windowDays} calendar days using at most 31 recent observations. The historical window varies with available dates; each point uses its actual elapsed interval and disclosed input dates. Not a live spending rate.`;
    } else if(!last) rate.note+=' No usable pair of dated debt observations is available; no rate or projection can be calculated.';
    const available=metrics.filter(m=>m.status==='ok').length;
    cached={version:1,fetchedAt,status:available===metrics.length?'ok':metrics.some(m=>m.value!==null)?'partial':'unavailable',metrics,groups:GROUPS.map(g=>({...g,metricIds:metrics.filter(m=>m.group===g.id).map(m=>m.id)})),sources:[...sourceStates.values()],note:'Observed data, not a live tally. Fiscal years, annual rates and observation dates differ by series. Histories cover up to six years; debt subject to limit covers up to one year.'};
    return cached;
  }
  return {async getSnapshot({force=false}={}) {
    if(inflight)return inflight;
    const elapsed=time()-lastAttempt;
    // A force request cannot turn the public endpoint into an upstream flood.
    if(cached && (elapsed<60000||(!force&&elapsed<cacheMs)))return cached;
    lastAttempt=time();inflight=refresh();try{return await inflight;}finally{inflight=null;}
  }};
}
