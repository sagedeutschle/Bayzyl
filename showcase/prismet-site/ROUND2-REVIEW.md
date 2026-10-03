# prismet.xyz — round 2 review for Sage (2026-10-03)

Branch `claude/funny-wozniak-4j636o`. Production is still Fly v20 (the round-1 redesign). Nothing in this
round is deployed. The questions for you are at the end; `ASK-SAGE.md` next to this file holds the full list.

## 1. What the studio did

Six stations ran against the live site: art, world/UX, frontend, curator, and a read-only critic and QA.

- **The entrance.** The hall plan is now surveyed linework: walls end on the surfaces they meet, rooms have
  doorways, the piers stand in the ring, the prism sits centred with a faint brass inlay, strokes snap to whole
  pixels, and day mode has its own survey-grey line and a warm lantern. On phones the wing list names each wing's
  lead record ("Helm + 1") instead of a count. The bar shows GitHub and LinkedIn from 1100px wide.
- **The four doors** sit as one family: the same inset and gutter, a 3×2 tile mosaic, Helm's faces in two even rows,
  the PrismCode door re-cropped from the 2× capture, and every text row aligned across each pair.
- **The register** gained a seek line ("Find a record": typing filters by name, tool, stack or wing; `/` focuses it
  from anywhere on the home page; Enter opens the top match; Esc clears) and thread ticks (hovering or focusing a row
  lights its related records). Chips fit one line at 1440 and wrap sanely at 320. Nothing changed for a visitor who
  never touches either.
- **The Bayzyl bench** opens on the finished plaza (step 9 of 9) with a tower elevation beside the stepper, drawn from
  the real numbers in `/hcyl … 6 18` and `/hpyramid … 7`, captioned with both commands.
- **Rooms in day mode:** every band stays dark; the PrismCode notebook is a blueprint band in both modes; the arcade
  wall is 7×3 ending on the two lens tiles; era placards stand beside the stage at wide widths. The Wordgame tile
  expands a one-line note naming the `/api/wordle` route as text.
- **Images:** 189 width variants next to their sources, so no image is drawn above 2× its CSS width; the three
  flagship pages weigh 505, 443 and 429 KB on a full scroll (were 895, 814, 583). Every asset URL carries
  `?v=<content hash>`.
- **Checks:** `verify.mjs` runs at the end of every build (no editor, inline script, reserved name, broken link, TODO
  or private string) and `tools/tests/site-check.mjs` runs 44 browser checks. Both pass on this build.
- **Words and alt text:** 22 alt texts corrected against what the images show; search aliases on all 13 records;
  the share image declares its alt, width, height and card type; `favicon.ico` is served.

## 2. Privacy, fixed and pending

Fixed in this repo (already on the branch):
- The WoW character-select captures (character names and a realm) are deleted from all four places they lived.
- The three PrismCode plates and their originals printed a home-folder path with the account name inside the agent
  panes; those text runs are masked in the row colour and the status bar is cropped. The demo script that produced the
  path now uses a neutral one.

Pending, because they live in the server image, not this repo (needs a deploy; see section 6):
- `/shots/helm-1-full.webp` on the live server is the Helm "Mesh" screen listing your host and server names. Nothing
  links it, but the old site did, so the URL is known to caches and crawlers.
- `/steam` opens with your SteamID64 already in the search box, and its script restores it on Reset.

## 3. Proposed copy, flagged for you

Everything below is new copy written by the studio, never your wording. It is all in new keys in
`content/site.md` under the "2026-10 redesign" comment, so it is easy to drop or edit:

- **Case notes** on Bayzyl ("Why it exists", "The hard part", "What comes next", plus a built-versus-planned note),
  Prismet ("Why it exists") and Helm ("Why it exists"): `bench.why/hard/next/plan_note`, `prismet.why`, `helm.why`,
  `case.*_title`. Written only from claims your README and docs support, quoting your phrases where possible. To
  remove them, delete the keys; the build skips a flagship whose keys are missing.
- **Seek line:** `seek.label` "Find a record", `seek.placeholder`, `seek.hint`, `seek.none`.
- **Wordgame note:** `arcade.note_hint` "Daily word", `arcade.note_wordgame`.
- **Elevation:** `art.elevation_title`, `art.elevation_note`, `art.elevation_alt`.
- **Alt text:** `plate.alt`, `og.image_alt`. **Bar:** `nav.github`, `nav.linkedin`.
- **Changed:** `plate.caption` now reads "A night build on a Minecraft server I ran." (first person; it no longer
  says who built it).

## 4. What the critic and QA measured on the live v20

- Critic's score: 37 of 45, up from 32 after round 1. Nothing fell. The gate failed only on the two server-side
  privacy items above and on three lines of your own Prismet wording (see `ASK-SAGE.md`).
- QA: every security header present, no CSP errors, axe clean, all four pages within Google's "good" LCP and CLS
  through Fly. One real visit (home, two flagships, a lens, home again) sends about 70 requests against the server's
  limit of 75 per minute per IP; a visitor who steps through the whole bench sends 78 and gets cut off with a JSON
  error that blames Steam. That is the strongest reason to deploy the server patch.

## 5. Still open after this round

- The share card still loses the name in platforms that crop to a square (Slack compact, WhatsApp); LinkedIn and
  iMessage large cards show it whole. Fixing the square crop means a different composition for the card.
- The torus formula ring was tried and dropped: unreadable around a circle, and no room beside the stepper.
- Fonts are 213 KB on every first visit. Subsetting the italic further depends on how much italic stays.
- Chips leave one orphan at two width bands (around 690px and 1090–1270px).
- `PROJECTS_URL` on Fly (lost in the v19 incident) is still unknown to anyone but you.

## 6. Preview and the deploy question

Preview with Edit mode: https://claude.ai/artifact/C51vasTVQgxsp6JLJjccCv (private; round-2 build).

Deploying means two things, and both need your explicit yes:

1. **The site build** (this branch's `dist/`): everything in section 1.
2. **The server patch v21** (`showcase/tools/deploy/server-v21.patch`, `scrub-public.sh`, one whiteout line): retires
   `/shots/*` with 404 and removes the folder from the image; strips the Steam ID from `/steam` (the box opens empty
   and Reset clears it; the demo fixture and search work as before); raises the static rate limit from 75 to 600 a
   minute and answers a limited request with plain text and `retry-after`; sends `vary: accept-encoding`; and caches
   `?v=`-versioned site files for a year. It was tested locally against every protected route (`/api/wordle`, `/rtc`
   upgrade 101, `/steam`, `/debt`). It is the first change to the server code since the redesign.

Say "deploy both", "deploy the site only", or wait. Before any deploy I will also need the Mac copy of
`business/showcase` reconciled, or your word to deploy from the branch as is.
