<!-- Wording for the "Prismet" project page and its card. Edit the text under each heading; keep the "## " lines.
     *gold highlight*, **bold**. Highlights are "- " lines. Facts are "- Label: Value" lines. -->

## title
Prismet

## subtitle
A calm home for classic games, on iPhone, iPad, and Mac

## status
Live on the App Store

## year
2026

## role
Product, design, iOS + macOS engineering, release

## summary
Nineteen classic games and three live-data lenses in one app, each game with its own hand-built look. Chess plays a tunable Stockfish engine in 2D or 3D, the Rubik's Cube is a real SceneKit cube, and friends can play Sea Battle, Checkers, Reversi, Connect Four, and Gomoku online. The Mac app ships at feature parity with iOS.

## facts
- Games: 19
- Platforms: iPhone · iPad · Mac · Watch
- Codebase: ≈81k lines Swift + Python
- Tests: ≈640 Swift · 237 Python

## highlights
- Shipped v1.0 to the App Store; v1.1 rebrand submitted with saves and Game Center continuity preserved
- A 3D Catan board, rendered in its meadow theme
- Daily Wordgame fed by a self-hosted endpoint on prismet.xyz
- Light, parchment, and dark reading themes; sound and haptics on every move
- Began as Chess Hotswap, built to hot-swap between 2D and 3D chess

<!-- Proposed engineering-story wording, added by an agent on 2026-10-04 for Sage’s review.
     Existing authored wording above is preserved. Edit these fields through /edit. -->

## story.focus
A collection with individual character.

## story.contribution
Co-developed across product, design, iOS and macOS engineering, and release work.

## story.decision.1.title
Give each game its own interface

## story.decision.1.text
Chess exposes opponent and board controls; Sudoku uses a number sheet; Solitaire keeps the card table. A shared library brings those distinct experiences together.

## story.decision.2.title
Separate rules from presentation

## story.decision.2.text
The browser ports expose game state and actions separately from rendering. Seeded replay fixtures compare 2048, Minesweeper and Lights Out with native results.

## story.outcome
Explore the iPhone and iPad captures, then play the browser collection. Three portable browser save formats are checked against native fixtures; other facets keep browser-only saves.

## story.evidence
Co-developed · native captures + browser play
