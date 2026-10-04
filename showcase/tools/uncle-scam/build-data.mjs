#!/usr/bin/env node
// build-data.mjs: writes the data Uncle Scam (/scam) reads, from published sources. Zero dependencies.
//
//   node showcase/tools/uncle-scam/build-data.mjs [--tf page.html] [--zcta rel.txt] [--mts table9.json]
//
// Without a flag each source is fetched. Output (commit it; the page never calls these sources itself, except the
// Treasury statement, which it refreshes live and falls back to the snapshot written here):
//   showcase/prismet-site/pages/scam-data/tax-<year>.json    federal tables, state tables, ZIP prefix → state
//   showcase/prismet-site/pages/scam-data/mts-snapshot.json  Monthly Treasury Statement table 9, trimmed
//   showcase/prismet-site/pages/scam-data/congress.json      sitting members of Congress, with the debt on their first day
//   showcase/prismet-site/pages/scam-data/zip/<3 digits>.json  ZIP → county and congressional district(s)
//   more flags: [--cd rel.txt] [--leg legislators-current.json] [--no-debt] (skip the Treasury lookups; for tests)
//
// Sources
//   federal brackets + standard deduction  IRS Rev. Proc. 2025-32, sections 4.01 and 4.14 (typed in below, checked
//                                          by scam-check.mjs against the table's own "the tax is" amounts)
//   Social Security wage base              SSA, Contribution and Benefit Base
//   Medicare rates and thresholds          IRC section 3101(b); not indexed for inflation
//   state brackets, deductions, exemptions Tax Foundation, State Individual Income Tax Rates and Brackets (parsed)
//   ZIP → state                            Census 2020 ZCTA to county relationship file (parsed)
//   budget by function                     Treasury Fiscal Data, Monthly Treasury Statement table 9
//   ZIP → congressional district           Census 2020 ZCTA to 119th Congressional District relationship file
//   members of Congress                    unitedstates/congress-legislators (public domain)
//   debt on a given day                    Treasury Fiscal Data, Debt to the Penny (1993 on) and Historical Debt Outstanding
import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { statementRows, pickStatement } from '../../prismet-site/pages/scam-calc.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, '../../prismet-site/pages/scam-data');
const YEAR = 2026;
const TF_URL = `https://taxfoundation.org/data/all/state/state-income-tax-rates-${YEAR}/`;
const ZCTA_URL = 'https://www2.census.gov/geo/docs/maps-data/data/rel2020/zcta520/tab20_zcta520_county20_natl.txt';
const CD_URL = 'https://www2.census.gov/geo/docs/maps-data/data/rel2020/cd-sld/tab20_cd11920_zcta520_natl.txt';
const LEG_URL = 'https://unitedstates.github.io/congress-legislators/legislators-current.json';
const FISCAL = 'https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v2/accounting/od';
export const MTS_URL = 'https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/mts/mts_table_9'
  + '?fields=record_date,classification_desc,current_fytd_rcpt_outly_amt,record_type_cd,sequence_level_nbr'
  + '&sort=-record_date&page%5Bsize%5D=300';

const FEDERAL = {
  year: YEAR,
  sources: [
    { what: 'Income tax brackets and standard deduction', name: 'IRS Rev. Proc. 2025-32', url: 'https://www.irs.gov/pub/irs-drop/rp-25-32.pdf' },
    { what: 'Social Security wage base', name: 'SSA, Contribution and Benefit Base', url: 'https://www.ssa.gov/oact/cola/cbb.html' },
    { what: 'Medicare rates', name: 'IRS Topic no. 751', url: 'https://www.irs.gov/taxtopics/tc751' },
  ],
  standardDeduction: { single: 16100, joint: 32200, head: 24150 },
  // [taxable income over, rate]
  brackets: {
    single: [[0, 0.10], [12400, 0.12], [50400, 0.22], [105700, 0.24], [201775, 0.32], [256225, 0.35], [640600, 0.37]],
    joint: [[0, 0.10], [24800, 0.12], [100800, 0.22], [211400, 0.24], [403550, 0.32], [512450, 0.35], [768700, 0.37]],
    head: [[0, 0.10], [17700, 0.12], [67450, 0.22], [105700, 0.24], [201750, 0.32], [256200, 0.35], [640600, 0.37]],
  },
  socialSecurity: { rate: 0.062, wageBase: 184500 },
  medicare: { rate: 0.0145, additionalRate: 0.009, additionalOver: { single: 200000, joint: 250000, head: 200000 } },
};

