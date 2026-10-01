// Browser check for the prismet.xyz preview build. Run after: node showcase/prismet-site/build.mjs --preview
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
await p.goto(new URL('../../prismet-site/dist/index.html', import.meta.url).href, { waitUntil: 'networkidle' });
await p.evaluate(async () => { document.querySelectorAll('img[loading=lazy]').forEach(i => i.loading = 'eager'); await new Promise(r => setTimeout(r, 800)); });
await p.waitForLoadState('networkidle'); await p.evaluate(() => document.fonts.ready);
const sel = await p.locator('#selected').boundingBox(); const work = await p.locator('#work').boundingBox();
await p.screenshot({ path: '/tmp/grid.png', fullPage: true, clip: { x: 0, y: work.y, width: 1440, height: work.height } });
await p.screenshot({ path: '/tmp/selected.png', fullPage: true, clip: { x: 0, y: sel.y, width: 1440, height: Math.min(sel.height, 1500) } });
await b.close();
