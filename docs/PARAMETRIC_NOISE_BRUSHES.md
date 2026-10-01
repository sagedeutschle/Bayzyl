# Parametric Noise Brushes (Gen Brushes)

A family of brushes that mimic vanilla worldgen on demand: ridges, plateaus, valleys, ravines, caves, dunes, mesas, peaks, cliffs, boulders, scree. Each brush reads the terrain around the target before placing anything so the result reads as if vanilla had always put it there — whether you're on a superflat slab or in the middle of an Amplified world.

Designed to sit alongside `/genfeature`, `/genstructure`, `/forestgen`, and `/generatebiome`.

## Why this exists

`/sphere stone 12` gives you a stone sphere. That's the right output if you wanted a sphere. It is not the right output if you wanted "a hill that looks like Minecraft put it here." For natural-looking terrain a builder typically has to layer half a dozen WorldEdit brushes by hand — `naturalize`, `smooth`, paint passes for grass/dirt/stone, then more smoothing. Gen brushes encode that workflow into one click per feature.

Each gen brush:

1. **Probes the environment** — biome family, surface block, subsurface, deep fill, water level, terrain shape.
2. **Picks a palette** from what it found — grass-on-dirt-on-stone in plains, sand-on-sandstone in deserts, netherrack-on-blackstone in nether, deepslate-on-stone in deep caves.
3. **Generates noise-driven geometry** sized to fit the surroundings — a ridge on a superflat slab gets a different default height than the same brush on a mountain biome.
4. **Blends the edges into existing terrain** so the seam is invisible.

Everything goes through `HistoryService#record` so each click is one undoable action via `/undo` or `/oops`.

## Commands

```text
/brushgen <type> [subtype] [options]
/brush gen <type> [subtype] [options]
/brush noise <type> [subtype] [options]      # smart-dispatch: gen if <type> is a gen brush, otherwise the existing pattern brush
```

All three forms produce the same result. `/brush noise <block>` still works for the pattern-noise brush — the dispatcher only routes to gen when the second arg is a recognized gen type.

After binding, right-click terrain to apply. `/mask`, `/size`, and `/brush none` work on the bound brush.

## Types

| Type      | Default radius | What it does |
|-----------|----------------|--------------|
| `ridge`   | 10             | Raises a long noisy crest. Uses ridged-fbm so peaks read as a vanilla mountain range, not a smooth hump. |
| `plateau` | 10             | Flat-topped table with falloff edges. `edges:smooth` or `edges:cliff`. |
| `valley`  | 10             | Broad meandering depression. Auto-fills with water if floor drops below sea level. `river:true` for a tighter channel. |
| `basin`   | 10             | Circular bowl. Same auto-water rule as valley. |
| `erode`   | 10             | Adds vanilla-style noise to clean terrain. Shaves crests, scatters pebbles, drops debris. The cleanup pass for sphere/cylinder shapes. |
| `ravine`  | 5              | Vertical chasm. Direction follows your facing unless overridden. Optional bridges. |
| `cave`    | 8              | 3D cave carve. Subtypes pick silhouette + palette. See below. |
| `dunes`   | 10             | Sand wave ridges. Wavelength runs perpendicular to crest direction. |
| `mesa`    | 10             | Banded plateau in badlands palette. Optional terraces. |
| `peak`    | 10             | Sharp single-summit mountain. Snow cap above `snowline`. |
| `cliff`   | 10             | Directional vertical cliff face. |
| `boulder` | 6              | Scattered rock blobs with Worley-distributed centers. |
| `scree`   | 6              | Loose rock debris draped down slopes. |

Hard radius cap: 48. Hard volume cap: 750k blocks per click.

## Cave subtypes

```text
/brushgen cave <subtype> [options]
```

