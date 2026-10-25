// The Arcade's motion layer. The pure helpers decide what moved; the DOM helpers play it. Every effect answers
// something the player did, and all of it is off under prefers-reduced-motion.

// ── pure ────────────────────────────────────────────────────────────────────────────────────────────────────
/** Where every 2048 tile goes for a move, computed the way the engine merges (each tile merges at most once). */
export function slides2048(grid, direction, size = 4) {
  const next = Array(size * size).fill(0), moves = [];
  for (let line = 0; line < size; line++) {
    const positions = Array.from({ length: size }, (_, i) => direction === 'left' ? line * size + i : direction === 'right' ? line * size + size - 1 - i : direction === 'up' ? i * size + line : (size - 1 - i) * size + line);
    const sources = positions.filter((i) => grid[i]);
    let target = 0;
    for (let i = 0; i < sources.length; i++) {
      let value = grid[sources[i]];
      const to = positions[target++];
      moves.push({ from: sources[i], to });
      if (i + 1 < sources.length && grid[sources[i + 1]] === value) { value *= 2; i++; moves.push({ from: sources[i], to }); }
      next[to] = value;
    }
  }
  moves.sort((a, b) => a.from - b.from);
  return { moves, grid: next };
}
/** The square a new tile appeared on, or -1. */
export const spawnIndex = (before, after) => after.findIndex((value, i) => value && !before[i]);
/** Indexes whose text or tone changed between two views of the same board; null when there is no comparable view. */
export function cellChanges(prev, next) {
  if (!prev || !next || prev.length !== next.length) return null;
  const changed = [];
  for (let i = 0; i < next.length; i++) if ((prev[i].text || '') !== (next[i].text || '') || (prev[i].tone || '') !== (next[i].tone || '')) changed.push(i);
  return changed;
}
/** Reveal delays that grow with the Manhattan distance from the pressed square. */
export function cascadeDelays(indexes, origin, width, step = 18) {
  const row = Math.floor(origin / width), col = origin % width, delays = new Map();
  for (const i of indexes) delays.set(i, (Math.abs(Math.floor(i / width) - row) + Math.abs(i % width - col)) * step);
  return delays;
}
/**
 * Keyed FLIP matching: items present in both renders under the same key but at another place. Items that stayed put
 * pair first; whatever is left pairs with the nearest leftover of its key, so one moved checker never drags its twins.
 */
export function matchMoved(prev, next) {
  const pools = new Map();
  for (const item of prev) { if (!pools.has(item.key)) pools.set(item.key, []); pools.get(item.key).push(item.rect); }
  const samePlace = (a, b) => Math.abs(a.x - b.x) <= .5 && Math.abs(a.y - b.y) <= .5;
  const pending = [];
  for (const item of next) {
    const pool = pools.get(item.key);
    if (!pool?.length) continue;
    const still = pool.findIndex((rect) => samePlace(rect, item.rect));
    if (still >= 0) pool.splice(still, 1); else pending.push(item);
  }
  const moved = [];
  for (const item of pending) {
    const pool = pools.get(item.key);
    if (!pool?.length) continue;
    let best = 0, nearest = Infinity;
    pool.forEach((rect, i) => { const d = Math.hypot(rect.x - item.rect.x, rect.y - item.rect.y); if (d < nearest) { nearest = d; best = i; } });
    moved.push({ key: item.key, from: pool.splice(best, 1)[0], to: item.rect });
  }
  return moved;
}

// ── DOM ─────────────────────────────────────────────────────────────────────────────────────────────────────
const EASE = 'cubic-bezier(.2,.7,.2,1)';
export const reduced = () => typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
/** Slide an element in from where it was (FLIP: the layout is already final, the transform plays backwards). */
export function flyFrom(element, from, to, duration = 180) {
  if (reduced() || typeof element.animate !== 'function') return;
  const dx = from.x - to.x, dy = from.y - to.y;
  if (!dx && !dy) return;
  element.animate([{ transform: `translate(${dx}px,${dy}px)` }, { transform: 'none' }], { duration, easing: EASE });
}
/** A short settle after a change: scale down from a little larger (or smaller) to rest. */
export function pop(element, { delay = 0, from = 1.14, duration = 220 } = {}) {
  if (reduced() || typeof element.animate !== 'function') return;
  element.animate([{ transform: `scale(${from})`, offset: 0 }, { transform: 'none' }], { duration, delay, easing: EASE, fill: 'backwards' });
}
/** Record keyed rectangles of the current children so the next render can FLIP them. */
export function snapshotKeys(root, selector) {
  return [...root.querySelectorAll(selector)].map((element) => ({ key: element.dataset.key, rect: element.getBoundingClientRect() }));
}
/** After a render: fly every keyed element in from where its key was before. */
export function flyMoved(root, selector, before, duration) {
  if (!before?.length || reduced()) return;
  const after = [...root.querySelectorAll(selector)];
  const moved = matchMoved(before, after.map((element) => ({ key: element.dataset.key, rect: element.getBoundingClientRect() })));
  if (!moved.length) return;
  const byKey = new Map();
  for (const element of after) { const key = element.dataset.key; if (!byKey.has(key)) byKey.set(key, []); byKey.get(key).push(element); }
  const cursor = new Map();
  for (const entry of moved) {
    const list = byKey.get(entry.key) || [], start = cursor.get(entry.key) || 0;
    const element = list.slice(start).find((candidate) => { const r = candidate.getBoundingClientRect(); return Math.abs(r.x - entry.to.x) < .5 && Math.abs(r.y - entry.to.y) < .5; });
    if (!element) continue;
    cursor.set(entry.key, list.indexOf(element) + 1);
    flyFrom(element, entry.from, entry.to, duration);
  }
}
