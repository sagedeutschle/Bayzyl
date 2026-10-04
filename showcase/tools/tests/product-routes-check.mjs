import assert from 'node:assert/strict';
import { createPrismetServer } from '../../server/server.js';

// A missing route or failure to use the injected service makes this fail without
// contacting any public data provider. The existing portfolio stays on disk.
let calls = 0;
const snapshot = { version: 1, status: 'partial', fetchedAt: '2026-10-04T00:00:00.000Z', metrics: [], groups: [], sources: [] };
const server = createPrismetServer({ debt: { async getSnapshot() { calls++; return snapshot; } } });
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const base = `http://127.0.0.1:${server.address().port}`;
try {
  const response = await fetch(`${base}/api/debt`);
  assert.equal(response.status, 200, 'Debt data has a first-class JSON route');
  assert.deepEqual(await response.json(), snapshot);
  assert.equal(calls, 1);
  assert.match(response.headers.get('content-security-policy'), /default-src 'self'/);
  const head = await fetch(`${base}/api/debt`, { method: 'HEAD' });
  assert.equal(head.status, 200);
  assert.equal(await head.text(), '');
  assert.equal(calls, 1, 'HEAD must not trigger an upstream data refresh');
  assert.equal((await fetch(`${base}/api/debt`, { method: 'POST' })).status, 405);
  assert.equal((await fetch(`${base}/api/unrecognized`)).status, 404);
  for (const route of ['/arcade', '/arcade/', '/arcade/2048', '/arcade/minesweeper', '/arcade/lights-out', ...['wordle','rubiks-cube','snake','sudoku','sliding-15','nonogram','chess','reversi','checkers','connect-four','gomoku','sea-battle','solitaire','spider','crazy-8','catan','brick-bench'].map(id=>`/arcade/${id}`), '/tools', '/tools/']) {
    const page = await fetch(base + route);
    assert.equal(page.status, 200, `${route} has a static destination`);
    assert.match(page.headers.get('content-type'), /text\/html/);
    assert.match(await page.text(), /Prismet/);
  }
  const association = await fetch(base + '/.well-known/apple-app-site-association');
  assert.equal(association.status, 200);
  assert.match(association.headers.get('content-type'), /application\/json/);
  assert.equal((await association.json()).applinks.details.length > 0, true);
  const config = await fetch(base + '/api/arcade/config');
  assert.equal(config.status, 200);
  assert.equal((await config.json()).appStoreUrl, 'https://apps.apple.com/us/app/kaleidescope/id6785993194');
  assert.equal(config.headers.get('cache-control'), 'no-store');
  assert.equal((await fetch(base + '/api/arcade/unknown')).status, 404);
  console.log('✓ product-routes: debt, Arcade, tools, association JSON, API headers and method boundaries');
} finally {
  server.closeAllConnections();
  await new Promise((resolve) => server.close(resolve));
}
