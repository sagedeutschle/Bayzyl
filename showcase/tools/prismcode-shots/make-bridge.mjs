// make-bridge.mjs — writes dist/mock-bridge.js: a stand-in for PrismCode's Electron preload.
// It serves a demo workspace (the public qr-scanner repo), a git state, past sessions, a PRISM
// A/B run, a terminal, and three scripted agents (Claude, Codex, DeepSeek) working one task.
//
//   node make-bridge.mjs [path/to/qr-scanner]
import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = process.argv[2] || '/home/user/sagedeutschle/qr-scanner';
const ROOT = '/code/qr-scanner'; // the path the demo shows (keep home-folder and account names out of captures)

// --- workspace: tree + text files ----------------------------------------------------------
const files = {};
const tree = (dir, rel = '') => {
  const kids = readdirSync(dir).filter((n) => n !== '.git').sort((a, b) => {
    const da = statSync(join(dir, a)).isDirectory(), db = statSync(join(dir, b)).isDirectory();
    return da === db ? a.localeCompare(b) : da ? -1 : 1;
  });
  return kids.map((name) => {
    const abs = join(dir, name), path = `${ROOT}/${rel}${name}`;
    if (statSync(abs).isDirectory()) return { name, path, kind: 'dir', children: tree(abs, `${rel}${name}/`) };
    if (/\.(html|md|js|json|css|txt)$|LICENSE|\.gitignore/.test(name) && statSync(abs).size < 60000) files[path] = readFileSync(abs, 'utf8');
    return { name, path, kind: 'file' };
  });
};
const children = tree(REPO);
// The file the agents create during the demo.
children.unshift({ name: 'tests', path: `${ROOT}/tests`, kind: 'dir', children: [{ name: 'torch.spec.js', path: `${ROOT}/tests/torch.spec.js`, kind: 'file' }] });
files[`${ROOT}/tests/torch.spec.js`] = `import { test, expect } from '@playwright/test';

// A fake camera track that reports torch support, so the toggle can be tested headless.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const track = {
      getCapabilities: () => ({ torch: true }),
      applyConstraints: async (c) => { window.__torch = c.advanced?.[0]?.torch; },
      stop() {}
    };
    navigator.mediaDevices.getUserMedia = async () => ({ getTracks: () => [track], getVideoTracks: () => [track] });
  });
});

test('torch toggle appears when the camera supports it', async ({ page }) => {
  await page.goto('/');
  await page.click('#start');
  await expect(page.locator('#torch')).toBeVisible();
});

test('torch toggle turns the light on and off', async ({ page }) => {
  await page.goto('/');
  await page.click('#start');
  await page.click('#torch');
  expect(await page.evaluate(() => window.__torch)).toBe(true);
  await page.click('#torch');
  expect(await page.evaluate(() => window.__torch)).toBe(false);
});
`;
const log = execSync(`git -C ${REPO} log --no-merges --format=%h%x09%at%x09%s -12`, { encoding: 'utf8' })
  .trim().split('\n').map((l) => { const [hash, at, subject] = l.split('\t'); return { hash, subject, author: 'Sage Deutschle', date: +at * 1000 }; });

// --- the change both agents make -------------------------------------------------------------
const indexDiff = `diff --git a/index.html b/index.html
--- a/index.html
+++ b/index.html
@@ -157,6 +157,7 @@
     <div class="controls">
       <button id="start">Start Camera</button>
+      <button id="torch" hidden aria-pressed="false" title="Flashlight">Light</button>
       <select id="cameraSelect" aria-label="Camera">
@@ -258,8 +259,23 @@
         activeStream = stream;
         video.srcObject = stream;
         await video.play();
+        setupTorch(stream.getVideoTracks()[0]);
         await loadCameras();
         setStatus("Point the camera at the QR code.");
         requestAnimationFrame(scan);
@@ -270,6 +286,20 @@
+    // Low-light codes: offer the flashlight when the camera track supports it.
+    function setupTorch(track) {
+      const caps = track?.getCapabilities?.() ?? {};
+      torchButton.hidden = !caps.torch;
+      torchButton.onclick = async () => {
+        const on = torchButton.getAttribute("aria-pressed") !== "true";
+        await track.applyConstraints({ advanced: [{ torch: on }] });
+        torchButton.setAttribute("aria-pressed", String(on));
+      };
+    }
+
     async function scan() {`;
