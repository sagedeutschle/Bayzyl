// image-variants.mjs: narrower copies of the site's images, so pages can offer srcset instead of one big file.
//
//   node showcase/prismet-site/build.mjs        # the tool reads which images the built pages use
//   node showcase/tools/image-variants.mjs      # writes <name>-<w>.webp next to each source; skips files that exist
//   node showcase/prismet-site/build.mjs        # img() in build.mjs finds the variants and emits srcset/sizes
//
// Commit the variants with their sources: the build never encodes images. Pass --force to re-encode existing
// variants, --dry to only list what would be written.
//
// ImageMagick 6 ignores -quality for WebP (it encodes at libwebp's default, q75); method=6 and sharp YUV keep small
// UI text and saturated reds (Helm, PrismCode) clean at that quality.
import { readFileSync, readdirSync, existsSync, statSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const SHOWCASE = join(dirname(fileURLToPath(import.meta.url)), '..');
const DIST = join(SHOWCASE, 'prismet-site/dist');
const FORCE = process.argv.includes('--force'), DRY = process.argv.includes('--dry');

// Which images get which widths. First match wins; [] means none (the reason is on the line).
const RULES = [
  [/\/bench\/step-\d+\.webp$/, []],              // 960 px frames: at most 1.2× the 820 px stage, and a 390@3× phone needs all of it
  [/\/worlds\/long-now-[a-z]+\.webp$/, []],      // 1280 px eras: at most 1.7× the 760 px stage
  [/\/minecraft\/server-dark-spire-/, []],       // the home plate has its own 860/1655 pair
  [/prismcode-compare-door\.webp$/, []],         // 960 px, drawn at 150 % of the door
  [/-thumb\.webp$/, [192], (n) => `${n}x${n * 5 / 8}^`], // register rows: 96×60 boxes (72×48 on phones), filled like the 320×200 thumbs
  [/\/tiles\/[^/]+\.webp$/, [128]],              // 72 px arcade tiles, 64 px lens tiles
  [/\/icons\/prismet-app\.webp$/, [128]],        // the favicon
  [/\.webp$/, [360, 720, 1080, 1440]],           // plates, doors, frontispieces, the Helm rack
];

const webpSize = (buf) => {
  const chunk = buf.toString('ascii', 12, 16);
  if (chunk === 'VP8X') return { w: 1 + buf.readUIntLE(24, 3), h: 1 + buf.readUIntLE(27, 3) };
  if (chunk === 'VP8 ') return { w: buf.readUInt16LE(26) & 0x3fff, h: buf.readUInt16LE(28) & 0x3fff };
  if (chunk === 'VP8L') { const b = buf.readUInt32LE(21); return { w: 1 + (b & 0x3fff), h: 1 + ((b >> 14) & 0x3fff) }; }
  throw new Error('not a WebP');
};

if (!existsSync(join(DIST, 'index.html'))) { console.error('✗ build the site first: node showcase/prismet-site/build.mjs'); process.exit(1); }
const pages = ['index.html', 'colophon.html', ...readdirSync(join(DIST, 'work')).map((f) => 'work/' + f)].filter((f) => existsSync(join(DIST, f)));
const used = new Set();
for (const page of pages) {
  const html = readFileSync(join(DIST, page), 'utf8');
  for (const m of html.matchAll(/<img\b[^>]*?\ssrc="([^"]+)"|<link\b[^>]*?rel="icon"[^>]*?href="([^"]+)"/g)) {
    const p = (m[1] || m[2]).replace(/^(\.\.\/)+/, '').replace(/[?#].*$/, '');
    if (/\.webp$/.test(p)) used.add(p);
  }
}

let wrote = 0, bytes = 0, kept = 0, dropped = 0;
for (const p of [...used].sort()) {
  const [, widths, fill] = RULES.find(([re]) => re.test(p));
  const src = join(SHOWCASE, p), { w } = webpSize(readFileSync(src));
  for (const n of widths) {
    if (!fill && n > w * 0.9) continue;               // a variant within 10 % of the source saves nothing
    if (fill && n >= w) continue;
    const out = src.replace(/\.webp$/, `-${n}.webp`), name = out.slice(SHOWCASE.length + 1);
    if (existsSync(out) && !FORCE) { kept++; continue; }
    if (DRY) { wrote++; console.log(`would write ${name}`); continue; }
    execFileSync('convert', [src, '-strip', '-filter', 'Lanczos', '-resize', fill ? fill(n) : `${n}x`, '-define', 'webp:method=6', '-define', 'webp:use-sharp-yuv=true', out]);
    const size = statSync(out).size, of = statSync(src).size;
    // a narrower file that isn't lighter (alpha, flat UI) saves no bytes: don't keep it
    if (size > of * 0.95) { rmSync(out); dropped++; console.log(`dropped ${name}: ${(size / 1024).toFixed(1)} KB, not lighter than the source`); continue; }
    wrote++; bytes += size;
    console.log(`wrote ${name} (${(size / 1024).toFixed(1)} KB)`);
  }
}
console.log(`${used.size} images in use; ${wrote} variants ${DRY ? 'to write' : `written (${(bytes / 1024).toFixed(0)} KB), ${dropped} dropped`}, ${kept} already there`);
