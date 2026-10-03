import { isIP } from 'node:net';

const CATEGORIES = new Set(['apps', 'games', 'tools', 'experiments', 'systems']);
const LAYOUTS = new Set(['standard', 'wide', 'tall', 'wide-tall']);
const PUBLIC_INTERNAL_ACTIONS = new Set(['/steam', '/debt']);
const PUBLIC_REPOS = new Set([
  'sagedeutschle/kaleidoscope',
  'sagedeutschle/qr-scanner',
  'sagedeutschle/Bayzyl',
]);
const SAFE_PROJECT_KEYS = new Set([
  'id', 'title', 'summary', 'history', 'category', 'status', 'platforms',
  'art', 'layout', 'featured', 'action', 'repo', 'version', 'language',
  'topics', 'lastActiveAt', 'release', 'linkHealth', 'updatedAt', 'shots',
]);

const MAX_SHOTS_PER_PROJECT = 8;
// Screenshots are addressed by slug only. The renderer expands a slug into
// /shots/<slug>-card.webp, so a slug must never be able to escape that directory.
const SHOT_SRC_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const MAX_SHOT_EDGE = 10_000;

const TEXT_LIMITS = Object.freeze({
  id: 80,
  title: 100,
  summary: 280,
  history: 500,
  status: 40,
  platform: 40,
  art: 80,
  action: 2_048,
  repo: 200,
  version: 64,
  language: 64,
  topic: 50,
  release: 128,
  linkHealth: 40,
  shotSrc: 80,
  shotAlt: 240,
});

const isPublicHostname = (hostname) => {
  const host = hostname.toLowerCase().replace(/^\[|\]$/g, '').replace(/\.$/, '');
  if (!host.includes('.') || isIP(host) !== 0) return false;
  return !['localhost', '.localhost', '.local', '.internal', '.home', '.lan', '.home.arpa']
    .some((suffix) => host === suffix.replace(/^\./, '') || host.endsWith(suffix));
};

const isSafeAction = (value) => {
  if (
    typeof value !== 'string'
    || value.length === 0
    || value.length > TEXT_LIMITS.action
    || value !== value.trim()
  ) return false;
  if (value.startsWith('/')) return PUBLIC_INTERNAL_ACTIONS.has(value);

  try {
    const url = new URL(value);
    return url.protocol === 'https:'
      && !url.username
      && !url.password
      && isPublicHostname(url.hostname);
  } catch {
    return false;
  }
};

const githubRepoIdentity = (name, value) => {
  if (
    typeof name !== 'string'
    || name !== name.trim()
    || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/.test(name)
    || !isSafeAction(value)
  ) return null;

  try {
    const url = new URL(value);
    const path = url.pathname.split('/').filter(Boolean);
    if (
      url.hostname.toLowerCase() !== 'github.com'
      || url.search
      || url.hash
      || path.length !== 2
      || decodeURIComponent(path[1]).toLowerCase() !== name.toLowerCase()
    ) return null;
  } catch {
    return null;
  }

  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
};

const requireText = (value, label, maxLength, pattern) => {
  if (
    typeof value !== 'string'
    || value.length === 0
    || value.length > maxLength
    || value !== value.trim()
    || /[\u0000-\u001f\u007f]/.test(value)
    || (pattern && !pattern.test(value))
  ) {
    throw new TypeError(`${label} must be valid text no longer than ${maxLength} characters`);
  }
  return value;
};

const requirePixelEdge = (value, label) => {
  if (!Number.isInteger(value) || value < 1 || value > MAX_SHOT_EDGE) {
    throw new TypeError(`${label} must be a whole number of pixels between 1 and ${MAX_SHOT_EDGE}`);
  }
  return value;
};

