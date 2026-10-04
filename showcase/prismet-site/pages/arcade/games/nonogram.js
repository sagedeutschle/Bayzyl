import { generator } from '../engines.js';
import { NONOGRAMS } from './puzzle-data.js';
import { array,int,same } from './puzzle-common.js';
export const clues=line=>{const out=[];let n=0;for(const bit of [...line,false])if(bit)n++;else if(n){out.push(n);n=0;}return out.length?out:[0];};
const engine={
 initial(seed='1',{level}={}){const p=NONOGRAMS.find(p=>p.id===level)||NONOGRAMS[generator(seed).next(NONOGRAMS.length)];return {level:p.id,size:p.size,solution:p.art.join('').split('').map(x=>x!=='.'&&x!==' '),marks:Array(p.size*p.size).fill(0),moves:0};},
 apply(s,a){if(a?.type==='level')return engine.initial('1',{level:a.value});if(a?.type==='next'){const i=NONOGRAMS.findIndex(p=>p.id===s.level);return engine.initial('1',{level:NONOGRAMS[(i+1)%NONOGRAMS.length].id});}if(a?.type!=='cycle'||!int(a.index,0,s.marks.length-1))return s;const marks=[...s.marks];marks[a.index]=(marks[a.index]+1)%3;return {...s,marks,moves:s.moves+1};},
 status:s=>s.marks.every((m,i)=>(m===1)===s.solution[i])?'won':'playing',
 validate(s){const level=NONOGRAMS.find(p=>p.id===s?.level);return !!level&&s.size===level.size&&same(s.solution,level.art.join('').split('').map(x=>x!=='.'&&x!==' '))&&array(s.marks,s.size*s.size,x=>int(x,0,2))&&int(s.moves,0,1e9);},
 view(s){const rows=Array.from({length:s.size},(_,r)=>clues(s.solution.slice(r*s.size,(r+1)*s.size))),columns=Array.from({length:s.size},(_,c)=>clues(Array.from({length:s.size},(_,r)=>s.solution[r*s.size+c])));return {kind:'grid',columns:s.size,rowClues:rows,columnClues:columns,cells:s.marks.map((m,i)=>({text:m===1?'■':m===2?'×':'',label:`Row ${Math.floor(i/s.size)+1}, column ${i%s.size+1}, ${['empty','filled','crossed'][m]}`,tone:m===1?'purple':'empty',action:{type:'cycle',index:i}})),stats:[{label:'Picture',value:NONOGRAMS.find(p=>p.id===s.level).name},{label:'Moves',value:s.moves}],message:engine.status(s)==='won'?'Picture revealed. Try another from the native collection.':'Numbers give runs of filled squares. Leave at least one empty square between runs. Click to cycle fill, cross, empty.',controls:[{label:'Next picture',action:{type:'next'}}]};},
};export default engine;
