import { createServer } from 'node:http';
import { readFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { isIP } from 'node:net';
import { extname, normalize, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { serializePublicCatalog, validateCatalog } from './project-catalog.js';
import { createProjectFeed } from './project-feed.js';
import { readCurrentDailyWordCache, sanitizeDailyWordPayload } from './wordle-daily.js';
import { createSignaling } from './signaling.js';
import { createEditApi } from './edit-api.js';
import { createArcadeApi } from './arcade-api.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PUBLIC_ROOT = resolve(__dirname, 'public');
// The site itself is a static build (showcase/prismet-site in sagedeutschle/Bayzyl),
// served ahead of public/, which keeps /steam, /debt, /shots and their assets.
const SITE_ROOT = resolve(__dirname, 'site');
const STATIC_ROOTS = [SITE_ROOT, PUBLIC_ROOT];
readFileSync(resolve(SITE_ROOT, 'index.html')); // refuse to start without the site build
const BUNDLED_CATALOG = validateCatalog(JSON.parse(
  readFileSync(resolve(__dirname, 'data/projects.json'), 'utf8'),
));
const PORT = Number(process.env.PORT || 8080);
const KEY = process.env.STEAM_WEB_API_KEY || '';
const WORDLE_URL = process.env.WORDLE_DAILY_URL
  || 'https://cmufcjysgbiqhohozkrf.supabase.co/storage/v1/object/public/kaleidoscope-public/wordle/daily.json';
const CC = 'us';
const API = 'https://api.steampowered.com';
const STORE = 'https://store.steampowered.com/api/appdetails';

const REQUEST_TIMEOUT_MS = Math.max(2_000, Number(process.env.REQUEST_TIMEOUT_MS || 8_000));
const WORDLE_TIMEOUT_MS = Math.max(1_000, Number(process.env.WORDLE_TIMEOUT_MS || 6_000));
const WORDLE_CACHE_MS = Math.max(10_000, Number(process.env.WORDLE_CACHE_MS || 5 * 60 * 1000));
const MAX_QUERY_LENGTH = 128;
const MAX_GAME_NAME_LENGTH = 64;
const MAX_STEAM_GAMES = 500;
const MAX_STEAM_CACHE_ENTRIES = 512;
const MAX_RATE_BUCKETS = 10_000;

const ALLOWED_ORIGINS = new Set([
  'https://prismet.xyz',
  'https://www.prismet.xyz',
]);

const ALLOWED_HOSTS = new Set([
  'localhost',
  '127.0.0.1',
  'prismet.xyz',
  'www.prismet.xyz',
  'steamcdn-a.akamaihd.net',
  'avatars.steamstatic.com',
  'api.steampowered.com',
  'store.steampowered.com',
  'api.fiscaldata.treasury.gov',
]);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.json': 'application/json',
  '.ico': 'image/x-icon',
  '.webmanifest': 'application/manifest+json',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
  '.woff2': 'font/woff2',
};

const SECURITY_HEADERS = {
  'content-security-policy': "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; img-src 'self' data: https:; connect-src 'self' https://api.steampowered.com https://store.steampowered.com https://api.fiscaldata.treasury.gov https://api.usaspending.gov; form-action 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none';",
  'strict-transport-security': 'max-age=31536000; includeSubDomains',
  'referrer-policy': 'strict-origin-when-cross-origin',
  'x-content-type-options': 'nosniff',
  'x-frame-options': 'DENY',
  'cross-origin-resource-policy': 'same-site',
  'permissions-policy': 'camera=(), microphone=(), geolocation=()',
};

const DEFAULT_CACHE_CONTROL = 'public, max-age=300';
const VERSIONED_CACHE_CONTROL = 'public, max-age=31536000, immutable';   // assets the site build addresses as ?v=<content hash>
// Screenshot files are content-addressed by slug and replaced rather than edited,
// so they can be cached far longer than the pages that reference them.
const SHOTS_ROOT = resolve(PUBLIC_ROOT, 'shots');
const SHOTS_CACHE_CONTROL = 'public, max-age=604800, immutable';
const PROJECTS_CACHE_CONTROL = 'public, max-age=300';
const PROJECT_SOURCES = new Set(['bundled', 'remote', 'last-known-good']);
const PROJECT_UPSTREAMS = new Set(['projects', 'github']);
const PROJECT_ERROR_CODES = new Set([
  'http-error',
  'invalid-json',
  'timeout',
  'request-failed',
  'invalid-catalog',
  'invalid-response',
]);

export function createBoundedCache(maxEntries) {
  if (!Number.isInteger(maxEntries) || maxEntries < 1) {
    throw new TypeError('maxEntries must be a positive integer');
  }

  return new class extends Map {
    get(key) {
      if (!super.has(key)) return undefined;
      const value = super.get(key);
      super.delete(key);
      super.set(key, value);
      return value;
    }

    set(key, value) {
      if (super.has(key)) {
        super.delete(key);
      } else if (this.size >= maxEntries) {
        super.delete(this.keys().next().value);
      }
      return super.set(key, value);
    }
  }();
}

const rateBuckets = createBoundedCache(MAX_RATE_BUCKETS);
const editApi = createEditApi({ clientAddress, sendJSON });   // prismet.xyz/edit: PIN session + GitHub proxy (edit-api.js)
const RATE_LIMITS = {
  steam: { max: 12, windowMs: 60_000 },
  wordle: { max: 30, windowMs: 60_000 },
  debt: { max: 6, windowMs: 60_000 },
  arcade: { max: 90, windowMs: 60_000 },
  arcadeAuth: { max: 8, windowMs: 60_000 },
  static: { max: 600, windowMs: 60_000 },   // one visit is ~70 requests (fonts, plates, tiles); 75 cut real visitors off
  rtc: { max: 20, windowMs: 60_000 },
};
let cachedWordle = { expiresAt: 0, payload: null };

function securityHeaders(overrides = {}) {
  return {
    ...SECURITY_HEADERS,
    ...overrides,
  };
}

function sanitizeText(value, maxLength = 100) {
  return String(value ?? '')
    .replace(/[\u0000-\u001F\u007F-\u009F]/g, '')
    .trim()
    .slice(0, maxLength);
}

function sanitizeQuery(value, maxLength = MAX_QUERY_LENGTH) {
  return String(value ?? '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, maxLength);
}

function sanitizeHost(value, allowedHosts = ALLOWED_HOSTS) {
  if (!value || typeof value !== 'string') return null;
  try {
    const parsed = new URL(value);
    return allowedHosts.has(parsed.hostname) ? value : null;
  } catch {
    return null;
  }
}

function sanitizeHostUrl(value, allowedHosts = ALLOWED_HOSTS) {
  if (!value || typeof value !== 'string') return null;
  try {
    const parsed = new URL(value);
    return (allowedHosts.has(parsed.hostname) && parsed.protocol === 'https:') ? parsed.href : null;
  } catch {
    return null;
  }
}

function clamp(value, min = 0, max = Number.MAX_SAFE_INTEGER) {
  const n = Number(value);
  if (!Number.isFinite(n)) return null;
  if (n < min || n > max) return Math.min(Math.max(n, min), max);
  return n;
}

function sanitizeYear(value) {
  const year = clamp(value, 1970, new Date().getUTCFullYear() + 1);
  return year === null ? null : Math.round(year);
}

export function clientAddress(req) {
  const flyClientIp = req.headers['fly-client-ip'];
  if (typeof flyClientIp === 'string') {
    const candidate = flyClientIp.trim();
    if (isIP(candidate)) return candidate;
  }
  return sanitizeText(req.socket?.remoteAddress || 'unknown', 64);
}

function rateLimited(action, req, res) {
  const ip = clientAddress(req);
  const config = RATE_LIMITS[action] || RATE_LIMITS.static;
  const key = `${action}:${ip}`;
  const now = Date.now();
  const bucket = rateBuckets.get(key);

  if (!bucket || now - bucket.start >= config.windowMs) {
    rateBuckets.set(key, { start: now, count: 1 });
    return false;
  }
  if (bucket.count >= config.max) {
    return true;
  }
  bucket.count += 1;
  rateBuckets.set(key, bucket);
  return false;
}

function sendResponse(req, res, status, body = '', headers = {}) {
  const responseHeaders = {
    ...securityHeaders(),
    'cache-control': headers['cache-control'] || 'no-store',
    vary: 'accept-encoding',   // Fly's edge compresses per request; shared caches must key on the encoding
    ...headers,
  };
  res.writeHead(status, responseHeaders);
  if (req.method === 'HEAD') {
    res.end();
    return;
  }
  if (typeof body === 'string' || body instanceof Uint8Array) {
    res.end(body);
  } else {
    res.end();
  }
}

function sendJSON(req, res, status, payload, headers = {}) {
  sendResponse(req, res, status, JSON.stringify(payload), {
    'content-type': 'application/json; charset=utf-8',
    ...headers,
  });
}

function sendText(req, res, status, text, headers = {}) {
  sendResponse(req, res, status, text, {
    'content-type': 'text/plain; charset=utf-8',
    ...headers,
  });
}

function sendMethodNotAllowed(req, res) {
  sendJSON(req, res, 405, { ok: false, error: 'Method not allowed.' }, { allow: 'GET, HEAD, OPTIONS' });
}

function sanitizeProjectSnapshot(snapshot) {
  if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot)) {
    throw new TypeError('project feed returned an invalid snapshot');
  }

  const catalog = serializePublicCatalog(snapshot.catalog);
  const refreshed = typeof snapshot.refreshedAt === 'string'
    ? new Date(snapshot.refreshedAt)
    : null;
  const refreshedAt = refreshed && !Number.isNaN(refreshed.valueOf())
    ? refreshed.toISOString()
    : catalog.updatedAt;
  const source = PROJECT_SOURCES.has(snapshot.source) ? snapshot.source : 'bundled';
  const upstreamErrors = Array.isArray(snapshot.upstreamErrors)
    ? snapshot.upstreamErrors.flatMap((error) => {
      if (
        !error
        || typeof error !== 'object'
        || !PROJECT_UPSTREAMS.has(error.upstream)
        || !PROJECT_ERROR_CODES.has(error.code)
      ) return [];
      const safeError = { upstream: error.upstream, code: error.code };
      if (Number.isInteger(error.status) && error.status >= 400 && error.status <= 599) {
        safeError.status = error.status;
      }
      return [safeError];
    })
    : [];

  return {
    catalog,
    source,
    refreshedAt,
    stale: snapshot.stale === true,
    upstreamErrors,
  };
}

