import { lightsOut } from './engines.js';
import { validateSave } from './saves.js';
export const PROTOCOL = 'prismet-lights-coop';
const id = (value) => typeof value === 'string' && /^[A-Za-z0-9_-]{8,64}$/.test(value);
const revision = (value) => Number.isSafeInteger(value) && value >= 0 && value < Number.MAX_SAFE_INTEGER;
export function validRoomMessage(message) {
  if (!message || message.protocol !== PROTOCOL || message.version !== 1 || !id(message.sessionID)) return false;
  if (message.type === 'state') return revision(message.revision) && validateSave(message.envelope) && message.envelope.gameID === 'lights-out' && message.envelope.mode === 'challenge';
  if (message.type === 'action') return revision(message.baseRevision) && id(message.actionID) && Number.isInteger(message.index) && message.index >= 0 && message.index < 25;
  return false;
}
export function hostSession(envelope, sessionID) {
  if (!validateSave(envelope) || envelope.gameID !== 'lights-out' || !id(sessionID)) throw new Error('Invalid room board');
  let state = { ...structuredClone(envelope), mode: 'challenge' }; delete state.dailyDate;
  let currentRevision = 0; const seen = new Set();
  const snapshot = () => ({ protocol: PROTOCOL, version: 1, type: 'state', sessionID, revision: currentRevision, envelope: structuredClone(state) });
  return {
    snapshot,
    receive(message) {
      if (!validRoomMessage(message) || message.type !== 'action' || message.sessionID !== sessionID || message.baseRevision !== currentRevision || seen.has(message.actionID) || lightsOut.status(state.state) !== 'playing' || state.moves >= Number.MAX_SAFE_INTEGER) return { accepted: false, message: snapshot() };
      seen.add(message.actionID); if (seen.size > 1024) seen.delete(seen.values().next().value);
      state.state = lightsOut.apply(state.state, { type: 'press', index: message.index }); state.moves++; state.savedAt = new Date().toISOString(); currentRevision++;
      return { accepted: true, message: snapshot() };
    },
  };
}
export function acceptHostState(message, { sessionID, revision: currentRevision = -1, fromPeerID, hostPeerID }) {
  return fromPeerID === hostPeerID && validRoomMessage(message) && message.type === 'state' && (!sessionID || message.sessionID === sessionID) && message.revision > currentRevision;
}
export function actionMessage(sessionID, baseRevision, index, actionID) { const message = { protocol: PROTOCOL, version: 1, type: 'action', sessionID, baseRevision, index, actionID }; if (!validRoomMessage(message)) throw new Error('Invalid room action'); return message; }
