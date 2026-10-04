import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createRequire } from 'node:module';
import { once } from 'node:events';
import { createSignaling } from '../../server/signaling.js';
const require = createRequire(new URL('../../server/package.json', import.meta.url));
const { WebSocket } = require('ws');
const signaling = createSignaling({ createBoundedCache: () => new Map(), rateLimit: { max: 1000, windowMs: 60000 }, clientAddress: () => 'fixture-local' });
const server = createServer(); server.on('upgrade', (req, socket, head) => signaling.handleUpgrade(req, socket, head));
server.listen(0, '127.0.0.1'); await once(server, 'listening');
const endpoint = `ws://127.0.0.1:${server.address().port}/rtc`, clients = [];
async function connect(id) {
  const ws = new WebSocket(endpoint), messages = [];
  ws.on('message', data => messages.push(JSON.parse(data.toString()))); await once(ws, 'open'); clients.push(ws);
  ws.send(JSON.stringify({ type: 'hello', peerId: id, platform: 'fixture', displayName: 'Fixture' }));
  const send = value => ws.send(JSON.stringify(value));
  async function next(type) { const deadline = Date.now() + 1500; while (Date.now() < deadline) { const index = messages.findIndex(m => m.type === type); if (index !== -1) return messages.splice(index, 1)[0]; await new Promise(r => setTimeout(r, 5)); } throw new Error(`Missing ${type}`); }
  return { ws, messages, send, next };
}
try {
  const host = await connect('host'), guest = await connect('guest'), attacker = await connect('attacker');
  host.send({ type: 'create_room', gameId: 'lights-out', maxPlayers: 2, requireHostResumeProof: true });
  const created = await host.next('room_created'), code = created.roomCode, proof = created.hostResumeToken;
  assert.match(proof, /^[A-Za-z0-9_-]{43}$/, 'Protected room issues a host-only resume capability');
  guest.send({ type: 'join_room', roomCode: code, peerId: 'guest' });
  const joined = await guest.next('room_joined'); assert.equal(JSON.stringify(joined).includes(proof), false, 'Peers never receive host capability');
  await host.next('peer_joined');
  attacker.send({ type: 'join_room', roomCode: code, peerId: 'host' });
  assert.equal((await attacker.next('error')).code, 'peer_id_in_use');
  host.send({ type: 'offer', roomCode: code, fromPeerId: 'host', toPeerId: 'guest', sdp: 'fixture-offer', hostResumeToken: proof });
  const offer = await guest.next('offer'); assert.equal(offer.sdp, 'fixture-offer'); assert.equal('hostResumeToken' in offer, false, 'Relay strips capability even if mistakenly attached');
  host.ws.close(); await once(host.ws, 'close'); await guest.next('peer_left');
  attacker.send({ type: 'join_room', roomCode: code, peerId: 'extra-guest' });
  assert.equal((await attacker.next('error')).code, 'room_full', 'Protected host retains a seat while disconnected');
  attacker.send({ type: 'join_room', roomCode: code, peerId: 'host' });
  assert.equal((await attacker.next('error')).code, 'host_proof_required', 'Disconnected host identity stays reserved');
  attacker.send({ type: 'join_room', roomCode: code, peerId: 'host', hostResumeToken: 'x'.repeat(43) });
  assert.equal((await attacker.next('error')).code, 'host_proof_required');
  const resumed = await connect('host'); resumed.send({ type: 'join_room', roomCode: code, peerId: 'host', hostResumeToken: proof });
  await resumed.next('room_joined'); await guest.next('peer_joined');
  resumed.send({ type: 'answer', roomCode: code, fromPeerId: 'host', toPeerId: 'guest', sdp: 'resumed' });
  assert.equal((await guest.next('answer')).sdp, 'resumed');
  const legacy = await connect('legacy-host'); legacy.send({ type: 'create_room', gameId: 'chess', maxPlayers: 3 });
  const legacyRoom = await legacy.next('room_created'); assert.equal('hostResumeToken' in legacyRoom, false);
  const visitor = await connect('visitor'); visitor.send({ type: 'join_room', roomCode: legacyRoom.roomCode, peerId: 'visitor' });
  await visitor.next('room_joined'); await legacy.next('peer_joined');
  visitor.send({ type: 'offer', roomCode: code, fromPeerId: 'visitor', toPeerId: 'guest', sdp: 'cross-room' });
  visitor.send({ type: 'ping' }); await visitor.next('pong');
  assert.equal(guest.messages.some(m => m.sdp === 'cross-room'), false);
  legacy.ws.close(); await once(legacy.ws, 'close'); await visitor.next('peer_left');
  const legacyResume = await connect('legacy-host'); legacyResume.send({ type: 'join_room', roomCode: legacyRoom.roomCode, peerId: 'legacy-host' });
  await legacyResume.next('room_joined'); await visitor.next('peer_joined');
  legacyResume.send({ type: 'offer', roomCode: legacyRoom.roomCode, fromPeerId: 'legacy-host', toPeerId: 'visitor', sdp: 'legacy' });
  assert.equal((await visitor.next('offer')).sdp, 'legacy', 'Legacy native reconnect needs no new fields');
  visitor.send({ type: 'create_room', gameId: 'chess', maxPlayers: 2 });
  const visitorRoom = await visitor.next('room_created'); await legacyResume.next('peer_left');
  assert.equal(signaling.rooms.get(legacyRoom.roomCode).peers.has('visitor'), false, 'Creating a room removes old membership');
  visitor.send({ type: 'join_room', roomCode: visitorRoom.roomCode, peerId: 'renamed-visitor' }); await visitor.next('room_joined');
  assert.deepEqual([...signaling.rooms.get(visitorRoom.roomCode).peers.keys()], ['renamed-visitor'], 'Same-room identity change leaves no ghost');
  // Deliberately invalidate server membership to test current-socket relay defense.
  const prior = signaling.rooms.get(code).peers.get('host'); signaling.rooms.get(code).peers.set('host', { ...prior, ws: attacker.ws });
  resumed.send({ type: 'offer', roomCode: code, fromPeerId: 'host', toPeerId: 'guest', sdp: 'stale-socket' }); resumed.send({ type: 'ping' }); await resumed.next('pong');
  assert.equal(guest.messages.some(m => m.sdp === 'stale-socket'), false);
  console.log('✓ signaling: protected host reconnect, live identity ownership, relay isolation, legacy compatibility and no ghost seats');
} finally {
  for (const ws of clients) ws.terminate(); signaling.close(); await new Promise(r => server.close(r));
}
