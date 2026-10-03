// edit-smoke.mjs: the editor at /edit in a real browser against the REAL server, with GitHub answered by a fake.
//   node showcase/prismet-site/build.mjs && node showcase/tools/tests/edit-smoke.mjs
// Starts showcase/server/server.js (site/ = dist) with a fake token, PIN 123456 and EDIT_GITHUB_API pointing at a fake
// GitHub that reads this checkout (fake-github.mjs; writes stay in memory). Unlocks with the PIN, types on the page,
// changes a token, moves a section, undoes it, publishes, and checks that GitHub receives one commit holding exactly
// what writeSections produces. Exits 1 on any failure.
import { spawn } from 'node:child_process';
import { readFileSync, existsSync, rmSync, cpSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { fakeGitHub } from './fake-github.mjs';
const { chromium } = await import('playwright').catch(() => import(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright/index.mjs'));
const CHROMIUM = process.env.CHROMIUM || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..', '..'), SITE = join(HERE, '..', '..', 'prismet-site'), DIST = join(SITE, 'dist'), SERVER = join(HERE, '..', '..', 'server');
if (!existsSync(join(DIST, 'edit/assets.json'))) { console.error('build first: node showcase/prismet-site/build.mjs'); process.exit(1); }

const SITE_PATH = 'showcase/prismet-site/content/site.md';
const siteText = readFileSync(join(SITE, 'content/site.md'), 'utf8');
const { parseSections, writeSections } = await import(join(SITE, 'content.mjs'));
const gh = fakeGitHub({ root: ROOT }), ghServer = await gh.listen();

// the real server, with the fresh build as site/
rmSync(join(SERVER, 'site'), { recursive: true, force: true }); cpSync(DIST, join(SERVER, 'site'), { recursive: true });
const port = 18200 + Math.floor(Math.random() * 90);
const server = spawn(process.execPath, ['server.js'], { cwd: SERVER, stdio: ['ignore', 'pipe', 'pipe'], env: { ...process.env, PORT: String(port), EDIT_GITHUB_TOKEN: 'github_pat_FAKE', EDIT_PIN: '123456', EDIT_GITHUB_API: ghServer.url, EDIT_COOKIE_SECURE: '0', EDIT_DRAFT_REPO: 'sagedeutschle/prismet-drafts' } });
let serverLog = ''; server.stdout.on('data', (d) => serverLog += d); server.stderr.on('data', (d) => serverLog += d);
const base = `http://127.0.0.1:${port}`;
for (let i = 0; i < 100; i++) { try { if ((await fetch(`${base}/healthz`)).ok) break; } catch {} await new Promise((r) => setTimeout(r, 100)); }

let ok = true; const errors = [];
const check = (name, cond, detail = '') => { console.log(`${cond ? '✓' : '✗'} ${name}${cond ? '' : ` (${detail})`}`); if (!cond) ok = false; };
const browser = await chromium.launch({ executablePath: CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.on('pageerror', (e) => errors.push(`pageerror ${e.message}`));
page.on('console', (m) => { if (m.type() === 'error' && !/status of 401/.test(m.text())) errors.push(`console ${m.text()}`); });   // the wrong-PIN step answers 401 on purpose
const until = async (fn, arg, ms = 10000) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (await page.evaluate(fn, arg).catch(() => false)) return true; await new Promise((r) => setTimeout(r, 100)); } return false; };
const inFrame = (fn, arg) => page.evaluate(`(${fn})(document.getElementById('frame').contentDocument, ${JSON.stringify(arg ?? null)})`);
const sections = () => inFrame((d) => [...d.querySelectorAll('#main > [data-section]')].map((s) => s.dataset.section).join(','));
const row = (name) => page.locator('#tree .row', { has: page.locator('.name', { hasText: new RegExp(`^${name}$`) }) });
try {
  const st = await (await fetch(`${base}/api/edit/status`)).json();
  check('the server reports the editor configured and locked', st.configured === true && st.authed === false, JSON.stringify(st));
  check('the edit API needs a session', (await fetch(`${base}/api/edit/files`)).status === 401 && (await fetch(`${base}/api/edit/commit`, { method: 'POST', headers: { 'x-prismet-edit': '1' }, body: '{}' })).status === 401);
  check('the CSP allows connections to the site only', !(await fetch(`${base}/edit`)).headers.get('content-security-policy').includes('api.github.com'));

  await page.goto(`${base}/edit`, { waitUntil: 'networkidle' });
  check('the page loads under the live CSP without errors', errors.length === 0, errors.join('; '));
  await page.waitForSelector('#pin-form:visible', { timeout: 10000 });
  await page.fill('#pin', '000000'); await page.click('#pin-form button');
  check('a wrong PIN is refused with the tries left', await until(() => /Wrong PIN\. 4 tries left/.test(document.querySelector('#pin-status').textContent)), await page.textContent('#pin-status'));
  await page.fill('#pin', '123456'); await page.click('#pin-form button');
  await page.waitForSelector('#tree .row', { timeout: 15000 });
  check('the right PIN unlocks and lists the site', (await page.$$('#tree .row')).length > 20 && await page.isHidden('#connect'));
  check('the preview is the site, rendered in the page under the live CSP', await until(() => document.getElementById('frame').contentDocument?.querySelector('[data-edit="hero.lede"]') && document.getElementById('frame').contentDocument.fonts !== undefined));
  check('the inspector lists the selection\'s words', (await page.$$('#inspector .field')).length > 5);

  // type on the page
  const before = parseSections(siteText)['hero.lede'];
  await page.frameLocator('#frame').locator('[data-edit="hero.lede"]').click();
  await page.keyboard.press('ControlOrMeta+A'); await page.keyboard.type('A lede typed on the page.');
  check('typing on the page fills the inspector and makes a draft', await until(() => document.getElementById('f-hero.lede')?.value === 'A lede typed on the page.' && /Publish 1 file/.test(document.getElementById('save').textContent)), await page.textContent('#save'));
  await row('Register').click();
  check('the page keeps the new words', await inFrame((d) => d.querySelector('[data-edit="hero.lede"]').textContent) === 'A lede typed on the page.');

  // a design token
  await page.click('[data-tab="design"]');
  await page.fill('[data-token="--brass"] input[type="text"]', '#D07A2C'); await page.keyboard.press('Tab');
  check('a token changes the whole preview at once', await until(() => getComputedStyle(document.getElementById('frame').contentDocument.documentElement).getPropertyValue('--brass').trim() === '#D07A2C'));

  // one element, one width: the hero's first line is smaller on phones only
  await page.click('[data-tab="site"]');
  await page.frameLocator('#frame').locator('[data-edit="hero.title"]').click();
  await page.click('#inspector .element .seg button:has-text("Mobile")');
  await page.fill('#inspector [data-style="font-size"] input[type="text"]', '40'); await page.keyboard.press('Tab');
  const sizeOf = () => page.evaluate(() => { const f = document.getElementById('frame'); return f.contentWindow.getComputedStyle(f.contentDocument.querySelector('[data-edit="hero.title"]')).fontSize; });
  check('a size set at Mobile shows at mobile width', await until(() => { const f = document.getElementById('frame'); return f.contentWindow.innerWidth < 420 && f.contentWindow.getComputedStyle(f.contentDocument.querySelector('[data-edit="hero.title"]')).fontSize === '40px'; }), await sizeOf());
  await page.click('#device [data-w="1440"]');
  check('and leaves the desktop size alone', await until(() => { const f = document.getElementById('frame'); return f.contentWindow.innerWidth > 1000 && f.contentWindow.getComputedStyle(f.contentDocument.querySelector('[data-edit="hero.title"]')).fontSize !== '40px'; }), await sizeOf());
  await row('Register').click();

  // a new page from the section library
  await page.selectOption('#tree .adder.head', 'article');
  check('a new page opens in the preview', await until(() => document.getElementById('frame').contentDocument?.querySelector('main.x-page .x-title')?.textContent === 'A new page') && await row('New page').count() === 1);
  await page.frameLocator('#frame').locator('.x-title').click();
  await page.keyboard.press('ControlOrMeta+A'); await page.keyboard.type('About this workshop');
  await row('New page').click();
  await page.fill('#inspector [data-fid="page|title"]', 'About'); await page.keyboard.press('Tab');
  await page.selectOption('#inspector .prop:has-text("Status") select', 'published');
  await page.click('#inspector .prop.check:has-text("In the navigation") input');
  check('a published page in the navigation shows in the bar', await until(() => [...document.getElementById('frame').contentDocument.querySelectorAll('.nav a')].some((a) => a.textContent === 'About' && a.getAttribute('href') === 'new-page.html')));
  await row('Home').click();
  await page.selectOption('#tree .adder:not(.head)', 'quote');
  check('a library section joins the home page', await until(() => document.getElementById('frame').contentDocument?.querySelector('#main > .x-quote blockquote')));
  await row('Register').click();

  // move a section, then undo
  await page.click('[data-tab="site"]');
  const order0 = await sections();
  await row('Lenses').click(); await page.click('#inspector .btn:has-text("Move up")');
  check('a section moves in the preview', await until((o) => { const d = document.getElementById('frame').contentDocument; return [...d.querySelectorAll('#main > [data-section]')].map((s) => s.dataset.section).join(',') !== o; }, order0), await sections());
  await page.click('#undo');
  check('undo puts it back', await until((o) => { const d = document.getElementById('frame').contentDocument; return [...d.querySelectorAll('#main > [data-section]')].map((s) => s.dataset.section).join(',') === o; }, order0), await sections());
  if (process.env.EDIT_SHOT) await page.screenshot({ path: process.env.EDIT_SHOT, fullPage: false });

  // the draft survives a reload
  await page.reload({ waitUntil: 'networkidle' }); await page.waitForSelector('#tree .row', { timeout: 15000 });
  check('the draft survives a reload', await until(() => /Publish 5 files/.test(document.getElementById('save').textContent)), await page.textContent('#save'));

  // publish
  await page.click('#save');
  await until(() => /Published/.test(document.querySelector('#status').textContent));
  const commits = gh.calls.filter((c) => c.method === 'POST' && c.path.endsWith('/git/commits'));
  check('GitHub receives one commit', commits.length === 1, `${commits.length} commits`);
  check('site.md arrives exactly as writeSections writes it', gh.written.get(SITE_PATH) === writeSections(siteText, { 'hero.lede': 'A lede typed on the page.' }));
  check('theme.json arrives with the token', JSON.parse(gh.written.get('showcase/prismet-site/data/theme.json') || '{}').root?.['--brass'] === '#D07A2C');
  check('styles.json arrives with the size for phones only', JSON.stringify(JSON.parse(gh.written.get('showcase/prismet-site/data/styles.json') || '{}').rules) === '{"text:hero.title":{"mobile":{"font-size":"40px"}}}', gh.written.get('showcase/prismet-site/data/styles.json'));
  { const pg = JSON.parse(gh.written.get('showcase/prismet-site/data/pages.json') || '{}'), pj = JSON.parse(gh.written.get('showcase/prismet-site/data/projects.json') || '{}');
    check('pages.json arrives with the page and the home section, and the home order names it', pg.pages?.[0]?.title === 'About' && pg.pages[0].status === 'published' && pg.pages[0].sections[0].props.title === 'About this workshop' && pg.home?.[0]?.type === 'quote' && pj.layout.sections.includes(pg.home[0].id), JSON.stringify(pg).slice(0, 200)); }
  check('the commit message names the files', /site\.md \(1\); projects\.json; theme\.json/.test(commits[0]?.body.message || ''), commits[0]?.body.message);
  check('only the server ever showed GitHub the token', gh.calls.every((c) => c.auth === 'Bearer github_pat_FAKE'));
  await until(() => /Live on prismet.xyz/.test(document.querySelector('#deploy').textContent));
  check('the page reports the deploy result and an empty draft', /Live on prismet.xyz/.test(await page.textContent('#deploy')) && /Nothing to publish/.test(await page.textContent('#save')));
  check('the first words were "' + before.slice(0, 12) + '…": nothing else in site.md changed', parseSections(gh.written.get(SITE_PATH))['hero.title'] === parseSections(siteText)['hero.title']);
  const pageSource = await page.content();
  check('the token never reaches the browser', !pageSource.includes('github_pat') && !(await page.evaluate(() => JSON.stringify(Object.entries(sessionStorage)) + JSON.stringify(Object.entries(localStorage)))).includes('github_pat'));
  // history
  await page.click('[data-tab="history"]'); await page.waitForSelector('.revision', { timeout: 10000 });
  check('History lists what was published', (await page.$$('.revision')).length === 2);
  await page.locator('.revision').nth(1).click();
  check('a revision opens as a draft', await until(() => /as a draft|already shows/.test(document.querySelector('#status').textContent)), await page.textContent('#status'));
  await page.click('#lock'); await page.waitForSelector('#pin-form:visible', { timeout: 10000 });
  check('Lock ends the session', (await (await fetch(`${base}/api/edit/status`)).json()).authed === false);
  check('no console or page errors', errors.length === 0, errors.join('; '));
} catch (e) { check('the flow completed', false, e.message); console.error(serverLog.slice(-600)); }
await browser.close(); server.kill(); ghServer.close();
console.log(ok ? '✓ edit-smoke: all checks passed' : '✗ edit-smoke: failures above'); process.exit(ok ? 0 : 1);
