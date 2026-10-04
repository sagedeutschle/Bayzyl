// scam-check.mjs: Uncle Scam's arithmetic and data (/scam). Node only, nothing goes on the network.
//   node showcase/tools/tests/scam-check.mjs
// 1. The federal schedules reproduce every "the tax is" amount printed in IRS Rev. Proc. 2025-32, section 4.01.
// 2. Whole bills worked by hand (federal, payroll, state) come out to the cent.
// 3. The data file is complete and well-formed: 50 states and DC, every ZIP prefix naming a known place.
// 4. The Treasury statement is picked, checked and split so the lines add up to the bill.
// 5. No state or status ever taxes a higher wage less, or takes more than the wage.
// 6. Members of Congress resolve for a ZIP, each with a debt figure for their first day; recipient lists fold and tidy.
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const HERE = dirname(fileURLToPath(import.meta.url));
const PAGES = join(HERE, '..', '..', 'prismet-site', 'pages');
const calc = await import(join(PAGES, 'scam-calc.js'));
const data = JSON.parse(readFileSync(join(PAGES, 'scam-data', 'tax-2026.json'), 'utf8'));
const snapshot = JSON.parse(readFileSync(join(PAGES, 'scam-data', 'mts-snapshot.json'), 'utf8'));
const congress = JSON.parse(readFileSync(join(PAGES, 'scam-data', 'congress.json'), 'utf8'));
const shard = (zip) => JSON.parse(readFileSync(join(PAGES, 'scam-data', 'zip', `${zip.slice(0, 3)}.json`), 'utf8'))[zip] || null;

let checks = 0;
const near = (a, b, what) => { assert.ok(Math.abs(a - b) <= 0.011, `${what}: ${a} is not ${b}`); checks++; };

// ── 1. the IRS's own amounts at each bracket edge ──
const IRS = {
  single: [[12400, 1240], [50400, 5800], [105700, 17966], [201775, 41024], [256225, 58448], [640600, 192979.25]],
  joint: [[24800, 2480], [100800, 11600], [211400, 35932], [403550, 82048], [512450, 116896], [768700, 206583.50]],
  head: [[17700, 1770], [67450, 7740], [105700, 16155], [201750, 39207], [256200, 56631], [640600, 191171]],
};
for (const [status, edges] of Object.entries(IRS)) {
  for (const [taxable, tax] of edges) near(calc.bracketTax(taxable, data.federal.brackets[status]), tax, `${status} at ${taxable}`);
  const [last, tax] = edges.at(-1);
  near(calc.bracketTax(last + 1000, data.federal.brackets[status]), tax + 370, `${status} above the top edge`);
}
assert.deepEqual(data.federal.standardDeduction, { single: 16100, joint: 32200, head: 24150 }); checks++;
assert.equal(calc.bracketTax(0, data.federal.brackets.single), 0); checks++;

