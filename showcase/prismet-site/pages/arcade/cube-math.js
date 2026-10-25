// Pure geometry for the 3D cube. The engine (games/rubiks-cube.js) lives in model space: x right, y up, z toward
// the viewer, right-handed quarter turns about +axis. CSS 3D space has y pointing down, so a model point (x, y, z)
// is drawn at (x, −y, z) and every model rotation about x or z plays with the opposite sign; rotations about y keep
// theirs. All of this is covered by tools/tests/arcade-motion-check.mjs.
export const FACES = { U: [1, 1], D: [1, -1], L: [0, -1], R: [0, 1], F: [2, 1], B: [2, -1] };   // face → [axis, layer]
const FLIP = [1, -1, 1];
const AXES = [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
export function faceFor(axis, layer) { for (const [face, [a, l]] of Object.entries(FACES)) if (a === axis && l === layer) return face; return null; }
export const inverse = (move) => move.endsWith('2') ? move : move.endsWith("'") ? move[0] : `${move}'`;
const quartersOf = (move) => move.endsWith('2') ? 2 : move.endsWith("'") ? 3 : 1;
const clean = (n) => (Math.abs(n) < 1e-9 ? 0 : Math.round(n * 1e6) / 1e6);

/** The CSS transform that places a cubie: its orientation columns conjugated by the y flip, its position scaled. */
export function cssMatrix({ position, orientation }, size) {
  const column = (j) => [0, 1, 2].map((i) => clean(FLIP[i] * FLIP[j] * orientation[j][i]));
  const t = [clean(position[0] * size), clean(-position[1] * size), clean(position[2] * size)];
  return `matrix3d(${[...column(0), 0, ...column(1), 0, ...column(2), 0, ...t, 1].join(',')})`;
}
/** The pivot rotation that plays a move in CSS: rotate3d(axis, deg). */
export function turnAngle(move) {
  const [axis] = FACES[move[0]], quarters = quartersOf(move);
  const model = quarters === 2 ? 180 : quarters === 3 ? -90 : 90;
  return { axis: AXES[axis], deg: axis === 1 ? model : -model };
}
const rad = (deg) => deg * Math.PI / 180;
const multiply = (a, b) => a.map((row, i) => [0, 1, 2].map((j) => row[0] * b[0][j] + row[1] * b[1][j] + row[2] * b[2][j]));
/** `transform: rotateX(ax) rotateY(ay)` as a numeric matrix (CSS convention). */
export function orbitMatrix(ax, ay) {
  const cx = Math.cos(rad(ax)), sx = Math.sin(rad(ax)), cy = Math.cos(rad(ay)), sy = Math.sin(rad(ay));
  const rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]], ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]];
  return multiply(rx, ry).map((row) => row.map(clean));
}
export const applyMatrix = (m, v) => m.map((row) => row[0] * v[0] + row[1] * v[1] + row[2] * v[2]);
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const toCss = (v) => [v[0] * FLIP[0], v[1] * FLIP[1], v[2] * FLIP[2]];

/**
 * A drag that started on a sticker, resolved to the engine move whose on-screen motion best matches it.
 * `cubie.position` and `normal` are in model space; `drag` is in screen pixels; `orbit` is the current orbitMatrix.
 */
export function resolveDrag({ cubie, normal, drag, orbit, minimum = 14 }) {
  const length = Math.hypot(drag.x, drag.y);
  if (length < minimum) return null;
  const p = toCss(cubie.position), n = toCss(normal), r = p.map((c, i) => c + n[i] * .5);
  let best = null;
  for (let axis = 0; axis < 3; axis++) {
    if (n[axis] !== 0) continue;                 // a sticker cannot turn about its own normal
    const layer = cubie.position[axis];
    if (Math.abs(layer) !== 1) continue;         // the middle slice is not a face move
    for (const spin of [1, -1]) {
      const omega = AXES[axis].map((c) => c * spin), v = applyMatrix(orbit, cross(omega, r));
      const magnitude = Math.hypot(v[0], v[1]);
      if (magnitude < 1e-6) continue;
      const score = (v[0] * drag.x + v[1] * drag.y) / (magnitude * length);
      if (score > (best?.score ?? .45)) best = { score, axis, spin, layer };
    }
  }
  if (!best) return null;
  const modelSpin = best.axis === 1 ? best.spin : -best.spin;
  const face = faceFor(best.axis, best.layer);
  return modelSpin === 1 ? face : `${face}'`;
}
/** What changed between two move histories: [] nothing, [move] one turn to play, null a new board to snap to. */
export function movesBetween(prev, next) {
  const prefix = (a, b) => a.every((move, i) => move === b[i]);
  if (prev.length === next.length) return prefix(prev, next) ? [] : null;
  if (next.length === prev.length + 1 && prefix(prev, next)) return [next[next.length - 1]];
  if (prev.length === next.length + 1 && prefix(next, prev)) return [inverse(prev[prev.length - 1])];
  return null;
}
const LIGHT = (() => { const l = [.35, -.8, .5], m = Math.hypot(...l); return l.map((c) => c / m); })();
/** How much light a sticker facing `normal` (model space) catches under the orbit: 0.55 in shadow, 1 facing the light. */
export function shade(normal, orbit) {
  const world = applyMatrix(orbit, toCss(normal));
  const lambert = Math.max(0, world[0] * LIGHT[0] + world[1] * LIGHT[1] + world[2] * LIGHT[2]);
  return Math.round((0.55 + 0.45 * lambert) * 1000) / 1000;
}
