// Rules and evaluation ported from Prismet ConnectFourGame.swift / ConnectFourAI.swift.
// Shared deterministic primitives are also used by the other board ports.
export const inside=(r,c,n,m=n)=>r>=0&&r<n&&c>=0&&c<m;
export const indexOK=(i,n)=>Number.isInteger(i)&&i>=0&&i<n;
export const other=(p,a,b)=>p===a?b:a;
export const seedOK=s=>typeof s==='string'&&/^(0|[1-9]\d{0,19})$/.test(s)&&BigInt(s)<=18446744073709551615n;
export function rng(seed){let s=BigInt(seed)||0x9e3779b97f4a7c15n;return n=>{s=BigInt.asUintN(64,s*6364136223846793005n+1442695040888963407n);return Number(s%BigInt(n));};}
export function base(seed,options={}){if(!seedOK(seed))throw new Error('Expected UInt64 seed');return {seed,mode:options.mode==='bot'?'bot':'local',difficulty:['easy','normal','hard'].includes(options.difficulty)?options.difficulty:'normal',moves:0};}
export const baseOK=s=>!!s&&seedOK(s.seed)&&['local','bot'].includes(s.mode)&&['easy','normal','hard'].includes(s.difficulty)&&Number.isSafeInteger(s.moves)&&s.moves>=0&&s.moves<=100000;
export const directions=[[0,1],[1,0],[1,1],[1,-1]];
export function lineWinner(board,rows,cols,length){for(let r=0;r<rows;r++)for(let c=0;c<cols;c++){const p=board[r*cols+c];if(!p)continue;for(const[dr,dc]of directions)if(inside(r+dr*(length-1),c+dc*(length-1),rows,cols)&&Array.from({length},(_,k)=>board[(r+dr*k)*cols+c+dc*k]).every(x=>x===p))return p;}return null;}
export const searchBudget=()=>({remaining:8000});
export function search(s,depth,player,legal,move,evaluate,turn=s=>s.currentPlayer,alpha=-Infinity,beta=Infinity,budget=searchBudget()){
 if(budget.remaining<=0)return evaluate(s);
 budget.remaining--;
 if(depth<=0||budget.remaining<=0)return evaluate(s);
 const ms=legal(s);if(!ms.length)return evaluate(s);
 let value=turn(s)===player?-Infinity:Infinity;
 for(const m of ms){
  if(budget.remaining<=0)break;
  const v=search(move(s,m),depth-1,player,legal,move,evaluate,turn,alpha,beta,budget);
  if(turn(s)===player){value=Math.max(value,v);alpha=Math.max(alpha,value);}else{value=Math.min(value,v);beta=Math.min(beta,value);}
  if(alpha>=beta)break;
 }
 return value;
}
export function bestMove(s,depth,legal,move,evaluate){
 const p=s.currentPlayer,budget=searchBudget();let chosen=null,best=-Infinity;
 for(const m of legal(s)){
  if(budget.remaining<=0)break;
  const score=search(move(s,m),depth-1,p,legal,move,evaluate,n=>n.currentPlayer,-Infinity,Infinity,budget);
  if(score>best){best=score;chosen=m;}
 }
 return chosen;
}
export function controls(s,done=false){return [{label:'Local two-player',action:{type:'mode',mode:'local'},disabled:s.mode==='local'},{label:'Play with bot',action:{type:'mode',mode:'bot'},disabled:s.mode==='bot'},{label:'Bot move',action:{type:'bot'},disabled:done},...['easy','normal','hard'].map(d=>({label:`${d[0].toUpperCase()+d.slice(1)} bot`,action:{type:'difficulty',difficulty:d},disabled:s.difficulty===d}))];}
export function setting(s,a){if(a?.type==='mode'&&['local','bot'].includes(a.mode))return {...s,mode:a.mode};if(a?.type==='difficulty'&&['easy','normal','hard'].includes(a.difficulty))return {...s,difficulty:a.difficulty};return null;}
const initial=(seed,options)=>({...base(seed,options),board:Array(42).fill(null),currentPlayer:'red',winner:null});
const legal=s=>s.winner||s.board.every(Boolean)?[]:[3,2,4,1,5,0,6].filter(c=>s.board[c]===null);
function drop(s,column){if(!legal(s).includes(column))return s;const n={...s,board:[...s.board],moves:s.moves+1};let r=5;while(n.board[r*7+column])r--;n.board[r*7+column]=s.currentPlayer;n.winner=lineWinner(n.board,6,7,4);if(!n.winner&&n.board.some(x=>!x))n.currentPlayer=other(s.currentPlayer,'red','yellow');return n;}
function evaluate(s,p){if(s.winner)return s.winner===p?1e6:-1e6;let v=0;for(let r=0;r<6;r++)for(let c=0;c<7;c++){if(s.board[r*7+c])v+=(s.board[r*7+c]===p?1:-1)*(c===3?15:1);for(const[dr,dc]of directions){if(!inside(r+dr*3,c+dc*3,6,7))continue;const w=Array.from({length:4},(_,k)=>s.board[(r+dr*k)*7+c+dc*k]);const a=w.filter(x=>x===p).length,b=w.filter(x=>x&&x!==p).length;if(!b)v+=[0,8,110,850,100000][a];if(!a)v-=[0,9,130,950,100000][b];}}return v;}
function bot(s){const ls=legal(s);for(const p of[s.currentPlayer,other(s.currentPlayer,'red','yellow')])for(const c of ls)if(drop({...s,currentPlayer:p},c).winner===p)return c;return bestMove(s,{easy:1,normal:3,hard:4}[s.difficulty],legal,drop,n=>evaluate(n,s.currentPlayer));}
function apply(s,a){const t=setting(s,a);if(t)return t;if(a?.type==='bot'){const c=bot(s);return c===null?s:drop(s,c);}return a?.type==='drop'?drop(s,a.column):s;}
const status=s=>s.winner?(s.winner==='red'?'won':'lost'):s.board.every(Boolean)?'draw':'playing';
function validate(s){if(!baseOK(s)||!Array.isArray(s.board)||s.board.length!==42||!s.board.every(x=>x===null||['red','yellow'].includes(x))||!['red','yellow'].includes(s.currentPlayer)||![null,'red','yellow'].includes(s.winner))return false;for(let c=0;c<7;c++){let filled=false;for(let r=0;r<6;r++){if(s.board[r*7+c])filled=true;else if(filled)return false;}}const red=s.board.filter(x=>x==='red').length,yellow=s.board.filter(x=>x==='yellow').length;return red>=yellow&&red-yellow<=1&&s.moves===red+yellow&&s.winner===lineWinner(s.board,6,7,4)&&s.currentPlayer===(s.winner||s.board.every(Boolean)?(red>yellow?'red':'yellow'):(red>yellow?'yellow':'red'));}
function view(s){const ls=legal(s);return {kind:'grid',columns:7,cells:s.board.map((p,i)=>({text:p?'●':'',label:`Row ${Math.floor(i/7)+1}, column ${i%7+1}: ${p||'empty'}`,tone:p||'empty',disabled:!ls.includes(i%7),action:{type:'drop',column:i%7}})),stats:[{label:'Turn',value:s.currentPlayer},{label:'Moves',value:s.moves}],message:s.winner?`${s.winner} wins`:status(s)==='draw'?'Draw':'Choose a column. Four connected wins.',controls:controls(s,status(s)!=='playing')};}
export default {initial,apply,status,validate,view,legalMoves:legal};
