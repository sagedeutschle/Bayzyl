<!-- Wording for the "PrismCode" project page and its card. Edit the text under each heading; keep the "## " lines.
     *gold highlight*, **bold**. Highlights are "- " lines. Facts are "- Label: Value" lines. -->

## title
PrismCode

## subtitle

The engineering workbench behind the unified Cicero app

## status

Local workbench · evolving

## year
2026

## role
Design + engineering

## summary

PrismCode developed the engineering workspace for Claude Code, Codex and DeepSeek: agent panes, transcripts, approvals, terminals, files, Git, diffs, sessions and usage tracking. Its worktree comparison workflow puts proposed changes side by side before adoption.

That foundation is now part of the unified Cicero workbench, whose source also includes advisory Opinion, Legion and Senate dispatch, campaign tracking and a Historian. The older council prototype and installed phone companion belong to the same project lineage.

## facts

- Source stack: Electron · React · TypeScript
- Platform: Mac desktop
- Current source name: Cicero
- Gallery: Scripted interface demonstrations

## highlights

- Agent adapters normalize streamed activity into a shared interface
- Separate Git worktrees support side-by-side comparison
- A mock-agent development loop for interface work
- Advisory and dispatch workflows brought together in the unified source

<!-- Proposed engineering-story wording, added by an agent on 2026-10-04 for Sage’s review.
     Existing authored wording above is preserved. Edit these fields through /edit. -->
## story.focus
Make agent work comparable.

## story.contribution
Design and engineering of a desktop workbench for Claude Code, Codex and DeepSeek, with shared session and review surfaces.

## story.decision.1.title
Normalize at the adapter boundary

## story.decision.1.text
Agent-specific adapters emit one event contract for streamed text, tool activity, file changes and permission requests, keeping provider details out of the shared interface.

## story.decision.2.title
Put review before adoption

## story.decision.2.text
The comparison workflow presents proposed changes side by side with explicit choices to apply one result or keep both. A mock-agent loop supports interface development.

## story.outcome

The scripted demonstrations show the comparison and diff views from the engineering workbench. They illustrate that interface, rather than a benchmark or a fresh runtime test of the unified Cicero app.

## story.evidence
Scripted demo
