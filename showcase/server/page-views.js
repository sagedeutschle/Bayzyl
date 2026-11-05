import { createServer } from 'node:http';
import { readdirSync } from 'node:fs';
import { join } from 'node:path';

const host = /^(?:www\.)?prismet\.xyz(?::(?:80|443))?$/i;
const knownAutomation = /bot\b|crawler|spider|slurp|headlesschrome|lighthouse|facebookexternalhit|bytespider|uptimerobot|curl\/|wget\/|python-requests\/|go-http-client\//i;
const gamePaths = new Set([
  '2048', 'minesweeper', 'lights-out', 'wordle', 'rubiks-cube', 'snake', 'sudoku',
  'sliding-15', 'nonogram', 'chess', 'reversi', 'checkers', 'connect-four', 'gomoku',
  'sea-battle', 'solitaire', 'spider', 'crazy-8', 'catan', 'brick-bench',
].map(game => '/arcade/' + game));

export function isArcadeGamePath(pathname) {
  return gamePaths.has(pathname.replace(/\/$/, ''));
}

function publicRoutes(siteRoot) {
  const routes = new Map([['/', '/'], ['/index.html', '/']]);
  for (const page of ['/steam', '/debt', '/scam', '/arcade', '/tools', '/shorepact', '/bayzyl',
    '/support', '/privacy', '/shorepact/support', '/shorepact/privacy']) {
    routes.set(page, page);
    routes.set(page + '.html', page);
  }
  for (const page of [...gamePaths, '/colophon.html', '/prismet-arcade-android-privacy.html',
    '/prismet-arcade-android-support.html']) routes.set(page, page);

  // Only release-owned filenames become labels. Draft/editor paths and arbitrary request URLs never do.
  let entries;
  try {
    entries = readdirSync(join(siteRoot, 'work'), { withFileTypes: true });
  } catch {
    // A partial site build can still serve and count the fixed public routes.
    return routes;
  }
  const workPages = entries.filter(entry => entry.isFile() && /^[a-z0-9][a-z0-9-]{0,80}\.html$/.test(entry.name))
    .map(entry => '/work/' + entry.name).sort().slice(0, 128);
  for (const page of workPages) routes.set(page, page);
  return routes;
}

export function createPageViews(siteRoot) {
  const routes = publicRoutes(siteRoot);
  const counters = new Map([...new Set(routes.values())].sort().map(page => [page, 0]));
  const started = Math.floor(Date.now() / 1000);

  return {
    // Called only after reading an HTML file successfully, before sending its 200 response.
    observe(req, res, pathname) {
      const page = routes.get(pathname) ?? routes.get(pathname.replace(/\/$/, ''));
      if (!page || req.method !== 'GET' || !host.test(req.headers.host || '')) return;
      const headers = req.headers;
      if (!headers['user-agent'] || knownAutomation.test(headers['user-agent'])
        || /prefetch|prerender/i.test(`${headers['sec-purpose'] || ''} ${headers.purpose || ''}`)
        || headers.dnt === '1' || headers['sec-gpc'] === '1') return;

      res.once('finish', () => counters.set(page, counters.get(page) + 1));
    },
    exposition() {
      return [
        '# HELP prismet_page_views_collection_start_seconds Unix time at which this process began counting.',
        '# TYPE prismet_page_views_collection_start_seconds gauge',
        `prismet_page_views_collection_start_seconds ${started}`,
        '# HELP prismet_page_views_total Completed eligible HTML GET responses by public page.',
        '# TYPE prismet_page_views_total counter',
        ...Array.from(counters, ([page, count]) => `prismet_page_views_total{page="${page}"} ${count}`),
        '',
      ].join('\n');
    },
  };
}

export function createMetricsServer(pageViews) {
  return createServer((req, res) => {
    if (req.url !== '/metrics' || req.method !== 'GET') {
      res.writeHead(404).end();
      return;
    }
    res.writeHead(200, { 'content-type': 'text/plain; version=0.0.4; charset=utf-8', 'cache-control': 'no-store' });
    res.end(pageViews.exposition());
  });
}
