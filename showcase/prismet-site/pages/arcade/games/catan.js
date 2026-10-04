// Browser port of Prismet iOS CatanGame, CatanBoard and the owned heuristic AI.
// Matches its first-playable rules: 4:1 bank trades; Knight and VP development.
import { generator } from '../engines.js';
import board from './catan-board.js';
import { int,array,uint64,restoredRNG } from './puzzle-common.js';
export { board };
const RES=['brick','lumber','wool','grain','ore'];
const COST={road:{brick:1,lumber:1},settlement:{brick:1,lumber:1,wool:1,grain:1},city:{grain:2,ore:3},development:{wool:1,grain:1,ore:1}};
const PHASES=['setupSettlement','setupRoad','roll','build','moveRobber','gameOver'];
const countResources=p=>RES.reduce((n,r)=>n+p.resources[r],0);
const afford=(s,c,p=s.currentPlayer)=>Object.entries(c).every(([r,n])=>s.players[p].resources[r]>=n);
const pay=(s,c)=>{for(const [r,n]of Object.entries(c))s.players[s.currentPlayer].resources[r]-=n;};
const pips=n=>n?6-Math.abs(n-7):0;
const pipSum=(s,v)=>board.vertexHexes[v].reduce((n,h)=>n+pips(s.tiles[h].number),0);
const best=(items,fn)=>items.reduce((a,b)=>a===undefined||fn(b)>fn(a)?b:a,undefined);
const shuffled=(list,rng)=>{for(let i=list.length-1;i>0;i--){const j=rng.next(i+1);[list[i],list[j]]=[list[j],list[i]];}return list;};
const addLog=(s,text)=>{s.log.push(text);s.log=s.log.slice(-30);};
export function score(s,p,hidden=false){return s.buildings.reduce((n,b)=>n+(b?.owner===p?(b.kind==='city'?2:1):0),0)+(s.longestRoadOwner===p?2:0)+(s.largestArmyOwner===p?2:0)+(hidden?[...s.players[p].devCards,...s.players[p].newDevCards].filter(c=>c==='victoryPoint').length:0);}
function connects(s,v,p){return s.buildings[v]?s.buildings[v].owner===p:board.vertexEdges[v].some(e=>s.roads[e]===p);}
export function legal(s,type,p=s.currentPlayer){
 const setup=s.phase.startsWith('setup');
 if(type==='settlement')return board.vertices.flatMap((_,v)=>s.players[p].settlementsLeft>0&&!s.buildings[v]&&!board.adjacency[v].some(n=>s.buildings[n])&&(setup||board.vertexEdges[v].some(e=>s.roads[e]===p))?[v]:[]);
 if(type==='city')return s.players[p].citiesLeft>0?s.buildings.flatMap((b,v)=>b?.owner===p&&b.kind==='settlement'?[v]:[]):[];
 if(type==='road')return s.players[p].roadsLeft>0?board.edges.flatMap(([a,b],e)=>s.roads[e]===-1&&(setup?[a,b].includes(s.lastSetupVertex):connects(s,a,p)||connects(s,b,p))?[e]:[]):[];
 if(type==='robber')return s.tiles.flatMap((_,i)=>i!==s.robberHex?[i]:[]);
 return [];
}
export function longestRoad(s,p){
 let max=0;const edges=new Set(s.roads.flatMap((owner,e)=>owner===p?[e]:[]));
 function walk(v,used){max=Math.max(max,used.size);if(s.buildings[v]&&s.buildings[v].owner!==p)return;for(const e of board.vertexEdges[v])if(edges.has(e)&&!used.has(e)){used.add(e);const [a,b]=board.edges[e];walk(a===v?b:a,used);used.delete(e);}}
 for(const e of edges)for(const v of board.edges[e])walk(v,new Set());
 return max;
}
function awards(s){
 for(const [field,values,min]of [['longestRoadOwner',s.players.map((_,p)=>longestRoad(s,p)),5],['largestArmyOwner',s.players.map(p=>p.knightsPlayed),3]]){
  const high=Math.max(...values);if(high<min){s[field]=null;continue;}const leaders=values.flatMap((v,p)=>v===high?[p]:[]);if(!leaders.includes(s[field]))s[field]=leaders[0];
 }
 if(score(s,s.currentPlayer,true)>=10){s.winner=s.currentPlayer;s.phase='gameOver';addLog(s,`${s.players[s.winner].name} wins with ${score(s,s.winner,true)} points.`);}
}
function mutate(s,a){
 if(!a||s.phase==='gameOver')return false;const p=s.currentPlayer,player=s.players[p];
 if(a.type==='tool'&&['settlement','road','city'].includes(a.value)){s.tool=a.value;return true;}
 if(a.type==='difficulty'&&['gentle','cozy','clever'].includes(a.value)){s.difficulty=a.value;return true;}
 if(a.type==='settlement'||a.type==='city'||a.type==='road'){
  const setup=s.phase===(a.type==='road'?'setupRoad':'setupSettlement');
  if(a.type==='city'&&s.phase!=='build'||!setup&&s.phase!=='build'||!int(a.index,0,a.type==='road'?71:53)||!legal(s,a.type).includes(a.index)||!setup&&!afford(s,COST[a.type]))return false;
  if(!setup)pay(s,COST[a.type]);
  if(a.type==='road'){
   s.roads[a.index]=p;player.roadsLeft--;
   if(setup){s.setupStep++;s.lastSetupVertex=null;if(s.setupStep>=s.setupOrder.length){s.currentPlayer=0;s.phase='roll';}else{s.currentPlayer=s.setupOrder[s.setupStep];s.phase='setupSettlement';}}
  }else{
   s.buildings[a.index]={owner:p,kind:a.type};
   if(a.type==='city'){player.citiesLeft--;player.settlementsLeft++;}else player.settlementsLeft--;
   if(setup){s.lastSetupVertex=a.index;if(s.setupStep>=s.players.length)for(const h of board.vertexHexes[a.index]){const r=s.tiles[h].resource;if(r)player.resources[r]++;}s.phase='setupRoad';}
  }
  addLog(s,`${player.name} built a ${a.type}.`);if(!setup)awards(s);return true;
 }
 if(a.type==='roll'&&s.phase==='roll'){
  const rng=restoredRNG(s.rngState),dice=[rng.next(6)+1,rng.next(6)+1],total=dice[0]+dice[1];s.rngState=rng.state;s.lastRoll=dice;addLog(s,`${player.name} rolled ${total}.`);
  if(total===7){for(const person of s.players){const total=countResources(person);if(total>7)for(let n=0;n<Math.floor(total/2);n++){const r=best(RES,r=>person.resources[r]);person.resources[r]--;}}s.phase='moveRobber';}
  else{for(let h=0;h<19;h++){const tile=s.tiles[h];if(h===s.robberHex||tile.number!==total||!tile.resource)continue;for(const v of board.hexVertices[h]){const b=s.buildings[v];if(b)s.players[b.owner].resources[tile.resource]+=b.kind==='city'?2:1;}}s.phase='build';}
  return true;
 }
 if(a.type==='robber'&&s.phase==='moveRobber'&&legal(s,'robber').includes(a.index)){
  s.robberHex=a.index;const opponents=[...new Set(board.hexVertices[a.index].flatMap(v=>s.buildings[v]&&s.buildings[v].owner!==p?[s.buildings[v].owner]:[]))].sort();const victim=best(opponents,v=>countResources(s.players[v]));
  if(victim!==undefined&&countResources(s.players[victim])>0){const bag=RES.flatMap(r=>Array(s.players[victim].resources[r]).fill(r)),rng=restoredRNG(s.rngState),r=bag[rng.next(bag.length)];s.rngState=rng.state;s.players[victim].resources[r]--;player.resources[r]++;}
  s.phase='build';addLog(s,`${player.name} moved the robber.`);return true;
 }
 if(s.phase!=='build')return false;
 if(a.type==='trade'&&RES.includes(a.give)&&RES.includes(a.get)&&a.give!==a.get&&player.resources[a.give]>=4){player.resources[a.give]-=4;player.resources[a.get]++;addLog(s,`${player.name}: four ${a.give} for one ${a.get}.`);return true;}
 if(a.type==='development'&&s.devDeck.length&&afford(s,COST.development)){pay(s,COST.development);player.newDevCards.push(s.devDeck.shift());awards(s);return true;}
 if(a.type==='knight'&&!player.playedDevThisTurn&&player.devCards.includes('knight')){player.devCards.splice(player.devCards.indexOf('knight'),1);player.knightsPlayed++;player.playedDevThisTurn=true;awards(s);if(s.phase!=='gameOver')s.phase='moveRobber';return true;}
 if(a.type==='end'){player.devCards.push(...player.newDevCards);player.newDevCards=[];player.playedDevThisTurn=false;s.currentPlayer=(p+1)%s.players.length;s.phase='roll';s.tool='settlement';return true;}
 return false;
}
export function botAction(s){
 const p=s.currentPlayer,player=s.players[p],placement=type=>best(legal(s,type),v=>pipSum(s,v));
 if(s.phase==='setupSettlement')return {type:'settlement',index:placement('settlement')};
 if(s.phase==='setupRoad')return {type:'road',index:best(legal(s,'road'),e=>pipSum(s,board.edges[e].find(v=>v!==s.lastSetupVertex)))};
 if(s.phase==='roll')return {type:'roll'};
 if(s.phase==='moveRobber'){
  const leader=best(s.players.map((_,i)=>i),i=>score(s,i));
  return {type:'robber',index:best(legal(s,'robber'),h=>board.hexVertices[h].reduce((sum,v)=>{const b=s.buildings[v];if(!b)return sum;if(b.owner===p)return sum-100;let weight=(b.kind==='city'?2:1)*pips(s.tiles[h].number);if(s.difficulty==='clever'&&b.owner===leader)weight+=3*pips(s.tiles[h].number);if(s.difficulty==='gentle'&&b.owner===0)weight-=6*pips(s.tiles[h].number);return sum+weight;},0))};
 }
 if(s.phase!=='build')return {type:'none'};
 for(const type of ['city','settlement'])if(afford(s,COST[type])&&legal(s,type).length)return {type,index:placement(type)};
 if(!player.playedDevThisTurn&&player.devCards.includes('knight')&&(board.hexVertices[s.robberHex].some(v=>s.buildings[v]?.owner===p)||s.difficulty!=='gentle'&&player.knightsPlayed+1>=3&&s.players.every((other,i)=>i===p||other.knightsPlayed<player.knightsPlayed+1)))return {type:'knight'};
 if(afford(s,COST.road)){
  const potential=e=>Math.max(...board.edges[e].map(v=>!s.buildings[v]&&!board.adjacency[v].some(n=>s.buildings[n])?pipSum(s,v):0));
  const edge=best(legal(s,'road'),potential);if(edge!==undefined&&potential(edge)>0)return {type:'road',index:edge};
 }
 if(s.devDeck.length&&afford(s,COST.development)&&countResources(player)>=(s.difficulty==='clever'?4:s.difficulty==='gentle'?7:5))return {type:'development'};
 if(s.difficulty!=='gentle')for(const type of ['city','settlement'])if(legal(s,type).length){const cost=COST[type],missing=Object.keys(cost).find(r=>player.resources[r]<cost[r]);if(missing){const give=RES.find(r=>r!==missing&&player.resources[r]>=4+(cost[r]||0));if(give)return {type:'trade',give,get:missing};}}
 return {type:'end'};
}
const engine={
 initial(seed='1',{bots=true,difficulty='cozy'}={}){
  const rng=generator(seed),bag=shuffled([...Array(3).fill('brick'),...Array(4).fill('lumber'),...Array(4).fill('wool'),...Array(4).fill('grain'),...Array(3).fill('ore'),null],rng),numbers=shuffled([2,3,3,4,4,5,5,6,6,8,8,9,9,10,10,11,11,12],rng);let n=0;
  const tiles=bag.map(resource=>({resource,number:resource?numbers[n++]:null})),devDeck=shuffled([...Array(14).fill('knight'),...Array(5).fill('victoryPoint')],rng);
  return {tiles,robberHex:bag.indexOf(null),buildings:Array(54).fill(null),roads:Array(72).fill(-1),players:['You','Amber','Jade'].map((name,index)=>({name,index,isBot:!!bots&&index!==0,resources:Object.fromEntries(RES.map(r=>[r,0])),settlementsLeft:5,citiesLeft:4,roadsLeft:15,devCards:[],newDevCards:[],knightsPlayed:0,playedDevThisTurn:false})),currentPlayer:0,phase:'setupSettlement',lastRoll:null,setupOrder:[0,1,2,2,1,0],setupStep:0,lastSetupVertex:null,devDeck,winner:null,longestRoadOwner:null,largestArmyOwner:null,log:['Place your first settlement.'],rngState:rng.state,tool:'settlement',difficulty:['gentle','cozy','clever'].includes(difficulty)?difficulty:'cozy'};
 },
 apply(s,a){const next=structuredClone(s);if(!mutate(next,a))return s;for(let n=0;n<150&&next.phase!=='gameOver'&&next.players[next.currentPlayer].isBot;n++){if(!mutate(next,botAction(next)))break;}return next;},
 status:s=>s.winner===null?'playing':s.winner===0?'won':'lost',
 validate(s){
  if(!s||!uint64(s.rngState)||!array(s.tiles,19,t=>t&&((t.resource===null&&t.number===null)||(RES.includes(t.resource)&&[2,3,4,5,6,8,9,10,11,12].includes(t.number))))||s.tiles.filter(t=>t.resource===null).length!==1||!int(s.robberHex,0,18)||!array(s.buildings,54,b=>b===null||b&&int(b.owner,0,2)&&['settlement','city'].includes(b.kind))||!array(s.roads,72,p=>int(p,-1,2))||!array(s.players,3,p=>p&&int(p.index,0,2)&&typeof p.name==='string'&&p.name.length<30&&typeof p.isBot==='boolean'&&p.resources&&RES.every(r=>int(p.resources[r],0,9999))&&int(p.settlementsLeft,0,5)&&int(p.citiesLeft,0,4)&&int(p.roadsLeft,0,15)&&int(p.knightsPlayed,0,14)&&typeof p.playedDevThisTurn==='boolean'&&[p.devCards,p.newDevCards].every(a=>Array.isArray(a)&&a.length<=19&&a.every(c=>['knight','victoryPoint'].includes(c)))))return false;
  if(!int(s.currentPlayer,0,2)||!PHASES.includes(s.phase)||!int(s.setupStep,0,6)||JSON.stringify(s.setupOrder)!=='[0,1,2,2,1,0]'||!(s.lastSetupVertex===null||int(s.lastSetupVertex,0,53))||!['settlement','road','city'].includes(s.tool)||!['gentle','cozy','clever'].includes(s.difficulty)||![s.winner,s.longestRoadOwner,s.largestArmyOwner].every(p=>p===null||int(p,0,2))||!(s.lastRoll===null||array(s.lastRoll,2,d=>int(d,1,6)))||!Array.isArray(s.log)||s.log.length>30||!s.log.every(x=>typeof x==='string'&&x.length<200)||!Array.isArray(s.devDeck)||s.devDeck.length>19||!s.devDeck.every(c=>['knight','victoryPoint'].includes(c)))return false;
  if(s.players.some((p,i)=>p.index!==i||p.roadsLeft+s.roads.filter(v=>v===i).length!==15||p.settlementsLeft+s.buildings.filter(b=>b?.owner===i&&b.kind==='settlement').length!==5||p.citiesLeft+s.buildings.filter(b=>b?.owner===i&&b.kind==='city').length!==4))return false;
  if(s.phase.startsWith('setup')){
   const road=s.phase==='setupRoad';
   if(s.setupStep>=6||s.currentPlayer!==s.setupOrder[s.setupStep]||s.roads.filter(p=>p>=0).length!==s.setupStep||s.buildings.filter(Boolean).length!==s.setupStep+(road?1:0)||s.buildings.some(b=>b&&b.kind!=='settlement'))return false;
   if(road){if(s.lastSetupVertex===null||s.buildings[s.lastSetupVertex]?.owner!==s.currentPlayer)return false;}else if(s.lastSetupVertex!==null)return false;
  }else if(s.setupStep!==6||s.lastSetupVertex!==null)return false;
  if(s.buildings.some((b,i)=>b&&board.adjacency[i].some(j=>s.buildings[j])))return false;
  if(RES.some((r,i)=>s.tiles.filter(t=>t.resource===r).length!==[3,4,4,4,3][i]))return false;
  if(JSON.stringify(s.tiles.map(t=>t.number).filter(n=>n!==null).sort((a,b)=>a-b))!=='[2,3,3,4,4,5,5,6,6,8,8,9,9,10,10,11,11,12]')return false;
  const cards=[...s.devDeck,...s.players.flatMap(p=>[...p.devCards,...p.newDevCards])];
  if(cards.filter(c=>c==='knight').length+s.players.reduce((n,p)=>n+p.knightsPlayed,0)!==14||cards.filter(c=>c==='victoryPoint').length!==5)return false;
  return s.phase==='gameOver'?s.winner!==null&&score(s,s.winner,true)>=10:s.winner===null;
 },
 view(s){
  const player=s.players[s.currentPlayer],type=s.phase==='setupRoad'?'road':s.phase==='setupSettlement'?'settlement':s.tool,canPlace=s.phase.startsWith('setup')||s.phase==='build'&&afford(s,COST[type]),allowed=canPlace?legal(s,type):[];
  const controls=[{label:'Roll dice',action:{type:'roll'},disabled:s.phase!=='roll'},...['settlement','road','city'].map(value=>({label:`${value[0].toUpperCase()+value.slice(1)}${s.tool===value?' ✓':''}`,action:{type:'tool',value},disabled:s.phase!=='build'})),{label:'Buy development (wool + grain + ore)',action:{type:'development'},disabled:s.phase!=='build'||!s.devDeck.length||!afford(s,COST.development)},{label:'Play Knight',action:{type:'knight'},disabled:s.phase!=='build'||player.playedDevThisTurn||!player.devCards.includes('knight')},{label:'End turn',action:{type:'end'},disabled:s.phase!=='build'}];
  if(s.phase==='build')for(const give of RES)if(player.resources[give]>=4)for(const get of RES)if(get!==give)controls.push({label:`4 ${give} → 1 ${get}`,action:{type:'trade',give,get}});
  controls.push(...['gentle','cozy','clever'].map(value=>({label:`Bots: ${value}${s.difficulty===value?' ✓':''}`,action:{type:'difficulty',value}})));
  const message=s.phase==='gameOver'?`${s.players[s.winner].name} wins.`:s.phase==='roll'?`${player.name}: roll the dice.`:s.phase==='moveRobber'?'Choose a different hex for the robber.':s.phase==='setupSettlement'?'Place a settlement at a highlighted corner. Keep one corner between settlements.':s.phase==='setupRoad'?'Place a road beside your new settlement.':`${player.name}: build, trade, or end the turn. Road: brick + lumber. Settlement: brick + lumber + wool + grain. City: 2 grain + 3 ore.`;
  return {kind:'hex',hexes:board.centers.map((point,i)=>({...point,...s.tiles[i],robber:i===s.robberHex,label:`Hex ${i+1}: ${s.tiles[i].resource||'desert'} ${s.tiles[i].number||''}${i===s.robberHex?', robber':''}`,action:s.phase==='moveRobber'&&i!==s.robberHex?{type:'robber',index:i}:undefined})),vertices:board.vertices.map((point,i)=>({...point,owner:s.buildings[i]?.owner??null,building:s.buildings[i]?.kind??null,label:`Corner ${i+1}${s.buildings[i]?`, ${s.players[s.buildings[i].owner].name} ${s.buildings[i].kind}`:''}`,action:type!=='road'&&allowed.includes(i)?{type,index:i}:undefined})),edges:board.edges.map(([a,b],i)=>({x1:board.vertices[a].x,y1:board.vertices[a].y,x2:board.vertices[b].x,y2:board.vertices[b].y,owner:s.roads[i],label:`Road ${i+1}`,action:type==='road'&&allowed.includes(i)?{type:'road',index:i}:undefined})),stats:[...s.players.map((p,i)=>({label:p.name,value:`${score(s,i,i===0)} VP`})),...RES.map(r=>({label:r,value:player.resources[r]})),{label:'Dice',value:s.lastRoll?s.lastRoll.join(' + '):'—'}],message,controls,log:s.log.slice(-4),note:'Native rules: three players, 4:1 bank trading, Knight and Victory Point development cards. First to 10 points wins.'};
 },
};export default engine;
