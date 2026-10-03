# Editing prismet.xyz yourself

Everything a visitor reads on the site comes from a few plain-text files in this repository. Change a file, and the
site rebuilds and goes live by itself in about three minutes. No HTML, no server, no Claude session needed.

## The quick way: prismet.xyz/edit

1. Open https://prismet.xyz/edit and enter the PIN. That opens an eight-hour session in this browser.
2. The editor has three parts. Left: the site (the home page's sections, the colophon, the shared words, every record)
   and, under **Design**, the design tokens. Middle: the site itself, live. Right: everything about what is selected.
3. Change things. Click any text on the page and type. Drag a section or a record in the list to move it; the dot
   beside it hides or shows it; the star puts a record among the Principal works. **+** adds a record. Under Design,
   a token (the accent, the corner radius, a type size) changes everything that uses it, for night and day separately.
4. One element, one width: click a piece of text (or pick a section) and the right side shows its own style: size,
   weight, colour, spacing, hidden or shown. **Desktop / Tablet / Mobile** above those fields choose where the style
   applies: Desktop is every width, Tablet is 900px and narrower, Mobile is 600px and narrower, and a narrower width
   inherits whatever it does not set. A greyed value is what the page draws now; a dot marks what you set; ↺ removes it.
5. Pages and sections: **+ Page** makes a page (blank, article, showcase) out of library sections: hero, text, image +
   text, records, statistics, quote, call to action, gallery, divider, spacer. A page has a title, an address, a
   status (Draft is not built; Hidden is reachable by its address only; Published is on the site), and can join the
   navigation. **+ Section** under a page, or under Home, adds a section; on the home page it sits in the same order
   as the built-in sections. **Site settings** holds your name, the three links and the search description.
6. Images: wherever an image is chosen (a record's cover and gallery, a section's image) the media library opens.
   **Add images** (or drop files on it) takes any picture, resizes it, turns it into WebP and removes camera data. A new
   image shows at once and goes to the site with the next Publish. Describe each picture: the description is its alt text.
7. History: the **History** tab lists what was published. Click a revision to see the site as it was; it opens as a
   draft, so Undo comes back and Publish restores it.
8. Every change is a **draft**. It shows at once in the preview, saves by itself, survives a reload, and can be undone
   (⌘Z, ⇧⌘Z). Nothing reaches the site until you press **Publish**: one commit with every changed file. The bar at
   the bottom follows the build; "Live on prismet.xyz" means done. **Lock** ends the session; the draft stays.

Also: Desktop / Tablet / Mobile (or any width) for the preview; **Preview** shows the page as a visitor sees it;
⌘K searches pages, records, words, tokens and commands; a dot marks anything that differs from the live site and ↺
puts a field back.

Rules the fields follow (the hints under each field say the same):
- `*single asterisks*` make words gold, `**double asterisks**` make them bold.
- Lists are one item per line, each starting with `- `. Facts are `- Label: Value`.
- Keep placeholders such as `{count}`; the build fills them in.
- The build refuses anything that looks private (home-folder paths, IP addresses, Steam ids) and the word "TODO".
  If a publish ends with "the build ended with failure", open the run on GitHub; the last lines say which text to fix.

Where a draft lives: in this browser, and, when the drafts repository is set up (below), in a private repository, so
a draft started on one device opens on another. It never sits in this public repository.

### One-time setup: the secrets
The GitHub token that writes the files lives on the server, never in a browser; the PIN unlocks it. Both are
repository secrets (Settings → Secrets and variables → Actions) that the deploy job copies to Fly:
- `EDIT_GITHUB_TOKEN`: on GitHub, Settings → Developer settings → Personal access tokens → **Fine-grained tokens** →
  Generate new token. Repository access: only this repository. Permissions: **Contents: Read and write**,
  **Actions: Read**. Expiry: up to a year; when it expires the edit page says "GitHub refused the stored token" and
  you make a new one and update the secret.
- `EDIT_PIN`: at least six characters; digits are fine. Change it any time by editing the secret.
- `EDIT_DRAFT_REPO` (optional): `owner/name` of a **private** repository that holds the draft (one file,
  `draft.json`). Create it with a README so it has a `main` branch, and give the token above access to it as well
  (Repository access: both repositories; Contents: Read and write). Without it drafts stay in the browser.
After adding or changing any of them, run the workflow once (Actions → site → Run workflow with "deploy" ticked, or make
any edit on `main`); the next deploy carries the new values.

Wrong PINs: five tries per address per fifteen minutes, and after twenty-five wrong tries from anywhere the editor
locks for an hour. A lost PIN is replaced through the secret, never recovered.

## The other way: edit the files on GitHub

Open the file on github.com, press the pencil, edit, press "Commit changes". The same build runs.

| What you want to change | File |
|---|---|
| Any words on the home page, the labels, the colophon | `showcase/prismet-site/content/site.md` |
| A project's title, subtitle, status, summary, facts, highlights | `showcase/prismet-site/content/work/<project>.md` |
| Which projects show, their order, their rooms, their images and links | `showcase/prismet-site/data/projects.json` |
| A screenshot | upload it under `showcase/assets/<area>/`, then name it in `projects.json` (`cover`, `gallery`, `shotAlts`) |
| Design tokens changed at /edit | `showcase/prismet-site/data/theme.json` (only what differs from `src/site.css`) |
| One element's style, per width, set at /edit | `showcase/prismet-site/data/styles.json` |
| Pages and sections made at /edit | `showcase/prismet-site/data/pages.json` (the kinds of section: `pages/lib/sections.js`) |
| Images added at /edit | `showcase/assets/uploads/` |
| The project pages' structure, the design | `showcase/prismet-site/pages/lib/render.js`, `src/site.css` (this is code) |

Hiding a project: set `"hidden": true` on its entry in `projects.json`. Featuring one: add its slug to
`layout.featuredOrder`. Adding a project: copy an existing entry in `projects.json` and an existing
`content/work/<slug>.md`, give both the same new slug, upload its images.

## What happens after a change

The GitHub Action `site` (file `.github/workflows/site.yml`) runs on every push that touches `showcase/`:
1. **Build and verify.** Builds the site and checks it: no editor code, no inline scripts, every image sized and
   described, every link resolving, no private strings. A problem stops here and nothing changes on the site.
2. **Gate.** Starts the real server with the new build and checks every route the apps rely on (`/api/wordle`,
   `/steam`, `/debt`, `/rtc`, the old capture folder answering 404, the Steam page carrying no id).
3. **Deploy** (only from the `main` branch). Pushes the image to Fly, deploys it with the app's `fly.toml`, then runs
   the same gate against https://prismet.xyz.
4. **Browser checks** run beside the deploy and report in the Actions tab; they do not block it.

Undo: on GitHub, open the commit and press **Revert**; the previous content deploys the same way.

Secrets the workflow uses, under Settings → Secrets and variables → Actions:
- `FLY_API_TOKEN`: a Fly deploy token for the app (`fly tokens create deploy -a prismet-site-restless-horizon-217`).
- `EDIT_GITHUB_TOKEN` and `EDIT_PIN`: the edit page (above).
- `PRISMET_PRIVATE_WORDS` (optional): comma-separated words that must never appear on the site, such as host names.

## What the edit page cannot do yet

Delete or replace an image file, schedule a page, change the built-in pages' structure (the hall plan, the register,
the record pages' modules), or animate. Those stay in the code. New images wait in the browser they were added in until
published; a draft opened on another device does not carry them.