function setCorsIfAllowed(req, res) {
  const origin = sanitizeText(req.headers.origin || '', 256);
  if (!origin) return;
  if (ALLOWED_ORIGINS.has(origin)) {
    res.setHeader('access-control-allow-origin', origin);
    res.setHeader('vary', 'Origin');
  }
}

function safeStaticPath(urlPath, staticRoot) {
  let rel = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '');
  if (rel === 'steam') rel = 'steam.html';
  if (rel === 'debt') rel = 'debt.html';
  if (/^(arcade|tools)\/?$/.test(rel)) rel = `${rel.replace(/\/$/, '')}.html`;
  if (/^arcade\/(2048|minesweeper|lights-out|wordle|rubiks-cube|snake|sudoku|sliding-15|nonogram|chess|reversi|checkers|connect-four|gomoku|sea-battle|solitaire|spider|crazy-8|catan|brick-bench)\/?$/.test(rel)) rel = 'arcade/game.html';
  if (rel === 'edit' || rel === 'privacy' || rel === 'support' || rel === 'scam') rel = `${rel}.html`;   // pages shipped by the site build (site/)
  rel = rel.split('?')[0].split('#')[0];
  if (/%/i.test(rel)) {
    try {
      rel = decodeURIComponent(rel);
    } catch {
      return null;
    }
  }
  if (rel.includes('\\') || rel.includes('\0') || rel.includes('..')) return null;
  const normalized = normalize(rel);
  const file = resolve(staticRoot, normalized);
  const root = staticRoot.endsWith('/') ? staticRoot : `${staticRoot}/`;
  if (!file.startsWith(root)) return null;
  return file;
}

