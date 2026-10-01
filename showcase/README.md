# Showcase: Fiverr kit + prismet.xyz redesign

Everything here is built from Sage's real work across six repos (Bayzyl, Prismet,
qr-scanner, PrismCode, THE HELM, Quark). Nothing is a mockup pretending to be a screenshot.

| Path | What it is |
|---|---|
| [`fiverr/FIVERR-KIT.md`](fiverr/FIVERR-KIT.md) | Profile copy, 6 gigs (titles, packages, descriptions, tags, FAQ), portfolio entries, upload order |
| [`fiverr/out/`](fiverr/out/) | 6 gig covers, 10 portfolio boards, 1 LinkedIn banner (2560×1538 PNG) |
| [`fiverr/gigs.json`](fiverr/gigs.json) | Source for the kit; `node showcase/fiverr/kit.mjs` validates it against Fiverr's limits |
| [`fiverr/render.mjs`](fiverr/render.mjs) | Renders every image from HTML templates with Playwright |
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
