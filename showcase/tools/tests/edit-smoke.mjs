// edit-smoke.mjs: the /edit page in a real browser against the REAL server, with GitHub answered by a fake.
//   node showcase/prismet-site/build.mjs && node showcase/tools/tests/edit-smoke.mjs
// Starts showcase/server/server.js (site/ = dist) with a fake token, PIN 123456 and EDIT_GITHUB_API pointing at a fake
// GitHub in this process; unlocks with the PIN, edits one field, publishes, and checks the PUT GitHub receives is
// exactly what writeSections produces and that the page reports the deploy. Exits 1 on any failure.
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { readFileSync, existsSync, rmSync, cpSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const { chromium } = await import('playwright').catch(() => import(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright/index.mjs'));
const CHROMIUM = process.env.CHROMIUM || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const HERE = dirname(fileURLToPath(import.meta.url));
const SITE = join(HERE, '..', '..', 'prismet-site'), DIST = join(SITE, 'dist'), SERVER = join(HERE, '..', '..', 'server');
if (!existsSync(join(DIST, 'edit.html'))) { console.error('build first: node showcase/prismet-site/build.mjs'); process.exit(1); }

// the fake GitHub
const siteText = readFileSync(join(SITE, 'content/site.md'), 'utf8');
const bayzylText = readFileSync(join(SITE, 'content/work/bayzyl.md'), 'utf8');
const { parseSections, writeSections } = await import(join(SITE, 'content.mjs'));
const b64 = (s) => Buffer.from(s, 'utf8').toString('base64');
let put = null, authSeen = new Set();
const gh = createServer((req, res) => {
  authSeen.add(req.headers.authorization);
  const json = (b, status = 200) => { res.writeHead(status, { 'content-type': 'application/json' }); res.end(JSON.stringify(b)); };
  let body = ''; req.on('data', (c) => body += c); req.on('end', () => {
    if (req.url.includes('/contents/showcase/prismet-site/content/work?')) return json([{ type: 'file', name: 'bayzyl.md', path: 'showcase/prismet-site/content/work/bayzyl.md' }]);
    if (req.method === 'PUT') { put = JSON.parse(body); return json({ content: { sha: 'b'.repeat(40) }, commit: { sha: 'c0ffee1' } }); }
    if (req.url.includes('/contents/showcase/prismet-site/content/site.md')) return json({ sha: 'a'.repeat(40), content: b64(siteText) });
    if (req.url.includes('/contents/showcase/prismet-site/content/work/bayzyl.md')) return json({ sha: 'c'.repeat(40), content: b64(bayzylText) });
    if (req.url.includes('/actions/runs')) return json({ workflow_runs: [{ status: 'completed', conclusion: 'success', html_url: 'https://github.com/sagedeutschle/Bayzyl/actions/runs/1' }] });
    json({ message: 'Not Found' }, 404);
  });
});
await new Promise((r) => gh.listen(0, '127.0.0.1', r));
const ghPort = gh.address().port;

// the real server, with the fresh build as site/
rmSync(join(SERVER, 'site'), { recursive: true, force: true }); cpSync(DIST, join(SERVER, 'site'), { recursive: true });
const port = 18200 + Math.floor(Math.random() * 90);
const server = spawn(process.execPath, ['server.js'], { cwd: SERVER, stdio: ['ignore', 'pipe', 'pipe'], env: { ...process.env, PORT: String(port), EDIT_GITHUB_TOKEN: 'github_pat_FAKE', EDIT_PIN: '123456', EDIT_GITHUB_API: `http://127.0.0.1:${ghPort}`, EDIT_COOKIE_SECURE: '0' } });
let serverLog = ''; server.stdout.on('data', (d) => serverLog += d); server.stderr.on('data', (d) => serverLog += d);
const base = `http://127.0.0.1:${port}`;
for (let i = 0; i < 100; i++) { try { if ((await fetch(`${base}/healthz`)).ok) break; } catch {} await new Promise((r) => setTimeout(r, 100)); }

let ok = true; const errors = [];
const check = (name, cond, detail = '') => { console.log(`${cond ? '✓' : '✗'} ${name}${cond ? '' : ` (${detail})`}`); if (!cond) ok = false; };
const browser = await chromium.launch({ executablePath: CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1200, height: 900 } });
page.on('pageerror', (e) => errors.push(`pageerror ${e.message}`));
page.on('console', (m) => { if (m.type() === 'error' && !/status of 401/.test(m.text())) errors.push(`console ${m.text()}`); });   // the wrong-PIN step answers 401 on purpose
const until = async (fn, ms = 10000) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (await page.evaluate(fn)) return true; await new Promise((r) => setTimeout(r, 100)); } return false; };
try {
  const st = await (await fetch(`${base}/api/edit/status`)).json();
  check('the server reports the editor configured and locked', st.configured === true && st.authed === false, JSON.stringify(st));
  check('the edit API needs a session', (await fetch(`${base}/api/edit/files`)).status === 401);
  check('the CSP no longer allows api.github.com', !(await fetch(`${base}/edit`)).headers.get('content-security-policy').includes('api.github.com'));

  await page.goto(`${base}/edit`, { waitUntil: 'networkidle' });
  check('the page loads under the live CSP without errors', errors.length === 0, errors.join('; '));
  await page.waitForSelector('#pin-form:visible', { timeout: 10000 });
  await page.fill('#pin', '000000'); await page.click('#pin-form button');
  check('a wrong PIN is refused with the tries left', await until(() => /Wrong PIN\. 4 tries left/.test(document.querySelector('#status').textContent)), await page.textContent('#status'));
  await page.fill('#pin', '123456'); await page.click('#pin-form button');
  await page.waitForSelector('#files button', { timeout: 10000 });
  check('the right PIN unlocks and lists the files', (await page.$$('#files button')).length === 2);
  check('the home file opens with one field per section', (await page.$$('#editor .field')).length === Object.keys(parseSections(siteText)).length);
  await page.click('#files button:nth-child(2)');
  await until(() => document.querySelector('#editor h2')?.textContent === 'Bayzyl');
  const sub = await page.$('#f-subtitle'); const before = await sub.inputValue();
  await sub.fill(before + ' Edited.');
  if (process.env.EDIT_SHOT) await page.screenshot({ path: process.env.EDIT_SHOT, fullPage: false });
  check('an edit marks the field and enables Publish', await page.$eval('.field[data-key="subtitle"]', (e) => e.classList.contains('dirty')) && !(await page.$eval('#save', (b) => b.disabled)));
  await page.click('#save');
  await until(() => /Saved/.test(document.querySelector('#status').textContent));
  const expected = writeSections(bayzylText, { subtitle: before + ' Edited.' });
  check('GitHub receives the file exactly as writeSections writes it, on main, with the sha', put && Buffer.from(put.content, 'base64').toString('utf8') === expected && put.sha === 'c'.repeat(40) && put.branch === 'main', put ? `branch ${put.branch}` : 'no PUT');
  check('the commit message names the file and the key', /Bayzyl \(subtitle\)/.test(put?.message || ''), put?.message);
  check('only the server ever showed GitHub the token', [...authSeen].every((a) => a === 'Bearer github_pat_FAKE'));
  await until(() => /Live on prismet.xyz/.test(document.querySelector('#deploy').textContent));
  check('the page reports the deploy result', /Live on prismet.xyz/.test(await page.textContent('#deploy')));
  const pageSource = await page.content();
  check('the token never reaches the browser', !pageSource.includes('github_pat') && !(await page.evaluate(() => JSON.stringify(Object.entries(sessionStorage)) + JSON.stringify(Object.entries(localStorage)))).includes('github_pat'));
  await page.click('#lock'); await page.waitForSelector('#pin-form:visible', { timeout: 10000 });
  check('Lock ends the session', (await (await fetch(`${base}/api/edit/status`)).json()).authed === false);
  check('no console or page errors', errors.length === 0, errors.join('; '));
} catch (e) { check('the flow completed', false, e.message); console.error(serverLog.slice(-600)); }
await browser.close(); server.kill(); gh.close();
console.log(ok ? '✓ edit-smoke: all checks passed' : '✗ edit-smoke: failures above'); process.exit(ok ? 0 : 1);
