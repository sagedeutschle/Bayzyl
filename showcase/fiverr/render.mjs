// render.mjs — builds every Fiverr gig image + portfolio board as HTML, then screenshots it.
//
//   node showcase/fiverr/render.mjs            # all images
//   node showcase/fiverr/render.mjs gig-ios     # just one (prefix match)
//
// Every visual is real: App Store screenshots, Playwright captures of the live web tools,
// QML widgets rendered from source, and text pulled from the repos (Bayzyl's help pages,
// command registry, mc-admin usage). Diagrams are labeled as diagrams. Nothing is mocked
// up to look like a screenshot of something that doesn't exist.
import { chromium } from '/opt/node22/lib/node_modules/playwright/index.mjs';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..');               // repo root
const TPL = join(HERE, 'templates');
const OUT = join(HERE, 'out');
mkdirSync(TPL, { recursive: true }); mkdirSync(OUT, { recursive: true });
const A = (p) => '../../assets/' + p;              // asset path as seen from templates/

const W = 1280, H = 769;                           // Fiverr's recommended gig image size
const HUE = { apps: '#E68C33', minecraft: '#4D8C6B', worlds: '#CCA838', web: '#3D75A8', ai: '#75579E', desktop: '#DB4757' };
// brighter companions for text on the dark ground (same hue family, AA on #10111A)
const HUE_TXT = { apps: '#F2A55A', minecraft: '#7CC49C', worlds: '#E3C35A', web: '#7FAEE0', ai: '#B49AE0', desktop: '#F27A86' };

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// ── brand mark: the Prismet lens (gold ring + six wheel rays), simplified from the app icon
const mark = (size = 34) => `
<svg width="${size}" height="${size}" viewBox="0 0 64 64" aria-hidden="true">
  <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F4D77E"/><stop offset=".55" stop-color="#D8A53B"/><stop offset="1" stop-color="#9A6E22"/></linearGradient></defs>
  ${['#DB4757', '#E68C33', '#CCA838', '#4D8C6B', '#3D75A8', '#75579E'].map((c, i) => {
    const a = (i * 60 - 90) * Math.PI / 180, x = 32 + Math.cos(a) * 29, y = 32 + Math.sin(a) * 29;
    const l = (i * 60 - 90 - 13) * Math.PI / 180, r = (i * 60 - 90 + 13) * Math.PI / 180;
    return `<path d="M${(32 + Math.cos(l) * 17).toFixed(1)} ${(32 + Math.sin(l) * 17).toFixed(1)} L${x.toFixed(1)} ${y.toFixed(1)} L${(32 + Math.cos(r) * 17).toFixed(1)} ${(32 + Math.sin(r) * 17).toFixed(1)}Z" fill="${c}" opacity=".9"/>`;
  }).join('')}
  <circle cx="32" cy="32" r="17" fill="#141331" stroke="url(#g)" stroke-width="3"/>
  <path d="M32 22 L41 38 L23 38 Z" fill="none" stroke="url(#g)" stroke-width="2.4" stroke-linejoin="round"/>
</svg>`;

// fonts are self-hosted (showcase/assets/fonts) — no third-party requests, renders offline
const FONTS = `<link rel="stylesheet" href="../../assets/fonts/fonts.css">`;

