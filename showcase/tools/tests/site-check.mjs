// site-check.mjs: browser check of a production build. It serves dist itself (with the live CSP, ignoring query
// strings like the live server), then loads the home page, the Bayzyl, Prismet and Helm pages and /bayzyl at 1440×900 and
// 390×844 and checks:
//   - no console error, page error, failed request or HTTP error (a CSP violation is a console error), no broken image
//   - no horizontal overflow, before and after scrolling through the page
//   - home: the wing filter (chips and the plan), the #<wing> deep link, and clearing the filter
//   - Bayzyl: the stepper moves with the arrow keys and its buttons, and its live region stays quiet on load
//
//   node showcase/prismet-site/build.mjs && node showcase/tools/tests/site-check.mjs [dist-folder]
//
// Exits 1 on any failure. CHROMIUM overrides the browser binary, PLAYWRIGHT the module path.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { join, extname, normalize, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const { chromium } = await import('playwright').catch(() => import(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright/index.mjs'));
const CHROMIUM = process.env.CHROMIUM || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const DIST = process.argv[2] || join(dirname(fileURLToPath(import.meta.url)), '../../prismet-site/dist');
const CSP = "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; img-src 'self' data: https:; connect-src 'self' https://api.steampowered.com https://store.steampowered.com https://api.fiscaldata.treasury.gov https://api.usaspending.gov; form-action 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'";
const TYPES = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.webp': 'image/webp', '.jpg': 'image/jpeg', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const PAGES = ['index.html', 'work/bayzyl.html', 'work/prismet-app.html', 'work/the-helm.html', 'work/command-helm.html', 'work/command-helm-remote.html', 'work/castra.html', 'work/music-helm.html', 'work/taberna.html', 'work/helix-research-desk.html', 'work/westeros-uebs2.html', 'work/uncle-scam.html', 'bayzyl.html', 'arcade', 'arcade/rubiks-cube', 'arcade/2048', 'arcade/chess', 'arcade/solitaire'];
const SIZES = [[1440, 900, 1], [390, 844, 3], [375, 812, 3]];

