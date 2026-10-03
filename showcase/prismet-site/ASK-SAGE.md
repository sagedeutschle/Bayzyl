# Questions only Sage can answer

For prismet.xyz, as of 2026-10-03. The studio could not settle these from the repo. Each one has a
default that holds until you answer, and nothing ships without your yes. Short answers are fine.

## 1. Your wording: confirm or fix

| File and line | It says | What we found | Default |
|---|---|---|---|
| `content/work/prismet-app.md:20` | "three live-data lenses" | Two are live: Steam Rewind and Debt Clock. The third, Oracle, came from the Wizard King's Decree, which was scrapped. | Unchanged |
| `prismet-app.md:24` | "Watch" | No Watch app, file or capture in the repo. | Unchanged |
| `prismet-app.md:26` | "237 Python" tests | The scrapped Decree's test suite has the same count (`wizard-kings-decree.md:24`). | Unchanged |
| `prismet-app.md:30` | "A 3D Catan board" | Catan is not one of the 19 game tiles. Does it ship? | Unchanged |
| `prismet-app.md:34` | "(see Agent Ops)" | That page was removed on 10-01, so the pointer leads nowhere. | Unchanged |
| `prismet-app.md:5` and `:17` | Title "Prismet"; role line | "Prismet" is also the name of the whole site. Rename the app "Prismet Arcade"? Was it co-developed? | Unchanged |
| `content/work/the-helm.md:20` and `:25` | "28" QML widgets / faces | 24 faces were rendered from source; 23 can be shown. | Unchanged |
| `the-helm.md:27` and `:28` | "VSCODIUM: Custom built IDE", "Install: Best on Mac or Linux" | Unclear on a page about a KDE desktop. What do they mean? | Unchanged |
| `content/work/hoi4-war-room.md:27` | "An observer console logs each AI agent's turns" | The only capture shows the game's own console in observe mode, logging dice rolls. | Unchanged |
| `hoi4-war-room.md:20` and `:26` | "control and standings board" | No capture shows the board. | Unchanged |
| `content/work/debt-clock.md:20` | "using current Treasury data" | Every capture shows the page's own labelled fallback estimate. | Unchanged |
| `content/work/bayzyl.md:28` | "16-page in-game guide" | True in the code; the help screenshot still reads "1/13". See captures. | Unchanged |
| Bayzyl `README.md:131` | "parametric noise brushes" listed as planned v2 work | The code already has them (`/brushgen`, 13 terrain types). Are they part of the alpha? | Site text treats them as built |
| The Long Now | (no engine named) | "Godot" appears only in studio notes, never in your words, so the site does not say it. | Not named |
| `content/site.md:382–413` | New proposed text, flagged [P] | Written by the studio from your docs: Bayzyl "why / the hard part / what's next", a "why" for Prismet and Helm, and image descriptions for the night plate and the link preview. Edit freely. | Not shown until you approve |

## 2. Captures that would strengthen the site

Highest value first (the full list is in `showcase/shots/README.md`):

1. **Bayzyl:** a detail brush and a gen brush in use (flame or lightning; a ridge or a cave). Also the wand selection outline, a before/after of a big edit, and `/bzlhelp` at GUI scale 2 so it reads 1/16, at 1920×1080 or larger.
2. **Helm:** a real full-desktop screenshot of each monitor, plus 2–3 close-ups of panels. The iPhone Mesh tab with host names hidden or renamed.
3. **Minecraft:** the cloud-sea spire again with the HUD off.
4. **Prismet:** the Mac app (home grid and two or three games, window capture); iPad and Watch if they exist; the 3D Catan board.
5. **The Long Now:** each era at 2560×1440, plus the court and campaign screens.
6. **Smaller:** Cicero (council floor, a transcript, a verdict); HOI4 (console and map mid-turn at 2560 wide, and the standings board); Civ V (the launcher with both profiles, then in-game with mods loaded); Quark (the main window); Airhorn (the window on a Retina Mac).

How:
- **Mac:** ⌘⇧4, then Space, then click a window. Hold ⌥ while clicking to leave out the shadow.
- **KDE:** press Print to open Spectacle, then pick Full Screen or Active Window.
- **Minecraft:** F1 hides the HUD, then F2 saves the screenshot.
- **Steam games:** F12.
- **Before sharing:** check for IP addresses, host names, emails, account names and notifications.
- **Where:** `showcase/shots/<project>/from-sage/` (on GitHub: Add file → Upload files), or hand them to any Claude session.

## 3. Who built the server builds?

The three builds in `fiverr/screenshots/02-minecraft/` (the night village with the giant tree, the
cherry village, the spire above the clouds). The repo's own captions only say they are on "the server
Bayzyl runs on". The home page shows the night one, captioned "A night build on the Minecraft network
Sage runs." (`site.md:260`).

- Did you build them? If so, with what, and over roughly how long? Then they can become a record of their own.
- Should the Minecraft server network page come back (you removed it on 10-01), merged with the builds?
  Your intro and hire list mention running servers, but nothing on the site shows them now.

Default: the plate and caption stay; no new record.

## 4. PROJECTS_URL

This setting on the live server (the address of your old project catalog) was lost on 10-02 during
the failed v19 deploy. Nothing on the new site uses it. Restore it? If yes, set it yourself or share the
address privately; it never goes in this repo. Default: left unset.

## 5. Your Mac's newer copy

Your Mac has a newer `business/showcase` folder (a different project list and build script, seven
copy files, seven Mac-only projects, 29 images) that was never pushed. Push or share it so its
wording and project list can be merged. Default: no deploy until it is merged.

## 6. Volhaven / Velthir

Nothing from it is in the repo, and the 10-01 Fiverr spec keeps it off public pages, so the site uses
none of it. Should that change? Default: it stays off.

## 7. Smaller calls

- **Links between records:** does PrismCode's local DeepSeek run on one of Helm's machines? Does Helm's
  Mesh tab watch the server Bayzyl runs on? A yes to either lets us link those pages.
- **Privacy:** three PrismCode screenshots show your Mac home-folder path inside the agent panes (the
  status bar was already cropped). Keep, crop or recapture?
- **Old screenshots** of the Helm iPhone app are still served from the live server's `/shots`. Remove them? (A server change and a deploy.)
- **Git history** still holds the private captures removed on 10-02. Rewriting history is your call.
- **Server code:** create the private repo `sagedeutschle/prismet-site` so the server code can be pushed there.