const CSS = `
:root{
  /* Prismet dark palette (from PrismetDesign.swift) + the six-hue wheel */
  --ground:#10111A; --panel:#1B1D2A; --panelHi:#282B3C; --hair:rgba(255,255,255,.10); --outline:rgba(255,255,255,.18);
  --ink:#F2F4FC; --ink2:rgba(242,244,252,.70); --ink3:rgba(242,244,252,.46);
  --gold:#B88A33; --goldHi:#E2B65C;
  --display:"Unbounded", "Arial Black", sans-serif;
  --body:"Hanken Grotesk", system-ui, sans-serif;
  --mono:"Martian Mono", ui-monospace, monospace;
  --code:"JetBrains Mono", ui-monospace, monospace;
}
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:${W}px;height:${H}px;overflow:hidden;background:var(--ground);color:var(--ink);font-family:var(--body);-webkit-font-smoothing:antialiased}
.frame{position:relative;width:${W}px;height:${H}px;overflow:hidden;isolation:isolate}
.frame::before{content:"";position:absolute;inset:0;z-index:-2;
  background:radial-gradient(900px 620px at 0% 0%, color-mix(in srgb,var(--hue) 30%, transparent), transparent 70%),
             radial-gradient(700px 520px at 100% 100%, color-mix(in srgb,var(--hue) 16%, transparent), transparent 70%)}
.frame::after{content:"";position:absolute;inset:0;z-index:-1;opacity:.5;
  background-image:radial-gradient(rgba(255,255,255,.06) 1px, transparent 1px);background-size:22px 22px;
  mask-image:linear-gradient(90deg,#000 0,transparent 55%)}
.left{position:absolute;left:64px;top:64px;bottom:104px;width:500px;display:flex;flex-direction:column;gap:22px}
.eyebrow{font:600 13px/1 var(--mono);letter-spacing:.1em;text-transform:uppercase;color:var(--goldHi);display:flex;align-items:center;gap:12px}
.eyebrow i{display:inline-block;width:26px;height:3px;border-radius:2px;background:var(--hue)}
h1{font:700 50px/1.1 var(--display);letter-spacing:-.025em;text-wrap:balance}
h1 em{font-style:normal;color:var(--hueTxt)}
.lede{font:500 21px/1.42 var(--body);color:var(--ink2);max-width:470px;text-wrap:pretty}
.chips{display:flex;flex-direction:column;gap:10px;margin-top:auto}
.chip{display:flex;align-items:center;gap:12px;font:600 19px/1.25 var(--body);color:var(--ink)}
.chip b{flex:none;width:10px;height:10px;border-radius:50%;background:var(--hue);box-shadow:0 0 0 4px color-mix(in srgb,var(--hue) 25%, transparent)}
.foot{position:absolute;left:64px;right:64px;bottom:26px;display:flex;align-items:center;justify-content:space-between;
  font:500 12.5px/1 var(--mono);color:var(--ink3);letter-spacing:.04em}
.foot .brand{display:flex;align-items:center;gap:10px;color:var(--ink2)}
.foot .brand strong{font:700 15px/1 var(--display);letter-spacing:-.01em;color:var(--ink)}
.right{position:absolute;top:0;right:0;bottom:0;left:600px}
.label{font:600 12px/1 var(--mono);letter-spacing:.14em;text-transform:uppercase;color:var(--ink3)}
.card{background:var(--panel);border:1px solid var(--outline);border-radius:18px;box-shadow:0 24px 60px rgba(0,0,0,.45)}
.shot{border-radius:22px;box-shadow:0 30px 70px rgba(0,0,0,.55), 0 0 0 1px rgba(255,255,255,.08)}
/* Minecraft chat (Bayzyl) */
.mc{font-family:"Pixelify Sans", var(--mono);font-size:21px;line-height:1.38;background:rgba(0,0,0,.62);padding:14px 18px;border-radius:4px;
  text-shadow:2px 2px 0 rgba(0,0,0,.55);box-shadow:0 30px 70px rgba(0,0,0,.5)}
.mc .l{white-space:pre-wrap}
.mc .b{font-weight:600}
/* terminal */
.term{font:500 15px/1.6 var(--code);color:#D7DBEA;background:#0B0C12;border:1px solid var(--outline);border-radius:14px;overflow:hidden;box-shadow:0 30px 70px rgba(0,0,0,.55)}
.term .bar{display:flex;gap:7px;align-items:center;padding:11px 14px;background:#151722;border-bottom:1px solid var(--hair)}
.term .bar i{width:11px;height:11px;border-radius:50%;background:#3A3D4F}
.term .bar span{margin-left:10px;font-size:12px;color:var(--ink3);letter-spacing:.06em}
.term pre{padding:16px 18px;font:inherit;white-space:pre}
.term .c{color:#6E748C} .term .p{color:var(--hueTxt)} .term .k{color:var(--goldHi)}
/* diagram boxes */
.node{position:absolute;padding:14px 16px;border-radius:14px;background:var(--panel);border:1px solid var(--outline);font:600 17px/1.25 var(--body)}
.node small{display:block;margin-top:6px;font:500 13px/1.35 var(--code);color:var(--ink3);letter-spacing:.02em}
.node.hot{border-color:color-mix(in srgb,var(--hue) 70%, white 10%);box-shadow:0 0 0 4px color-mix(in srgb,var(--hue) 22%, transparent), 0 20px 50px rgba(0,0,0,.45)}
svg.wires{position:absolute;inset:0;overflow:visible}
.tag{position:absolute;font:600 11px/1 var(--mono);letter-spacing:.1em;text-transform:uppercase;color:var(--ink3)}
`;

const page = (body, { hue = 'apps', title = 'image' } = {}) => `<!doctype html><html><head><meta charset="utf-8"><title>${esc(title)}</title>${FONTS}
<style>${CSS}</style></head><body><div class="frame" style="--hue:${HUE[hue]};--hueTxt:${HUE_TXT[hue]}">${body}</div></body></html>`;

const foot = (right = 'prismet.xyz · github.com/sagedeutschle') =>
  `<div class="foot"><div class="brand">${mark(30)}<strong>Sage Deutschle</strong></div><div>${esc(right)}</div></div>`;

