import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
const out = process.argv[2];
const browser = await chromium.launch();
for (const [w,h,name] of [[1440,1000,'qr-homescreen-filled-desktop'],[390,844,'qr-homescreen-filled-phone']]) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 2, colorScheme: 'dark' });
  const page = await ctx.newPage();
  await page.goto('file:///home/user/sagedeutschle/qr-scanner/homescreen.html');
  await page.getByText('Add the usual suspects').click();
  await page.waitForTimeout(800);
  const phone = page.locator('.phone, [class*=phone]').first();
  await phone.scrollIntoViewIfNeeded(); await page.waitForTimeout(400);
  await page.screenshot({ path: `${out}/${name}.png` });
  try { await phone.screenshot({ path: `${out}/${name}-mock.png` }); } catch(e) { console.log('no phone el', e.message.slice(0,80)); }
  console.log('ok', name); await ctx.close();
}
await browser.close();
