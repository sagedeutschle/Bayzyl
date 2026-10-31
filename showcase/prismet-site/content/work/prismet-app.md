<!-- Wording for the "Prismet" project page and its card. Edit the text under each heading; keep the "## " lines.
     *gold highlight*, **bold**. Highlights are "- " lines. Facts are "- Label: Value" lines. -->

## title
Prismet Arcade

## subtitle

A home for classic games and useful lenses, built for Apple devices

## status

iPhone build installed · in development

## year
2026

## role

Co-development · product, design and Apple-platform engineering

## summary

Co-developed with Ben, Prismet brings classic games and useful data lenses into one library. The iPhone home includes search, favorites and a place to continue saved games. Its source spans iPhone, iPad and Mac, with separate browser adaptations you can play here. Each game keeps its own presentation, from chess boards and a 3D cube to Sudoku sheets and card tables.

## facts
- App Store: Version 1.3 · released 13 October 2026
- Installed iPhone: 1.3 (19), observed 31 October 2026
- Native work: iPhone · iPad · Mac
- Collaboration: Co-developed with Ben

## highlights

- An installed iPhone library with search, favorites and Continue
- Chess with 2D and 3D presentation and Stockfish opponent controls
- A SceneKit Rubik’s Cube and individually styled games
- Daily Wordgame infrastructure shared with prismet.xyz
- Began as Chess Hotswap, exploring a switch between 2D and 3D chess

<!-- Proposed engineering-story wording, added by an agent on 2026-10-04 for Sage’s review.
     Existing authored wording above is preserved. Edit these fields through /edit. -->
## story.focus
A collection with individual character.

## story.contribution

Co-developed across product, design, iOS and macOS engineering, and release work, alongside Ben.

## story.decision.1.title
Give each game its own interface

## story.decision.1.text
Chess exposes opponent and board controls; Sudoku uses a number sheet; Solitaire keeps the card table. A shared library brings those distinct experiences together.

## story.decision.2.title
Separate rules from presentation

## story.decision.2.text
The browser ports expose game state and actions separately from rendering. Seeded replay fixtures compare 2048, Minesweeper and Lights Out with native results.

## story.outcome

Explore the native captures, then play the browser collection. Selected browser rules and portable saves have native reference fixtures; the native and browser collections have their own feature and release histories.

## story.evidence

Co-developed · native captures + browser adaptations
