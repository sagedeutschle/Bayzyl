// verify.mjs: checks a production build before anyone deploys it. build.mjs runs it last; it also runs alone:
//
//   node showcase/prismet-site/verify.mjs [dist-folder]        # exit 1 on any problem
//
// It refuses:
//   - the editor in production: editor.js, editor.css, _preview.html, or any data-edit attribute
//   - an inline <script> (the live CSP is script-src 'self') or an on* handler attribute
//   - a file the live server reserves for /steam and /debt: style.css, steam.js, debt.js, icon.svg,
//     manifest.webmanifest, og-image.png anywhere, or shots/, api/, steam, debt, rtc, healthz at the root
//   - an <img> without width, height or alt
//   - an href, src or srcset URL (or a url() in a stylesheet) that is not https and does not resolve to a file in dist;
//     the live server's own pages /steam and /debt are allowed; a #fragment must name an id (or a register filter)
//   - "TODO", and private strings: /Users/ paths, private and mesh IPv4 addresses, .local host names, steamcommunity,
//     17-digit numbers (Steam ids), and the words in $PRISMET_PRIVATE_WORDS (comma-separated) or in the file named by
//     $PRISMET_PRIVATE_FILE (one per line; default ~/.config/prismet/private-words.txt). Host names can't be listed in
//     this file: the repo is public.
//   - EXIF or XMP metadata in an image
// Then it prints one budget line: the home page's first-view requests, estimated from its HTML.
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, relative, resolve, dirname, extname, posix } from 'node:path';
import { homedir } from 'node:os';
import { fileURLToPath } from 'node:url';

