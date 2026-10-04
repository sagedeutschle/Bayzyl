// Rules and timing ported from the native SnakeGame; render state is browser-only.
import { generator } from '../engines.js';
import { int, uint64, restoredRNG } from './puzzle-common.js';
const DIR = { up: [-1,0], down: [1,0], left: [0,-1], right: [0,1] };
const directionOK = value => typeof value === 'string' && Object.hasOwn(DIR,value);
const opposite = (a,b) => DIR[a][0]+DIR[b][0] === 0 && DIR[a][1]+DIR[b][1] === 0;
const engine = {
  initial(seed='1') { return { width:14, height:14, body:[104,103,102], direction:'right', pendingDirection:null, apple:80, score:0, phase:'playing', running:false, rngState:generator(seed).state }; },
  apply(s,a) {
    if (!a || s.phase !== 'playing') return s;
    if (a.type === 'turn' && directionOK(a.direction) && a.direction !== s.direction && !opposite(a.direction,s.direction)) return {...s,pendingDirection:a.direction};
    if (a.type === 'start' || a.type === 'pause') return {...s,running:a.type==='start'};
    if (a.type !== 'tick' || !s.running) return s;
    const direction = s.pendingDirection || s.direction, [dr,dc] = DIR[direction];
    const r = Math.floor(s.body[0]/s.width)+dr, c=s.body[0]%s.width+dc, next=r*s.width+c;
    const eat = next === s.apple, occupied = eat ? s.body : s.body.slice(0,-1);
    if (r<0 || r>=s.height || c<0 || c>=s.width || occupied.includes(next)) return {...s,direction,pendingDirection:null,phase:'lost',running:false};
    const body = [next,...s.body]; if (!eat) body.pop();
    const state = {...s,body,direction,pendingDirection:null};
    if (eat) {
      state.score++;
      const empty = Array.from({length:s.width*s.height},(_,i)=>i).filter(i=>!body.includes(i));
      if (!empty.length) return {...state,apple:null,phase:'won',running:false};
      const rng=restoredRNG(s.rngState); state.apple=empty[rng.next(empty.length)];state.rngState=rng.state;
    }
    return state;
  },
  status:s=>s.phase,
  tickMs:s=>s.running ? Math.max(110,320-s.score*12) : 0,
  keymap:key=>({ArrowUp:{type:'turn',direction:'up'},ArrowDown:{type:'turn',direction:'down'},ArrowLeft:{type:'turn',direction:'left'},ArrowRight:{type:'turn',direction:'right'}})[key],
  validate(s) {
    if (!s || s.width!==14 || s.height!==14 || !Array.isArray(s.body) || s.body.length<3 || s.body.length>196 || !s.body.every(x=>int(x,0,195)) || new Set(s.body).size!==s.body.length || !directionOK(s.direction) || !(s.pendingDirection===null || directionOK(s.pendingDirection) && !opposite(s.pendingDirection,s.direction)) || !int(s.score,0,193) || s.score!==s.body.length-3 || !['playing','lost','won'].includes(s.phase) || typeof s.running!=='boolean' || !uint64(s.rngState)) return false;
    if (!s.body.every((x,i)=>i===0 || Math.abs(Math.floor(x/14)-Math.floor(s.body[i-1]/14))+Math.abs(x%14-s.body[i-1]%14)===1)) return false;
    return s.phase==='won' ? s.body.length===196 && s.apple===null && !s.running : int(s.apple,0,195) && !s.body.includes(s.apple) && (s.phase!=='lost'||!s.running);
  },
  view(s) { return { kind:'grid',columns:14,cells:Array.from({length:196},(_,i)=>({text:i===s.apple?'●':i===s.body[0]?'◆':s.body.includes(i)?'■':'',label:`Row ${Math.floor(i/14)+1}, column ${i%14+1}${i===s.apple?', apple':s.body.includes(i)?', snake':''}`,tone:i===s.apple?'red':s.body.includes(i)?'green':'empty',disabled:true})),stats:[{label:'Apples',value:s.score},{label:'Length',value:s.body.length}],message:s.phase==='lost'?'The trail ends here. Start a new game to try again.':s.phase==='won'?'Every square is yours.':s.running?'Arrow keys or the direction controls. Avoid walls and your own trail.':'Press Start when you are ready.',controls:[{label:s.running?'Pause':'Start',action:{type:s.running?'pause':'start'},disabled:s.phase!=='playing'},...Object.keys(DIR).map(direction=>({label:direction[0].toUpperCase()+direction.slice(1),action:{type:'turn',direction},disabled:s.phase!=='playing'}))]}; },
};
export default engine;
