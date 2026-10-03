# prismet.xyz handoff — 2026-10-02 (the workshop redesign)

For the agent taking over Sage's website. Read all of it before you touch production. This
repo is public, so this file holds no secrets or internal host names, and anything you add
must not either.

## Where things stand

- **Live:** prismet.xyz runs Fly release **v20** (2026-10-02 14:48 UTC), image tag
  `redesign-20261002-1`, the workshop redesign built from commit **`1e69496`** on
  `claude/funny-wozniak-4j636o`. `/api/wordle`, `/steam`, `/debt` and `/rtc` all work; every route in
  `check-routes.sh` matches the v18 baseline. Sage approved the deploy in session
  `session_01LNDcbCcy64DGsNuWVV1hR6`.
- **Incident on the way there (read this before your first deploy):** release v19 was deployed with a
  minimal `fly.toml` holding only the app name and region. `fly deploy` treats the toml as the whole
  machine config, so it **stripped the HTTP service and the env vars** from both machines; Fly's edge
  answered 503 for about 13 minutes until v20 redeployed the same image with
  `showcase/tools/deploy/fly.toml` (http_service on port 8080, force_https, auto-stop/start). Always
  deploy with that file, or run `fly config save -a prismet-site-restless-horizon-217` first and deploy
  with the saved config. The old env vars `PROJECTS_URL` and `PROJECTS_CACHE_MS` were lost in the
  process and their values are not in any repo; `/api/projects` now serves the bundled catalog
  instead of the gist (nothing uses it). `GITHUB_USER` was set back to `sagedeutschle`, `PORT` to `8080`.
  Ask Sage whether the gist URL should be restored.
- **Source:** `showcase/prismet-site/` in this repo, on branch **`claude/funny-wozniak-4j636o`**
  (the redesign branch; it contains the old deploy branch `claude/kind-sagan-pbm82l` at `93e3987`
  plus the redesign commits). The content branch `claude/fiverr-prismet-redesign-jgyb6i` is still
  at `fcba36c`. `main` doesn't have the site.
- **Not reconciled:** Sage's Mac holds a newer `~/Desktop/GtrktscrB/business/showcase` (a different
  `projects.json` and `build.mjs`, seven project copy files, seven Mac-only projects, 29 assets).
  It was never pushed, so the redesign was built from the branch. **Reconcile it before any deploy**
  (wording and project list from the Mac; images, build and layout from this branch).
- **Preview with Edit mode:** artifact https://claude.ai/artifact/C51vasTVQgxsp6JLJjccCv (version 1, built
  from `23d3ca1`). Its database starts empty. The older preview (`Nig6eZ26fLTtBzgiTzQfQ2`) shows the v18 design.
- **Server code:** not in any repo. See "The live server". The server needs no change for the
  redesign: the site is still static files in `site/`.
- **Round 2 (2026-10-03), built and pushed, NOT deployed:** the branch head carries the studio's second
  round: the surveyed hall plan, doors as one family, the Bayzyl elevation, the seek line and thread ticks
  in the register, responsive images (`srcset` variants next to every source), `?v=<hash>` cache-busting,
  `verify.mjs` at the end of every build, `tools/tests/site-check.mjs`, corrected alt text, search
  aliases, case notes under the three flagships (proposed copy, see ASK-SAGE.md), a share image that
  opens on the name, `favicon.ico`, and privacy fixes in the tree (WoW captures gone, PrismCode plates
  masked). `showcase/prismet-site/ROUND2-REVIEW.md` is the review for Sage; `ASK-SAGE.md` is the list of
  decisions only Sage can make.
- **v21 candidate (server-side privacy and rate-limit fix), waiting on Sage's yes:** the live image still
  serves the old `public/shots/` captures (one Helm screen lists host names) and `/steam` opens with a
  SteamID64 in its search box; the static rate limit (75/min/IP) is lower than one real visit (about 70
  requests). `showcase/tools/deploy/server-v21.patch` + `scrub-public.sh` + a `whiteouts.txt` line
  (`public/shots`) fix all three. See "Deploying from a cloud session".
- **Round 3 (2026-10-03):** Sage saw round 2 and asked for "less claudy font and something more Sage". The serif
  (Newsreader) is gone; the type is Sage's own v18 trio (see "The redesign, in short"). Sage answered the deploy
  question with "whatever you think is best"; the creative director chose to deploy both the site and the v21
  server patch, but the cloud harness's production-deploy guard refused the registry push, so the deploy waits on
  Sage running or allowing the two commands in "Deploying from a cloud session".
