import { uint64 } from '../engines.js';
export { uint64 };
export const int = (x, min, max) => Number.isSafeInteger(x) && x >= min && x <= max;
export const array = (x, n, predicate) => Array.isArray(x) && x.length === n && x.every(predicate);
export const same = (a,b) => JSON.stringify(a) === JSON.stringify(b);
export function restoredRNG(seed) {
  if (!uint64(seed)) throw new Error('Invalid random state');
  let state = BigInt(seed);
  return { next(n) { state = (state * 6364136223846793005n + 1442695040888963407n) & ((1n << 64n)-1n); return Number(state % BigInt(n)); }, get state() { return String(state); } };
}
