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

Deploys do not rebuild this image from scratch: `../tools/deploy/push-overlay.py` adds one layer (this `server.js`,
`site/`, the two scrubbed Steam files, the whiteout) on top of the v13 base image. If a dependency or any other file
here changes, the layer must carry it too (extend `assemble-overlay.sh`).
