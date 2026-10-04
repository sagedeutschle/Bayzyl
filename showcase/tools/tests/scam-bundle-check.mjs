// Data files loaded at runtime are not discoverable from HTML links. Refuse an
// incomplete deployment bundle before the browser can print a misleading bill.
import assert from 'node:assert/strict';
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { verifyScamData } from '../../prismet-site/verify.mjs';

const source = join(dirname(fileURLToPath(import.meta.url)), '../../prismet-site/pages/scam-data');
const root = mkdtempSync(join(tmpdir(), 'prismet-scam-bundle-'));
try {
  cpSync(source, join(root, 'scam-data'), { recursive: true });
  assert.deepEqual(verifyScamData(root), [], 'The complete source bundle is deployable');
  const localFile = join(root, 'scam-data/local-2026.json');
  const original = readFileSync(localFile);
  rmSync(localFile);
  assert.ok(verifyScamData(root).some(message => message.includes('local-2026.json')), 'Missing local table fails before deploy');
  writeFileSync(localFile, original);
  writeFileSync(join(root, 'scam-data/zip-local/432.json'), '{');
  assert.ok(verifyScamData(root).some(message => message.includes('432.json')), 'Malformed ZIP shard fails before deploy');
  cpSync(join(source, 'zip-local/432.json'), join(root, 'scam-data/zip-local/432.json'));
  rmSync(join(root, 'scam-data/zip-local/100.json'));
  assert.ok(verifyScamData(root).some(message => message.includes('100.json')), 'A missing prefix cannot silently lose local coverage');
  cpSync(join(source, 'zip-local/100.json'), join(root, 'scam-data/zip-local/100.json'));
  writeFileSync(join(root, 'scam-data/zip-local/432.json'), '{"99999":[1,2,3]}');
  assert.ok(verifyScamData(root).some(message => message.includes('432.json')), 'A ZIP cannot be packaged under the wrong prefix');
  writeFileSync(join(root, 'scam-data/zip-local/432.json'), '{}');
  assert.ok(verifyScamData(root).some(message => message.includes('432.json')), 'An empty placeholder cannot replace a covered prefix');
  console.log('✓ Uncle Scam bundle: complete data, missing table/shard, malformed JSON, wrong-prefix and empty-placeholder detection');
} finally {
  rmSync(root, { recursive: true, force: true });
}
