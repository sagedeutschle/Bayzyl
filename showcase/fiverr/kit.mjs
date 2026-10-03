// kit.mjs — validates gigs.json against Fiverr's field limits, then writes:
//   showcase/fiverr/FIVERR-KIT.md   the copy-paste kit (profile, gigs, portfolio, upload steps)
//   showcase/fiverr/kit/index.html  the same kit as a page with copy buttons + web-sized images
//
//   node showcase/fiverr/kit.mjs [--preview]   (--preview writes kit/_preview.html for an artifact)
import { readFileSync, writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import { dirname, join, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, 'out');
const KIT = join(HERE, 'kit');
const PREVIEW = process.argv.includes('--preview');
const { profile, gigs, portfolio } = JSON.parse(readFileSync(join(HERE, 'gigs.json'), 'utf8'));

// ── limits (Fiverr seller UI as of 2026; re-check if Fiverr changes them) ──────────────────
const problems = [];
const check = (ok, msg) => { if (!ok) problems.push(msg); };
check(profile.about.length >= 150 && profile.about.length <= 600, `profile.about is ${profile.about.length} chars (150–600)`);
check(profile.oneLiner.length <= 70, `profile.oneLiner is ${profile.oneLiner.length} chars (≤70)`);
for (const g of gigs) {
  check(g.title.length <= 80, `${g.id}: title ${g.title.length} chars (≤80)`);
  check(g.description.length <= 1200, `${g.id}: description ${g.description.length} chars (≤1200)`);
  check(g.tags.length <= 5, `${g.id}: ${g.tags.length} tags (≤5)`);
  g.tags.forEach((t) => check(t.length <= 20 && /^[a-z0-9 ]+$/i.test(t), `${g.id}: tag "${t}" (≤20, letters/numbers/spaces)`));
  g.packages.forEach((p) => {
    check(p.name.length <= 35, `${g.id}: package name "${p.name}" (≤35)`);
    check(p.desc.length <= 100, `${g.id}: package "${p.name}" desc ${p.desc.length} chars (≤100)`);
  });
  g.faq.forEach(([q, a]) => check(a.length <= 300, `${g.id}: FAQ answer for "${q}" is ${a.length} chars (≤300)`));
  g.images.forEach((i) => check(existsSync(join(OUT, i)), `${g.id}: missing image ${i} (run render.mjs)`));
  check(g.images.length <= 3, `${g.id}: ${g.images.length} images (≤3)`);
}
if (problems.length) { console.error('Fiverr limit problems:\n  ' + problems.join('\n  ')); process.exit(1); }

// ── FIVERR-KIT.md ──────────────────────────────────────────────────────────────────────────
const money = (n) => `$${n}`;
const md = [];
md.push(`# Fiverr kit — fiverr.com/bayzyl

Everything to paste into Fiverr, plus the images in [\`out/\`](out/). Generated from
[\`gigs.json\`](gigs.json) by \`node showcase/fiverr/kit.mjs\`, which also checks every field
against Fiverr's limits. Edit the JSON, not this file.

> **Prices are starting points.** They sit in the middle of what similar gigs charge. Set
> your own before publishing. Fiverr adds its service fee on top for buyers.

## Images in this kit

Every image is built from real work: App Store screenshots, THE HELM widgets rendered from
their QML source, Playwright captures of the live qr-scanner tools, and text pulled straight
from Bayzyl's help pages and command registry. Diagrams are labeled as diagrams.
All are 1280×769 at 2× (2560×1538 PNG), Fiverr's recommended gig image ratio.

| Gig covers | Portfolio boards |
|---|---|
${['gig-mc-plugin', 'gig-mc-server', 'gig-ios-app', 'gig-web-tool', 'gig-ai-agents', 'gig-linux-desktop'].map((g, i) => {
  const p = ['portfolio-prismet-spread', 'portfolio-prismet-tiles', 'portfolio-helm-wall', 'portfolio-helm-arcade', 'portfolio-bayzyl-help', 'portfolio-bayzyl-commands'][i];
  return `| ![${g}](out/${g}.png) | ![${p}](out/${p}.png) |`;
}).join('\n')}
| | ![portfolio-mc-network](out/portfolio-mc-network.png) |
| | ![portfolio-qr-suite](out/portfolio-qr-suite.png) |
| | ![portfolio-oracle](out/portfolio-oracle.png) |

Also: [\`out/linkedin-banner.png\`](out/linkedin-banner.png) (1584×396) refreshes your LinkedIn
background with the full range instead of only the QR scanner.

## 1. Profile

**Display name:** ${profile.displayName}

**One-liner:** ${profile.oneLiner}

**Description** (${profile.about.length}/600):

> ${profile.about}

**Skills:** ${profile.skills.join(', ')}

**Languages:** ${profile.languages.join(', ')}

**Profile photo:** a clear, well-lit headshot outperforms a logo on Fiverr. If you'd rather not
show your face, use the Prismet app icon (\`showcase/assets/icons/prismet-app.webp\`).

## 2. Gigs
`);
gigs.forEach((g, i) => {
  md.push(`### ${i + 1}. I will ${g.title}

- **Category:** ${g.category}
- **Search tags:** ${g.tags.map((t) => `\`${t}\``).join(' · ')}
- **Gallery images:** ${g.images.map((im) => `[\`${im}\`](out/${im})`).join(', ')}

| | ${g.packages.map((p) => `**${p.name}**`).join(' | ')} |
|---|---|---|---|
| Price | ${g.packages.map((p) => money(p.price)).join(' | ')} |
| Delivery | ${g.packages.map((p) => `${p.days} days`).join(' | ')} |
| Revisions | ${g.packages.map((p) => p.revisions).join(' | ')} |
| What's included | ${g.packages.map((p) => p.desc).join(' | ')} |

**Description** (${g.description.length}/1200):

\`\`\`text
${g.description}
\`\`\`

**FAQ**

${g.faq.map(([q, a]) => `- **${q}** ${a}`).join('\n')}

**Requirements (questions buyers answer when ordering)**

${g.requirements.map((r) => `1. ${r}`).join('\n')}
`);
});
md.push(`## 3. Portfolio

Fiverr → your profile → **Portfolio** → **Add project**. For each, upload the images, paste
the description, and link the matching gig so buyers see proof on the gig page too.

${portfolio.map((p) => `### ${p.title}

- **Images:** ${p.images.map((im) => `[\`${im}\`](out/${im})`).join(', ')}
- **Linked gig:** I will ${gigs.find((g) => g.id === p.gig).title}

> ${p.desc}
`).join('\n')}
**Still to add (needs you):** *Westeros for UEBS 2*. Drop 3–6 screenshots into
\`showcase/assets/worlds/\`, fill in the \`westeros-uebs2\` entry in
\`showcase/prismet-site/data/projects.json\`, and it shows up on prismet.xyz and here.

## 4. Upload order

1. **Profile first.** Paste the description and one-liner, add the skills, and set your photo.
2. **Publish gigs one at a time**, strongest proof first: iOS apps → Minecraft plugins →
   Minecraft servers → AI agents → web tools → Linux desktops.
   For each: Overview (title, category, tags) → Pricing (three packages, matching the table) →
   Description & FAQ → Requirements → Gallery (images in the listed order; the first one is
   the cover) → Publish.
3. **Portfolio last**, linking each project to its gig.
4. **Point everything at everything:** put \`prismet.xyz\` in your Fiverr description and
   LinkedIn, and the redesigned prismet.xyz has a "Hire me on Fiverr" button and a list of
   these same six services.

## 5. After launch

- Answer messages fast. Response time is one of the signals Fiverr ranks new sellers on.
- After the first few orders, raise prices on whichever gig fills up first.
- Re-render the images after shipping something new:
  \`node showcase/fiverr/render.mjs\` (gig covers + boards), then \`node showcase/fiverr/kit.mjs\`.
`);
writeFileSync(join(HERE, 'FIVERR-KIT.md'), md.join('\n'));

