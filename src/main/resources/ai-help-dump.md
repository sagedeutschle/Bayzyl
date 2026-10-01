# Bayzyl — AI Help Dump

Working reference for the AI agent. Comprehensive, current, raw. The user adapts entries from this into `help-pages.yml` in their own voice — the yml is what players see; this is the source.

When updating: prefer accuracy over polish. Each entry should answer "what does this do, what flags does it accept, what's the gotcha." Page numbers, color codes, and personality belong in the yml, not here.

Last sync: 0.1.1 alpha development build.

---

## 0. Conventions and shared vocabulary

- **target** — the block your crosshair is pointed at.
- **selection** — the cuboid region between pos1 and pos2, set with the wand or `/pos1` `/pos2`.
- **clipboard** — the per-player copy buffer set by `/copy` or `/cut` or by structure generation.
- **at:** anchor option on shape/generation commands. Accepts `player`, `target`, `selection-center`, `center`. Default depends on command.
- **mask:** option on shape/brush commands. Comma-separated block list; only those blocks are affected.
- **distribution** — comma-separated material list with optional weights, e.g. `stone,diorite,andesite` or `60%stone,40%glass`.
- **confirm:true** — required override for soft-warn safety tiers (large volume, large chunk span). Hard-refuse tiers cannot be overridden.
- **chunked operation** — runs in 4 ms per-tick budget at ~10k blocks/tick; server stays responsive. Triggers on volume above the per-command threshold.
- **rail prefix** — the colored `|` separator at the start of Bayzyl chat lines. Color is the configured menu accent.

---

## 1. Selection

Cuboid only as of 0.1.1. Multiple selection types are tracked internally (`SelectionType.CUBOID` is the only live one).

| Command | Description | Usage |
|---|---|---|
| `/wand` | Get the selection wand item | `/wand` |
| `/pos1` | Set pos1 to your current position (or `at:target` / `at:selection-center`) | `/pos1` |
| `/pos2` | Set pos2 to your current position (or `at:target` / `at:selection-center`) | `/pos2` |
| `/select cube <size> at:<player\|target>` | Quick selection cube around an anchor | `/select cube 16 at:target` |
| `/selcorners` | Show pos1 and pos2 coordinates | `/selcorners` |
| `/selswap` | Swap pos1 and pos2 | `/selswap` |
| `/selcenter` | Toggle a particle marker at the selection center | `/selcenter` |
| `/expand <amount> [direction]` | Grow the selection. `all <n>` grows in every direction | `/expand 5 north`, `/expand all 3` |
| `/contract <amount> [direction]` | Shrink the selection. `all <n>` shrinks in every direction | `/contract 2 up` |
| `/measure` | Report selection dimensions, volume, solid/air counts | `/measure` |
| `/biomeinfo` | Report biome composition inside the selection | `/biomeinfo` |
| `/particlevisualtoggle` | Toggle selection outline particles per-player | `/particlevisualtoggle` |

**Wand behavior:** right-click sets pos1, left-click sets pos2. Hand-placed wand item or `/wand` both work.

**Multiplayer outlines (0.1.1):** other players' selections render in a lighter outline. Color collisions render the peer's outline in the inverted/complementary palette so each viewer sees something distinct. `MAX_REMOTE_EMITS_PER_TICK = 240` keeps total particles bounded on busy servers.

**Large-selection tab info:** for selections above ~200k blocks, solid/air counts may show approximate `~` values to avoid forcing a full block scan. See `tabinfo` section.

---

## 2. Selection bookmarks

Per-player named selections, persisted to `plugins/Bayzyl/selection-bookmarks.yml`.

| Command | Description | Usage |
|---|---|---|
| `/selsave <name>` | Save the current selection by name | `/selsave temple-courtyard` |
| `/selsave share` | Print a portable share code for the current selection (0.1.1) | `/selsave share` |
| `/selload` | List all saved bookmarks for this player | `/selload` |
| `/selload <name>` | Restore a saved selection | `/selload temple-courtyard` |
| `/selload share <code>` | Apply a share code to the current selection (0.1.1) | `/selload share *1S(world;1,64,1;5,68,5;CUBOID)` |

**Name validation:** lowercase letters, digits, `_`, `-`, max 32 chars.

