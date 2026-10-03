# prismet.xyz — the workshop redesign, review for Sage (2026-10-02)

Branch `claude/funny-wozniak-4j636o`. Preview: https://claude.ai/artifact/C51vasTVQgxsp6JLJjccCv (private; Edit page mode works).
Not deployed. Production stays on Fly v18 until you say otherwise.

## 1. The theme

prismet.xyz is a **workshop and the register it keeps**. The home page opens on a hall plan: six wings (your
six categories) around a rotunda with the prism in its floor. Each wing is a door into the register, a ruled
ledger of every record. Projects are rooms off that hall, and each room has one material and one signature
module: the Bayzyl bench (nine real commands, one at a time), the Arcade tile wall, the Helm rack in Helm's own
header grammar, the Long Now era dial, the PrismCode schematic. Night is slate and chalk, sampled from your
night builds; day is cloud and ink, from the build above the clouds. Brass for labels, amethyst for what runs
live. There are no textures, no glows except the plan's single lantern, no invented lore, no call numbers.
Everything "magical" is real: a command that built a dome, a plan drawn loosely after your own plaza, a
screenshot at honest size.

## 2. Source material (exact paths in this repo)

- Palette and the plate: `showcase/fiverr/screenshots/02-minecraft/server-build-dark-spire.jpg` (night sky
  #0F161D, the lantern, the single beacon line), `server-build-above-the-clouds.jpg` (cloud #E4E8EA, day mode),
  `server-build-cherry-blossom-village.jpg` (lamplight).
- The hall plan's geometry: `docs/images/showcase-overview.webp` and `showcase-plaza-dome.webp` (square court,
  four piers, central dome); the tower elevation from `showcase-tower.webp`.
- The bench stepper: `docs/images/bayzyl-timelapse.gif`, split into nine captioned steps
  (`showcase/assets/bench/step-0..8.webp`, commands in `data/projects.json → bayzyl.steps`).
- The Arcade: `showcase/shots/prismet-app/raw-iphone-v14/shot_*.webp` and `showcase/assets/prismet/tiles/*.webp`
  (21 tiles; the scrapped Oracle tile is out).
- The Helm rack: `showcase/fiverr/screenshots/03-linux-desktop-helm/widgets/helm-widget-*.png` at 2×
  (`showcase/assets/helm2/`; the Fleet face is out: it lists machine names).
- The Long Now era dial: `showcase/fiverr/screenshots/06-games-and-mods/the-long-now-{medieval,industrial,dyson}-era.jpg`.
- PrismCode: `showcase/shots/prismcode/prismcode-{02,03,06,07,09,10,11}-*.webp` (bottom 3% cropped: it showed a
  home-folder path).
- Type: Newsreader and Martian Mono, re-subset from `github.com/google/fonts` (`showcase/assets/fonts/`).
- Documentation mined for truth checks: `README.md`, `docs/DETAIL_BRUSHES.md`, `docs/PARAMETRIC_NOISE_BRUSHES.md`,
  `showcase/fiverr/FIVERR-KIT.md`, the Fiverr handoff on `claude/great-gates-hovh8a`.
- NOT LOCATED in this container (they live on your Mac): Volhaven/Velthir (anything), the 13 Godot Long Now
  shots and six era renders, `ART-CANON.md`, `sageskillz.md`, `prismet-site.bundle`, and the newer
  `~/Desktop/GtrktscrB/business/showcase`.

## 3. What changed

- A new generator, stylesheet and script (`build.mjs`, `src/site.css`, `src/site.js`), still zero-dependency,
  still plain HTML/CSS/JS with no inline scripts. Every `<img>` carries width and height.
- Fonts: Unbounded, Hanken Grotesk, JetBrains Mono and Pixelify Sans leave production; Newsreader (display and
  body) and Martian Mono (data only) stay, subset with arrows and math symbols; the italic is instanced small.
- Data: `projects.json` gains `room`, `tier`, `hidden`, `related`, `doorFacts`, `steps`, `tiles`, `rack`, `eras`
  and an ordered `skills` list. Five records are hidden (the Decree, WoW Sidepanel, Yggdrasil, Westeros,
  Helix) with `noindex` stubs at their old URLs.
