// scam.js: the Uncle Scam page (/scam). Everything is worked out here in the browser by scam-calc.js;
// only public ZIP-prefix files are fetched automatically; salary stays in memory. The optional USAspending lookup
// sends the requested ZIP only after the visitor asks. Treasury requests carry no visitor inputs.
import { computeBill, placeForZip, statementRows, pickStatement, splitByFunction, borrowedFor, timeUnits, officialsFor, debtSince, lastFiscalYear, topRecipients, localBill, localRows } from './scam-calc.js?v=20261004e';

const YEAR = 2026;
const MTS_URL = 'https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/mts/mts_table_9'
  + '?fields=record_date,classification_desc,current_fytd_rcpt_outly_amt,record_type_cd,sequence_level_nbr'
  + '&sort=-record_date&page%5Bsize%5D=300';
// Plain names for the budget's functions; anything not listed prints as the Treasury names it.
const PLAIN = {
  'Net Interest': 'Interest on the debt',
  'National Defense': 'Defense',
  Health: 'Health (Medicaid, research)',
  'Income Security': 'Income security',
  'Veterans Benefits and Services': 'Veterans',
  'Administration of Justice': 'Justice',
  'Education, Training, Employment, and Social Services': 'Education and training',
  'Natural Resources and Environment': 'Environment',
  'General Science, Space, and Technology': 'Science and space',
  'Community and Regional Development': 'Community development',
  'International Affairs': 'Foreign affairs',
};
const DEBT_URL = 'https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v2/accounting/od/debt_to_penny'
  + '?fields=record_date,tot_pub_debt_out_amt&sort=-record_date&page%5Bsize%5D=1';
