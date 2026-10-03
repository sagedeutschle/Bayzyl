// content.mjs — reads and writes the plain-text wording files in content/.
//
// Format: "## key" on its own line, then the text for that key until the next "## " line.
// HTML comments (<!-- -->) are notes for the editor and are ignored.
// In text: *gold highlight*, **bold**. Lists are "- item" lines; facts are "- Label: Value".
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

export const CONTENT = join(dirname(fileURLToPath(import.meta.url)), 'content');

const stripComments = (s) => s.replace(/<!--[\s\S]*?-->/g, '');

export function parseSections(text) {
  const out = {};
  let key = null, buf = [];
  const flush = () => { if (key) out[key] = buf.join('\n').trim(); };
  for (const line of stripComments(text).split('\n')) {
    const m = line.match(/^## +(\S+)\s*$/);
    if (m) { flush(); key = m[1]; buf = []; } else if (key) buf.push(line);
  }
  flush();
  return out;
}

/** Replace the bodies of the given keys, keeping comments, order, and every other section as is. */
export function writeSections(text, updates) {
  const lines = text.split('\n'), out = [], seen = new Set();
  let skipping = false, inComment = false;
  for (const line of lines) {
    if (line.includes('<!--')) inComment = true;
    const m = !inComment && line.match(/^## +(\S+)\s*$/);
    if (line.includes('-->')) inComment = false;
    if (m) {
      skipping = false;
      out.push(line);
      if (m[1] in updates) { out.push(String(updates[m[1]]).trim(), ''); seen.add(m[1]); skipping = true; }
      continue;
    }
    if (!skipping) out.push(line);
  }
  for (const [k, v] of Object.entries(updates)) if (!seen.has(k)) out.push('', `## ${k}`, String(v).trim());
  return out.join('\n').replace(/\n{3,}/g, '\n\n').trimEnd() + '\n';
}

export const listItems = (v = '') => v.split('\n').filter((l) => /^- /.test(l)).map((l) => l.slice(2).trim());
export const factPairs = (v = '') => listItems(v).map((l) => { const i = l.indexOf(':'); return i < 0 ? [l, ''] : [l.slice(0, i).trim(), l.slice(i + 1).trim()]; });
export const toList = (items) => items.map((i) => `- ${i}`).join('\n');
export const toFacts = (pairs) => pairs.map(([k, v]) => `- ${k}: ${v}`).join('\n');

const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
/** Text → safe inline HTML: **bold**, *gold highlight*, {vars}. */
export function inline(s, vars = {}) {
  return esc(s)
    .replace(/\{(\w+)\}/g, (m, k) => (k in vars ? esc(vars[k]) : m))
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>');
}
export const plain = (s, vars = {}) => String(s ?? '').replace(/\{(\w+)\}/g, (m, k) => (k in vars ? vars[k] : m)).replace(/\*\*?(.+?)\*\*?/g, '$1');

export function loadSite() { return parseSections(readFileSync(join(CONTENT, 'site.md'), 'utf8')); }
export function loadWork(slug) {
  const f = join(CONTENT, 'work', slug + '.md');
  return existsSync(f) ? parseSections(readFileSync(f, 'utf8')) : {};
}
export const workSlugs = () => readdirSync(join(CONTENT, 'work')).filter((f) => f.endsWith('.md')).map((f) => f.slice(0, -3));

/**
 * Apply edits keyed the way the page labels its text:
 *   hero.title                         → content/site.md, section hero.title
 *   work.<slug>.summary                → content/work/<slug>.md, section summary
 *   work.<slug>.highlights.<n>         → nth "- " line of the highlights section
 *   work.<slug>.facts.<n>.label|value  → one side of the nth "- Label: Value" line
 * Returns the list of files changed.
 */
export function applyEdits(edits) {
  const site = {}, work = {}, skipped = [];
  for (const { key, text } of edits) {
    const m = key.match(/^work\.([a-z0-9-]+)\.(.+)$/);
    if (!m) { site[key] = text; continue; }
    const [, slug, field] = m;
    if (!existsSync(join(CONTENT, 'work', slug + '.md'))) { skipped.push(key); continue; }
    (work[slug] ||= []).push({ field, text });
  }
  const changed = [];
  if (Object.keys(site).length) {
    const f = join(CONTENT, 'site.md');
    writeFileSync(f, writeSections(readFileSync(f, 'utf8'), site)); changed.push(f);
  }
  for (const [slug, list] of Object.entries(work)) {
    const f = join(CONTENT, 'work', slug + '.md');
    const raw = readFileSync(f, 'utf8'), cur = parseSections(raw), upd = {};
    for (const { field, text } of list) {
      let mm;
      if ((mm = field.match(/^highlights\.(\d+)$/))) {
        const items = listItems(upd.highlights ?? cur.highlights); items[+mm[1]] = text.replace(/\n+/g, ' '); upd.highlights = toList(items);
      } else if ((mm = field.match(/^facts\.(\d+)\.(label|value)$/))) {
        const pairs = factPairs(upd.facts ?? cur.facts); const row = pairs[+mm[1]] || ['', ''];
        row[mm[2] === 'label' ? 0 : 1] = text.replace(/\n+/g, ' ').replace(/:/g, '·'); pairs[+mm[1]] = row; upd.facts = toFacts(pairs);
      } else upd[field] = text;
    }
    writeFileSync(f, writeSections(raw, upd)); changed.push(f);
  }
  return { changed, skipped };
}
