// The collection shell: the lens index beside every page, and the library itself, laid out like the app's Home:
// a resume strip ("On the cabinet"), favorites, and the categories with the app's tile art. Progress and favorites
// stay in this browser. Pure helpers (resumeItems) are covered by tools/tests/arcade-motion-check.mjs.
import { catalog, categories, byID, matchesCatalog, collectionCount } from './catalog.js';
import { engines } from './engines.js';
const FAVORITES = 'prismet.arcade.favorites.v1';
const store = {
  get(key) { try { return localStorage.getItem(key); } catch { return null; } },
  set(key, value) { try { localStorage.setItem(key, value); return true; } catch { return false; } },
  keys() { try { return Array.from({ length: localStorage.length }, (_, i) => localStorage.key(i)); } catch { return []; } },
};
const el = (tag, cls, text) => { const node = document.createElement(tag); if (cls) node.className = cls; if (text !== undefined) node.textContent = String(text); return node; };
function art(entry, size = 64) {
  if (!entry.art) { const icon = el('span', 'facet-hex', entry.id === 'uncle-scam' ? '§' : '⬡'); icon.setAttribute('aria-hidden', 'true'); return icon; }
  const image = el('img'); image.src = entry.art; image.width = size; image.height = size; image.alt = ''; image.loading = 'lazy'; image.decoding = 'async'; return image;
}
function row(entry) {
  const anchor = el('a', 'facet-row'); anchor.href = entry.href; anchor.dataset.facet = entry.id; anchor.dataset.category = entry.category.toLowerCase();
  anchor.append(art(entry, 28));
  const text = el('span'); text.append(el('strong', '', entry.title), el('small', '', entry.category)); anchor.append(text);
  if (location.pathname.replace(/\/$/, '') === entry.href) anchor.setAttribute('aria-current', 'page');
  return anchor;
}
function card(entry, index) {
  const article = el('article', 'facet-card'); article.dataset.card = entry.id; article.style.setProperty('--accent', entry.accent); article.style.setProperty('--i', index);
  const anchor = el('a', 'facet-tile'); anchor.href = entry.href; anchor.dataset.facet = entry.id; anchor.dataset.category = entry.category.toLowerCase();
  const frame = el('span', 'facet-art'); frame.append(art(entry, 64));
  const text = el('span', 'facet-text'); text.append(el('strong', '', entry.title), el('small', '', entry.description));
  anchor.append(frame, text);
  if (location.pathname.replace(/\/$/, '') === entry.href) anchor.setAttribute('aria-current', 'page');
  const star = el('button', 'facet-fav', '★'); star.type = 'button'; star.dataset.favorite = entry.id;
  article.append(anchor, star);
  return article;
}
function favorites() {
  try { const ids = JSON.parse(store.get(FAVORITES) || '[]'); return Array.isArray(ids) ? ids.filter((id) => typeof id === 'string' && Object.hasOwn(byID, id)) : []; } catch { return []; }
}
function setStar(button, on, title) { button.setAttribute('aria-pressed', String(on)); button.setAttribute('aria-label', `${on ? 'Remove' : 'Add'} ${title} ${on ? 'from' : 'to'} favorites`); }
export function ago(then, now = Date.now()) {
  const minutes = Math.floor((now - then) / 60000);
  if (!(minutes >= 1)) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60); if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24); return days === 1 ? 'yesterday' : `${days} days ago`;
}
/** Boards worth coming back to, newest first (at most three): local saves with moves, still in play where that is knowable. */
export function resumeItems(keys, read, now = Date.now()) {
  const items = [];
  for (const key of keys) {
    const m = /^prismet\.arcade\.(browser\.)?v1\.([a-z0-9-]+)\.(free|daily|challenge)(?:\.(.+))?$/.exec(key);
    if (!m || !Object.hasOwn(byID, m[2])) continue;
    try {
      const save = JSON.parse(read(key));
      if (!save || typeof save !== 'object' || save.gameID !== m[2]) continue;
      const steps = m[1] ? save.steps : save.moves;
      if (!Number.isSafeInteger(steps) || steps <= 0 || typeof save.savedAt !== 'string') continue;
      const savedAt = Date.parse(save.savedAt);
      if (!Number.isFinite(savedAt) || savedAt > now + 60000) continue;
      if (!m[1] && engines[m[2]]?.status(save.state) !== 'playing') continue;
      const query = new URLSearchParams({ mode: save.mode, seed: String(save.seed) });
      if (save.mode === 'daily' && save.dailyDate) query.set('date', save.dailyDate);
      items.push({ entry: byID[m[2]], steps, savedAt, mode: save.mode, href: `/arcade/${m[2]}?${query}` });
    } catch { /* unreadable local data is not a board to resume */ }
  }
  return items.sort((a, b) => b.savedAt - a.savedAt).slice(0, 3);
}
function renderCabinet(container) {
  const items = resumeItems(store.keys(), store.get);
  container.hidden = !items.length;
  if (!items.length) return;
  const list = el('ul');
  for (const item of items) {
    const li = el('li'), anchor = el('a', 'cabinet-item'); anchor.href = item.href; anchor.style.setProperty('--accent', item.entry.accent);
    anchor.append(art(item.entry, 40));
    const text = el('span'); text.append(el('strong', '', item.entry.title), el('small', '', `${item.steps} ${item.steps === 1 ? 'move' : 'moves'}${item.mode === 'daily' ? ' on the daily board' : ''}, ${ago(item.savedAt)}`));
    anchor.append(text, el('em', '', 'Resume'));
    li.append(anchor); list.append(li);
  }
  container.append(list);
}
export function responsiveDisclosure(details, media) {
  let chosen=false, automatic=media.matches;
  details.open=automatic;
  details.querySelector('summary')?.addEventListener('click',()=>{chosen=true;});
  details.addEventListener('toggle',()=>{if(details.open!==automatic)chosen=true;});
  media.addEventListener('change',()=>{if(!chosen){automatic=media.matches;details.open=automatic;}});
}
export function mountAppShell() {
  const index = document.getElementById('app-index'), library = document.getElementById('facet-library');
  if (index) {
    const details = el('details', 'app-index-disclosure'), summary = el('summary', '', 'Browse the collection'); details.append(summary);
    const inner = el('nav'); inner.setAttribute('aria-label', 'Prismet collection');
    const home = el('a', 'app-home-link', 'Prismet · turn the lens.'); home.href = '/arcade'; inner.append(home);
    for (const category of categories) { const group = el('div', 'index-category'); group.append(el('h2', '', category)); for (const entry of catalog.filter((e) => e.category === category)) group.append(row(entry)); inner.append(group); }
    details.append(inner); index.append(details); responsiveDisclosure(details, matchMedia('(min-width:981px)'));
  }
  const guide = document.getElementById('game-help');
  if (guide) responsiveDisclosure(guide, matchMedia('(min-width:981px)'));
  if (library) {
    const cabinet = document.getElementById('cabinet');
    if (cabinet) renderCabinet(cabinet);
    const favoritesSection = el('section', 'facet-category category-favorites'); favoritesSection.id = 'category-favorites'; favoritesSection.hidden = true;
    favoritesSection.append(el('h2', '', 'Your favorites'), el('div', 'facet-grid'));
    library.append(favoritesSection);
    let position = 0;
    for (const category of categories) {
      const section = el('section', `facet-category category-${category.toLowerCase()}`); section.id = `category-${category.toLowerCase()}`;
      const grid = el('div', 'facet-grid');
      for (const entry of catalog.filter((e) => e.category === category)) grid.append(card(entry, position++));
      section.append(el('h2', '', category), grid); library.append(section);
    }
    const search = document.getElementById('facet-search'), clear = document.getElementById('facet-clear');
    function filterLibrary() {
      const query = search?.value || '', seen = new Set();
      for (const node of library.querySelectorAll('.facet-card')) { const match = matchesCatalog(byID[node.dataset.card], query); node.hidden = !match; if (match) seen.add(node.dataset.card); }
      for (const section of library.children) section.hidden = ![...section.querySelectorAll('.facet-card')].some((node) => !node.hidden);
      document.getElementById('facet-count').textContent = collectionCount(query, seen.size);
      document.getElementById('facet-empty').hidden = seen.size !== 0;
      if (clear) clear.hidden = !query;
    }
    function syncFavorites() {
      const ids = favorites(), grid = favoritesSection.querySelector('.facet-grid');
      grid.replaceChildren(...ids.map((id, i) => card(byID[id], i)));
      for (const button of library.querySelectorAll('[data-favorite]')) setStar(button, ids.includes(button.dataset.favorite), byID[button.dataset.favorite].title);
      filterLibrary();
    }
    library.addEventListener('click', (event) => {
      const button = event.target.closest('[data-favorite]'); if (!button) return;
      const id = button.dataset.favorite, ids = favorites(), next = ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id];
      store.set(FAVORITES, JSON.stringify(next)); syncFavorites();
    });
    search?.addEventListener('input', filterLibrary);
    clear?.addEventListener('click', () => { search.value = ''; filterLibrary(); search.focus(); });
    syncFavorites();
    setTimeout(() => library.classList.add('settled'), 1400);
  }
}
if (typeof document !== 'undefined') mountAppShell();

export function refreshAppSelection(id) { for (const link of document.querySelectorAll('[data-facet]')) { link.removeAttribute('aria-current'); if (link.dataset.facet === id) link.setAttribute('aria-current', 'page'); } }
/** The game's tile beside its title on a play page, and its accent on the page. */
export function showGameArt(entry) {
  document.documentElement.style.setProperty('--accent', entry?.accent || 'var(--brass)');
  const title = document.querySelector('.game-title');
  let image = document.getElementById('game-art');
  if (!entry?.art || !title) { image?.remove(); return; }
  if (!image) { image = el('img', 'game-art'); image.id = 'game-art'; image.alt = ''; image.width = 56; image.height = 56; title.prepend(image); }
  image.src = entry.art;
}
