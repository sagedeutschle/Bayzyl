import {
  mergeCatalog,
  projectFromPublicRepo,
  serializePublicCatalog,
  validateCatalog,
} from './project-catalog.js';

const DEFAULT_CACHE_MS = 1_800_000;
const DEFAULT_TIMEOUT_MS = 8_000;
const GITHUB_ACCEPT = 'application/vnd.github+json';
const GITHUB_USER_AGENT = 'prismet-site-project-feed/1.0';

class UpstreamError extends Error {
  constructor(code, status) {
    super(code);
    this.code = code;
    this.status = status;
  }
}

const clone = (value) => structuredClone(value);

const asDuration = (value, fallback, label) => {
  const duration = value ?? fallback;
  if (!Number.isFinite(duration) || duration < 0) {
    throw new TypeError(`${label} must be a non-negative finite number`);
  }
  return duration;
};

const asClock = (now) => {
  if (typeof now !== 'function') throw new TypeError('now must be a function');
  return () => {
    const value = now();
    const milliseconds = value instanceof Date
      ? value.valueOf()
      : typeof value === 'string'
        ? Date.parse(value)
        : value;
    if (!Number.isFinite(milliseconds)) throw new TypeError('now must return a valid time');
    return milliseconds;
  };
};

const toUpstreamError = (upstream, error) => {
  const entry = {
    upstream,
    code: error instanceof UpstreamError ? error.code : 'request-failed',
  };
  if (error instanceof UpstreamError && Number.isInteger(error.status)) {
    entry.status = error.status;
  }
  return entry;
};

async function fetchJson(fetchImpl, url, { headers, timeoutMs }) {
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  timer.unref?.();

  try {
    const response = await fetchImpl(url, { headers, signal: controller.signal });
    if (!response || response.ok !== true) {
      const status = Number.isInteger(response?.status) ? response.status : undefined;
      throw new UpstreamError('http-error', status);
    }
    if (typeof response.json !== 'function') throw new UpstreamError('invalid-json');
    try {
      return await response.json();
    } catch {
      throw new UpstreamError('invalid-json');
    }
  } catch (error) {
    if (timedOut) throw new UpstreamError('timeout');
    if (error instanceof UpstreamError) throw error;
    throw new UpstreamError('request-failed');
  } finally {
    clearTimeout(timer);
  }
}

const githubReposUrl = (githubUser) => (
  `https://api.github.com/users/${encodeURIComponent(githubUser)}`
  + '/repos?per_page=100&sort=pushed&direction=desc'
);

const normalizedRepo = (repo, githubUser) => {
  if (
    !repo
    || typeof repo !== 'object'
    || Array.isArray(repo)
    || repo.private !== false
    || typeof repo.name !== 'string'
  ) return null;

  const fallbackName = `${githubUser}/${repo.name}`;
  const fullName = typeof repo.full_name === 'string' ? repo.full_name : fallbackName;
  const pieces = fullName.split('/');
  if (
    pieces.length !== 2
    || pieces[0].toLowerCase() !== githubUser.toLowerCase()
    || pieces[1].toLowerCase() !== repo.name.toLowerCase()
  ) return null;

  const canonicalUrl = `https://github.com/${pieces.map(encodeURIComponent).join('/')}`;
  const normalized = {
    ...repo,
    full_name: fullName,
    html_url: repo.html_url ?? canonicalUrl,
    archived: repo.archived === true,
  };
  normalized.html_url = matchingRepositoryUrl(normalized) ?? canonicalUrl;
  return normalized;
};

const safeTopics = (topics) => (
  Array.isArray(topics)
  && topics.length <= 20
  && topics.every((topic) => (
    typeof topic === 'string'
    && topic.length <= 50
    && /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(topic)
  ))
);

const safeText = (value, maxLength) => (
  typeof value === 'string'
  && value.trim().length > 0
  && value.trim().length <= maxLength
  && !/[\u0000-\u001f\u007f]/.test(value)
);

const isoDate = (value) => {
  if (typeof value !== 'string') return null;
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return null;
  return date.toISOString();
};

const matchingRepositoryUrl = (repo) => {
  try {
    const url = new URL(repo.html_url);
    const path = url.pathname.split('/').filter(Boolean).map(decodeURIComponent);
    if (
      url.protocol !== 'https:'
      || url.hostname.toLowerCase() !== 'github.com'
      || url.username
      || url.password
      || url.search
      || url.hash
      || path.length !== 2
      || path.join('/').toLowerCase() !== repo.full_name.toLowerCase()
    ) return null;
    return url.href;
  } catch {
    return null;
  }
};

const safeHomepage = (homepage) => {
  if (typeof homepage !== 'string' || homepage !== homepage.trim()) return null;
  try {
    const url = new URL(homepage);
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : null;
  } catch {
    return null;
  }
};

const applyRepoFacts = (project, repo, catalogUpdatedAt) => {
  const updates = {};
  if (safeText(repo.language, 64)) updates.language = repo.language.trim();
  if (safeTopics(repo.topics)) updates.topics = [...repo.topics];
  const lastActiveAt = isoDate(repo.pushed_at);
  if (lastActiveAt) updates.lastActiveAt = lastActiveAt;
  if (repo.archived === true) updates.status = 'Archive';

  if (project.action === undefined) {
    updates.action = safeHomepage(repo.homepage) ?? matchingRepositoryUrl(repo) ?? undefined;
  }

  try {
    return validateCatalog({
      schemaVersion: 1,
      updatedAt: catalogUpdatedAt,
      projects: [{ ...project, ...updates }],
    }).projects[0];
  } catch {
    return project;
  }
};

