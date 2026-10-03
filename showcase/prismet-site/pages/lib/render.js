// render.js — turns the site's words and data into HTML. Pure: no filesystem, no Node APIs, so the same code
// runs in the build (build.mjs), in the editor's live preview (pages/edit.js, in the browser) and in tests.
//
//   renderSite({ data, site, work, assets, urls, preview }) → { pages, missing, shown, projects, layoutState }
//
//   data     data/projects.json, parsed
//   site     content/site.md as { key: text }
//   work     { slug: content/work/<slug>.md as { key: text } }
//   assets   { size(p) → { w, h }, has(p) → boolean, url(p) → the URL to write for p (throws when p is missing) };
//            p is a path under showcase/, such as assets/minecraft/x.webp
//   urls     { css, js, og }: the URLs of site.css, site.js and the share image
//   styles   data/styles.json, parsed (optional): the wording it styles is marked so its rules can find it
//   pages    data/pages.json, parsed (optional): sections added to the home page and whole pages, built from the
//            section library (sections.js). drafts: true also renders pages still marked draft (the editor's preview)
//   preview  true for the artifact preview: every piece of wording carries its key (data-edit), plus its own editor
//   edit     true for the editor at /edit: wording carries its key and hidden sections and records stay on the page
//            (class is-off), so they can be selected and brought back
//
// pages maps a path in the site (index.html, work/<slug>.html, colophon.html) to its HTML. missing lists content keys
// a page asked for and did not find.
import { inline, plain, listItems, factPairs } from './format.js';
import { styledKeys } from './styles.js';
import { renderSection, validSlug } from './sections.js';

// Spectrum order, red deviates least: desktop, apps, worlds, minecraft, web, ai.
export const SPECTRUM = ['desktop', 'apps', 'worlds', 'minecraft', 'web', 'ai'];
export const SECTION_IDS = ['selected', 'work', 'plate', 'lenses', 'about'];

