// build.mjs — generates the static prismet.xyz site from data/projects.json.
//
//   node showcase/prismet-site/build.mjs            # → showcase/prismet-site/dist/
//   node showcase/prismet-site/build.mjs --preview  # also writes dist/_preview.html (artifact-safe fragment)
//
// No dependencies. Output is plain HTML/CSS/JS + images, so it drops into the existing Fly app
// as static files next to the /api/wordle route (see REDESIGN-PLAN.md).
import { readFileSync, writeFileSync, mkdirSync, copyFileSync, existsSync, rmSync, cpSync } from 'node:fs';
import { dirname, join, basename, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const HERE = dirname(fileURLToPath(import.meta.url));
const SHOWCASE = join(HERE, '..');
const DIST = join(HERE, 'dist');
const PREVIEW = process.argv.includes('--preview');
const data = JSON.parse(readFileSync(join(HERE, 'data/projects.json'), 'utf8'));
const { owner, beams, lenses, projects } = data;
const beamById = Object.fromEntries(beams.map((b) => [b.id, b]));

rmSync(DIST, { recursive: true, force: true });
mkdirSync(join(DIST, 'work'), { recursive: true });

const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// ── assets: copy what pages reference; Fiverr boards (big PNGs) become webp ─────────────────
const copied = new Map();
function asset(p) {
  if (!p) return null;
  if (copied.has(p)) return copied.get(p);
  let out = p;
  const src = join(SHOWCASE, p);
  if (p.startsWith('fiverr/out/')) {
    out = 'assets/boards/' + basename(p, extname(p)) + '.webp';
    mkdirSync(join(DIST, 'assets/boards'), { recursive: true });
    try { execFileSync('convert', [src, '-resize', '1600x', '-quality', '84', join(DIST, out)]); }
    catch { out = 'assets/boards/' + basename(p); copyFileSync(src, join(DIST, out)); }
  } else {
    mkdirSync(dirname(join(DIST, out)), { recursive: true });
    copyFileSync(src, join(DIST, out));
  }
  copied.set(p, out);
  return out;
}
cpSync(join(SHOWCASE, 'assets/fonts'), join(DIST, 'assets/fonts'), { recursive: true });
copyFileSync(join(HERE, 'src/site.css'), join(DIST, 'site.css'));
copyFileSync(join(HERE, 'src/site.js'), join(DIST, 'site.js'));
asset('assets/icons/prismet-app.webp');            // favicon
asset('fiverr/out/portfolio-prismet-spread.png');   // og:image

// ── shared pieces ───────────────────────────────────────────────────────────────────────────
const hueVars = (id) => `--h:var(--${id});--hi:var(--${id}-ink)`;
const mark = (size = 30) => `<svg width="${size}" height="${size}" viewBox="0 0 64 64" aria-hidden="true">
  <defs><linearGradient id="mk" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F4D77E"/><stop offset=".55" stop-color="#D8A53B"/><stop offset="1" stop-color="#9A6E22"/></linearGradient></defs>
  ${['desktop', 'apps', 'worlds', 'minecraft', 'web', 'ai'].map((c, i) => {
    const a = (i * 60 - 90) * Math.PI / 180, l = a - 0.23, r = a + 0.23, P = (ang, rad) => `${(32 + Math.cos(ang) * rad).toFixed(1)} ${(32 + Math.sin(ang) * rad).toFixed(1)}`;
    return `<path d="M${P(l, 17)} L${P(a, 30)} L${P(r, 17)}Z" fill="var(--${c})"/>`;
  }).join('')}
  <circle cx="32" cy="32" r="17" fill="#141331" stroke="url(#mk)" stroke-width="3"/>
  <path d="M32 22 L41 38 L23 38 Z" fill="none" stroke="url(#mk)" stroke-width="2.4" stroke-linejoin="round"/></svg>`;

const FONT_LINK = PREVIEW
  ? '' // preview uses Google Fonts (artifact CSP); production self-hosts
  : '';
const head = ({ title, desc, root = '' }) => `<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${esc(title)}</title>
<meta name="description" content="${esc(desc)}">
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(desc)}">
<meta property="og:image" content="https://prismet.xyz/assets/boards/portfolio-prismet-spread.webp">
<meta name="theme-color" content="#10111A">
<link rel="icon" href="${root}assets/icons/prismet-app.webp">
<link rel="stylesheet" href="${root}assets/fonts/fonts.css">
<link rel="stylesheet" href="${root}site.css">${FONT_LINK}`;

const bar = (root = '') => `<a class="skip" href="#main">Skip to content</a>
<header class="bar"><div class="wrap">
  <a class="brand" href="${root}index.html">${mark(30)}<strong>Prismet</strong><span>by Sage Deutschle</span></a>
  <nav class="nav" aria-label="Main">
    <a href="${root}index.html#work">Work</a>
    <a href="${root}index.html#lenses">Lenses</a>
    <a href="${root}index.html#about">About</a>
    <a class="btn primary keep" href="${esc(owner.fiverr)}">Hire me</a>
    <button class="btn theme" type="button" id="theme-toggle" aria-label="Switch light or dark theme">◐</button>
  </nav>
</div></header>`;

const footer = () => `<footer><div class="wrap">
  <span>© 2026 Sage Deutschle · prismet.xyz</span>
  <span><a href="${esc(owner.github)}">GitHub</a> · <a href="${esc(owner.linkedin)}">LinkedIn</a> · <a href="${esc(owner.fiverr)}">Fiverr</a></span>
</div></footer>`;

const facts = (rows) => `<dl class="facts">${rows.map(([k, v]) => `<div><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join('')}</dl>`;
const isTodo = (p) => p.todo === true;

// ── the prism (hero + navigation) ───────────────────────────────────────────────────────────
// Spectrum order, red deviates least: desktop, apps, worlds, minecraft, web, ai.
const SPECTRUM = ['desktop', 'apps', 'worlds', 'minecraft', 'web', 'ai'];
function prism() {
  const ex = 364, ey = 232, lx = 556;
  const ys = [64, 132, 200, 268, 336, 404];
  const count = (id) => projects.filter((p) => p.beam === id).length;
  const beamsSvg = SPECTRUM.map((id, i) => {
    const b = beamById[id], y = ys[i], d = `M${ex} ${ey} L${lx - 12} ${y}`;
    const n = count(id);
    return `<a class="beam" href="#work" data-beam="${id}" aria-label="${esc(b.label)}: ${n} project${n === 1 ? '' : 's'}">
      <path class="ray" d="${d}" stroke="var(--${id})" stroke-width="5" stroke-linecap="round"/>
      <path class="flow" d="${d}" stroke="#fff" stroke-opacity=".55" stroke-width="2" stroke-linecap="round"/>
      <circle cx="${lx - 12}" cy="${y}" r="5" fill="var(--${id})"/>
      <text x="${lx + 4}" y="${y + 1}">${esc(b.label)}</text>
      <text class="count" x="${lx + 4}" y="${y + 19}">${n} PROJECT${n === 1 ? '' : 'S'}</text>
    </a>`;
  }).join('');
  return `<svg class="prism" viewBox="0 0 720 470" role="group" aria-label="Six categories of work, split from one beam of light">
  <defs>
    <linearGradient id="gold" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F4D77E"/><stop offset=".55" stop-color="#D8A53B"/><stop offset="1" stop-color="#9A6E22"/></linearGradient>
    <linearGradient id="glass" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".16"/><stop offset="1" stop-color="#fff" stop-opacity=".02"/></linearGradient>
    <filter id="glow" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="7"/></filter>
  </defs>
  <circle cx="300" cy="236" r="182" fill="none" stroke="url(#gold)" stroke-opacity=".38" stroke-width="2"/>
  <circle cx="300" cy="236" r="196" fill="none" stroke="url(#gold)" stroke-opacity=".14" stroke-width="1"/>
  <g filter="url(#glow)" opacity=".55">${SPECTRUM.map((id, i) => `<path d="M${ex} ${ey} L${lx - 12} ${ys[i]}" stroke="var(--${id})" stroke-width="10"/>`).join('')}</g>
  <path d="M0 252 L227 252" stroke="var(--beam-core)" stroke-width="10" opacity=".25" filter="url(#glow)"/>
  <path d="M0 252 L227 252" stroke="var(--beam-core)" stroke-width="5" stroke-linecap="round"/>
  <path class="flow" d="M0 252 L227 252" stroke="var(--gold)" stroke-width="2" style="stroke-dasharray:10 14;animation:flow 2.4s linear infinite"/>
  <polygon points="227,252 ${ex},${ey - 14} ${ex},${ey + 14}" fill="var(--beam-core)" opacity=".18"/>
  <polygon points="300,92 410,332 190,332" fill="url(#glass)" stroke="url(#gold)" stroke-width="3.5" stroke-linejoin="round"/>
  ${beamsSvg}
