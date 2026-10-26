(function(){
  'use strict';
  const E=window.StrideLabEngine2,H=window.StrideLabEngine2Host;
  const esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const localDate=d=>[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');
  const today=()=>localDate(new Date());
  const label={running:'Running',strength:'Strength',calisthenics:'Calisthenics',conditioning:'Conditioning'};
  let draft=null,dialog=null,opener=null;
  const style=document.createElement('style');style.textContent=`
  #e2-dialog{background:var(--bg,#151719);color:var(--tx,#f4f1ea);border:1px solid #666;border-radius:20px;padding:0;width:min(700px,calc(100% - 20px));max-height:90dvh;overflow:auto;overscroll-behavior:contain;box-sizing:border-box}
  #e2-dialog::backdrop{background:#000b}#e2-dialog *{box-sizing:border-box}#e2-dialog .e2-pad{padding:20px}#e2-dialog label{display:block;margin:12px 0;font-size:1rem}#e2-dialog select,#e2-dialog input:not([type=checkbox]){display:block;width:100%;background:#262a2d;color:inherit;border:1px solid #777;border-radius:8px;padding:10px;font:inherit;margin-top:5px}#e2-dialog fieldset{border:1px solid #555;border-radius:12px;padding:12px;margin:15px 0;min-width:0}#e2-dialog .e2-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,180px),1fr));gap:12px}#e2-dialog .e2-inline{display:flex;flex-wrap:wrap;gap:10px}#e2-dialog .e2-inline label{margin:6px}#e2-dialog button{white-space:normal;overflow-wrap:anywhere;min-height:44px}#e2-dialog p,#e2-dialog li{overflow-wrap:anywhere;line-height:1.5}#e2-dialog .e2-item{border-top:1px solid #555;padding:12px 0}#e2-message{color:#f7c987}#e2-dialog [hidden]{display:none!important}#e2-dialog h2{margin-top:18px}#e2-dialog summary{cursor:pointer;padding:10px 0;min-height:44px}#e2-open{margin:8px 0}#e2-dialog :focus-visible{outline:3px solid #f7c987;outline-offset:3px}
  `;document.head.append(style);
  function mount(){const app=document.querySelector('#app'),s=H.snapshot();if(!app || s.tab!=='training'||document.querySelector('#e2-open'))return;const b=document.createElement('button');b.id='e2-open';b.className='chip';b.textContent='Unified coach · Engine 2.0';b.addEventListener('click',open);(app.querySelector('.top')||app).append(b);}
  function history(s){
    const out=(s.engine2History||[]).slice(),ids=new Set(out.map(h=>h.id));
    for(const a of s.p4dImports||[]) if(E.validDate(a.date)&&a.date<=today()&&typeof a.durationSec==='number') {const id='import:'+a.id;if(!ids.has(id)){out.push({id,date:a.date,discipline:'running',source:'recorded',completed:true,minutes:a.durationSec/60,distanceKm:a.distanceKm});ids.add(id);}}
    for(const [ts,v] of Object.entries(s.log||{})) {
      if(!v.workoutId?.startsWith('e2-') || !Array.isArray(v.ex))continue;
      const session=s.engine2?.plan.sessions.find(x=>x.id===v.workoutId),date=localDate(new Date(+ts));
      const id='log:'+ts;if(!session||!E.validDate(date)||date>today()||ids.has(id)||out.some(h=>h.sessionId===session.id))continue;
      // Legacy strength logger has no measured duration or technique feedback.
      out.push({id,date,discipline:session.discipline,source:'manual',completed:true,sessionId:session.id,minutes:0,durationMissing:true});ids.add(id);
    }
    return out;
  }
  function readContext(){
    const s=H.snapshot(),old=s.engine2?.plan.context;
    return {...(old||{schemaVersion:2,goals:[{discipline:'running',sessions:3}],abilities:{running:0,strength:0,calisthenics:0,conditioning:0},availability:{days:[1,3,5,0],minutes:45},equipment:[]}),asOf:today(),history:history(s),feedback:s.engine2Feedback||[],missed:[]};
  }
  const options=(items,sel)=>items.map(([v,t])=>`<option value="${esc(v)}" ${String(v)===String(sel)?'selected':''}>${esc(t)}</option>`).join('');
  function close(){if(dialog){dialog.close();dialog.remove();dialog=null;draft=null;opener?.focus();}}
  function open(){
    if(dialog)return;opener=document.activeElement;const c=readContext(),s=H.snapshot();
    dialog=document.createElement('dialog');dialog.id='e2-dialog';dialog.setAttribute('aria-labelledby','e2-title');
    dialog.innerHTML=`<div class="e2-pad"><div class="row sp"><h1 id="e2-title" style="font-size:1.6rem">Your unified coach</h1><button class="chip" id="e2-close" aria-label="Close unified coach">Close</button></div><p>Build one coordinated plan. Preview recommendations, then approve changes to your calendar. Your current plan stays active until approval.</p><p class="sm mu">Foundation version: conservative general fitness programming. No race-specific periodization or medical clearance. Everything here works offline.</p>
    <form id="e2-form"><fieldset><legend>Goals in priority order</legend><label>Primary goal<select id="e2-primary">${options(E.DISC.map(d=>[d,label[d]]),c.goals[0].discipline)}</select></label>
    ${E.DISC.map(d=>`<div class="e2-grid"><label><input type="checkbox" name="goal" value="${d}" ${c.goals.some(g=>g.discipline===d)?'checked':''}> ${label[d]}</label><label>Requested ${label[d].toLowerCase()} sessions / week<input aria-label="${label[d]} sessions" id="e2-freq-${d}" type="number" min="1" max="6" value="${c.goals.find(g=>g.discipline===d)?.sessions||2}"></label><label>${label[d]} experience<select id="e2-ability-${d}">${options([[0,'Beginner'],[1,'Established'],[2,'Experienced']],c.abilities[d])}</select></label></div>`).join('')}</fieldset>
    <fieldset><legend>Your schedule</legend><div class="e2-inline">${['Sun','Mon','Tue','Wed','Thu','Fri','Sat'].map((d,i)=>`<label><input type="checkbox" name="day" value="${i}" ${c.availability.days.includes(i)?'checked':''}> ${d}</label>`).join('')}</div><label>Minutes available per session<input id="e2-minutes" type="number" min="15" max="180" value="${c.availability.minutes}"></label><label>Current recovery<select id="e2-recovery">${options([['normal','Feeling well'],['fatigued','Unusually tired'],['returning','Returning after a break or recovery'],['ill','Currently ill'],['injured','Current injury'],['redFlag','Concerning symptoms: stop and seek assessment']],c.recovery?.status||'normal')}</select></label></fieldset>
    <fieldset><legend>Available equipment</legend><p>Only select equipment you can safely use. Bodyweight exercises are available by default.</p><div class="e2-inline">${[['dumbbell','Dumbbell'],['band','Resistance band'],['bar','Secure pull-up bar'],['stableSurface','Stable raised surface']].map(([id,n])=>`<label><input type="checkbox" name="equipment" value="${id}" ${c.equipment.some(e=>e.id===id)?'checked':''}> ${n}</label>`).join('')}</div></fieldset>
    <details><summary>Advanced controls and recent interruptions</summary><label>Typical completed running minutes / week (used only without recent recorded runs)<input id="e2-baseline" type="number" min="0" max="1000" value="${c.runningBaselineMinutes||0}"></label><label>Running quality sessions / week<select id="e2-quality">${options([[0,'None'],[1,'Up to one, when readiness and history support it']],c.qualitySessions??1)}</select></label><label>Preferred long run day<select id="e2-long">${options([['','Let coach choose'],...['Sun','Mon','Tue','Wed','Thu','Fri','Sat'].map((d,i)=>[i,d])],c.longRunDay??'')}</select></label><label>Missed work since your last review<select id="e2-missed">${options([['','No interruption'],['busy','Busy / schedule conflict'],['tired','Fatigue'],['sick','Illness still needs recovery review'],['injured','Injury still needs recovery review'],['other','Other']], '')}</select></label><label>Previous night sleep hours (optional)<input id="e2-sleep" type="number" min="0" max="24" step="0.1"></label><label>Your usual sleep hours (optional)<input id="e2-sleep-base" type="number" min="1" max="24" step="0.1"></label></details>
    <button class="btn" type="submit">Preview coordinated plan</button></form><div id="e2-message" role="status" aria-live="polite"></div><section id="e2-preview" aria-label="Suggested plan"></section>
    ${s.engine2?'<details><summary>Record completed training and technique feedback</summary><p>Enter what you actually completed. This supports the next preview; it does not automatically change your plan.</p><form id="e2-log-form"><label>Planned session<select id="e2-session">'+options(s.engine2.plan.sessions.filter(x=>x.date<=today()).map(x=>[x.id,x.date+' · '+x.title]),'')+'</select></label><label>Actual minutes<input id="e2-actual-min" type="number" min="1" max="1440" required></label><label>Session effort (RPE 1–10)<input id="e2-rpe" type="number" min="1" max="10" required></label><label><input id="e2-technique" type="checkbox"> Every prescribed repetition was completed with controlled technique and no pain</label><label><input id="e2-pain" type="checkbox"> I felt pain (do not progress)</label><button class="btn" type="submit">Save training feedback</button></form></details>':''}
    </div>`;
    document.body.append(dialog);dialog.showModal();dialog.querySelector('#e2-close').onclick=close;
    dialog.addEventListener('cancel',e=>{e.preventDefault();close();});
    dialog.querySelector('#e2-form').addEventListener('submit',e=>{e.preventDefault();preview();});
    dialog.querySelector('#e2-form').addEventListener('input',()=>{draft=null;dialog.querySelector('#e2-preview').replaceChildren();});
    dialog.querySelector('#e2-log-form')?.addEventListener('submit',e=>{e.preventDefault();record();});
  }
  function message(t){dialog.querySelector('#e2-message').textContent=t;}
  function preview(){try{
    const c=readContext(),q=id=>dialog.querySelector('#e2-'+id),primary=q('primary').value;
    const selected=[...dialog.querySelectorAll('[name=goal]:checked')].map(x=>x.value);
    if(!selected.includes(primary))throw Error('Select your primary discipline in the goal list.');
    c.goals=[primary,...selected.filter(d=>d!==primary)].map(d=>({discipline:d,sessions:+q('freq-'+d).value}));
    c.abilities=Object.fromEntries(E.DISC.map(d=>[d,+q('ability-'+d).value]));
    c.availability={days:[...dialog.querySelectorAll('[name=day]:checked')].map(x=>+x.value),minutes:+q('minutes').value};
    c.equipment=[...dialog.querySelectorAll('[name=equipment]:checked')].map(x=>({id:x.value,quantity:1}));
    c.runningBaselineMinutes=+q('baseline').value;c.qualitySessions=+q('quality').value;c.recovery={status:q('recovery').value};
    if(q('long').value!=='')c.longRunDay=+q('long').value;else delete c.longRunDay;
    c.missed=q('missed').value?[{sessionId:'self-reported-interruption',reason:q('missed').value}]:[];
    c.wellness={};if(q('sleep').value!=='')c.wellness.sleepHours=+q('sleep').value;if(q('sleep-base').value!=='')c.wellness.sleepBaselineHours=+q('sleep-base').value;
    draft=E.proposal(H.snapshot().engine2||null,c,{weeks:4});
    const p=draft.plan,work=E.summary(p),container=q('preview');
    container.innerHTML=`<h2>Suggested plan</h2><p>${p.sessions.length} sessions across this draft. ${Object.entries(work.minutes).map(([d,m])=>esc(label[d])+': '+m+' min').join(' · ')||'Recovery hold'}. ${draft.baseRevision?'Your accepted plan has '+draft.changes.previousSessions+' sessions.':'No unified plan accepted yet.'}</p>${p.notes.map(n=>`<p>${esc(n)}</p>`).join('')}<details ${p.unmet.length?'open':''}><summary>Scheduling limits and equipment gaps (${p.unmet.length})</summary><ul>${[...new Set(p.unmet.map(x=>x.reason))].map(n=>`<li>${esc(n)}</li>`).join('')}</ul></details><h2>First week</h2>${p.sessions.filter(s=>s.date>=c.asOf&&s.date<E.add(c.asOf,7)).map(s=>`<div class="e2-item"><b>${esc(s.date)} · ${esc(s.title)}</b><p>${s.minutes} min · ${esc(s.why)}</p>${(s.prescription.exercises||[]).map(x=>`<p>${esc(x.name)}: ${x.sets} × ${x.reps}, ${x.rir} reps in reserve, rest ${x.restSec}s.</p>`).join('')}${s.discipline==='running'?`<p>${esc(s.prescription.cue)}</p>`:''}</div>`).join('')}<button id="e2-approve" class="btn">Approve and replace my calendar plan</button><p class="sm mu">This is a significant plan change. Approval saves the draft locally and replaces future calendar suggestions. Completed training history stays available.</p>`;
    q('approve').onclick=()=>{try{H.apply(draft);close();}catch(e){message(e.message);}};message('Preview ready. Review the suggested schedule and limits before approving.');
  }catch(e){draft=null;message(e.message);}}
  function record(){try{
    const s=H.snapshot(),q=id=>dialog.querySelector('#e2-'+id),session=s.engine2.plan.sessions.find(x=>x.id===q('session').value);
    if(!session)throw Error('No past or current session is available to record.');
    const id='manual:'+session.id+':'+today();if((s.engine2History||[]).some(h=>h.id===id))throw Error('Feedback for this session is already recorded today.');
    const clean=q('technique').checked,pain=q('pain').checked;
    const entry={id,sessionId:session.id,date:today(),discipline:session.discipline,source:'manual',completed:true,minutes:+q('actual-min').value,rpe:+q('rpe').value};
    const feedback=(session.prescription.exercises||[]).map(e=>({historyId:id,exerciseId:e.id,stage:e.stage,success:clean&&!pain,technique:clean,pain}));
    H.record(entry,feedback);draft=null;dialog.querySelector('#e2-preview').replaceChildren();message('Training feedback saved. Generate a new preview to review any progression.');
  }catch(e){message(e.message);}}
  // Prevent legacy edit/move controls bypassing unified recovery and approval checks.
  document.addEventListener('click',e=>{const b=e.target.closest('[data-a]');if(!b||!H.snapshot().engine2)return;if(['move','mv','edit','swap'].includes(b.dataset.a)&&String(b.dataset.v||'').includes('e2-')){e.preventDefault();e.stopImmediatePropagation();alert('Review a coordinated plan change through Unified coach.');}},true);
  try{H.restore();}catch(e){const p=document.createElement('p');p.setAttribute('role','alert');p.textContent=e.message;document.querySelector('#app').prepend(p);}
  new MutationObserver(mount).observe(document.querySelector('#app'),{childList:true,subtree:true});mount();
  window.StrideLabEngine2UI={open,history};
})();
