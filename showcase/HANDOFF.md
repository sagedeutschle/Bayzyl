# prismet.xyz handoff — 2026-10-02

For the agent taking over Sage's website. Read all of it before you touch production. This
repo is public, so this file holds no secrets or internal host names, and anything you add
must not either.

## Where things stand

- **Live:** prismet.xyz runs Fly release **v18** (2026-10-01 09:11 UTC), built from commit
  **`fcba36c`**. It's the redesigned site with 18 projects. It's healthy: `/api/wordle`,
  `/steam`, `/debt` and `/rtc` all work.
- **Source:** `showcase/prismet-site/` in this repo. Two branches carry it, and both are at
  `fcba36c`:
  - `claude/kind-sagan-pbm82l` is the deploy branch.
  - `claude/fiverr-prismet-redesign-jgyb6i` is the content branch.

  `main` doesn't have the site.
- **Preview with Edit mode:** artifact `https://claude.ai/artifact/Nig6eZ26fLTtBzgiTzQfQ2`
  (version 7, built from `fcba36c`). Its database is empty, so no edits are waiting.
- **Server code:** not in any repo yet. See "The live server" below.
- **Open:** four decisions are waiting on Sage. Nothing else is in flight.

## Rules

1. **Sage's wording goes in verbatim.** Don't polish it unless asked. Credit is "Sage Deutschle".
2. **Real visuals only.** Use screenshots and renders of the actual work. No mockups, generated
   images, collages or device frames. Picking, resizing and re-encoding to WebP is fine.
3. **Privacy.** Keep host names, IPs, mesh addresses, emails, account names, clipboard text and
   other people's names off the site and out of this public repo. The Helm iPhone remote
   captures fail this test (they show host and server names), and so were removed.
4. **Never break these routes:**
   - `/api/wordle`: the shipping iOS and macOS apps hardcode it. Its path and JSON shape
     `{"answer","date","sourceName"}` must not change.
   - `/rtc`: WebSocket signaling for the app's multiplayer rooms.
   - `/steam` and `/debt`: linked from the app's Settings.
5. **Production needs Sage's explicit yes in your session.** That covers `fly deploy`, pushing
   images, and pulling the deployed image to read its code. The auto-mode classifier blocks
   these otherwise. Don't work around a block; ask Sage.
6. **Before every deploy, collect every source of edits.** Build from the newest commit of
   both branches, and check the preview's database for edits Sage saved after the last
   commit. v14 shipped without five fresh edits for lack of this check.

## Map

| What | Where |
|---|---|
| Build | `node showcase/prismet-site/build.mjs` → `dist/` (must print `built 18 project pages + index`); `--preview` adds the Edit page editor |
| Wording | `showcase/prismet-site/content/site.md`, `content/work/<slug>.md` |
| Images, links, layout, order | `showcase/prismet-site/data/projects.json` (`cover`, `wideCover`, `gallery`, `shotAlts`, `layout`, `featuredOrder`, `wide`, `hidden`) |
| Styles / scripts | `showcase/prismet-site/src/site.css`, `site.js` (editor: `editor.js`, `editor.css`, preview only) |
| Apply Edit-mode changes | `node showcase/prismet-site/apply-edits.mjs <file>`, which takes a JSON dump of the preview's db or the "Copy my changes" text |
| Compressed site images | `showcase/assets/{live,prismet,helm,web,icons,ai,minecraft,fonts}/` |
| New captures (wired in) | `showcase/shots/<project>/`; its `README.md` lists what only Sage can capture |
| Screenshot library and Fiverr boards | `showcase/fiverr/screenshots/`, `showcase/fiverr/out/` |
| Plan and design notes | `showcase/prismet-site/REDESIGN-PLAN.md`, `showcase/README.md` |
| Deploy tools | `showcase/tools/deploy/push-overlay.py`, `showcase/tools/deploy/check-routes.sh` |

## Edit loop

1. Sage edits words and layout in the preview's **Edit page** mode. Edits land in the artifact's
   database: collection `edits` (`{key, text}`) and document `layout/main`.
2. Read them with ArtifactData. Compare timestamps against recent commits: a draft saved before
   a later rewrite of the same key is superseded.
3. Apply them with `apply-edits.mjs`, then rebuild, commit and push.
4. Deploy, following the next section.
5. Republish the preview from the same commit with `node build.mjs --preview`. Only then delete
   the applied docs, using pinned versions. If you delete them first, the preview falls back to
   stale text.

## The live server

