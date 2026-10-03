// fake-github.mjs — the slice of the GitHub API that server/edit-api.js uses, answered from this checkout.
// Reads come from the working tree; writes stay in memory unless persist is set, so a test or a local editing
// session never changes the repository by accident. Used by edit-check.mjs and tools/dev-editor.mjs.
//
//   const gh = fakeGitHub({ root });        // root = the repository's top folder
//   gh.fetch(url, init)                      // a fetch() for createEditApi({ fetchImpl })
//   await gh.listen()                        // or an HTTP server for EDIT_GITHUB_API; resolves to its URL
//   gh.written                               // Map path → text of everything written
import { createServer } from 'node:http';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, statSync, existsSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';

const blobSha = (text) => { const b = Buffer.from(text, 'utf8'); return createHash('sha1').update(`blob ${b.length}\0`).update(b).digest('hex'); };
const hex = (s) => createHash('sha1').update(s).digest('hex');

export function fakeGitHub({ root, persist = false } = {}) {
  const written = new Map(), trees = new Map(), commits = new Map(), blobs = new Map(), calls = [];
  let head = hex('head-0');
  const read = (path) => (written.has(path) ? written.get(path) : existsSync(join(root, path)) && statSync(join(root, path)).isFile() ? readFileSync(join(root, path), 'utf8') : null);
  const write = (path, text) => { written.set(path, text); if (persist) { mkdirSync(dirname(join(root, path)), { recursive: true }); writeFileSync(join(root, path), text); } };
  const answer = (status, body) => ({ status, body });

  function handle(method, url, body, auth) {
    const u = new URL(url, 'http://fake'), m = u.pathname.match(/^\/repos\/[^/]+\/[^/]+\/(.*)$/), rest_ = m ? m[1] : '';
    calls.push({ method, path: u.pathname, query: u.search, auth, body: rest_ === 'git/blobs' ? null : body });
    if (!m) return answer(404, { message: 'Not Found' });
    const rest = decodeURIComponent(m[1]);
    if (rest.startsWith('contents/')) {
      const path = rest.slice('contents/'.length).replace(/\/$/, '');
      if (method === 'PUT') {
        const cur = read(path);
        if (cur !== null && blobSha(cur) !== body.sha) return answer(409, { message: 'sha does not match' });
        const text = Buffer.from(body.content, 'base64').toString('utf8'); write(path, text);
        head = hex(head + path + text);
        return answer(200, { content: { sha: blobSha(text) }, commit: { sha: head } });
      }
      const abs = join(root, path);
      if (existsSync(abs) && statSync(abs).isDirectory()) {
        const names = new Set([...readdirSync(abs), ...[...written.keys()].filter((k) => dirname(k) === path).map((k) => k.slice(path.length + 1))]);
        return answer(200, [...names].sort().map((name) => ({ type: 'file', name, path: `${path}/${name}` })));
      }
      const text = read(path);
      if (Buffer.isBuffer(text)) return answer(200, { sha: hex('bin' + path), content: text.toString('base64') });
      return text === null ? answer(404, { message: 'Not Found' }) : answer(200, { sha: blobSha(text), content: Buffer.from(text, 'utf8').toString('base64') });
    }
    if (rest.startsWith('git/ref/heads/')) return answer(200, { object: { sha: head } });
    if (rest.startsWith('git/commits/')) return answer(200, { sha: rest.slice(12), tree: { sha: hex('tree' + rest) } });
    if (rest === 'git/blobs' && method === 'POST') { const sha = hex('blob' + body.content); blobs.set(sha, Buffer.from(body.content, body.encoding === 'base64' ? 'base64' : 'utf8')); return answer(201, { sha }); }
    if (rest === 'git/trees' && method === 'POST') { const sha = hex(JSON.stringify(body)); trees.set(sha, body.tree); return answer(201, { sha }); }
    if (rest === 'git/commits' && method === 'POST') {
      if (!trees.has(body.tree) || body.parents?.[0] !== head) return answer(422, { message: 'bad tree or parent' });
      const sha = hex(body.message + body.tree + head); commits.set(sha, body); return answer(201, { sha });
    }
    if (rest.startsWith('git/refs/heads/') && method === 'PATCH') {
      const c = commits.get(body.sha);
      if (!c || c.parents[0] !== head) return answer(422, { message: 'not a fast-forward' });
      for (const f of trees.get(c.tree)) { if (f.sha) written.set(f.path, blobs.get(f.sha)); else write(f.path, f.content); }
      head = body.sha;
      return answer(200, { object: { sha: head } });
    }
    if (rest === 'commits') return answer(200, [{ sha: head, commit: { message: 'Edit from prismet.xyz/edit: site.md (1)\n\nbody', committer: { date: '2026-10-03T22:54:18Z' } } }, { sha: hex('older'), commit: { message: 'An older revision', committer: { date: '2026-10-02T10:00:00Z' } } }]);
    if (rest.startsWith('actions/runs')) return answer(200, { workflow_runs: [{ status: 'completed', conclusion: 'success', html_url: 'https://github.com/sagedeutschle/Bayzyl/actions/runs/1' }] });
    return answer(404, { message: 'Not Found' });
  }

  return {
    written, calls,
    get head() { return head; },
    fetch: async (url, init = {}) => {
      const r = handle(init.method || 'GET', url, init.body ? JSON.parse(init.body) : null, init.headers?.authorization);
      return { ok: r.status < 400, status: r.status, text: async () => JSON.stringify(r.body) };
    },
    listen: () => new Promise((resolve) => {
      const server = createServer((req, res) => {
        let raw = ''; req.on('data', (c) => { raw += c; }); req.on('end', () => {
          const r = handle(req.method, req.url, raw ? JSON.parse(raw) : null, req.headers.authorization);
          res.writeHead(r.status, { 'content-type': 'application/json' }); res.end(JSON.stringify(r.body));
        });
      });
      server.listen(0, '127.0.0.1', () => resolve({ url: `http://127.0.0.1:${server.address().port}`, close: () => server.close() }));
    }),
  };
}