async function serveStatic(req, res, urlPath, versioned = false) {
  // The old site's capture folder (public/shots/) is retired: nothing links it and some
  // captures showed host names. The folder is removed from the image; refuse the path too.
  if (urlPath === '/shots' || urlPath.startsWith('/shots/')) {
    return sendResponse(req, res, 404, '<h1>404 — not found</h1><p><a href="/">prismet</a></p>', {
      'content-type': 'text/html; charset=utf-8',
    });
  }
  for (const staticRoot of STATIC_ROOTS) {
    const file = safeStaticPath(urlPath, staticRoot);
    if (!file) {
      return sendText(req, res, 403, 'forbidden');
    }
    let data;
    try {
      data = await readFile(file);
    } catch {
      continue;
    }
    return sendResponse(req, res, 200, data, {
      'content-type': file.endsWith('/.well-known/apple-app-site-association') ? 'application/json; charset=utf-8' : MIME[extname(file)] || 'application/octet-stream',
      'cache-control': versioned && file.startsWith(SITE_ROOT) ? VERSIONED_CACHE_CONTROL : file.startsWith(SHOTS_ROOT) ? SHOTS_CACHE_CONTROL : DEFAULT_CACHE_CONTROL,
    });
  }
  sendResponse(req, res, 404, '<h1>404 — not found</h1><p><a href="/">prismet</a></p>', {
    'content-type': 'text/html; charset=utf-8',
  });
}

