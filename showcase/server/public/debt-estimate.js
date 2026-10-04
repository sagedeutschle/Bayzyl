// Illustrative headline only. Published metrics and historical observations stay untouched.
export function projectDebt(balance, perSecond, elapsedSeconds) {
  if(!Number.isFinite(balance)||balance<0||balance>Number.MAX_SAFE_INTEGER)return {value:null,estimated:false};
  if(!Number.isFinite(perSecond)||!Number.isFinite(elapsedSeconds))return {value:balance,estimated:false};
  const value=balance+perSecond*Math.max(0,elapsedSeconds);
  return Number.isFinite(value)&&value>=0&&value<=Number.MAX_SAFE_INTEGER ? {value,estimated:true} : {value:balance,estimated:false};
}
export function estimateClock({now=Date.now,paused=false}={}) {
  let balance=null,rate=null,elapsed=0,last=now();
  function advance(){const time=now();if(Number.isFinite(time)){if(!paused)elapsed+=Math.max(0,(time-last)/1000);last=Math.max(last,time);}}
  return {
    reset(nextBalance,nextRate){balance=nextBalance;rate=nextRate;elapsed=0;last=now();return this.read();},
    read(){advance();return {...projectDebt(balance,rate,elapsed),elapsedSeconds:elapsed,paused};},
    pause(value){advance();paused=!!value;return this.read();},
  };
}
