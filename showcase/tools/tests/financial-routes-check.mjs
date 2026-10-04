import assert from 'node:assert/strict';
import { createPrismetServer } from '../../server/server.js';

// Catch a missing/overbroad catalog query mapping at the HTTP boundary without
// live provider calls. The data service separately tests the real projections.
let calls = 0;
const optionsSeen = [];
const legacy = { version: 1, status: 'partial', fetchedAt: '2026-10-04T00:00:00Z', metrics: [{ id: 'totalDebt' }], groups: [], sources: [] };
const expanded = { ...legacy, metrics: [{ id: 'totalDebt' }, { id: 'realMedianWeeklyEarnings' }] };
const server = createPrismetServer({ debt: {
  async getSnapshot(options) {
    calls++;
    optionsSeen.push(options);
    return options.expanded ? expanded : legacy;
  },
} });
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const base = `http://127.0.0.1:${server.address().port}`;
try {
  for (const query of ['', '?catalog=unknown', '?catalog=true']) {
    const response = await fetch(`${base}/api/debt${query}`);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), legacy, 'Unversioned and unknown catalog requests keep the legacy response');
  }
  const response = await fetch(`${base}/api/debt?catalog=expanded`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), expanded, 'Expanded catalog requires the explicit supported query');
  assert.equal(response.headers.get('cache-control'), 'no-store');
  const beforeHead = calls;
  const head = await fetch(`${base}/api/debt?catalog=expanded`, { method: 'HEAD' });
  assert.equal(head.status, 200);
  assert.equal(await head.text(), '');
  assert.equal(calls, beforeHead, 'HEAD never initiates data work');
  assert.equal((await fetch(`${base}/api/debt?catalog=expanded`, { method: 'POST' })).status, 405);
  // Reading the cached catalog is a normal navigation, not an upstream refresh.
  // A few reloads across shared links must not exhaust the stricter refresh cap.
  for (let i = 0; i < 8; i++) assert.equal((await fetch(`${base}/api/debt?catalog=expanded`)).status, 200, 'Repeated cached reads stay usable');
  assert.ok(optionsSeen.every(options => options.force === false));
  for (let i = 0; i < 6; i++) assert.equal((await fetch(`${base}/api/debt?refresh=1`)).status, 200);
  assert.ok(optionsSeen.slice(-6).every(options => options.force === true));
  const beforeThrottle = calls;
  const limitedRefresh = await fetch(`${base}/api/debt?force=true`);
  assert.equal(limitedRefresh.status, 429, 'Both force aliases share the strict refresh budget');
  assert.equal(limitedRefresh.headers.get('retry-after'), '60');
  assert.equal(calls, beforeThrottle, 'Throttled refresh never reaches the provider service');
  for (let i = 12; i < 30; i++) assert.equal((await fetch(`${base}/api/debt`)).status, 200, 'The read budget is separate and shared by both catalogs');
  assert.equal((await fetch(`${base}/api/debt?catalog=expanded`)).status, 429, 'Cached reads remain bounded');
  const beforeFinalHead = calls;
  assert.equal((await fetch(`${base}/api/debt`, { method: 'HEAD' })).status, 200);
  assert.equal(calls, beforeFinalHead);
  console.log('✓ financial routes: explicit catalog, legacy/HEAD/method contracts, separate bounded reads and refreshes');
} finally {
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
}
