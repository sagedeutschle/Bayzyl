import { IDS, engines, uint64, dailySeed } from './engines.js';
export const SAVE_VERSION = 1;
export function utcDate(now = new Date()) { return now.toISOString().slice(0, 10); }
export function validDate(date) { try { return typeof date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(date) && utcDate(new Date(`${date}T00:00:00Z`)) === date; } catch { return false; } }
const bounded = (n) => Number.isSafeInteger(n) && n >= 0;
export function validateSave(value) {
  try {
    if (!value || typeof value !== 'object' || Array.isArray(value) || value.version !== 1 || value.stateVersion !== 1 || !IDS.includes(value.gameID) || !uint64(value.seed) || !['free', 'daily', 'challenge'].includes(value.mode) || !bounded(value.moves) || !bounded(value.elapsedMs) || typeof value.savedAt !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,3})?Z$/.test(value.savedAt) || (!Number.isFinite(Date.parse(value.savedAt)) || !validDate(value.savedAt.slice(0, 10)) || new Date(value.savedAt).toISOString().slice(0, 19) !== value.savedAt.slice(0, 19))) return false;
    if (Object.keys(value).some((k) => !['version', 'stateVersion', 'gameID', 'seed', 'mode', 'dailyDate', 'state', 'moves', 'elapsedMs', 'savedAt'].includes(k))) return false;
    if (value.mode === 'daily' ? !validDate(value.dailyDate) || dailySeed(value.gameID, value.dailyDate) !== value.seed : value.dailyDate !== undefined) return false;
    if (!engines[value.gameID].validate(value.state)) return false;
    if (value.gameID === '2048' && value.state.size !== 4) return false;
    if (value.gameID === 'minesweeper' && value.state.seed !== value.seed) return false;
    return true;
  } catch { return false; }
}
export function parseSave(text) { if (typeof text !== 'string' || new TextEncoder().encode(text).length > 65536) throw new Error('Choose a Prismet save smaller than 64 KiB.'); let value; try { value = JSON.parse(text); } catch { throw new Error('This file is not valid JSON.'); } if (!validateSave(value)) throw new Error('This save has an unsupported version or invalid game data. Your current game is unchanged.'); return value; }
export function newSave(gameID, seed, mode = 'free', dailyDate) {
  const value = { version: 1, stateVersion: 1, gameID, seed, mode, ...(mode === 'daily' ? { dailyDate } : {}), state: engines[gameID].initial(seed), moves: 0, elapsedMs: 0, savedAt: new Date().toISOString() };
  if (!validateSave(value)) throw new Error('Invalid game settings'); return value;
}
export const slot = (save) => `prismet.arcade.v1.${save.gameID}.${save.mode}${save.mode === 'daily' ? `.${save.dailyDate}` : save.mode === 'challenge' ? `.${save.seed}` : ''}`;
export function storageAdapter(storage) {
  return {
    read(name) { try { return { value: storage?.getItem(name) ?? null, ok: !!storage }; } catch { return { value: null, ok: false }; } },
    write(name, value) { try { if (!storage) return false; storage.setItem(name, value); return true; } catch { return false; } },
  };
}
export function restore(adapter, initial) { const stored = adapter.read(slot(initial)); if (!stored.value) return { save: initial, persistent: stored.ok, recovered: false }; try { const save = parseSave(stored.value); if (slot(save) !== slot(initial)) throw new Error('Wrong save slot'); return { save, persistent: stored.ok, recovered: true }; } catch { return { save: initial, persistent: stored.ok, recovered: false, invalid: true }; } }
export function completionDates(text) { try { const dates = JSON.parse(text); return Array.isArray(dates) ? [...new Set(dates.filter(validDate))].sort().slice(-366) : []; } catch { return []; } }
export function streak(dates, today = utcDate()) { let day = new Date(`${today}T00:00:00Z`), count = 0; const set = new Set(dates); if (!set.has(utcDate(day))) day.setUTCDate(day.getUTCDate() - 1); while (set.has(utcDate(day))) { count++; day.setUTCDate(day.getUTCDate() - 1); } return count; }
export function routeSettings(gameID, query, today = utcDate()) {
  if ([...query].some(([name]) => !['seed', 'mode', 'date'].includes(name) || query.getAll(name).length !== 1)) throw new Error('Unsupported challenge parameters');
  const date = query.get('date'), mode = query.get('mode') || (date ? 'daily' : query.has('seed') ? 'challenge' : 'free');
  if (!IDS.includes(gameID) || !['free', 'daily', 'challenge'].includes(mode)) throw new Error('Unknown challenge');
  if (mode === 'daily') { const day = date || today; if (!validDate(day)) throw new Error('Invalid challenge date'); const seed = dailySeed(gameID, day); if (query.has('seed') && query.get('seed') !== seed) throw new Error('Daily seed does not match its date'); return { mode, seed, dailyDate: day }; }
  if (date) throw new Error('Only daily challenges have dates');
  const seed = query.get('seed'); if (seed !== null && !uint64(seed)) throw new Error('Invalid challenge seed'); return { mode, seed };
}

export function routeForSave(save) {
  if (!validateSave(save)) throw new Error('Invalid game save');
  const query = new URLSearchParams({ mode: save.mode, seed: save.seed });
  if (save.mode === 'daily') query.set('date', save.dailyDate);
  return `/arcade/${save.gameID}?${query}`;
}
