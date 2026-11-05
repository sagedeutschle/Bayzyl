import assert from 'node:assert/strict';
import { request } from 'node:http';
import { EventEmitter, once } from 'node:events';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { createMetricsServer, createPageViews } from '../page-views.js';
import { createPrismetServer } from '../server.js';

function send(port, path, { method = 'GET', headers = {} } = {}) {
  return new Promise((resolve, reject) => {
    const req = request({
      host: '127.0.0.1', port, method, path,
      headers: { host: 'prismet.xyz', 'user-agent': 'Mozilla/5.0', ...headers },
    }, res => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', chunk => { body += chunk; });
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body }));
    });
    req.on('error', reject);
    req.end();
  });
}

test('server counts completed public page responses and keeps aggregate metrics off the public port', async t => {
  const site = fileURLToPath(new URL('../site/', import.meta.url));
  const pageViews = createPageViews(site);
  const server = createPrismetServer({ pageViews });
  const metrics = createMetricsServer(pageViews);
  for (const listener of [server, metrics]) {
    listener.listen(0, '127.0.0.1');
    await once(listener, 'listening');
    t.after(() => new Promise(resolve => listener.close(resolve)));
  }
  const publicPort = server.address().port;
  const privatePort = metrics.address().port;
  const initial = await send(privatePort, '/metrics');
  assert.equal(initial.status, 200);
  assert.match(initial.body, /^prismet_page_views_collection_start_seconds [1-9]\d+$/m);
  assert.match(initial.body, /^prismet_page_views_total\{page="\/"\} 0$/m);
  assert.equal((await send(publicPort, '/metrics')).status, 404);
  assert.equal((await send(privatePort, '/')).status, 404);

  const hidden = 'not-a-metric-label';
  const privateHeaders = { host: 'www.prismet.xyz', referer: 'https://' + hidden, cookie: 'id=' + hidden, 'x-forwarded-for': '192.0.2.43' };
  for (const [path, options, status] of [
    ['/', {}, 200],
    ['/index.html?q=' + hidden, { headers: privateHeaders }, 200],
    ['/arcade/chess/', {}, 200],
    ['/work/bayzyl.html', {}, 200],
    ['/', { method: 'HEAD' }, 200],
    ['/', { method: 'OPTIONS' }, 204],
    ['/', { method: 'POST' }, 405],
    ['/', { headers: { host: 'prismet.xyz.attacker.invalid', 'x-forwarded-host': 'prismet.xyz' } }, 200],
    ['/', { headers: { host: 'prismet-site-restless-horizon-217.fly.dev' } }, 200],
    ['/', { headers: { 'user-agent': '' } }, 200],
    ['/', { headers: { 'user-agent': 'Mozilla/5.0 (compatible; Googlebot/2.1)' } }, 200],
    ['/', { headers: { 'user-agent': 'curl/8' } }, 200],
    ['/', { headers: { 'sec-purpose': 'prefetch;prerender' } }, 200],
    ['/', { headers: { 'sec-gpc': '1' } }, 200],
    ['/', { headers: { dnt: '1' } }, 200],
    ['/edit', {}, 200], ['/edit.html', {}, 200], ['/site.css', {}, 200], ['/healthz', {}, 200],
    ['/api/arcade/config', {}, 200], ['/arcade/game.html', {}, 200],
    ['/work/' + hidden + '.html', {}, 404],
  ]) assert.equal((await send(publicPort, path, options)).status, status, path);
  const result = await send(privatePort, '/metrics');
  assert.match(result.headers['content-type'], /text\/plain; version=0.0.4/);
  assert.match(result.body, /^prismet_page_views_total\{page="\/"\} 2$/m);
  assert.match(result.body, /^prismet_page_views_total\{page="\/arcade\/chess"\} 1$/m);
  assert.match(result.body, /^prismet_page_views_total\{page="\/work\/bayzyl\.html"\} 1$/m);
  const values = [...result.body.matchAll(/^prismet_page_views_total\{page="[^"]+"\} (\d+)$/gm)];
  assert.equal(values.reduce((sum, [, value]) => sum + Number(value), 0), 4);
  assert.doesNotMatch(result.body, /not-a-metric-label|192\.0\.2\.43|user-agent|page="\/edit/);
});

test('a page increments after finish and an interrupted response never increments', () => {
  const site = fileURLToPath(new URL('../site/', import.meta.url));
  const views = createPageViews(site);
  const req = { method: 'GET', headers: { host: 'prismet.xyz', 'user-agent': 'Mozilla/5.0' } };
  const interrupted = new EventEmitter();
  views.observe(req, interrupted, '/');
  interrupted.emit('close');
  const completed = new EventEmitter();
  views.observe(req, completed, '/');
  assert.match(views.exposition(), /^prismet_page_views_total\{page="\/"\} 0$/m);
  completed.emit('finish');
  completed.emit('close');
  assert.match(views.exposition(), /^prismet_page_views_total\{page="\/"\} 1$/m);
});

test('the public page counters remain available when a partial build has no work pages', t => {
  const site = mkdtempSync(join(tmpdir(), 'page-views-'));
  t.after(() => rmSync(site, { recursive: true, force: true }));
  const text = createPageViews(site).exposition();
  assert.match(text, /^prismet_page_views_total\{page="\/"\} 0$/m);
  assert.doesNotMatch(text, /page="\/work\//);
});
