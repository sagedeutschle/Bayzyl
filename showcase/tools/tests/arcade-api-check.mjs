import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { validateSave } from '../../prismet-site/pages/arcade/saves.js';

const module = await import('../../server/arcade-api.js').catch(() => ({}));
assert.equal(typeof module.createArcadeApi, 'function', 'Authenticated progress API is implemented');
const userID = '11111111-1111-4111-8111-111111111111';
const records = new Map();
let identityOverride;
let providerOverride;
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
const provider = async (input, options = {}) => {
  const u = new URL(input), token = options.headers?.authorization;
  if (providerOverride) return providerOverride(input, options);
  if (u.pathname === '/auth/v1/signup') return json({ user: { id: userID, email_confirmed_at: null } });
  if (u.pathname === '/auth/v1/token') {
    const body = JSON.parse(options.body);
    if (body.email !== 'player@example.invalid' || body.password !== 'fixture-password') return json({ error: 'invalid credentials' }, 400);
    return json({ access_token: 'fixture-access', refresh_token: 'fixture-refresh', expires_in: 3600, token_type: 'bearer', user: { id: userID } });
  }
  if (u.pathname === '/auth/v1/user') {
    if (identityOverride) return json(identityOverride);
    if (token === 'Bearer fixture-guest') return json({ id: userID, is_anonymous: true });
    return token === 'Bearer fixture-access' ? json({ id: userID, is_anonymous: false, email: 'player@example.invalid', email_confirmed_at: '2026-10-04T00:00:00Z' }) : json({ error: 'invalid token' }, 401);
  }
  if (u.pathname === '/auth/v1/logout') return json({});
  if (token !== 'Bearer fixture-access') return json({ error: 'denied' }, 401);
  if (u.pathname === '/rest/v1/prismet_arcade_saves') {
    assert.equal(u.searchParams.get('user_id'), `eq.${userID}`, 'Only verified identity scopes reads');
    const key = `${u.searchParams.get('game_id')}|${u.searchParams.get('slot')}`;
    return json(records.has(key) ? [records.get(key)] : []);
  }
  if (u.pathname === '/rest/v1/rpc/prismet_save_arcade_progress') {
    const body = JSON.parse(options.body);
    const key = `eq.${body.p_game_id}|eq.${body.p_slot}`;
    const prior = records.get(key);
    if ((prior?.revision || 0) !== body.p_expected_revision) return json({ code: 'PT409' }, 409);
    const record = { revision: (prior?.revision || 0) + 1, envelope: body.p_envelope, updated_at: '2026-10-04T01:00:00.000Z' };
    records.set(key, record); return json([record]);
  }
  throw new Error(`Unexpected provider path: ${u.pathname}`);
};
const sendJSON = (_req, res, status, body, headers = {}) => {
  res.writeHead(status, { 'content-type': 'application/json', ...headers }); res.end(JSON.stringify(body));
};
const api = module.createArcadeApi({ supabaseUrl: 'https://auth.example.invalid', publishableKey: 'fixture-public', fetchImpl: provider, validateEnvelope: validateSave, sendJSON, secureCookies: false });
const server = createServer(async (req, res) => {
  if (!await api.handle(req, res, new URL(req.url, 'http://localhost'), req.method)) { res.writeHead(404); res.end(); }
});
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const base = `http://127.0.0.1:${server.address().port}`;
const envelope = { version: 1, stateVersion: 1, gameID: 'lights-out', seed: '1', mode: 'free', state: { grid: Array(25).fill(true) }, moves: 0, elapsedMs: 0, savedAt: '2026-10-04T00:00:00.000Z' };
const post = (path, body, extra = {}) => fetch(base + path, { method: 'POST', headers: { origin: base, 'content-type': 'application/json', ...extra }, body: JSON.stringify(body) });
try {
  const config = await (await fetch(base + '/api/arcade/config')).json();
  assert.equal(config.syncAvailable, true);
  assert.equal(config.appStoreUrl, 'https://apps.apple.com/us/app/kaleidescope/id6785993194');
  const signup = await post('/api/arcade/auth', { action: 'signup', email: 'player@example.invalid', password: 'fixture-password' });
  assert.equal(signup.status, 200);
  assert.deepEqual(await signup.json(), { signedIn: false, confirmationRequired: true }, 'Registration waits for account confirmation');
  assert.equal(signup.headers.get('set-cookie'), null);
  assert.equal((await fetch(base + '/api/arcade/progress/lights-out')).status, 401);
  assert.equal((await fetch(base + '/api/arcade/progress/lights-out', { headers: { authorization: 'Bearer forged' } })).status, 401);
  assert.equal((await fetch(base + '/api/arcade/progress/lights-out', { headers: { authorization: 'Bearer fixture-guest' } })).status, 403, 'An anonymous device identity is not a cross-device account');
  assert.equal((await post('/api/arcade/auth', { action: 'signin', email: 'player@example.invalid', password: 'wrong' })).status, 401);
  const login = await post('/api/arcade/auth', { action: 'signin', email: 'player@example.invalid', password: 'fixture-password' });
  assert.equal(login.status, 200);
  assert.match(login.headers.get('set-cookie'), /HttpOnly/);
  assert.match(login.headers.get('set-cookie'), /SameSite=Strict/);
  assert.equal(JSON.stringify(await login.json()).includes('fixture-access'), false, 'Bearer tokens never reach browser JavaScript');
  const cookie = login.headers.get('set-cookie').split(';')[0];
  const route = '/api/arcade/progress/lights-out?slot=free';
  for (const incompleteIdentity of [
    { id: userID },
    { id: userID, is_anonymous: false },
    { id: userID, is_anonymous: false, email: 'player@example.invalid' },
    { id: userID, email: 'player@example.invalid', email_confirmed_at: '2026-10-04T00:00:00Z' },
    { id: userID, is_anonymous: false, email: 'player@example.invalid', email_confirmed_at: 'invalid' },
  ]) {
    identityOverride = incompleteIdentity;
    assert.equal((await fetch(base + route, { headers: { cookie } })).status, 403, 'Unconfirmed or incomplete identity fails closed');
  }
  identityOverride = undefined;
  const verified = { id: userID, is_anonymous: false, email: 'player@example.invalid', email_confirmed_at: '2026-10-04T00:00:00Z' };
  let rejectedBodyCancelled = false;
  providerOverride = () => new Response(new ReadableStream({
    start(controller) { controller.enqueue(new TextEncoder().encode(JSON.stringify(verified))); },
    cancel() { rejectedBodyCancelled = true; },
  }), { headers: { 'content-length': '10000000' } });
  assert.equal((await fetch(base + route, { headers: { cookie } })).status, 503, 'Reject declared oversized upstream before reading');
  assert.equal(rejectedBodyCancelled, true, 'Oversized upstream reader is cancelled');
  let chunkedCancelled = false;
  providerOverride = () => new Response(new ReadableStream({
    start(controller) { controller.enqueue(new TextEncoder().encode(JSON.stringify({ ...verified, padding: 'x'.repeat(90 * 1024) }))); },
    cancel() { chunkedCancelled = true; },
  }));
  assert.equal((await fetch(base + route, { headers: { cookie } })).status, 503, 'Chunked upstream response is bounded');
  assert.equal(chunkedCancelled, true);
  providerOverride = undefined;

  assert.deepEqual(await (await fetch(base + route, { headers: { cookie } })).json(), { revision: 0, envelope: null, updatedAt: null });
  const put = (body, headers = {}) => fetch(base + route, { method: 'PUT', headers: { cookie, origin: base, 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });
  assert.equal((await put({ expectedRevision: 0, envelope }, { origin: 'https://untrusted.example' })).status, 403, 'Cross-origin writes rejected');
  assert.equal((await put({ expectedRevision: 0, envelope: { ...envelope, gameID: '2048' } })).status, 400, 'Route and state game IDs must match');
  assert.equal((await put({ expectedRevision: -1, envelope })).status, 400);
  assert.equal((await put({ expectedRevision: 0, envelope: { ...envelope, state: { grid: [true] } } })).status, 400);
  assert.equal((await put({ expectedRevision: 0, envelope, padding: 'x'.repeat(72 * 1024) })).status, 413, 'Oversized requests cannot reach storage');
  const saved = await put({ expectedRevision: 0, envelope });
  assert.equal(saved.status, 200);
  assert.equal((await saved.json()).revision, 1);
  const conflict = await put({ expectedRevision: 0, envelope: { ...envelope, moves: 1 } });
  assert.equal(conflict.status, 409, 'Stale device cannot replace newer progress');
  const loaded = await (await fetch(base + route, { headers: { authorization: 'Bearer fixture-access' } })).json();
  assert.equal(loaded.revision, 1);
  assert.deepEqual(loaded.envelope, envelope);
  assert.equal((await put({ expectedRevision: 1, envelope: { ...envelope, mode: 'challenge' } })).status, 400, 'Save modes remain separate');
  assert.equal((await fetch(base + '/api/arcade/progress/unknown', { headers: { cookie } })).status, 400);
  assert.equal((await fetch(base + route, { method: 'DELETE', headers: { cookie, origin: base } })).status, 405);
  assert.equal((await post('/api/arcade/auth', { action: 'signout' }, { cookie })).status, 200);
  assert.equal((await fetch(base + route, { headers: { cookie } })).status, 401);
  console.log('✓ arcade-api: authentication, CSRF, bounded state validation, save/load and revision conflicts');
} finally { server.closeAllConnections(); await new Promise((resolve) => server.close(resolve)); }