</svg>`;
}

// ── feature media per project (real visuals only) ───────────────────────────────────────────
function media(p) {
  const g = (p.gallery || []).map(asset);
  if (p.slug === 'prismet-app') return `<div class="phones">${[g[2], g[0], g[1]].map((s) => `<img src="${s}" alt="" loading="lazy">`).join('')}</div>`;
  if (p.slug === 'the-helm') return `<div class="collage">${[g[0], g[2], g[4], g[5]].map((s) => `<img src="${s}" alt="" loading="lazy">`).join('')}</div>`;
  if ((p.cover || '').includes('/icons/')) return `<img class="icon" src="${asset(p.cover)}" alt="">`;
  return `<img class="cover" src="${asset(p.cover)}" alt="" loading="lazy">`;
}
const mediaAlt = (p) => ({
  'prismet-app': 'Prismet App Store screenshots: Sea Battle, the home screen, and Chess',
  'the-helm': 'Four HELM widgets rendered from source: clock, GPU telemetry, fleet radar, and departures board',
}[p.slug] || `${p.title}`);

const feature = (p) => {
  const b = beamById[p.beam];
  return `<article class="feature" style="${hueVars(p.beam)}">
  <a class="media" href="work/${p.slug}.html" aria-label="${esc(mediaAlt(p))}">${media(p)}</a>
  <div class="text">
    <span class="beam-tag">${esc(b.label)}</span>
    <h3>${esc(p.title)}</h3>
    <p class="sub">${esc(p.subtitle)}</p>
    ${facts(p.facts.slice(0, 4))}
    <div class="more"><a class="btn" href="work/${p.slug}.html">Read the case study</a>${(p.links || []).filter((l) => l.href.startsWith('http')).slice(0, 1).map((l) => `<a class="btn" href="${esc(l.href)}">${esc(l.label)} ↗</a>`).join('')}</div>
  </div>
