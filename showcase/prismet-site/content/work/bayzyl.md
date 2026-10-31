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

Selections, shapes, brushes, clipboards, persistent undo, shared kits, and builder profiles, in one consistent command set. Options use option:value syntax, with tab completion for masks and block distributions and confirmation around large edits. It integrates with WorldEdit and FastAsyncWorldEdit while retaining native implementations for core building workflows.

## facts
- Release: 0.2.0-alpha.2 · public prerelease
- Target: Paper 1.21.11 · Java 21
- Language: Java
- License: MIT

## highlights
- Durable clipboard recovery and /resume for interrupted copies in the published alpha.2
- Read-only Redstone Audit for inspecting a loaded selection
- Per-player persistent undo and /oops for recovering an edit
- Shared builder kits with themes, icons, aliases and notes
- Formula generation, detail and terrain brushes alongside selections, shapes and clipboard tools

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

The historical in-game sequence shows Bayzyl commands producing a dome, tower, sandstone pyramid and formula-driven copper ring in one demonstration world. It records that build, rather than the current server installation.

## story.evidence
In-game captures