const codexDiff = indexDiff.replace('title="Flashlight">Light', 'title="Toggle flashlight">🔦').replace('// Low-light codes: offer the flashlight when the camera track supports it.', '// Torch: only some rear cameras expose it (Chrome on Android, Safari 17.4+).');
const specDiff = `diff --git a/tests/torch.spec.js b/tests/torch.spec.js
new file mode 100644
--- /dev/null
+++ b/tests/torch.spec.js
@@ -0,0 +1,31 @@
` + files[`${ROOT}/tests/torch.spec.js`].trim().split('\n').map((l) => '+' + l).join('\n');

const PROMPT = 'Add a flashlight toggle to the scanner for low-light QR codes. Only show it when the camera supports torch, and cover it with a Playwright test.';

// --- scripted agents: [delayMs, event] ---------------------------------------------------------
const think = (s) => ({ t: 'thinking-delta', text: s });
const say = (s) => ({ t: 'text-delta', text: s });
const SCRIPTS = {
  claude: [
    [300, { t: 'status', s: 'thinking' }],
    [500, think('The camera stream is created in start(); the torch is a capability of the video track, so the toggle belongs right after video.play().')],
    [500, { t: 'status', s: 'tool' }],
    [200, { t: 'tool-start', id: 'c1', name: 'Read', input: { file_path: 'index.html' } }],
    [400, { t: 'tool-end', id: 'c1', output: '312 lines', isError: false }],
    [200, { t: 'tool-start', id: 'c2', name: 'Grep', input: { pattern: 'getUserMedia|applyConstraints' } }],
    [400, { t: 'tool-end', id: 'c2', output: 'index.html:246:        const stream = await navigator.mediaDevices.getUserMedia({\nindex.html:304:    if (!navigator.mediaDevices?.getUserMedia) {', isError: false }],
    [300, say('Plan: read the active track\'s capabilities after the stream starts. If `torch` is supported, reveal a Light button that flips it with `applyConstraints`. Browsers without torch never see the button.')],
    [300, { t: 'tool-start', id: 'c3', name: 'Edit', input: { file_path: 'index.html' } }],
    [300, { t: 'file-change', path: `${ROOT}/index.html`, kind: 'edit', diff: indexDiff }],
    [300, { t: 'tool-end', id: 'c3', output: 'Applied 3 hunks to index.html', isError: false }],
    [200, { t: 'tool-start', id: 'c4', name: 'Write', input: { file_path: 'tests/torch.spec.js' } }],
    [300, { t: 'file-change', path: `${ROOT}/tests/torch.spec.js`, kind: 'create', diff: specDiff }],
    [300, { t: 'tool-end', id: 'c4', output: 'Created tests/torch.spec.js (31 lines)', isError: false }],
    [200, { t: 'tool-start', id: 'c5', name: 'Bash', input: { command: 'npx playwright test tests/torch.spec.js' } }],
    [700, { t: 'tool-end', id: 'c5', output: 'Running 2 tests using 2 workers\n  ✓ torch toggle appears when the camera supports it (412ms)\n  ✓ torch toggle turns the light on and off (388ms)\n\n  2 passed (1.9s)', isError: false }],
    [300, say('Done. The Light button shows up only on cameras that expose a torch (most rear phone cameras), and stays hidden on laptops. Both Playwright tests pass against a fake torch-capable track.')],
    [200, { t: 'turn-end', usage: { inputTokens: 48210, outputTokens: 2875, costUsd: 0.41 } }],
    [50, { t: 'status', s: 'idle' }],
  ],
  codex: [
    [400, { t: 'status', s: 'thinking' }],
    [600, think('Need to find where the MediaStream is created; torch support is per-track via getCapabilities().torch.')],
    [300, { t: 'status', s: 'tool' }],
    [200, { t: 'tool-start', id: 'x1', name: 'shell', input: { command: 'rg -n "getUserMedia|srcObject" index.html' } }],
    [500, { t: 'tool-end', id: 'x1', output: '246:        const stream = await navigator.mediaDevices.getUserMedia({\n259:        video.srcObject = stream;', isError: false }],
    [300, { t: 'tool-start', id: 'x2', name: 'apply_patch', input: { path: 'index.html' } }],
    [300, { t: 'file-change', path: `${ROOT}/index.html`, kind: 'edit', diff: codexDiff }],
    [300, { t: 'tool-end', id: 'x2', output: 'Success. Updated the following files:\nM index.html', isError: false }],
    [300, say('Added a 🔦 toggle next to Start Camera. It is hidden unless `track.getCapabilities().torch` is true, so desktop webcams never show it.')],
    [200, { t: 'permission-request', id: 'x-perm', tool: 'shell', input: { command: 'npm i -D @playwright/test' } }],
    [50, { t: 'status', s: 'awaiting-approval' }],
  ],
  codexAfterApproval: [
    [200, { t: 'status', s: 'tool' }],
    [200, { t: 'tool-start', id: 'x3', name: 'shell', input: { command: 'npm i -D @playwright/test' } }],
    [600, { t: 'tool-end', id: 'x3', output: 'added 3 packages in 2s', isError: false }],
    [300, say('Installed the test runner. Test file next.')],
    [200, { t: 'turn-end', usage: { inputTokens: 31640, outputTokens: 1990, costUsd: 0.27 } }],
    [50, { t: 'status', s: 'idle' }],
  ],
  deepseek: [
    [300, { t: 'status', s: 'thinking' }],
    [700, think('Torch is exposed through MediaTrackCapabilities. Check support before showing UI; Safari added it in 17.4, Firefox does not support it.')],
    [500, say('Browser support check for the torch toggle:\n\n')],
    [300, say('- Chrome on Android: supported on rear cameras\n- Safari on iOS 17.4+: supported\n- Firefox: not supported, so the button must stay hidden\n- Desktop webcams: no torch, button hidden\n')],
    [300, say('Reading `getCapabilities().torch` after `getUserMedia` covers all four cases. No polyfill needed. Runs locally, so this review cost nothing.')],
    [200, { t: 'turn-end', usage: { inputTokens: 9120, outputTokens: 640, costUsd: 0 } }],
    [50, { t: 'status', s: 'idle' }],
  ],
};

