import { validateSave, validDate } from './saves.js';
export function cloudClient(fetchImpl = fetch) {
  async function request(path, body, method = 'GET') {
    const response = await fetchImpl(`/api/arcade/${path}`, { method, credentials: 'same-origin', redirect: 'error', headers: body ? { 'content-type': 'application/json' } : {}, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(12000) });
    let value; try { let text;
      if (response.body?.getReader) {
        const reader = response.body.getReader(), chunks = []; let size = 0;
        try { while (true) { const item = await reader.read(); if (item.done) break; size += item.value.byteLength; if (size > 71680) { await reader.cancel(); throw new Error('Response too large'); } chunks.push(item.value); } }
        finally { reader.releaseLock(); }
        const bytes = new Uint8Array(size); let offset = 0; for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; } text = new TextDecoder().decode(bytes);
      } else { text = await response.text(); if (new TextEncoder().encode(text).length > 71680) throw new Error('Response too large'); }
      value = JSON.parse(text); } catch { throw new Error('The cloud response could not be read. Your local game is unchanged.'); }
    if (!response.ok) { const error = new Error(value.error || 'Cloud saves are unavailable. Your local game is unchanged.'); error.status = response.status; throw error; }
    return value;
  }
  const pathFor = (save) => `progress/${encodeURIComponent(save.gameID)}?slot=${encodeURIComponent(save.mode)}`;
  const canonical = (value) => JSON.stringify(value, (_, item) => item && typeof item === 'object' && !Array.isArray(item) ? Object.fromEntries(Object.keys(item).sort().map((name) => [name, item[name]])) : item);
  const validTimestamp = (text) => typeof text === 'string' && /^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,6})?(?:Z|\+00:00)$/.test(text) && validDate(text.slice(0, 10)) && Number.isFinite(Date.parse(text));
  function record(value, save) {
    if (!value || !Number.isSafeInteger(value.revision) || value.revision < 0 || (value.envelope !== null && (!validateSave(value.envelope) || value.envelope.gameID !== save.gameID || value.envelope.mode !== save.mode)) || (value.envelope === null ? value.revision !== 0 || value.updatedAt !== null : value.revision < 1 || !validTimestamp(value.updatedAt))) throw new Error('The cloud save could not be validated. Your local game is unchanged.');
    return value;
  }
  return { config: () => request('config'), session: () => request('auth'), auth: (body) => request('auth', body, 'POST'), load: async (save) => record(await request(pathFor(save)), save), save: async (save, expectedRevision) => { if (!validateSave(save) || !Number.isSafeInteger(expectedRevision) || expectedRevision < 0) throw new Error('Invalid save'); const confirmed = record(await request(pathFor(save), { envelope: save, expectedRevision }, 'PUT'), save); if (confirmed.revision !== expectedRevision + 1 || canonical(confirmed.envelope) !== canonical(save)) throw new Error('The cloud save was not confirmed. Your local game is unchanged.'); return confirmed; } };
}
export async function mountCloud(panel, arcade, client = cloudClient()) {
  const status = panel.querySelector('[data-cloud-status]'), form = panel.querySelector('form'), signed = panel.querySelector('[data-cloud-signed]');
  const revisions = new Map(); let signedIn = false, busy = false;
  const say = (text) => { status.textContent = text; };
  const update = () => { form.hidden = signedIn; signed.hidden = !signedIn; };
  async function perform(fn) { if (busy) return; busy = true; panel.setAttribute('aria-busy', 'true'); for (const b of panel.querySelectorAll('button')) b.disabled = true; try { await fn(); } catch (error) { say(error.message || 'Could not reach cloud saves. Local play is unchanged.'); if (error.status === 401) { signedIn = false; update(); } } finally { busy = false; panel.removeAttribute('aria-busy'); for (const b of panel.querySelectorAll('button')) b.disabled = false; } }
  try { const config = await client.config(); if (!config.syncAvailable) { say('Cloud saves are not enabled here. Local saves and file transfers still work.'); return; } const session = await client.session(); signedIn = session.signedIn === true; update(); say(signedIn ? 'Signed in. Save and load only when you choose.' : 'Sign in or create an account to transfer progress between devices. Nothing uploads automatically.'); }
  catch { say('Cloud saves could not be reached. Local saves and file transfers still work.'); return; }
  form.addEventListener('submit', (event) => { event.preventDefault(); const action = event.submitter?.value === 'signup' ? 'signup' : 'signin'; void perform(async () => { const credentials = { action, email: form.elements.email.value.trim(), password: form.elements.password.value }; if (action === 'signup' && credentials.password.length < 12) throw new Error('Choose a password with at least 12 characters.'); try { const result = await client.auth(credentials); signedIn = result.signedIn === true; revisions.clear(); update(); say(result.confirmationRequired ? 'Account created. Confirm your email, then sign in here. No game data has been uploaded.' : signedIn ? 'Signed in. Your local game is unchanged.' : 'Sign-in was not confirmed.'); } finally { form.elements.password.value = ''; } }); });
  panel.querySelector('[data-cloud-signout]').addEventListener('click', () => void perform(async () => { await client.auth({ action: 'signout' }); signedIn = false; revisions.clear(); update(); say('Signed out. Your local game is still here.'); }));
  panel.querySelector('[data-cloud-load]').addEventListener('click', () => void perform(async () => { if (arcade.isRoomActive()) throw new Error('Leave the cooperative room before loading personal progress.'); const current = arcade.snapshot(), record = await client.load(current); if (!record.envelope) { revisions.set(`${current.gameID}:${current.mode}`, 0); say('There is no cloud save for this game and mode yet.'); return; } const imported = await arcade.importSave(record.envelope); if (imported) revisions.set(`${current.gameID}:${current.mode}`, record.revision); say(imported ? `Cloud save loaded (revision ${record.revision}).` : 'Load canceled. Your local game is unchanged.'); }));
  panel.querySelector('[data-cloud-save]').addEventListener('click', () => void perform(async () => {
    if (arcade.isRoomActive()) throw new Error('Leave the cooperative room before saving personal progress.');
    const current = arcade.snapshot(), id = `${current.gameID}:${current.mode}`;
    if (!revisions.has(id)) { const remote = await client.load(current); if (remote.envelope && !confirm(`Replace the cloud ${current.mode} save (${remote.envelope.moves} moves, saved ${remote.envelope.savedAt}) with this local board? Load it first if you want to keep playing that version.`)) { say('Cloud save canceled. Neither board changed.'); return; } revisions.set(id, remote.revision); }
    try { const record = await client.save(current, revisions.get(id)); revisions.set(id, record.revision); say(`Saved to your account (revision ${record.revision}).`); }
    catch (error) { if (error.status === 409) { revisions.delete(id); say('Another device saved a newer version. Nothing was overwritten. Load the cloud save to review it, or keep playing locally.'); return; } throw error; }
  }));
}
