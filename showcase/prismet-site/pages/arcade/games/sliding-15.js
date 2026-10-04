import { generator } from '../engines.js';
import { array,int } from './puzzle-common.js';
const solved=Array.from({length:16},(_,i)=>(i+1)%16);
const adjacent=(a,b)=>Math.abs(Math.floor(a/4)-Math.floor(b/4))+Math.abs(a%4-b%4)===1;
const legal=t=>{const i=t.indexOf(0),r=Math.floor(i/4),c=i%4;return [r>0?i-4:null,r<3?i+4:null,c>0?i-1:null,c<3?i+1:null].filter(x=>x!==null);};
export function solvable(tiles) { const nums=tiles.filter(Boolean);let inv=0;for(let i=0;i<15;i++)for(let j=i+1;j<15;j++)if(nums[i]>nums[j])inv++;return (inv+4-Math.floor(tiles.indexOf(0)/4))%2===1; }
const engine={
  initial(seed='1') { const tiles=[...solved],rng=generator(seed);for(let n=0;n<80;n++){const choices=legal(tiles),j=choices[rng.next(choices.length)],i=tiles.indexOf(0);[tiles[i],tiles[j]]=[tiles[j],tiles[i]];}if(tiles.every((x,i)=>x===solved[i]))[tiles[14],tiles[15]]=[tiles[15],tiles[14]];return {tiles,moves:0}; },
  apply(s,a) { let target=a?.index;const i=s.tiles.indexOf(0);if(a?.type==='direction')target={up:i-4,down:i+4,left:i-1,right:i+1}[a.direction];else if(a?.type!=='move')return s;if(!int(target,0,15)||!adjacent(i,target))return s;const tiles=[...s.tiles];[tiles[i],tiles[target]]=[tiles[target],tiles[i]];return {tiles,moves:s.moves+1}; },
  status:s=>s.tiles.every((x,i)=>x===solved[i])?'won':'playing',
  validate:s=>!!s&&array(s.tiles,16,x=>int(x,0,15))&&new Set(s.tiles).size===16&&solvable(s.tiles)&&int(s.moves,0,1e9),
  keymap:key=>({ArrowUp:{type:'direction',direction:'up'},ArrowDown:{type:'direction',direction:'down'},ArrowLeft:{type:'direction',direction:'left'},ArrowRight:{type:'direction',direction:'right'}})[key],
  view(s){const blank=s.tiles.indexOf(0);return {kind:'grid',columns:4,cells:s.tiles.map((n,i)=>({text:n?String(n):'',label:n?`Tile ${n}`:'Empty square',tone:n?'gold':'empty',disabled:!n||!adjacent(i,blank),action:{type:'move',index:i}})),stats:[{label:'Moves',value:s.moves}],message:engine.status(s)==='won'?'In order. Beautiful.':'Slide a neighboring tile into the empty square. Arrange 1 through 15.'};},
};export default engine;