</article>`;
};

const card = (p, root = '') => {
  const b = beamById[p.beam];
  const cover = p.cover ? asset(p.cover) : null;
  const contain = cover && cover.includes('/icons/') || (p.cover || '').includes('/tiles/');
  return `<a class="card" href="${root}work/${p.slug}.html" data-beam="${p.beam}" style="${hueVars(p.beam)}">
    <div class="thumb">${cover ? `<img class="${contain ? 'contain' : ''}" src="${root}${cover}" alt="" loading="lazy">` : `<div class="placeholder">Screenshots coming soon</div>`}</div>
    <div class="body">
      <span class="beam-tag">${esc(b.short)}</span>
      <h3>${esc(p.title)}</h3>
      <p>${esc(p.subtitle)}</p>
      <div class="meta"><span class="status">${esc(isTodo(p) ? 'In progress' : p.status)}</span></div>
    </div>
  </a>`;
};

const GIGS = [
  ['minecraft', 'Minecraft plugins', 'Paper and Spigot, from one command to a full toolkit'],
  ['minecraft', 'Minecraft servers & networks', 'Velocity, Docker, permissions, backups'],
  ['apps', 'iPhone, iPad & Mac apps', 'SwiftUI, from first screen to App Store review'],
  ['web', 'Web tools & landing pages', 'Fast, private, no bloat'],
  ['ai', 'AI coding-agent setup', 'Claude Code and Codex working as a team'],
  ['desktop', 'Linux desktop customization', 'KDE Plasma widgets, themes, scripts'],
];

// ── index ───────────────────────────────────────────────────────────────────────────────────
const order = ['prismet-app', 'the-helm', 'bayzyl', 'prismcode'];
const featured = order.map((s) => projects.find((p) => p.slug === s)).filter((p) => p && !isTodo(p));
const tileFor = { 'steam-rewind': 'steamrewind', 'debt-clock': 'debtclock', 'wordgame-api': 'wordle' };

const indexBody = `${bar()}
<main id="main">
<section class="hero" aria-labelledby="hero-title" style="padding:0"><div class="wrap">
  <div>
    <p class="eyebrow">Prismet · the workshop of Sage Deutschle</p>
    <h1 id="hero-title">One workshop, <em>split six ways</em>.</h1>
    <p class="lede">I ship iPhone and Mac games, Minecraft plugins and servers, custom Linux desktops, AI agent systems, and small web tools that keep your data on your device.</p>
    <div class="ctas"><a class="btn primary" href="#work">See the work</a><a class="btn" href="${esc(owner.fiverr)}">Hire me on Fiverr</a></div>
  </div>
  <div>
    ${prism()}
    <div class="beam-chips" aria-label="Categories">${SPECTRUM.map((id) => `<a class="chip" href="#work" data-beam="${id}" style="--h:var(--${id})"><i></i>${esc(beamById[id].label)}</a>`).join('')}</div>
  </div>
