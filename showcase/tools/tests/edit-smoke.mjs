// edit-smoke.mjs: the /edit page in a real browser, with api.github.com answered by fixtures (nothing leaves the machine).
//   node showcase/prismet-site/build.mjs && node showcase/tools/tests/edit-smoke.mjs
// Serves dist with the live CSP, connects with a fake token, edits one field, publishes, and checks the PUT body is
// exactly what writeSections produces and that the page reports the deploy. Exits 1 on any failure.
import { createServer } from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
const { chromium } = await import('playwright').catch(() => import(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright/index.mjs'));
const CHROMIUM = process.env.CHROMIUM || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const HERE = dirname(fileURLToPath(import.meta.url));
const SITE = join(HERE, '..', '..', 'prismet-site'), DIST = join(SITE, 'dist');
const MIME = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.woff2': 'font/woff2', '.ico': 'image/x-icon', '.webp': 'image/webp', '.jpg': 'image/jpeg' };
const CSP = "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; img-src 'self' data: https:; connect-src 'self' https://api.github.com; form-action 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none';";
const server = createServer((req, res) => {
  let p = req.url.split('?')[0]; if (p === '/edit') p = '/edit.html';
  const file = join(DIST, p);
  if (!existsSync(file)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'content-type': MIME[extname(file)] || 'application/octet-stream', 'content-security-policy': CSP }); res.end(readFileSync(file));
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const port = server.address().port;

const siteText = readFileSync(join(SITE, 'content/site.md'), 'utf8');
const bayzylText = readFileSync(join(SITE, 'content/work/bayzyl.md'), 'utf8');
const { parseSections, writeSections } = await import(join(SITE, 'content.mjs'));
const b64 = (s) => Buffer.from(s, 'utf8').toString('base64');
let put = null; const errors = [];
const browser = await chromium.launch({ executablePath: CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1200, height: 900 } });
page.on('pageerror', (e) => errors.push(`pageerror ${e.message}`));
page.on('console', (m) => { if (m.type() === 'error') errors.push(`console ${m.text()}`); });
await page.route('https://api.github.com/**', async (route) => {
  const url = route.request().url(), method = route.request().method();
  const json = (body) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body), headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*' } });
  if (method === 'OPTIONS') return route.fulfill({ status: 204, headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*' } });
  if (url.endsWith('/user')) return json({ login: 'sage' });
  if (url.includes('/contents/showcase/prismet-site/content/work?')) return json([{ type: 'file', name: 'bayzyl.md', path: 'showcase/prismet-site/content/work/bayzyl.md' }]);
  if (method === 'PUT') { put = JSON.parse(route.request().postData()); return json({ content: { sha: 'sha2' }, commit: { sha: 'c0ffee' } }); }
  if (url.includes('/contents/showcase/prismet-site/content/site.md')) return json({ sha: 'sha-site', content: b64(siteText) });
  if (url.includes('/contents/showcase/prismet-site/content/work/bayzyl.md')) return json({ sha: 'sha-bz', content: b64(bayzylText) });
  if (url.includes('/actions/runs')) return json({ workflow_runs: [{ status: 'completed', conclusion: 'success', html_url: 'https://github.com/sagedeutschle/Bayzyl/actions/runs/1' }] });
  return route.fulfill({ status: 404, body: '{}' });
});

let ok = true; const check = (name, cond, detail = '') => { console.log(`${cond ? '✓' : '✗'} ${name}${cond ? '' : ` (${detail})`}`); if (!cond) ok = false; };
await page.goto(`http://127.0.0.1:${port}/edit`, { waitUntil: 'networkidle' });
check('the page loads under the live CSP without errors', errors.length === 0, errors.join('; '));
await page.fill('#token', 'github_pat_fake');
await page.click('#token-form button');
await page.waitForSelector('#files button', { timeout: 10000 });
check('connects and lists the files', (await page.$$('#files button')).length === 2);
check('the home file opens with one field per section', (await page.$$('#editor .field')).length === Object.keys(parseSections(siteText)).length);
await page.click('#files button:nth-child(2)');
await page.waitForFunction(() => document.querySelector('#editor h2')?.textContent === 'Bayzyl');
const sub = await page.$('#f-subtitle'); const before = await sub.inputValue();
await sub.fill(before + ' Edited.');
if (process.env.EDIT_SHOT) await page.screenshot({ path: process.env.EDIT_SHOT, fullPage: false });
check('an edit marks the field and enables Publish', await page.$eval('.field[data-key="subtitle"]', (e) => e.classList.contains('dirty')) && !(await page.$eval('#save', (b) => b.disabled)));
await page.click('#save');
await page.waitForFunction(() => /Saved/.test(document.querySelector('#status').textContent), null, { timeout: 10000 });
const expected = writeSections(bayzylText, { subtitle: before + ' Edited.' });
check('the PUT carries the file exactly as writeSections writes it', put && Buffer.from(put.content, 'base64').toString('utf8') === expected && put.sha === 'sha-bz' && put.branch === 'main', put ? `branch ${put.branch}, sha ${put.sha}` : 'no PUT');
check('the commit message names the file and the key', /Bayzyl \(subtitle\)/.test(put?.message || ''), put?.message);
await page.waitForFunction(() => /Live on prismet.xyz/.test(document.querySelector('#deploy').textContent), null, { timeout: 10000 }).catch(() => {});
check('the page reports the deploy result', /Live on prismet.xyz/.test(await page.textContent('#deploy')));
check('no console or page errors', errors.length === 0, errors.join('; '));
await browser.close(); server.close();
console.log(ok ? '✓ edit-smoke: all checks passed' : '✗ edit-smoke: failures above'); process.exit(ok ? 0 : 1);
