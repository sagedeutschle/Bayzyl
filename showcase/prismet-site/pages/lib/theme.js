// theme.js — data/theme.json → CSS. Pure, shared by the build and the editor's preview.
//
// site.css holds the design's defaults as custom properties. theme.json holds only what was changed at /edit:
//   { "root": { "--brass": "#D08A3C", "--radius": "6px" }, "day": { "--brass": "#7A4A12" } }
// root is night's colours plus everything that is the same day and night (type, sizes, corners); day is what differs
// in daylight. An empty theme writes nothing, so the stylesheet is site.css byte for byte.
import { STYLES_MARK } from './styles.js';

const NAME = /^--[a-z][a-z0-9-]*$/;
/** A value is one declaration's worth of CSS: no braces, semicolons, markup, url() or at-rules. */
export const safeValue = (v) => typeof v === 'string' && v.trim().length > 0 && v.length <= 200 && !/[;{}<>\\]|url\(|@|\/\*/i.test(v);
const decls = (o) => Object.entries(o || {}).filter(([k, v]) => NAME.test(k) && safeValue(v)).map(([k, v]) => `${k}: ${v.trim()};`).join(' ');

const MARK = '\n/* theme: what data/theme.json changes (edited at /edit) */\n';
/** site.css as served, without what the build added from theme.json and styles.json: the design's defaults. */
export const stripTheme = (css) => [MARK, STYLES_MARK].reduce((c, m) => (c.includes(m) ? c.slice(0, c.indexOf(m)) : c), css);

export function themeCss(theme) {
  const root = decls(theme?.root), day = decls(theme?.day);
  if (!root && !day) return '';
  return MARK
    + (root ? `:root { ${root} }\n` : '')
    + (day ? `@media (prefers-color-scheme: light) { :root:not([data-theme="dark"]) { ${day} } }\n:root[data-theme="light"] { ${day} }\n` : '');
}