</div></section>

<section id="lenses" aria-labelledby="lenses-title"><div class="wrap">
  <div class="sec-head"><div><p class="eyebrow">Live on prismet.xyz</p><h2 id="lenses-title">Lenses</h2></div>
    <p>Live-data tools that started inside the Prismet app. They run here too.</p></div>
  <div class="lenses">${lenses.map((l) => `<a class="lens" href="${esc(l.href)}">
    <img src="${asset('assets/prismet/tiles/' + tileFor[l.id] + '.webp')}" alt="">
    <div><h3>${esc(l.label)}</h3><p>${esc(l.blurb)}</p>${l.kind === 'api' ? `<code>GET ${esc(l.href)}</code>` : ''}</div></a>`).join('')}</div>
</div></section>

<section id="selected" aria-labelledby="selected-title" style="padding-top:0"><div class="wrap">
  <div class="sec-head"><div><p class="eyebrow">Selected work</p><h2 id="selected-title">Four builds, four beams</h2></div></div>
  ${featured.map(feature).join('\n')}
</div></section>

<section id="work" aria-labelledby="work-title" style="background:var(--ground-2);border-block:1px solid var(--hair)"><div class="wrap">
  <div class="sec-head"><div><p class="eyebrow">Everything</p><h2 id="work-title">All work</h2></div>
    <p>${projects.length} projects across six beams. Pick a color to filter.</p></div>
  <div class="filters" role="group" aria-label="Filter by category">
    <button class="chip" type="button" data-filter="all" aria-pressed="true" style="--h:var(--gold)"><i></i>All</button>
    ${SPECTRUM.map((id) => `<button class="chip" type="button" data-filter="${id}" aria-pressed="false" style="--h:var(--${id})"><i></i>${esc(beamById[id].label)}</button>`).join('')}
  </div>
  <div class="grid" id="grid">${projects.map((p) => card(p)).join('')}</div>
</div></section>

<section id="about" aria-labelledby="about-title"><div class="wrap about">
  <div>
    <p class="eyebrow">About</p><h2 id="about-title" style="font-size:var(--t-xl);margin:12px 0 22px">Hi, I'm Sage.</h2>
    <div class="prose">
      <p>Prismet started as a games app I build with family and a crew of AI agents, and it became the name for everything I make. The app is on the App Store, the lenses run on this site, and the rest of the work lives on GitHub.</p>
      <p>I like tools that <strong>respect the person using them</strong>: fast to load, honest about what they do, and private by default. That goes for a Minecraft plugin with confirmations on big edits, a web tool with no backend, and a desktop where every widget earns its pixels.</p>
    </div>
  </div>
  <div class="hire" id="hire">
    <p class="eyebrow">Work with me</p>
    <h3 style="margin-top:10px">What I can build for you</h3>
    <ul>${GIGS.map(([h, t, s]) => `<li style="--h:var(--${h})"><i></i><span>${esc(t)}<small>${esc(s)}</small></span></li>`).join('')}</ul>
    <div class="links"><a class="btn primary" href="${esc(owner.fiverr)}">Hire me on Fiverr</a><a class="btn" href="${esc(owner.github)}">GitHub</a><a class="btn" href="${esc(owner.linkedin)}">LinkedIn</a></div>
  </div>
