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
const parseOf = (text) => content.parseSections(text);
const SITE_PATH = 'showcase/prismet-site/content/site.md';
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

// ── the editor's preview renders what the build renders ──
// lib/render.js with the manifest the build wrote (edit/assets.json) must give the pages in dist/, byte for byte.
const { existsSync } = await import('node:fs');
const { renderSite } = await import(join(SITE, 'pages/lib/render.js'));
const { themeCss, stripTheme, safeValue } = await import(join(SITE, 'pages/lib/theme.js'));
const { createStore, PROJECTS_FILE, THEME_FILE, workFile } = await import(join(SITE, 'pages/edit/store.js'));
const { fakeGitHub: checkoutGitHub } = await import(join(HERE, 'fake-github.mjs'));
const REPO_ROOT = join(HERE, '..', '..', '..');
const disk = (rel) => ({ path: `showcase/prismet-site/${rel}`, text: readFileSync(join(SITE, rel), 'utf8'), sha: 'a'.repeat(40) });
const published = () => [...files.map(disk), disk('data/projects.json'), disk('data/theme.json'), disk('data/styles.json')];
const memory = () => { const m = new Map(); return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, v), removeItem: (k) => m.delete(k) }; };
const tick = (ms = 450) => new Promise((r) => setTimeout(r, ms));

if (existsSync(join(SITE, 'dist/edit/assets.json'))) {
  const manifest = JSON.parse(readFileSync(join(SITE, 'dist/edit/assets.json'), 'utf8'));
  const assets = { size: (p) => { const f = manifest.files[p]; return f ? { w: f[0], h: f[1] } : { w: 0, h: 0 }; }, has: (p) => p in manifest.files,
    url: (p) => { if (!p) return null; const f = manifest.files[p]; if (!f) throw new Error(`missing image: ${p}`); return `${p}?v=${f[2]}`; } };
  const st = createStore({ storage: memory() }); st.load(published());
  const cssUrl = readFileSync(join(SITE, 'dist/index.html'), 'utf8').match(/href="(site\.css\?v=[0-9a-f]+)"/)[1];
  const out = renderSite({ data: st.docs.projects, site: st.docs.site, work: st.docs.work, assets, urls: { css: cssUrl, js: manifest.urls.js, og: manifest.urls.og }, styles: st.docs.styles });
  for (const [path, html] of out.pages) { assert.equal(html, readFileSync(join(SITE, 'dist', path), 'utf8'), `${path}: the editor's render differs from the build's`); checks++; }
  assert.equal(out.missing.size, 0); checks++;
  const ed = renderSite({ data: st.docs.projects, site: st.docs.site, work: st.docs.work, assets, urls: { css: 'site.css', js: manifest.urls.js, og: manifest.urls.og }, edit: true });
  assert.match(ed.pages.get('index.html'), /data-edit="hero\.title"/, 'edit mode labels the wording'); assert.ok(!ed.pages.get('index.html').includes('editor.js'), 'edit mode loads no preview editor'); checks += 2;
} else console.log('  (skipped the render check: build first to compare with dist/)');

