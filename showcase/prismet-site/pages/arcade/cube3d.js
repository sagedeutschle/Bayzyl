// The 3D cube scene: CSS 3D, driven by the engine's state. No rule lives here; the engine (games/rubiks-cube.js)
// decides what a move does and this file only shows it, the way the app's SceneKit view does: it keeps the state it
// has drawn, recognises an applied move by diffing the move history, plays turns one at a time on a pivot, snaps the
// affected cubies onto the exact model transforms when the turn ends, and snaps everything on a scramble, restore or
// import. The flat six-face net (built by the caller into `net`) carries the accessible state; the scene is decorative
// and pointer-driven: drag a sticker to turn its layer, drag beside the cube to orbit, arrow keys orbit.
import { cssMatrix, turnAngle, orbitMatrix, resolveDrag, movesBetween, shade, FACES } from './cube-math.js';
import { reduced } from './motion.js';

const DIRS = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];   // the engine's colour order
const TONES = ['red', 'orange', 'white', 'yellow', 'green', 'blue'];
// Where each face sits in a cubie's own frame, in CSS space (the model's +y is CSS −y).
const PLACE = ['rotateY(90deg)', 'rotateY(-90deg)', 'rotateX(90deg)', 'rotateX(-90deg)', '', 'rotateY(180deg)'];
const TURN_MS = 160, MAX_QUEUE = 6, HOME = [-28, -35];
const key = (home) => home.join(',');
const el = (tag, cls) => { const node = document.createElement(tag); if (cls) node.className = cls; return node; };
const worldNormal = (cubie, dir) => [0, 1, 2].map((i) => dir[0] * cubie.orientation[0][i] + dir[1] * cubie.orientation[1][i] + dir[2] * cubie.orientation[2][i]);