// ── 2. bills worked by hand ──
{ // $60,000, single, Ohio
  const b = calc.computeBill({ wages: 60000, status: 'single', stateCode: 'OH' }, data);
  near(b.federalIncome, 1240 + 0.12 * (43900 - 12400), 'federal on 60k');          // 5,020
  near(b.socialSecurity, 3720, 'social security on 60k');
  near(b.medicare, 870, 'medicare on 60k');
  near(b.state, (60000 - 2400 - 26050) * 0.0275, 'Ohio on 60k');                    // 867.63
  near(b.total, 5020 + 3720 + 870 + 867.63, 'total on 60k');
  near(b.takeHome, 60000 - b.total, 'take-home on 60k');
  assert.equal(b.marginal, 0.12); checks++;
}
{ // the Social Security cap and the additional Medicare tax
  const s = calc.payrollTax(300000, 'single', data.federal), j = calc.payrollTax(300000, 'joint', data.federal);
  near(s.socialSecurity, 11439, 'social security is capped at the wage base');
  near(s.medicare, 300000 * 0.0145 + 100000 * 0.009, 'medicare, single, 300k');     // 5,250
  near(j.medicare, 300000 * 0.0145 + 50000 * 0.009, 'medicare, joint, 300k');       // 4,800
}
{ // states: none, a tax credit, a graduated schedule with a credit, a credit larger than the tax
  for (const code of ['TX', 'FL', 'WA', 'NH', 'TN', 'AK', 'NV', 'SD', 'WY']) near(calc.stateIncomeTax(250000, 'single', data.state.states[code]).tax, 0, `${code} taxes no wages`);
  near(calc.stateIncomeTax(100000, 'single', data.state.states.UT).tax, 4500 - 966, 'Utah on 100k');
  near(calc.stateIncomeTax(100000, 'single', data.state.states.CA).tax,
    110.79 + 303.70 + 607.52 + 965.40 + 1214.56 + (94460 - 72724) * 0.093 - 153, 'California on 100k');
  near(calc.stateIncomeTax(3000, 'single', data.state.states.AR).tax, 0, 'a credit never makes the tax negative');
  near(calc.stateIncomeTax(100000, 'head', data.state.states.CA).tax, calc.stateIncomeTax(100000, 'single', data.state.states.CA).tax, 'head of household uses the single schedule');
  near(calc.stateIncomeTax(200000, 'joint', data.state.states.NJ).tax,
    20000 * 0.014 + 30000 * 0.0175 + 20000 * 0.0245 + 10000 * 0.035 + 70000 * 0.0553 + (198000 - 150000) * 0.0637, 'New Jersey, joint, 200k');
  near(calc.computeBill({ wages: 10000, status: 'single', stateCode: 'OH' }, data).federalIncome, 0, 'under the standard deduction');
}

// ── 3. the data file ──
const states = data.state.states;
assert.equal(Object.keys(states).length, 51); checks++;
assert.deepEqual(Object.keys(states).filter((c) => states[c].wages === 'none').sort(), ['AK', 'FL', 'NH', 'NV', 'SD', 'TN', 'TX', 'WA', 'WY']); checks++;
for (const [code, s] of Object.entries(states)) {
  assert.match(code, /^[A-Z]{2}$/); assert.ok(s.name, code);
  if (s.wages !== 'taxed') continue;
  for (const who of ['single', 'joint']) {
    assert.ok(s[who].length >= 1 && s[who].every(([over, rate], i) => over >= 0 && rate >= 0 && rate < 0.15 && (!i || over > s[who][i - 1][0])), `${code} ${who} brackets`);
    assert.ok(s.deduct[who] >= 0 && s.credit[who] >= 0, `${code} ${who} deduction`);
  }
  checks++;
}
const places = new Set([...Object.keys(states), ...Object.keys(data.state.territories)]);
assert.ok(Object.keys(data.zip.prefixes).length > 880); checks++;
for (const [k, v] of [...Object.entries(data.zip.prefixes), ...Object.entries(data.zip.exceptions)]) assert.ok(/^\d{3}(\d{2})?$/.test(k) && places.has(v), `ZIP ${k} → ${v}`);
checks++;
for (const [zip, want] of [['43201', 'OH'], ['10001', 'NY'], ['90210', 'CA'], ['60601', 'IL'], ['77002', 'TX'], ['98101', 'WA'], ['20001', 'DC'], ['99501', 'AK'], ['96813', 'HI'], ['03579', 'ME']]) {
  assert.deepEqual(calc.placeForZip(zip, data), { code: want }, `ZIP ${zip}`); checks++;
}
assert.deepEqual(calc.placeForZip('00901', data), { code: 'PR', territory: true }); checks++;
for (const bad of ['', '4320', '432011', 'abcde', '00000', null]) { assert.equal(calc.placeForZip(bad, data), null, `ZIP ${bad}`); checks++; }

