/* This adapter is inserted inside the accepted application's closure. */
window.StrideLabEngine2Host = {
  snapshot: () => JSON.parse(JSON.stringify(S)),
  record: function (entry, feedback) {
    const disk=localStorage.getItem(K);
    if(disk!==JSON.stringify(S))throw Error('Training data changed in another tab. Reload before saving.');
    const next=Object.assign({},S,{engine2History:[...(S.engine2History||[]),entry],engine2Feedback:[...(S.engine2Feedback||[]),...feedback]});
    // Reuse model validation before persistence; never accept a malformed manual history item.
    window.StrideLabEngine2.context({...S.engine2.plan.context,asOf:entry.date,history:next.engine2History,feedback:next.engine2Feedback});
    localStorage.setItem(K,JSON.stringify(next));S=next;
  },
  apply: function (draft) {
    const Engine2=window.StrideLabEngine2;
    const disk=JSON.parse(localStorage.getItem(K)||'{}');
    if(JSON.stringify(disk.engine2||null)!==JSON.stringify(S.engine2||null)) throw Error('Another tab changed the plan. Reload and review a new preview.');
    const accepted=Engine2.accept(S.engine2||null,draft,true),p=this.projection(accepted.plan);
    // Preserve writes from other tabs rather than overwriting unknown local keys.
    if(JSON.stringify(disk)!==JSON.stringify(S)) throw Error('Training data changed. Reload before approving this plan.');
    const next=Object.assign({},S,{engine2:accepted,gp:null,mv:{},plan:'on',tab:'training',sel:draft.request.start});
    localStorage.setItem(K,JSON.stringify(next));
    S=next;publishProjection(p);render();
    return accepted;
  },
  projection: function (plan) {
    const disc={running:'run',strength:'str',calisthenics:'cal',conditioning:'run'};
    const legacy={start:plan.start,end:plan.end,sessions:plan.sessions.map(s=>{
      const p=s.prescription;
      return {id:s.id,date:s.date,week:Math.floor(Engine.gap(s.date,plan.start)/7),disc:disc[s.discipline],title:s.title,mins:s.minutes,why:s.why,
        steps:['running','conditioning'].includes(s.discipline)?[['Warm-up',`${p.warmupMin||5} min easy`],['Main set',p.intervals?`${p.intervals.reps} × ${p.intervals.workSec/60} min controlled, ${p.intervals.recoverySec/60} min easy between. Remaining time easy. RPE ${p.rpe}.`:(p.cue||'Easy conversational effort')],['Cooldown',`${p.cooldownMin||5} min easy`]]:null,
        ex:(p.exercises||[]).map(e=>({n:e.name,sets:e.sets,reps:e.reps,eq:e.equipment.join(' + ')||'Bodyweight',cue:`${e.cue} Rest ${e.restSec} sec.`,pr:[e.name]}))};
    })};
    return planProjection(legacy);
  },
  restore: function () {
    if(!S.engine2)return;
    if(S.engine2.schemaVersion!==2)throw Error('This saved plan requires a newer engine. Existing data was preserved.');
    window.StrideLabEngine2.check(S.engine2.plan);
    publishProjection(this.projection(S.engine2.plan));render();
  }
};
