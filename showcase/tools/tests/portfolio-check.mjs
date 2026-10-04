// Render contracts for native screenshot galleries. No browser or network required.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join, dirname } from 'node:path';
import { renderSite } from '../../prismet-site/pages/lib/render.js';
import { loadSite, loadWork } from '../../prismet-site/content.mjs';
const siteRoot = join(dirname(fileURLToPath(import.meta.url)), '../../prismet-site');
const data = JSON.parse(readFileSync(join(siteRoot, 'data/projects.json')));
const manifest = JSON.parse(readFileSync(join(siteRoot, 'dist/edit/assets.json')));
const assets = {
  has: (path) => path in manifest.files,
  size: (path) => ({ w: manifest.files[path]?.[0] || 0, h: manifest.files[path]?.[1] || 0 }),
  url: (path) => path ? `${path}?v=${manifest.files[path][2]}` : null,
};
const options = { data, site: loadSite(), work: Object.fromEntries(data.projects.map((p) => [p.slug, loadWork(p.slug)])), assets, urls: { css: 'site.css', js: 'site.js', og: 'https://prismet.xyz/assets/og.jpg' } };
const live = renderSite(options), home = live.pages.get('index.html');
let checks = 0;
const check = (ok, message) => { assert.ok(ok, message); checks++; };
const galleries = (html) => [...html.matchAll(/<ol class="gallery-track"[\s\S]*?<\/ol>/g)].map((m) => m[0]);
const links = (html) => [...html.matchAll(/<a class="gallery-image" href="([^"]+)"[^>]*>/g)].map((m) => m[1]);
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
check(data.featuredOrder.length === 6 && galleries(home).filter((g) => g.includes('id="portfolio-')).length === 6, 'all six principal projects have galleries');
check(data.projects.filter((p) => p.portfolio).length === 8, 'eight curated portfolios');
const minecraftGallery = galleries(home).find((g) => g.includes('id="minecraft-build-gallery"'));
check(!!minecraftGallery && links(minecraftGallery).length === 8, 'eight separately curated Minecraft plate views');
check(data.projects.reduce((n, p) => n + (p.portfolio?.length || 0), 0) === 40, 'all forty curated views retained');
for (const p of data.projects.filter((p) => p.portfolio)) {
  const html = live.pages.get(`work/${p.slug}.html`), gallery = galleries(html)[0];
  check(links(gallery).length === p.portfolio.length, `${p.slug}: entire selected sequence`);
  check(new Set(links(gallery)).size === p.portfolio.length, `${p.slug}: no duplicate slides`);
  for (const shot of p.portfolio) {
    check(links(gallery).includes(`../${assets.url(shot.src)}`), `${p.slug}: full-resolution fallback link`);
    check(gallery.includes(esc(shot.caption)) && gallery.includes(esc(shot.evidence)), `${p.slug}: caption and honest evidence label`);
    check(Object.keys(shot).every((field) => ['src', 'alt', 'caption', 'evidence'].includes(field)), `${p.slug}: no private provenance`);
  }
  check((gallery.match(/loading="lazy"/g) || []).length === p.portfolio.length - 1, `${p.slug}: only first detail slide eager`);
}
for (const [path, html] of live.pages) {
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map((m) => m[1]);
  check(new Set(ids).size === ids.length, `${path}: unique ids`);
  if (galleries(html).length) check((html.match(/<dialog /g) || []).length === 1 && html.includes('aria-labelledby="viewer-title"'), `${path}: one labelled viewer`);
}
check(galleries(home).every((g) => (g.match(/<img /g) || []).length === (g.match(/loading="lazy"/g) || []).length), 'homepage galleries all lazy');
for (const mode of ['edit', 'preview']) {
  const out = renderSite({ ...options, [mode]: true });
  const html = out.pages.get('index.html');
  check(!html.includes('<dialog ') && html.includes('data-gallery-edit'), `${mode}: overlay disabled for editor`);
  check(links(html).length >= links(home).length, `${mode}: actual fallback links retained`);
}
const modified = structuredClone(data), p = modified.projects.find((p) => p.portfolio);
p.portfolio = [{ ...p.portfolio[0], caption: '<script>unsafe</script> & "caption"', alt: '<img onerror="bad">', evidence: '<b>label</b>' }, p.portfolio[0], { src: '../../private.webp' }, { src: 'assets/missing.webp' }];
const adversarial = galleries(renderSite({ ...options, data: modified }).pages.get(`work/${p.slug}.html`))[0];
check(links(adversarial).length === 1, 'duplicate, missing and invalid paths filtered');
check(adversarial.includes('&lt;script&gt;unsafe&lt;/script&gt;') && adversarial.includes('&lt;b&gt;label&lt;/b&gt;') && !adversarial.includes('<script>unsafe'), 'captions, labels and alt are escaped');
console.log(`✓ portfolio-check: ${checks} render contracts passed`);
