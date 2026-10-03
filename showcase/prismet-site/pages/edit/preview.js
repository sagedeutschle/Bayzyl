// preview.js — the live preview: the draft rendered by the build's own renderer (lib/render.js) into a frame.
//
// What the frame shows is what build.mjs would write for the draft, with three additions in Edit mode: wording carries
// its key (click it and type), hidden sections and records stay on the page dimmed, and clicks select instead of
// navigating. Token changes repaint without a reload; structural changes re-render and keep the scroll position.
import { renderSite } from '../lib/render.js';
import { themeCss } from '../lib/theme.js';
import { inline, plain } from '../lib/format.js';

const FRAME_CSS = `
.pe-on [data-edit] { cursor: text; }
.pe-on .pe-hover { outline: 1px dashed rgba(110, 168, 254, .9); outline-offset: 2px; }
.pe-on .pe-sel { outline: 2px solid #6EA8FE; outline-offset: 2px; }
.pe-on [contenteditable] { outline: 2px solid #6EA8FE; outline-offset: 2px; background: rgba(110, 168, 254, .1); }
.pe-on #main > section.is-off, .pe-on .card.is-off { opacity: .38; }
.pe-on #main > section.is-off { position: relative; }
.pe-on #main > section.is-off::before { content: 'Hidden'; position: absolute; top: 8px; right: 12px; z-index: 5; font: 600 11px/1 ui-monospace, monospace; letter-spacing: .08em; text-transform: uppercase; padding: 5px 8px; background: #6EA8FE; color: #06121F; }
`;
const norm = (s) => s.replace(/ /g, ' ').replace(/\r/g, '').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
const SELECTABLE = '[data-edit], [data-slug], [data-section], .entrance';

