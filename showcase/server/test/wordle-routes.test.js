import assert from 'node:assert/strict';
import { request } from 'node:http';
import test from 'node:test';
import { createPrismetServer } from '../server.js';
import { createDailyWordService } from '../wordle-daily.js';

test('daily HTTP route preserves legacy JSON, honors local date, rejects bad dates and keeps HEAD offline', async () => {
  const originalFetch = globalThis.fetch;
  let upstreamReads = 0;
  // The pre-fix route ignores the injected service. Keep that red run offline too.
  globalThis.fetch = async () => {
    upstreamReads++;
    return new Response(JSON.stringify({ answer: 'slate', date: '2026-11-02', sourceName: 'Daily' }));
  };
  const wordle = createDailyWordService({
    now: () => new Date('2026-11-03T01:00:00Z'),
    fetchImpl: async url => {
      upstreamReads++;
      const date = url.endsWith('2026-11-03.json') ? '2026-11-03' : '2026-11-02';
      return new Response(JSON.stringify({ id: 12345, solution: date.endsWith('03') ? 'crane' : 'slate', print_date: date, days_since_launch: 1962, editor: 'Editor' }));
    },
  });
  const server = createPrismetServer({ wordle });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  async function get(path, method = 'GET') {
    return new Promise((resolve, reject) => {
      const req = request({ hostname: '127.0.0.1', port: server.address().port, path, method }, res => {
        let body = '';
        res.setEncoding('utf8');
        res.on('data', chunk => { body += chunk; });
        res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: body ? JSON.parse(body) : null }));
      });
      req.on('error', reject);
      req.end();
    });
  }
  try {
    const local = await get('/api/wordle?date=2026-11-03');
    assert.equal(local.status, 200);
    assert.deepEqual(local.body, { answer: 'crane', date: '2026-11-03', sourceName: 'Daily' });
    assert.equal(local.headers['cache-control'], 'no-store');
    assert.deepEqual((await get('/api/wordle')).body, { answer: 'slate', date: '2026-11-02', sourceName: 'Daily' });
    const beforeHead = upstreamReads;
    const head = await get('/api/wordle?date=2026-11-03', 'HEAD');
    assert.equal(head.status, 200);
    assert.equal(head.body, null);
    assert.equal(upstreamReads, beforeHead, 'HEAD must not initiate upstream work');
    for (const query of ['?date=', '?date=2026-11-3', '?date=2026-11-31', '?date=2026-11-01', '?date=2026-11-04', '?date=2026-11-03&date=2026-11-02']) {
      const response = await get(`/api/wordle${query}`);
      assert.equal(response.status, 400, query);
      assert.equal(response.headers['cache-control'], 'no-store');
    }
    assert.equal(upstreamReads, beforeHead, 'invalid input must not reach the puzzle upstream');
    assert.equal((await get('/api/wordle?date=2026-11-03', 'POST')).status, 405);
  } finally {
    globalThis.fetch = originalFetch;
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
  }
});
