# Bayzyl

**An in-game building toolkit for Paper servers.**

Selections, brushes, shapes, shared kits, and builder profiles — with a command surface that aims to be approachable for newer builders while staying useful for those already comfortable in WorldEdit or FAWE.

---

## Overview

Bayzyl is a full editing workflow for Paper servers — selections, shapes, brushes, clipboards, history, shared kits, and builder profiles — packaged as a consistent, builder-friendly command set.

Bayzyl is designed to play nicely with [WorldEdit](https://enginehub.org/worldedit/) and [FastAsyncWorldEdit](https://www.spigotmc.org/resources/fastasyncworldedit.13932/) — both are great tools and Bayzyl is happy sitting alongside them. It softdepends on FAWE so large-region operations get FAWE's speed when it's installed. If your server doesn't run either, Bayzyl falls back to its own implementations for every core editing, selection, shape, brush, and clipboard feature.

**What Bayzyl focuses on**

- **Consistent command syntax** — every option uses `option:value` form, and tab-completion is aware of selections, masks, distributions, and player context. `/bzlhelp` indexes everything.
- **Forgiving by default** — confirmations on large operations, per-player undo history that persists across restarts, and `/oops` to undo with a broadcast so your teammates know.
- **Built for teams** — shared builder kits with themes, icons, aliases, and notes; named profiles for full loadout switching; server-side selection bookmarks.
- **Quality of life** — auto-unstick, ghost-hand mode, ruler, surface/ascend/descend teleports, snap-to-cardinal alignment, RAM alerts, and a tab info panel.

---

## Features

### Selection & navigation
`/wand` `/selcorners` `/selswap` `/selsave` `/selload` `/selcenter` `/expand` `/contract` `/whereami` `/ruler` `/measure` `/surface` `/ascend` `/descend` `/align` `/ceil` `/centerme` `/thru` `/unstick`

### Edit operations
`/set` `/replace` `/copy` `/cut` `/paste` `/move` `/stack` `/rotate` `/flip` `/walls` `/overlay` `/smooth` `/naturalize` `/cleanup`

### Shapes
`/sphere` `/hsphere` `/dome` `/hdome` `/bowl` `/hbowl` `/cyl` `/hcyl` `/pyramid` `/hpyramid` `/generate` (formula-driven)

### Brushes
Bind reusable brushes to any held item with `/brush sphere|hsphere|cyl|hcyl|pyramid|hpyramid|clipboard|paint|naturalize|smooth|raise|lower|flatten|erase|surface|noise|spatter|blend|vegetation|decay` and more. Live-tweak them with `/mask` `/material` `/size` `/density` without rebinding.

### Generation
`/forestgen` `/pumpkins` `/generatebiome` `/biomeinfo`

### Builder profiles & shared kits
Save a complete loadout (toolbar, runtime toggles, preferences) as a profile. Share themed kits across the server with `/kit menu`, `/kitmake`, `/kitupdate`. Kits support themes, icons, aliases, and inline notes.

### History
`/undo` `/redo` `/oops` (broadcast undo) — persistent across server restarts, per-player.

### Server-side QoL
RAM alerts with configurable thresholds, a tab info panel, decoy player count for events, runtime admin/builder toggles.

### BayzylBridge (optional companion)
A separate plugin that pipes in-game chat to an LLM so builders can ask questions about a build, get suggestions, or run vision queries — with per-player persistent memory.

---

## Install

1. Download the latest `bayzyl.jar` from the [Releases page](https://github.com/sagedeutschle/Bayzyl/releases).
2. Drop it into your server's `plugins/` folder.
3. Restart the server.
4. *(Optional)* Install [FastAsyncWorldEdit](https://www.spigotmc.org/resources/fastasyncworldedit.13932/) for accelerated large-region operations. Bayzyl auto-detects it.

**Requirements**

- Paper 1.21 or newer
- Java 21+

Spigot, Folia, Forge, and Fabric ports are on the v2 roadmap.

---

## Quick start

```
/wand                        # get the selection wand
/sphere stone 12             # build a stone sphere centered on you
/brush sphere stone 5        # bind a stone-sphere brush to your held item
/mask grass_block,dirt       # mask: only affect grass and dirt
/paste at:target             # paste your clipboard at the block you're looking at
/oops                        # undo and broadcast
/bzlhelp                     # browse all topics
```

Block distributions work everywhere a block is accepted:

```
/set 60%stone,30%cobblestone,10%mossy_cobblestone
/brush paint 5 oak_leaves density:0.3 mask:grass_block
```

---

## Build from source

```bash
git clone https://github.com/sagedeutschle/Bayzyl.git
cd Bayzyl/paperdevelopment
./gradlew build
```

The compiled jar lands in `paperdevelopment/build/libs/`.

---

## Repository layout

- `paperdevelopment/` — active Bayzyl Paper plugin source
- `personalbridge/` — BayzylBridge companion plugin (separate)
- `releases/` — public release artifacts
- `versionports/` — work-in-progress ports to other server platforms
- `MISC/` — design docs and unrelated material (including `bzlV2.md`)

---

## Roadmap

Active design work for v2 lives in [`MISC/bzlV2.md`](MISC/bzlV2.md). Highlights:

- **BzlBlender** — optional Fabric companion mod for client-side ghost rendering and floating GUI panels
- **Redstone Audit** — static analysis of redstone circuits with fault localization
- **Vanilla generator tools** — Bayzyl-style wrappers around `/place feature`, bounded chunk regen, parametric noise brushes
- **Server maintenance pillar** — tick profiling, build impact reports, optimization suggestions
- Multi-platform ports: Spigot, Folia, Forge, Fabric

---

## Contributing

Bayzyl is in active solo development. Issues and pull requests are welcome — please open an issue first for anything non-trivial so we can sync on direction before code is written.

## License

Bayzyl is released under the [MIT License](LICENSE). Use it on personal or commercial servers, fork it, redistribute it — just keep the copyright notice.

---

*Built by [@sagedeutschle](https://github.com/sagedeutschle).*
