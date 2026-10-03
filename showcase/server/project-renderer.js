import { serializePublicCatalog } from './project-catalog.js';

const KNOWN_ART_KEYS = new Set([
  'prismet',
  'long-now',
  'prismcode',
  'steam',
  'debt',
  'cicero',
  'chess',
  'helix',
  'axiom',
  'qr',
  'screen',
  'escape',
  'war-room',
  'civ',
  'yggdrasil',
  'wow',
]);

const MARKER = (name) => `<!-- PRISMET:${name} -->`;

const escapeHtml = (value) => String(value ?? '')
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;')
  .replaceAll("'", '&#39;');

const categoryLabel = (category) => (
  `${category.slice(0, 1).toUpperCase()}${category.slice(1)}`
);

const numberWord = (value) => {
  const words = [
    'Zero', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
    'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen',
    'Eighteen', 'Nineteen', 'Twenty',
  ];
  return words[value] ?? String(value);
};

const formatUtc = (value) => {
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return 'refresh time unavailable';
  const month = new Intl.DateTimeFormat('en-US', {
    month: 'short',
    timeZone: 'UTC',
  }).format(date);
  const day = date.getUTCDate();
  const year = date.getUTCFullYear();
  const hours = String(date.getUTCHours()).padStart(2, '0');
  const minutes = String(date.getUTCMinutes()).padStart(2, '0');
  return `${day} ${month} ${year} at ${hours}:${minutes} UTC`;
};

