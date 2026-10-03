// edit-check.mjs: the /edit page must read and write the content files exactly as the build does.
//   node showcase/tools/tests/edit-check.mjs
// 1. parseSections/writeSections in pages/edit.js give the same results as content.mjs on every content file.
// 2. writeSections keeps comments, order and untouched sections; an edit round-trips through parse.
// 3. makeClient talks to the site's own /api/edit/* routes (checked against a fake fetch; nothing goes on the network).
// 4. edit-api.js (the server side): PIN check, sessions, lockout, the path allow-list and the GitHub proxy, against a
//    fake GitHub and fake request objects.
import { readFileSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { EventEmitter } from 'node:events';
import assert from 'node:assert/strict';

const HERE = dirname(fileURLToPath(import.meta.url));
const SITE = join(HERE, '..', '..', 'prismet-site');
const edit = await import(join(SITE, 'pages', 'edit.js'));
const content = await import(join(SITE, 'content.mjs'));
const { createEditApi } = await import(join(HERE, '..', '..', 'server', 'edit-api.js'));

let checks = 0;
const files = ['content/site.md', ...readdirSync(join(SITE, 'content/work')).filter((f) => f.endsWith('.md')).map((f) => `content/work/${f}`)];
for (const rel of files) {
  const text = readFileSync(join(SITE, rel), 'utf8');
  const a = edit.parseSections(text), b = content.parseSections(text);
  assert.deepEqual(a, b, `${rel}: parse differs from content.mjs`); checks++;
  const keys = Object.keys(a); assert.ok(keys.length > 0, `${rel}: no sections`);
  const k = keys[Math.floor(keys.length / 2)];
  const updates = { [k]: `${a[k]}\nEdited line with *gold* and {count}.` };
  const w1 = edit.writeSections(text, updates), w2 = content.writeSections(text, updates);
  assert.equal(w1, w2, `${rel}: write differs from content.mjs`); checks++;
  const again = edit.parseSections(w1);
  assert.equal(again[k], updates[k].trim(), `${rel}: the edit did not round-trip`); checks++;
  for (const other of keys) if (other !== k) assert.equal(again[other], a[other], `${rel}: ${other} changed although untouched`);
  checks++;
  if (text.includes('<!--')) assert.ok(w1.includes('<!--'), `${rel}: the comment block was lost`), checks++;
  assert.equal(Object.keys(again).join(','), keys.join(','), `${rel}: section order changed`); checks++;
}

assert.deepEqual(edit.lostVars('{shown} of {total} records', 'all records'), ['{shown}', '{total}']);
assert.deepEqual(edit.lostVars('{shown} of {total}', '{total} / {shown}'), []);
assert.equal(edit.label('hero.directory_source'), 'Hero Directory Source');
assert.equal(edit.isLong('facts', '- a: b'), true); assert.equal(edit.isLong('title', 'Bayzyl'), false); checks += 4;

// ── the page's client against a fake fetch ──
const calls = [];
const pageFetch = async (url, init = {}) => {
  calls.push({ url, method: init.method || 'GET', body: init.body ? JSON.parse(init.body) : null, headers: init.headers, credentials: init.credentials });
  const body = url.endsWith('/api/edit/status') ? { ok: true, configured: true, authed: false, branch: 'main' }
    : url.endsWith('/api/edit/files') ? { ok: true, files: ['showcase/prismet-site/content/site.md'] }
    : init.method === 'PUT' ? { ok: true, commit: { sha: 'c0ffee1' }, content: { sha: 'newsha' } }
    : url.includes('/api/edit/run?') ? { ok: true, run: { status: 'completed', conclusion: 'success', html_url: 'https://github.com/x' } }
    : url.includes('/api/edit/file?') ? { ok: true, path: 'showcase/prismet-site/content/site.md', sha: 'a'.repeat(40), text: '## title\nBayzyl ✓ é\n' }
    : { ok: true };
  return { ok: true, status: 200, json: async () => body };
};
const c = edit.makeClient(pageFetch);
assert.equal((await c.status()).configured, true);
await c.unlock('123456');
assert.deepEqual(await c.listFiles(), ['showcase/prismet-site/content/site.md']);
const f = await c.getFile('showcase/prismet-site/content/site.md');
assert.equal(f.text, '## title\nBayzyl ✓ é\n');
const put = await c.putFile(f.path, '## title\nNew ✓ é\n', f.sha, 'msg');
assert.equal(put.commit.sha, 'c0ffee1');
assert.equal((await c.runFor('c0ffee1')).conclusion, 'success');
assert.ok(calls.every((x) => x.url.startsWith('/api/edit/') && x.credentials === 'same-origin' && x.headers['x-prismet-edit'] === '1'), 'every call is same-origin, to /api/edit, with the header');
assert.equal(calls.find((x) => x.method === 'POST').body.pin, '123456');
assert.ok(!JSON.stringify(calls).includes('github_pat'), 'no token anywhere in the page client'); checks += 8;

// ── the server side against a fake GitHub ──
const gh = []; const fileText = '## title\nBayzyl\n';
const fakeGitHub = async (url, init = {}) => {
  gh.push({ url, method: init.method || 'GET', auth: init.headers.authorization, body: init.body ? JSON.parse(init.body) : null });
  const json = (b, status = 200) => ({ ok: status < 400, status, text: async () => JSON.stringify(b) });
  if (url.includes('/contents/showcase/prismet-site/content/work?')) return json([{ type: 'file', name: 'bayzyl.md', path: 'showcase/prismet-site/content/work/bayzyl.md' }, { type: 'file', name: 'x.txt', path: 'x' }]);
  if (init.method === 'PUT') return json({ commit: { sha: 'c0ffee1' }, content: { sha: 'b'.repeat(40) } });
  if (url.includes('/contents/')) return json({ sha: 'a'.repeat(40), content: Buffer.from(fileText).toString('base64') });
  if (url.includes('/actions/runs')) return json({ workflow_runs: [{ status: 'completed', conclusion: 'success', html_url: 'https://github.com/run' }] });
  return json({}, 404);
};
let clock = 1_000_000;
const api = createEditApi({ env: { EDIT_GITHUB_TOKEN: 'github_pat_FAKE', EDIT_PIN: '123456', EDIT_COOKIE_SECURE: '0' }, fetchImpl: fakeGitHub, clientAddress: (req) => req.ip || '1.1.1.1', now: () => clock,
  sendJSON: (req, res, status, payload, headers = {}) => { res.status = status; res.body = payload; res.headers = headers; } });
assert.equal(api.configured, true);
const req = (method, path, { body, cookie, ip, header = true } = {}) => {
  const r = new EventEmitter(); r.method = method; r.headers = { ...(cookie ? { cookie } : {}), ...(header ? { 'x-prismet-edit': '1' } : {}) }; r.ip = ip; r.destroy = () => {};
  process.nextTick(() => { if (body !== undefined) r.emit('data', Buffer.from(JSON.stringify(body))); r.emit('end'); });
  return r;
};
const call = async (method, path, opts) => { const res = {}; const u = new URL(path, 'http://x'); const handled = await api.handle(req(method, path, opts), res, u, method); return { handled, ...res }; };

let r = await call('GET', '/api/edit/status'); assert.equal(r.body.configured, true); assert.equal(r.body.authed, false); checks++;
r = await call('GET', '/api/edit/files'); assert.equal(r.status, 401, 'no session, no files'); checks++;
r = await call('POST', '/api/edit/session', { body: { pin: '000000' } }); assert.equal(r.status, 401); assert.equal(r.body.remaining, 4); checks++;
r = await call('POST', '/api/edit/session', { body: { pin: '123456' }, header: false }); assert.equal(r.status, 400, 'the header is required'); checks++;
r = await call('POST', '/api/edit/session', { body: { pin: '123456' } }); assert.equal(r.status, 200);
const cookie = r.headers['set-cookie']; assert.match(cookie, /^edit_session=[0-9a-f]{64}; Path=\/; HttpOnly; SameSite=Strict; Max-Age=28800$/); checks++;
const sid = cookie.split(';')[0];
r = await call('GET', '/api/edit/status', { cookie: sid }); assert.equal(r.body.authed, true); checks++;
r = await call('GET', '/api/edit/files', { cookie: sid }); assert.deepEqual(r.body.files, ['showcase/prismet-site/content/site.md', 'showcase/prismet-site/content/work/bayzyl.md']); checks++;
r = await call('GET', '/api/edit/file?path=showcase/prismet-site/content/site.md', { cookie: sid }); assert.equal(r.body.text, fileText); assert.equal(r.body.sha, 'a'.repeat(40)); checks++;
r = await call('GET', '/api/edit/file?path=showcase/server/server.js', { cookie: sid }); assert.equal(r.status, 400, 'only content files'); checks++;
r = await call('GET', '/api/edit/file?path=showcase/prismet-site/content/../../server/x.md', { cookie: sid }); assert.equal(r.status, 400, 'no traversal'); checks++;
r = await call('PUT', '/api/edit/file', { cookie: sid, body: { path: 'showcase/prismet-site/content/site.md', text: '## title\nNew\n', sha: 'a'.repeat(40), message: 'Edit\u0007 from the page' } });
assert.equal(r.status, 200); assert.equal(r.body.commit.sha, 'c0ffee1');
const putCall = gh.find((x) => x.method === 'PUT'); assert.equal(putCall.body.branch, 'main'); assert.equal(putCall.body.message, 'Edit from the page', 'control characters stripped');
assert.equal(Buffer.from(putCall.body.content, 'base64').toString('utf8'), '## title\nNew\n'); checks++;
r = await call('PUT', '/api/edit/file', { cookie: sid, body: { path: 'showcase/prismet-site/content/site.md', text: 'x', sha: 'nope' } }); assert.equal(r.status, 400, 'a sha is required'); checks++;
r = await call('GET', '/api/edit/run?sha=c0ffee1', { cookie: sid }); assert.equal(r.body.run.conclusion, 'success'); checks++;
assert.ok(gh.every((x) => x.auth === 'Bearer github_pat_FAKE' && x.url.startsWith('https://api.github.com/')), 'the server uses the stored token against GitHub'); checks++;
r = await call('DELETE', '/api/edit/session', { cookie: sid }); assert.equal(r.status, 200);
r = await call('GET', '/api/edit/files', { cookie: sid }); assert.equal(r.status, 401, 'locked again'); checks++;
// per-address limit: five wrong guesses, then 429 for fifteen minutes
for (let i = 0; i < 5; i++) r = await call('POST', '/api/edit/session', { body: { pin: 'wrong1' }, ip: '2.2.2.2' });
r = await call('POST', '/api/edit/session', { body: { pin: '123456' }, ip: '2.2.2.2' }); assert.equal(r.status, 429, 'the sixth try from one address is refused even with the right PIN'); checks++;
clock += 16 * 60 * 1000;
r = await call('POST', '/api/edit/session', { body: { pin: '123456' }, ip: '2.2.2.2' }); assert.equal(r.status, 200, 'the window passes'); checks++;
// global lock: 25 wrong guesses from many addresses lock everyone for an hour
for (let i = 0; i < 25; i++) r = await call('POST', '/api/edit/session', { body: { pin: 'wrong2' }, ip: `9.9.9.${i}` });
r = await call('POST', '/api/edit/session', { body: { pin: '123456' }, ip: '3.3.3.3' }); assert.equal(r.status, 429, 'global lock'); checks++;
clock += 61 * 60 * 1000;
r = await call('POST', '/api/edit/session', { body: { pin: '123456' }, ip: '3.3.3.3' }); assert.equal(r.status, 200, 'the lock lifts'); checks++;
// sessions expire
const sid2 = r.headers['set-cookie'].split(';')[0];
clock += 9 * 60 * 60 * 1000;
r = await call('GET', '/api/edit/files', { cookie: sid2 }); assert.equal(r.status, 401, 'an eight-hour session expires'); checks++;
// not configured
const off = createEditApi({ env: { EDIT_PIN: '12' }, fetchImpl: fakeGitHub, clientAddress: () => '1.1.1.1', sendJSON: (q, res, status, payload) => { res.status = status; res.body = payload; } });
assert.equal(off.configured, false); assert.match(off.reason, /EDIT_GITHUB_TOKEN/); checks++;

console.log(`✓ edit-check: ${checks} checks over ${files.length} content files; the page, its client and the server's edit API agree`);
