"""Builds the Fiverr Gig Kit page from ../gigs.json and checks every field against Fiverr's limits.

    python3 showcase/fiverr/paste-kit/build.py

Writes showcase/fiverr/paste-kit/fiverr-gig-kit.html, the page published as the
"Fiverr Gig Kit" artifact. Edit gigs.json (and the CHECKLIST below for live status), not the HTML.
"""
import html
import re
import json
import sys
from pathlib import Path

E = html.escape
problems = []


def check(label, text, lo=None, hi=None):
    n = len(text)
    if lo is not None and n < lo:
        problems.append(f"{label}: {n} < {lo}")
    if hi is not None and n > hi:
        problems.append(f"{label}: {n} > {hi}")
    return n


# ---------------------------------------------------------------- data (from gigs.json)
HERE = Path(__file__).resolve().parent
KIT = json.loads((HERE.parent / "gigs.json").read_text())
P = KIT["profile"]
LAUNCH_IDS = ["mc-plugin", "mc-server", "ios-app", "ai-agents"]
STYLE = {
    "mc-plugin": ("#4D8C6B", "Minecraft plugins", "100+ commands and 16-page guide (was 99 and 15). Spigot caveat added to FAQ 1."),
    "mc-server": ("#4D8C6B", "Minecraft servers", "Dropped the \"one admin command\" claim from the description and the top package."),
    "ios-app": ("#E68C33", "iPhone & Mac apps", "Top package is $700 / 30 days (was $1,500 / 45). App name is Prismet Arcade, credited as co-developed to match your resume. Live-data lenses removed. FAQ 4 added."),
    "ai-agents": ("#75579E", "AI agents", "The Wizard King's Decree bullet and its image are gone, since that project was scrapped."),
    "web-tool": ("#3D75A8", "Web tools", ""),
    "linux-desktop": ("#DB4757", "Linux desktops", ""),
}


def as_gig(g):
    hue, short, changed = STYLE[g["id"]]
    return dict(id=g["id"], hue=hue, short=short, changed=changed, title=g["title"], tags=g["tags"],
                packages=[(p["name"], p["price"], p["days"], p["revisions"], p["desc"]) for p in g["packages"]],
                desc=g["description"], faq=[tuple(x) for x in g["faq"]], reqs=g["requirements"],
                images=g["images"], category=g["category"])


GIGS = {g["id"]: as_gig(g) for g in KIT["gigs"]}
LAUNCH = [GIGS[i] for i in LAUNCH_IDS]
HELD = [g for i, g in GIGS.items() if i not in LAUNCH_IDS]
TITLE_LIVE = P["oneLiner"]
ABOUT = P["about"]
EDUCATION = list(P["education"].items())
SKILLS = [(s, P["skillLevels"][s]) for s in P["skills"]]
OLD_SKILLS = ", ".join(P["removeSkills"])
WORK = [(k, v) for k, v in P["work"].items() if k != "Description"]
WORK_DESC = P["work"]["Description"]
VIDEO_SCRIPT = [tuple(x) for x in P["introVideo"]["beats"]]
GIG_VIDEO = [tuple(x) for x in P["gigVideo"]["beats"]]
PORTFOLIO = [(p["title"], p["desc"], p["images"]) for p in KIT["portfolio"]]

# Live status on fiverr.com/bayzyl. Update by hand after each Fiverr session.
CHECKLIST = [
    ("display-name", "live", "Display name", "Sage Deutschle. Saved Oct 1.", None),
    ("title", "live", "Title", TITLE_LIVE + ". Saved Oct 1.", None),
    ("verify", "blocked", "ID check and W-9", "Fiverr flagged an ID check and a W-9 tax form before your gigs can be seen (Astra saw this Oct 2). Only you can complete them.", "#face"),
    ("photo", "optional", "Profile photo", "Your \"B\" logo is allowed. A headshot usually gets more clicks, but your face is never required.", "#face"),
    ("about", "todo", "About", "You approved saving it on Oct 2. Not saved yet as of Astra's last report. Paste the text below.", "#about"),
    ("education", "blocked", "Education", "Fiverr's year list stops at 2026, so 2027 can't be saved. Leave it empty for now.", "#education"),
    ("skills", "todo", "Skills", "Replace the 13 skills that are all set to Intermediate.", "#skills"),
    ("portfolio", "todo", "Portfolio", "0 projects as of Oct 1. Add the 5 below. You approved saving them on Oct 2.", "#portfolio"),
    ("gigs", "todo", "Gigs", "1 draft saved: Minecraft plugins (Astra deleted an older duplicate on Oct 2). Add the other 3 launch gigs, and check prices before each Publish.", "#gigs"),
    ("languages", "todo", "Languages", "Make sure English has a proficiency level set.", None),
    ("video", "optional", "Intro video", "Needs you on camera. Skip it if you'd rather not show your face.", "#video"),
    ("gig-video", "optional", "Gig video", "No face needed. A captioned Bayzyl build video for the plugin gig.", "#gig-video"),
    ("work", "optional", "Work experience", "One entry is enough. Draft below.", "#work"),
]

