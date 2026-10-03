// panels.js — the two side panels: the site's structure on the left, the inspector for the selection on the right.
//
// A selection is one of:
//   { type: 'section', id }        a part of the home page (hero, selected, work, plate, lenses, about)
//   { type: 'project', slug }      a record: its wording file and its entry in projects.json
//   { type: 'page', page }         a page with no parts of its own (the colophon)
//   { type: 'shared' }             words used on every page (navigation, footer, labels)
//   { type: 'tokens', group }      a group of design tokens
//   { type: 'cpage', slug }        a page made from the section library: its title, address, status, navigation
//   { type: 'psection', page, id } a section of such a page (a library section on the home page is a 'section')
//   { type: 'settings' }           the site's own details: name, links, search description
// plus an optional key: the one field to bring into view (set when text is clicked in the preview). With a key the
// inspector also offers that element's own style, per width (data/styles.json); a section offers its layout.
import { editTarget } from '../lib/format.js';
import { SECTION_IDS } from '../lib/render.js';
import { safeValue } from '../lib/theme.js';
import { TIERS, tierFor } from '../lib/styles.js';
import { SECTION_TYPES, sectionTitle, validSlug } from '../lib/sections.js';
import { findSection } from './store.js';

const AREAS = [
  ['hero', /^(hero|plan|beam)\./, 'Hero'],
  ['selected', /^selected\./, 'Principal works'],
  ['work', /^(work|seek|access)\./, 'Register'],
  ['plate', /^plate\./, 'Plate'],
  ['lenses', /^(lenses|lens)\./, 'Lenses'],
  ['about', /^(about|hire)\./, 'About + Hire'],
];
export const sectionName = (id) => AREAS.find((a) => a[0] === id)?.[2] || id;
const isColophon = (key) => /^colophon\./.test(key);

/** The design tokens the editor offers, by group. Defaults are read from site.css; only changes are stored. */
export const TOKEN_GROUPS = [
  { id: 'colors', label: 'Colors', modes: true, tokens: [['--ground', 'Background'], ['--surface', 'Surface'], ['--raised', 'Surface raised'], ['--ink', 'Text primary'], ['--ink-2', 'Text secondary'], ['--ink-3', 'Text muted'], ['--brass', 'Accent'], ['--brass-ink', 'Accent text'], ['--on-brass', 'Text on accent'], ['--lantern', 'Lantern'], ['--live', 'Live'], ['--line', 'Survey line'], ['--hair', 'Hairline'], ['--rule', 'Rule']] },
  { id: 'wings', label: 'Wing hues', modes: true, tokens: ['desktop', 'apps', 'worlds', 'minecraft', 'web', 'ai'].flatMap((w) => [[`--${w}`, `${w[0].toUpperCase()}${w.slice(1)} inlay`], [`--${w}-ink`, `${w[0].toUpperCase()}${w.slice(1)} text`]]) },
  { id: 'type', label: 'Typography', tokens: [['--display', 'Display font'], ['--sans', 'Reading font'], ['--mono', 'Label font'], ['--t-2xl', 'Display size'], ['--t-xl', 'H2 size'], ['--t-lg', 'H3 size'], ['--t-md', 'Lede size'], ['--t-base', 'Body size'], ['--t-sm', 'Small size'], ['--t-xs', 'Caption size']] },
  { id: 'shape', label: 'Shape and layout', tokens: [['--radius', 'Corner radius', ['0', '2px', '4px', '6px', '8px', '12px', '16px']], ['--maxw', 'Page width'], ['--gutter', 'Page gutter'], ['--bar', 'Top bar height']] },
];
// What can be styled on one element (data/styles.json). Each list is short on purpose: common changes, values that
// come from the design's scales first, anything else typed. unit is added to a bare number.
const SPACE = ['0', '4px', '8px', '12px', '16px', '24px', '32px', '48px', '64px', '96px', '128px'];
const COLORS = [['var(--ink)', 'Text primary'], ['var(--ink-2)', 'Text secondary'], ['var(--ink-3)', 'Text muted'], ['var(--brass)', 'Accent'], ['var(--brass-ink)', 'Accent text'], ['var(--lantern)', 'Lantern'], ['var(--live)', 'Live'], ['var(--ground)', 'Background'], ['var(--surface)', 'Surface'], ['var(--raised)', 'Surface raised']];
const STYLE_CONTROLS = {
  text: [
    ['Typography', [
      { prop: 'font-size', label: 'Size', unit: 'px', steps: [['var(--t-2xl)', 'Display'], ['var(--t-xl)', 'H2'], ['var(--t-lg)', 'H3'], ['var(--t-md)', 'Lede'], ['var(--t-base)', 'Body'], ['var(--t-sm)', 'Small'], ['var(--t-xs)', 'Caption']] },
      { prop: 'font-weight', label: 'Weight', options: ['300', '400', '500', '600', '700', '800', '900'] },
      { prop: 'line-height', label: 'Line height' },
      { prop: 'letter-spacing', label: 'Letter spacing', unit: 'em' },
      { prop: 'font-family', label: 'Font', options: [['var(--display)', 'Display'], ['var(--sans)', 'Reading'], ['var(--mono)', 'Label']] },
      { prop: 'text-transform', label: 'Case', options: ['none', 'uppercase', 'lowercase', 'capitalize'] },
      { prop: 'text-align', label: 'Align', options: ['left', 'center', 'right'] },
      { prop: 'color', label: 'Color', color: true, steps: COLORS },
      { prop: 'opacity', label: 'Opacity' },
      { prop: 'max-width', label: 'Max width', unit: 'px' },
    ]],
    ['Spacing', [{ prop: 'margin-top', label: 'Space above', unit: 'px', steps: SPACE }, { prop: 'margin-bottom', label: 'Space below', unit: 'px', steps: SPACE }]],
  ],
  section: [
    ['Layout', [{ prop: 'padding-top', label: 'Padding top', unit: 'px', steps: SPACE }, { prop: 'padding-bottom', label: 'Padding bottom', unit: 'px', steps: SPACE }, { prop: 'background-color', label: 'Background', color: true, steps: COLORS }]],
  ],
};
const DEVICE = { base: 1440, tablet: 820, mobile: 390 };       // the preview width that shows each tier
const rgbHex = (v) => { const m = String(v).match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/); return m ? `#${[m[1], m[2], m[3]].map((n) => Number(n).toString(16).padStart(2, '0')).join('')}` : null; };

