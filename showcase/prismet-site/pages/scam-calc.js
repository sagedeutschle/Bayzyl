// scam-calc.js: the arithmetic behind Uncle Scam (/scam). Pure functions, no DOM, no network: the page and
// showcase/tools/tests/scam-check.mjs both import it. Data shapes come from scam-data/tax-<year>.json
// (showcase/tools/uncle-scam/build-data.mjs writes it).
//
// What the estimate assumes: wages from one job are the only income, the standard deduction, no dependents, no
// credits, under 65. "joint" treats the salary as the household's wages, earned by one person.

const cents = (n) => Math.round(n * 100) / 100;

// brackets: [[income over, rate], …] ascending. Income under the first threshold is taxed at zero.
export function bracketTax(taxable, brackets) {
  let tax = 0;
  for (let i = 0; i < brackets.length; i++) {
    const [over, rate] = brackets[i];
    if (taxable <= over) break;
    const top = i + 1 < brackets.length ? Math.min(taxable, brackets[i + 1][0]) : taxable;
    tax += (top - over) * rate;
  }
  return cents(tax);
}

export function marginalRate(taxable, brackets) {
  let rate = 0;
  for (const [over, r] of brackets) if (taxable > over) rate = r;
  return rate;
}

export function federalIncomeTax(wages, status, federal) {
  const taxable = Math.max(0, wages - federal.standardDeduction[status]);
  return { taxable, tax: bracketTax(taxable, federal.brackets[status]), marginal: marginalRate(taxable, federal.brackets[status]) };
}

export function payrollTax(wages, status, federal) {
  const { socialSecurity: ss, medicare: mc } = federal;
  const over = Math.max(0, wages - mc.additionalOver[status]);
  return {
    socialSecurity: cents(Math.min(wages, ss.wageBase) * ss.rate),
    medicare: cents(wages * mc.rate + over * mc.additionalRate),
  };
}

// state: an entry of tax.state.states. Head of household uses the single schedule (the source lists two).
export function stateIncomeTax(wages, status, state) {
  if (!state || state.wages !== 'taxed') return { taxable: 0, tax: 0 };
  const who = status === 'joint' ? 'joint' : 'single';
  const taxable = Math.max(0, wages - state.deduct[who]);
  return { taxable, tax: Math.max(0, cents(bracketTax(taxable, state[who]) - state.credit[who])) };
}

// "43201" → { code: "OH" } | { code: "PR", territory: true } | null (not a ZIP this data knows)
export function placeForZip(zip, data) {
  const z = String(zip || '').trim();
  if (!/^\d{5}$/.test(z)) return null;
  const code = data.zip.exceptions[z] || data.zip.prefixes[z.slice(0, 3)];
  if (!code) return null;
  return data.state.states[code] ? { code } : { code, territory: true };
}

export function computeBill({ wages, status, stateCode }, data) {
  const fed = federalIncomeTax(wages, status, data.federal);
  const pay = payrollTax(wages, status, data.federal);
  const st = stateIncomeTax(wages, status, data.state.states[stateCode]);
  const federalTotal = cents(fed.tax + pay.socialSecurity + pay.medicare);
  const total = cents(federalTotal + st.tax);
  return {
    wages, status, stateCode,
    federalIncome: fed.tax, federalTaxable: fed.taxable, marginal: fed.marginal,
    socialSecurity: pay.socialSecurity, medicare: pay.medicare,
    state: st.tax, federalTotal, total,
    takeHome: cents(wages - total),
    rate: wages > 0 ? total / wages : 0,
  };
}

// ── the rest of the bill: faithful shared-native port, revision 3275077 ──
// Government tables and ZIP rows are loaded by the caller; salary never leaves the browser.
// This preserves the app's definitions, including incomplete coverage. A missing ZIP
// shard must be disclosed by the UI, not presented as evidence that no local tax applies.
function localRateTax(entry, base, status) {
  if (entry.rate != null) return cents(base * entry.rate);
  const steps = entry.brackets?.[status === 'single' ? 'single' : 'joint'];
  if (!steps?.length) return 0;
  if (entry.whole === true) {
    let rate = steps[0][1];
    for (const [over, r] of steps) if (base > over) rate = r;
    return cents(base * rate);
  }
  return bracketTax(base, steps);
}

