import { preserveImport } from './import-backup.js';
import { refreshAppSelection } from './app-shell.js';
import { mountRoom } from './room.js';
import { mountCloud } from './cloud.js';
import { engines, IDS, adjacent, dailySeed } from './engines.js';
import { newSave, parseSave, slot, restore, storageAdapter, utcDate, completionDates, streak, routeSettings, routeForSave } from './saves.js';
const $ = (id) => document.getElementById(id);
const descriptions = {
  '2048': { title: '2048', guide: 'Make a little room.', instructions: 'Slide the board to combine equal tiles. Every successful move adds a new tile. Reach 2048, or keep going for a higher score.', input: 'Focus the board and use arrow keys or W A S D. Swipe across it, or use the direction buttons below.', note: 'The native merge rule combines each tile at most once per move. A no-op consumes no random draws. A 64-bit seeded generator chooses each new tile and its position.' },
  minesweeper: { title: 'Minesweeper', guide: 'Every number tells a story.', instructions: 'Reveal every safe square. A number counts mines in the eight neighboring squares. Your first reveal is always safe; flags help you keep track.', input: 'Tap to reveal. Turn on Flag mode to mark squares, or right-click. Use arrows to move focus, Enter or Space to act, and F to flag the focused square. On small screens, scroll the field sideways.', note: 'Mines are placed from the seed after your first reveal, avoiding that square. Seed and first-square choice together determine the field. Empty areas reveal with a bounded flood fill.' },
  'lights-out': { title: 'Lights Out', guide: 'Think one light ahead.', instructions: 'Press a light to flip it and its horizontal and vertical neighbors. Turn every light off to solve the board.', input: 'Tap any light. Use arrows to move focus, then Enter or Space to press. Undo lets you explore a different sequence.', note: 'Each board begins dark and receives ten seeded legal presses. That construction guarantees a solution: replay the same presses, in any order. Pressing the same square twice cancels itself.' },
};
let gameID = location.pathname.split('/').filter(Boolean).at(-1);
if (!IDS.includes(gameID)) gameID = '2048';
let browserStorage; try { browserStorage = localStorage; } catch { /* Private/blocked storage: play still works. */ }
const storage = storageAdapter(browserStorage);
const randomSeed = () => { const words = new Uint32Array(2); crypto.getRandomValues(words); return ((BigInt(words[0]) << 32n) | BigInt(words[1])).toString(); };
let settings, routeError = '';
try { settings = routeSettings(gameID, new URLSearchParams([...new URLSearchParams(location.search)].filter(([name]) => !['room', 'host'].includes(name)))); } catch (error) { settings = { mode: 'free', seed: null }; routeError = `${error.message}. Opened free play instead.`; }
const initial = newSave(gameID, settings.seed || randomSeed(), settings.mode, settings.dailyDate);
const resumed = restore(storage, initial);
let save = resumed.save, undo = [], flagMode = false, focusedCell = 0, lastTick = performance.now(), importPending = false;
let persistent = resumed.persistent, completionRecorded = false, localBeforeRoom = null;
function currentStatus() { return engines[gameID].status(save.state); }
function elapsed() {
  const now = performance.now();
  if (!document.hidden && save.moves > 0 && currentStatus() === 'playing') save.elapsedMs = Math.min(Number.MAX_SAFE_INTEGER, save.elapsedMs + Math.max(0, Math.round(now - lastTick)));
  lastTick = now;
}
function timeLabel(ms) { const seconds = Math.floor(ms / 1000); return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`; }
function persist() { if (localBeforeRoom) return; save.savedAt = new Date().toISOString(); if (!storage.write(slot(save), JSON.stringify(save))) persistent = false; }
function updateNotice(message = '') { $('save-notice').textContent = message || (persistent ? 'Saved on this device. No account needed.' : 'Browser storage is unavailable. You can still play; export a save before leaving.'); }
function completionKey() { return `prismet.arcade.v1.completed.${gameID}`; }
function updateStreak() {
  const dates = completionDates(storage.read(completionKey()).value);
  const count = streak(dates);
  $('streak').textContent = save.mode === 'daily' ? `${save.dailyDate} · UTC. ${count ? `${count}-day local streak.` : 'Complete a daily board to begin a local streak.'}` : 'Send the same starting board to a friend. Your progress stays private.';
}
function recordCompletion() {
  if (localBeforeRoom || completionRecorded || save.mode !== 'daily' || currentStatus() !== 'won') return;
  const dates = completionDates(storage.read(completionKey()).value); dates.push(save.dailyDate);
  if (!storage.write(completionKey(), JSON.stringify([...new Set(dates)].sort().slice(-366)))) persistent = false;
  completionRecorded = true; updateStreak();
}
function heading() {
  refreshAppSelection(gameID);
  const info = descriptions[gameID]; document.title = `${info.title} · Prismet Arcade`;
  $('game-title').textContent = info.title; $('guide-title').textContent = info.guide; $('instructions').textContent = info.instructions; $('input-hint').textContent = info.input; $('engine-note').textContent = info.note;
  $('game-kicker').textContent = save.mode === 'daily' ? 'The daily board · UTC' : save.mode === 'challenge' ? 'A shared challenge' : 'Prismet Arcade · free play';
  $('free-link').href = `/arcade/${gameID}`; $('daily-link').href = `/arcade/${gameID}?mode=daily`;
  for (const [id, mode] of [['free-link', 'free'], ['daily-link', 'daily']]) { $(id).removeAttribute('aria-current'); if (save.mode === mode) $(id).setAttribute('aria-current', 'page'); }
  $('score-label').textContent = gameID === '2048' ? 'Score' : gameID === 'minesweeper' ? 'Mines / flags' : 'Lights on';
  $('seed-label').textContent = save.seed;
  $('challenge-label').textContent = save.mode === 'daily' ? 'One date, one challenge' : 'Your own pace';
  $('challenge-title').textContent = save.mode === 'daily' ? 'Today, a little further.' : 'A board worth sharing.';
  $('room-panel').hidden = gameID !== 'lights-out';
  $('direction-pad').hidden = gameID !== '2048'; $('flag-mode').hidden = gameID !== 'minesweeper'; $('undo').hidden = gameID === 'minesweeper'; $('new-game').hidden = save.mode !== 'free';
  $('board').dataset.game = gameID; $('board').setAttribute('aria-label', `${info.title} board`); $('board').tabIndex = gameID === '2048' ? 0 : -1;
  const nativeURL = new URL(`prismet://arcade/${gameID}`); nativeURL.searchParams.set('seed', save.seed); nativeURL.searchParams.set('mode', save.mode === 'daily' ? 'daily' : 'challenge'); if (save.mode === 'daily') nativeURL.searchParams.set('date', save.dailyDate); $('open-native').href = nativeURL.href;
  updateStreak();
}
function render(focus = false) {
  const board = $('board'), state = save.state, status = currentStatus();
  const activeIndex = Number(document.activeElement?.dataset?.index); if (Number.isInteger(activeIndex)) focusedCell = activeIndex;
  const fragment = document.createDocumentFragment();
  if (gameID === '2048') {
    state.grid.forEach((value, i) => { const tile = document.createElement('span'); tile.className = `tile${value >= 128 ? ' high' : ''}`; tile.dataset.value = value; tile.textContent = value || ''; tile.setAttribute('aria-label', `Row ${Math.floor(i / 4) + 1}, column ${i % 4 + 1}: ${value || 'empty'}`); fragment.append(tile); });
    $('score').textContent = state.score.toLocaleString();
  } else {
    const size = gameID === 'minesweeper' ? state.width * state.height : 25, width = gameID === 'minesweeper' ? state.width : 5;
    if (gameID === 'minesweeper') { board.style.gridTemplateColumns = `repeat(${width},minmax(44px,1fr))`; board.style.setProperty('--mine-width', `${width * 44 + (width - 1) * 3}px`); } else { board.style.removeProperty('grid-template-columns'); board.style.removeProperty('--mine-width'); }
    for (let i = 0; i < size; i++) {
      const button = document.createElement('button'); button.type = 'button'; button.dataset.index = i; button.tabIndex = i === focusedCell ? 0 : -1;
      let label = `Row ${Math.floor(i / width) + 1}, column ${i % width + 1}`;
      if (gameID === 'lights-out') { const lit = state.grid[i]; button.className = `tile light-cell${lit ? ' lit' : ''}`; button.setAttribute('aria-pressed', String(lit)); label += lit ? ', light on' : ', light off'; }
      else { const revealed = state.revealed.includes(i), flagged = state.flagged.includes(i), mine = state.mines.includes(i), showMine = mine && status !== 'playing', count = adjacent(state, i); button.className = `tile mine-cell${revealed ? ' revealed' : ''}${flagged ? ' flagged' : ''}${showMine ? ' mine' : ''}`; button.dataset.count = revealed ? count : ''; button.textContent = showMine ? '✳' : flagged ? '⚑' : revealed ? count || '' : ''; label += showMine ? ', mine' : flagged ? ', flagged' : revealed ? `, ${count} adjacent mines` : ', covered'; }
      button.setAttribute('aria-label', label); fragment.append(button);
    }
    $('score').textContent = gameID === 'minesweeper' ? `${state.mineCount} / ${state.flagged.length}` : state.grid.filter(Boolean).length;
  }
  board.replaceChildren(fragment);
  $('moves').textContent = save.moves.toLocaleString(); $('timer').textContent = timeLabel(save.elapsedMs);
  $('undo').disabled = !undo.length;
  $('outcome').textContent = status === 'won' ? gameID === '2048' ? '2048 reached. Keep going, or enjoy the win.' : 'Solved. Beautifully done.' : status === 'lost' ? gameID === 'minesweeper' ? 'A mine. Try the same board again, or start fresh.' : 'No moves left. Restart, or undo the last move.' : save.moves ? gameID === 'lights-out' ? `${state.grid.filter(Boolean).length} lights left to turn off.` : 'Your next move.' : 'Ready when you are.';
  if (focus && gameID !== '2048') board.querySelector(`[data-index="${focusedCell}"]`)?.focus({ preventScroll: true });
}
function act(action) {
  if (save.moves >= Number.MAX_SAFE_INTEGER) { updateNotice('This save has reached the move limit. Start a new board.'); return; }
  if (localBeforeRoom) { window.dispatchEvent(new CustomEvent('prismet:room-action', { detail: action })); return; }
  if (currentStatus() !== 'playing' && gameID !== '2048') return;
  elapsed(); const next = engines[gameID].apply(save.state, action); if (next === save.state) return;
  if (gameID !== 'minesweeper') { undo.push({ state: structuredClone(save.state), moves: save.moves }); if (undo.length > 64) undo.shift(); }
  save.state = next; save.moves++; persist(); recordCompletion(); render(document.activeElement?.closest('#board') !== null); updateNotice();
  window.dispatchEvent(new CustomEvent('prismet:arcade-save', { detail: structuredClone(save) }));
}
$('board').addEventListener('click', (event) => { const cell = event.target.closest('[data-index]'); if (!cell) return; focusedCell = Number(cell.dataset.index); act({ type: gameID === 'lights-out' ? 'press' : flagMode ? 'flag' : 'reveal', index: focusedCell }); });
$('board').addEventListener('contextmenu', (event) => { if (gameID !== 'minesweeper') return; const cell = event.target.closest('[data-index]'); if (!cell) return; event.preventDefault(); focusedCell = Number(cell.dataset.index); act({ type: 'flag', index: focusedCell }); });
const directions = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', w: 'up', a: 'left', s: 'down', d: 'right' };
$('board').addEventListener('keydown', (event) => {
  if (event.altKey || event.ctrlKey || event.metaKey) return;
  const direction = directions[event['key']];
  if (gameID === '2048') { if (direction) { event.preventDefault(); act({ direction }); } return; }
  const width = gameID === 'minesweeper' ? save.state.width : 5, height = gameID === 'minesweeper' ? save.state.height : 5;
  if (direction) { event.preventDefault(); const row = Math.floor(focusedCell / width), col = focusedCell % width; focusedCell = Math.max(0, Math.min(height - 1, row + (direction === 'up' ? -1 : direction === 'down' ? 1 : 0))) * width + Math.max(0, Math.min(width - 1, col + (direction === 'left' ? -1 : direction === 'right' ? 1 : 0))); for (const cell of $('board').children) cell.tabIndex = Number(cell.dataset.index) === focusedCell ? 0 : -1; $('board').querySelector(`[data-index="${focusedCell}"]`)?.focus(); }
  if (gameID === 'minesweeper' && event['key'].toLowerCase() === 'f') { event.preventDefault(); act({ type: 'flag', index: focusedCell }); }
});
let touchStart = null;
$('board').addEventListener('pointerdown', (event) => { if (gameID === '2048' && event.pointerType !== 'mouse') touchStart = { x: event.clientX, y: event.clientY }; });
$('board').addEventListener('pointerup', (event) => { if (!touchStart || gameID !== '2048') return; const dx = event.clientX - touchStart.x, dy = event.clientY - touchStart.y; touchStart = null; if (Math.max(Math.abs(dx), Math.abs(dy)) < 24) return; act({ direction: Math.abs(dx) > Math.abs(dy) ? dx > 0 ? 'right' : 'left' : dy > 0 ? 'down' : 'up' }); });
$('board').addEventListener('pointercancel', () => { touchStart = null; });
document.querySelectorAll('[data-direction]').forEach((button) => button.addEventListener('click', () => act({ direction: button.dataset.direction })));
$('flag-mode').addEventListener('click', () => { flagMode = !flagMode; $('flag-mode').setAttribute('aria-pressed', String(flagMode)); $('flag-mode').textContent = flagMode ? 'Flag mode on' : 'Flag mode'; });
$('undo').addEventListener('click', () => { if (localBeforeRoom || !undo.length) return; elapsed(); const previous = undo.pop(); save.state = previous.state; save.moves = previous.moves; persist(); render(); updateNotice(); });
function restart(fresh) { if (localBeforeRoom) { updateNotice('Leave the room before starting a personal board.'); return; } if (save.moves && !confirm(fresh ? 'Start a new board? Your current board will be replaced.' : 'Restart this board? Your current progress will be replaced.')) return; save = newSave(gameID, fresh ? randomSeed() : save.seed, save.mode, save.dailyDate); undo = []; focusedCell = 0; completionRecorded = false; lastTick = performance.now(); persist(); heading(); render(); updateNotice(); }
$('restart').addEventListener('click', () => restart(false)); $('new-game').addEventListener('click', () => restart(true));
function challengeURL() { const url = new URL(`/arcade/${gameID}`, location.origin); url.searchParams.set('mode', save.mode === 'daily' ? 'daily' : 'challenge'); url.searchParams.set('seed', save.seed); if (save.mode === 'daily') url.searchParams.set('date', save.dailyDate); return url.href; }
$('share').addEventListener('click', async () => { const url = challengeURL(); try { await navigator.clipboard.writeText(url); $('share').textContent = 'Challenge link copied'; } catch { $('share-fallback').hidden = false; $('share-url').value = url; $('share-url').focus(); $('share-url').select(); } });
$('export-save').addEventListener('click', () => { elapsed(); persist(); const blob = new Blob([JSON.stringify(save, null, 2)], { type: 'application/json' }), url = URL.createObjectURL(blob), link = document.createElement('a'); link.href = url; link.download = `prismet-${gameID}-${save.mode}.json`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); $('transfer-status').textContent = 'Save exported. Your current board is unchanged.'; });
async function importSave(incoming) {
  if (localBeforeRoom) throw new Error('Leave the room before importing a personal save.');
  if (importPending) return false;
  importPending = true;
  try { const candidate = parseSave(typeof incoming === 'string' ? incoming : JSON.stringify(incoming)); if (!confirm(`Import ${descriptions[candidate.gameID].title} (${candidate.moves} moves)? This replaces the visible session. Export your current save first if you need a separate copy.`)) return false; elapsed(); const backupID = `${Date.now()}.${crypto.randomUUID()}`; preserveImport(storage,save,slot(candidate),'prismet.arcade.v1.import-backup',backupID); const backedUp = true; save = candidate; gameID = candidate.gameID; undo = []; focusedCell = 0; completionRecorded = currentStatus() === 'won'; lastTick = performance.now(); history.replaceState(null, '', routeForSave(save)); persist(); heading(); render(); updateNotice(); $('transfer-status').textContent = backedUp ? 'Save imported. Your previous session remains in the local import backup.' : 'Save imported. Browser storage is unavailable; export this session before leaving.'; return true; }
  finally { importPending = false; }
}
$('import-save').addEventListener('change', async (event) => { const file = event.target.files[0]; if (!file) return; try { if (file.size > 65536) throw new Error('Choose a save smaller than 64 KiB.'); await importSave(await file.text()); } catch (error) { $('transfer-status').textContent = error.message; } event.target.value = ''; });
// Explicit, validated integration hooks for opt-in sync/native handoff. No background transfer.
window.PrismetArcade = Object.freeze({
  snapshot: () => { elapsed(); return structuredClone(save); }, importSave, challengeURL,
  isRoomActive: () => !!localBeforeRoom,
  beginRoom() { if (gameID !== 'lights-out' || localBeforeRoom) return false; if (!confirm('Join a cooperative board? Your personal game will be preserved and restored when you leave. The room host controls the shared state.')) return false; elapsed(); persist(); localBeforeRoom = structuredClone(save); undo = []; $('undo').disabled = true; $('restart').disabled = true; $('new-game').disabled = true; return true; },
  roomState(envelope) { const next = parseSave(JSON.stringify(envelope)); if (!localBeforeRoom || next.gameID !== 'lights-out') throw new Error('Not in a Lights Out room'); save = next; focusedCell = Math.min(24, focusedCell); lastTick = performance.now(); heading(); render(document.activeElement?.closest('#board') !== null); updateNotice('Cooperative session. Your personal board is safely set aside.'); },
  endRoom() { if (!localBeforeRoom) return; save = localBeforeRoom; localBeforeRoom = null; undo = []; lastTick = performance.now(); $('restart').disabled = false; $('new-game').disabled = false; heading(); render(); updateNotice('Left the room. Your personal board is restored.'); },
});
void mountCloud($('cloud-panel'), window.PrismetArcade);
mountRoom($('room-panel'), window.PrismetArcade);
document.addEventListener('visibilitychange', () => { if (document.hidden) { /* account for the last visible interval before pausing */ const now = performance.now(); if (save.moves > 0 && currentStatus() === 'playing') save.elapsedMs = Math.min(Number.MAX_SAFE_INTEGER, save.elapsedMs + Math.max(0, Math.round(now - lastTick))); lastTick = now; persist(); } else lastTick = performance.now(); });
window.addEventListener('pagehide', () => { elapsed(); persist(); });
let ticks = 0;
setInterval(() => { elapsed(); $('timer').textContent = timeLabel(save.elapsedMs); if (++ticks % 5 === 0 && save.moves > 0) persist(); }, 1000);
heading(); render(); updateNotice(routeError || (resumed.invalid ? 'An unreadable local save was ignored. A fresh board is ready; you can still import a valid backup.' : resumed.recovered ? 'Your saved board is ready. Progress stays on this device.' : ''));
