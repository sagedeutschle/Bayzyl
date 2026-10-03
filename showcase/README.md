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
| [`assets/`](assets/) | Compressed real visuals + self-hosted fonts (SIL OFL). `helm2/` holds the widget faces at 2×, `bench/` the nine timelapse steps, `worlds/` the Long Now eras |
| [`tools/`](tools/) | Asset collection, offscreen QML widget renderer, web capture scripts |

## Rebuild everything

```bash
node showcase/fiverr/render.mjs        # gig covers + portfolio boards → fiverr/out/
node showcase/fiverr/kit.mjs           # validate + regenerate FIVERR-KIT.md and fiverr/kit/
node showcase/prismet-site/build.mjs   # site → prismet-site/dist/
```

Needs Node 20+, Playwright with Chromium, and ImageMagick (`convert`) for WebP output. Note that ImageMagick 6
ignores `-quality` for WebP; use `-define webp:method=6` (and `webp:target-size=N` to cap a file).

### Fonts

`assets/fonts/` holds Newsreader (roman and italic, weight 200–800, optical size 6–72) and Martian Mono (weight
100–800, width 75–112.5), subset to Latin plus arrows and math symbols. To regenerate from the variable TTFs in
[google/fonts](https://github.com/google/fonts) (`ofl/newsreader`, `ofl/martianmono`):

```bash
pip install fonttools brotli
U='U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2190-2199,U+2212,U+2215,U+2248,U+2260,U+2264,U+2265,U+FEFF,U+FFFD'
pyftsubset 'Newsreader[opsz,wght].ttf' --unicodes="$U" --layout-features='kern,liga,tnum,calt' --flavor=woff2 --output-file=Newsreader.woff2
fonttools varLib.instancer 'Newsreader-Italic[opsz,wght].ttf' wght=300:600 opsz=18 -o italic.ttf   # the italic is pinned at optical size 18
pyftsubset italic.ttf --unicodes="$U" --layout-features='kern,liga,tnum,calt' --flavor=woff2 --output-file=Newsreader-Italic.woff2
pyftsubset 'MartianMono[wdth,wght].ttf' --unicodes="$U" --layout-features='kern,tnum,calt' --flavor=woff2 --output-file=MartianMono.woff2
```

THE HELM screenshots come from `tools/render-helm-faces.py`, which renders each widget's
`Face.qml` offscreen with PySide6 using the widget's own sample data
(`tools/qmlstub/` stands in for KDE's `plasma5support` module):

```bash
QML_IMPORT_PATH=showcase/tools/qmlstub python render-helm-faces.py <out-dir> ~/helm-dotfiles/helm/plasmoids/*/contents/ui/Face.qml
```

## Status (2026-10-02)

**The workshop redesign** (branch `claude/funny-wozniak-4j636o`): the site is now a workshop with a hall plan for
navigation, a ledger register, and one signature module per flagship. See `showcase/HANDOFF.md` for the current
state, and `prismet-site/build.mjs` for how pages are made. `node build.mjs --preview` writes to `dist-preview/`.

## Status (2026-10-01)

**Done**
- `fiverr/screenshots/`: 135 real screenshots grouped by gig, including 27 upload-ready 1280×769 versions
  (`node showcase/fiverr/frame-screenshots.mjs`).
- prismet.xyz redesign: 18 projects (7 removed at Sage's request), real screenshots,
  Unbounded + Martian Mono type, light/dark, phone layout. Every word lives in `prismet-site/content/`.
- Preview with **Edit page** mode (words + layout); Sage's edits so far are applied.
- `shots/`: better screenshots per project, plus the list of what Sage still needs to capture
  (`shots/README.md`).
- Fiverr kit: profile, 6 gigs, 6 portfolio projects, 6 covers + 10 boards + LinkedIn banner.
  The current, claim-checked copy of `fiverr/` now lives on Sage's Mac; this branch's
  `fiverr/gigs.json` is older.

**Next**
- Deploy to Fly (`prismet-site-restless-horizon-217`): inspect the running image first so `/steam`,
  `/debt`, `/api/wordle` keep working, and ask Sage before `fly deploy`. See `prismet-site/REDESIGN-PLAN.md`.
- Pick covers/galleries from `shots/` and wire them into `prismet-site/data/projects.json`.
- Westeros for UEBS 2: needs screenshots + Workshop link (`prismet-site/content/work/westeros-uebs2.md`).

**Notes for the next session**
- Chromium (Playwright) needs the proxy CA in its trust store before loading any https page:
  `apt-get install -y libnss3-tools && certutil -d sql:$HOME/.pki/nssdb -A -t "C,," -n ccr-agent-proxy -i /root/.ccr/agent-proxy-ca.crt`
- Browser checks: `node showcase/tools/tests/site-check.mjs` after a production build. It serves `dist/` itself with
  the live CSP and checks the home, Bayzyl, Prismet and Helm pages at 1440 and 390 (console errors, overflow, the
  filter and its deep link, the stepper); exit 1 on any failure. The build already runs `prismet-site/verify.mjs`.
- New or changed site images: `node showcase/tools/image-variants.mjs` after a build writes the `-360/-720/-1080/-1440`
  variants `img()` puts in `srcset`.
- Fiverr's site shows automated clients a captcha; gig edits happen in Sage's browser.