const RESERVED_FILES = ['style.css', 'steam.js', 'debt.js', 'icon.svg', 'manifest.webmanifest', 'og-image.png'];
const RESERVED_ROOT = ['shots', 'api', 'steam', 'steam.html', 'debt', 'debt.html', 'rtc', 'healthz'];
const ROUTES = new Set(['/steam', '/debt', '/edit', '/privacy', '/support', '/scam']);            // served by the live server, not by this build
const STATIC_ALIASES = new Map([
  ['/arcade', 'arcade.html'], ['/arcade/', 'arcade.html'], ['/tools', 'tools.html'], ['/tools/', 'tools.html'],
  ...['2048', 'minesweeper', 'lights-out', 'wordle', 'rubiks-cube', 'snake', 'sudoku', 'sliding-15', 'nonogram', 'chess', 'reversi', 'checkers', 'connect-four', 'gomoku', 'sea-battle', 'solitaire', 'spider', 'crazy-8', 'catan', 'brick-bench'].flatMap((id) => [[`/arcade/${id}`, 'arcade/game.html'], [`/arcade/${id}/`, 'arcade/game.html']]),
]);
const TEXT = new Set(['.html', '.css', '.js', '.json', '.svg', '.txt', '.xml', '.webmanifest']);
const BUDGET = 20;                                      // requests on the home page's first view
const PRIVATE = [
  [/TODO/, 'TODO'],
  [/\/Users\//, 'a /Users/ path'],
  [/\b(?:10\.\d{1,3}|192\.168|172\.(?:1[6-9]|2\d|3[01])|100\.(?:6[4-9]|[7-9]\d|1[01]\d|12[0-7]))\.\d{1,3}\.\d{1,3}\b/, 'a private or mesh IPv4 address'],
  [/\b[a-z0-9-]+\.local\b/i, 'a .local host name'],
  [/steamcommunity/i, 'a steamcommunity link'],
  [/(?<!\d)\d{17}(?!\d)/, 'a 17-digit number (Steam id)'],
];

function privateWords() {
  const file = process.env.PRISMET_PRIVATE_FILE || join(homedir(), '.config/prismet/private-words.txt');
  const words = (process.env.PRISMET_PRIVATE_WORDS || '').split(',');
  let from = process.env.PRISMET_PRIVATE_WORDS ? '$PRISMET_PRIVATE_WORDS' : '';
  if (existsSync(file)) { words.push(...readFileSync(file, 'utf8').split('\n')); from = from ? `${from} + ${file}` : file; }
  const list = [...new Set(words.map((w) => w.trim()).filter((w) => w && !w.startsWith('#')))];
  return { from, re: list.map((w) => [new RegExp(`\\b${w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i'), 'a private word']) };
}

const walk = (dir) => readdirSync(dir, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)]));
// Tags and attributes, quote-aware (values never hold a raw "<" or ">": the build escapes them).
const TAG = /<(!--[\s\S]*?--|[a-zA-Z][\w:-]*)((?:\s+[^\s"'>/=]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s"'=<>`]+))?)*)\s*\/?>/g;
const ATTR = /([^\s"'>/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;
const attrsOf = (s) => Object.fromEntries([...(s || '').matchAll(ATTR)].map((m) => [m[1].toLowerCase(), (m[2] ?? m[3] ?? m[4] ?? '').replace(/&amp;/g, '&')]));
const tagsOf = (html) => [...html.matchAll(TAG)].filter((m) => !m[1].startsWith('!--')).map((m) => ({ name: m[1].toLowerCase(), a: attrsOf(m[2]), end: m.index + m[0].length }));

// These fetch paths are assembled in JavaScript, so the ordinary HTML URL walk
// cannot discover them. The ACS bundle has seven fewer prefixes than the broader
// ZIP/district file; those uncovered prefixes intentionally remain unavailable.
export function verifyScamData(dist) {
  const problems = [];
  const record = value => value !== null && typeof value === 'object' && !Array.isArray(value);
  const readJSON = (path) => {
    try { return JSON.parse(readFileSync(join(dist, path), 'utf8')); }
    catch { problems.push(`${path}: missing or invalid JSON`); return null; }
  };
  for (const name of ['tax-2026', 'mts-snapshot', 'congress']) readJSON(`scam-data/${name}.json`);
  const local = readJSON('scam-data/local-2026.json');
  if (!record(local) || !record(local.localIncome) || !record(local.sales?.states) || !record(local.property) || !record(local.counties)) {
    problems.push('scam-data/local-2026.json: incomplete local tax tables');
  }
  let prefixes = [];
  try { prefixes = readdirSync(join(dist, 'scam-data/zip')).filter(name => /^\d{3}\.json$/.test(name)); }
  catch { problems.push('scam-data/zip: missing geographic shards'); }
  if (!prefixes.length) problems.push('scam-data/zip: no geographic prefixes');
  const uncovered = new Set(['008.json', '202.json', '204.json', '205.json', '753.json', '772.json', '969.json']);
  let bundled = [];
  try { bundled = readdirSync(join(dist, 'scam-data/zip-local')).filter(name => /^\d{3}\.json$/.test(name)); }
  catch { problems.push('scam-data/zip-local: missing local shards'); }
  for (const name of new Set([...prefixes.filter(name => !uncovered.has(name)), ...bundled])) {
    const path = `scam-data/zip-local/${name}`;
    const shard = readJSON(path);
    if (!record(shard) || !Object.keys(shard).length ||
        Object.entries(shard).some(([zip, row]) => !/^\d{5}$/.test(zip) || !zip.startsWith(name.slice(0, 3)) || !Array.isArray(row))) {
      problems.push(`${path}: invalid ZIP row or prefix`);
    }
  }
  return problems;
}

export function verify(dist = join(dirname(fileURLToPath(import.meta.url)), 'dist')) {
  dist = resolve(dist);
  const problems = [];
  const fail = (where, what) => problems.push(`${where}: ${what}`);
  const files = walk(dist).map((f) => relative(dist, f).split('\\').join('/'));
  const fileSet = new Set(files);
  const pages = files.filter((f) => f.endsWith('.html'));
  const html = Object.fromEntries(pages.map((f) => [f, readFileSync(join(dist, f), 'utf8')]));
  const anchors = (page) => { const t = html[page] || ''; return new Set([...t.matchAll(/\sid="([^"]+)"/g), ...t.matchAll(/\sdata-filter="([^"]+)"/g)].map((m) => m[1])); };

  if (fileSet.has('scam.html')) problems.push(...verifyScamData(dist));

  // the editor, reserved names
  for (const f of files) {
    const base = posix.basename(f), top = f.split('/')[0];
    if (/^(editor\.(js|css)|_preview\.html)$/.test(base)) fail(f, 'editor file in a production build');
    if (RESERVED_FILES.includes(base)) fail(f, 'a name the live server reserves for /steam and /debt');
    if (RESERVED_ROOT.includes(top)) fail(f, `${top} at the root is a live server route`);
  }

  // URLs: every one must be https or a file in dist (or a live route); fragments must exist
  let urls = 0;
  const checkUrl = (from, raw, what) => {
    urls++;
    const u = raw.trim();
    if (!u) return fail(from, `empty ${what}`);
    if (/^https:\/\//i.test(u)) return;
    if (/^[a-z][a-z0-9+.-]*:/i.test(u)) return fail(from, `${what} ${u} is not https`);
    const url = new URL(u, `https://site.invalid/${from}`);
    const path = decodeURIComponent(url.pathname);
    if (ROUTES.has(path)) return;
    const target = STATIC_ALIASES.get(path) || path.slice(1) || 'index.html';
    if (!fileSet.has(target)) return fail(from, `${what} ${u} does not resolve to a file in dist`);
    const frag = url.hash.slice(1);
    if (frag && target.endsWith('.html') && !anchors(target).has(decodeURIComponent(frag))) fail(from, `${what} ${u}: no #${frag} on ${target}`);
  };

  let imgs = 0;
  for (const page of pages) {
    const text = html[page];
    if (/\sdata-edit=/.test(text)) fail(page, 'data-edit attributes (an editor build)');
    for (const t of tagsOf(text)) {
      for (const name of Object.keys(t.a)) if (/^on[a-z]+$/.test(name)) fail(page, `<${t.name} ${name}=…> inline handler`);
      if (t.name === 'script') {
        const body = text.slice(t.end, text.indexOf('</script', t.end));
        if (!t.a.src || body.trim()) fail(page, 'inline <script>');
      }
      if (t.name === 'img') {
        imgs++;
        for (const k of ['width', 'height']) if (!/^[1-9]\d*$/.test(t.a[k] || '')) fail(page, `<img src="${t.a.src}"> has no ${k}`);
        if (!('alt' in t.a)) fail(page, `<img src="${t.a.src}"> has no alt`);
      }
      for (const k of ['href', 'src', 'poster']) if (k in t.a && !(t.name === 'meta')) checkUrl(page, t.a[k], `${t.name} ${k}`);
      if ('srcset' in t.a) for (const c of t.a.srcset.split(',')) checkUrl(page, c.trim().split(/\s+/)[0], `${t.name} srcset`);
      if (t.a.href && /^javascript:/i.test(t.a.href)) fail(page, 'javascript: URL');
    }
  }
  for (const css of files.filter((f) => f.endsWith('.css'))) for (const m of readFileSync(join(dist, css), 'utf8').matchAll(/url\(\s*(['"]?)([^'")]+)\1\s*\)/g)) if (!m[2].startsWith('data:')) checkUrl(css, m[2], 'url()');

  // private strings and TODOs in every text file
  const words = privateWords();
  for (const f of files.filter((x) => TEXT.has(extname(x)))) {
    const text = readFileSync(join(dist, f), 'utf8');
    for (const [re, what] of [...PRIVATE, ...words.re]) { const m = text.match(re); if (m) fail(f, `${what} ("${what === 'a private word' ? m[0][0] + '…' : m[0]}")`); }
  }
  // image metadata
  for (const f of files.filter((x) => /\.(webp|jpe?g)$/i.test(x))) {
    const buf = readFileSync(join(dist, f));
    if (f.endsWith('.webp')) {
      for (let i = 12; i + 8 <= buf.length;) { const id = buf.toString('ascii', i, i + 4), n = buf.readUInt32LE(i + 4); if (id === 'EXIF' || id === 'XMP ') fail(f, `${id.trim()} metadata`); i += 8 + n + (n & 1); }
    } else if (buf.includes('Exif\0') || buf.includes('http://ns.adobe.com/xap/1.0/')) fail(f, 'EXIF or XMP metadata');
  }

  // budget: the home page's first view, estimated from its HTML (Chrome also fetches lazy images within ~1250px of
  // the viewport, which on a desktop is everything above the register)
  const home = html['index.html'] || '';
  const tags = tagsOf(home), workAt = home.search(/<section id="work"/);
  const css = tags.filter((t) => t.name === 'link' && /\bstylesheet\b/.test(t.a.rel || ''));
  const fonts = new Set(css.flatMap((t) => { const p = new URL(t.a.href, 'https://site.invalid/').pathname.slice(1); return fileSet.has(p) ? [...readFileSync(join(dist, p), 'utf8').matchAll(/@font-face\s*{[^}]*url\(\s*['"]?([^'")]+)/g)].map((m) => m[1]) : []; }));
  const homeImgs = [...home.matchAll(/<img\b[^>]*>/g)];
  const eager = homeImgs.filter((m) => !/\sloading="lazy"/.test(m[0])).length;
  const early = homeImgs.filter((m) => /\sloading="lazy"/.test(m[0]) && (workAt < 0 || m.index < workAt)).length;
  const n = { html: 1, css: css.length, js: tags.filter((t) => t.name === 'script' && t.a.src).length, fonts: fonts.size, icon: tags.filter((t) => t.name === 'link' && /\bicon\b/.test(t.a.rel || '')).length };
  const total = n.html + n.css + n.js + n.fonts + n.icon + eager + early;

  const label = 'verify:';
  if (problems.length) {
    console.error(`✗ ${label} ${problems.length} problem${problems.length === 1 ? '' : 's'} in ${dist}`);
    for (const p of problems.slice(0, 60)) console.error('  ' + p);
    if (problems.length > 60) console.error(`  … and ${problems.length - 60} more`);
  } else {
    console.log(`✓ ${label} ${pages.length} pages, ${files.length} files, ${imgs} images, ${urls} URLs; no editor, inline script, reserved name, TODO or private string${words.from ? ` (private words from ${words.from})` : ' (no private word list: set PRISMET_PRIVATE_FILE)'}`);
  }
  console.log(`budget: home first view ≈ ${total} requests of ${BUDGET}${total > BUDGET ? ' (OVER)' : ''}: html ${n.html}, css ${n.css}, js ${n.js}, fonts ${n.fonts}, icon ${n.icon}, images ${eager + early} (${eager} eager + ${early} lazy above the register)`);
  return problems.length === 0;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) process.exit(verify(process.argv[2]) ? 0 : 1);
