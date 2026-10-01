<!-- Wording for the "The Wizard King's Decree" project page and its card. Edit the text under each heading; keep the "## " lines.
     *gold highlight*, **bold**. Highlights are "- " lines. Facts are "- Label: Value" lines. -->

## title
The Wizard King's Decree

## subtitle
Can chat models call the news before it happens?

## status
Running daily

## year
2026

## role
Design + engineering

## summary
A council of two chat models must agree on a no-hedge prophecy about upcoming news. A separate, search-grounded Court Historian grades each decree against reality with at least two independent sources, and wrong calls trigger public corrections on an escalating ladder. Under the robes it is an auditable forecasting experiment with hit rates, bootstrap confidence intervals, and calibration.

## facts
- Models: Claude + GPT council, Gemini judge
- Tests: 237, fully offline
- Output: decrees.json → Prismet's Oracle lens

## highlights
- Every SDK, clock, and network call sits behind an injectable seam, so the whole suite runs with no keys and no network
- Append-only rulings and corrections in SQLite
- Static-site chronicle generated with the standard library only