const now = Date.UTC(2026, 8, 30, 21, 40);
const DATA = {
  ROOT, PROMPT, files, log, SCRIPTS,
  tree: { name: 'qr-scanner', path: ROOT, kind: 'dir', children },
  git: {
    status: { branch: 'feat/torch-toggle', ahead: 1, behind: 0, staged: [], unstaged: [{ path: 'index.html', code: '.M' }], untracked: ['tests/torch.spec.js'] },
    branches: { current: 'feat/torch-toggle', local: ['main', 'feat/torch-toggle', 'playlist-maker'] },
    diffs: { 'index.html': indexDiff, 'tests/torch.spec.js': specDiff },
  },
  profiles: {
    claude: [
      { name: 'Personal', dir: '~/.claude', color: '#d97757', hasToken: true, keychainDefault: false },
      { name: 'Work', dir: '~/.claude-work', color: '#e0a458', hasToken: true, keychainDefault: false },
    ],
    codex: [{ name: 'Default', dir: '~/.codex', color: '#2dd4bf', hasToken: false, keychainDefault: true }],
    deepseek: [],
  },
  sessions: [
    { id: 's1', nativeSessionId: 'n1', project: ROOT, agentKind: 'claude', profileDir: '~/.claude', title: 'Add Playlist Maker page', status: 'done', costUsd: 2.84, createdAt: now - 9 * 864e5, lastActiveAt: now - 9 * 864e5 + 5e6 },
    { id: 's2', nativeSessionId: 'n2', project: ROOT, agentKind: 'codex', profileDir: '~/.codex', title: 'Home Screen Planner: folder drag-and-drop', status: 'done', costUsd: 1.37, createdAt: now - 15 * 864e5, lastActiveAt: now - 15 * 864e5 + 3e6 },
    { id: 's3', nativeSessionId: 'n3', project: ROOT, agentKind: 'deepseek', profileDir: null, title: 'Review Phone Declutter copy', status: 'done', costUsd: 0, createdAt: now - 16 * 864e5, lastActiveAt: now - 16 * 864e5 + 9e5 },
    { id: 's4', nativeSessionId: 'n4', project: ROOT, agentKind: 'claude', profileDir: '~/.claude-work', title: 'Fix layout blowup with long camera names', status: 'done', costUsd: 0.62, createdAt: now - 21 * 864e5, lastActiveAt: now - 21 * 864e5 + 2e6 },
  ],
  prism: {
    run: { id: 'run-1', root: ROOT, prompt: PROMPT, status: 'complete', worktrees: [{ kind: 'claude', path: `${ROOT}/.prism/claude`, changedFiles: 2 }, { kind: 'codex', path: `${ROOT}/.prism/codex`, changedFiles: 1 }], createdAt: now },
    compare: {
      runId: 'run-1',
      summary: [{ kind: 'claude', filesChanged: 2, additions: 47, deletions: 0 }, { kind: 'codex', filesChanged: 1, additions: 16, deletions: 0 }],
      files: [{ path: 'index.html', claudeDiff: indexDiff, codexDiff }, { path: 'tests/torch.spec.js', claudeDiff: specDiff }],
    },
  },
  terminal: '\x1b[38;5;245m~/code/qr-scanner\x1b[0m \x1b[38;5;141mfeat/torch-toggle\x1b[0m $ npx playwright test\r\n\r\nRunning 2 tests using 2 workers\r\n\r\n  \x1b[32m✓\x1b[0m  1 torch toggle appears when the camera supports it \x1b[2m(412ms)\x1b[0m\r\n  \x1b[32m✓\x1b[0m  2 torch toggle turns the light on and off \x1b[2m(388ms)\x1b[0m\r\n\r\n  \x1b[32m2 passed\x1b[0m \x1b[2m(1.9s)\x1b[0m\r\n\r\n\x1b[38;5;245m~/code/qr-scanner\x1b[0m \x1b[38;5;141mfeat/torch-toggle\x1b[0m $ ',
};

