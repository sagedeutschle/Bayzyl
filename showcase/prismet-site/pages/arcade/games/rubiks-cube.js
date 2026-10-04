// Integer cubie rotations directly mirror Prismet's native RubiksCube model.
import { generator } from '../engines.js';
import { int,array,same } from './puzzle-common.js';
const FACE={U:[1,1],D:[1,-1],L:[0,-1],R:[0,1],F:[2,1],B:[2,-1]};
const COLORS=['red','orange','white','yellow','green','blue'];
const DIR=[[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]];
const identity=()=>[[1,0,0],[0,1,0],[0,0,1]];
const homes=[];for(let x=-1;x<=1;x++)for(let y=-1;y<=1;y++)for(let z=-1;z<=1;z++)if(x||y||z)homes.push([x,y,z]);
const vec=v=>array(v,3,x=>int(x,-1,1));
const dot=(a,b)=>a.reduce((n,x,i)=>n+x*b[i],0);
const mul=(m,v)=>[0,1,2].map(i=>m[0][i]*v[0]+m[1][i]*v[1]+m[2][i]*v[2]);
const rotate=(v,axis)=>axis===0?[v[0],-v[2],v[1]]:axis===1?[v[2],v[1],-v[0]]:[-v[1],v[0],v[2]];
const moves=Object.keys(FACE).flatMap(f=>[f,`${f}'`,`${f}2`]);
function turn(cubies,move){const [axis,layer]=FACE[move[0]],quarters=move.endsWith('2')?2:move.endsWith("'")?3:1;return cubies.map(c=>{if(c.position[axis]!==layer)return c;let position=c.position,orientation=c.orientation;for(let n=0;n<quarters;n++){position=rotate(position,axis);orientation=orientation.map(v=>rotate(v,axis));}return {...c,position,orientation};});}
function colour(c,d){const body=c.orientation.map(v=>dot(v,d));return DIR.findIndex(v=>same(v,body));}
const engine={
 initial(seed='1',{solved=false}={}){let cubies=homes.map(home=>({home:[...home],position:[...home],orientation:identity()}));const rng=generator(seed);if(!solved)for(let n=0;n<25;n++){const f=Object.keys(FACE)[rng.next(6)],q=rng.next(3)+1;cubies=turn(cubies,q===1?f:q===2?`${f}2`:`${f}'`);}const state={cubies,history:[]};if(!solved&&engine.status(state)==='won')state.cubies=turn(state.cubies,'R');return state;},
 apply(s,a){if(a?.type==='undo'&&s.history.length){const history=s.history.slice(0,-1),m=s.history.at(-1),inverse=m.endsWith('2')?m:m.endsWith("'")?m[0]:`${m}'`;return {cubies:turn(s.cubies,inverse),history};}if(a?.type!=='turn'||!moves.includes(a.move)||s.history.length>=2000)return s;return {cubies:turn(s.cubies,a.move),history:[...s.history,a.move]};},
 status(s){return DIR.every(d=>new Set(s.cubies.filter(c=>dot(c.position,d)===1).map(c=>colour(c,d))).size===1)?'won':'playing';},
 validate(s){if(!s||!array(s.cubies,26,c=>c&&vec(c.home)&&vec(c.position)&&array(c.orientation,3,vec))||!Array.isArray(s.history)||s.history.length>2000||!s.history.every(m=>moves.includes(m)))return false;const points=new Set();for(let i=0;i<26;i++){const c=s.cubies[i],m=c.orientation;if(!same(c.home,homes[i])||!same(mul(m,c.home),c.position))return false;for(let j=0;j<3;j++)for(let k=0;k<3;k++)if(dot(m[j],m[k])!==(j===k?1:0))return false;const cross=[m[0][1]*m[1][2]-m[0][2]*m[1][1],m[0][2]*m[1][0]-m[0][0]*m[1][2],m[0][0]*m[1][1]-m[0][1]*m[1][0]];if(dot(cross,m[2])!==1)return false;points.add(c.position.join(','));}return points.size===26;},
 view(s){const faces=Object.entries(FACE).map(([label,[axis,layer]])=>{const direction=[0,0,0];direction[axis]=layer;const cubies=s.cubies.filter(c=>c.position[axis]===layer);const coords=axis===1?[2,0]:axis===0?[1,2]:[1,0];cubies.sort((a,b)=>b.position[coords[0]]-a.position[coords[0]]||a.position[coords[1]]-b.position[coords[1]]);return {label,cells:cubies.map(c=>{const color=COLORS[colour(c,direction)];return {text:'',label:`${label} face, ${color} sticker`,tone:color};})};});return {kind:'cube',faces,stats:[{label:'Turns',value:s.history.length}],message:engine.status(s)==='won'?'Six faces, six colors. Solved.':'Turn a face clockwise, prime (inverse), or twice. All six faces remain visible.',controls:[...moves.map(move=>({label:move,action:{type:'turn',move}})),{label:'Undo turn',action:{type:'undo'},disabled:!s.history.length}]};},
};export default engine;