const SPENDING = 'https://api.usaspending.gov/api/v2/search';
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const $ = (id) => document.getElementById(id);
const usd = (n) => n.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });
const usd2 = (n) => n.toLocaleString('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const pct = (x, digits = 1) => `${(x * 100).toFixed(digits)}%`;

// "$16.43 trillion", "$309.6 million": sums too large to read digit by digit
export function big(n) {
  const a = Math.abs(n);
  if (a >= 1e12) return `$${(n / 1e12).toFixed(2)} trillion`;
  if (a >= 1e9) return `$${(n / 1e9).toFixed(1)} billion`;
  if (a >= 1e6) return `$${(n / 1e6).toFixed(1)} million`;
  return usd(n);
}
const day = (iso) => { const [y, m, d] = iso.split('-').map(Number); return `${MONTHS[m - 1]} ${d}, ${y}`; };
const el = (tag, cls, text) => { const n = document.createElement(tag); if (cls) n.className = cls; if (text != null) n.textContent = text; return n; };
const getJSON = async (url, init, ms = 12000) => {
  const ctl = new AbortController(); const timer = setTimeout(() => ctl.abort(), ms);
  try { const r = await fetch(url, { ...init, signal: ctl.signal }); if (!r.ok) throw new Error(`HTTP ${r.status}`); return await r.json(); } finally { clearTimeout(timer); }
};

// A separate lifecycle keeps local-data failures and old ZIP responses away from the first receipt.
// Only the most recent explicit housing choice is stored; no wage, bill or lookup history is persisted.
const HOUSING_KEY = 'prismet.scam.housing';
export function createLocalReceiptSession({ loadJSON = getJSON, storage = null, onChange = () => {} } = {}) {
  let generation = 0, saved = null, storageAvailable = !!storage;
  let state = { phase: 'idle', zipCode: '', bill: null, tax: null, local: null, zipRow: null, county: null, choice: null, result: null, warnings: [], message: '' };
  try {
    const raw = storage?.getItem(HOUSING_KEY);
    try { const candidate = JSON.parse(raw || 'null'); if (candidate && /^\d{5}$/.test(candidate.zip) && typeof candidate.owns === 'boolean') saved = candidate; } catch { /* Malformed preference is ignored, never a reason to block a receipt. */ }
  } catch { storageAvailable = false; }
  function forget() { saved = null; try { storage?.removeItem(HOUSING_KEY); } catch { storageAvailable = false; } }
  function calculate() {
    if (state.phase !== 'ready') return;
    try { state.result = localBill({ bill: state.bill, zipCode: state.zipCode, county: state.county, zipRow: state.zipRow, owns: state.choice }, state.tax, state['local']); }
    catch { state.phase = 'unavailable'; state.result = null; state.message = 'The local tables could not be read. Local amounts are not included. Your first receipt is unchanged. Try loading again.'; }
  }
  function view(percent = false) {
    const warnings = [...state.warnings];
    const coverage = state.result?.incomplete ? 'Some lines are not on file and are left out of ALL IN. Other tax categories are not modeled.' : 'ALL IN adds modeled income taxes and typical sales/property figures. It is an estimate, not a bill; other tax categories are not modeled.';
    return { ...state, warnings, storageAvailable, coverage, retryable: state.phase === 'unavailable' || warnings.length > 0, rows: state.phase === 'ready' ? localRows(state.result, state.bill, state.tax, state['local'], percent) : [] };
  }
  function emit() { onChange(view()); }
  function invalidate(zipCode) {
    if (!state.zipCode || state.zipCode === zipCode) return;
    generation++; forget();
    state = { ...state, phase: 'idle', zipCode, bill: null, local: null, zipRow: null, county: null, choice: null, result: null, warnings: [], message: 'ZIP changed. Print again to load the matching local receipt. The receipt above still shows your previous calculation.' };
    emit();
  }
  async function load({ bill, tax, zipCode }) {
    const attempt = ++generation;
    if (!/^\d{5}$/.test(zipCode)) throw new Error('A five-digit ZIP is required.');
    let choice = state.zipCode === zipCode ? state.choice : saved?.zip === zipCode ? saved.owns : null;
    if (state.zipCode !== zipCode && saved?.zip !== zipCode) forget();
    state = { phase: 'loading', zipCode, bill, tax, local: null, zipRow: null, county: null, choice, result: null, warnings: [], message: 'Loading the public local-tax and ZIP tables. Your first receipt is ready.' };
    emit();
    const results = await Promise.allSettled([
      `scam-data/local-${YEAR}.json`, `scam-data/zip-local/${zipCode.slice(0, 3)}.json`, `scam-data/zip/${zipCode.slice(0, 3)}.json`,
    ].map(url => Promise.resolve().then(() => loadJSON(url))));
    if (attempt !== generation) return view();
    const [tables, localZIP, countyZIP] = results;
    const local = tables.status === 'fulfilled' ? tables.value : null;
    const validShard = localZIP.status === 'fulfilled' && localZIP.value && typeof localZIP.value === 'object' && !Array.isArray(localZIP.value);
    const rowAbsent = validShard && !Object.hasOwn(localZIP.value, zipCode);
    const row = validShard && !rowAbsent ? localZIP.value[zipCode] : null;
    const county = countyZIP.status === 'fulfilled' ? countyZIP.value?.[zipCode]?.c || null : null;
    const validTables = local && local.localIncome && Array.isArray(local.sales?.bands) && local.sales?.states && Number.isInteger(local.property?.year);
    const validRow = Array.isArray(row) && row.length > 0 && row.length <= 6 && row.every((value, index) => value == null || (index === 3 ? typeof value === 'string' && /^\d{7}$/.test(value) : Number.isFinite(value) && value >= 0 && (![2, 4].includes(index) || Number.isInteger(value) && value <= 100)));
    if (!validTables || !validShard || (!rowAbsent && !validRow)) {
      state.phase = 'unavailable';
      state.message = !validTables ? 'The local tax tables did not load or could not be read. Local amounts are not included. Your first receipt is unchanged.' : localZIP.status === 'rejected' ? 'The ZIP-level local file did not load. Local amounts are not included; missing coverage is not zero tax. Your first receipt is unchanged.' : 'The ZIP-level local file could not be read. Local amounts are not included; missing coverage is not zero tax. Your first receipt is unchanged.';
    } else {
      state = { ...state, phase: 'ready', local, zipRow: row, county, message: '' };
      calculate();
      if (local.localIncome[bill.stateCode]?.by === 'county' && state.result?.incomeStatus === 'countyUnknown' && countyZIP.status === 'rejected') state.warnings.push('The county lookup failed. Retry local tables can try that connection again.');
    }
    emit(); return view();
  }
  function choose(owns) {
    if (typeof owns !== 'boolean' || !/^\d{5}$/.test(state.zipCode)) return;
    state.choice = owns; saved = { zip: state.zipCode, owns };
    try { if (storage) storage.setItem(HOUSING_KEY, JSON.stringify(saved)); else storageAvailable = false; } catch { storageAvailable = false; }
    calculate(); emit();
  }
  return { load, choose, invalidate, view };
}

export function combinedReceiptRows(first, localView) {
  if (localView.phase !== 'ready') return first.slice();
  return [...first, { k: 'rule' }, { k: 'note', t: localView.coverage }, ...localView.rows,
    ...localView.warnings.map(t => ({ k: 'note', t }))];
}

let data = null, statement = null, live = false, last = null;
let congress = null, debtNow = null, lastZip = '', printedZip = '', officialsGeneration = 0, lookupGeneration = 0;
let localSession = null;

// "65,000", "$65000", "65k" → 65000; anything else → NaN
export function parseSalary(text) {
  const m = /^\$?\s*([\d,]*\.?\d*)\s*(k|m)?$/i.exec(String(text).trim());
  if (!m || !/\d/.test(m[1])) return NaN;
  return Math.round(Number(m[1].replace(/,/g, '')) * ({ k: 1e3, m: 1e6 }[(m[2] || '').toLowerCase()] || 1));
}

// The receipt as rows; the page and the saved image both draw from this. `percent` swaps dollars for shares of pay.
export function receiptRows(bill, st, tax, percent) {
  const state = tax.state.states[bill.stateCode];
  const money = (n) => (percent ? pct(bill.wages ? n / bill.wages : 0) : usd(n));
  const t = timeUnits(bill, tax.federal.year);
  const [, mm, dd] = t.freedomDay.split('-').map(Number);
  const mins = t.minutesPerWorkday;
  const through = `${MONTHS[Number(st.date.slice(5, 7)) - 1]} ${st.date.slice(0, 4)}`;
  const rows = [
    { k: 'center', t: 'UNCLE SCAM', c: 'title' },
    { k: 'center', t: 'INFERNAL REVENUE SERVICE' },
    { k: 'center', t: `Branch: ${state.name} · Tax year ${tax.federal.year}`, c: 'small' },
    { k: 'rule' },
    { k: 'row', l: 'GROSS PAY', r: percent ? '100.0%' : usd(bill.wages) },
    { k: 'rule' },
    { k: 'head', t: 'YOUR BILL' },
    { k: 'row', l: 'Federal income tax', r: money(bill.federalIncome) },
    { k: 'row', l: 'Social Security tax', r: money(bill.socialSecurity) },
    { k: 'row', l: 'Medicare tax', r: money(bill.medicare) },
    { k: 'row', l: `${state.name} income tax`, r: state.wages === 'taxed' ? money(bill.state) : `${money(0)} lucky you` },
    { k: 'total', l: 'TOTAL', r: money(bill.total) },
    { k: 'row', l: 'You keep', r: money(bill.takeHome) },
    ...(percent ? [] : [{ k: 'row', l: 'Share of your pay', r: pct(bill.rate) }]),
    { k: 'rule' },
    { k: 'head', t: 'WHAT YOUR INCOME TAX BOUGHT' },
    ...splitByFunction(bill.federalIncome, st).map((x) => ({ k: 'row', l: x.rest ? x.name : (PLAIN[x.name] || x.name), r: money(x.amount) })),
    { k: 'note', t: 'Your Social Security and Medicare taxes went to Social Security and Medicare.' },
    { k: 'rule' },
    { k: 'head', t: 'PUT ON THE CARD', c: 'red' },
    { k: 'row', l: 'Borrowed on top of your bill', r: money(borrowedFor(bill.federalTotal, st)), c: 'red' },
    { k: 'note', t: `Washington spent ${usd2(1 + st.borrowedPerDollar)} for every $1.00 it collected (fiscal ${st.fiscalYear} through ${through}). You will be billed later.` },
    { k: 'rule' },
    { k: 'head', t: 'TIME SERVED' },
    { k: 'row', l: 'Working for the government until', r: `${MONTHS[mm - 1]} ${dd}` },
    { k: 'row', l: 'Of every 8-hour day', r: `${Math.floor(mins / 60)} h ${String(mins % 60).padStart(2, '0')} min` },
    ...(percent ? [] : [{ k: 'row', l: 'Out of each paycheck (of 26)', r: usd(t.perPaycheck) }]),
    { k: 'rule' },
    { k: 'stamp', t: 'NO REFUNDS' },
    { k: 'center', t: 'THANK YOU FOR YOUR CONTRIBUTION.' },
    { k: 'center', t: 'IT WAS MANDATORY.' },
    { k: 'barcode' },
    { k: 'center', t: 'prismet.xyz/scam · an estimate, not tax advice', c: 'small' },
  ];
  return rows;
}

export function drawDom(rows, target = $('receipt')) {
  const el = target;
  el.textContent = '';
  for (const row of rows) {
    let node;
    if (row.k === 'rule') node = document.createElement('hr'), node.className = 'r-rule';
    else if (row.k === 'barcode') node = document.createElement('div'), node.className = 'r-barcode', node.setAttribute('aria-hidden', 'true');
    else if (row.k === 'row' || row.k === 'total') {
      node = document.createElement('div');
      node.className = `r-row${row.k === 'total' ? ' r-total' : ''}${row.c === 'red' ? ' r-red' : ''}`;
      for (const text of [row.l, row.r]) { const s = document.createElement('span'); s.textContent = text; node.append(s); }
    } else if (row.k === 'stamp') {
      node = document.createElement('div'); node.className = 'r-center';
      const s = document.createElement('span'); s.className = 'r-stamp'; s.textContent = row.t; node.append(s);
    } else {
      node = document.createElement(row.k === 'head' ? 'h3' : 'p');
      node.className = { center: 'r-center', head: 'r-head', note: 'r-note' }[row.k] + (row.c ? ` r-${row.c}` : '');
      node.style.margin = row.k === 'center' ? '0' : '';
      node.textContent = row.t;
    }
    el.append(node);
  }
}

// The same rows on a canvas, for "Save as an image". Returns the canvas.
export function drawCanvas(rows) {
  const W = 400, PAD = 24, S = 2, LINE = 19;
  const mono = (px, weight = 400) => `${weight} ${px}px "Martian Mono", ui-monospace, Menlo, Consolas, monospace`;
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');
  const wrap = (text, font, width) => {
    ctx.font = font;
    const lines = []; let cur = '';
    for (const word of text.split(' ')) { const next = cur ? `${cur} ${word}` : word; if (ctx.measureText(next).width > width && cur) { lines.push(cur); cur = word; } else cur = next; }
    return [...lines, cur];
  };
  // lay out, then size the canvas, then paint
  const ops = []; let y = 34;
  for (const row of rows) {
    if (row.k === 'rule') { ops.push({ y: y + 4, rule: true }); y += 16; continue; }
    if (row.k === 'barcode') { ops.push({ y: y + 6, barcode: true }); y += 54; continue; }
    if (row.k === 'stamp') { ops.push({ y: y + 18, stamp: row.t }); y += 40; continue; }
    if (row.k === 'row' || row.k === 'total') {
      if (row.k === 'total') { ops.push({ y: y + 3, line: true }); y += 12; }
      const font = mono(row.k === 'total' ? 14 : 12, row.k === 'total' ? 800 : row.c === 'red' ? 700 : 400);
      ctx.font = font; const rw = ctx.measureText(row.r).width;
      const ls = wrap(row.l, font, W - PAD * 2 - rw - 12);
      ls.forEach((text, i) => ops.push({ y: y + 13 + i * LINE, text, font, x: PAD, red: row.c === 'red' }));
      ops.push({ y: y + 13, text: row.r, font, x: W - PAD, align: 'right', red: row.c === 'red' });
      y += ls.length * LINE + (row.k === 'total' ? 4 : 0); continue;
    }
    const font = row.c === 'title' ? `400 22px "Marcellus", Georgia, serif` : mono(row.k === 'note' || row.c === 'small' ? 13 : 12, row.k === 'head' ? 700 : 400);
    const step = row.c === 'title' ? 28 : row.k === 'note' || row.c === 'small' ? 19 : LINE;
    if (row.k === 'note') y += 4;
    for (const text of wrap(row.t, font, W - PAD * 2)) {
      ops.push({ y: y + 13, text, font, x: row.k === 'center' ? W / 2 : PAD, align: row.k === 'center' ? 'center' : 'left', dim: row.k === 'note' || row.c === 'small', red: row.c === 'red' });
      y += step;
    }
  }
  const H = y + 22;
  canvas.width = W * S; canvas.height = H * S;
  ctx.scale(S, S);
  ctx.fillStyle = '#F4F0E4'; ctx.fillRect(0, 0, W, H);
  for (const op of ops) {
    ctx.fillStyle = ctx.strokeStyle = op.red ? '#B3261E' : op.dim ? '#5A5D63' : '#1B1D21';
    if (op.rule) { ctx.save(); ctx.globalAlpha = 0.55; ctx.setLineDash([4, 3]); ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(PAD, op.y); ctx.lineTo(W - PAD, op.y); ctx.stroke(); ctx.restore(); }
    else if (op.line) { ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(PAD, op.y); ctx.lineTo(W - PAD, op.y); ctx.stroke(); }
    else if (op.barcode) { let x = W * 0.12; const end = W * 0.88; const widths = [2, 1, 3, 1, 2, 3, 1, 1]; for (let i = 0; x < end; i++) { const w = widths[i % widths.length]; if (i % 2 === 0) ctx.fillRect(x, op.y, w, 38); x += w + (i % 3 === 0 ? 2 : 1); } }
    else if (op.stamp) {
      ctx.save(); ctx.translate(W / 2, op.y); ctx.rotate(-0.07); ctx.fillStyle = ctx.strokeStyle = '#B3261E';
      ctx.font = mono(13, 800); ctx.textAlign = 'center'; const w = ctx.measureText(op.stamp).width + 22;
      ctx.lineWidth = 2; ctx.strokeRect(-w / 2, -15, w, 24); ctx.fillText(op.stamp, 0, 2); ctx.restore();
    } else { ctx.font = op.font; ctx.textAlign = op.align || 'left'; ctx.fillText(op.text, op.x, op.y); }
  }
  return canvas;
}

function render() {
  if (!last) return;
  const percent = $('percent').checked;
  drawDom(receiptRows(last, statement, data, percent));
  renderRest();
}

function renderRest() {
  if (!localSession || !last) return;
  const view = localSession.view($('percent').checked), ready = view.phase === 'ready';
  $('rest-receipt').hidden = !ready;
  $('rest-context').hidden = !ready;
  $('rest-status').textContent = view.message;
  $('rest-status').hidden = !view.message;
  $('rest-retry').hidden = !view.retryable;
  $('rest-retry').disabled = view.phase === 'loading';
  $('housing-rent').disabled = $('housing-own').disabled = !ready;
  $('housing-rent').checked = ready && !view.result.owns;
  $('housing-own').checked = ready && view.result.owns;
  $('housing-note').textContent = ready ? (view.choice === null
    ? Number.isFinite(view.zipRow?.[2]) ? `Default for ZIP ${view.zipCode}: ${view.zipRow[2]}% of homes are owned. It selects “I own” at 50% or above. You can change it.` : 'Homeownership share is not on file. The default is “I rent” until you choose.'
    : `Your choice for ZIP ${view.zipCode} ${view.storageAvailable ? 'is saved only on this device' : 'lasts for this visit; browser storage is unavailable'}. Changing ZIP clears it.`)
    : 'Choose after the ZIP data loads. Your choice stays on this device and resets when the ZIP changes.';
  if (ready) {
    drawDom(view.rows, $('rest-receipt'));
    $('rest-coverage').textContent = view.coverage;
    $('rest-warnings').replaceChildren(...view.warnings.map(text => el('p', '', text)));
    const incomeSource = view['local'].localIncome[last.stateCode];
    const sources = [incomeSource?.source, incomeSource?.methodSource, incomeSource?.placeSource, view['local'].sales.source, view['local'].sales.localRates?.[last.stateCode], ...(view['local'].property.sources || [])].filter(source => source?.name && /^https:\/\//.test(source.url));
    const seen = new Set(); const target = $('rest-sources'); target.textContent = 'Local receipt sources: ';
    for (const source of sources) {
      if (seen.has(source.url)) continue;
      if (seen.size) target.append('; ');
      seen.add(source.url); const a = el('a', '', source.name); a.href = source.url; a.rel = 'noopener'; target.append(a);
    }
    target.append('.');
  } else {
    $('rest-receipt').replaceChildren();
    $('rest-warnings').replaceChildren();
  }
  $('save').textContent = ready ? 'Save both receipts as an image' : 'Save first receipt as an image';
  $('save-scope').textContent = ready ? 'The image includes both receipts, the selected dollar/percent view and the local coverage notes.' : 'The image includes only the first receipt. The local receipt is not ready.';
}


function fail(id, message) { $(id).textContent = message; if (message) $(id.replace('-err', '')).focus(); return !message; }

function submit(event) {
  event.preventDefault();
  if (!data || !statement) return;
  $('salary-err').textContent = $('zip-err').textContent = '';
  const wages = parseSalary($('salary').value);
  if (!(wages >= 1 && wages <= 1e9)) return fail('salary-err', 'Enter a yearly salary, like 65,000.');
  const zip = $('zip').value.trim();
  if (!/^\d{5}$/.test(zip)) return fail('zip-err', 'Enter a five-digit ZIP code.');
  const place = placeForZip(zip, data);
  let stateCode = place && !place.territory ? place.code : '';
  if (place && place.territory) return fail('zip-err', `${data.state.territories[place.code]} files under different rules. Uncle Scam covers the 50 states and DC for now.`);
  if (!stateCode) {
    $('state-field').hidden = false;
    stateCode = $('state').value;
    if (!stateCode) { $('zip-err').textContent = 'That ZIP code is not in the Census list. Pick your state below.'; $('state').focus(); return false; }
  } else $('state-field').hidden = true;
  const status = new FormData($('form')).get('status');
  last = computeBill({ wages, status, stateCode }, data);
  printedZip = zip;
  localSession.load({ bill: last, tax: data, zipCode: zip });
  render();
  $('result').hidden = false;
  const paper = $('receipt');
  paper.classList.remove('printing'); void paper.offsetWidth; paper.classList.add('printing');
  $('status').textContent = `Receipt printed: ${usd(last.total)} in taxes on ${usd(last.wages)}, ${pct(last.rate)} of your pay.`
    + (live ? '' : ' Budget figures are a saved Treasury statement; the live one did not load.');
  showOfficials(place ? zip : '', stateCode);
  showLocal(place ? zip : '');
  $('result').scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' });
  return true;
}

// ── who signs off ──
function personCard(m, role) {
  const li = el('li', 'person');
  const head = el('p', 'person-name');
  if (m.url) { const a = el('a', '', m.n); a.href = m.url; a.rel = 'noopener'; head.append(a); } else head.textContent = m.n;
  li.append(head, el('p', 'person-role', `${role} · ${m.p}`));
  li.append(el('p', 'person-term', `In Congress since ${day(m.since)}. Current term ends ${day(m.end)}.`));
  const d = debtSince(m, debtNow);
  if (d) {
    const then = d.yearEnd ? `at the end of fiscal ${d.thenDate.slice(0, 4)}, before they arrived` : 'that day';
    const p = el('p', 'person-debt');
    p.append('National debt ', then, ': ', el('strong', '', big(d.then)), '. Today: ', el('strong', '', big(d.now)), '. ', el('span', 'added', `${big(d.added)} added.`));
    li.append(p);
  }
  return li;
}

async function showOfficials(zip, stateCode) {
  const attempt = ++officialsGeneration;
  const box = $('officials'), list = $('officials-list'), intro = $('officials-intro');
  box.hidden = true;
  try {
    congress ||= await getJSON('scam-data/congress.json');
    debtNow ||= congress.debtNow;
    const entry = zip ? (await getJSON(`scam-data/zip/${zip.slice(0, 3)}.json`))[zip] || null : null;
    if (attempt !== officialsGeneration) return;
    const { senators, seats } = officialsFor(entry, stateCode, congress);
    const stateName = (c) => (data.state.states[c] || {}).name || c;
    list.textContent = '';
    for (const seat of seats) {
      const role = seat.state === 'DC' ? 'Delegate, District of Columbia' : `U.S. Representative, ${stateName(seat.state)}${seat.district ? ` district ${seat.district}` : ', at large'}`;
      list.append(seat.member ? personCard(seat.member, role) : Object.assign(el('li', 'person'), { textContent: `${role}: this seat is vacant.` }));
    }
    for (const m of senators) list.append(personCard(m, `U.S. Senator, ${stateName(m.s)}`));
    intro.textContent = '';
    if (seats.length > 1) {
      const a = el('a', '', 'house.gov can tell you which'); a.href = 'https://www.house.gov/representatives/find-your-representative'; a.rel = 'noopener';
      intro.append(`ZIP code ${zip} crosses a district line, so one of these ${seats.length} representatives is yours; `, a, '.');
    } else if (!seats.length && senators.length) intro.textContent = 'Without a ZIP code in the Census list only your senators can be named.';
    else if (!senators.length && seats.length) intro.textContent = 'The District has a delegate in the House and no senators.';
    box.hidden = !list.children.length;
  } catch { if (attempt === officialsGeneration) box.hidden = true; }
}

// ── what landed near you: asked for, never automatic ──
function showLocal(zip) {
  lookupGeneration++;
  lastZip = zip;
  $('lookup').disabled = false;
  $('local').hidden = !zip;
  $('local-out').hidden = true;
  $('local-status').textContent = '';
  $('lookup').hidden = false; $('lookup-note').hidden = false;
  $('lookup').textContent = `Look up ${zip}`;
}

async function lookup() {
  const zip = lastZip, attempt = ++lookupGeneration, lookupBill = last;
  if (!zip || !lookupBill) return;
  $('lookup').disabled = true;
  $('local-status').textContent = 'Asking USAspending.gov…';
  try {
    const fy = lastFiscalYear(new Date().toISOString().slice(0, 10));
    const period = [{ start_date: fy.start, end_date: fy.end }];
    const post = (path, body) => getJSON(`${SPENDING}/${path}/`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }, 20000);
    const entry = (await getJSON(`scam-data/zip/${zip.slice(0, 3)}.json`))[zip];
    const [recipients, county] = await Promise.all([
      post('spending_by_category/recipient', { filters: { time_period: period, place_of_performance_locations: [{ country: 'USA', zip }] }, limit: 30 }),
      entry ? post('spending_by_geography', { scope: 'place_of_performance', geo_layer: 'county', geo_layer_filters: [entry.c], filters: { time_period: period } }).catch(() => null) : null,
    ]);
    if (attempt !== lookupGeneration || zip !== lastZip) return;
    const top = topRecipients(recipients.results, 8);
    const c = county && county.results && county.results[0];
    const head = $('local-county'); head.textContent = '';
    if (c && c.aggregated_amount > 0) {
      head.append(el('strong', '', big(c.aggregated_amount)), ` in federal awards went to ${c.display_name} in fiscal ${fy.fy}`);
      if (c.per_capita > 0) head.append(', ', el('strong', '', usd(c.per_capita)), ' for each resident');
      head.append(`. Your federal bill was ${usd(lookupBill.federalTotal)}.`);
    }
    $('local-zip-title').textContent = top.length ? `Largest recipients with work in ${zip}` : `No awards are recorded with work in ${zip} for fiscal ${fy.fy}.`;
    const list = $('local-list'); list.textContent = '';
    for (const r of top) { const li = el('li'); li.append(el('span', '', r.name), el('span', 'amount', big(r.amount))); list.append(li); }
    const src = $('local-source'); src.textContent = '';
    const a = el('a', '', 'USAspending.gov'); a.href = 'https://www.usaspending.gov/search'; a.rel = 'noopener';
    src.append('Source: ', a, `, awards by place of performance, October ${fy.fy - 1} through September ${fy.fy}. Amounts are obligations: money committed, not always paid out yet.`);
    $('local-out').hidden = false;
    $('local-status').textContent = '';
    $('lookup').hidden = true; $('lookup-note').hidden = true;
  } catch {
    if (attempt !== lookupGeneration) return;
    $('local-status').textContent = 'USAspending.gov did not answer. Try again in a moment.';
  } finally { if (attempt === lookupGeneration) $('lookup').disabled = false; }
}

async function save() {
  if (!last) return;
  try { await document.fonts.ready; } catch { /* fall back to the system monospace */ }
  const percent = $('percent').checked;
  const localView = localSession.view(percent);
  const canvas = drawCanvas(combinedReceiptRows(receiptRows(last, statement, data, percent), localView));
  canvas.toBlob((blob) => {
    if (!blob) return;
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download = localView.phase === 'ready' ? 'uncle-scam-both-receipts.png' : 'uncle-scam-receipt.png';
    document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  }, 'image/png');
}

async function loadStatement(snapshot) {
  try {
    const ctl = new AbortController(); const timer = setTimeout(() => ctl.abort(), 6000);
    const res = await fetch(MTS_URL, { headers: { Accept: 'application/json' }, signal: ctl.signal });
    clearTimeout(timer);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const picked = pickStatement(statementRows(await res.json()));
    if (!picked) throw new Error('unusable statement');
    live = true; return picked;
  } catch { live = false; return pickStatement(snapshot.rows); }
}

function sources() {
  const el = $('sources');
  const all = [...data.federal.sources, { name: data.state.source.name, url: data.state.source.url }, { name: data.zip.source.name, url: 'https://www.census.gov/geographies/reference-files/time-series/geo/relationship-files.2020.html' },
    { name: `U.S. Treasury, Monthly Treasury Statement (${live ? 'live' : 'saved'}, through ${statement.date})`, url: 'https://fiscaldata.treasury.gov/datasets/monthly-treasury-statement/' }];
  el.textContent = 'Sources: ';
  all.forEach((s, i) => { const a = document.createElement('a'); a.href = s.url; a.rel = 'noopener'; a.textContent = s.name; el.append(a, i < all.length - 1 ? '; ' : '.'); });
}

async function start() {
  try {
    const [tax, snapshot] = await Promise.all([`scam-data/tax-${YEAR}.json`, 'scam-data/mts-snapshot.json'].map((u) => fetch(u).then((r) => { if (!r.ok) throw new Error(u); return r.json(); })));
    data = tax;
    for (const [code, s] of Object.entries(data.state.states).sort((a, b) => a[1].name.localeCompare(b[1].name))) $('state').add(new Option(s.name, code));
    statement = await loadStatement(snapshot);
    getJSON(DEBT_URL, { headers: { Accept: 'application/json' } }, 6000)
      .then((j) => { const r = j.data && j.data[0]; if (r && Number(r.tot_pub_debt_out_amt) > 0) debtNow = { date: r.record_date, amount: Math.round(Number(r.tot_pub_debt_out_amt)) }; })
      .catch(() => { /* the figure saved with the member list stands in */ });
    if (!statement) throw new Error('no statement');
    sources();
    $('go').disabled = false;
    $('status').textContent = '';
  } catch {
    $('status').textContent = 'The tax tables did not load. Reload the page to try again.';
  }
}

if (typeof document !== 'undefined') {
  let storage = null; try { storage = localStorage; } catch { /* device storage can be blocked */ }
  localSession = createLocalReceiptSession({ storage, onChange: renderRest });
  $('housing-rent').addEventListener('change', event => { if (event.target.checked) localSession.choose(false); });
  $('housing-own').addEventListener('change', event => { if (event.target.checked) localSession.choose(true); });
  $('rest-retry').addEventListener('click', () => { if (last && printedZip === $('zip').value.trim()) localSession.load({ bill: last, tax: data, zipCode: printedZip }); });
  $('form').addEventListener('submit', submit);
  $('percent').addEventListener('change', render);
  $('save').addEventListener('click', save);
  $('lookup').addEventListener('click', lookup);
  $('zip').addEventListener('input', () => {
    $('state-field').hidden = true; $('state').value = '';
    localSession.invalidate($('zip').value.trim());
    if (printedZip && printedZip !== $('zip').value.trim()) { officialsGeneration++; $('officials').hidden = true; showLocal(''); }
  });
  start();
}
