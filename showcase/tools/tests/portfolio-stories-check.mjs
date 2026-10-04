// Source-only case-study contracts: no build artifact, browser or network required.
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { renderSite } from '../../prismet-site/pages/lib/render.js';
import { loadSite, loadWork, parseSections, writeSections } from '../../prismet-site/content.mjs';
const root=fileURLToPath(new URL('../../',import.meta.url));
const data=JSON.parse(readFileSync(`${root}prismet-site/data/projects.json`));
const site=loadSite(),work=Object.fromEntries(data.projects.map(p=>[p.slug,loadWork(p.slug)]));
const assets={has:p=>existsSync(root+p),size:()=>({w:1600,h:900}),url:p=>p};
const options={data,site,work,assets,urls:{css:'site.css',js:'site.js',og:'https://prismet.xyz/assets/og.jpg'}};
const expected=['bayzyl','prismet-app','the-helm','prismcode'];
let count=0;const check=(value,label)=>{assert.ok(value,label);count++;};
check(JSON.stringify(data.projects.filter(p=>p.story).map(p=>p.slug).sort())===JSON.stringify([...expected].sort()),'only four agreed flagships have stories');
check(data.projects.reduce((n,p)=>n+(p.portfolio?.length||0),0)===40,'40 original portfolio views retained');
const escape=s=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
for(const mode of ['live','edit','preview']){
 const output=renderSite({...options,...(mode==='live'?{}:{[mode]:true})});
 check(output.missing.size===0,`${mode}: all wording keys resolve`);
 const home=output.pages.get('index.html');
 for(const slug of expected){
  const project=data.projects.find(p=>p.slug===slug),html=output.pages.get(`work/${slug}.html`);
  check(html.includes('id="engineering-story"')&&html.includes('aria-labelledby="story-heading"'),`${mode}/${slug}: accessible story section`);
  check(home.includes(`work/${slug}.html#engineering-story`),`${mode}/${slug}: chapter deep link`);
  check(html.includes(`href="#portfolio-${slug}"`)&&html.includes(`id="portfolio-${slug}"`),`${mode}/${slug}: evidence link resolves`);
  const fields=['focus','contribution','outcome','evidence',...project.story.decisions.flatMap(id=>[`decision.${id}.title`,`decision.${id}.text`])];
  for(const field of fields){
   check(typeof work[slug][`story.${field}`]==='string',`${slug}: ${field} wording lives in Markdown`);
   check(html.includes(escape(work[slug][`story.${field}`])),`${mode}/${slug}: ${field} shown`);
   if(mode!=='live')check(html.includes(`data-edit="work.${slug}.story.${field}"`),`${mode}/${slug}: ${field} editable`);
  }
  check(Object.keys(project.story).every(key=>key==='decisions')&&project.story.decisions.every(Number.isInteger),`${slug}: story data is structural only`);
  const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);check(ids.length===new Set(ids).size,`${mode}/${slug}: unique IDs`);
  if(mode!=='live'){check(html.includes('data-edit="story.contribution"'),`${mode}/${slug}: label remains editable`);check(html.includes(`data-edit="work.${slug}.summary"`),`${mode}/${slug}: original copy still editable`);check(home.includes(`data-edit="work.${slug}.story.focus"`),`${mode}/${slug}: homepage focus editable`);}
 }
 check(!output.pages.get('work/qr-tools.html').includes('id="engineering-story"'),`${mode}: unrelated page unaffected`);
}
const dirty=structuredClone(data),dirtyWork=structuredClone(work),story=dirty.projects.find(p=>p.slug==='prismcode').story;
for(const key of ['focus','contribution','outcome','evidence'])dirtyWork.prismcode[`story.${key}`]='<img src=x onerror="bad"> & test';
dirtyWork.prismcode['story.decision.1.title']='<script>bad</script>';dirtyWork.prismcode['story.decision.1.text']='<b>untrusted</b>';story.decisions.push(null);
const attacked=renderSite({...options,data:dirty,work:dirtyWork}).pages.get('work/prismcode.html');
check(!attacked.includes('<script>bad')&&!attacked.includes('<img src=x'), 'all story Markdown escaped');
check(attacked.includes('&lt;script&gt;bad&lt;/script&gt;')&&attacked.includes('&lt;b&gt;untrusted&lt;/b&gt;'),'decision fields escaped and malformed rows ignored');
story.decisions=null;check(!renderSite({...options,data:dirty}).pages.get('work/prismcode.html').includes('id="engineering-story"'),'malformed optional story omitted safely');
check(work['prismet-app']['story.contribution'].startsWith('Co-developed across '),'co-developed credit retained without collaborator name');
for(const slug of expected){
 const raw=readFileSync(`${root}prismet-site/content/work/${slug}.md`,'utf8'),marker='<!-- Proposed engineering-story wording';
 check(raw.includes(marker),`${slug}: agent wording is flagged as proposed`);
 const originalPrefix=raw.slice(0,raw.indexOf(marker));
 for(const key of Object.keys(work[slug]).filter(key=>key.startsWith('story.'))){
  const edited=`Edited ${key} & **reviewed**`,updated=writeSections(raw,{[key]:edited});
  check(updated.startsWith(originalPrefix),`${slug}/${key}: editor preserves authored prefix`);
  const nextWork={...work,[slug]:parseSections(updated)},output=renderSite({...options,work:nextWork,edit:true});
  check(nextWork[slug][key]===edited&&output.pages.get(`work/${slug}.html`).includes(`Edited ${key} &amp; <strong>reviewed</strong>`),`${slug}/${key}: edit round-trips through Markdown and renderer`);
 }
}
check(data.projects.find(p=>p.slug==='prismcode').portfolio.every(p=>p.evidence==='Scripted demo'),'scripted labels unchanged');
console.log(`✓ portfolio-stories-check: ${count} source render contracts passed`);