**Share code format (0.1.1):** `*1S(world;x1,y1,z1;x2,y2,z2;TYPE)`. Single-line, copy-pasteable. Decoder falls back to the player's current world if the encoded world name isn't loaded locally.

---

## 3. Editing — small ops

| Command | Description | Usage |
|---|---|---|
| `/set <block\|distribution> [options]` | Fill the selection | `/set stone`, `/set 60%stone,40%glass mask:air` |
| `/replace <from...> <block\|distribution> [options]` | Replace matching blocks within the selection | `/replace stone glass`, `/replace stone,cobblestone mossy_cobblestone` |
| `/walls <block\|distribution> [options]` | Walls (4 vertical sides) of the selection | `/walls stone_bricks` |
| `/overlay <block> [options]` | Place blocks on top of the selection's surface | `/overlay grass_block` |
| `/smooth [options]` | Smooth terrain in the selection | `/smooth` |
| `/naturalize [depth:<n>] [bedrock:on\|off] [confirm:true]` | Naturalize terrain (grass→dirt→stone layering) | `/naturalize depth:3` |

**Options shared:** `mask:<blocks>`, `at:<anchor>` where applicable.

---

## 4. Editing — clipboard & large ops

All four (`/copy`, `/cut`, `/paste`, `/undo`/`/redo`) share the chunked scheduler in 0.1.1. Server stays responsive during multi-million-block operations.

| Command | Description | Usage |
|---|---|---|
| `/copy [options]` | Copy the selection to clipboard | `/copy` |
| `/cut [options]` | Cut the selection to clipboard | `/cut`, `/cut confirm:true` |
| `/paste [-aons] [rotation:<deg>] [confirm:true]` | Paste the clipboard at your position | `/paste`, `/paste rotation:90` |
| `/move <distance> [direction] [-a] [confirm:true]` | Move selection contents | `/move 5 north` |
| `/stack <count> [direction] [-a] [confirm:true]` | Stack the selection along a direction | `/stack 3 up` |
| `/rotate <0\|90\|180\|270\|left\|right\|back>` | Rotate clipboard | `/rotate 90` |
| `/flip <axis>` | Flip clipboard. `x\|y\|z\|left-right\|front-back\|up-down\|left\|right\|forward\|back` | `/flip up-down` |
| `/clipboardinfo` | Show clipboard dimensions and origin | `/clipboardinfo` |
| `/clearclipboard` | Clear only the caller's current clipboard | `/clearclipboard` |

### Paste/cut safety tiers (0.1.1)

Volume-driven gating, in order:

1. **Hard refuse — volume cap (10,000,000 blocks).** No `confirm:true` override.
2. **Hard refuse — chunk-span cap (≥ 16,384 chunks horizontal).** No override.
3. **Hard refuse — entity cap (> 1,000 entities in clipboard).** No override.
4. **Hard refuse — entire paste outside world Y range.** No override.
5. **Soft warn — danger volume (2M–10M blocks).** Re-run with `confirm:true`.
6. **Soft warn — chunk-span (1,024–16,384 chunks).** Re-run with `confirm:true`.
7. **Soft warn — danger volume on cut.** Same tier.
8. **Y-shave informational note.** Skips out-of-world layers, paste proceeds, count noted.
9. **Existing 200k confirm tier.** Re-run with `confirm:true`. Below 200k runs synchronously.

### Chunked scheduler thresholds (0.1.1)

| Operation | Threshold | Per-tick budget |
|---|---|---|
| Paste | 300k blocks | 4 ms / 10k blocks |
| Cut | 300k blocks | 4 ms / 10k blocks |
| Copy | 200k blocks | 4 ms / 10k blocks |
| Undo / Redo | 50k blocks | 4 ms / 10k blocks |

### Memory & responsiveness guards

- **Memory pre-flight.** Refuse paste/cut/move/stack up front if there isn't enough free heap to record the undo. Message names byte estimates.
- **Heap-pressure abort.** At 92%+ used heap, an in-flight chunked op aborts mid-tick with placed-count reported.
- **Re-entry guard.** Second `/paste`/`/cut` while one is running → `Already pasting…` / `Already performing an edit…`. Same for `/undo`/`/redo` (`Already performing an undo/redo…`).
- **Cancel-on-disable.** Plugin disable cancels all in-flight chunked tasks; partial blocks remain placed.
- **No more 200k undo cap (0.1.1).** Pastes of any size record the full undo. In-memory entries age out via TTL eviction (default 20 minutes); persistent on-disk history is unaffected. See `EditHistory.evictOlderThan`.
- **Persisted history per-action cap.** 50k changes; oversize → metadata stub on disk (`tooLargeToPersist: true` with selection bounds preserved).

