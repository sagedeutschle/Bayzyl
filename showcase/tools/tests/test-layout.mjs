// Browser check for the prismet.xyz preview build. Run after: node showcase/prismet-site/build.mjs --preview
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { writeFileSync } from 'node:fs';
const b = await chromium.launch();
const ctx = await b.newContext({ viewport: { width: 1440, height: 1000 }, colorScheme: 'dark' });
await ctx.grantPermissions(['clipboard-read', 'clipboard-write']);
const p = await ctx.newPage(); const errs = [];
p.on('pageerror', e => errs.push(e.message));
await p.goto(new URL('../../prismet-site/dist/index.html', import.meta.url).href, { waitUntil: 'load' });
await p.click('#bz-toggle');
const bar = (t, a) => p.locator(`.bz-bar[data-target="${t}"] [data-act="${a}"]`);
await bar('lenses', 'sec-down').click();          // lenses below selected
await bar('about', 'sec-hide').click();           // hide about
await bar('bayzyl', 'card-wide').click();         // bayzyl wide
await bar('cicero', 'card-feat').click();         // feature cicero
await bar('airhorn', 'card-hide').click();        // hide airhorn
await bar('prismet-app', 'card-next').click();    // move prismet one later
await bar('the-long-now', 'feat-up').click();     // long now up in selected
await p.locator('.bz-bar[data-target="steam-rewind"] .bz-drag').dragTo(p.locator('.card[data-slug="the-helm"]'), { targetPosition: { x: 20, y: 200 } });
await p.locator('#selected-title').click(); await p.keyboard.press('Control+A'); await p.keyboard.type('Favorites');
await p.locator('#work-title').click();
await p.waitForTimeout(700);
const state = await p.evaluate(() => ({
  sections: [...document.querySelectorAll('#main > [data-section]')].map(s => s.dataset.section + (s.classList.contains('is-off') ? '(off)' : '')),
  cards: [...document.querySelectorAll('#grid .card')].slice(0, 6).map(c => c.dataset.slug + (c.classList.contains('wide') ? '[W]' : '') + (c.classList.contains('is-off') ? '(off)' : '')),
  featured: [...document.querySelectorAll('#selected .feature:not(.is-off)')].map(f => f.dataset.slug + (f.classList.contains('flip') ? '(flip)' : '')),
  intro: document.querySelector('[data-edit="work.intro"]').textContent,
  ls: localStorage.getItem('prismet.layout') ? 'layout saved' : 'no layout', text: localStorage.getItem('prismet.edits'),
}));
console.log(JSON.stringify(state, null, 1));
await p.screenshot({ path: '/tmp/edit-mode.png', clip: { x: 0, y: (await p.locator('#work').boundingBox()).y - 20, width: 1440, height: 900 } });
await p.click('#bz-copy'); const copied = await p.evaluate(() => navigator.clipboard.readText());
writeFileSync('/tmp/changes.md', copied); console.log('copied bytes', copied.length, copied.includes('## layout'));
await p.click('#bz-toggle'); await p.waitForTimeout(200);
console.log('about hidden when not editing:', await p.locator('#about').isHidden(), '| airhorn hidden:', await p.locator('.card[data-slug=airhorn]').isHidden());
// reload: state persists?
await p.reload({ waitUntil: 'load' });
console.log('after reload first section:', await p.evaluate(() => document.querySelector('#main > [data-section]').dataset.section), '| selected title:', await p.locator('#selected-title').textContent());
console.log('errors:', errs.length ? errs : 'none');
await b.close();
