// store.js — the draft: every document the editor can change, with undo, autosave and "what would Publish write".
//
// Documents (store.docs):
//   site      content/site.md as { key: text }
//   work      { slug: content/work/<slug>.md as { key: text } }
//   projects  data/projects.json (records, section order, hidden flags, featured list)
//   theme     data/theme.json ({ root, day }: design tokens changed from site.css's defaults)
//   styles    data/styles.json ({ rules }: single elements, per width)
// The published copy of each is kept beside it (store.base), so every field can say whether it differs and go back.
// The draft autosaves to this browser and survives a reload; nothing reaches the site until Publish.
import { parseSections, writeSections, editTarget, workUpdate, workValue } from '../lib/format.js';

export const ROOT = 'showcase/prismet-site/';
export const SITE_FILE = `${ROOT}content/site.md`;
export const PROJECTS_FILE = `${ROOT}data/projects.json`;
export const THEME_FILE = `${ROOT}data/theme.json`;
export const STYLES_FILE = `${ROOT}data/styles.json`;
export const workFile = (slug) => `${ROOT}content/work/${slug}.md`;
const slugOf = (path) => path.slice(`${ROOT}content/work/`.length, -3);
const KEY = 'prismet.edit.draft.v1';
const HISTORY = 200, MERGE_MS = 1500, SAVE_MS = 400;
const clone = (o) => JSON.parse(JSON.stringify(o));
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const json = (o) => JSON.stringify(o, null, 2) + '\n';
/** The keys a new record's wording file needs for the build to accept it. */
export const WORK_KEYS = ['title', 'subtitle', 'status', 'year', 'role', 'summary', 'facts', 'highlights'];

