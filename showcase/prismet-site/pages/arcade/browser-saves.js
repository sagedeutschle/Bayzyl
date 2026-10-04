// Browser-only facets deliberately use a separate contract from native portable saves.
import { byID } from './catalog.js';
import { uint64 } from './engines.js';
import { validDate, utcDate } from './saves.js';
export function browserDailySeed(id, date) {
  if (!Object.hasOwn(byID,id) || !validDate(date)) throw new Error('Invalid daily board');
  let hash = 14695981039346656037n;
  for (const char of `prismet-daily-v1:${id}:${date}`) hash = BigInt.asUintN(64,(hash ^ BigInt(char.charCodeAt(0))) * 1099511628211n);
  return hash.toString();
}
export function browserSettings(id, query, today = utcDate()) {
  if (!Object.hasOwn(byID,id) || ['mode','seed','date'].some(key=>query.getAll(key).length>1)) throw new Error('Invalid challenge parameters');
  const mode = query.get('mode') || (query.has('seed') ? 'challenge' : 'free');
  if (!['free','daily','challenge'].includes(mode)) throw new Error('Unknown mode');
  if (mode === 'daily') { const date = query.get('date') || today, seed = browserDailySeed(id,date); if(query.has('seed') && query.get('seed') !== seed) throw new Error('Daily seed mismatch'); return {mode,seed,dailyDate:date}; }
  if(query.has('date')) throw new Error('Date requires daily mode');
  const seed = query.get('seed'); if(mode==='challenge' && seed===null) throw new Error('Challenge requires a seed'); if(seed !== null && !uint64(seed)) throw new Error('Invalid seed'); return {mode,seed};
}
export const browserSlot = (save) => `prismet.arcade.browser.v1.${save.gameID}.${save.mode}${save.mode === 'daily' ? `.${save.dailyDate}` : save.mode === 'challenge' ? `.${save.seed}` : ''}`;
export function validateBrowserSave(save,engine) {
  try {
    if(!save || typeof save!=='object' || Array.isArray(save) || Object.keys(save).some(key=>!['browserVersion','gameID','seed','mode','dailyDate','state','steps','elapsedMs','savedAt'].includes(key)) || new TextEncoder().encode(JSON.stringify(save)).length>262144) return false;
    return save.browserVersion===1 && Object.hasOwn(byID,save.gameID) && !byID[save.gameID].portable && byID[save.gameID].category!=='Lenses' && uint64(save.seed) && ['free','daily','challenge'].includes(save.mode) && Number.isSafeInteger(save.steps) && save.steps>=0 && Number.isSafeInteger(save.elapsedMs) && save.elapsedMs>=0 && typeof save.savedAt==='string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(save.savedAt) && validDate(save.savedAt.slice(0,10)) && new Date(save.savedAt).toISOString()===save.savedAt && (save.mode==='daily' ? validDate(save.dailyDate) && save.seed===browserDailySeed(save.gameID,save.dailyDate) : save.dailyDate===undefined) && engine.validate(save.state) && (save.state.seed===undefined || save.state.seed===save.seed);
  } catch { return false; }
}
export function browserRoute(save) { const query = new URLSearchParams({mode:save.mode,seed:save.seed}); if(save.mode==='daily')query.set('date',save.dailyDate); return `/arcade/${save.gameID}?${query}`; }