### Undo/Redo

| Command | Description | Usage |
|---|---|---|
| `/undo [steps:<n>]` | Undo last n actions (default 1) | `/undo`, `/undo steps:5` |
| `/redo [steps:<n>]` | Redo last n actions (default 1) | `/redo` |
| `/oops [steps:<n>]` | Undo with a public broadcast | `/oops` |
| `/resume` | Resume an interrupted command after server restart | `/resume` |

Chunked path triggers when total blocks across the requested steps > 50k. Progress messages at 25/50/75% during the run; final completion message reports actions and blocks.

---

## 5. Shapes

All shapes accept `<block|distribution> <radius>` as primary args, plus standard `[mask:] [at:] [confirm:true]`.

| Command | Description |
|---|---|
| `/sphere`, `/hsphere` | Solid / hollow sphere |
| `/dome`, `/hdome` | Solid / hollow dome (top half) |
| `/bowl`, `/hbowl` | Solid / hollow bowl (bottom half) |
| `/cyl <r> [height]`, `/hcyl <r> [height]` | Solid / hollow cylinder |
| `/pyramid <size>`, `/hpyramid <size>` | Solid / hollow pyramid |

**at:** anchor defaults to `player` for most shapes; `selection-center` is common for relocating an existing selection's footprint.

---

## 6. Generation — formula and feature

| Command | Description | Usage |
|---|---|---|
| `/generate <block> <expression> [mode:normalized\|raw\|center\|origin] [hollow:true] [confirm:true]` | Formula-driven shape inside the selection | `/generate stone "x*x+y*y+z*z<25"` |
| `/generatebiome <biome> [shape] [args]` | Biome fill, primitive, or formula edit | `/generatebiome plains sphere` |
| `/forestgen [size] [type] [density] [at:] [confirm:true]` | Forest patch around an anchor | `/forestgen 12 oak 0.6 at:player` |
| `/genfeature <feature_id> [at:] [confirm:true]` | Place a vanilla configured feature | `/genfeature minecraft:oak at:player` |
| `/genstructure <structure_id> [at:] [confirm:true]` | Place a vanilla structure | `/genstructure minecraft:village_plains at:target` |
| `/regen` | Reroll the last generated structure at the saved anchor | `/regen` |
| `/pumpkins [size] [at:] [confirm:true]` | Generate pumpkin patches | `/pumpkins 8 at:player` |

**Genstructure world-context fix (0.1.1):** `/place` is dispatched via `/execute in <world> run …` and chunks are pre-loaded; previously `That position is not loaded` errors fired in non-default worlds.

**Capture to clipboard:** `/genstructure` and `/genfeature` capture placement results into the clipboard so `/paste` can reposition.

---

## 7. Pattern brushes

Bind to held item, then right-click in-world. Use `/none` to unbind. Saved brushes live in `BrushPresetService` (`plugins/Bayzyl/brushes.yml`).

| Command | Description | Usage |
|---|---|---|
| `/brush <type> [params]` | Bind a pattern brush to held item | `/brush sphere stone 5` |
| `/brush save <name>` | Save the held brush | `/brush save mossy-cyl` |
| `/brush load <name>` | Bind a saved brush | `/brush load mossy-cyl` |
| `/brush list` | List saved brushes | `/brush list` |
| `/brush delete <name>` | Delete a saved brush | `/brush delete mossy-cyl` |
| `/brush info` | Inspect held brush | `/brush info` |
| `/brush none` | Unbind | `/brush none` |

**Built-in pattern brush types:**
- Shape: `sphere`, `hsphere`, `cyl`, `hcyl`, `pyramid`, `hpyramid`
- Material: `paint`, `surface`, `spatter`, `replace`, `blend`, `noise`, `vegetation`
- Cleanup: `decay`, `restore`, `erase`, `naturalize`, `smooth`
- Terrain: `raise`, `lower`, `flatten`
- Structure: `structure <id>` (see Genstructure section)
- Clipboard: `clipboard` (paste-stamp via right-click)

