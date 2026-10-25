// arcade-motion-check.mjs: the pure parts of the Arcade's motion layer and of the 3D cube.
//   node showcase/tools/tests/arcade-motion-check.mjs
// Covers: 2048 slide maps and spawn detection, cell diffs, reveal cascades, keyed FLIP matching; the cube's
// model-to-CSS matrices, pivot turn angles, orbit projection, sticker-drag resolution, history diffs and shading.
import assert from 'node:assert/strict';
import { slides2048, spawnIndex, cellChanges, cascadeDelays, matchMoved } from '../../prismet-site/pages/arcade/motion.js';
import { cssMatrix, turnAngle, orbitMatrix, applyMatrix, resolveDrag, movesBetween, inverse, shade, faceFor } from '../../prismet-site/pages/arcade/cube-math.js';
import { resumeItems, ago } from '../../prismet-site/pages/arcade/app-shell.js';
import { game2048 } from '../../prismet-site/pages/arcade/engines.js';

let checks = 0;
const check = (value, label) => { assert.ok(value, label); checks++; };
const same = (a, b, label) => { assert.deepEqual(a, b, label); checks++; };

// ── 2048: where every tile goes, so the board can slide before it re-renders ───────────────────────────────
const row = (...values) => [...values, ...Array(16 - values.length).fill(0)];
same(slides2048(row(2, 0, 2, 0), 'left'), { moves: [{ from: 0, to: 0 }, { from: 2, to: 0 }], grid: row(4) }, 'two equal tiles merge leftward into one');
same(slides2048(row(2, 0, 2, 0), 'right'), { moves: [{ from: 0, to: 3 }, { from: 2, to: 3 }], grid: row(0, 0, 0, 4) }, 'the same pair merges rightward');
same(slides2048(row(2, 2, 2, 0), 'left').moves, [{ from: 0, to: 0 }, { from: 1, to: 0 }, { from: 2, to: 1 }], 'a tile merges at most once per move');
same(slides2048(row(4, 2, 0, 0), 'left').moves, [{ from: 0, to: 0 }, { from: 1, to: 1 }], 'unequal tiles keep their order');
const column = Array(16).fill(0); column[0] = 2; column[12] = 2;
same(slides2048(column, 'down'), { moves: [{ from: 0, to: 12 }, { from: 12, to: 12 }], grid: Object.assign(Array(16).fill(0), { 12: 4 }) }, 'vertical merges resolve by column');
same(slides2048(row(2, 4, 8, 16), 'left'), { moves: [{ from: 0, to: 0 }, { from: 1, to: 1 }, { from: 2, to: 2 }, { from: 3, to: 3 }], grid: row(2, 4, 8, 16) }, 'a full unmergeable row stays put');
check(spawnIndex(row(4), row(4, 0, 2)) === 2, 'the spawned tile is the one that appeared on an empty square');
check(spawnIndex(row(4), row(4)) === -1, 'no spawn when nothing appeared');

// ── grid diffs and cascades ──────────────────────────────────────────────────────────────────────────────────
const cells = (...pairs) => pairs.map(([text, tone]) => ({ text, tone }));
same(cellChanges(cells(['', 'empty'], ['●', 'black']), cells(['●', 'white'], ['●', 'black'])), [0], 'only the cell whose text or tone changed is reported');
same(cellChanges(cells(['a', 'x']), cells(['a', 'y'])), [0], 'a tone change alone counts');
check(cellChanges(null, cells(['a', 'x'])) === null, 'no previous view means no changes to animate');
check(cellChanges(cells(['a', 'x']), cells(['a', 'x'], ['b', 'x'])) === null, 'a different cell count is a new board, not a change');
const delays = cascadeDelays([0, 1, 2, 9, 18], 0, 9, 20);
check(delays.get(0) === 0 && delays.get(1) === 20 && delays.get(9) === 20 && delays.get(2) === 40 && delays.get(18) === 40, 'reveal delay grows with Manhattan distance from the pressed square');
check(cascadeDelays([5], 5, 9, 20).get(5) === 0, 'the pressed square reveals at once');