/** site.css's own values: { root, day }. */
export function parseDefaults(css) {
  const text = css.replace(/\/\*[\s\S]*?\*\//g, '');
  const block = (re) => { const m = text.match(re); return m ? Object.fromEntries([...m[1].matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+);/g)].map((x) => [x[1], x[2].trim()])) : {}; };
  return { root: block(/:root\s*\{([^}]*)\}/), day: block(/:root\[data-theme="light"\]\s*\{([^}]*)\}/) };
}
const hex6 = (v) => { const m = String(v).trim().match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i); return m ? `#${m[1].length === 3 ? [...m[1]].map((c) => c + c).join('') : m[1]}`.toLowerCase() : null; };

/** Which selection owns an edit key. */
export function ownerOf(key, slugs) {
  const t = editTarget(key, slugs);
  if (t.file === 'pages') return t.page === 'home' ? { type: 'section', id: t.section } : { type: 'psection', page: t.page, id: t.section };
  if (t.file === 'work') return { type: 'project', slug: t.slug };
  if (isColophon(key)) return { type: 'page', page: 'colophon.html' };
  const area = AREAS.find((a) => a[1].test(key));
  return area ? { type: 'section', id: area[0] } : { type: 'shared' };
}
export const pageOf = (sel) => (sel?.type === 'project' ? `work/${sel.slug}.html` : sel?.type === 'page' ? sel.page : sel?.type === 'section' ? 'index.html' : sel?.type === 'cpage' ? `${sel.slug}.html` : sel?.type === 'psection' ? `${sel.page}.html` : null);
export const sameSel = (a, b) => a?.type === b?.type && a?.id === b?.id && a?.slug === b?.slug && a?.page === b?.page && a?.group === b?.group;

// Small DOM builder: h('button', { class: 'x', onclick }, 'Label').
export function h(tag, attrs = {}, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v); else if (k === 'class') el.className = v; else if (k in el && k !== 'list') el[k] = v; else el.setAttribute(k, v === true ? '' : v);
  }
  el.append(...kids.flat(Infinity).filter((x) => x != null && x !== false));
  return el;
}
const pretty = (key) => key.replace(/^work\.[a-z0-9-]+\./, '').replace(/[._-]/g, ' ').replace(/\b\w/, (c) => c.toUpperCase());