| Subtype     | Look | Palette accent |
|-------------|------|----------------|
| `auto`      | Picks a subtype from the environment (lush biome → lush, badlands → cheese, deep Y → deep_dark, etc.) | — |
| `noodle`    | Thin meandering tunnels (vanilla noodle caves) | none |
| `cheese`    | Large round chambers (vanilla cheese caves) | gravel floor |
| `spaghetti` | Long winding worm-like tunnels | none |
| `dripstone` | Cheese chambers with dripstone block accents and pointed dripstone stalactites | dripstone block + pointed dripstone |
| `lush`      | Cheese chambers draped with moss carpet, hanging roots, clay | moss + hanging roots |
| `deep_dark` | Cheese chambers in deepslate with sculk patches | sculk + sculk veins |
| `aquifer`   | Spaghetti tunnels mostly flooded with water | water |
| `regular`   | Generic perlin worm | none |

Tab-completion offers all subtypes after `/brushgen cave <tab>`.

## Universal options

Every gen brush accepts:

| Option | Type | What it does |
|--------|------|--------------|
| `size:<n>` / `radius:<n>` / `r:<n>` | int 1–48 | Brush radius. |
| `intensity:<0..1>` | float | Overall amplitude multiplier. 1.0 = full effect. |
| `mask:<blocks>` | block list | Only mutate columns whose surface matches. |
| `adapt:on\|off` | bool | Environment probe + palette derivation. Default on. Turn off for a stone-only palette. |
| `seed:<n>` | long | Deterministic seed. Same seed + same target = same result. Default is current time. |

## Per-type options

### `ridge`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `height:<n>` | int | auto from env | Max crest height. |
| `steepness:<0..1>` | float | 0.6 | Edge falloff sharpness. Higher = more "wall-like". |
| `crest:<0..1>` | float | 0.75 | Sharpness of the ridge peaks. Higher = thinner crest. |
| `warp:<n>` | float | 1.2 | Domain-warp strength. Bigger = more crooked silhouette. |
| `blend:<0..1>` | float | 0.35 | Edge blend zone width. |

### `plateau`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `height:<n>` | int | auto | Top height above anchor surface. |
| `flatness:<0..0.9>` | float | 0.65 | How much of the radius is the flat top before the edge starts. |
| `edges:smooth\|cliff` | string | smooth | Edge profile. |
| `roughness:<0..1>` | float | 0.18 | Tiny noise on the top so it's not a perfect disc. |
| `blend:<0..1>` | float | 0.25 | Edge seam blend. |

### `valley`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `depth:<n>` | int | auto | Max carve depth. |
| `width:<0.2..1.0>` | float | 0.7 | Channel width within the brush radius. |
| `meander:<0..1>` | float | 0.5 | Domain-warp meander. |
| `water:auto\|on\|off` | enum | auto | Liquid fill. `auto` fills if floor < sea level. |
| `river:true` | bool | false | Forces a narrow river channel rather than a broad valley. |

### `basin`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `depth:<n>` | int | auto | Max carve depth. |
| `flatness:<0..0.85>` | float | 0.5 | Flat-floor fraction. |
| `water:auto\|on\|off` | enum | auto | Liquid fill. |
| `roughness:<0..1>` | float | 0.15 | Floor noise. |

### `erode`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `passes:<n>` | int | 1 | Number of passes to run in one click. |
| `aggression:<0..1>` | float | 0.6 | How hard to displace columns. |
| `cracks:<0..1>` | float | 0.35 | Worley crack notch chance. |
| `debris:true\|false` | bool | true | Drop occasional accent blocks. |

### `ravine`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `length:<n>` | int | radius * 4 | Length along travel axis. |
| `depth:<n>` | int | radius * 3 | Total vertical reach. |
| `jaggedness:<0..1>` | float | 0.55 | Sideways jitter. |
| `bridges:<0..1>` | float | 0.25 | Probability of leaving bridge slabs. |
| `width:<n>` | float | 1.0 | Width multiplier. |
| `direction:<n\|s\|e\|w\|...>` | enum | player facing | Travel direction. |

### `cave`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `vertical:<n>` | int | auto | Vertical reach. |
| `density:<0..1>` | float | 0.55 | Carve threshold. Higher = more air. |
| `frequency:<n>` | float | varies | Noise frequency. |
| `verticality:<0..1>` | float | 0.35 (spaghetti) | Bias toward vertical tunnels. |

