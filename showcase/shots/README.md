# Project screenshots

Better screenshots for the 18 projects on prismet.xyz, one folder per project. Each folder's
`from-live-site/` holds the originals the current prismet.xyz serves, as a baseline. Everything
else is new.

Nothing here is wired into the site yet. Pick the best ones, then point `cover` / `gallery` in
`prismet-site/data/projects.json` at them.

## What's collected

| Project | New shots | Source |
|---|---|---|
| PrismCode | 7 screens at 3200×2000: three agents on one task, a Codex approval, the git panel, a diff, session history, the usage table, PRISM A/B compare | The real app interface, built from the prismcode repo and run in a browser with demo data (`tools/prismcode-shots/`). The demo project is the public qr-scanner repo. |
| Steam Rewind | All 13 lenses, the full page, 2 phone views | Live tool, built-in demo library |
| Debt Clock | Desktop dark and light, full page, phone, phone full page | Live tool |
| QR tools | Scanner, Home Screen Planner, Phone Declutter, Playlist Maker on desktop and phone, plus the scanner decoding a real QR code from a fake camera | qr-scanner repo |
| Prismet app | The 7-screen App Store pack (1320×2868) and 7 raw iPhone captures | kaleidoscope repo, `ios/docs/appstore-screenshots-v14` |
| Bayzyl | 5 in-game build shots, 2 help pages, the tab panel, a timelapse GIF | Bayzyl release 0.2.0-alpha.1 (`docs/images` on main) |

Rebuild: `node showcase/tools/shoot-projects.mjs` (web tools) and `tools/prismcode-shots/`
(PrismCode: `vite build`, then `node make-bridge.mjs`, serve `dist/` on :5181, `node shoot.mjs <out>`).

## What only Sage can capture

These run on Sage's own machines, in games, or behind logins, so they can't be shot from here.

| Project | Have now | Capture |
|---|---|---|
| Prismet app | iPhone only | The Mac app: home grid and two or three games, window capture. iPad and Watch if they exist. The 3D Catan board. |
| The Helm | Widget renders from source, 3 old iPhone remote shots | A real full-desktop screenshot of each monitor, plus 2–3 close-ups of panels. |
| Bayzyl | Release shots (help page reads "1/13"; the current guide is 16 pages) | The wand selection outline, a before/after of a big edit, a brush in use, `/bzlhelp` at GUI scale 2, at 1920×1080 or larger. |
| The Long Now | 3 shots at 1280×720 | Each era at 2560×1440, plus the court and campaign screens. |
| Cicero | 1 shot | The council floor, a transcript and a verdict. |
| HOI4 AI War Room | 2 shots at 1920×1080 | The console and map mid-turn, at 2560 wide. |
| Wizard King's Decree | Only the app tile | A screenshot of a decree, if the project stays on the site (the Mac session reports Oracle was scrapped). |
| Civ V Mod Profiles | 1 shot | The launcher with both profiles, then in-game with mods loaded. |
| WoW Sidepanel | 1 ultrawide shot | In-game close-ups of the panel. |
| Yggdrasil | Nothing | Any 2–3 screens. |
| Westeros (UEBS2) | Nothing | The map in-game (overview and a battle) and the Steam Workshop page. |
| Helix Research Desk | Nothing | Main window and one result. |
| Quark | Icon only | The main window. |
| Airhorn | 1 small shot (1200×800) | The app window on a Retina Mac. |

How to capture:
- **Mac:** press ⌘⇧4, then Space, then click a window. Hold ⌥ while clicking to leave out the shadow. Screenshots are 2× on Retina.
- **KDE:** press Print to open Spectacle, then pick Full Screen or Active Window.
- **Minecraft:** press F1 to hide the HUD, then F2 to save a screenshot.
- **Steam games:** press F12 to save a screenshot.
- **Before sharing:** check for IP addresses, host names, emails, account names and notifications.

Where to put them: `showcase/shots/<project>/from-sage/`. Upload them on GitHub (on this branch, use
Add file → Upload files) or hand them to any Claude session.
