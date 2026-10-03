// prismet.xyz/edit: the site's editor. Three panes: the structure of the site, a live preview, an inspector.
//
// The page reads the wording (content/site.md, content/work/*.md), the structure (data/projects.json) and the design
// tokens (data/theme.json) through the site's own server (/api/edit/*, see showcase/server/edit-api.js), which holds
// the GitHub token. A PIN unlocks an eight-hour session; nothing secret ever reaches the browser. Changes are a draft
// that autosaves in this browser and shows at once in the preview, rendered by the same code the build uses
// (lib/render.js). Publish writes every changed file in one commit; the repository's GitHub Action rebuilds and
// deploys, so it is live about three minutes later. No dependencies.
//
//   edit/store.js    the draft: documents, undo, autosave, what Publish writes
//   edit/preview.js  the frame: rendering, selection, typing on the page
//   edit/panels.js   the tree and the inspector
import { parseSections, writeSections } from './lib/format.js';
import { stripTheme } from './lib/theme.js';
import { createStore, SITE_FILE, PROJECTS_FILE, THEME_FILE } from './edit/store.js';
import { createPreview } from './edit/preview.js';
import { createPanels, ownerOf, pageOf, sameSel, sectionName, TOKEN_GROUPS, h } from './edit/panels.js';

export { SITE_FILE, parseSections, writeSections };
export const label = (key) => key.replace(/[._]/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
const LIST_KEYS = new Set(['facts', 'highlights', 'links']);
export const isLong = (key, body) => LIST_KEYS.has(key) || body.includes('\n') || body.length > 90;
/** Placeholders like {count} must survive an edit, or the build prints them wrong. */
export const lostVars = (before, after) => (before.match(/\{\w+\}/g) || []).filter((v) => !after.includes(v));

// ── the site's edit API ─────────────────────────────────────────────────────────────────────────────────────────────
export function makeClient(fetchImpl = globalThis.fetch, base = '') {
  const call = async (path, init = {}) => {
    const r = await fetchImpl(base + path, { credentials: 'same-origin', ...init, headers: { accept: 'application/json', 'x-prismet-edit': '1', ...(init.body ? { 'content-type': 'application/json' } : {}), ...(init.headers || {}) } });
    let body = null; try { body = await r.json(); } catch { body = null; }
    if (!r.ok) { const e = new Error(body?.error || `The server answered ${r.status}.`); e.status = r.status; e.body = body; throw e; }
    return body;
  };
  return {
    status: () => call('/api/edit/status'),
    unlock: (pin) => call('/api/edit/session', { method: 'POST', body: JSON.stringify({ pin }) }),
    lock: () => call('/api/edit/session', { method: 'DELETE' }),
    listFiles: async () => (await call('/api/edit/files')).files,
    getFile: (path) => call(`/api/edit/file?path=${encodeURIComponent(path)}`),
    putFile: (path, text, sha, message) => call('/api/edit/file', { method: 'PUT', body: JSON.stringify({ path, text, sha, message }) }),
    /** Publish: [{ path, text, sha }] in one commit. */
    commit: (files, message) => call('/api/edit/commit', { method: 'POST', body: JSON.stringify({ files, message }) }),
    getDraft: () => call('/api/edit/draft'),
    putDraft: (text, sha) => call('/api/edit/draft', { method: 'PUT', body: JSON.stringify({ text, sha }) }),
    runFor: async (sha) => (await call(`/api/edit/run?sha=${encodeURIComponent(sha)}`)).run,
  };
}

// ── the page ────────────────────────────────────────────────────────────────────────────────────────────────────────
async function init() {
  const $ = (s) => document.querySelector(s);
  const status = $('#status'), draft = $('#draft'), deploy = $('#deploy'), save = $('#save');
  const say = (msg, kind = '') => { status.textContent = msg; status.className = `status ${kind}`; };
  const client = makeClient();
  const store = createStore();
  const UI = 'prismet.edit.ui';
  const ui = (() => { try { return JSON.parse(localStorage.getItem(UI) || '{}'); } catch { return {}; } })();
  const remember = (patch) => { Object.assign(ui, patch); try { localStorage.setItem(UI, JSON.stringify(ui)); } catch { /* storage blocked */ } };
  let preview = null, panels = null, selection = null, tab = 'site', pollTimer = null;
  let items = [], hits = [], at = 0;                            // search and commands
  let remote = { on: false, sha: null, timer: null, saved: '' };   // the draft in the private drafts repository

  if (location.protocol !== 'https:' && !['localhost', '127.0.0.1'].includes(location.hostname)) say('This page is not on https; the session cookie will not be set.', 'warn');

  // ── the PIN ───────────────────────────────────────────────────────────────────────────────
  const pinSay = (msg) => { $('#pin-status').textContent = msg; };
  const askPin = (msg = '') => { $('#connect').hidden = false; pinSay(msg); $('#pin').focus(); };
  $('#pin-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const pin = $('#pin').value;
    if (!pin) return pinSay('Enter the PIN.');
    pinSay('Checking…');
    try { await client.unlock(pin); $('#pin').value = ''; $('#connect').hidden = true; if (store.loaded) say('Unlocked. Your draft is as you left it.', 'ok'); else await load(); }
    catch (err) { pinSay(err.status === 401 && err.body?.remaining != null ? `Wrong PIN. ${err.body.remaining} ${err.body.remaining === 1 ? 'try' : 'tries'} left before a pause.` : err.message); }
  });
  $('#lock').addEventListener('click', async () => { try { await client.lock(); } catch { /* already gone */ } location.reload(); });

  try {
    const st = await client.status();
    $('#branch').textContent = st.branch; remote.on = Boolean(st.drafts);
    if (!st.configured) return say(`The editor is not set up on the server yet: ${st.reason}. See showcase/EDITING.md.`, 'warn');
    if (st.authed) await load(); else askPin();
  } catch (err) { say(`The server did not answer: ${err.message}`, 'warn'); }

  // ── loading ───────────────────────────────────────────────────────────────────────────────
  async function load() {
    say('Loading the site…');
    try {
      const optional = (p) => client.getFile(p).catch((e) => (e.status === 404 ? null : Promise.reject(e)));
      const [paths, projects, theme, manifest, css] = await Promise.all([
        client.listFiles(), client.getFile(PROJECTS_FILE), optional(THEME_FILE),
        fetch('edit/assets.json', { cache: 'no-cache' }).then((r) => r.json()),
        fetch('site.css', { cache: 'no-cache' }).then((r) => r.text()),
      ]);
      const words = await Promise.all(paths.map((p) => client.getFile(p)));
      if (!preview) start(manifest, stripTheme(css));
      store.load([...words, projects, theme].filter(Boolean));
      if (remote.on) await pullDraft();
    } catch (err) {
      if (err.status === 401) return askPin('The session ended. Enter the PIN again.');
      say(`Could not load the site: ${err.message}`, 'warn');
    }
  }

  // ── the draft on the server ───────────────────────────────────────────────────────────────
  // The browser copy is the fast one; a few seconds after the last change the same draft goes to the private drafts
  // repository, so another device opens to it. A draft made from older versions of the files is never applied.
  const SYNC_MS = 6000;
  async function pullDraft() {
    try {
      const r = await client.getDraft(); remote.sha = r.sha; remote.saved = r.text || '';
      if (r.text && store.adopt(JSON.parse(r.text))) say('Picked up the draft you saved from another device. Nothing is published until you press Publish.', 'ok');
    } catch (err) { remote.on = false; say(`Drafts stay in this browser for now: ${err.message}`, 'warn'); }
  }
  function queueDraft() { if (!remote.on) return; clearTimeout(remote.timer); remote.timer = setTimeout(pushDraft, SYNC_MS); }
  async function pushDraft() {
    const snap = store.snapshot(), text = JSON.stringify(store.dirty ? snap : { shas: snap.shas, docs: null, at: snap.at });
    if (text === remote.saved) return;
    try { remote.sha = (await client.putDraft(text, remote.sha)).sha; remote.saved = text; syncTop(); }
    catch (err) { if (err.status === 409) remote.on = false; draft.textContent = 'Draft saved in this browser only'; draft.className = 'draft warn'; if (err.status !== 401) say(err.message, 'warn'); }
  }
  document.addEventListener('visibilitychange', () => { if (!document.hidden) return; store.flush(); if (remote.on && remote.timer) { clearTimeout(remote.timer); pushDraft(); } });
  window.addEventListener('pagehide', () => store.flush());

  // ── the workspace ─────────────────────────────────────────────────────────────────────────
  function start(manifest, baseCss) {
    preview = createPreview({ frame: $('#frame'), stage: $('#stage'), store, manifest, baseCss, say, onKey,
      onSelect: (sel) => select(sel, 'preview'),
      onNavigate: (page, hash) => { preview.setPage(page, hash); syncTop(); } });
    panels = createPanels({ treeEl: $('#tree'), inspectorEl: $('#inspector'), crumbsEl: $('#crumbs'), store, baseCss, say,
      select: (sel) => select(sel, 'panel'), getSelection: () => selection, getTab: () => tab,
      setThemeMode: (t) => preview.setTheme(t) });

    store.subscribe((ev) => {
      if (ev.type === 'saving') { draft.textContent = 'Saving draft…'; draft.className = 'draft'; return; }
      if (ev.type === 'save-error') { draft.textContent = 'Draft not saved in this browser'; draft.className = 'draft warn'; say('This browser refused to store the draft. Publish, or keep this tab open.', 'warn'); return; }
      if (ev.type === 'saved') { queueDraft(); syncTop(); return; }
      if (ev.type === 'load') {
        const first = ui.selection && valid(ui.selection) ? ui.selection : { type: 'section', id: 'hero' };
        select(first, 'load');
        say(ev.restored ? 'Your draft from this browser is back. Nothing is published until you press Publish.' : ev.stale ? 'The site changed since your last draft, so it was set aside and the published copy is shown.' : 'Loaded. Click the page to edit it.', ev.stale ? 'warn' : 'ok');
      }
      if (ev.type === 'change') {
        if (ev.kind === 'text') panels.refreshField(ev.key, ev.source);
        else { if (selection && !valid(selection)) selection = null; panels.renderInspector(); }
        if (ev.kind === 'history') say(`${ev.label}.`);
        panels.renderTree();
      }
      if (ev.type === 'published') { panels.renderTree(); panels.renderInspector(); }
      syncTop();
    });

    // top bar
    $('#undo').addEventListener('click', () => store.undo());
    $('#redo').addEventListener('click', () => store.redo());
    $('#save').addEventListener('click', publish);
    $('#find').addEventListener('click', () => openPalette());
    $('#page').addEventListener('change', (e) => select(JSON.parse(e.target.value), 'panel'));
    const setDevice = (w) => { remember({ device: w }); preview.setDevice(w); $('#width').value = w || ''; $('#device').querySelectorAll('button').forEach((b) => b.setAttribute('aria-pressed', String(Number(b.dataset.w) === w))); };
    $('#device').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) setDevice(Number(b.dataset.w)); });
    $('#width').addEventListener('change', (e) => setDevice(Math.max(280, Math.min(2560, Number(e.target.value) || 1440))));
    $('#zoom').addEventListener('change', (e) => preview.setZoom(e.target.value === 'fit' ? 'fit' : Number(e.target.value)));
    $('#mode').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) setMode(b.dataset.mode); });
    document.querySelectorAll('.tabs [role="tab"]').forEach((b) => b.addEventListener('click', () => setTab(b.dataset.tab)));
    document.addEventListener('keydown', onKey);
    setDevice(ui.device || 1440);
  }

  function valid(sel) { return Boolean(sel) && (sel.type !== 'project' || store.docs.projects.projects.some((p) => p.slug === sel.slug)) && (sel.type !== 'tokens' || TOKEN_GROUPS.some((g) => g.id === sel.group)); }
  function setMode(mode) { preview.setMode(mode); document.querySelectorAll('#mode button').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.mode === mode))); }
  function setTab(next) {
    tab = next;
    document.querySelectorAll('.tabs [role="tab"]').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.tab === tab)));
    if (tab === 'design' && selection?.type !== 'tokens') return select({ type: 'tokens', group: 'colors' }, 'panel');
    if (tab === 'site' && selection?.type === 'tokens') return select({ type: 'section', id: 'hero' }, 'panel');
    panels.renderTree();
  }

  /** The one place a selection changes. source: preview (a click on the page), panel, load or palette. */
  function select(sel, source) {
    if (sel?.type === 'text') sel = { ...ownerOf(sel.key, store.slugs()), key: sel.key };
    if (sel && !valid(sel)) sel = null;
    const moved = !sameSel(sel, selection) || sel?.key !== selection?.key;
    selection = sel;
    if (sel) remember({ selection: { ...sel, key: undefined } });
    tab = sel?.type === 'tokens' ? 'design' : sel ? 'site' : tab;
    document.querySelectorAll('.tabs [role="tab"]').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.tab === tab)));
    const page = pageOf(sel);
    if (page && source !== 'preview') preview.setPage(page);
    preview.select(sel?.key ? { type: 'text', key: sel.key } : sel, { scroll: source !== 'preview' && source !== 'load' });
    if (moved || source !== 'preview') { panels.renderTree(); panels.renderInspector(); }
    syncTop();
  }

  function syncTop() {
    if (!store.loaded) return;
    $('#undo').disabled = !store.canUndo; $('#redo').disabled = !store.canRedo;
    const files = store.changedFiles(), n = files.length;
    save.disabled = n === 0; save.textContent = n ? `Publish ${n} file${n === 1 ? '' : 's'}` : 'Nothing to publish';
    save.title = files.map((f) => f.label).join('\n');
    draft.textContent = !n ? 'Same as the live site' : remote.on ? (JSON.stringify(store.snapshot()) === remote.saved ? 'Draft saved ✓' : 'Draft saved here, syncing…') : 'Draft saved in this browser ✓'; draft.className = `draft ${n ? 'ok' : ''}`;
    const sel = $('#page'), opts = [[{ type: 'section', id: 'hero' }, 'Home'], [{ type: 'page', page: 'colophon.html' }, 'Colophon'], ...store.docs.projects.projects.map((p) => [{ type: 'project', slug: p.slug }, panels.title(p.slug)])];
    sel.disabled = false;
    sel.replaceChildren(...opts.map(([s, t]) => h('option', { value: JSON.stringify(s), selected: pageOf(s) === preview.page }, t)));
    $('#live').href = preview.page;
  }

  // ── publish ───────────────────────────────────────────────────────────────────────────────
  async function publish() {
    if (preview.missing.size) return say(`Not published: a page asks for wording that is missing (${[...preview.missing][0]}).`, 'warn');
    const files = store.changedFiles();
    if (!files.length) return;
    save.disabled = true; say('Publishing…');
    try {
      const res = await client.commit(files.map(({ path, text, sha }) => ({ path, text, sha })), `Edit from prismet.xyz/edit: ${files.map((f) => f.label).join('; ')}`.slice(0, 190));
      store.published(res.files, new Map(files.map((f) => [f.path, f.text])));
      say('Published. The site is rebuilding; it goes live in about three minutes.', 'ok');
      watch(res.commit.sha);
    } catch (err) {
      if (err.status === 401) askPin('The session ended. Enter the PIN again; your draft is still here.');
      else say(`Not published: ${err.message} Your draft is still here.`, 'warn');
    }
    syncTop();
  }

  function watch(sha) {
    clearInterval(pollTimer);
    const a = h('a', { href: 'https://github.com/sagedeutschle/Bayzyl/actions', target: '_blank', rel: 'noopener' }, 'Build'), span = h('span', {});
    deploy.replaceChildren(span, ' ', a);
    let ticks = 0;
    const tick = async () => {
      ticks++;
      try {
        const run = await client.runFor(sha);
        if (!run) span.textContent = 'Waiting for the build to start…';
        else if (run.status !== 'completed') { span.textContent = `Build ${run.status.replace('_', ' ')}…`; a.href = run.html_url; }
        else { clearInterval(pollTimer); a.href = run.html_url; span.textContent = run.conclusion === 'success' ? 'Live on prismet.xyz.' : `The build ended with "${run.conclusion}"; nothing changed on the site.`; span.className = run.conclusion === 'success' ? 'ok' : 'warn'; }
      } catch { clearInterval(pollTimer); span.textContent = 'Published. Follow the build on GitHub.'; }
      if (ticks > 60) clearInterval(pollTimer);
    };
    tick(); pollTimer = setInterval(tick, 15000);
  }

  // ── keys ──────────────────────────────────────────────────────────────────────────────────
  function onKey(e) {
    const mod = e.metaKey || e.ctrlKey, typing = e.target.closest?.('input, textarea, select, [contenteditable]');
    if (mod && /^k$/i.test(e.key)) { e.preventDefault(); return openPalette(); }
    if (mod && /^s$/i.test(e.key)) { e.preventDefault(); return say(store.dirty ? 'The draft is saved in this browser. Publish sends it to the site.' : 'Nothing to save: this is the live site.'); }
    if (mod && /^z$/i.test(e.key) && !typing) { e.preventDefault(); return e.shiftKey ? store.redo() : store.undo(); }
    if (mod && /^d$/i.test(e.key) && selection?.type === 'project' && !typing) { e.preventDefault(); return panels.actions.newProject(selection.slug); }
    if (e.key === 'Escape') { if (!$('#palette').hidden) return closePalette(); if (!typing) select(null, 'panel'); }
  }

  // ── search and commands ───────────────────────────────────────────────────────────────────
  function paletteItems() {
    const d = store.docs, out = [];
    const cmd = (name, run) => out.push({ kind: 'Command', name, run });
    cmd('Undo', () => store.undo()); cmd('Redo', () => store.redo()); cmd('Publish changes', publish);
    cmd('New record', () => panels.actions.newProject());
    cmd('Preview: desktop', () => $('#device [data-w="1440"]').click()); cmd('Preview: tablet', () => $('#device [data-w="820"]').click()); cmd('Preview: mobile', () => $('#device [data-w="390"]').click());
    cmd('Mode: edit', () => setMode('edit')); cmd('Mode: preview as a visitor', () => setMode('preview'));
    cmd('Theme: night', () => preview.setTheme('dark')); cmd('Theme: day', () => preview.setTheme('light'));
    cmd('Discard every change in this draft', () => store.discard()); cmd('Lock the editor', () => $('#lock').click());
    out.push({ kind: 'Page', name: 'Home', sel: { type: 'section', id: 'hero' } }, { kind: 'Page', name: 'Colophon', sel: { type: 'page', page: 'colophon.html' } }, { kind: 'Words', name: 'Shared words', sel: { type: 'shared' } });
    for (const id of ['selected', 'work', 'plate', 'lenses', 'about']) out.push({ kind: 'Section', name: `Home / ${sectionName(id)}`, sel: { type: 'section', id } });
    for (const p of d.projects.projects) out.push({ kind: 'Record', name: panels.title(p.slug), more: p.slug, sel: { type: 'project', slug: p.slug } });
    for (const g of TOKEN_GROUPS) for (const [name, lab] of g.tokens) out.push({ kind: 'Token', name: `${g.label} / ${lab}`, more: name, sel: { type: 'tokens', group: g.id } });
    for (const [key, text] of Object.entries(d.site)) out.push({ kind: 'Text', name: `${key}: ${text.slice(0, 80)}`, sel: { type: 'text', key } });
    for (const [slug, w] of Object.entries(d.work)) for (const [f, text] of Object.entries(w)) out.push({ kind: 'Text', name: `${slug} ${f}: ${text.slice(0, 80)}`, sel: { type: 'text', key: `work.${slug}.${f}` } });
    return out;
  }
  function openPalette() {
    if (!store.loaded) return;
    items = paletteItems(); $('#palette').hidden = false; const input = $('#palette-input'); input.value = ''; filter(); input.focus();
  }
  function closePalette() { $('#palette').hidden = true; }
  function filter() {
    const words = $('#palette-input').value.toLowerCase().split(/\s+/).filter(Boolean);
    hits = items.filter((it) => { const t = `${it.kind} ${it.name} ${it.more || ''}`.toLowerCase(); return words.every((w) => t.includes(w)); }).slice(0, 40);
    at = 0; drawHits();
  }
  function drawHits() {
    $('#palette-list').replaceChildren(...hits.map((it, i) => h('li', { role: 'option', 'aria-selected': String(i === at), onclick: () => run(i) }, h('span', {}, it.name), h('small', {}, it.kind))));
    $('#palette-list').children[at]?.scrollIntoView({ block: 'nearest' });
  }
  function run(i) { const it = hits[i]; if (!it) return; closePalette(); if (it.run) it.run(); else select(it.sel, 'palette'); }
  $('#palette-input').addEventListener('input', filter);
  $('#palette-input').addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); at = (at + (e.key === 'ArrowDown' ? 1 : hits.length - 1)) % Math.max(1, hits.length); drawHits(); }
    if (e.key === 'Enter') { e.preventDefault(); run(at); }
  });
  $('#palette').addEventListener('click', (e) => { if (e.target === e.currentTarget) closePalette(); });
}

if (typeof document !== 'undefined') init();
