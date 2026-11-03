import assert from 'node:assert/strict';
import test from 'node:test';
import * as daily from '../wordle-daily.js';

const instant = new Date('2026-11-03T01:00:00Z'); // Nov 2 in New York, Nov 3 in Tokyo.
const released = (date, solution = 'crane') => new Response(JSON.stringify({
  id: 12345, solution, print_date: date, days_since_launch: 1962, editor: 'Editor',
}), { headers: { 'content-type': 'application/json' } });
const code = expected => error => error?.code === expected;
function service(options = {}) {
  assert.equal(typeof daily.createDailyWordService, 'function', 'the daily service must resolve the released puzzle by requested date');
  return daily.createDailyWordService({ now: () => instant, ...options });
}

test('legacy requests retain the New York date while local-date requests can advance immediately', () => {
  assert.equal(daily.resolveDailyWordDate?.(undefined, instant), '2026-11-02');
  assert.equal(daily.resolveDailyWordDate?.('2026-11-03', instant), '2026-11-03');
});

test('date requests reject malformed, nonexistent, duplicate-shaped, and inactive dates', () => {
  for (const date of ['', '2026-11-3', '2026-11-31', '2026-11-01', '2026-11-04', ' 2026-11-02', ['2026-11-02']]) {
    assert.throws(() => daily.resolveDailyWordDate?.(date, instant), code('wordleDate'), String(date));
  }
});

test('date bounds cover all currently active local dates at UTC minus 12 through plus 14', () => {
  const now = new Date('2026-11-03T11:00:00Z');
  for (const date of ['2026-11-02', '2026-11-03', '2026-11-04']) {
    assert.equal(daily.resolveDailyWordDate?.(date, now), date);
  }
  assert.throws(() => daily.resolveDailyWordDate?.('2026-11-05', now), code('wordleDate'));
});

test('the public released-puzzle payload maps to the existing response without exposing other upstream fields', async () => {
  const daily = service({ fetchImpl: async url => {
    assert.equal(url, 'https://www.nytimes.com/svc/wordle/v2/2026-11-03.json');
    return released('2026-11-03', 'CRANE');
  } });
  assert.deepEqual(await daily.getDailyWord('2026-11-03'), { answer: 'crane', date: '2026-11-03', sourceName: 'Daily' });
});

test('date-keyed cache cannot return the previous puzzle at a local or New York midnight', async () => {
  let now = instant;
  let fetches = 0;
  const daily = service({ now: () => now, fetchImpl: async url => {
    fetches++;
    return url.endsWith('2026-11-02.json') ? released('2026-11-02', 'slate') : released('2026-11-03', 'crane');
  } });
  assert.equal((await daily.getDailyWord()).answer, 'slate');
  assert.equal((await daily.getDailyWord('2026-11-03')).answer, 'crane');
  assert.equal((await daily.getDailyWord('2026-11-02')).answer, 'slate');
  assert.equal(fetches, 2, 'repeat reads reuse only the matching date');
  now = new Date('2026-11-03T05:00:00Z');
  assert.equal((await daily.getDailyWord()).date, '2026-11-03');
});

test('successful cache entries expire and refresh instead of pinning a corrected answer all day', async () => {
  let now = instant;
  let fetches = 0;
  const daily = service({ now: () => now, cacheMs: 1000, fetchImpl: async () => released('2026-11-03', ++fetches === 1 ? 'slate' : 'crane') });
  assert.equal((await daily.getDailyWord('2026-11-03')).answer, 'slate');
  now = new Date(instant.getTime() + 1001);
  assert.equal((await daily.getDailyWord('2026-11-03')).answer, 'crane');
});

test('a failed or wrong-date fetch never becomes a cached daily answer', async () => {
  let fetches = 0;
  const daily = service({ fetchImpl: async () => ++fetches === 1 ? released('2026-11-02') : released('2026-11-03') });
  await assert.rejects(daily.getDailyWord('2026-11-03'), code('wordleStale'));
  assert.equal((await daily.getDailyWord('2026-11-03')).date, '2026-11-03');
  assert.equal(fetches, 2);
});

test('only an exact five-letter ASCII solution and matching print date are accepted', async () => {
  for (const payload of [
    { solution: 'c!rane', print_date: '2026-11-03' },
    { solution: 'caféx', print_date: '2026-11-03' },
    { solution: 12345, print_date: '2026-11-03' },
    { answer: 'crane', date: '2026-11-03' },
    { solution: 'crane', print_date: '2026-11-3' },
  ]) {
    const daily = service({ fetchImpl: async () => new Response(JSON.stringify(payload)) });
    await assert.rejects(daily.getDailyWord('2026-11-03'), error => ['wordleFormat', 'wordleStale'].includes(error?.code));
  }
});

test('upstream HTTP failures and invalid JSON remain retryable failures', async () => {
  for (const response of [new Response('denied', { status: 403 }), new Response('not json')]) {
    const daily = service({ fetchImpl: async () => response });
    await assert.rejects(daily.getDailyWord('2026-11-03'), code('wordleFetch'));
  }
});

test('upstream bodies exceeding 32 KiB are cancelled while streaming', async () => {
  let cancelled = false;
  const daily = service({ fetchImpl: async () => new Response(new ReadableStream({
    start(controller) { controller.enqueue(new Uint8Array(17_000)); controller.enqueue(new Uint8Array(17_000)); },
    cancel() { cancelled = true; },
  })) });
  await assert.rejects(daily.getDailyWord('2026-11-03'), code('wordleFetch'));
  assert.equal(cancelled, true);
});

test('upstream timeout aborts the request and does not poison the next attempt', async () => {
  let fetches = 0;
  let aborted = false;
  const daily = service({ timeoutMs: 20, fetchImpl: async (_url, { signal }) => {
    if (++fetches > 1) return released('2026-11-03');
    return new Promise((_resolve, reject) => signal.addEventListener('abort', () => { aborted = true; reject(signal.reason); }, { once: true }));
  } });
  await assert.rejects(daily.getDailyWord('2026-11-03'), code('wordleFetch'));
  assert.equal(aborted, true);
  assert.equal((await daily.getDailyWord('2026-11-03')).answer, 'crane');
});

test('concurrent requests for the same date share one bounded upstream fetch', async () => {
  let finish;
  let fetches = 0;
  const daily = service({ fetchImpl: () => { fetches++; return new Promise(resolve => { finish = resolve; }); } });
  const first = daily.getDailyWord('2026-11-03');
  const second = daily.getDailyWord('2026-11-03');
  finish(released('2026-11-03'));
  assert.deepEqual(await first, await second);
  assert.equal(fetches, 1);
});