const server = createServer(async (req, res) => {
  let p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (p === '/') p = '/index.html';
  // The live server's Arcade routes: /arcade is the library, /arcade/<game> the shared game shell, and the cloud
  // configuration answers "not enabled" so a game page loads without a network error.
  if (p === '/arcade' || p === '/arcade/') p = '/arcade.html';
  else if (/^\/arcade\/[a-z0-9-]+\/?$/.test(p) && !extname(p)) p = '/arcade/game.html';
  if (p === '/api/arcade/config') return res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' }).end('{"syncAvailable":false,"appStoreUrl":"https://apps.apple.com/us/app/kaleidescope/id6785993194"}');
  try {
    const body = await readFile(join(DIST, normalize(p).replace(/^(\.\.[/\\])+/, '')));
    res.writeHead(200, { 'content-type': TYPES[extname(p)] || 'application/octet-stream', 'content-security-policy': CSP }).end(body);
  } catch { res.writeHead(404, { 'content-type': 'text/plain' }).end('not found'); }
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const BASE = `http://127.0.0.1:${server.address().port}/`;

const results = [];
const check = (name, ok, detail = '') => { results.push({ name, ok }); console.log(`${ok ? '✓' : '✗'} ${name}${detail ? ` (${detail})` : ''}`); };
const browser = await chromium.launch({ executablePath: CHROMIUM });

async function open(ctx, url, { scroll = true } = {}) {
  const page = await ctx.newPage(), errors = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('requestfailed', (r) => errors.push(`failed ${r.url().replace(BASE, '')}`));
  page.on('response', (r) => { if (r.status() >= 400) errors.push(`${r.status()} ${r.url().replace(BASE, '')}`); });
  await page.goto(BASE + url, { waitUntil: 'networkidle' });
  const overflow = () => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  const before = await overflow();
  if (scroll) {
    await page.evaluate(async () => { for (let y = 0; y < document.body.scrollHeight; y += innerHeight * 0.7) { scrollTo(0, y); await new Promise((r) => setTimeout(r, 60)); } scrollTo(0, 0); });
    await page.waitForLoadState('networkidle');
  }
  const after = await overflow();
  const broken = await page.evaluate(() => [...document.images].filter((i) => i.complete && i.currentSrc && i.naturalWidth === 0).map((i) => i.getAttribute('src')));
  return { page, errors, overflow: Math.max(before, after), broken };
}

for (const [w, h, dpr] of SIZES) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: dpr, isMobile: w < 500, hasTouch: w < 500 });
  // Count writes to the stepper's live region from the moment the page is parsed.
  await ctx.addInitScript(() => document.addEventListener('DOMContentLoaded', () => {
    const out = document.querySelector('[data-stepper] output'); window.__liveWrites = 0;
    if (out) new MutationObserver((m) => { window.__liveWrites += m.length; }).observe(out, { childList: true, characterData: true, subtree: true });
  }));
  // A narrower Build Big pass also catches command examples that fit at 390px
  // but overflow on a phone with classic scrollbar space reserved.
  for (const url of (w === 375 ? ['bayzyl.html'] : PAGES)) {
    const { page, errors, overflow, broken } = await open(ctx, url);
    check(`${url} @${w}: no console or network errors`, errors.length === 0, errors.slice(0, 3).join('; '));
    check(`${url} @${w}: no horizontal overflow`, overflow <= 0, overflow > 0 ? `${overflow}px wider than the viewport` : '');
    check(`${url} @${w}: no broken image`, broken.length === 0, broken.slice(0, 3).join(', '));

    if (url === 'index.html') {
      const state = () => page.evaluate(() => ({
        shown: [...document.querySelectorAll('#grid .row')].filter((r) => !r.hidden).map((r) => r.dataset.beam),
        total: document.querySelectorAll('#grid .row').length,
        pressed: [...document.querySelectorAll('[data-filter][aria-pressed="true"]')].map((b) => b.dataset.filter),
        hash: location.hash,
        count: document.querySelector('[data-count-template]')?.textContent.trim(),
      }));
      const all = await state();
      const expect = (s, beam) => s.shown.length > 0 && s.shown.every((b) => b === beam) && s.shown.length === all.shown.filter((b) => b === beam).length && s.pressed.join() === beam && s.count.includes(String(s.shown.length));
      await page.click('[data-filter="minecraft"]');
      let s = await state();
      check(`index @${w}: the Minecraft chip filters the register`, expect(s, 'minecraft') && s.hash === '#minecraft', `${s.shown.length} of ${s.total} rows, hash ${s.hash}, "${s.count}"`);
      await page.click('[data-filter="all"]');
      s = await state();
      check(`index @${w}: All shows every row again`, s.shown.length === s.total && s.pressed.join() === 'all' && s.hash === '', `${s.shown.length} of ${s.total}`);
      const wing = '.wing-list a[data-beam="ai"]';
      await page.click(wing);
      s = await state();
      check(`index @${w}: the Developer Tools category navigation filters the register`, expect(s, 'ai'), `${s.shown.length} rows`);

      const deep = await open(ctx, 'index.html#web', { scroll: false });
      await deep.page.waitForTimeout(300);
      const d = await deep.page.evaluate(() => {
        const head = document.getElementById('work-title').getBoundingClientRect();
        return { shown: [...document.querySelectorAll('#grid .row')].filter((r) => !r.hidden).map((r) => r.dataset.beam), pressed: [...document.querySelectorAll('[data-filter][aria-pressed="true"]')].map((b) => b.dataset.filter), top: Math.round(head.top), vh: innerHeight };
      });
      check(`index#web @${w}: the deep link filters and lands on the register`, d.shown.length > 0 && d.shown.every((b) => b === 'web') && d.pressed.join() === 'web' && d.top >= 0 && d.top < d.vh, `${d.shown.length} rows, heading at ${d.top}px`);
      check(`index#web @${w}: no console or network errors`, deep.errors.length === 0, deep.errors.slice(0, 3).join('; '));
      await deep.page.close();
    }

    if (url === 'work/bayzyl.html') {
      const step = () => page.evaluate(() => ({ i: [...document.querySelectorAll('[data-stepper] .step')].findIndex((s) => s.hasAttribute('aria-current')), n: document.querySelectorAll('[data-stepper] .step').length, out: document.querySelector('[data-stepper] output').textContent.trim(), visible: [...document.querySelectorAll('[data-stepper] .step')].filter((s) => s.getBoundingClientRect().height > 0).length }));
      const quiet = await page.evaluate(() => window.__liveWrites);
      check(`bayzyl @${w}: the stepper's live region is not written on load`, quiet === 0, `${quiet} writes`);
      await page.focus('[data-stepper] [data-next]');
      await page.keyboard.press('ArrowRight');
      let s = await step();
      check(`bayzyl @${w}: ArrowRight wraps from the last step to the first`, s.i === 0 && s.out.endsWith(`1 / ${s.n}`) && s.visible === 1, `step ${s.i}, "${s.out}", ${s.visible} visible`);
      await page.keyboard.press('ArrowLeft'); await page.keyboard.press('ArrowLeft');
      s = await step();
      check(`bayzyl @${w}: ArrowLeft wraps back to the last step and steps once more`, s.i === s.n - 2 && s.out.endsWith(`${s.n - 1} / ${s.n}`), `step ${s.i}, "${s.out}"`);
      await page.click('[data-stepper] [data-next]');
      s = await step();
      check(`bayzyl @${w}: the Next button returns to the last step`, s.i === s.n - 1 && s.out.endsWith(`${s.n} / ${s.n}`), `step ${s.i}`);
      const writes = await page.evaluate(() => window.__liveWrites);
      check(`bayzyl @${w}: the live region speaks after each move`, writes >= 4, `${writes} writes`);
    }
    if (url === 'bayzyl.html') {
      const gallery = page.locator('[data-gallery="builds"] .image-link');
      const demos = page.locator('[data-gallery="demos"] .image-link');
      check(`Build Big @${w}: all 17 builds and four demonstrations remain`, await gallery.count() === 17 && await demos.count() === 4);
      check(`Build Big @${w}: images have no visible taglines`, await page.locator('#gallery figcaption, #try figcaption').count() === 0);
      const viewer = page.locator('#build-viewer');
      const count = () => page.locator('[data-viewer-counter]').innerText();
      await gallery.first().click();
      check(`Build Big @${w}: first image opens in the viewer`, await viewer.isVisible() && await count() === '1 / 17');
      await page.locator('[data-viewer-next]').click();
      check(`Build Big @${w}: next shows the second full image`, await count() === '2 / 17' && new URL(await page.locator('[data-viewer-image]').getAttribute('src'), BASE).href === new URL(await gallery.nth(1).getAttribute('href'), BASE).href);
      await page.keyboard.press('ArrowLeft');
      check(`Build Big @${w}: keyboard navigation returns to the first image`, await count() === '1 / 17');
      await page.keyboard.press('Escape');
      await page.waitForFunction(() => getComputedStyle(document.body).position !== 'fixed' && getComputedStyle(document.documentElement).overflow !== 'hidden');
      check(`Build Big @${w}: Escape closes and restores focus`, !(await viewer.isVisible()) && await gallery.first().evaluate((a) => document.activeElement === a));
      check(`Build Big @${w}: closing restores page scrolling`, await page.evaluate(() => getComputedStyle(document.body).overflow !== 'hidden' && getComputedStyle(document.documentElement).overflow !== 'hidden'));
      await demos.first().click();
      check(`Build Big @${w}: demonstration images use their own collection`, await viewer.isVisible() && await count() === '1 / 4');
      await page.locator('[data-viewer-close]').click();
      check(`Build Big @${w}: close button works`, !(await viewer.isVisible()));
      check(`Build Big @${w}: viewer has no browser errors`, errors.length === 0, errors.slice(0, 3).join('; '));
      const fallback = await browser.newContext({ viewport: { width: w, height: h }, javaScriptEnabled: false });
      const plain = await fallback.newPage();
      await plain.goto(BASE + url);
      const fallbackLink = plain.locator('[data-gallery="builds"] .image-link').first();
      const imageURL = new URL(await fallbackLink.getAttribute('href'), BASE).href;
      await fallbackLink.click();
      check(`Build Big @${w}: full images remain accessible without JavaScript`, plain.url() === imageURL);
      await fallback.close();
    }
    if (url === 'arcade') {
      // Tiles are lazy images; "no broken image" above covers the ones that loaded, this covers presence.
      const cards = () => page.evaluate(() => ({ all: document.querySelectorAll('#facet-library .facet-card').length, favorites: document.querySelectorAll('#category-favorites .facet-card').length, shown: document.querySelectorAll('#category-favorites:not([hidden]) .facet-card').length, art: [...document.querySelectorAll('#facet-library .facet-art img')].filter((i) => /\/arcade\/art\/[a-z0-9]+\.svg$/.test(i.getAttribute('src'))).length }));
      let c = await cards();
      check(`arcade @${w}: every facet has a card with its tile art`, c.all === 23 && c.favorites === 0 && c.art === 23, `${c.all} cards, ${c.art} tiles`);
      await page.click('[data-favorite="rubiks-cube"]');
      c = await cards();
      check(`arcade @${w}: a star puts the game under Your favorites`, c.shown === 1 && await page.evaluate(() => document.querySelector('[data-favorite="rubiks-cube"]').getAttribute('aria-pressed') === 'true'), `${c.shown} shown`);
      await page.click('#category-favorites [data-favorite="rubiks-cube"]');
      c = await cards();
      check(`arcade @${w}: the star removes it again`, c.shown === 0 && c.favorites === 0);
      await page.fill('#facet-search', 'cube');
      const filtered = await page.evaluate(() => ({ visible: [...document.querySelectorAll('#facet-library .facet-card')].filter((n) => !n.hidden).map((n) => n.dataset.card), count: document.getElementById('facet-count').textContent }));
      check(`arcade @${w}: search narrows the library`, filtered.visible.join() === 'rubiks-cube' && filtered.count === '1 of 23 in the collection', `${filtered.visible.join()} "${filtered.count}"`);
      await page.click('#facet-clear');
      check(`arcade @${w}: Clear restores the library`, (await cards()).all === 23 && await page.evaluate(() => document.getElementById('facet-search').value === '' && document.getElementById('facet-clear').hidden));
    }
    if (url === 'arcade/rubiks-cube') {
      const scene = () => page.evaluate(() => ({ cubies: document.querySelectorAll('.cube .cubie, .cube-pivot .cubie').length, stickers: document.querySelectorAll('.cube-face.sticker').length, pivots: document.querySelectorAll('.cube-pivot').length, net: document.querySelectorAll('.cube-net .engine-cell').length, moves: document.getElementById('moves').textContent, art: document.getElementById('game-art')?.complete }));
      let s = await scene();
      check(`cube @${w}: the scene has 26 cubies, 54 stickers and the flat view`, s.cubies === 26 && s.stickers === 54 && s.net === 54 && s.pivots === 0 && s.art === true, JSON.stringify(s));
      await page.focus('#board');
      await page.keyboard.press('r');
      await page.waitForTimeout(500);
      s = await scene();
      check(`cube @${w}: a letter key turns a layer and the pivot settles`, s.moves === '1' && s.cubies === 26 && s.pivots === 0, JSON.stringify(s));
      await page.click('#engine-controls .btn:last-child');
      await page.waitForTimeout(500);
      s = await scene();
      check(`cube @${w}: Undo turn plays back and settles`, s.moves === '2' && s.pivots === 0 && s.cubies === 26, JSON.stringify(s));
      const keypad = await page.evaluate(() => document.querySelectorAll('#engine-controls .btn').length);
      check(`cube @${w}: eighteen face moves and Undo on the keypad`, keypad === 19, `${keypad}`);
    }
    if (url === 'arcade/2048') {
      const board = () => page.evaluate(() => ({ tiles: document.querySelectorAll('#board .tile').length, filled: [...document.querySelectorAll('#board .tile')].filter((t) => t.dataset.value !== '0').length, moves: document.getElementById('moves').textContent }));
      const start = await board();
      await page.focus('#board');
      for (const key of ['ArrowLeft', 'ArrowDown', 'ArrowRight', 'ArrowUp']) await page.keyboard.press(key);
      await page.waitForTimeout(400);
      const after = await board();
      check(`2048 @${w}: arrow keys move the board and spawn tiles`, start.tiles === 16 && after.tiles === 16 && Number(after.moves) >= 1 && after.filled > start.filled, JSON.stringify({ start, after }));
    }
    if (url === 'arcade/chess') {
      const squares = await page.evaluate(() => ({ cells: document.querySelectorAll('#board .engine-cell').length, keyed: document.querySelectorAll('#board [data-key]').length }));
      check(`chess @${w}: 64 squares, 32 keyed pieces`, squares.cells === 64 && squares.keyed === 32, JSON.stringify(squares));
    }
    await page.close();
  }
  await ctx.close();
}
await browser.close();
server.close();
const failed = results.filter((r) => !r.ok).length;
console.log(`${failed ? '✗' : '✓'} site-check: ${results.length - failed} of ${results.length} checks passed`);
process.exit(failed ? 1 : 0);
