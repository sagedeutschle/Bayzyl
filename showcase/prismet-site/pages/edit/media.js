// media.js — the media library: the images on the site, and images added here that go up with the next publish.
//
// An added image is resized and re-encoded as WebP in the browser (which also drops camera metadata), named after its
// file, and kept in this browser until a publish carries it to showcase/assets/uploads/. The preview draws it at once.
// The draft only ever holds an image's path, so one image used in five places is one file.
import { h } from './panels.js';

const DB = 'prismet-edit', STORE = 'media', MAX_WIDTH = 1600, MAX_BYTES = 2 * 1024 * 1024;
const BLANK = 'data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==';
const VARIANT = /-(128|192|360|720|1080|1440|thumb)\.webp$/;      // width variants and register thumbnails of another image
const open = () => new Promise((res, rej) => { const r = indexedDB.open(DB, 1); r.onupgradeneeded = () => r.result.createObjectStore(STORE, { keyPath: 'path' }); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); });
const tx = async (mode, fn) => { try { const db = await open(); return await new Promise((res, rej) => { const t = db.transaction(STORE, mode), q = fn(t.objectStore(STORE)); t.oncomplete = () => res(q?.result); t.onerror = () => rej(t.error); }); } catch { return null; } };   // storage blocked: the image lives until the tab closes
const slug = (name) => name.toLowerCase().replace(/\.[a-z0-9]+$/, '').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40) || 'image';

export async function createMedia({ manifest, say }) {
  const files = manifest.files;
  const pending = new Map();                                    // path → { path, dataUrl, w, h, bytes, sent }
  for (const m of (await tx('readonly', (s) => s.getAll())) || []) { if (files[m.path]) tx('readwrite', (s) => s.delete(m.path)); else pending.set(m.path, m); }
  const missing = new Set();

  /** What lib/render.js asks about images: sizes, existence, the URL to draw. An image nobody has is drawn blank and remembered. */
  const assets = {
    size: (p) => { const m = pending.get(p), f = files[p]; return m ? { w: m.w, h: m.h } : f ? { w: f[0], h: f[1] } : { w: 0, h: 0 }; },
    has: (p) => pending.has(p) || p in files,
    url: (p) => { if (!p) return null; const m = pending.get(p); if (m) return m.dataUrl; const f = files[p]; if (!f) { missing.add(p); return BLANK; } return `${p}?v=${f[2]}`; },
  };
  // For the editor's own thumbnails: an image the site does not serve yet (in the repository, used by no page) is drawn blank.
  const src = (p) => pending.get(p)?.dataUrl || (files[p]?.[3] ? `${p}?v=${files[p][2]}` : BLANK);
  const list = () => [...[...pending.values()].sort((a, b) => b.at - a.at).map((m) => ({ path: m.path, w: m.w, h: m.h, bytes: m.bytes, pending: true })),
    ...Object.entries(files).filter(([p, f]) => f[3] && !VARIANT.test(p)).map(([p, f]) => ({ path: p, w: f[0], h: f[1] }))];

  async function add(file) {
    if (!/^image\//.test(file.type)) throw new Error(`${file.name} is not an image.`);
    const bmp = await createImageBitmap(file), scale = Math.min(1, MAX_WIDTH / bmp.width), w = Math.round(bmp.width * scale), hh = Math.round(bmp.height * scale);
    const c = document.createElement('canvas'); c.width = w; c.height = hh; c.getContext('2d').drawImage(bmp, 0, 0, w, hh);
    let blob = null;
    for (const q of [0.86, 0.7, 0.5]) { blob = await new Promise((r) => c.toBlob(r, 'image/webp', q)); if (blob && blob.size <= MAX_BYTES) break; }
    if (!blob || blob.type !== 'image/webp') throw new Error('This browser cannot write WebP images. Add images from Chrome, Edge or Firefox.');
    if (blob.size > MAX_BYTES) throw new Error(`${file.name} is still over 2 MB after resizing.`);
    const dataUrl = await new Promise((res, rej) => { const r = new FileReader(); r.onload = () => res(r.result); r.onerror = () => rej(r.error); r.readAsDataURL(blob); });
    const stem = slug(file.name); let name = stem, n = 2;
    while (pending.has(`assets/uploads/${name}.webp`) || files[`assets/uploads/${name}.webp`]) name = `${stem}-${n++}`;
    const m = { path: `assets/uploads/${name}.webp`, dataUrl, w, h: hh, bytes: blob.size, sent: false, at: Date.now() };
    pending.set(m.path, m); tx('readwrite', (s) => s.put(m));
    return m.path;
  }

  // ── the picker ────────────────────────────────────────────────────────────────────────────
  const box = document.getElementById('picker'), grid = document.getElementById('picker-grid'), find = document.getElementById('picker-find'), file = document.getElementById('picker-file');
  let onPick = null;
  const close = () => { box.hidden = true; onPick = null; };
  function draw() {
    const words = find.value.toLowerCase().split(/\s+/).filter(Boolean);
    const items = list().filter((m) => words.every((w) => m.path.toLowerCase().includes(w)));
    grid.replaceChildren(...items.map((m) => h('button', { class: `pick${m.pending ? ' new' : ''}`, type: 'button', title: `${m.path}\n${m.w} × ${m.h}${m.bytes ? ` · ${Math.round(m.bytes / 1024)} KB · goes up with the next publish` : ''}`,
      onclick: () => { const fn = onPick; close(); if (fn) fn(m.path); else say(m.path); } },
    h('img', { src: src(m.path), alt: '', loading: 'lazy', width: 120, height: 80 }), h('span', {}, m.path.split('/').pop().replace(/\.webp$/, '')), m.pending ? h('small', {}, 'new') : null)));
    if (!items.length) grid.append(h('p', { class: 'note' }, 'No image matches. Add one with the button above, or drop files here.'));
  }
  async function take(list_) {
    let last = null;
    for (const f of list_) { try { last = await add(f); } catch (err) { say(err.message, 'warn'); } }
    draw();
    if (last && onPick && list_.length === 1) { const fn = onPick; close(); fn(last); }      // one file added while choosing: that is the choice
  }
  find.addEventListener('input', draw);
  file.addEventListener('change', () => { take([...file.files]); file.value = ''; });
  document.getElementById('picker-add').addEventListener('click', () => file.click());
  box.addEventListener('click', (e) => { if (e.target === box) close(); });
  box.addEventListener('keydown', (e) => { if (e.key === 'Escape') { e.stopPropagation(); close(); } });
  box.addEventListener('dragover', (e) => { e.preventDefault(); });
  box.addEventListener('drop', (e) => { e.preventDefault(); take([...e.dataTransfer.files]); });

  return {
    assets, missing, src, list, add,
    /** Opens the library. With onPick the click chooses an image for a field; without, it only browses. */
    pick(fn = null) { onPick = fn; box.hidden = false; find.value = ''; draw(); find.focus(); },
    /** The new images the draft uses, for the publish: [{ path, base64 }] with paths as they are in the repository. */
    toPublish(docs) { const text = JSON.stringify(docs); return [...pending.values()].filter((m) => !m.sent && text.includes(m.path)).map((m) => ({ path: `showcase/${m.path}`, base64: m.dataUrl.slice(m.dataUrl.indexOf(',') + 1) })); },
    /** After a publish: these are on their way to the site; they stay drawable here until the site has them. */
    sent(paths) { for (const p of paths) { const m = pending.get(p.replace(/^showcase\//, '')); if (m) { m.sent = true; tx('readwrite', (s) => s.put(m)); } } },
  };
}
