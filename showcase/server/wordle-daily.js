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