- **Open:** the decisions in "Waiting on Sage" and in `ASK-SAGE.md`. Nothing else is in flight.

## Rules

1. **Sage's wording goes in verbatim.** Don't polish it unless asked. Credit is "Sage Deutschle".
   The redesign added new keys at the bottom of `content/site.md` (section labels, the directory,
   the colophon); they are proposed wording and are flagged for Sage. Nothing above that comment
   was changed.
2. **Real visuals only.** Screenshots and renders of the actual work. No mockups, generated images,
   collages or device frames. Picking, cropping, resizing and re-encoding to WebP is fine. The Fiverr
   boards (`fiverr/out/`) and the App Store pack (`assets/prismet/store-*`) are composites: not on the site.
3. **Privacy.** Keep host names, IPs, mesh addresses, emails, account names, Steam IDs, clipboard
   text and other people's names off the site and out of this public repo. Removed on 10-02 for
   this reason: the Helm iPhone captures, the Helm Fleet Radar face, the PrismCode launch screen,
   the Linux gig board that embedded the fleet face, and every Steam Rewind capture that showed a
   Steam ID in its search field. Git history still holds them; squashing that is Sage's call.
4. **Never break these routes:**
   - `/api/wordle`: the shipping iOS and macOS apps hardcode it. Its path and JSON shape
     `{"answer","date","sourceName"}` must not change.
   - `/rtc`: WebSocket signaling for the app's multiplayer rooms.
   - `/steam` and `/debt`: linked from the app's Settings. They load `/style.css`, `/steam.js`,
     `/debt.js`, `/icon.svg` and `/manifest.webmanifest` from `public/`: the build must never emit
     files with those names (it doesn't: it writes `site.css`, `site.js`, `index.html`,
     `colophon.html`, `work/`, `assets/`).
5. **Production needs Sage's explicit yes in your session.** That covers `fly deploy`, pushing
   images, and pulling the deployed image to read its code. Don't work around a block; ask Sage.
6. **Before every deploy, collect every source of edits.** The Mac copy (above), the newest commit of
   the site branches, and the preview's database.

## Map

