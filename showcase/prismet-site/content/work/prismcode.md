<!-- Wording for the "PrismCode" project page and its card. Edit the text under each heading; keep the "## " lines.
     *gold highlight*, **bold**. Highlights are "- " lines. Facts are "- Label: Value" lines. -->

## title
PrismCode

## subtitle
Mission control for Claude Code, Codex, and DeepSeek

## status
Private build

## year
2026

## role
Design + engineering

## summary
A desktop IDE where three coding agents work side by side over one workspace, each normalized into a single event stream. PRISM A/B races Claude against Codex on the same prompt in separate git worktrees, then diffs the results so you keep the winner. Sessions resume, accounts hot-swap, and a usage tracker shows spend per account.

## facts
- Agents: Claude Code · Codex · DeepSeek
- Tests: 51 passing (Vitest)
- Ships as: signed macOS app

## highlights
- One adapter per agent, one AgentEvent contract: adding an agent needed no UI work
- Git worktree racing with compare, apply-winner, and keep-both
- Zero-token dev loop with a mock agent
