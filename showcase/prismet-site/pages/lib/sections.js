// sections.js — the section library: the kinds of section a page made at /edit can hold, and how each is drawn.
// Pure, shared by the build and the editor. Every kind is built from the site's own pieces (.wrap, .btn, .plaque,
// .prose, the doors) and its tokens, so a new section looks like Prismet without anyone styling it.
//
// A section is { id, type, hidden?, props: { field: text } } in data/pages.json. Fields are plain text in the content
// format: *gold*, **bold**, a blank line for a new paragraph, "- item" lists, "- Label: Value" pairs.
import { inline, plain, listItems, factPairs } from './format.js';

/** kind → what the editor offers. fields: [prop, label, kind], kind = line | text | href | image | choice:<a>,<b>. */
export const SECTION_TYPES = {
  hero: { label: 'Hero', fields: [['eyebrow', 'Eyebrow', 'line'], ['title', 'Title', 'line'], ['lede', 'Lede', 'text'], ['cta', 'Button label', 'line'], ['href', 'Button link', 'href']],
    defaults: { eyebrow: 'Prismet', title: 'A new page', lede: 'One or two sentences that say what this page is for.', cta: '', href: '' } },
  text: { label: 'Text', fields: [['heading', 'Heading', 'line'], ['body', 'Body', 'text']],
    defaults: { heading: 'A heading', body: 'A paragraph. A blank line starts the next one.' } },
  split: { label: 'Image + text', fields: [['image', 'Image', 'image'], ['alt', 'Image description', 'line'], ['heading', 'Heading', 'line'], ['body', 'Body', 'text'], ['side', 'Image side', 'choice:left,right']],
    defaults: { image: '', alt: '', heading: 'A heading', body: 'A paragraph beside the image.', side: 'left' } },
  records: { label: 'Records', fields: [['heading', 'Heading', 'line'], ['slugs', 'Records (slugs, comma separated; empty = the principal works)', 'line']],
    defaults: { heading: 'Selected work', slugs: '' } },
  stats: { label: 'Statistics', fields: [['heading', 'Heading', 'line'], ['items', 'Figures', 'text']],
    defaults: { heading: '', items: '- Records: 13\n- Years: 4' } },
  quote: { label: 'Quote', fields: [['quote', 'Quote', 'text'], ['source', 'Source', 'line']],
    defaults: { quote: 'A sentence worth setting apart.', source: '' } },
  cta: { label: 'Call to action', fields: [['heading', 'Heading', 'line'], ['body', 'Body', 'text'], ['cta', 'Button label', 'line'], ['href', 'Button link', 'href']],
    defaults: { heading: 'Work with me', body: 'A sentence about what happens next.', cta: 'Get in touch', href: '' } },
  gallery: { label: 'Gallery', fields: [['heading', 'Heading', 'line'], ['images', 'Images', 'text']],
    defaults: { heading: '', images: '' } },
  divider: { label: 'Divider', fields: [], defaults: {} },
  spacer: { label: 'Spacer', fields: [['size', 'Height', 'choice:16px,32px,48px,64px,96px,128px']], defaults: { size: '48px' } },
};
export const SLUG = /^[a-z0-9][a-z0-9-]{0,40}$/;
/** Names a page cannot take: the site's own pages and the server's routes. */
export const RESERVED = new Set(['index', 'colophon', 'edit', 'steam', 'debt', 'privacy', 'support', 'work', 'assets', 'lib', 'api', 'rtc', 'healthz', 'shots', 'site', 'favicon', 'robots', 'style', 'icon', 'manifest', 'home']);
export const validSlug = (s) => SLUG.test(s || '') && !RESERVED.has(s);
/** An image a section or record may name: a file under assets/, no way out of that folder, nothing that could be markup. */
export const validAsset = (p) => typeof p === 'string' && /^assets\/[A-Za-z0-9._/-]+$/.test(p) && !p.includes('..') && !p.includes('//');
export const validId = (s) => /^[a-z][a-z0-9-]{0,40}$/.test(s || '');
/** A link a section may carry: https, or a path inside the site. Anything else is dropped. */
export const safeHref = (h) => { const v = String(h || '').trim(); return /^https:\/\/[^\s"'<>]+$/.test(v) || /^(?!\/\/)[a-z0-9#/][a-z0-9._#/-]*$/i.test(v) ? v : ''; };
/** A section's name in lists: its kind and the first words it holds. */
export const sectionTitle = (s) => { const t = SECTION_TYPES[s.type]; const lead = plain(s.props?.heading || s.props?.title || s.props?.quote || ''); return `${t?.label || s.type}${lead ? `: ${lead.slice(0, 28)}` : ''}`; };

const paras = (body) => String(body || '').split(/\n\s*\n/).filter((p) => p.trim()).map((p) => (/^- /.test(p.trim()) ? `<ul>${listItems(p).map((i) => `<li>${inline(i)}</li>`).join('')}</ul>` : `<p>${inline(p.trim())}</p>`)).join('');

/**
 * One section as HTML. ctx: { esc, img, has, ed(key, raw), door(project), bySlug, shown, featured, top }
 * (top: this is the page's first section, so its title is the page's h1). scope is 'home' or the page's slug.
 */
export function renderSection(s, scope, ctx) {
  const t = SECTION_TYPES[s.type];
  if (!t || !validId(s.id)) return '';
  const p = { ...t.defaults, ...(s.props || {}) }, { esc } = ctx;
  const a = (prop) => ctx.ed(`page.${scope}.${s.id}.${prop}`, p[prop] ?? '');
  const has = (prop) => String(p[prop] ?? '').trim().length > 0;
  const h2 = (prop = 'heading') => (has(prop) || ctx.editing ? `<h2${a(prop)}>${inline(p[prop])}</h2>` : '');
  const button = () => { const href = safeHref(p.href); return has('cta') && href ? `<div class="ctas"><a class="btn primary" href="${esc(href)}"${a('cta')}>${inline(p.cta)}</a></div>` : ''; };
  const picture = (src, alt) => (validAsset(src) && ctx.has(src) ? ctx.img(src, { alt: plain(alt || '') }) : '');
  const body = {
    hero: () => `<div class="wrap">${has('eyebrow') ? `<p class="plaque brass"${a('eyebrow')}>${inline(p.eyebrow)}</p>` : ''}<${ctx.top ? 'h1' : 'h2'} class="x-title"${a('title')}>${inline(p.title)}</${ctx.top ? 'h1' : 'h2'}>${has('lede') ? `<p class="lede"${a('lede')}>${inline(p.lede)}</p>` : ''}${button()}</div>`,
    text: () => `<div class="wrap">${h2()}<div class="prose paras"${a('body')}>${paras(p.body)}</div></div>`,
    split: () => `<div class="wrap"><figure class="x-media">${picture(p.image, p.alt)}</figure><div class="x-copy">${h2()}<div class="prose paras"${a('body')}>${paras(p.body)}</div></div></div>`,
    records: () => {
      const list = (has('slugs') ? p.slugs.split(',').map((x) => ctx.bySlug[x.trim()]) : ctx.featured).filter((q) => q && ctx.shown.includes(q));
      return `<div class="wrap">${h2()}<div class="doors">${list.map((q) => ctx.door(q)).join('\n')}</div></div>`;
    },
    stats: () => `<div class="wrap">${h2()}<dl>${factPairs(p.items).map(([k, v]) => `<div><dt>${inline(k)}</dt><dd>${inline(v)}</dd></div>`).join('')}</dl></div>`,
    quote: () => `<div class="wrap"><figure><blockquote${a('quote')}>${inline(p.quote)}</blockquote>${has('source') ? `<figcaption${a('source')}>${inline(p.source)}</figcaption>` : ''}</figure></div>`,
    cta: () => `<div class="wrap"><div class="x-box">${h2()}${has('body') ? `<div class="prose paras"${a('body')}>${paras(p.body)}</div>` : ''}${button()}</div></div>`,
    gallery: () => `<div class="wrap">${h2()}<div class="grid">${factPairs(p.images).map(([src, alt]) => { const pic = picture(src, alt); return pic ? `<figure>${pic}${alt ? `<figcaption>${inline(alt)}</figcaption>` : ''}</figure>` : ''; }).join('')}</div></div>`,
    divider: () => '<div class="wrap"><hr></div>',
    spacer: () => `<div style="height:${/^\d{1,3}px$/.test(p.size) ? p.size : '48px'}"></div>`,
  }[s.type]();
  const cls = `x-sec x-${s.type}${s.type === 'split' && p.side === 'right' ? ' flip' : ''}${s.hidden ? ' is-off' : ''}`;
  return `<section id="${s.id}" data-section="${s.id}" class="${cls}">${body}</section>`;
}