// ── kit page (copy buttons) ─────────────────────────────────────────────────────────────────
rmSync(KIT, { recursive: true, force: true }); mkdirSync(join(KIT, 'img'), { recursive: true });
const webImg = (name) => {
  const out = 'img/' + basename(name, '.png') + '.webp';
  if (!existsSync(join(KIT, out))) execFileSync('convert', [join(OUT, name), '-resize', '1280x', '-quality', '84', join(KIT, out)]);
  return out;
};
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
let fid = 0;
const field = (label, text, { mono = false, rows } = {}) => {
  const id = `f${++fid}`;
  return `<div class="field"><div class="fh"><label for="${id}">${esc(label)}</label><span class="n">${text.length} chars</span><button type="button" class="copy" data-for="${id}">Copy</button></div>
  <textarea id="${id}" readonly rows="${rows || Math.min(18, Math.max(2, Math.ceil(text.length / 70) + (text.match(/\n/g) || []).length))}"${mono ? ' class="mono"' : ''}>${esc(text)}</textarea></div>`;
};
const SHORT = { 'mc-plugin': 'Minecraft plugins', 'mc-server': 'Minecraft servers', 'ios-app': 'iPhone & Mac apps', 'web-tool': 'Web tools', 'ai-agents': 'AI agents', 'linux-desktop': 'Linux desktops' };
const HUES = { minecraft: '#4D8C6B', apps: '#E68C33', web: '#3D75A8', ai: '#75579E', desktop: '#DB4757', worlds: '#CCA838' };
const body = `
<header class="top"><div class="wrap">
  <p class="eyebrow">fiverr.com/bayzyl · paste kit</p>
  <h1>Fiverr Gig Kit</h1>
  <p class="lede">Profile copy, six gigs, and six portfolio projects, each field checked against Fiverr's limits. Tap <b>Copy</b>, paste into Fiverr, move on. Prices are starting points.</p>
  <nav class="toc" aria-label="Gigs">${gigs.map((g) => `<a href="#${g.id}" style="--h:${HUES[g.beam]}"><i></i>${esc(SHORT[g.id] || g.id)}</a>`).join('')}<a href="#portfolio" style="--h:#E2B65C"><i></i>Portfolio</a></nav>
</div></header>
<main class="wrap">
<section id="profile" class="card">
  <h2>Profile</h2>
  ${field('One-liner', profile.oneLiner)}
  ${field('Description (150–600)', profile.about)}
  ${field('Skills', profile.skills.join(', '))}
</section>
${gigs.map((g, i) => `<section id="${g.id}" class="card gig" style="--h:${HUES[g.beam]}">
  <p class="eyebrow">Gig ${i + 1} of ${gigs.length}</p>
  <h2><span class="iwill">I will</span> ${esc(g.title)}</h2>
  <div class="shots">${g.images.map((im, k) => `<figure><img src="${webImg(im)}" alt="${esc(im)}" loading="${i < 2 ? 'eager' : 'lazy'}"><figcaption>${k === 0 ? 'Cover · ' : ''}${esc(im)}</figcaption></figure>`).join('')}</div>
  ${field('Title (after "I will")', g.title)}
  ${field('Search tags (one per box on Fiverr)', g.tags.join(', '))}
  <div class="tablewrap"><table><thead><tr><th></th>${g.packages.map((p) => `<th>${esc(p.name)}</th>`).join('')}</tr></thead><tbody>
    <tr><th>Price</th>${g.packages.map((p) => `<td class="num">$${p.price}</td>`).join('')}</tr>
    <tr><th>Delivery</th>${g.packages.map((p) => `<td class="num">${p.days} days</td>`).join('')}</tr>
    <tr><th>Revisions</th>${g.packages.map((p) => `<td class="num">${p.revisions}</td>`).join('')}</tr>
    <tr><th>Includes</th>${g.packages.map((p) => `<td>${esc(p.desc)}</td>`).join('')}</tr>
  </tbody></table></div>
  ${field('Description (≤1200)', g.description)}
  <details><summary>FAQ (${g.faq.length}) and buyer requirements (${g.requirements.length})</summary>
    ${g.faq.map(([q, a], k) => field(`FAQ ${k + 1}: ${q}`, a)).join('')}
    ${g.requirements.map((r, k) => field(`Requirement ${k + 1}`, r)).join('')}
  </details>
  <p class="cat"><b>Category:</b> ${esc(g.category)}</p>
