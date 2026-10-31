import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createRequire } from 'node:module';
import { once } from 'node:events';
import test from 'node:test';
import { connect } from 'node:net';
import { randomBytes } from 'node:crypto';
import { createSignaling } from '../../server/signaling.js';

const require = createRequire(new URL('../../server/package.json', import.meta.url));
const { WebSocket } = require('ws');

async function requestUpgrade(t, origin) {
  const signaling = createSignaling({ createBoundedCache: () => new Map() });
  const server = createServer();
  server.on('upgrade', (req, socket, head) => signaling.handleUpgrade(req, socket, head));
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const headers = origin === undefined ? {} : { Origin: origin };
  const ws = new WebSocket(`ws://127.0.0.1:${server.address().port}/rtc`, { headers });
  t.after(async () => {
    ws.terminate();
    signaling.close();
    await new Promise(resolve => server.close(resolve));
  });
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Upgrade did not settle')), 2000);
    const finish = value => { clearTimeout(timer); resolve(value); };
    ws.on('error', error => { clearTimeout(timer); reject(error); });
    ws.on('unexpected-response', (_req, res) => {
      res.resume();
      finish({ status: res.statusCode });
    });
    ws.on('open', () => {
      ws.once('message', data => finish({ status: 101, message: JSON.parse(data.toString()) }));
      ws.send(JSON.stringify({ type: 'ping' }));
    });
  });
}

for (const [name, origin] of [
  ['native client without Origin', undefined],
  ['Prismet browser', 'https://prismet.xyz'],
  ['www Prismet browser', 'https://www.prismet.xyz'],
]) {
  test(`keeps signaling available to ${name}`, async t => {
    assert.deepEqual(await requestUpgrade(t, origin), { status: 101, message: { type: 'pong' } });
  });
}

test('closes a rejected upgrade even when its peer retains a writable half', async t => {
  const signaling = createSignaling({ createBoundedCache: () => new Map() });
  const server = createServer();
  let socketClosed;
  server.on('upgrade', (req, socket, head) => {
    socketClosed = once(socket, 'close');
    signaling.handleUpgrade(req, socket, head);
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const client = connect({ host: '127.0.0.1', port: server.address().port, allowHalfOpen: true });
  t.after(async () => {
    client.destroy();
    signaling.close();
    await new Promise(resolve => server.close(resolve));
  });
  await once(client, 'connect');
  let response = '';
  client.on('data', data => { response += data.toString(); });
  const ended = once(client, 'end');
  client.write([
    'GET /rtc HTTP/1.1', 'Host: localhost', 'Connection: Upgrade', 'Upgrade: websocket',
    'Sec-WebSocket-Version: 13', `Sec-WebSocket-Key: ${randomBytes(16).toString('base64')}`,
    'Origin: https://audit.invalid', '', '',
  ].join('\r\n'));
  await ended;
  assert.match(response, /^HTTP\/1\.1 403 /);
  let timer;
  const closed = await Promise.race([
    socketClosed.then(() => true),
    new Promise(resolve => { timer = setTimeout(() => resolve(false), 200); }),
  ]);
  clearTimeout(timer);
  assert.equal(closed, true, 'The rejected connection releases its server socket');
});

for (const [name, origin] of [
  ['unrelated website', 'https://audit.invalid'],
  ['opaque browser origin', 'null'],
  ['empty Origin header', ''],
  ['insecure website', 'http://prismet.xyz'],
  ['hostname suffix spoof', 'https://prismet.xyz.audit.invalid'],
  ['different port', 'https://prismet.xyz:8443'],
  ['non-origin URL', 'https://prismet.xyz/path'],
  ['duplicate Origin headers', ['https://prismet.xyz', 'https://audit.invalid']],
]) {
  test(`rejects ${name} before a WebSocket upgrade`, async t => {
    assert.deepEqual(await requestUpgrade(t, origin), { status: 403 });
  });
}
