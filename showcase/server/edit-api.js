// edit-api.js — the server side of prismet.xyz/edit.
//
// The GitHub token that writes the content files lives here, in the server's environment (a Fly secret), never in a
// browser. A visitor unlocks the editor with a PIN; the server answers with an HttpOnly session cookie and then
// proxies a small, allow-listed slice of the GitHub API: list the content files, read one, write one, publish several
// in one commit, read the workflow run for a commit. Wrong PINs are rate-limited per address and globally.
// What can be read and written: the wording (content/**.md) and four data files (data/projects.json, theme.json,
// styles.json, pages.json).
//
// Environment: EDIT_GITHUB_TOKEN (fine-grained token: Contents read/write, Actions read, this repository only),
// EDIT_PIN (6+ characters), EDIT_REPO (default sagedeutschle/Bayzyl), EDIT_BRANCH (default main),
// EDIT_GITHUB_API (default https://api.github.com; tests point it at a fake), EDIT_COOKIE_SECURE (default 1).
// Drafts: EDIT_DRAFT_REPO (owner/name of a PRIVATE repository; unset = drafts stay in the browser), EDIT_DRAFT_BRANCH
// (default main), EDIT_DRAFT_TOKEN (default: the same token, which then needs Contents read/write on that repository
// too). The unpublished draft is one file there, draft.json, so it follows Sage between devices and never sits in
// the public repository.
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

const SITE_ROOT = 'showcase/prismet-site/';
const CONTENT_ROOT = `${SITE_ROOT}content/`;
const DATA_FILES = new Set(['projects', 'theme', 'styles', 'pages'].map((name) => `${SITE_ROOT}data/${name}.json`));
const COMMIT_MAX_FILES = 40;
const DRAFT_FILE = 'draft.json', DRAFT_LIMIT = 2 * 1024 * 1024;
const SESSION_MS = 8 * 60 * 60 * 1000;
const PIN_WINDOW_MS = 15 * 60 * 1000, PIN_MAX_PER_IP = 5;     // 5 wrong guesses per address per 15 minutes
const LOCK_WINDOW_MS = 60 * 60 * 1000, LOCK_AFTER = 25;        // 25 wrong guesses from anywhere in an hour locks the PIN for an hour
const API_WINDOW_MS = 60 * 1000, API_MAX_PER_IP = 120;
const BODY_LIMIT = 512 * 1024;
const COOKIE = 'edit_session';

const sha256 = (s) => createHash('sha256').update(String(s)).digest();
const sameSecret = (a, b) => timingSafeEqual(sha256(a), sha256(b));
const clean = (s, max) => String(s ?? '').replace(/[\u0000-\u001F\u007F-\u009F]/g, '').slice(0, max);
const contentPath = (p) => typeof p === 'string' && p.startsWith(CONTENT_ROOT) && p.endsWith('.md') && !p.includes('..') && !p.includes('//') && p.length < 200;
const allowedPath = (p) => contentPath(p) || DATA_FILES.has(p);
// A new file can only be a record's wording: content/work/<slug>.md.
const creatablePath = (p) => typeof p === 'string' && /^[a-z0-9][a-z0-9-]{0,60}\.md$/.test(p.slice(`${CONTENT_ROOT}work/`.length)) && p.startsWith(`${CONTENT_ROOT}work/`);
// The id git gives a file's contents, so the page knows each file's new version without reading it back.
const blobSha = (text) => { const b = Buffer.from(text, 'utf8'); return createHash('sha1').update(`blob ${b.length}\0`).update(b).digest('hex'); };
const validJson = (text) => { try { const v = JSON.parse(text); return v !== null && typeof v === 'object' && !Array.isArray(v); } catch { return false; } };