// ── keyed FLIP: which pieces travelled between two renders ─────────────────────────────────────────────────
const item = (key, x, y) => ({ key, rect: { x, y, width: 10, height: 10 } });
same(matchMoved([item('♞', 0, 0), item('♟', 50, 0)], [item('♞', 20, 30), item('♟', 50, 0)]), [{ key: '♞', from: { x: 0, y: 0, width: 10, height: 10 }, to: { x: 20, y: 30, width: 10, height: 10 } }], 'a piece that changed place is matched by its glyph');
same(matchMoved([item('A♠', 0, 0), item('A♠', 100, 0)], [item('A♠', 0, 0), item('A♠', 100, 40)]).map((m) => m.from.x), [100], 'duplicate keys are matched in order, so only the card that moved travels');
same(matchMoved([item('x', 0, 0)], [item('y', 5, 5)]), [], 'a new key has nowhere to travel from');
same(matchMoved([item('●', 0, 0), item('●', 50, 0), item('●', 100, 0)], [item('●', 0, 0), item('●', 100, 0), item('●', 150, 0)]), [{ key: '●', from: { x: 50, y: 0, width: 10, height: 10 }, to: { x: 150, y: 0, width: 10, height: 10 } }], 'pieces that stayed put pair first, so only the checker that moved travels');
same(matchMoved([item('●', 0, 0), item('●', 200, 0)], [item('●', 20, 0), item('●', 210, 0)]).map((m) => [m.from.x, m.to.x]), [[0, 20], [200, 210]], 'leftover pieces pair with the nearest leftover of their key');

