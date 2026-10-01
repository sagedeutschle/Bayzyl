# `/tool` — WorldEdit Parity Plan

This file scopes the work to turn Bayzyl's current `/tool` (terrain-only) into a full WorldEdit-equivalent click-tool surface. Goal: every WorldEdit `/tool <X>` subcommand has a Bayzyl-native equivalent, and the bare `/tool` label belongs to Bayzyl (it already does after the 0.1.1 shortcut pass — see `Bayzyl.java` boot log `Claimed /tool for Bayzyl (replaced 5 existing entries)`).

Sources for the WE surface:
- [WorldEdit Tools docs](https://worldedit.enginehub.org/en/latest/usage/tools/tools/)
- [WorldEdit permissions reference](https://github.com/EngineHub/WorldEditDocs/blob/master/source/permissions.rst)

---

## Current state (after 0.1.1 shortcut pass)

- `/tool` and `/bzl tool` both route to `BayzylCommand#handleToolNamespace`.
- That handler only accepts terrain modes: `smooth`, `raise`, `lower`, `flatten`. Anything else falls into the `Not implemented yet: /bzl tool <sub>` branch.
- Bayzyl already has analogous functionality scattered across other commands:
  - selection wand → `/wand` (top-level)
  - selection at-target → `/select cube ... at:target`
  - shape/brush placement → `/brush ...` family
  - tree/forest generation → `/forestgen`
  - block info → `/whereami`, `/biomeinfo` (selection-scoped, not click-tool)
  - stack → `/stack` (selection-scoped, not click-tool)
- Several WorldEdit features have **no Bayzyl equivalent yet**: info-tool, repl-tool, cycler, floodfill, deltree, farwand, stacker, lrbuild, navwand, single-tree placer.

---

## Mapping table: WE `/tool <X>` → Bayzyl plan

| WE subcommand | What it does | Closest Bayzyl today | Plan |
|---|---|---|---|
| `/tool none` | Unbind held item's tool | `/none` already does this for brushes/eraser | Extend `/none` and add `/tool none` to clear click-tool PDC too |
| `/tool selwand` | Bind selection wand (default: wooden axe) | `/wand` gives the wand item | New: `/tool selwand` binds wand-behavior to held item (no item swap) |
| `/tool navwand` | L-click = jumpto, R-click = thru | `/thru`, `/ascend`, `/surface` are commands, not click tools | New: bind a NAVWAND tool that does jumpto on L-click, thru on R-click |
| `/tool info` | R-click block → print coords, type, states, light, internal id | `/whereami` shows player pos only | New: bind INFO tool. Output uses Bayzyl chat style (`MessageThemeService`) |
| `/tool tree [type]` | R-click ground → spawn one tree (Minecraft `TreeType`) | `/forestgen` places many trees in a region | New: TREE tool. Argument list = `TreeType.values()` |
| `/tool repl` | R-click block → replace with stored pattern | `/replace from to` operates on whole selection | New: REPL tool. Stores a `BlockDistribution` (single block or weighted) in tool PDC |
| `/tool cycler` | L-click = pick property to cycle, R-click = step value | none | New: CYCLER tool. Reuses Paper's `BlockData.getAsString()` parsing for state mutation |
| `/tool floodfill <pattern> <range>` | R-click → flood-fill connected blocks of the same type | `/replace` with mask but no flood semantics | New: FLOODFILL tool. Stores pattern + range. BFS bounded by range, recorded via `HistoryService` |
| `/tool deltree` | R-click leaf/log → remove the whole disconnected tree | none | New: DELTREE tool. Reuses CleanupService/floating-block walker; bounded crawl |
| `/tool farwand` | Long-range selection wand (uses target block, not collision) | none — `/wand` is short-range only | New: FARWAND tool. On click, raycasts to `Player#getTargetBlockExact(maxRange)` and sets pos1/pos2 |
| `/tool stacker <count> [range]` | R-click block → stack it N times in the look direction | `/stack` operates on selection | New: STACKER tool. Stores count + optional range mask |
| `/tool lrbuild <primary> <secondary>` | L-click = place primary at look-target, R-click = secondary | none | New: LRBUILD tool. Reuses Bayzyl block-placement (`EditService#applyChanges`) |

Plus existing terrain modes stay under the same root:
- `/tool smooth <radius> [power] [bedrock:on|off]`
- `/tool raise <radius> [power] [bedrock:on|off]`
- `/tool lower <radius> [power] [bedrock:on|off]`
- `/tool flatten <radius> [power] [bedrock:on|off]`

---

## Architecture

New package: `com.bayzyl.tool` (separate from the existing `com.bayzyl.detail` and `ToolManager`).

```
com.bayzyl.tool/
├── ClickToolType.java          enum NAVWAND, INFO, TREE, REPL, CYCLER,
│                                  FLOODFILL, DELTREE, FARWAND, STACKER, LRBUILD, SELWAND
├── ClickToolBinding.java       record (type, params Map<String,String>, version)
├── ClickToolService.java       bind / unbind / read PDC, dispatch click → handler
├── ClickToolCommandParser.java parse `/tool <sub> [args]` → ClickToolBinding
├── handlers/
│   ├── ClickToolHandler.java         interface { handleLeft, handleRight }
│   ├── InfoToolHandler.java
│   ├── NavWandHandler.java
│   ├── TreeToolHandler.java
│   ├── ReplToolHandler.java
│   ├── CyclerToolHandler.java
│   ├── FloodFillToolHandler.java
│   ├── DelTreeToolHandler.java
│   ├── FarWandHandler.java
│   ├── StackerToolHandler.java
│   ├── LrBuildToolHandler.java
│   └── SelWandHandler.java       (delegates to existing wand logic)
```

### PDC namespace

`bayzyl:click_tool` — distinct from `bayzyl:brush` (pattern), `bayzyl:detail_brush` (detail), `bayzyl:wand` (wand item), `bayzyl:eraser` (eraser).

Held item can hold **at most one** Bayzyl tool binding at a time. Binding `/tool <X>` strips any existing brush/detail-brush/eraser PDC on that item to prevent ambiguous dispatch.

### Click flow

```
PlayerInteractEvent
  → BayzylListener#onPlayerInteract
    → ToolManager.getActiveTool(item)
      ├─ WAND       → existing wand path
      ├─ ERASER     → existing eraser path
      ├─ BRUSH      → existing brush path
      ├─ DETAIL_BRUSH → existing detail brush path
      └─ CLICK_TOOL → ClickToolService.handle(event)
                       → handler.handleLeft / handleRight
```

### History integration

Every click-tool mutation goes through `HistoryService#record(uuid, changes)` so:
- `/undo` reverses one click.
- Persistent history captures it on shutdown.
- 20-min TTL and chunked-undo paths apply automatically.

Tools that only read (INFO, FARWAND when not committing) do NOT record history.

---

## Phased delivery

### Phase A — Foundation (1 PR)
- Create `com.bayzyl.tool` package skeleton.
- Add `ClickToolType` enum (entries listed above) and `ClickToolBinding` record.
- Extend `ToolManager` (or add `ClickToolService`) to read/write the new PDC.
- Wire BayzylListener dispatch path so unknown click-tools log a warning instead of crashing.
- Add `/tool none` and `/tool list` (debug-only "show what's bound").
- **Done when:** `/tool none` clears any Bayzyl tool PDC and old detail-brush/brush/eraser paths still work.

### Phase B — Read-only tools (1 PR)
- `InfoToolHandler` — print block coords/type/states/light/internal-id via Bayzyl chat style.
- `FarWandHandler` — long-range selection corners (left=pos1, right=pos2). Uses `Player#getTargetBlockExact` with a reasonable max range (e.g. 256, configurable).
- `SelWandHandler` — short-range pos1/pos2 (already exists in BayzylListener; refactor to share logic).
- **Done when:** all three can be bound to any held item, click reads work, no edits happen.

### Phase C — Single-block mutators (1 PR)
- `ReplToolHandler` — store a `BlockDistribution` parsed at bind time (`/tool repl stone`, `/tool repl 60%stone,40%glass`). Right-click → replace looked-at block, record one change.
- `CyclerToolHandler` — left-click cycles which `BlockData` property to target, right-click steps that property's value. Property cursor stored in PDC.
- `TreeToolHandler` — right-click → `World#generateTree(loc, TreeType)`. Tree types tab-completed.
- `StackerToolHandler` — store count + optional range. Right-click → stack one block N times in look direction, recorded as one history action.
- **Done when:** all four bound tools execute correctly, integrate with `/undo`, and have tab completion.

### Phase D — Region mutators (1 PR)
- `FloodFillToolHandler` — store pattern + range. BFS from clicked block, bounded by range and matching material, replaces matched cells. Reuses chunked-edit scheduler if the BFS exceeds the 300k threshold.
- `DelTreeToolHandler` — connected-component walk over log+leaf blocks. Refuses if the component touches the ground (= still planted). Removes all blocks in the component in one history action.
- `LrBuildToolHandler` — left = primary block at look-target, right = secondary. Single block per click. Argument list: `/tool lrbuild <primary> <secondary> [range:<n>]`.
- `NavWandHandler` — left = jumpto (target block), right = thru (next non-solid past wall). Reuses `/thru` and a new `/jumpto` if needed.
- **Done when:** all four work, integrate with history, can be unbound with `/tool none`.

### Phase E — Polish + parity (1 PR)
- Full tab completion for every `/tool <sub>` path (block names for repl, tree types for tree, ranges for stacker/floodfill, etc.).
- Aliases: `/tool selwand`, `/tool navwand` etc. claim the bare WE names (`/selwand`, `/navwand`, `/info`, `/tree`, `/repl`, `/cycler`, `/floodfill`, `/deltree`, `/farwand`, `/lrbuild`) — see "Bare name claims" below.
- Permission nodes mirror WE: `bayzyl.tool.info`, `bayzyl.tool.tree`, etc. Defaults to `op` for safety; `bayzyl.admin` grants all.
- `/bzlhelp` page entry (likely new page or appended to page 10 / page 16).
- **Done when:** every WE `/tool <X>` has a working Bayzyl equivalent and the QUICKTEST has a section for tool parity.

---

## Bare name claims (WorldEdit's deprecated globals)

WorldEdit deprecated `/selwand`, `/navwand`, `/info`, `/tree`, `/repl`, `/cycler`, `/floodfill`, `/deltree`, `/farwand`, `/lrbuild` in favor of `/tool <X>`. After Phase E we should:

- Add each as a top-level shortcut in `plugin.yml` + `CommandRegistry` (same pattern as the 0.1.1 cleanup shortcuts).
- Dispatch in `BayzylCommand#onCommand` calls `handleToolRoot(sender, new String[]{"<sub>", ...args})`.
- Claim system displaces WE's deprecated globals (logs `replaced N existing entries`).
- Aliased permissions so `bayzyl.tool.tree` controls both `/tree` and `/tool tree`.

Risks to call out:
- `/info`, `/tree`, `/repl` are common names — other plugins may also register them. Test on a server with EssentialsX + WorldEdit + a tree-management plugin to catch conflicts.
- `/cycler` is rare; safe to claim.
- `/farwand`, `/lrbuild`, `/deltree`, `/floodfill`, `/navwand` are WE-specific and safe to claim.

---

## Open questions before Phase A

1. **Does `/tool` keep terrain modes at the top level, or move them under `/tool terrain <mode>`?**
   - Pro keep: muscle memory from current `/bzl tool smooth`.
   - Pro move: cleaner grouping. WE puts terrain under `/brush smooth`/`/brush flatten`, not `/tool`.
   - Default: keep at top level for now (`/tool smooth 5` still works), add `/tool terrain smooth 5` as a synonym.

2. **One tool per item, or stack them on the same item?**
   - WE allows one tool per item. Bayzyl already enforces one-brush-per-item via PDC.
   - Default: same constraint — binding a new tool strips any existing Bayzyl PDC.

3. **Click cooldowns?**
   - Detail brushes have per-preset cooldowns (2t–6t).
   - Click tools probably don't need cooldowns for INFO/SELWAND/FARWAND/CYCLER (no edit).
   - REPL/STACKER/TREE/FLOODFILL/DELTREE/LRBUILD should have a small cooldown (~3t) to prevent runaway holding.

4. **Permissions: do we follow WE's exact node names or use a Bayzyl prefix?**
   - WE uses `worldedit.tool.tree`. Bayzyl would use `bayzyl.tool.tree`.
   - Both should be checked, so admins migrating from WE don't have to rewrite LuckPerms groups.
   - Default: check `bayzyl.tool.<x>` first, fall back to `bayzyl.admin`. Do NOT honor `worldedit.*` perms (those should still gate WE's actual commands if WE is reachable via `/worldedit:` prefix after authority giveup).

5. **`/jumpto` — does this become a standalone Bayzyl command too?**
   - Probably yes, since NavWand R-click is `/thru` (Bayzyl has) and L-click is `/jumpto` (Bayzyl lacks).
   - Adding `/jumpto` is small (target-block teleport with sanity check) and unlocks NavWand cleanly.

---

## Estimated scope

| Phase | Files touched | LOC | Calendar |
|---|---|---|---|
| A | new package + ToolManager extension + BayzylCommand | ~250 | 1 session |
| B | 3 handlers + dispatch + completion | ~400 | 1 session |
| C | 4 handlers + parsing + completion | ~600 | 2 sessions |
| D | 4 handlers + BFS + history wiring | ~800 | 2 sessions |
| E | bare-name claims + permission audit + QUICKTEST + help | ~300 | 1 session |

Total: ~2350 LOC, ~7 sessions if iterating with playtest between phases. Each phase is independently shippable behind `bayzyl.tool.<x>` permission gates.

---

## Out of scope (for this gameplan)

- Brush-style tools (`/brush sphere` etc.) — those already exist under `/brush`.
- Schematic/clipboard tools — `/brush clipboard` covers this.
- BzlBlender (v2 client-side ghost rendering) — separate effort in `MISC/bzlV2.md`.