export function mountCube(board, state, { engine, onMove } = {}) {
  const stage = el('div', 'cube-stage'), space = el('div', 'cube-space'), cube = el('div', 'cube');
  stage.setAttribute('aria-hidden', 'true');
  space.append(cube); stage.append(space);
  const hint = el('p', 'cube-hint'); hint.textContent = 'Drag a sticker to turn its layer. Drag beside the cube to look around.';
  const flat = el('details', 'cube-flat'), summary = el('summary'), net = el('div', 'cube-net');
  summary.textContent = 'Flat view'; flat.append(summary, net);
  board.append(stage, hint, flat);

  const nodes = new Map(), faces = [];
  for (const cubie of state.cubies) {
    const node = el('div', 'cubie'); node.dataset.home = key(cubie.home);
    DIRS.forEach((dir, d) => {
      const face = el('div', 'cube-face');
      if (cubie.home[0] * dir[0] + cubie.home[1] * dir[1] + cubie.home[2] * dir[2] === 1) { face.classList.add('sticker', `tone-${TONES[d]}`); face.dataset.dir = d; }
      face.style.transform = `${PLACE[d]} translateZ(calc(var(--s) / 2))`;
      node.append(face); faces.push({ face, home: node.dataset.home, dir });
    });
    cube.append(node); nodes.set(node.dataset.home, node);
  }

  let rendered = state, logical = state, queue = [], turning = false, pivot = null, step = 48, ax = HOME[0], ay = HOME[1], lightFrame = 0;
  const byHome = () => new Map(rendered.cubies.map((cubie) => [key(cubie.home), cubie]));
  function placeAll() { for (const cubie of rendered.cubies) nodes.get(key(cubie.home)).style.transform = cssMatrix(cubie, step); }
  function relight() {
    lightFrame = 0;
    const orbit = orbitMatrix(ax, ay), cubies = byHome();
    for (const { face, home, dir } of faces) face.style.setProperty('--dim', (1 - shade(worldNormal(cubies.get(home), dir), orbit)).toFixed(3));
  }
  const scheduleLight = () => { if (!lightFrame) lightFrame = requestAnimationFrame(relight); };
  function setOrbit() { cube.style.transform = `rotateX(${ax.toFixed(2)}deg) rotateY(${ay.toFixed(2)}deg)`; scheduleLight(); }
  function layout() {
    const width = stage.clientWidth || 320;
    step = Math.max(22, Math.round(width / 5.9));
    stage.style.setProperty('--s', `${Math.round(step * .92)}px`);
    placeAll();
  }
  function dissolve() {
    if (!pivot) return;
    for (const node of [...pivot.children]) cube.append(node);
    pivot.remove(); pivot = null;
  }
  function snap(target) {
    if (pivot) { pivot.style.transition = 'none'; dissolve(); }
    queue = []; turning = false; rendered = logical = target;
    placeAll(); relight();
  }
  function pump() {
    if (turning || !queue.length) return;
    const move = queue.shift();
    const after = engine?.apply(rendered, { type: 'turn', move });
    if (!after || after === rendered) { snap(logical); return; }
    if (reduced()) { rendered = after; placeAll(); relight(); pump(); return; }
    turning = true;
    const { axis, deg } = turnAngle(move), [a, layer] = FACES[move[0]], duration = Math.abs(deg) === 180 ? TURN_MS * 1.6 : TURN_MS;
    pivot = el('div', 'cube-pivot');
    pivot.style.transform = `rotate3d(${axis},0deg)`;
    cube.append(pivot);
    for (const cubie of rendered.cubies) if (cubie.position[a] === layer) pivot.append(nodes.get(key(cubie.home)));
    const mine = pivot;
    let timer = 0;
    const finish = (event) => {
      if (event && event.target !== mine) return;
      if (pivot !== mine) return;
      clearTimeout(timer); mine.removeEventListener('transitionend', finish);
      dissolve(); rendered = after; placeAll(); relight();
      turning = false; pump();
    };
    mine.addEventListener('transitionend', finish);
    timer = setTimeout(finish, duration + 120);
    requestAnimationFrame(() => requestAnimationFrame(() => { if (pivot !== mine) return; mine.style.transition = `transform ${duration}ms cubic-bezier(.3,.6,.2,1)`; mine.style.transform = `rotate3d(${axis},${deg}deg)`; }));
  }
  function update(next) {
    stage.classList.toggle('solved', engine ? engine.status(next) === 'won' : false);
    if (next === logical) return;
    const moves = movesBetween(logical.history, next.history);
    if (moves?.length && queue.length + moves.length <= MAX_QUEUE) { logical = next; queue.push(...moves); pump(); }
    else snap(next);
  }

  // Pointer: a sticker drag turns a layer; any other drag orbits.
  let drag = null;
  stage.addEventListener('pointerdown', (event) => {
    if (event.button) return;
    const face = event.target.closest('.cube-face.sticker');
    drag = { id: event.pointerId, x: event.clientX, y: event.clientY, face: face ? { home: face.parentNode.dataset.home, dir: DIRS[face.dataset.dir] } : null, done: false };
    stage.setPointerCapture(event.pointerId); stage.classList.add('grabbing');
    event.preventDefault(); board.focus?.({ preventScroll: true });
  });
  stage.addEventListener('pointermove', (event) => {
    if (!drag || event.pointerId !== drag.id) return;
    const dx = event.clientX - drag.x, dy = event.clientY - drag.y;
    if (drag.face) {
      if (drag.done) return;
      const cubie = logical.cubies.find((c) => key(c.home) === drag.face.home);
      const move = cubie && resolveDrag({ cubie, normal: worldNormal(cubie, drag.face.dir), drag: { x: dx, y: dy }, orbit: orbitMatrix(ax, ay) });
      if (move) { drag.done = true; onMove?.(move); }
      return;
    }
    ay += dx * .4; ax = Math.max(-85, Math.min(85, ax - dy * .4)); drag.x = event.clientX; drag.y = event.clientY; setOrbit();
  });
  const release = (event) => { if (drag && event.pointerId === drag.id) { drag = null; stage.classList.remove('grabbing'); } };
  stage.addEventListener('pointerup', release); stage.addEventListener('pointercancel', release);
  board.addEventListener('keydown', (event) => {
    if (event.altKey || event.ctrlKey || event.metaKey || event.target.closest('input,textarea,select,button,summary')) return;
    const turn = { ArrowLeft: [0, -15], ArrowRight: [0, 15], ArrowUp: [-15, 0], ArrowDown: [15, 0] }[event.key];
    if (turn) { ax = Math.max(-85, Math.min(85, ax + turn[0])); ay += turn[1]; }
    else if (event.key === 'Home') [ax, ay] = HOME;
    else return;
    event.preventDefault(); setOrbit();
  });

  layout(); setOrbit(); relight(); update(state);
  const observer = typeof ResizeObserver === 'function' ? new ResizeObserver(layout) : null;
  observer?.observe(stage);
  return { net, update, destroy() { observer?.disconnect(); stage.remove(); hint.remove(); flat.remove(); } };
}
