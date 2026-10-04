// scam.js: the Uncle Scam page (/scam). Everything is worked out here in the browser by scam-calc.js; the salary and
// the ZIP code are never sent anywhere. The only requests are this site's own data file and the Treasury's public
// statement (which carries nothing about the visitor), with a snapshot to fall back on.
import { computeBill, placeForZip, statementRows, pickStatement, splitByFunction, borrowedFor, timeUnits, officialsFor, debtSince, lastFiscalYear, topRecipients } from './scam-calc.js';

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

let data = null, statement = null, live = false, last = null;
let congress = null, debtNow = null, lastZip = '';

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

function drawDom(rows) {
  const el = $('receipt');
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
    const font = row.c === 'title' ? `400 24px "Marcellus", Georgia, serif` : mono(row.k === 'note' || row.c === 'small' ? 10.5 : 12, row.k === 'head' ? 700 : 400);
    const step = row.c === 'title' ? 28 : row.k === 'note' || row.c === 'small' ? 15 : LINE;
    if (row.k === 'note') y += 4;
    for (const text of wrap(row.t, font, W - PAD * 2)) {
      ops.push({ y: y + 13, text, font, x: row.k === 'center' ? W / 2 : PAD, align: row.k === 'center' ? 'center' : 'left', dim: row.k === 'note' || row.c === 'small', red: row.c === 'red' });
      y += step;
    }
  }
  const H = y + 22;
  canvas.width = W * S; canvas.height = H * S;
  ctx.scale(S, S);
  ctx.fillStyle = '#EEF3F2'; ctx.fillRect(0, 0, W, H);
  for (const op of ops) {
    ctx.fillStyle = ctx.strokeStyle = op.red ? '#B3261E' : op.dim ? '#55636B' : '#16202A';
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
  const box = $('officials'), list = $('officials-list'), intro = $('officials-intro');
  try {
    congress ||= await getJSON('scam-data/congress.json');
    debtNow ||= congress.debtNow;
    const entry = zip ? (await getJSON(`scam-data/zip/${zip.slice(0, 3)}.json`))[zip] || null : null;
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
  } catch { box.hidden = true; }
}

// ── what landed near you: asked for, never automatic ──
function showLocal(zip) {
  lastZip = zip;
  $('local').hidden = !zip;
  $('local-out').hidden = true;
  $('local-status').textContent = '';
  $('lookup').hidden = false; $('lookup-note').hidden = false;
  $('lookup').textContent = `Look up ${zip}`;
}

async function lookup() {
  const zip = lastZip;
  if (!zip || !last) return;
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
    if (zip !== lastZip) return;
    const top = topRecipients(recipients.results, 8);
    const c = county && county.results && county.results[0];
    const head = $('local-county'); head.textContent = '';
    if (c && c.aggregated_amount > 0) {
      head.append(el('strong', '', big(c.aggregated_amount)), ` in federal awards went to ${c.display_name} in fiscal ${fy.fy}`);
      if (c.per_capita > 0) head.append(', ', el('strong', '', usd(c.per_capita)), ' for each resident');
      head.append(`. Your federal bill was ${usd(last.federalTotal)}.`);
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
    $('local-status').textContent = 'USAspending.gov did not answer. Try again in a moment.';
  } finally { $('lookup').disabled = false; }
}

async function save() {
  if (!last) return;
  try { await document.fonts.ready; } catch { /* fall back to the system monospace */ }
  const canvas = drawCanvas(receiptRows(last, statement, data, $('percent').checked));
  canvas.toBlob((blob) => {
    if (!blob) return;
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download = 'uncle-scam-receipt.png';
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
  $('form').addEventListener('submit', submit);
  $('percent').addEventListener('change', render);
  $('save').addEventListener('click', save);
  $('lookup').addEventListener('click', lookup);
  $('zip').addEventListener('input', () => { $('state-field').hidden = true; $('state').value = ''; });
  start();
}