### Runtime tuning (modify the held brush in place)

| Command | Description |
|---|---|
| `/size <radius> [height]` | Resize the held brush |
| `/density <0.0-1.0>` | Scatter density (paint, spatter, blend, noise, surface, vegetation) |
| `/mask <blocks\|none>` | Apply or clear mask |
| `/material <block\|distribution>` | Change brush material |
| `/none` | Unbind |

---

## 8. Detail brushes

Preset-driven decorative brushes: flame, cloud, lightning, vine, bark. Each preset has variants (e.g. `bonfire` for flame, `puffy-cloud` for cloud). Variants live in `plugins/Bayzyl/detail-brushes.yml`.

| Command | Description | Usage |
|---|---|---|
| `/detailbrush` | Show subcommand help | `/detailbrush` |
| `/db <variant> [override]` | Quick-load a built-in variant onto the held item. Trailing token autofills against the variant's preset enum (color/tint/species/direction) | `/db bonfire`, `/db thunderbolt red` |
| `/db tool <preset> [override]` | Bind a fresh preset with optional override | `/db tool flame` |
| `/db set <param> <value>` | Tune the held detail brush | `/db set heat 1.0`, `/db set length 32` |
| `/db info` | Show preset, params, block estimate, cooldown | `/db info` |
| `/db presets` | List all detail brush presets | `/db presets` |
| `/db variants [preset]` | List all variants, filtered to a preset | `/db variants flame` |
| `/db save <name>` | Save the held detail brush as a variant | `/db save my-bonfire` |
| `/db load <name>` | Load a saved variant | `/db load my-bonfire` |
| `/db delete <name>` | Delete a saved variant | `/db delete my-bonfire` |
| `/db code <code>` | Apply a share code to the held item | `/db code F2S(0.7,8,3,0.4,0.0,0.0,0.85)` |
| `/db getcode` | Print a share code for the held detail brush | `/db getcode` |
| `/db mode <auto\|stable\|extend>` | Held-use behavior selector | `/db mode auto` |
| `/db undo-last [n]` | Pop the last n actions (default 1, max 32) | `/db undo-last 3` |
| `/db none` | Unbind | `/db none` |

### Held-use behavior

- **right-click (use):** paint at stable target.
- **left-click (break):** extend from matching brush-created blocks.
- **Placement-identity tracking:** hand-placed matching materials are not treated as brush output; only the brush's own writes count for the extend-from logic.

### Per-preset stamp cooldowns (0.1.1 first pass; tune in playtest)

| Preset | Cooldown |
|---|---|
| flame | 2t |
| cloud | 3t |
| lightning | 6t |
| vine | 4t |
| bark | 3t |

### Built-in variants (current set)

- **Flame:** `campfire`, `bonfire`, `wildfire`, `torch-flame`, `ember-smoke`, `white-hot`
- **Cloud:** `puffy-cloud`, `thunderhead`, `wispy`, `sunset-cloud`
- **Lightning:** `thunderbolt`, `forked-bolt`, `spark`, `groundstrike`
- **Vine:** `jungle-vine`, `ivy`, `azalea-drape`, `mangrove-root`
- **Bark:** `oak-bark`, `spruce-bark`, `birch-bark`, `cherry-bark`

### Direct shortcuts

When the variant name is unclaimed at boot, a direct `/<variant>` shortcut is registered (`/bonfire`, `/puffy-cloud`, `/thunderbolt`, etc.). Reserved names (`undo`, `redo`, `set`, `info`, `save`, etc.) are rejected on save.

### Share codes

Format is `<type><version><mode>(...)`, for example `F2S(...)` for a flame stamp code. v1 codes such as `F1S(...)` still decode by filling missing trailing values with spec defaults (cloud opacity, lightning color).

---

## 9. Brush menu — `/brushmenu` (`/bm`)

In-game GUI for browsing, designing, saving brushes. Modeled after `/kit menu`.

### Pages

