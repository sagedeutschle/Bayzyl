// build.mjs — generates the static prismet.xyz site.
//
//   node showcase/prismet-site/build.mjs            # → showcase/prismet-site/dist/
//   node showcase/prismet-site/build.mjs --preview  # → showcase/prismet-site/dist-preview/ with the Edit page editor
//
// WORDS live in content/site.md and content/work/<slug>.md (plain text, edit freely).
// DATA (images, links, categories, rooms, layout) lives in data/projects.json.
// No dependencies. Output is plain HTML/CSS/JS + images, so it drops into the Fly app as static files next to
// the /api/wordle, /rtc, /steam and /debt routes, which this build never touches.
//
// The site is a workshop with a hall plan: six wings (the beams) around a rotunda (the prism). The home page is the
// entrance, the principal works, the register (a ledger of every record), one plate, the lenses and the keeper's
// desk. Each project page shares one skeleton; its "room" (data-room) sets the material and one signature module.
import { readFileSync, writeFileSync, mkdirSync, copyFileSync, rmSync, cpSync, existsSync, statSync } from 'node:fs';
import { dirname, join, basename, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { loadSite, loadWork, inline, plain, listItems, factPairs } from './content.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const SHOWCASE = join(HERE, '..');
const PREVIEW = process.argv.includes('--preview');
const DIST = join(HERE, PREVIEW ? 'dist-preview' : 'dist');
const data = JSON.parse(readFileSync(join(HERE, 'data/projects.json'), 'utf8'));
const { owner, beams, lenses, projects } = data;
const beamById = Object.fromEntries(beams.map((b) => [b.id, b]));
// Spectrum order, red deviates least: desktop, apps, worlds, minecraft, web, ai.
export const SPECTRUM = ['desktop', 'apps', 'worlds', 'minecraft', 'web', 'ai'];
export const SECTION_IDS = ['selected', 'work', 'plate', 'lenses', 'about'];

// ── words ───────────────────────────────────────────────────────────────────────────────────
const S = loadSite();
const W = Object.fromEntries(projects.map((p) => [p.slug, loadWork(p.slug)]));
const missing = new Set();
const site = (key) => { if (!(key in S)) { missing.add(`content/site.md → ## ${key}`); return key; } return S[key]; };
const ABOUT_PARAS = Object.keys(S).filter((key) => /^about\.p\d+$/.test(key)).sort((a, b) => a.slice(7) - b.slice(7));
const work = (slug, f) => { const v = W[slug][f]; if (v === undefined) { missing.add(`content/work/${slug}.md → ## ${f}`); return ''; } return v; };
const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
// In --preview every piece of wording carries its key, so the page editor can save edits back.
const ed = (key, raw) => (PREVIEW ? ` data-edit="${esc(key)}" data-src="${esc(raw)}"` : '');
const T = (key, vars) => ({ a: ed(key, site(key)), h: inline(site(key), vars), p: plain(site(key), vars) });
const P = (slug, f) => ({ a: ed(`work.${slug}.${f}`, work(slug, f)), h: inline(work(slug, f)), p: plain(work(slug, f)) });
const isTodo = (p) => p.todo === true;

rmSync(DIST, { recursive: true, force: true });
mkdirSync(join(DIST, 'work'), { recursive: true });

// ── assets: copy what pages reference, read sizes so every <img> carries width/height ───────
const copied = new Map();
const sizes = new Map();
function webpSize(buf) {
  if (buf.toString('ascii', 0, 4) !== 'RIFF' || buf.toString('ascii', 8, 12) !== 'WEBP') return null;
  const chunk = buf.toString('ascii', 12, 16);
  if (chunk === 'VP8X') return { w: 1 + buf.readUIntLE(24, 3), h: 1 + buf.readUIntLE(27, 3) };
  if (chunk === 'VP8 ') return { w: buf.readUInt16LE(26) & 0x3fff, h: buf.readUInt16LE(28) & 0x3fff };
  if (chunk === 'VP8L') { const b = buf.readUInt32LE(21); return { w: 1 + (b & 0x3fff), h: 1 + ((b >> 14) & 0x3fff) }; }
  return null;
}
function sizeOf(absPath) {
  if (sizes.has(absPath)) return sizes.get(absPath);
  let s = null;
  try { s = webpSize(readFileSync(absPath)); } catch { /* fall through to identify */ }
  if (!s) {
    try { const [w, h] = execFileSync('identify', ['-format', '%w %h', absPath + '[0]']).toString().trim().split(' ').map(Number); s = { w, h }; }
    catch { s = { w: 0, h: 0 }; }
  }
  sizes.set(absPath, s);
  return s;
}
function asset(p) {
  if (!p) return null;
  if (copied.has(p)) return copied.get(p);
  const src = join(SHOWCASE, p);
  if (!existsSync(src)) throw new Error(`missing asset: ${p}`);
  mkdirSync(dirname(join(DIST, p)), { recursive: true });
  copyFileSync(src, join(DIST, p));
  copied.set(p, p);
  return p;
}
/** <img> with width/height from the file, lazy by default. root = '' on the home page, '../' on project pages. */
function img(p, { alt = '', root = '', cls = '', lazy = true, sizesAttr = '', srcset = '', priority = false } = {}) {
  const out = asset(p), { w, h } = sizeOf(join(SHOWCASE, p));
  return `<img${cls ? ` class="${cls}"` : ''} src="${root}${out}"${srcset ? ` srcset="${srcset}" sizes="${sizesAttr}"` : ''} width="${w}" height="${h}" alt="${esc(alt)}"${lazy ? ' loading="lazy" decoding="async"' : priority ? ' fetchpriority="high"' : ''}>`;
}
cpSync(join(SHOWCASE, 'assets/fonts'), join(DIST, 'assets/fonts'), { recursive: true });
copyFileSync(join(HERE, 'src/site.css'), join(DIST, 'site.css'));
copyFileSync(join(HERE, 'src/site.js'), join(DIST, 'site.js'));
if (PREVIEW) for (const f of ['editor.js', 'editor.css']) if (existsSync(join(HERE, 'src', f))) copyFileSync(join(HERE, 'src', f), join(DIST, f));
asset('assets/icons/prismet-app.webp');            // favicon
// og:image: a capture of the entrance (showcase/tools/shoot-og.mjs writes assets/og/entrance.jpg).
copyFileSync(join(SHOWCASE, 'assets/og/entrance.jpg'), join(DIST, 'assets/og.jpg'));

// ── shared pieces ───────────────────────────────────────────────────────────────────────────
const hue = (id) => `--h:var(--${id});--hi:var(--${id}-ink)`;
const arrow = '<svg class="ext" viewBox="0 0 12 12" width="11" height="11" aria-hidden="true"><path d="M3 9.5 9.5 3M4.5 3h5v5" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';
// The artifact preview can't load fonts from its own files, so it uses Google Fonts; production self-hosts.
const GOOGLE_FONTS = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Newsreader:ital,opsz,wght@0,6..72,200..800;1,6..72,200..800&family=Martian+Mono:wdth,wght@75..112.5,100..800&display=swap">';
// The mark: the rotunda seen from above, with the prism inlaid in its floor.
const mark = (size = 28) => `<svg class="mark" width="${size}" height="${size}" viewBox="0 0 64 64" aria-hidden="true">
  <circle cx="32" cy="32" r="29" fill="none" stroke="currentColor" stroke-width="2"/>
  <circle cx="32" cy="32" r="21" fill="none" stroke="currentColor" stroke-width="1" opacity=".55"/>
  <path d="M32 19 L44 40 L20 40 Z" fill="none" stroke="var(--brass)" stroke-width="2.4" stroke-linejoin="round"/>
  ${SPECTRUM.map((c, i) => { const a = (i * 60 - 90) * Math.PI / 180; const x1 = 32 + Math.cos(a) * 23, y1 = 32 + Math.sin(a) * 23, x2 = 32 + Math.cos(a) * 28, y2 = 32 + Math.sin(a) * 28; return `<path d="M${x1.toFixed(1)} ${y1.toFixed(1)} L${x2.toFixed(1)} ${y2.toFixed(1)}" stroke="var(--${c})" stroke-width="3" stroke-linecap="round"/>`; }).join('')}
</svg>`;

const head = ({ title, desc, root = '', noindex = false, url = '' }) => `<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${esc(title)}</title>
<meta name="description" content="${esc(desc)}">${noindex ? '\n<meta name="robots" content="noindex">' : ''}
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(desc)}">
<meta property="og:image" content="https://prismet.xyz/assets/og.jpg">${url ? `\n<meta property="og:url" content="https://prismet.xyz/${esc(url)}">` : ''}
<meta name="theme-color" content="#0F161D" media="(prefers-color-scheme: dark)">
<meta name="theme-color" content="#E4E8EA" media="(prefers-color-scheme: light)">
<link rel="icon" href="${root}assets/icons/prismet-app.webp">
<link rel="preload" href="${root}assets/fonts/Newsreader.woff2" as="font" type="font/woff2" crossorigin>
${PREVIEW ? GOOGLE_FONTS + '\n' : ''}<link rel="stylesheet" href="${root}site.css">${PREVIEW ? `\n<link rel="stylesheet" href="${root}editor.css">` : ''}
<script src="${root}site.js"></script>`;
const scripts = (root = '') => (PREVIEW ? `<script src="${root}editor.js"></script>` : '');

const bar = (root = '') => {
  const [w, l, a, h] = ['nav.work', 'nav.lenses', 'nav.about', 'nav.hire'].map((k) => T(k));
  return `<a class="skip" href="#main">Skip to content</a>
<header class="bar"><div class="wrap">
  <a class="brand" href="${root}index.html">${mark(28)}<strong>Prismet</strong><span>${esc(owner.name)}</span></a>
  <nav class="nav" aria-label="Main">
    <a class="keep" href="${root}index.html#work"${w.a}>${w.h}</a>
    <a href="${root}index.html#lenses"${l.a}>${l.h}</a>
    <a href="${root}index.html#about"${a.a}>${a.h}</a>
    <a class="btn primary keep" href="${esc(owner.fiverr)}"${h.a}>${h.h}</a>
    <button class="btn theme keep" type="button" id="theme-toggle" aria-label="${esc(T('footer.day_night').p)}" data-to-day="${esc(T('theme.to_day').p)}" data-to-night="${esc(T('theme.to_night').p)}"><span class="sun" aria-hidden="true"></span></button>
  </nav>
</div></header>`;
};

const footer = (root = '') => `<footer><div class="wrap">
  <span${T('footer.copyright').a}>${T('footer.copyright').h}</span>
  <nav aria-label="Footer"><a href="${root}index.html#work">${T('nav.work').h}</a><a href="${root}index.html#lenses">${T('nav.lenses').h}</a><a href="${root}index.html#about">${T('nav.about').h}</a><a href="${root}colophon.html"${T('footer.colophon').a}>${T('footer.colophon').h}</a><a href="${esc(owner.github)}">GitHub</a><a href="${esc(owner.linkedin)}">LinkedIn</a><a href="${esc(owner.fiverr)}">Fiverr</a></nav>
</div></footer>`;

const factRows = (slug, extra = [], limit = Infinity) => [...extra, ...factPairs(work(slug, 'facts')).map(([k, v], i) => ({
  k: { a: ed(`work.${slug}.facts.${i}.label`, k), h: inline(k) },
  v: { a: ed(`work.${slug}.facts.${i}.value`, v), h: inline(v) },
}))].slice(0, limit);
const factList = (rows, cls = '') => `<dl class="facts${cls ? ' ' + cls : ''}">${rows.map(({ k, v }) => `<div><dt${k.a}>${k.h}</dt><dd${v.a}>${v.h}</dd></div>`).join('')}</dl>`;
const beamLabel = (id) => T(`beam.${id}`);
const status = (p) => (isTodo(p) ? T('project.in_progress') : P(p.slug, 'status'));
const typeLine = (p) => (W[p.slug].tag ? P(p.slug, 'tag') : beamLabel(p.beam));

// Access links, classified so the register can show them as Source · App Store · Live.
function accessOf(p, root = '') {
  const links = (p.links || []).map((l) => {
    const kind = /github/i.test(l.label) || /github\.com/.test(l.href) ? 'source' : /app store/i.test(l.label) ? 'appstore' : 'live';
    const href = l.href.startsWith('/') ? root + '..' + l.href : l.href;
    const external = /^https?:/.test(l.href);
    return { ...l, kind, external, href: l.href.startsWith('/') ? (root ? '..' + l.href : l.href) : href };
  });
  return links;
}
const accessLabel = { source: 'access.source', appstore: 'access.appstore', live: 'access.live' };
const accessLinks = (p, root = '', withLabel = false) => accessOf(p, root).map((l) => `<a class="link" href="${esc(l.href)}">${withLabel ? esc(l.label) : T(accessLabel[l.kind]).h}${l.external ? arrow : ''}</a>`).join('');

// ── layout (projects.json → layout, featuredOrder, per-project hidden) ──────────────────────
const bySlug = Object.fromEntries(projects.map((p) => [p.slug, p]));
const L = data.layout || {};
const sectionOrder = [...(L.sections || []).filter((id) => SECTION_IDS.includes(id)), ...SECTION_IDS.filter((id) => !(L.sections || []).includes(id))];
const hiddenSections = new Set(L.hiddenSections || []);
const shown = projects.filter((p) => !p.hidden && !isTodo(p));
const featuredSlugs = (data.featuredOrder || []).filter((s) => bySlug[s] && shown.includes(bySlug[s]));
const off = (isOff) => (isOff ? ' is-off' : '');
const tierOf = (p) => (featuredSlugs.includes(p.slug) ? 'principal' : p.tier === 'cabinet' ? 'cabinet' : 'records');
const href = (p, root = '') => `${root}work/${p.slug}.html`;

// ── the hall plan (hero + navigation): six wings around a rotunda, the prism in its floor ────
function plan() {
  const W_ = 720, H_ = 540, cx = 360, cy = 270, R = 78;
  const count = (id) => shown.filter((p) => p.beam === id).length;
  const rooms = { desktop: [48, 60], apps: [48, 216], worlds: [48, 372], minecraft: [472, 60], web: [472, 216], ai: [472, 372] };
  const rw = 200, rh = 108;
  const wings = SPECTRUM.map((id, i) => {
    const [rx, ry] = rooms[id], left = rx < cx;
    const ex = left ? rx + rw : rx, ey = ry + rh / 2;          // the room's inner door
    const dx = ex - cx, dy = ey - cy, len = Math.hypot(dx, dy), ux = dx / len, uy = dy / len;
    const sx = cx + ux * R, sy = cy + uy * R;                   // leave the rotunda
    const px = -uy * 7, py = ux * 7;                            // corridor walls, 7px either side
    const n = count(id), label = beamLabel(id), lead = shown.find((p) => p.beam === id);
    const f = (v) => v.toFixed(1);
    return `<a class="wing" href="#work" data-beam="${id}" style="--i:${i}" aria-label="${esc(label.p)}: ${n} record${n === 1 ? '' : 's'}">
      <path class="wall" d="M${f(sx + px)} ${f(sy + py)} L${f(ex + px)} ${f(ey + py)}" pathLength="1"/>
      <path class="wall" d="M${f(sx - px)} ${f(sy - py)} L${f(ex - px)} ${f(ey - py)}" pathLength="1"/>
      <path class="inlay" d="M${f(sx)} ${f(sy)} L${f(ex)} ${f(ey)}" stroke="var(--${id})" pathLength="1"/>
      <rect class="room" x="${rx}" y="${ry}" width="${rw}" height="${rh}" pathLength="1"/>
      <rect class="room-fill" x="${rx}" y="${ry}" width="${rw}" height="${rh}"/>
      <text class="room-name" x="${rx + rw / 2}" y="${ry + 48}" text-anchor="middle"${ed(`beam.${id}`, site(`beam.${id}`))}>${esc(label.p)}</text>
      <text class="room-lead" x="${rx + rw / 2}" y="${ry + 76}" text-anchor="middle">${lead ? esc(plain(work(lead.slug, 'title'))) + (n > 1 ? ` + ${n - 1}` : '') : ''}</text>
    </a>`;
  }).join('');
  const piers = [[300, 210], [420, 210], [300, 330], [420, 330]].map(([x, y]) => `<rect class="pier" x="${x - 6}" y="${y - 6}" width="12" height="12" pathLength="1"/>`).join('');
  return `<figure class="plan" id="plan">
<svg viewBox="0 0 ${W_} ${H_}" role="group" aria-label="${esc(T('plan.caption').p)}">
  <g class="g-court"><rect class="court" x="24" y="24" width="${W_ - 48}" height="${H_ - 48}" pathLength="1"/><rect class="court inner" x="34" y="34" width="${W_ - 68}" height="${H_ - 68}" pathLength="1"/></g>
  <g class="g-rotunda">
    <path class="beacon" d="M${cx} ${cy - R} L${cx} 34" pathLength="1"/>
    <circle class="rotunda" cx="${cx}" cy="${cy}" r="${R}" pathLength="1"/>
    <circle class="rotunda inner" cx="${cx}" cy="${cy}" r="${R - 18}" pathLength="1"/>
    ${piers}
    <path class="prism" d="M${cx} ${cy - 34} L${cx + 30} ${cy + 20} L${cx - 30} ${cy + 20} Z" pathLength="1"/>
    <path class="beam-in" d="M${cx - 60} ${cy + 2} L${cx - 12} ${cy + 2}" pathLength="1"/>
  </g>
  <g class="g-wings">${wings}</g>
</svg>
<div class="lantern" aria-hidden="true"></div>
<figcaption>
  <span${T('plan.caption').a}>${T('plan.caption').h}</span>
  <ul class="wing-list" aria-label="Wings">${SPECTRUM.map((id) => { const b = beamLabel(id), n = count(id); return `<li><a href="#work" data-beam="${id}" style="${hue(id)}"><i></i><span${b.a}>${b.h}</span><small>${n}</small></a></li>`; }).join('')}</ul>
</figcaption>
</figure>`;
}

// ── signature modules: one per room, only where the data exists ─────────────────────────────
const tileSrc = (id) => `assets/prismet/tiles/${id}.webp`;
function doorModule(p, root = '') {
  const g = p.gallery || [];
  switch (p.room) {
    case 'bench': {
      const step = p.steps[3];
      return `<figure class="mod bench-door">${img('assets/minecraft/bayzyl-scene-plaza-dome.webp', { root, alt: (p.shotAlts || [])[0] || '' })}<figcaption><code>${esc(step.command)}</code><span>${esc(step.title)}</span></figcaption></figure>`;
    }
    case 'arcade':
      return `<div class="mod tile-mosaic" aria-hidden="true">${(p.tiles || []).slice(0, 9).map((t) => img(tileSrc(t), { root, alt: '' })).join('')}</div>`;
    case 'bridge':
      return `<div class="mod face-quad" aria-hidden="true">${['chronos', 'gpu', 'cpu', 'net'].map((n) => img(`assets/helm2/${n}.webp`, { root, alt: '' })).join('')}</div>`;
    case 'notebook':
      return `<figure class="mod plate-door">${img(p.doorCover || g[2] || p.cover, { root, alt: (p.shotAlts || [])[2] || '' })}</figure>`;
    default:
      return p.cover ? `<figure class="mod plate-door">${img(p.cover, { root, alt: '' })}</figure>` : '';
  }
}

function signature(p, root = '../') {
  const s = p.slug;
  if (p.steps) {
    const t = T('bench.title'), c = T('bench.caption');
    return `<section class="sig bench" aria-labelledby="bench-title">
  <div class="wrap">
    <div class="sig-head"><h2 id="bench-title"${t.a}>${t.h}</h2><p${c.a}>${c.h}</p></div>
    <div class="stepper" data-stepper>
      <ol class="steps">${p.steps.map((st, i) => `<li class="step"${i === 0 ? ' aria-current="step"' : ''} data-step="${i}">
        ${img(`assets/bench/step-${i}.webp`, { root, alt: st.command ? `${st.title}: the test plaza after ${st.command}` : st.title, lazy: i !== 0 })}
        ${st.command ? `<p class="step-cmd"><code>${esc(st.command)}</code></p>` : ''}
        <p class="step-title"><span class="step-n">${i}/${p.steps.length - 1}</span> ${esc(st.title)}</p>
      </li>`).join('')}</ol>
      <div class="step-nav"><button type="button" class="btn" data-prev aria-label="${esc(T('bench.previous').p)}"><svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true"><path d="M8 1.5 3.5 6 8 10.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg></button><output aria-live="polite">${esc(T('bench.step').p)} 0 / ${p.steps.length - 1}</output><button type="button" class="btn" data-next aria-label="${esc(T('bench.next').p)}"><svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true"><path d="M4 1.5 8.5 6 4 10.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg></button></div>
    </div>
  </div>
</section>`;
  }
  if (p.tiles) {
    const t = T('arcade.title'), oh = T('arcade.opens_here');
    const lensHref = { steamrewind: '/steam', debtclock: '/debt' };
    return `<section class="sig arcade" aria-labelledby="arcade-title"><div class="wrap">
  <div class="sig-head"><h2 id="arcade-title"${t.a}>${t.h}</h2></div>
  <ul class="tiles">${p.tiles.map((id) => { const name = p.tileNames[id] || id, lens = lensHref[id]; const inner = `${img(tileSrc(id), { root, alt: '' })}<span>${esc(name)}</span>${lens ? `<small${oh.a}>${oh.h}</small>` : ''}`; return `<li${lens ? ' class="lens-tile"' : ''}>${lens ? `<a href="${lens}">${inner}</a>` : inner}</li>`; }).join('')}</ul>
</div></section>`;
  }
  if (p.rack) {
    const t = T('rack.title');
    const groups = ['horizon', 'telemetry', 'wit', 'systems', 'arcade'];
    const alts = Object.fromEntries((p.gallery || []).map((g, i) => [basename(g, '.webp'), (p.shotAlts || [])[i] || '']));
    return `<section class="sig rack" aria-labelledby="rack-title"><div class="wrap">
  <div class="sig-head"><h2 id="rack-title"${t.a}>${t.h}</h2></div>
  ${p.rack.map((faces, gi) => { const g = T(`rack.group_${groups[gi]}`); return `<div class="rack-group"><h3${g.a}>${g.h}</h3><div class="rack-row n${faces.length}">${faces.map((n) => `<figure>${img(`assets/helm2/${n}.webp`, { root, alt: alts[n] })}</figure>`).join('')}</div></div>`; }).join('')}
</div></section>`;
  }
  if (p.eras) {
    const t = T('museum.title');
    return `<section class="sig museum" aria-labelledby="museum-title"><div class="wrap">
  <div class="sig-head"><h2 id="museum-title"${t.a}>${t.h}</h2></div>
  <div class="eras">
    <div class="era-stage">${p.eras.map(([id], i) => img(`assets/worlds/long-now-${id}.webp`, { root, alt: (p.shotAlts || [])[i] || '', cls: `era era-${id}`, lazy: i !== 0 })).join('')}</div>
    <fieldset class="era-dial"><legend class="sr-only">Era</legend>${p.eras.map(([id, label], i) => `<label><input type="radio" name="era" value="${id}"${i === 0 ? ' checked' : ''}><span>${esc(label)}</span></label>`).join('')}</fieldset>
  </div>
</div></section>`;
  }
  if (p.room === 'notebook') {
    const t = T('notebook.title');
    const n = (k) => T(`notebook.node_${k}`);
    return `<section class="sig notebook" aria-labelledby="notebook-title"><div class="wrap">
  <div class="sig-head"><h2 id="notebook-title"${t.a}>${t.h}</h2></div>
  <div class="notebook-grid">
  <div class="schematic">
    <svg viewBox="0 0 720 300" role="img" aria-label="${esc(t.p)}">
      <path class="wire" d="M170 60 C 230 60, 230 150, 290 150" pathLength="1"/><path class="wire" d="M170 150 L290 150" pathLength="1"/><path class="wire" d="M170 240 C 230 240, 230 150, 290 150" pathLength="1"/>
      <path class="wire" d="M470 150 L540 150" pathLength="1"/>
      <path class="wire" d="M380 190 L380 240 L540 240" pathLength="1"/>
      ${[['Claude Code', 60], ['Codex', 150], ['DeepSeek', 240]].map(([name, y]) => `<rect class="node" x="30" y="${y - 24}" width="140" height="48" pathLength="1"/><text x="100" y="${y + 5}" text-anchor="middle">${name}</text>`).join('')}
      <rect class="node hot" x="290" y="110" width="180" height="80" pathLength="1"/>
      <text x="380" y="144" text-anchor="middle"${n('stream').a}><tspan x="380">One AgentEvent</tspan><tspan x="380" dy="22">stream</tspan></text>
      <rect class="node" x="540" y="120" width="150" height="60" pathLength="1"/>
      <text x="615" y="155" text-anchor="middle"${n('ui').a}>${n('ui').h}</text>
      <rect class="node" x="540" y="214" width="150" height="56" pathLength="1"/>
      <text class="small" x="615" y="238" text-anchor="middle"><tspan x="615">PRISM A/B</tspan><tspan x="615" dy="18">two worktrees, one prompt</tspan></text>
      <text class="small muted" x="100" y="282" text-anchor="middle"${n('agents').a}>${n('agents').h}</text>
    </svg>
    <p class="schematic-note"><span${n('race').a}>${n('race').h}</span> <em${n('compare').a}>${n('compare').h}</em></p>
  </div>
  <figure class="notebook-plate">${img(p.gallery[2], { root, alt: (p.shotAlts || [])[2] || '' })}<figcaption>${esc((p.shotAlts || [])[2] || '')}</figcaption></figure>
  </div>
</div></section>`;
  }
  return '';
}

// ── home: principal works (doors) ───────────────────────────────────────────────────────────
const door = (p, isOff = false) => {
  const title = P(p.slug, 'title'), sub = P(p.slug, 'subtitle'), st = status(p), tl = typeLine(p);
  return `<article class="door feature${off(isOff)}" data-slug="${p.slug}" data-room="${p.room || 'cabinet'}" style="${hue(p.beam)}">
  <a class="door-media" href="${href(p)}" tabindex="-1" aria-hidden="true">${doorModule(p)}</a>
  <div class="door-text">
    <p class="typeline"><i></i><span${tl.a}>${tl.h}</span></p>
    <h3><a href="${href(p)}"${title.a}>${title.h}</a></h3>
    <p class="sub"${sub.a}>${sub.h}</p>
    ${factList(p.doorFacts ? factRows(p.slug).filter((_, i) => p.doorFacts.includes(i)) : factRows(p.slug, [], 2), 'small')}
    <p class="access"><span class="plaque status"${st.a}>${st.h}</span>${accessLinks(p)}</p>
  </div>
</article>`;
};

// ── home: the register (ledger) ─────────────────────────────────────────────────────────────
// Row thumbnails are 96×60 boxes: use <cover>-thumb.webp when it exists, so the register doesn't pull full-size covers.
// Make one with: convert <cover>.webp -strip -resize '320x200^' -define webp:method=6 <cover>-thumb.webp
const thumbOf = (src) => { const t = src.replace(/\.webp$/, '-thumb.webp'); return t !== src && existsSync(join(SHOWCASE, t)) ? t : src; };
const row = (p) => {
  const title = P(p.slug, 'title'), sub = P(p.slug, 'subtitle'), st = status(p), tl = typeLine(p);
  const links = accessOf(p);
  const thumb = p.cover ? img(thumbOf(p.cover), { cls: 'row-thumb', alt: '' }) : '<span class="row-thumb blank" aria-hidden="true"></span>';
  return `<div class="row card${off(p.hidden)}" data-slug="${p.slug}" data-beam="${p.beam}" data-group="${tierOf(p)}" style="${hue(p.beam)}">
    <span class="row-tick" aria-hidden="true"></span>
    ${thumb}
    <span class="row-record"><a class="row-link" href="${href(p)}"${title.a}>${title.h}</a><em${sub.a}>${sub.h}</em></span>
    <span class="row-wing"><span${beamLabel(p.beam).a}>${beamLabel(p.beam).h}</span></span>
    <span class="row-stack">${(p.stack || []).slice(0, 3).map(esc).join(' · ')}</span>
    <span class="row-status"><span${st.a}>${st.h}</span></span>
    <span class="row-access">${links.length ? links.map((l) => `<a href="${esc(l.href)}">${T(accessLabel[l.kind]).h}${l.external ? arrow : ''}</a>`).join('') : `<span class="muted"${T('access.private').a}>${T('access.private').h}</span>`}</span>
  </div>`;
};

function ledger() {
  const groups = [['principal', 'work.group_principal'], ['records', 'work.group_records'], ['cabinet', 'work.group_cabinet']];
  const list = PREVIEW ? projects.filter((p) => !isTodo(p)) : shown;
  const drafting = projects.filter((p) => p.hidden && (isTodo(p) || !p.cover)).filter((p) => !/decree|sidepanel/.test(p.slug));
  const cols = ['work.col_record', 'work.col_wing', 'work.col_stack', 'work.col_status', 'work.col_access'].map((k) => T(k));
  return `<div class="ledger" id="grid" data-ledger>
  <div class="ledger-head" aria-hidden="true"><span></span><span></span>${cols.map((c) => `<span${c.a}>${c.h}</span>`).join('')}</div>
  ${groups.map(([g, key]) => { const rows = list.filter((p) => tierOf(p) === g); if (!rows.length) return ''; const gt = T(key); return `<div class="ledger-group" data-group="${g}"><h3${gt.a}>${gt.h}</h3>${rows.map(row).join('')}</div>`; }).join('\n')}
  ${drafting.length ? `<p class="drafting"><span${T('work.drafting').a}>${T('work.drafting').h}</span>: ${drafting.map((p) => inline(work(p.slug, 'title'))).join(' · ')}</p>` : ''}
</div>`;
}

// ── home: skills from the stacks of the shown projects, each linked to its evidence ─────────
function skills() {
  const list = (data.skills || []).map(([s, slug]) => [s, bySlug[slug]]).filter(([, p]) => p && shown.includes(p));
  return `<ul class="skills">${list.map(([s, p]) => `<li><a href="${href(p)}">${esc(s)}</a><small>${inline(work(p.slug, 'title'))}</small></li>`).join('')}</ul>`;
}

const GIGS = [['mc-plugin', 'minecraft'], ['mc-server', 'minecraft'], ['ios-app', 'apps'], ['web-tool', 'web'], ['ai-agents', 'ai'], ['linux-desktop', 'desktop']];
const tileFor = { 'steam-rewind': 'steamrewind', 'debt-clock': 'debtclock' };
const k = (key, vars) => T(key, vars);
const sec = (id, attrs, inner) => (hiddenSections.has(id) && !PREVIEW ? '' :
  `<section id="${id}" data-section="${id}" class="${off(hiddenSections.has(id)).trim()}" ${attrs}>${inner}</section>`);

const SECTIONS = {
  selected: () => sec('selected', 'aria-labelledby="selected-title"', `<div class="wrap">
  <div class="sec-head"><h2 id="selected-title"${k('selected.title_new').a}>${k('selected.title_new').h}</h2></div>
  <div class="doors">
  ${featuredSlugs.map((s) => door(bySlug[s])).join('\n')}
  ${PREVIEW ? projects.filter((p) => !featuredSlugs.includes(p.slug) && !isTodo(p) && !p.hidden).map((p) => door(p, true)).join('\n') : ''}
  </div>
</div>`),
  work: () => sec('work', 'aria-labelledby="work-title"', `<div class="wrap">
  <div class="sec-head"><h2 id="work-title"${k('work.title').a}>${k('work.title').h}</h2>
    <p class="count" role="status" data-count-template="${esc(site('work.count'))}" data-total="${shown.length}"${k('work.count').a}>${inline(site('work.count'), { shown: shown.length, total: shown.length })}</p></div>
  <div class="filters" role="group" aria-label="Filter by wing">
    <button class="chip" type="button" data-filter="all" aria-pressed="true" style="--h:var(--brass)"><i></i><span${k('work.filter_all').a}>${k('work.filter_all').h}</span></button>
    ${SPECTRUM.map((id) => { const b = beamLabel(id), n = shown.filter((p) => p.beam === id).length; return `<button class="chip" type="button" data-filter="${id}" aria-pressed="false" style="--h:var(--${id})"><i></i><span${b.a}>${b.h}</span><small>${n}</small></button>`; }).join('')}
  </div>
  ${ledger()}
</div>`),
  plate: () => sec('plate', 'aria-label="Plate"', `<div class="wrap">
  <figure class="plate" style="${hue('minecraft')}">
    ${img('assets/minecraft/server-dark-spire-1655.webp', { alt: 'A night view of a Minecraft server build: a giant hollow tree with lit windows on a stone plinth, a walled farm village, a cherry pagoda, a lit castle, a dark spire with beacon beams and a snowy ridge under a starry sky.', srcset: `${asset('assets/minecraft/server-dark-spire-860.webp')} 860w, ${asset('assets/minecraft/server-dark-spire-1655.webp')} 1655w`, sizesAttr: '(max-width: 900px) 100vw, 860px' })}
    <figcaption><i></i><span${k('plate.caption').a}>${k('plate.caption').h}</span> <a href="#minecraft" data-beam="minecraft">${beamLabel('minecraft').h}</a></figcaption>
  </figure>
</div>`),
  lenses: () => sec('lenses', 'aria-labelledby="lenses-title"', `<div class="wrap">
  <div class="sec-head"><div><h2 id="lenses-title"${k('lenses.title').a}>${k('lenses.title').h}</h2><p${k('lenses.intro').a}>${k('lenses.intro').h}</p></div></div>
  <div class="lenses">${lenses.map((l) => { const t = k(`lens.${l.id}.title`), d = k(`lens.${l.id}.blurb`); return `<a class="lens" href="${esc(l.href)}">
    ${img('assets/prismet/tiles/' + tileFor[l.id] + '.webp', { alt: '' })}
    <span class="lens-text"><span class="plaque live"${k('lenses.live').a}>${k('lenses.live').h}</span><h3${t.a}>${t.h}</h3><p${d.a}>${d.h}</p></span></a>`; }).join('')}</div>
</div>`),
  about: () => sec('about', 'aria-labelledby="about-title"', `<div class="wrap about">
  <div class="about-text">
    <h2 id="about-title"${k('about.title').a}>${k('about.title').h}</h2>
    <div class="prose">
      ${ABOUT_PARAS.map((key) => `<p${k(key).a}>${k(key).h}</p>`).join('\n      ')}
    </div>
    <h3 class="skills-title"${k('about.skills_title').a}>${k('about.skills_title').h}</h3>
    ${skills()}
  </div>
  <div class="hire" id="hire">
    <h3${k('hire.title').a}>${k('hire.title').h}</h3>
    <ul class="hire-list">${GIGS.map(([id, h]) => { const t = k(`hire.${id}.title`), s = k(`hire.${id}.sub`); return `<li style="--h:var(--${h})"><i></i><span><span${t.a}>${t.h}</span><small${s.a}>${s.h}</small></span></li>`; }).join('')}</ul>
    <h4${k('hire.doors_title').a}>${k('hire.doors_title').h}</h4>
    <ul class="doors-list">
      <li><a href="${esc(owner.linkedin)}"><strong>LinkedIn</strong><span${k('hire.door_linkedin').a}>${k('hire.door_linkedin').h}</span>${arrow}</a></li>
      <li><a href="${esc(owner.github)}"><strong>GitHub</strong><span${k('hire.door_github').a}>${k('hire.door_github').h}</span>${arrow}</a></li>
      <li><a href="${esc(owner.fiverr)}"><strong>Fiverr</strong><span${k('hire.door_fiverr').a}>${k('hire.door_fiverr').h}</span>${arrow}</a></li>
    </ul>
    <a class="btn primary" href="${esc(owner.fiverr)}"${k('hire.cta').a}>${k('hire.cta').h}</a>
  </div>
</div>`),
};

// The editor starts from this; "apply my edits" writes its changes back into projects.json.
const layoutState = {
  sections: sectionOrder, hiddenSections: [...hiddenSections],
  order: projects.map((p) => p.slug), featured: featuredSlugs,
  wide: [], hidden: projects.filter((p) => p.hidden).map((p) => p.slug),
  names: Object.fromEntries(projects.map((p) => [p.slug, plain(work(p.slug, 'title'))])),
};

const indexBody = `${bar()}
<main id="main">
<section class="entrance" aria-labelledby="hero-title"><div class="wrap">
  <div class="entrance-text">
    <p class="plaque brass"${k('hero.eyebrow').a}>${k('hero.eyebrow').h}</p>
    <p class="salute"${k('hero.title').a}>${k('hero.title').h}</p>
    <h1 id="hero-title">${esc(owner.name)}</h1>
    <p class="lede"${k('hero.lede').a}>${k('hero.lede').h}</p>
    <dl class="directory">
      <div><dt${k('hero.directory_source').a}>${k('hero.directory_source').h}</dt><dd><a href="${esc(owner.github)}">github.com/sagedeutschle${arrow}</a></dd></div>
      <div><dt${k('hero.directory_contact').a}>${k('hero.directory_contact').h}</dt><dd><a href="${esc(owner.linkedin)}">LinkedIn${arrow}</a></dd></div>
      <div><dt${k('hero.directory_commissions').a}>${k('hero.directory_commissions').h}</dt><dd><a href="${esc(owner.fiverr)}">Fiverr${arrow}</a></dd></div>
    </dl>
    <div class="ctas"><a class="btn primary" href="#work"${k('hero.cta_primary').a}>${k('hero.cta_primary').h}</a><a class="btn" href="${esc(owner.fiverr)}"${k('hero.cta_secondary').a}>${k('hero.cta_secondary').h}</a></div>
  </div>
  ${plan()}
</div></section>
${sectionOrder.map((id) => SECTIONS[id]()).join('\n')}
</main>
${footer()}
${PREVIEW ? `<script type="application/json" id="bz-layout">${JSON.stringify(layoutState).replace(/</g, '\\u003c')}</script>` : ''}
${scripts()}`;

writeFileSync(join(DIST, 'index.html'), `<!doctype html><html lang="en"><head>${head({ title: plain(site('page.title')), desc: plain(site('page.description')), url: '' })}</head><body>${indexBody}</body></html>`);

// ── project pages ───────────────────────────────────────────────────────────────────────────
const plates = (p) => {
  const gal = (p.gallery || []);
  if (!gal.length) return '';
  const t = T('project.plates');
  return `<section class="plates" aria-labelledby="plates-title"><div class="wrap">
  <h2 id="plates-title"${t.a}>${t.h}</h2>
  <div class="plate-grid">${gal.map((g, n) => { const { w, h } = sizeOf(join(SHOWCASE, g)); const alt = (p.shotAlts && p.shotAlts[n]) || `${plain(work(p.slug, 'title'))}, plate ${n + 1}`; return `<figure class="${h > w ? 'tall' : w / h > 2.2 ? 'wide' : ''}">${img(g, { root: '../', alt, lazy: n > 1 })}<figcaption>${esc(alt)}</figcaption></figure>`; }).join('')}</div>
</div></section>`;
};

const threads = (p) => {
  const rel = (p.related || []).map((s) => bySlug[s]).filter((q) => q && shown.includes(q));
  if (!rel.length) return '';
  const t = T('project.threads');
  return `<div class="threads"><h2${t.a}>${t.h}</h2><ul>${rel.map((q) => `<li><a href="${q.slug}.html" style="${hue(q.beam)}"><i></i>${inline(work(q.slug, 'title'))}<small>${inline(work(q.slug, 'subtitle'))}</small></a></li>`).join('')}</ul></div>`;
};

shown.forEach((p, i) => {
  const b = beamLabel(p.beam), s = p.slug;
  const next = shown[(i + 1) % shown.length], prev = shown[(i - 1 + shown.length) % shown.length];
  const title = P(s, 'title'), sub = P(s, 'subtitle'), st = status(p), sum = P(s, 'summary');
  const hl = listItems(work(s, 'highlights')), hlT = T('project.highlights');
  const roleK = T('project.role_label'), yearK = T('project.year_label');
  const isFlag = p.tier === 'flagship' || p.tier === 'featured';
  const front = !isFlag && p.cover ? `<figure class="frontispiece${p.cover.includes('/icons/') ? ' icon' : ''}"><div class="wrap">${img(p.cover, { root: '../', alt: (p.shotAlts || [])[0] || plain(work(s, 'title')), lazy: false, priority: true })}</div></figure>` : '';
  const body = `${bar('../')}
<main id="main" data-room="${p.room || 'cabinet'}" data-beam="${p.beam}" style="${hue(p.beam)}">
  <div class="wrap p-head">
    <p class="crumbs"><a href="../index.html#work">${T('nav.work').h}</a> <span>/</span> <a href="../index.html#${p.beam}">${b.h}</a></p>
    <h1${title.a}>${title.h}</h1>
    <p class="sub"${sub.a}>${sub.h}</p>
    <div class="row"><span class="plaque status"${st.a}>${st.h}</span>${accessLinks(p, '../', true).replace(/class="link"/g, 'class="btn"')}</div>
  </div>
  ${isFlag ? signature(p) : front}
  <div class="wrap p-body">
    <div class="prose">
      <div class="summary${isTodo(p) ? ' todo' : ''}"${sum.a}>${sum.h.split(/\n\s*\n/).map((para) => `<p>${para}</p>`).join('')}</div>
      ${hl.length ? `<h2${hlT.a}>${hlT.h}</h2><ul class="hl">${hl.map((h, n) => `<li${ed(`work.${s}.highlights.${n}`, h)}>${inline(h)}</li>`).join('')}</ul>` : ''}
    </div>
    <aside>
      ${factList(factRows(s, [
        { k: roleK, v: { a: ed(`work.${s}.role`, work(s, 'role')), h: inline(work(s, 'role')) } },
        { k: yearK, v: { a: ed(`work.${s}.year`, work(s, 'year')), h: inline(work(s, 'year')) } },
      ]))}
      <div class="stack" aria-label="Stack">${p.stack.map((x) => `<span>${esc(x)}</span>`).join('')}</div>
      ${threads(p)}
    </aside>
  </div>
  ${isFlag ? (p.rack || p.eras ? '' : plates(p)) : (p.gallery || []).length > 1 ? plates({ ...p, gallery: p.gallery.slice(1), shotAlts: (p.shotAlts || []).slice(1) }) : ''}
  <nav class="wrap next" aria-label="Register">
    <a href="${prev.slug}.html"><small>← ${T('project.previous').h}</small><strong>${inline(work(prev.slug, 'title'))}</strong></a>
    <a class="back" href="../index.html#work"${T('project.back').a}>${T('project.back').h}</a>
    <a href="${next.slug}.html" style="text-align:right"><small>${T('project.next').h} →</small><strong>${inline(work(next.slug, 'title'))}</strong></a>
  </nav>
</main>
${footer('../')}
${scripts('../')}`;
  writeFileSync(join(DIST, 'work', s + '.html'), `<!doctype html><html lang="en"><head>${head({ title: `${plain(work(s, 'title'))} · Prismet`, desc: plain(work(s, 'subtitle')), root: '../', url: `work/${s}.html` })}</head><body>${body}</body></html>`);
});

// Hidden records keep a one-line stub at their old URL, so links from elsewhere don't 404.
projects.filter((p) => !shown.includes(p)).forEach((p) => {
  const s = p.slug, w = (isTodo(p) || !p.cover) && !/decree|sidepanel/.test(s) ? T('project.drafting_stub') : T('project.withdrawn');
  const body = `${bar('../')}
<main id="main" class="withdrawn"><div class="wrap">
  <h1>${inline(work(s, 'title'))}</h1>
  <p${w.a}>${w.h}</p>
  <p><a class="btn" href="../index.html#work"${T('project.back').a}>${T('project.back').h}</a></p>
</div></main>
${footer('../')}`;
  writeFileSync(join(DIST, 'work', s + '.html'), `<!doctype html><html lang="en"><head>${head({ title: `${plain(work(s, 'title'))} · Prismet`, desc: w.p, root: '../', noindex: true })}</head><body>${body}</body></html>`);
});

// ── colophon ────────────────────────────────────────────────────────────────────────────────
{
  const t = T('colophon.title');
  const paras = Object.keys(S).filter((key) => /^colophon\.p\d+$/.test(key)).sort((a, b) => a.slice(10) - b.slice(10));
  const body = `${bar()}
<main id="main" class="colophon"><div class="wrap">
  <h1${t.a}>${t.h}</h1>
  <div class="prose">${paras.map((key) => `<p${k(key).a}>${k(key).h}</p>`).join('')}</div>
  <dl class="facts">
    <div><dt>Type</dt><dd>Newsreader · Martian Mono</dd></div>
    <div><dt>Licence</dt><dd>SIL Open Font License 1.1</dd></div>
    <div><dt>Requests to other sites</dt><dd>0</dd></div>
    <div><dt>Records</dt><dd>${shown.length}</dd></div>
  </dl>
  <p><a class="btn" href="index.html#work"${T('project.back').a}>${T('project.back').h}</a></p>
</div></main>
${footer()}`;
  writeFileSync(join(DIST, 'colophon.html'), `<!doctype html><html lang="en"><head>${head({ title: `${t.p} · Prismet`, desc: plain(site('colophon.p1')), url: 'colophon.html' })}</head><body>${body}</body></html>`);
}

// ── artifact preview: the same page as a fragment (the artifact host supplies <head>/<body>) ─
if (PREVIEW) {
  writeFileSync(join(DIST, '_preview.html'), `<title>Prismet Workshop</title>
${GOOGLE_FONTS}<link rel="stylesheet" href="editor.css"><link rel="stylesheet" href="site.css">
<script src="site.js"></script>
${indexBody}`);
}
if (missing.size) {
  console.error('✗ missing wording (the key would show on the page):\n  ' + [...missing].join('\n  '));
  process.exit(1);
}
const total = [...copied.values()].reduce((n, p) => n + statSync(join(DIST, p)).size, 0);
console.log(`built ${shown.length} project pages + ${projects.length - shown.length} withdrawn stubs + index + colophon → ${DIST}${PREVIEW ? ' (with editor)' : ''}; ${copied.size} images, ${(total / 1024).toFixed(0)} KB`);
