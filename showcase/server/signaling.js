import { randomInt, randomBytes, timingSafeEqual } from 'node:crypto';
import { WebSocketServer } from 'ws';

// Stateless WebRTC signaling for the Prismet universal room system.
// Spec: Kaleidoscope/docs/superpowers/specs/2026-07-20-universal-room-system-design.md §5.4/§16.1.
//
// This is pure rendezvous: who is in this room, and where do I forward their
// SDP/ICE. No table, no row, no persisted game state — everything below
// lives in the two in-memory maps returned from createSignaling() and is
// gone on process restart by design.

const CODE_ALPHABET = Array.from('23456789ABCDEFGHJKMNPQRSTUVWXYZ'); // no 0/O/1/I/L
const CODE_LENGTH = 4;
const MIN_SEATS = 2;
const MAX_SEATS = 8; // RoomLimits.maxSeats
const ROOM_TTL_MS = 10 * 60 * 1000; // 10-minute sliding lifetime, refreshed by hello/ping
const EMPTY_ROOM_GRACE_MS = 60 * 1000; // 60s grace before an emptied room is evicted
const SWEEP_INTERVAL_MS = 5_000;
const MAX_FRAME_BYTES = 64 * 1024; // cap signaling frame size (SDP/ICE payloads are small)
const MAX_ROOMS = 10_000; // defense-in-depth ceiling on top of per-IP rate limiting
const MAX_TEXT_LENGTH = 128;
const REROLL_ATTEMPTS = 50;

const CONTROL_UPPER_BOUND = 32; // codepoints below this are ASCII control chars (0x00-0x1F)
const DEL_CODE = 127;
const C1_UPPER_BOUND = 160; // C1 control range is 0x7F..0x9F inclusive

function sanitizeText(value, maxLength = MAX_TEXT_LENGTH) {
  const source = String(value ?? '');
  let out = '';
  for (const ch of source) {
    const code = ch.codePointAt(0);
    const isControl = code < CONTROL_UPPER_BOUND || (code >= DEL_CODE && code < C1_UPPER_BOUND);
    if (!isControl) out += ch;
  }
  return out.trim().slice(0, maxLength);
}

function clampInt(value, min, max, fallback) {
  const n = Number(value);
  if (!Number.isInteger(n)) return fallback;
  return Math.min(Math.max(n, min), max);
}

function normalizeRoomCode(value) {
  const upper = sanitizeText(value, 16).toUpperCase();
  const normalized = upper.split('').filter((c) => CODE_ALPHABET.includes(c)).join('');
  return normalized.length === CODE_LENGTH ? normalized : null;
}

function generateRoomCode() {
  let out = '';
  for (let i = 0; i < CODE_LENGTH; i += 1) {
    out += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
  }
  return out;
}

function defaultClientAddress(req) {
  const flyClientIp = req.headers?.['fly-client-ip'];
  if (typeof flyClientIp === 'string' && flyClientIp.trim()) return flyClientIp.trim();
  return sanitizeText(req.socket?.remoteAddress || 'unknown', 64);
}

/**
 * @param {object} deps
 * @param {(maxEntries: number) => Map} deps.createBoundedCache - reused from server.js
 * @param {{max: number, windowMs: number}} [deps.rateLimit] - RATE_LIMITS.rtc from server.js
 * @param {(req: import('node:http').IncomingMessage) => string} [deps.clientAddress]
 * @param {number} [deps.maxRooms]
 */
