import { catalog, categories, byID, matchesCatalog, collectionCount } from './catalog.js';
function art(entry) {
  if (!entry.art) { const icon = document.createElement('span'); icon.className = 'facet-hex'; icon.textContent = entry.id==='uncle-scam'?'§':'⬡'; icon.setAttribute('aria-hidden','true'); return icon; }
  const image = document.createElement('img'); image.src = entry.art; image.width = 56; image.height = 56; image.alt = ''; image.loading = 'lazy'; return image;
}
function link(entry, small = false) {
  const anchor = document.createElement('a'); anchor.href = entry.href; anchor.className = small ? 'facet-row' : 'facet-tile'; anchor.dataset.facet = entry.id; anchor.dataset.category = entry.category.toLowerCase(); anchor.append(art(entry));
  const text = document.createElement('span'), title = document.createElement('strong'), detail = document.createElement('small'); title.textContent = entry.title; detail.textContent = small ? entry.category : entry.description; text.append(title,detail); anchor.append(text);
  if (location.pathname.replace(/\/$/,'') === entry.href) anchor.setAttribute('aria-current','page');
  return anchor;
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
    const details = document.createElement('details'); details.className = 'app-index-disclosure'; const summary = document.createElement('summary'); summary.textContent = 'Browse the collection'; details.append(summary);
    const inner = document.createElement('nav'); inner.setAttribute('aria-label','Prismet collection'); const home = document.createElement('a'); home.className = 'app-home-link'; home.href = '/arcade'; home.textContent = 'Prismet · turn the lens.'; inner.append(home);
    for (const category of categories) { const group = document.createElement('div'); group.className = 'index-category'; const label = document.createElement('h2'); label.textContent = category; group.append(label); for (const entry of catalog.filter((e)=>e.category===category)) group.append(link(entry,true)); inner.append(group); }
    details.append(inner); index.append(details); responsiveDisclosure(details,matchMedia('(min-width:981px)'));
  }
  const guide = document.getElementById('game-help');
  if (guide) responsiveDisclosure(guide,matchMedia('(min-width:981px)'));
  if (library) {
    for (const category of categories) { const section = document.createElement('section'); section.className = `facet-category category-${category.toLowerCase()}`; section.id = `category-${category.toLowerCase()}`; const heading = document.createElement('h2'); heading.textContent = category; const grid = document.createElement('div'); grid.className = 'facet-grid'; for (const entry of catalog.filter((e)=>e.category===category)) grid.append(link(entry)); section.append(heading,grid); library.append(section); }
    const search = document.getElementById('facet-search');
    function filterLibrary(){const query=search?.value||'';let count=0;for(const tile of library.querySelectorAll('[data-facet]')){const match=matchesCatalog(byID[tile.dataset.facet],query);tile.hidden=!match;if(match)count++;}for(const section of library.children)section.hidden=![...section.querySelectorAll('[data-facet]')].some(tile=>!tile.hidden);document.getElementById('facet-count').textContent=collectionCount(query,count);document.getElementById('facet-empty').hidden=count!==0;}
    search?.addEventListener('input',filterLibrary);
    filterLibrary();
  }
}
if(typeof document!=='undefined')mountAppShell();

export function refreshAppSelection(id) { for(const link of document.querySelectorAll("[data-facet]")){link.removeAttribute("aria-current");if(link.dataset.facet===id)link.setAttribute("aria-current","page");} }