const STATES = {
  Alabama: 'AL', Alaska: 'AK', Arizona: 'AZ', Arkansas: 'AR', California: 'CA', Colorado: 'CO', Connecticut: 'CT',
  Delaware: 'DE', Florida: 'FL', Georgia: 'GA', Hawaii: 'HI', Idaho: 'ID', Illinois: 'IL', Indiana: 'IN', Iowa: 'IA',
  Kansas: 'KS', Kentucky: 'KY', Louisiana: 'LA', Maine: 'ME', Maryland: 'MD', Massachusetts: 'MA', Michigan: 'MI',
  Minnesota: 'MN', Mississippi: 'MS', Missouri: 'MO', Montana: 'MT', Nebraska: 'NE', Nevada: 'NV',
  'New Hampshire': 'NH', 'New Jersey': 'NJ', 'New Mexico': 'NM', 'New York': 'NY', 'North Carolina': 'NC',
  'North Dakota': 'ND', Ohio: 'OH', Oklahoma: 'OK', Oregon: 'OR', Pennsylvania: 'PA', 'Rhode Island': 'RI',
  'South Carolina': 'SC', 'South Dakota': 'SD', Tennessee: 'TN', Texas: 'TX', Utah: 'UT', Vermont: 'VT',
  Virginia: 'VA', Washington: 'WA', 'West Virginia': 'WV', Wisconsin: 'WI', Wyoming: 'WY', 'Washington DC': 'DC',
};
const FIPS = {
  '01': 'AL', '02': 'AK', '04': 'AZ', '05': 'AR', '06': 'CA', '08': 'CO', '09': 'CT', 10: 'DE', 11: 'DC', 12: 'FL',
  13: 'GA', 15: 'HI', 16: 'ID', 17: 'IL', 18: 'IN', 19: 'IA', 20: 'KS', 21: 'KY', 22: 'LA', 23: 'ME', 24: 'MD',
  25: 'MA', 26: 'MI', 27: 'MN', 28: 'MS', 29: 'MO', 30: 'MT', 31: 'NE', 32: 'NV', 33: 'NH', 34: 'NJ', 35: 'NM',
  36: 'NY', 37: 'NC', 38: 'ND', 39: 'OH', 40: 'OK', 41: 'OR', 42: 'PA', 44: 'RI', 45: 'SC', 46: 'SD', 47: 'TN',
  48: 'TX', 49: 'UT', 50: 'VT', 51: 'VA', 53: 'WA', 54: 'WV', 55: 'WI', 56: 'WY',
  60: 'AS', 66: 'GU', 69: 'MP', 72: 'PR', 78: 'VI',
};
const TERRITORIES = { AS: 'American Samoa', GU: 'Guam', MP: 'Northern Mariana Islands', PR: 'Puerto Rico', VI: 'U.S. Virgin Islands' };

const arg = (name) => { const i = process.argv.indexOf(`--${name}`); return i > 0 ? process.argv[i + 1] : null; };
async function source(name, url) {
  const file = arg(name);
  if (file) return readFileSync(file, 'utf8');
  const r = await fetch(url, { headers: { 'user-agent': 'Mozilla/5.0 (prismet.xyz data build)' } });
  if (!r.ok) throw new Error(`${url}: HTTP ${r.status}`);
  return r.text();
}