### `dunes`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `height:<n>` | int | radius / 3 | Max dune height. |
| `wavelength:<n>` | float | ~radius/2 | Distance between crests. |
| `direction:<n\|s\|e\|w>` | enum | player facing | Crest line direction. |

### `mesa`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `height:<n>` | int | radius | Total height. |
| `banding:<0..1>` | float | 0.85 | Strict band ordering (vs random palette fill). |
| `terraces:<n>` | int | 3 | Step count. 0 = smooth top. |
| `blend:<0..1>` | float | 0.18 | Edge blend. |

### `peak`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `height:<n>` | int | radius * 2 | Max height. |
| `sharpness:<0..1>` | float | 0.7 | Tip sharpness. |
| `snowline:<0..1>` | float | 0.7 | Where snow cap starts (fraction of height). |

### `cliff`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `height:<n>` | int | radius | Cliff height. |
| `steepness:<0..1>` | float | 0.85 | Edge sharpness — 1.0 = vertical wall. |
| `direction:<n\|s\|e\|w>` | enum | player facing | Direction the high side faces. |

### `boulder`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `count:<n>` | int | radius/2 | Number of boulders. |
| `radius:<n>` | int | radius/3 | Max boulder radius. |
| `cluster:<0..1>` | float | 0.4 | Pull centers toward brush center. |
| `block:<material>` | string | env-derived | Force a specific block. |

### `scree`
| Option | Type | Default | What it does |
|--------|------|---------|--------------|
| `density:<0..1>` | float | 0.65 | Coverage chance. |
| `block:<material>` | string | env accent | Force a specific block. |

## Examples

```text
/brushgen ridge height:18 crest:0.9 steepness:0.4
/brushgen plateau height:8 flatness:0.7 edges:cliff
/brushgen valley depth:10 water:auto river:true
/brushgen cave lush size:14 density:0.7
/brushgen cave dripstone size:10
/brushgen cave deep_dark size:18 vertical:8
/brushgen dunes height:6 wavelength:12 direction:east
/brushgen mesa height:14 terraces:4 banding:0.9
/brushgen peak height:30 sharpness:0.85 snowline:0.6
/brushgen ravine depth:24 bridges:0.3 jaggedness:0.7
/brushgen boulder count:8 cluster:0.6
/brushgen scree density:0.8

# Same effects via /brush:
/brush gen ridge height:18 crest:0.9
/brush noise plateau height:8 flatness:0.7 edges:cliff
```

## Environment adaptation

Pass `adapt:off` to disable the probe and use a generic stone palette. With `adapt:on` (the default) every brush samples a 25×25 box around the target:

- Surface block (most common block at the top of each column)
- Subsurface (2 blocks down)
- Fill (5 down)
- Deep (8 down)
- Biome and biome family
- Water level
- Terrain shape — superflat / flat / rolling / hilly / mountain / peak / underwater / void

Then the palette is picked:

| Env signal | Cap | Sub | Fill | Core | Accent |
|------------|-----|-----|------|------|--------|
| Plains/grass | grass_block | dirt | stone | stone or deepslate | cobble |
| Desert (sand) | sand | sand | sandstone | stone | sandstone |
| Badlands (red sand) | red_sand | red_sand | red_sandstone | stone | red_sand |
| Snowy biome | snow_block | dirt | stone | deepslate | podzol |
| Taiga | grass_block | dirt | stone | deepslate | podzol |
| Jungle / lush | grass_block | dirt | stone | deepslate | moss_block |
| Cherry grove | grass_block | dirt | stone | deepslate | pink_petals |
| Nether | netherrack | netherrack | netherrack | blackstone | soul_soil |
| End | end_stone | end_stone | end_stone | end_stone | end_stone |

The probe also influences default heights — a ridge on a superflat slab defaults to half the brush radius, the same ridge in an Amplified biome defaults to roughly double the radius, so the result fits the terrain it lands in.

## Architecture

