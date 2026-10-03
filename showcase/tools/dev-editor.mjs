// dev-editor.mjs — the editor on this machine, against this checkout instead of GitHub.
//
//   node showcase/prismet-site/build.mjs && node showcase/tools/dev-editor.mjs [--write] [--port 18300]
//
// Starts the real server (showcase/server/server.js) with the fresh build as site/ and a fake GitHub that reads the
// working tree. "Publish" then lands in memory, or in the working tree with --write (rebuild to see it built).
// The PIN is $EDIT_PIN, or "prismet-dev" for this local session only. Nothing here talks to GitHub or Fly.
import { spawn } from 'node:child_process';
import { existsSync, rmSync, cpSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { fakeGitHub } from './tests/fake-github.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..'), DIST = join(ROOT, 'showcase/prismet-site/dist'), SERVER = join(ROOT, 'showcase/server');
if (!existsSync(join(DIST, 'edit.html'))) { console.error('build first: node showcase/prismet-site/build.mjs'); process.exit(1); }
const arg = (name) => { const i = process.argv.indexOf(name); return i < 0 ? null : process.argv[i + 1]; };
const port = arg('--port') || '18300', persist = process.argv.includes('--write');

const gh = await fakeGitHub({ root: ROOT, persist }).listen();
rmSync(join(SERVER, 'site'), { recursive: true, force: true }); cpSync(DIST, join(SERVER, 'site'), { recursive: true });
const pin = process.env.EDIT_PIN || 'prismet-dev';
const server = spawn(process.execPath, ['server.js'], { cwd: SERVER, stdio: 'inherit', env: { ...process.env, PORT: port, EDIT_GITHUB_TOKEN: 'local-dev-not-a-token', EDIT_PIN: pin, EDIT_GITHUB_API: gh.url, EDIT_COOKIE_SECURE: '0' } });
console.log(`editor: http://127.0.0.1:${port}/edit   PIN: ${process.env.EDIT_PIN ? '$EDIT_PIN' : pin}   publish → ${persist ? 'the working tree' : 'memory only'}`);
const stop = () => { server.kill(); gh.close(); process.exit(0); };
process.on('SIGINT', stop); process.on('SIGTERM', stop); server.on('exit', stop);
