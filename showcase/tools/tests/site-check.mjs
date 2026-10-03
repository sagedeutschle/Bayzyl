// site-check.mjs: browser check of a production build. It serves dist itself (with the live CSP, ignoring query
// strings like the live server), then loads the home page and the Bayzyl, Prismet and Helm pages at 1440×900 and
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
const CSP = "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; img-src 'self' data: https:; connect-src 'self' https://api.steampowered.com https://store.steampowered.com https://api.fiscaldata.treasury.gov; form-action 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'";
const TYPES = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.webp': 'image/webp', '.jpg': 'image/jpeg', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const PAGES = ['index.html', 'work/bayzyl.html', 'work/prismet-app.html', 'work/the-helm.html'];
const SIZES = [[1440, 900, 1], [390, 844, 3]];

const server = createServer(async (req, res) => {
  let p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (p === '/') p = '/index.html';
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
  for (const url of PAGES) {
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
      const wing = w < 900 ? '.wing-list a[data-beam="ai"]' : '#plan a.wing[data-beam="ai"]';
      await page.click(wing);
      s = await state();
      check(`index @${w}: the plan's AI wing filters the register`, expect(s, 'ai'), `${s.shown.length} rows`);

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
    await page.close();
  }
  await ctx.close();
}
await browser.close();
server.close();
const failed = results.filter((r) => !r.ok).length;
console.log(`${failed ? '✗' : '✓'} site-check: ${results.length - failed} of ${results.length} checks passed`);
process.exit(failed ? 1 : 0);