```
com.bayzyl.gen/
├── GenBrushType.java            enum: RIDGE, PLATEAU, VALLEY, BASIN, ERODE,
│                                       RAVINE, CAVE, DUNES, MESA, PEAK,
│                                       CLIFF, BOULDER, SCREE
├── CaveSubtype.java             enum: AUTO, NOODLE, CHEESE, SPAGHETTI,
│                                       DRIPSTONE, LUSH, DEEP_DARK, AQUIFER,
│                                       REGULAR
├── GenBrushSettings.java        record (type, caveSubtype, radius, params,
│                                        mask, adapt, seed)
├── GenBrushParameters.java      typed view over key:value option pairs
├── GenBrushService.java         orchestrator + per-player cooldown + history
├── GenBrushRegistry.java        type → generator lookup
├── GenBrushCommandParser.java   parses /brushgen tail into settings
├── GenChangeCollector.java      stage-and-commit helper, bedrock-safe
├── primitives/
│   ├── FbmNoise.java             value-noise fbm + ridged + billow variants
│   ├── DomainWarp.java           offset coords for organic silhouettes
│   ├── Worley.java               cellular noise (boulders, cracks, rooms)
│   └── Falloff.java              linear / smooth / smoother / plateau / cliff
├── env/
│   ├── EnvironmentProbe.java     samples surface / biome / shape / palette
│   ├── EnvironmentSample.java    immutable snapshot
│   ├── BiomeFamily.java          coarse biome buckets
│   └── TerrainShape.java         superflat / flat / rolling / hilly / etc.
├── palette/
│   ├── TerrainPalette.java       cap / sub / fill / core / accent / wet
│   └── CavePalette.java          cave-subtype specific accent + liquid
└── generators/
    ├── GenBrushGenerator.java    interface
    ├── GenHelpers.java           column painters / surface finders
    ├── RidgeGenerator.java
    ├── PlateauGenerator.java
    ├── ValleyGenerator.java
    ├── BasinGenerator.java
    ├── ErodeGenerator.java
    ├── RavineGenerator.java
    ├── CaveGenerator.java        dispatches by CaveSubtype
    ├── DunesGenerator.java
    ├── MesaGenerator.java
    ├── PeakGenerator.java
    ├── CliffGenerator.java
    ├── BoulderGenerator.java
    └── ScreeGenerator.java
```

### Click flow

```
PlayerInteractEvent
  → BayzylListener#onInteract
    → toolType == GEN_BRUSH
      → BayzylListener#handleGenBrush
        → ToolManager#readGenBrushSettings (from held item PDC)
        → GenBrushService#apply
          → EnvironmentProbe#probe
          → registry.get(type).generate(ctx)
          → GenChangeCollector#commit
          → HistoryService#record  (one click = one /undo)
```

### Adding a new generator

1. Add a value to `GenBrushType`.
2. Implement `GenBrushGenerator` in `com.bayzyl.gen.generators`.
3. Register it in `GenBrushRegistry`.
4. Add per-type options to `BayzylCommand#perTypeOptions`.
5. Update `plugin.yml` and `CommandRegistry` usage strings to list the new type.
6. Document the new type and its options in this file.

## Open design questions

- **Path-aware strokes.** Holding right-click currently retriggers a fresh stamp after a 250ms cooldown. For ravines and valleys a path-aware stroke (drag-along-target) would be a better fit, similar to the detail brush stroke mode.
- **Per-player saved variants.** Detail brushes have `/db save <name>`. Gen brushes inherit the standard `/brush save <name>` workflow for now; a richer per-type variant gallery might be worth doing once usage patterns settle.
- **Selection-bounded gen.** Today a gen brush stamps relative to the click target. A selection-bounded mode (apply within the current selection only) would help builders who want to remodel a specific zone.

## Related

- `MISC/bzlV2.md` §3 "Vanilla Generator Tools" — the original design sketch for this surface.
- `DETAIL_BRUSHES.md` — adjacent system for non-terrain detail (fire, clouds, lightning, vines, bark). Shares the PDC pattern and history integration.
- `BayzylCommand#perTypeOptions` — per-type autocomplete data.
