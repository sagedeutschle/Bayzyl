# Project screenshots

The source library of captures for prismet.xyz, one folder per project. Each folder's
`from-live-site/` holds what the old (v18) site served, as a baseline; everything else is newer.

The live site does not load files from here. It uses re-encoded copies under `showcase/assets/`,
picked in `prismet-site/data/projects.json` (`cover`, `gallery`, and `shotAlts` for the alt text).
To use a new capture: re-encode it to WebP into `showcase/assets/<group>/`, point `gallery` at it,
and write its `shotAlts` entry from what the image actually shows.

Every open question for Sage (wording, captures, decisions) is collected in
`prismet-site/ASK-SAGE.md`.

## What's collected

| Project | New shots | Source |
|---|---|---|
| PrismCode | 7 screens at 3200×2000 (three of them 3200×1940 with the status bar cropped and the demo home-folder path masked): three agents on one task, a Codex approval, the git panel, a diff, session history, the usage table, PRISM A/B compare | The real app interface, built from the prismcode repo and run in a browser with demo data (`tools/prismcode-shots/`). The demo project is the public qr-scanner repo. The agent panes show a home-folder path (the status bar was cropped for the site). |
| Steam Rewind | (removed: every capture showed a Steam ID in the search field; the site uses crops of the list only) | Live tool, built-in demo library |
| Debt Clock | Desktop dark and light, full page, phone, phone full page | Live tool. Every capture shows the page's labelled Treasury fallback estimate. |
| QR tools | Scanner, Home Screen Planner, Phone Declutter, Playlist Maker on desktop and phone, plus the scanner decoding a real QR code from a fake camera | qr-scanner repo |
| Prismet app | 7 raw iPhone captures (on the site), plus the 7-screen App Store pack (1320×2868), which is device-framed marketing and stays off the site | kaleidoscope repo, `ios/docs/appstore-screenshots-v14` |
| Bayzyl | 5 in-game build shots, 2 help pages, the tab panel, a timelapse GIF | Bayzyl release 0.2.0-alpha.1 (`docs/images` on main) |
| The Helm | 23 faces rendered from their QML source at 2× (in `fiverr/screenshots/03-linux-desktop-helm/widgets/`; the site uses 15 of them from `assets/helm2/`) | `tools/render-helm-faces.py` |

Rebuild: `node showcase/tools/shoot-projects.mjs` (web tools) and `tools/prismcode-shots/`
(PrismCode: `vite build`, then `node make-bridge.mjs`, serve `dist/` on :5181, `node shoot.mjs <out>`).

## What only Sage can capture

These run on Sage's own machines, in games, or behind logins, so they can't be shot from here.
Highest value first.

| Project | Have now | Capture |
|---|---|---|
| Bayzyl | Release shots (help page reads "1/13"; the current guide is 16 pages). No image shows a detail brush or a gen brush in use. | A detail brush and a gen brush in use (flame or lightning; a ridge or a cave). The wand selection outline, a before/after of a big edit, `/bzlhelp` at GUI scale 2 (so it reads 1/16), at 1920×1080 or larger. |
| The Helm | Widget renders from source (the old iPhone remote shots and the Fleet Radar face were removed: they show host names) | A real full-desktop screenshot of each monitor, plus 2–3 close-ups of panels. The iPhone Mesh tab with host names hidden or renamed. |
| Minecraft builds (`fiverr/screenshots/02-minecraft/`) | 3 builds; the cloud-sea spire shows the HUD (hotbar, held block, crosshair) | The cloud-sea spire again with the HUD off. Who built the three builds is still an open question. |
| Prismet app | iPhone only | The Mac app: home grid and two or three games, window capture. iPad and Watch if they exist. The 3D Catan board. |
| The Long Now | 3 shots at 1280×720 | Each era at 2560×1440, plus the court and campaign screens. |
| Cicero | 1 shot | The council floor, a transcript and a verdict. |
| HOI4 AI War Room | 2 shots at 1920×1080, both the game alone | The console and map mid-turn, at 2560 wide. The control and standings board. |
| Civ V Mod Profiles | 1 shot | The launcher with both profiles, then in-game with mods loaded. |
| Quark | Icon only | The main window. |
| Airhorn | 1 small shot (1200×800) | The app window on a Retina Mac. |
| PrismCode | 7 demo-data screens | Optional: the app on a real project, with paths and account labels out of frame. |
| WoW Sidepanel (hidden) | 1 ultrawide shot of the character-select screen, with character names | In-game close-ups of the panel, names cropped. |
| Yggdrasil (hidden) | Nothing | Any 2–3 screens. |
| Westeros (UEBS2) (hidden) | Nothing | The map in-game (overview and a battle) and the Steam Workshop page. |
| Helix Research Desk (hidden) | Nothing | Main window and one result. |
| Wizard King's Decree (hidden) | Only the app tile | Nothing needed: the project was scrapped. |

How to capture:
- **Mac:** press ⌘⇧4, then Space, then click a window. Hold ⌥ while clicking to leave out the shadow. Screenshots are 2× on Retina.
- **KDE:** press Print to open Spectacle, then pick Full Screen or Active Window.
- **Minecraft:** press F1 to hide the HUD, then F2 to save a screenshot.
- **Steam games:** press F12 to save a screenshot.
- **Before sharing:** check for IP addresses, host names, emails, account names and notifications.

Where to put them: `showcase/shots/<project>/from-sage/`. Upload them on GitHub (on this branch, use
Add file → Upload files) or hand them to any Claude session.
