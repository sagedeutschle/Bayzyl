# Browser Arcade contract v1

`engines.js` exports `engines[gameID]` with pure `initial(seed)`, `apply(state, action)`, `status(state)` and `validate(state)`. Valid moves return a new state; no-op moves return the same state. IDs are `2048`, `minesweeper`, `lights-out`. Seeds and RNG states are decimal UInt64 strings. Native LCG arithmetic wraps at64 bits. Native2048 initial generation consumes four draws; continuation starts after those draws.

Portable JSON fields: `version:1`, `stateVersion:1`, `gameID`, `seed`, `mode` (`free`, `daily`, `challenge`), `dailyDate` (daily only), `state`, `moves`, `elapsedMs`, `savedAt` (UTC ISO8601). Only validated imports may replace a session, after explicit confirmation. Files contain no accounts or private paths. Progress is local and unverified; no competitive leaderboard claims.

2048 state: `{size:4,grid:[16 integers],score,rngState}`. Minesweeper state: `{width,height,mineCount,seed,mines,revealed,flagged,hasPlacedMines,status}`. Lights Out state: `{grid:[25 booleans]}`. Arrays of cell indexes are unique and in range. Status must agree with revealed mines/safe cells. See `saves.js` for numeric bounds and validation.

Actions: 2048 `{direction:up|down|left|right}`; Minesweeper `{type:reveal|flag,index}`; Lights Out `{type:press,index}`. The `spawn:false`2048 option is for native parity tests only. UI always spawns.

Daily seed: FNV-1a64 over ASCII `prismet-daily-v1:<gameID>:<UTC YYYY-MM-DD>`, offset14695981039346656037 and prime1099511628211, wrapping64 bits. `?mode=daily&date=YYYY-MM-DD&seed=<decimal>` and `?mode=challenge&seed=<decimal>` reproduce settings. Mines are placed on the first revealed cell, as in native; shared seed plus same first reveal yields the same board. Completion streaks are per-game, local to this browser and based on UTC dates. Free/daily/challenge saves occupy separate local slots. No server authentication or multiplayer is implied.

## Browser collection extensions

The full collection uses the native category names and tile art. The original three portable games retain the contract above. Additional games and Brick Bench use pure modules under `games/` and the separate `browserVersion: 1` contract in `browser-saves.js`; these files do not claim native transfer or cloud-sync compatibility. The universal controller consumes each engine's grid, cards, word, cube, or hex projection and passes its declared actions through unchanged.

Browser-only saves include `gameID`, decimal UInt64 `seed`, `mode`, optional daily-only `dailyDate`, `state`, `steps`, `elapsedMs`, and canonical millisecond UTC `savedAt`. Validation rejects unknown fields, inherited catalog keys, invalid engine states, date/seed conflicts, and serialized files over 256 KiB. Import requires confirmation and preserves separate timestamped backups of both the visible session and any destination slot before replacing either. Local storage denial leaves play available with an export reminder. Snake resumes paused; board bots run only during their own turns with cancelable scheduling.

`arcade-library-check.mjs` checks all 17 browser-only engines and projections, browser save/daily route round trips, strict parsing regressions, bot-turn policy, paused Snake restoration, and canceled scheduling. Browser layout, keyboard, and real touch acceptance are separate parent-owned gates.