- **Home:** Pattern Brushes / Brush Builder / Detail Brushes / Menu Theme tiles. Close button at slot 8.
- **Pattern List:** browse 20 built-in pattern types, toggle to "Saved" tab. Pagination at 36/page.
- **Detail List:** built-in variants first, then saved. Pagination.
- **Pattern Design:** steppers for each param the brush wants (radius, height, density), 18-block material picker, preview book, Reset, Bind to Held Item, Save & Equip.
- **Detail Design:** override cyclers for each STRING-enum param (color, species, tint, direction). Preview, Bind, Save & Equip.
- **Builder Home:** friendly walkthrough splash; same tiles as Home.
- **Menu Theme (0.1.1):** 16 named-color swatches plus Reset. Active swatch shows ✔ + enchant glint. Click sets the menu accent live.

### Save & Equip flow

Closes inventory → chat-prompt asks for a name → typed message intercepted (not broadcast) → brush lands in a free hotbar slot. Empty main hand wins; otherwise first free hotbar slot. `cancel`/`abort`/empty bails. 60s expiry.

### Known v1 GUI gaps (parked for v2 — see `MISC/bzlV2.md`)

- Material picker is a fixed 18-material palette; no full block search yet.
- No multi-block distributions or mask picker in the GUI.
- Detail Save & Equip drops override tokens past the first.
- Click-to-update closes/reopens inventory (flicker).
- Uses `AsyncPlayerChatEvent` (deprecated in newer Paper).

---

## 10. Profiles and presets

Builder configuration profiles, persisted to `plugins/Bayzyl/profiles.yml`. Three types: **config**, **toolbar**, **combined**.

| Command | Description | Usage |
|---|---|---|
| `/profile save <name> <type> [overwrite:true]` | Save profile of given type | `/profile save dawn-build combined` |
| `/profile update <name>` | Refresh a profile from current state | `/profile update dawn-build` |
| `/profile load <name>` | Apply a saved profile | `/profile load dawn-build` |
| `/profile inspect <name>` | Show profile contents | `/profile inspect dawn-build` |
| `/profile list` | Paginated listing | `/profile list` |
| `/profile delete <name>` | Delete a profile | `/profile delete dawn-build` |
| `/profile rename <from> <to>` | Rename a profile | `/profile rename old new` |
| `/profile duplicate <from> <to>` | Duplicate a profile | `/profile duplicate dawn dusk` |

**What a profile captures:**
- **config-only:** runtime toggles (admin mode, night vision, ghost-hand, auto-unstick, stack direction, auto-move, nudge step/vertical/invert, particle visualization, tab menu module state, recent edit trail count, tab info panel state, message theme accent).
- **toolbar:** the player's current hotbar (item, slot, NBT/PDC, brush metadata).
- **combined:** both config and toolbar.

**Overwrite protection:** `overwrite:true` required to clobber an existing name.

---

## 11. Builder kits — shared loadouts

Server-shared toolbar/inventory snapshots, persisted to `plugins/Bayzyl/kits.yml`. Includes alias system and metadata (notes, theme, icon).

| Command | Description | Usage |
|---|---|---|
| `/kit list` | List all kits | `/kit list` |
| `/kit menu` | Open the kit GUI | `/kit menu` |
| `/kit <name>` | Load a kit (after the kit name is registered as a shortcut) | `/kit autumn` |
| `/kit load <name>` | Load a kit | `/kit load autumn` |
| `/kit inspect <name>` | Show kit contents and metadata | `/kit inspect autumn` |
| `/kitmake <hotbar\|inventory> <name> [overwrite:true]` | Capture a kit | `/kitmake hotbar autumn` |
| `/kitupdate <name>` | Update kit from current loadout (asks for confirmation) | `/kitupdate autumn` |
| `/kitconfirm` | Confirm a pending kit update | `/kitconfirm` |
| `/kit delete <name>` | Delete a kit | `/kit delete autumn` |
| `/kit rename <from> <to>` | Rename | `/kit rename autumn fall` |
| `/kit duplicate <from> <to>` | Duplicate | `/kit duplicate autumn winter` |
| `/kit restoredefaults` | Restore the bundled default kits | `/kit restoredefaults` |
| `/kit note <name> <note>` | Set a kit description note | `/kit note autumn "leaf brushes + warm palette"` |
| `/kit theme <name> <accent>` | Set kit's display accent | `/kit theme autumn gold` |
| `/kit icon <name> <material>` | Set kit's GUI icon | `/kit icon autumn oak_leaves` |
| `/kit alias <list\|add\|remove> <kit> [alias]` | Manage shortcut aliases | `/kit alias add autumn fall` |
| `/kithelp [topic]` | Show kit help | `/kithelp` |
| `/kitlist [page]` | Paginated browser | `/kitlist 2` |

