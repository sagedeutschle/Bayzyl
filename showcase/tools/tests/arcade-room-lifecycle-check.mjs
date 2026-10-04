// Node-only lifecycle tests with controlled transport promises; no browser or real network.
import assert from 'node:assert/strict';
import { mountRoom } from '../../prismet-site/pages/arcade/room.js';
import { newSave } from '../../prismet-site/pages/arcade/saves.js';
const originals = Object.fromEntries(['window','location','fetch','WebSocket','RTCPeerConnection'].map((name) => [name, globalThis[name]]));
const tick = () => new Promise((resolve) => setImmediate(resolve));
const deferred = () => { let resolve; const promise = new Promise((r) => { resolve = r; }); return { promise, resolve }; };
class Target {
  listeners = new Map(); value = ''; hidden = false; textContent = '';
  addEventListener(type, fn) { this.listeners.set(type, fn); }
  fire(type, event = {}) { this.listeners.get(type)?.(event); }
}
const sockets = [];
class Socket extends Target {
  static OPEN = 1; readyState = 0; sent = [];
  constructor() { super(); sockets.push(this); }
  send(text) { this.sent.push(JSON.parse(text)); }
  close() { this.readyState = 3; this.fire('close'); }
  open() { this.readyState = 1; this.fire('open'); }
}
function fixture() {
  const elements = new Map();
  const panel = { querySelector(selector) { if (!elements.has(selector)) elements.set(selector, new Target()); return elements.get(selector); } };
  let ended = 0;
  globalThis.window = new Target(); globalThis.location = { search: '', protocol: 'http:', host: 'localhost:1234', origin: 'http://localhost:1234' };
  const api = mountRoom(panel, { snapshot: () => newSave('lights-out','42'), beginRoom: () => true, roomState() {}, endRoom() { ended++; } });
  return { api, el: (name) => panel.querySelector(`[data-room-${name}]`), ended: () => ended };
}
let checks = 0;
try {
  globalThis.WebSocket = Socket;
  const held = deferred(); globalThis.fetch = () => held.promise;
  const first = fixture(); first.el('create').fire('click'); first.api.leave();
  held.resolve({ ok: true, json: async () => ({ rtcIceServers: [] }) }); await tick(); await tick();
  assert.equal(sockets.length, 0, 'leaving during config fetch cannot create a socket'); checks++;
  assert.equal(first.el('connected').hidden, true, 'late fetch cannot reveal room controls'); checks++;
  assert.equal(first.el('status').textContent, 'Room closed. Your personal game is restored.'); checks++;
  globalThis.fetch = async () => ({ ok: true, json: async () => ({ rtcIceServers: [] }) });
  const second = fixture(); second.el('create').fire('click'); await tick();
  const stale = sockets.at(-1); second.api.leave(); second.el('create').fire('click'); await tick();
  const latest = sockets.at(-1); stale.open();
  assert.equal(stale.sent.length, 0, 'closed socket late-open cannot join'); checks++;
  assert.equal(latest.sent.length, 0, 'stale callback cannot send through newer socket'); checks++;
  second.api.leave();
  const offer = deferred(); let localDescriptions = 0;
  class Peer extends Target {
    createDataChannel() { const channel = new Target(); channel.close = () => {}; return channel; }
    createOffer() { return offer.promise; }
    async setLocalDescription() { localDescriptions++; }
    close() {}
  }
  globalThis.RTCPeerConnection = Peer;
  const third = fixture(); third.el('create').fire('click'); await tick(); const ws = sockets.at(-1); ws.open();
  ws.fire('message', { data: JSON.stringify({ type:'room_created',roomCode:'ABCD',hostResumeToken:'h'.repeat(43) }) });
  ws.fire('message', { data: JSON.stringify({ type:'peer_joined',peerId:'other_peer' }) });
  third.api.leave(); offer.resolve({ type:'offer',sdp:'fixture' }); await tick();
  assert.equal(localDescriptions, 0, 'late SDP promise cannot mutate closed peer'); checks++;
  assert.equal(ws.sent.filter((m) => m.type === 'offer').length, 0, 'late SDP promise cannot relay after leave'); checks++;
  assert.equal(third.el('status').textContent, 'Room closed. Your personal game is restored.', 'stale promise does not replace restored status'); checks++;
  console.log(`✓ arcade-room-lifecycle-check: ${checks} async cancellation checks passed`);
} finally { for (const [name, value] of Object.entries(originals)) { if (value === undefined) delete globalThis[name]; else globalThis[name] = value; } }