function readBody(req, limit = BODY_LIMIT) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > limit) { reject(new Error('body too large')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

function parseCookies(header = '') {
  const out = {};
  for (const part of String(header).split(';')) { const i = part.indexOf('='); if (i > 0) out[part.slice(0, i).trim()] = part.slice(i + 1).trim(); }
  return out;
}

export function createEditApi({ env = process.env, fetchImpl = globalThis.fetch, clientAddress, sendJSON, now = Date.now } = {}) {
  const TOKEN = env.EDIT_GITHUB_TOKEN || '';
  const PIN = env.EDIT_PIN || '';
  const REPO = /^[\w.-]+\/[\w.-]+$/.test(env.EDIT_REPO || '') ? env.EDIT_REPO : 'sagedeutschle/Bayzyl';
  const BRANCH = clean(env.EDIT_BRANCH || 'main', 100);
  const API = (env.EDIT_GITHUB_API || 'https://api.github.com').replace(/\/+$/, '');
  const SECURE = env.EDIT_COOKIE_SECURE !== '0';
  const DRAFT_REPO = /^[\w.-]+\/[\w.-]+$/.test(env.EDIT_DRAFT_REPO || '') ? env.EDIT_DRAFT_REPO : '';
  const DRAFT_BRANCH = clean(env.EDIT_DRAFT_BRANCH || 'main', 100);
  const DRAFT_TOKEN = env.EDIT_DRAFT_TOKEN || TOKEN;
  const configured = TOKEN.length > 0 && PIN.length >= 6;
  const reason = !TOKEN ? 'EDIT_GITHUB_TOKEN is not set' : PIN.length < 6 ? 'EDIT_PIN must be at least 6 characters' : '';

  const sessions = new Map();            // id → expiry
  const pinAttempts = new Map();         // ip → { start, count }
  let lock = { start: 0, count: 0 };
  const apiHits = new Map();             // ip → { start, count }

  const sweep = () => { const t = now(); for (const [k, v] of sessions) if (v < t) sessions.delete(k); if (pinAttempts.size > 5000) pinAttempts.clear(); if (apiHits.size > 5000) apiHits.clear(); };
  const bump = (map, ip, windowMs) => { const t = now(); const b = map.get(ip); if (!b || t - b.start >= windowMs) { map.set(ip, { start: t, count: 1 }); return 1; } b.count += 1; return b.count; };

  const sessionOf = (req) => { const id = parseCookies(req.headers.cookie)[COOKIE]; if (!id) return null; const exp = sessions.get(id); if (!exp || exp < now()) { sessions.delete(id); return null; } return id; };
  const cookie = (value, maxAge) => `${COOKIE}=${value}; Path=/; HttpOnly; SameSite=Strict; Max-Age=${maxAge}${SECURE ? '; Secure' : ''}`;

  async function github(path, init = {}) {
    const ctrl = new AbortController(); const t = setTimeout(() => ctrl.abort(), 15_000);
    try {
      const r = await fetchImpl(API + path, { ...init, signal: ctrl.signal, headers: { accept: 'application/vnd.github+json', authorization: `Bearer ${init.token || TOKEN}`, 'x-github-api-version': '2022-11-28', 'user-agent': 'prismet.xyz edit', ...(init.headers || {}) } });
      const text = await r.text();
      let body = null; try { body = text ? JSON.parse(text) : null; } catch { body = null; }
      return { status: r.status, ok: r.ok, body };
    } finally { clearTimeout(t); }
  }
  const upstream = (res, req, r, what) => {
    if (r.status === 401 || r.status === 403) return sendJSON(req, res, 502, { ok: false, error: `GitHub refused the stored token while ${what} (${r.status}). It may have expired or lack a permission.` });
    if (r.status === 404) return sendJSON(req, res, 404, { ok: false, error: `GitHub has no such ${what === 'listing the files' ? 'folder' : 'file'} on ${BRANCH}.` });
    if (r.status === 409 || r.status === 422) return sendJSON(req, res, 409, { ok: false, error: 'The file changed on GitHub since it was loaded. Reload and try again.' });
    return sendJSON(req, res, 502, { ok: false, error: `GitHub answered ${r.status} while ${what}.` });
  };

  // Reads take the branch's newest files, or a past revision's when ?ref=<commit> is given (the history).
  const refOf = (url) => { const ref = url.searchParams.get('ref') || ''; return /^[0-9a-f]{40}$/.test(ref) ? ref : encodeURIComponent(BRANCH); };

  /** Returns true when the request was handled. */
  async function handle(req, res, url, method) {
    const ip = clientAddress(req);
    sweep();
    if (bump(apiHits, ip, API_WINDOW_MS) > API_MAX_PER_IP) { sendJSON(req, res, 429, { ok: false, error: 'Too many requests. Try again in a minute.' }, { 'retry-after': '60' }); return true; }
    const p = url.pathname;

    if (p === '/api/edit/status' && method === 'GET') {
      sendJSON(req, res, 200, { ok: true, configured, reason, authed: Boolean(sessionOf(req)), repo: REPO, branch: BRANCH, drafts: Boolean(DRAFT_REPO) });
      return true;
    }
    if (p === '/api/edit/session' && method === 'DELETE') {
      const id = sessionOf(req); if (id) sessions.delete(id);
      sendJSON(req, res, 200, { ok: true }, { 'set-cookie': cookie('', 0) });
      return true;
    }
    if (p === '/api/edit/session' && method === 'POST') {
      if (!configured) { sendJSON(req, res, 503, { ok: false, error: `The editor is not set up on the server: ${reason}.` }); return true; }
      if (req.headers['x-prismet-edit'] !== '1') { sendJSON(req, res, 400, { ok: false, error: 'Missing request header.' }); return true; }
      const t = now();
      if (t - lock.start < LOCK_WINDOW_MS && lock.count >= LOCK_AFTER) { sendJSON(req, res, 429, { ok: false, error: 'Too many wrong PINs; the editor is locked for an hour.' }, { 'retry-after': '3600' }); return true; }
      const tries = bump(pinAttempts, ip, PIN_WINDOW_MS);
      if (tries > PIN_MAX_PER_IP) { sendJSON(req, res, 429, { ok: false, error: 'Too many wrong PINs from here; wait fifteen minutes.' }, { 'retry-after': '900' }); return true; }
      let body; try { body = JSON.parse(await readBody(req) || '{}'); } catch { sendJSON(req, res, 400, { ok: false, error: 'Bad request.' }); return true; }
      const pin = clean(body.pin, 128);
      if (!pin || !sameSecret(pin, PIN)) {
        if (t - lock.start >= LOCK_WINDOW_MS) lock = { start: t, count: 0 };
        lock.count += 1;
        sendJSON(req, res, 401, { ok: false, error: 'Wrong PIN.', remaining: Math.max(0, PIN_MAX_PER_IP - tries) });
        return true;
      }
      pinAttempts.delete(ip);
      const id = randomBytes(32).toString('hex'); sessions.set(id, t + SESSION_MS);
      sendJSON(req, res, 200, { ok: true, expiresInSeconds: SESSION_MS / 1000 }, { 'set-cookie': cookie(id, SESSION_MS / 1000) });
      return true;
    }
    if (!p.startsWith('/api/edit/')) return false;

    // everything below needs a session
    if (!sessionOf(req)) { sendJSON(req, res, 401, { ok: false, error: 'Unlock the editor with the PIN first.' }); return true; }
    if (method !== 'GET' && req.headers['x-prismet-edit'] !== '1') { sendJSON(req, res, 400, { ok: false, error: 'Missing request header.' }); return true; }

    // History: what was published, newest first. A revision's files are read with ?ref=<its commit>.
    if (p === '/api/edit/history' && method === 'GET') {
      const r = await github(`/repos/${REPO}/commits?sha=${encodeURIComponent(BRANCH)}&path=${SITE_ROOT.slice(0, -1)}&per_page=40`);
      if (!r.ok || !Array.isArray(r.body)) return upstream(res, req, r, 'reading the history'), true;
      sendJSON(req, res, 200, { ok: true, revisions: r.body.filter((c) => /^[0-9a-f]{40}$/.test(c?.sha || '')).map((c) => ({ sha: c.sha, date: clean(c.commit?.committer?.date, 40), message: clean(String(c.commit?.message || '').split('\n')[0], 200) })) });
      return true;
    }
    if (p === '/api/edit/files' && method === 'GET') {
      const r = await github(`/repos/${REPO}/contents/${CONTENT_ROOT}work?ref=${refOf(url)}`);
      if (!r.ok || !Array.isArray(r.body)) return upstream(res, req, r, 'listing the files'), true;
      const work = r.body.filter((f) => f.type === 'file' && /\.md$/.test(f.name)).map((f) => f.path).filter(allowedPath).sort();
      sendJSON(req, res, 200, { ok: true, files: [`${CONTENT_ROOT}site.md`, ...work] });
      return true;
    }
    if (p === '/api/edit/file' && method === 'GET') {
      const path = url.searchParams.get('path') || '';
      if (!allowedPath(path)) { sendJSON(req, res, 400, { ok: false, error: 'Only the content files can be edited.' }); return true; }
      const r = await github(`/repos/${REPO}/contents/${path}?ref=${refOf(url)}`);
      if (!r.ok || typeof r.body?.content !== 'string') return upstream(res, req, r, 'reading the file'), true;
      sendJSON(req, res, 200, { ok: true, path, sha: r.body.sha, text: Buffer.from(String(r.body.content).replace(/\s/g, ''), 'base64').toString('utf8') });
      return true;
    }
    if (p === '/api/edit/file' && method === 'PUT') {
      let body; try { body = JSON.parse(await readBody(req) || '{}'); } catch { sendJSON(req, res, 400, { ok: false, error: 'Bad request.' }); return true; }
      const { path, text, sha } = body;
      if (!allowedPath(path) || typeof text !== 'string' || typeof sha !== 'string' || !/^[0-9a-f]{40}$/.test(sha) || text.length > BODY_LIMIT) { sendJSON(req, res, 400, { ok: false, error: 'Only the content files can be edited, with their current version.' }); return true; }
      const message = clean(body.message, 200) || `Edit from prismet.xyz/edit: ${path.split('/').pop()}`;
      const r = await github(`/repos/${REPO}/contents/${path}`, { method: 'PUT', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message, content: Buffer.from(text, 'utf8').toString('base64'), sha, branch: BRANCH }) });
      if (!r.ok || !r.body?.commit) return upstream(res, req, r, 'writing the file'), true;
      sendJSON(req, res, 200, { ok: true, commit: { sha: r.body.commit.sha }, content: { sha: r.body.content?.sha } });
      return true;
    }
    // The draft: one JSON file in the private drafts repository. The page saves it a few seconds after the last change.
    if (p === '/api/edit/draft' && (method === 'GET' || method === 'PUT')) {
      if (!DRAFT_REPO) { sendJSON(req, res, 404, { ok: false, error: 'Drafts are kept in the browser: no drafts repository is set on the server.' }); return true; }
      const at = `/repos/${DRAFT_REPO}/contents/${DRAFT_FILE}`;
      if (method === 'GET') {
        const r = await github(`${at}?ref=${encodeURIComponent(DRAFT_BRANCH)}`, { token: DRAFT_TOKEN });
        if (r.status === 404) { sendJSON(req, res, 200, { ok: true, text: null, sha: null }); return true; }
        if (!r.ok || typeof r.body?.content !== 'string') return upstream(res, req, r, 'reading the draft'), true;
        sendJSON(req, res, 200, { ok: true, sha: r.body.sha, text: Buffer.from(String(r.body.content).replace(/\s/g, ''), 'base64').toString('utf8') });
        return true;
      }
      let body; try { body = JSON.parse(await readBody(req, DRAFT_LIMIT) || '{}'); } catch { sendJSON(req, res, 400, { ok: false, error: 'Bad request.' }); return true; }
      const okSha = body.sha === null || (typeof body.sha === 'string' && /^[0-9a-f]{40}$/.test(body.sha));
      if (typeof body.text !== 'string' || !validJson(body.text) || !okSha) { sendJSON(req, res, 400, { ok: false, error: 'A draft is one JSON document, saved over the version it was loaded from.' }); return true; }
      const r = await github(at, { method: 'PUT', token: DRAFT_TOKEN, headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ message: 'Draft from prismet.xyz/edit', content: Buffer.from(body.text, 'utf8').toString('base64'), branch: DRAFT_BRANCH, ...(body.sha ? { sha: body.sha } : {}) }) });
      if (r.status === 409 || r.status === 422) { sendJSON(req, res, 409, { ok: false, error: 'A newer draft was saved from another device. Reload to pick it up; this one stays in this browser.' }); return true; }
      if (!r.ok || !r.body?.content?.sha) return upstream(res, req, r, 'saving the draft'), true;
      sendJSON(req, res, 200, { ok: true, sha: r.body.content.sha });
      return true;
    }
    // Publish: several files in ONE commit (one build, one deploy). Each file names the version it was edited from;
    // if any of them changed on GitHub since, nothing is written.
    if (p === '/api/edit/commit' && method === 'POST') {
      let body; try { body = JSON.parse(await readBody(req, 4 * BODY_LIMIT) || '{}'); } catch { sendJSON(req, res, 400, { ok: false, error: 'Bad request.' }); return true; }
      const files = Array.isArray(body.files) ? body.files : [];
      const bad = files.length === 0 || files.length > COMMIT_MAX_FILES || new Set(files.map((f) => f?.path)).size !== files.length || files.some((f) => !f || !allowedPath(f.path) || typeof f.text !== 'string' || f.text.length > BODY_LIMIT
        || !(f.sha === null ? creatablePath(f.path) : typeof f.sha === 'string' && /^[0-9a-f]{40}$/.test(f.sha))
        || (f.path.endsWith('.json') && !validJson(f.text)));
      if (bad) { sendJSON(req, res, 400, { ok: false, error: 'Only the content and data files can be published, each with its current version.' }); return true; }
      const message = clean(body.message, 200) || `Edit from prismet.xyz/edit: ${files.length} file${files.length === 1 ? '' : 's'}`;
      const repo = `/repos/${REPO}`, post = (path, payload, m = 'POST') => github(path, { method: m, headers: { 'content-type': 'application/json' }, body: JSON.stringify(payload) });
      const ref = await github(`${repo}/git/ref/heads/${encodeURIComponent(BRANCH)}`);
      if (!ref.ok || !ref.body?.object?.sha) return upstream(res, req, ref, 'reading the branch'), true;
      const head = ref.body.object.sha;
      const now_ = await Promise.all(files.map((f) => github(`${repo}/contents/${f.path}?ref=${head}`)));
      const stale = files.filter((f, i) => (f.sha === null ? now_[i].status !== 404 : now_[i].body?.sha !== f.sha)).map((f) => f.path.split('/').pop());
      if (stale.length) { sendJSON(req, res, 409, { ok: false, error: `Changed on GitHub since it was loaded: ${stale.join(', ')}. Reload; your draft is kept in this browser.`, stale }); return true; }
      const commit = await github(`${repo}/git/commits/${head}`);
      if (!commit.ok || !commit.body?.tree?.sha) return upstream(res, req, commit, 'reading the branch'), true;
      const tree = await post(`${repo}/git/trees`, { base_tree: commit.body.tree.sha, tree: files.map((f) => ({ path: f.path, mode: '100644', type: 'blob', content: f.text })) });
      if (!tree.ok || !tree.body?.sha) return upstream(res, req, tree, 'writing the files'), true;
      const made = await post(`${repo}/git/commits`, { message, tree: tree.body.sha, parents: [head] });
      if (!made.ok || !made.body?.sha) return upstream(res, req, made, 'writing the files'), true;
      const moved = await post(`${repo}/git/refs/heads/${encodeURIComponent(BRANCH)}`, { sha: made.body.sha, force: false }, 'PATCH');
      if (!moved.ok) return upstream(res, req, moved, 'writing the files'), true;
      sendJSON(req, res, 200, { ok: true, commit: { sha: made.body.sha }, files: files.map((f) => ({ path: f.path, sha: blobSha(f.text) })) });
      return true;
    }
    if (p === '/api/edit/run' && method === 'GET') {
      const sha = url.searchParams.get('sha') || '';
      if (!/^[0-9a-f]{7,40}$/.test(sha)) { sendJSON(req, res, 400, { ok: false, error: 'Bad commit.' }); return true; }
      const r = await github(`/repos/${REPO}/actions/runs?head_sha=${sha}&per_page=1`);
      if (!r.ok) return upstream(res, req, r, 'reading the build'), true;
      const run = r.body?.workflow_runs?.[0];
      sendJSON(req, res, 200, { ok: true, run: run ? { status: run.status, conclusion: run.conclusion, html_url: run.html_url } : null });
      return true;
    }
    sendJSON(req, res, 404, { ok: false, error: 'Endpoint not found.' });
    return true;
  }

  return { handle, configured, reason };
}