const unescape = (s) => s.replace(/&amp;/g, '&').replace(/&nbsp;/g, ' ').replace(/&#8211;|&ndash;/g, '-').replace(/&#\d+;|&[a-z]+;/g, '');
const money = (s) => { const m = /^\$([\d,]+(?:\.\d+)?)/.exec(s); return m ? Number(m[1].replace(/,/g, '')) : null; };
const rate = (s) => { const m = /^(\d+(?:\.\d+)?)%$/.exec(s); return m ? Math.round(Number(m[1]) * 1e4) / 1e6 : null; };

// The page's second table: one row per bracket, the state's name on its first row and "- Name" on the rest.
export function parseStates(html) {
  const tables = html.match(/<table[\s\S]*?<\/table>/g) || [];
  const table = tables.find((t) => /Single Filer \(Rates\)/.test(t));
  if (!table) throw new Error('state table not found on the Tax Foundation page');
  const rows = (table.match(/<tr[\s\S]*?<\/tr>/g) || [])
    .map((r) => [...r.matchAll(/<t[hd][^>]*>([\s\S]*?)<\/t[hd]>/g)].map((c) => unescape(c[1].replace(/<[^>]+>/g, '')).trim()));
  const states = {};
  let cur = null;
  for (const c of rows.slice(1)) {
    if (c.length < 12) continue;
    const first = !c[0].startsWith('- ');
    const name = c[0].replace(/^- /, '').replace(/\s*\(.*$/, '').trim();
    const code = STATES[name];
    if (!code) throw new Error(`unknown state "${c[0]}"`);
    if (first) {
      cur = states[code] = { name: name === 'Washington DC' ? 'District of Columbia' : name, wages: 'taxed', single: [], joint: [], deduct: { single: 0, joint: 0 }, credit: { single: 0, joint: 0 } };
      if (c[1] === 'none') cur.wages = 'none';
      else if (rate(c[1]) === null) { cur.wages = 'none'; cur.note = c[1]; }           // Washington: capital gains only
      // standard deduction (cols 7, 8) and personal exemption (cols 9, 10): a plain amount reduces income, "$n credit" reduces tax
      for (const [col, who] of [[7, 'single'], [8, 'joint'], [9, 'single'], [10, 'joint']]) {
        const v = money(c[col]);
        if (v === null) continue;
        if (/credit/i.test(c[col])) cur.credit[who] += v; else cur.deduct[who] += v;
      }
    }
    if (!cur || cur.wages === 'none') continue;
    if (rate(c[1]) !== null) cur.single.push([money(c[3]), rate(c[1])]);
    if (rate(c[4]) !== null) cur.joint.push([money(c[6]), rate(c[4])]);
  }
  for (const [code, s] of Object.entries(states)) {
    if (s.wages === 'none') { delete s.single; delete s.joint; delete s.deduct; delete s.credit; continue; }
    for (const who of ['single', 'joint']) {
      const b = s[who];
      if (!b.length) throw new Error(`${code}: no ${who} brackets`);
      for (let i = 0; i < b.length; i++) {
        if (b[i][0] === null || !(b[i][1] >= 0 && b[i][1] < 0.2)) throw new Error(`${code}: bad ${who} bracket ${JSON.stringify(b[i])}`);
        if (i && b[i][0] <= b[i - 1][0]) throw new Error(`${code}: ${who} brackets out of order`);
      }
    }
  }
  const missing = Object.values(STATES).filter((c) => !states[c]);
  if (missing.length) throw new Error(`states missing from the table: ${missing.join(', ')}`);
  return states;
}

// ZCTA → the state holding most of its land; then one state per 3-digit prefix and the 5-digit ZIPs that differ.
export function parseZips(text) {
  const lines = text.replace(/^﻿/, '').split('\n');
  const head = lines[0].split('|');
  const iz = head.indexOf('GEOID_ZCTA5_20'), ic = head.indexOf('GEOID_COUNTY_20'), ia = head.indexOf('AREALAND_PART');
  if (iz < 0 || ic < 0 || ia < 0) throw new Error('unexpected Census relationship file header');
  const land = new Map();
  for (const line of lines.slice(1)) {
    const f = line.split('|');
    if (!f[iz] || !f[ic]) continue;
    const st = FIPS[f[ic].slice(0, 2)] || FIPS[Number(f[ic].slice(0, 2))];
    if (!st) throw new Error(`unknown state FIPS in ${f[ic]}`);
    const m = land.get(f[iz]) || land.set(f[iz], {}).get(f[iz]);
    m[st] = (m[st] || 0) + Number(f[ia] || 0) + 1;
  }
  const top = (m) => Object.entries(m).sort((a, b) => b[1] - a[1] || (a[0] < b[0] ? -1 : 1))[0][0];
  const byZip = new Map([...land].map(([z, m]) => [z, top(m)]));
  const count = {};
  for (const [z, st] of byZip) { const p = z.slice(0, 3); (count[p] ||= {})[st] = (count[p][st] || 0) + 1; }
  const prefixes = Object.fromEntries(Object.keys(count).sort().map((p) => [p, top(count[p])]));
  const exceptions = Object.fromEntries([...byZip].filter(([z, st]) => prefixes[z.slice(0, 3)] !== st).sort());
  // the county holding most of each ZCTA's land, for the local lookups
  const county = new Map();
  for (const line of lines.slice(1)) {
    const f = line.split('|');
    if (!f[iz] || !f[ic]) continue;
    const a = Number(f[ia] || 0), cur = county.get(f[iz]);
    if (!cur || a > cur[1]) county.set(f[iz], [f[ic], a]);
  }
  return { prefixes, exceptions, zctas: byZip.size, county: new Map([...county].map(([z, c]) => [z, c[0]])) };
}

// ZCTA → its congressional districts, largest share of land first: "OH3", "WY0" (at large), "DC0" (the delegate).
export function parseDistricts(text) {
  const lines = text.replace(/^\uFEFF/, '').split('\n');
  const head = lines[0].split('|');
  const iz = head.indexOf('GEOID_ZCTA5_20'), id = head.indexOf('GEOID_CD119_20'), ia = head.indexOf('AREALAND_PART');
  if (iz < 0 || id < 0 || ia < 0) throw new Error('unexpected Census district relationship file header');
  const out = new Map();
  for (const line of lines.slice(1)) {
    const f = line.split('|');
    if (!f[iz] || !f[id] || !/^\d{4}$/.test(f[id])) continue;                    // "ZZ": water, no district
    const st = FIPS[f[id].slice(0, 2)] || FIPS[Number(f[id].slice(0, 2))];
    if (!st) throw new Error(`unknown state FIPS in district ${f[id]}`);
    const n = Number(f[id].slice(2));
    const key = `${st}${n === 98 || n === 0 ? 0 : n}`;
    (out.get(f[iz]) || out.set(f[iz], []).get(f[iz])).push([key, Number(f[ia] || 0)]);
  }
  return new Map([...out].map(([z, list]) => [z, list.sort((a, b) => b[1] - a[1]).map((x) => x[0])]));
}

// The sitting members, trimmed to what the page shows. `since` is the first day of their first term in Congress.
export function parseLegislators(json) {
  const members = json.map((m) => {
    const t = m.terms.at(-1);
    return {
      n: m.name.official_full || `${m.name.first} ${m.name.last}`, p: t.party, s: t.state, t: t.type,
      ...(t.type === 'rep' ? { d: t.district ?? 0 } : {}),
      since: m.terms.map((x) => x.start).sort()[0], end: t.end, url: t.url || '',
    };
  }).sort((a, b) => (a.s + a.t + String(a.d ?? '').padStart(2, '0') + a.n < b.s + b.t + String(b.d ?? '').padStart(2, '0') + b.n ? -1 : 1));
  if (members.length < 500 || members.some((m) => !/^\d{4}-\d\d-\d\d$/.test(m.since) || !/^[A-Z]{2}$/.test(m.s) || !m.n)) throw new Error('legislators: unexpected shape');
  return members;
}

// Total public debt on each date: the first daily figure on or after it (1993 on), else the last fiscal-year-end figure before it.
async function debtOn(dates) {
  const get = async (url) => { const r = await fetch(url); if (!r.ok) throw new Error(`${url}: HTTP ${r.status}`); return (await r.json()).data; };
  const yearly = (await get(`${FISCAL}/debt_outstanding?fields=record_date,debt_outstanding_amt&sort=-record_date&page%5Bsize%5D=400`)).map((r) => [r.record_date, Math.round(Number(r.debt_outstanding_amt))]);
  const out = {};
  for (const d of dates) {
    if (d >= '1993-04-01') {
      const [row] = await get(`${FISCAL}/debt_to_penny?fields=record_date,tot_pub_debt_out_amt&filter=record_date:gte:${d}&sort=record_date&page%5Bsize%5D=1`);
      if (!row) throw new Error(`no debt figure on or after ${d}`);
      out[d] = { date: row.record_date, amount: Math.round(Number(row.tot_pub_debt_out_amt)) };
    } else {
      const row = yearly.find(([date]) => date <= d);
      if (!row) throw new Error(`no debt figure before ${d}`);
      out[d] = { date: row[0], amount: row[1], yearEnd: true };
    }
  }
  const [now] = await get(`${FISCAL}/debt_to_penny?fields=record_date,tot_pub_debt_out_amt&sort=-record_date&page%5Bsize%5D=1`);
  return { byDate: out, now: { date: now.record_date, amount: Math.round(Number(now.tot_pub_debt_out_amt)) } };
}

// Keep only the statement the page would pick (scam-calc.js decides), so the fallback is one month of rows.
export function trimStatement(json) {
  const rows = statementRows(json);
  const picked = pickStatement(rows);
  if (!picked) throw new Error('Monthly Treasury Statement: no usable statement in the answer');
  return rows.filter((r) => r.d === picked.date);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const states = parseStates(await source('tf', TF_URL));
  const zips = parseZips(await source('zcta', ZCTA_URL));
  const mts = trimStatement(JSON.parse(await source('mts', MTS_URL)));
  const districts = parseDistricts(await source('cd', CD_URL));
  const members = parseLegislators(JSON.parse(await source('leg', LEG_URL)));
  const debt = process.argv.includes('--no-debt') ? null : await debtOn([...new Set(members.map((m) => m.since))].sort());
  const built = new Date().toISOString().slice(0, 10);
  const tax = {
    _readme: 'Written by showcase/tools/uncle-scam/build-data.mjs. Do not edit by hand; fix the script and run it again.',
    built,
    federal: FEDERAL,
    state: {
      year: YEAR,
      source: { name: `Tax Foundation, State Individual Income Tax Rates and Brackets, ${YEAR}`, url: TF_URL },
      note: 'Rates as of January 1. Head of household uses the single schedule. City, county and school district income taxes are not included.',
      states,
      territories: TERRITORIES,
    },
    zip: {
      source: { name: 'U.S. Census Bureau, 2020 ZCTA to County Relationship File', url: ZCTA_URL },
      prefixes: zips.prefixes,
      exceptions: zips.exceptions,
    },
  };
  mkdirSync(OUT, { recursive: true });
  writeFileSync(join(OUT, `tax-${YEAR}.json`), JSON.stringify(tax) + '\n');
  writeFileSync(join(OUT, 'mts-snapshot.json'), JSON.stringify({ built, source: 'U.S. Treasury, Monthly Treasury Statement, table 9', rows: mts }) + '\n');
  writeFileSync(join(OUT, 'congress.json'), JSON.stringify({
    built,
    source: { name: 'unitedstates/congress-legislators', url: 'https://github.com/unitedstates/congress-legislators' },
    debtSource: { name: 'U.S. Treasury, Debt to the Penny and Historical Debt Outstanding', url: 'https://fiscaldata.treasury.gov/datasets/debt-to-the-penny/' },
    debtNow: debt ? debt.now : null,
    members: members.map((m) => (debt ? { ...m, debt: debt.byDate[m.since] } : m)),
  }) + '\n');
  // one small file per 3-digit prefix: { "43201": { c: county FIPS, d: [districts] } }
  rmSync(join(OUT, 'zip'), { recursive: true, force: true });
  mkdirSync(join(OUT, 'zip'), { recursive: true });
  const shards = {};
  for (const [z, c] of zips.county) (shards[z.slice(0, 3)] ||= {})[z] = { c, d: districts.get(z) || [] };
  for (const [p, body] of Object.entries(shards)) writeFileSync(join(OUT, 'zip', `${p}.json`), JSON.stringify(Object.fromEntries(Object.entries(body).sort())) + '\n');
  console.log(`uncle-scam data: ${members.length} members of Congress, ${Object.keys(shards).length} ZIP files, debt ${debt ? `as of ${debt.now.date}` : 'skipped'}`);
  const none = Object.entries(states).filter(([, s]) => s.wages === 'none').map(([c]) => c);
  console.log(`uncle-scam data: ${Object.keys(states).length} states (${none.length} without a wage tax: ${none.join(' ')}), `
    + `${Object.keys(zips.prefixes).length} ZIP prefixes, ${Object.keys(zips.exceptions).length} exceptions from ${zips.zctas} ZCTAs, `
    + `${mts.length} statement rows, latest ${mts[0].d}`);
}
