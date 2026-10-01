// editor.js — preview-only "Edit words" mode. Click any text, retype it, click away to save.
//
// Saves to the artifact's shared store when the page runs as a Claude artifact (so Claude can
// read the edits back and write them into content/*.md), otherwise to this browser.
// "Copy my changes" puts every edit on the clipboard in the same "## key" format as the
// content files, so it can also be pasted into a chat or straight into content/site.md.
(() => {
  const KEY_LS = 'prismet.edits';
  const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const inline = (s, vars) => esc(s)
    .replace(/\{(\w+)\}/g, (m, k) => (vars && k in vars ? esc(vars[k]) : m))
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>');
  const plain = (s, vars) => String(s).replace(/\{(\w+)\}/g, (m, k) => (vars && k in vars ? vars[k] : m)).replace(/\*\*?(.+?)\*\*?/g, '$1');
  const norm = (s) => s.replace(/ /g, ' ').replace(/\r/g, '').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();

  const nodes = () => [...document.querySelectorAll('[data-edit]')];
  const original = new Map();                     // key → wording the page was built with
  nodes().forEach((el) => { if (!original.has(el.dataset.edit)) original.set(el.dataset.edit, el.dataset.src ?? el.textContent); });
  const edits = new Map();                        // key → edited wording
  const current = (key) => (edits.has(key) ? edits.get(key) : original.get(key));

  function paint(key, force = false) {
    const text = current(key);
    document.querySelectorAll(`[data-edit="${CSS.escape(key)}"]`).forEach((el) => {
      el.dataset.src = text;
      if (!force && el === document.activeElement) return;
      const vars = el.dataset.count ? { count: el.dataset.count } : null;
      if (el instanceof SVGElement) el.textContent = plain(text, vars);
      else if (el.classList.contains('summary')) el.innerHTML = text.split(/\n\s*\n/).map((p) => `<p>${inline(p, vars)}</p>`).join('');
      else el.innerHTML = inline(text, vars);
      el.classList.toggle('bz-changed', edits.has(key));
    });
  }

  // ── storage: artifact db when available, else this browser ─────────────────────────────
  let db = null, where = 'browser';
  const lsLoad = () => { try { return JSON.parse(localStorage.getItem(KEY_LS) || '{}'); } catch { return {}; } };
  const lsSave = () => { try { localStorage.setItem(KEY_LS, JSON.stringify(Object.fromEntries(edits))); } catch { /* storage blocked */ } };
  Object.entries(lsLoad()).forEach(([k, v]) => { if (original.has(k)) { edits.set(k, v); paint(k); } });

  async function persist(key) {
    if (!db) { lsSave(); return true; }
    const ref = db.collection('edits').doc(key);
    try {
      if (edits.has(key)) await ref.set({ key, text: edits.get(key), at: new Date().toISOString() });
      else await ref.delete();
      return true;
    } catch (e) {
      say(e && e.code === 'not_granted' ? 'This view can read but not save edits.' : 'Couldn’t save that edit. Try again in a moment.');
      return false;
    }
  }

  (async () => {
    try { db = window.claude && typeof window.claude.use === 'function' ? await window.claude.use('db') : null; } catch { db = null; }
    if (!db) return;
    where = 'page';
    // Edits made earlier in this browser move into the shared store, one at a time.
    for (const k of [...edits.keys()]) await persist(k);
    try { localStorage.removeItem(KEY_LS); } catch { /* ignore */ }
    db.collection('edits').onSnapshot((qs) => {
      qs.docChanges().forEach((c) => {
        const d = c.doc.data() || {}, k = d.key || c.doc.id;
        if (!original.has(k)) return;
        if (c.type === 'removed') edits.delete(k); else if (typeof d.text === 'string') edits.set(k, d.text);
        paint(k);
      });
      count();
    }, () => say('Lost the connection to saved edits. Reload to retry.'));
    count();
  })();

  // ── the dock ───────────────────────────────────────────────────────────────────────────
  const dock = document.createElement('div');
  dock.className = 'bz-dock';
  dock.setAttribute('role', 'region');
  dock.setAttribute('aria-label', 'Edit the wording on this page');
  dock.innerHTML = `
    <button type="button" class="bz-btn bz-main" id="bz-toggle" aria-pressed="false">✎ Edit words</button>
    <button type="button" class="bz-btn" id="bz-copy" hidden>Copy my changes <span id="bz-n">0</span></button>
    <button type="button" class="bz-btn bz-quiet" id="bz-reset" hidden>Discard all</button>
    <p class="bz-msg" id="bz-msg" role="status" aria-live="polite"></p>`;
  document.body.appendChild(dock);
  const $ = (id) => document.getElementById(id);
  let timer;
  function say(msg) { const m = $('bz-msg'); m.textContent = msg; clearTimeout(timer); if (msg) timer = setTimeout(() => { m.textContent = editing ? hint() : ''; }, 3200); }
  const hint = () => 'Click any text and type. It saves when you click away. *word* = gold.';
  function count() { $('bz-n').textContent = edits.size; $('bz-copy').hidden = !editing && edits.size === 0; $('bz-reset').hidden = !editing || edits.size === 0; }

  let editing = false;
  const editable = (el) => !(el instanceof SVGElement) && !el.closest('.bz-dock');
  function setEditing(on) {
    editing = on;
    document.documentElement.classList.toggle('bz-editing', on);
    $('bz-toggle').setAttribute('aria-pressed', String(on));
    $('bz-toggle').textContent = on ? 'Done editing' : '✎ Edit words';
    nodes().filter(editable).forEach((el) => {
      if (on) { el.setAttribute('contenteditable', 'plaintext-only'); if (el.contentEditable !== 'plaintext-only') el.setAttribute('contenteditable', 'true'); el.spellcheck = true; }
      else { el.removeAttribute('contenteditable'); }
    });
    if (document.activeElement && document.activeElement.hasAttribute('data-edit')) document.activeElement.blur();
    say(on ? hint() : edits.size ? `${edits.size} change${edits.size === 1 ? '' : 's'} saved ${where === 'page' ? 'on this page' : 'in this browser'}.` : '');
    count();
  }
  $('bz-toggle').addEventListener('click', () => setEditing(!editing));

  // While editing, links and buttons don't navigate or filter; you're just placing the cursor.
  document.addEventListener('click', (e) => {
    if (!editing || e.target.closest('.bz-dock')) return;
    if (e.target.closest('[data-edit]') || e.target.closest('a, button')) e.preventDefault();
  }, true);

  document.addEventListener('focusin', (e) => {
    const el = e.target.closest && e.target.closest('[data-edit]');
    if (!editing || !el || !editable(el)) return;
    el.textContent = current(el.dataset.edit);   // show the raw wording, asterisks and all
  });
  document.addEventListener('keydown', (e) => {
    const el = e.target.closest && e.target.closest('[contenteditable][data-edit]');
    if (!el) return;
    if (e.key === 'Escape') { el.textContent = current(el.dataset.edit); el.blur(); }
    // a space inside a <button> would press it instead of typing
    if (e.key === ' ' && el.closest('button')) { e.preventDefault(); document.execCommand('insertText', false, ' '); }
    if (e.key === 'Enter' && !el.matches('.summary, p, li') && !e.shiftKey) { e.preventDefault(); el.blur(); }
  });
  document.addEventListener('focusout', async (e) => {
    const el = e.target.closest && e.target.closest('[contenteditable][data-edit]');
    if (!el) return;
    const key = el.dataset.edit, text = norm(el.innerText);
    if (!text) { say('That text can’t be empty. Put back the original wording.'); edits.delete(key); paint(key, true); count(); return; }
    if (text === current(key)) { paint(key, true); return; }
    if (text === original.get(key)) edits.delete(key); else edits.set(key, text);
    paint(key, true); count();
    if (await persist(key)) say(where === 'page' ? 'Saved on this page.' : 'Saved in this browser.');
  });

  $('bz-copy').addEventListener('click', async () => {
    const out = [...edits].map(([k, v]) => `## ${k}\n${v}\n`).join('\n');
    if (!out) { say('No changes yet.'); return; }
    try { await navigator.clipboard.writeText(out); say(`Copied ${edits.size} change${edits.size === 1 ? '' : 's'}. Paste them into the chat with Claude.`); }
    catch {
      let ta = $('bz-copybox');
      if (!ta) { ta = document.createElement('textarea'); ta.id = 'bz-copybox'; ta.className = 'bz-copybox'; ta.readOnly = true; ta.setAttribute('aria-label', 'Your changes'); dock.appendChild(ta); }
      ta.value = out; ta.hidden = false; ta.focus(); ta.select(); say('Selected. Press ⌘C to copy.');
    }
  });

  let armed = false;
  $('bz-reset').addEventListener('click', async () => {
    if (!armed) { armed = true; $('bz-reset').textContent = 'Discard all? Click again'; setTimeout(() => { armed = false; $('bz-reset').textContent = 'Discard all'; }, 3500); return; }
    armed = false; $('bz-reset').textContent = 'Discard all';
    for (const k of [...edits.keys()]) { edits.delete(k); paint(k); await persist(k); }
    count(); say('All changes discarded.');
  });

  count();
})();
