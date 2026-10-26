/* Portable deterministic coaching foundation. No network, clocks, DOM or storage. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.StrideLabEngine2 = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const VERSION = 2, POLICY = '2.0-foundation.1';
  const DISC = ['running', 'strength', 'calisthenics', 'conditioning'];
  const clone = x => JSON.parse(JSON.stringify(x));
  const assert = (ok, message) => { if (!ok) throw new Error(message); };
  const num = (x, lo, hi) => typeof x === 'number' && Number.isFinite(x) && x >= lo && x <= hi;
  const integer = (x, lo, hi) => Number.isInteger(x) && x >= lo && x <= hi;
  function validDate(s) { return typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && !isNaN(Date.parse(s)) && new Date(s).toISOString().slice(0,10) === s; }
  const add = (s, n) => new Date(Date.parse(s) + n * 86400000).toISOString().slice(0,10);
  const gap = (a,b) => Math.round((Date.parse(a)-Date.parse(b))/86400000);
  const dow = s => new Date(s).getUTCDay();
  function canonical(x) { return x && typeof x === 'object' ? Array.isArray(x) ? '['+x.map(canonical).join(',')+']' : '{'+Object.keys(x).sort().map(k=>JSON.stringify(k)+':'+canonical(x[k])).join(',')+'}' : JSON.stringify(x); }
  function hash(x) { let h=2166136261; for(const c of canonical(x)) h=Math.imul(h^c.charCodeAt(0),16777619); return (h>>>0).toString(16).padStart(8,'0'); }

  function context(input) {
    assert(input && input.schemaVersion === VERSION, 'Unsupported athlete schema version');
    const c=clone(input);
    assert(validDate(c.asOf), 'Supply a valid local asOf date');
    assert(Array.isArray(c.goals) && c.goals.length>=1 && c.goals.length<=4, 'Choose one to four discipline goals');
    assert(new Set(c.goals.map(g=>g.discipline)).size===c.goals.length, 'Duplicate discipline goal');
    for(const g of c.goals) {
      assert(DISC.includes(g.discipline), 'Unsupported discipline');
      assert(integer(g.sessions,1,6), 'Requested sessions must be between 1 and 6');
    }
    assert(c.abilities && DISC.every(d=>integer(c.abilities[d],0,2)), 'Set a separate ability (0–2) for every discipline');
    assert(c.availability && Array.isArray(c.availability.days) && c.availability.days.length>0 && c.availability.days.every(d=>integer(d,0,6)), 'Choose valid available weekdays');
    c.availability.days=[...new Set(c.availability.days)].sort();
    assert(integer(c.availability.minutes,15,180), 'Session time must be 15–180 minutes');
    assert(Array.isArray(c.equipment), 'Equipment inventory is required, even if empty');
    for(const e of c.equipment) assert(['dumbbell','band','bar','bench','rack','barbell','stableSurface'].includes(e.id) && integer(e.quantity,1,10), 'Invalid equipment inventory');
    assert(new Set(c.equipment.map(e=>e.id)).size===c.equipment.length,'Duplicate equipment');
    c.recovery=Object.assign({status:'normal'},c.recovery);
    assert(['normal','fatigued','returning','ill','injured','redFlag'].includes(c.recovery.status), 'Unknown recovery status');
    c.history=c.history||[]; assert(Array.isArray(c.history) && c.history.length<=2000, 'History must contain at most 2000 entries');
    const seen=new Set();
    for(const h of c.history) {
      assert(typeof h.id==='string' && h.id.length>0 && !seen.has(h.id), 'History ids must be unique'); seen.add(h.id);
      assert(DISC.includes(h.discipline) && validDate(h.date) && h.date<=c.asOf, 'Invalid history discipline/date');
      assert(['recorded','manual','demo','simulated'].includes(h.source), 'History provenance is required');
      assert(num(h.minutes,0,1440), 'Invalid history duration');
      if(h.rpe!=null) assert(num(h.rpe,1,10),'Invalid actual RPE');
      if(h.distanceKm!=null) assert(num(h.distanceKm,0,300),'Invalid measured distance');
    }
    c.feedback=c.feedback||[]; assert(Array.isArray(c.feedback) && c.feedback.length<=1000, 'Invalid feedback');
    for(const f of c.feedback) assert(typeof f.exerciseId==='string' && typeof f.historyId==='string' && integer(f.stage,0,2) && typeof f.success==='boolean' && typeof f.technique==='boolean' && typeof f.pain==='boolean','Invalid exercise feedback');
    c.missed=c.missed||[]; assert(Array.isArray(c.missed) && c.missed.every(m=>typeof m.sessionId==='string' && ['busy','sick','injured','tired','other'].includes(m.reason)),'Invalid missed-workout reason');
    if(c.runningBaselineMinutes!=null) assert(num(c.runningBaselineMinutes,0,1000),'Invalid declared running baseline');
    if(c.qualitySessions!=null) assert(integer(c.qualitySessions,0,1),'Foundation supports zero or one running quality session');
    if(c.longRunDay!=null) assert(integer(c.longRunDay,0,6) && c.availability.days.includes(c.longRunDay),'Long run day must be available');
    if(c.wellness) for(const k of ['sleepHours','sleepBaselineHours','restingHr','restingHrBaseline','hrv','hrvBaseline']) if(c.wellness[k]!=null) assert(num(c.wellness[k],0,300),'Invalid wellness value');
    return c;
  }
  function actual(c) { return c.history.filter(h=>h.source!=='demo' && h.source!=='simulated' && h.completed===true); }
  function assess(c) {
    const notes=[],status=c.recovery.status;
    const blocked=['ill','injured','redFlag'].includes(status) || c.missed.some(m=>['sick','injured'].includes(m.reason));
    let reduced=['fatigued','returning'].includes(status) || c.missed.some(m=>m.reason==='tired');
    const w=c.wellness||{};
    if(num(w.sleepHours,0,24)&&num(w.sleepBaselineHours,1,24)&&w.sleepHours<w.sleepBaselineHours-1.5) { reduced=true;notes.push('Sleep is below your supplied baseline; review fatigue and reduce this draft.'); }
    if(num(w.restingHr,1,250)&&num(w.restingHrBaseline,1,250)&&w.restingHr>w.restingHrBaseline+7) {reduced=true;notes.push('Resting heart rate is above your supplied baseline; review how you feel. This is not a diagnosis.');}
    if(num(w.hrv,1,300)&&num(w.hrvBaseline,1,300)&&w.hrv<w.hrvBaseline*.8) notes.push('HRV is below your supplied baseline. One reading does not establish readiness.');
    if(blocked) notes.push('Exercise is on hold for recovery review. This planner cannot clear illness, injury or red-flag symptoms.');
    if(reduced) notes.push('Reduced duration and no running quality in this draft. Resume progression only after recovery review.');
    return {blocked,reduced,notes,wearableData:!!Object.keys(w).length};
  }
  const CATALOG = [
    {id:'squat',pattern:'squat',names:['Bodyweight squat','Goblet squat','Goblet squat'],equipment:[[],['dumbbell'],['dumbbell']],lower:true},
    {id:'hinge',pattern:'hinge',names:['Unloaded hip hinge','Dumbbell Romanian deadlift','Dumbbell Romanian deadlift'],equipment:[[],['dumbbell'],['dumbbell']],lower:true},
    {id:'push',pattern:'push',names:['Wall push-up','Incline push-up','Push-up'],equipment:[[],['stableSurface'],[]],lower:false},
    {id:'pull',pattern:'pull',names:['Resistance band row','Dumbbell row','Pull-up'],equipment:[['band'],['dumbbell'],['bar']],lower:false},
    {id:'core',pattern:'core',names:['Dead bug','Dead bug','Dead bug'],equipment:[[],[],[]],lower:false}
  ];
  function stage(c, ex, ability) {
    // A running background cannot promote strength. Require two distinct completed exposures.
    const permitted=Math.min(ability,2), valid=new Map(actual(c).map(h=>[h.id,h]));
    let s=0;
    const recent=c.feedback.filter(f=>f.exerciseId===ex.id && valid.has(f.historyId) && gap(c.asOf,valid.get(f.historyId).date)<28);
    const lastPain=recent.filter(f=>f.pain).map(f=>valid.get(f.historyId).date).sort().pop();
    while(s<permitted) {
      const exposures=new Set(recent.filter(f=>f.stage===s && f.success && f.technique && !f.pain && (!lastPain || valid.get(f.historyId).date>lastPain) && ['strength','calisthenics'].includes(valid.get(f.historyId).discipline)).map(f=>f.historyId));
      if(exposures.size<2) break; s++;
    }
    return s;
  }
  function resistance(c, d, recovery) {
    const owned=new Set(c.equipment.map(e=>e.id)),a=c.abilities[d],missing=[];
    let exercises=CATALOG.map(ex=>{
      let s=stage(c,ex,a);
      while(s>=0 && !ex.equipment[s].every(e=>owned.has(e))) s--;
      // Pulling has no safe equipment-free equivalent. A row is not a pull-up progression claim.
      if(s<0){missing.push(ex.pattern);return null;}
      const sets=recovery.reduced?1:a===0?2:3;
      return {id:ex.id,name:ex.names[s],pattern:ex.pattern,stage:s,sets,reps:ex.id==='core'?6:8,repRange:[6,10],rir:3,restSec:ex.lower?120:90,lower:ex.lower,equipment:ex.equipment[s],loadKg:null,cue:'Controlled pain-free reps. Stop with about 3 good reps left. Choose a comfortable load; no automatic kg increase.'};
    }).filter(Boolean);
    const duration=()=>Math.ceil(5+exercises.reduce((t,e)=>t+e.sets*(e.reps*4+e.restSec)/60+1,0));
    while(duration()>c.availability.minutes && exercises.some(e=>e.sets>1)) exercises.filter(e=>e.sets>1).forEach(e=>e.sets--);
    if(duration()>c.availability.minutes) return {unmet:'Balanced resistance session cannot fit the available time.',exercises:[],minutes:0};
    return {exercises,minutes:duration(),missing,unmet:null};
  }
  function runningBaseline(c) {
    const runs=actual(c).filter(h=>h.discipline==='running' && gap(c.asOf,h.date)<28);
    const observed=runs.reduce((t,h)=>t+h.minutes,0)/4;
    if(runs.length) return {minutes:observed,source:'completed history over the last 28 days'};
    return {minutes:c.runningBaselineMinutes||0,source:c.runningBaselineMinutes?'user-declared baseline':'no running baseline'};
  }
  function build(c, start, weeks=4) {
    c=context(c);assert(validDate(start) && start>=c.asOf,'Plan must start on or after the context date');
    assert(integer(weeks,1,12),'Plan horizon must be 1–12 weeks');
    const rec=assess(c),base=runningBaseline(c),sessions=[],unmet=[],notes=rec.notes.slice();
    notes.push('Goals are ordered by priority. Requested frequencies are ceilings. Missed workouts are not added as training debt.');
    if(!base.minutes && c.goals.some(g=>g.discipline==='running')) notes.push('No established running baseline: start with short run/walk sessions.');
    const runGoal=c.goals.find(g=>g.discipline==='running'),runCount=runGoal?.sessions||1;
    let runMinutes=base.minutes>0?Math.floor(base.minutes/runCount):20;
    runMinutes=Math.min(c.availability.minutes,Math.max(10,runMinutes));
    if(rec.reduced) runMinutes=Math.max(10,Math.floor(runMinutes*.65));
    const id='e2-'+hash({c,start,weeks,policy:POLICY});
    if(!rec.blocked) for(let week=0;week<weeks;week++) {
      const ws=add(start,week*7),weekSessions=[];
      let allocatedRunning=0;
      const slots=Array.from({length:7},(_,i)=>add(ws,i)).filter(d=>c.availability.days.includes(dow(d)));
      for(const goal of c.goals) for(let index=0;index<goal.sessions;index++) {
        const d=goal.discipline;
        let p,minutes,demanding=false,title,rpe=3,kind='easy';
        if(d==='running') {
          // Different durations without increasing the established weekly budget.
          const weights=Array.from({length:goal.sessions},(_,i)=>c.abilities.running>0 && goal.sessions>1 && i===goal.sessions-1?1.3:1);
          const weighted=base.minutes>0?Math.floor(base.minutes*weights[index]/weights.reduce((a,b)=>a+b,0)):20;
          runMinutes=Math.min(c.availability.minutes,Math.max(10,weighted));
          if(rec.reduced)runMinutes=Math.max(10,Math.floor(runMinutes*.65));
          // Do not turn tiny/sparse recorded volumes into a high-frequency starter plan.
          if(base.minutes>0 && allocatedRunning+runMinutes>base.minutes*(rec.reduced?.65:1)+1) {unmet.push({week,discipline:d,reason:'Requested runs exceed the established weekly duration budget.'});continue;}
          const quality=index===0 && !rec.reduced && (c.qualitySessions??1)>0 && c.abilities.running>0 && base.minutes>=90 && runMinutes>=30;
          const long=index===goal.sessions-1 && goal.sessions>1 && c.abilities.running>0 && base.minutes>=90 && !quality;
          kind=quality?'threshold':long?'long':'easy'; demanding=quality||long;minutes=runMinutes;rpe=quality?6:3;
          title=quality?'Controlled threshold intervals':c.abilities.running===0?'Easy run/walk':long?'Long easy run':'Easy run';
          p={warmupMin:quality?10:5,mainMin:minutes-(quality?15:10),cooldownMin:5,rpe,paceSecPerKm:null,intervals:quality?{reps:3,workSec:120,recoverySec:120}:null,cue:c.abilities.running===0?'Alternate 1 minute gentle jogging and 1 minute walking. Keep conversation easy.':'Use conversational effort for easy work; no pace inferred from missing race data.'};
        } else if(d==='conditioning') {minutes=Math.min(c.availability.minutes,rec.reduced?15:20);title='Easy aerobic conditioning';p={mode:'brisk walking',rpe:3,cue:'Comfortable walking on a suitable route. Slow down if conversation is difficult.'};}
        else {p=resistance(c,d,rec);if(p.unmet){unmet.push({week,discipline:d,reason:p.unmet});continue;} minutes=p.minutes;demanding=p.exercises.some(e=>e.lower);title=d==='strength'?'Full-body strength':'Calisthenics foundations';rpe=5;}
        const candidates=slots.filter(date=>!weekSessions.some(s=>s.date===date) && weekSessions.length<6 && (!demanding || !sessions.concat(weekSessions).some(s=>s.demanding && Math.abs(gap(date,s.date))<=1)));
        if(kind==='long' && c.longRunDay!=null) candidates.sort((a,b)=>(dow(a)===c.longRunDay?-1:0)-(dow(b)===c.longRunDay?-1:0));
        const date=candidates[0];
        if(!date) {unmet.push({week,discipline:d,reason:'No slot fits availability and shared recovery spacing.'});continue;}
        weekSessions.push({id:id+'-w'+week+'-'+d+'-'+index,week,date,discipline:d,title,kind,minutes,rpe,demanding,prescription:p,why:'Coordinated with all disciplines; '+(rec.reduced?'reduced recovery draft.':d==='running'?'volume based on '+base.source+'.':'equipment and technique gate progression.')});
        if(d==='running')allocatedRunning+=minutes;
        if(p.missing?.length) unmet.push({week,discipline:d,reason:'Equipment does not support these movement patterns: '+p.missing.join(', ')+'. No equivalent is invented.'});
      }
      sessions.push(...weekSessions);
    }
    const plan={schemaVersion:VERSION,policyVersion:POLICY,id,start,end:add(start,weeks*7-1),weeks,context:c,sessions:sessions.sort((a,b)=>a.date.localeCompare(b.date)||a.id.localeCompare(b.id)),unmet,notes,recovery:rec};
    check(plan);return plan;
  }
  function check(plan) {
    const seen=new Set(),dates=new Set();
    for(const s of plan.sessions) {
      assert(!seen.has(s.id),'Duplicate session id');seen.add(s.id);
      assert(validDate(s.date)&&s.date>=plan.start&&s.date<=plan.end,'Session outside plan horizon');
      assert(!dates.has(s.date),'Multiple sessions per day');dates.add(s.date);
      assert(s.minutes<=plan.context.availability.minutes,'Session exceeds available time');
      if(s.demanding) assert(!plan.sessions.some(t=>t.id!==s.id&&t.demanding&&Math.abs(gap(t.date,s.date))<=1),'Demanding sessions are adjacent');
    }
    // Includes boundaries between nominal training weeks.
    for(let d=plan.start;d<=plan.end;d=add(d,1)) assert(plan.sessions.filter(s=>s.date>=d&&s.date<add(d,7)).length<=6,'No rolling-week rest day');
    return true;
  }
  function summary(plan) {
    const v={minutes:{},resistanceSets:0,demandingExposures:0};
    for(const s of plan.sessions){v.minutes[s.discipline]=(v.minutes[s.discipline]||0)+s.minutes;v.resistanceSets+=(s.prescription.exercises||[]).reduce((t,e)=>t+e.sets,0);v.demandingExposures+=+s.demanding;}return v;
  }
  function proposal(previous, input, options={}) {
    const c=context(input),start=options.start||c.asOf,weeks=options.weeks||4;
    assert(!previous || previous.schemaVersion===VERSION,'Unsupported accepted plan');
    let plan=build(c,start,weeks);
    const baseRevision=previous?.revision||0;
    // Existing completed/past prescriptions remain immutable audit history. New horizon starts today or later.
    if(previous) {
      const completed=new Set(actual(c).map(h=>h.sessionId).filter(Boolean));
      const retained=previous.plan.sessions.filter(s=>s.date<c.asOf || completed.has(s.id)).map(clone);
      plan.sessions=retained.concat(plan.sessions.filter(s=>!retained.some(t=>t.date===s.date)));
      plan.start=retained.length?retained.reduce((d,s)=>s.date<d?s.date:d,plan.start):plan.start;
      // Never schedule new work next to a retained demanding exposure.
      const retainedIds=new Set(retained.map(s=>s.id));
      plan.sessions=plan.sessions.filter(s=>retainedIds.has(s.id)||!s.demanding||!retained.some(t=>t.demanding&&Math.abs(gap(s.date,t.date))<=1));
      check(plan);
    }
    const prior=previous?.plan.sessions||[];
    const changes={previousSessions:prior.length,suggestedSessions:plan.sessions.length,previousWorkload:previous?summary(previous.plan):null,suggestedWorkload:summary(plan),reason:c.recovery.status,missed:c.missed.length};
    return {schemaVersion:VERSION,baseRevision,request:{context:c,start,weeks},plan,changes,requiresApproval:true};
  }
  function accept(previous, draft, approved) {
    assert(approved===true,'Explicit user approval is required');
    assert(draft && draft.schemaVersion===VERSION && draft.baseRevision===(previous?.revision||0),'Stale plan proposal; review a fresh preview');
    const fresh=proposal(previous,draft.request.context,{start:draft.request.start,weeks:draft.request.weeks});
    assert(canonical(fresh)===canonical(draft),'Proposal changed; generate a fresh preview');
    return {schemaVersion:VERSION,revision:draft.baseRevision+1,plan:clone(draft.plan)};
  }
  return {VERSION,POLICY,DISC,context,actual,assess,build,proposal,accept,check,summary,canonical,add,gap,validDate};
});