const requireShots = (shots) => {
  if (!Array.isArray(shots) || shots.length === 0 || shots.length > MAX_SHOTS_PER_PROJECT) {
    throw new TypeError(`project shots must contain between 1 and ${MAX_SHOTS_PER_PROJECT} entries`);
  }
  const seen = new Set();
  for (const shot of shots) {
    if (!shot || typeof shot !== 'object' || Array.isArray(shot)) {
      throw new TypeError('project shot must be an object');
    }
    requireText(shot.src, 'project shot src', TEXT_LIMITS.shotSrc, SHOT_SRC_PATTERN);
    // Every screenshot has to carry a real description; the grid is image-led,
    // so a missing alt would leave a screen reader with nothing to go on.
    requireText(shot.alt, 'project shot alt', TEXT_LIMITS.shotAlt);
    requirePixelEdge(shot.w, 'project shot width');
    requirePixelEdge(shot.h, 'project shot height');
    if (seen.has(shot.src)) throw new TypeError(`duplicate project shot src: ${shot.src}`);
    seen.add(shot.src);
    for (const key of Object.keys(shot)) {
      if (!['src', 'alt', 'w', 'h'].includes(key)) {
        throw new TypeError(`unknown project shot field: ${key}`);
      }
    }
  }
  return shots;
};

const requireIsoDate = (value, label) => {
  const isoPattern = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$/;
  const date = typeof value === 'string' && isoPattern.test(value) ? new Date(value) : null;
  if (!date || Number.isNaN(date.valueOf()) || date.toISOString().slice(0, 19) !== value.slice(0, 19)) {
    throw new TypeError(`${label} must be an ISO date`);
  }
  return value;
};

export function validateCatalog(input, { now } = {}) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) {
    throw new TypeError('catalog must be an object');
  }
  if (input.schemaVersion !== 1) {
    throw new TypeError('catalog schemaVersion must be 1');
  }
  if (!Array.isArray(input.projects) || input.projects.length === 0) {
    throw new TypeError('catalog must contain at least one project');
  }
  requireIsoDate(input.updatedAt, 'catalog updatedAt');

  const ids = new Set();
  for (const project of input.projects) {
    if (ids.has(project?.id)) {
      throw new TypeError(`duplicate project id: ${project.id}`);
    }
    ids.add(project?.id);
  }

  const catalog = structuredClone(input);
  for (const project of catalog.projects) {
    if (!project || typeof project !== 'object' || Array.isArray(project)) {
      throw new TypeError('project must be an object');
    }
    requireText(project.id, 'project id', TEXT_LIMITS.id, /^[a-z0-9]+(?:-[a-z0-9]+)*$/);
    requireText(project.title, 'project title', TEXT_LIMITS.title);
    requireText(project.summary, 'project summary', TEXT_LIMITS.summary);
    if (project.history !== undefined) {
      requireText(project.history, 'project history', TEXT_LIMITS.history);
    }
    if (project.repo !== undefined) {
      requireText(project.repo, 'project repo', TEXT_LIMITS.repo, /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/);
      if (!PUBLIC_REPOS.has(project.repo)) {
        throw new TypeError('project repo must be an approved public repository');
      }
    }
    if (project.version !== undefined) {
      requireText(project.version, 'project version', TEXT_LIMITS.version);
    }
    if (project.language !== undefined) {
      requireText(project.language, 'project language', TEXT_LIMITS.language);
    }
    if (project.release !== undefined) {
      requireText(project.release, 'project release', TEXT_LIMITS.release);
    }
    if (project.linkHealth !== undefined) {
      requireText(project.linkHealth, 'project linkHealth', TEXT_LIMITS.linkHealth);
    }
    if (project.topics !== undefined) {
      if (!Array.isArray(project.topics) || project.topics.length > 20) {
        throw new TypeError('project topics must be an array with at most 20 entries');
      }
      for (const topic of project.topics) {
        requireText(topic, 'project topic', TEXT_LIMITS.topic, /^[a-z0-9]+(?:-[a-z0-9]+)*$/);
      }
    }
    if (project.shots !== undefined) {
      requireShots(project.shots);
    }
    if (project.lastActiveAt !== undefined) {
      requireIsoDate(project.lastActiveAt, 'project lastActiveAt');
    }
    if (project.updatedAt !== undefined) {
      requireIsoDate(project.updatedAt, 'project updatedAt');
    }
    requireText(project.status, 'project status', TEXT_LIMITS.status);
    requireText(project.art, 'project art', TEXT_LIMITS.art, /^[a-z0-9]+(?:-[a-z0-9]+)*$/);
    if (!Array.isArray(project.platforms) || project.platforms.length === 0 || project.platforms.length > 12) {
      throw new TypeError('project platforms must contain between 1 and 12 entries');
    }
    for (const platform of project.platforms) {
      requireText(platform, 'project platform', TEXT_LIMITS.platform);
    }
    if (typeof project.featured !== 'boolean') {
      throw new TypeError('project featured must be a boolean');
    }
    const category = typeof project?.category === 'string'
      ? project.category.trim().toLowerCase()
      : '';
    const layout = typeof project?.layout === 'string'
      ? project.layout.trim().toLowerCase()
      : '';
    if (!CATEGORIES.has(category)) throw new TypeError(`unknown category: ${project?.category}`);
    if (!LAYOUTS.has(layout)) throw new TypeError(`unknown layout: ${project?.layout}`);
    if (project.action !== undefined && !isSafeAction(project.action)) {
      throw new TypeError(
        'project action must be a local path or HTTPS URL; external actions require a public HTTPS URL',
      );
    }
    project.category = category;
    project.layout = layout;
  }

  void now;
  return catalog;
}

