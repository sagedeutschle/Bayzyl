// Opt-in account saves. The publishable key never bypasses database RLS; every
// read/write carries the caller's verified Supabase access token.
import { randomBytes } from 'node:crypto';

const GAMES = new Set(['2048', 'minesweeper', 'lights-out']);
const MODES = new Set(['free', 'daily', 'challenge']);
const COOKIE = 'prismet_arcade_session';
const MAX_BODY = 70 * 1024;
const MAX_SAVE = 64 * 1024;
const SESSION_MS = 7 * 24 * 60 * 60 * 1000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
const fail = (status, message) => { throw new ApiError(status, message); };

async function readBody(req) {
  if (!/^application\/json(?:\s*;|$)/i.test(req.headers['content-type'] || '')) fail(415, 'Send JSON data.');
  if (Number(req.headers['content-length'] || 0) > MAX_BODY) fail(413, 'This save is too large.');
  let size = 0;
  const chunks = [];
  const timer = setTimeout(() => req.destroy(), 8000);
  try {
    for await (const chunk of req) {
      size += chunk.length;
      if (size > MAX_BODY) fail(413, 'This save is too large.');
      chunks.push(chunk);
    }
    const value = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    if (!value || typeof value !== 'object' || Array.isArray(value)) fail(400, 'Invalid request.');
    return value;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    fail(400, 'Invalid JSON request.');
  } finally { clearTimeout(timer); }
}

function sameOrigin(req) {
  const origin = req.headers.origin;
  if (typeof origin !== 'string') return false;
  if (origin === 'https://prismet.xyz' || origin === 'https://www.prismet.xyz') return true;
  const host = req.headers.host || '';
  return /^(?:127\.0\.0\.1|localhost)(?::\d{1,5})?$/.test(host) && origin === `http://${host}`;
}

