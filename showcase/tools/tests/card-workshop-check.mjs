import assert from 'node:assert/strict';
import solitaire, { deck, cardID } from '../../prismet-site/pages/arcade/games/solitaire.js';
import { readFileSync } from 'node:fs';
import spider from '../../prismet-site/pages/arcade/games/spider.js';
import crazy from '../../prismet-site/pages/arcade/games/crazy-8.js';
import brick, { exportXML } from '../../prismet-site/pages/arcade/games/brick-bench.js';
const engines = { solitaire, spider, 'crazy-8': crazy, 'brick-bench': brick };
let checks=0; const check=(x,m)=>{assert.ok(x,m);checks++;};
for(const [id,engine] of Object.entries(engines)) for(const seed of ['42','0','18446744073709551615']) {
 const state=engine.initial(seed);check(engine.validate(state),`${id} initial valid`);assert.deepEqual(engine.initial(seed),state);checks++;
 const frozen=JSON.stringify(state);assert.equal(engine.apply(state,{type:'unsupported'}),state);checks++;assert.equal(JSON.stringify(state),frozen);checks++;
 check(['grid','cards'].includes(engine.view(state).kind),'view is concrete');
 check(!engine.validate({}),'reject missing state');
}
let s=solitaire.initial('42'); const stock=s.stock.length; let drawn=solitaire.apply(s,{type:'draw'});check(drawn!==s&&drawn.stock.length===stock-1&&s.stock.length===stock,'immutable draw');
check(!solitaire.validate({...s,stock:[...s.stock,s.stock[0]]}),'duplicate card rejected');
assert.equal(solitaire.apply(s,{type:'tableauMove',from:0,index:0,to:0}),s);checks++;
let p=spider.initial('42'); let dealt=spider.apply(p,{type:'deal'});check(dealt.stockRows.length===4&&dealt.tableau.every((a,i)=>a.length===p.tableau[i].length+1),'spider deals10');
check(!spider.validate({...p,completedSets:9}),'impossible sets rejected');
let c=crazy.initial('42',{mode:'passAndPlay'});let next=crazy.apply(c,{type:'draw'});check(next.currentPlayer==='guest'&&next.hands.host.length===8,'crazy draw passes turn');
assert.equal(crazy.apply(c,{type:'play',index:999}),c);checks++;
let b=brick.initial('42');b=brick.apply(b,{type:'origin',x:11,y:11});b=brick.apply(b,{type:'place'});check(b.bricks[0].origin.x===10&&b.bricks[0].origin.y===8,'native2x4 footprint clamped');
b=brick.apply(b,{type:'rotate',quarters:1});check(b.bricks[0].rotationQuarters===1&&b.bricks[0].origin.x===8,'rotation re-clamps footprint');
check(!brick.validate({...b,bricks:[{...b.bricks[0],layer:13}]}),'invalid layer rejected');
const fixtures=JSON.parse(readFileSync(new URL('./fixtures/card-workshop/native-parity.json',import.meta.url),'utf8'));
for(const fixture of fixtures){const engine=engines[fixture.game];let state=engine.initial(fixture.seed,fixture.options);const projection=s=>Object.fromEntries(Object.keys(fixture.initial).map(k=>[k,s[k]]));assert.deepEqual(projection(state),fixture.initial,`${fixture.game} native deal seed${fixture.seed}`);checks++;for(const action of fixture.actions)state=engine.apply(state,action);assert.deepEqual(projection(state),fixture.final,`${fixture.game} native actions seed${fixture.seed}`);checks++;check(engine.validate(state),'parity final valid');}
const remaining=used=>deck().filter(c=>!used.some(x=>cardID(x)===cardID(c)));
let nearWin={...solitaire.initial('1'),stock:[],waste:[],tableau:Array.from({length:7},()=>[]),foundations:Object.fromEntries(['spades','hearts','diamonds','clubs'].map(suit=>[suit,deck().filter(c=>c.suit===suit)]))};
nearWin.tableau[0]=[{card:nearWin.foundations.clubs.pop(),isFaceUp:true}];check(solitaire.validate(nearWin),'valid near-win');check(solitaire.status(solitaire.apply(nearWin,{type:'tableauFoundation',from:0}))==='won','solitaire can finish');
const ace={rank:1,suit:'hearts'},hidden={rank:5,suit:'spades'};let flipState={...solitaire.initial('1'),stock:remaining([ace,hidden]),waste:[],tableau:[[{card:hidden,isFaceUp:false},{card:ace,isFaceUp:true}],...Array.from({length:6},()=>[])]};
check(solitaire.validate(flipState),'hidden fixture valid');const flipped=solitaire.apply(flipState,{type:'tableauFoundation',from:0});check(flipped.tableau[0][0].isFaceUp&&!flipState.tableau[0][0].isFaceUp,'exposed card flips immutably');assert.equal(solitaire.apply(flipState,{type:'tableauMove',from:0,index:1,to:1}),flipState);checks++;
const run=()=>Array.from({length:13},(_,i)=>({card:{rank:13-i,suit:'spades'},isFaceUp:true}));let spiderWin={tableau:[run().slice(0,-1),run().slice(-1),...Array.from({length:7},run),[]],stockRows:[],completedSets:0,moves:0,selected:null};check(spider.validate(spiderWin),'spider finish fixture valid');check(spider.status(spider.apply(spiderWin,{type:'move',from:1,index:0,to:0}))==='won','spider completes all8 runs');
const last={rank:1,suit:'spades'},top={rank:2,suit:'spades'},other={rank:3,suit:'hearts'};let crazyWin={...crazy.initial('1',{mode:'passAndPlay'}),hands:{host:[last],guest:[other]},drawPile:remaining([last,top,other]),discardPile:[top],declaredSuit:'spades'};check(crazy.validate(crazyWin),'crazy finish fixture valid');check(crazy.status(crazy.apply(crazyWin,{type:'play',index:0}))==='won','crazy hand empties towin');
check(!crazy.validate({...crazyWin,declaredSuit:'diamonds'}),'non-eight discard must determine declared suit');
const eight={rank:8,suit:'clubs'};let wild={...crazyWin,hands:{host:[eight,last],guest:[other]},drawPile:remaining([eight,last,top,other])};let wildNext=crazy.apply(wild,{type:'play',index:0,suit:'diamonds'});check(wildNext.declaredSuit==='diamonds'&&wildNext.currentPlayer==='guest','eight declares suit and passes turn');check(crazy.validate(wildNext),'eight may declare a different suit');
let recycled={...crazyWin,drawPile:[],discardPile:remaining([last,other])};recycled.declaredSuit=recycled.discardPile.at(-1).suit;const recycleNext=crazy.apply(recycled,{type:'draw'});check(recycleNext.discardPile.length===1&&recycleNext.hands.host.length===2,'discard recycled preserving top');
for(let turn=0,state=crazy.initial('42');turn<200&&crazy.status(state)==='playing';turn++){const view=crazy.view(state);const action=view.piles[0].cards.find(c=>!c.disabled)?.action||{type:'draw'};const next=crazy.apply(state,action);check(crazy.validate(next),'bot game stays valid');check(JSON.stringify(next)!==JSON.stringify(state),'solo round progresses');state=next;}
check(brick.view(b).cells.length===144,'12x12 workshop');const removed=brick.apply(b,{type:'delete'});check(removed.bricks.length===0&&b.bricks.length===1,'delete is reversible by retained snapshot');
const workshopFixture=JSON.parse(readFileSync(new URL('./fixtures/card-workshop/native-workshop.json',import.meta.url),'utf8'));
let workshop=brick.initial('42');for(const action of [{type:'origin',x:11,y:11},{type:'place'},{type:'move',dx:3,dy:-10,dLayer:12},{type:'rotate',quarters:1}])workshop=brick.apply(workshop,action);
assert.deepEqual(workshop.bricks,workshopFixture.bricks,'native translation/rotation clamping');checks++;assert.equal(exportXML(workshop),workshopFixture.xml,'native part catalog/color/export');checks++;
console.log(`✓ card-workshop: ${checks} checks; ${fixtures.length} actual native card fixtures + workshop match`);