---

## 12. Schematic workflow

| Command | Description | Usage |
|---|---|---|
| `/schematic save <name>` | Save the current selection or clipboard as a schematic | `/schematic save my-build` |
| `/schematic load <name>` | Load a schematic into the clipboard | `/schematic load my-build` |
| `/schematic list` | List saved schematics | `/schematic list` |

**Edge cases (open in 0.1.1):** spaces in names, special chars, very long names — see alpha checklist.

---

## 13. Cleanup

| Command | Description | Usage |
|---|---|---|
| `/cleanup floatingcleanup` | Remove orphan floating blocks in the selection | `/cleanup floatingcleanup` |
| `/cleanup foliagecleanup` | Remove leaves/grass/foliage in the selection | `/cleanup foliagecleanup` |
| `/cleanup liquidcleanup` | Remove water/lava in the selection | `/cleanup liquidcleanup` |
| `/cleanup snowcleanup` | Remove snow layers in the selection | `/cleanup snowcleanup` |
| `/cleanup lightcleanup` | Remove redundant light blocks in the selection | `/cleanup lightcleanup` |
| `/cleanup brush <type>` | Bind a cleanup brush to the held item | `/cleanup brush foliagecleanup` |
| `/floatingcleanup [confirm:true]` | Top-level shortcut for `/cleanup floatingcleanup` | `/floatingcleanup` |
| `/foliagecleanup [confirm:true]` | Top-level shortcut for `/cleanup foliagecleanup` | `/foliagecleanup` |
| `/liquidcleanup [confirm:true]` | Top-level shortcut for `/cleanup liquidcleanup` | `/liquidcleanup` |
| `/snowcleanup [confirm:true]` | Top-level shortcut for `/cleanup snowcleanup` | `/snowcleanup` |
| `/lightcleanup [confirm:true]` | Top-level shortcut for `/cleanup lightcleanup` | `/lightcleanup` |

---

## 14. Movement / navigation / QoL

| Command | Description |
|---|---|
| `/ascend` | Teleport to next safe floor above |
| `/descend` | Teleport to nearest safe floor below |
| `/surface` | Teleport to the nearest safe surface above |
| `/thru` | Teleport through the wall you face |
| `/unstick` | Move yourself to nearest safe open space |
| `/unstick auto <on\|off\|status>` | Auto-unstick toggle |
| `/align [direction]` | Snap your facing to a cardinal direction |
| `/centerme` | Center yourself on the current block |
| `/ceil` | Report distance to the ceiling above |
| `/whereami` | Show your exact position and context |
| `/ruler` | Measure distance to the block you're looking at |
| `/ghosthand` (`/gh`) | Toggle interact-through mode for containers/buttons |
| `/nightvision [on\|off\|toggle]` | Toggle night vision |
| `/eraser` | Get the eraser tool |

---

## 15. Tab info panel

Footer panel in tab list with composable modules. State per-player; module toggles persist.

| Command | Description |
|---|---|
| `/tabmenu status` | Show enabled modules |
| `/tabmenu all <on\|off\|status>` | Toggle the whole panel |
| `/tabmenu module <name> <on\|off\|status>` | Toggle a specific module |
| `/bzl tabmenu …` | Same surface, namespaced |

**Modules:** RAM metrics + spike graph; clipboard summary + mini-render; recent edit trail (toggleable count 1–20, default 5); selection summary with overlap detection between online players' selections.

**0.1.1 caveats:** density/phrasing under real player counts; non-WorldEdit-installed verification; large-selection approximation (`~` counts above ~200k).

---

## 16. Runtime settings — `/bzl env` and friends

