// prismet.xyz — theme toggle + beam filter. No dependencies.
(() => {
  const root = document.documentElement;
  const store = {
    get() { try { return localStorage.getItem('prismet.theme'); } catch { return null; } },
    set(v) { try { localStorage.setItem('prismet.theme', v); } catch { /* storage blocked: theme just won't persist */ } },
  };
  const saved = store.get();
  if (saved === 'light' || saved === 'dark') root.dataset.theme = saved;
  const toggle = document.getElementById('theme-toggle');
  if (toggle) toggle.addEventListener('click', () => {
    const dark = root.dataset.theme ? root.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
    root.dataset.theme = dark ? 'light' : 'dark';
    store.set(root.dataset.theme);
  });

  // Beam filter: the prism beams, the mobile chips, and the filter row all drive one state.
  const grid = document.getElementById('grid');
  if (!grid) return;
  const cards = [...grid.querySelectorAll('.card')];
  const buttons = [...document.querySelectorAll('[data-filter]')];
  function apply(beam) {
    const id = beam && beam !== 'all' ? beam : 'all';
    cards.forEach((c) => { c.hidden = id !== 'all' && c.dataset.beam !== id; });
    buttons.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.filter === id)));
  }
  buttons.forEach((b) => b.addEventListener('click', () => apply(b.dataset.filter)));
  document.querySelectorAll('a[data-beam]').forEach((a) => a.addEventListener('click', () => apply(a.dataset.beam)));
  // deep link: prismet.xyz/#minecraft
  const hash = location.hash.slice(1);
  if (buttons.some((b) => b.dataset.filter === hash)) { apply(hash); document.getElementById('work')?.scrollIntoView(); }
})();