- **Fly app** `prismet-site-restless-horizon-217`:
  - Region iad, two machines (shared-cpu-1x, 256 MB), auto-stop and auto-start.
  - Certificates for `prismet.xyz` and `www.prismet.xyz`.
- **Image:** Node 22 Alpine running `node server.js` in `/app`. The files are `server.js`,
  `signaling.js`, `wordle-daily.js`, `project-catalog.js`, `project-feed.js`,
  `project-renderer.js`, `data/`, `public/` (the tools and the old site's files) and
  `site/` (this repo's build). Its one dependency is `ws`.
- **Static files** are looked up in `site/` first, then `public/`. The server refuses to start
  without `site/index.html`.
- **Routes:**
  - `/healthz`
  - `/api/wordle`: proxies the daily word that Sage's broker publishes to Supabase.
  - `/api/steam`: needs the `STEAM_WEB_API_KEY` secret.
  - `/api/projects`: the old gist catalog, unused by the new site.
  - `/rtc`: WebSocket.
  - `/steam`, `/debt` (also as `.html`), `/shots/*`, and everything else static.
- **Headers:** every response sets the CSP (`script-src 'self'`, so no inline scripts),
  HSTS, `X-Frame-Options: DENY` and the rest.
- **Rate limit:** static files count too, at 75 requests per minute per IP. Normal browsing stays
  under it.
- **Env (in Fly):** `GITHUB_USER`, `PORT`, `PROJECTS_CACHE_MS`, `PROJECTS_URL`. One secret:
  `STEAM_WEB_API_KEY`.
- **History:** v13 (2026-07-26) is Sage's original, image tag
  `deployment-01KYE5MM868CFCAWKRR3N9CKB1`. Releases v14–v18 equal v13 plus one image layer
  holding a changed `server.js` and `site/`. The `server.js` changes are:
  - static files come from `site/` first, then `public/`;
  - `/` is no longer rendered on the server;
  - a `font/woff2` content type.
- **Where the code lives:**
  1. In the deployed image (see below).
  2. In `prismet-site.bundle`, a git bundle sent to Sage. It holds three commits: v13 as
     recovered, the change, and README + tools + the deployed `site/`.
  3. Sage's Mac may hold an older copy. Deploying that would wipe out the redesign, so compare
     first.

  Sage chose a **private repo `sagedeutschle/prismet-site`**. The GitHub app couldn't create it.
  Once Sage creates it empty and gives the Claude app access, push the bundle's `main` there.
- **Getting the code from the image** (needs Sage's OK; see rule 5):
  - `fly image show -a prismet-site-restless-horizon-217` gives the current tag.
  - Fetch `https://registry.fly.io/v2/prismet-site-restless-horizon-217/manifests/<tag>` with
    basic auth `x:$FLY_API_TOKEN` and the header
    `Accept: application/vnd.oci.image.manifest.v1+json`.
  - The last layer (tar+gzip) holds `app/server.js` and `app/site/`. Earlier zstd layers hold
    the other app files; layers over 20 MB are the Node base and can be skipped.

## Deploying from a cloud session

- **Setup:** install flyctl with `curl -sSL https://fly.io/install.sh | sh -s -- --non-interactive`,
  then add `/root/.fly/bin` to `PATH`. `FLY_API_TOKEN` comes from the environment's variables.
- **No build service:** Fly's builder (`depot.dev`) is blocked by the network policy, and there's
  no Docker daemon. So skip the build: push the base image plus a new layer.

```sh
# <dir> holds server.js and site/ (copy showcase/prismet-site/dist to <dir>/site; no editor.js)
FLY_API_TOKEN=... showcase/tools/deploy/push-overlay.py <dir> redesign-YYYYMMDD-N
fly deploy --image registry.fly.io/prismet-site-restless-horizon-217:redesign-YYYYMMDD-N \
  -a prismet-site-restless-horizon-217     # run where a fly.toml for the app exists
```

- **Tags so far:** `redesign-20261001-1` to `-5` became releases v14 to v18.
- **Before deploying:**
  - Run the server locally: `npm ci`, then `PORT=18081 node ./server.js`.
  - Run `check-routes.sh http://127.0.0.1:18081` and compare against the last run.
  - To test `/api/wordle` locally, point `WORDLE_DAILY_URL` at a mock JSON file.
- **After deploying:**
  - `check-routes.sh https://prismet.xyz`.
  - `curl https://prismet.xyz/api/wordle` must return today's word.
  - A WebSocket upgrade on `/rtc` must get `101`.
  - Load the pages in a browser.
- **Rollback:** `fly deploy --image registry.fly.io/prismet-site-restless-horizon-217:<earlier tag>`.
  v17 is `redesign-20261001-4`; the original is `deployment-01KYE5MM868CFCAWKRR3N9CKB1`.

## Waiting on Sage

1. **Old screenshots still reachable.** The old site's `/shots/helm-*-full.webp` are still served
   from `public/shots/`. Nothing links to them, but they show host names. Removing them is a
   server change plus a deploy.
2. **WoW Sidepanel.** Its only screenshot shows character names and a realm. Keep or drop it?
3. **Wizard King's Decree.** The Oracle project was reported scrapped, and the Fiverr handoff
   (below) dropped it and its board. The site still lists it, with the `portfolio-oracle` board
   in its gallery. The likely fix is `"hidden": true`, but confirm with Sage.
4. **Private repo** for the server code (see above).

## Known issues and backlog

- **Wide Helm card:** it crops `worldclock` ("HELM" reads "ELM"). Below 980 px the card isn't
  wide, but `build.mjs` still picks `wideCover`.
- **Facts grid:** an odd number of facts leaves a lighter empty cell (`.facts` background shows).
- **Placeholder cards** print the project title twice.
- **Still placeholders:** `yggdrasil`, `westeros-uebs2` and `helix-research-desk`. They need
  Sage's own screenshots (see `showcase/shots/README.md`).
- **Debt Clock captures** were taken while the Treasury fetch was failing, so they show the
  labelled estimate. Its "Increase Today" figure also wraps mid-number.
- **Steam Rewind:** some lenses showed negative values in captures. That looks like a bug in the
  tool, not the site.
- **Helm gallery faces** are 1× renders; 2× PNGs are in
  `showcase/fiverr/screenshots/03-linux-desktop-helm/widgets/`.
- **Dead server files:** `project-renderer.js`, `public/index.template*.html` and the old
  `public/index.html` / `public/site.js` (now shadowed by `site/`).

## Other sessions and workstreams

- **"Prismet redesign and Fiverr work"** (`session_011TmKkmVpF1HJj59TZeM17a`) changes wording,
  the project list and `showcase/shots/` at Sage's request, on the content branch. The agreed
  split:
  - that session owns wording, content and the project list;
  - the deploy session (now you) owns the image fields in `projects.json`, the server and deploys.

  It reports pushes by `send_message`; merge them before you deploy.
- **Earlier site sessions:** `session_011s138UmbRx2eWDLFpaZwtc` (site code) and
  `session_013ko1NYtHoUM7TtxB6LSFnA` (the deploy session that wrote this).
- **Fiverr:** branch `claude/great-gates-hovh8a` holds
  `showcase/fiverr/HANDOFF-2026-10-02.md`, a separate workstream. It already contains
  `fcba36c` and only adds Fiverr files plus two lines in `showcase/README.md`, so it merges
  cleanly.
- **The Wordle broker** runs on Sage's own machine. When `/api/wordle` returns 502
  ("Today's daily word is still refreshing."), the broker hasn't published today's word yet.
  Nothing on Fly is wrong.

## Gotchas

- **Background subagents die when the session's worker restarts.** Give each one a
  `git archive <commit> showcase docs | tar -x` export in the scratchpad, and have it deliver
  files (new WebPs plus a `fields.json`) for you to merge. With `isolation: "worktree"`, some
  started on `main` instead of your branch.
- **`pkill -f "node server.js"` kills your own shell** when that text appears in the same
  command. Use `pgrep -f '[n]ode server[.]js'` in a separate command.
- **Chromium/Playwright needs the proxy CA** before loading https pages:
  `certutil -d sql:$HOME/.pki/nssdb -A -t "C,," -n ccr-agent-proxy -i /root/.ccr/agent-proxy-ca.crt`.
  Then launch with `proxy: { server: process.env.HTTPS_PROXY }`.
- **Proxy errors on prismet.xyz:** if requests through the proxy get 502 or "injection failed",
  an API credential is bound to prismet.xyz in the environment settings. Sage removed one such
  AWS-typed credential on 2026-10-01.
- **The production build must not contain `editor.js`.** `push-overlay.py` doesn't check this,
  so do.

## First steps

1. Fetch both site branches and confirm they're still at `fcba36c`; merge anything newer.
2. Run `fly releases -a prismet-site-restless-horizon-217` (expect v18) and `check-routes.sh`
   against prismet.xyz.
3. Check the preview's database for new edits.
4. Ask Sage about the four open decisions.
