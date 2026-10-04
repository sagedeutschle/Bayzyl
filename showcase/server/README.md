# showcase/server — the prismet.xyz server

The Node server that runs on Fly (`prismet-site-restless-horizon-217`): static files from `site/` (the built showcase)
then `public/` (the Steam Rewind and Debt Clock tools), `/api/wordle`, `/api/steam`, `/api/projects`, `/rtc` (WebRTC
signalling over WebSocket), `/healthz`. Extracted from the deployed image on 2026-10-03 and patched since:

- v21: `/shots/*` answers 404 (the old capture folder is also whited out of the image); the Steam page no longer
  prefills a Steam id (`public/steam.html`, `public/steam.js`, scrubbed with `../tools/deploy/scrub-public.sh`);
  static rate limit 600/min with a plain-text 429 and `retry-after`; `vary: accept-encoding`; `?v=<hash>` site files
  cached for a year.
- v22: `/edit`, `/privacy` and `/support` map to `<name>.html` in `site/`; `api.github.com` allowed in `connect-src`
  for the edit page.

Run it locally: `npm ci --omit=dev`, copy `../prismet-site/dist` to `site/`, `PORT=18081 node server.js`, then
`../tools/deploy/gate.sh http://127.0.0.1:18081 local`. Env: `PORT`, `GITHUB_USER`, `STEAM_API_KEY` (Steam search),
`WORDLE_DAILY_URL`, `PROJECTS_URL` (optional gist; the bundled `data/projects.json` is the fallback). No secrets live
here; `node_modules/` and `site/` are ignored.

The 2026-10-04 product hub adds `/arcade`, 20 playable destinations, `/tools`, and `/api/debt` (43 dated,
source-attributed metrics with independent stale/missing states). `debt-data.js` fetches upstream observations
with bounded requests and caching; `public/debt-metrics.js` defines the catalog. Game rules and browser saves
live under the built `site/arcade/` tree.

Optional cloud progress uses `arcade-api.js`. Without both `ARCADE_SUPABASE_URL` (HTTPS origin) and
`ARCADE_SUPABASE_PUBLISHABLE_KEY`, `/api/arcade/config` reports sync unavailable and local play remains usable.
Before enabling it, apply `migrations/20261004_arcade_progress.sql` to the intended provider project through
the normal database change process, configure those two environment values using the existing secret store,
and verify confirmed-email authentication plus owner-only reads and revision conflicts. The website workflow
does not apply database migrations or provision identities. Only 2048, Minesweeper and Lights Out currently
support portable/native cloud saves; other games have a distinct browser-only format. New native deep-link
handling also requires a native app release; serving the association file alone does not update installed apps.

Deploys do not rebuild this image from scratch: `../tools/deploy/push-overlay.py` adds all top-level server
JavaScript modules, `site/`, the Steam/Debt public assets and the privacy whiteout on top of the v13 base image.
If dependencies or other server assets change, extend `assemble-overlay.sh` and the overlay archive together.
