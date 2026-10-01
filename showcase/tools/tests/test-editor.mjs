// Browser check for the prismet.xyz preview build. Run after: node showcase/prismet-site/build.mjs --preview
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { writeFileSync } from 'node:fs';
const b = await chromium.launch();
const ctx = await b.newContext({ viewport: { width: 1280, height: 900 } });
const p = await ctx.newPage(); const errs = [];
p.on('pageerror', e => errs.push(e.message)); p.on('console', m => m.type() === 'error' && errs.push(m.text()));
await p.goto(new URL('../../prismet-site/dist/index.html', import.meta.url).href, { waitUntil: 'networkidle' });
await p.click('#bz-toggle');
const h1 = p.locator('#hero-title');
await h1.click();
console.log('raw on focus:', JSON.stringify(await h1.textContent()));
await p.keyboard.press('Control+A'); await p.keyboard.type('Built by *one* person.');
await p.locator('.lede').click();                       // moves focus → saves the title
await p.keyboard.press('End'); await p.keyboard.type(' Hello from the editor.');
await p.locator('#lenses-title').click();               // saves the lede
// edit a project card title (inside a link) and a beam chip label (inside a button)
const card = p.locator('.card[data-beam="minecraft"] h3').first();
await card.click(); await p.keyboard.press('Control+A'); await p.keyboard.type('Bayzyl toolkit');
await p.locator('.filters [data-edit="beam.minecraft"]').click(); await p.keyboard.press('End'); await p.keyboard.type(' stuff');
await p.locator('#work-title').click();
console.log('h1 html:', await h1.innerHTML());
console.log('url still index:', p.url().endsWith('index.html'));
console.log('localStorage:', await p.evaluate(() => localStorage.getItem('prismet.edits')));
await p.click('#bz-copy');
const out = await p.evaluate(() => document.getElementById('bz-copybox')?.value || '(clipboard ok)');
console.log('copy output:\n' + out);
writeFileSync('/tmp/edits.md', out);
await p.click('#bz-toggle');
await p.screenshot({ path: '/tmp/editor.png' });
console.log('errors:', errs.length ? errs : 'none');
await b.close();
