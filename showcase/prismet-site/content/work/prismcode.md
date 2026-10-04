<!-- Wording for the "PrismCode" project page and its card. Edit the text under each heading; keep the "## " lines.
     *gold highlight*, **bold**. Highlights are "- " lines. Facts are "- Label: Value" lines. -->

## title
PrismCode

## subtitle
Mission control for Claude Code, Codex, and DeepSeek

## status
Building

## year
2026

## role
Design + engineering

## summary
A desktop IDE where three coding agents work side by side over one workspace, each normalized into a single event stream. PRISM A/B races Claude against Codex on the same prompt in separate git worktrees, then diffs the results so you keep the winner. Sessions resume, accounts hot-swap, and a usage tracker shows spend per account.

## facts
- Agents: Claude Code · Codex · DeepSeek
- Platform: Mac
- Version: 0.1.0
- Tests: 51 passing (Vitest)

## highlights
- One adapter per agent, one AgentEvent contract: adding an agent needed no UI work
- Git worktree racing with compare, apply-winner, and keep-both
- Zero-token dev loop with a mock agent

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
The scripted demonstrations show the comparison and diff views. They illustrate the review workflow; they do not establish real agent performance or production usage.

## story.evidence
Scripted demo