| Command | Description | Usage |
|---|---|---|
| `/bzl env accent <name\|hex\|reset\|status>` | Set the menu accent color | `/bzl env accent gold`, `/bzl env accent #55ffaa` |
| `/bzl selectionparticles …` | Selection particle controls | `/bzl selectionparticles status` |
| `/bzl nudge …` | Selection nudge settings | `/bzl nudge status` |
| `/bzl authority <status\|claim\|giveup>` | Contested-command authority controls | `/bzl authority status` |
| `/authority <status\|claim\|giveup>` | Top-level shortcut for `/bzl authority`. Aliases: `bzlauthority`, `commandauthority` | `/authority status` |
| `/bzl stacklook <on\|off\|status>` | Default stack direction from look angle | `/bzl stacklook on` |
| `/bzl stackautomove <on\|off\|status\|toggle>` | Auto-move to end after stack | `/bzl stackautomove toggle` |
| `/bzl ramalert …` | RAM alert controls (see below) | `/bzl ramalert status` |
| `/ramalert <on\|off\|status\|help> [options]` | Top-level RAM alert | `/ramalert status` |
| `/memreset [global]` | Clear clipboard/history/trail state and reclaim Bayzyl edit memory. `global` requires admin mode. Use `/clearclipboard` for clipboard-only cleanup. | `/memreset`, `/memreset global` |
| `/bzl env memreset [global]` | Namespaced form of `/memreset` | `/bzl env memreset` |
| `/bzl env accent <color\|reset\|status\|#RRGGBB>` | Namespaced accent setter | `/bzl env accent gold` |
| `/accent <color\|reset\|status\|#RRGGBB>` | Top-level shortcut for `/bzl env accent`. Alias: `bzlaccent` | `/accent gold` |
| `/nudge <status\|invert\|step\|vertical\|reset>` | Nudge settings | `/nudge step 4` |
| `/bzltoggle <admin\|ramalert\|authority>` | Runtime toggles aggregator | `/bzltoggle admin` |
| `/bzl tool <smooth\|raise\|lower\|flatten> <radius> [power] [bedrock:on\|off]` | Bayzyl terrain tool binder. v1 supports terrain modes only; full WorldEdit-equivalent `/tool` is a separate gameplan | `/bzl tool smooth 5` |
| `/tool <smooth\|raise\|lower\|flatten> <radius> [power] [bedrock:on\|off]` | Top-level shortcut for `/bzl tool`. Alias: `bzltool`. Claiming this label currently displaces WorldEdit's `/tool` — use `/bzl authority giveup` to revert | `/tool smooth 5` |

### Accent values

Named: `red`, `gold`, `yellow`, `green`, `aqua`, `blue`, `light_purple`, `dark_red`, `dark_purple`, `dark_blue`, `dark_aqua`, `dark_green`, `dark_gray`, `gray`, `white`, `black`. Hex: `#rrggbb`. The brushmenu Theme picker (slot 31 on home) provides a swatch UI for the named set.

### Profile/accent round-trip

Saving a `combined` profile captures the current accent. Loading it restores the accent and refreshes help/menu styling immediately.

---

## 17. Palette utilities

| Command | Description |
|---|---|
| `/palette analyze` | Analyze block composition of the selection or clipboard |
| `/palette swap` | Swap blocks within the selection by mapping |
| `/bzl palette …` | Same surface, namespaced |

---

## 18. Visualization

| Command | Description |
|---|---|
| `/particlevisualtoggle` | Per-player toggle for selection outline particles |
| `/selcenter` | Toggle particle marker at selection center |

**Outline color:** uses `Particle.DUST`; default sage for cuboid. Multiplayer collisions render as inverted/complementary palette per viewer.

---

## 19. Help system

| Command | Description |
|---|---|
| `/bzl` (or `/bayzyl`) | Main command root. Shows a top-level summary. |
| `/bzlhelp [page\|topic]` | In-game help pager. Topics: `Selection`, `Editing`, `Clipboard`, `Shapes`, `Brushes`, `Tools`, `Cleanup`, `Palette`, `Nudge`, `Profiles`, `Kits`, `Runtime`. |
| `/bzlhelp unsorted` | Temporary plain list of registered commands missing from the curated help pages | `/bzlhelp unsorted` |
| `/bzl help [topic]` | Same as `/bzlhelp` |
| `/kithelp [topic]` | Kit-specific help |

Help content is read directly from the jar (`help-pages.yml`); no on-disk file dependency. The `HelpContentService` was refactored in pre-alpha to avoid the `Could not save` warning.

---

## 20. Niche / fun commands

| Command | Description |
|---|---|
| `/bubu` | Cute pink chat phrase |
| `/susu` | Spawn a calico cat named Susu |
| `/artie` | Spawn a tuxedo cat named Artie |
| `/jail <player>` | Admin-mode prank-jail a player |
| `/liberate <player>` | Release jailed player |
| `/trailclear` | Clear your recent edit trail |