// ── 4. the Treasury statement ──
const st = calc.pickStatement(snapshot.rows);
assert.ok(st, 'the snapshot holds a usable statement'); checks++;
assert.ok(st.months >= 6 || st.date.slice(5, 7) === '09', 'the snapshot is at least half a fiscal year'); checks++;
assert.ok(st.outlays > st.receipts && st.borrowedPerDollar > 0 && st.borrowedPerDollar < 2); checks++;
for (const amount of [5020, 0.01, 123456.78, 1]) {
  const lines = calc.splitByFunction(amount, st);
  near(lines.reduce((s, l) => s + l.amount, 0), amount, `lines add up to ${amount}`);
  near(lines.reduce((s, l) => s + l.share, 0), 1, 'shares add up to 1');
  assert.equal(lines.length, 9); assert.ok(lines.at(-1).rest);
  assert.ok(!lines.some((l) => l.name === 'Social Security' || l.name === 'Medicare'), 'payroll programs stay off the income tax split'); checks++;
  assert.ok(lines.slice(0, -1).every((l, i, a) => !i || l.amount <= a[i - 1].amount), 'largest first'); checks++;
}
assert.equal(calc.splitByFunction(5020, st)[0].name, 'Net Interest', 'with Social Security and Medicare on their own lines, interest leads'); checks++;
near(calc.borrowedFor(1000, st), Math.round(1000 * st.borrowedPerDollar * 100) / 100, 'borrowed on top');
{ // which statement: a young fiscal year yields to the last full one; a broken table is refused
  const month = (d, scale = 1) => snapshot.rows.map((r) => ({ ...r, d, a: Math.round(r.a * scale) }));
  assert.equal(calc.pickStatement([...month('2026-11-30', 0.2), ...month('2026-09-30')]).date, '2026-09-30'); checks++;
  assert.equal(calc.pickStatement(month('2026-11-30', 0.2)).date, '2026-11-30'); checks++;
  assert.equal(calc.pickStatement([...month('2027-04-30'), ...month('2026-09-30')]).date, '2027-04-30'); checks++;
  assert.equal(calc.pickStatement(month('2026-09-30')).fiscalYear, 2026); assert.equal(calc.pickStatement(month('2026-11-30')).fiscalYear, 2027); checks += 2;
  assert.equal(calc.pickStatement(snapshot.rows.filter((r) => r.n !== 'National Defense')), null, 'a missing function fails the totals check'); checks++;
  assert.equal(calc.pickStatement([]), null); checks++;
  assert.equal(calc.statementRows({ data: [{ record_date: '2026-08-31', classification_desc: 'Receipts', current_fytd_rcpt_outly_amt: 'null', record_type_cd: 'SL', sequence_level_nbr: '1' }] }).length, 0); checks++;
}

// ── time ──
{
  const t = calc.timeUnits({ rate: 0.25, total: 26000 }, 2026);
  assert.deepEqual([t.dayOfYear, t.freedomDay, t.minutesPerWorkday, t.perPaycheck, t.days], [92, '2026-04-02', 120, 1000, 365]); checks++;
  assert.equal(calc.timeUnits({ rate: 0, total: 0 }, 2026).freedomDay, '2026-01-01'); assert.equal(calc.timeUnits({ rate: 0.25, total: 1 }, 2028).days, 366); checks += 2;
}

// ── 5. nobody pays less for earning more, or more than they earn ──
for (const code of Object.keys(states)) {
  for (const status of ['single', 'joint', 'head']) {
    let prev = -1;
    for (let wages = 0; wages <= 3_000_000; wages += wages < 200_000 ? 1_000 : 50_000) {
      const b = calc.computeBill({ wages, status, stateCode: code }, data);
      assert.ok(b.total >= prev, `${code} ${status}: the bill fell at ${wages}`);
      assert.ok(b.total <= wages * 0.6, `${code} ${status}: ${b.total} on ${wages}`);
      prev = b.total;
    }
    checks++;
  }
}

