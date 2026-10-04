import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {catalog,byID,matchesCatalog,collectionCount} from '../../prismet-site/pages/arcade/catalog.js';
import {responsiveDisclosure} from '../../prismet-site/pages/arcade/app-shell.js';
let checks=0;const check=(value,label)=>{assert.ok(value,label);checks++;};
for(const [query,id] of [['catan','catan'],['settler scramble','catan'],['wordle','wordle'],['word game','wordle'],['battleship','sea-battle'],['connect 4','connect-four'],['connect4','connect-four'],['crazy eights','crazy-8'],['15 puzzle','sliding-15'],['picross','nonogram'],['rubiks cube','rubiks-cube'],['neighborhood','lights-out'],['tax receipt','uncle-scam'],['TAXES','uncle-scam']])check(matchesCatalog(byID[id],query),`${query} finds ${id}`);
check(byID.catan.title==='Settler Scramble'&&byID.catan.id==='catan'&&byID.catan.href==='/arcade/catan','display title changes without breaking IDs/routes');
check(!catalog.some(entry=>matchesCatalog(entry,'no such purple zebra')),'unmatched search empty');
check(catalog.filter(entry=>entry.category==='Lenses').length===3&&byID['uncle-scam'].href==='/scam','three real lenses including live tax tool');
check(catalog.filter(entry=>entry.category!=='Lenses').length===20,'no playable route expansion');
check(collectionCount('',23)==='23 in the collection'&&collectionCount('   ',23)==='23 in the collection','plain count on initial/cleared search');
check(collectionCount('tax',1)==='1 of 23 in the collection','filtered count from catalog');
function mocks(wide){const summary=new EventTarget(),details=new EventTarget(),media=new EventTarget();details.open=false;details.querySelector=()=>summary;media.matches=wide;return {details,summary,media,resize(value){media.matches=value;media.dispatchEvent(new Event('change'));},toggle(value,click=true){if(click)summary.dispatchEvent(new Event('click'));details.open=value;details.dispatchEvent(new Event('toggle'));}};}
let m=mocks(true);responsiveDisclosure(m.details,m.media);check(m.details.open,'wide initial open');m.details.dispatchEvent(new Event('toggle'));m.resize(false);check(!m.details.open,'untouched breakpoint follows layout');m.toggle(true);m.resize(true);m.resize(false);check(m.details.open,'opened reader choice survives resize');
m=mocks(true);responsiveDisclosure(m.details,m.media);m.toggle(false);m.resize(false);m.resize(true);check(!m.details.open,'closed reader choice survives resize');
m=mocks(false);responsiveDisclosure(m.details,m.media);m.toggle(true,false);m.resize(true);m.resize(false);check(m.details.open,'assistive toggle without click preserved');
const root=new URL('../../prismet-site/pages/',import.meta.url),game=readFileSync(new URL('arcade/game.html',root),'utf8'),home=readFileSync(new URL('arcade.html',root),'utf8'),css=readFileSync(new URL('arcade/arcade.css',root),'utf8');
check(/id="board"[^>]+role="group"/.test(game),'board is labelled group');
check(/<form method="post" hidden>/.test(game),'auth form cannot default to GET');
for(const html of [home,game]){check(!html.includes('/kaleidescope/'),'old App Store slug absent');check(html.includes('https://apps.apple.com/app/id6785993194'),'canonical App Store ID link');const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);check(ids.length===new Set(ids).size,'unique shell IDs');}
check(!/>22 in the collection</.test(home),'no stale static count');
check(/\.game-metrics span\s*\{[^}]*font:400 \.75rem/.test(css),'metric labels readable at twelve CSS pixels');
check(/\.game-metrics span\s*\{[^}]*font-size:\.75rem/.test(css),'phone metric labels retain size');
check(css.includes('grid-template-columns:repeat(2,minmax(0,1fr))'),'intended phone two-column tiles retained');
console.log(`✓ arcade-shell-check: ${checks} search, disclosure, catalog and markup checks passed`);
