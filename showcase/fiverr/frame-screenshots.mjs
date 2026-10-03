// frame-screenshots.mjs — turns the best raw screenshots in fiverr/screenshots/ into Fiverr-ready
// 1280×769 images (rendered at 2×): the screenshot centered on the Prismet ground, tinted by its
// category color, with a short caption. Output: fiverr/screenshots/00-fiverr-ready/.
//
//   node showcase/fiverr/frame-screenshots.mjs
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const LIB = join(HERE, 'screenshots');
const OUT = join(LIB, '00-fiverr-ready');
mkdirSync(OUT, { recursive: true });
const HUE = { apps: '#E68C33', minecraft: '#4D8C6B', worlds: '#CCA838', web: '#3D75A8', ai: '#75579E', desktop: '#DB4757' };
const INK = { apps: '#F2A55A', minecraft: '#7CC49C', worlds: '#E3C35A', web: '#7FAEE0', ai: '#B49AE0', desktop: '#F27A86' };

// [file under screenshots/, category, eyebrow, caption]
const PICKS = [
  ['01-iphone-mac-apps/prismet-iphone-home.jpg', 'apps', 'Prismet · App Store', '19 games and 3 live-data lenses in one calm app'],
  ['01-iphone-mac-apps/prismet-iphone-chess2d.jpg', 'apps', 'Prismet · Chess', 'A tunable chess engine with 2D and 3D boards'],
  ['01-iphone-mac-apps/prismet-iphone-seabattle.jpg', 'apps', 'Prismet · Sea Battle', 'Play solo, pass-and-play, or online with a friend'],
  ['01-iphone-mac-apps/prismet-catan-3d-board.jpg', 'apps', 'Prismet · 3D board', 'A 3D Catan board in its meadow theme'],
  ['07-desktop-apps/airhorn.jpg', 'apps', 'Airhorn', 'One big red button, fully offline'],
  ['02-minecraft/server-build-cherry-blossom-village.jpg', 'minecraft', 'Minecraft · Paper server', 'A cherry-blossom village on the server Bayzyl runs on'],
  ['02-minecraft/server-build-above-the-clouds.jpg', 'minecraft', 'Minecraft · Paper server', 'Another build on the same server, above the clouds'],
  ['02-minecraft/server-build-dark-spire.jpg', 'minecraft', 'Minecraft · AxiomEXTD', 'A build on the server the AxiomEXTD extension targets'],
  ['03-linux-desktop-helm/widgets/helm-widget-chronos.png', 'desktop', 'THE HELM · KDE Plasma 6', 'A custom clock and kernel widget, rendered from its QML source'],
  ['03-linux-desktop-helm/widgets/helm-widget-cpu.png', 'desktop', 'THE HELM · KDE Plasma 6', 'Per-thread CPU load drawn as lit building windows'],
  ['03-linux-desktop-helm/widgets/helm-widget-worldclock.png', 'desktop', 'THE HELM · KDE Plasma 6', 'A world clock that spans a whole monitor'],
  ['04-web-tools/steam-rewind-desktop.jpg', 'web', 'Steam Rewind · prismet.xyz', 'A Steam library turned into stories about time and value'],
  ['04-web-tools/debt-clock-desktop.jpg', 'web', 'Accessible Debt Clock', 'Treasury data, readable by screen readers and keyboards'],
  ['04-web-tools/homescreen-filled-desktop-mock.png', 'web', 'Home Screen Planner', 'Sorts 300+ apps into folders on a live phone mockup'],
  ['04-web-tools/organize-desktop.jpg', 'web', 'Phone Declutter', '46 tasks across 11 zones, saved only on your device'],
  ['04-web-tools/qr-scanner-decoding.png', 'web', 'QR Scanner', 'One HTML file, no backend, works offline'],
  ['08-prismet-site-redesign/site-home-dark.jpg', 'web', 'prismet.xyz redesign', 'A prism hero that splits the work into six categories'],
  ['08-prismet-site-redesign/site-all-work.jpg', 'web', 'prismet.xyz redesign', '25 projects, filterable by category'],
  ['05-ai-agents/cicero-council-floor.jpg', 'ai', 'Cicero', 'A council of AI agents with a transcript and verdict'],
  ['05-ai-agents/hoi4-ai-war-room-observer-console.jpg', 'ai', 'HOI4 AI War Room', 'AI agents play Hearts of Iron IV while a console logs each turn'],
  ['05-ai-agents/usage-tracker-dashboard.jpg', 'ai', 'Usage Tracker', 'Agent token use and costs per provider and account'],
  ['06-games-and-mods/the-long-now-medieval-era.jpg', 'worlds', 'The Long Now', 'A pixel-art game world that changes across eras'],
  ['06-games-and-mods/civ5-mod-profiles-choose-your-build.jpg', 'worlds', 'Civ V Mod Profiles', 'Pick a stable or a risky mod build before launch'],
];

