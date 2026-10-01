# Bayzyl

**An in-game building toolkit for Paper servers.**

Selections, brushes, shapes, shared kits, and builder profiles — with a command surface that aims to be approachable for newer builders while staying useful for those already comfortable in WorldEdit or FAWE.

---

## Overview

Bayzyl is a full editing workflow for Paper servers — selections, shapes, brushes, clipboards, history, shared kits, and builder profiles — packaged as a consistent, builder-friendly command set.

Bayzyl is designed to play nicely with [WorldEdit](https://enginehub.org/worldedit/) and [FastAsyncWorldEdit](https://www.spigotmc.org/resources/fastasyncworldedit.13932/) — both are great tools and Bayzyl is happy sitting alongside them. Bayzyl auto-detects WorldEdit-compatible plugins for supported shape operations while keeping its native edit, history, selection, brush, and clipboard behavior available without external dependencies.

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

### Detail brushes
Preset paint brushes for fire, clouds, lightning, vines, roots, and bark — each with its own purpose-built parameters (heat, flicker, height, branches, etc.) and a curated palette. `/detailbrush tool flame` to bind, `/detailbrush set heat 0.9` to tweak, `/detailbrush save mybrush` to persist a variant. Quick-load with overrides: `/db thunderbolt red`, `/db jungle-vine jungle`. See [`docs/DETAIL_BRUSHES.md`](docs/DETAIL_BRUSHES.md) for the system design.

### Generation
`/forestgen` `/pumpkins` `/generatebiome` `/biomeinfo`

### Builder profiles & shared kits
Save a complete loadout (toolbar, runtime toggles, preferences) as a profile. Share themed kits across the server with `/kit menu`, `/kitmake`, `/kitupdate`. Kits support themes, icons, aliases, and inline notes.

### History
`/undo` `/redo` `/oops` (broadcast undo) — persistent across server restarts, per-player.

### Server-side QoL
RAM alerts with configurable thresholds, a tab info panel, decoy player count for events, runtime admin/builder toggles.

---

## Install

1. Download the latest `bayzyl.jar` from the [Releases page](https://github.com/sagedeutschle/Bayzyl/releases).
2. Drop it into your server's `plugins/` folder.
3. Restart the server.
4. *(Optional)* Install [WorldEdit](https://enginehub.org/worldedit/) or [FastAsyncWorldEdit](https://www.spigotmc.org/resources/fastasyncworldedit.13932/) if you want Bayzyl to use its WorldEdit-compatible shape adapter when available.

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
cd Bayzyl
./gradlew build
```

The compiled jar lands in `build/libs/`. Requires Java 21+.

Design notes for individual systems live in [`docs/`](docs/): [detail brushes](docs/DETAIL_BRUSHES.md), [parametric noise brushes](docs/PARAMETRIC_NOISE_BRUSHES.md), and [WorldEdit parity](docs/TOOL_WORLDEDIT_PARITY.md). A Python [terrain schematic generator](terrain_schematic_generator/) is included as an experimental tool.

---

## Roadmap

Planned v2 work, highlights:

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