export function serializePublicCatalog(catalog) {
  const sanitized = {
    schemaVersion: catalog.schemaVersion,
    updatedAt: catalog.updatedAt,
    projects: catalog.projects.map((project) => Object.fromEntries(
      Object.entries(project)
        .filter(([key, value]) => SAFE_PROJECT_KEYS.has(key) && value !== undefined),
    )),
  };
  return validateCatalog(sanitized);
}

export function mergeCatalog(base, remote) {
  const baseCatalog = validateCatalog(base);
  const remoteCatalog = validateCatalog(remote);
  const remoteById = new Map(remoteCatalog.projects.map((project) => [project.id, project]));
  const baseIds = new Set(baseCatalog.projects.map(({ id }) => id));
  const projects = baseCatalog.projects.map((project) => (
    remoteById.has(project.id) ? { ...project, ...remoteById.get(project.id) } : project
  ));

  for (const project of remoteCatalog.projects) {
    if (!baseIds.has(project.id)) projects.push(project);
  }

  return validateCatalog({
    schemaVersion: remoteCatalog.schemaVersion,
    updatedAt: remoteCatalog.updatedAt,
    projects,
  });
}

export function projectFromPublicRepo(repo) {
  if (
    !repo
    || typeof repo !== 'object'
    || Array.isArray(repo)
    || repo.private !== false
    || !Array.isArray(repo.topics)
    || !repo.topics.includes('prismet-project')
  ) return null;

  const id = githubRepoIdentity(repo.name, repo.html_url);
  if (!id) return null;
  if (repo.description != null && typeof repo.description !== 'string') return null;
  if (repo.language != null && typeof repo.language !== 'string') return null;

  const project = {
    id,
    title: repo.name,
    summary: repo.description?.trim() || 'A public project.',
    category: 'tools',
    status: repo.archived ? 'Archive' : 'Live',
    platforms: ['Public repository'],
    art: 'generic',
    layout: 'standard',
    featured: false,
    action: repo.html_url,
    topics: [...repo.topics],
  };
  if (repo.language?.trim()) project.language = repo.language.trim();

  try {
    return validateCatalog({
      schemaVersion: 1,
      updatedAt: '1970-01-01T00:00:00.000Z',
      projects: [project],
    }).projects[0];
  } catch {
    return null;
  }
}
