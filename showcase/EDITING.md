# Editing prismet.xyz yourself

Everything a visitor reads on the site comes from a few plain-text files in this repository. Change a file, and the
site rebuilds and goes live by itself in about three minutes. No HTML, no server, no Claude session needed.

## The quick way: prismet.xyz/edit

1. Open https://prismet.xyz/edit.
2. Paste a GitHub token (one-time setup below) and press Connect.
3. Pick "Home page and shared words" or a project. Every block of text is a field. Change what you like.
4. Press **Publish**. The page saves the file to GitHub and shows the build's progress; "Live on prismet.xyz" means done.

Rules the fields follow (the hints under each field say the same):
- `*single asterisks*` make words gold, `**double asterisks**` make them bold.
- Lists are one item per line, each starting with `- `. Facts are `- Label: Value`.
- Keep placeholders such as `{count}`; the build fills them in.
- The build refuses anything that looks private (home-folder paths, IP addresses, Steam ids) and the word "TODO".
  If a save ends with "the build ended with failure", open the run on GitHub; the last lines say which text to fix.

### One-time setup: the token
On GitHub: Settings → Developer settings → Personal access tokens → **Fine-grained tokens** → Generate new token.
Repository access: only this repository. Permissions: **Contents: Read and write**, **Actions: Read**. Expiry: your
choice; the page asks again when it expires. The token lives only in the browser tab where you pasted it.

## The other way: edit the files on GitHub

Open the file on github.com, press the pencil, edit, press "Commit changes". The same build runs.

| What you want to change | File |
|---|---|
| Any words on the home page, the labels, the colophon | `showcase/prismet-site/content/site.md` |
| A project's title, subtitle, status, summary, facts, highlights | `showcase/prismet-site/content/work/<project>.md` |
| Which projects show, their order, their rooms, their images and links | `showcase/prismet-site/data/projects.json` |
| A screenshot | upload it under `showcase/assets/<area>/`, then name it in `projects.json` (`cover`, `gallery`, `shotAlts`) |
| The project pages' structure, the design | `showcase/prismet-site/build.mjs`, `src/site.css` (this is code) |

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

Secrets the workflow needs, once, under Settings → Secrets and variables → Actions:
- `FLY_API_TOKEN`: a Fly deploy token for the app (`fly tokens create deploy -a prismet-site-restless-horizon-217`).
- `PRISMET_PRIVATE_WORDS` (optional): comma-separated words that must never appear on the site, such as host names.

## What the edit page cannot do

Reorder or hide projects, add images, or change the design. Those are the JSON file and the code, edited on GitHub.
