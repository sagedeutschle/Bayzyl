# Detail Brushes

Preset paint brushes for Minecraft, modeled after the curated brush libraries in drawing apps (Photoshop, Procreate). Each preset is a self-contained generator that knows how to render one specific kind of detail: fire, lightning, clouds, vines, roots, or bark. Parameters should be builder-facing and meaningful, not implementation trivia.

## Why this exists

WorldEdit-style brushes are universal but generic: a sphere of stained glass with random weights does not look like fire. Real glasswork builds (GoodTimesWithScar's burning trees, large-scale flames, flowing water) are layered: a dense hot core, a sparser warm mid-zone, a wispy cool edge, all with noise-driven holes so the surface reads as flame instead of candy.

Detail brushes encode that knowledge per-preset, expose only parameters a builder would actually want to tune, and let players save or share tuned variants through names and compact brush codes.

## Agent Onboarding

If you are new to this subsystem, read in this order:

1. `README.md` for workspace rules.
3. This file for stable detail brush architecture.
4. `BayzylListener#handleDetailBrush` for input/targeting behavior.
5. `DetailBrushPresetRegistry` plus the preset file you need to change.

When changing a preset, keep command autocomplete, built-in variants, docs, and share-code compatibility in mind.

## Architecture

```
com.bayzyl.detail/
├── DetailBrushPreset.java          interface every preset implements
├── DetailBrushFamily.java          VOLUMETRIC | LINEAR | SURFACE
├── DetailBrushParameterSpec.java   schema entry: name, type, range, default, description
├── DetailBrushParameters.java      typed accessor over a Map<String,String>
├── DetailBrushSettings.java        presetId + parameters + mode
├── DetailBrushMode.java            STAMP | STROKE
├── DetailBrushPresetRegistry.java  preset lookup + built-in variants
├── DetailBrushService.java         orchestrator: routes apply, records history
├── DetailBrushVariantService.java  per-server YAML storage of named variants
├── DetailBrushCodeCodec.java       compact share-code encode/decode
├── primitives/
│   ├── PaletteZones.java           core/mid/edge weighted material picker
│   ├── NoiseField.java             value-noise field with deterministic seed
│   └── FalloffCurve.java           linear / smooth / sharp falloff helpers
└── presets/
    ├── FlamePreset.java            volumetric fire and smoke details
    ├── CloudPreset.java            volumetric cloud masses
    ├── LightningPreset.java        linear branching bolts
    ├── VinePreset.java             linear foliage drapes
    └── BarkPreset.java             surface trunk grain detailing
```

### Three preset families

Each family shares the framework but has a different placement loop:

- **VOLUMETRIC** — fill a 3D region using palette zones, falloff, and noise. Flame, cloud, smoke, mist, wave.
- **LINEAR** — trace a path with optional branching. Lightning, vine, crack.
- **SURFACE** — wrap or scatter on existing geometry, not air. Bark, moss, ember.

All three families produce `List<BlockChange>` so that one click = one undoable action through the existing `HistoryService`.

### Click flow

1. Player binds a detail brush via `/detailbrush tool <preset>`. The preset id, parameters, and mode are serialized into the held item's PDC alongside the existing brush PDC namespace.
2. While use is held, `BayzylListener#handleDetailBrush` reads the settings, resolves a stable paint target, and calls `DetailBrushService#apply`. Use-button painting ignores only blocks recorded as placed by this same detail brush signature; identical materials already present in a build are treated as normal build blocks.
3. While break is held, the listener resolves a matching recorded detail block and extends from it, so the builder can deliberately grow flame/detail outward.
4. The service looks up the preset, generates a deterministic seed from the player UUID + a stamp counter, and calls `preset.apply(player, target, parameters, seed)`.
5. The returned `List<BlockChange>` is recorded as a single edit action — `/undo` rolls back one paint application.

## v1 status (this branch)

Shipped:
- Framework, primitives, registry, service, variant persistence
- `FlamePreset`, `CloudPreset`, `LightningPreset`, `VinePreset`, `BarkPreset`
- `/detailbrush` command surface: `tool`, `set`, `info`, `presets`, `save`, `load`, `code`, `getcode`, `variants`, `delete`, `mode`, `none`
- Held-use paint handling with edit-history integration: use paints stable surfaces, break extends matching detail
- Tab autocomplete for sub-commands, presets, params, modes
- Built-in variant quick-loads through `/db <variant>` and safe direct shortcuts such as `/bonfire`
- Universal detail brush share codes through `/db getcode` and `/db code <code>`
- ToolType `DETAIL_BRUSH` and ToolManager bind/read/unbind paths

Not yet shipped:
- Full path-aware stroke interpolation — current held-use painting applies repeated stamps
- Book editor for parameter sheets
- Rich share-code import/export UI beyond the current `/db getcode` and `/db code <code>` commands
- `/detailbrush capture` (analyze a selection and fit a preset variant to it) — the headline feature, planned after the five base presets exist

## Adding a new preset

1. Create a class in `com.bayzyl.detail.presets` implementing `DetailBrushPreset`.
2. Declare parameters in `parameterSpecs()` with descriptive names that mean something to a builder ("heat", "flicker") rather than implementation names ("noise-frequency", "octaves").
3. Implement `apply(player, target, parameters, seed)` — return a `List<BlockChange>`. Use the primitives (`PaletteZones`, `NoiseField`, `FalloffCurve`) for shared math.
4. Register in `DetailBrushPresetRegistry`'s constructor.
5. Register useful built-in variants in `DetailBrushPresetRegistry`; `/db` tab suggestions are generated from the registry and preset parameter specs.
6. Document the parameter set in this file.

## FlamePreset reference parameters

| Parameter | Type | Range | Default | What it does |
|-----------|------|-------|---------|--------------|
| `heat` | float | 0.0–1.0 | 0.7 | Palette balance. 0 = dark embers, 1 = white-hot core. |
| `height` | int | 1–64 | 8 | Vertical extent in blocks. |
| `width` | int | 1–24 | 3 | Base radius in blocks. |
| `flicker` | float | 0.0–1.0 | 0.4 | Edge perturbation. Higher = more wispy. |
| `lean_x` | float | -1.0–1.0 | 0.0 | Top tilt along +x. 1 = lean a full width over the height. |
| `lean_z` | float | -1.0–1.0 | 0.0 | Top tilt along +z. |
| `density` | float | 0.0–1.0 | 0.85 | Overall placement chance multiplier. |

Current palette logic favors yellow/orange/red stained glass with glowstone/magma accents. White glass is now limited to hotter cores so fire does not wash out.

## CloudPreset parameters

| Parameter | Type | Range | Default | What it does |
|-----------|------|-------|---------|--------------|
| `volume` | int | 1-32 | 6 | Overall radius of the cloud mass. |
| `puffiness` | float | 0.0-1.0 | 0.6 | Lobe breakup. Higher values make broken puffs. |
| `density` | float | 0.0-1.0 | 0.7 | Fill chance multiplier. |
| `flatness` | float | 0.0-1.0 | 0.35 | Vertical squash. |
| `opacity` | float | 0.0-1.0 | 0.72 | Solid concrete/snow weight versus glass weight. |
| `tint` | string | white/gray/sunset/storm | white | Palette family. |

Clouds currently use concrete, snow, and some stained glass. Wool was intentionally removed after playtest feedback.

## LightningPreset parameters

| Parameter | Type | Range | Default | What it does |
|-----------|------|-------|---------|--------------|
| `length` | int | 2-96 | 14 | Main bolt length. |
| `jaggedness` | float | 0.0-1.0 | 0.55 | Sideways jitter. |
| `branches` | int | 0-8 | 2 | Number of forked branches. |
| `branch_length` | float | 0.0-1.0 | 0.45 | Branch length as fraction of main bolt. |
| `glow` | float | 0.0-1.0 | 0.6 | Core light-source weight. |
| `direction` | string | down/up/north/south/east/west | down | Travel direction. |
| `color` | string | blue/white/purple/yellow/red | blue | Bolt color family. |

Lightning places the first bolt block at the target before stepping, so using a lightning brush on the top face of a block should work. Purple uses crying obsidian as its core; yellow uses glowstone; red uses shroomlight; blue/white use sea lantern.

## Built-in Flame variants

Use these with `/detailbrush tool flame <variant>` or `/detailbrush load <variant>`:

| Variant | Intended use |
|---------|--------------|
| `campfire` | Small, dense, warm utility flame. |
| `bonfire` | Tall, readable fire column for build focal points. |
| `wildfire` | Wide, windy, uneven flame for burning trees and ruins. |
| `torch-flame` | Tiny vertical flame for lamps, braziers, and detail work. |
| `ember-smoke` | Sparse, low-heat ember/smoke shape using darker edge material. |
| `white-hot` | Bright core-heavy flame with a cleaner silhouette. |

## Built-in Cloud variants

| Variant | Intended use |
|---------|--------------|
| `puffy-cloud` | Round white cumulus shape for skyboxes and floating islands. |
| `thunderhead` | Heavy storm cloud with a gray gradient. |
| `wispy` | Thin, sparse cloud streak. |
| `sunset-cloud` | Pink and orange warm-light cloud. |

## Built-in Lightning variants

| Variant | Intended use |
|---------|--------------|
| `thunderbolt` | Strong jagged downward strike with blue core/aura defaults. |
| `forked-bolt` | Heavier purple forked strike with several branches. |
| `spark` | Tiny bright crackle for small accents. |
| `groundstrike` | Longer, straighter yellow cinematic strike. |

## Built-in Vine variants

| Variant | Intended use |
|---------|--------------|
| `jungle-vine` | Long, wandering jungle drape with leaves. |
| `ivy` | Short oak ivy strand for ruins and stone walls. |
| `azalea-drape` | Flowering lush-biome drape. |
| `mangrove-root` | Dangling mangrove root strands with short side branches. |

## Built-in Bark variants

| Variant | Intended use |
|---------|--------------|
| `oak-bark` | Mixed oak log, wood, and stripped grain for trunks. |
| `spruce-bark` | Vertical spruce grain with occasional knots. |
| `birch-bark` | Subtle birch grain with light stripped accents. |
| `cherry-bark` | Cherry trunk grain for ornamental builds. |

Built-in and saved variants can also be loaded with `/db <variant>`. If the variant name is not reserved and no existing command owns it, Bayzyl registers a direct shortcut such as `/bonfire`. Reserved names include Minecraft/server commands and Bayzyl command paths.

## Detail brush codes

Use `/db getcode` while holding a detail brush to print a shareable configuration code. Another player can load that exact preset and parameter set with `/db code <code>`.

Code format is compact and ordered by each preset's parameter spec:

```text
F2S(0.7,8,3,0.4,0.0,0.0,0.85)
```

The first letter is the detail brush type (`F` flame, `C` cloud, `L` lightning, `V` vine, `B` bark), the digit is the code version (current: `2`), and `S` is stamp mode. The values inside the parentheses are the preset parameters in registry order. Adding a new parameter changes that preset's code shape; bump the version and update docs/autocomplete when doing so.

Older `F1S(...)`-style codes are accepted on decode: missing trailing values are filled with current spec defaults. New encodes always emit the latest version.

## Persistence

Variants are stored in the plugin data folder as `detail-brushes.yml`:

```yaml
variants:
  tall-bonfire:
    preset: flame
    mode: STAMP
    updated_at: 1746...
    params:
      heat: 0.9
      height: 16
      width: 4
      flicker: 0.6
```

Saved variants are server-wide. Per-player namespacing is a v1.1 candidate if it turns out builders step on each other's names.

## Open design questions

- **Paint cadence vs stroke pathing** — v1 detail brushes paint repeatedly while use is held. Use-button targeting ignores only blocks recorded as placed by the same detail brush signature, so existing builds made from the same materials remain normal targets. Break-button targeting extends from matching recorded detail. Full path-aware stroke interpolation is still a v1.1 candidate.
- **Per-player vs server-wide variants** — currently server-wide for simplicity. Watch for collisions in real use before deciding.
- **Mask integration** — presets currently mostly place into air. Any future solid-replacement behavior should be explicit, bounded, and probably per-preset rather than hidden.

## Related

- `paperdevelopment/src/main/java/com/bayzyl/BrushPresetService.java` — the existing per-server brush ItemStack persistence used by `/brush save|load`. Detail brushes use a parallel system because their settings shape is different and we want them in their own YAML namespace.
- `paperdevelopment/src/main/java/com/bayzyl/HistoryService.java` — atomic undo recording. All detail brush placements go through `record(uuid, changes)` so each paint application is undoable.
