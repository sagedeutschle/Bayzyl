# Screenshot library for Fiverr

Real screenshots of real work, sorted by the gig they prove. Nothing here is a mockup.

- **Start with `00-fiverr-ready/`.** Those are already sized for Fiverr (1280×769, rendered at 2×) with a caption.
- The numbered folders hold the full-resolution originals, for portfolio projects or your own layouts.
- Fiverr accepts JPG and PNG. Gig galleries take up to 3 images; the first one is the cover.
- Designed gig covers and portfolio boards are in [`../out/`](../out/).

| Folder | Files | Use for | What's inside |
|---|---|---|---|
| [`00-fiverr-ready/`](00-fiverr-ready/) | 27 | Any gig | 1280×769 at 2× (2560×1538 JPG). Drop straight into a gig gallery or a portfolio project. |
| [`01-iphone-mac-apps/`](01-iphone-mac-apps/) | 40 | SwiftUI apps | App Store screenshots, raw iPhone screens, the 3D Catan board, the app icon, and 22 game tiles. |
| [`02-minecraft/`](02-minecraft/) | 3 | Minecraft plugins · Minecraft servers | Builds on the Paper server Bayzyl runs on (window borders trimmed). |
| [`03-linux-desktop-helm/`](03-linux-desktop-helm/) | 23 | KDE / Linux desktops | 23 custom QML widgets rendered from their source at 2× (the Fleet Radar face and the iPhone remote captures were removed: they show host names). |
| [`04-web-tools/`](04-web-tools/) | 17 | Web tools & landing pages | QR Scanner suite captures, Steam Rewind (demo library), and the Accessible Debt Clock, desktop and phone. |
| [`05-ai-agents/`](05-ai-agents/) | 6 | AI coding-agent setup | PrismCode, Cicero, HOI4 AI War Room, Usage Tracker. |
| [`06-games-and-mods/`](06-games-and-mods/) | 5 | (portfolio) | The Long Now eras, Civ V Mod Profiles, WoW Sidepanel. |
| [`07-desktop-apps/`](07-desktop-apps/) | 2 | (portfolio) | Airhorn, Quark icon. |
| [`08-prismet-site-redesign/`](08-prismet-site-redesign/) | 8 | Web tools & landing pages | Home in dark, light, and phone; Selected and All work; three project pages. |

## Rebuild

```bash
node showcase/tools/shoot-library.mjs <dir>     # live Steam Rewind / Debt Clock + the redesigned site
node showcase/fiverr/frame-screenshots.mjs      # regenerate 00-fiverr-ready/ from the originals
```

Sources: the live prismet.xyz `/shots/*-full.webp`, App Store screenshots in the Prismet repo, Helm widgets via
`showcase/tools/render-helm-faces.py` (`QT_SCALE_FACTOR=2`), and Playwright captures. Steam Rewind is shown with its
built-in demo library, not a real account. Widgets that show mesh IPs or clipboard text are left out.
