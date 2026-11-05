// The broker binds each connection to one seat. Only the host's client runs rules.
const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
export const maxFrameBytes = 256 * 1024;
const limit = 512;
const waitingTime = 15 * 60_000;
const idleTime = 3 * 60_000;
const validSeats = n => Number.isInteger(n) && n >= 2 && n <= 4;
function roomCode() {
  return Array.from(crypto.getRandomValues(new Uint8Array(8)), b => alphabet[b & 31]).join('');
}
export class Broker {
  constructor({now = Date.now, makeCode = roomCode} = {}) {
    this.now = now;
    this.makeCode = makeCode;
    this.peers = new Set();
    this.rooms = new Map();
  }
  attach(io, saved) {
    if (this.peers.size >= limit) {
      io.close(1013, 'Tables are busy. Try again shortly.');
      return null;
    }
    const time = this.now();
    const peer = {io, connected: time, lastSeen: time, bucket: time, count: 0,
      seat: -1, room: null, rtc: false};
    this.peers.add(peer);
    if (saved) {
      peer.connected = saved.connected;
      peer.lastSeen = saved.lastSeen;
      peer.bucket = saved.bucket;
      peer.count = saved.count;
    }
    if (saved?.room) {
      const info = saved.room;
      const room = this.rooms.get(info.code) ?? {...info, members: Array(info.seats).fill(null)};
      this.rooms.set(room.code, room);
      room.members[saved.seat] = peer;
      Object.assign(peer, {room, seat: saved.seat, rtc: saved.rtc});
    }
    this.save(peer);
    return peer;
  }
  save(peer) {
    if (!peer.io.persist) return;
    const {room, seat, rtc, connected, lastSeen, bucket, count} = peer;
    const info = room && {code: room.code, mode: room.mode, seats: room.seats,
      started: room.started, created: room.created};
    peer.io.persist({room: info, seat, rtc, connected, lastSeen, bucket, count});
  }
  terminate(peer, reason, code = 4000) {
    if (!this.peers.has(peer)) return;
    this.detach(peer);
    try { peer.io.close(code, reason); } catch { /* Already disconnected. */ }
  }
  send(peer, message) {
    if (!peer || !this.peers.has(peer)) return;
    try { peer.io.send(JSON.stringify(message)); }
    catch { this.terminate(peer, 'Connection lost.'); }
  }
  receive(peer, raw) {
    if (!this.peers.has(peer)) return;
    if (typeof raw !== 'string' || raw.length > maxFrameBytes) {
      return this.terminate(peer, 'Invalid packet.');
    }
    const now = this.now();
    if (now - peer.bucket >= 2_000) { peer.bucket = now; peer.count = 0; }
    if (++peer.count > 80) return this.terminate(peer, 'Too many requests. Try again.');
    peer.lastSeen = now;
    this.save(peer);
    let msg;
    try { msg = JSON.parse(raw); } catch { return this.terminate(peer, 'Invalid packet.'); }
    if (!msg || typeof msg !== 'object') return this.terminate(peer, 'Invalid packet.');
    if (!peer.room) return this.admit(peer, msg);
    if (msg.type === 'ping') return this.send(peer, {type:'pong'});
    if (msg.type === 'leave') return this.terminate(peer, 'You left the table.', 1000);
    const room = peer.room;
    if (!room.started || !Number.isInteger(msg.to) || msg.to === peer.seat) return;
    const target = room.members[msg.to];
    if (!target || (peer.seat !== 0 && target.seat !== 0)) return;
    if (msg.type === 'signal') {
      if (room.mode !== 'create' || !peer.rtc || !target.rtc) return;
    } else if (msg.type === 'data') {
      const kind = msg.body?.kind;
      if (peer.seat === 0 ? !['view', 'rejection'].includes(kind) : !['move', 'sync'].includes(kind)) return;
    } else { return; }
    // Never forward caller-supplied sender or recipient identifiers as authority.
    this.send(target, {type: msg.type, from: peer.seat, body: msg.body});
  }
  admit(peer, msg) {
    if (msg.type !== 'hello' || msg.protocol !== 1) {
      return this.terminate(peer, 'Update Shorepact to join this table.');
    }
    if (!['create','join','quick'].includes(msg.mode)) return this.terminate(peer, 'Invalid table.');
    let room;
    if (msg.mode === 'join') {
      const code = typeof msg.code === 'string' ? msg.code.replace(/[ -]/g,'').toUpperCase() : '';
      room = this.rooms.get(code);
      if (!room || room.mode !== 'create' || room.started) {
        return this.terminate(peer, 'No open table with that code.');
      }
    } else {
      if (!validSeats(msg.seats)) return this.terminate(peer, 'Choose two to four seats.');
      if (msg.mode === 'quick') {
        room = [...this.rooms.values()].find(r => r.mode === 'quick' && r.seats === msg.seats && !r.started);
      }
      if (!room) {
        let code;
        do { code = this.makeCode(); } while (this.rooms.has(code));
        room = {code, mode:msg.mode, seats:msg.seats, started:false, created:this.now(),
          members:Array(msg.seats).fill(null)};
        this.rooms.set(code, room);
      }
    }
    const seat = room.members.indexOf(null);
    if (seat < 0) return this.terminate(peer, 'The table is full.');
    room.members[seat] = peer;
    Object.assign(peer, {room, seat, rtc: msg.rtc === true && room.mode === 'create'});
    room.started = room.members.every(Boolean);
    this.announce(room);
  }
  announce(room) {
    const joined = room.members.filter(Boolean).length;
    const rtcSeats = room.members.filter(m => m?.rtc).map(m => m.seat);
    for (const p of room.members) {
      if (!p) continue;
      this.save(p);
      this.send(p, {type:'lobby', code:room.mode === 'quick' ? null : room.code, mode:room.mode,
        joined, seats:room.seats, seat:p.seat, started:room.started, rtcSeats});
    }
  }
  detach(peer) {
    if (!this.peers.delete(peer)) return;
    const {room} = peer;
    if (!room) return;
    room.members[peer.seat] = null;
    peer.room = null;
    if (room.started || !room.members[0]) {
      this.rooms.delete(room.code);
      const reason = room.started ? 'A player disconnected. This table has ended.' : 'The host left the lobby.';
      for (const p of room.members) {
        if (!p) continue;
        p.room = null;
        this.terminate(p, reason);
      }
    } else { this.announce(room); }
  }
  sweep() {
    const now = this.now();
    for (const p of this.peers) {
      if (!p.room && now - p.connected > 15_000) this.terminate(p, 'Join request timed out.');
      else if (p.room && !p.room.started && now - p.room.created > waitingTime) this.terminate(p, 'Lobby timed out.');
      else if (now - p.lastSeen > idleTime) this.terminate(p, 'Connection timed out.');
    }
  }
}
