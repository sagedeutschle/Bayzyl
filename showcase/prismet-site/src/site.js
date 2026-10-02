// prismet.xyz — theme, the wing filter, the hall plan's lantern, the bench stepper. No dependencies.
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
  root.classList.add('js');
  const motionOK = () => !matchMedia('(prefers-reduced-motion: reduce)').matches;

  document.addEventListener('DOMContentLoaded', () => {
    // Day / night
    const toggle = document.getElementById('theme-toggle');
    if (toggle) toggle.addEventListener('click', () => {
      const dark = root.dataset.theme ? root.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
      root.dataset.theme = dark ? 'light' : 'dark';
      store.set('prismet.theme', root.dataset.theme);
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

    // The register's filter: the plan's wings, the chips and the #hash all drive one state.
    const ledger = document.getElementById('grid');
    if (ledger) {
      const rows = [...ledger.querySelectorAll('.row')];
      const groups = [...ledger.querySelectorAll('.ledger-group')];
      const buttons = [...document.querySelectorAll('[data-filter]')];
      const count = document.querySelector('[data-count-template]');
      const apply = (beam, push) => {
        const id = beam && beam !== 'all' && buttons.some((b) => b.dataset.filter === beam) ? beam : 'all';
        let n = 0;
        rows.forEach((r) => { const show = id === 'all' || r.dataset.beam === id; r.hidden = !show; if (show && !r.classList.contains('is-off')) n++; });
        groups.forEach((g) => { g.hidden = ![...g.querySelectorAll('.row')].some((r) => !r.hidden); });
        buttons.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.filter === id)));
        if (count) count.textContent = count.dataset.countTemplate.replace('{shown}', n).replace('{total}', count.dataset.total);
        if (push) history.replaceState(null, '', id === 'all' ? location.pathname : `#${id}`);
      };
      buttons.forEach((b) => b.addEventListener('click', () => apply(b.dataset.filter, true)));
      document.querySelectorAll('a[data-beam]').forEach((a) => a.addEventListener('click', () => { apply(a.dataset.beam, false); }));
      const fromHash = () => { const h = location.hash.slice(1); if (buttons.some((b) => b.dataset.filter === h)) { apply(h, false); document.getElementById('work')?.scrollIntoView(); } };
      window.addEventListener('hashchange', fromHash);
      fromHash();
    }

    // The bench: one command at a time. Without JS every step is on the page; with it, one at a time.
    document.querySelectorAll('[data-stepper]').forEach((st) => {
      const steps = [...st.querySelectorAll('.step')], out = st.querySelector('output');
      const prev = st.querySelector('[data-prev]'), next = st.querySelector('[data-next]');
      const label = out ? out.textContent.replace(/\d+\s*\/\s*\d+$/, '').trim() : 'Step';
      let i = Math.max(0, steps.findIndex((s) => s.hasAttribute('aria-current')));
      const show = (n) => {
        i = (n + steps.length) % steps.length;
        steps.forEach((s, j) => { if (j === i) s.setAttribute('aria-current', 'step'); else s.removeAttribute('aria-current'); });
        if (out) out.textContent = `${label} ${i} / ${steps.length - 1}`;
      };
      prev?.addEventListener('click', () => show(i - 1));
      next?.addEventListener('click', () => show(i + 1));
      st.addEventListener('keydown', (e) => { if (e.key === 'ArrowLeft') { show(i - 1); e.preventDefault(); } if (e.key === 'ArrowRight') { show(i + 1); e.preventDefault(); } });
      show(i);
    });
  });
})();
