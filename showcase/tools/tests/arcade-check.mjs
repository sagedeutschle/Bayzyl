// Pure game rules, native replay fixtures and persistence/import boundaries; no browser required.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { engines, generator, uint64, dailySeed, game2048, lightsOut, minesweeper, adjacent } from '../../prismet-site/pages/arcade/engines.js';
import { newSave, validateSave, parseSave, storageAdapter, restore, slot, completionDates, streak, routeSettings, routeForSave, validDate } from '../../prismet-site/pages/arcade/saves.js';
let checks = 0;
const check = (ok, message) => { assert.ok(ok, message); checks++; };
const equal = (a, b, message) => { assert.deepEqual(a, b, message); checks++; };
const rejects = (fn, message) => { assert.throws(fn, undefined, message); checks++; };
const fixtures = JSON.parse(readFileSync(new URL('./fixtures/arcade/native-parity.json', import.meta.url)));
for (const f of fixtures) {
  const engine = engines[f.gameID]; let state = engine.initial(f.seed);
  equal(state, f.initial, `${f.gameID}/${f.seed}: native initial state`);
  for (const action of f.actions) { const before = JSON.stringify(state); const input = state; state = engine.apply(state, action); equal(JSON.stringify(input), before, 'apply never mutates input'); check(engine.validate(state), 'native replay remains valid'); }
  equal(state, f.final, `${f.gameID}/${f.seed}: native final replay`);
  const envelope = { ...newSave(f.gameID, f.seed), state, moves: f.moves };
  check(validateSave(envelope), 'native replay portable'); equal(parseSave(JSON.stringify(envelope)), envelope, 'portable roundtrip');
}
const blank = { size: 4, grid: Array(16).fill(0), score: 0, rngState: '1' };
let merge = { ...blank, grid: [2, 2, 4, 0, ...Array(12).fill(0)] };
let moved = game2048.apply(merge, { direction: 'left', spawn: false });
equal(moved.grid.slice(0, 4), [4, 4, 0, 0], 'newly merged tile cannot chain merge'); equal(moved.score, 4, 'merge score');
merge = { ...blank, grid: [2, 2, 2, 2, ...Array(12).fill(0)] }; moved = game2048.apply(merge, { direction: 'right', spawn: false });
equal(moved.grid.slice(0, 4), [0, 0, 4, 4], 'right merge preserves direction'); equal(moved.score, 8, 'two independent merges');
const noOp = { ...blank, grid: [2, ...Array(15).fill(0)] };
check(game2048.apply(noOp, { direction: 'left' }) === noOp, 'no-op preserves board, score and RNG');
check(game2048.apply(noOp, { direction: 'bad' }) === noOp, 'invalid direction ignored');
equal(game2048.status({ ...blank, grid: [2,4,2,4,4,2,4,2,2,4,2,4,4,2,4,2] }), 'lost', 'full locked board loses');
equal(game2048.status({ ...blank, grid: [2048, ...Array(15).fill(0)] }), 'won', '2048 recognized');
check(!game2048.validate({ ...blank, grid: [3, ...Array(15).fill(0)] }), 'non-power tiles rejected');
for (const seed of ['0','1','7','42','18446744073709551615']) {
  for (const index of [0, 40, 80]) { const state = minesweeper.apply(minesweeper.initial(seed), { type: 'reveal', index }); check(!state.mines.includes(index), 'first reveal safe'); equal(state.mines.length, 10, 'exact mine count'); check(minesweeper.validate(state), 'first reveal valid'); }
  let state = lightsOut.initial(seed); const rng = generator(seed), presses = [];
  for (let i = 0; i < 10; i++) presses.push(rng.next(5) * 5 + rng.next(5));
  for (const index of presses) state = lightsOut.apply(state, { type: 'press', index });
  // A vanishing scramble adds a fixed corner press in the native algorithm.
  if (state.grid.some(Boolean)) state = lightsOut.apply(state, { type: 'press', index: 0 });
  equal(lightsOut.status(state), 'won', 'seeded scramble has a legal solution');
}
let field = { width: 3, height: 3, mineCount: 1, seed: '1', mines: [8], revealed: [], flagged: [], hasPlacedMines: true, status: 'playing' };
equal(adjacent(field, 4), 1, 'eight-neighbor count');
let flagged = minesweeper.apply(field, { type: 'flag', index: 0 }); check(minesweeper.apply(flagged, { type: 'reveal', index: 0 }) === flagged, 'flag prevents accidental reveal');
let won = minesweeper.apply(field, { type: 'reveal', index: 0 }); equal(won.status, 'won', 'flood reveals all safe cells'); check(minesweeper.apply(won, { type: 'flag', index: 8 }) === won, 'finished field immutable');
let lost = minesweeper.apply(field, { type: 'reveal', index: 8 }); equal(lost.status, 'lost', 'mine loses'); check(minesweeper.validate(lost), 'lost state consistent');
check(!minesweeper.validate({ ...field, mines: [8,8] }), 'duplicate mine rejected'); check(!minesweeper.validate({ ...field, status: 'won' }), 'false win rejected'); check(!minesweeper.validate({ ...field, flagged: [0], revealed: [0] }), 'revealed flag conflict rejected');
let dark = { grid: Array(25).fill(false) };
const corner = lightsOut.apply(dark, { type: 'press', index: 0 }); equal(corner.grid.filter(Boolean).length, 3, 'corner toggles three'); equal(lightsOut.apply(corner, { type: 'press', index: 0 }), dark, 'repeat press cancels'); equal(lightsOut.apply(dark, { type: 'press', index: 12 }).grid.filter(Boolean).length, 5, 'center toggles five');
for (const seed of ['-1','01','18446744073709551616',1,null,'1.2']) check(!uint64(seed), 'strict decimal UInt64');
check(validDate('2024-02-29') && !validDate('2026-02-29') && !validDate('2026-02-30'), 'real UTC dates');
const base = newSave('2048', '42');
for (const patch of [{ version: 2 }, { stateVersion: 2 }, { gameID: 'unknown' }, { seed: 42 }, { moves: -1 }, { elapsedMs: Infinity }, { savedAt: '2026-02-30T00:00:00Z' }, { savedAt: '2026-10-04T24:00:00Z' }, { dailyDate: '2026-10-04' }, { extra: true }]) check(!validateSave({ ...base, ...patch }), 'malformed envelope rejected');
rejects(() => parseSave('{'), 'invalid JSON'); rejects(() => parseSave(' '.repeat(65537)), 'byte cap'); rejects(() => parseSave('🎲'.repeat(17000)), 'UTF8 byte cap');
const daily = newSave('lights-out', dailySeed('lights-out', '2026-10-04'), 'daily', '2026-10-04'); check(validateSave(daily), 'daily envelope'); check(!validateSave({ ...daily, seed: '1' }), 'daily seed cannot disagree with date');
equal(routeSettings('lights-out', new URLSearchParams('mode=daily'), '2026-10-04'), { mode: 'daily', seed: daily.seed, dailyDate: '2026-10-04' }, 'stable UTC daily route');
rejects(() => routeSettings('2048', new URLSearchParams('seed=1&seed=2')), 'ambiguous query'); rejects(() => routeSettings('2048', new URLSearchParams('mode=daily&date=2026-02-30')), 'invalid daily query');
const memory = new Map(), adapter = storageAdapter({ getItem: (k) => memory.get(k), setItem: (k,v) => memory.set(k,v) });
adapter.write(slot(base), JSON.stringify(base)); equal(restore(adapter, newSave('2048', '7')).save, base, 'free play resumes same save despite newly generated seed');
adapter.write(slot(base), '{'); check(restore(adapter, base).invalid, 'corrupt storage ignored safely');
const denied = storageAdapter({ getItem() { throw Error('denied'); }, setItem() { throw Error('denied'); } }); check(!denied.write('x','x') && !restore(denied, base).persistent, 'storage denial nonfatal');
check(slot(base) !== slot({ ...base, mode: 'daily', dailyDate: '2026-10-04' }) && slot(base) !== slot({ ...base, mode: 'challenge' }), 'daily, free and challenge isolated');
equal(completionDates('["2026-10-02","bad","2026-10-02","2026-10-03"]'), ['2026-10-02','2026-10-03'], 'completion records sanitize'); equal(streak(['2026-10-02','2026-10-03'], '2026-10-04'), 2, 'yesterday streak held through current day'); equal(streak(['2026-10-02'], '2026-10-04'), 0, 'missed UTC day breaks streak');
for (const imported of [base, daily, newSave('minesweeper', '7', 'challenge')]) {
  const route = new URL(routeForSave(imported), 'https://prismet.xyz');
  const settings = routeSettings(imported.gameID, route.searchParams);
  const restoredInitial = newSave(imported.gameID, settings.seed, settings.mode, settings.dailyDate);
  equal(slot(restoredInitial), slot(imported), 'import URL reload selects the correct save slot');
}
// Explicit cloud transport and cooperative authority contracts.
const { cloudClient } = await import('../../prismet-site/pages/arcade/cloud.js');
const requests = []; let responseStatus = 200, responseValue = { revision: 1, envelope: base, updatedAt: base.savedAt };
const cloud = cloudClient(async (url, options) => { requests.push({ url, options }); return { ok: responseStatus < 400, status: responseStatus, text: async () => JSON.stringify(responseValue) }; });
equal((await cloud.load(base))['envelope'], base, 'cloud record validated');
check(requests[0].url === '/api/arcade/progress/2048?slot=free' && requests[0].options.credentials === 'same-origin', 'same-origin cookie request');
responseValue = { revision: 2, envelope: base, updatedAt: base.savedAt }; await cloud.save(base, 1); equal(JSON.parse(requests.at(-1).options.body).expectedRevision, 1, 'optimistic revision included');
responseValue = { revision: 1, envelope: base, updatedAt: base.savedAt }; await assert.rejects(cloud.save(base, 1)); checks++;
responseValue = { revision: 2, envelope: { ...base, moves: 99 }, updatedAt: base.savedAt }; await assert.rejects(cloud.save(base, 1)); checks++;
responseValue = { revision: 2, envelope: base, updatedAt: '2026-02-30T00:00:00Z' }; await assert.rejects(cloud.save(base, 1)); checks++;
responseStatus = 409; responseValue = { error: 'Newer save exists' };
await assert.rejects(cloud.save(base, 1), (error) => error.status === 409); checks++;
responseStatus = 200; responseValue = { revision: 1, envelope: { ...base, gameID: 'lights-out' } };
await assert.rejects(cloud.load(base)); checks++;
responseValue = { revision: 0, envelope: null, updatedAt: null }; equal(await cloud.load(base), responseValue, 'empty cloud slot is explicit');
const { hostSession, actionMessage, acceptHostState, validRoomMessage } = await import('../../prismet-site/pages/arcade/room-protocol.js');
const session = hostSession(newSave('lights-out', '42'), 'session_123');
const start = session.snapshot(), press = actionMessage('session_123', 0, 12, 'action_123');
const result = session.receive(press); check(result.accepted, 'host accepts valid action'); equal(result.message.revision, 1, 'host advances revision once'); equal(result.message['envelope'].state, lightsOut.apply(start['envelope'].state, { type: 'press', index: 12 }), 'host uses canonical engine');
check(!session.receive(press).accepted, 'duplicate action rejected');
check(!session.receive(actionMessage('session_123', 0, 3, 'action_456')).accepted, 'stale revision rejected');
check(!session.receive(actionMessage('different_session', 1, 3, 'action_789')).accepted, 'other-room action rejected');
const context = { sessionID: 'session_123', revision: 0, hostPeerID: 'host_123', fromPeerID: 'host_123' };
check(acceptHostState(result.message, context), 'guest accepts authoritative fresh state');
check(!acceptHostState(result.message, { ...context, fromPeerID: 'guest_123' }), 'non-host cannot set state');
check(!acceptHostState(result.message, { ...context, revision: 1 }), 'duplicate state ignored');
check(!acceptHostState(result.message, { ...context, sessionID: 'other_session' }), 'foreign session state ignored');
check(!validRoomMessage({ ...result.message, envelope: base }), 'other game cannot enter Lights Out room');
check(!validRoomMessage({ ...press, index: 25 }) && !validRoomMessage({ ...press, index: -1 }) && !validRoomMessage({ ...press, baseRevision: NaN }), 'malformed room action rejected');
console.log(`✓ arcade-check: ${checks} assertions; nine native seed/action fixtures match`);
