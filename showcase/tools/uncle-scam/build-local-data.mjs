#!/usr/bin/env node
// Faithful website data builder port from co-developed Prismet native commit 59b4c4a068a1ef83bb3b9640f7960adae43657af.
// Sources, parser checks and output shapes retained; only default output/provenance paths changed.
// Uncle Scam, the rest of the bill: local income tax, sales tax and property tax, by ZIP code.
//
//   node showcase/tools/uncle-scam/build-local-data.mjs [--cache <folder>] [--out <folder>]
//
// Reads public government files, writes the data both apps bundle and the website publishes:
//   <out>/local-2026.json          rate tables: local income tax, the IRS sales tax tables, county names
//   <out>/zip-local/<3 digits>.json   per ZIP: typical property tax, home value, share of homes owned,
//                                     and (where a state taxes by city) the city and its local sales rate
// <out> defaults to showcase/prismet-site/pages/scam-data.
// Rebuilding replaces only local-2026.json and zip-local; preserve a release backup before updating.
//
// Every rate and dollar figure comes from a file named in SOURCES below; nothing is typed in from memory.
// States without a table here are simply absent: the receipt says the line is not on file, it does not say "none". PDF tables are read
// with `pdftotext -layout` (poppler). The script stops when a table does not look the way it expects, rather
// than writing a guess.
//
// PRISM: Fable/Claude 2026-10-04. The website's port must read the same files; see docs/lenses/uncle-scam-local.md.

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';

const here = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const arg = (name, fallback) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : fallback; };
const cache = resolve(arg('--cache', join(tmpdir(), 'uncle-scam-local-cache')));
const out = resolve(arg('--out', join(here, '../../prismet-site/pages/scam-data')));
mkdirSync(cache, { recursive: true });

const ACS = 'https://www2.census.gov/programs-surveys/acs/summary_file/2024/table-based-SF/data/5YRData/';
const FINDER = 'https://thefinder.tax.ohio.gov/api/file-downloads/content?target=%2Ffinder%2Fapi%2Fv1%2Ftax-rates%2Fdownloads%2F';
const SOURCES = {
  counties: { name: 'Census Bureau, 2020 county codes', url: 'https://www2.census.gov/geo/docs/reference/codes2020/national_county2020.txt' },
  propertyTax: { name: 'Census Bureau, American Community Survey 2020 to 2024, table B25103 (median real estate taxes paid)', url: ACS + 'acsdt5y2024-b25103.dat' },
  homeValue: { name: 'Census Bureau, American Community Survey 2020 to 2024, table B25077 (median home value)', url: ACS + 'acsdt5y2024-b25077.dat' },
  tenure: { name: 'Census Bureau, American Community Survey 2020 to 2024, table B25003 (homes owned and rented)', url: ACS + 'acsdt5y2024-b25003.dat' },
  places: { name: 'Census Bureau, 2020 ZIP Code Tabulation Area to place relationship file', url: 'https://www2.census.gov/geo/docs/maps-data/data/rel2020/zcta520/tab20_zcta520_place20_natl.txt' },
  salesTables: { name: 'IRS, 2025 Instructions for Schedule A, Optional State Sales Tax Tables', url: 'https://www.irs.gov/pub/irs-pdf/i1040sca.pdf' },
  maryland: { name: 'Comptroller of Maryland, 2026 State and Local Withholding Information, Attachment 1', url: 'https://www.marylandcomptroller.gov/content/dam/mdcomp/md/state-payroll/memos/2026/2026-maryland-state-and-local-withholding-information.pdf' },
  marylandMethod: { name: 'USDA National Finance Center, Maryland State and Counties Income Tax Withholding (2026 bulletin)', url: 'https://help.nfc.usda.gov/bulletins/2026/1783003892.htm' },
  indiana: { name: 'Indiana Department of Revenue, Departmental Notice #1 (county income tax rates)', url: 'https://www.in.gov/dor/files/dn01.pdf' },
  ohioCities: { name: 'Ohio Department of Taxation, The Finder: Ohio Municipal Income Tax Rates', url: FINDER + 'Muni%2FOHMuniRateTable.csv' },
  ohioSales: { name: 'Ohio Department of Taxation, The Finder: Ohio County Rate Table by Zip Code', url: FINDER + 'CountySalesTaxRateReport.csv' },
};

