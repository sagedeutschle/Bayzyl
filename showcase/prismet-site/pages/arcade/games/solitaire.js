import { generator, uint64 } from '../engines.js';
export const suits=['spades','hearts','diamonds','clubs'];
export const integer=(x,a,b)=>Number.isSafeInteger(x)&&x>=a&&x<=b;
export const cardOK=c=>!!c&&integer(c.rank,1,13)&&suits.includes(c.suit);
export const red=c=>c.suit==='hearts'||c.suit==='diamonds';
export const cardID=c=>`${c.suit}-${c.rank}`;
export const cardLabel=c=>`${({1:'A',11:'J',12:'Q',13:'K'})[c.rank]||c.rank}${({spades:'♠',hearts:'♥',diamonds:'♦',clubs:'♣'})[c.suit]}`;
export const deck=()=>suits.flatMap(suit=>Array.from({length:13},(_,i)=>({rank:i+1,suit})));
export function shuffle(cards,seed) { const result=structuredClone(cards),rng=generator(seed);for(let i=result.length-1;i>0;i--){const j=rng.next(i+1);[result[i],result[j]]=[result[j],result[i]];}return result; }
export const cell=(card,extra={})=>({text:cardLabel(card),label:`${card.rank} of ${card.suit}`,tone:red(card)?'red':'dark',...extra});
export function facePileOK(pile,max=104){if(!Array.isArray(pile)||pile.length>max)return false;let up=false;return pile.every(x=>{if(!x||!cardOK(x.card)||typeof x.isFaceUp!=='boolean'||(up&&!x.isFaceUp))return false;up ||= x.isFaceUp;return true;})&&(!pile.length||pile.at(-1).isFaceUp);}
export function uniqueDeck(cards){return cards.length===52&&cards.every(cardOK)&&new Set(cards.map(cardID)).size===52;}
const foundation=(s,c)=>c.rank===(s.foundations[c.suit].at(-1)?.rank||0)+1;
const place=(s,c,pile)=>integer(pile,0,6)&&(s.tableau[pile].length?red(c)!==red(s.tableau[pile].at(-1).card)&&c.rank===s.tableau[pile].at(-1).card.rank-1:c.rank===13);
const validRun=run=>run.length>0&&run.every((x,i)=>x.isFaceUp&&(!i||red(x.card)!==red(run[i-1].card)&&x.card.rank===run[i-1].card.rank-1));
const flip=pile=>{if(pile.length)pile.at(-1).isFaceUp=true;};
function move(s,a){
 if(a.type==='draw'){if(!s.stock.length){if(!s.waste.length)return false;s.stock=s.waste.reverse();s.waste=[];}else for(let n=0;n<s.drawCount&&s.stock.length;n++)s.waste.push(s.stock.pop());}
 else if(a.type==='wasteFoundation'){const c=s.waste.at(-1);if(!c||!foundation(s,c))return false;s.foundations[c.suit].push(s.waste.pop());}
 else if(a.type==='wasteTableau'){const c=s.waste.at(-1);if(!c||!place(s,c,a.to))return false;s.tableau[a.to].push({card:s.waste.pop(),isFaceUp:true});}
 else if(a.type==='tableauFoundation'){if(!integer(a.from,0,6))return false;const c=s.tableau[a.from].at(-1);if(!c?.isFaceUp||!foundation(s,c.card))return false;s.foundations[c.card.suit].push(s.tableau[a.from].pop().card);flip(s.tableau[a.from]);}
 else if(a.type==='tableauMove'){if(!integer(a.from,0,6)||!integer(a.to,0,6)||a.from===a.to||!integer(a.index,0,s.tableau[a.from].length-1))return false;const run=s.tableau[a.from].slice(a.index);if(!validRun(run)||!place(s,run[0].card,a.to))return false;s.tableau[a.to].push(...s.tableau[a.from].splice(a.index));flip(s.tableau[a.from]);}
 else return false;s.moves++;s.selected=null;return true;
}
const solitaire={
 initial(seed,options={}){if(!uint64(seed))throw Error('Invalid seed');const stock=shuffle(deck(),seed),tableau=Array.from({length:7},(_,i)=>Array.from({length:i+1},(_,j)=>({card:stock.pop(),isFaceUp:j===i})));return{stock,waste:[],foundations:Object.fromEntries(suits.map(s=>[s,[]])),tableau,drawCount:options.drawCount===3?3:1,moves:0,selected:null};},
 validate(s){try{if(!s||![1,3].includes(s.drawCount)||!integer(s.moves,0,1e9)||!Array.isArray(s.stock)||!Array.isArray(s.waste)||s.stock.length>52||s.waste.length>52||!Array.isArray(s.tableau)||s.tableau.length!==7||!s.tableau.every(p=>facePileOK(p,52)&&(!p.length||validRun(p.filter(x=>x.isFaceUp))))||!s.foundations)return false;for(const suit of suits){const p=s.foundations[suit];if(!Array.isArray(p)||p.length>13||p.some((c,i)=>!cardOK(c)||c.suit!==suit||c.rank!==i+1))return false;}if(!uniqueDeck([...s.stock,...s.waste,...suits.flatMap(x=>s.foundations[x]),...s.tableau.flat().map(x=>x.card)]))return false;return s.selected===null||s.selected?.from==='waste'&&s.waste.length>0||s.selected?.from==='tableau'&&integer(s.selected.pile,0,6)&&integer(s.selected.index,0,s.tableau[s.selected.pile].length-1)&&validRun(s.tableau[s.selected.pile].slice(s.selected.index));}catch{return false;}},
 status:s=>suits.every(x=>s.foundations[x].length===13)?'won':'playing',
 apply(s,a){if(!solitaire.validate(s)||!a||s.moves>=1e9)return s;const n=structuredClone(s);
 if(a.type==='selectWaste'){if(!n.waste.length)return s;n.selected={from:'waste'};return n;}
 if(a.type==='select'){if(!integer(a.pile,0,6)||!integer(a.index,0,n.tableau[a.pile].length-1)||!validRun(n.tableau[a.pile].slice(a.index)))return s;n.selected={from:'tableau',pile:a.pile,index:a.index};return n;}
 if(a.type==='cancel'){if(!n.selected)return s;n.selected=null;return n;}
 if(a.type==='toTableau'){if(!n.selected)return s;a=n.selected.from==='waste'?{type:'wasteTableau',to:a.pile}:{type:'tableauMove',from:n.selected.pile,index:n.selected.index,to:a.pile};}
 if(a.type==='toFoundation'){if(!n.selected)return s;if(n.selected.from==='tableau'&&n.selected.index!==n.tableau[n.selected.pile].length-1)return s;a=n.selected.from==='waste'?{type:'wasteFoundation'}:{type:'tableauFoundation',from:n.selected.pile};}
 if(a.type==='auto'){let changed=false,progress=true;while(progress){progress=move(n,{type:'wasteFoundation'});for(let i=0;i<7;i++)progress=move(n,{type:'tableauFoundation',from:i})||progress;changed ||= progress;}return changed?n:s;}
 return move(n,a)?n:s;},
 view(s){return{kind:'cards',piles:[{label:`Stock · ${s.stock.length}`,cards:[{text:s.stock.length?'Draw':'Recycle',label:'Draw from stock or recycle waste',action:{type:'draw'}}]},{label:'Waste',cards:s.waste.length?[cell(s.waste.at(-1),{selected:s.selected?.from==='waste',action:{type:'selectWaste'}})]:[{text:'Empty',label:'Waste is empty',disabled:true}]},...suits.map(suit=>({label:`${suit} foundation`,cards:s.foundations[suit].length?[cell(s.foundations[suit].at(-1),{disabled:true})]:[{text:'A',label:`Empty ${suit} foundation`,disabled:true}]})),...s.tableau.map((p,i)=>({label:`Tableau ${i+1}`,cards:p.map((x,j)=>x.isFaceUp?cell(x.card,{selected:s.selected?.pile===i&&j>=s.selected.index,action:{type:'select',pile:i,index:j}}):{text:'▧',label:'Face-down card',tone:'back',disabled:true})}))],stats:[{label:'Moves',value:s.moves},{label:'Draw',value:s.drawCount}],message:solitaire.status(s)==='won'?'All four foundations complete.':'Select a face-up run or the waste, then choose its destination. Alternate colours, descending ranks; only kings fill empty columns.',controls:[{label:'Draw / recycle',action:{type:'draw'}},{label:'Send selected to foundation',action:{type:'toFoundation'},disabled:!s.selected},{label:'Auto collect',action:{type:'auto'}},...s.tableau.map((_,i)=>({label:`Move to tableau ${i+1}`,action:{type:'toTableau',pile:i},disabled:!s.selected})),{label:'Clear selection',action:{type:'cancel'},disabled:!s.selected}]};}
};
export default solitaire;
