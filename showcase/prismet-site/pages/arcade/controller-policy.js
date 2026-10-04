// Shared scheduling policy; no browser globals, so cancellation can be tested directly.
const human = {chess:'white',reversi:'black',checkers:'dark','connect-four':'red',gomoku:'black','sea-battle':'host'};
export function needsBot(id,state,engine) {
  if(engine.status(state)!=='playing')return false;
  if(id==='crazy-8')return state.mode==='soloBot'&&state.currentPlayer==='guest';
  return Object.hasOwn(human,id)&&state.mode==='bot'&&state.currentPlayer!==human[id];
}
export function pauseRestored(id,state,engine) { return id==='snake'&&state.running ? engine.apply(state,{type:'pause'}) : state; }
export function scheduler(set = setTimeout, clear = clearTimeout) {
  let timer, generation=0;
  const cancel=()=>{generation++;clear(timer);};
  return {cancel,schedule(task,ms=260){cancel();const attempt=generation;timer=set(()=>{if(attempt===generation)task();},ms);}};
}
