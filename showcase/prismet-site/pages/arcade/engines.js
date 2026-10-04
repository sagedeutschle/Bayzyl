// Pure browser ports of Prismet's native game rules. UInt64 math is intentionally explicit.
export const IDS = ['2048', 'minesweeper', 'lights-out'];
const MAX64 = (1n << 64n) - 1n;
export const uint64 = (value) => typeof value === 'string' && /^(0|[1-9][0-9]{0,19})$/.test(value) && BigInt(value) <= MAX64;
export function generator(seed) {
  if (!uint64(seed)) throw new Error('Seed must be a decimal UInt64 string.');
  let value = BigInt(seed) || 0x9E3779B97F4A7C15n;
  return { next(bound) { value = (value * 6364136223846793005n + 1442695040888963407n) & MAX64; return Number(value % BigInt(bound)); }, get state() { return value.toString(); } };
}
// Restoring the exact RNG state differs from initializing a zero seed.
function restoredGenerator(state) {
  let value = BigInt(state);
  return { next(bound) { value = (value * 6364136223846793005n + 1442695040888963407n) & MAX64; return Number(value % BigInt(bound)); }, get state() { return value.toString(); } };
}
const integer = (x, min, max) => Number.isSafeInteger(x) && x >= min && x <= max;
const record = (x) => x && typeof x === 'object' && !Array.isArray(x);
const indices = (a, max) => Array.isArray(a) && a.length <= max && a.every((x) => integer(x, 0, max - 1)) && new Set(a).size === a.length;
function spawn(state, rng) { const empty = state.grid.flatMap((x, i) => x === 0 ? [i] : []); if (empty.length) state.grid[empty[rng.next(empty.length)]] = rng.next(10) === 0 ? 4 : 2; }
export const game2048 = {
  initial(seed = '1') { const rng = generator(seed), state = { size: 4, grid: Array(16).fill(0), score: 0, rngState: '' }; spawn(state, rng); spawn(state, rng); state.rngState = rng.state; return state; },
  apply(state, action) {
    if (!['up', 'down', 'left', 'right'].includes(action?.direction)) return state;
    const n = state.size, grid = Array(n * n).fill(0); let gained = 0;
    for (let line = 0; line < n; line++) {
      const positions = Array.from({ length: n }, (_, i) => action.direction === 'left' ? line * n + i : action.direction === 'right' ? line * n + n - 1 - i : action.direction === 'up' ? i * n + line : (n - 1 - i) * n + line);
      const values = positions.map((i) => state.grid[i]).filter(Boolean); let target = 0;
      for (let i = 0; i < values.length; i++) { let value = values[i]; if (values[i + 1] === value) { value *= 2; gained += value; i++; } grid[positions[target++]] = value; }
    }
    if (grid.every((v, i) => v === state.grid[i])) return state;
    const next = { ...state, grid, score: state.score + gained }, rng = restoredGenerator(state.rngState);
    if (action.spawn !== false) spawn(next, rng);
    next.rngState = rng.state; return next;
  },
  status(state) {
    if (state.grid.some((x) => x >= 2048)) return 'won';
    if (state.grid.includes(0)) return 'playing';
    return state.grid.some((v, i) => (i % state.size < state.size - 1 && v === state.grid[i + 1]) || v === state.grid[i + state.size]) ? 'playing' : 'lost';
  },
  validate(s) { return !!(record(s) && integer(s.size, 3, 6) && Array.isArray(s.grid) && s.grid.length === s.size * s.size && s.grid.every((x) => x === 0 || (integer(x, 2, 2 ** 30) && Number.isInteger(Math.log2(x)))) && integer(s.score, 0, 10 ** 12) && uint64(s.rngState)); },
};
export function neighbors(state, index) {
  const row = Math.floor(index / state.width), col = index % state.width, result = [];
  for (let dr = -1; dr <= 1; dr++) for (let dc = -1; dc <= 1; dc++) if ((dr || dc) && row + dr >= 0 && row + dr < state.height && col + dc >= 0 && col + dc < state.width) result.push((row + dr) * state.width + col + dc);
  return result;
}
export const adjacent = (state, index) => neighbors(state, index).filter((i) => state.mines.includes(i)).length;
export const minesweeper = {
  initial(seed = '1') { if (!uint64(seed)) throw new Error('Invalid seed'); return { width: 9, height: 9, mineCount: 10, seed, mines: [], revealed: [], flagged: [], hasPlacedMines: false, status: 'playing' }; },
  apply(state, action) {
    if (state.status !== 'playing' || !integer(action?.index, 0, state.width * state.height - 1) || !['reveal', 'flag'].includes(action.type) || state.revealed.includes(action.index)) return state;
    const next = structuredClone(state), index = action.index;
    if (action.type === 'flag') { next.flagged = next.flagged.includes(index) ? next.flagged.filter((x) => x !== index) : [...next.flagged, index].sort((a, b) => a - b); return next; }
    if (next.flagged.includes(index)) return state;
    if (!next.hasPlacedMines) { const rng = generator(next.seed), mines = new Set(); while (mines.size < next.mineCount) { const candidate = rng.next(next.width * next.height); if (candidate !== index) mines.add(candidate); } next.mines = [...mines].sort((a, b) => a - b); next.hasPlacedMines = true; }
    if (next.mines.includes(index)) { next.revealed.push(index); next.revealed.sort((a, b) => a - b); next.status = 'lost'; return next; }
    const queue = [index], seen = new Set(next.revealed);
    while (queue.length) { const cell = queue.pop(); if (seen.has(cell) || next.flagged.includes(cell) || next.mines.includes(cell)) continue; seen.add(cell); if (adjacent(next, cell) === 0) queue.push(...neighbors(next, cell)); }
    next.revealed = [...seen].sort((a, b) => a - b);
    if (next.revealed.length === next.width * next.height - next.mineCount) next.status = 'won'; return next;
  },
  status: (s) => s.status,
  validate(s) {
    if (!record(s) || !integer(s.width, 2, 30) || !integer(s.height, 2, 30)) return false;
    const n = s.width * s.height;
    if (!integer(s.mineCount, 1, n - 1) || !uint64(s.seed) || !indices(s.mines, n) || !indices(s.flagged, n) || !indices(s.revealed, n) || typeof s.hasPlacedMines !== 'boolean' || !['playing', 'won', 'lost'].includes(s.status) || s.flagged.some((i) => s.revealed.includes(i))) return false;
    if (!s.hasPlacedMines) return s.mines.length === 0 && s.revealed.length === 0 && s.status === 'playing';
    if (s.mines.length !== s.mineCount) return false;
    const hit = s.revealed.some((i) => s.mines.includes(i)), won = !hit && s.revealed.length === n - s.mineCount;
    return s.status === (hit ? 'lost' : won ? 'won' : 'playing');
  },
};
function toggle(grid, index) { const row = Math.floor(index / 5), col = index % 5; for (const [r, c] of [[row, col], [row - 1, col], [row + 1, col], [row, col - 1], [row, col + 1]]) if (r >= 0 && r < 5 && c >= 0 && c < 5) grid[r * 5 + c] = !grid[r * 5 + c]; }
export const lightsOut = {
  initial(seed = '1') { const rng = generator(seed), grid = Array(25).fill(false); for (let i = 0; i < 10; i++) toggle(grid, rng.next(5) * 5 + rng.next(5)); if (!grid.some(Boolean)) toggle(grid, 0); return { grid }; },
  apply(state, action) { if (!integer(action?.index, 0, 24) || action.type !== 'press') return state; const grid = [...state.grid]; toggle(grid, action.index); return { grid }; },
  status: (s) => s.grid.some(Boolean) ? 'playing' : 'won',
  validate: (s) => !!(record(s) && Array.isArray(s.grid) && s.grid.length === 25 && s.grid.every((x) => typeof x === 'boolean')),
};
export const engines = { '2048': game2048, minesweeper, 'lights-out': lightsOut };
export function dailySeed(gameID, date) {
  if (!IDS.includes(gameID) || !/^\d{4}-\d{2}-\d{2}$/.test(date) || new Date(`${date}T00:00:00Z`).toISOString().slice(0, 10) !== date) throw new Error('Invalid daily challenge');
  let hash = 14695981039346656037n;
  for (const char of `prismet-daily-v1:${gameID}:${date}`) hash = ((hash ^ BigInt(char.charCodeAt(0))) * 1099511628211n) & MAX64;
  return hash.toString();
}
