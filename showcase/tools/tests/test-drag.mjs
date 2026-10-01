// Browser check for the prismet.xyz preview build. Run after: node showcase/prismet-site/build.mjs --preview
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1440, height: 1000 } }); const errs = [];
p.on('pageerror', e => errs.push(e.message));
await p.goto(new URL('../../prismet-site/dist/index.html', import.meta.url).href, { waitUntil: 'load' });
await p.evaluate(() => localStorage.clear()); await p.reload({ waitUntil: 'load' });
await p.click('#bz-toggle');
const h = p.locator('.bz-bar[data-target="steam-rewind"] .bz-drag'); await h.scrollIntoViewIfNeeded();
const hb = await h.boundingBox(); const tb = await p.locator(".card[data-slug=prismcode]").boundingBox();
await p.mouse.move(hb.x + 5, hb.y + 5); await p.mouse.down();
await p.mouse.move(tb.x + 20, tb.y + 150, { steps: 12 }); await p.mouse.up();
console.log(await p.evaluate(() => [...document.querySelectorAll('#grid .card')].slice(0, 5).map(c => c.dataset.slug).join(' → ')));
console.log('errors:', errs.length ? errs : 'none'); await b.close();