const bridge = `// Generated by make-bridge.mjs — demo stand-in for PrismCode's Electron preload.
(() => {
  const D = ${JSON.stringify(DATA)};
  const ok = (value) => Promise.resolve({ ok: true, value });
  const subs = (set) => (cb) => { set.add(cb); return () => set.delete(cb); };
  const agentSubs = new Set(), ptyData = new Set(), ptyExit = new Set(), treeSubs = new Set();
  const panes = {};
  const play = (paneId, steps) => {
    let t = 0;
    for (const [d, ev] of steps) { t += d * (window.__speed || 1); setTimeout(() => agentSubs.forEach((cb) => cb(paneId, [ev])), t); }
  };
  const rel = (p) => p.startsWith(D.ROOT + '/') ? p.slice(D.ROOT.length + 1) : p;
  window.prismAgent = {
    startSession: (paneId, kind) => { panes[paneId] = kind; setTimeout(() => agentSubs.forEach((cb) => cb(paneId, [{ t: 'session', id: 'demo-' + paneId }])), 50); play(paneId, D.SCRIPTS[kind] || []); return ok({ sessionId: 'demo-' + paneId }); },
    sendMessage: () => ok(undefined),
    respondPermission: (paneId) => { if (panes[paneId] === 'codex') play(paneId, D.SCRIPTS.codexAfterApproval); return ok(undefined); },
    interrupt: () => ok(undefined),
    onEvent: subs(agentSubs),
    listProfiles: (kind) => ok(kind ? D.profiles[kind] || [] : [...D.profiles.claude, ...D.profiles.codex]),
    listModels: (kind) => ok(kind === 'deepseek' ? ['deepseek-context:latest', 'deepseek-r1:14b', 'qwen3-coder:30b'] : []),
    setPaneProfile: () => ok(undefined), saveProfileToken: () => ok(undefined), clearProfileToken: () => ok(undefined),
    addProfile: (kind, name) => ok({ name, dir: '~/.' + kind + '-' + name.toLowerCase(), color: '#999', hasToken: false, keychainDefault: true }),
  };
  window.prismFs = {
    openFolder: () => ok(D.ROOT),
    readTree: () => ok(D.tree),
    readFile: (p) => p in D.files ? ok(D.files[p]) : (D.files[D.ROOT + '/' + p] != null ? ok(D.files[D.ROOT + '/' + p]) : Promise.resolve({ ok: false, error: 'binary file' })),
    writeFile: (p, t) => { D.files[p] = t; return ok(undefined); },
    onTreeChange: subs(treeSubs),
    searchText: (root, q) => { const out = []; for (const [p, t] of Object.entries(D.files)) t.split('\\n').forEach((line, i) => { const c = line.indexOf(q); if (c >= 0 && out.length < 50) out.push({ path: p, line: i + 1, column: c + 1, preview: line.trim() }); }); return ok(out); },
  };
  let ptyN = 0;
  window.prismPty = {
    create: () => { const id = 'pty' + (++ptyN); setTimeout(() => ptyData.forEach((cb) => cb(id, new TextEncoder().encode(D.terminal))), 300); return ok(id); },
    write: () => ok(undefined), resize: () => ok(undefined), kill: () => ok(undefined),
    onData: subs(ptyData), onExit: subs(ptyExit),
  };
  window.prismGit = {
    status: () => ok(D.git.status), stage: () => ok(undefined), unstage: () => ok(undefined), discard: () => ok(undefined),
    commit: () => ok('a1b2c3d'), branches: () => ok(D.git.branches), checkout: () => ok(undefined),
    log: (root, n) => ok(D.log.slice(0, n)),
    diffFile: (root, p) => ok(D.git.diffs[rel(p)] || ''),
  };
  window.prismStore = {
    listSessions: () => ok(D.sessions),
    getTranscript: () => ok([{ t: 'text-delta', text: 'Session restored.' }]),
    updateSession: () => ok(undefined),
  };
  window.prismPrism = {
    fanOut: () => ok(D.prism.run), status: () => ok(D.prism.run), compare: () => ok(D.prism.compare),
    applyWinner: () => ok(undefined), discard: () => ok(undefined), keepBoth: () => ok(undefined),
  };
  window.prismDev = { rebuild: () => Promise.resolve({ ok: true, message: 'ok' }) };
  window.__demo = D;
})();
`;
writeFileSync(join(HERE, 'dist/mock-bridge.js'), bridge);
// Screenshot-only overrides: Inter stands in for the Mac system font, JetBrains Mono loads up front
// (xterm measures glyphs once), and transcript cards don't shrink when a pane overflows (app bug:
// .transcript is a flex column and .tool has overflow:hidden, so tool cards collapse to slivers).
writeFileSync(join(HERE, 'dist/shot-overrides.css'), `@font-face{font-family:"JetBrains Mono";src:url(fonts/JetBrainsMono.woff2) format("woff2");font-weight:100 900;font-display:block}
:root{--font-ui:"Inter",system-ui,sans-serif !important}
.transcript>*{flex-shrink:0}
`);
console.log(`mock-bridge.js: ${Object.keys(files).length} files, ${log.length} commits, ${(bridge.length / 1024).toFixed(0)} KB`);