# ---------------------------------------------------------------- validate
check("About", ABOUT, 150, 600)
check("Work description", WORK_DESC, 1, 1000)
for g in LAUNCH + HELD:
    check(f"{g['id']} title", "I will " + g["title"], 15, 80)
    if len(g["tags"]) > 5:
        problems.append(f"{g['id']}: more than 5 tags")
    for t in g["tags"]:
        check(f"{g['id']} tag {t}", t, 1, 20)
        if not re.fullmatch(r"[a-z0-9 ]+", t):
            problems.append(f"{g['id']} tag {t}: letters, numbers, and spaces only")
    for p in g["packages"]:
        check(f"{g['id']} package {p[0]} name", p[0], 1, 35)
        check(f"{g['id']} package {p[0]} desc", p[4], 1, 100)
    check(f"{g['id']} description", g["desc"], 120, 1200)
    for q, a in g.get("faq", []):
        check(f"{g['id']} FAQ q {q}", q, 1, 80)
        check(f"{g['id']} FAQ a {q}", a, 1, 300)
    for r in g.get("reqs", []):
        check(f"{g['id']} req", r, 1, 400)
for t, d, imgs in PORTFOLIO:
    check(f"portfolio {t} desc", d, 120, 1400)
    check(f"portfolio {t} title", t, 1, 70)
    if len(imgs) > 5:
        problems.append(f"portfolio {t}: more than 5 files")
words = sum(len(s.split()) for _, s in VIDEO_SCRIPT)
seconds = round(words / 2.4)  # about 145 spoken words a minute
if not 20 <= seconds <= 60:
    problems.append(f"video script runs ~{seconds}s")
for t, shot, cap in GIG_VIDEO:
    check(f"gig video caption {t}", cap, 1, 60)

if problems:
    print("\n".join(problems))
    sys.exit(1)

# ---------------------------------------------------------------- render
fid = 0


