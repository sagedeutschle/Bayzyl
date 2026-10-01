// shoot-projects.mjs — retina screenshots of the projects that run in a browser, saved into
// showcase/shots/<project>/ for the site and Fiverr.
//   node showcase/tools/shoot-projects.mjs [path/to/qr-scanner]
// Live tools come from the Fly app behind prismet.xyz (Steam Rewind uses its built-in demo
// library via #reset, no real account). QR tools come from a local clone of sagedeutschle/qr-scanner.
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const SHOTS = join(dirname(fileURLToPath(import.meta.url)), '../shots');
const LIVE = 'https://prismet-site-restless-horizon-217.fly.dev';
const QR = process.argv[2] || '/home/user/sagedeutschle/qr-scanner';
// QR_VIDEO: optional .y4m used as the fake camera (e.g. a QR code on a desk) for the scanner shot.
const video = process.env.QR_VIDEO;
const b = await chromium.launch({ args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', ...(video ? [`--use-file-for-fake-video-capture=${video}`] : [])] });

async function shot(slug, name, url, { w = 1440, h = 900, dpr = 2, scheme = 'dark', full = false, before } = {}) {
  mkdirSync(join(SHOTS, slug), { recursive: true });
  const ctx = await b.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: dpr, colorScheme: scheme, permissions: ['camera'] });
  const p = await ctx.newPage();
  await p.goto(url, { waitUntil: 'networkidle', timeout: 45000 });
  await p.evaluate(() => document.fonts.ready);
  if (before) await before(p);
  await p.waitForTimeout(900);
  await p.screenshot({ path: join(SHOTS, slug, `${name}.png`), fullPage: full });
  console.log('shot', slug, name);
  await ctx.close();
}

// Steam Rewind: every lens on the demo library, plus phone.
const demo = async (p) => { await p.click('#reset'); await p.waitForTimeout(800); };
const lens = (i) => async (p) => { await demo(p); await p.selectOption('#lens', { index: i }); await p.waitForTimeout(700); };
const LENSES = ['most-played', 'recent-activity', 'most-expensive', 'best-value', 'recent-value', 'metacritic', 'completion', 'rarest-unlocks', 'deck-time', 'oldest', 'genres', 'backlog-of-shame', 'latest-release'];
for (const [i, n] of LENSES.entries()) await shot('steam-rewind', `steam-rewind-${String(i + 1).padStart(2, '0')}-${n}`, `${LIVE}/steam`, { before: lens(i) });
await shot('steam-rewind', 'steam-rewind-full-page', `${LIVE}/steam`, { before: demo, full: true });
await shot('steam-rewind', 'steam-rewind-phone', `${LIVE}/steam`, { w: 390, h: 844, dpr: 3, before: demo });
await shot('steam-rewind', 'steam-rewind-phone-backlog', `${LIVE}/steam`, { w: 390, h: 844, dpr: 3, before: lens(11) });

// Debt Clock: above the fold, the whole page, light and dark, phone.
await shot('debt-clock', 'debt-clock-desktop-dark', `${LIVE}/debt`);
await shot('debt-clock', 'debt-clock-desktop-light', `${LIVE}/debt`, { scheme: 'light' });
await shot('debt-clock', 'debt-clock-full-page', `${LIVE}/debt`, { full: true });
await shot('debt-clock', 'debt-clock-phone', `${LIVE}/debt`, { w: 390, h: 844, dpr: 3 });
await shot('debt-clock', 'debt-clock-phone-full', `${LIVE}/debt`, { w: 390, h: 844, dpr: 3, full: true });

// QR tools: the four single-file pages.
const qr = (f) => pathToFileURL(join(QR, f)).href;
for (const [f, n] of [['index.html', 'qr-scanner'], ['homescreen.html', 'homescreen-planner'], ['organize.html', 'phone-declutter'], ['playlist.html', 'playlist-maker']]) {
  for (const scheme of ['dark', 'light']) await shot('qr-tools', `${n}-desktop-${scheme}`, qr(f), { scheme });
  await shot('qr-tools', `${n}-phone`, qr(f), { w: 390, h: 844, dpr: 3 });
}
// The scanner mid-decode, reading the fake camera.
const decode = async (p) => { await p.click('#start'); await p.waitForFunction(() => document.querySelector('#result')?.value, null, { timeout: 15000 }).catch(() => console.log('no decode')); };
if (video) {
  await shot('qr-tools', 'qr-scanner-decoding-desktop', qr('index.html'), { before: decode });
  await shot('qr-tools', 'qr-scanner-decoding-phone', qr('index.html'), { w: 390, h: 844, dpr: 3, before: decode });
}
await b.close();