const fonts = pathToFileURL(join(HERE, '../assets/fonts/fonts.css')).href;
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
const html = (img, cat, eyebrow, caption) => `<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="${fonts}"><style>
*{box-sizing:border-box;margin:0}
html,body{width:1280px;height:769px;overflow:hidden;background:#10111A;font-family:"Hanken Grotesk",system-ui,sans-serif;color:#F2F4FC}
.f{position:relative;width:1280px;height:769px;background:radial-gradient(900px 600px at 0% 0%,${HUE[cat]}40,transparent 70%),radial-gradient(800px 560px at 100% 100%,${HUE[cat]}26,transparent 70%),#10111A}
.stage{position:absolute;left:48px;right:48px;top:40px;bottom:120px;display:flex;align-items:center;justify-content:center}
.stage img{max-width:100%;max-height:100%;border-radius:14px;box-shadow:0 30px 70px rgba(0,0,0,.6),0 0 0 1px rgba(255,255,255,.08)}
.cap{position:absolute;left:56px;bottom:38px;right:360px}
.eb{font:600 13px/1 "Martian Mono",monospace;letter-spacing:.1em;text-transform:uppercase;color:${INK[cat]};display:flex;align-items:center;gap:10px}
.eb i{width:22px;height:3px;border-radius:2px;background:${HUE[cat]}}
.t{margin-top:10px;font:700 24px/1.2 "Unbounded",sans-serif;letter-spacing:-.02em}
.by{position:absolute;right:56px;bottom:40px;font:500 13px/1.6 "Martian Mono",monospace;color:rgba(242,244,252,.55);text-align:right}
.by b{display:block;font:700 15px/1.3 "Unbounded",sans-serif;color:#F2F4FC;letter-spacing:-.01em}
</style></head><body><div class="f"><div class="stage"><img src="${pathToFileURL(join(LIB, img)).href}"></div>
<div class="cap"><div class="eb"><i></i>${esc(eyebrow)}</div><div class="t">${esc(caption)}</div></div>
<div class="by"><b>Sage Deutschle</b>prismet.xyz</div></div></body></html>`;

const b = await chromium.launch();
const ctx = await b.newContext({ viewport: { width: 1280, height: 769 }, deviceScaleFactor: 2 });
const p = await ctx.newPage();
const tmp = join(OUT, '.frame.html');
const index = [];
for (const [img, cat, eyebrow, caption] of PICKS) {
  writeFileSync(tmp, html(img, cat, eyebrow, caption));
  await p.goto(pathToFileURL(tmp).href, { waitUntil: 'load' });
  await p.evaluate(() => document.fonts.ready);
  const name = img.split('/').pop().replace(/\.(jpg|png)$/, '') + '.jpg';
  await p.screenshot({ path: join(OUT, name), type: 'jpeg', quality: 92 });
  index.push([name, img, caption]);
}
await b.close();
(await import('node:fs')).unlinkSync(tmp);
console.log(`framed ${index.length} → ${OUT}`);
