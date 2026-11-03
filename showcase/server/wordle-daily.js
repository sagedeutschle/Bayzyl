function sanitizeText(value, maxLength) {
  return String(value ?? '')
    .replace(/[\u0000-\u001F\u007F-\u009F]/g, '')
    .trim()
    .slice(0, maxLength);
}

export function dateLabelInTimeZone(date = new Date(), timeZone = 'America/New_York') {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date);
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  return `${values.year}-${values.month}-${values.day}`;
}

export function sanitizeDailyWordPayload(payload, expectedDate = dateLabelInTimeZone()) {
  if (!payload || typeof payload !== 'object') throw { code: 'wordleFormat' };
  const answer = sanitizeText(payload.answer || '', 12).toLowerCase().replace(/[^a-z]/g, '');
  if (answer.length !== 5) throw { code: 'wordleFormat' };
  const date = sanitizeText(payload.date || '', 20);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw { code: 'wordleFormat' };
  if (date !== expectedDate) {
    throw Object.assign(new Error('stale daily word'), { code: 'wordleStale' });
  }
  const sourceName = sanitizeText(payload.sourceName || '', 32);
  return sourceName ? { answer, date, sourceName } : { answer, date };
}

export function readCurrentDailyWordCache(cache, now = new Date()) {
  if (!cache?.payload || now.getTime() >= cache.expiresAt) return null;
  return cache.payload.date === dateLabelInTimeZone(now) ? cache.payload : null;
}

// A puzzle releases at the player's local midnight. At most three calendar
// dates are active anywhere between UTC-12 and UTC+14 at a given instant.
// Missing dates preserve the existing New York contract for older clients.
export function resolveDailyWordDate(requestedDate, now = new Date()) {
  if (requestedDate === undefined || requestedDate === null) return dateLabelInTimeZone(now);
  if (typeof requestedDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(requestedDate)) {
    throw Object.assign(new Error('invalid daily date'), { code: 'wordleDate' });
  }
  const parsed = new Date(`${requestedDate}T00:00:00Z`);
  const firstActive = new Date(now.getTime() - 12 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const lastActive = new Date(now.getTime() + 14 * 60 * 60 * 1000).toISOString().slice(0, 10);
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== requestedDate
      || requestedDate < firstActive || requestedDate > lastActive) {
    throw Object.assign(new Error('inactive daily date'), { code: 'wordleDate' });
  }
  return requestedDate;
}

function boundedDuration(value, fallback, minimum, maximum) {
  return Number.isFinite(Number(value))
    ? Math.max(minimum, Math.min(maximum, Number(value)))
    : fallback;
}

export function createDailyWordService({
  fetchImpl = globalThis.fetch,
  now = () => new Date(),
  timeoutMs = 6000,
  cacheMs = 5 * 60 * 1000,
} = {}) {
  const requestTimeout = boundedDuration(timeoutMs, 6000, 1, 10_000);
  const cacheDuration = boundedDuration(cacheMs, 5 * 60 * 1000, 1000, 5 * 60 * 1000);
  const maxPayloadBytes = 32 * 1024;
  const cache = new Map();
  const pending = new Map();

  async function fetchReleasedWord(date) {
    const controller = new AbortController();
    let timer;
    let reader;
    const unavailable = () => Object.assign(new Error('daily upstream unavailable'), { code: 'wordleFetch' });
    const timeout = new Promise((_resolve, reject) => {
      timer = setTimeout(() => {
        controller.abort();
        if (reader) void reader.cancel().catch(() => {});
        reject(unavailable());
      }, requestTimeout);
    });
    const request = (async () => {
      const response = await fetchImpl(`https://www.nytimes.com/svc/wordle/v2/${date}.json`, {
        headers: { 'user-agent': 'prismet.xyz', accept: 'application/json' },
        redirect: 'error',
        cache: 'no-store',
        signal: controller.signal,
      });
      if (!response.ok || Number(response.headers.get('content-length')) > maxPayloadBytes) throw unavailable();
      reader = response.body?.getReader();
      if (!reader) throw unavailable();
      const chunks = [];
      let size = 0;
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > maxPayloadBytes) throw unavailable();
        chunks.push(value);
      }
      const payload = JSON.parse(Buffer.concat(chunks, size).toString('utf8'));
      if (!payload || typeof payload.solution !== 'string' || !/^[a-z]{5}$/i.test(payload.solution)
          || typeof payload.print_date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(payload.print_date)) {
        throw Object.assign(new Error('invalid released daily word'), { code: 'wordleFormat' });
      }
      if (payload.print_date !== date) throw Object.assign(new Error('wrong daily date'), { code: 'wordleStale' });
      return Object.freeze({ answer: payload.solution.toLowerCase(), date, sourceName: 'Daily' });
    })();
    try {
      return await Promise.race([request, timeout]);
    } catch (error) {
      if (['wordleFormat', 'wordleStale'].includes(error?.code)) throw error;
      throw unavailable();
    } finally {
      clearTimeout(timer);
      controller.abort();
      if (reader) void reader.cancel().catch(() => {});
    }
  }

  const resolveDate = requestedDate => resolveDailyWordDate(requestedDate, now());
  return {
    resolveDate,
    async getDailyWord(requestedDate) {
      const date = resolveDate(requestedDate);
      const cached = cache.get(date);
      if (cached && now().getTime() < cached.expiresAt) return cached.payload;
      if (pending.has(date)) return pending.get(date);
      if (pending.size >= 3) throw Object.assign(new Error('daily fetch capacity'), { code: 'wordleFetch' });
      const request = fetchReleasedWord(date).then(payload => {
        cache.delete(date);
        if (cache.size >= 3) cache.delete(cache.keys().next().value);
        cache.set(date, { payload, expiresAt: now().getTime() + cacheDuration });
        return payload;
      }).finally(() => pending.delete(date));
      pending.set(date, request);
      return request;
    },
  };
}