| What | Where |
|---|---|
| Build | `node showcase/prismet-site/build.mjs` → `dist/` (must print `built 13 project pages + 5 withdrawn stubs + index + colophon`); `--preview` writes `dist-preview/` with the Edit page editor, so `editor.js` never lands in `dist/` |
| Wording | `showcase/prismet-site/content/site.md`, `content/work/<slug>.md` |
| Images, links, rooms, layout | `showcase/prismet-site/data/projects.json` (`room`, `tier`, `hidden`, `related`, `cover`, `gallery`, `shotAlts`, `steps`, `tiles`, `rack`, `eras`, `layout`, `featuredOrder`) |
| Styles / scripts | `showcase/prismet-site/src/site.css`, `site.js` (editor: `editor.js`, `editor.css`, preview only) |
| Apply Edit-mode changes | `node showcase/prismet-site/apply-edits.mjs <file>` |
| Site images | `showcase/assets/{ai,bench,helm2,icons,live,minecraft,prismet,web,worlds,fonts}/` |
| Captures not yet on the site | `showcase/shots/<project>/`; its `README.md` lists what only Sage can capture |
| Fiverr | `showcase/fiverr/` (a separate workstream; the Mac copy is newer) |
| Design review | `showcase/prismet-site/REDESIGN-REVIEW.md` (the brief and studio reports were session scratch files) |
| Deploy tools | `showcase/tools/deploy/push-overlay.py`, `showcase/tools/deploy/check-routes.sh`, `showcase/tools/deploy/fly.toml` (the app's service config: deploy from its folder or copy it next to `server.js`) |

## The redesign, in short

The site is **a workshop and the register it keeps**. The home page's hero is a hall plan: six
wings (the six beams, Sage's categories) around a rotunda with the prism in its floor; each wing
is a link that filters the register. Type (since round 3, at Sage's request: "less claudy, more Sage") is
Unbounded for names and headings, Hanken Grotesk for reading and Martian Mono caps for labels and data,
the three faces Sage chose for v18; there is no serif and no italic voice. Night is slate and chalk, day
is cloud and ink, brass for labels, amethyst for what runs live. No textures, no glows except the plan's one lantern.

- **Home:** entrance (plaque, "Hello!", the name, the lede, Source / Contact / Commissions) →
  Principal works (four doors: Bayzyl, Prismet, Helm, PrismCode, each with one signature module) →
  the register (a ledger grouped Principal / Records / Cabinet, with a drafting-table line for the
  three projects that have no media) → one captioned plate (a night server build) → Lenses →
  About + Hire (skills linked to evidence, three doors to LinkedIn, GitHub, Fiverr) → footer.
- **Records** share one skeleton. `room` sets the material and the signature module: `bench`
  (the Bayzyl stepper: nine real commands from the release timelapse), `arcade` (the tile wall),
  `bridge` (the Helm rack at 2×, with Helm's own header grammar), `museum` (the Long Now era dial),
  `notebook` (the PrismCode schematic), `observatory`, `council`, `cabinet`.
- **Hidden** (`"hidden": true`): Wizard King's Decree (scrapped), WoW Sidepanel (its only capture is
  the character-select screen with names), Yggdrasil, Westeros for UEBS 2, Helix Research Desk (no
  media). They keep a one-line `noindex` stub at their old URL and leave prev/next.
- **Motion:** the plan draws once per session; the lantern follows the wing in hand; the stepper
  and the era dial respond to input; cross-document view transitions settle page changes. Reduced
  motion shows every finished state. Without JS every step, era and row is on the page.
- **Every `<img>` carries width and height.** The build reads WebP headers itself.

## Edit loop

1. Sage edits words and layout in the preview's **Edit page** mode. Edits land in the artifact's
   database: collection `edits` (`{key, text}`) and document `layout/main`.
2. Read them with ArtifactData. Compare timestamps against recent commits: a draft saved before
   a later rewrite of the same key is superseded.
3. Apply them with `apply-edits.mjs`, then rebuild, commit and push.
4. Deploy, following the next section.
5. Republish the preview from the same commit with `node build.mjs --preview` (publish
   `dist-preview/_preview.html` as the page with the rest of `dist-preview/` as files, declaring
   the `db` and `user` capabilities). Only then delete the applied docs.

## The live server

- **Fly app** `prismet-site-restless-horizon-217`: region iad, two machines (shared-cpu-1x, 256 MB),
  auto-stop and auto-start. Certificates for `prismet.xyz` and `www.prismet.xyz`.
- **Image:** Node 22 Alpine running `node server.js` in `/app`. The files are `server.js`,
  `signaling.js`, `wordle-daily.js`, `project-catalog.js`, `project-feed.js`,
  `project-renderer.js`, `data/`, `public/` (the tools and the old site's files) and
  `site/` (this repo's build). Its one dependency is `ws`.
- **Static files** are looked up in `site/` first, then `public/`. The server refuses to start
  without `site/index.html`.
- **Routes:** `/healthz`; `/api/wordle` (proxies the daily word Sage's broker publishes);
  `/api/steam` (needs the `STEAM_WEB_API_KEY` secret); `/api/projects` (the old gist catalog,
  unused); `/rtc` (WebSocket); `/steam`, `/debt` (also as `.html`), `/shots/*`, and everything
  else static.
- **Headers:** every response sets the CSP (`script-src 'self'`, so no inline scripts; the build
  has none), HSTS, `X-Frame-Options: DENY` and the rest. `cache-control: public, max-age=300`.
- **Rate limit:** static files count too, at 75 requests per minute per IP. The home page's first
  view stays well under it; keep it that way.
- **Env (in Fly):** `GITHUB_USER`, `PORT`, `PROJECTS_CACHE_MS`, `PROJECTS_URL`. One secret:
  `STEAM_WEB_API_KEY`.
- **History:** v13 (2026-07-26) is Sage's original, image tag
  `deployment-01KYE5MM868CFCAWKRR3N9CKB1`. Releases v14–v18 equal v13 plus one image layer
  holding a changed `server.js` and `site/`.
- **Where the server code lives:** in the deployed image; in `prismet-site.bundle`, a git bundle sent
  to Sage; possibly an older copy on Sage's Mac (deploying that would wipe out the site). Sage chose a
  private repo `sagedeutschle/prismet-site` for it; once it exists, push the bundle's `main` there.
- **Getting the code from the image** (needs Sage's OK; see rule 5): `fly image show -a
  prismet-site-restless-horizon-217` gives the tag; fetch the manifest from
  `https://registry.fly.io/v2/prismet-site-restless-horizon-217/manifests/<tag>` with basic auth
  `x:$FLY_API_TOKEN` and `Accept: application/vnd.oci.image.manifest.v1+json`; the last layer holds
  `app/server.js` and `app/site/`.

## Deploying from a cloud session

- **Setup:** install flyctl with `curl -sSL https://fly.io/install.sh | sh -s -- --non-interactive`,
  then add `/root/.fly/bin` to `PATH`. `FLY_API_TOKEN` comes from the environment's variables.
- **No build service:** Fly's builder is blocked by the network policy, and there's no Docker
  daemon. So push the base image plus a new layer:

```sh
# <dir> holds server.js and site/ (copy showcase/prismet-site/dist to <dir>/site; never dist-preview).
# Optional: <dir>/public/ (files added to /app/public) and <dir>/whiteouts.txt (paths deleted from the
# image, one per line, e.g. `public/shots`). DRY_RUN=1 builds and lists the layer without pushing.
FLY_API_TOKEN=... showcase/tools/deploy/push-overlay.py <dir> redesign-YYYYMMDD-N
fly deploy --image registry.fly.io/prismet-site-restless-horizon-217:redesign-YYYYMMDD-N \
  -a prismet-site-restless-horizon-217     # run where showcase/tools/deploy/fly.toml is (see the incident)
```

- **The v21 candidate, step by step** (needs Sage's explicit yes; it changes `/steam` slightly: the box
  opens empty and Reset clears it instead of restoring the id):
  1. Extract the live `server.js` and `public/steam.html` + `public/steam.js` from the image (below).
  2. `patch server.js < showcase/tools/deploy/server-v21.patch` (retires `/shots/*` with 404, static
     limit 600/min with a plain-text 429 and `retry-after`, `vary: accept-encoding`, and
     `max-age=31536000, immutable` for site files requested with `?v=<hash>`).
  3. Copy `steam.html` and `steam.js` to `<dir>/public/` and run `showcase/tools/deploy/scrub-public.sh <dir>/public`.
  4. `echo public/shots > <dir>/whiteouts.txt`; `site/` = a fresh `dist/`.
  5. Run the patched server locally (`npm ci`, `PORT=18081 node server.js`) and `check-routes.sh`:
     `/shots/*` must be 404, `/steam` must contain no 17-digit number, `/favicon.ico` 200, `/rtc` 101.
  6. Push the overlay, deploy with the real `fly.toml`, re-run `check-routes.sh` against prismet.xyz.

- **Tags so far:** `redesign-20261001-1` to `-5` became releases v14 to v18; `redesign-20261002-1`
  became v19 (broken config, see above) and v20 (the live redesign).
- **The server code** is also recoverable from the image layers without Docker: the small zstd layers
  hold `app/` (package.json, node_modules/ws, server.js, signaling.js, wordle-daily.js, project-*.js,
  data/, public/); `pip install zstandard` and extract them with Python's tarfile. The v18+ `server.js`
  is in the last gzip layer.
- **Before deploying:** build with `PRISMET_PRIVATE_FILE=<your list> node showcase/prismet-site/build.mjs`
  (the build ends with `verify.mjs`: no editor, inline script, reserved name, broken link, TODO or private
  string; the private word list holds host names, so it lives outside the repo, e.g.
  `~/.config/prismet/private-words.txt`), run `node showcase/tools/tests/site-check.mjs` (44 browser
  checks), run the server locally (`npm ci`, `PORT=18081 node ./server.js`), run
  `check-routes.sh http://127.0.0.1:18081` and compare against the last run; load the pages at 1440 and 390.
- **After deploying:** `check-routes.sh https://prismet.xyz`; `curl https://prismet.xyz/api/wordle`
  must return today's word; a WebSocket upgrade on `/rtc` must get `101`; load the pages.
- **Rollback:** `fly deploy --image registry.fly.io/prismet-site-restless-horizon-217:<earlier tag>`
  from the folder holding `showcase/tools/deploy/fly.toml`. v18 is `redesign-20261001-5`; the original
  is `deployment-01KYE5MM868CFCAWKRR3N9CKB1`.

## Waiting on Sage

1. **The Mac reconciliation** (above) still has to happen; the redesign went live from the branch.
   Also: should `PROJECTS_URL` (the old gist catalog) be restored on Fly? Its value is only on Sage's side.
2. **Claims in Sage's wording that the studio could not verify** (left untouched, flagged):
   "Prismet" vs "Prismet Arcade" and "co-developed" (the Fiverr kit uses both); "three live-data
   lenses" (now two: the Oracle lens was the scrapped Decree); "Watch"; "(see Agent Ops)" (that
   page was removed on 10-01); "237 Python" tests (the Decree's suite had the same count); Helm's
   "28 QML faces" (24 were rendered) and the "VSCODIUM" and "Install" facts; the Bayzyl help capture
   reads "1/13" while the guide is 16 pages; "Godot" for The Long Now; whether Catan ships.
3. **Who built the server builds** in `fiverr/screenshots/02-minecraft/`. The site captions the
   plate "A build on the Paper server Bayzyl runs on" (the repo's own caption). If Sage built them,
   say so and they can become a record of their own.
4. **Volhaven / Velthir.** Nothing exists in the repo, and the 10-01 Fiverr spec excludes it from
   public surfaces. The redesign uses nothing from it. Sage decides whether that changes.
5. **Old screenshots still reachable.** The old site's `/shots/helm-*-full.webp` are still served
   from `public/shots/` on the server. Removing them is a server change plus a deploy.
6. **Private repo** for the server code (see above).
7. **Captures that would strengthen the site** (see `shots/README.md`): Bayzyl's detail and terrain
   brushes in use, a real full-desktop Helm screenshot, a redacted Helm Mesh capture, the Mac app,
   The Long Now's court and campaign screens, a re-shot of the cloud-sea spire with the HUD off (F1).

## Known issues and backlog

- **Budgets (round 2, 2026-10-03, local dist):** home first view 19 requests at 1440 (14 at 390); full
  scroll about 35; Bayzyl 505 KB, Prismet 443 KB, Helm 429 KB on a full scroll at 1440; no image drawn
  above 2x its CSS width; CLS under 0.01; axe 0 violations. Fonts are still 213 KB on every first visit.
  Live v20 (QA on Fly): all four pages within Google's "good" LCP/CLS; one realistic visit sends about 70
  requests against the server's 75/min limit, which the v21 server patch raises to 600 for static files.
- **Image variants:** `img()` emits `srcset` from files named `<name>-{128,192,360,720,1080,1440}.webp`
  beside the source. After adding or replacing an image: build, `node showcase/tools/image-variants.mjs`,
  build again, commit the new files. Replacing a source means deleting its old variants first (they are
  not regenerated if present).

- **The Long Now** is a featured room with three 1280×720 captures. The Mac holds more.
- **Steam Rewind and Debt Clock** plates are crops of demo data; the Debt Clock captures show the
  labelled Treasury fallback.
- **Edit mode's "Wide" toggle** has no effect any more (there is no card grid); it still writes
  `wide` into `projects.json`, which the build ignores.
- **The Fiverr `render.mjs`** now draws the Volumes face where the Fleet face was; re-render
  `gig-linux-desktop.png` before using it.

## Gotchas

- **Background subagents die when the session's worker restarts.** Give each one an export in the
  scratchpad and have it deliver files for you to merge.
- **`pkill -f "node server.js"` kills your own shell** when that text appears in the same command.
- **Chromium/Playwright** is at `/opt/pw-browsers/chromium-1194/chrome-linux/chrome`; pass it as
  `executablePath`. For https pages through the agent proxy, launch with `proxy: { server: process.env.HTTPS_PROXY }`
  and the proxy CA's key pinned: `args: ['--ignore-certificate-errors-spki-list=<sha256 of the CA key>']`
  (compute it from `/root/.ccr/agent-proxy-ca.crt` with openssl). `ignoreHTTPSErrors: true` is not enough:
  about one request in six then fails at the proxy hop with `ERR_TOO_MANY_RETRIES`. `certutil` isn't installed.
- **Subagents cannot write `report.md` files in the scratchpad** (the harness refuses); have them return the
  report as their final message and save it yourself (the transcript JSONL holds it under `SubagentHandback`).
- **ImageMagick 6 ignores `-quality` for WebP.** Use `-define webp:method=6` and, to cap a file,
  `-define webp:target-size=N`.
- **Google Fonts is reachable through the proxy**; `pip install fonttools brotli` works. The fonts
  were re-subset that way (see `README.md`).
- **The production build must not contain `editor.js`.** It can't any more (the preview has its own
  folder), but `push-overlay.py` doesn't check, so look.

## First steps

1. Fetch the site branches; confirm the redesign branch builds and prints the line above.
2. Ask Sage for the Mac copy of `business/showcase` and reconcile wording and the project list.
3. Run `fly releases -a prismet-site-restless-horizon-217` (expect v18) and `check-routes.sh`
   against prismet.xyz.
4. Check the preview's database for new edits.
5. Ask Sage about the decisions above.