// zipRow: [property tax, home value, owned %, city code, city land %, combined sales rate].
// Optional numeric outputs are omitted when the native result is nil; absence is not zero.
export function localBill({ bill, zipCode, county = null, zipRow = null, owns = null }, tax, local) {
  let incomeStatus = 'notOnFile', incomeName = '', income = 0, incomeIfInside = 0, incomeBase = 0;
  const table = local.localIncome[bill.stateCode];
  if (table) {
    incomeBase = table.base === 'wages' ? bill.wages : stateIncomeTax(bill.wages, bill.status, tax.state.states[bill.stateCode]).taxable;
    if (table.by === 'county') {
      const entry = county != null ? table.rates[county] : null;
      if (entry) {
        incomeStatus = 'taxed'; incomeName = local.counties[county] ?? entry.name ?? '';
        income = localRateTax(entry, incomeBase, bill.status);
      } else incomeStatus = county == null ? 'countyUnknown' : 'untaxed';
    } else if (zipRow == null) {
      incomeStatus = 'zipUnknown';
    } else {
      const entry = zipRow?.[3] != null ? table.rates[zipRow[3]] : null;
      if (entry) {
        incomeName = entry.name ?? '';
        const amount = localRateTax(entry, incomeBase, bill.status);
        if ((zipRow?.[4] ?? 0) >= 50) { incomeStatus = 'taxed'; income = amount; }
        else { incomeStatus = 'partOfZip'; incomeIfInside = amount; }
      } else incomeStatus = 'untaxed';
    }
  }
  const incomeRate = incomeBase > 0 ? (incomeStatus === 'taxed' ? income : incomeIfInside) / incomeBase : 0;
  let salesState, salesLocal, salesStateRate = 0, salesLocalRate = 0, salesLocalKind = 'none';
  const sales = local.sales.states[bill.stateCode];
  if (sales) {
    let band = 0;
    for (let i = 0; i < local.sales.bands.length; i++) if (bill.wages >= local.sales.bands[i]) band = i;
    const amount = sales.amounts[band][bill.status === 'single' ? 0 : 1];
    salesState = amount; salesStateRate = sales.rate; salesLocalKind = sales['local'];
    if (salesLocalKind === 'none') salesLocal = 0;
    else if (zipRow?.[5] != null && sales.rate > 0) {
      salesLocalRate = Math.max(0, zipRow[5] - sales.rate);
      salesLocal = Math.round(amount * salesLocalRate / sales.rate);
    }
  } // No state table is not evidence of no local sales tax (notably Alaska).
  const typical = zipRow?.[0], propertyCapped = (typical ?? 0) > 10000;
  const own = owns ?? ((zipRow?.[2] ?? 0) >= 50);
  const property = own ? Math.min(typical ?? 0, 10000) : 0;
  const state = cents(bill.state + (salesState ?? 0));
  const localTotal = cents(income + (salesLocal ?? 0) + property);
  const total = cents(bill.federalTotal + state + localTotal);
  return {
    zip: zipCode, incomeStatus, incomeName, incomeRate, income, incomeIfInside, incomeBase,
    ...(salesState != null ? { salesState } : {}), salesStateRate,
    ...(salesLocal != null ? { salesLocal } : {}), salesHousehold: bill.status === 'single' ? 1 : 2, salesLocalRate, salesLocalKind,
    ...(typical != null ? { propertyTypical: Math.min(typical, 10000) } : {}), propertyCapped, owns: own, property,
    lowerBound: own && propertyCapped,
    incomplete: ['notOnFile','zipUnknown','countyUnknown'].includes(incomeStatus) || salesLocal == null || (own && typical == null),
    federal: bill.federalTotal, state, local: localTotal, total,
    takeHome: cents(bill.wages - total), rate: bill.wages > 0 ? total / bill.wages : 0,
  };
}

