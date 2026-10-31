<!-- Wording for the "THE HELM" project page and its card. Edit the text under each heading; keep the "## " lines.
     *gold highlight*, **bold**. Highlights are "- " lines. Facts are "- Label: Value" lines. -->

## title
Helm

## subtitle

A KDE desktop built like a command bridge.

## status

Personal desktop environment

## year
2026

## role
Design + engineering

## summary

Helm is a custom KDE Plasma desktop: native QML instruments for clocks, CPU, GPU, network and storage, alongside small arcade games and controls for arranging the workspace. Shared panel components and palette tokens give the instruments one visual language.

This record covers the Linux desktop and its widget design. Command Helm, the separately developed editor and phone companion, has its own project history.

## facts

- Platform: KDE Plasma 6 on Linux
- Interface: Native QML widgets
- Gallery: Standalone renders with sample data
- Role: Desktop design and automation

## highlights

- Clocks, system instruments and small games in a shared panel language
- Standalone rendering for inspecting individual QML faces
- Shared chrome and palette tokens across the widget family
- Layout controls for arranging and recalling the workspace

<!-- Proposed engineering-story wording, added by an agent on 2026-10-04 for Sage’s review.
     Existing authored wording above is preserved. Edit these fields through /edit. -->
## story.focus
A design system for useful instruments.

## story.contribution
Design and engineering of the desktop control environment, including QML widget faces and their shared visual language.

## story.decision.1.title
Share the instrument frame

## story.decision.1.text
A common panel component and palette tokens give clocks, resource meters and controls a consistent structure without making every face identical.

## story.decision.2.title
Make each face inspectable

## story.decision.2.text
Standalone sample-data rendering lets individual QML faces be previewed outside a full desktop session, including offscreen capture.

## story.outcome

The gallery shows standalone clock, CPU, GPU, storage and instrument renders with sample data. They demonstrate the widget design; the displayed values are not current machine readings.

## story.evidence
Rendered widgets · sample data
