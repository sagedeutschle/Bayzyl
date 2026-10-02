// apply-edits.mjs — writes the preview's Edit-mode changes back into the source files:
//   wording → content/site.md and content/work/*.md
//   layout  → data/projects.json (section order/hidden, card order, wide, featured, hidden)
//
//   node showcase/prismet-site/apply-edits.mjs edits.md     # text from "Copy my changes"
//   node showcase/prismet-site/apply-edits.mjs edits.json   # a dump of the preview's saved edits/layout
//
// Then rebuild: node showcase/prismet-site/build.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseSections, applyEdits } from './content.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const file = process.argv[2];
if (!file) { console.error('usage: node apply-edits.mjs <edits.md|edits.json>'); process.exit(1); }
const raw = readFileSync(file, 'utf8');

let edits = [], layout = null;
const isLayout = (v) => v && typeof v === 'object' && Array.isArray(v.order) && Array.isArray(v.sections);
if (/^\s*[[{]/.test(raw)) {
  // Any JSON shape: every {key, text} is a wording edit; an object with order + sections is the layout.
  const walk = (v) => {
    if (Array.isArray(v)) v.forEach(walk);
    else if (v && typeof v === 'object') {
      if (typeof v.key === 'string' && typeof v.text === 'string') edits.push({ key: v.key, text: v.text });
      else if (isLayout(v)) layout = v;
      else Object.values(v).forEach(walk);
    }
  };
  walk(JSON.parse(raw));
} else {
  const sections = parseSections(raw);
  if (sections.layout) { layout = JSON.parse(sections.layout); delete sections.layout; }
  edits = Object.entries(sections).map(([key, text]) => ({ key, text }));
}
if (!edits.length && !layout) { console.error('No edits found in ' + file); process.exit(1); }

if (edits.length) {
  const { changed, skipped } = applyEdits(edits);
  console.log(`wording: applied ${edits.length} edit(s) → ${changed.map((f) => f.replace(/.*showcase\//, 'showcase/')).join(', ')}`);
  if (skipped.length) console.warn('skipped (unknown project):', skipped.join(', '));
}

if (layout) {
  const f = join(HERE, 'data/projects.json');
  const data = JSON.parse(readFileSync(f, 'utf8'));
  const known = new Set(data.projects.map((p) => p.slug));
  const list = (a) => (Array.isArray(a) ? a.filter((s) => known.has(s)) : []);
  const order = list(layout.order);
  const rank = (s) => { const i = order.indexOf(s); return i < 0 ? 1e9 : i; };
  data.projects.sort((a, b) => rank(a.slug) - rank(b.slug));
  const wide = new Set(list(layout.wide)), hidden = new Set(list(layout.hidden));
  for (const p of data.projects) {
    if (wide.has(p.slug)) p.wide = true; else delete p.wide;
    if (hidden.has(p.slug)) p.hidden = true; else delete p.hidden;
  }
  data.featuredOrder = list(layout.featured);
  const SECTIONS = ['selected', 'work', 'plate', 'lenses', 'about'];
  data.layout = {
    sections: (layout.sections || []).filter((s) => SECTIONS.includes(s)),
    hiddenSections: (layout.hiddenSections || []).filter((s) => SECTIONS.includes(s)),
  };
  writeFileSync(f, JSON.stringify(data, null, 2) + '\n');
  console.log(`layout: sections ${data.layout.sections.join(' → ')}${data.layout.hiddenSections.length ? ` (hidden: ${data.layout.hiddenSections.join(', ')})` : ''}; ` +
    `featured ${data.featuredOrder.length}; wide ${wide.size}; hidden ${hidden.size} → showcase/prismet-site/data/projects.json`);
}
