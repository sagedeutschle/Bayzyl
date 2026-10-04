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
  // A chosen theme overrides the system one, so the browser chrome (theme-color) follows the choice, not the system.
  const tint = () => { if (!root.dataset.theme) return; const c = getComputedStyle(root).getPropertyValue('--ground').trim(); if (c) document.querySelectorAll('meta[name="theme-color"]').forEach((m) => m.setAttribute('content', c)); };
  tint();
  root.classList.add('js');
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const motionOK = () => !reducedMotion.matches;

  document.addEventListener('DOMContentLoaded', () => {
    // Day / night
    const toggle = document.getElementById('theme-toggle');
    const isDark = () => (root.dataset.theme ? root.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches);
    const labelToggle = () => { if (toggle && toggle.dataset.toDay) toggle.setAttribute('aria-label', isDark() ? toggle.dataset.toDay : toggle.dataset.toNight); };
    labelToggle();
    if (toggle) toggle.addEventListener('click', () => {
      root.dataset.theme = isDark() ? 'light' : 'dark';
      store.set('prismet.theme', root.dataset.theme);
      tint(); labelToggle();
    });

    // The hall plan: fixed navigation, with responsive glass and light in its centre.
    const plan = document.getElementById('plan');
    if (plan && !plan.classList.contains('plan-strip')) {
      if (motionOK()) {
        let drawn = false;
        try { drawn = sessionStorage.getItem('prismet.plan') === '1'; } catch { /* no session storage */ }
        if (!drawn) { plan.classList.add('draw'); try { sessionStorage.setItem('prismet.plan', '1'); } catch { /* ignore */ } }
      }
      const svg = plan.querySelector('svg');
      const finePointer = matchMedia('(hover: hover) and (pointer: fine)');
      const lantern = (wing) => {
        if (!wing || !motionOK()) { plan.style.setProperty('--lo', '.55'); plan.style.setProperty('--lx', '50%'); plan.style.setProperty('--ly', '50%'); return; }
        const r = wing.querySelector('.room').getBBox(), vb = svg.viewBox.baseVal;
        plan.style.setProperty('--lx', `${((r.x + r.width / 2) / vb.width * 100).toFixed(1)}%`);
        plan.style.setProperty('--ly', `${((r.y + r.height / 2) / vb.height * 100 * (svg.clientHeight / plan.clientHeight)).toFixed(1)}%`);
        plan.style.setProperty('--lo', '.9');
      };
      plan.querySelectorAll('a.wing').forEach((w) => {
        w.addEventListener('pointerenter', (event) => { if (event.pointerType !== 'touch' && finePointer.matches) lantern(w); });
        w.addEventListener('focus', () => lantern(w));
        w.addEventListener('pointerleave', () => lantern(null));
        w.addEventListener('blur', () => lantern(null));
      });

      // One update per pointer frame, never a perpetual animation loop. CSS eases to the new pose.
      let lightFrame = 0, pointerX = 0, pointerY = 0;
      const opticsProperties = ['--prism-x', '--prism-y', '--prism-turn', '--fan-turn', '--fan-spread', '--prism-light'];
      const resetOptics = () => {
        if (lightFrame) cancelAnimationFrame(lightFrame);
        lightFrame = 0;
        opticsProperties.forEach((name) => plan.style.removeProperty(name));
        lantern(null);
      };
      svg.addEventListener('pointermove', (event) => {
        if (event.pointerType === 'touch' || !finePointer.matches || !motionOK() || document.hidden) return;
        const rect = svg.getBoundingClientRect();
        if (!rect.width || !rect.height) return;
        pointerX = Math.max(-1, Math.min(1, (event.clientX - rect.left) / rect.width * 2 - 1));
        pointerY = Math.max(-1, Math.min(1, (event.clientY - rect.top) / rect.height * 2 - 1));
        if (lightFrame) return;
        lightFrame = requestAnimationFrame(() => {
          lightFrame = 0;
          plan.style.setProperty('--prism-x', `${(pointerX * 7).toFixed(2)}px`);
          plan.style.setProperty('--prism-y', `${(pointerY * 5).toFixed(2)}px`);
          plan.style.setProperty('--prism-turn', `${(pointerX * 7).toFixed(2)}deg`);
          plan.style.setProperty('--fan-turn', `${(pointerY * 15).toFixed(2)}deg`);
          plan.style.setProperty('--fan-spread', (1.15 + pointerX * .22).toFixed(2));
          plan.style.setProperty('--prism-light', '.85');
        });
      });
      svg.addEventListener('pointerleave', resetOptics);
      svg.addEventListener('pointercancel', resetOptics);
      window.addEventListener('blur', resetOptics);
      document.addEventListener('visibilitychange', resetOptics);
      window.addEventListener('resize', resetOptics);
      finePointer.addEventListener('change', resetOptics);
      reducedMotion.addEventListener('change', () => {
        if (!motionOK()) plan.classList.remove('draw');
        resetOptics();
      });
    }

    // Homepage optics: sparse local transforms on input only. Editor previews retain the static artwork.
    const opticalHome = document.querySelector('.portfolio-home[data-optical-motion]');
    if (opticalHome) {
      const finePointer = matchMedia('(hover: hover) and (pointer: fine)');
      const entrance = opticalHome.querySelector('.entrance');
      const heroArt = entrance.querySelector('.hero-optics');
      const layers = [...opticalHome.querySelectorAll('.hero-optics, .optical-rail')];
      const visible = new Set(), lastDrift = new WeakMap();
      let frame = 0, focused = true, scrollDirty = true, pointerDirty = false, x = 0, y = 0, overHero = false;
      const enabled = () => motionOK() && finePointer.matches && !document.hidden && focused && !root.classList.contains('pe-on');
      const draw = () => {
        frame = 0;
        if (!enabled()) return;
        if (scrollDirty) {
          visible.forEach((layer) => {
            const rect = layer.parentElement.getBoundingClientRect();
            const progress = Math.max(-1, Math.min(1, (innerHeight / 2 - rect.top - rect.height / 2) / (innerHeight / 2 + rect.height / 2)));
            const drift = Math.round(progress * 10);
            if (lastDrift.get(layer) !== drift) {
              layer.style.setProperty('--decor-scroll', `${drift}px`);
              lastDrift.set(layer, drift);
            }
          });
          scrollDirty = false;
        }
        if (pointerDirty) {
          heroArt.style.setProperty('--decor-x', `${(x * 8).toFixed(1)}px`);
          heroArt.style.setProperty('--decor-y', `${(y * 6).toFixed(1)}px`);
          heroArt.style.setProperty('--decor-light', overHero ? '1' : '.85');
          heroArt.style.setProperty('--hero-prism-turn', `${(x * 6).toFixed(1)}deg`);
          heroArt.style.setProperty('--hero-beam-turn', `${(y * 12).toFixed(1)}deg`);
          heroArt.style.setProperty('--hero-fan-spread', (1 + x * .16).toFixed(2));
          pointerDirty = false;
        }
      };
      const schedule = () => { if (!frame && enabled()) frame = requestAnimationFrame(draw); };
      const reset = () => {
        if (frame) cancelAnimationFrame(frame);
        frame = 0; x = 0; y = 0; overHero = false; pointerDirty = false; scrollDirty = true;
        layers.forEach((layer) => {
          ['--decor-scroll', '--decor-x', '--decor-y', '--decor-light', '--hero-prism-turn', '--hero-beam-turn', '--hero-fan-spread'].forEach((key) => layer.style.removeProperty(key));
          lastDrift.delete(layer);
        });
      };
      // Observe the reserved illustration/heading slots, so drift cannot change intersection state.
      if ('IntersectionObserver' in window) {
        const observer = new IntersectionObserver((entries) => {
          entries.forEach((entry) => {
            const layer = layers.find((item) => item.parentElement === entry.target);
            if (entry.isIntersecting) visible.add(layer); else visible.delete(layer);
          });
          scrollDirty = true; schedule();
        }, { rootMargin: '80px' });
        layers.forEach((layer) => observer.observe(layer.parentElement));
      } else layers.forEach((layer) => visible.add(layer));
      entrance.addEventListener('pointermove', (event) => {
        if (event.pointerType === 'touch' || !enabled()) return;
        const rect = entrance.getBoundingClientRect();
        if (!rect.width || !rect.height) return;
        x = Math.max(-1, Math.min(1, (event.clientX - rect.left) / rect.width * 2 - 1));
        y = Math.max(-1, Math.min(1, (event.clientY - rect.top) / rect.height * 2 - 1));
        overHero = true; pointerDirty = true; schedule();
      });
      const leaveHero = () => { x = 0; y = 0; overHero = false; pointerDirty = true; schedule(); };
      entrance.addEventListener('pointerleave', leaveHero);
      entrance.addEventListener('pointercancel', leaveHero);
      window.addEventListener('scroll', () => { scrollDirty = true; schedule(); }, { passive: true });
      window.addEventListener('resize', () => { scrollDirty = true; schedule(); }, { passive: true });
      window.addEventListener('blur', () => { focused = false; reset(); });
      window.addEventListener('focus', () => { focused = true; scrollDirty = true; schedule(); });
      document.addEventListener('visibilitychange', () => { reset(); schedule(); });
      reducedMotion.addEventListener('change', () => { reset(); schedule(); });
      finePointer.addEventListener('change', () => { reset(); schedule(); });
      schedule();
    }

    // Native scrolling remains available without JavaScript; links always name the full image.
    const viewer = document.getElementById('image-viewer');
    let viewerLinks = [], viewerIndex = 0, viewerOrigin = null, viewerRequest = 0;
    const showViewerImage = (index) => {
      if (!viewer || !viewerLinks.length) return;
      viewerIndex = Math.max(0, Math.min(index, viewerLinks.length - 1));
      const link = viewerLinks[viewerIndex], source = link.querySelector('img');
      const stage = viewer.querySelector('[data-viewer-stage]'), status = viewer.querySelector('[data-viewer-status]');
      const image = document.createElement('img'), request = ++viewerRequest;
      image.alt = source.alt; image.width = Number(link.dataset.width); image.height = Number(link.dataset.height);
      stage.setAttribute('aria-busy', 'true'); status.hidden = false; status.textContent = viewer.dataset.loading;
      image.addEventListener('load', () => {
        if (request !== viewerRequest) return;
        stage.setAttribute('aria-busy', 'false'); status.hidden = true;
      });
      image.addEventListener('error', () => {
        if (request !== viewerRequest) return;
        stage.setAttribute('aria-busy', 'false'); status.textContent = viewer.dataset.error;
      });
      stage.replaceChildren(image); image.src = link.href;
      viewer.querySelector('#viewer-caption').textContent = link.closest('figure').querySelector('[data-gallery-caption]').innerText;
      viewer.querySelector('[data-viewer-count]').textContent = `${viewerIndex + 1} / ${viewerLinks.length}`;
      viewer.querySelector('[data-viewer-prev]').disabled = viewerIndex === 0;
      viewer.querySelector('[data-viewer-next]').disabled = viewerIndex === viewerLinks.length - 1;
      viewer.querySelector('[data-viewer-full]').href = link.href;
    };
    if (viewer && typeof viewer.showModal === 'function') {
      viewer.querySelector('[data-viewer-close]').addEventListener('click', () => viewer.close());
      viewer.querySelector('[data-viewer-prev]').addEventListener('click', () => showViewerImage(viewerIndex - 1));
      viewer.querySelector('[data-viewer-next]').addEventListener('click', () => showViewerImage(viewerIndex + 1));
      viewer.addEventListener('keydown', (event) => {
        if (!viewer.open || event.altKey || event.ctrlKey || event.metaKey) return;
        if (event['key'] === 'ArrowLeft' || event['key'] === 'ArrowRight') {
          event.preventDefault(); showViewerImage(viewerIndex + (event['key'] === 'ArrowLeft' ? -1 : 1));
        }
      });
      viewer.addEventListener('close', () => {
        viewerRequest++; viewer.querySelector('[data-viewer-stage]').replaceChildren();
        viewerOrigin?.focus({ preventScroll: true });
      });
    }
    document.querySelectorAll('[data-gallery]').forEach((gallery) => {
      if (gallery.hasAttribute('data-gallery-edit')) return;
      const track = gallery.querySelector('.gallery-track'), slides = [...gallery.querySelectorAll('[data-gallery-slide]')];
      const links = [...gallery.querySelectorAll('[data-gallery-open]')];
      const previous = gallery.querySelector('[data-gallery-prev]'), next = gallery.querySelector('[data-gallery-next]');
      const count = gallery.querySelector('[data-gallery-count]');
      let scrollFrame = 0, current = 0;
      const refresh = () => {
        scrollFrame = 0;
        const box = track.getBoundingClientRect(), bounds = slides.map((slide) => slide.getBoundingClientRect());
        current = bounds.reduce((best, rect, i) => Math.abs(rect.left - box.left) < Math.abs(bounds[best].left - box.left) ? i : best, 0);
        const visible = bounds.map((rect, i) => rect.right > box.left + 8 && rect.left < box.right - 8 ? i + 1 : 0).filter(Boolean);
        const first = visible[0] || current + 1, last = visible.at(-1) || first;
        const value = `${first === last ? first : `${first}–${last}`} / ${slides.length}`;
        if (count.textContent !== value) count.textContent = value;
        previous.disabled = track.scrollLeft <= 2;
        next.disabled = track.scrollLeft >= track.scrollWidth - track.clientWidth - 2;
      };
      const schedule = () => { if (!scrollFrame) scrollFrame = requestAnimationFrame(refresh); };
      const move = (direction) => {
        if (scrollFrame) cancelAnimationFrame(scrollFrame);
        refresh();
        const target = slides[Math.max(0, Math.min(current + direction, slides.length - 1))];
        const left = target.getBoundingClientRect().left - track.getBoundingClientRect().left + track.scrollLeft - 4;
        track.scrollTo({ left, behavior: motionOK() ? 'smooth' : 'instant' });
      };
      previous.addEventListener('click', () => move(-1)); next.addEventListener('click', () => move(1));
      track.addEventListener('scroll', schedule, { passive: true });
      if ('ResizeObserver' in window) new ResizeObserver(schedule).observe(track);
      else window.addEventListener('resize', schedule, { passive: true });
      links.forEach((link, index) => link.addEventListener('click', (event) => {
        if (!viewer || typeof viewer.showModal !== 'function' || root.classList.contains('pe-on') || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault(); viewerLinks = links; viewerOrigin = link;
        viewer.querySelector('#viewer-title').textContent = gallery.dataset.galleryTitle;
        showViewerImage(index); viewer.showModal();
      }));
      gallery.classList.add('is-ready'); refresh();
    });

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
