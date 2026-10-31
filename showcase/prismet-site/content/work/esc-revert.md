<!-- Project wording; the content below is intentionally separate from private inventory evidence. -->

## title
Escape Revert

## subtitle
Three Escape presses to go back.

## status
Local utility

## year
2026

## role
Small macOS accessibility and keyboard utility.

## summary
A macOS utility that turns three rapid Escape presses into the standard Back keyboard shortcut.

## facts
- Phase: Local utility
- Stack: Swift · AppKit · CoreGraphics
- Review: Source inspected · 31 October 2026

## highlights
- Detects three rapid Escape presses through a macOS event tap.
- Sends the standard Command–[ Back shortcut.
- Uses a cooldown and requires macOS Accessibility permission.
