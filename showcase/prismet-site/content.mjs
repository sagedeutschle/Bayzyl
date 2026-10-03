// content.mjs — reads and writes the plain-text wording files in content/.
//
// Format: "## key" on its own line, then the text for that key until the next "## " line.
// HTML comments (<!-- -->) are notes for the editor and are ignored.
// In text: *gold highlight*, **bold**. Lists are "- item" lines; facts are "- Label: Value".
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseSections, writeSections, listItems, factPairs, toList, toFacts, inline, plain } from './pages/lib/format.js';

export { parseSections, writeSections, listItems, factPairs, toList, toFacts, inline, plain };
export const CONTENT = join(dirname(fileURLToPath(import.meta.url)), 'content');

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
