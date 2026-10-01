import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
const [,, outDir] = process.argv;
const base = 'file:///home/user/sagedeutschle/qr-scanner/';
const pages = [
  ['homescreen.html', 'qr-homescreen-desktop', 1440, 900, 'dark'],
  ['homescreen.html', 'qr-homescreen-phone', 390, 844, 'dark'],
  ['organize.html', 'qr-organize-desktop', 1440, 900, 'dark'],
  ['organize.html', 'qr-organize-phone', 390, 844, 'dark'],
  ['playlist.html', 'qr-playlist-desktop', 1440, 900, 'dark'],
  ['index.html', 'qr-index-desktop', 1440, 900, 'dark'],
];
const browser = await chromium.launch();
for (const [p, name, w, h, scheme] of pages) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 2, colorScheme: scheme });
  const page = await ctx.newPage();
  await page.goto(base + p); await page.waitForTimeout(1200);
  await page.screenshot({ path: `${outDir}/${name}.png` });
  console.log('shot', name); await ctx.close();
}
await browser.close();