const gigLeft = ({ eyebrow, title, lede, chips }) => `
<div class="left">
  <div class="eyebrow"><i></i>${esc(eyebrow)}</div>
  <h1>${title}</h1>
  ${lede ? `<p class="lede">${esc(lede)}</p>` : ''}
  <div class="chips">${chips.map((c) => `<div class="chip"><b></b>${esc(c)}</div>`).join('')}</div>
</div>`;

// ── Bayzyl: parse help-pages.yml (Minecraft & color codes) ──────────────────────────────
const MC = { '0': '#000', '1': '#00A', '2': '#0A0', '3': '#0AA', '4': '#A00', '5': '#A0A', '6': '#FFAA00', '7': '#AAA', '8': '#555', '9': '#55F', a: '#55FF55', b: '#55FFFF', c: '#FF5555', d: '#FF55FF', e: '#FFFF55', f: '#FFFFFF' };
const helpPages = (() => {
  const src = readFileSync(join(ROOT, 'BayzylHome/src/main/resources/help-pages.yml'), 'utf8');
  const pages = {}; let cur = null;
  for (const line of src.split('\n')) {
    const m = line.match(/^ {2}(\d+):\s*$/); if (m) { cur = pages[m[1]] = []; continue; }
    const l = line.match(/^ {4}- "(.*)"\s*$/); if (l && cur) cur.push(l[1].replace(/\\"/g, '"'));
  }
  return pages;
})();
const mcLine = (s) => {
  let color = '#FFFFFF', bold = false, out = '';
  for (const part of s.split(/(&[0-9a-fklmnor])/)) {
    const c = part.match(/^&([0-9a-fklmnor])$/);
    if (c) { const k = c[1]; if (MC[k]) { color = MC[k]; bold = false; } else if (k === 'l') bold = true; else if (k === 'r') { color = '#FFF'; bold = false; } continue; }
    if (part) out += `<span style="color:${color}"${bold ? ' class="b"' : ''}>${esc(part.replace(/\|/g, ''))}</span>`;
  }
  return `<div class="l">${out}</div>`;
};
const mcChat = (n, style = '', max = 99) => `<div class="mc" style="${style}">${helpPages[n].slice(0, max).map(mcLine).join('')}</div>`;

// ── Bayzyl: real CommandSpec rows from CommandRegistry.java ──────────────────────────────
const commandSpecs = (() => {
  const src = readFileSync(join(ROOT, 'BayzylHome/src/main/java/com/bayzyl/CommandRegistry.java'), 'utf8');
  return [...src.matchAll(/new CommandSpec\("([^"]+)", "([^"]+)", "([^"]+)"\)/g)].map((m) => ({ name: m[1], desc: m[2], usage: m[3] }));
})();

// ── mc-admin usage block (real header text from the script) ─────────────────────────────
const MC_ADMIN = `<span class="c"># mc-admin — one control plane for the whole network</span>
<span class="p">$</span> mc-admin whitelist add <span class="k">&lt;player&gt;</span>   <span class="c"># auto-fetches the Mojang UUID</span>
<span class="p">$</span> mc-admin ban <span class="k">&lt;player&gt;</span> [reason…]   <span class="c"># network-wide, at the proxy</span>
<span class="p">$</span> mc-admin ipban <span class="k">&lt;player|ip&gt;</span>       <span class="c"># proxy only, never a backend</span>
<span class="p">$</span> mc-admin op <span class="k">&lt;player&gt;</span> [-s hub]       <span class="c"># per-server, over RCON</span>
<span class="p">$</span> mc-admin gamerule <span class="k">&lt;rule&gt; &lt;value&gt;</span> -w <span class="k">&lt;world&gt;</span>
<span class="p">$</span> mc-admin worlds                  <span class="c"># Multiverse world list</span>
<span class="p">$</span> mc-admin list                    <span class="c"># players on proxy + backends</span>`;

const networkDiagram = (x0 = 0, y0 = 0) => `
<div style="position:absolute;left:${x0}px;top:${y0}px;width:650px;height:330px">
  <svg class="wires" width="650" height="330" viewBox="0 0 650 330" fill="none">
    <path d="M100 60 C 160 60, 160 165, 200 165" stroke="var(--hue)" stroke-width="2.5" stroke-dasharray="7 7"/>
    <path d="M100 165 L 200 165" stroke="var(--hue)" stroke-width="2.5" stroke-dasharray="7 7"/>
    <path d="M100 270 C 160 270, 160 165, 200 165" stroke="var(--hue)" stroke-width="2.5" stroke-dasharray="7 7"/>
    <path d="M392 165 C 425 165, 425 86, 452 86" stroke="var(--ink3)" stroke-width="2"/>
    <path d="M392 165 C 425 165, 425 236, 452 236" stroke="var(--ink3)" stroke-width="2"/>
  </svg>
  <div class="node" style="left:0;top:36px;width:100px;text-align:center">Players</div>
  <div class="node" style="left:0;top:141px;width:100px;text-align:center">Players</div>
  <div class="node" style="left:0;top:246px;width:100px;text-align:center">Players</div>
  <div class="node hot" style="left:200px;top:110px;width:192px">Velocity proxy<small>login · whitelist<br>LibertyBans</small></div>
  <div class="node" style="left:452px;top:40px;width:196px">Survival<small>Paper · Docker<br>Multiverse worlds</small></div>
  <div class="node" style="left:452px;top:196px;width:196px">Public hub<small>Paper · Docker</small></div>
  <div class="tag" style="left:200px;top:84px">diagram</div>
</div>`;

const helmFace = (n, x, y, w, extra = '') =>
  `<img src="${A('helm/' + n + '.webp')}" style="position:absolute;left:${x}px;top:${y}px;width:${w}px;box-shadow:0 26px 60px rgba(0,0,0,.6);${extra}">`;

const phone = (src, x, y, w, rot = 0, z = 1) =>
  `<img class="shot" src="${A(src)}" style="position:absolute;left:${x}px;top:${y}px;width:${w}px;transform:rotate(${rot}deg);z-index:${z}">`;

// ════════════════════════════════════════════════════════════════════════════════════════
// GIG COVERS (1280×769)
// ════════════════════════════════════════════════════════════════════════════════════════
const IMAGES = {
  'gig-mc-plugin': () => page(`
    ${gigLeft({ eyebrow: 'Minecraft · Paper plugins', title: 'Custom Minecraft <em>plugins</em>, built to last',
      lede: 'Commands, GUIs, events, and integrations for Paper and Spigot servers.',
      chips: ['Author of Bayzyl: 99 commands', 'Java 21 · Paper 1.21 · FAWE-aware', 'Clean, documented source code'] })}
    <div class="right" style="left:560px">
      <img src="${A('live/bayzyl-1.webp')}" alt="" style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover;mask-image:linear-gradient(90deg,transparent 0,#000 22%);-webkit-mask-image:linear-gradient(90deg,transparent 0,#000 22%)">
      <div style="position:absolute;inset:0;background:linear-gradient(0deg,rgba(16,17,26,.85) 0,rgba(16,17,26,0) 46%)"></div>
      ${mcChat('1', 'position:absolute;left:118px;bottom:92px;width:590px;font-size:17px;line-height:1.32;background:rgba(0,0,0,.55)', 7)}
      <div class="tag" style="right:28px;top:26px;color:rgba(255,255,255,.75)">real build · /bzlhelp in chat</div>
    </div>${foot('github.com/sagedeutschle/Bayzyl')}`, { hue: 'minecraft', title: 'Minecraft plugins' }),

  'gig-mc-server': () => page(`
    ${gigLeft({ eyebrow: 'Minecraft · Servers & networks', title: 'Your Minecraft <em>network</em>, set up right',
      lede: 'Proxies, backends, permissions, and backups, documented so you can run it.',
      chips: ['Velocity proxy + Paper backends', 'Docker, bans, whitelist, worlds', 'One admin command for all of it'] })}
    <div class="right" style="left:560px">
      <img src="${A('live/bayzyl-2.webp')}" alt="" style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover;opacity:.32;mask-image:linear-gradient(90deg,transparent 0,#000 30%);-webkit-mask-image:linear-gradient(90deg,transparent 0,#000 30%)">
    </div>
    <div class="right">
      ${networkDiagram(0, 56)}
      <div class="term" style="position:absolute;left:0;top:420px;width:650px">
        <div class="bar"><i></i><i></i><i></i><span>mc-admin · usage</span></div>
        <pre style="font-size:14px;line-height:1.55">${MC_ADMIN.split('\n').slice(0, 6).join('\n')}</pre>
      </div>
    </div>${foot()}`, { hue: 'minecraft', title: 'Minecraft servers' }),

  'gig-ios-app': () => page(`
    ${gigLeft({ eyebrow: 'iOS · iPadOS · macOS', title: 'SwiftUI apps, <em>shipped</em> to the App Store',
      lede: 'iPhone, iPad, and Mac apps with real polish, from first screen to review.',
      chips: ['Prismet: live on the App Store', '19 games on iPhone, iPad, and Mac', 'Design through release'] })}
    <div class="right">
      ${phone('prismet/store-03_chess.webp', 30, 70, 230, -7, 1)}
      ${phone('prismet/store-01_home.webp', 222, 34, 250, 0, 3)}
      ${phone('prismet/store-04_seabattle.webp', 430, 70, 230, 7, 2)}
    </div>${foot()}`, { hue: 'apps', title: 'iOS apps' }),

  'gig-web-tool': () => page(`
    ${gigLeft({ eyebrow: 'Web tools · Landing pages', title: 'Fast, <em>private</em> web tools',
      lede: 'Tools and pages that load instantly and keep your users’ data on their device.',
      chips: ['No trackers, no bloat, no build step', 'Works offline and on any phone', 'Live demos you can open today'] })}
    <div class="right">
      <div class="card" style="position:absolute;left:16px;top:92px;width:620px;overflow:hidden;padding:0">
        <div style="display:flex;gap:7px;padding:12px 14px;background:#151722;border-bottom:1px solid var(--hair)"><i style="width:11px;height:11px;border-radius:50%;background:#3A3D4F"></i><i style="width:11px;height:11px;border-radius:50%;background:#3A3D4F"></i><i style="width:11px;height:11px;border-radius:50%;background:#3A3D4F"></i><span style="margin-left:12px;font:500 12px/1 var(--mono);color:var(--ink3)">sagedeutschle.github.io/qr-scanner</span></div>
        <img src="${A('web/qr-organize-desktop.webp')}" style="display:block;width:100%">
      </div>
      <img src="${A('web/qr-homescreen-filled-desktop-mock.webp')}" style="position:absolute;left:430px;top:250px;width:236px;filter:drop-shadow(0 30px 50px rgba(0,0,0,.6))">
    </div>${foot('sagedeutschle.github.io/qr-scanner')}`, { hue: 'web', title: 'Web tools' }),

  'gig-ai-agents': () => page(`
    ${gigLeft({ eyebrow: 'AI coding agents · Automation', title: 'AI coding agents that work as a <em>team</em>',
      lede: 'Claude Code and Codex set up with lanes, rules, and hand-offs that hold up.',
      chips: ['Built PrismCode, a multi-agent IDE', 'Claude Code · Codex · AGENTS.md', 'Shipped an App Store app with agents'] })}
    <div class="right">
      <div style="position:absolute;left:0;top:70px;width:660px;height:600px">
        <svg class="wires" width="660" height="600" viewBox="0 0 660 600" fill="none">
          <path d="M150 74 C 260 74, 250 214, 330 214" stroke="#E68C33" stroke-width="3" stroke-dasharray="9 7"/>
          <path d="M150 214 L 330 214" stroke="#B49AE0" stroke-width="3" stroke-dasharray="9 7"/>
          <path d="M150 354 C 260 354, 250 214, 330 214" stroke="#3DD6C3" stroke-width="3" stroke-dasharray="9 7"/>
          <path d="M480 270 L 480 360" stroke="var(--ink3)" stroke-width="2"/>
        </svg>
        <div class="node" style="left:0;top:44px;width:150px">Claude Code<small>agent pane</small></div>
        <div class="node" style="left:0;top:184px;width:150px">Codex<small>agent pane</small></div>
        <div class="node" style="left:0;top:324px;width:150px">DeepSeek<small>agent pane</small></div>
        <div class="node hot" style="left:330px;top:172px;width:300px">One event stream<small>each agent normalized<br>to a shared AgentEvent</small></div>
        <div class="node" style="left:330px;top:360px;width:300px">PRISM A/B race<small>same prompt, two git worktrees<br>diff → keep the winner</small></div>
        <img src="${A('icons/prismcode.webp')}" style="position:absolute;left:470px;top:-8px;width:150px;filter:drop-shadow(0 20px 40px rgba(0,0,0,.6))">
        <div class="tag" style="left:330px;top:146px">PrismCode · diagram</div>
      </div>
    </div>${foot()}`, { hue: 'ai', title: 'AI agents' }),

  'gig-linux-desktop': () => page(`
    ${gigLeft({ eyebrow: 'Linux · KDE Plasma 6', title: 'A <em>custom</em> desktop, down to the widgets',
      lede: 'Plasma widgets, themes, and scripts built around how you actually work.',
      chips: ['28 hand-built QML widgets', 'KDE Plasma 6 · Arch · multi-monitor', 'Themes, overlays, and scripts'] })}
    <div class="right" style="background:radial-gradient(500px 400px at 60% 50%, rgba(255,0,51,.10), transparent 70%)">
      ${helmFace('chronos', 40, 52, 420)}
      ${helmFace('reactor', 476, 40, 196)}
      ${helmFace('cpu', 40, 268, 380)}
      ${helmFace('fleet', 436, 252, 236)}
      ${helmFace('transit', 40, 492, 300)}
      ${helmFace('net', 356, 470, 316)}
    </div>${foot('widgets rendered from source')}`, { hue: 'desktop', title: 'Linux desktops' }),

  // ══════════════════════════════════════════════════════════════════════════════════════
  // PORTFOLIO BOARDS (1280×769)
  // ══════════════════════════════════════════════════════════════════════════════════════
  'portfolio-prismet-spread': () => page(`
    <div style="position:absolute;left:64px;top:56px;right:64px;display:flex;justify-content:space-between;align-items:flex-end">
      <div><div class="eyebrow"><i></i>Prismet · App Store</div><h1 style="font-size:42px;margin-top:14px">Twenty classics, one lens</h1></div>
      <img src="${A('icons/prismet-app.webp')}" style="width:96px;border-radius:22px;box-shadow:0 14px 34px rgba(0,0,0,.5)">
    </div>
    ${['store-01_home', 'store-03_chess', 'store-04_seabattle', 'store-02_wordgame', 'store-05_2048'].map((s, i) =>
      phone('prismet/' + s + '.webp', 64 + i * 232, 200, 214, 0, 1)).join('')}
    ${foot('App Store screenshots')}`, { hue: 'apps', title: 'Prismet spread' }),

  'portfolio-prismet-tiles': () => {
    const tiles = ['wordle', '2048', 'snake', 'minesweeper', 'sudoku', 'rubiks', 'lightsout', 'sliding', 'nonogram', 'chess', 'reversi', 'checkers', 'connectfour', 'gomoku', 'seabattle', 'solitaire', 'spider', 'crazyeight', 'brickbench', 'oracle', 'debtclock', 'steamrewind'];
    const names = { wordle: 'Wordgame', '2048': '2048', snake: 'Snake', minesweeper: 'Minesweeper', sudoku: 'Sudoku', rubiks: "Rubik's", lightsout: 'Lights Out', sliding: 'Sliding', nonogram: 'Nonogram', chess: 'Chess', reversi: 'Reversi', checkers: 'Checkers', connectfour: 'Connect 4', gomoku: 'Gomoku', seabattle: 'Sea Battle', solitaire: 'Solitaire', spider: 'Spider', crazyeight: 'Crazy 8', brickbench: 'Brick Bench', oracle: 'Oracle', debtclock: 'Debt Clock', steamrewind: 'Steam Rewind' };
    return page(`
    <div style="position:absolute;left:64px;top:56px"><div class="eyebrow"><i></i>Prismet · game tiles</div><h1 style="font-size:42px;margin-top:14px">19 games, 3 live lenses</h1></div>
    <p class="lede" style="position:absolute;left:64px;top:610px;max-width:900px">Every tile is drawn for the app, so each game gets its own look on the home screen.</p>
    <div style="position:absolute;left:64px;right:64px;top:232px;display:grid;grid-template-columns:repeat(11,1fr);gap:40px 14px">
      ${tiles.map((t) => `<figure style="display:flex;flex-direction:column;align-items:center;gap:9px">
        <img src="${A('prismet/tiles/' + t + '.webp')}" style="width:98px;border-radius:22px;box-shadow:0 12px 26px rgba(0,0,0,.5)">
        <figcaption style="font:600 14px/1.1 var(--body);color:var(--ink2);text-align:center">${names[t]}</figcaption></figure>`).join('')}
    </div>${foot('icons drawn for the app')}`, { hue: 'apps', title: 'Prismet tiles' });
  },

  'portfolio-helm-wall': () => page(`
    <div style="position:absolute;left:48px;top:40px;display:flex;align-items:baseline;gap:18px"><div class="eyebrow"><i></i>THE HELM · KDE Plasma 6</div></div>
    ${helmFace('worldclock', 48, 78, 1184)}
    ${helmFace('chronos', 48, 390, 396)}
    ${helmFace('gpu', 460, 390, 346)}
    ${helmFace('diskmap', 822, 390, 262)}
    ${helmFace('reactor', 1100, 390, 132)}
    ${foot('28 QML widgets · rendered from source')}`, { hue: 'desktop', title: 'Helm wall' }),

  'portfolio-helm-arcade': () => page(`
    <div style="position:absolute;left:64px;top:56px"><div class="eyebrow"><i></i>THE HELM · arcade faces</div><h1 style="font-size:37px;margin-top:14px;max-width:330px">Games that live on the desktop</h1></div>
    ${helmFace('breakout', 64, 330, 280)}
    ${helmFace('minesweeper', 400, 70, 270)}
    ${helmFace('orbital', 690, 90, 262)}
    ${helmFace('snake', 970, 120, 250)}
    ${foot('rendered from source')}`, { hue: 'desktop', title: 'Helm arcade' }),

  'portfolio-qr-suite': () => page(`
    <div style="position:absolute;left:64px;top:56px"><div class="eyebrow"><i></i>Web tools · no backend</div><h1 style="font-size:40px;margin-top:14px;max-width:560px">Four tools, one HTML file each</h1></div>
    <img class="shot" src="${A('web/qr-scanner-desktop.webp')}" style="position:absolute;left:64px;top:262px;width:560px;border-radius:12px">
    <img src="${A('web/qr-homescreen-filled-desktop-mock.webp')}" style="position:absolute;left:672px;top:70px;width:268px;filter:drop-shadow(0 30px 50px rgba(0,0,0,.6))">
    <img class="shot" src="${A('web/qr-organize-phone.webp')}" style="position:absolute;left:972px;top:150px;width:244px;height:500px;object-fit:cover;object-position:top;border-radius:26px">
    ${foot('sagedeutschle.github.io/qr-scanner')}`, { hue: 'web', title: 'QR suite' }),

  'portfolio-bayzyl-help': () => page(`
    <div style="position:absolute;left:64px;top:56px"><div class="eyebrow"><i></i>Bayzyl · /bzlhelp</div><h1 style="font-size:38px;margin-top:14px;max-width:520px">A 15-page guide, inside the game</h1>
      <p class="lede" style="margin-top:16px;max-width:470px">Rendered from the plugin's own help-pages.yml, color codes and all.</p></div>
    ${mcChat('6', 'position:absolute;left:640px;top:56px;width:580px;font-size:17px')}
    ${mcChat('8', 'position:absolute;left:64px;top:372px;width:600px;font-size:17px', 9)}
    ${foot('github.com/sagedeutschle/Bayzyl')}`, { hue: 'minecraft', title: 'Bayzyl help' }),

  'portfolio-bayzyl-commands': () => {
    const pick = ['sphere', 'cyl', 'pyramid', 'brush', 'mask', 'generate', 'generatebiome', 'forestgen'];
    const rows = pick.map((n) => commandSpecs.find((c) => c.name === n)).filter(Boolean);
    return page(`
    <div style="position:absolute;left:64px;top:56px"><div class="eyebrow"><i></i>Bayzyl · command registry</div><h1 style="font-size:38px;margin-top:14px">99 commands, one syntax</h1></div>
    <div class="card" style="position:absolute;left:64px;right:64px;top:196px;padding:8px 0;overflow:hidden">
      ${rows.map((r) => `<div style="display:grid;grid-template-columns:150px 1fr;gap:20px;padding:10px 24px;border-bottom:1px solid var(--hair)">
        <div style="font:700 17px/1.4 var(--code);color:${HUE_TXT.minecraft}">/${esc(r.name)}</div>
        <div style="min-width:0"><div style="font:600 16px/1.35 var(--body)">${esc(r.desc)}</div>
          <div style="font:400 13px/1.45 var(--code);color:var(--ink3);white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(r.usage)}</div></div></div>`).join('')}
    </div>${foot('from CommandRegistry.java')}`, { hue: 'minecraft', title: 'Bayzyl commands' });
  },

  'portfolio-mc-builds': () => page(`
    <div style="position:absolute;left:64px;top:52px"><div class="eyebrow"><i></i>Minecraft · on the server</div><h1 style="font-size:40px;margin-top:14px">Builds on the Paper server Bayzyl runs on</h1></div>
    <img class="shot" src="${A('live/bayzyl-1.webp')}" style="position:absolute;left:64px;top:180px;width:700px;height:470px;object-fit:cover;border-radius:16px">
    <img class="shot" src="${A('live/bayzyl-2.webp')}" style="position:absolute;left:784px;top:180px;width:432px;height:225px;object-fit:cover;border-radius:16px">
    <img class="shot" src="${A('live/axiomextd-1.webp')}" style="position:absolute;left:784px;top:425px;width:432px;height:225px;object-fit:cover;border-radius:16px">
    ${foot('screenshots from the server')}`, { hue: 'minecraft', title: 'Minecraft builds' }),

  'portfolio-mc-network': () => page(`
    <div style="position:absolute;left:64px;top:56px"><div class="eyebrow"><i></i>Minecraft · network ops</div><h1 style="font-size:38px;margin-top:14px;max-width:560px">Every command lands on the right layer</h1></div>
    ${networkDiagram(64, 250)}
    <div class="term" style="position:absolute;left:720px;top:210px;width:500px">
      <div class="bar"><i></i><i></i><i></i><span>mc-admin · usage</span></div>
      <pre style="font-size:13px;line-height:1.6">${MC_ADMIN.replace(/ {2,}(<span class="c">)/g, '\n    $1')}</pre>
    </div>
    ${foot()}`, { hue: 'minecraft', title: 'Minecraft network' }),

  'portfolio-oracle': () => page(`
    <div style="position:absolute;left:64px;top:56px;display:flex;gap:24px;align-items:center">
      <img src="${A('prismet/tiles/oracle.webp')}" style="width:92px;border-radius:22px;box-shadow:0 14px 30px rgba(0,0,0,.5)">
      <div><div class="eyebrow"><i></i>The Wizard King's Decree</div><h1 style="font-size:37px;margin-top:12px">Can chat models call the news?</h1></div>
    </div>
    <div style="position:absolute;left:64px;right:64px;top:240px;height:380px">
      <svg class="wires" width="1152" height="380" viewBox="0 0 1152 380" fill="none">
        ${[[250, 90, 330, 90], [580, 90, 660, 90], [910, 90, 940, 90, 940, 230, 910, 230], [660, 230, 580, 230], [330, 230, 250, 230]].map((p) =>
          `<polyline points="${p.join(' ')}" stroke="var(--hue)" stroke-width="2.5" stroke-dasharray="7 7"/>`).join('')}
      </svg>
      <div class="node" style="left:0;top:50px;width:250px">Harvester<small>pull news matters<br>falsifiability gate</small></div>
      <div class="node hot" style="left:330px;top:50px;width:250px">Council of Mages<small>Claude + GPT deliberate<br>consensus or "divided"</small></div>
      <div class="node" style="left:660px;top:50px;width:250px">The Wizard King<small>proclaims the decree<br>as absolute fact</small></div>
      <div class="node" style="left:660px;top:190px;width:250px">Court Historian<small>Gemini, search-grounded<br>≥2 independent sources</small></div>
      <div class="node" style="left:330px;top:190px;width:250px">Scoring<small>hit rate · bootstrap CI<br>calibration</small></div>
      <div class="node" style="left:0;top:190px;width:250px">Chronicle<small>static site + decrees.json<br>→ Prismet Oracle lens</small></div>
      <div class="tag" style="left:0;top:330px">diagram · 237 offline tests</div>
    </div>${foot()}`, { hue: 'ai', title: 'Oracle' }),

  // LinkedIn banner refresh (1584×396) — same system, broader than the QR-only banner
  'linkedin-banner': () => `<!doctype html><html><head><meta charset="utf-8">${FONTS}<style>${CSS}
    html,body,.frame{width:1584px;height:396px}</style></head><body><div class="frame" style="--hue:#B88A33;--hueTxt:#E2B65C">
    <div style="position:absolute;left:560px;top:78px;right:80px">
      <div style="font:700 50px/1 var(--display);letter-spacing:-.025em">Sage Deutschle<span style="color:var(--goldHi)">.</span></div>
      <div style="font:500 22px/1.4 var(--body);color:var(--ink2);margin-top:16px;max-width:820px">Apps on the App Store, Minecraft plugins and servers, custom Linux desktops, AI agent systems, and small web tools.</div>
      <div style="display:flex;gap:10px;margin-top:22px">${Object.entries({ apps: 'Swift · SwiftUI', minecraft: 'Java · Paper', desktop: 'QML · KDE', ai: 'Claude Code · Codex', web: 'JavaScript' }).map(([k, v]) =>
        `<span style="font:600 15px/1 var(--body);padding:9px 13px;border-radius:999px;border:1px solid ${HUE[k]};color:${HUE_TXT[k]};background:color-mix(in srgb, ${HUE[k]} 14%, transparent)">${v}</span>`).join('')}</div>
    </div>
    <div style="position:absolute;right:80px;bottom:28px;font:500 15px/1 var(--mono);color:var(--ink3)">prismet.xyz · github.com/sagedeutschle</div>
    </div></body></html>`,
};

// ── render ──────────────────────────────────────────────────────────────────────────────
const only = process.argv[2];
const browser = await chromium.launch();
for (const [name, build] of Object.entries(IMAGES)) {
  if (only && !name.startsWith(only)) continue;
  const html = build();
  const file = join(TPL, name + '.html');
  writeFileSync(file, html);
  const size = name === 'linkedin-banner' ? { width: 1584, height: 396 } : { width: W, height: H };
  const ctx = await browser.newContext({ viewport: size, deviceScaleFactor: 2 });
  const p = await ctx.newPage();
  await p.goto('file://' + file, { waitUntil: 'networkidle' });
  await p.evaluate(() => document.fonts.ready);
  await p.waitForTimeout(250);
  await p.screenshot({ path: join(OUT, name + '.png') });
  await ctx.close();
  console.log('rendered', name);
}
await browser.close();
