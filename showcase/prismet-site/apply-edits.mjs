// apply-edits.mjs — writes wording edits back into content/site.md and content/work/*.md.
//
//   node showcase/prismet-site/apply-edits.mjs edits.md     # "## key" blocks from "Copy my changes"
//   node showcase/prismet-site/apply-edits.mjs edits.json   # [{key, text}], or a dump of the preview's saved edits
//
// Then rebuild: node showcase/prismet-site/build.mjs
import { readFileSync } from 'node:fs';
import { parseSections, applyEdits } from './content.mjs';

const file = process.argv[2];
if (!file) { console.error('usage: node apply-edits.mjs <edits.md|edits.json>'); process.exit(1); }
const raw = readFileSync(file, 'utf8');
let edits = [];
if (/^\s*[[{]/.test(raw)) {
  // Accept any JSON shape: collect every object that has a string `key` and `text`.
  const walk = (v) => {
    if (Array.isArray(v)) v.forEach(walk);
    else if (v && typeof v === 'object') {
      if (typeof v.key === 'string' && typeof v.text === 'string') edits.push({ key: v.key, text: v.text });
      else Object.values(v).forEach(walk);
    }
  };
  walk(JSON.parse(raw));
} else {
  edits = Object.entries(parseSections(raw)).map(([key, text]) => ({ key, text }));
}
if (!edits.length) { console.error('No edits found in ' + file); process.exit(1); }
const { changed, skipped } = applyEdits(edits);
console.log(`applied ${edits.length} edit(s) → ${changed.map((f) => f.replace(/.*showcase\//, 'showcase/')).join(', ')}`);
if (skipped.length) console.warn('skipped (unknown project):', skipped.join(', '));
