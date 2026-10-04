import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import snake from '../../prismet-site/pages/arcade/games/snake.js';
import sliding from '../../prismet-site/pages/arcade/games/sliding-15.js';
import sudoku from '../../prismet-site/pages/arcade/games/sudoku.js';
import nonogram from '../../prismet-site/pages/arcade/games/nonogram.js';
import cube from '../../prismet-site/pages/arcade/games/rubiks-cube.js';
import word, { evaluate } from '../../prismet-site/pages/arcade/games/wordle.js';
import { SUDOKU, NONOGRAMS } from '../../prismet-site/pages/arcade/games/puzzle-data.js';

const native=JSON.parse(readFileSync(new URL('./fixtures/puzzles/native-parity.json',import.meta.url)));
for(const fixture of native){
  assert.deepEqual(sliding.initial(fixture.seed).tiles,fixture.sliding,'Actual native sliding shuffle');
  assert.deepEqual(JSON.parse(JSON.stringify(cube.initial(fixture.seed).cubies)),fixture.cube,'Actual native cubie positions and orientations');
  let sn=snake.initial(fixture.seed);sn=snake.apply(sn,{type:'turn',direction:'up'});sn=snake.apply(sn,{type:'start'});sn=snake.apply(sn,{type:'tick'});
  assert.deepEqual(sn.body,fixture.snake,'Actual native buffered snake turn');
}

function solutionCount(givens){
 const grid=[...givens];let found=0;
 function search(){
  if(found>=2)return;let cell=-1,choices=[];
  for(let i=0;i<81;i++)if(!grid[i]){
   const row=Math.floor(i/9),col=i%9,used=new Set();
   for(let j=0;j<81;j++)if(grid[j]&&(Math.floor(j/9)===row||j%9===col||Math.floor(j/27)===Math.floor(row/3)&&Math.floor((j%9)/3)===Math.floor(col/3)))used.add(grid[j]);
   const values=[1,2,3,4,5,6,7,8,9].filter(v=>!used.has(v));if(!values.length)return;
   if(cell===-1||values.length<choices.length){cell=i;choices=values;}
  }
  if(cell===-1){found++;return;}for(const v of choices){grid[cell]=v;search();grid[cell]=0;if(found>=2)return;}
 }
 search();return found;
}
for(const puzzle of Object.values(SUDOKU).flat())assert.equal(solutionCount(puzzle.givens),1,'Every copied native Sudoku has exactly one solution');

for (const engine of [snake, sliding, sudoku, nonogram, cube, word]) {
  const start = engine.initial('42');
  assert.equal(engine.validate(start), true);
  assert.deepEqual(start, engine.initial('42'), 'Seeded starts reproduce');
  assert.equal(engine.validate(null), false);
  assert.equal(engine.validate({}), false);
  const before = JSON.stringify(start);
  engine.apply(start, { type: 'unknown' });
  assert.equal(JSON.stringify(start), before, 'Actions preserve input state');
  assert.equal(typeof engine.view(start).message, 'string');
}
let sn = snake.initial('1');
assert.equal(snake.validate({...sn,direction:'__proto__'}),false);
assert.deepEqual(snake.apply(sn,{type:'turn',direction:'constructor'}),sn);
assert.equal(sudoku.validate({...sudoku.initial('1'),difficulty:'constructor'}),false);
assert.equal(sudoku.initial('1',{difficulty:'__proto__'}).difficulty,'easy');
sn = snake.apply(sn, { type: 'turn', direction: 'left' });
assert.equal(sn.pendingDirection, null, 'Cannot reverse committed heading');
sn = snake.apply(sn, { type: 'turn', direction: 'up' });
sn = snake.apply(sn, { type: 'turn', direction: 'left' });
assert.equal(sn.pendingDirection, 'up', 'Buffered turns cannot sneak a reversal');
sn = snake.apply(sn, { type: 'start' });
sn = snake.apply(sn, { type: 'tick' });
assert.equal(sn.body[0], 6 * 14 + 6);
const paused = snake.apply(sn, { type: 'pause' });
assert.deepEqual(snake.apply(paused, { type: 'tick' }), paused);
for (let i = 0; i < 30; i++) sn = snake.apply(sn, { type: 'tick' });
assert.equal(snake.status(sn), 'lost');
assert.equal(snake.validate(sn), true);
const solved = { tiles: Array.from({ length: 16 }, (_, i) => (i + 1) % 16), moves: 0 };
assert.equal(sliding.status(solved), 'won');
assert.equal(sliding.validate({ ...solved, tiles: [2, 1, ...solved.tiles.slice(2)] }), false, 'Unsolvable imported permutation rejected');
assert.equal(sliding.status(sliding.initial('42')), 'playing');
assert.deepEqual(sliding.apply(solved, { type: 'move', index: 0 }), solved);
let almost = sliding.apply(solved, { type: 'move', index: 14 });
assert.equal(sliding.status(sliding.apply(almost, { type: 'move', index: 15 })), 'won');
assert.equal(Object.values(SUDOKU).flat().length, 12);
for (const difficulty of ['easy', 'medium', 'hard']) {
  let state = sudoku.initial('42', { difficulty });
  const given = state.puzzle.findIndex(Boolean), empty = state.puzzle.indexOf(0);
  assert.deepEqual(sudoku.apply(state, { type: 'value', index: given, value: 2 }), state);
  state = sudoku.apply(state, { type: 'select', index: empty });
  state = sudoku.apply(state, { type: 'notes' });
  state = sudoku.apply(state, { type: 'value', value: 3 });
  assert.deepEqual(state.notes[empty], [3]);
  assert.equal(state.entries[empty], 0);
  assert.equal(sudoku.status({ ...state, entries: [...state.solution] }), 'won');
  assert.equal(sudoku.validate({ ...state, solution: Array(81).fill(1) }), false);
}
assert.equal(NONOGRAMS.length, 12);
for (const level of NONOGRAMS) {
  const state = nonogram.initial('1', { level: level.id });
  assert.equal(nonogram.validate(state), true);
  assert.equal(nonogram.status({ ...state, marks: state.solution.map(v => v ? 1 : 2) }), 'won');
  assert.equal(state.solution.filter(Boolean).length, nonogram.view(state).rowClues.flat().reduce((a,b)=>a+b,0));
}
let cu = cube.initial('1', { solved: true });
const originalCube = structuredClone(cu.cubies);
for (let n = 0; n < 4; n++) cu = cube.apply(cu, { type: 'turn', move: 'R' });
assert.deepEqual(cu.cubies, originalCube, 'Four quarter turns restore cube');
for (let n = 0; n < 6; n++) for (const move of ['R','U',"R'","U'"]) cu = cube.apply(cu, { type: 'turn', move });
assert.deepEqual(cu.cubies, originalCube, 'Native cube order-six relation');
assert.equal(cube.status(cube.initial('42')), 'playing');
assert.equal(cube.validate({ ...cu, cubies: Array(26).fill(cu.cubies[0]) }), false);
const wo = word.initial('1', { answer: 'apple' });
assert.deepEqual(evaluate('apple','alley'), ['correct','present','absent','present','absent'], 'Duplicate guesses consume answer letters once');
assert.equal(word.status(word.apply(wo, { type: 'guess', value: 'apple' })), 'won');
assert.equal(word.apply(wo, { type: 'guess', value: 'zzzzz' }).rows.length, 0);
assert.equal(word.validate({ ...wo, answer: '<html>' }), false);
console.log('✓ puzzle-games: six native rule ports, bundled data, terminal states, immutable actions and invalid-save rejection');
