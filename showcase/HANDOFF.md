# prismet.xyz — handoff (2026-10-03)

For whoever takes over Sage Deutschle's website: Sage, a future Claude session, or Codex. Read it all before you
touch production. This repository is public: no secrets, tokens, host names, IPs or other people's names belong in
it, in this file included.

## 1. Where things stand

| | |
|---|---|
| Live | https://prismet.xyz, Fly app `prismet-site-restless-horizon-217`, release **v22** (2026-10-03 18:17 UTC), image tag `site-11-0ca1c66`, built from `main` at `0ca1c66` by the GitHub Action: the first automatic deploy |
| Source of truth | this repository, `showcase/`, on `main` (merged from `claude/funny-wozniak-4j636o` through [PR #2](https://github.com/sagedeutschle/Bayzyl/pull/2) on 2026-10-03) |
| Deploys | the GitHub Action `site` (`.github/workflows/site.yml`): every push under `showcase/` builds, verifies and gates the site; from `main` it deploys to Fly. The `FLY_API_TOKEN` secret is set (Sage, 2026-10-03); every commit to `main` under `showcase/` now goes live by itself |
| Editing | https://prismet.xyz/edit: enter the PIN, edit, Publish; the server commits to `main` with its stored token and the Action deploys. Guide: `showcase/EDITING.md`. Locked out until the `EDIT_GITHUB_TOKEN` and `EDIT_PIN` secrets exist |
| Preview with Edit mode | https://claude.ai/artifact/C51vasTVQgxsp6JLJjccCv (private; the round-3 build). Its database (`edits`, `layout/main`) is empty as of 2026-10-03; the `/edit` page supersedes it for words |
| Decisions only Sage can make | `showcase/prismet-site/ASK-SAGE.md` (wording claims, captures, authorship, `PROJECTS_URL`, the Mac copy, Volhaven) |
| In flight elsewhere | Codex (at Sage's request) holds a local, unpushed candidate limited to `content/work` reconciliation, selected asset metadata, the `/privacy` and `/support` pages and route coverage. The server already maps those two routes (v22); the pages themselves are not in the repo yet |

What a visitor gets today: the workshop redesign (rounds 1–3), Sage's wording verbatim, Sage's v18 type, every
protected route intact, the two privacy leaks of the old server closed.

## 2. Rules

1. **Sage's wording goes in verbatim.** `content/site.md` above the "2026-10 redesign" comment and every
   `content/work/*.md` are Sage's. Agents add new keys below that comment and flag them as proposed; they do not
   rewrite Sage's lines. Sage edits anything through `/edit`.
2. **Real visuals only.** Screenshots and renders of the actual work; cropping, resizing, masking a private string
   and re-encoding to WebP are fine. No mockups, generated images, collages or device frames.
3. **Privacy.** No host names, IPs, mesh addresses, emails, account names, Steam ids, clipboard text or other
   people's names on the site or in this repo. `verify.mjs` enforces the mechanical part on every build; the word
   list of host names lives outside the repo (secret `PRISMET_PRIVATE_WORDS` or `PRISMET_PRIVATE_FILE`).
4. **Never break these routes:** `/api/wordle` (the shipping apps hard-code it; JSON shape
   `{"answer","date","sourceName"}`), `/rtc` (WebSocket signalling), `/steam` and `/debt` (linked from the app's
   Settings; they load `/style.css`, `/steam.js`, `/debt.js`, `/icon.svg`, `/manifest.webmanifest` from the
   server's `public/`). The build must never emit those names; `verify.mjs` refuses them, `gate.sh` probes them.
5. **Production.** Sage's own edits deploy themselves through the Action; that is the point. An agent still needs
   Sage's explicit yes in its session before it merges to `main`, pushes an image or runs `fly deploy`, and never
   deploys someone else's unfinished work from an old checkout.
6. **Before a manual deploy, collect every source of edits:** `main`, the preview's database, and whatever Sage's
   Mac still holds that was never pushed (section 9).

## 3. Map of the repository

| What | Where |
|---|---|
| The renderer | `showcase/prismet-site/pages/lib/render.js`: words + data → HTML, pure, shared by the build, the editor's preview and the tests (`edit-check.mjs` holds the two to the same bytes) |
| The site generator | `showcase/prismet-site/build.mjs` (zero dependencies; `--preview` writes `dist-preview/` with the artifact editor, so `editor.js` never lands in `dist/`) |
| Build check | `showcase/prismet-site/verify.mjs`, run by the build: no editor code, no inline scripts or handlers, every `<img>` with width/height/alt, every link resolving in `dist/` or https (extension-less internal links allowed: `/steam /debt /edit /privacy /support`), no reserved file name, no TODO, no private string; prints the home page's request budget |
| Wording | `showcase/prismet-site/content/site.md` (home, labels, colophon) and `content/work/<slug>.md` (title, subtitle, tag, status, year, role, summary, facts, highlights). Format: `## key` headings, text under them, `*gold*`, `**bold**`, `- ` lists, `- Label: Value` facts, `{placeholders}` |
| Structure, images, links | `showcase/prismet-site/data/projects.json` (`room`, `tier`, `hidden`, `related`, `aliases`, `cover`, `gallery`, `shotAlts`, `doorFacts`, `steps`, `tiles`, `rack`, `eras`, `layout`, `featuredOrder`, `skills`) |
| Styles and script | `showcase/prismet-site/src/site.css`, `src/site.js` (preview editor: `editor.js`, `editor.css`) |
| Standalone pages | `showcase/prismet-site/pages/` copied verbatim into `dist/`: `edit.html` + `edit.js` + `edit.css` (the editor), `robots.txt`; `scam.html` + `scam.js` + `scam-calc.js` + `scam.css` + `scam-seal.svg` + `scam-data/` (the Uncle Scam lens at `/scam`: a tax receipt worked out in the browser; data rebuilt by `showcase/tools/uncle-scam/build-data.mjs`, checked by `tools/tests/scam-check.mjs`; the salary and ZIP code never leave the page). Future: `privacy.html`, `support.html`, `legal.css` |
| Site images | `showcase/assets/{ai,bench,helm2,icons,live,minecraft,prismet,web,worlds,og,fonts}/`; width variants `<name>-{128,192,360,720,1080,1440}.webp` beside their sources; register thumbnails `<cover>-thumb.webp` |
| Captures not on the site | `showcase/shots/<project>/`; `shots/README.md` ranks what only Sage can capture |
| The server | `showcase/server/` (section 7) |
| Deploy tooling | `showcase/tools/deploy/`: `assemble-overlay.sh`, `push-overlay.py`, `gate.sh`, `check-routes.sh`, `scrub-public.sh`, `fly.toml` |
| Tests | `showcase/tools/tests/site-check.mjs` (44 browser checks), `edit-check.mjs` (126 checks of the editor's parser, writer and client), `edit-smoke.mjs` (the editor in Chromium with GitHub mocked) |
| Other tools | `showcase/tools/image-variants.mjs`, `shoot-og.mjs` (the share image), `prismcode-shots/` |
| The workflow | `.github/workflows/site.yml`, `.github/dependabot.yml` |
| Reviews and asks | `showcase/prismet-site/REDESIGN-REVIEW.md` (round 1), `ROUND2-REVIEW.md`, `ASK-SAGE.md`, `showcase/EDITING.md` |
| Fiverr | `showcase/fiverr/`: a separate workstream. As of 2026-10-03 the local receipt on Sage's Mac reports all four gig drafts complete (V2 copy and images, skills, the co-developed portfolio credit), nothing published, identity and W-9 with Sage |

## 4. How the site is built

- `node showcase/prismet-site/build.mjs` writes `dist/` and must end with
  `built 13 project pages + 5 withdrawn stubs + index + colophon` and a `✓ verify` line. It reads WebP headers for
  image sizes, emits `srcset` from the width variants, appends `?v=<content hash>` to every asset URL (the server
  caches those for a year), copies fonts, the share image, `favicon.ico` and `pages/`.
- Content keys are mandatory: a missing key in `site.md` fails the build. The editor never deletes keys.
- After adding or replacing an image: build, `node showcase/tools/image-variants.mjs`, build again, commit the
  variants. Replacing a source means deleting its old variants first; they are not regenerated when present.
- Tests: `node showcase/tools/tests/site-check.mjs` after a build (needs Chromium; see Gotchas), `edit-check.mjs`
  (Node only), `edit-smoke.mjs` (Chromium). The Action runs all of them.
- Budgets at 2026-10-03: home first view 19 requests at 1440 (14 at 390), flagship pages 505/443/429 KB on a full
  scroll, no image drawn above 2× its CSS width, CLS under 0.01, axe clean in both modes. Fonts are 213 KB.

## 5. The design, in short

**2026-10-03 revision (Sage's brief: no yellow tint, blues and greens, Marcellus, fewer machine-made habits).** Night is
deep water `#0D1820` with cool white ink; day is pale sky `#EEF3F5`. Green is the accent, blue marks what is live, and
the six wing hues run green to indigo. Words are set in Marcellus (one weight, no italic: `font-synthesis: none`, so
emphasis is colour and size), figures in Martian Mono; Unbounded and Hanken Grotesk are retired. Removed: capital
letter-spaced micro-labels, boxed plaques and status dots, the arrow after outbound links, doubled section rules, the
pointer-following glow, the plan's drawing animation and the hover corner ticks. The token names `--brass` and
`--lantern` are unchanged (the editor and `data/theme.json` use them): read them as accent and second accent. Not yet
restyled: `/steam`, `/debt` (`server/public/style.css`), the share image, the editor's own chrome. Where the text below
says slate, chalk, brass or lantern, it describes the earlier palette.

**2026-10-04, "prism and glass" (Sage picked this direction for a solarpunk feel).** The palette follows the visitor's
clock while no theme has been chosen: `site.js` sets `data-phase` (dawn 5 to 8, day 8 to 17, dusk 17 to 20, night) and
`data-auto`; dawn and day use the day tokens, dusk and night the night tokens, and dawn and dusk change the ground.
`?phase=dawn|day|dusk|night` shows one phase at any hour. The toggle carries an arc with the sun or moon where the
clock puts it; choosing a theme stops the cycle. In the plan a sunbeam (`--sun`, the only gold on the site) comes
down into the prism and leaves as six rays in the wing colours; a ray thickens under the pointer. Section heads carry
a strip of six panes; wing markers are small arched panes; the principal works sit in frames with arched top corners
and their wing's colour along the top; the plate is an arched window. The footer states the footprint
(`footer.footprint`). Wording, same day, at Sage's request ("tone it down"): the hero and the page description say
developer tools instead of AI agent systems, the sixth wing is named Developer Tools, the hire line is Developer
tooling setup, and the Prismet page no longer says how it was built. The three projects about AI keep their own words.

**2026-10-04, the emblem.** Sage found the hall plan too plain and pointed at a profile picture (a circuit letter
inside a wreath of leaves). The plan is now an emblem drawn by `plan()` in `pages/lib/render.js`: a dark disc inside
three rings of leaves, the prism lit at the centre, a sunbeam from the top, a sprout at the foot, fireflies, and six
modules (icon badge, name, lead record) joined to the prism by glowing traces in the wing colours. It is all SVG from
numbers (`rnd` is a fixed sequence, so every build draws the same wreath); no image files. It keeps its own night
colours in every phase. The wreath sways and the fireflies glint unless reduced motion is asked for. Under 900px the
emblem stays as a picture with icons only and the wing list below names the wings. The anchors are still
`#plan a.wing[data-beam]` with a `.room` inside, which `site.js` and `site-check.mjs` rely on.

**2026-10-04, tech and nature woven together** (Sage: "a marriage between tech and nature", and carry it into the
page's background). Motifs taken from solarpunk writing: Art Nouveau curves, greenhouse and geodesic glass, solar
cells, climbing vines. In the emblem: a flower of photovoltaic petals turns slowly around the rotunda, the modules are
chips with pins, each trace puts out leaves where it bends, two vines climb inside the wreath carrying small lamps in
the wing colours, the leaves have veins, the dome's triangle lattice shows through the disc, and a sun sits where the
beam enters. Behind every page: canopy light from the top right, the same lattice fading toward the middle so text
sits on clear ground, and from 1200px a vine that is also a circuit (stem, leaves, traces ending in joints) up each
margin. The background art is two `data:` SVGs inside `site.css` (`body::before`, `body::after`), so nothing is
fetched; `#work` is slightly see-through so the weave carries behind it.

The site is **a workshop and the register it keeps**. The home page opens on a hall plan, six wings (Sage's six
categories) around a rotunda with the prism in its floor; each wing filters the register. Then four doors (Bayzyl,
Prismet, Helm, PrismCode, each with one signature module built from its own material), the register (a ledger grouped
Principal / Records / Cabinet with a seek line, thread ticks, a stack column and honest access links), one captioned
plate, the lenses, About and Hire. Every record shares one skeleton; `room` gives it its material and module: `bench`
(nine real Bayzyl commands with a tower elevation drawn from the numbers in two of them), `arcade` (the tile wall),
`bridge` (the Helm rack in Helm's own header grammar), `museum` (the era dial), `notebook` (the PrismCode schematic),
`observatory`, `council`, `cabinet`. Type, since round 3 ("less claudy, more Sage"): Unbounded for names and headings,
Hanken Grotesk for reading, Martian Mono caps for labels and data, the three faces Sage chose for v18; no serif, no
italic. Night is slate and chalk, day is cloud and ink, brass for labels, amethyst for what runs live. No textures, no
glows but the plan's lantern. Reduced motion shows finished states; everything works without JavaScript.

Hidden records (`"hidden": true`, a `noindex` stub at the old URL): Wizard King's Decree, WoW Sidepanel, Yggdrasil,
Westeros for UEBS 2, Helix Research Desk.

## 6. Editing the live site yourself (the self-serve layer)

**For Sage.** Open https://prismet.xyz/edit, enter the PIN, change any field, press Publish. The page
commits the file to `main` and watches the build; "Live on prismet.xyz" means done, about three minutes. The same
happens for any file edited on github.com. `showcase/EDITING.md` is the full guide, including which file holds what,
how to hide or add a project, how to add a screenshot, and how to undo (revert the commit on GitHub).

**What the Action does** (`.github/workflows/site.yml`, actions pinned to release commits, Dependabot keeps them
current, jobs time out at 15–20 minutes, deploys are serialised):
1. `build`: builds the site (verify included), runs `edit-check.mjs`, installs the server from its lockfile, starts
   it with the new build and runs `gate.sh` against it, `/api/wordle` included. A failure stops here; nothing
   changes on the site.
2. `browser-check`: `site-check.mjs` and `edit-smoke.mjs` in Chromium. Reported, not blocking.
3. `deploy` (only `main`, or a manual run with "deploy" ticked): assembles the overlay, pushes the image, deploys
   with the app's `fly.toml`, runs `gate.sh https://prismet.xyz live`. It runs in the GitHub environment
   `production`; add a required reviewer there if you ever want a manual approval step.

**Secrets** (repository Settings → Secrets and variables → Actions):
- `FLY_API_TOKEN`, required. A deploy-scoped token for the app, made on a machine logged into Fly as Sage:
  `fly tokens create deploy -a prismet-site-restless-horizon-217 -x 17520h -n github-actions`. The cloud session
  could not create one (its own token lacks that permission), so this is Sage's step. Rotate by creating a new one
  and replacing the secret; revoke the old with `fly tokens revoke`.
- `PRISMET_PRIVATE_WORDS`, optional: comma-separated host names and other words the build must refuse.

- `EDIT_GITHUB_TOKEN` and `EDIT_PIN`: the edit page's token (a fine-grained personal access token, one repository,
  Contents read and write, Actions read, up to a year's expiry) and the PIN (6+ characters). The deploy job stages
  both on Fly (`fly secrets set --stage`) before each deploy; change either by editing the secret and deploying.

**Security model.** The GitHub token lives only on the server (`showcase/server/edit-api.js`); the browser never
sees it. The page is public but inert without the PIN. A correct PIN sets an HttpOnly, Secure, SameSite=Strict
session cookie for eight hours; sessions live in the server's memory and die with a restart. Wrong PINs are limited
to five per address per fifteen minutes and twenty-five from anywhere per hour, with constant-time comparison. The
server proxies only an allow-list: list, read and write files under `content/` on one branch of one repository,
and read a workflow run; writes need the file's current sha and a custom request header (CSRF). The page's CSP
allows connections to the site only. `robots.txt` and a `noindex` meta keep it out of search. Whatever is written
still passes `verify.mjs`, the server gate and the live gate before it is served, and the build escapes all text.
The workflow holds `contents: read`; the deploy job alone sees the Fly token and the two editor secrets. `main` is
not branch-protected on purpose: required status checks would reject the editor's direct commits, and the gate
after the commit protects the site.

## 7. The server

- Source: `showcase/server/` (`server.js`, `signaling.js`, `wordle-daily.js`, `project-*.js`, `data/`, `public/`,
  `package.json` + lockfile; one dependency, `ws`). Extracted from the Fly image on 2026-10-03; `README.md` there.
- Runs as `node server.js` in `/app` of the image (Node 22 Alpine). Static lookup: `site/` (the build) first, then
  `public/` (the old tools). Refuses to start without `site/index.html`.
- Routes: `/healthz`; `/api/wordle` (proxies the daily word); `/api/steam` (needs `STEAM_WEB_API_KEY`);
  `/api/projects` (the bundled catalog); `/api/edit/*` (the editor: PIN session and GitHub proxy, `edit-api.js`,
  env `EDIT_GITHUB_TOKEN`, `EDIT_PIN`, optional `EDIT_REPO`, `EDIT_BRANCH`); `/rtc` (WebSocket); `/steam`, `/debt`, `/edit`, `/privacy`, `/support`
  map to `<name>.html`; `/shots/*` answers 404 (v21); everything else static.
- Headers on every response: CSP (`script-src 'self'`, `style-src 'self' 'unsafe-inline'`, `connect-src` self +
  Steam + Treasury), HSTS, `X-Frame-Options: DENY`, nosniff, referrer and permissions policies,
  `vary: accept-encoding`. Cache: `public, max-age=300`; `?v=<hash>` site files `max-age=31536000, immutable`.
- Rate limits per IP per minute: static 600 (plain-text 429 with `retry-after`), wordle 30, steam 12, rtc 20.
- Env on Fly: `PORT=8080`, `GITHUB_USER=sagedeutschle`; secret `STEAM_WEB_API_KEY`. `PROJECTS_URL` and
  `PROJECTS_CACHE_MS` were lost in the v19 incident; their values are only on Sage's side (ASK-SAGE).
- Patches carried on top of the v13 base image: v21 (`/shots` retired and whited out of the image, the Steam page
  scrubbed of the Steam id, rate limit, vary, immutable caching), v22 (`/edit`, `/privacy`, `/support`) and v23 (the
  edit API in `edit-api.js`). `assemble-overlay.sh` builds the layer from the repo: `server.js`, `edit-api.js`,
  `site/`, the two scrubbed Steam files, `whiteouts.txt` with `public/shots`. Any other server file that changes must be added to it.

## 8. Deploying

- **Normal:** merge or commit to `main`; the Action does the rest (section 6). Tags are `site-<run>-<sha7>`.
- **From a cloud session, by hand** (needs Sage's yes): build with the private word list, run the three tests,
  `showcase/tools/deploy/assemble-overlay.sh <dir>`, run the server from `showcase/server` with `site/` = `dist/`
  and `gate.sh http://127.0.0.1:18081 local`, then `FLY_API_TOKEN=… python3 showcase/tools/deploy/push-overlay.py
  <dir> <tag>` (`DRY_RUN=1` lists the layer without pushing) and, from `showcase/tools/deploy/`,
  `fly deploy --image registry.fly.io/prismet-site-restless-horizon-217:<tag> -a prismet-site-restless-horizon-217`,
  then `gate.sh https://prismet.xyz live`. Fly's remote builder and Docker are unavailable in the cloud session; the
  overlay (base image plus one layer) is how every deploy since v14 was made.
- **Always deploy with `showcase/tools/deploy/fly.toml`.** v19 (2026-10-02) was deployed with a minimal toml and
  `fly deploy` stripped the HTTP service and env from both machines: 503 for 13 minutes until v20.
- **Rollback:** revert the commit on GitHub (the Action redeploys), or `fly deploy --image <earlier tag>` from the
  `fly.toml` folder. v21 is `redesign-20261003-1`, v20 `redesign-20261002-1`, v18 `redesign-20261001-5`, the
  original v13 `deployment-01KYE5MM868CFCAWKRR3N9CKB1`.
- **Checks:** `gate.sh` is the pass/fail list; `check-routes.sh` prints status, type, size and cache-control of
  every route for a before/after comparison.

## 9. Waiting on Sage

1. **The two editor secrets** (section 6): `EDIT_GITHUB_TOKEN` and `EDIT_PIN` as repository secrets, then one deploy.
2. **The Mac copy** of `~/Desktop/GtrktscrB/business/showcase` was never pushed: a different `projects.json` and
   `build.mjs`, seven project copy files, seven Mac-only projects, 29 assets, `ART-CANON.md`, more Long Now captures.
   Codex is reconciling `content/work` from it; the rest is still Sage's call.
3. **Wording claims the studio could not verify**, listed with file and line in `ASK-SAGE.md`: "three live-data
   lenses", "Watch", "(see Agent Ops)", "237 Python tests", "28 QML faces", the VSCODIUM and Install facts,
   "Prismet" vs "Prismet Arcade", "co-developed", "Godot", Catan, the HOI4 observer console.
4. **Who built the Minecraft server builds** in `fiverr/screenshots/02-minecraft/`. The plate's caption says only
   "A night build on a Minecraft server I ran."
5. **`PROJECTS_URL`** (the old gist catalog): restore on Fly or drop.
6. **Volhaven / Velthir**: nothing in the repo; the 10-01 Fiverr spec excludes it from public surfaces.
7. **Captures that would strengthen the site**: `shots/README.md`, ranked.
8. **Git history** still holds the captures removed for privacy (Helm iPhone screens, the Fleet face, the WoW
   character screen, the unmasked PrismCode plates, Steam Rewind with the id). Rewriting history is Sage's call.
9. **The `/privacy` and `/support` pages** (Codex's candidate): the server routes exist; the pages go in
   `showcase/prismet-site/pages/` and must pass `verify.mjs` (no inline scripts, images sized, links resolving, no
   external fonts, no emails or host names). Add them to `gate.sh` when they land.

## 10. Known issues and backlog

- The share card loses the name in platforms that crop to a square (Slack compact, WhatsApp); large cards are fine.
- Fonts are 213 KB on every first visit.
- Register chips leave one orphan around 690px and 1090–1270px wide.
- The torus formula ring for Bayzyl was tried and dropped (unreadable around a circle).
- `/debt` wraps its headline figure at 390 and neither old tool links back to the home page beyond the brand mark
  (both in `showcase/server/public`, Sage's tool code).
- The `/edit` page (the editor: site tree, live preview, inspector) edits words, section and record order, hiding, featuring, records and design tokens, as a draft published in one commit. One element can carry its own style per width (`data/styles.json` → `pages/lib/styles.js`: an allow-list of properties, three tiers, base / 900px / 600px; the site marks styled wording with `data-s`). Pages and home sections come from a section library (`data/pages.json` → `pages/lib/sections.js`); images are added in a media library and travel with the publish to `assets/uploads/` (`pages/edit/media.js`; the server checks each is a WebP without metadata); the History tab opens a past revision as a draft (`/api/edit/history`, `?ref=`). Not yet: deleting images, scheduled pages, animation, structure of the built-in pages. Its parts: `pages/edit.html|js|css`, `pages/edit/{store,preview,panels}.js`, the renderer it shares with the build in `pages/lib/{render,format,theme}.js`, `data/theme.json`, `server/edit-api.js` (`/api/edit/commit`, `/api/edit/draft`), `tools/dev-editor.mjs` (the editor on a laptop against the checkout), `tools/tests/fake-github.mjs`.
- The preview artifact's Edit mode still writes to the artifact database; `apply-edits.mjs` applies those if ever used.
- The Fiverr `render.mjs` draws the Volumes face where the Fleet face was; re-render the gig board before using it.

## 11. Gotchas

- **The cloud session's production guard** refused a registry push once even with Sage's approval in the
  conversation; it went through after Sage repeated the instruction. Say what you are about to do and why.
- **`pkill -f "node server.js"` kills your own shell** when that text is in the same command. Keep the pid instead.
- **Chromium/Playwright** in the cloud session: `/opt/pw-browsers/chromium-1194/chrome-linux/chrome`, module at
  `/opt/node22/lib/node_modules/playwright/index.mjs`. Through the agent proxy, launch with
  `proxy: { server: process.env.HTTPS_PROXY }` and pin the proxy CA's key with
  `--ignore-certificate-errors-spki-list=<sha256 of the key in /root/.ccr/agent-proxy-ca.crt>`;
  `ignoreHTTPSErrors` alone drops one request in six. `www.prismet.xyz` is not reachable from the session.
- **`fly tokens create`** is not authorised for the session's token; Sage makes deploy tokens.
- **Subagents cannot write report files** into the scratchpad; have them return the report as their final message.
- **ImageMagick 6 ignores `-quality` for WebP**: use `-define webp:method=6` (and `webp:target-size=N` to cap).
- **Google Fonts is reachable through the proxy**; `pip install fonttools brotli` works for re-subsetting.
- **`_`-prefixed file names** cannot be published to the preview artifact; `index.html` cannot be listed in `files`.
- **The build fails on a missing content key**; the editor never removes keys, but a hand edit on GitHub can.

## 12. History

- v13 (2026-07-26): Sage's original site and server. v14–v18 (2026-10-01): the first redesign attempts, each the
  base image plus one layer. v19/v20 (2026-10-02): the workshop redesign, round 1, and the fly.toml incident.
- v21 (2026-10-03): round 2 (six-station studio: surveyed plan, doors, Bayzyl elevation, seek line, thread ticks,
  responsive images, cache-busting, verify, alt text, aliases, case notes, share card, favicon, privacy fixes in the
  tree), round 3 (Sage's type), and the v21 server patch. Approved by Sage ("deploy it claude").
- v22 (2026-10-03 18:17): the self-serve layer (section 6), merged to `main` through PRs #2, #7 and #8, deployed by
  the GitHub Action once Sage set `FLY_API_TOKEN`. `/edit` live; `gate.sh https://prismet.xyz live` passed.

## 13. First steps for the next agent

1. `git fetch`; confirm `main` builds and the Action's last run is green.
2. `fly releases -a prismet-site-restless-horizon-217` and `gate.sh https://prismet.xyz live`.
3. Read `ASK-SAGE.md` and the "Waiting on Sage" list; ask, don't guess.
4. Before any change to wording, check with Sage whether Codex's reconciliation has landed.
5. Never merge, push an image or deploy without Sage's yes in your own session.