</section>`).join('\n')}
<section id="portfolio" class="card">
  <h2>Portfolio projects</h2>
  <div class="pgrid">${portfolio.map((p) => `<article class="pf"><img src="${webImg(p.images[0])}" alt="" loading="lazy">
    <h3>${esc(p.title)}</h3>${field('Description', p.desc)}<p class="cat">Images: ${p.images.map(esc).join(', ')}</p></article>`).join('')}</div>
</section>
</main>
<script>
document.addEventListener('click', async (e) => {
  const b = e.target.closest('.copy'); if (!b) return;
  const t = document.getElementById(b.dataset.for);
  try { await navigator.clipboard.writeText(t.value); b.textContent = 'Copied'; }
  catch { t.focus(); t.select(); b.textContent = 'Selected, press ⌘C'; }
  setTimeout(() => { b.textContent = 'Copy'; }, 1600);
});
</script>`;
const css = `<style>
:root{color-scheme:dark;--bg:#10111A;--bg2:#0B0C13;--panel:#1B1D2A;--hi:#282B3C;--hair:rgba(255,255,255,.10);--line:rgba(255,255,255,.18);--ink:#F2F4FC;--ink2:rgba(242,244,252,.70);--ink3:rgba(242,244,252,.50);--gold:#E2B65C;--goldd:#B88A33;
--display:"Unbounded","Arial Black",sans-serif;--body:"Hanken Grotesk",system-ui,sans-serif;--mono:"Martian Mono",ui-monospace,monospace}
@media (prefers-color-scheme: light){:root:not([data-theme="dark"]){color-scheme:light;--bg:#F6F2E8;--bg2:#EEE8DA;--panel:#FDFBF6;--hi:#E6E0D1;--hair:rgba(28,26,18,.14);--line:rgba(28,26,18,.28);--ink:#1C1A12;--ink2:rgba(28,26,18,.78);--ink3:rgba(28,26,18,.58);--gold:#8E6A22;--goldd:#8E6A22}}
:root[data-theme="light"]{color-scheme:light;--bg:#F6F2E8;--bg2:#EEE8DA;--panel:#FDFBF6;--hi:#E6E0D1;--hair:rgba(28,26,18,.14);--line:rgba(28,26,18,.28);--ink:#1C1A12;--ink2:rgba(28,26,18,.78);--ink3:rgba(28,26,18,.58);--gold:#8E6A22;--goldd:#8E6A22}
:root[data-theme="dark"]{color-scheme:dark;--bg:#10111A;--bg2:#0B0C13;--panel:#1B1D2A;--hi:#282B3C;--hair:rgba(255,255,255,.10);--line:rgba(255,255,255,.18);--ink:#F2F4FC;--ink2:rgba(242,244,252,.70);--ink3:rgba(242,244,252,.50);--gold:#E2B65C;--goldd:#B88A33}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:400 16px/1.55 var(--body)}
.wrap{max-width:1080px;margin-inline:auto;padding-inline:clamp(16px,4vw,40px)}
.top{border-bottom:1px solid var(--hair);background:radial-gradient(70% 120% at 0% 0%,color-mix(in srgb,var(--goldd) 18%,transparent),transparent 70%)}
.top .wrap{padding-block:40px 28px}
h1,h2,h3{font-family:var(--display);font-weight:600;line-height:1.15;letter-spacing:-.02em;text-wrap:balance;margin:0}
h1{font-size:clamp(1.8rem,4.4vw,2.7rem);font-weight:700;margin-top:12px}h2{font-size:1.25rem}h3{font-size:1rem;margin:12px 0 4px}
.eyebrow{margin:0;font:600 .72rem/1.3 var(--mono);letter-spacing:.1em;text-transform:uppercase;color:var(--gold)}
.lede{color:var(--ink2);max-width:42rem;margin:14px 0 0;font-size:1.08rem}
.toc{display:flex;flex-wrap:wrap;gap:8px;margin-top:22px}
.toc a{display:inline-flex;align-items:center;gap:8px;text-decoration:none;color:var(--ink);font:600 .86rem/1 var(--body);padding:9px 12px;border-radius:999px;border:1px solid var(--line);background:var(--panel)}
.toc i{width:9px;height:9px;border-radius:50%;background:var(--h)}
main{padding-block:28px 64px;display:grid;gap:22px}
.card{background:var(--panel);border:1px solid var(--hair);border-radius:18px;padding:clamp(16px,3vw,28px);display:grid;gap:16px;min-width:0}
.gig{border-top:4px solid var(--h)}
.iwill{color:var(--ink3);font-weight:600}
.shots{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,220px),1fr));gap:12px}
.shots figure{margin:0;min-width:0}.shots img{width:100%;height:auto;display:block;border-radius:10px;border:1px solid var(--hair)}
.shots figcaption{font:500 .74rem/1.3 var(--mono);color:var(--ink3);margin-top:6px;overflow-wrap:anywhere}
.field{display:grid;gap:6px;min-width:0}
.fh{display:flex;align-items:center;gap:10px}
.fh label{font:600 .8rem/1.2 var(--mono);letter-spacing:.06em;text-transform:uppercase;color:var(--ink2);margin-right:auto}
.fh .n{font:500 .74rem/1 var(--mono);color:var(--ink3);font-variant-numeric:tabular-nums}
.copy{font:700 .8rem/1 var(--body);padding:8px 12px;border-radius:9px;border:1px solid var(--line);background:var(--hi);color:var(--ink);cursor:pointer;min-width:64px}
.copy:hover{border-color:var(--gold)}.copy:focus-visible,textarea:focus-visible,summary:focus-visible,a:focus-visible{outline:2px solid var(--gold);outline-offset:2px}
textarea{width:100%;resize:vertical;background:var(--bg2);color:var(--ink);border:1px solid var(--hair);border-radius:10px;padding:10px 12px;font:400 .95rem/1.55 var(--body)}
.tablewrap{overflow-x:auto}table{border-collapse:collapse;width:100%;min-width:520px;font-size:.92rem}
th,td{text-align:left;padding:9px 10px;border-bottom:1px solid var(--hair);vertical-align:top}thead th{font-family:var(--display);font-size:.82rem;font-weight:600}
tbody th{font:600 .74rem/1.3 var(--mono);letter-spacing:.08em;text-transform:uppercase;color:var(--ink3)}
.num{font-variant-numeric:tabular-nums;font-weight:600}
details{border-top:1px solid var(--hair);padding-top:12px}details>.field{margin-top:12px}
summary{cursor:pointer;font-weight:600;color:var(--ink2)}
.cat{margin:0;color:var(--ink3);font-size:.86rem}
.pgrid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,300px),1fr));gap:18px}
.pf{min-width:0;display:grid;gap:8px;align-content:start}.pf img{width:100%;border-radius:10px;border:1px solid var(--hair)}
</style>`;
const fonts = `<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Unbounded:wght@400..800&family=Hanken+Grotesk:wght@400..700&family=Martian+Mono:wdth,wght@75..112.5,400..700&display=swap">`;
writeFileSync(join(KIT, 'index.html'), `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Fiverr Gig Kit</title><link rel="stylesheet" href="../../assets/fonts/fonts.css">${css}</head><body>${body}</body></html>`);
if (PREVIEW) writeFileSync(join(KIT, '_preview.html'), `<title>Fiverr Gig Kit</title>${fonts}${css}${body}`);
console.log(`ok: ${gigs.length} gigs, ${portfolio.length} portfolio projects, all fields within Fiverr limits`);
console.log(gigs.map((g) => `  ${g.id.padEnd(14)} title ${String(g.title.length).padStart(2)}/80  desc ${String(g.description.length).padStart(4)}/1200`).join('\n'));
console.log(`  profile about ${profile.about.length}/600`);