- Images: Helm faces at 2×, cropped Minecraft plates, PrismCode crops, Steam Rewind crops without the Steam ID,
  register thumbnails, a share image captured from the entrance.
- Removed from the public tree (host names or account ids): the Helm iPhone captures, the Fleet face, the
  PrismCode launch screen, the Linux gig board, every Steam Rewind capture with the ID field.
- Your wording is untouched. New labels live only in new `content/site.md` keys under the 2026-10 comment.
- `--preview` writes to `dist-preview/`, so `editor.js` can never land in `dist/`.
- `showcase/HANDOFF.md` and `README.md` are rewritten for the new state.

## 4. Homepage

Entrance: a small-caps plaque (your eyebrow), "Hello!" as a salute, your name as the H1, your lede, a directory
(Source → GitHub, Contact → LinkedIn, Commissions → Fiverr), the two buttons, and the hall plan on the right
with each wing naming its lead record ("Helm + 1"). Then Principal works (four doors), All work (the register
with wing chips, a stack column and access links), one captioned plate, Fun Analytic Tools (the lenses as live
instruments), and About + What I can build for you with a skills ledger linked to evidence and three doors.
On phones the plan becomes a two-column list of wings and the doors stack.

## 5. Project system

One skeleton on every record: breadcrumb, title, subtitle, status plaque, links; the signature module for
flagships; summary, highlights, facts, stack, threads; plates; previous / back / next. Identity comes from the
room tokens and the module, never from a different layout: Bayzyl's stone bench and chat-gold commands, the
Arcade's navy and tile wall, Helm's oxblood rack, the Long Now's soil band and era dial, PrismCode's blueprint
band and schematic. Standard records get a frontispiece instead of a module.

## 6. Before / after

- The first viewport now carries your name as the largest type, your lede, GitHub and LinkedIn, and six wings
  naming real work. v18 led with "Hello!" and a prism that named categories only.
- The register replaces a 3,400px card grid with a 13-row ledger that filters, deep-links and works without JS.
- Every flagship has a module built from its own material instead of a cover image in a rounded box.
- No horizontal galleries, no placeholder cards, no "Screenshots coming soon", no "TODO" on a live page.
- Layout shift over a full scroll fell from 0.18 to under 0.001; the Bayzyl page's throttled LCP from 4.8 s to 1.5 s.
  axe reports no violations on any page in either mode.

## 7. Risks

- The home page's first view is 22 requests at 1440 (budget 20) because the door modules are real images
  (nine tiles, four faces); the server limits 75 requests a minute per IP, so a fast click-through of three
  pages stays under it, but only just.
- Fonts are 214 KB (v18 shipped 124 KB) and throttled first paint went from 0.8 s to 1.3 s.
- The hall plan is the most experimental element: distinctive, but it is a diagram some visitors will not read
  as navigation. The chips below it do the same job.
- Claims in your own wording that the studio could not verify are left untouched and off the doors (see HANDOFF
  "Waiting on Sage"): "three live-data lenses", "Watch", "(see Agent Ops)", 237 Python tests, "28 QML faces",
  the VSCODIUM and Install facts, "Prismet" vs "Prismet Arcade", "co-developed".
- The plate's caption, "A night build on the Minecraft network Sage runs", rests on the Fiverr copy you approved;
  who built it is still yours to say.
- Volhaven appears nowhere: the Fiverr spec excludes it and nothing exists in the repo.
- Your Mac's newer `business/showcase` has not been reconciled; wording and the project list there may differ.

## 8. Preview

https://claude.ai/artifact/C51vasTVQgxsp6JLJjccCv (private). Project pages are reachable from the home page;
Edit page mode saves words and layout to the artifact's database as before.

## 9. Deployment

Nothing is deployed. Deploying means: reconcile the Mac copy, answer the open claims, then push the overlay
image and `fly deploy` per the handoff. Say yes explicitly if you want this version live.
