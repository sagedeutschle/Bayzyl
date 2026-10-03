// format.js — the content format, pure: parsing, writing and inline markup. No filesystem, so the build, the
// editor in the browser and the tests share one copy. content.mjs adds the file reading and writing.
//
// Format: "## key" on its own line, then the text for that key until the next "## " line.
// HTML comments (<!-- -->) are notes for the editor and are ignored.
// In text: *gold highlight*, **bold**. Lists are "- item" lines; facts are "- Label: Value".
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

/**
 * Where an edit keyed the way the page labels its text belongs:
 *   hero.title                         → { file: 'site', key: 'hero.title' }
 *   work.<slug>.summary                → { file: 'work', slug, field: 'summary' }
 *   work.<slug>.highlights.<n>         → the nth "- " line of that record's highlights
 *   work.<slug>.facts.<n>.label|value  → one side of the nth "- Label: Value" line
 *   page.<home|slug>.<section id>.<field> → that field of a library section in data/pages.json
 * slugs is the list of records that exist: site.md has keys that start with "work." too.
 */
export function editTarget(key, slugs) {
  const pg = key.match(/^page\.([a-z0-9-]+)\.([a-z][a-z0-9-]*)\.([a-z]+)$/);     // a library section's field (data/pages.json)
  if (pg) return { file: 'pages', page: pg[1], section: pg[2], prop: pg[3] };
  const m = key.match(/^work\.([a-z0-9-]+)\.(.+)$/);
  return m && slugs.includes(m[1]) ? { file: 'work', slug: m[1], field: m[2] } : { file: 'site', key };
}
/** The section update for one edit to a record: { sectionKey: newText }. cur is the record's sections. */
export function workUpdate(cur, field, text) {
  let m;
  if ((m = field.match(/^highlights\.(\d+)$/))) { const items = listItems(cur.highlights); items[+m[1]] = text.replace(/\n+/g, ' '); return { highlights: toList(items) }; }
  if ((m = field.match(/^facts\.(\d+)\.(label|value)$/))) {
    const pairs = factPairs(cur.facts), row = pairs[+m[1]] || ['', ''];
    row[m[2] === 'label' ? 0 : 1] = text.replace(/\n+/g, ' ').replace(/:/g, '·'); pairs[+m[1]] = row;
    return { facts: toFacts(pairs) };
  }
  return { [field]: text };
}
/** The text an edit key currently holds in a record's sections (the inverse of workUpdate). */
export function workValue(cur, field) {
  let m;
  if ((m = field.match(/^highlights\.(\d+)$/))) return listItems(cur.highlights)[+m[1]] ?? '';
  if ((m = field.match(/^facts\.(\d+)\.(label|value)$/))) return (factPairs(cur.facts)[+m[1]] || ['', ''])[m[2] === 'label' ? 0 : 1];
  return cur[field] ?? '';
}
