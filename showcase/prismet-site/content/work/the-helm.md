<!-- Wording for the "THE HELM" project page and its card. Edit the text under each heading; keep the "## " lines.
     *gold highlight*, **bold**. Highlights are "- " lines. Facts are "- Label: Value" lines. -->

## title
Helm

## subtitle
A command bridge for the home fleet.

## status
Daily driver

## year
2026

## role
Design + engineering

## summary
Helm runs the machines at home. On the desktop it is a full KDE Plasma 6 system: 28 native QML widgets for telemetry, network, storage, and arcade games, fullscreen app overlays, floating toys, a live packet scope, and a widget locker that docks and deploys faces on command. One helm command drives it all, and a written design-language spec keeps every face consistent.

In your pocket it is a native iPhone master remote for home services, lifecycle controls, backups, consoles, and quick actions.

## facts
- Widgets: 28 QML faces
- Remote: native iPhone app
- VSCODIUM: Custom built IDE
- Install: Best on Mac or Linux

## highlights
- The iPhone Mesh tab lists hosts and Minecraft servers with live status dots, resource use, and tap-to-wake
- Faces render standalone with sample data, so every widget can be previewed and screenshotted headless
- Shared chrome and palette tokens synced into every widget
- Arcade faces (Breakout, Minesweeper, Snake, Orbital Defense) next to CPU, GPU, and fleet telemetry

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
The gallery shows rendered clock, CPU, GPU, storage and process views. These specimens demonstrate the interface and sample-data presentation, rather than a live machine reading.

## story.evidence
Rendered widgets · sample data