const overlayGithub = (catalog, repos, githubUser) => {
  if (!Array.isArray(repos)) throw new UpstreamError('invalid-response');

  const projects = catalog.projects.map((project) => clone(project));
  const explicitRepos = new Map();
  projects.forEach((project, index) => {
    if (typeof project.repo === 'string') explicitRepos.set(project.repo.toLowerCase(), index);
  });
  const ids = new Set(projects.map(({ id }) => id));

  for (const input of repos) {
    const repo = normalizedRepo(input, githubUser);
    if (!repo) continue;

    const explicitIndex = explicitRepos.get(repo.full_name.toLowerCase());
    if (explicitIndex !== undefined) {
      projects[explicitIndex] = applyRepoFacts(
        projects[explicitIndex],
        repo,
        catalog.updatedAt,
      );
      continue;
    }

    const discovered = projectFromPublicRepo(repo);
    if (!discovered || ids.has(discovered.id)) continue;
    const withFacts = applyRepoFacts(discovered, repo, catalog.updatedAt);
    const homepage = safeHomepage(repo.homepage);
    if (homepage) {
      const withHomepage = applyRepoFacts({ ...withFacts, action: undefined }, { ...repo, homepage }, catalog.updatedAt);
      withFacts.action = withHomepage.action;
    }
    projects.push(withFacts);
    ids.add(withFacts.id);
  }

  return serializePublicCatalog({ ...catalog, projects });
};

export function createProjectFeed({
  bundledCatalog,
  projectsUrl,
  githubUser,
  cacheMs = DEFAULT_CACHE_MS,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  fetchImpl = globalThis.fetch,
  now = Date.now,
} = {}) {
  if (typeof fetchImpl !== 'function') throw new TypeError('fetchImpl must be a function');
  if (projectsUrl !== undefined && (typeof projectsUrl !== 'string' || !projectsUrl)) {
    throw new TypeError('projectsUrl must be a non-empty string');
  }
  if (
    githubUser !== undefined
    && (typeof githubUser !== 'string' || !/^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$/.test(githubUser))
  ) throw new TypeError('githubUser must be a valid GitHub user');

  const bundled = serializePublicCatalog(validateCatalog(bundledCatalog));
  const cacheDuration = asDuration(cacheMs, DEFAULT_CACHE_MS, 'cacheMs');
  const requestTimeout = asDuration(timeoutMs, DEFAULT_TIMEOUT_MS, 'timeoutMs');
  const clock = asClock(now);

  let cachedSnapshot = null;
  let cacheExpiresAt = 0;
  let lastGoodCatalog = null;
  let lastGoodRefreshedAt = null;
  let refreshInFlight = null;

  const refresh = async (attemptedAt) => {
    const refreshedAt = new Date(attemptedAt).toISOString();
    const upstreamErrors = [];
    let source = 'bundled';
    let finalCatalog = clone(bundled);

    if (projectsUrl) {
      try {
        const input = await fetchJson(fetchImpl, projectsUrl, {
          headers: { Accept: 'application/json' },
          timeoutMs: requestTimeout,
        });
        let remote;
        try {
          remote = validateCatalog(input);
          finalCatalog = serializePublicCatalog(mergeCatalog(bundled, remote));
        } catch {
          throw new UpstreamError('invalid-catalog');
        }
        source = 'remote';
      } catch (error) {
        upstreamErrors.push(toUpstreamError('projects', error));
        return {
          catalog: clone(lastGoodCatalog ?? bundled),
          source: lastGoodCatalog ? 'last-known-good' : 'bundled',
          refreshedAt: lastGoodRefreshedAt ?? refreshedAt,
          stale: true,
          upstreamErrors,
        };
      }
    }

    if (githubUser) {
      try {
        const repos = await fetchJson(fetchImpl, githubReposUrl(githubUser), {
          headers: {
            Accept: GITHUB_ACCEPT,
            'User-Agent': GITHUB_USER_AGENT,
          },
          timeoutMs: requestTimeout,
        });
        finalCatalog = overlayGithub(finalCatalog, repos, githubUser);
      } catch (error) {
        upstreamErrors.push(toUpstreamError('github', error));
        if (lastGoodCatalog) {
          return {
            catalog: clone(lastGoodCatalog),
            source: 'last-known-good',
            refreshedAt: lastGoodRefreshedAt,
            stale: true,
            upstreamErrors,
          };
        }
      }
    }

    finalCatalog = serializePublicCatalog(finalCatalog);
    if (upstreamErrors.length === 0) {
      lastGoodCatalog = clone(finalCatalog);
      lastGoodRefreshedAt = refreshedAt;
    }
    return {
      catalog: finalCatalog,
      source,
      refreshedAt,
      stale: upstreamErrors.length > 0,
      upstreamErrors,
    };
  };

  const getSnapshot = async (options = {}) => {
    const force = options?.force === true;
    const currentTime = clock();
    if (!force && cachedSnapshot && currentTime < cacheExpiresAt) {
      return clone(cachedSnapshot);
    }

    if (!refreshInFlight) {
      refreshInFlight = refresh(currentTime).then((snapshot) => {
        cachedSnapshot = clone(snapshot);
        cacheExpiresAt = currentTime + cacheDuration;
        return snapshot;
      });
    }

    try {
      return clone(await refreshInFlight);
    } finally {
      refreshInFlight = null;
    }
  };

  const getCatalog = async (options) => (await getSnapshot(options)).catalog;
  return { getCatalog, getSnapshot };
}