export function createArcadeApi({
  supabaseUrl = process.env.ARCADE_SUPABASE_URL || '',
  publishableKey = process.env.ARCADE_SUPABASE_PUBLISHABLE_KEY || '',
  fetchImpl = globalThis.fetch,
  now = Date.now,
  sendJSON,
  rateLimited = () => false,
  secureCookies = process.env.ARCADE_COOKIE_SECURE !== '0',
  validateEnvelope = async (value) => (await import('./site/arcade/saves.js')).validateSave(value),
} = {}) {
  if (typeof sendJSON !== 'function') throw new TypeError('sendJSON is required');
  let base = '';
  try {
    const u = new URL(supabaseUrl);
    if (u.protocol === 'https:' && !u.username && !u.password && u.pathname === '/' && !u.search && !u.hash) base = u.origin;
  } catch { /* Unconfigured is a supported local-first mode. */ }
  const configured = !!base && typeof publishableKey === 'string' && publishableKey.length > 0;
  const sessions = new Map();
  const cookieValue = (id, age = SESSION_MS / 1000) => `${COOKIE}=${id}; Path=/api/arcade; HttpOnly; SameSite=Strict; Max-Age=${age}${secureCookies ? '; Secure' : ''}`;
  function sessionID(req) {
    const entry = (req.headers.cookie || '').split(';').map((part) => part.trim()).find((part) => part.startsWith(`${COOKIE}=`));
    const id = entry?.slice(COOKIE.length + 1);
    return typeof id === 'string' && /^[A-Za-z0-9_-]{43}$/.test(id) ? id : null;
  }
  function prune() {
    for (const [id, session] of sessions) if (session.deadline <= now()) sessions.delete(id);
    while (sessions.size > 2000) sessions.delete(sessions.keys().next().value);
  }
  async function upstream(path, { token, body, method = 'GET' } = {}) {
    if (!configured) fail(503, 'Cloud saves are not enabled on this server. Your browser saves still work.');
    let response;
    try {
      response = await fetchImpl(base + path, {
        method, redirect: 'error', signal: AbortSignal.timeout(8000),
        headers: { apikey: publishableKey, ...(token ? { authorization: `Bearer ${token}` } : {}), ...(body ? { 'content-type': 'application/json' } : {}) },
        ...(body ? { body: JSON.stringify(body) } : {}),
      });
    } catch { fail(503, 'Cloud saves could not be reached. Your local game is unchanged.'); }
    if (response.status === 409) fail(409, 'A newer cloud save exists. Load it or keep playing locally.');
    if (response.status === 401 || response.status === 403 || (path.startsWith('/auth/v1/token') && response.status === 400)) fail(401, 'Sign in again to use cloud saves.');
    if (!response.ok) fail(503, 'Cloud saves are temporarily unavailable. Your local game is unchanged.');
    if (response.status === 204) return {};
    let reader;
    try {
      const declared = response.headers.get('content-length');
      if (declared !== null && (!/^\d+$/.test(declared) || Number(declared) > MAX_BODY)) {
        await response.body?.cancel();
        fail(503, 'The cloud response was too large.');
      }
      reader = response.body?.getReader();
      if (!reader) fail(503, 'The cloud response could not be read.');
      const chunks = [];
      let size = 0;
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > MAX_BODY) {
          await reader.cancel();
          fail(503, 'The cloud response was too large.');
        }
        chunks.push(Buffer.from(value));
      }
      return JSON.parse(Buffer.concat(chunks).toString('utf8'));
    } catch (error) {
      if (error instanceof ApiError) throw error;
      fail(503, 'The cloud response could not be read.');
    } finally { reader?.releaseLock(); }
  }
  function applyTokens(session, payload) {
    if (typeof payload.access_token !== 'string' || payload.access_token.length > 16384 || typeof payload.refresh_token !== 'string' || payload.refresh_token.length > 16384 || !Number.isFinite(payload.expires_in) || payload.expires_in <= 0) fail(503, 'The sign-in response could not be verified.');
    session.access = payload.access_token;
    session.refresh = payload.refresh_token;
    session.expiresAt = now() + Math.min(payload.expires_in, 86400) * 1000;
  }
  async function credential(req) {
    const header = req.headers.authorization;
    if (header !== undefined) {
      if (typeof header !== 'string' || !/^Bearer [^\s]{1,16384}$/i.test(header)) fail(401, 'Sign in to use cloud saves.');
      return header.slice(7);
    }
    const id = sessionID(req), session = sessions.get(id);
    if (!session || session.deadline <= now()) { if (id) sessions.delete(id); fail(401, 'Sign in to use cloud saves.'); }
    if (session.expiresAt <= now() + 30000) {
      session.refreshing ??= upstream('/auth/v1/token?grant_type=refresh_token', { method: 'POST', body: { refresh_token: session.refresh } })
        .then((payload) => applyTokens(session, payload)).finally(() => { session.refreshing = null; });
      try { await session.refreshing; } catch (error) { sessions.delete(id); throw error; }
    }
    return session.access;
  }
  async function identity(token) {
    const user = await upstream('/auth/v1/user', { token });
    if (!UUID.test(user?.id || '')) fail(401, 'The account could not be verified.');
    if (user.is_anonymous !== false || typeof user.email !== 'string' || !user.email.trim() || typeof user.email_confirmed_at !== 'string' || !Number.isFinite(Date.parse(user.email_confirmed_at))) fail(403, 'Use a confirmed Arcade account for cross-device saves.');
    return user.id;
  }
  async function recordValue(record, gameID, slot) {
    if (!record) return { revision: 0, envelope: null, updatedAt: null };
    if (!Number.isSafeInteger(record.revision) || record.revision < 1 || !record.envelope || record.envelope.gameID !== gameID || record.envelope.mode !== slot || !await validateEnvelope(record.envelope) || !Number.isFinite(Date.parse(record.updated_at))) fail(503, 'The cloud save could not be validated. Your local game is unchanged.');
    return { revision: record.revision, envelope: record.envelope, updatedAt: record.updated_at };
  }
  const reply = (req, res, status, body, headers = {}) => sendJSON(req, res, status, body, { 'cache-control': 'no-store', ...headers });
  return {
    async handle(req, res, url, method) {
      if (!url.pathname.startsWith('/api/arcade/')) return false;
      try {
        prune();
        if (rateLimited(url.pathname.endsWith('/auth') && method === 'POST' ? 'arcadeAuth' : 'arcade', req, res)) {
          reply(req, res, 429, { error: 'Please wait before trying again.' }, { 'retry-after': '60' }); return true;
        }
        if (url.pathname === '/api/arcade/config' && (method === 'GET' || method === 'HEAD')) {
          reply(req, res, 200, {
            syncAvailable: configured,
            appStoreUrl: 'https://apps.apple.com/us/app/kaleidescope/id6785993194',
            rtcIceServers: [{ urls: 'stun:stun.cloudflare.com:3478' }],
          }); return true;
        }
        if (url.pathname === '/api/arcade/auth') {
          if (method === 'GET' || method === 'HEAD') {
            try { const token = await credential(req); await identity(token); reply(req, res, 200, { configured, signedIn: true }); }
            catch (error) { if (error.status === 401 || !configured) reply(req, res, 200, { configured, signedIn: false }); else throw error; }
            return true;
          }
          if (method !== 'POST') fail(405, 'Use GET or POST.');
          if (!sameOrigin(req)) fail(403, 'Open Prismet to manage this session.');
          const body = await readBody(req);
          if (body.action === 'signout') {
            const id = sessionID(req), session = sessions.get(id); sessions.delete(id);
            if (session) await upstream('/auth/v1/logout?scope=local', { method: 'POST', token: session.access }).catch(() => {});
            reply(req, res, 200, { signedIn: false }, { 'set-cookie': cookieValue('', 0) }); return true;
          }
          if (!['signin', 'signup'].includes(body.action) || typeof body.email !== 'string' || body.email.length > 254 || !/^[^\s@]+@[^\s@]+$/.test(body.email) || typeof body.password !== 'string' || !body.password.length || body.password.length > 1024) fail(400, 'Enter your account email and password.');
          if (body.action === 'signup' && body.password.length < 12) fail(400, 'Choose a password with at least 12 characters.');
          const payload = await upstream(body.action === 'signup' ? '/auth/v1/signup' : '/auth/v1/token?grant_type=password', { method: 'POST', body: { email: body.email, password: body.password } });
          if (body.action === 'signup' && !payload.access_token) {
            reply(req, res, 200, { signedIn: false, confirmationRequired: true }); return true;
          }
          const session = { deadline: now() + SESSION_MS };
          applyTokens(session, payload); await identity(session.access);
          const id = randomBytes(32).toString('base64url');
          const old = sessionID(req); if (old) sessions.delete(old);
          while (sessions.size >= 2000) sessions.delete(sessions.keys().next().value);
          sessions.set(id, session);
          reply(req, res, 200, { signedIn: true }, { 'set-cookie': cookieValue(id) }); return true;
        }
        const match = /^\/api\/arcade\/progress\/([^/]+)$/.exec(url.pathname);
        if (!match) fail(404, 'Arcade endpoint not found.');
        const gameID = match[1], slot = url.searchParams.get('slot') || 'free';
        if (!GAMES.has(gameID) || !MODES.has(slot)) fail(400, 'Unknown game or save mode.');
        if (!['GET', 'HEAD', 'PUT'].includes(method)) fail(405, 'Use GET or PUT.');
        if (method === 'PUT' && (!req.headers.authorization || req.headers.origin) && !sameOrigin(req)) fail(403, 'Open Prismet to save your progress.');
        const token = await credential(req), userID = await identity(token);
        if (method === 'GET' || method === 'HEAD') {
          const query = new URLSearchParams({ user_id: `eq.${userID}`, game_id: `eq.${gameID}`, slot: `eq.${slot}`, select: 'revision,envelope,updated_at', limit: '1' });
          const rows = await upstream(`/rest/v1/prismet_arcade_saves?${query}`, { token });
          if (!Array.isArray(rows)) fail(503, 'The cloud response could not be read.');
          reply(req, res, 200, await recordValue(rows[0], gameID, slot)); return true;
        }
        const body = await readBody(req), envelope = body.envelope;
        if (!Number.isSafeInteger(body.expectedRevision) || body.expectedRevision < 0 || body.expectedRevision >= Number.MAX_SAFE_INTEGER || !envelope || Buffer.byteLength(JSON.stringify(envelope), 'utf8') > MAX_SAVE || envelope.gameID !== gameID || envelope.mode !== slot || !await validateEnvelope(envelope)) fail(400, 'This game save is invalid. Your local game is unchanged.');
        const rows = await upstream('/rest/v1/rpc/prismet_save_arcade_progress', { method: 'POST', token, body: { p_game_id: gameID, p_slot: slot, p_expected_revision: body.expectedRevision, p_envelope: envelope } });
        if (!Array.isArray(rows) || rows.length !== 1) fail(503, 'The cloud save was not confirmed.');
        reply(req, res, 200, await recordValue(rows[0], gameID, slot)); return true;
      } catch (error) {
        reply(req, res, error instanceof ApiError ? error.status : 503, { error: error instanceof ApiError ? error.message : 'Cloud saves are temporarily unavailable. Your local game is unchanged.' });
        return true;
      }
    },
  };
}
