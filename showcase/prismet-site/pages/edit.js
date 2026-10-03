// prismet.xyz/edit: change every word on the site without touching HTML or JSON.
//
// The page reads content/site.md and content/work/*.md straight from GitHub, shows one field per block of text, and
// commits the edited file back to the branch. The repository's GitHub Action then rebuilds and deploys the site, so a
// saved change is live about three minutes later. No dependencies. The GitHub token you paste is kept in this tab's
// sessionStorage only and is sent to api.github.com and nowhere else.
//
// The parser and writer below are the same as showcase/prismet-site/content.mjs, so what you save is exactly what the
// build reads. The functions are exported so tools/tests can round-trip them in Node.

export const REPO = 'sagedeutschle/Bayzyl';
export const DEFAULT_BRANCH = 'main';
export const SITE_FILE = 'showcase/prismet-site/content/site.md';
export const WORK_DIR = 'showcase/prismet-site/content/work';
const API = 'https://api.github.com';
const TOKEN_KEY = 'prismet-edit-token';

// ── the content format (mirror of content.mjs) ──────────────────────────────────────────────────────────────────────
export const stripComments = (s) => s.replace(/<!--[\s\S]*?-->/g, '');

export function parseSections(text) {
  const out = {};
  let key = null, buf = [];
  const flush = () => { if (key) out[key] = buf.join('\n').trim(); };
  for (const line of stripComments(text).split('\n')) {
    const m = line.match(/^## +(\S+)\s*$/);
    if (m) { flush(); key = m[1]; buf = []; } else if (key) buf.push(line);
  }
  flush();
  return out;
}

export function writeSections(text, updates) {
  const lines = text.split('\n'), out = [], seen = new Set();
  let skipping = false, inComment = false;
  for (const line of lines) {
    if (line.includes('<!--')) inComment = true;
    const m = !inComment && line.match(/^## +(\S+)\s*$/);
    if (line.includes('-->')) inComment = false;
    if (m) {
      skipping = false;
      out.push(line);
      if (m[1] in updates) { out.push(String(updates[m[1]]).trim(), ''); seen.add(m[1]); skipping = true; }
      continue;
    }
    if (!skipping) out.push(line);
  }
  for (const [k, v] of Object.entries(updates)) if (!seen.has(k)) out.push('', `## ${k}`, String(v).trim());
  return out.join('\n').replace(/\n{3,}/g, '\n\n').trimEnd() + '\n';
}

// ── what a field looks like ─────────────────────────────────────────────────────────────────────────────────────────
const LIST_KEYS = new Set(['facts', 'highlights', 'links']);
const HINTS = {
  facts: 'One fact per line: "- Label: Value".',
  highlights: 'One point per line, each starting with "- ".',
  links: 'One link per line: "- Label: https://…".',
  summary: 'The paragraph under the title on the project page. A blank line starts a new paragraph.',
  subtitle: 'One line under the title, on the page and in the register.',
  status: 'Shown as a small plaque: "Live", "Building", "Alpha · open source (MIT)"…',
  tag: 'The small line above the title on the home page door.',
};
export const label = (key) => key.replace(/[._]/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
export const isLong = (key, body) => LIST_KEYS.has(key) || body.includes('\n') || body.length > 90;
/** Placeholders like {count} must survive an edit, or the build prints them wrong. */
export const lostVars = (before, after) => (before.match(/\{\w+\}/g) || []).filter((v) => !after.includes(v));

// ── GitHub ──────────────────────────────────────────────────────────────────────────────────────────────────────────
const utf8ToB64 = (s) => { const b = new TextEncoder().encode(s); let bin = ''; for (const c of b) bin += String.fromCharCode(c); return btoa(bin); };
const b64ToUtf8 = (s) => { const bin = atob(s.replace(/\s/g, '')); const b = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) b[i] = bin.charCodeAt(i); return new TextDecoder().decode(b); };

export function makeClient(token, branch = DEFAULT_BRANCH, fetchImpl = globalThis.fetch) {
  const call = async (path, init = {}) => {
    const r = await fetchImpl(API + path, { ...init, headers: { accept: 'application/vnd.github+json', authorization: `Bearer ${token}`, 'x-github-api-version': '2022-11-28', ...(init.headers || {}) } });
    if (!r.ok) { const t = await r.text().catch(() => ''); throw new Error(`GitHub said ${r.status}${t ? `: ${t.slice(0, 160)}` : ''}`); }
    return r.status === 204 ? null : r.json();
  };
  return {
    whoami: () => call('/user'),
    listWork: async () => (await call(`/repos/${REPO}/contents/${WORK_DIR}?ref=${encodeURIComponent(branch)}`)).filter((f) => f.type === 'file' && f.name.endsWith('.md')).map((f) => f.path),
    getFile: async (path) => { const f = await call(`/repos/${REPO}/contents/${path}?ref=${encodeURIComponent(branch)}`); return { path, sha: f.sha, text: b64ToUtf8(f.content) }; },
    putFile: (path, text, sha, message) => call(`/repos/${REPO}/contents/${path}`, { method: 'PUT', body: JSON.stringify({ message, content: utf8ToB64(text), sha, branch }) }),
    runFor: async (sha) => { const r = await call(`/repos/${REPO}/actions/runs?head_sha=${sha}&per_page=1`); return r.workflow_runs?.[0] || null; },
  };
}

// ── the page ────────────────────────────────────────────────────────────────────────────────────────────────────────
function init() {
  const $ = (s) => document.querySelector(s);
  const branch = new URLSearchParams(location.search).get('branch') || DEFAULT_BRANCH;
  const status = $('#status'), filesNav = $('#files'), form = $('#editor'), actions = $('#actions'), deploy = $('#deploy');
  const say = (msg, kind = '') => { status.textContent = msg; status.className = `status ${kind}`; };
  let client = null, files = new Map(), current = null, pollTimer = null;
  const dirtyCount = () => [...files.values()].reduce((n, f) => n + f.dirty.size, 0);

  $('#branch').textContent = branch;
  const saved = sessionStorage.getItem(TOKEN_KEY);
  if (saved) { $('#token').value = saved; connect(saved); }

  $('#token-form').addEventListener('submit', (e) => { e.preventDefault(); connect($('#token').value.trim()); });
  $('#forget').addEventListener('click', () => { sessionStorage.removeItem(TOKEN_KEY); location.reload(); });
  window.addEventListener('beforeunload', (e) => { if (dirtyCount()) { e.preventDefault(); e.returnValue = ''; } });

  async function connect(token) {
    if (!token) return say('Paste a token first.', 'warn');
    say('Connecting…');
    try {
      client = makeClient(token, branch);
      const me = await client.whoami();
      sessionStorage.setItem(TOKEN_KEY, token);
      $('#connect').hidden = true;
      say(`Connected as ${me.login}. Loading the words…`);
      const paths = [SITE_FILE, ...(await client.listWork())];
      for (const p of paths) {
        const f = await client.getFile(p);
        files.set(p, { ...f, sections: parseSections(f.text), dirty: new Set(), fields: new Map() });
      }
      renderNav(); open(SITE_FILE);
      say(`Loaded ${files.size} files from ${branch}. Change any text and press Publish.`, 'ok');
    } catch (err) { client = null; say(`Could not connect: ${err.message}`, 'warn'); }
  }

  function titleOf(path) {
    if (path === SITE_FILE) return 'Home page and shared words';
    const f = files.get(path); return f?.sections.title || path.split('/').pop().replace(/\.md$/, '');
  }

  function renderNav() {
    filesNav.hidden = false; filesNav.replaceChildren();
    for (const path of files.keys()) {
      const b = document.createElement('button'); b.type = 'button'; b.dataset.path = path; b.textContent = titleOf(path);
      b.addEventListener('click', () => open(path)); filesNav.append(b);
    }
    markNav();
  }
  function markNav() {
    for (const b of filesNav.querySelectorAll('button')) {
      const f = files.get(b.dataset.path);
      b.classList.toggle('current', b.dataset.path === current);
      b.classList.toggle('dirty', f.dirty.size > 0);
      b.textContent = titleOf(b.dataset.path) + (f.dirty.size ? ` · ${f.dirty.size}` : '');
    }
  }

  function open(path) {
    current = path; const f = files.get(path);
    form.hidden = false; actions.hidden = false; form.replaceChildren();
    const h = document.createElement('h2'); h.textContent = titleOf(path); form.append(h);
    const small = document.createElement('p'); small.className = 'path'; small.textContent = path; form.append(small);
    for (const [key, body] of Object.entries(f.sections)) {
      const wrap = document.createElement('div'); wrap.className = 'field'; wrap.dataset.key = key;
      const lab = document.createElement('label'); lab.textContent = label(key); lab.htmlFor = `f-${key}`;
      const ta = document.createElement('textarea'); ta.id = `f-${key}`; ta.value = body; ta.rows = isLong(key, body) ? Math.min(14, Math.max(3, body.split('\n').length + 1)) : 1;
      ta.spellcheck = true; ta.addEventListener('input', () => edited(f, key, ta));
      const hint = document.createElement('small'); hint.textContent = HINTS[key] || (body.includes('{') ? 'Keep the {placeholders}; they are filled in by the build.' : '');
      wrap.append(lab, ta, hint); form.append(wrap); f.fields.set(key, ta);
      if (f.dirty.has(key)) wrap.classList.add('dirty');
    }
    markNav(); updateSave();
  }

  function edited(f, key, ta) {
    const changed = ta.value.trim() !== f.sections[key];
    if (changed) f.dirty.add(key); else f.dirty.delete(key);
    ta.closest('.field').classList.toggle('dirty', changed);
    const lost = lostVars(f.sections[key], ta.value);
    ta.closest('.field').classList.toggle('warn', lost.length > 0);
    ta.closest('.field').querySelector('small').textContent = lost.length ? `Missing ${lost.join(', ')}: the build fills these in, keep them.` : (HINTS[key] || '');
    markNav(); updateSave();
  }
  function updateSave() { const n = dirtyCount(); $('#save').disabled = n === 0; $('#save').textContent = n ? `Publish ${n} change${n === 1 ? '' : 's'}` : 'Nothing to publish'; }

  $('#save').addEventListener('click', async () => {
    const changedFiles = [...files.values()].filter((f) => f.dirty.size);
    if (!changedFiles.length) return;
    $('#save').disabled = true; say('Saving to GitHub…');
    let lastSha = null;
    try {
      for (const f of changedFiles) {
        const updates = {}; for (const k of f.dirty) updates[k] = f.fields.get(k)?.value ?? f.sections[k];
        const text = writeSections(f.text, updates);
        const keys = [...f.dirty].join(', ');
        const res = await client.putFile(f.path, text, f.sha, `Edit from prismet.xyz/edit: ${titleOf(f.path)} (${keys})`);
        f.text = text; f.sha = res.content.sha; f.sections = parseSections(text); f.dirty.clear(); lastSha = res.commit.sha;
        for (const [k, ta] of f.fields) { ta.closest('.field')?.classList.remove('dirty', 'warn'); ta.value = f.sections[k] ?? ta.value; }
      }
      markNav(); updateSave();
      say('Saved. The site is rebuilding; it goes live in about three minutes.', 'ok');
      watch(lastSha);
    } catch (err) { say(`Not saved: ${err.message}`, 'warn'); updateSave(); }
  });

  function watch(sha) {
    clearInterval(pollTimer);
    const link = `https://github.com/${REPO}/actions`;
    deploy.innerHTML = ''; const a = document.createElement('a'); a.href = link; a.textContent = 'Watch the build on GitHub'; a.target = '_blank'; a.rel = 'noopener';
    const span = document.createElement('span'); deploy.append(span, ' ', a);
    let ticks = 0;
    const tick = async () => {
      ticks++;
      try {
        const run = await client.runFor(sha);
        if (!run) { span.textContent = 'Waiting for the build to start…'; }
        else if (run.status !== 'completed') { span.textContent = `Build ${run.status.replace('_', ' ')}…`; a.href = run.html_url; }
        else { clearInterval(pollTimer); a.href = run.html_url; span.textContent = run.conclusion === 'success' ? 'Live on prismet.xyz.' : `The build ended with "${run.conclusion}"; nothing changed on the site. Open the run to see why.`; span.className = run.conclusion === 'success' ? 'ok' : 'warn'; }
      } catch { clearInterval(pollTimer); span.textContent = 'Saved. Follow the build on GitHub.'; }
      if (ticks > 60) clearInterval(pollTimer);
    };
    tick(); pollTimer = setInterval(tick, 15000);
  }
}

if (typeof document !== 'undefined') init();