export function createSignaling({
  createBoundedCache,
  rateLimit = { max: 20, windowMs: 60_000 },
  clientAddress = defaultClientAddress,
  maxRooms = MAX_ROOMS,
} = {}) {
  if (typeof createBoundedCache !== 'function') {
    throw new TypeError('createSignaling requires createBoundedCache');
  }

  /** @type {Map<string, {code:string, gameID:string|null, maxPlayers:number, hostPeerId:string, peers:Map<string,{peerId:string, ws:import('ws').WebSocket, displayName:string, platform:string, joinedAt:number}>, createdAt:number, expiresAt:number, emptySince:number|null}>} */
  const rooms = new Map();
  /** @type {Map<import('ws').WebSocket, {peerId:string|null, roomCode:string|null, platform:string, displayName:string, lastSeen:number, ip:string}>} */
  const sockets = new Map();
  const joinRateBuckets = createBoundedCache(10_000);

  function rtcRateLimited(ip) {
    const key = `rtc:${ip}`;
    const now = Date.now();
    const bucket = joinRateBuckets.get(key);
    if (!bucket || now - bucket.start >= rateLimit.windowMs) {
      joinRateBuckets.set(key, { start: now, count: 1 });
      return false;
    }
    if (bucket.count >= rateLimit.max) return true;
    bucket.count += 1;
    joinRateBuckets.set(key, bucket);
    return false;
  }

  function send(ws, payload) {
    if (ws.readyState === ws.OPEN) {
      try {
        ws.send(JSON.stringify(payload));
      } catch {
        // Socket is mid-teardown; drop silently.
      }
    }
  }

  function sendError(ws, code) {
    send(ws, { type: 'error', code });
  }

  function publicPeer(peer) {
    return { peerId: peer.peerId, displayName: peer.displayName, platform: peer.platform };
  }

  function touchRoom(room) {
    room.expiresAt = Date.now() + ROOM_TTL_MS;
  }

  function rerollCode() {
    for (let attempt = 0; attempt < REROLL_ATTEMPTS; attempt += 1) {
      const code = generateRoomCode();
      if (!rooms.has(code)) return code;
    }
    return null;
  }

  function removePeerFromRoom(ws) {
    const socketState = sockets.get(ws);
    if (!socketState || !socketState.roomCode) return;
    const room = rooms.get(socketState.roomCode);
    const departedPeerId = socketState.peerId;
    socketState.roomCode = null;
    if (!room || !departedPeerId) return;
    const peer = room.peers.get(departedPeerId);
    if (!peer || peer.ws !== ws) return; // don't evict a newer connection reusing the same peerId
    room.peers.delete(departedPeerId);
    for (const other of room.peers.values()) {
      send(other.ws, { type: 'peer_left', peerId: departedPeerId });
    }
    if (room.peers.size === 0) {
      room.emptySince = Date.now();
    }
  }

  function handleHello(ws, socketState, msg) {
    const peerId = sanitizeText(msg.peerId, 64);
    // Identity is fixed once this socket is seated in a room. A later hello may
    // refresh display fields, but re-keying peerId mid-room would orphan the
    // room's peer entry (keyed by the OLD id), leaking a ghost peer that never
    // emits peer_left on disconnect and never frees its seat. Only accept a
    // peerId while unseated.
    if (peerId && !socketState.roomCode) socketState.peerId = peerId;
    socketState.platform = sanitizeText(msg.platform, 16);
    socketState.displayName = sanitizeText(msg.displayName, 50);
    socketState.lastSeen = Date.now();
    if (socketState.roomCode) {
      const room = rooms.get(socketState.roomCode);
      if (room) touchRoom(room);
    }
  }

  function handleCreateRoom(ws, socketState, msg) {
    if (!socketState.peerId) {
      sendError(ws, 'invalid_code'); // create_room carries no peerId — hello must precede it
      return;
    }
    if (rtcRateLimited(socketState.ip)) {
      sendError(ws, 'rate_limited');
      return;
    }
    if (rooms.size >= maxRooms) {
      sendError(ws, 'rate_limited');
      return;
    }
    const code = rerollCode();
    if (!code) {
      sendError(ws, 'invalid_room');
      return;
    }
    const maxPlayers = clampInt(msg.maxPlayers, MIN_SEATS, MAX_SEATS, MIN_SEATS);
    const room = {
      code,
      gameID: sanitizeText(msg.gameId, 64) || null,
      maxPlayers,
      hostPeerId: socketState.peerId,
      // Optional browser capability; legacy native rooms retain their existing wire contract.
      hostResumeToken: msg.requireHostResumeProof === true ? randomBytes(32).toString('base64url') : null,
      peers: new Map(),
      createdAt: Date.now(),
      expiresAt: Date.now() + ROOM_TTL_MS,
      emptySince: null,
    };
    removePeerFromRoom(ws);
    room.peers.set(socketState.peerId, {
      peerId: socketState.peerId,
      ws,
      displayName: socketState.displayName,
      platform: socketState.platform,
      joinedAt: Date.now(),
    });
    rooms.set(code, room);
    socketState.roomCode = code;
    send(ws, { type: 'room_created', roomCode: code,
      ...(room.hostResumeToken ? { hostResumeToken: room.hostResumeToken } : {}) });
  }

  function handleJoinRoom(ws, socketState, msg) {
    if (rtcRateLimited(socketState.ip)) {
      sendError(ws, 'rate_limited');
      return;
    }
    const code = normalizeRoomCode(msg.roomCode);
    if (!code) {
      sendError(ws, 'invalid_code');
      return;
    }
    const room = rooms.get(code);
    if (!room) {
      sendError(ws, 'invalid_room');
      return;
    }
    const peerId = sanitizeText(msg.peerId, 64) || socketState.peerId;
    if (!peerId) {
      sendError(ws, 'invalid_code');
      return;
    }
    const occupied = room.peers.get(peerId);
    if (occupied && occupied.ws !== ws) {
      sendError(ws, 'peer_id_in_use');
      return;
    }
    if (room.hostResumeToken && peerId === room.hostPeerId) {
      const proof = msg.hostResumeToken;
      if (typeof proof !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(proof)
          || !timingSafeEqual(Buffer.from(proof), Buffer.from(room.hostResumeToken))) {
        sendError(ws, 'host_proof_required');
        return;
      }
    }
    const replacesOwnSeat = socketState.roomCode === code && socketState.peerId !== peerId
      && room.peers.get(socketState.peerId)?.ws === ws;
    const joiningCount = room.peers.size - (replacesOwnSeat ? 1 : 0) + (occupied ? 0 : 1);
    const hostPresentAfterJoin = peerId === room.hostPeerId
      || (room.peers.has(room.hostPeerId) && !(replacesOwnSeat && socketState.peerId === room.hostPeerId));
    // A protected host keeps one seat during reconnect, even if guests know the room code.
    const reservedHostSeat = room.hostResumeToken && !hostPresentAfterJoin ? 1 : 0;
    if (joiningCount + reservedHostSeat > room.maxPlayers) {
      sendError(ws, 'room_full');
      return;
    }

    if (socketState.roomCode && (socketState.roomCode !== code || socketState.peerId !== peerId)) {
      removePeerFromRoom(ws);
    }

    socketState.peerId = peerId;
    socketState.roomCode = code;
    socketState.lastSeen = Date.now();

    const existingPeers = [...room.peers.values()]
      .filter((p) => p.peerId !== peerId)
      .map(publicPeer);

    room.peers.set(peerId, {
      peerId,
      ws,
      displayName: socketState.displayName,
      platform: socketState.platform,
      joinedAt: Date.now(),
    });
    room.emptySince = null;
    touchRoom(room);

    send(ws, { type: 'room_joined', roomCode: code, peers: existingPeers });
    for (const other of room.peers.values()) {
      if (other.peerId === peerId) continue;
      send(other.ws, { type: 'peer_joined', peerId });
    }
  }

  function handleLeaveRoom(ws, socketState, msg) {
    const code = normalizeRoomCode(msg.roomCode);
    if (!code || socketState.roomCode !== code) return;
    removePeerFromRoom(ws);
  }

  function relay(ws, socketState, msg) {
    const code = normalizeRoomCode(msg.roomCode);
    if (!code || socketState.roomCode !== code) return;
    const room = rooms.get(code);
    if (!room) return;
    const fromPeerId = sanitizeText(msg.fromPeerId, 64);
    if (!fromPeerId || fromPeerId !== socketState.peerId) return; // best-effort sender spoofing guard
    if (room.peers.get(fromPeerId)?.ws !== ws) return;
    const toPeerId = sanitizeText(msg.toPeerId, 64);
    const target = room.peers.get(toPeerId);
    if (!target) return; // target not present (already left/never joined) — drop, no error frame defined
    // Keep legacy payload fields, but never forward a reconnect capability.
    const { hostResumeToken, requireHostResumeProof, ...payload } = msg;
    send(target.ws, payload);
    socketState.lastSeen = Date.now();
  }

  function handlePing(ws, socketState) {
    socketState.lastSeen = Date.now();
    if (socketState.roomCode) {
      const room = rooms.get(socketState.roomCode);
      if (room) touchRoom(room);
    }
    send(ws, { type: 'pong' });
  }

  function handleMessage(ws, raw) {
    const socketState = sockets.get(ws);
    if (!socketState) return;

    if (Buffer.byteLength(typeof raw === 'string' ? raw : raw) > MAX_FRAME_BYTES) {
      sendError(ws, 'invalid_code');
      return;
    }

    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      sendError(ws, 'invalid_code');
      return;
    }
    if (!msg || typeof msg !== 'object' || typeof msg.type !== 'string') {
      sendError(ws, 'invalid_code');
      return;
    }

    switch (msg.type) {
      case 'hello':
        handleHello(ws, socketState, msg);
        break;
      case 'create_room':
        handleCreateRoom(ws, socketState, msg);
        break;
      case 'join_room':
        handleJoinRoom(ws, socketState, msg);
        break;
      case 'leave_room':
        handleLeaveRoom(ws, socketState, msg);
        break;
      case 'offer':
      case 'answer':
      case 'ice':
        relay(ws, socketState, msg);
        break;
      case 'ping':
        handlePing(ws, socketState);
        break;
      default:
        sendError(ws, 'invalid_code');
    }
  }

  const wss = new WebSocketServer({ noServer: true, maxPayload: MAX_FRAME_BYTES });

  wss.on('connection', (ws, req) => {
    const ip = clientAddress(req);
    sockets.set(ws, {
      peerId: null,
      roomCode: null,
      platform: '',
      displayName: '',
      lastSeen: Date.now(),
      ip,
    });

    ws.on('message', (data) => handleMessage(ws, data));
    ws.on('close', () => {
      removePeerFromRoom(ws);
      sockets.delete(ws);
    });
    ws.on('error', () => {
      removePeerFromRoom(ws);
      sockets.delete(ws);
    });
  });

  function handleUpgrade(req, socket, head) {
    wss.handleUpgrade(req, socket, head, (ws) => {
      wss.emit('connection', ws, req);
    });
  }

  const sweepTimer = setInterval(() => {
    const now = Date.now();
    for (const [code, room] of rooms) {
      if (room.peers.size === 0) {
        if (room.emptySince === null) room.emptySince = now;
        if (now - room.emptySince >= EMPTY_ROOM_GRACE_MS) rooms.delete(code);
        continue;
      }
      if (now >= room.expiresAt) {
        for (const peer of room.peers.values()) {
          sendError(peer.ws, 'invalid_room');
          try {
            peer.ws.close(4000, 'room expired');
          } catch {
            // already closing
          }
        }
        rooms.delete(code);
      }
    }
  }, SWEEP_INTERVAL_MS);
  sweepTimer.unref?.();

  function close() {
    clearInterval(sweepTimer);
    wss.close();
  }

  return { handleUpgrade, close, rooms, sockets };
}
