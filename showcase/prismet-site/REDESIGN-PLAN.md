# prismet.xyz facelift plan

**Goal:** turn prismet.xyz from a two-tool page into the front door for everything Sage
builds: easy to scan, easy to hire from, and easy to keep current.

**Preview:** the prototype in `dist/` (build with `node showcase/prismet-site/build.mjs`).

## What's there today (as far as this session could see)

The live site couldn't be loaded from the cloud session that wrote this (prismet.xyz is
blocked by its network policy), so this comes from the Prismet repo's coordination log:

- Runs on **Fly.io** as the app `prismet-site-restless-horizon-217`.
- Serves the **Steam Rewind** explorer and the **Debt Clock**, linked from the Prismet app's
  Settings ("Steam Rewind and the Debt Clock, on the web").
- Serves **`/api/wordle`**, a proxy to the Supabase object the Wordgame broker publishes.
  **The shipping iOS and macOS apps call this URL every day. It must keep working byte-for-byte.**
- Missing: Bayzyl, THE HELM, PrismCode, Quark, the Oracle experiment, the qr-scanner tools,
  the Minecraft network, the Westeros map for UEBS 2, and any way to hire Sage.

## The new structure

```
/                      Home: prism hero → Lenses → Selected work → All work → About + Hire
/work/<slug>.html      One case-study page per project (generated)
/steam-rewind          existing tool, unchanged   ← confirm real path
/debt-clock            existing tool, unchanged   ← confirm real path
/api/wordle            existing route, untouched
```

- **The prism is the navigation.** One beam enters and splits into six colored beams, one per
  category. Clicking a beam (or a chip on phones) filters the work grid. Deep links work:
  `prismet.xyz/#minecraft`.
- **Six beams, six colors**, taken from the Prismet app's own color wheel
  (`PrismetDesign.swift`): Desktop & Linux (crimson), Apps & Games (orange),
  Game Worlds (ochre), Minecraft (green), Web Tools & Data (blue), AI & Agents (violet).
  Every card, tag, and page header carries its beam's color, so the category reads at a glance.
- **Lenses stay front and center**, right under the hero, because they're the live part of
  the site and the reason the app links here.
- **Selected work** gives the four strongest projects big editorial rows with real visuals and
  a fact strip (numbers like "99 commands", "19 + 3 lenses", "28 QML faces").
- **All work** lists every project with a status pill (Live on the App Store, Private build,
  Daily driver, In progress).
- **Hire** lists the same six services as the Fiverr gigs and links to Fiverr, GitHub, and LinkedIn.

## Design system

Taken from the Prismet app so the site, the app, and the Fiverr images look like one brand:

| Token | Dark (default) | Light |
|---|---|---|
| Ground | `#10111A` | `#F6F2E8` |
| Panel | `#1B1D2A` | `#FDFBF6` |
| Gold | `#E2B65C` | `#8E6A22` |
| Ink | `#F2F4FC` | `#1C1A12` |

- **Type:** Newsreader for display (closest web match to the app's New York serif titles),
  Hanken Grotesk for body, JetBrains Mono for labels and data. All **self-hosted**
  (`assets/fonts/`), so the site makes zero third-party requests, in keeping with the
  no-trackers stance of the qr-scanner tools.
- **Dark first**, matching the app's default paper; light mode uses the app's High Contrast paper.
  The ◐ button switches and remembers the choice.
- Respects reduced motion (the beams stop flowing), keyboard focus is always visible, and the
  layout holds from 390px phones to wide desktops with no sideways scroll.

## Adding a project

Everything comes from **`data/projects.json`**, the same file the Fiverr kit reads:

1. Add an object to `projects` (copy an existing one): `slug`, `title`, `subtitle`, `beam`,
   `status`, `facts`, `summary`, `highlights`, `stack`, `links`, `cover`, `gallery`.
2. Put images in `showcase/assets/<area>/` (WebP, ~720–1440px wide).
3. `node showcase/prismet-site/build.mjs` and deploy `dist/`.

**First one to add: Westeros for UEBS 2.** The entry exists with `"todo": true` and shows as
"In progress" until it has screenshots, a Workshop link, and a short description.

## Shipping it to Fly without breaking the apps

The site's source wasn't in any repo this session could reach, so check which of these
matches the current Fly app before deploying:

1. **Find the source** (likely on Sage's Mac): look for `fly.toml` with
   `app = "prismet-site-restless-horizon-217"`.
2. **If it's a Node/Express (or similar) server:** copy `dist/` into its static folder and keep
   the `/api/wordle`, Steam Rewind, and Debt Clock routes registered **before** the static
   handler.
3. **If it's a static server (nginx/Caddy) with a proxy rule for `/api/wordle`:** replace the
   web root with `dist/` and leave the proxy rule alone.
4. Update the `lenses[].href` values in `projects.json` to the real Steam Rewind and Debt Clock
   paths, then rebuild.
5. **Before switching DNS or deploying to the live app:**
   - `curl -sS https://<staging>/api/wordle` returns today's payload.
   - Open Steam Rewind and the Debt Clock from the new homepage.
   - Open the site on a phone.
6. `fly deploy`, then re-run the `/api/wordle` check against `https://prismet.xyz`.

## Launch checklist

- [ ] Real routes for Steam Rewind and Debt Clock set in `projects.json`
- [ ] Westeros screenshots + description added (or entry hidden)
- [ ] Agent Ops details filled in from `CLAUDE-OPERATIONS-HANDOFF.md`
- [ ] Fiverr profile URL confirmed (`owner.fiverr` in `projects.json`)
- [ ] `/api/wordle` verified after deploy
- [ ] Share preview checked (the `og:image` is the Prismet App Store spread)