const localRateText = rate => (rate * 100).toFixed(3).replace(/\.?0+$/, '') + '%';
const localPercent = n => {
  const fixed = (n * 100).toFixed(1);
  return (Number(fixed) === 0 ? '0.0' : fixed) + '%';
};
function localDollars(n) {
  // Match the native toFixed-compatible formatter, including whole-dollar half cases.
  const fixed = n.toFixed(0), negative = Number(fixed) < 0;
  return (negative ? '-$' : '$') + fixed.replace(/^-/, '').replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

// Same row object vocabulary as the first receipt, usable for DOM and image rendering.
export function localRows(b, bill, tax, local, percent = false) {
  const stateName = tax.state.states[bill.stateCode]?.name ?? bill.stateCode;
  const money = n => percent ? localPercent(bill.wages !== 0 ? n / bill.wages : 0) : localDollars(n);
  const place = b.incomeStatus === 'taxed' && b.incomeName ? `${b.incomeName}, ${stateName}` : stateName;
  const rows = [{k:'center',t:'THE REST OF THE BILL',c:'title'}, {k:'center',t:`ZIP ${b.zip} · ${place}`,c:'small'}, {k:'rule'}, {k:'head',t:'CITY AND COUNTY INCOME TAX'}];
  const row = (l,r) => rows.push({k:'row',l,r});
  const note = t => rows.push({k:'note',t});
  const table = local.localIncome[bill.stateCode];
  if (b.incomeStatus === 'taxed') {
    row(`${b.incomeName} income tax (${localRateText(b.incomeRate)})`,money(b.income));
    if (table?.note != null) note(table.note);
  } else if (b.incomeStatus === 'partOfZip') {
    row('City income tax',money(0));
    note(`Part of this ZIP lies in ${b.incomeName}, which taxes wages at ${localRateText(b.incomeRate)}: ${money(b.incomeIfInside)} if you live and work there. Not counted.`);
  } else if (b.incomeStatus === 'untaxed') {
    row('Local income tax',money(0));
    note(table?.by === 'place' ? `No ${stateName} city with an income tax on file covers this ZIP.` : 'No rate on file for this county.');
  } else if (b.incomeStatus === 'countyUnknown') {
    row('County income tax','not counted');
    note(`${stateName} counties tax income. Uncle Scam needs a connection to find this ZIP's county.`);
  } else if (b.incomeStatus === 'zipUnknown') {
    row('City income tax','not on file');
    note(`Uncle Scam has no local figures for ZIP ${b.zip}. If your city charges an income tax, it is not counted here.`);
  } else {
    row('Local income tax','not on file');
    const names = Object.keys(local.localIncome).sort().map(code=>tax.state.states[code]?.name).filter(name=>name != null).join(', ');
    note(`Uncle Scam has city and county income tax tables for ${names} so far. If your city or county charges one, it is not counted here.`);
  }
  rows.push({k:'head',t:'SALES TAX'});
  if (b.salesState != null) {
    row(`${stateName} sales tax (${localRateText(b.salesStateRate)})`,money(b.salesState));
    if (b.salesLocalKind === 'partly-included') note(`That figure includes the local rate every place in ${stateName} charges.`);
    if (b.salesLocalKind === 'none') note(`${stateName} has no local sales tax.`);
    else if (b.salesLocal != null) row(`Local sales tax (${localRateText(b.salesLocalRate)} on top)`,money(b.salesLocal));
    else { row('Local sales tax','not on file'); note(`Places in ${stateName} add their own sales tax. This ZIP's rate is not on file yet, so it is not counted.`); }
    note(`What the IRS reckons a household of your income pays in a year, counted as ${b.salesHousehold === 1 ? 'one person' : 'two people'} (${local.sales.year} tables).`);
  } else {
    row(`${stateName} sales tax`,money(0)); note(`The IRS tables list no state sales tax for ${stateName}.`);
    row('Local sales tax','not on file');note(`If a place in ${stateName} charges its own sales tax, it is not counted here.`);
  }
  rows.push({k:'head',t:'PROPERTY TAX'});
  if (b.propertyTypical != null) {
    const figure = (b.propertyCapped ? 'over ' : '') + money(b.propertyTypical);
    if (b.owns) row(`Typical homeowner in ${b.zip}`,figure);
    else { row('Paid directly by a renter',money(0)); note(`The typical homeowner in ${b.zip} pays ${figure} a year. A landlord passes it on in the rent.`); }
    note(`The middle real estate tax bill among homeowners here, Census survey ${local.property.year - 4} to ${local.property.year}.`);
  } else { row('Property tax','no figure'); note('The Census survey has no property tax figure for this ZIP.'); }
  rows.push({k:'rule'},{k:'head',t:'EVERY LEVEL'});
  const over = b.lowerBound ? 'over ' : '';
  row('Federal',money(b.federal));row('State',money(b.state));row('Local',over + money(b['local']));
  rows.push({k:'total',l:'ALL IN',r:over + money(b.total)});row('You keep',(b.lowerBound ? 'under ' : '') + money(b.takeHome));
  if (!percent) row('Share of your pay',over + localPercent(b.rate));
  if (b.lowerBound) note("The property tax figure is the survey's ceiling, so the local line and the total are floors.");
  if (b.incomplete) note('Lines marked not on file, not counted or no figure are left out of these totals.');
  note('Not counted: taxes on gas, alcohol and tobacco, vehicle fees, tariffs, and the taxes businesses pass on in prices.');
  rows.push({k:'rule'},{k:'center',t:'estimates: income taxes modeled from rates in law,',c:'small'},{k:'center',t:'sales and property from typical figures',c:'small'});
  return rows;
}

// ── the Monthly Treasury Statement, table 9 ─────────────────────────────────────────────────────────────────────
// rows: [{ d: record date, n: line name, a: fiscal-year-to-date dollars, t: record type, l: level }], any order.
// Type F rows are outlays by budget function; type RSG rows are receipts by source (level 2, and the level-3
// children of "Social Insurance and Retirement Receipts", whose own row carries no amount).

// The API's answer → rows. Lines without an amount (section headings) are dropped.
export function statementRows(json) {
  return ((json && json.data) || [])
    .filter((r) => r.current_fytd_rcpt_outly_amt && r.current_fytd_rcpt_outly_amt !== 'null' && Number.isFinite(Number(r.current_fytd_rcpt_outly_amt)))
    .map((r) => ({ d: r.record_date, n: r.classification_desc, a: Math.round(Number(r.current_fytd_rcpt_outly_amt)), t: r.record_type_cd, l: Number(r.sequence_level_nbr) }));
}

// The newest statement, unless its fiscal year (October to September) is under six months old: then the last full
// year (a September statement) when the rows hold one, so a single month never stands in for a budget.
export function pickStatement(rows) {
  const dates = [...new Set(rows.map((r) => r.d))].sort().reverse();
  if (!dates.length) return null;
  const monthsIn = (d) => ((Number(d.slice(5, 7)) - 10 + 12) % 12) + 1;
  let date = dates[0];
  if (monthsIn(date) < 6) date = dates.find((d) => d.slice(5, 7) === '09') || date;
  const mine = rows.filter((r) => r.d === date);
  const functions = mine.filter((r) => r.t === 'F').map((r) => ({ name: r.n, amount: r.a }));
  const outlays = functions.reduce((s, f) => s + f.amount, 0);
  const receipts = mine.filter((r) => r.t === 'RSG').reduce((s, r) => s + r.a, 0);
  if (functions.length < 10 || !(outlays > 0) || !(receipts > 0)) return null;
  // the statement's own "Total" lines (receipts, outlays; the API folds them into one row when asked for a subset
  // of fields) must agree with the sums, or the table's shape has changed under us
  const totals = mine.filter((r) => r.t === 'SL' && r.n === 'Total').reduce((s, r) => s + r.a, 0);
  if (totals && Math.abs(totals - (outlays + receipts)) > totals * 0.001) return null;
  const fiscalYear = Number(date.slice(0, 4)) + (Number(date.slice(5, 7)) >= 10 ? 1 : 0);
  return { date, fiscalYear, months: monthsIn(date), functions, outlays, receipts, borrowedPerDollar: (outlays - receipts) / receipts };
}

const OWN_TAX = new Set(['Social Security', 'Medicare']);      // paid for on the bill's payroll lines

// Spread an income tax bill over the budget's functions in proportion to outlays, leaving out the two programs
// the payroll lines already pay for. The `top` largest get their own line; the rest (small functions, and the
// negative lines: offsetting receipts) are netted into one. Lines add up to `amount` to the cent.
export function splitByFunction(amount, statement, top = 8) {
  const funcs = statement.functions.filter((f) => !OWN_TAX.has(f.name));
  const base = funcs.reduce((s, f) => s + f.amount, 0);
  const big = funcs.filter((f) => f.amount > 0).sort((a, b) => b.amount - a.amount).slice(0, top);
  const lines = big.map((f) => ({ name: f.name, share: f.amount / base, amount: cents(amount * f.amount / base) }));
  const rest = cents(amount - lines.reduce((s, l) => s + l.amount, 0));
  lines.push({ name: 'Everything else', share: 1 - lines.reduce((s, l) => s + l.share, 0), amount: rest, rest: true });
  return lines;
}

// What Washington borrowed on top of each dollar it collected, scaled to this bill.
export function borrowedFor(federalTotal, statement) {
  return cents(federalTotal * statement.borrowedPerDollar);
}

// ── the bill in units of time ───────────────────────────────────────────────────────────────────────────────────
export function timeUnits(bill, year) {
  const days = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0 ? 366 : 365;
  const dayOfYear = Math.min(days, Math.max(1, Math.ceil(bill.rate * days)));
  // the last day of the year whose pay goes to tax, working from January 1
  const freedom = new Date(Date.UTC(year, 0, dayOfYear));
  return {
    dayOfYear, days,
    freedomDay: freedom.toISOString().slice(0, 10),
    minutesPerWorkday: Math.round(bill.rate * 480),
    perPaycheck: cents(bill.total / 26),
    perDay: cents(bill.total / days),
  };
}

// ── who signs off: the members of Congress for a ZIP ────────────────────────────────────────────────────────────
// entry: a ZIP's record from scam-data/zip/<prefix>.json ({ c: county FIPS, d: ["OH3", …] }) or null when the ZIP is
// not in the Census list; then only the state's senators are known. A ZIP that crosses a district line lists every
// district it touches, largest share of land first.
export function officialsFor(entry, stateCode, congress) {
  const senators = congress.members.filter((m) => m.t === 'sen' && m.s === stateCode).sort((a, b) => (a.since < b.since ? -1 : 1));
  const seats = ((entry && entry.d) || []).map((key) => {
    const state = key.slice(0, 2), district = Number(key.slice(2));
    return { state, district, member: congress.members.find((m) => m.t === 'rep' && m.s === state && m.d === district) || null };
  });
  return { senators, seats };
}

// The debt on a member's first day in Congress against the debt now.
export function debtSince(member, now) {
  if (!member.debt || !now) return null;
  return { then: member.debt.amount, thenDate: member.debt.date, yearEnd: member.debt.yearEnd === true, now: now.amount, nowDate: now.date, added: now.amount - member.debt.amount, times: now.amount / member.debt.amount };
}

// ── federal awards landing in a place (USAspending) ─────────────────────────────────────────────────────────────
// The last fiscal year that has ended (October 1 to September 30), as of `today` (YYYY-MM-DD).
export function lastFiscalYear(today) {
  const y = Number(today.slice(0, 4)), fy = today.slice(5) >= '10-01' ? y : y - 1;
  return { fy, start: `${fy - 1}-10-01`, end: `${fy}-09-30` };
}

const KEEP_UPPER = new Set(['LLC', 'LLP', 'LP', 'PLLC', 'USA', 'US', 'II', 'III', 'IV', 'NA', 'PC']);
const KEEP_LOWER = new Set(['of', 'and', 'the', 'for', 'in', 'at', 'on', 'to']);
// "OHIO STATE UNIVERSITY, THE" → "The Ohio State University"
const PLACEHOLDERS = { 'REDACTED DUE TO PII': 'Individuals (names withheld)', 'MULTIPLE RECIPIENTS': 'Many recipients (reported together)' };
export function tidyName(raw) {
  let s = String(raw || '').trim().replace(/\s+/g, ' ');
  if (PLACEHOLDERS[s.toUpperCase()]) return PLACEHOLDERS[s.toUpperCase()];
  const m = /^(.*),\s*THE$/i.exec(s);
  if (m) s = `THE ${m[1]}`;
  return s.split(' ').map((w, i) => {
    const bare = w.replace(/[^A-Za-z]/g, '');
    if (KEEP_UPPER.has(bare.toUpperCase())) return w.toUpperCase();
    if (i && KEEP_LOWER.has(w.toLowerCase())) return w.toLowerCase();
    return w.toLowerCase().replace(/(^|[-'.&/(])([a-z])/g, (x, a, b) => a + b.toUpperCase());
  }).join(' ');
}

// USAspending lists one row per registration; fold rows that share a name, largest first.
export function topRecipients(results, top = 8) {
  const sum = new Map();
  for (const r of results || []) {
    const amount = Number(r.amount);
    if (!r.name || !Number.isFinite(amount)) continue;
    const name = tidyName(r.name);
    sum.set(name, (sum.get(name) || 0) + amount);
  }
  return [...sum].map(([name, amount]) => ({ name, amount })).filter((r) => r.amount > 0).sort((a, b) => b.amount - a.amount).slice(0, top);
}
