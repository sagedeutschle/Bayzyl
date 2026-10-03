// build.mjs — generates the static prismet.xyz site.
//
//   node showcase/prismet-site/build.mjs            # → showcase/prismet-site/dist/
//   node showcase/prismet-site/build.mjs --preview  # → showcase/prismet-site/dist-preview/ with the Edit page editor
//
// WORDS live in content/site.md and content/work/<slug>.md (plain text, edit freely).
// DATA (images, links, categories, rooms, layout) lives in data/projects.json.
// No dependencies. Output is plain HTML/CSS/JS + images, so it drops into the Fly app as static files next to
// the /api/wordle, /rtc, /steam and /debt routes, which this build never touches.
//
// The site is a workshop with a hall plan: six wings (the beams) around a rotunda (the prism). The home page is the
// entrance, the principal works, the register (a ledger of every record), one plate, the lenses and the keeper's
// desk. Each project page shares one skeleton; its "room" (data-room) sets the material and one signature module.
import { readFileSync, writeFileSync, mkdirSync, copyFileSync, rmSync, cpSync, existsSync, statSync, readdirSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { loadSite, loadWork } from './content.mjs';
import { renderSite } from './pages/lib/render.js';
import { themeCss } from './pages/lib/theme.js';
import { stylesCss } from './pages/lib/styles.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const SHOWCASE = join(HERE, '..');
const PREVIEW = process.argv.includes('--preview');
const DIST = join(HERE, PREVIEW ? 'dist-preview' : 'dist');
const data = JSON.parse(readFileSync(join(HERE, 'data/projects.json'), 'utf8'));

rmSync(DIST, { recursive: true, force: true });
mkdirSync(join(DIST, 'work'), { recursive: true });

// ── assets: what render.mjs asks for is copied into dist; sizes are read from the files ─────
const copied = new Map();
const sizes = new Map();
function webpSize(buf) {
  if (buf.toString('ascii', 0, 4) !== 'RIFF' || buf.toString('ascii', 8, 12) !== 'WEBP') return null;
  const chunk = buf.toString('ascii', 12, 16);
  if (chunk === 'VP8X') return { w: 1 + buf.readUIntLE(24, 3), h: 1 + buf.readUIntLE(27, 3) };
  if (chunk === 'VP8 ') return { w: buf.readUInt16LE(26) & 0x3fff, h: buf.readUInt16LE(28) & 0x3fff };
  if (chunk === 'VP8L') { const b = buf.readUInt32LE(21); return { w: 1 + (b & 0x3fff), h: 1 + ((b >> 14) & 0x3fff) }; }
  return null;
}
function sizeOf(absPath) {
  if (sizes.has(absPath)) return sizes.get(absPath);
  let s = null;
  try { s = webpSize(readFileSync(absPath)); } catch { /* fall through to identify */ }
  if (!s) {
    try { const [w, h] = execFileSync('identify', ['-format', '%w %h', absPath + '[0]']).toString().trim().split(' ').map(Number); s = { w, h }; }
    catch { s = { w: 0, h: 0 }; }
  }
  sizes.set(absPath, s);
  return s;
}
// Every URL the build writes for a file it copies ends in ?v=<8 hex of the file's hash>, so a returning visitor never
// pairs new HTML with a stale stylesheet, script or image cached under the same name; the server ignores query
// strings. Fonts keep their plain URLs (site.css loads them and the preload must match). The preview keeps plain URLs.
const hashOf = (abs) => createHash('sha256').update(readFileSync(abs)).digest('hex').slice(0, 8);
const versioned = (p, abs) => (PREVIEW ? p : `${p}?v=${hashOf(abs)}`);
/** Copies a file from showcase/ into dist (once) and returns its versioned URL, relative to the site root. */
function asset(p) {
  if (!p) return null;
  if (copied.has(p)) return copied.get(p);
  const src = join(SHOWCASE, p);
  if (!existsSync(src)) throw new Error(`missing asset: ${p}`);
  mkdirSync(dirname(join(DIST, p)), { recursive: true });
  copyFileSync(src, join(DIST, p));
  copied.set(p, versioned(p, src));
  return copied.get(p);
}
cpSync(join(SHOWCASE, 'assets/fonts'), join(DIST, 'assets/fonts'), { recursive: true });
// site.css = the design's defaults (src/site.css) plus whatever data/theme.json (tokens) and data/styles.json (single
// elements) change; nothing is added when they are empty.
const readData = (name) => (existsSync(join(HERE, 'data', name)) ? JSON.parse(readFileSync(join(HERE, 'data', name), 'utf8')) : {});
const theme = readData('theme.json'), styles = readData('styles.json');
writeFileSync(join(DIST, 'site.css'), readFileSync(join(HERE, 'src/site.css'), 'utf8') + themeCss(theme) + stylesCss(styles));
copyFileSync(join(HERE, 'src/site.js'), join(DIST, 'site.js'));
const CSS_URL = versioned('site.css', join(DIST, 'site.css')), JS_URL = versioned('site.js', join(HERE, 'src/site.js'));
if (PREVIEW) for (const f of ['editor.js', 'editor.css']) if (existsSync(join(HERE, 'src', f))) copyFileSync(join(HERE, 'src', f), join(DIST, f));
// og:image: a capture of the entrance (showcase/tools/shoot-og.mjs writes assets/og/entrance.jpg).
copyFileSync(join(SHOWCASE, 'assets/og/entrance.jpg'), join(DIST, 'assets/og.jpg'));
// /favicon.ico: browsers and the old /steam page ask for it by name; the server looks in site/ first, so this answers it.
copyFileSync(join(SHOWCASE, 'assets/icons/favicon.ico'), join(DIST, 'favicon.ico'));
// pages/: standalone pages shipped as they are (the editor at /edit with the renderer it shares with this build in
// lib/; later /privacy and /support). The server maps the extension-less routes to these files.
if (existsSync(join(HERE, 'pages'))) cpSync(join(HERE, 'pages'), DIST, { recursive: true });
const OG_URL = `https://prismet.xyz/${versioned('assets/og.jpg', join(DIST, 'assets/og.jpg'))}`;

// ── pages ───────────────────────────────────────────────────────────────────────────────────
const { pages, missing, shown, projects } = renderSite({
  data,
  site: loadSite(),
  work: Object.fromEntries(data.projects.map((p) => [p.slug, loadWork(p.slug)])),
  assets: { size: (p) => sizeOf(join(SHOWCASE, p)), has: (p) => existsSync(join(SHOWCASE, p)), url: asset },
  urls: { css: CSS_URL, js: JS_URL, og: OG_URL },
  styles,
  preview: PREVIEW,
});
for (const [path, html] of pages) writeFileSync(join(DIST, path), html);

// edit/assets.json: what the editor's preview needs to render a draft the way this build would, without a filesystem:
// every image's size and version, and the versioned URLs of the script and the share image.
if (!PREVIEW) {
  const walk = (dir) => readdirSync(dir, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)]));
  const files = Object.fromEntries(walk(join(SHOWCASE, 'assets')).filter((f) => f.endsWith('.webp')).sort().map((abs) => {
    const { w, h } = sizeOf(abs);
    return [relative(SHOWCASE, abs).split('\\').join('/'), [w, h, hashOf(abs)]];
  }));
  mkdirSync(join(DIST, 'edit'), { recursive: true });
  writeFileSync(join(DIST, 'edit/assets.json'), JSON.stringify({ urls: { js: JS_URL, og: OG_URL }, files }));
}

if (missing.size) {
  console.error('✗ missing wording (the key would show on the page):\n  ' + [...missing].join('\n  '));
  process.exit(1);
}
const total = [...copied.keys()].reduce((n, p) => n + statSync(join(DIST, p)).size, 0);
console.log(`built ${shown.length} project pages + ${projects.length - shown.length} withdrawn stubs + index + colophon → ${DIST}${PREVIEW ? ' (with editor)' : ''}; ${copied.size} image files with variants, ${(total / 1024).toFixed(0)} KB`);
// The production build checks itself before anyone can deploy it; verify.mjs lists what it refuses.
if (!PREVIEW && !(await import('./verify.mjs')).verify(DIST)) process.exit(1);
