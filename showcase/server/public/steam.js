    const statusEl = document.getElementById('status');
    const metaEl = document.getElementById('meta');
    const out = document.getElementById('out');
    const lensEl = document.getElementById('lens');
    const qEl = document.getElementById('q');
    const resetBtn = document.getElementById('reset');

    const DEMO_COLUMNS = {
      n: [
        'Hollow Knight','Hades','Celeste','Outer Wilds','Cult of the Lamb','Hollow Knight','Sable','Disco Elysium','Stardew Valley','Hollow Moon','Axiom Verge 2','Titanfall 2','Hollow Knight','Dead Cells','Factorio','Noita','Hollow Knight','The Talos Principle','Papers, Please','Slay the Spire','RimWorld','Hollow Knight Silksong','Risk of Rain 2','Brotato','Hollow Knight: Silksong','Papers Please 2','Owlboy','Satisfactory','Baba Is You','Noita','Forager','Hollow Knight','Stardew Valley','Hades 2'],
      h: [120, 430, 88, 72, 58, 30, 160, 12, 95, 44, 510, 80, 220, 41, 380, 17, 15, 102, 56, 240, 20, 190, 62, 74, 14, 33, 29, 66, 99, 250, 70, 16, 47],
      w: [10, 12, 6, 2, 1, 0, 8, 0, 5, 1, 18, 0, 3, 0, 11, 2, 6, 0, 5, 1, 0, 14, 9, 0, 0, 1, 2, 10, 0, 4, 3, 0],
      p: [14.99, 29.99, 21.99, 24.99, 11.99, 29.99, 19.99, 9.99, 10.0, 7.99, 24.99, 19.99, 12.99, 29.99, 34.99, 16.99, 12.99, 7.99, 14.99, 23.99, 9.99, 14.99, 11.99, 49.99, 13.99, 24.99, 19.99, 9.99, 9.99, 0, 19.99, 24.99, 34.99],
      rev: [83, 90, 88, 95, 79, 91, 80, 82, 87, 73, 92, 84, 89, 86, 91, 81, 76, 64, 87, 90, 88, 82, 77, 84, 70, 79, 85, 71, 83, 86, 90, 88],
      ap: [35, 64, 91, 54, 12, 70, 33, 20, 88, 45, 61, 99, 15, 49, 80, 44, 11, 22, 50, 73, 38, 95, 29, 14, 66, 23, 58, 10, 34, 42, 78, 90],
      rare: [23.1, 11.9, 1.2, 7.4, 31.5, 18.7, 14.2, 5.6, 8.9, 27.3, 2.4, 3.1, 9.2, 16.7, 12.8, 6.3, 32.4, 21.1, 14.2, 10.1, 28.6, 4.9, 3.3, 22.8, 6.7, 12.9, 7.7, 39.5, 8.8, 10.2, 15.6, 18.4, 5.0],
      g: ['Metroidvania','Action','Platformer','Sci-Fi','Roguelike','Adventure','Puzzle','RPG','Simulation','Action','Shooter','Action','Metroidvania','Card','Strategy','Simulation','Puzzle','Adventure','Deckbuilder','Management','Action','RPG','Comedy','Platformer','Strategy','Action','Platformer','Puzzle','Simulation','Adventure','Simulation','RPG'],
      y: [2017,2020,2018,2019,2022,2016,2019,2013,2016,2022,2013,2011,2017,2012,2016,2014,2007,2013,2017,2016,2024,2013,2019,2023,2014,2016,2012,2017,2019,2017,2016,2022],
      dk: [0, 0, 9, 0, 12, 0, 0, 2, 3, 0, 4, 0, 1, 6, 8, 0, 0, 5, 2, 0, 0, 7, 10, 0, 0, 0, 11, 0, 0, 1, 3, 0],
      last: [2, 5, 11, 18, 4, 60, 90, 230, 7, 75, 6, 12, 14, 48, 3, 15, 120, 200, 28, 44, 80, 9, 16, 11, 300, 95, 17, 19, 25, 1, 33, 55, 41],
    };

    // One record per index, with a small per-row jitter so the lenses have something to sort by.
    const demo = Array.from({ length: 32 }, (_, i) => {
      const at = (key) => DEMO_COLUMNS[key][i];
      return {
        n: `${at('n')}${i >= 24 ? ' — Demo' : ''}`,
        h: at('h'),
        w: at('w') + (i % 5),
        p: Number((at('p') + (i % 3) * 0.5).toFixed(2)),
        rev: at('rev') - (i % 4),
        ap: Number(Math.max(0, Math.min(100, at('ap') + (i % 7) * 2)).toFixed(1)),
        rare: Number(at('rare').toFixed(2)),
        g: at('g'),
        y: at('y'),
        dk: at('dk'),
        last: at('last'),
      };
    });

    const lenses = {
      playtime: { label: 'Most played', by: (a, b) => (b.h ?? 0) - (a.h ?? 0), score: (g) => g.h ?? 0 },
      recent: { label: 'Most recently played', by: (a, b) => (a.last ?? 1e9) - (b.last ?? 1e9), score: (g) => -(g.last ?? 1e9) },
      cost: { label: 'Most expensive', by: (a, b) => (b.p ?? 0) - (a.p ?? 0), score: (g) => g.p ?? 0 },
      costPerHour: { label: 'Best value (cost / hour)', by: (a, b) => {
          const av = (a.p ?? 0) / Math.max(a.h || 1, 1);
          const bv = (b.p ?? 0) / Math.max(b.h || 1, 1);
          return av - bv;
        }, score: (g) => (g.p ?? 0) / Math.max(g.h || 1, 1)
      },
      recentCost: { label: 'Recent value score', by: (a, b) => {
          const af = ((a.p ?? 0) * ((a.w ?? 0) + 1)) / Math.max(a.h || 1, 1);
          const bf = ((b.p ?? 0) * ((b.w ?? 0) + 1)) / Math.max(b.h || 1, 1);
          return bf - af;
        }, score: (g) => ((g.p ?? 0) * ((g.w ?? 0) + 1)) / Math.max(g.h || 1, 1)
      },
      metacritic: { label: 'Highest Metacritic', by: (a, b) => (b.rev ?? 0) - (a.rev ?? 0), score: (g) => g.rev ?? 0 },
      achievement: { label: 'Highest completion %', by: (a, b) => (b.ap ?? 0) - (a.ap ?? 0), score: (g) => g.ap ?? 0 },
      achievementRarity: { label: 'Rarest unlock', by: (a, b) => (a.rare ?? 0) - (b.rare ?? 0), score: (g) => -(g.rare ?? 0) },
      deck: { label: 'Deck time', by: (a, b) => (b.dk ?? 0) - (a.dk ?? 0), score: (g) => g.dk ?? 0 },
      age: { label: 'Oldest titles', by: (a, b) => (a.y ?? 0) - (b.y ?? 0), score: (g) => -(g.y ?? 0) },
      genre: { label: 'Genre bucket', by: (a, b) => (a.g || '').localeCompare(b.g || ''), score: (g) => g.g || '' },
      backlog: { label: 'Backlog of shame', by: (a, b) => {
          const ac = (a.ap ?? 0) / Math.max(a.rare ?? 100, 1);
          const bc = (b.ap ?? 0) / Math.max(b.rare ?? 100, 1);
          return bc - ac;
        }, score: (g) => (g.ap ?? 0) / Math.max(g.rare ?? 100, 1)
      },
      newest: { label: 'Newest releases', by: (a, b) => (b.y ?? 0) - (a.y ?? 0), score: (g) => g.y ?? 0 },
    };

    function fmtMoney(v) {
      if (v == null) return '—';
      return '$' + Number(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    function fmtPlay(h) { return `${(h ?? 0).toFixed(1)}h`; }

    function textElement(tagName, className, text) {
      const element = document.createElement(tagName);
      if (className) element.className = className;
      element.textContent = text;
      return element;
    }

    function rowLabel(g, lensKey) {
      if (lensKey === 'backlog') {
        const owned = g.ownedDate || g.owned || null;
        const acquisition = owned ? `acquired ${owned}` : 'acquisition date unavailable';
        return `Backlog line: played ${fmtPlay(g.h)}; ${acquisition}.`; 
      }
      if (lensKey === 'costPerHour') {
        return `Cost/hour score: ${(g.p == null ? '—' : fmtMoney((g.p / Math.max(g.h, 1)).toFixed(2)))} · cost ${fmtMoney(g.p)} · ${fmtPlay(g.h)}`;
      }
      if (lensKey === 'achievementRarity') {
        return `Rarest unlock percent: ${g.rare == null ? '—' : g.rare.toFixed(2)}% · completion ${(g.ap == null ? '—' : g.ap.toFixed(1) + '%')}`;
      }
      if (lensKey === 'recent') {
        return `Last played: ${g.last == null ? 'never' : g.last + ' days ago'}`;
      }
      if (lensKey === 'recentCost') {
        return `Recent score: ${(lenses.recentCost.score(g) || 0).toFixed(2)} · recent ${fmtPlay(g.w)} · owned ${(g.owned || 'unknown')}`;
      }
      return `Genre: ${g.g || '—'} · Release: ${g.y || '—'} · Last: ${g.last == null ? '—' : g.last + ' days ago'}`;
    }

    function render(data, player, sourceLabel, visibility = 'public') {
      const use = lenses[lensEl.value] || lenses.playtime;

      const games = [...data]
        .filter((g) => Number.isFinite((g.h ?? 0)) || Number.isFinite((g.p ?? 0)))
        .map((g) => ({ g, score: use.score(g) }))
        .sort((a, b) => {
          const c = use.by(a.g, b.g);
          if (c !== 0) return c;
          return b.g.h - a.g.h;
        })
        .map((x) => x.g);

      const header = document.createElement('div');
      header.className = 'result-head';
      header.appendChild(textElement('strong', '', sourceLabel));
      header.appendChild(textElement(
        'span',
        '',
        `${player?.personaName || 'Steam player'} · ${player?.gamesCount ?? games.length} games`,
      ));
      header.appendChild(textElement('span', 'muted', `lens: ${use.label}`));

      const grid = document.createElement('div');
      grid.className = 'result-grid';
      const valueFor = (g) => {
        const raw = use.score(g);
        if (raw == null || Number.isNaN(raw)) return '—';
        if (lensEl.value === 'cost' || lensEl.value === 'costPerHour' || lensEl.value === 'recentCost') {
          return fmtMoney(typeof raw === 'number' ? raw : Number(raw) || 0);
        }
        if (lensEl.value === 'metacritic' || lensEl.value === 'achievement') return `${Number(raw).toFixed(1)}%`;
        if (lensEl.value === 'achievementRarity') return `${Number(raw).toFixed(2)}%`;
        if (lensEl.value === 'genre') return g.g || '—';
        return typeof raw === 'number' ? raw.toFixed(2) : String(raw);
      };

      games.slice(0, 60).forEach((g) => {
        const row = document.createElement('article');
        row.className = 'game';

        const top = document.createElement('div');
        top.className = 'top';
        top.appendChild(textElement('div', 'title', g.n));
        top.appendChild(textElement('div', 'value', valueFor(g)));

        const meta = document.createElement('div');
        meta.className = 'meta-line';

        const pills = document.createElement('div');
        pills.appendChild(textElement(
          'span',
          'pill',
          `played ${fmtPlay(g.h)} (this week ${fmtPlay(g.w)})`,
        ));
        pills.appendChild(textElement('span', 'pill', `price ${fmtMoney(g.p)}`));
        pills.appendChild(textElement('span', 'pill', `deck ${fmtPlay(g.dk)}`));

        const sub = document.createElement('p');
        sub.className = 'subline';
        sub.textContent = rowLabel(g, lensEl.value);

        meta.appendChild(pills);
        meta.appendChild(sub);
        row.appendChild(top);
        row.appendChild(meta);
        grid.appendChild(row);
      });

      out.replaceChildren(header, grid);

      if (visibility === 'private') {
        setStatus('Profile is private. Public game data is required for results.');
      }
      metaEl.textContent = `${sourceLabel} · ${games.length} games · lens ${use.label}`;
    }

    function setStatus(msg, kind = 'ok') {
      statusEl.textContent = msg;
      statusEl.style.color = kind === 'err' ? 'var(--bad)' : 'var(--ink2)';
    }

    let G = demo;
    let playerState = { personaName: 'Demo User', gamesCount: demo.length };
    function renderDemo() {
      render(G, playerState, 'Demo fixture', 'public');
      setStatus('Demo loaded (fixture). Use search to run live profile analysis.');
      metaEl.textContent = `fixture: ${demo.length} games`;
    }

    async function doSearch() {
      const q = qEl.value.trim();
      if (!q) {
        setStatus('Enter an ID, vanity, or URL.');
        return;
      }

      setStatus('Loading profile...');
      try {
        const r = await fetch('/api/steam?q=' + encodeURIComponent(q));
        const json = await r.json();

        if (!json.ok) {
          setStatus(json.error || 'Search failed.', 'err');
          return;
        }
        if (json.visibility === 'private') {
          setStatus('Profile is private; showing zeroed game metrics.');
          const privateMessage = textElement(
            'div',
            'panel',
            'Profile visibility is private. Make Steam Game details public and retry.',
          );
          privateMessage.style.padding = '16px';
          out.replaceChildren(privateMessage);
          return;
        }

        G = Array.isArray(json.games) ? json.games : [];
        playerState = json.player || playerState;
        render(G, playerState, `Live lookup for ${q}`, json.visibility || 'public');
        setStatus(`Loaded ${G.length} games for ${playerState.personaName || 'Steam player'}.`);
        metaEl.textContent = `live · ${G.length} games · lens ${lenses[lensEl.value].label}`;
      } catch (e) {
        setStatus('Failed to reach API proxy. Is the local server running?', 'err');
        console.error(e);
      }
    }

    renderDemo();

    document.getElementById('form').addEventListener('submit', (e) => {
      e.preventDefault();
      doSearch();
    });

    lensEl.addEventListener('change', () => {
      render(G, playerState, out.querySelector('strong')?.textContent || 'Demo fixture', 'public');
    });

    resetBtn.addEventListener('click', () => {
      qEl.value = '';
      renderDemo();
    });
