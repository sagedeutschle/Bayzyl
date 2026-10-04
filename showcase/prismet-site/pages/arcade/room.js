import { hostSession, acceptHostState, actionMessage, validRoomMessage } from './room-protocol.js';
const CODE = /^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}$/;
export function mountRoom(panel, arcade) {
  panel.hidden = arcade.snapshot().gameID !== 'lights-out';
  const $ = (name) => panel.querySelector(`[data-room-${name}]`), status = $('status');
  let socket, peer, channel, ping, timeout, socketTimeout, host = false, code = '', remoteID = '', hostID = '', sessionID = '', version = -1, hostResumeToken = '', authority = null, active = false, iceServers = [], pendingICE = [], starting = false, generation = 0;
  const peerID = crypto.randomUUID();
  const say = (text) => { status.textContent = text; };
  const uuid = () => crypto.randomUUID();
  const signal = (message) => { if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify(message)); };
  const relay = (type, payload) => signal({ type, roomCode: code, fromPeerId: peerID, toPeerId: remoteID, ...payload });
  function sendState() { if (!authority) return; const message = authority.snapshot(); version = message.revision; sessionID = message.sessionID; arcade.roomState(message.envelope); if (channel?.readyState === 'open') channel.send(JSON.stringify(message)); }
  function closePeer() { clearTimeout(timeout); const oldChannel = channel, oldPeer = peer; channel = null; peer = null; pendingICE = []; oldChannel?.close(); oldPeer?.close(); }
  function leave() { generation++; active = false; starting = false; signal({ type: 'leave_room', roomCode: code }); clearInterval(ping); clearTimeout(socketTimeout); closePeer(); socket?.close(); socket = null; code = ''; hostResumeToken = ''; authority = null; hostID = ''; sessionID = ''; version = -1; $('connected').hidden = true; $('setup').hidden = false; arcade.endRoom(); say('Room closed. Your personal game is restored.'); }
  function channelReady(dataChannel, connection) {
    channel = dataChannel;
    const current = () => active && peer === connection && channel === dataChannel;
    channel.addEventListener('open', () => { if (!current()) return; clearTimeout(timeout); $('retry').hidden = true; say(host ? 'Connected. Both players can press lights; your board is authoritative.' : 'Connected. Press a light to send a move to the host.'); if (host) sendState(); });
    channel.addEventListener('message', (event) => {
      if (!current() || typeof event.data !== 'string' || event.data.length > 65536) return;
      let message; try { message = JSON.parse(event.data); } catch { return; }
      if (!validRoomMessage(message)) return;
      if (host) { if (message.type !== 'action' || !authority) return; authority.receive(message); sendState(); }
      else if (acceptHostState(message, { sessionID, revision: version, fromPeerID: remoteID, hostPeerID: hostID })) { sessionID = message.sessionID; version = message.revision; arcade.roomState(message.envelope); say('Shared board updated. Your move.'); }
    });
    channel.addEventListener('close', () => { if (!current()) return; say(host ? 'Your partner disconnected. The room board is paused until they rejoin.' : 'Host disconnected. The shared board is paused; reconnect or leave to restore your personal game.'); $('retry').hidden = false; });
    channel.addEventListener('error', () => { if (!current()) return; say('The peer connection failed. Retry, or leave to restore your personal board.'); $('retry').hidden = false; });
  }
  function makePeer() {
    closePeer(); const connection = new RTCPeerConnection({ iceServers }); peer = connection;
    const current = () => active && peer === connection;
    peer.addEventListener('icecandidate', (event) => { if (current() && event.candidate) relay('ice', { candidate: event.candidate.toJSON() }); });
    peer.addEventListener('datachannel', (event) => { if (current() && !host && event.channel.label === 'prismet-lights-v1') channelReady(event.channel, connection); else event.channel.close(); });
    peer.addEventListener('connectionstatechange', () => { if (current() && connection.connectionState === 'failed') { say('Could not connect directly. Some networks require a relay service, which is not configured. Retry on another network or keep playing locally.'); $('retry').hidden = false; } });
    timeout = setTimeout(() => { if (current() && channel?.readyState !== 'open') { say('Connection timed out. Some networks need a relay service. Retry, or leave to restore local play.'); $('retry').hidden = false; } }, 20000);
    return peer;
  }
  async function offer(to, attempt) {
    if (!active || attempt !== generation) return;
    remoteID = to; const connection = makePeer();
    const current = () => active && attempt === generation && peer === connection;
    channelReady(connection.createDataChannel('prismet-lights-v1', { ordered: true }), connection);
    const description = await connection.createOffer(); if (!current()) return;
    await connection.setLocalDescription(description); if (!current()) return;
    relay('offer', { sdp: description.sdp });
  }
  async function onSignal(message, attempt) {
    if (!active || attempt !== generation) return;
    if (message.type === 'room_created') { if (!/^[A-Za-z0-9_-]{43}$/.test(message.hostResumeToken || '')) { leave(); say('This room service does not support protected browser hosts. Your personal board is restored.'); return; } hostResumeToken = message.hostResumeToken; code = message.roomCode; hostID = peerID; $('code').textContent = code; say('Room created. Copy an invitation for one other browser player.'); $('connected').hidden = false; $('setup').hidden = true; return; }
    if (message.type === 'room_joined') { code = message.roomCode; $('code').textContent = code; $('connected').hidden = false; $('setup').hidden = true; const other = message.peers?.find((p) => p.peerId !== peerID); if (host) { if (other) await offer(other.peerId, attempt); else say('Rejoined. Waiting for your partner.'); } else { if (!hostID) hostID = other?.peerId || ''; if (!other || other.peerId !== hostID) { say('The expected host is not in this room. Waiting for them to reconnect.'); return; } remoteID = hostID; say('Joined. Waiting for the host’s connection.'); } return; }
    if (message.type === 'peer_joined') { if (host) await offer(message.peerId, attempt); else if (message.peerId === hostID || !hostID) { hostID = message.peerId; remoteID = hostID; say('Host returned. Reconnecting…'); } return; }
    if (message.type === 'peer_left' && message.peerId === remoteID) { closePeer(); $('retry').hidden = false; say(host ? 'Partner left. Your room is waiting for them.' : 'Host left. Shared play is paused; leave to restore your personal board.'); return; }
    if (message.type === 'error') { say(`Room unavailable (${String(message.code).replace(/[^a-z_]/g, '')}). Retry or leave; your personal game is preserved.`); $('retry').hidden = false; return; }
    if (!['offer','answer','ice'].includes(message.type) || message.roomCode !== code || message.toPeerId !== peerID) return;
    if (message.fromPeerId !== (host ? remoteID : hostID)) return;
    if (message.type === 'offer' && !host && typeof message.sdp === 'string') {
      remoteID = hostID; const waitingICE = pendingICE, connection = makePeer(); pendingICE = waitingICE;
      const current = () => active && attempt === generation && peer === connection;
      await connection.setRemoteDescription({ type: 'offer', sdp: message.sdp }); if (!current()) return;
      const description = await connection.createAnswer(); if (!current()) return;
      await connection.setLocalDescription(description); if (!current()) return;
      relay('answer', { sdp: description.sdp });
    } else if (message.type === 'answer' && host && peer && typeof message.sdp === 'string') {
      const connection = peer; await connection.setRemoteDescription({ type: 'answer', sdp: message.sdp });
      if (!active || attempt !== generation || peer !== connection) return;
    } else if (message.type === 'ice' && message.candidate) {
      if (peer?.remoteDescription) { const connection = peer; await connection.addIceCandidate(message.candidate); if (!active || attempt !== generation || peer !== connection) return; }
      else if (pendingICE.length < 256) pendingICE.push(message.candidate);
    }
    if (peer?.remoteDescription && pendingICE.length) {
      const connection = peer;
      for (const candidate of pendingICE.splice(0)) { if (!active || attempt !== generation || peer !== connection) return; await connection.addIceCandidate(candidate); }
    }
  }
  async function connect(create) {
    if (starting) return;
    starting = true; const attempt = ++generation;
    const current = () => active && attempt === generation;
    try {
      if (!active) {
        if (!arcade.beginRoom()) return;
        active = true;
        if (create) { host = true; hostID = peerID; authority = hostSession(arcade.snapshot(), uuid()); sendState(); }
        else host = false;
      }
      clearInterval(ping); clearTimeout(socketTimeout);
      const oldSocket = socket; socket = null; oldSocket?.close(); closePeer(); say('Connecting to the room service…');
      $('connected').hidden = false; $('setup').hidden = true;
      const config = await fetch('/api/arcade/config', { credentials: 'same-origin', signal: AbortSignal.timeout(8000) }).then((r) => r.ok ? r.json() : {});
      if (!current()) return;
      iceServers = Array.isArray(config.rtcIceServers) ? config.rtcIceServers.filter((entry) => typeof entry.urls === 'string' && /^stuns?:/.test(entry.urls)) : [];
      const ws = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/rtc`); socket = ws;
      const currentSocket = () => current() && socket === ws;
      socketTimeout = setTimeout(() => { if (currentSocket() && ws.readyState !== WebSocket.OPEN) { ws.close(); say('Room service timed out. Reconnect or leave to restore local play.'); $('retry').hidden = false; } }, 15000);
      ws.addEventListener('open', () => {
        if (!currentSocket()) { ws.close(); return; }
        clearTimeout(socketTimeout);
        signal({ type: 'hello', peerId: peerID, displayName: host ? 'Browser host' : 'Browser guest', platform: 'web' });
        if (create && !code) signal({ type: 'create_room', gameId: 'lights-out', maxPlayers: 2, requireHostResumeProof: true });
        else signal({ type: 'join_room', roomCode: code, peerId: peerID, ...(host && hostResumeToken ? { hostResumeToken } : {}) });
        ping = setInterval(() => { if (currentSocket()) signal({ type: 'ping' }); }, 25000);
      });
      ws.addEventListener('message', (event) => {
        if (!currentSocket()) return;
        let message; try { message = JSON.parse(event.data); } catch { return; }
        void onSignal(message, attempt).catch(() => { if (!currentSocket()) return; say('Could not establish the peer connection. Retry or leave to restore local play.'); $('retry').hidden = false; });
      });
      ws.addEventListener('close', () => { if (!currentSocket()) return; clearInterval(ping); clearTimeout(socketTimeout); closePeer(); say('Room service disconnected. Shared play is paused. Reconnect to the same room or leave.'); $('retry').hidden = false; });
      ws.addEventListener('error', () => { if (currentSocket()) { say('Room service could not be reached. Local progress is preserved.'); $('retry').hidden = false; } });
    } catch {
      if (!current()) return;
      say('Room service could not be reached. Retry or leave to return to your local board.'); $('connected').hidden = false; $('setup').hidden = true; $('retry').hidden = false;
    } finally { if (attempt === generation) starting = false; }
  }
  $('create').addEventListener('click', () => { code = ''; hostID = ''; void connect(true); });
  $('join').addEventListener('submit', (event) => { event.preventDefault(); const input = $('input').value.trim().toUpperCase(); if (!CODE.test(input)) { say('Enter a four-character room code.'); return; } code = input; void connect(false); });
  $('retry').addEventListener('click', () => void connect(host && !code));
  $('leave').addEventListener('click', leave);
  $('copy').addEventListener('click', async () => { if (!code) return; const attempt = generation; const url = new URL('/arcade/lights-out', location.origin); url.searchParams.set('room', code); url.searchParams.set('host', host ? peerID : hostID); $('invite').value = url.href; $('invite').hidden = false; try { await navigator.clipboard.writeText(url.href); if (!active || attempt !== generation) return; say('Invitation copied. This is a two-player browser room.'); } catch { if (!active || attempt !== generation) return; $('invite').focus(); $('invite').select(); say('Copy the invitation from the field.'); } });
  window.addEventListener('prismet:room-action', (event) => { if (!active) return; if (!channel || channel.readyState !== 'open' || !sessionID || !Number.isInteger(event.detail?.index)) { say('Waiting for the other player’s connection.'); return; } const message = actionMessage(sessionID, version, event.detail.index, uuid()); if (host) { authority.receive(message); sendState(); } else { channel.send(JSON.stringify(message)); say('Move sent to the host…'); } });
  window.addEventListener('pagehide', leave);
  const query = new URLSearchParams(location.search), invitation = query.get('room'), expectedHost = query.get('host');
  if (CODE.test(invitation || '') && /^[A-Za-z0-9_-]{8,64}$/.test(expectedHost || '')) { $('input').value = invitation; hostID = expectedHost; panel.open = true; say('An invitation is ready. Choose Join room to connect; your personal board will be preserved.'); }
  return { leave };
}