</div></section>
</main>
${footer()}
<script src="site.js"></script>`;

const DESC = 'Sage Deutschle builds iPhone and Mac games, Minecraft plugins and servers, custom Linux desktops, AI agent systems, and private web tools.';
writeFileSync(join(DIST, 'index.html'), `<!doctype html><html lang="en"><head>${head({ title: 'Prismet · Sage Deutschle', desc: DESC })}</head><body>${indexBody}</body></html>`);

// ── project pages ───────────────────────────────────────────────────────────────────────────
projects.forEach((p, i) => {
  const b = beamById[p.beam];
  const next = projects[(i + 1) % projects.length], prev = projects[(i - 1 + projects.length) % projects.length];
  const gal = (p.gallery || []).map(asset);
  const wide = gal.some((s) => /boards|helm|web\//.test(s));
  const body = `${bar('../')}
<main id="main" style="${hueVars(p.beam)}">
  <div class="wrap p-hero">
    <p class="crumbs"><a href="../index.html#work">Work</a> / <a href="../index.html#work" data-beam="${p.beam}">${esc(b.label)}</a></p>
    <h1>${esc(p.title)}</h1>
    <p class="sub">${esc(p.subtitle)}</p>
    <div class="row"><span class="status">${esc(isTodo(p) ? 'In progress' : p.status)}</span>${(p.links || []).map((l) => `<a class="btn" href="${esc(l.href.startsWith('/') ? '..' + l.href : l.href)}">${esc(l.label)}${l.href.startsWith('http') ? ' ↗' : ''}</a>`).join('')}</div>
  </div>
  ${gal.length ? `<div class="gallery${wide ? ' wide' : ''}" tabindex="0" aria-label="Gallery">${gal.map((s, k) => `<img src="../${s}" alt="${esc(p.title)} image ${k + 1}" loading="${k < 2 ? 'eager' : 'lazy'}">`).join('')}</div>` : ''}
  <div class="wrap p-body">
    <div class="prose">
      ${isTodo(p) ? `<p class="todo">${esc(p.summary)}</p>` : `<p>${esc(p.summary)}</p>`}
      ${p.highlights?.length ? `<h2>Highlights</h2><ul class="hl">${p.highlights.map((h) => `<li>${esc(h)}</li>`).join('')}</ul>` : ''}
    </div>
    <aside>
      ${facts([['Role', p.role], ['Year', p.year], ...p.facts])}
      <div class="stack" aria-label="Stack">${p.stack.map((s) => `<span>${esc(s)}</span>`).join('')}</div>
    </aside>
  </div>
  <div class="wrap next">
    <a href="${prev.slug}.html"><small>← Previous</small><strong>${esc(prev.title)}</strong></a>
    <a href="${next.slug}.html" style="text-align:right"><small>Next →</small><strong>${esc(next.title)}</strong></a>
  </div>
</main>
${footer()}
<script src="../site.js"></script>`;
  writeFileSync(join(DIST, 'work', p.slug + '.html'), `<!doctype html><html lang="en"><head>${head({ title: `${p.title} · Prismet`, desc: p.subtitle, root: '../' })}</head><body>${body}</body></html>`);
});

// ── artifact preview: same page as a fragment (the artifact host supplies <head>/<body>) ────
if (PREVIEW) {
  const frag = `<title>Prismet Redesign</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Newsreader:ital,opsz,wght@0,6..72,400..800;1,6..72,400..600&family=Hanken+Grotesk:wght@400..700&family=JetBrains+Mono:wght@400..700&display=swap">
<link rel="stylesheet" href="site.css">
${indexBody}`;
  writeFileSync(join(DIST, '_preview.html'), frag);
}
console.log(`built ${projects.length} project pages + index → ${DIST}`);
