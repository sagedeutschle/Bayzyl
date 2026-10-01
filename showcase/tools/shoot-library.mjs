// shoot-library.mjs — captures the live prismet.xyz tools and the redesigned site at retina
// resolution for the Fiverr screenshot library.  node showcase/tools/shoot-library.mjs <out-dir>
// (Chromium must trust the session's proxy CA first; see showcase/README.md.)
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
const out = process.argv[2];
const site = new URL('../prismet-site/dist/', import.meta.url).href;
const b = await chromium.launch();
async function shot(url, name, { w = 1440, h = 900, dpr = 2, scheme = 'dark', full = false, before } = {}) {
  const ctx = await b.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: dpr, colorScheme: scheme });
  const p = await ctx.newPage();
  await p.goto(url, { waitUntil: 'networkidle', timeout: 45000 });
  await p.evaluate(() => { document.querySelectorAll('img[loading=lazy]').forEach((i) => { i.loading = 'eager'; }); return document.fonts.ready; });
  if (before) await before(p);
  await p.waitForTimeout(900);
  await p.screenshot({ path: `${out}/${name}.png`, fullPage: full });
  console.log('shot', name); await ctx.close();
}
const demo = async (p) => { await p.click('#reset'); await p.waitForTimeout(800); };
const lens = (v) => async (p) => { await demo(p); await p.selectOption('#lens', { index: v }); await p.waitForTimeout(600); };
// live tools (same Fly app as prismet.xyz; the .fly.dev host avoids custom-domain proxy rules) (Steam Rewind uses its built-in demo fixture, no real account)
await shot('https://prismet-site-restless-horizon-217.fly.dev/steam', 'steam-rewind-desktop', { before: demo });
await shot('https://prismet-site-restless-horizon-217.fly.dev/steam', 'steam-rewind-desktop-lens2', { before: lens(1) });
await shot('https://prismet-site-restless-horizon-217.fly.dev/steam', 'steam-rewind-phone', { w: 390, h: 844, dpr: 3, before: demo });
await shot('https://prismet-site-restless-horizon-217.fly.dev/debt', 'debt-clock-desktop');
await shot('https://prismet-site-restless-horizon-217.fly.dev/debt', 'debt-clock-phone', { w: 390, h: 844, dpr: 3 });
// the redesigned site (local production build)
await shot(site + 'index.html', 'site-home-dark');
await shot(site + 'index.html', 'site-home-light', { scheme: 'light' });
await shot(site + 'index.html', 'site-home-phone', { w: 390, h: 844, dpr: 3 });
await shot(site + 'index.html', 'site-all-work', { before: async (p) => { await p.locator('#work').scrollIntoViewIfNeeded(); await p.evaluate(() => window.scrollBy(0, 60)); } });
await shot(site + 'index.html', 'site-selected-work', { before: async (p) => { await p.locator('#selected').scrollIntoViewIfNeeded(); } });
await shot(site + 'work/bayzyl.html', 'site-project-bayzyl');
await shot(site + 'work/the-helm.html', 'site-project-helm');
await shot(site + 'work/the-long-now.html', 'site-project-long-now', { scheme: 'light' });
await b.close();
