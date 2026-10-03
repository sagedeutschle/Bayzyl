// styles.js — data/styles.json → CSS. Pure, shared by the build and the editor's preview.
//
// theme.json changes a token everywhere it is used; styles.json changes ONE element, optionally at one width:
//   { "rules": { "text:hero.title": { "base": { "font-size": "64px" }, "mobile": { "font-size": "40px" } },
//                "section:lenses":  { "mobile": { "display": "none" } } } }
// A target is a piece of wording (text:<its key in the content files>) or a part of the home page (section:<id>).
// base applies at every width; tablet at 900px and narrower; mobile at 600px and narrower. A narrower tier inherits
// whatever it does not set. Only the properties listed here are written, each with one declaration's worth of value,
// so the file cannot grow into a second stylesheet.
const MARK = '\n/* styles: what data/styles.json changes (edited at /edit) */\n';
export const STYLES_MARK = MARK;
/** [id, label, media query, the widest viewport it covers]. Order matters: later tiers override earlier ones. */
export const TIERS = [['base', 'Desktop', '', Infinity], ['tablet', 'Tablet', '(max-width: 900px)', 900], ['mobile', 'Mobile', '(max-width: 600px)', 600]];
export const tierFor = (width) => (width <= 600 ? 'mobile' : width <= 900 ? 'tablet' : 'base');
export const PROPS = new Set(['font-size', 'font-weight', 'line-height', 'letter-spacing', 'text-transform', 'text-align', 'font-family', 'color', 'opacity',
  'max-width', 'margin-top', 'margin-bottom', 'padding-top', 'padding-bottom', 'background-color', 'display']);
const safe = (v) => typeof v === 'string' && v.trim().length > 0 && v.length <= 200 && !/[;{}<>\\!]|url\(|@|\/\*/i.test(v);

/** The CSS selector of a target. attr is the attribute that carries a wording key: data-s on the site, data-edit in the editor. */
export function selectorOf(target, attr = 'data-s') {
  const m = String(target).match(/^(text|section):([A-Za-z0-9._-]{1,120})$/);
  if (!m) return null;
  if (m[1] === 'text') return `[${attr}="${m[2]}"]`;
  return m[2] === 'hero' ? '.entrance' : /^[a-z][a-z0-9-]*$/.test(m[2]) ? `#main > [data-section="${m[2]}"]` : null;
}
const decls = (o) => Object.entries(o || {}).filter(([k, v]) => PROPS.has(k) && safe(v)).map(([k, v]) => `${k}: ${v.trim()} !important;`).join(' ');

/** The wording keys that carry a style: the renderer marks those elements (data-s) so the rules can find them. */
export function styledKeys(styles) {
  return new Set(Object.entries(styles?.rules || {}).filter(([t, r]) => t.startsWith('text:') && selectorOf(t) && TIERS.some(([id]) => decls(r?.[id]))).map(([t]) => t.slice(5)));
}

export function stylesCss(styles, { attr = 'data-s' } = {}) {
  const out = [];
  for (const [id, , media] of TIERS) {
    const rules = Object.entries(styles?.rules || {}).map(([t, r]) => [selectorOf(t, attr), decls(r?.[id])]).filter(([s, d]) => s && d).map(([s, d]) => `${s} { ${d} }`);
    if (rules.length) out.push(media ? `@media ${media} { ${rules.join(' ')} }` : rules.join('\n'));
  }
  return out.length ? `${MARK}${out.join('\n')}\n` : '';
}
