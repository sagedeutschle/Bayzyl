// prismet.xyz — theme, the register (wing filter, seek line, threads), the hall plan's lantern, the bench stepper, the
// Wordgame note. No dependencies.
// Loaded in <head> without defer so the saved theme applies before first paint; everything that touches the DOM
// waits for DOMContentLoaded.
(() => {
  const root = document.documentElement;
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch { /* storage blocked: the choice just won't persist */ } },
  };
  const saved = store.get('prismet.theme');
  if (saved === 'light' || saved === 'dark') root.dataset.theme = saved;
  // The sun cycle: with no saved choice the palette follows the visitor's clock (dawn and day are light, dusk and
  // night are dark, each with its own ground). ?phase=dawn|day|dusk|night shows one phase, whatever the hour.
  const PHASES = { dawn: 6.6, day: 12.5, dusk: 17.6, night: 23 };
  const clock = () => { const d = new Date(); return d.getHours() + d.getMinutes() / 60; };
  const phaseAt = (h) => (h >= 5 && h < 8 ? 'dawn' : h >= 8 && h < 17 ? 'day' : h >= 17 && h < 20 ? 'dusk' : 'night');
  let asked = null;
  try { asked = new URLSearchParams(location.search).get('phase'); } catch { /* no URL API */ }
  if (!(asked in PHASES)) asked = null;
  const sunHour = asked ? PHASES[asked] : clock();
  const phase = asked || phaseAt(sunHour);
  root.dataset.phase = phase;
  if (asked || !root.dataset.theme) { root.dataset.theme = phase === 'dawn' || phase === 'day' ? 'light' : 'dark'; root.dataset.auto = '1'; }
  // A chosen theme overrides the system one, so the browser chrome (theme-color) follows the choice, not the system.
  const tint = () => { if (!root.dataset.theme) return; const c = getComputedStyle(root).getPropertyValue('--ground').trim(); if (c) document.querySelectorAll('meta[name="theme-color"]').forEach((m) => m.setAttribute('content', c)); };
  tint();
  root.classList.add('js');
  const motionOK = () => !matchMedia('(prefers-reduced-motion: reduce)').matches;

  document.addEventListener('DOMContentLoaded', () => {
    // Day / night
    const toggle = document.getElementById('theme-toggle');
    const isDark = () => (root.dataset.theme ? root.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches);
    const labelToggle = () => { if (toggle && toggle.dataset.toDay) toggle.setAttribute('aria-label', isDark() ? toggle.dataset.toDay : toggle.dataset.toNight); };
    labelToggle();
    // The orb on the toggle's arc: the sun from six to six, the moon after. A chosen theme stops the cycle, not the orb.
    const sky = toggle && toggle.querySelector('.sky');
    if (sky) {
      const night = sunHour < 6 || sunHour >= 18, t = night ? ((sunHour - 18 + 24) % 24) / 12 : (sunHour - 6) / 12;
      const orb = sky.querySelector('.orb');
      orb.setAttribute('cx', (20 - 17 * Math.cos(Math.PI * t)).toFixed(1));
      orb.setAttribute('cy', (19 - 17 * Math.sin(Math.PI * t)).toFixed(1));
      sky.classList.toggle('moon', night);
    }
    if (toggle) toggle.addEventListener('click', () => {
      delete root.dataset.auto;
      root.dataset.theme = isDark() ? 'light' : 'dark';
      store.set('prismet.theme', root.dataset.theme);
      tint(); labelToggle();
    });

    // The hall plan: draw it once per session; the lantern follows the wing in hand.
    const plan = document.getElementById('plan');
    if (plan) {
      if (motionOK()) {
        let drawn = false;
        try { drawn = sessionStorage.getItem('prismet.plan') === '1'; } catch { /* no session storage */ }
        if (!drawn) { plan.classList.add('draw'); try { sessionStorage.setItem('prismet.plan', '1'); } catch { /* ignore */ } }
      }
      const svg = plan.querySelector('svg');
      const lantern = (wing) => {
        if (!wing) { plan.style.setProperty('--lo', '.55'); plan.style.setProperty('--lx', '50%'); plan.style.setProperty('--ly', '50%'); return; }
        const r = wing.querySelector('.room').getBBox(), vb = svg.viewBox.baseVal;
        plan.style.setProperty('--lx', `${((r.x + r.width / 2) / vb.width * 100).toFixed(1)}%`);
        plan.style.setProperty('--ly', `${((r.y + r.height / 2) / vb.height * 100 * (svg.clientHeight / plan.clientHeight)).toFixed(1)}%`);
        plan.style.setProperty('--lo', '.9');
      };
      plan.querySelectorAll('a.wing').forEach((w) => {
        w.addEventListener('pointerenter', () => lantern(w));
        w.addEventListener('focus', () => lantern(w));
        w.addEventListener('pointerleave', () => lantern(null));
        w.addEventListener('blur', () => lantern(null));
      });
    }

    // The register: the wing (plan, chips, #hash) and the seek line compose into one view. Only the wing reaches the URL.
    const ledger = document.getElementById('grid');
    if (ledger) {
      const rows = [...ledger.querySelectorAll('.row')];
      const groups = [...ledger.querySelectorAll('.ledger-group')];
      const buttons = [...document.querySelectorAll('[data-filter]')];
      const count = document.querySelector('[data-count-template]');
      const seek = document.getElementById('seek');
      const none = ledger.querySelector('.seek-none');
      const drafting = ledger.querySelector('.drafting');
      // Fold case, accents and punctuation; pad with spaces so a short term can be held to the start of a word
      // ("ai" finds AI & Agents, not "daily"), while a term of four letters or more may sit anywhere ("edit" → WorldEdit).
      const fold = (s) => ` ${String(s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim()} `;
      const text = (r, sel) => r.querySelector(sel)?.textContent || '';
      const hay = (r) => fold([text(r, '.row-record'), text(r, '.row-wing'), text(r, '.row-status'), r.dataset.seek, r.dataset.slug].join(' '));
      const terms = () => (seek ? fold(seek.value).trim().split(' ').filter(Boolean) : []);
      let wing = 'all', top = null;
      const apply = (beam, push) => {
        if (beam !== undefined) wing = beam && beam !== 'all' && buttons.some((b) => b.dataset.filter === beam) ? beam : 'all';
        const q = terms();
        let n = 0; top = null;
        rows.forEach((r) => {
          const h = q.length ? hay(r) : '';
          const show = (wing === 'all' || r.dataset.beam === wing) && q.every((t) => h.includes(t.length > 3 ? t : ` ${t}`));
          r.hidden = !show;
          if (show && !r.classList.contains('is-off')) { n++; if (!top) top = r; }
        });
        rows.forEach((r) => r.classList.toggle('is-top', q.length > 0 && r === top));   // what Enter opens
        groups.forEach((g) => { g.hidden = ![...g.querySelectorAll('.row')].some((r) => !r.hidden); });
        if (drafting) drafting.hidden = wing !== 'all' || q.length > 0;
        if (none) none.hidden = n > 0;
        buttons.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.filter === wing)));
        if (count) { const t = count.dataset.countTemplate.replace('{shown}', n).replace('{total}', count.dataset.total); if (count.textContent !== t) count.textContent = t; }
        if (push) history.replaceState(null, '', wing === 'all' ? location.pathname : `#${wing}`);
      };
      buttons.forEach((b) => b.addEventListener('click', () => apply(b.dataset.filter, true)));
      document.querySelectorAll('a[data-beam]').forEach((a) => a.addEventListener('click', () => { apply(a.dataset.beam, false); }));
      const fromHash = () => { const h = location.hash.slice(1); if (buttons.some((b) => b.dataset.filter === h)) { apply(h, false); document.getElementById('work')?.scrollIntoView(); } };
      window.addEventListener('hashchange', fromHash);
      fromHash();

      // The seek line: typing filters, Enter opens the top match, Esc clears, "/" from anywhere on the page focuses it.
      if (seek) {
        const editing = () => root.classList.contains('bz-editing');
        seek.addEventListener('input', () => apply());
        seek.addEventListener('keydown', (e) => {
          if (e.key === 'Enter' && !e.isComposing) {
            e.preventDefault();
            const link = top && terms().length && top.querySelector('.row-link');
            if (link && !editing()) link.click();
          } else if (e.key === 'Escape') {
            e.preventDefault();
            if (seek.value) { seek.value = ''; apply(); } else seek.blur();
          }
        });
        const field = (el) => el && el.closest && (el.isContentEditable || el.closest('input, textarea, select, [contenteditable]'));
        document.addEventListener('keydown', (e) => {
          if (e.key !== '/' || e.ctrlKey || e.metaKey || e.altKey || e.defaultPrevented || e.isComposing) return;
          if (field(e.target) || field(document.activeElement) || editing()) return;
          e.preventDefault();
          seek.focus({ preventScroll: true });
          seek.select();
          const r = seek.getBoundingClientRect(), barH = document.querySelector('.bar')?.offsetHeight || 0;
          if (r.top < barH || r.bottom > innerHeight) seek.closest('.seek').scrollIntoView({ block: 'start' });
        });
        // A restored page (back button) may bring its typed text back: show the rows that text selects.
        window.addEventListener('pageshow', () => { if (seek.value) apply(); });
        if (seek.value) apply();
      }

      // Threads: a row in hand (mouse hover or keyboard focus, never a tap) lights the ticks of its related records.
      const held = { hover: null, focus: null };
      const light = () => {
        const src = held.hover || held.focus, rel = (src?.dataset.related || '').split(' ');
        rows.forEach((r) => r.classList.toggle('is-thread', rel.includes(r.dataset.slug)));
      };
      const keyboardFocus = (el) => { try { return el.matches(':focus-visible'); } catch { return true; } };
      rows.forEach((r) => {
        r.addEventListener('pointerenter', (e) => { if (e.pointerType === 'touch') return; held.hover = r; light(); });
        r.addEventListener('pointerleave', () => { if (held.hover === r) { held.hover = null; light(); } });
        r.addEventListener('focusin', (e) => { held.focus = keyboardFocus(e.target) ? r : null; light(); });
        r.addEventListener('focusout', (e) => { if (held.focus === r && !r.contains(e.relatedTarget)) { held.focus = null; light(); } });
      });
    }

    // The Wordgame tile: a button that shows one line about where today's word comes from. Without JS the line is shown.
    document.querySelectorAll('[data-note]').forEach((tile) => {
      const note = document.getElementById(tile.dataset.note);
      if (!note) return;
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'tile-btn';
      btn.setAttribute('aria-expanded', 'false');
      btn.setAttribute('aria-controls', note.id);
      while (tile.firstChild) btn.appendChild(tile.firstChild);
      tile.appendChild(btn);
      btn.addEventListener('click', () => {
        const open = btn.getAttribute('aria-expanded') !== 'true';
        btn.setAttribute('aria-expanded', String(open));
        note.classList.toggle('is-open', open);
      });
    });

    // The bench: one command at a time. Without JS every step is on the page; with it, one at a time.
    document.querySelectorAll('[data-stepper]').forEach((st) => {
      const steps = [...st.querySelectorAll('.step')], out = st.querySelector('output');
      const prev = st.querySelector('[data-prev]'), next = st.querySelector('[data-next]');
      const label = out ? out.textContent.replace(/\d+\s*\/\s*\d+$/, '').trim() : 'Step';
      let i = Math.max(0, steps.findIndex((s) => s.hasAttribute('aria-current')));
      // The output is a live region: write it only when the visitor moves, so nothing is announced on load.
      const show = (n, announce = true) => {
        i = (n + steps.length) % steps.length;
        steps.forEach((s, j) => { if (j === i) s.setAttribute('aria-current', 'step'); else s.removeAttribute('aria-current'); });
        if (out && announce) out.textContent = `${label} ${i + 1} / ${steps.length}`;
      };
      prev?.addEventListener('click', () => show(i - 1));
      next?.addEventListener('click', () => show(i + 1));
      st.addEventListener('keydown', (e) => { if (e.key === 'ArrowLeft') { show(i - 1); e.preventDefault(); } if (e.key === 'ArrowRight') { show(i + 1); e.preventDefault(); } });
      show(i, false);
    });
  });
})();