const UA = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15';
function fetchFile(key, file) {
  const path = join(cache, file);
  if (!existsSync(path) || statSync(path).size === 0) {
    process.stderr.write(`fetching ${key}\n`);
    execFileSync('curl', ['-sS', '--fail', '-L', '-m', '300', '-A', UA, '-o', path, SOURCES[key].url]);
  }
  return path;
}
const text = (key, file) => readFileSync(fetchFile(key, file), 'utf8').replace(/^﻿/, '');
function pdfText(key, file) {
  const pdf = fetchFile(key, file);
  try { return execFileSync('pdftotext', ['-layout', pdf, '-'], { encoding: 'utf8', maxBuffer: 64 << 20 }); }
  catch (e) { throw new Error('pdftotext (poppler) is needed to read ' + file + ': ' + e.message); }
}
function need(ok, message) { if (!ok) throw new Error('uncle-scam-local-data: ' + message); }
const plain = (s) => s.toLowerCase().replace(/[’']/g, '').replace(/\b(county|city)\b/g, '').replace(/[^a-z]/g, '');

// ---- counties: FIPS → name -------------------------------------------------------------------------------
const counties = {};          // "39049" → "Franklin County"
const countyByName = {};      // "IN|adams" → "18001"
const stateOfFips = {};       // "39" → "OH"
for (const line of text('counties', 'counties.txt').split('\n').slice(1)) {
  const [st, sf, cf, , name] = line.split('|');
  if (!cf) continue;
  counties[sf + cf] = name;
  stateOfFips[sf] = st;
  // Baltimore city and Baltimore County differ only by the word this strips: keep the city apart
  const key = /\bcity$/i.test(name) && st === 'MD' ? plain(name) + 'city' : plain(name);
  countyByName[st + '|' + key] = sf + cf;
}
need(Object.keys(counties).length > 3100, 'county list is short');

// ---- Maryland: county income tax, on Maryland taxable income ---------------------------------------------
function maryland() {
  const t = pdfText('maryland', 'md.pdf');
  const start = t.indexOf('Local Income Tax Withholding Rates for 2026');
  need(start > 0, 'Maryland attachment 1 not found');
  const lines = t.slice(start).split('\n');
  const rates = {};
  let open = null;      // the county whose graduated schedule is being read
  let who = null;
  const lower = (s) => {
    const n = [...s.matchAll(/\$?([\d,]+)(?:\.\d\d)?/g)].map((m) => Number(m[1].replace(/,/g, '')));
    need(n.length > 0, 'Maryland bracket without a number: ' + s);
    return n[0] <= 1 ? 0 : (/over|or more/i.test(s) ? n[0] - (n[0] % 1000 === 1 ? 1 : 0) : n[0] - 1);
  };
  for (const raw of lines) {
    const line = raw.replace(/[’]/g, "'").trim();
    const flat = line.match(/^(.+?(?:County|City))\s+(\d\d)\s+(\d\.\d\d)$/);
    const head = line.match(/^(.+?(?:County|City))\s+(\d\d)\s+Single/);
    if (flat || head) {
      const name = (flat || head)[1];
      const key = /city$/i.test(name) ? plain(name) + 'city' : plain(name);
      const fips = countyByName['MD|' + key];
      need(fips, 'Maryland county not matched: ' + name);
      if (flat) { rates[fips] = { rate: Number((Number(flat[3]) / 100).toFixed(6)) }; open = null; }
      else { rates[fips] = { brackets: { single: [], joint: [] } }; open = fips; who = 'single'; }
      continue;
    }
    if (!open) continue;
    if (/^MFJ/.test(line)) { who = 'joint'; continue; }
    const step = line.match(/^(\d\.\d\d)\s*[–-]\s*\(?(.+?)\)?$/);
    if (step) rates[open].brackets[who].push([lower(step[2]), Number((Number(step[1]) / 100).toFixed(6))]);
    if (/^County$/.test(line) || /Earnings Statement/.test(line)) break;
  }
  need(Object.keys(rates).length === 24, 'Maryland: expected 24 jurisdictions, read ' + Object.keys(rates).length);
  // The comptroller's table lists both stepped counties alike, but they work differently: Anne Arundel taxes each
  // slice of income at its own rate; Frederick taxes the whole income at the one rate its size selects. The
  // federal payroll bulletin names them "Graduated Tax Table" and "Fixed Rate Tax Table"; check it still does.
  const method = text('marylandMethod', 'md-method.htm').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
  need(/Anne Arundel Graduated Tax Table/.test(method) && /Frederick Fixed Rate Tax Table/.test(method), 'Maryland: the bulletin no longer describes the two stepped counties as expected');
  const stepped = Object.entries(rates).filter(([, r]) => r.brackets).map(([f]) => f).sort();
  need(String(stepped) === '24003,24021', 'Maryland: stepped counties are not Anne Arundel and Frederick: ' + stepped);
  rates['24021'].whole = true;
  for (const [fips, r] of Object.entries(rates)) {
    if (!r.brackets) { need(r.rate > 0.02 && r.rate < 0.04, 'Maryland rate out of range for ' + fips); continue; }
    for (const side of ['single', 'joint']) {
      const b = r.brackets[side];
      need(b.length >= 3 && b[0][0] === 0 && b.every((x, i) => i === 0 || x[0] > b[i - 1][0]), 'Maryland schedule unreadable for ' + fips);
    }
  }
  return rates;
}

// ---- Indiana: county income tax, on Indiana taxable income -----------------------------------------------
function indiana() {
  const t = pdfText('indiana', 'in.pdf');
  const effective = (t.match(/Effective\s+([A-Z][a-z]+\.? \d+, \d{4})/) || [])[1] || '';
  const rates = {};
  for (const m of t.matchAll(/([A-Z][A-Za-z. ]+?)\s+(\d\d)\s+(0\.\d+)\*?/g)) {
    const fips = countyByName['IN|' + plain(m[1])];
    need(fips, 'Indiana county not matched: ' + m[1]);
    rates[fips] = { rate: Number(m[3]) };
  }
  need(Object.keys(rates).length === 92, 'Indiana: expected 92 counties, read ' + Object.keys(rates).length);
  need(Object.values(rates).every((r) => r.rate > 0.003 && r.rate < 0.04), 'Indiana rate out of range');
  return { rates, effective };
}

// ---- Ohio: city income tax, on wages ---------------------------------------------------------------------
function ohioCities() {
  const rates = {};
  const since = {};
  for (const line of text('ohioCities', 'oh-muni.csv').split(/\r?\n/)) {
    const [from, to, fips, name, rate] = line.split(',');
    // the rate in force at the end of tax year 2026: a rate that starts later is not this year's
    if (!/^\d{5}$/.test(fips || '') || Number(from) > 20261231 || Number(to) < 20260101) continue;
    if (since[fips] && since[fips] > Number(from)) continue;
    since[fips] = Number(from);
    if (!(Number(rate) > 0)) { delete rates['39' + fips]; continue; }      // listed, but charges no income tax
    rates['39' + fips] = { name: name.trim().toLowerCase().replace(/\b[a-z]/g, (c) => c.toUpperCase()), rate: Number(rate) };
  }
  need(Object.keys(rates).length > 500, 'Ohio: too few cities');
  need(Object.values(rates).every((r) => r.rate > 0 && r.rate <= 0.035), 'Ohio rate out of range');
  return rates;
}
function ohioSales() {
  const byZip = {};
  for (const line of text('ohioSales', 'oh-sales.csv').split(/\r?\n/).slice(1)) {
    const [zip, , , rate] = line.split(',');
    if (!/^\d{5}$/.test(zip || '')) continue;
    const r = Number(rate);
    need(r >= 0.0575 && r <= 0.09, 'Ohio sales rate out of range for ' + zip);
    byZip[zip] = Math.max(byZip[zip] || 0, r);      // a ZIP that crosses counties lists more than once
  }
  need(Object.keys(byZip).length > 1000, 'Ohio: too few ZIP codes');
  return byZip;
}

// ---- IRS optional state sales tax tables -----------------------------------------------------------------
const STATE_CODES = { Alabama: 'AL', Alaska: 'AK', Arizona: 'AZ', Arkansas: 'AR', California: 'CA', Colorado: 'CO', Connecticut: 'CT', Delaware: 'DE',
  'District of Columbia': 'DC', Florida: 'FL', Georgia: 'GA', Hawaii: 'HI', Idaho: 'ID', Illinois: 'IL', Indiana: 'IN', Iowa: 'IA', Kansas: 'KS',
  Kentucky: 'KY', Louisiana: 'LA', Maine: 'ME', Maryland: 'MD', Massachusetts: 'MA', Michigan: 'MI', Minnesota: 'MN', Mississippi: 'MS', Missouri: 'MO',
  Montana: 'MT', Nebraska: 'NE', Nevada: 'NV', 'New Hampshire': 'NH', 'New Jersey': 'NJ', 'New Mexico': 'NM', 'New York': 'NY', 'North Carolina': 'NC',
  'North Dakota': 'ND', Ohio: 'OH', Oklahoma: 'OK', Oregon: 'OR', Pennsylvania: 'PA', 'Rhode Island': 'RI', 'South Carolina': 'SC', 'South Dakota': 'SD',
  Tennessee: 'TN', Texas: 'TX', Utah: 'UT', Vermont: 'VT', Virginia: 'VA', Washington: 'WA', 'West Virginia': 'WV', Wisconsin: 'WI', Wyoming: 'WY' };
function salesTables() {
  const t = pdfText('salesTables', 'irs-sca.pdf');
  const start = t.indexOf('2025 Optional State Sales Tax Tables');
  const end = t.indexOf('Which Optional Local Sales Tax Table Should I Use?');
  need(start > 0 && end > start, 'IRS state sales tax tables not found');
  const states = {};
  let bands = null;
  let current = [];       // the states of the block being read, left to right
  let rows = [];
  const close = () => {
    if (!current.length) return;
    need(rows.length === 19, 'IRS block for ' + current.map((s) => s.code).join(',') + ' has ' + rows.length + ' rows');
    const lows = rows.map((r) => r.low);
    if (!bands) bands = lows; else need(String(bands) === String(lows), 'IRS income bands differ between blocks');
    current.forEach((s, i) => { states[s.code] = { rate: s.rate, local: s.local, amounts: rows.map((r) => r.values.slice(i * 6, i * 6 + 6)) }; });
    current = []; rows = [];
  };
  for (const raw of t.slice(start, end).split('\n')) {
    const line = raw.trim();
    if (/^Income\s+[A-Z]/.test(line) && line.includes('%')) {
      close();
      for (const m of line.replace(/^Income\s+/, '').matchAll(/([A-Z][A-Za-z ]+?)\s+([\d,]+)\s+(\d+\.\d+)%/g)) {
        const code = STATE_CODES[m[1].trim()];
        need(code, 'IRS table names a state this script does not know: ' + m[1]);
        // the IRS footnotes: 1 and 2, the state has local sales taxes on top; 3 and 5, the table already holds a
        // local rate every place in the state charges; 4, the state has no local sales tax
        const notes = m[2].split(',').map(Number);
        const local = notes.includes(4) ? 'none' : (notes.includes(3) || notes.includes(5)) ? 'partly-included' : 'extra';
        current.push({ code, rate: Number((Number(m[3]) / 100).toFixed(6)), local });
      }
      need(current.length >= 1 && current.length <= 3, 'IRS header unreadable: ' + line);
      continue;
    }
    const band = line.match(/^\$?([\d,]+)\s+(?:\$?[\d,]+|or more)\s+(.*)$/);
    if (!band || !current.length) continue;
    // the last block shares its lines with the footnotes: take the whole numbers that lead the row
    const values = [];
    for (const token of band[2].trim().split(/\s+/)) { if (!/^\d+$/.test(token) || values.length === current.length * 6) break; values.push(Number(token)); }
    need(values.length === current.length * 6, 'IRS row unreadable: ' + line);
    rows.push({ low: Number(band[1].replace(/,/g, '')), values });
  }
  close();
  need(Object.keys(states).length >= 45, 'IRS: expected at least 45 states, read ' + Object.keys(states).length);
  need(bands[0] === 0 && bands[18] === 300000, 'IRS income bands are not the expected nineteen');
  return { bands, states };
}

// ---- Census: property tax, home value, tenure, by ZIP ----------------------------------------------------
function acs(key, file, pick) {
  const outMap = {};
  const lines = text(key, file).split('\n');
  const head = lines[0].split('|');
  for (const line of lines) {
    if (!line.startsWith('860Z200US')) continue;
    const cells = line.split('|');
    outMap[cells[0].slice(9)] = pick((name) => {
      const v = Number(cells[head.indexOf(name)]);
      return Number.isFinite(v) && v >= 0 ? v : null;       // the survey marks "no figure" with large negatives
    });
  }
  need(Object.keys(outMap).length > 30000, file + ': too few ZIP areas');
  return outMap;
}
const propertyTax = acs('propertyTax', 'b25103.dat', (v) => v('B25103_E001'));
const homeValue = acs('homeValue', 'b25077.dat', (v) => v('B25077_E001'));
const tenure = acs('tenure', 'b25003.dat', (v) => { const all = v('B25003_E001'), own = v('B25003_E002'); return all ? Math.round((own / all) * 100) : null; });

// ---- Census: the city each ZIP mostly lies in (only kept where a state taxes income by city) ---------------
const mdRates = maryland();
const inRates = indiana();
const ohRates = ohioCities();
const ohSales = ohioSales();
const sales = salesTables();
const zipPlace = {};          // zip → [place GEOID, share of the ZIP's land in it, 0…100]
{
  const best = {};
  for (const line of text('places', 'zcta-place.txt').split('\n').slice(1)) {
    const c = line.split('|');
    const zip = c[1], place = c[9], land = Number(c[3]), part = Number(c[16]);
    if (!zip || !place || !ohRates[place] || !(land > 0)) continue;       // only cities with a rate on file
    if (!best[zip] || part > best[zip].part) best[zip] = { place, part, land };
  }
  for (const [zip, b] of Object.entries(best)) zipPlace[zip] = [b.place, Math.min(100, Math.round((b.part / b.land) * 100))];
}
need(zipPlace['43201'] && zipPlace['43201'][0] === '3918000', 'ZIP 43201 should lie in Columbus');

// ---- write -------------------------------------------------------------------------------------------------
const today = new Date().toISOString().slice(0, 10);
const local = {
  _readme: 'Written by showcase/tools/uncle-scam/build-local-data.mjs (native 59b4c4a). Do not edit by hand: change the script and run it again.',
  built: today,
  counties,
  localIncome: {
    MD: { by: 'county', base: 'stateTaxable', year: 2026, source: SOURCES.maryland, methodSource: SOURCES.marylandMethod, rates: mdRates,
          note: 'County tax on Maryland taxable income, at the county you live in.' },
    IN: { by: 'county', base: 'stateTaxable', year: 2026, effective: inRates.effective, source: SOURCES.indiana, rates: inRates.rates,
          note: 'County tax on Indiana taxable income, at the county you lived in on January 1.' },
    OH: { by: 'place', base: 'wages', year: 2026, source: SOURCES.ohioCities, placeSource: SOURCES.places, rates: ohRates,
          note: 'City tax on wages. Ohio cities tax where you work; this assumes you live and work in the same city. School district income tax is not included.' },
  },
  sales: { year: 2025, source: SOURCES.salesTables, bands: sales.bands, states: sales.states,
           note: 'What the IRS estimates a household of this income and size pays in state sales tax in a year. Local sales tax is added where a ZIP-level rate is on file, by the IRS rule: state amount times local rate over state rate.',
           localRates: { OH: SOURCES.ohioSales } },
  property: { year: 2024, sources: [SOURCES.propertyTax, SOURCES.homeValue, SOURCES.tenure],
              note: 'Median real estate tax paid by homeowners in the ZIP area, 2020 to 2024. The survey stops counting at $10,000: 10001 means more than that.' },
};
mkdirSync(out, { recursive: true });
writeFileSync(join(out, 'local-2026.json'), JSON.stringify(local) + '\n');

// per ZIP: [median property tax, median home value, percent of homes owned, city GEOID, percent of ZIP in it, combined sales rate]
const zipDir = join(out, 'zip-local');
rmSync(zipDir, { recursive: true, force: true });
mkdirSync(zipDir, { recursive: true });
const byPrefix = {};
const zips = new Set([...Object.keys(propertyTax), ...Object.keys(homeValue), ...Object.keys(tenure), ...Object.keys(zipPlace), ...Object.keys(ohSales)]);
for (const zip of [...zips].sort()) {
  const row = [propertyTax[zip] ?? null, homeValue[zip] ?? null, tenure[zip] ?? null];
  const place = zipPlace[zip];
  if (place || ohSales[zip]) row.push(place ? place[0] : null, place ? place[1] : null, ohSales[zip] ?? null);
  while (row.length && row[row.length - 1] === null) row.pop();
  if (!row.length) continue;
  (byPrefix[zip.slice(0, 3)] ||= {})[zip] = row;
}
let bytes = 0;
for (const [prefix, rows] of Object.entries(byPrefix)) {
  const body = JSON.stringify(rows) + '\n';
  bytes += body.length;
  writeFileSync(join(zipDir, prefix + '.json'), body);
}
process.stdout.write(JSON.stringify({
  out, built: today, counties: Object.keys(counties).length,
  maryland: Object.keys(mdRates).length, indiana: Object.keys(inRates.rates).length, ohioCities: Object.keys(ohRates).length,
  ohioZipSales: Object.keys(ohSales).length, salesStates: Object.keys(sales.states).length,
  zips: zips.size, prefixFiles: Object.keys(byPrefix).length, zipLocalBytes: bytes,
  sample43201: byPrefix['432']?.['43201'], columbus: ohRates['3918000'], ohioSales: sales.states.OH?.rate,
}, null, 1) + '\n');