// ── the cube: model space (y up, z toward you) into CSS space (y down) ─────────────────────────────────────
const identity = [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
check(cssMatrix({ position: [1, -1, 0], orientation: identity }, 50) === 'matrix3d(1,0,0,0,0,1,0,0,0,0,1,0,50,50,0,1)', 'translation flips y and scales by the cubie size');
// A cubie turned +90° about the model x axis: e1 → e2, e2 → −e1. In CSS the same physical pose reads −90° about x.
check(cssMatrix({ position: [0, 0, 0], orientation: [[1, 0, 0], [0, 0, 1], [0, -1, 0]] }, 10) === 'matrix3d(1,0,0,0,0,0,-1,0,0,1,0,0,0,0,0,1)', 'orientation columns are conjugated by the y flip');
same(turnAngle('R'), { axis: [1, 0, 0], deg: -90 }, 'a model quarter turn about x plays as −90° in CSS');
same(turnAngle("R'"), { axis: [1, 0, 0], deg: 90 }, 'the prime turn plays the other way');
same(turnAngle('U'), { axis: [0, 1, 0], deg: 90 }, 'turns about y keep their sign through the flip');
same(turnAngle('F2'), { axis: [0, 0, 1], deg: -180 }, 'a half turn plays as 180°');
same(turnAngle('D'), { axis: [0, 1, 0], deg: 90 }, 'D turns about the same axis as U, the layer differs');
check(faceFor(0, 1) === 'R' && faceFor(0, -1) === 'L' && faceFor(1, 1) === 'U' && faceFor(1, -1) === 'D' && faceFor(2, 1) === 'F' && faceFor(2, -1) === 'B', 'axis and layer name the face');
check(inverse('R') === "R'" && inverse("U'") === 'U' && inverse('F2') === 'F2', 'inverses');

// Orbit: `rotateX(ax) rotateY(ay)`; the identity orbit looks straight at the front face.
same(orbitMatrix(0, 0), identity, 'no orbit is the identity');
same(applyMatrix(orbitMatrix(0, 90), [0, 0, 1]).map(Math.round), [1, 0, 0], 'a 90° spin about y brings the front face to the right');
same(applyMatrix(orbitMatrix(-90, 0), [0, 0, 1]).map(Math.round), [0, 1, 0], 'tilting the top toward the viewer brings the front face down');

// Drags: grab the front sticker of the top-right-front cubie with no orbit and pull.
const corner = { position: [1, 1, 1] };
const front = [0, 0, 1];
check(resolveDrag({ cubie: corner, normal: front, drag: { x: 40, y: 0 }, orbit: orbitMatrix(0, 0) }) === 'U', 'dragging the top row to the right turns the top layer as the engine defines U');
check(resolveDrag({ cubie: corner, normal: front, drag: { x: -40, y: 0 }, orbit: orbitMatrix(0, 0) }) === "U'", 'dragging it left turns the other way');
check(resolveDrag({ cubie: corner, normal: front, drag: { x: 0, y: 40 }, orbit: orbitMatrix(0, 0) }) === 'R', 'dragging the right column down turns the right layer as the engine defines R');
check(resolveDrag({ cubie: corner, normal: front, drag: { x: 0, y: -40 }, orbit: orbitMatrix(0, 0) }) === "R'", 'dragging it up turns the other way');
check(resolveDrag({ cubie: { position: [0, 0, 1] }, normal: front, drag: { x: 40, y: 0 }, orbit: orbitMatrix(0, 0) }) === null, 'the centre sticker belongs to no outer layer');
check(resolveDrag({ cubie: corner, normal: front, drag: { x: 3, y: 2 }, orbit: orbitMatrix(0, 0) }) === null, 'a tiny drag is not a turn');
// Seen from behind (spun half way round), the back row is the one facing you: dragging it right is still the turn that
// moves the near row right (a layer turning one way moves its front edge right and its back edge left).
check(resolveDrag({ cubie: { position: [-1, 1, -1] }, normal: [0, 0, -1], drag: { x: 40, y: 0 }, orbit: orbitMatrix(0, 180) }) === 'U', 'the drag is resolved through the current orbit');
check(resolveDrag({ cubie: { position: [-1, 1, -1] }, normal: [0, 0, -1], drag: { x: 0, y: 40 }, orbit: orbitMatrix(0, 180) }) === "L'", 'from behind, the visible right-hand column is the left layer');

// History diffs decide what to animate and when to snap.
same(movesBetween(['R'], ['R', 'U']), ['U'], 'one appended move animates');
same(movesBetween(['R', 'U'], ['R']), ["U'"], 'an undo animates the inverse');
same(movesBetween(['R'], []), ["R'"], 'undoing the only move');
same(movesBetween(['R'], ['R']), [], 'nothing changed');
check(movesBetween([], ['R', 'U']) === null && movesBetween(['R', 'U'], ['R', 'F']) === null, 'anything else is a new board and snaps');

// Shading: with no orbit, the top and front stickers catch more light than the bottom.
const bright = (n) => shade(n, orbitMatrix(0, 0));
check(bright([0, 1, 0]) > bright([0, -1, 0]) && bright([0, 0, 1]) > bright([0, -1, 0]), 'light falls from above and in front');
check(bright([0, 1, 0]) <= 1 && bright([0, -1, 0]) >= 0, 'shade stays within 0..1');

// ── the library's "On the cabinet" strip: which local saves are worth resuming ─────────────────────────────
const now = Date.parse('2026-10-24T20:00:00.000Z');
const portable = (moves, savedAt, state = game2048.initial('7')) => JSON.stringify({ version: 1, stateVersion: 1, gameID: '2048', seed: '7', mode: 'free', state, moves, elapsedMs: 0, savedAt });
const browser = (gameID, steps, savedAt, extra = {}) => JSON.stringify({ browserVersion: 1, gameID, seed: '9', mode: 'free', state: {}, steps, elapsedMs: 0, savedAt, ...extra });
const stored = {
  'prismet.arcade.v1.2048.free': portable(12, '2026-10-24T19:57:00.000Z'),
  'prismet.arcade.browser.v1.chess.free': browser('chess', 4, '2026-10-24T18:00:00.000Z'),
  'prismet.arcade.browser.v1.snake.free': browser('snake', 0, '2026-10-24T19:59:00.000Z'),
  'prismet.arcade.browser.v1.sudoku.daily.2026-10-24': browser('sudoku', 3, '2026-10-24T17:00:00.000Z', { mode: 'daily', dailyDate: '2026-10-24' }),
  'prismet.arcade.browser.v1.spider.free': browser('spider', 2, '2026-10-24T16:00:00.000Z'),
  'prismet.arcade.v1.completed.2048': '["2026-10-23"]',
  'prismet.arcade.v1.import-backup.x.visible': portable(5, '2026-10-24T19:58:00.000Z'),
  'prismet.arcade.browser.v1.nope.free': browser('nope', 9, '2026-10-24T19:58:30.000Z'),
  'prismet.arcade.browser.v1.gomoku.free': 'not json',
  'prismet.arcade.v1.minesweeper.free': JSON.stringify({ gameID: 'minesweeper', moves: 3, savedAt: '2026-10-24T19:58:40.000Z', state: { status: 'lost' }, seed: '1', mode: 'free' }),
};
const items = resumeItems(Object.keys(stored), (key) => stored[key], now);
same(items.map((item) => item.entry.id), ['2048', 'chess', 'sudoku'], 'newest first, at most three, only saves with moves and known games; lost boards and backups are skipped');
check(items[0].href === '/arcade/2048?mode=free&seed=7' && items[0].steps === 12, 'a portable save resumes through its own route');
check(items[2].href === '/arcade/sudoku?mode=daily&seed=9&date=2026-10-24' && items[2].mode === 'daily', 'a daily browser save keeps its date');
same(resumeItems(['prismet.arcade.v1.2048.free'], () => portable(12, '2026-10-24T19:57:00.000Z', Object.assign(game2048.initial('7'), { grid: Array(16).fill(2048) })), now), [], 'a won portable board is not offered again');
check(ago(now - 30000, now) === 'just now' && ago(now - 5 * 60000, now) === '5 min ago' && ago(now - 3 * 3600000, now) === '3 h ago' && ago(now - 26 * 3600000, now) === 'yesterday' && ago(now - 72 * 3600000, now) === '3 days ago', 'relative times read naturally');

console.log(`✓ arcade-motion-check: ${checks} motion, cube-math and library checks passed`);
