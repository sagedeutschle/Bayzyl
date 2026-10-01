# Showcase: Fiverr kit + prismet.xyz redesign

Everything here is built from Sage's real work across six repos (Bayzyl, Prismet,
qr-scanner, PrismCode, THE HELM, Quark). Nothing is a mockup pretending to be a screenshot.

| Path | What it is |
|---|---|
| [`fiverr/FIVERR-KIT.md`](fiverr/FIVERR-KIT.md) | Profile copy, 6 gigs (titles, packages, descriptions, tags, FAQ), portfolio entries, upload order |
| [`fiverr/out/`](fiverr/out/) | 6 gig covers, 10 portfolio boards, 1 LinkedIn banner (2560×1538 PNG) |
| [`fiverr/gigs.json`](fiverr/gigs.json) | Source for the kit; `node showcase/fiverr/kit.mjs` validates it against Fiverr's limits |
| [`fiverr/render.mjs`](fiverr/render.mjs) | Renders every image from HTML templates with Playwright |
| [`fiverr/screenshots/`](fiverr/screenshots/) | Real screenshots grouped by gig; `00-fiverr-ready/` is sized for upload |
| [`prismet-site/REDESIGN-PLAN.md`](prismet-site/REDESIGN-PLAN.md) | The facelift plan: structure, design system, how to deploy on Fly safely |
| [`prismet-site/data/projects.json`](prismet-site/data/projects.json) | One project catalog for the site and the Fiverr portfolio |
| [`prismet-site/build.mjs`](prismet-site/build.mjs) | Builds the static site into `prismet-site/dist/` (`--preview` adds the Edit page mode) |
| [`prismet-site/content/`](prismet-site/content/) | Every word on the site, as plain text |
| [`prismet-site/apply-edits.mjs`](prismet-site/apply-edits.mjs) | Writes Edit-mode changes (words + layout) back into `content/` and `data/projects.json` |
| [`assets/`](assets/) | Compressed real visuals + self-hosted fonts (SIL OFL) |
| [`tools/`](tools/) | Asset collection, offscreen QML widget renderer, web capture scripts |

## Rebuild everything

```bash
node showcase/fiverr/render.mjs        # gig covers + portfolio boards → fiverr/out/
node showcase/fiverr/kit.mjs           # validate + regenerate FIVERR-KIT.md and fiverr/kit/
node showcase/prismet-site/build.mjs   # site → prismet-site/dist/
```

Needs Node 20+, Playwright with Chromium, and ImageMagick (`convert`) for WebP output.

THE HELM screenshots come from `tools/render-helm-faces.py`, which renders each widget's
`Face.qml` offscreen with PySide6 using the widget's own sample data
(`tools/qmlstub/` stands in for KDE's `plasma5support` module):

```bash
QML_IMPORT_PATH=showcase/tools/qmlstub python render-helm-faces.py <out-dir> ~/helm-dotfiles/helm/plasmoids/*/contents/ui/Face.qml
```

## Status (2026-10-01)

**Done**
- `fiverr/screenshots/`: 135 real screenshots grouped by gig, including 27 upload-ready 1280×769 versions
  (`node showcase/fiverr/frame-screenshots.mjs`).
- prismet.xyz redesign with all 20 live projects plus 5 new ones (25), real screenshots,
  Unbounded + Martian Mono type, light/dark, phone layout. Every word lives in `prismet-site/content/`.
- Preview with **Edit page** mode (words + layout), saving to the preview's store:
  https://claude.ai/artifact/Nig6eZ26fLTtBzgiTzQfQ2. Sage's first 7 edits are applied.
- Fiverr kit: profile, 6 gigs, 6 portfolio projects, 6 covers + 10 boards + LinkedIn banner.
  Copy-paste page: https://claude.ai/artifact/7V4XHRcdSZUM6mFK9wfufc

**Next**
- Deploy to Fly (`prismet-site-restless-horizon-217`): needs `FLY_API_TOKEN` in the environment
  (new session). Inspect the running image first so `/steam`, `/debt`, `/api/wordle` keep working,
  and ask Sage before `fly deploy`. See `prismet-site/REDESIGN-PLAN.md`.
- Westeros for UEBS 2: needs screenshots + Workshop link (`prismet-site/content/work/westeros-uebs2.md`).
- `sageskillz.md` / `CLAUDE-OPERATIONS-HANDOFF.md` never reached this session; reconcile
  `fiverr/gigs.json` against them when available.
- `/api/wordle` was returning 502 ("still refreshing"); another agent is fixing the broker on archbox.

**Notes for the next session**
- Chromium (Playwright) needs the proxy CA in its trust store before loading any https page:
  `apt-get install -y libnss3-tools && certutil -d sql:$HOME/.pki/nssdb -A -t "C,," -n ccr-agent-proxy -i /root/.ccr/agent-proxy-ca.crt`
- Browser checks for the editor: `node showcase/tools/tests/test-layout.mjs` (after a `--preview` build).
- Fiverr's site shows automated clients a captcha; gig edits happen in Sage's browser.