---

## 21. Command claiming (0.1.1)

106/106 commands in `CommandRegistry.getAllCommands()` are claimed at boot. Each claim line in console reads:

```
[Bayzyl] Claimed /<name> (replaced N existing entries)
```

Bayzyl fully owns the bare names previously contested by WorldEdit/Multiverse/Axiom: `/copy`, `/paste`, `/cut`, `/set`, `/replace`, `/move`, `/stack`, `/sphere`, `/cyl`, `/walls`, `/overlay`, `/smooth`, `/expand`, `/contract`, `/oops`, `/cleanup`, `/wand`, `/eraser`, `/select`, `/undo`, `/redo`, etc.

**Mechanism:** `CommandOverrideService.claimPrimaryCommand` uses reflection to register the Bayzyl handler at the bare name and add a namespaced fallback (`bayzyl:<name>`) so the player can still reach the original via the namespace. Don't remove the namespaced fallback path — see `feedback_command_claim_quirk` in agent memory.

**Roadmap (v2):** `/tool`, `/flatten`, `/detritus` need real implementations before they should be claimed. See `MISC/bzlV2.md` §5h.

---

## 22. Persistence files

Under `plugins/Bayzyl/`:

| File | Purpose |
|---|---|
| `profiles.yml` | Builder profiles (config/toolbar/combined) |
| `kits.yml` | Shared builder kits |
| `brushes.yml` | Saved pattern brush presets |
| `detail-brushes.yml` | Saved detail brush variants |
| `selection-bookmarks.yml` | Per-player named selections |
| `edit-history.yml` | Persistent undo/redo stacks (capped at 50k changes per action; oversize → metadata stub) |
| `runtime-preferences.yml` | Global + per-player runtime toggles |
| `list-menus.yml` | List menu styling overrides |
| `help-pages.yml` (jar resource) | In-game help content |

---

## 23. 0.1.1 alpha-period changes — quick reference

| Area | Change |
|---|---|
| Paste/cut/copy | Chunked at 200–300k threshold; tiered safety gates; defense-in-depth (heap-pressure abort, memory pre-flight, cancel-on-disable, container CMD shim); placed-count accuracy; persisted history async + 50k cap; fixed deadlock from main-thread async scheduling |
| Undo/redo | Chunked at 50k threshold; re-entry guard; 25/50/75% progress messages; 200k in-memory cap removed; 20-min TTL eviction in `EditHistory`; cancel-on-disable |
| Brushmenu | New GUI; chat-prompt naming for Save & Equip; Theme picker page (16 swatches + reset) |
| Detail brushes | Variant + override quick-load; `/db undo-last`; share-code v2 with v1 backcompat; per-preset cooldowns; bind feedback shows block estimate |
| Runtime/memory | `/memreset` top-level command, `/bzl env memreset`, admin-only global reset, and `/clearclipboard` for clipboard-only cleanup |
| Structures | `/genstructure` and `/genfeature` capture to clipboard; `/regen` rerolls the last generated structure at the saved anchor; structure brush world-context fix (`/execute in <world>`) |
| Selection | Multiplayer outline rendering with collision-inverted palette; share codes for bookmarks (`/selsave share`, `/selload share <code>`) |
| Command claim | 106/106 names claimed; deduped `getAllCommands` |
| Help | Beginner-friendly rewrite of `help-pages.yml`; `HelpContentService` refactored to read from jar |
| Tabinfo | Modules: RAM + spike graph, clipboard, recent edits, selection summary with overlap detection |

---

## 24. Update etiquette for this file

- **When a command is added:** add a row to the relevant section. If the command is a new family (e.g. structure brushes were one), give it its own subsection.
- **When a flag changes:** update the usage cell. Don't backfill old behavior unless explicitly versioned.
- **When a 0.1.x feature ships:** also append a row to §23 quick reference.
- **When a feature is parked:** delete the row here and add it to `MISC/bzlV2.md` §5 with the same level of detail. The yml ↔ player-facing translation is the user's job — this file is the source.
- **Don't add color codes, page numbers, or chat formatting.** This is a working reference, not in-game copy.
- **Cite file paths sparingly.** Only when a maintainer would need to know where the logic lives (e.g. `EditHistory.evictOlderThan`, `CommandOverrideService.claimPrimaryCommand`).
