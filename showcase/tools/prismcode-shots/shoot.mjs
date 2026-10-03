// shoot.mjs — drives the PrismCode demo build (see make-bridge.mjs) and saves screenshots.
//   node shoot.mjs <outDir>
// Needs dist/ served at http://127.0.0.1:5181/ (python3 -m http.server 5181 -d dist).
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { mkdirSync } from 'node:fs';

const OUT = process.argv[2] || 'out';
mkdirSync(OUT, { recursive: true });
const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const ctx = await b.newContext({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 2 });
const p = await ctx.newPage();
p.on('pageerror', (e) => console.log('pageerror', e.message));
await p.goto('http://127.0.0.1:5181/', { waitUntil: 'networkidle' });
await p.evaluate(() => document.fonts.load('13px "JetBrains Mono"'));
const shot = async (name) => { await p.waitForTimeout(500); await p.screenshot({ path: `${OUT}/${name}.png` }); console.log('saved', name); };
const palette = async (title) => {
  await p.evaluate(() => document.activeElement?.blur());
  await p.keyboard.press('Control+Shift+P');
  await p.locator('.palette-input').fill(title);
  await p.waitForTimeout(400);
  await p.locator('.palette-input').press('Enter');
  await p.waitForTimeout(400);
  if (await p.locator('.palette-backdrop').count()) { console.log('palette did not run:', title); await p.keyboard.press('Escape'); }
};

await p.getByRole('button', { name: 'Open Folder' }).click();
await p.waitForTimeout(400);

await p.getByRole('button', { name: /Add Claude/ }).click();
await palette('Agent: Add Codex');
await palette('Agent: Add DeepSeek');
await p.locator('.promptbar textarea, textarea[placeholder^="Prompt the pool"]').first().fill(await p.evaluate(() => window.__demo.PROMPT));
await p.keyboard.press('Control+Enter');
await p.waitForTimeout(3500);
await shot('prismcode-02-three-agents-working');
await p.waitForTimeout(6000);
await shot('prismcode-03-codex-asks-approval');
const allow = p.getByRole('button', { name: /^Allow$/ }).first();
if (await allow.count()) { await allow.click(); await p.waitForTimeout(3000); }
await shot('prismcode-04-all-agents-done');

// Floating terminal over Mission Control.
await p.keyboard.press('Control+t');
await p.waitForTimeout(1200);
await shot('prismcode-05-floating-terminal');
await p.getByRole('button', { name: 'Close terminal' }).first().click();

// Git panel + diff tab.
await palette('View: Show Git');
await p.waitForTimeout(500);
await shot('prismcode-06-git-panel');
const changed = p.locator('text=index.html').first();
if (await changed.count()) { await changed.click(); await p.waitForTimeout(1500); await shot('prismcode-07-diff'); }

// Sessions history.
await palette('View: Show Sessions');
await p.waitForTimeout(600);
await shot('prismcode-09-sessions');

// Usage overview.
await palette('Usage: Show All-Accounts Overview');
await p.waitForTimeout(600);
await shot('prismcode-10-usage');
await p.evaluate(() => { const o = document.querySelector('[class*=usage-overlay], [class*=usage-modal], [role=dialog]'); const b = [...(o || document).querySelectorAll('button')].find((x) => /close|×|✕/i.test(x.getAttribute('aria-label') || x.textContent)); b?.click(); });
await p.waitForTimeout(300);

// PRISM A/B: race Claude and Codex in twin worktrees, then compare.
await palette('Go to Mission Control');
await p.getByRole('button', { name: /PRISM A\/B/ }).click();
await p.locator('.prism-input').fill(await p.evaluate(() => window.__demo.PROMPT));
await p.getByRole('button', { name: 'Fan out' }).click();
await p.waitForTimeout(800);
await p.getByRole('button', { name: 'Compare' }).click();
await p.waitForTimeout(1500);
await shot('prismcode-11-prism-compare');

// (No editor shot: Monaco runs on the main thread here and stalls headless Chromium.)
await b.close();
