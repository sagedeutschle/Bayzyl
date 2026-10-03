// edit-check.mjs: the /edit page must read and write the content files exactly as the build does.
//   node showcase/tools/tests/edit-check.mjs
// 1. parseSections/writeSections in pages/edit.js give the same results as content.mjs on every content file.
// 2. writeSections keeps comments, order and untouched sections; an edit round-trips through parse.
// 3. makeClient sends the right GitHub requests (checked against a fake fetch; nothing goes on the network).
import { readFileSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const HERE = dirname(fileURLToPath(import.meta.url));
const SITE = join(HERE, '..', '..', 'prismet-site');
const edit = await import(join(SITE, 'pages', 'edit.js'));
const content = await import(join(SITE, 'content.mjs'));

let checks = 0;
const files = ['content/site.md', ...readdirSync(join(SITE, 'content/work')).filter((f) => f.endsWith('.md')).map((f) => `content/work/${f}`)];
for (const rel of files) {
  const text = readFileSync(join(SITE, rel), 'utf8');
  const a = edit.parseSections(text), b = content.parseSections(text);
  assert.deepEqual(a, b, `${rel}: parse differs from content.mjs`); checks++;
  const keys = Object.keys(a); assert.ok(keys.length > 0, `${rel}: no sections`);
  const k = keys[Math.floor(keys.length / 2)];
  const updates = { [k]: `${a[k]}\nEdited line with *gold* and {count}.` };
  const w1 = edit.writeSections(text, updates), w2 = content.writeSections(text, updates);
  assert.equal(w1, w2, `${rel}: write differs from content.mjs`); checks++;
  const again = edit.parseSections(w1);
  assert.equal(again[k], updates[k].trim(), `${rel}: the edit did not round-trip`); checks++;
  for (const other of keys) if (other !== k) assert.equal(again[other], a[other], `${rel}: ${other} changed although untouched`);
  checks++;
  if (text.includes('<!--')) assert.ok(w1.includes('<!--'), `${rel}: the comment block was lost`), checks++;
  assert.equal(Object.keys(again).join(','), keys.join(','), `${rel}: section order changed`); checks++;
}

assert.deepEqual(edit.lostVars('{shown} of {total} records', 'all records'), ['{shown}', '{total}']);
assert.deepEqual(edit.lostVars('{shown} of {total}', '{total} / {shown}'), []);
assert.equal(edit.label('hero.directory_source'), 'Hero Directory Source');
assert.equal(edit.isLong('facts', '- a: b'), true); assert.equal(edit.isLong('title', 'Bayzyl'), false); checks += 4;

// the client: a fake fetch records every request
const calls = [];
const fakeFetch = async (url, init = {}) => {
  calls.push({ url, method: init.method || 'GET', body: init.body ? JSON.parse(init.body) : null, auth: init.headers.authorization });
  const body = url.endsWith('/user') ? { login: 'sage' }
    : url.includes('/contents/showcase/prismet-site/content/work?') ? [{ type: 'file', name: 'bayzyl.md', path: 'showcase/prismet-site/content/work/bayzyl.md' }, { type: 'dir', name: 'x', path: 'x' }]
    : init.method === 'PUT' ? { content: { sha: 'newsha' }, commit: { sha: 'c0ffee' } }
    : url.includes('/actions/runs') ? { workflow_runs: [{ status: 'completed', conclusion: 'success', html_url: 'https://github.com/x' }] }
    : { sha: 'abc', content: Buffer.from('## title\nBayzyl ✓ é\n', 'utf8').toString('base64') };
  return { ok: true, status: 200, json: async () => body, text: async () => JSON.stringify(body) };
};
const c = edit.makeClient('tok', 'main', fakeFetch);
assert.equal((await c.whoami()).login, 'sage');
assert.deepEqual(await c.listWork(), ['showcase/prismet-site/content/work/bayzyl.md']);
const f = await c.getFile('showcase/prismet-site/content/site.md');
assert.equal(f.text, '## title\nBayzyl ✓ é\n', 'utf-8 decode');
const put = await c.putFile(f.path, '## title\nNew ✓ é\n', f.sha, 'msg');
assert.equal(put.commit.sha, 'c0ffee');
const last = calls.at(-1);
assert.equal(last.method, 'PUT'); assert.equal(last.body.branch, 'main'); assert.equal(last.body.sha, 'abc');
assert.equal(Buffer.from(last.body.content, 'base64').toString('utf8'), '## title\nNew ✓ é\n', 'utf-8 encode');
assert.ok(calls.every((x) => x.url.startsWith('https://api.github.com/') && x.auth === 'Bearer tok'), 'every call goes to api.github.com with the token');
assert.equal((await c.runFor('c0ffee')).conclusion, 'success'); checks += 8;

console.log(`✓ edit-check: ${checks} checks over ${files.length} content files; the /edit page reads and writes exactly what the build reads`);