export function renderSite({ data, site: S, work: W, assets, urls, styles = null, pages: PAGES = null, drafts: DRAFTS = false, preview: PREVIEW = false, edit: EDIT = false }) {
const ANNOTATE = PREVIEW || EDIT;
// The body below is not indented: its template literals carry the pages' own whitespace.
const { size: sizeOf, has, url: asset } = assets;
const { css: CSS_URL, js: JS_URL, og: OG_URL } = urls;
const pages = new Map();
const emit = (path, html) => pages.set(path, html);
const { owner, beams, lenses, projects } = data;
const beamById = Object.fromEntries(beams.map((b) => [b.id, b]));

// ── words ───────────────────────────────────────────────────────────────────────────────────
const missing = new Set();
const site = (key) => { if (!(key in S)) { missing.add(`content/site.md → ## ${key}`); return key; } return S[key]; };
const ABOUT_PARAS = Object.keys(S).filter((key) => /^about\.p\d+$/.test(key)).sort((a, b) => a.slice(7) - b.slice(7));
const work = (slug, f) => { const v = W[slug][f]; if (v === undefined) { missing.add(`content/work/${slug}.md → ## ${f}`); return ''; } return v; };
const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
// In --preview every piece of wording carries its key, so the page editor can save edits back.
// On the site only wording that data/styles.json styles is marked (data-s), so its rules can find it.
const STYLED = styledKeys(styles);
const ed = (key, raw) => (ANNOTATE ? ` data-edit="${esc(key)}" data-src="${esc(raw)}"` : STYLED.has(key) ? ` data-s="${esc(key)}"` : '');
const T = (key, vars) => ({ a: ed(key, site(key)), h: inline(site(key), vars), p: plain(site(key), vars) });
const P = (slug, f) => ({ a: ed(`work.${slug}.${f}`, work(slug, f)), h: inline(work(slug, f)), p: plain(work(slug, f)) });
const isTodo = (p) => p.todo === true;

// ── images: every <img> carries width/height; srcset comes from the variants the assets report ─
// Responsive variants are <name>-<w>.webp next to the source (showcase/tools/image-variants.mjs writes them; the build
// encodes nothing). Each one narrower than the source joins srcset at its real width.
const LADDER = [128, 192, 360, 720, 1080, 1440];
const variantsOf = (p) => {
  const { w } = sizeOf(p);
  return LADDER.map((n) => p.replace(/\.webp$/, `-${n}.webp`)).filter((v) => v !== p && has(v))
    .map((v) => ({ v, w: sizeOf(v).w })).filter((x) => x.w < w);
};
// sizes = how wide the image is drawn, in CSS px. .wrap is at most 1240px with clamp(16px, 4vw, 56px) gutters, so its
// content is 100vw − 32px, then 92vw, then 1128px. Lazy images get sizes="auto" first: Chromium replaces it with the
// laid-out width; other browsers skip it and use the list after it (WRAP when the caller doesn't say: never too small).
const WRAP = '(max-width: 400px) calc(100vw - 32px), (max-width: 1240px) 92vw, 1128px';
const SIZES_BY_PATH = [[/-thumb\.webp$/, '(max-width: 900px) 72px, 96px'], [/\/tiles\//, '72px']];
const sizesFor = (p) => (SIZES_BY_PATH.find(([re]) => re.test(p)) || [null, WRAP])[1];
// The inside of a door frame (two doors across above 760px, 3vw apart) times k, for modules drawn at a share of it.
const DOOR = (k) => `(max-width: 400px) calc(${k} * (100vw - 34px)), (max-width: 760px) calc(${k} * (92vw - 2px)), (max-width: 1240px) calc(${k} * (44.5vw - 2px)), ${Math.round(541 * k)}px`;
/** <img> with width/height from the file, lazy by default. root = '' on the home page, '../' on project pages.
 *  srcset comes from the variants on disk unless given; sizesAttr says how wide the image is drawn. */
function img(p, { alt = '', root = '', cls = '', lazy = true, sizesAttr = '', srcset = '', priority = false } = {}) {
  const out = asset(p), { w, h } = sizeOf(p);
  const vs = srcset ? [] : variantsOf(p);
  if (vs.length) {
    srcset = [...vs.map((x) => `${root}${asset(x.v)} ${x.w}w`), `${root}${out} ${w}w`].join(', ');
    sizesAttr = `${lazy ? 'auto, ' : ''}${sizesAttr || sizesFor(p)}`;
  }
  return `<img${cls ? ` class="${cls}"` : ''} src="${root}${out}"${srcset ? ` srcset="${srcset}" sizes="${sizesAttr}"` : ''} width="${w}" height="${h}" alt="${esc(alt)}"${lazy ? ' loading="lazy" decoding="async"' : priority ? ' fetchpriority="high"' : ''}>`;
}
// favicon: the app icon, at 128px when that variant exists (the 512px original is 15 KB on every first view)
const FAVICON = asset(['assets/icons/prismet-app-128.webp', 'assets/icons/prismet-app.webp'].find((p) => has(p)));

// ── shared pieces ───────────────────────────────────────────────────────────────────────────
const hue = (id) => `--h:var(--${id});--hi:var(--${id}-ink)`;
const arrow = '<svg class="ext" viewBox="0 0 12 12" width="11" height="11" aria-hidden="true"><path d="M3 9.5 9.5 3M4.5 3h5v5" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';
// The artifact preview can't load fonts from its own files, so it uses Google Fonts; production self-hosts.
const GOOGLE_FONTS = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Unbounded:wght@200..900&family=Hanken+Grotesk:wght@100..900&family=Martian+Mono:wdth,wght@75..112.5,100..800&display=swap">';
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
<meta property="og:image" content="${OG_URL}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="${esc(plain(site('og.image_alt')))}">
<meta property="og:type" content="website">
<meta name="twitter:card" content="summary_large_image">${url ? `\n<meta property="og:url" content="https://prismet.xyz/${esc(url)}">` : ''}
<meta name="theme-color" content="#0F161D" media="(prefers-color-scheme: dark)">
<meta name="theme-color" content="#E4E8EA" media="(prefers-color-scheme: light)">
<link rel="icon" href="${root}${FAVICON}">
<link rel="preload" href="${root}assets/fonts/HankenGrotesk.woff2" as="font" type="font/woff2" crossorigin>
<link rel="preload" href="${root}assets/fonts/Unbounded.woff2" as="font" type="font/woff2" crossorigin>
${PREVIEW ? GOOGLE_FONTS + '\n' : ''}<link rel="stylesheet" href="${root}${CSS_URL}">${PREVIEW ? `\n<link rel="stylesheet" href="${root}editor.css">` : ''}
<script src="${root}${JS_URL}"></script>`;
const scripts = (root = '') => (PREVIEW ? `<script src="${root}editor.js"></script>` : '');
// In the artifact preview the home page is the artifact itself, so links to it point at the folder, not index.html.
const HOME = (root = '') => (PREVIEW ? (root || './') : `${root}index.html`);

const bar = (root = '') => {
  const [w, l, a, h, g, li] = ['nav.work', 'nav.lenses', 'nav.about', 'nav.hire', 'nav.github', 'nav.linkedin'].map((k) => T(k));
  return `<a class="skip" href="#main">Skip to content</a>
<header class="bar"><div class="wrap">
  <a class="brand" href="${HOME(root)}">${mark(28)}<strong>Prismet</strong><span>${esc(owner.name)}</span></a>
  <nav class="nav" aria-label="Main">
    <a class="keep" href="${HOME(root)}#work"${w.a}>${w.h}</a>
    <a href="${HOME(root)}#lenses"${l.a}>${l.h}</a>
    <a href="${HOME(root)}#about"${a.a}>${a.h}</a>${navPages(root)}
    <a class="wide" href="${esc(owner.github)}"${g.a}>${g.h}</a>
    <a class="wide" href="${esc(owner.linkedin)}"${li.a}>${li.h}</a>
    <a class="btn primary keep" href="${esc(owner.fiverr)}"${h.a}>${h.h}</a>
    <button class="btn theme keep" type="button" id="theme-toggle" aria-label="${esc(T('footer.day_night').p)}" data-to-day="${esc(T('theme.to_day').p)}" data-to-night="${esc(T('theme.to_night').p)}"><span class="sun" aria-hidden="true"></span></button>
  </nav>
</div></header>`;
};

const footer = (root = '') => `<footer><div class="wrap">
  <span${T('footer.copyright').a}>${T('footer.copyright').h}</span>
  <nav aria-label="Footer"><a href="${HOME(root)}#work">${T('nav.work').h}</a><a href="${HOME(root)}#lenses">${T('nav.lenses').h}</a><a href="${HOME(root)}#about">${T('nav.about').h}</a>${navPages(root)}<a href="${root}colophon.html"${T('footer.colophon').a}>${T('footer.colophon').h}</a><a href="${esc(owner.github)}">GitHub</a><a href="${esc(owner.linkedin)}">LinkedIn</a><a href="${esc(owner.fiverr)}">Fiverr</a></nav>
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
// Library sections added to the home page (data/pages.json → home) take part in the same order and hiding as the built-in ones.
const HOME_CUSTOM = (PAGES?.home || []).filter((s) => s && typeof s.id === 'string' && !SECTION_IDS.includes(s.id));
const ALL_IDS = [...SECTION_IDS, ...HOME_CUSTOM.map((s) => s.id)];
const sectionOrder = [...(L.sections || []).filter((id) => ALL_IDS.includes(id)), ...ALL_IDS.filter((id) => !(L.sections || []).includes(id))];
// Pages made at /edit. A draft is not built; a hidden page is built but kept out of the navigation and of search.
const CUSTOM = (PAGES?.pages || []).filter((p) => p && validSlug(p.slug) && (ANNOTATE || DRAFTS || p.status !== 'draft'));
const NAV_PAGES = CUSTOM.filter((p) => p.nav && p.status === 'published');
const navPages = (root) => NAV_PAGES.map((p) => `<a href="${root}${p.slug}.html">${esc(p.navLabel || p.title || p.slug)}</a>`).join('');
const hiddenSections = new Set(L.hiddenSections || []);
const shown = projects.filter((p) => !p.hidden && !isTodo(p));
const featuredSlugs = (data.featuredOrder || []).filter((s) => bySlug[s] && shown.includes(bySlug[s]));
const off = (isOff) => (isOff ? ' is-off' : '');
const tierOf = (p) => (featuredSlugs.includes(p.slug) ? 'principal' : p.tier === 'cabinet' ? 'cabinet' : 'records');
const href = (p, root = '') => `${root}work/${p.slug}.html`;

// ── the hall plan (hero + navigation): six wings around a rotunda, the prism in its floor ────
function plan() {
  // Surveyed, not sketched: every line ends on the surface it meets. Corridor walls start on the rotunda's circle and
  // stop at the room's inner wall, which opens a doorway between them; the white beam runs on the cross axis from the
  // inner ring to the prism's face; the beacon runs north from the rotunda to the court wall.
  const W_ = 720, H_ = 540, cx = 360, cy = 270, R = 78, Ri = 60, half = 7;
  const count = (id) => shown.filter((p) => p.beam === id).length;
  const rooms = { desktop: [48, 60], apps: [48, 216], worlds: [48, 372], minecraft: [472, 60], web: [472, 216], ai: [472, 372] };
  const rw = 200, rh = 108;
  const f = (v) => String(Math.round(v * 10) / 10);
  const wings = SPECTRUM.map((id, i) => {
    const [rx, ry] = rooms[id], left = rx < cx;
    const ex = left ? rx + rw : rx, ey = ry + rh / 2;          // the room's inner wall, at its middle
    const dx = ex - cx, dy = ey - cy, len = Math.hypot(dx, dy), ux = dx / len, uy = dy / len, nx = -uy, ny = ux;
    // A wall `half` either side of the corridor's axis: from the rotunda's circle to the room's inner wall (x = ex).
    const wall = (s) => {
      const fx = cx + nx * s * half, fy = cy + ny * s * half, t0 = Math.sqrt(R * R - half * half), t1 = (ex - fx) / ux;
      return [fx + ux * t0, fy + uy * t0, ex, fy + uy * t1];
    };
    const [a, b] = [wall(1), wall(-1)], top = Math.min(a[3], b[3]), bot = Math.max(a[3], b[3]);
    // The room's walls, open where the corridor comes in.
    const room = left
      ? `M${ex} ${f(bot)}V${ry + rh}H${rx}V${ry}H${ex}V${f(top)}`
      : `M${ex} ${f(top)}V${ry}H${rx + rw}V${ry + rh}H${ex}V${f(bot)}`;
    const n = count(id), label = beamLabel(id), lead = shown.find((p) => p.beam === id);
    return `<a class="wing" href="#work" data-beam="${id}" style="--i:${i}" aria-label="${esc(label.p)}: ${n} record${n === 1 ? '' : 's'}">
      <path class="wall" d="M${f(a[0])} ${f(a[1])}L${f(a[2])} ${f(a[3])}" pathLength="1"/>
      <path class="wall" d="M${f(b[0])} ${f(b[1])}L${f(b[2])} ${f(b[3])}" pathLength="1"/>
      <path class="inlay" d="M${f(cx + ux * R)} ${f(cy + uy * R)}L${ex} ${ey}" stroke="var(--${id})" pathLength="1"/>
      <rect class="room-fill" x="${rx}" y="${ry}" width="${rw}" height="${rh}"/>
      <path class="room" d="${room}" pathLength="1"/>
      <text class="room-name" x="${rx + rw / 2}" y="${ry + 46}" text-anchor="middle"${ed(`beam.${id}`, site(`beam.${id}`))}>${esc(label.p)}</text>
      <text class="room-lead" x="${rx + rw / 2}" y="${ry + 74}" text-anchor="middle">${lead ? esc(plain(work(lead.slug, 'title'))) + (n > 1 ? ` + ${n - 1}` : '') : ''}</text>
    </a>`;
  }).join('');
  // Four piers carry the dome, standing in the ambulatory between the two rings on the diagonals, clear of every corridor.
  const pd = (R + Ri) / 2 / Math.SQRT2;
  const piers = [[-1, -1], [1, -1], [-1, 1], [1, 1]].map(([sx, sy]) => `<rect class="pier" x="${f(cx + sx * pd - 4.5)}" y="${f(cy + sy * pd - 4.5)}" width="9" height="9" pathLength="1"/>`).join('');
  // The prism: an equilateral triangle centred on the rotunda; the beam meets its left face on the cross axis.
  const ph = 54, pw = 31, apex = cy - ph * 2 / 3, base = cy + ph / 3, face = cx - pw * (cy - apex) / ph;
  return `<figure class="plan" id="plan">
<svg viewBox="0 0 ${W_} ${H_}" role="group" aria-label="${esc(T('plan.caption').p)}">
  <g class="g-court"><rect class="court" x="24" y="24" width="${W_ - 48}" height="${H_ - 48}" pathLength="1"/><rect class="court inner" x="34" y="34" width="${W_ - 68}" height="${H_ - 68}" pathLength="1"/></g>
  <g class="g-rotunda">
    <path class="beacon" d="M${cx} ${cy - R}V34" pathLength="1"/>
    <circle class="rotunda" cx="${cx}" cy="${cy}" r="${R}" pathLength="1"/>
    <circle class="rotunda inner" cx="${cx}" cy="${cy}" r="${Ri}" pathLength="1"/>
    ${piers}
    <path class="prism" d="M${cx} ${f(apex)}L${cx + pw} ${f(base)}L${cx - pw} ${f(base)}Z" pathLength="1"/>
    <path class="beam-in" d="M${cx - Ri} ${cy}H${f(face)}" pathLength="1"/>
  </g>
  <g class="g-wings">${wings}</g>
</svg>
<div class="lantern" aria-hidden="true"></div>
<figcaption>
  <span${T('plan.caption').a}>${T('plan.caption').h}</span>
  <ul class="wing-list" aria-label="Wings">${SPECTRUM.map((id) => { const b = beamLabel(id), n = count(id); return `<li><a href="#work" data-beam="${id}" style="${hue(id)}" aria-label="${esc(b.p)}: ${n} record${n === 1 ? '' : 's'}"><i></i><span${b.a}>${b.h}</span><small>${(() => { const lead = shown.find((p) => p.beam === id); return lead ? esc(plain(work(lead.slug, 'title'))) + (n > 1 ? ` + ${n - 1}` : '') : ''; })()}</small></a></li>`; }).join('')}</ul>
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
    case 'arcade':     // two rows of three: the whole wall would not fit a door, and a hidden third row only costs requests
      return `<div class="mod tile-mosaic" aria-hidden="true">${(p.tiles || []).slice(0, 6).map((t) => img(tileSrc(t), { root, alt: '' })).join('')}</div>`;
    case 'bridge': {   // two justified rows: columns in proportion to each face, so every row is one height and no frame is cropped
      const face = (n) => `assets/helm2/${n}.webp`, ratio = (n) => { const { w, h } = sizeOf(face(n)); return (w / h).toFixed(3); };
      return `<div class="mod face-quad" aria-hidden="true">${[['chronos', 'net'], ['cpu', 'gpu']].map((r) => `<div class="face-row" style="grid-template-columns:${r.map((n) => `${ratio(n)}fr`).join(' ')}">${r.map((n) => img(face(n), { root, alt: '' })).join('')}</div>`).join('')}</div>`;
    }
    case 'notebook':
      return `<figure class="mod plate-door">${img(p.doorCover || g[2] || p.cover, { root, alt: (p.shotAlts || [])[2] || '' })}</figure>`;
    default:
      return p.cover ? `<figure class="mod plate-door">${img(p.cover, { root, alt: '' })}</figure>` : '';
  }
}

// ── the bench's drawing: the tower in elevation, dimensioned from the command that built it ──
// Read from the step (/hcyl <pattern> <r> <h>  +  /hpyramid <pattern> <s>) and drawn with Bayzyl's native generator
// geometry (NativeShapeAdapter): a drum of true radius r + ½, so 2r + 1 blocks across, h layers high; a roof of s layers,
// 2s − 1 blocks at the eaves, one block in on each side per layer. The fine lines on the drum fall where the
// generator's circle steps back a block, which is why the built tower is fluted. One unit per block; chalk on stone.
function elevation(p) {
  const i = (p.steps || []).findIndex((st) => /\/hcyl\b/.test(st.command) && /\/hpyramid\b/.test(st.command));
  if (i < 0) return '';
  const [cyl, pyr = ''] = p.steps[i].command.split(/\s+\+\s+/);
  const c = cyl.match(/^(\/hcyl\s+\S+)\s+(\d+)\s+(\d+)\s*$/), y = pyr.match(/^(\/hpyramid\s+\S+)\s+(\d+)\s*$/);
  if (!c || !y) return '';
  const r = +c[2], h = +c[3], s = +y[2], u = 12, f = (v) => String(Math.round(v * 100) / 100);
  const rx = r + 0.5, half = rx * u;
  const depth = (x) => Math.floor(rx * Math.sqrt(Math.max(0, 1 - (x / rx) ** 2)));   // the front-most block of column x
  const eave = (k) => (s - 1 - k + 0.5) * u;                                          // half-width of roof layer k
  const wide = Math.max(half, eave(0)), ax = 18 + wide, roofTop = 16, top = roofTop + s * u, gy = top + h * u;
  const dx = ax + wide + 26, W = dx + 34, H = gy + 46, dy = gy + 22;
  const tick = (x, yy) => `M${f(x - 4)} ${f(yy + 4)}L${f(x + 4)} ${f(yy - 4)}`;
  let roof = `M${f(ax - eave(0))} ${top}`;
  for (let k = 0; k < s; k++) { roof += `V${top - (k + 1) * u}`; if (k < s - 1) roof += `H${f(ax - eave(k + 1))}`; }
  roof += `H${f(ax + eave(s - 1))}`;
  for (let k = s - 1; k >= 0; k--) { roof += `V${top - k * u}`; if (k > 0) roof += `H${f(ax + eave(k - 1))}`; }
  roof += 'Z';
  const joints = Array.from({ length: s - 1 }, (_, j) => `M${f(ax - eave(j + 1))} ${top - (j + 1) * u}H${f(ax + eave(j + 1))}`).join('');
  const flutes = Array.from({ length: r }, (_, x) => x).filter((x) => depth(x) !== depth(x + 1))
    .flatMap((x) => [-1, 1].map((sg) => `M${f(ax + sg * (x + 0.5) * u)} ${top}V${gy}`)).join('');
  const cmd = (m) => `<code class="cmd">${esc(m[1]).replace(/,/g, ',<wbr>')} <b>${esc(m.slice(2).join(' '))}</b></code>`;
  const t = T('art.elevation_title', { n: i + 1 }), note = T('art.elevation_note'), alt = T('art.elevation_alt', { w: 2 * r + 1, h, s });
  return `<figure class="elevation">
      <svg viewBox="0 0 ${f(W)} ${f(H)}" role="img" aria-label="${esc(alt.p)}">
        <path class="axis" d="M${ax} ${roofTop - 12}V${dy + 8}"/>
        <path class="line" d="M${f(ax - half)} ${top}V${gy}H${f(ax + half)}V${top}"/>
        <path class="line" d="${roof}"/>
        <path class="fine" d="${joints}${flutes}"/>
        <path class="ground" d="M${f(ax - wide - 14)} ${gy}H${f(ax + wide + 14)}"/>
        <path class="dim" d="M${f(ax + half + 4)} ${gy}H${dx + 5}M${f(ax + wide + 4)} ${top}H${dx + 5}M${f(ax + eave(s - 1) + 4)} ${roofTop}H${dx + 5}M${dx} ${gy}V${roofTop}${tick(dx, gy)}${tick(dx, top)}${tick(dx, roofTop)}M${f(ax + half)} ${gy + 4}V${dy + 5}M${ax} ${dy}H${f(ax + half)}${tick(ax, dy)}${tick(ax + half, dy)}"/>
        <text x="${dx + 8}" y="${f((gy + top) / 2)}">${h}</text>
        <text x="${dx + 8}" y="${f((top + roofTop) / 2)}">${s}</text>
        <text x="${f(ax + half / 2)}" y="${dy + 17}" text-anchor="middle">r ${r}</text>
      </svg>
      <figcaption><span class="elev-title"${t.a}>${t.h}</span>${cmd(c)}${cmd(y)}<small${note.a}>${note.h}</small></figcaption>
    </figure>`;
}

function signature(p, root = '../') {
  const s = p.slug;
  if (p.steps) {
    const t = T('bench.title'), c = T('bench.caption');
    return `<section class="sig bench" aria-labelledby="bench-title">
  <div class="wrap">
    <div class="sig-head"><h2 id="bench-title"${t.a}>${t.h}</h2><p${c.a}>${c.h}</p></div>
    <div class="bench-grid">
    <div class="stepper" data-stepper>
      <ol class="steps">${p.steps.map((st, i) => `<li class="step"${i === p.steps.length - 1 ? ' aria-current="step"' : ''} data-step="${i}">
        ${img(`assets/bench/step-${i}.webp`, { root, alt: st.command ? `${st.title}: the test plaza after ${st.command}` : st.title, lazy: i !== p.steps.length - 1, priority: i === p.steps.length - 1 })}
        ${st.command ? `<p class="step-cmd"><code>${esc(st.command)}</code></p>` : ''}
        <p class="step-title"><span class="step-n">${i + 1}/${p.steps.length}</span> ${esc(st.title)}</p>
      </li>`).join('')}</ol>
      <div class="step-nav"><button type="button" class="btn" data-prev aria-label="${esc(T('bench.previous').p)}"><svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true"><path d="M8 1.5 3.5 6 8 10.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg></button><output aria-live="polite">${esc(T('bench.step').p)} ${p.steps.length} / ${p.steps.length}</output><button type="button" class="btn" data-next aria-label="${esc(T('bench.next').p)}"><svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true"><path d="M4 1.5 8.5 6 4 10.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg></button></div>
    </div>
    ${elevation(p)}
    </div>
  </div>
</section>`;
  }
  if (p.tiles) {
    const t = T('arcade.title'), oh = T('arcade.opens_here');
    const lensHref = { steamrewind: '/steam', debtclock: '/debt' };
    return `<section class="sig arcade" aria-labelledby="arcade-title"><div class="wrap">
  <div class="sig-head"><h2 id="arcade-title"${t.a}>${t.h}</h2></div>
  <ul class="tiles">${p.tiles.map((id) => { const name = p.tileNames[id] || id, lens = lensHref[id]; const inner = `${img(tileSrc(id), { root, alt: '' })}<span>${esc(name)}</span>${lens ? `<small${oh.a}>${oh.h}</small>` : ''}`; return id === 'wordle' ? wordTile(inner) : `<li${lens ? ' class="lens-tile"' : ''}>${lens ? `<a href="${lens}">${inner}</a>` : inner}</li>`; }).join('')}</ul>
</div></section>`;
  }
  if (p.rack) {
    const t = T('rack.title');
    const groups = ['horizon', 'telemetry', 'wit', 'systems', 'arcade'];
    const alts = Object.fromEntries((p.gallery || []).map((g, i) => [g.split('/').pop().replace(/\.webp$/, ''), (p.shotAlts || [])[i] || '']));
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
      <path class="wire" d="M470 150 L525 150" pathLength="1"/>
      <path class="wire" d="M380 190 L380 242 L525 242" pathLength="1"/>
      ${[['Claude Code', 60], ['Codex', 150], ['DeepSeek', 240]].map(([name, y]) => `<rect class="node" x="30" y="${y - 24}" width="140" height="48" pathLength="1"/><text x="100" y="${y + 5}" text-anchor="middle">${name}</text>`).join('')}
      <rect class="node hot" x="290" y="110" width="180" height="80" pathLength="1"/>
      <text x="380" y="144" text-anchor="middle"${n('stream').a}><tspan x="380">One AgentEvent</tspan><tspan x="380" dy="22">stream</tspan></text>
      <rect class="node" x="525" y="120" width="180" height="60" pathLength="1"/>
      <text x="615" y="155" text-anchor="middle"${n('ui').a}>${n('ui').h}</text>
      <rect class="node" x="525" y="214" width="180" height="56" pathLength="1"/>
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
const thumbOf = (src) => { const t = src.replace(/\.webp$/, '-thumb.webp'); return t !== src && has(t) ? t : src; };
// data-seek: what the seek line matches besides the visible title, subtitle, wing and status (projects.json `aliases`,
// the full stack, the type line, the wing's short name). data-related: the record's Threads, lit on hover or focus.
const seekWords = (p) => [...(p.aliases || []), ...(p.stack || []), W[p.slug].tag ? plain(work(p.slug, 'tag')) : '', beamById[p.beam]?.short || ''].filter(Boolean).join(' ');
const threadSlugs = (p) => (p.related || []).filter((s) => bySlug[s] && shown.includes(bySlug[s])).join(' ');
const row = (p) => {
  const title = P(p.slug, 'title'), sub = P(p.slug, 'subtitle'), st = status(p), tl = typeLine(p);
  const links = accessOf(p);
  const thumb = p.cover ? img(thumbOf(p.cover), { cls: 'row-thumb', alt: '' }) : '<span class="row-thumb blank" aria-hidden="true"></span>';
  const rel = threadSlugs(p);
  return `<div class="row card${off(p.hidden)}" data-slug="${p.slug}" data-beam="${p.beam}" data-group="${tierOf(p)}" data-seek="${esc(seekWords(p))}"${rel ? ` data-related="${rel}"` : ''} style="${hue(p.beam)}">
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
  const list = ANNOTATE ? projects.filter((p) => !isTodo(p)) : shown;
  const drafting = projects.filter((p) => p.hidden && (isTodo(p) || !p.cover)).filter((p) => !/decree|sidepanel/.test(p.slug));
  const cols = ['work.col_record', 'work.col_wing', 'work.col_stack', 'work.col_status', 'work.col_access'].map((k) => T(k));
  // The seek line heads the ledger. It is display:none until site.js runs (.js), so without JS nothing is promised.
  const [sl, sh, sn] = ['seek.label', 'seek.hint', 'seek.none'].map((key) => T(key));
  return `<div class="ledger" id="grid" data-ledger>
  <div class="seek" role="search">
    <label for="seek"${sl.a}>${sl.h}</label>
    <span class="seek-field"><input id="seek" type="search" autocomplete="off" autocapitalize="off" spellcheck="false" enterkeyhint="go" placeholder="${esc(T('seek.placeholder').p)}" aria-describedby="seek-hint" aria-keyshortcuts="/"><kbd aria-hidden="true">/</kbd></span>
    <p class="seek-hint" id="seek-hint"${sh.a}>${sh.h}</p>
  </div>
  <div class="ledger-head" aria-hidden="true"><span></span><span></span>${cols.map((c) => `<span${c.a}>${c.h}</span>`).join('')}</div>
  ${groups.map(([g, key]) => { const rows = list.filter((p) => tierOf(p) === g); if (!rows.length) return ''; const gt = T(key); return `<div class="ledger-group" data-group="${g}"><h3${gt.a}>${gt.h}</h3>${rows.map(row).join('')}</div>`; }).join('\n')}
  <p class="seek-none" hidden${sn.a}>${sn.h}</p>
  ${drafting.length ? `<p class="drafting"><span${T('work.drafting').a}>${T('work.drafting').h}</span>: ${drafting.map((p) => inline(work(p.slug, 'title'))).join(' · ')}</p>` : ''}
</div>`;
}

// The Wordgame tile on the Prismet page carries a one-line note: where today's word comes from. Without JS the note is
// simply on the page; site.js turns the tile into a button (aria-expanded) that shows and hides it. The route is text.
function wordTile(inner) {
  const hint = T('arcade.note_hint'), note = T('arcade.note_wordgame');
  return `<li class="word-tile" data-note="word-note">${inner}<small${hint.a}>${hint.h}</small></li><li class="tile-note" id="word-note"><p${note.a}>${note.h.replace('prismet.xyz/api/wordle', '<code>prismet.xyz/api/wordle</code>')}</p></li>`;
}

// ── home: skills from the stacks of the shown projects, each linked to its evidence ─────────
function skills() {
  const list = (data.skills || []).map(([s, slug]) => [s, bySlug[slug]]).filter(([, p]) => p && shown.includes(p));
  return `<ul class="skills">${list.map(([s, p]) => `<li><a href="${href(p)}">${esc(s)}</a><small>${inline(work(p.slug, 'title'))}</small></li>`).join('')}</ul>`;
}

const GIGS = [['mc-plugin', 'minecraft'], ['mc-server', 'minecraft'], ['ios-app', 'apps'], ['web-tool', 'web'], ['ai-agents', 'ai'], ['linux-desktop', 'desktop']];
const tileFor = { 'steam-rewind': 'steamrewind', 'debt-clock': 'debtclock' };
const k = (key, vars) => T(key, vars);
const sec = (id, attrs, inner) => (hiddenSections.has(id) && !ANNOTATE ? '' :
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
    ${SPECTRUM.map((id) => { const b = beamLabel(id), n = shown.filter((p) => p.beam === id).length; return `<button class="chip" type="button" data-filter="${id}" aria-pressed="false" style="--h:var(--${id})"><i></i><span class="long"${b.a}>${b.h}</span><span class="short">${esc(beamById[id]?.short || plain(site(`beam.${id}`)))}</span><small>${n}</small></button>`; }).join('')}
  </div>
  ${ledger()}
</div>`),
  plate: () => sec('plate', 'aria-label="Plate"', `<div class="wrap">
  <figure class="plate" style="${hue('minecraft')}">
    ${img('assets/minecraft/server-dark-spire-1655.webp', { alt: T('plate.alt').p, srcset: `${asset('assets/minecraft/server-dark-spire-860.webp')} 860w, ${asset('assets/minecraft/server-dark-spire-1655.webp')} 1655w`, sizesAttr: '(max-width: 900px) 100vw, 860px' })}
    <figcaption><i></i><span${k('plate.caption').a}>${k('plate.caption').h}</span> <a href="#minecraft" data-beam="minecraft">${beamLabel('minecraft').h}</a></figcaption>
  </figure>
</div>`),
  lenses: () => sec('lenses', 'aria-labelledby="lenses-title"', `<div class="wrap">
  <div class="sec-head"><div><h2 id="lenses-title"${k('lenses.title').a}>${k('lenses.title').h}</h2><p${k('lenses.intro').a}>${k('lenses.intro').h}</p></div></div>
  <div class="lenses">${lenses.map((l) => { const t = k(`lens.${l.id}.title`), d = k(`lens.${l.id}.blurb`); return `<a class="lens" href="${esc(l.href)}">
    ${img('assets/prismet/tiles/' + tileFor[l.id] + '.webp', { alt: '', sizesAttr: '64px' })}
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

// What a library section may use from this page: the image helper, the doors, the records.
const sectionCtx = (top) => ({ esc, img, has, ed, door, bySlug, shown, featured: featuredSlugs.map((slug) => bySlug[slug]), top, editing: ANNOTATE });
const homeSection = (id) => {
  const s = HOME_CUSTOM.find((x) => x.id === id);
  return !s || (hiddenSections.has(id) && !ANNOTATE) ? '' : renderSection({ ...s, hidden: hiddenSections.has(id) }, 'home', sectionCtx(false));
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
${sectionOrder.map((id) => (SECTIONS[id] ? SECTIONS[id]() : homeSection(id))).join('\n')}
</main>
${footer()}
${PREVIEW ? `<script type="application/json" id="bz-layout">${JSON.stringify(layoutState).replace(/</g, '\\u003c')}</script>` : ''}
${scripts()}`;

emit('index.html', `<!doctype html><html lang="en"><head>${head({ title: plain(site('page.title')), desc: plain(site('page.description')), url: '' })}</head><body>${indexBody}</body></html>`);

// ── project pages ───────────────────────────────────────────────────────────────────────────
// Plates sit in auto-fill columns of at least 280px with 20px gaps (one, two or three across the .wrap); under 600px
// only tall plates pair up. Wide plates span the row; tall ones stop at 300px.
const PLATE_SIZES = {
  '': '(max-width: 400px) calc(100vw - 32px), (max-width: 630px) 92vw, (max-width: 956px) calc(46vw - 10px), (max-width: 1240px) calc(30.7vw - 13px), 367px',
  wide: WRAP,
  tall: '(max-width: 400px) calc(50vw - 22px), (max-width: 600px) calc(46vw - 6px), 300px',
};
// A frontispiece fills the .wrap but stops at 72vh tall (object-fit: contain), so a tall cover is drawn narrower.
const frontSizes = (src) => {
  if (src.includes('/icons/')) return '(max-width: 352px) calc(100vw - 32px), 320px';
  const { w, h } = sizeOf(src), tall = `calc(72vh * ${(w / h).toFixed(3)})`;
  return `(max-width: 400px) min(calc(100vw - 32px), ${tall}), (max-width: 1240px) min(92vw, ${tall}), min(1128px, ${tall})`;
};
const plates = (p) => {
  const gal = (p.gallery || []);
  if (!gal.length) return '';
  const t = T('project.plates');
  return `<section class="plates" aria-labelledby="plates-title"><div class="wrap">
  <h2 id="plates-title"${t.a}>${t.h}</h2>
  <div class="plate-grid">${gal.map((g, n) => { const { w, h } = sizeOf(g); const alt = (p.shotAlts && p.shotAlts[n]) || `${plain(work(p.slug, 'title'))}, plate ${n + 1}`; const kind = h > w ? 'tall' : w / h > 2.2 ? 'wide' : ''; return `<figure class="${kind}">${img(g, { root: '../', alt, lazy: n > 1, sizesAttr: PLATE_SIZES[kind] })}<figcaption aria-hidden="true">${esc(alt)}</figcaption></figure>`; }).join('')}</div>
</div></section>`;
};

// Case notes: proposed copy (content/site.md, keys bench.* / prismet.* / helm.*) rendered under the summary of the
// three flagships that have it. Each part is optional; a record with only `.why` gets one heading and one paragraph.
const CASE = { bayzyl: 'bench', 'prismet-app': 'prismet', 'the-helm': 'helm' };
const caseNotes = (p) => {
  const pre = CASE[p.slug];
  if (!pre) return '';
  const parts = [['why', 'case.why_title'], ['hard', 'case.hard_title'], ['next', 'case.next_title']]
    .filter(([f]) => `${pre}.${f}` in S)
    .map(([f, h]) => { const t = T(h), b = T(`${pre}.${f}`); return `<h2${t.a}>${t.h}</h2><p${b.a}>${b.h}</p>`; });
  const n = `${pre}.plan_note` in S ? T(`${pre}.plan_note`) : null;
  return parts.length ? `<div class="case">${parts.join('')}${n ? `<p class="plan-note"${n.a}><em>${n.h}</em></p>` : ''}</div>` : '';
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
  const front = !isFlag && p.cover ? `<figure class="frontispiece${p.cover.includes('/icons/') ? ' icon' : ''}"><div class="wrap">${img(p.cover, { root: '../', alt: (p.shotAlts || [])[0] || plain(work(s, 'title')), lazy: false, priority: true, sizesAttr: frontSizes(p.cover) })}</div></figure>` : '';
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
      ${caseNotes(p)}
      ${hl.length ? `<h2${hlT.a}>${hlT.h}</h2><ul class="hl">${hl.map((h, n) => `<li${ed(`work.${s}.highlights.${n}`, h)}>${inline(h)}</li>`).join('')}</ul>` : ''}
    </div>
    <aside>
      ${factList(factRows(s, [
        { k: roleK, v: { a: ed(`work.${s}.role`, work(s, 'role')), h: inline(work(s, 'role')) } },
        { k: yearK, v: { a: ed(`work.${s}.year`, work(s, 'year')), h: inline(work(s, 'year')) } },
      ]))}
      <div class="stack" role="list" aria-label="Stack">${p.stack.map((x) => `<span role="listitem">${esc(x)}</span>`).join('')}</div>
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
  emit(`work/${s}.html`, `<!doctype html><html lang="en"><head>${head({ title: `${plain(work(s, 'title'))} · Prismet`, desc: plain(work(s, 'subtitle')), root: '../', url: `work/${s}.html` })}</head><body>${body}</body></html>`);
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
  emit(`work/${s}.html`, `<!doctype html><html lang="en"><head>${head({ title: `${plain(work(s, 'title'))} · Prismet`, desc: w.p, root: '../', noindex: true })}</head><body>${body}</body></html>`);
});

// ── pages from the section library ──────────────────────────────────────────────────────────
CUSTOM.forEach((pg) => {
  const list = (pg.sections || []).filter((s) => s && (ANNOTATE || !s.hidden));
  const body = `${bar()}
<main id="main" class="x-page">
${list.map((s, i) => renderSection(s, pg.slug, sectionCtx(i === 0))).join('\n')}
</main>
${footer()}
${scripts()}`;
  emit(`${pg.slug}.html`, `<!doctype html><html lang="en"><head>${head({ title: `${plain(pg.title || pg.slug)} · Prismet`, desc: plain(pg.description || pg.title || pg.slug), noindex: pg.status !== 'published', url: `${pg.slug}.html` })}</head><body>${body}</body></html>`);
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
    <div><dt>Type</dt><dd>Unbounded · Hanken Grotesk · Martian Mono</dd></div>
    <div><dt>Licence</dt><dd>SIL Open Font License 1.1</dd></div>
    <div><dt>Requests to other sites</dt><dd>0</dd></div>
    <div><dt>Records</dt><dd>${shown.length}</dd></div>
  </dl>
  <p><a class="btn" href="index.html#work"${T('project.back').a}>${T('project.back').h}</a></p>
</div></main>
${footer()}`;
  emit('colophon.html', `<!doctype html><html lang="en"><head>${head({ title: `${t.p} · Prismet`, desc: plain(site('colophon.p1')), url: 'colophon.html' })}</head><body>${body}</body></html>`);
}

// ── artifact preview: the same page as a fragment (the artifact host supplies <head>/<body>) ─
if (PREVIEW) {
  emit('_preview.html', `<title>Prismet Workshop</title>
${GOOGLE_FONTS}<link rel="stylesheet" href="editor.css"><link rel="stylesheet" href="site.css">
<script src="site.js"></script>
${indexBody}`);
}
return { pages, missing, shown, projects, layoutState };
}