export function createPanels({ treeEl, inspectorEl, crumbsEl, store, baseCss, select, getSelection, getTab, setThemeMode, getWidth, setDevice, computed, say }) {
  const defaults = parseDefaults(baseCss);
  let themeMode = 'root';                                     // which set of colours the token rows edit: root (night) or day
  const D = () => store.docs, B = () => store.base;
  const projects = () => D().projects.projects;
  // The home page's sections: the built-in ones and the library sections added to it, in one order.
  const homeIds = (d = D()) => [...SECTION_IDS, ...d.pages.home.map((x) => x.id)];
  const sections = (d = D()) => { const L = d.projects.layout || {}, all = homeIds(d); return [...(L.sections || []).filter((id) => all.includes(id)), ...all.filter((id) => !(L.sections || []).includes(id))]; };
  const custom = (id) => D().pages.home.find((x) => x.id === id);
  const secName = (id) => (custom(id) ? sectionTitle(custom(id)) : sectionName(id));
  const pageBySlug = (slug, d = D()) => d.pages.pages.find((p) => p.slug === slug);
  // A library section, wherever it lives: { scope: 'home' | slug, s } or null.
  const library = (sel) => (sel?.type === 'psection' ? { scope: sel.page, s: findSection(D().pages, sel.page, sel.id) } : sel?.type === 'section' && custom(sel.id) ? { scope: 'home', s: custom(sel.id) } : null);
  const fieldKeys = (scope, sec) => (SECTION_TYPES[sec?.type]?.fields || []).filter(([, , kind]) => kind === 'line' || kind === 'text').map(([prop]) => `page.${scope}.${sec.id}.${prop}`);
  const hiddenSections = () => new Set(D().projects.layout?.hiddenSections || []);
  const keysOf = (sel) => (library(sel)?.s ? fieldKeys(library(sel).scope, library(sel).s) : sel.type === 'cpage' || sel.type === 'settings' || sel.type === 'psection' ? [] : sel.type === 'project' ? Object.keys(D().work[sel.slug] || {}).map((f) => `work.${sel.slug}.${f}`)
    : Object.keys(D().site).filter((key) => { const o = ownerOf(key, store.slugs()); return sameSel(o, sel); }));
  const changed = (key) => store.text(key) !== store.publishedText(key);
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const selChanged = (sel) => keysOf(sel).some(changed)
    || (sel.type === 'cpage' && !same(pageBySlug(sel.slug), pageBySlug(sel.slug, B())))
    || (sel.type === 'psection' && !same(library(sel)?.s, findSection(B().pages, sel.page, sel.id)))
    || (sel.type === 'project' && !same(projects().find((p) => p.slug === sel.slug), B().projects.projects.find((p) => p.slug === sel.slug)))
    || (sel.type === 'section' && hiddenSections().has(sel.id) !== new Set(B().projects.layout?.hiddenSections || []).has(sel.id));
  const title = (slug) => (D().work[slug]?.title || slug).replace(/\*\*?(.+?)\*\*?/g, '$1');

  // ── structure changes ─────────────────────────────────────────────────────────────────────
  const layoutOf = (d) => (d.projects.layout ||= { sections: [...SECTION_IDS], hiddenSections: [] });
  const moveTo = (arr, item, target, after) => { const out = arr.filter((x) => x !== item); out.splice(out.indexOf(target) + (after ? 1 : 0), 0, item); return out; };
  const actions = {
    moveSection(id, target, after) { const order = moveTo(sections(), id, target, after); store.change(`Move ${secName(id)}`, (d) => { layoutOf(d).sections = order; }); },
    nudgeSection(id, delta) { const order = sections(), i = order.indexOf(id), j = i + delta; if (j < 0 || j >= order.length) return; actions.moveSection(id, order[j], delta > 0); },
    toggleSection(id) { store.change(`${hiddenSections().has(id) ? 'Show' : 'Hide'} ${secName(id)}`, (d) => { const L = layoutOf(d), s = new Set(L.hiddenSections || []); if (s.has(id)) s.delete(id); else s.add(id); L.hiddenSections = [...s]; }); },
    moveProject(slug, target, after) { store.change(`Move ${title(slug)}`, (d) => { const order = moveTo(d.projects.projects.map((p) => p.slug), slug, target, after); d.projects.projects.sort((a, b) => order.indexOf(a.slug) - order.indexOf(b.slug)); }); },
    toggleProject(slug) { store.change(`Show or hide ${title(slug)}`, (d) => { const p = d.projects.projects.find((x) => x.slug === slug); if (p.hidden) delete p.hidden; else p.hidden = true; }); },
    toggleFeatured(slug) { store.change(`Feature ${title(slug)}`, (d) => { const f = d.projects.featuredOrder ||= []; const i = f.indexOf(slug); if (i < 0) f.push(slug); else f.splice(i, 1); }); },
    setProject(slug, label, fn) { store.change(label, (d) => fn(d.projects.projects.find((x) => x.slug === slug), d), { merge: `${slug}:${label}` }); },
    newProject(from = null) {
      const taken = new Set([...projects().map((p) => p.slug), ...Object.keys(D().work), ...Object.keys(B().work)]);
      const stem = from ? `${from}-copy` : 'new-record'; let slug = stem, n = 2; while (taken.has(slug)) slug = `${stem}-${n++}`;
      store.change(from ? `Duplicate ${title(from)}` : 'New record', (d) => {
        const src = from && d.projects.projects.find((p) => p.slug === from);
        d.projects.projects.push(src ? { ...JSON.parse(JSON.stringify(src)), slug, hidden: true } : { slug, beam: d.projects.beams[0].id, stack: [], links: [], tier: 'record', hidden: true });
        d.work[slug] = src ? { ...d.work[from], title: `${d.work[from].title} copy` }
          : { title: 'New record', subtitle: 'One line about it.', status: 'Building', year: String(new Date().getFullYear()), role: 'Design and build', summary: 'What it is and why it exists.', facts: '- Kind: tool', highlights: '- The first thing worth knowing' };
      });
      select({ type: 'project', slug });
    },
    renameProject(slug, next) {
      if (!/^[a-z0-9][a-z0-9-]{0,60}$/.test(next)) return say('A slug is lowercase letters, digits and dashes.', 'warn');
      if (projects().some((p) => p.slug === next) || B().work[next]) return say(`"${next}" is taken.`, 'warn');
      store.change(`Rename ${slug}`, (d) => {
        d.projects.projects.forEach((p) => { if (p.slug === slug) p.slug = next; if (p.related) p.related = p.related.map((r) => (r === slug ? next : r)); });
        d.projects.featuredOrder = (d.projects.featuredOrder || []).map((s) => (s === slug ? next : s));
        d.work[next] = d.work[slug]; delete d.work[slug];
      });
      select({ type: 'project', slug: next });
    },
    // ── the section library ──
    addSection(scope, type) {
      const t = SECTION_TYPES[type]; if (!t) return;
      const taken = new Set([...homeIds(), ...D().pages.pages.flatMap((p) => p.sections.map((x) => x.id))]);
      let id; do { id = `s-${Math.random().toString(36).slice(2, 7)}`; } while (taken.has(id));
      store.change(`Add ${t.label}`, (d) => {
        const sec = { id, type, props: { ...t.defaults } };
        if (scope === 'home') { const order = sections(d); d.pages.home.push(sec); layoutOf(d).sections = [...order, id]; } else pageBySlug(scope, d).sections.push(sec);
      });
      select(scope === 'home' ? { type: 'section', id } : { type: 'psection', page: scope, id });
    },
    removeSection(scope, id) {
      store.change('Delete section', (d) => {
        if (scope === 'home') { d.pages.home = d.pages.home.filter((x) => x.id !== id); const L = layoutOf(d); L.sections = (L.sections || []).filter((x) => x !== id); L.hiddenSections = (L.hiddenSections || []).filter((x) => x !== id); }
        else { const p = pageBySlug(scope, d); p.sections = p.sections.filter((x) => x.id !== id); }
        delete d.styles.rules[`section:${id}`];
      });
      select(scope === 'home' ? { type: 'section', id: 'hero' } : { type: 'cpage', slug: scope });
    },
    movePageSection(slug, id, target, after) { store.change('Move section', (d) => { const p = pageBySlug(slug, d), order = moveTo(p.sections.map((x) => x.id), id, target, after); p.sections.sort((a, b) => order.indexOf(a.id) - order.indexOf(b.id)); }); },
    nudgePageSection(slug, id, delta) { const ids = pageBySlug(slug).sections.map((x) => x.id), j = ids.indexOf(id) + delta; if (j >= 0 && j < ids.length) actions.movePageSection(slug, id, ids[j], delta > 0); },
    togglePageSection(slug, id) { store.change('Show or hide section', (d) => { const x = findSection(d.pages, slug, id); if (x.hidden) delete x.hidden; else x.hidden = true; }); },
    setSectionProp(scope, id, prop, value) { store.change(`Edit ${prop}`, (d) => { (findSection(d.pages, scope, id).props ||= {})[prop] = value; }, { merge: `prop:${id}:${prop}` }); },
    // ── pages ──
    newPage(template = 'blank') {
      const taken = new Set(D().pages.pages.map((p) => p.slug)); let slug = 'new-page', n = 2; while (taken.has(slug)) slug = `new-page-${n++}`;
      const kinds = { blank: ['hero'], article: ['hero', 'text'], showcase: ['hero', 'records', 'cta'] }[template] || ['hero'];
      store.change('New page', (d) => { d.pages.pages.push({ slug, title: 'New page', description: '', status: 'draft', nav: false, navLabel: '', sections: [] }); });
      kinds.forEach((k) => actions.addSection(slug, k));
      select({ type: 'cpage', slug });
    },
    setPage(slug, label, fn) { store.change(label, (d) => fn(pageBySlug(slug, d)), { merge: `page:${slug}:${label}` }); },
    renamePage(slug, next) {
      if (!validSlug(next)) return say('An address is lowercase letters, digits and dashes, and not a name the site already uses.', 'warn');
      if (pageBySlug(next)) return say(`"${next}" is taken.`, 'warn');
      store.change(`Rename ${slug}`, (d) => { pageBySlug(slug, d).slug = next; });
      select({ type: 'cpage', slug: next });
    },
    duplicatePage(slug) {
      const taken = new Set(D().pages.pages.map((p) => p.slug)); let next = `${slug}-copy`, n = 2; while (taken.has(next)) next = `${slug}-copy-${n++}`;
      const ids = new Set([...homeIds(), ...D().pages.pages.flatMap((p) => p.sections.map((x) => x.id))]);
      store.change('Duplicate page', (d) => {
        const copy = JSON.parse(JSON.stringify(pageBySlug(slug, d)));
        copy.sections.forEach((x) => { let id; do { id = `s-${Math.random().toString(36).slice(2, 7)}`; } while (ids.has(id)); ids.add(id); x.id = id; });
        d.pages.pages.push({ ...copy, slug: next, title: `${copy.title} copy`, status: 'draft', nav: false });
      });
      select({ type: 'cpage', slug: next });
    },
    deletePage(slug) { store.change('Delete page', (d) => { d.pages.pages = d.pages.pages.filter((p) => p.slug !== slug); }); select({ type: 'section', id: 'hero' }); },
    setOwner(prop, value) { store.change(`Edit ${prop}`, (d) => { d.projects.owner[prop] = value.trim(); }, { merge: `owner:${prop}` }); },
    deleteProject(slug) {
      store.change(`Delete ${title(slug)}`, (d) => {
        d.projects.projects = d.projects.projects.filter((p) => p.slug !== slug);
        d.projects.projects.forEach((p) => { if (p.related) p.related = p.related.filter((r) => r !== slug); });
        d.projects.featuredOrder = (d.projects.featuredOrder || []).filter((s) => s !== slug);
        if (!B().work[slug]) delete d.work[slug];           // a published record keeps its wording file; only the entry goes
      });
      select({ type: 'section', id: 'work' });
    },
    /** One property of one element at one tier; an empty value removes it (the element inherits again). */
    setStyle(target, tier, prop, value) {
      const v = String(value).trim();
      if (v && !safeValue(v)) return say('That value has characters a style cannot hold.', 'warn');
      store.change(`Style ${prop}`, (d) => {
        const rules = d.styles.rules, r = (rules[target] ||= {}), t = (r[tier] ||= {});
        if (v) t[prop] = v; else delete t[prop];
        if (!Object.keys(t).length) delete r[tier];
        if (!Object.keys(r).length) delete rules[target];
      }, { kind: 'styles', merge: `style:${target}:${tier}:${prop}` });
    },
    setToken(bucket, name, value) {
      const v = String(value).trim();
      if (v && !safeValue(v)) return say('That value has characters a token cannot hold.', 'warn');
      const def = bucket === 'day' ? defaults.day[name] : defaults.root[name];
      store.change(`Set ${name}`, (d) => { if (!v || v === def) delete d.theme[bucket][name]; else d.theme[bucket][name] = v; }, { kind: 'theme', merge: `token:${bucket}:${name}` });
    },
  };

  // ── drag to reorder ───────────────────────────────────────────────────────────────────────
  let drag = null;
  const clearDrop = () => treeEl.querySelectorAll('.drop-before, .drop-after').forEach((x) => x.classList.remove('drop-before', 'drop-after'));
  function sortable(row, kind, id, onDrop) {
    row.draggable = true;
    row.addEventListener('dragstart', (e) => { drag = { kind, id }; e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', id); row.classList.add('dragging'); });
    row.addEventListener('dragend', () => { drag = null; row.classList.remove('dragging'); clearDrop(); });
    row.addEventListener('dragover', (e) => {
      if (!drag || drag.kind !== kind || drag.id === id) return;
      e.preventDefault(); clearDrop();
      const r = row.getBoundingClientRect(); row.classList.add(e.clientY > r.top + r.height / 2 ? 'drop-after' : 'drop-before');
    });
    row.addEventListener('drop', (e) => {
      if (!drag || drag.kind !== kind || drag.id === id) return;
      e.preventDefault(); const after = row.classList.contains('drop-after'); clearDrop(); onDrop(drag.id, id, after);
    });
  }

  // ── the tree ──────────────────────────────────────────────────────────────────────────────
  const collapsed = new Set();
  function row(sel, label, { depth = 0, kind = null, id = null, onDrop = null, off = false, tools = [], twisty = null } = {}) {
    const own = twisty == null || sel.type === 'cpage';             // Home opens its first part and that part shows as selected; a made page is its own row
    const cur = own && sameSel(getSelection(), sel);
    const el = h('div', { class: `row${cur ? ' current' : ''}${off ? ' off' : ''}`, style: `--d:${depth}`, role: 'treeitem', 'aria-selected': String(cur), tabIndex: 0,
      onclick: () => select(sel), onkeydown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); select(sel); } } },
    twisty != null ? h('button', { class: 'twisty', type: 'button', 'aria-label': collapsed.has(twisty) ? 'Expand' : 'Collapse', onclick: (e) => { e.stopPropagation(); if (collapsed.has(twisty)) collapsed.delete(twisty); else collapsed.add(twisty); renderTree(); } }, collapsed.has(twisty) ? '▸' : '▾') : h('span', { class: 'twisty' }, kind ? '⠿' : ''),
    h('span', { class: 'name' }, label),
    own && selChanged(sel) ? h('span', { class: 'dot', title: 'Changed in this draft' }) : null,
    h('span', { class: 'tools' }, tools));
    if (kind) sortable(el, kind, id, onDrop);
    return el;
  }
  const tool = (label, title, pressed, fn) => h('button', { class: 'tool', type: 'button', title, 'aria-label': title, 'aria-pressed': String(pressed), onclick: (e) => { e.stopPropagation(); fn(); } }, label);

  // "+ Section": the library, as a menu under a page's sections.
  const adder = (scope) => h('select', { class: 'adder', 'aria-label': 'Add a section', onchange: (e) => { const v = e.target.value; e.target.value = ''; if (v) actions.addSection(scope, v); } },
    h('option', { value: '' }, '+ Section'), Object.entries(SECTION_TYPES).map(([id, t]) => h('option', { value: id }, t.label)));

  function renderTree() {
    treeEl.replaceChildren();
    if (getTab() === 'design') {
      treeEl.append(h('div', { class: 'tree-head' }, 'Design system'));
      for (const g of TOKEN_GROUPS) {
        const n = g.tokens.filter(([name]) => name in D().theme.root || name in D().theme.day).length;
        treeEl.append(row({ type: 'tokens', group: g.id }, g.label, { tools: n ? [h('span', { class: 'count' }, String(n))] : [] }));
      }
      return;
    }
    const hid = hiddenSections(), feat = D().projects.featuredOrder || [];
    treeEl.append(h('div', { class: 'tree-head' }, 'Pages',
      h('select', { class: 'adder head', 'aria-label': 'New page', onchange: (e) => { const v = e.target.value; e.target.value = ''; if (v) actions.newPage(v); } },
        h('option', { value: '' }, '+ Page'), [['blank', 'Blank'], ['article', 'Article'], ['showcase', 'Showcase']].map(([v, t]) => h('option', { value: v }, t)))));
    treeEl.append(row({ type: 'section', id: 'hero' }, 'Home', { twisty: 'home' }));
    if (!collapsed.has('home')) {
      treeEl.append(row({ type: 'section', id: 'hero' }, 'Hero', { depth: 1 }));
      for (const id of sections()) {
        treeEl.append(row({ type: 'section', id }, secName(id), { depth: 1, kind: 'section', id, onDrop: actions.moveSection, off: hid.has(id),
          tools: [tool(hid.has(id) ? '○' : '●', hid.has(id) ? 'Show this section' : 'Hide this section', hid.has(id), () => actions.toggleSection(id))] }));
      }
    }
    if (!collapsed.has('home')) treeEl.append(adder('home'));
    for (const p of D().pages.pages) {
      treeEl.append(row({ type: 'cpage', slug: p.slug }, p.title || p.slug, { twisty: `page:${p.slug}`, off: p.status === 'hidden', tools: [h('span', { class: `badge${p.status === 'published' ? '' : ' off'}` }, p.status === 'published' ? 'Live' : p.status === 'hidden' ? 'Hidden' : 'Draft')] }));
      if (collapsed.has(`page:${p.slug}`)) continue;
      for (const x of p.sections) {
        treeEl.append(row({ type: 'psection', page: p.slug, id: x.id }, sectionTitle(x), { depth: 1, kind: `psec:${p.slug}`, id: x.id, onDrop: (id, target, after) => actions.movePageSection(p.slug, id, target, after), off: Boolean(x.hidden),
          tools: [tool(x.hidden ? '○' : '●', x.hidden ? 'Show this section' : 'Hide this section', Boolean(x.hidden), () => actions.togglePageSection(p.slug, x.id))] }));
      }
      treeEl.append(adder(p.slug));
    }
    treeEl.append(row({ type: 'page', page: 'colophon.html' }, 'Colophon'));
    treeEl.append(row({ type: 'shared' }, 'Shared words'));
    treeEl.append(row({ type: 'settings' }, 'Site settings'));
    treeEl.append(h('div', { class: 'tree-head' }, `Records · ${projects().filter((p) => !p.hidden).length} shown of ${projects().length}`,
      h('button', { class: 'tool add', type: 'button', title: 'New record', onclick: () => actions.newProject() }, '+')));
    for (const p of projects()) {
      treeEl.append(row({ type: 'project', slug: p.slug }, title(p.slug), { kind: 'project', id: p.slug, onDrop: actions.moveProject, off: Boolean(p.hidden),
        tools: [tool('★', feat.includes(p.slug) ? 'Remove from Principal works' : 'Show in Principal works', feat.includes(p.slug), () => actions.toggleFeatured(p.slug)),
          tool(p.hidden ? '○' : '●', p.hidden ? 'Show this record' : 'Hide this record', Boolean(p.hidden), () => actions.toggleProject(p.slug))] }));
    }
  }

  // ── the inspector ─────────────────────────────────────────────────────────────────────────
  const open = new Map();                                     // group title → open?
  function group(name, ...kids) {
    const d = h('details', { class: 'group', open: open.get(name) ?? true, ontoggle: () => open.set(name, d.open) }, h('summary', {}, name), h('div', { class: 'group-body' }, kids));
    return d;
  }
  const HINTS = { facts: 'One per line: "- Label: Value".', highlights: 'One per line, each starting with "- ".', summary: 'A blank line starts a new paragraph.' };
  function field(key, label = pretty(key)) {
    const value = store.text(key), long = value.includes('\n') || value.length > 60 || /\.(summary|facts|highlights)$/.test(key);
    const ta = h('textarea', { id: `f-${key}`, rows: long ? Math.min(12, Math.max(2, value.split('\n').length + Math.ceil(value.length / 60))) : 1, spellcheck: true, value,
      oninput: () => { store.setText(key, ta.value, { source: 'inspector' }); mark(); } });
    const reset = h('button', { class: 'reset', type: 'button', title: 'Back to the published text', onclick: () => { store.setText(key, store.publishedText(key) ?? ''); } }, '↺');
    const lost = () => (String(store.publishedText(key) ?? '').match(/\{\w+\}/g) || []).filter((v) => !ta.value.includes(v));
    const hint = h('small', {});
    const wrap = h('div', { class: 'field', 'data-key': key }, h('label', { for: `f-${key}` }, label, h('span', { class: 'dot', title: 'Changed in this draft' }), reset), ta, hint);
    function mark() {
      wrap.classList.toggle('dirty', changed(key));
      const l = lost(); wrap.classList.toggle('warn', l.length > 0);
      hint.textContent = l.length ? `Missing ${l.join(', ')}: the build fills these in, keep them.` : HINTS[key.split('.').pop()] || '';
    }
    mark();
    return wrap;
  }
  const input = (label, value, onchange, attrs = {}) => h('label', { class: 'prop' }, h('span', {}, label), h('input', { type: 'text', value: value ?? '', spellcheck: false, onchange: (e) => onchange(e.target.value), ...attrs }));
  const choice = (label, value, options, onchange) => h('label', { class: 'prop' }, h('span', {}, label), h('select', { onchange: (e) => onchange(e.target.value) }, options.map(([v, t]) => h('option', { value: v, selected: v === value }, t))));
  const check = (label, on, onchange) => h('label', { class: 'prop check' }, h('input', { type: 'checkbox', checked: on, onchange }), h('span', {}, label));

  function armed(label, sure, fn) {
    let hot = false, t;
    const b = h('button', { class: 'btn danger', type: 'button', onclick: () => { if (hot) return fn(); hot = true; b.textContent = sure; t = setTimeout(() => { hot = false; b.textContent = label; }, 3000); } }, label);
    return b;
  }

  function tokenRows(g) {
    const bucket = g.modes ? themeMode : 'root', theme = D().theme;
    return g.tokens.map(([name, label, steps]) => {
      const def = bucket === 'day' ? defaults.day[name] ?? theme.root[name] ?? defaults.root[name] : defaults.root[name];
      const own = theme[bucket][name], value = own ?? def ?? '', color = g.modes ? hex6(value) : null;
      const text = h('input', { type: 'text', 'data-fid': `token|${bucket}|${name}`, value, spellcheck: false, list: steps ? `steps-${name}` : null, 'aria-label': label, onchange: (e) => actions.setToken(bucket, name, e.target.value) });
      return h('div', { class: `token${own != null ? ' dirty' : ''}`, 'data-token': name },
        h('span', { class: 'token-name', title: name }, label, h('span', { class: 'dot', title: `Custom. Default: ${def}` })),
        color ? h('input', { type: 'color', value: color, 'aria-label': `${label} colour`, oninput: (e) => { text.value = e.target.value.toUpperCase(); actions.setToken(bucket, name, e.target.value.toUpperCase()); } }) : null,
        text,
        steps ? h('datalist', { id: `steps-${name}` }, steps.map((s) => h('option', { value: s }))) : null,
        h('button', { class: 'reset', type: 'button', title: `Back to the default (${def})`, onclick: () => actions.setToken(bucket, name, '') }, '↺'));
    });
  }

  // ── one element's own style, per width ────────────────────────────────────────────────────
  function styleRow(target, tier, c) {
    const rule = D().styles.rules[target] || {}, own = rule[tier]?.[c.prop];
    const order = TIERS.map((t) => t[0]), wider = order.slice(0, order.indexOf(tier)).reverse().find((t) => rule[t]?.[c.prop] != null);
    const from = wider ? `${rule[wider][c.prop]} (${TIERS.find((t) => t[0] === wider)[1]})` : '', now = computed(target, c.prop);
    const fid = `${target}|${c.prop}`, set = (v) => actions.setStyle(target, tier, c.prop, c.unit && /^-?\d*\.?\d+$/.test(v.trim()) ? `${v.trim()}${c.unit}` : v);
    const pair = (o) => (Array.isArray(o) ? o : [o, o]);
    let control, swatch = null;
    if (c.options) control = h('select', { 'data-fid': fid, 'aria-label': c.label, onchange: (e) => set(e.target.value) }, h('option', { value: '', selected: own == null }, from || 'Default'), c.options.map(pair).map(([v, t]) => h('option', { value: v, selected: v === own }, t)));
    else {
      control = h('input', { type: 'text', 'data-fid': fid, value: own ?? '', placeholder: from || now, spellcheck: false, list: c.steps ? `steps-${fid}` : null, 'aria-label': c.label, onchange: (e) => set(e.target.value) });
      if (c.color) swatch = h('input', { type: 'color', value: hex6(own) || rgbHex(now) || '#000000', 'aria-label': `${c.label}: custom colour`, oninput: (e) => { control.value = e.target.value.toUpperCase(); }, onchange: (e) => set(e.target.value.toUpperCase()) });
    }
    return h('div', { class: `token${own != null ? ' dirty' : ''}`, 'data-style': c.prop },
      h('span', { class: 'token-name', title: wider ? `Set for ${from}` : now ? `Drawn now: ${now}` : '' }, c.label, h('span', { class: 'dot', title: 'Set for this element at this width' })),
      swatch, control,
      c.steps && !c.options ? h('datalist', { id: `steps-${fid}` }, c.steps.map(pair).map(([v, t]) => h('option', { value: v, label: t === v ? null : t }))) : null,
      h('button', { class: 'reset', type: 'button', title: wider ? `Back to ${from}` : 'Back to the design\'s own value', onclick: () => actions.setStyle(target, tier, c.prop, '') }, '↺'));
  }
  // text: the wording itself heads the block, so a click on the page lands on its words and its style together.
  function styleBlock(target, kind, name, key = null) {
    const tier = tierFor(getWidth()), rule = D().styles.rules[target] || {}, [, label, media] = TIERS.find((t) => t[0] === tier);
    const hidden = rule[tier]?.display === 'none';
    return [
      h('div', { class: 'element' }, h('span', { class: 'element-name', title: target }, name),
        h('div', { class: 'seg' }, TIERS.map(([id, t]) => h('button', { type: 'button', 'aria-pressed': String(id === tier), title: `Style this at ${t.toLowerCase()} width`, onclick: () => setDevice(DEVICE[id]) }, t, rule[id] ? h('span', { class: 'dot' }) : null)))),
      key ? group('Text', field(key, key)) : null,
      h('p', { class: 'note tier' }, media ? `${label}: applies at ${media.match(/\d+/)[0]}px and narrower. Empty fields inherit from the wider widths.` : 'Desktop: applies at every width unless Tablet or Mobile says otherwise.'),
      STYLE_CONTROLS[kind].map(([title, list]) => group(title, list.map((c) => styleRow(target, tier, c)))),
      group('Visibility', check(media ? `Hidden at ${label.toLowerCase()} width and narrower` : 'Hidden at every width', hidden, () => actions.setStyle(target, tier, 'display', hidden ? '' : 'none'))),
    ].flat(Infinity).filter(Boolean);
  }

  // Redrawing the panel must not take the keyboard away: the field that had focus gets it back.
  // Nor move the panel under the reader: the scroll position stays while the selection does.
  let drawn = '';
  function renderInspector() {
    const fid = document.activeElement?.closest?.('[data-fid]')?.dataset.fid, top = inspectorEl.scrollTop, now = JSON.stringify(getSelection());
    drawInspector();
    inspectorEl.scrollTop = now === drawn ? top : 0; drawn = now;
    if (fid) inspectorEl.querySelector(`[data-fid="${CSS.escape(fid)}"]`)?.focus({ preventScroll: true });
  }
  function drawInspector() {
    const sel = getSelection();
    inspectorEl.replaceChildren(); crumbsEl.replaceChildren();
    const crumb = (label, to) => { if (crumbsEl.childNodes.length) crumbsEl.append(h('span', { class: 'sep' }, '/')); crumbsEl.append(to ? h('button', { type: 'button', onclick: () => select(to) }, label) : h('span', {}, label)); };
    if (!sel) { inspectorEl.append(h('p', { class: 'empty' }, 'Select something: click the page, or pick from the list on the left.')); return; }

    if (sel.type === 'tokens') {
      const g = TOKEN_GROUPS.find((x) => x.id === sel.group) || TOKEN_GROUPS[0];
      crumb('Design system'); crumb(g.label);
      inspectorEl.append(h('h2', {}, g.label));
      if (g.modes) setThemeMode(themeMode === 'day' ? 'light' : 'dark');   // the preview shows the set of colours being edited
      if (g.modes) inspectorEl.append(h('div', { class: 'seg' }, [['root', 'Night'], ['day', 'Day']].map(([v, t]) => h('button', { type: 'button', 'aria-pressed': String(themeMode === v), onclick: () => { themeMode = v; setThemeMode(v === 'day' ? 'light' : 'dark'); renderInspector(); } }, t))));
      inspectorEl.append(group('Tokens', tokenRows(g)), h('p', { class: 'note' }, 'A token changes everything that uses it. A dot marks a value that differs from the design\'s default; ↺ goes back.'));
      return;
    }

    const lib = library(sel);
    if (lib) {
      const { scope, s: sec } = lib;
      if (!sec) { inspectorEl.append(h('p', { class: 'empty' }, 'That section is gone. Undo brings it back.')); return; }
      const page = scope === 'home' ? null : pageBySlug(scope), hid = scope === 'home' ? hiddenSections().has(sec.id) : Boolean(sec.hidden);
      crumb(page ? page.title || scope : 'Home', page ? { type: 'cpage', slug: scope } : { type: 'section', id: 'hero' }); crumb(sectionTitle(sec), { ...sel, key: undefined }); if (sel.key) crumb(sel.key.split('.').pop());
      inspectorEl.append(h('h2', {}, SECTION_TYPES[sec.type]?.label || sec.type, hid ? h('span', { class: 'badge off' }, 'Hidden') : null));
      if (sel.key) inspectorEl.append(...styleBlock(`text:${sel.key}`, 'text', sel.key.split('.').pop(), null));
      inspectorEl.append(group('Content', (SECTION_TYPES[sec.type]?.fields || []).map(([prop, label, kind]) => {
        const key = `page.${scope}.${sec.id}.${prop}`;
        if (kind.startsWith('choice:')) return choice(label, sec.props?.[prop], kind.slice(7).split(',').map((v) => [v, v]), (v) => actions.setSectionProp(scope, sec.id, prop, v));
        if (kind === 'image' || kind === 'href') return input(label, sec.props?.[prop], (v) => actions.setSectionProp(scope, sec.id, prop, v.trim()), { placeholder: kind === 'image' ? 'assets/…/picture.webp' : 'https://… or page.html', 'data-fid': key });
        return field(key, label);
      })));
      inspectorEl.append(group('Section',
        check('Shown', !hid, () => (page ? actions.togglePageSection(scope, sec.id) : actions.toggleSection(sec.id))),
        h('div', { class: 'actions-row' },
          h('button', { class: 'btn', type: 'button', onclick: () => (page ? actions.nudgePageSection(scope, sec.id, -1) : actions.nudgeSection(sec.id, -1)) }, '↑ Move up'),
          h('button', { class: 'btn', type: 'button', onclick: () => (page ? actions.nudgePageSection(scope, sec.id, 1) : actions.nudgeSection(sec.id, 1)) }, '↓ Move down'),
          armed('Delete', 'Delete? Click again', () => actions.removeSection(scope, sec.id)))));
      if (!sel.key) inspectorEl.append(...styleBlock(`section:${sec.id}`, 'section', 'This section'));
    } else if (sel.type === 'cpage') {
      const p = pageBySlug(sel.slug);
      if (!p) { inspectorEl.append(h('p', { class: 'empty' }, 'That page is gone. Undo brings it back.')); return; }
      const set = (label, fn) => actions.setPage(p.slug, label, fn);
      crumb('Pages'); crumb(p.title || p.slug);
      inspectorEl.append(h('h2', {}, p.title || p.slug, h('span', { class: `badge${p.status === 'published' ? '' : ' off'}` }, p.status === 'published' ? 'Live' : p.status === 'hidden' ? 'Hidden' : 'Draft')));
      inspectorEl.append(group('Page',
        input('Title', p.title, (v) => set('Edit title', (x) => { x.title = v; }), { 'data-fid': 'page|title' }),
        input('Address', p.slug, (v) => actions.renamePage(p.slug, v.trim()), { 'data-fid': 'page|slug', title: `prismet.xyz/${p.slug}.html` }),
        choice('Status', p.status, [['draft', 'Draft: not on the site'], ['published', 'Published'], ['hidden', 'Hidden: reachable by its address only']], (v) => set('Change status', (x) => { x.status = v; })),
        check('In the navigation', Boolean(p.nav), () => set('Navigation', (x) => { x.nav = !x.nav; })),
        input('Navigation label', p.navLabel, (v) => set('Edit navigation label', (x) => { x.navLabel = v; }), { placeholder: p.title, 'data-fid': 'page|nav' })));
      inspectorEl.append(group('Search and sharing',
        input('Description', p.description, (v) => set('Edit description', (x) => { x.description = v; }), { placeholder: 'One sentence for search results and link previews', 'data-fid': 'page|desc' })));
      inspectorEl.append(group('Actions', h('div', { class: 'actions-row' },
        h('button', { class: 'btn', type: 'button', onclick: () => actions.duplicatePage(p.slug) }, 'Duplicate'),
        armed('Delete', 'Delete? Click again', () => actions.deletePage(p.slug)))),
      h('p', { class: 'note' }, p.status === 'draft' ? 'A draft page is not built. Set it to Published and press Publish to put it on the site.' : `On the site at /${p.slug}.html once published.`));
    } else if (sel.type === 'settings') {
      const o = D().projects.owner || {};
      crumb('Site'); crumb('Site settings');
      inspectorEl.append(h('h2', {}, 'Site settings'));
      inspectorEl.append(group('Search and sharing', field('page.title', 'Site title'), field('page.description', 'Site description'), 'og.image_alt' in D().site ? field('og.image_alt', 'Share image description') : null));
      inspectorEl.append(group('Identity and links',
        input('Name', o.name, (v) => actions.setOwner('name', v), { 'data-fid': 'owner|name' }),
        input('GitHub', o.github, (v) => actions.setOwner('github', v), { 'data-fid': 'owner|github' }),
        input('LinkedIn', o.linkedin, (v) => actions.setOwner('linkedin', v), { 'data-fid': 'owner|linkedin' }),
        input('Fiverr', o.fiverr, (v) => actions.setOwner('fiverr', v), { 'data-fid': 'owner|fiverr' })),
      h('p', { class: 'note' }, 'These appear in the top bar, the footer, the hero and the hire panel.'));
    } else if (sel.type === 'project') {
      const p = projects().find((x) => x.slug === sel.slug);
      if (!p) { inspectorEl.append(h('p', { class: 'empty' }, 'That record is gone. Undo brings it back.')); return; }
      const isNew = !B().work[p.slug], set = (label, fn) => actions.setProject(p.slug, label, fn);
      crumb('Records', { type: 'section', id: 'work' }); crumb(title(p.slug), { type: 'project', slug: p.slug }); if (sel.key) crumb(pretty(sel.key));
      inspectorEl.append(h('h2', {}, title(p.slug), isNew ? h('span', { class: 'badge' }, 'Draft') : null, p.hidden ? h('span', { class: 'badge off' }, 'Hidden') : null));
      if (sel.key) inspectorEl.append(...styleBlock(`text:${sel.key}`, 'text', pretty(sel.key), sel.key));
      inspectorEl.append(group('Content', keysOf(sel).filter((key) => key !== sel.key).map((key) => field(key))));
      const feat = (D().projects.featuredOrder || []).includes(p.slug);
      inspectorEl.append(group('Record',
        check('Shown on the site', !p.hidden, () => actions.toggleProject(p.slug)),
        check('In Principal works (a door on the home page)', feat, () => actions.toggleFeatured(p.slug)),
        choice('Wing', p.beam, D().projects.beams.map((b) => [b.id, b.short || b.id]), (v) => set('Change wing', (x) => { x.beam = v; })),
        choice('Tier', p.tier || 'record', [['flagship', 'Flagship'], ['featured', 'Featured'], ['record', 'Record'], ['cabinet', 'Cabinet']], (v) => set('Change tier', (x) => { x.tier = v; })),
        input('Stack (comma separated)', (p.stack || []).join(', '), (v) => set('Edit stack', (x) => { x.stack = v.split(',').map((s) => s.trim()).filter(Boolean); })),
        input('Slug (the page address)', p.slug, (v) => actions.renameProject(p.slug, v.trim()), { disabled: !isNew, title: isNew ? '' : 'A published record keeps its address.' })));
      inspectorEl.append(group('Links', (p.links || []).map((l, i) => h('div', { class: 'pair' },
        h('input', { type: 'text', value: l.label, placeholder: 'Label', 'aria-label': 'Link label', onchange: (e) => set('Edit link', (x) => { x.links[i].label = e.target.value; }) }),
        h('input', { type: 'text', value: l.href, placeholder: 'https://…', 'aria-label': 'Link address', onchange: (e) => set('Edit link', (x) => { x.links[i].href = e.target.value.trim(); }) }),
        h('button', { class: 'reset', type: 'button', title: 'Remove this link', onclick: () => set('Remove link', (x) => { x.links.splice(i, 1); }) }, '×'))),
      h('button', { class: 'btn', type: 'button', onclick: () => set('Add link', (x) => { (x.links ||= []).push({ label: 'GitHub', href: 'https://github.com/' }); }) }, '+ Link')));
      inspectorEl.append(group('Actions', h('div', { class: 'actions-row' },
        h('button', { class: 'btn', type: 'button', onclick: () => actions.newProject(p.slug) }, 'Duplicate'),
        armed('Delete', 'Delete? Click again', () => actions.deleteProject(p.slug)))));
    } else {
      const name = sel.type === 'section' ? secName(sel.id) : sel.type === 'page' ? 'Colophon' : 'Shared words';
      crumb(sel.type === 'section' ? 'Home' : 'Site', sel.type === 'section' ? { type: 'section', id: 'hero' } : null); crumb(name, { ...sel, key: undefined }); if (sel.key) crumb(pretty(sel.key));
      const movable = sel.type === 'section' && sel.id !== 'hero', hid = movable && hiddenSections().has(sel.id);
      inspectorEl.append(h('h2', {}, name, hid ? h('span', { class: 'badge off' }, 'Hidden') : null));
      if (movable) inspectorEl.append(group('Section',
        check('Shown on the home page', !hid, () => actions.toggleSection(sel.id)),
        h('div', { class: 'actions-row' }, h('button', { class: 'btn', type: 'button', onclick: () => actions.nudgeSection(sel.id, -1) }, '↑ Move up'), h('button', { class: 'btn', type: 'button', onclick: () => actions.nudgeSection(sel.id, 1) }, '↓ Move down'))));
      if (sel.key) inspectorEl.append(...styleBlock(`text:${sel.key}`, 'text', sel.key, sel.key));
      else if (sel.type === 'section') inspectorEl.append(...styleBlock(`section:${sel.id}`, 'section', `${name} section`));
      inspectorEl.append(group('Content', keysOf(sel).filter((key) => key !== sel.key).map((key) => field(key, key))));
    }
    if (sel.key) { const f = inspectorEl.querySelector(`[data-key="${CSS.escape(sel.key)}"]`); if (f) f.classList.add('focus'); }
  }

  /** After a text change made somewhere else: refresh that one field without rebuilding the panel (keeps the cursor). */
  function refreshField(key, source) {
    const f = inspectorEl.querySelector(`[data-key="${CSS.escape(key)}"]`); if (!f) return;
    const ta = f.querySelector('textarea');
    if (source !== 'inspector' && ta !== document.activeElement) ta.value = store.text(key);
    f.classList.toggle('dirty', changed(key));
  }

  return { renderTree, renderInspector, refreshField, actions, keysOf, title };
}