const formatActivityDate = (value) => {
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return null;
  return new Intl.DateTimeFormat('en-US', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(date);
};

const titleWithEditorialBreak = (title) => {
  const delimiter = ' / ';
  const index = title.indexOf(delimiter);
  if (index === -1) return escapeHtml(title);
  return `${escapeHtml(title.slice(0, index + 2))}<br>${escapeHtml(title.slice(index + delimiter.length))}`;
};

const projectActionLabel = (project, { featured = false } = {}) => {
  if (!project.action) return null;
  if (project.action.startsWith('/')) return `Open ${project.title}`;

  try {
    const { hostname } = new URL(project.action);
    if (hostname === 'apps.apple.com') return 'View on the App Store';
    if (hostname === 'github.com') return 'View public repository';
  } catch {
    // Catalog validation owns URL safety; keep a neutral label as a final fallback.
  }
  return featured ? 'Visit featured project' : 'Visit project';
};

const renderAction = (project, { featured = false } = {}) => {
  if (!project.action) {
    return '<span class="project-action project-action--muted">Public link unavailable</span>';
  }

  const external = !project.action.startsWith('/');
  const className = featured ? 'featured-action' : 'project-action';
  const rel = external ? ' rel="noreferrer"' : '';
  return `<a class="${className}" href="${escapeHtml(project.action)}"${rel}>`
    + `${escapeHtml(projectActionLabel(project, { featured }))} <span aria-hidden="true">↗</span></a>`;
};

const renderProjectFacts = (project) => {
  const facts = [];
  if (project.version) facts.push(['Version', project.version]);
  if (project.release) facts.push(['Release', project.release]);
  if (project.language) facts.push(['Language', project.language]);
  if (project.lastActiveAt) {
    const date = formatActivityDate(project.lastActiveAt);
    if (date) facts.push(['Active', date]);
  }
  if (project.linkHealth) facts.push(['Link', project.linkHealth]);
  if (facts.length === 0) return '';

  return '<dl class="project-facts">'
    + facts.map(([label, value]) => (
      `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`
    )).join('')
    + '</dl>';
};

const layoutClasses = (layout) => {
  if (layout === 'wide-tall') return ' project-card--wide project-card--tall';
  if (layout === 'wide') return ' project-card--wide';
  if (layout === 'tall') return ' project-card--tall';
  return '';
};

const shotUrl = (shot, size) => `/shots/${encodeURIComponent(shot.src)}-${size}.webp`;

// Portrait captures are phone screens; they get a narrower frame so a 9:19.5 shot
// does not blow out the height of the row it sits in.
const shotOrientation = (shot) => (shot.h > shot.w ? 'portrait' : 'landscape');

const renderShotImage = (shot, { eager = false, size = 'card' } = {}) => (
  `<img src="${escapeHtml(shotUrl(shot, size))}"`
  + ` width="${shot.w}" height="${shot.h}"`
  + ` alt="${escapeHtml(shot.alt)}"`
  + ` loading="${eager ? 'eager' : 'lazy'}" decoding="async">`
);

// Data attributes are the contract with site.js: it upgrades these figures into
// lightbox triggers at runtime, so with JS off they stay ordinary images.
const shotFigure = (shot, project, index, { eager = false, className = '' } = {}) => (
  `<figure class="project-shot project-shot--${shotOrientation(shot)}${className}"`
  + ` data-shot data-shot-full="${escapeHtml(shotUrl(shot, 'full'))}"`
  + ` data-shot-gallery="${escapeHtml(project.id)}" data-shot-index="${index}">`
  + renderShotImage(shot, { eager })
  + '</figure>'
);

const renderProjectVisual = (project, { eager = false } = {}) => {
  const shots = Array.isArray(project.shots) ? project.shots : [];
  if (shots.length === 0) {
    const art = KNOWN_ART_KEYS.has(project.art) ? project.art : 'generic';
    return `<div class="project-art project-art--${escapeHtml(art)}" aria-hidden="true">`
      + '<span></span><span></span><span></span></div>';
  }

  const [lead, ...rest] = shots;
  const strip = rest.length === 0 ? '' : (
    '<div class="project-shot-strip">'
    + rest.map((shot, offset) => shotFigure(shot, project, offset + 1, {
      className: ' project-shot--thumb',
    })).join('')
    + '</div>'
  );
  const count = shots.length > 1
    ? `<p class="project-shot-count">${shots.length} screenshots</p>`
    : '';

  return `<div class="project-shots" data-shot-group="${escapeHtml(project.id)}">`
    + shotFigure(lead, project, 0, { eager, className: ' project-shot--lead' })
    + strip
    + count
    + '</div>';
};

const renderProject = (project) => {
  const platforms = project.platforms
    .map((platform) => `<span>${escapeHtml(platform)}</span>`)
    .join('');
  const history = project.history
    ? `<p class="project-history">${escapeHtml(project.history)}</p>`
    : '';
  const shotState = Array.isArray(project.shots) && project.shots.length > 0
    ? ' project-card--shot'
    : ' project-card--art';

  return `        <article class="project-card${layoutClasses(project.layout)}${shotState}" data-project-card data-category="${escapeHtml(project.category)}">
          ${renderProjectVisual(project)}
          <div class="project-copy">
            <div class="project-kicker"><span>${escapeHtml(categoryLabel(project.category))}</span><span>${escapeHtml(project.status)}</span></div>
            <h3>${escapeHtml(project.title)}</h3>
            <p>${escapeHtml(project.summary)}</p>
            ${history}
            <div class="project-meta">${platforms}</div>
            ${renderProjectFacts(project)}
            ${renderAction(project)}
          </div>
        </article>`;
};

// The hero keeps the abstract prism stage only when the featured project has
// nothing real to show; otherwise the screenshots take the space.
const renderFeaturedStage = (project) => {
  const shots = Array.isArray(project.shots) ? project.shots : [];
  if (shots.length === 0) {
    return `<div class="featured-stage featured-stage--art" aria-hidden="true">
        <div class="stage-frame stage-frame--back"></div>
        <div class="stage-spectrum"></div>
        <div class="stage-frame stage-frame--front">
          <span class="stage-glyph"></span>
          <span class="stage-line"></span>
          <span class="stage-line"></span>
          <span class="stage-line"></span>
        </div>
      </div>`;
  }

  const [lead, ...rest] = shots;
  const strip = rest.length === 0 ? '' : (
    '<div class="featured-strip">'
    + rest.map((shot, offset) => shotFigure(shot, project, offset + 1, {
      className: ' project-shot--thumb',
    })).join('')
    + '</div>'
  );

  return `<div class="featured-stage featured-stage--shots" data-shot-group="${escapeHtml(project.id)}">`
    + shotFigure(lead, project, 0, { eager: true, className: ' project-shot--hero' })
    + strip
    + '</div>';
};

const replaceMarker = (html, name, replacement) => {
  const marker = MARKER(name);
  const occurrences = html.split(marker).length - 1;
  if (occurrences !== 1) {
    throw new TypeError(`template must contain exactly one ${marker} marker`);
  }
  return html.replace(marker, replacement);
};

export function renderHomepage(template, snapshot) {
  if (typeof template !== 'string' || template.length === 0) {
    throw new TypeError('template must be a non-empty string');
  }
  if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot)) {
    throw new TypeError('snapshot must be an object');
  }

  const catalog = serializePublicCatalog(snapshot.catalog);
  const featured = catalog.projects.find((project) => project.featured) ?? catalog.projects[0];
  const projectCount = catalog.projects.length;
  const categoryCount = new Set(catalog.projects.map(({ category }) => category)).size;
  const featuredMeta = [
    categoryLabel(featured.category),
    featured.platforms.join(' · '),
    featured.status,
  ].map((value) => `<span>${escapeHtml(value)}</span>`).join('\n          ');
  const freshness = snapshot.stale
    ? `Showing the last known project snapshot · Last successful refresh ${formatUtc(snapshot.refreshedAt)}`
    : `Updated from project sources · ${formatUtc(snapshot.refreshedAt)}`;

  const shotProjects = catalog.projects.filter(
    (project) => Array.isArray(project.shots) && project.shots.length > 0,
  );
  const shotCount = shotProjects.reduce((total, project) => total + project.shots.length, 0);
  const shotLedger = shotCount === 0
    ? 'Screenshots coming as each one becomes real'
    : `${shotCount} screenshots from ${shotProjects.length} of them`;

  const replacements = {
    PROJECT_COUNT: String(projectCount),
    PROJECT_COUNT_PADDED: String(projectCount).padStart(2, '0'),
    SHOT_LEDGER: escapeHtml(shotLedger),
    FEATURED_STATUS: escapeHtml(featured.status),
    FEATURED_TITLE: titleWithEditorialBreak(featured.title),
    FEATURED_SUMMARY: escapeHtml(featured.summary),
    FEATURED_META: featuredMeta,
    FEATURED_HISTORY: escapeHtml(featured.history ?? ''),
    FEATURED_ACTION: renderAction(featured, { featured: true }),
    FEATURED_STAGE: renderFeaturedStage(featured),
    COLLECTION_SUMMARY: `${numberWord(projectCount)} projects across ${categoryCount} families. Some are live, some are becoming, and some are preserved exactly where they stopped.`,
    FRESHNESS: escapeHtml(freshness),
    VISIBLE_COUNT: `${projectCount} ${projectCount === 1 ? 'project' : 'projects'}`,
    PROJECT_GRID: catalog.projects.map(renderProject).join('\n\n'),
    FOOTER_SUMMARY: `${projectCount} project families across Apple platforms, web, desktop, and games.`,
  };

  let html = template;
  for (const [name, replacement] of Object.entries(replacements)) {
    html = replaceMarker(html, name, replacement);
  }
  if (/<!--\s*PRISMET:/.test(html)) {
    throw new TypeError('template contains an unknown Prismet marker');
  }
  return html;
}
