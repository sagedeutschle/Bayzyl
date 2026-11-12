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
const expectedFeatured = ['shorepact', 'prismet-app', 'bayzyl', 'command-helm-remote', 'the-helm', 'prismcode', 'the-long-now', 'steam-rewind'];
const expectedPortfolios = { bayzyl: 8, shorepact: 7, savepoint: 3, 'prismet-app': 5, 'the-helm': 8, prismcode: 2, 'the-long-now': 7, 'steam-rewind': 2, 'debt-clock': 2, 'qr-tools': 2, 'command-helm-remote': 5, castra: 1, 'labienus-qlora': 1, 'music-helm': 1 };
check(JSON.stringify(data.featuredOrder) === JSON.stringify(expectedFeatured), 'the eight reviewed principal projects retain their order');
check(JSON.stringify(galleries(home).filter((g) => g.includes('id="portfolio-')).map((g) => g.match(/id="portfolio-([^"]+)"/)[1])) === JSON.stringify(expectedFeatured), 'each reviewed principal project has its own homepage gallery');
check(JSON.stringify(data.projects.filter((p) => p.portfolio).map((p) => p.slug).sort()) === JSON.stringify(Object.keys(expectedPortfolios).sort()), 'only the fourteen reviewed projects have curated portfolios');
for (const [slug, count] of Object.entries(expectedPortfolios)) check(data.projects.find((p) => p.slug === slug).portfolio.length === count, `${slug}: reviewed gallery selection retained`);
const minecraftGallery = galleries(home).find((g) => g.includes('id="minecraft-build-gallery"'));
check(!!minecraftGallery && links(minecraftGallery).length === 8, 'eight separately curated Minecraft plate views');
check(data.projects.reduce((n, p) => n + (p.portfolio?.length || 0), 0) === 54, 'fifty-four curated views including the reviewed app refresh');
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
// Release and preview evidence must remain distinct in the four refreshed app galleries.
const project = (slug) => data.projects.find((p) => p.slug === slug);
const wording = (slug) => options.work[slug];
check(project('shorepact').portfolio.every((shot) => /^Native (Mac|iPhone) 1\.1 candidate · /.test(shot.evidence)), 'Shorepact scenes identify the 1.1 candidate');
check(/release candidate/.test(wording('shorepact').status) && /Apple availability follows review/.test(wording('shorepact').summary), 'Shorepact does not promise reviewed Apple availability');
check(/browser playtest requires an invitation/.test(wording('shorepact').summary) && /three companion games currently stay on one device/.test(wording('shorepact')['story.outcome']), 'Shorepact preserves invite access and original-game-only crossplay scope');
check(project('prismet-app').portfolio.every((shot) => /^Local (Mac|iPhone|browser) design preview · 9 November 2026$/.test(shot.evidence)), 'Arcade newer imagery remains a dated local design preview');
check(/On the App Store/.test(wording('prismet-app').status) && /not a promise that every detail is already in the App Store version/.test(wording('prismet-app').summary), 'Arcade separates the available app from preview imagery');
check(project('command-helm-remote').portfolio.every((shot) => shot.evidence === 'Native iPhone capture · built-in sample data'), 'Command Helm retains native sample-data provenance');
check(project('command-helm-remote').links.some((link) => link.href === 'https://apps.apple.com/us/app/command-helm/id6829226962') && /iPhone · iPad/.test(wording('command-helm-remote').facts) && /configured connection are required/.test(wording('command-helm-remote').summary), 'Command Helm retains its reviewed store link, platforms and host requirement');
check(project('savepoint').portfolio.every((shot) => shot.evidence === 'Native component preview · sample project · November 2026'), 'Savepoint imagery is labeled as component previews, not device screenshots');
check(wording('savepoint').status === 'In development' && project('savepoint').links.length === 0 && /rather than a complete application session or a publicly released product/.test(wording('savepoint').summary), 'Savepoint does not claim a public release or full app capture');
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
