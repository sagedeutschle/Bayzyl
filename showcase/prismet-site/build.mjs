// build.mjs — generates the static prismet.xyz site.
//
//   node showcase/prismet-site/build.mjs            # → showcase/prismet-site/dist/
//   node showcase/prismet-site/build.mjs --preview  # adds the "Edit words" editor + dist/_preview.html
//
// WORDS live in content/site.md and content/work/<slug>.md (plain text, edit freely).
// DATA (images, links, categories) lives in data/projects.json.
// No dependencies. Output is plain HTML/CSS/JS + images, so it drops into the existing Fly app
// as static files next to the /api/wordle route (see REDESIGN-PLAN.md).
import { readFileSync, writeFileSync, mkdirSync, copyFileSync, rmSync, cpSync, existsSync } from 'node:fs';
import { dirname, join, basename, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { loadSite, loadWork, inline, plain, listItems, factPairs } from './content.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const SHOWCASE = join(HERE, '..');
const DIST = join(HERE, 'dist');
const PREVIEW = process.argv.includes('--preview');
const data = JSON.parse(readFileSync(join(HERE, 'data/projects.json'), 'utf8'));
const { owner, beams, lenses, projects } = data;
const beamById = Object.fromEntries(beams.map((b) => [b.id, b]));

// ── words ───────────────────────────────────────────────────────────────────────────────────
const S = loadSite();
const W = Object.fromEntries(projects.map((p) => [p.slug, loadWork(p.slug)]));
const VARS = { count: projects.length };
const missing = new Set();
const site = (key) => { if (!(key in S)) { missing.add(`content/site.md → ## ${key}`); return key; } return S[key]; };
const work = (slug, f) => { const v = W[slug][f]; if (v === undefined) { missing.add(`content/work/${slug}.md → ## ${f}`); return ''; } return v; };
// In --preview every piece of wording carries its key, so the page editor can save edits back.
const ed = (key, raw) => (PREVIEW ? ` data-edit="${esc(key)}" data-src="${esc(raw)}"` : '');
const T = (key) => ({ a: ed(key, site(key)), h: inline(site(key), VARS) });               // site text
const P = (slug, f) => ({ a: ed(`work.${slug}.${f}`, work(slug, f)), h: inline(work(slug, f)) }); // project text
const isTodo = (p) => p.todo === true;

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
if (PREVIEW) for (const f of ['editor.js', 'editor.css']) if (existsSync(join(HERE, 'src', f))) copyFileSync(join(HERE, 'src', f), join(DIST, f));
asset('assets/icons/prismet-app.webp');            // favicon
asset('fiverr/out/portfolio-prismet-spread.png');   // og:image

// ── shared pieces ───────────────────────────────────────────────────────────────────────────
const hueVars = (id) => `--h:var(--${id});--hi:var(--${id}-ink)`;
const mark = (size = 30) => `<svg width="${size}" height="${size}" viewBox="0 0 64 64" aria-hidden="true">
  <defs><linearGradient id="mk" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F4D77E"/><stop offset=".55" stop-color="#D8A53B"/><stop offset="1" stop-color="#9A6E22"/></linearGradient></defs>
  ${['desktop', 'apps', 'worlds', 'minecraft', 'web', 'ai'].map((c, i) => {
    const a = (i * 60 - 90) * Math.PI / 180, l = a - 0.23, r = a + 0.23, Pt = (ang, rad) => `${(32 + Math.cos(ang) * rad).toFixed(1)} ${(32 + Math.sin(ang) * rad).toFixed(1)}`;
    return `<path d="M${Pt(l, 17)} L${Pt(a, 30)} L${Pt(r, 17)}Z" fill="var(--${c})"/>`;
  }).join('')}
  <circle cx="32" cy="32" r="17" fill="#141331" stroke="url(#mk)" stroke-width="3"/>
  <path d="M32 22 L41 38 L23 38 Z" fill="none" stroke="url(#mk)" stroke-width="2.4" stroke-linejoin="round"/></svg>`;

// The artifact preview can't load fonts from its own files, so it uses Google Fonts; production self-hosts.
const GOOGLE_FONTS = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Unbounded:wght@300..900&family=Martian+Mono:wdth,wght@75..112.5,300..700&family=Hanken+Grotesk:wght@400..700&display=swap">';
const head = ({ title, desc, root = '' }) => `<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${esc(title)}</title>
<meta name="description" content="${esc(desc)}">
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(desc)}">
<meta property="og:image" content="https://prismet.xyz/assets/boards/portfolio-prismet-spread.webp">
<meta name="theme-color" content="#10111A">
<link rel="icon" href="${root}assets/icons/prismet-app.webp">
<link rel="stylesheet" href="${root}assets/fonts/fonts.css">${PREVIEW ? GOOGLE_FONTS + `<link rel="stylesheet" href="${root}editor.css">` : ''}
<link rel="stylesheet" href="${root}site.css">`;
const scripts = (root = '') => `<script src="${root}site.js"></script>${PREVIEW ? `<script src="${root}editor.js"></script>` : ''}`;

const bar = (root = '') => {
  const [w, l, a, h] = ['nav.work', 'nav.lenses', 'nav.about', 'nav.hire'].map(T);
  return `<a class="skip" href="#main">Skip to content</a>
<header class="bar"><div class="wrap">
  <a class="brand" href="${root}index.html">${mark(30)}<strong>Prismet</strong><span${T('brand.tagline').a}>${T('brand.tagline').h}</span></a>
  <nav class="nav" aria-label="Main">
    <a href="${root}index.html#work"${w.a}>${w.h}</a>
    <a href="${root}index.html#lenses"${l.a}>${l.h}</a>
    <a href="${root}index.html#about"${a.a}>${a.h}</a>
    <a class="btn primary keep" href="${esc(owner.fiverr)}"${h.a}>${h.h}</a>
    <button class="btn theme" type="button" id="theme-toggle" aria-label="Switch light or dark theme">◐</button>
  </nav>
</div></header>`;
};

const footer = () => `<footer><div class="wrap">
  <span${T('footer.copyright').a}>${T('footer.copyright').h}</span>
  <span><a href="${esc(owner.github)}">GitHub</a> · <a href="${esc(owner.linkedin)}">LinkedIn</a> · <a href="${esc(owner.fiverr)}">Fiverr</a></span>
</div></footer>`;

const factList = (slug, extra = []) => {
  const rows = [...extra, ...factPairs(work(slug, 'facts')).map(([k, v], i) => ({
    k: { a: ed(`work.${slug}.facts.${i}.label`, k), h: inline(k) },
    v: { a: ed(`work.${slug}.facts.${i}.value`, v), h: inline(v) },
  }))];
  return `<dl class="facts">${rows.map(({ k, v }) => `<div><dt${k.a}>${k.h}</dt><dd${v.a}>${v.h}</dd></div>`).join('')}</dl>`;
};
const beamLabel = (id) => T(`beam.${id}`);
const status = (p) => (isTodo(p) ? T('project.in_progress') : P(p.slug, 'status'));

// ── the prism (hero + navigation) ───────────────────────────────────────────────────────────
// Spectrum order, red deviates least: desktop, apps, worlds, minecraft, web, ai.
const SPECTRUM = ['desktop', 'apps', 'worlds', 'minecraft', 'web', 'ai'];
function prism() {
  const ex = 364, ey = 232, lx = 556;
  const ys = [64, 132, 200, 268, 336, 404];
  const count = (id) => projects.filter((p) => p.beam === id).length;
  const beamsSvg = SPECTRUM.map((id, i) => {
    const y = ys[i], d = `M${ex} ${ey} L${lx - 12} ${y}`, n = count(id), label = plain(site(`beam.${id}`));
    return `<a class="beam" href="#work" data-beam="${id}" aria-label="${esc(label)}: ${n} project${n === 1 ? '' : 's'}">
      <path class="ray" d="${d}" stroke="var(--${id})" stroke-width="5" stroke-linecap="round"/>
      <path class="flow" d="${d}" stroke="#fff" stroke-opacity=".55" stroke-width="2" stroke-linecap="round"/>
      <circle cx="${lx - 12}" cy="${y}" r="5" fill="var(--${id})"/>
      <text x="${lx + 4}" y="${y + 1}"${ed(`beam.${id}`, site(`beam.${id}`))}>${esc(label)}</text>
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
  if (p.slug === 'the-helm') {
    const phone = asset('assets/live/helm-1.webp'), faces = ['chronos', 'gpu'].map((n) => asset(`assets/helm/${n}.webp`));
    return `<div class="helmmix"><img class="phone" src="${phone}" alt="" loading="lazy"><div>${faces.map((x) => `<img src="${x}" alt="" loading="lazy">`).join('')}</div></div>`;
  }
  if (!p.cover) return `<div class="placeholder"><strong aria-hidden="true">${inline(work(p.slug, 'title'))}</strong></div>`;
  if (p.cover.includes('/icons/')) return `<img class="icon" src="${asset(p.cover)}" alt="">`;
  return `<img class="cover" src="${asset(p.cover)}" alt="" loading="lazy">`;
}
const mediaAlt = (p) => ({
  'prismet-app': 'Prismet App Store screenshots: Sea Battle, the home screen, and Chess',
  'the-helm': 'Helm on iPhone next to three desktop widgets rendered from source: clock, GPU telemetry, and fleet radar',
}[p.slug] || plain(work(p.slug, 'title')));

// ── layout (projects.json → layout, featuredOrder, per-project wide/hidden) ─────────────────
// Production leaves hidden things out. The preview renders everything, marked .is-off, so the
// editor can bring it back without a rebuild.
const bySlug = Object.fromEntries(projects.map((p) => [p.slug, p]));
const SECTION_IDS = ['lenses', 'selected', 'work', 'about'];
const L = data.layout || {};
const sectionOrder = [...(L.sections || []).filter((id) => SECTION_IDS.includes(id)), ...SECTION_IDS.filter((id) => !(L.sections || []).includes(id))];
const hiddenSections = new Set(L.hiddenSections || []);
const featuredSlugs = (data.featuredOrder || []).filter((s) => bySlug[s] && !isTodo(bySlug[s]) && !bySlug[s].hidden);
const shownProjects = projects.filter((p) => !p.hidden);
const off = (isOff) => (isOff ? ' is-off' : '');

const feature = (p, i, isOff = false) => {
  const title = P(p.slug, 'title'), sub = P(p.slug, 'subtitle'), more = T('selected.read_more');
  const b = W[p.slug].tag ? P(p.slug, 'tag') : beamLabel(p.beam); // optional "## tag" overrides the beam name on this row
  const facts = factPairs(work(p.slug, 'facts')).slice(0, 4);
  return `<article class="feature${i % 2 ? ' flip' : ''}${off(isOff)}" data-slug="${p.slug}" style="${hueVars(p.beam)}">
  <a class="media" href="work/${p.slug}.html" aria-label="${esc(mediaAlt(p))}">${media(p)}</a>
  <div class="text">
    <span class="beam-tag"${b.a}>${b.h}</span>
    <h3${title.a}>${title.h}</h3>
    <p class="sub"${sub.a}>${sub.h}</p>
    <dl class="facts">${facts.map(([k, v], n) => `<div><dt${ed(`work.${p.slug}.facts.${n}.label`, k)}>${inline(k)}</dt><dd${ed(`work.${p.slug}.facts.${n}.value`, v)}>${inline(v)}</dd></div>`).join('')}</dl>
    <div class="more"><a class="btn" href="work/${p.slug}.html"${more.a}>${more.h}</a>${(p.links || []).filter((l) => l.href.startsWith('http')).slice(0, 1).map((l) => `<a class="btn" href="${esc(l.href)}">${esc(l.label)} ↗</a>`).join('')}</div>
  </div>
</article>`;
};

const card = (p) => {
  const normal = p.cover ? asset(p.cover) : null, wideSrc = p.wideCover ? asset(p.wideCover) : null;
  const cover = p.wide && wideSrc ? wideSrc : normal || wideSrc;
  const contain = (cover && cover.includes('/icons/')) || (p.cover || '').includes('/tiles/');
  const title = P(p.slug, 'title'), sub = P(p.slug, 'subtitle'), st = status(p), ph = T('work.placeholder');
  const short = beamById[p.beam].short;
  const swap = PREVIEW && normal && wideSrc ? ` data-src-normal="${normal}" data-src-wide="${wideSrc}"` : '';
  return `<a class="card${p.wide ? ' wide' : ''}${off(p.hidden)}" href="work/${p.slug}.html" data-beam="${p.beam}" data-slug="${p.slug}" style="${hueVars(p.beam)}">
    <div class="thumb">${cover ? `<img class="${contain ? 'contain' : ''}" src="${cover}"${swap} alt="" loading="lazy">` : `<div class="placeholder"><strong aria-hidden="true">${inline(work(p.slug, 'title'))}</strong><span${ph.a}>${ph.h}</span></div>`}</div>
    <div class="body">
      <span class="beam-tag">${esc(short)}</span>
      <h3${title.a}>${title.h}</h3>
      <p${sub.a}>${sub.h}</p>
      <div class="meta"><span class="status"${st.a}>${st.h}</span></div>
    </div>
  </a>`;
};

const GIGS = [['mc-plugin', 'minecraft'], ['mc-server', 'minecraft'], ['ios-app', 'apps'], ['web-tool', 'web'], ['ai-agents', 'ai'], ['linux-desktop', 'desktop']];

// ── index ───────────────────────────────────────────────────────────────────────────────────
const tileFor = { 'steam-rewind': 'steamrewind', 'debt-clock': 'debtclock' };
const k = (key) => T(key); // shorthand
const sec = (id, attrs, inner) => (hiddenSections.has(id) && !PREVIEW ? '' :
  `<section id="${id}" data-section="${id}" class="${off(hiddenSections.has(id)).trim()}" ${attrs}>${inner}</section>`);

const SECTIONS = {
  lenses: () => sec('lenses', 'aria-labelledby="lenses-title"', `<div class="wrap">
  <div class="sec-head"><div><p class="eyebrow"${k('lenses.eyebrow').a}>${k('lenses.eyebrow').h}</p><h2 id="lenses-title"${k('lenses.title').a}>${k('lenses.title').h}</h2></div>
    <p${k('lenses.intro').a}>${k('lenses.intro').h}</p></div>
  <div class="lenses">${lenses.map((l) => { const t = k(`lens.${l.id}.title`), d = k(`lens.${l.id}.blurb`); return `<a class="lens" href="${esc(l.href)}">
    <img src="${asset('assets/prismet/tiles/' + tileFor[l.id] + '.webp')}" alt="">
    <div><h3${t.a}>${t.h}</h3><p${d.a}>${d.h}</p>${l.kind === 'api' ? `<code>GET ${esc(l.href)}</code>` : ''}</div></a>`; }).join('')}</div>
</div>`),
  selected: () => sec('selected', 'aria-labelledby="selected-title"', `<div class="wrap">
  <div class="sec-head"><div><p class="eyebrow"${k('selected.eyebrow').a}>${k('selected.eyebrow').h}</p><h2 id="selected-title"${k('selected.title').a}>${k('selected.title').h}</h2></div></div>
  ${featuredSlugs.map((s, i) => feature(bySlug[s], i)).join('\n')}
  ${PREVIEW ? projects.filter((p) => !featuredSlugs.includes(p.slug) && !isTodo(p)).map((p, i) => feature(p, i, true)).join('\n') : ''}
</div>`),
  work: () => sec('work', 'aria-labelledby="work-title"', `<div class="wrap">
  <div class="sec-head"><div><p class="eyebrow"${k('work.eyebrow').a}>${k('work.eyebrow').h}</p><h2 id="work-title"${k('work.title').a}>${k('work.title').h}</h2></div>
    <p${k('work.intro').a} data-count="${shownProjects.length}">${inline(site('work.intro'), { count: shownProjects.length })}</p></div>
  <div class="filters" role="group" aria-label="Filter by category">
    <button class="chip" type="button" data-filter="all" aria-pressed="true" style="--h:var(--gold)"><i></i><span${k('work.filter_all').a}>${k('work.filter_all').h}</span></button>
    ${SPECTRUM.map((id) => { const b = beamLabel(id); return `<button class="chip" type="button" data-filter="${id}" aria-pressed="false" style="--h:var(--${id})"><i></i><span${b.a}>${b.h}</span></button>`; }).join('')}
  </div>
  <div class="grid" id="grid">${(PREVIEW ? projects : shownProjects).map(card).join('')}</div>
</div>`),
  about: () => sec('about', 'aria-labelledby="about-title"', `<div class="wrap about">
  <div>
    <p class="eyebrow"${k('about.eyebrow').a}>${k('about.eyebrow').h}</p><h2 id="about-title" class="about-title"${k('about.title').a}>${k('about.title').h}</h2>
    <div class="prose">
      <p${k('about.p1').a}>${k('about.p1').h}</p>
      <p${k('about.p2').a}>${k('about.p2').h}</p>
    </div>
  </div>
  <div class="hire" id="hire">
    <p class="eyebrow"${k('hire.eyebrow').a}>${k('hire.eyebrow').h}</p>
    <h3${k('hire.title').a}>${k('hire.title').h}</h3>
    <ul>${GIGS.map(([id, h]) => { const t = k(`hire.${id}.title`), s = k(`hire.${id}.sub`); return `<li style="--h:var(--${h})"><i></i><span><span${t.a}>${t.h}</span><small${s.a}>${s.h}</small></span></li>`; }).join('')}</ul>
    <div class="links"><a class="btn primary" href="${esc(owner.fiverr)}"${k('hire.cta').a}>${k('hire.cta').h}</a><a class="btn" href="${esc(owner.github)}">GitHub</a><a class="btn" href="${esc(owner.linkedin)}">LinkedIn</a></div>
  </div>
</div>`),
};

// The editor starts from this; "apply my edits" writes its changes back into projects.json.
const layoutState = {
  sections: sectionOrder, hiddenSections: [...hiddenSections],
  order: projects.map((p) => p.slug), featured: featuredSlugs,
  wide: projects.filter((p) => p.wide).map((p) => p.slug), hidden: projects.filter((p) => p.hidden).map((p) => p.slug),
  names: Object.fromEntries(projects.map((p) => [p.slug, plain(work(p.slug, 'title'))])),
};

const indexBody = `${bar()}
<main id="main">
<section class="hero" aria-labelledby="hero-title" style="padding:0"><div class="wrap">
  <div>
    <p class="eyebrow"${k('hero.eyebrow').a}>${k('hero.eyebrow').h}</p>
    <h1 id="hero-title"${k('hero.title').a}>${k('hero.title').h}</h1>
    <p class="lede"${k('hero.lede').a}>${k('hero.lede').h}</p>
    <div class="ctas"><a class="btn primary" href="#work"${k('hero.cta_primary').a}>${k('hero.cta_primary').h}</a><a class="btn" href="${esc(owner.fiverr)}"${k('hero.cta_secondary').a}>${k('hero.cta_secondary').h}</a></div>
  </div>
  <div>
    ${prism()}
    <div class="beam-chips" aria-label="Categories">${SPECTRUM.map((id) => { const b = beamLabel(id); return `<a class="chip" href="#work" data-beam="${id}" style="--h:var(--${id})"><i></i><span${b.a}>${b.h}</span></a>`; }).join('')}</div>
  </div>
</div></section>
${sectionOrder.map((id) => SECTIONS[id]()).join('\n')}
</main>
${footer()}
${PREVIEW ? `<script type="application/json" id="bz-layout">${JSON.stringify(layoutState).replace(/</g, '\\u003c')}</script>` : ''}
${scripts()}`;

writeFileSync(join(DIST, 'index.html'), `<!doctype html><html lang="en"><head>${head({ title: plain(site('page.title')), desc: plain(site('page.description')) })}</head><body>${indexBody}</body></html>`);

// ── project pages ───────────────────────────────────────────────────────────────────────────
projects.forEach((p, i) => {
  const b = beamLabel(p.beam), s = p.slug;
  const next = projects[(i + 1) % projects.length], prev = projects[(i - 1 + projects.length) % projects.length];
  const gal = (p.gallery || []).map(asset);
  const title = P(s, 'title'), sub = P(s, 'subtitle'), st = status(p), sum = P(s, 'summary');
  const hl = listItems(work(s, 'highlights')), hlT = T('project.highlights');
  const roleK = T('project.role_label'), yearK = T('project.year_label');
  const body = `${bar('../')}
<main id="main" style="${hueVars(p.beam)}">
  <div class="wrap p-hero">
    <p class="crumbs"><a href="../index.html#work">${T('nav.work').h}</a> / <a href="../index.html#work">${b.h}</a></p>
    <h1${title.a}>${title.h}</h1>
    <p class="sub"${sub.a}>${sub.h}</p>
    <div class="row"><span class="status"${st.a}>${st.h}</span>${(p.links || []).map((l) => `<a class="btn" href="${esc(l.href.startsWith('/') ? '..' + l.href : l.href)}">${esc(l.label)}${l.href.startsWith('http') ? ' ↗' : ''}</a>`).join('')}</div>
  </div>
  ${gal.length ? `<div class="gallery" tabindex="0" aria-label="Screenshots">${gal.map((x, n) => `<img src="../${x}" alt="${esc((p.shotAlts && p.shotAlts[n]) || `${plain(work(s, 'title'))}, image ${n + 1}`)}" loading="${n < 2 ? 'eager' : 'lazy'}">`).join('')}</div>` : ''}
  <div class="wrap p-body">
    <div class="prose">
      <div class="summary${isTodo(p) ? ' todo' : ''}"${sum.a}>${sum.h.split(/\n\s*\n/).map((para) => `<p>${para}</p>`).join('')}</div>
      ${hl.length ? `<h2${hlT.a}>${hlT.h}</h2><ul class="hl">${hl.map((h, n) => `<li${ed(`work.${s}.highlights.${n}`, h)}>${inline(h)}</li>`).join('')}</ul>` : ''}
    </div>
    <aside>
      ${factList(s, [
        { k: roleK, v: { a: ed(`work.${s}.role`, work(s, 'role')), h: inline(work(s, 'role')) } },
        { k: yearK, v: { a: ed(`work.${s}.year`, work(s, 'year')), h: inline(work(s, 'year')) } },
      ])}
      <div class="stack" aria-label="Stack">${p.stack.map((x) => `<span>${esc(x)}</span>`).join('')}</div>
    </aside>
  </div>
  <div class="wrap next">
    <a href="${prev.slug}.html"><small>← ${T('project.previous').h}</small><strong>${inline(work(prev.slug, 'title'))}</strong></a>
    <a href="${next.slug}.html" style="text-align:right"><small>${T('project.next').h} →</small><strong>${inline(work(next.slug, 'title'))}</strong></a>
  </div>
</main>
${footer()}
${scripts('../')}`;
  writeFileSync(join(DIST, 'work', s + '.html'), `<!doctype html><html lang="en"><head>${head({ title: `${plain(work(s, 'title'))} · Prismet`, desc: plain(work(s, 'subtitle')), root: '../' })}</head><body>${body}</body></html>`);
});

// ── artifact preview: the same page as a fragment (the artifact host supplies <head>/<body>) ─
if (PREVIEW) {
  writeFileSync(join(DIST, '_preview.html'), `<title>Prismet Redesign</title>
${GOOGLE_FONTS}<link rel="stylesheet" href="editor.css"><link rel="stylesheet" href="site.css">
${indexBody}`);
}
if (missing.size) console.warn('⚠ missing wording (shown as the key on the page):\n  ' + [...missing].join('\n  '));
console.log(`built ${projects.length} project pages + index → ${DIST}${PREVIEW ? ' (with editor)' : ''}`);
