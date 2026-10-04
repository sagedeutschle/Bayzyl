<!-- Wording for the "Bayzyl" project page and its card. Edit the text under each heading; keep the "## " lines.
     *gold highlight*, **bold**. Highlights are "- " lines. Facts are "- Label: Value" lines. -->

## title
Bayzyl

## subtitle
WorldEdit is the foundation. Various tweaks to make it easier for beginners to learn, and for masters to create at their highest capacity. That is the core thesis behind all design decisions.

## tag
Paper Minecraft plugin

## status
Alpha · open source (MIT)

## year
2026

## role
Author

## summary
Selections, shapes, brushes, clipboards, persistent undo, shared kits, and builder profiles, in one consistent command set. Every option uses option:value syntax, tab-completion knows about masks and block distributions, and big edits ask for confirmation first. It runs alongside WorldEdit and FastAsyncWorldEdit, and falls back to its own implementations when neither is installed.

## facts
- Commands: 100+ registered
- Brushes: 25+ types
- Help: 16-page in-game guide
- Version: 0.2.0-alpha.1

## highlights
- Per-player undo history that survives restarts, plus /oops to undo and tell the team
- Shared builder kits with themes, icons, aliases, and notes
- Formula-driven /generate and /generatebiome
- Quality-of-life tools: ghost hand, auto-unstick, ruler, surface/ascend/descend

<!-- Proposed engineering-story wording, added by an agent on 2026-10-04 for Sage’s review.
     Existing authored wording above is preserved. Edit these fields through /edit. -->

## story.focus
Powerful edits, understandable controls.

## story.contribution
Author of the Paper plugin: command design, building tools and the safeguards around large edits.

## story.decision.1.title
Make scale explicit

## story.decision.1.text
Volume and chunk-span checks put confirmation at the point where a paste becomes a costly operation.

## story.decision.2.title
Bound recovery work

## story.decision.2.text
Undo history persists between sessions, with a cap on per-action block data. Oversized actions keep metadata rather than making restart recovery unbounded.

## story.outcome
The in-game sequence shows the commands producing a dome, tower, sandstone pyramid and formula-driven copper ring in one demonstration world.

## story.evidence
In-game captures