async function fetchJSON(url, timeoutMs) {
  const timerLimit = Number(timeoutMs) || REQUEST_TIMEOUT_MS;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timerLimit);
  try {
    const r = await fetch(url, {
      headers: { 'user-agent': 'prismet.xyz' },
      signal: controller.signal,
    });
    if (r.status === 429) throw { code: 'rateLimited' };
    if (r.status === 401 || r.status === 403) throw { code: 'invalidKey' };
    if (!r.ok) throw { code: 'network' };
    return await r.json();
  } catch (error) {
    if (error?.name === 'AbortError') throw { code: 'timeout' };
    if (error?.code) throw error;
    throw { code: 'network' };
  } finally {
    clearTimeout(timer);
  }
}

function classify(raw) {
  const input = sanitizeQuery(raw, MAX_QUERY_LENGTH);
  if (!input) return null;
  let m = input.match(/\/profiles\/(7656119\d{10})/);
  if (m) return { id: m[1] };
  m = input.match(/\/id\/([^/?#]+)/);
  if (m) {
    const vanity = sanitizeText(decodeURIComponent(m[1]), 64).toLowerCase();
    return { vanity };
  }
  if (/^7656119\d{10}$/.test(input)) return { id: input };
  const handle = input.split(/[/?#]/)[0];
  return handle ? { vanity: sanitizeText(handle, 64) } : null;
}

async function resolveSteam(input) {
  const c = classify(input);
  if (!c) throw { code: 'empty' };
  if (c.id) return c.id;
  const j = await fetchJSON(`${API}/ISteamUser/ResolveVanityURL/v1/?key=${KEY}&vanityurl=${encodeURIComponent(c.vanity)}&url_type=1`);
  if (j.response?.success === 1 && j.response.steamid) return j.response.steamid;
  throw { code: 'notFound' };
}

const appDetailsCache = createBoundedCache(MAX_STEAM_CACHE_ENTRIES);
const globalCache = createBoundedCache(MAX_STEAM_CACHE_ENTRIES);

async function pool(items, limit, fn) {
  const out = [];
  let i = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (i < items.length) {
      const idx = i++;
      out[idx] = await fn(items[idx]);
    }
  });
  await Promise.all(workers);
  return out;
}

async function libraryPrices(appids) {
  const out = {};
  for (let i = 0; i < appids.length; i += 50) {
    const chunk = appids.slice(i, i + 50);
    try {
      const j = await fetchJSON(`${STORE}?appids=${chunk.join(',')}&filters=price_overview&cc=${CC}`);
      for (const [key, value] of Object.entries(j || {})) {
        const initial = value?.data?.price_overview?.initial;
        if (value?.success && typeof initial === 'number') out[key] = initial;
      }
    } catch {
      // Best effort: skip failed chunk pricing.
    }
  }
  return out;
}

async function appDetails(appid) {
  if (appDetailsCache.has(appid)) return appDetailsCache.get(appid);
  let d = null;
  try {
    const j = await fetchJSON(`${STORE}?appids=${appid}&cc=${CC}&l=english`);
    const env = j?.[appid];
    if (env?.success) d = env.data;
  } catch {
    // Best effort: fetch failures should not block other data.
  }
  appDetailsCache.set(appid, d);
  return d;
}

async function globalPercents(appid) {
  if (globalCache.has(appid)) return globalCache.get(appid);
  const map = {};
  try {
    const j = await fetchJSON(`${API}/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/?gameid=${appid}`);
    for (const a of j?.achievementpercentages?.achievements || []) {
      const pct = typeof a.percent === 'number' ? a.percent : parseFloat(a.percent);
      if (!Number.isNaN(pct)) map[a.name] = pct;
    }
  } catch {
    // Best effort.
  }
  globalCache.set(appid, map);
  return map;
}

async function playerAch(id, appid) {
  try {
    const j = await fetchJSON(`${API}/ISteamUserStats/GetPlayerAchievements/v1/?key=${KEY}&steamid=${id}&appid=${appid}`);
    const ps = j?.playerstats;
    if (!ps?.success || !ps.achievements?.length) return null;
    const unlocked = ps.achievements.filter((a) => a?.achieved === 1);
    return {
      total: ps.achievements.length,
      unlockedNames: unlocked.map((a) => sanitizeText(a.apiname, 64)),
    };
  } catch {
    return null;
  }
}

const yearOf = (s) => {
  const m = (s || '').match(/(19|20)\d{2}/);
  return m ? parseInt(m[0], 10) : null;
};
const initials = (name) => {
  const p = sanitizeText(name || '', 12).split(/[\s_-]+/).filter(Boolean).slice(0, 2).map((x) => x[0]).join('');
  return (p || sanitizeText(name || 'ST', 2).slice(0, 2)).toUpperCase();
};

async function buildSnapshot(input) {
  const id = await resolveSteam(input);
  const [sumJ, lvlJ, ownJ] = await Promise.all([
    fetchJSON(`${API}/ISteamUser/GetPlayerSummaries/v2/?key=${KEY}&steamids=${id}`),
    fetchJSON(`${API}/IPlayerService/GetSteamLevel/v1/?key=${KEY}&steamid=${id}`).catch(() => ({})),
    fetchJSON(`${API}/IPlayerService/GetOwnedGames/v1/?key=${KEY}&steamid=${id}&include_appinfo=1&include_played_free_games=1`),
  ]);
  const p = sumJ?.response?.players?.[0];
  const player = {
    personaName: sanitizeText(p?.personaname || 'Steam user', 50),
    avatarInitials: initials(p?.personaname || 'Steam user'),
    avatarUrl: sanitizeHostUrl(p?.avatarfull) || null,
    level: clamp(p?.level || lvlJ?.response?.player_level, 0, 1_000),
    memberSinceYear: sanitizeYear(p?.timecreated ? new Date(p.timecreated * 1000).getUTCFullYear() : null),
    gamesCount: 0,
  };
  const games = Array.isArray(ownJ?.response?.games) ? ownJ.response.games : [];
  if (!games.length) {
    return { ok: true, visibility: games ? 'public' : 'private', player, games: [] };
  }
  player.gamesCount = games.length;
  const byPlay = [...games]
    .filter((g) => g && Number.isFinite(Number(g.appid)))
    .sort((a, b) => (b.playtime_forever || 0) - (a.playtime_forever || 0))
    .slice(0, MAX_STEAM_GAMES);
  const storeTargets = byPlay.slice(0, 60);
  const achTargets = byPlay.filter((g) => g.has_community_visible_stats).slice(0, 30);

  const [prices, storeArr, achArr] = await Promise.all([
    libraryPrices(byPlay.map((g) => g.appid)),
    pool(storeTargets, 6, (g) => appDetails(g.appid).then((d) => [g.appid, d])),
    pool(achTargets, 6, async (g) => {
      const pa = await playerAch(id, g.appid);
      if (!pa) return [g.appid, null];
      const gp = await globalPercents(g.appid);
      const rare = pa.unlockedNames.map((n) => gp[n]).filter((x) => typeof x === 'number');
      return [g.appid, {
        pct: pa.total ? (pa.unlockedNames.length / pa.total * 100) : null,
        rare: rare.length ? Math.min(...rare) : null,
      }];
    }),
  ]);

  const store = Object.fromEntries(storeArr);
  const ach = Object.fromEntries(achArr);

  const out = byPlay.map((g) => {
    const d = store[g.appid];
    const a = ach[g.appid];
    let cents = prices[g.appid];
    if (cents == null && d && d.is_free !== true && d.price_overview) cents = d.price_overview.initial ?? d.price_overview.final;
    return {
      n: sanitizeText(g.name || `App ${g.appid}`, MAX_GAME_NAME_LENGTH),
      h: clamp(g.playtime_forever, 0, 10_000_000),
      w: clamp(g.playtime_2weeks || 0, 0, 10_000_000),
      p: cents != null ? cents / 100 : null,
      rev: clamp(d?.metacritic?.score, 0, 100),
      ap: clamp(a?.pct, 0, 100),
      rare: clamp(a?.rare, 0, 100),
      g: sanitizeText(d?.genres?.[0]?.description || '', MAX_GAME_NAME_LENGTH) || null,
      y: sanitizeYear(yearOf(d?.release_date?.date)),
      dk: clamp(g.playtime_deck_forever || 0, 0, 10_000_000),
      last: clamp(g.rtime_last_played ? Math.round((Date.now() / 1000 - g.rtime_last_played) / 86400) : null, 0, 3650),
    };
  }).slice(0, MAX_STEAM_GAMES);

  return {
    ok: true,
    visibility: 'public',
    player,
    games: out.map((game) => ({
      ...game,
      h: game.h == null ? null : Number((game.h / 60).toFixed(4)),
      w: game.w == null ? null : Number((game.w / 60).toFixed(4)),
      dk: game.dk == null ? null : Number((game.dk / 60).toFixed(4)),
      p: game.p == null ? null : Number(game.p.toFixed(2)),
      rev: game.rev == null ? null : Math.round(game.rev),
      ap: game.ap == null ? null : Number(game.ap.toFixed(2)),
      rare: game.rare == null ? null : Number(game.rare.toFixed(2)),
    })),
  };
}

async function getWordlePayload() {
  const cached = readCurrentDailyWordCache(cachedWordle);
  if (cached) return cached;
  const payload = await fetchJSON(WORDLE_URL, WORDLE_TIMEOUT_MS);
  const cleaned = sanitizeDailyWordPayload(payload);
  cachedWordle = {
    payload: cleaned,
    expiresAt: Date.now() + WORDLE_CACHE_MS,
  };
  return cleaned;
}

const ERR = {
  empty: 'Type a Steam ID, vanity name, or profile URL.',
  notFound: "Couldn't find that profile — check the ID or vanity URL.",
  privateProfile: 'That profile is private. Set Game details to Public in Steam privacy, then try again.',
  rateLimited: 'Steam is throttling requests — give it a minute and retry.',
  invalidKey: 'Steam key is invalid or has invalid permissions.',
  missingKey: 'The server is missing its Steam API key.',
  network: 'Could not reach Steam. Try again in a moment.',
  timeout: 'The request took too long. Please try again.',
  wordleFetch: 'Could not load today\'s daily word right now.',
  wordleFormat: 'The daily word payload is not valid.',
  wordleStale: 'Today\'s daily word is still refreshing.',
  badInput: 'Invalid request input.',
};

async function handleSteam(req, res, url) {
  if (!KEY) return sendJSON(req, res, 500, { ok: false, error: ERR.missingKey }, { 'cache-control': 'no-store' });
  const query = sanitizeQuery(url.searchParams.get('q') || '');
  if (!query) return sendJSON(req, res, 400, { ok: false, error: ERR.empty });
  if (query.length > MAX_QUERY_LENGTH) return sendJSON(req, res, 413, { ok: false, error: ERR.badInput });

  try {
    const snap = await buildSnapshot(query);
    sendJSON(req, res, 200, snap, { 'cache-control': 'public, max-age=600' });
  } catch (e) {
    sendJSON(req, res, 200, { ok: false, error: ERR[e?.code] || ERR.network });
  }
}

async function handleWordle(req, res) {
  if (rateLimited('wordle', req, res)) {
    sendJSON(req, res, 429, { ok: false, error: ERR.rateLimited });
    return;
  }
  try {
    const payload = await getWordlePayload();
    sendJSON(req, res, 200, payload, { 'cache-control': 'no-store' });
  } catch (e) {
    sendJSON(req, res, 502, { ok: false, error: ERR[e?.code] || ERR.wordleFetch });
  }
}

async function routeRequest(req, res, projectFeed, debtService, arcadeApi) {
  if (!req.url) return sendText(req, res, 400, 'Bad request.');
  setCorsIfAllowed(req, res);

  let url;
  try {
    url = new URL(req.url, 'http://localhost');
  } catch {
    sendText(req, res, 400, 'Bad request.');
    return;
  }
  const method = req.method?.toUpperCase() || 'GET';

  if (url.pathname.startsWith('/api/arcade/') && method !== 'OPTIONS') {
    if (await arcadeApi.handle(req, res, url, method)) return;
  }

  if (method === 'OPTIONS') {
    sendResponse(req, res, 204, '', {
      'access-control-allow-methods': 'GET, HEAD, OPTIONS',
      'access-control-allow-headers': 'content-type',
      'access-control-max-age': '600',
      allow: 'GET, HEAD, OPTIONS',
    });
    return;
  }
  if (url.pathname.startsWith('/api/edit/')) {
    if (await editApi.handle(req, res, url, method)) return;
  }
  if (method !== 'GET' && method !== 'HEAD') {
    sendMethodNotAllowed(req, res);
    return;
  }

  if (rateLimited('static', req, res)) {
    sendText(req, res, 429, 'Too many requests. Try again in a minute.', { 'retry-after': '60' });
    return;
  }

  if (url.pathname === '/healthz') {
    sendText(req, res, 200, 'ok', {
      'cache-control': 'no-store',
      'content-type': 'text/plain; charset=utf-8',
    });
    return;
  }

  if (url.pathname === '/api/projects') {
    try {
      const snapshot = sanitizeProjectSnapshot(await projectFeed.getSnapshot());
      sendJSON(req, res, 200, snapshot, { 'cache-control': PROJECTS_CACHE_CONTROL });
    } catch {
      sendJSON(req, res, 503, {
        catalog: serializePublicCatalog(BUNDLED_CATALOG),
        source: 'bundled',
        refreshedAt: BUNDLED_CATALOG.updatedAt,
        stale: true,
        upstreamErrors: [],
      }, { 'cache-control': 'no-store' });
    }
    return;
  }

  if (url.pathname === '/api/steam') {
    if (rateLimited('steam', req, res)) {
      sendJSON(req, res, 429, { ok: false, error: ERR.rateLimited });
      return;
    }
    return handleSteam(req, res, url);
  }
  if (url.pathname === '/api/wordle') {
    return handleWordle(req, res);
  }
  if (url.pathname === '/api/debt') {
    if (method === 'HEAD') return sendJSON(req, res, 200, {}, { 'cache-control': 'no-store' });
    if (rateLimited('debt', req, res)) {
      return sendJSON(req, res, 429, { error: 'Please wait before refreshing the dashboard.' }, { 'retry-after': '60', 'cache-control': 'no-store' });
    }
    try {
      const snapshot = await debtService.getSnapshot({ force: url.searchParams.get('refresh') === '1' || url.searchParams.get('force') === 'true' });
      return sendJSON(req, res, 200, snapshot, { 'cache-control': 'no-store' });
    } catch {
      return sendJSON(req, res, 503, { error: 'Economic data is temporarily unavailable. Please try again.' }, { 'cache-control': 'no-store' });
    }
  }
  if (url.pathname.startsWith('/api/')) {
    sendJSON(req, res, 404, { ok: false, error: 'Endpoint not found.' });
    return;
  }

  return serveStatic(req, res, url.pathname, /^[0-9a-f]{6,}$/.test(url.searchParams.get('v') || ''));
}

export function createPrismetServer({ feed, signaling, debt, arcade } = {}) {
  const projectFeed = feed ?? createProjectFeed({
    bundledCatalog: BUNDLED_CATALOG,
    projectsUrl: process.env.PROJECTS_URL || undefined,
    githubUser: process.env.GITHUB_USER || undefined,
    cacheMs: process.env.PROJECTS_CACHE_MS
      ? Number(process.env.PROJECTS_CACHE_MS)
      : undefined,
  });
  if (!projectFeed || typeof projectFeed.getSnapshot !== 'function') {
    throw new TypeError('feed must provide getSnapshot()');
  }

  const rtcSignaling = signaling ?? createSignaling({
    createBoundedCache,
    rateLimit: RATE_LIMITS.rtc,
    clientAddress,
  });

  let debtModule;
  const debtService = debt ?? { async getSnapshot(options) {
    debtModule ??= import('./debt-data.js').then(({ createDebtService }) => createDebtService());
    return (await debtModule).getSnapshot(options);
  } };
  const arcadeApi = arcade ?? createArcadeApi({ sendJSON, rateLimited });
  const server = createServer((req, res) => routeRequest(req, res, projectFeed, debtService, arcadeApi));

  // Stateless WebRTC signaling sibling route — /rtc — alongside the HTTP handling
  // above. No table, no row, no persisted game state (universal-room-system
  // design spec §5.4/§16.1); rooms live only in signaling.js's in-memory maps.
  server.on('upgrade', (req, socket, head) => {
    let pathname;
    try {
      pathname = new URL(req.url, 'http://localhost').pathname;
    } catch {
      socket.destroy();
      return;
    }
    if (pathname !== '/rtc') {
      socket.destroy();
      return;
    }
    rtcSignaling.handleUpgrade(req, socket, head);
  });
  server.on('close', () => rtcSignaling.close());
  server.rtcSignaling = rtcSignaling;

  return server;
}

const isMainModule = process.argv[1]
  && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isMainModule) {
  const server = createPrismetServer();
  server.listen(PORT, () => {
    console.log(`prismet listening on :${PORT} (steam key: ${KEY ? 'set' : 'MISSING'})`);
  });
}