export function createStore({ storage = globalThis.localStorage, now = Date.now } = {}) {
  let files = new Map();            // path → { text, sha } as published
  let base = null, docs = null;
  let undo = [], redo = [], lastMerge = null, lastAt = 0, saveTimer = null, draftAt = 0;
  const listeners = new Set();
  const emit = (ev) => listeners.forEach((fn) => fn(ev));

  const parse = (from = files) => {
    const work = {};
    for (const [path, f] of from) if (path.startsWith(`${ROOT}content/work/`)) work[slugOf(path)] = parseSections(f.text);
    return {
      site: parseSections(from.get(SITE_FILE)?.text || ''),
      work,
      projects: JSON.parse(from.get(PROJECTS_FILE)?.text || '{"projects":[]}'),
      theme: (() => { const t = JSON.parse(from.get(THEME_FILE)?.text || '{}'); return { ...t, root: t.root || {}, day: t.day || {} }; })(),
      styles: (() => { const t = JSON.parse(from.get(STYLES_FILE)?.text || '{}'); return { ...t, rules: t.rules || {} }; })(),
    };
  };
  const shas = () => Object.fromEntries([...files].map(([p, f]) => [p, f.sha]));

  function write() {
    saveTimer = null;
    try {
      draftAt = now();
      if (same(docs, base)) storage.removeItem(KEY); else storage.setItem(KEY, JSON.stringify({ shas: shas(), docs, at: draftAt }));
      emit({ type: 'saved' });
    } catch (e) { emit({ type: 'save-error', error: e }); }
  }
  function persist() { clearTimeout(saveTimer); emit({ type: 'saving' }); saveTimer = setTimeout(write, SAVE_MS); }
  /** Saves now if a save is waiting: called when the page is being left, so the last change is never lost. */
  function flush() { if (saveTimer) { clearTimeout(saveTimer); write(); } }

  /** Takes what the server has: [{ path, text, sha }]. Restores this browser's draft when it was made from these versions. */
  function load(list) {
    files = new Map(list.map((f) => [f.path, { text: f.text, sha: f.sha }]));
    base = parse(); docs = clone(base); undo = []; redo = [];
    let restored = false, stale = false;
    try {
      const saved = JSON.parse(storage.getItem(KEY) || 'null');
      if (saved?.docs) {
        if (same(saved.shas, shas())) { docs = { ...clone(base), ...saved.docs }; draftAt = saved.at || 0; restored = !same(docs, base); }
        else { storage.setItem(`${KEY}.stale`, JSON.stringify(saved)); storage.removeItem(KEY); stale = true; }   // kept, not applied
      }
    } catch { /* storage blocked or corrupt: start from the published copy */ }
    emit({ type: 'load', restored, stale });
  }

  /** One undoable change. fn edits the draft in place. merge: consecutive changes with the same tag are one undo step. */
  function change(label, fn, { kind = 'structure', merge = null, key = null, source = null } = {}) {
    const before = clone(docs);
    fn(docs);
    if (same(before, docs)) return false;
    const t = now();
    if (!(merge && merge === lastMerge && t - lastAt < MERGE_MS && undo.length)) { undo.push({ label, docs: before }); if (undo.length > HISTORY) undo.shift(); }
    lastMerge = merge; lastAt = t; redo = [];
    persist();
    emit({ type: 'change', kind, label, key, source });
    return true;
  }
  const step = (from, to, type) => {
    const entry = from.pop(); if (!entry) return null;
    to.push({ label: entry.label, docs: clone(docs) }); docs = entry.docs; lastMerge = null;
    persist(); emit({ type: 'change', kind: 'history', label: `${type} ${entry.label}` });
    return entry.label;
  };

  const slugs = () => Object.keys(docs.work);
  const textIn = (d, key) => { const t = editTarget(key, Object.keys(d.work)); return t.file === 'site' ? d.site[key] : workValue(d.work[t.slug] || {}, t.field); };

  /** What Publish would write: [{ path, text, sha, label }], sha null for a file that does not exist yet. */
  function changedFiles() {
    const out = [];
    const diff = (cur, was) => Object.fromEntries(Object.entries(cur).filter(([k, v]) => v !== was?.[k]));
    const site = diff(docs.site, base.site);
    if (Object.keys(site).length) out.push({ path: SITE_FILE, text: writeSections(files.get(SITE_FILE).text, site), sha: files.get(SITE_FILE).sha, label: `site.md (${Object.keys(site).length})` });
    for (const [slug, sections] of Object.entries(docs.work)) {
      const path = workFile(slug), f = files.get(path);
      if (!f) { out.push({ path, text: Object.entries(sections).map(([k, v]) => `## ${k}\n${String(v).trim()}\n`).join('\n'), sha: null, label: `${slug}.md (new)` }); continue; }
      const d = diff(sections, base.work[slug]);
      if (Object.keys(d).length) out.push({ path, text: writeSections(f.text, d), sha: f.sha, label: `${slug}.md (${Object.keys(d).join(', ')})` });
    }
    if (!same(docs.projects, base.projects)) out.push({ path: PROJECTS_FILE, text: json(docs.projects), sha: files.get(PROJECTS_FILE)?.sha ?? null, label: 'projects.json' });
    if (!same(docs.theme, base.theme)) out.push({ path: THEME_FILE, text: json(docs.theme), sha: files.get(THEME_FILE)?.sha ?? null, label: 'theme.json' });
    if (!same(docs.styles, base.styles)) out.push({ path: STYLES_FILE, text: json(docs.styles), sha: files.get(STYLES_FILE)?.sha ?? null, label: 'styles.json' });
    return out;
  }

  return {
    load, change, changedFiles, flush,
    get docs() { return docs; },
    get base() { return base; },
    get loaded() { return docs !== null; },
    get dirty() { return docs !== null && !same(docs, base); },
    get canUndo() { return undo.length > 0; },
    get canRedo() { return redo.length > 0; },
    undo: () => step(undo, redo, 'Undo'),
    redo: () => step(redo, undo, 'Redo'),
    subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); },
    slugs,
    /** The text behind an edit key (hero.title, work.<slug>.summary, work.<slug>.facts.0.value…). */
    text: (key) => textIn(docs, key) ?? '',
    publishedText: (key) => textIn(base, key),
    setText(key, text, { source = null } = {}) {
      return change(`Edit ${key}`, (d) => {
        const t = editTarget(key, Object.keys(d.work));
        if (t.file === 'site') d.site[key] = text; else Object.assign(d.work[t.slug], workUpdate(d.work[t.slug], t.field, text));
      }, { kind: 'text', merge: `text:${key}`, key, source });
    },
    /** After a publish: the files now on the server become the published copy; the draft keeps anything changed since. */
    published(list, texts) {
      for (const f of list) files.set(f.path, { text: texts.get(f.path), sha: f.sha });
      base = parse(); persist(); emit({ type: 'published' });
    },
    /** The draft as it is stored: here in the browser, and in the drafts repository when the server has one. */
    snapshot: () => ({ shas: shas(), docs, at: draftAt }),
    /** Takes a draft saved from another device when it was made from these versions and is newer. Undoable. */
    adopt(saved) {
      if (!saved?.docs || !same(saved.shas, shas()) || (saved.at || 0) <= draftAt) return false;
      const took = change('Draft from another device', (d) => { Object.assign(d, clone(saved.docs)); });
      if (took) draftAt = saved.at;
      return took;
    },
    /** Makes the draft what the site was at a past revision: list is that revision's files. Undoable; Publish restores it. */
    restore(label, list) {
      // A file the revision does not have (a data file added since) keeps today's contents.
      const then = parse(new Map([...files, ...list.map((f) => [f.path, { text: f.text }])]));
      return change(`Restore ${label}`, (d) => { for (const k of Object.keys(d)) delete d[k]; Object.assign(d, then); });
    },
    /** Back to the published copy. Undoable. */
    discard: () => change('Discard all changes', (d) => { Object.assign(d, clone(base)); }),
  };
}