export function createPreview({ frame, stage, store, manifest, baseCss, onSelect, onNavigate, onKey, say }) {
  const files = manifest.files;
  const assets = {
    size: (p) => { const f = files[p]; return f ? { w: f[0], h: f[1] } : { w: 0, h: 0 }; },
    has: (p) => p in files,
    url: (p) => { if (!p) return null; const f = files[p]; if (!f) throw new Error(`missing image: ${p}`); return `${p}?v=${f[2]}`; },
  };
  let page = 'index.html', mode = 'edit', themeMode = '', device = 0, zoom = 'fit';
  let selection = null, missing = new Set(), pages = new Map(), pending = false, keepScroll = 0, editing = null, wantScroll = false;
  const doc = () => frame.contentDocument;
  const esc = (s) => frame.contentWindow.CSS.escape(s);

  // ── rendering ─────────────────────────────────────────────────────────────────────────────
  function render({ keep = true } = {}) {
    const d = store.docs;
    let out;
    try { out = renderSite({ data: d.projects, site: d.site, work: d.work, assets, urls: { css: 'site.css', js: manifest.urls.js, og: manifest.urls.og }, edit: mode === 'edit' }); }
    catch (e) { say(`The preview could not render: ${e.message}. Undo the last change.`, 'warn'); return; }
    pages = out.pages; missing = out.missing;
    if (!pages.has(page)) page = 'index.html';
    const root = page.includes('/') ? '../' : '';
    const styles = `<style>${baseCss}</style><style id="pe-theme">${themeCss(d.theme)}</style>${mode === 'edit' ? `<style>${FRAME_CSS}</style>` : ''}`;
    keepScroll = keep && doc()?.body ? frame.contentWindow.scrollY : 0;
    frame.srcdoc = pages.get(page).replace(`<link rel="stylesheet" href="${root}site.css">`, () => styles);
  }
  // Several changes in one turn (an undo that moves three things) render once. A timer, not an animation frame: frames
  // stop while the tab is in the background and the preview would fall behind the draft.
  const schedule = () => { if (pending) return; pending = true; setTimeout(() => { pending = false; render(); }, 0); };

  frame.addEventListener('load', () => {
    const d = doc(); if (!d?.body) return;
    d.documentElement.classList.toggle('pe-on', mode === 'edit');
    if (themeMode) d.documentElement.dataset.theme = themeMode;
    if (keepScroll) frame.contentWindow.scrollTo(0, keepScroll);
    d.addEventListener('click', onClick, true);
    d.addEventListener('mouseover', (e) => { if (mode !== 'edit') return; hover(e.target.closest?.(SELECTABLE)); });
    d.addEventListener('mouseleave', () => hover(null));
    d.addEventListener('keydown', onKeydown, true);
    d.addEventListener('input', onInput);
    d.addEventListener('focusout', onBlur);
    mark();
    if (wantScroll) { wantScroll = false; targets(selection)[0]?.scrollIntoView({ block: 'center' }); }
  });

  // ── selection ─────────────────────────────────────────────────────────────────────────────
  let hovered = null;
  function hover(el) { if (hovered === el) return; hovered?.classList.remove('pe-hover'); hovered = el || null; hovered?.classList.add('pe-hover'); }
  const targets = (sel) => {
    const d = doc(); if (!d || !sel) return [];
    if (sel.type === 'text') return [...d.querySelectorAll(`[data-edit="${esc(sel.key)}"]`)];
    if (sel.type === 'section') return [...d.querySelectorAll(sel.id === 'hero' ? '.entrance' : `[data-section="${esc(sel.id)}"]`)];
    if (sel.type === 'project') return [...d.querySelectorAll(`[data-slug="${esc(sel.slug)}"]`)];
    return [];
  };
  function mark() { const d = doc(); if (!d) return; d.querySelectorAll('.pe-sel').forEach((el) => el.classList.remove('pe-sel')); if (mode === 'edit') targets(selection).forEach((el) => el.classList.add('pe-sel')); }
  /** Shows a selection made elsewhere (the tree, the inspector, the palette). */
  function select(sel, { scroll = false } = {}) {
    selection = sel; mark();
    if (!scroll) return;
    const el = targets(sel)[0];
    if (el) el.scrollIntoView({ block: 'center', behavior: 'smooth' }); else wantScroll = true;   // the page is still loading
  }

  function onClick(e) {
    const a = e.target.closest?.('a[href]');
    if (mode !== 'edit') {
      if (!a) return;
      const href = a.getAttribute('href');
      if (href.startsWith('#')) return;                                    // in-page jumps work as they do on the site
      e.preventDefault();
      if (/^https?:/.test(href)) { window.open(href, '_blank', 'noopener'); return; }
      const u = new URL(href, `https://x/${page}`), path = u.pathname.slice(1) || 'index.html';
      if (pages.has(path)) onNavigate(path, u.hash); else window.open(u.pathname, '_blank', 'noopener');   // /steam, /debt: the live tools
      return;
    }
    if (e.target.closest('[contenteditable]')) return;                      // typing: let the click place the cursor
    e.preventDefault(); e.stopPropagation();
    const text = e.target.closest('[data-edit]'), card = e.target.closest('[data-slug]'), sec = e.target.closest('[data-section], .entrance');
    if (text) { onSelect({ type: 'text', key: text.dataset.edit }, 'preview'); beginEdit(text); }
    else if (card) onSelect({ type: 'project', slug: card.dataset.slug }, 'preview');
    else if (sec) onSelect({ type: 'section', id: sec.dataset.section || 'hero' }, 'preview');
    else onSelect(null, 'preview');
  }

  // ── typing on the page ────────────────────────────────────────────────────────────────────
  function beginEdit(el) {
    if (el instanceof frame.contentWindow.SVGElement) return;               // drawn labels are edited in the inspector
    const key = el.dataset.edit, raw = store.text(key);
    el.setAttribute('contenteditable', 'plaintext-only');
    if (el.contentEditable !== 'plaintext-only') el.setAttribute('contenteditable', 'true');
    el.spellcheck = true;
    if (norm(el.innerText) !== raw) el.textContent = raw;                   // show the source (*gold*, {placeholders}) while typing
    editing = { el, key, before: raw };
    el.focus();
  }
  function onInput(e) {
    if (!editing || e.target !== editing.el) return;
    const text = norm(editing.el.innerText);
    if (text) store.setText(editing.key, text, { source: 'preview' });
  }
  function onBlur(e) {
    if (!editing || e.target !== editing.el) return;
    const { el, key, before } = editing; editing = null;
    if (!norm(el.innerText)) { store.setText(key, before, { source: 'preview' }); say('That text cannot be empty; the earlier wording is back.', 'warn'); }
    el.removeAttribute('contenteditable');
    paint(key);
  }
  function onKeydown(e) {
    const el = editing?.el;
    if (el && e.target === el) {
      if (e.key === 'Escape') { el.textContent = editing.before; store.setText(editing.key, editing.before, { source: 'preview' }); el.blur(); e.stopPropagation(); return; }
      if (e.key === 'Enter' && !e.shiftKey && !el.matches('.summary, p, li')) { e.preventDefault(); el.blur(); return; }
      if (e.key === ' ' && el.closest('button')) { e.preventDefault(); doc().execCommand('insertText', false, ' '); }
      if (!(e.metaKey || e.ctrlKey) || /^[zZ]$/.test(e.key)) return;        // the browser's own undo while typing
    }
    onKey(e);
  }

  /** Redraws one key's text wherever it appears, without reloading the page. */
  function paint(key) {
    const d = doc(); if (!d) return;
    const text = store.text(key);
    if (/\{\w+\}/.test(text)) return schedule();                            // placeholders are filled by the renderer
    d.querySelectorAll(`[data-edit="${esc(key)}"]`).forEach((el) => {
      if (el === editing?.el) return;
      if (el instanceof frame.contentWindow.SVGElement) el.textContent = plain(text);
      else if (el.classList.contains('summary')) el.innerHTML = inline(text).split(/\n\s*\n/).map((p) => `<p>${p}</p>`).join('');
      else el.innerHTML = inline(text);
    });
  }

  store.subscribe((ev) => {
    if (ev.type === 'load') return render({ keep: false });
    if (ev.type !== 'change') return;
    if (ev.kind === 'text') return paint(ev.key);
    if (ev.kind === 'theme') { const s = doc()?.getElementById('pe-theme'); if (s) s.textContent = themeCss(store.docs.theme); else schedule(); return; }
    schedule();
  });

  // ── the frame's size ──────────────────────────────────────────────────────────────────────
  function layout() {
    const box = stage.getBoundingClientRect(), pad = 24;
    const width = device || Math.max(320, box.width - pad);
    const scale = zoom === 'fit' ? Math.min(1, (box.width - pad) / width) : zoom / 100;
    frame.style.width = `${width}px`;
    frame.style.height = `${Math.max(200, (box.height - pad) / scale)}px`;
    frame.style.transform = scale === 1 ? '' : `scale(${scale})`;
    frame.style.marginRight = scale === 1 ? '' : `${-(width * (1 - scale))}px`;
    frame.style.marginBottom = scale === 1 ? '' : `${-((box.height - pad) / scale) * (1 - scale)}px`;
    return { width, scale };
  }
  new ResizeObserver(layout).observe(stage);

  return {
    render, select, layout,
    get page() { return page; },
    get mode() { return mode; },
    get missing() { return missing; },
    get pageList() { return [...pages.keys()]; },
    setPage(p, hash = '') { if (p !== page) { page = p; wantScroll = false; render({ keep: false }); } if (hash) setTimeout(() => doc()?.getElementById(hash.slice(1))?.scrollIntoView(), 60); },
    setMode(m) { if (m !== mode) { mode = m; render(); } },
    setTheme(t) { themeMode = t; const d = doc(); if (d) { if (t) d.documentElement.dataset.theme = t; else delete d.documentElement.dataset.theme; } },
    setDevice(w) { device = w; return layout(); },
    setZoom(z) { zoom = z; return layout(); },
  };
}
