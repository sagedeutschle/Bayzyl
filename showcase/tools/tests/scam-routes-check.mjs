import assert from 'node:assert/strict';
import { createPrismetServer } from '../../server/server.js';

const server = createPrismetServer({ debt: { async getSnapshot() { throw new Error('Static tax routes must not fetch debt data'); } } });
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const base = `http://127.0.0.1:${server.address().port}`;
try {
  for (const path of ['/scam-data/local-2026.json', '/scam-data/zip-local/432.json', '/scam-data/zip-local/100.json', '/scam-data/zip/432.json']) {
    const response = await fetch(base + path);
    assert.equal(response.status, 200, `${path} is shipped`);
    assert.match(response.headers.get('content-type'), /application\/json/);
    assert.equal(response.headers.get('cache-control'), 'public, max-age=300');
    const data = await response.json();
    assert.ok(data && !Array.isArray(data) && Object.keys(data).length > 0);
    const head = await fetch(base + path, { method: 'HEAD' });
    assert.equal(head.status, 200);
    assert.equal(await head.text(), '');
  }
  const versioned = await fetch(base + '/scam-data/local-2026.json?v=20261004c');
  assert.equal(versioned.headers.get('cache-control'), 'public, max-age=31536000, immutable');
  const zip = await (await fetch(base + '/scam-data/zip-local/432.json')).json();
  assert.deepEqual(zip['43201'], [5349, 440000, 17, '3918000', 100, 0.08]);
  for (const missing of ['/scam-data/zip-local/000.json', '/scam-data/zip-local/', '/scam-data/unknown.json']) {
    assert.equal((await fetch(base + missing)).status, 404, 'Unavailable data stays missing, not an empty success');
  }
  console.log('✓ Uncle Scam routes: additive data, JSON/cache/HEAD contracts and honest missing files');
} finally {
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
}
