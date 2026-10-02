// shoot-og.mjs: captures the entrance of the built home page as the share image (1200×630), night mode.
//   node showcase/prismet-site/build.mjs && node showcase/tools/shoot-og.mjs
// Writes showcase/assets/og/entrance.jpg, which build.mjs copies to dist/assets/og.jpg. Needs Playwright + Chromium.
const { chromium } = await import(process.env.PLAYWRIGHT_MJS || '/opt/node22/lib/node_modules/playwright/index.mjs');
import { spawn } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
const HERE = dirname(fileURLToPath(import.meta.url));
const DIST = join(HERE, '..', 'prismet-site', 'dist');
const OUT = join(HERE, '..', 'assets', 'og', 'entrance.jpg');
const port = 18097;
const server = spawn('python3', ['-m', 'http.server', String(port), '--bind', '127.0.0.1'], { cwd: DIST, stdio: 'ignore' });
await new Promise((r) => setTimeout(r, 800));
try {
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
  const page = await browser.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 2, colorScheme: 'dark' });
  await page.goto(`http://127.0.0.1:${port}/index.html`, { waitUntil: 'networkidle' });
  await page.addStyleTag({ content: '.bar{display:none}.entrance .wrap{padding-block:24px;min-height:630px;align-items:center}.ctas,.directory{display:none}.entrance{border:0}' });
  await page.waitForTimeout(2600);
  await page.screenshot({ path: OUT.replace(/\.jpg$/, '.png'), clip: { x: 0, y: 0, width: 1200, height: 630 } });
  await browser.close();
  execFileSync('convert', [OUT.replace(/\.jpg$/, '.png'), '-resize', '1200x630', '-quality', '86', OUT]);
  execFileSync('rm', [OUT.replace(/\.jpg$/, '.png')]);
  console.log('wrote', OUT);
} finally { server.kill(); }