// ── theme tokens ──
assert.equal(themeCss({ root: {}, day: {} }), '', 'an empty theme adds nothing to site.css');
const css = themeCss({ root: { '--brass': '#D08A3C', '--radius': '6px', 'color': 'red', '--x': 'red; } body { display: none' }, day: { '--brass': '#7A4A12' } });
assert.match(css, /:root \{ --brass: #D08A3C; --radius: 6px; \}/); assert.match(css, /:root\[data-theme="light"\] \{ --brass: #7A4A12; \}/);
assert.ok(!css.includes('display: none') && !css.includes('color: red'), 'only custom properties with one declaration\'s worth of value');
assert.equal(stripTheme('a{}' + css), 'a{}'); assert.equal(safeValue('url(x)'), false); assert.equal(safeValue('clamp(1rem, 2vw, 3rem)'), true); checks += 7;

// ── one element's style, per width ──
{
  const { stylesCss, styledKeys, tierFor, selectorOf } = await import(join(SITE, 'pages/lib/styles.js'));
  assert.equal(stylesCss({ rules: {} }), '', 'no rules add nothing to site.css');
  const st = { rules: { 'text:hero.title': { base: { 'font-size': '64px' }, mobile: { 'font-size': '40px', position: 'fixed', color: 'red; } body { display: none' } },
    'section:lenses': { mobile: { display: 'none' } }, 'section:hero': { base: { 'padding-top': '96px' } }, 'text:x"] body, [y': { base: { color: 'red' } }, 'script:x': { base: { color: 'red' } } } };
  const out = stylesCss(st);
  assert.match(out, /\[data-s="hero\.title"\] \{ font-size: 64px !important; \}/); assert.match(out, /\.entrance \{ padding-top: 96px !important; \}/);
  assert.match(out, /@media \(max-width: 600px\) \{ \[data-s="hero\.title"\] \{ font-size: 40px !important; \} #main > \[data-section="lenses"\] \{ display: none !important; \} \}/);
  assert.ok(!out.includes('position') && !out.includes('body') && !out.includes('script'), 'only listed properties, one declaration each, known targets'); checks += 5;
  assert.ok(out.indexOf('64px') < out.indexOf('40px'), 'narrower tiers come later, so they win');
  assert.match(stylesCss(st, { attr: 'data-edit' }), /\[data-edit="hero\.title"\]/); assert.deepEqual([...styledKeys(st)], ['hero.title']);
  assert.deepEqual([1440, 901, 900, 601, 600, 390].map(tierFor), ['base', 'base', 'tablet', 'tablet', 'mobile', 'mobile']); assert.equal(selectorOf('section:../x'), null);
  assert.equal(stripTheme('a{}' + themeCss({ root: { '--brass': '#fff' } }) + out), 'a{}'); assert.equal(stripTheme('a{}' + out), 'a{}'); checks += 7;
  const s2 = createStore({ storage: memory() }); s2.load([...published(), disk('data/styles.json')]);
  const plain = renderSite({ data: s2.docs.projects, site: s2.docs.site, work: s2.docs.work, assets: { size: () => ({ w: 1, h: 1 }), has: () => true, url: (p) => p }, urls: { css: 'c', js: 'j', og: 'o' }, styles: st });
  assert.match(plain.pages.get('index.html'), /class="salute" data-s="hero\.title"/, 'the site marks the wording a style targets'); assert.equal((plain.pages.get('index.html').match(/data-s=/g) || []).length, 1, 'and only that'); checks += 2;
  s2.change('Size', (d) => { d.styles.rules['text:hero.title'] = { mobile: { 'font-size': '40px' } }; }, { kind: 'styles' });
  assert.deepEqual(s2.changedFiles().map((f) => f.label), ['styles.json']); checks++;
}

// ── the draft ──
{
  const storage = memory(), st = createStore({ storage }); st.load(published());
  assert.equal(st.dirty, false); assert.deepEqual(st.changedFiles(), []); checks += 2;
  st.setText('hero.lede', 'A new lede.'); st.setText('work.bayzyl.subtitle', 'A new subtitle.'); st.setText('work.bayzyl.facts.0.value', 'Changed');
  let out = st.changedFiles();
  assert.deepEqual(out.map((f) => f.path.split('/').pop()), ['site.md', 'bayzyl.md']);
  assert.equal(out[0].text, content.writeSections(disk('content/site.md').text, { 'hero.lede': 'A new lede.' }), 'site.md is written exactly as writeSections writes it');
  assert.match(out[1].text, /## subtitle\nA new subtitle\./); assert.match(out[1].text, /^- [^:\n]+: Changed$/m); assert.equal(st.text('work.bayzyl.facts.0.value'), 'Changed'); checks += 5;
  st.undo(); st.undo(); st.undo(); assert.equal(st.dirty, false, 'three undos, three edits'); st.redo(); assert.equal(st.text('hero.lede'), 'A new lede.'); checks += 2;
  st.change('Move work first', (d) => { d.projects.layout.sections = ['work', 'selected', 'plate', 'lenses', 'about']; });
  st.change('Accent', (d) => { d.theme.root['--brass'] = '#D08A3C'; }, { kind: 'theme' });
  st.change('New record', (d) => { d.projects.projects.push({ slug: 'new-record', beam: 'web', stack: [], hidden: true }); d.work['new-record'] = { title: 'New', subtitle: 's', status: 'x', year: '2026', role: 'r', summary: 's', facts: '- a: b', highlights: '- c' }; });
  out = st.changedFiles();
  assert.deepEqual(out.map((f) => f.path.split('/').pop()).sort(), ['new-record.md', 'projects.json', 'site.md', 'theme.json']);
  assert.equal(out.find((f) => f.path === workFile('new-record')).sha, null, 'a new record is a new file');
  assert.deepEqual(parseOf(out.find((f) => f.path === workFile('new-record')).text).title, 'New'); assert.equal(JSON.parse(out.find((f) => f.path === THEME_FILE).text).root['--brass'], '#D08A3C'); checks += 4;
  await tick();
  const again = createStore({ storage }); again.load(published());
  assert.equal(again.text('hero.lede'), 'A new lede.', 'the draft survives a reload'); assert.equal(again.docs.projects.layout.sections[0], 'work'); checks += 2;
  const moved = createStore({ storage }); moved.load(published().map((f) => (f.path.endsWith('site.md') ? { ...f, sha: 'b'.repeat(40) } : f)));
  assert.equal(moved.dirty, false, 'a draft made from an older version is set aside, not applied'); assert.ok(storage.getItem('prismet.edit.draft.v1.stale')); checks += 2;

  // ── publish: one commit, through the server, against a fake GitHub that reads this checkout ──
  const gh2 = checkoutGitHub({ root: REPO_ROOT });
  const api2 = createEditApi({ env: { EDIT_GITHUB_TOKEN: 'github_pat_FAKE', EDIT_PIN: '123456', EDIT_COOKIE_SECURE: '0' }, fetchImpl: gh2.fetch, clientAddress: () => '4.4.4.4',
    sendJSON: (q, res, status, payload, headers = {}) => { res.status = status; res.body = payload; res.headers = headers; } });
  const call2 = async (method, path, opts) => { const res = {}; await api2.handle(req(method, path, opts), res, new URL(path, 'http://x'), method); return res; };
  const sid3 = (await call2('POST', '/api/edit/session', { body: { pin: '123456' } })).headers['set-cookie'].split(';')[0];
  const get = async (path) => (await call2('GET', `/api/edit/file?path=${path}`, { cookie: sid3 })).body;
  assert.equal((await get(PROJECTS_FILE)).text, disk('data/projects.json').text, 'the data files can be read'); checks++;
  const real = createStore({ storage: memory() });
  real.load(await Promise.all([...files.map((f) => `showcase/prismet-site/${f}`), PROJECTS_FILE, THEME_FILE, 'showcase/prismet-site/data/styles.json'].map(get)));
  real.setText('hero.lede', 'Published lede.'); real.change('Hide plate', (d) => { d.projects.layout.hiddenSections = ['plate']; });
  real.change('New record', (d) => { d.projects.projects.push({ slug: 'new-record', beam: 'web', stack: [], hidden: true }); d.work['new-record'] = { title: 'New', subtitle: 's', status: 'x', year: '2026', role: 'r', summary: 's', facts: '- a: b', highlights: '- c' }; });
  const batch = real.changedFiles().map(({ path, text, sha }) => ({ path, text, sha }));
  r = await call2('POST', '/api/edit/commit', { body: { files: batch } }); assert.equal(r.status, 401, 'publishing needs a session'); checks++;
  r = await call2('POST', '/api/edit/commit', { cookie: sid3, body: { files: batch, message: 'Publish' } });
  assert.equal(r.status, 200, JSON.stringify(r.body)); assert.match(r.body.commit.sha, /^[0-9a-f]{40}$/); assert.equal(r.body.files.length, 3);
  assert.equal(gh2.calls.filter((c) => c.method === 'POST' && c.path.endsWith('/git/commits')).length, 1, 'one commit for every file');
  assert.equal(gh2.written.get(SITE_PATH), batch.find((f) => f.path === SITE_PATH).text); assert.ok(gh2.written.has(workFile('new-record'))); checks += 6;
  assert.equal((await get(SITE_PATH)).sha, r.body.files.find((f) => f.path === SITE_PATH).sha, 'the page learns each file\'s new version from the answer'); checks++;
  r = await call2('POST', '/api/edit/commit', { cookie: sid3, body: { files: batch } }); assert.equal(r.status, 409, 'a file that changed since it was loaded stops the publish'); assert.ok(r.body.stale.includes('site.md')); checks += 2;
  const refuse = async (file, why) => { const x = await call2('POST', '/api/edit/commit', { cookie: sid3, body: { files: [file] } }); assert.equal(x.status, 400, why); checks++; };
  await refuse({ path: 'showcase/server/server.js', text: 'x', sha: 'a'.repeat(40) }, 'only content and data files');
  await refuse({ path: 'showcase/prismet-site/data/other.json', text: '{}', sha: 'a'.repeat(40) }, 'only the three data files');
  await refuse({ path: PROJECTS_FILE, text: '{not json', sha: 'a'.repeat(40) }, 'a data file must be JSON');
  await refuse({ path: 'showcase/prismet-site/content/site2.md', text: 'x', sha: null }, 'a new file can only be a record');
  await refuse({ path: 'showcase/prismet-site/content/work/../x.md', text: 'x', sha: null }, 'no traversal');
  assert.ok(gh2.calls.every((c) => c.auth === 'Bearer github_pat_FAKE'), 'the server uses the stored token'); checks++;
  // ── history: published revisions, and a revision opened as a draft ──
  r = await call2('GET', '/api/edit/history', { cookie: sid3 });
  assert.equal(r.status, 200); assert.equal(r.body.revisions.length, 2); assert.equal(r.body.revisions[0].message, 'Edit from prismet.xyz/edit: site.md (1)', 'first line only'); assert.match(r.body.revisions[0].sha, /^[0-9a-f]{40}$/); checks += 4;
  r = await call2('GET', `/api/edit/file?path=${SITE_PATH}&ref=${'d'.repeat(40)}`, { cookie: sid3 }); assert.equal(r.status, 200);
  assert.ok(gh2.calls.at(-1).path.endsWith('site.md') && gh2.calls.some((c) => c.query === `?ref=${'d'.repeat(40)}`), 'a revision is read by its commit'); checks += 2;
  r = await call2('GET', `/api/edit/file?path=${SITE_PATH}&ref=main;rm`, { cookie: sid3 }); assert.ok(gh2.calls.at(-1).query === '?ref=main', 'anything but a commit id falls back to the branch'); checks++;
  const past = createStore({ storage: memory() }); past.load(published()); past.setText('hero.lede', 'Changed today.');
  assert.equal(past.restore('yesterday', published().filter((f) => !f.path.endsWith('styles.json'))), true); assert.equal(past.dirty, false, 'a restored revision replaces the draft'); past.undo(); assert.equal(past.text('hero.lede'), 'Changed today.', 'and undo comes back'); checks += 3;

  // ── the draft in the private drafts repository ──
  r = await call2('GET', '/api/edit/draft', { cookie: sid3 }); assert.equal(r.status, 404, 'no drafts repository, no server drafts'); checks++;
  const gh3 = checkoutGitHub({ root: REPO_ROOT });
  const api3 = createEditApi({ env: { EDIT_GITHUB_TOKEN: 'github_pat_FAKE', EDIT_PIN: '123456', EDIT_COOKIE_SECURE: '0', EDIT_DRAFT_REPO: 'sagedeutschle/prismet-drafts', EDIT_DRAFT_TOKEN: 'github_pat_DRAFTS' }, fetchImpl: gh3.fetch, clientAddress: () => '5.5.5.5',
    sendJSON: (q, res, status, payload, headers = {}) => { res.status = status; res.body = payload; res.headers = headers; } });
  const call3 = async (method, path, opts) => { const res = {}; await api3.handle(req(method, path, opts), res, new URL(path, 'http://x'), method); return res; };
  r = await call3('GET', '/api/edit/status'); assert.equal(r.body.drafts, true); checks++;
  r = await call3('GET', '/api/edit/draft'); assert.equal(r.status, 401, 'the draft needs a session'); checks++;
  const sid4 = (await call3('POST', '/api/edit/session', { body: { pin: '123456' } })).headers['set-cookie'].split(';')[0];
  r = await call3('GET', '/api/edit/draft', { cookie: sid4 }); assert.equal(r.status, 200); assert.equal(r.body.text, null, 'no draft yet'); checks += 2;
  const phone = createStore({ storage: memory() }); phone.load(published()); phone.setText('hero.lede', 'Written on the phone.'); await tick();
  r = await call3('PUT', '/api/edit/draft', { cookie: sid4, body: { text: JSON.stringify(phone.snapshot()), sha: null } }); assert.equal(r.status, 200, JSON.stringify(r.body)); checks++;
  const draftCalls = gh3.calls.filter((c) => c.path.includes('/contents/draft.json'));
  assert.ok(draftCalls.length === 2 && draftCalls.every((c) => c.path.startsWith('/repos/sagedeutschle/prismet-drafts/') && c.auth === 'Bearer github_pat_DRAFTS'), 'the draft goes to the drafts repository with its own token, nowhere else'); checks++;
  const saved = (await call3('GET', '/api/edit/draft', { cookie: sid4 })).body;
  const laptop = createStore({ storage: memory() }); laptop.load(published());
  assert.equal(laptop.adopt(JSON.parse(saved.text)), true); assert.equal(laptop.text('hero.lede'), 'Written on the phone.', 'another device opens to the draft'); laptop.undo(); assert.equal(laptop.dirty, false, 'and can undo it'); checks += 3;
  const newer = createStore({ storage: memory() }); newer.load(published().map((f) => (f.path.endsWith('site.md') ? { ...f, sha: 'b'.repeat(40) } : f)));
  assert.equal(newer.adopt(JSON.parse(saved.text)), false, 'a draft made from older files is not applied'); checks++;
  r = await call3('PUT', '/api/edit/draft', { cookie: sid4, body: { text: '{"a":1}', sha: 'f'.repeat(40) } }); assert.equal(r.status, 409, 'a save over a stale version is refused'); checks++;
  r = await call3('PUT', '/api/edit/draft', { cookie: sid4, body: { text: 'not json', sha: saved.sha } }); assert.equal(r.status, 400); checks++;
}

console.log(`✓ edit-check: ${checks} checks over ${files.length} content files; the page, its client, the draft, the renderer and the server's edit API agree`);