// ── 6. who signs off, and what landed locally ──
assert.ok(congress.members.length >= 530 && congress.members.length <= 541, 'about 535 members and 6 delegates'); checks++;
assert.ok(congress.debtNow.amount > 3e13 && /^\d{4}-\d\d-\d\d$/.test(congress.debtNow.date)); checks++;
for (const m of congress.members) {
  assert.ok(m.n && /^[A-Z]{2}$/.test(m.s) && (m.t === 'sen' || (m.t === 'rep' && Number.isInteger(m.d))) && m.since <= m.end, `member ${m.n}`);
  assert.ok(m.debt && m.debt.amount > 1e11 && m.debt.amount <= congress.debtNow.amount && m.debt.date <= congress.debtNow.date, `debt on ${m.n}'s first day`);
  assert.ok(m.debt.yearEnd ? m.debt.date <= m.since : m.debt.date >= m.since, `${m.n}: the debt figure is on the right side of the first day`);
}
checks += 3;
for (const code of Object.keys(states)) if (code !== 'DC') { assert.ok(calc.officialsFor(null, code, congress).senators.length <= 2, `${code} senators`); checks++; }
assert.ok(Object.keys(states).filter((c) => c !== 'DC' && calc.officialsFor(null, c, congress).senators.length === 2).length >= 47, 'nearly every state has both senators seated'); checks++;
{
  const col = calc.officialsFor(shard('43201'), 'OH', congress);
  assert.deepEqual(shard('43201'), { c: '39049', d: ['OH3'] }); checks++;
  assert.equal(col.seats.length, 1); assert.equal(col.seats[0].district, 3); assert.equal(col.seats[0].member.s, 'OH'); checks += 3;
  const split = calc.officialsFor(shard('43206'), 'OH', congress);
  assert.deepEqual(split.seats.map((x) => x.district), [3, 15], 'a ZIP across a district line lists both, largest first'); checks++;
  assert.equal(calc.officialsFor(shard('82001'), 'WY', congress).seats[0].district, 0, 'an at-large seat'); checks++;
  assert.equal(calc.officialsFor(shard('20001'), 'DC', congress).seats[0].member.s, 'DC', 'the District has a delegate'); checks++;
  assert.equal(calc.officialsFor(shard('20001'), 'DC', congress).senators.length, 0); checks++;
  assert.deepEqual(calc.officialsFor(null, 'OH', congress).seats, []); checks++;
  const d = calc.debtSince(col.seats[0].member, congress.debtNow);
  assert.ok(d.added > 0 && d.times > 1 && d.then + d.added === d.now); checks++;
  assert.equal(calc.debtSince({ n: 'x' }, congress.debtNow), null); checks++;
}
assert.deepEqual(calc.lastFiscalYear('2026-10-03'), { fy: 2026, start: '2025-10-01', end: '2026-09-30' });
assert.deepEqual(calc.lastFiscalYear('2026-09-30'), { fy: 2025, start: '2024-10-01', end: '2025-09-30' });
assert.deepEqual(calc.lastFiscalYear('2027-01-15'), { fy: 2026, start: '2025-10-01', end: '2026-09-30' }); checks += 3;
assert.equal(calc.tidyName('OHIO STATE UNIVERSITY, THE'), 'The Ohio State University');
assert.equal(calc.tidyName('BATTELLE MEMORIAL INSTITUTE'), 'Battelle Memorial Institute');
assert.equal(calc.tidyName('REDACTED DUE TO PII'), 'Individuals (names withheld)'); checks++;
assert.equal(calc.tidyName('ACME  WIDGETS OF OHIO LLC'), 'Acme Widgets of Ohio LLC');
assert.equal(calc.tidyName("O'NEIL-SMITH & SONS"), "O'Neil-Smith & Sons"); checks += 4;
assert.deepEqual(calc.topRecipients([{ name: 'OHIO STATE UNIVERSITY, THE', amount: 90 }, { name: 'BATTELLE', amount: 300 }, { name: 'OHIO STATE UNIVERSITY, THE', amount: 7 }, { name: 'REFUND CO', amount: -5 }, { name: '', amount: 9 }], 8),
  [{ name: 'Battelle', amount: 300 }, { name: 'The Ohio State University', amount: 97 }]); checks++;

console.log(`✓ scam-check: ${checks} checks`);
