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
