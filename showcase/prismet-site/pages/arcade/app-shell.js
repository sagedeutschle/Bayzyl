import { catalog, categories, byID } from './catalog.js';
function art(entry) {
  if (!entry.art) { const icon = document.createElement('span'); icon.className = 'facet-hex'; icon.textContent = '⬡'; icon.setAttribute('aria-hidden','true'); return icon; }
  const image = document.createElement('img'); image.src = entry.art; image.width = 56; image.height = 56; image.alt = ''; image.loading = 'lazy'; return image;
}
function link(entry, small = false) {
  const anchor = document.createElement('a'); anchor.href = entry.href; anchor.className = small ? 'facet-row' : 'facet-tile'; anchor.dataset.facet = entry.id; anchor.append(art(entry));
  const text = document.createElement('span'), title = document.createElement('strong'), detail = document.createElement('small'); title.textContent = entry.title; detail.textContent = small ? entry.category : entry.description; text.append(title,detail); anchor.append(text);
  if (location.pathname.replace(/\/$/,'') === entry.href) anchor.setAttribute('aria-current','page');
  return anchor;
}
export function mountAppShell() {
  const index = document.getElementById('app-index'), library = document.getElementById('facet-library');
  if (index) {
    const details = document.createElement('details'); details.className = 'app-index-disclosure'; const summary = document.createElement('summary'); summary.textContent = 'Browse the collection'; details.append(summary);
    const inner = document.createElement('nav'); inner.setAttribute('aria-label','Prismet collection'); const home = document.createElement('a'); home.className = 'app-home-link'; home.href = '/arcade'; home.textContent = 'Prismet · turn the lens.'; inner.append(home);
    for (const category of categories) { const group = document.createElement('div'); group.className = 'index-category'; const label = document.createElement('h2'); label.textContent = category; group.append(label); for (const entry of catalog.filter((e)=>e.category===category)) group.append(link(entry,true)); inner.append(group); }
    details.append(inner); index.append(details); const wide = matchMedia('(min-width:981px)'); details.open = wide.matches; wide.addEventListener('change', () => { details.open = wide.matches; });
  }
  if (library) {
    for (const category of categories) { const section = document.createElement('section'); section.className = 'facet-category'; section.id = `category-${category.toLowerCase()}`; const heading = document.createElement('h2'); heading.textContent = category; const grid = document.createElement('div'); grid.className = 'facet-grid'; for (const entry of catalog.filter((e)=>e.category===category)) grid.append(link(entry)); section.append(heading,grid); library.append(section); }
    const search = document.getElementById('facet-search');
    search?.addEventListener('input',()=>{ const query = search.value.trim().toLowerCase(); let count = 0; for (const tile of library.querySelectorAll('[data-facet]')) { const entry = byID[tile.dataset.facet]; const match = `${entry.title} ${entry.category}`.toLowerCase().includes(query); tile.hidden = !match; if(match) count++; } for(const section of library.children) section.hidden = ![...section.querySelectorAll('[data-facet]')].some((tile)=>!tile.hidden); document.getElementById('facet-count').textContent = `${count} of ${catalog.length} in the collection`; document.getElementById('facet-empty').hidden = count !== 0; });
  }
}
mountAppShell();

export function refreshAppSelection(id) { for(const link of document.querySelectorAll("[data-facet]")){link.removeAttribute("aria-current");if(link.dataset.facet===id)link.setAttribute("aria-current","page");} }
