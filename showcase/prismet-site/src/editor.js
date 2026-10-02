// editor.js — preview-only Edit mode for prismet.xyz: change WORDS and LAYOUT by clicking around.
//
// Words:  click any text, type, click away. *word* = gold, **word** = bold.
// Layout: section bars (move up/down, hide), card bars (drag, ◀ ▶, Wide, ★ Feature, Hide),
//         featured rows (▲ ▼, Remove from Selected).
// Saves to the artifact's shared store when the page runs as a Claude artifact (Claude reads it
// back and writes it into content/*.md and data/projects.json), otherwise to this browser.
(() => {
  const LS_TEXT = 'prismet.edits', LS_LAYOUT = 'prismet.layout';
  const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const inline = (s, vars) => esc(s)
    .replace(/\{(\w+)\}/g, (m, k) => (vars && k in vars ? esc(vars[k]) : m))
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>');
  const plain = (s, vars) => String(s).replace(/\{(\w+)\}/g, (m, k) => (vars && k in vars ? vars[k] : m)).replace(/\*\*?(.+?)\*\*?/g, '$1');
  const norm = (s) => s.replace(/ /g, ' ').replace(/\r/g, '').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
  const lsGet = (k, d) => { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch { return d; } };
  const lsSet = (k, v) => { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, JSON.stringify(v)); } catch { /* storage blocked */ } };

  // ══ WORDS ═════════════════════════════════════════════════════════════════════════════════
  const nodes = () => [...document.querySelectorAll('[data-edit]')];
  const original = new Map();
  nodes().forEach((el) => { if (!original.has(el.dataset.edit)) original.set(el.dataset.edit, el.dataset.src ?? el.textContent); });
  const edits = new Map();
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

  // ══ LAYOUT ════════════════════════════════════════════════════════════════════════════════
  const seed = (() => { try { return JSON.parse(document.getElementById('bz-layout')?.textContent || 'null'); } catch { return null; } })();
  const NAMES = seed?.names || {};
  const SEC_NAMES = { lenses: 'Lenses', selected: 'Selected work', work: 'All work', about: 'About + Hire' };
  const clone = (o) => JSON.parse(JSON.stringify(o));
  const BASE = seed ? clone({ sections: seed.sections, hiddenSections: seed.hiddenSections, order: seed.order, featured: seed.featured, wide: seed.wide, hidden: seed.hidden }) : null;
  let layout = BASE ? clone(BASE) : null;
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const layoutChanged = () => layout && !same(layout, BASE);

  // Trust nothing from storage: keep only known ids, add anything new at the end.
  function sanitize(l) {
    if (!BASE || !l || typeof l !== 'object') return BASE && clone(BASE);
    const keep = (arr, valid) => (Array.isArray(arr) ? arr.filter((x, i) => valid.includes(x) && arr.indexOf(x) === i) : []);
    const secs = keep(l.sections, BASE.sections), order = keep(l.order, BASE.order);
    return {
      sections: [...secs, ...BASE.sections.filter((x) => !secs.includes(x))],
      hiddenSections: keep(l.hiddenSections, BASE.sections),
      order: [...order, ...BASE.order.filter((x) => !order.includes(x))],
      featured: keep(l.featured, BASE.order).filter((s) => document.querySelector(`.feature[data-slug="${CSS.escape(s)}"]`)),
      wide: keep(l.wide, BASE.order), hidden: keep(l.hidden, BASE.order),
    };
  }

  const main = document.getElementById('main');
  const grid = document.getElementById('grid');
  const selWrap = document.querySelector('[data-section="selected"] .doors') || document.querySelector('[data-section="selected"] .wrap');

  function applyLayout() {
    if (!layout || !main) return;
    layout.sections.forEach((id) => { const s = main.querySelector(`:scope > [data-section="${id}"]`); if (s) main.appendChild(s); });
    main.querySelectorAll(':scope > [data-section]').forEach((s) => s.classList.toggle('is-off', layout.hiddenSections.includes(s.dataset.section)));
    if (grid) {
      layout.order.forEach((slug) => { const c = grid.querySelector(`.card[data-slug="${CSS.escape(slug)}"]`); if (c) c.parentElement.appendChild(c); });  // rows stay inside their ledger group
      grid.querySelectorAll('.card').forEach((c) => {
        const s = c.dataset.slug, wide = layout.wide.includes(s), img = c.querySelector('img[data-src-wide]');
        c.classList.toggle('wide', wide); c.classList.toggle('is-off', layout.hidden.includes(s));
        if (img) { const want = wide ? img.dataset.srcWide : img.dataset.srcNormal; if (img.getAttribute('src') !== want) img.setAttribute('src', want); }
      });
      const n = layout.order.length - layout.hidden.length;
      document.querySelectorAll('[data-edit="work.intro"]').forEach((el) => { el.dataset.count = n; });
      paint('work.intro');
    }
    if (selWrap) {
      let i = 0;
      layout.featured.forEach((slug) => { const f = selWrap.querySelector(`.feature[data-slug="${CSS.escape(slug)}"]`); if (f) selWrap.appendChild(f); });
      selWrap.querySelectorAll('.feature').forEach((f) => {
        const on = layout.featured.includes(f.dataset.slug) && !layout.hidden.includes(f.dataset.slug);
        f.classList.toggle('is-off', !on);
        f.classList.toggle('flip', on && (i++ % 2 === 1));
      });
    }
    refreshBars();
  }

  // ══ STORAGE ═══════════════════════════════════════════════════════════════════════════════
  let db = null, where = 'browser';
  Object.entries(lsGet(LS_TEXT, {})).forEach(([k, v]) => { if (original.has(k)) { edits.set(k, v); paint(k); } });
  if (BASE) { const saved = lsGet(LS_LAYOUT, null); if (saved) layout = sanitize(saved); }

  async function persistText(key) {
    if (!db) { lsSet(LS_TEXT, Object.fromEntries(edits)); return true; }
    const ref = db.collection('edits').doc(key);
    try {
      if (edits.has(key)) await ref.set({ key, text: edits.get(key), at: new Date().toISOString() }); else await ref.delete();
      return true;
    } catch (e) { say(e && e.code === 'not_granted' ? 'This view can read but not save edits.' : 'Couldn’t save that edit. Try again in a moment.'); return false; }
  }
  // Layout writes: one at a time, coalesced, only when it actually changed.
  let layoutTimer = null, layoutBusy = Promise.resolve(), lastWritten = null;
  function persistLayout() {
    clearTimeout(layoutTimer);
    layoutTimer = setTimeout(() => {
      const snap = clone(layout);
      if (!db) { lsSet(LS_LAYOUT, layoutChanged() ? snap : null); say('Layout saved in this browser.'); return; }
      layoutBusy = layoutBusy.then(async () => {
        if (same(snap, lastWritten)) return;
        try {
          const ref = db.doc('layout/main');
          if (same(snap, BASE)) await ref.delete(); else await ref.set({ ...snap, at: new Date().toISOString() });
          lastWritten = snap; say('Layout saved on this page.');
        } catch (e) { say(e && e.code === 'not_granted' ? 'This view can read but not save the layout.' : 'Couldn’t save the layout. Try again in a moment.'); }
      });
    }, 450);
  }

  (async () => {
    try { db = window.claude && typeof window.claude.use === 'function' ? await window.claude.use('db') : null; } catch { db = null; }
    if (!db) return;
    where = 'page';
    for (const k of [...edits.keys()]) await persistText(k);         // move browser-only edits up
    lsSet(LS_TEXT, null);
    if (layoutChanged() && lsGet(LS_LAYOUT, null)) { persistLayout(); lsSet(LS_LAYOUT, null); }
    db.collection('edits').onSnapshot((qs) => {
      qs.docChanges().forEach((c) => {
        const d = c.doc.data() || {}, k = d.key || c.doc.id;
        if (!original.has(k)) return;
        if (c.type === 'removed') edits.delete(k); else if (typeof d.text === 'string') edits.set(k, d.text);
        paint(k);
      });
      count();
    }, () => say('Lost the connection to saved edits. Reload to retry.'));
    if (BASE) db.doc('layout/main').onSnapshot((snap) => {
      const next = snap.exists ? sanitize(snap.data()) : clone(BASE);
      if (!same(next, layout)) { layout = next; lastWritten = clone(next); applyLayout(); count(); }
    }, () => {});
    count();
  })();

  // ══ DOCK ══════════════════════════════════════════════════════════════════════════════════
  const dock = document.createElement('div');
  dock.className = 'bz-dock';
  dock.setAttribute('role', 'region');
  dock.setAttribute('aria-label', 'Edit this page');
  dock.innerHTML = `
    <button type="button" class="bz-btn bz-main" id="bz-toggle" aria-pressed="false">✎ Edit page</button>
    <button type="button" class="bz-btn" id="bz-copy" hidden>Copy my changes <span id="bz-n">0</span></button>
    <button type="button" class="bz-btn bz-quiet" id="bz-reset" hidden>Discard all</button>
    <p class="bz-msg" id="bz-msg" role="status" aria-live="polite"></p>`;
  document.body.appendChild(dock);
  const $ = (id) => document.getElementById(id);
  let timer;
  const hint = () => 'Click text to type. Use the bars on sections and cards to move, resize, feature, or hide. Drag ⠿ to reorder.';
  function say(msg) { const m = $('bz-msg'); m.textContent = msg; clearTimeout(timer); if (msg) timer = setTimeout(() => { m.textContent = editing ? hint() : ''; }, 3200); }
  const total = () => edits.size + (layoutChanged() ? 1 : 0);
  function count() { $('bz-n').textContent = total(); $('bz-copy').hidden = !editing && total() === 0; $('bz-reset').hidden = !editing || total() === 0; }

  let editing = false;
  const editable = (el) => !(el instanceof SVGElement) && !el.closest('.bz-dock, .bz-bar');
  function setEditing(on) {
    editing = on;
    document.documentElement.classList.toggle('bz-editing', on);
    $('bz-toggle').setAttribute('aria-pressed', String(on));
    $('bz-toggle').textContent = on ? 'Done editing' : '✎ Edit page';
    nodes().filter(editable).forEach((el) => {
      if (on) { el.setAttribute('contenteditable', 'plaintext-only'); if (el.contentEditable !== 'plaintext-only') el.setAttribute('contenteditable', 'true'); el.spellcheck = true; }
      else el.removeAttribute('contenteditable');
    });
    if (document.activeElement && document.activeElement.hasAttribute('data-edit')) document.activeElement.blur();
    say(on ? hint() : total() ? `${total()} change${total() === 1 ? '' : 's'} saved ${where === 'page' ? 'on this page' : 'in this browser'}.` : '');
    count();
  }
  $('bz-toggle').addEventListener('click', () => setEditing(!editing));

  // ══ LAYOUT BARS ═══════════════════════════════════════════════════════════════════════════
  const btn = (act, label, title) => `<button type="button" class="bz-b" data-act="${act}" title="${title}" aria-label="${title}">${label}</button>`;
  function addBars() {
    if (!layout) return;
    main.querySelectorAll(':scope > [data-section]').forEach((s) => {
      const bar = document.createElement('div');
      bar.className = 'bz-bar bz-sec'; bar.dataset.target = s.dataset.section;
      bar.innerHTML = `<span class="bz-label">${esc(SEC_NAMES[s.dataset.section] || s.dataset.section)}</span>${btn('sec-up', '▲', 'Move section up')}${btn('sec-down', '▼', 'Move section down')}${btn('sec-hide', 'Hide', 'Hide this section')}`;
      s.prepend(bar);
    });
    grid?.querySelectorAll('.card').forEach((c) => {
      const bar = document.createElement('div');
      bar.className = 'bz-bar bz-card'; bar.dataset.target = c.dataset.slug;
      bar.innerHTML = `<span class="bz-drag" title="Drag to reorder" aria-hidden="true">⠿</span>${btn('card-prev', '◀', 'Move earlier')}${btn('card-next', '▶', 'Move later')}${btn('card-wide', 'Wide', 'Double-width card')}${btn('card-feat', '★', 'Show in Selected work')}${btn('card-hide', 'Hide', 'Hide this project')}`;
      c.prepend(bar);
    });
    selWrap?.querySelectorAll('.feature').forEach((f) => {
      const bar = document.createElement('div');
      bar.className = 'bz-bar bz-feat'; bar.dataset.target = f.dataset.slug;
      bar.innerHTML = `<span class="bz-label">${esc(NAMES[f.dataset.slug] || f.dataset.slug)}</span>${btn('feat-up', '▲', 'Move up')}${btn('feat-down', '▼', 'Move down')}${btn('feat-remove', 'Remove', 'Remove from Selected work')}`;
      f.prepend(bar);
    });
  }
  function refreshBars() {
    document.querySelectorAll('.bz-sec').forEach((b) => {
      const hidden = layout.hiddenSections.includes(b.dataset.target);
      b.querySelector('[data-act="sec-hide"]').textContent = hidden ? 'Show' : 'Hide';
      b.querySelector('[data-act="sec-hide"]').setAttribute('aria-pressed', String(hidden));
    });
    document.querySelectorAll('.bz-card').forEach((b) => {
      const s = b.dataset.target;
      b.querySelector('[data-act="card-wide"]').setAttribute('aria-pressed', String(layout.wide.includes(s)));
      b.querySelector('[data-act="card-feat"]').setAttribute('aria-pressed', String(layout.featured.includes(s)));
      b.querySelector('[data-act="card-feat"]').disabled = !document.querySelector(`.feature[data-slug="${CSS.escape(s)}"]`);
      const h = b.querySelector('[data-act="card-hide"]'); h.textContent = layout.hidden.includes(s) ? 'Show' : 'Hide'; h.setAttribute('aria-pressed', String(layout.hidden.includes(s)));
    });
  }
  const move = (arr, item, delta) => { const i = arr.indexOf(item), j = i + delta; if (i < 0 || j < 0 || j >= arr.length) return false; [arr[i], arr[j]] = [arr[j], arr[i]]; return true; };
  const toggle = (arr, item) => { const i = arr.indexOf(item); if (i < 0) arr.push(item); else arr.splice(i, 1); };

  function act(a, target) {
    const L = layout, name = NAMES[target] || SEC_NAMES[target] || target;
    switch (a) {
      case 'sec-up': if (!move(L.sections, target, -1)) return say('Already at the top.'); break;
      case 'sec-down': if (!move(L.sections, target, 1)) return say('Already at the bottom.'); break;
      case 'sec-hide': toggle(L.hiddenSections, target); say(L.hiddenSections.includes(target) ? `${name} hidden. It stays visible while editing so you can bring it back.` : `${name} shown.`); break;
      case 'card-prev': move(L.order, target, -1); break;
      case 'card-next': move(L.order, target, 1); break;
      case 'card-wide': toggle(L.wide, target); break;
      case 'card-feat': toggle(L.featured, target); say(L.featured.includes(target) ? `${name} added to Selected work.` : `${name} removed from Selected work.`); break;
      case 'card-hide': toggle(L.hidden, target); say(L.hidden.includes(target) ? `${name} hidden from the site.` : `${name} shown.`); break;
      case 'feat-up': move(L.featured, target, -1); break;
      case 'feat-down': move(L.featured, target, 1); break;
      case 'feat-remove': toggle(L.featured, target); say(`${name} removed from Selected work.`); break;
      default: return;
    }
    applyLayout(); count(); persistLayout();
    const again = document.querySelector(`.bz-bar[data-target="${CSS.escape(target)}"] [data-act="${a}"]`);
    if (again && !again.disabled) again.focus({ preventScroll: true });
  }
  document.addEventListener('click', (e) => {
    const b = e.target.closest('.bz-b');
    if (b) { e.preventDefault(); e.stopPropagation(); if (editing) act(b.dataset.act, b.closest('.bz-bar').dataset.target); }
  }, true);

  // Drag ⠿ to reorder cards: pointer events, so it works with a mouse, a finger, or a pen.
  // The ◀ ▶ buttons do the same from a keyboard.
  let drag = null;
  const clearDrop = () => document.querySelectorAll('.bz-drop-before, .bz-drop-after').forEach((x) => x.classList.remove('bz-drop-before', 'bz-drop-after'));
  document.addEventListener('pointerdown', (e) => {
    const h = e.target.closest && e.target.closest('.bz-drag'); if (!h || !editing) return;
    e.preventDefault();
    drag = { slug: h.closest('.bz-bar').dataset.target, card: h.closest('.card'), target: null, after: false };
    drag.card.classList.add('bz-dragging');
    try { h.setPointerCapture(e.pointerId); } catch { /* older browsers */ }
  });
  document.addEventListener('pointermove', (e) => {
    if (!drag) return;
    const c = document.elementFromPoint(e.clientX, e.clientY)?.closest('.card');
    clearDrop(); drag.target = null;
    if (c && c !== drag.card) {
      const r = c.getBoundingClientRect();
      drag.after = e.clientX > r.left + r.width / 2; drag.target = c;
      c.classList.add(drag.after ? 'bz-drop-after' : 'bz-drop-before');
    }
    if (e.clientY < 70) window.scrollBy(0, -14); else if (e.clientY > innerHeight - 70) window.scrollBy(0, 14);
  });
  const endDrag = (commit) => {
    if (!drag) return;
    if (commit && drag.target) {
      const o = layout.order.filter((s) => s !== drag.slug);
      let i = o.indexOf(drag.target.dataset.slug); if (drag.after) i += 1;
      o.splice(i, 0, drag.slug); layout.order = o;
      applyLayout(); count(); persistLayout();
      say(`Moved ${NAMES[drag.slug] || drag.slug}.`);
    }
    drag.card.classList.remove('bz-dragging'); clearDrop(); drag = null;
  };
  document.addEventListener('pointerup', () => endDrag(true));
  document.addEventListener('pointercancel', () => endDrag(false));

  // ══ TEXT EDITING EVENTS ═══════════════════════════════════════════════════════════════════
  // While editing, links and buttons don't navigate or filter; you're just placing the cursor.
  document.addEventListener('click', (e) => {
    if (!editing || e.target.closest('.bz-dock')) return;
    if (e.target.closest('.bz-bar')) { e.preventDefault(); return; }   // bars sit inside card links
    if (e.target.closest('[data-edit]') || e.target.closest('a, button')) e.preventDefault();
  }, true);
  document.addEventListener('focusin', (e) => {
    const el = e.target.closest && e.target.closest('[data-edit]');
    if (!editing || !el || !editable(el)) return;
    el.textContent = current(el.dataset.edit);
  });
  document.addEventListener('keydown', (e) => {
    const el = e.target.closest && e.target.closest('[contenteditable][data-edit]');
    if (!el) return;
    if (e.key === 'Escape') { el.textContent = current(el.dataset.edit); el.blur(); }
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
    if (await persistText(key)) say(where === 'page' ? 'Saved on this page.' : 'Saved in this browser.');
  });

  // ══ COPY / DISCARD ════════════════════════════════════════════════════════════════════════
  $('bz-copy').addEventListener('click', async () => {
    let out = [...edits].map(([k, v]) => `## ${k}\n${v}\n`).join('\n');
    if (layoutChanged()) out += `${out ? '\n' : ''}## layout\n${JSON.stringify(layout)}\n`;
    if (!out) { say('No changes yet.'); return; }
    try { await navigator.clipboard.writeText(out); say(`Copied ${total()} change${total() === 1 ? '' : 's'}. Paste them into the chat with Claude.`); }
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
    for (const k of [...edits.keys()]) { edits.delete(k); paint(k); await persistText(k); }
    if (layoutChanged()) { layout = clone(BASE); applyLayout(); persistLayout(); }
    count(); say('All changes discarded.');
  });

  addBars();
  applyLayout();
  count();
})();