def field(label, text, lo=None, hi=None, rows=None):
    global fid
    fid += 1
    i = f"f{fid}"
    n = len(text)
    lim = f"{n} / {hi}" if hi else f"{n} chars"
    if rows is None:
        rows = max(2, min(18, text.count("\n") + len(text) // 70 + 1))
    data = ""
    if hi:
        data += f' data-max="{hi}"'
    if lo:
        data += f' data-min="{lo}"'
    return (f'<div class="field"><div class="fh"><label for="{i}">{E(label)}</label>'
            f'<span class="n" data-count-for="{i}"{data}>{lim}</span>'
            f'<button type="button" class="copy" data-for="{i}">Copy</button></div>'
            f'<textarea id="{i}" readonly rows="{rows}">{E(text)}</textarea></div>')


def chip_copy(value):
    global fid
    fid += 1
    i = f"v{fid}"
    return (f'<span class="val"><span id="{i}">{E(value)}</span>'
            f'<button type="button" class="copy sm" data-text-for="{i}">Copy</button></span>')


def files_list(imgs, first_is_cover=True):
    items = []
    for k, name in enumerate(imgs):
        tag = '<span class="tag">Cover</span>' if (k == 0 and first_is_cover) else ""
        items.append(f"<li><code>{E(name)}</code>{tag}</li>")
    return f'<ol class="files">{"".join(items)}</ol>'


def pkg_table(pk):
    head = "".join(f"<th>{E(p[0])}</th>" for p in pk)
    price = "".join(f'<td class="num">${p[1]:,}</td>' for p in pk)
    days = "".join(f'<td class="num">{p[2]} day{"s" if p[2] != 1 else ""}</td>' for p in pk)
    rev = "".join(f'<td class="num">{p[3]}</td>' for p in pk)
    inc = "".join(f"<td>{E(p[4])}</td>" for p in pk)
    return (f'<div class="tablewrap"><table class="pk"><thead><tr><th></th>{head}</tr></thead><tbody>'
            f"<tr><th>Price</th>{price}</tr><tr><th>Delivery</th>{days}</tr>"
            f"<tr><th>Revisions</th>{rev}</tr><tr><th>Includes</th>{inc}</tr></tbody></table></div>")


def gig_card(g, n, total):
    faq = "".join(field(f"FAQ {k}: {q}", a, hi=300) for k, (q, a) in enumerate(g["faq"], 1))
    reqs = "".join(field(f"Requirement {k}", r) for k, r in enumerate(g["reqs"], 1))
    return f"""
<section id="{g['id']}" class="card gig" style="--h:{g['hue']}">
  <div class="gighead"><p class="eyebrow">Gig {n} of {total}</p><p class="changed"><b>Changed:</b> {E(g['changed'])}</p></div>
  <h3 class="gigtitle"><span class="iwill">I will</span> {E(g['title'])}</h3>
  {field('Title (after "I will")', g['title'], hi=73, rows=2)}
  {field('Search tags (one per box)', ', '.join(g['tags']), rows=2)}
  {pkg_table(g['packages'])}
  {field('Description', g['desc'], lo=120, hi=1200, rows=16)}
  <details><summary>FAQ ({len(g['faq'])}) and buyer requirements ({len(g['reqs'])})</summary>{faq}{reqs}</details>
  <div class="meta"><div><p class="label">Images to upload, in this order</p>{files_list(g['images'])}</div>
  <div><p class="label">Category</p><p class="cat">{E(g['category'])}</p></div></div>
</section>"""


def held_card(g):
    return f"""
<details class="held" style="--h:{g['hue']}"><summary><span class="dot"></span><span class="iwill">I will</span> {E(g['title'])}</summary>
  <div class="heldbody">
  {field('Title (after "I will")', g['title'], hi=73, rows=2)}
  {field('Search tags', ', '.join(g['tags']), rows=2)}
  {pkg_table(g['packages'])}
  {field('Description', g['desc'], lo=120, hi=1200, rows=14)}
  <div><p class="label">Images</p>{files_list(g['images'])}</div>
  </div>
</details>"""


status_label = {"live": "Live", "todo": "To do", "blocked": "Blocked", "optional": "Optional"}
rows = []
for key, st, name, note, link in CHECKLIST:
    locked = st == "live"
    box = (f'<input type="checkbox" id="c-{key}" data-key="{key}"'
           f'{" checked disabled" if locked else ""}>')
    nm = f'<a href="{link}">{E(name)}</a>' if link else E(name)
    rows.append(f'<li class="st-{st}">{box}<label for="c-{key}"><span class="pill">{status_label[st]}</span>'
                f'<span class="ck-name">{nm}</span><span class="ck-note">{E(note)}</span></label></li>')
checklist_html = "".join(rows)
todo_count = sum(1 for c in CHECKLIST if c[1] == "todo")

edu_rows = "".join(f"<tr><th>{E(k)}</th><td>{chip_copy(v)}</td></tr>" for k, v in EDUCATION)
work_rows = "".join(f"<tr><th>{E(k)}</th><td>{chip_copy(v) if k != 'Dates' else E(v)}</td></tr>" for k, v in WORK)
skill_items = "".join(
    f'<li class="lv-{lv.lower()}"><span class="sk">{E(s)}</span><span class="lv">{lv}</span></li>' for s, lv in SKILLS)
video_rows = "".join(f"<li><span class=\"beat\">{E(b)}</span><p>{E(t)}</p></li>" for b, t in VIDEO_SCRIPT)
video_full = " ".join(t for _, t in VIDEO_SCRIPT)

portfolio_html = ""
for t, d, imgs in PORTFOLIO:
    portfolio_html += f"""<article class="pf"><h3>{E(t)}</h3>{field('Title', t, rows=1)}{field('Description', d, lo=120, hi=1400, rows=4)}
<div><p class="label">Files</p>{files_list(imgs, first_is_cover=False)}</div></article>"""

gigs_html = "".join(gig_card(g, n, len(LAUNCH)) for n, g in enumerate(LAUNCH, 1))
held_html = "".join(held_card(g) for g in HELD)

toc = "".join(f'<a href="#{g["id"]}" style="--h:{g["hue"]}"><i></i>{E(g["short"])}</a>' for g in LAUNCH)

page = (HERE / "template.html").read_text()
for k, v in {
    "{{CHECKLIST}}": checklist_html,
    "{{TODO_COUNT}}": str(todo_count),
    "{{TITLE_LIVE}}": E(TITLE_LIVE),
    "{{ABOUT}}": field("About (150 to 600)", ABOUT, lo=150, hi=600, rows=7),
    "{{EDU_ROWS}}": edu_rows,
    "{{SKILLS}}": skill_items,
    "{{SKILL_COPY}}": field("All skill names", ", ".join(s for s, _ in SKILLS), rows=3),
    "{{OLD_SKILLS}}": E(OLD_SKILLS),
    "{{WORK_ROWS}}": work_rows,
    "{{WORK_DESC}}": field("Description", WORK_DESC, rows=5),
    "{{VIDEO_ROWS}}": video_rows,
    "{{VIDEO_FULL}}": field("Whole script", video_full, rows=6),
    "{{VIDEO_SECONDS}}": str(seconds),
    "{{GIG_VIDEO_ROWS}}": "".join(f'<tr><td class="num">{t}</td><td>{E(shot)}</td><td>{E(cap)}</td></tr>' for t, shot, cap in GIG_VIDEO),
    "{{VIDEO_WORDS}}": str(words),
    "{{PORTFOLIO}}": portfolio_html,
    "{{GIGS}}": gigs_html,
    "{{HELD}}": held_html,
    "{{TOC}}": toc,
}.items():
    page = page.replace(k, v)
assert "{{" not in page, re.findall(r"\{\{\w+\}\}", page)
(HERE / "fiverr-gig-kit.html").write_text(page)
print(f"ok: {len(LAUNCH)} launch gigs, {len(HELD)} held, {len(PORTFOLIO)} portfolio projects; "
      f"About {len(ABOUT)}/600; video ~{seconds}s ({words} words); all fields within limits")
