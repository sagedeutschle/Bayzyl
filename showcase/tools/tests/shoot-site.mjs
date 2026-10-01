// Browser check for the prismet.xyz preview build. Run after: node showcase/prismet-site/build.mjs --preview
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
const b = await chromium.launch();
const base = new URL('../../prismet-site/dist/', import.meta.url).href;
for (const [url, name, w, h, scheme, full] of [
  ['index.html', 'site-desktop', 1440, 900, 'dark', true],
  ['index.html', 'site-phone', 390, 844, 'dark', true],
  ['index.html', 'site-light', 1440, 900, 'light', false],
  ['work/prismet-app.html', 'site-project', 1440, 900, 'dark', true],
]) {
  const ctx = await b.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1, colorScheme: scheme });
  const p = await ctx.newPage(); const errs = [];
  p.on('console', m => m.type() === 'error' && errs.push(m.text())); p.on('pageerror', e => errs.push(e.message));
  await p.goto(base + url, { waitUntil: 'networkidle' }); await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(300);
  const sw = await p.evaluate(() => document.documentElement.scrollWidth);
  await p.screenshot({ path: `/tmp/${name}.png`, fullPage: full });
  console.log(name, 'scrollWidth', sw, 'viewport', w, errs.length ? 'ERRORS ' + errs.join(' | ') : 'no errors');
  await ctx.close();
}
await b.close();
