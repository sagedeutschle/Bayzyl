import assert from 'node:assert/strict';
import { createPrismetServer } from '../../server/server.js';

// Catch a missing/overbroad catalog query mapping at the HTTP boundary without
// live provider calls. The data service separately tests the real projections.
let calls = 0;
const legacy = { version: 1, status: 'partial', fetchedAt: '2026-10-04T00:00:00Z', metrics: [{ id: 'totalDebt' }], groups: [], sources: [] };
const expanded = { ...legacy, metrics: [{ id: 'totalDebt' }, { id: 'realMedianWeeklyEarnings' }] };
const server = createPrismetServer({ debt: {
  async getSnapshot(options) {
    calls++;
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
  console.log('✓ financial routes: explicit catalog opt-in, legacy default, no-store, HEAD and method boundaries');
} finally {
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
}
