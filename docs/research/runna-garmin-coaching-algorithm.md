# Runna and Garmin Run Coach: Behavioral Algorithm Research

**Reviewed:** 2026-11-02  
**Status:** Public-documentation analysis; no proprietary algorithm reconstruction claimed  
**Scope:** Independent coaching research for StrideLab Engine 1.5 and Engine 2.0  
**Implementation:** Research only. No production coaching-code changes.

## Purpose and limits

Investigate the *observable* rules behind running plan generation, progression, workout selection, and adaptation. Distinguish (1) vendor-documented behavior, (2) independently observed output, (3) inferred explanations, and (4) untested hypotheses. A vendor's plans are comparison data, **not** proof of physiological superiority.

Research methods: public vendor guidance, authorized manual plan generation/export, time-stamped observations, and independent comparison to exercise-science literature. Do not decompile binaries, scrape private APIs, bypass protections, or copy proprietary source code.

### Evidence legend

- **DOCUMENTED** — vendor explicitly describes the behavior (not independently verified).
- **OBSERVED** — reproduced from accessible app output or a verified plan export, with input metadata.
- **INFERRED** — consistent with evidence but alternative explanations remain.
- **UNKNOWN** — vendor does not disclose enough detail; test before implementation.

## Runna: planning controls and progression

| Topic | Evidence and interpretation | Status |
| --- | --- | --- |
| Race/distance plan baseline | Ability level, current weekly mileage and longest run establish initial workload; recent race times inform pace targets. | DOCUMENTED [R1][R2] |
| Baseline mileage window | Runna advises estimating weekly mileage from recent four-week training, accounting conservatively for recovery weeks and the longest repeatable run. | DOCUMENTED [R2] |
| Weekly progression | Race/distance plans use an increasing curve constrained by a weekly increase ceiling dependent on current mileage. Exact formula/thresholds undisclosed. | DOCUMENTED / exact algorithm UNKNOWN [R1] |
| Long-run progression | Long run is managed independently from weekly total; ability-dependent protection can reduce an otherwise too-long run. | DOCUMENTED [R1][R4] |
| Ability and volume | More experienced runners may receive higher total targets, not automatically faster build rates. Runs per week and plan duration also influence targets. | DOCUMENTED [R1][R3] |
| Training volume preference | Progressive, Steady, Gradual determine total mileage and rate of progression. | DOCUMENTED [R3] |
| Difficulty preference | Challenging, Balanced, Comfortable alter speed-work quantity/difficulty and some faster long-run components; not identical to volume preference. | DOCUMENTED [R3] |
| Non-race plan progression | Some non-distance plans use fixed weekly percentage increases scaled by ability and running frequency. Exact percentages not disclosed. | DOCUMENTED [R1] |
| Maintenance plan | Targets a relatively stable mileage, allowing some variation and retaining deloads. | DOCUMENTED [R5] |
| Deload | Hard workouts are reduced more than easy runs; long runs are also reduced according to ability and accumulated distance. | DOCUMENTED [R4] |
| Taper | Runna currently describes 3-week taper for half-marathon-and-longer or plans of at least 10 weeks; 2-week taper for shorter-distance/shorter plans. Applicability must be checked by plan type. | DOCUMENTED [R4] |
| Interrupted training | Illness/unwell mode can restrict workload up to 14 days and reintroduce easy, long and speed sessions in stages; holiday mode rearranges volume/deloads. | DOCUMENTED [R4][R6] |
| Missed-workout handling | Plan Realignment for multiple missed workouts/weeks offers skip, rearrange, rebuild/extend options; requires user choices and has plan-type exceptions. | DOCUMENTED [R7] |
| Pace insight adjustment | Actual vs target pace patterns in target-pace sessions can generate suggested changes; acceptance is controlled by runner. Numerical sensitivity not disclosed. | DOCUMENTED [R8] |

**Important nuance:** Runna's documented controls are not proof it internally uses separate software modules. They establish distinct *observable effects*, which suggests a useful independent design.

### Runna unknowns worth isolating

1. Functional relationship among starting mileage, running ability, days/week, plan length, and first/peak-week distance.
2. Long-run caps, progression increments and intervention thresholds.
3. Rules mapping distance goals and running frequency to hard-workout count/type (tempo, intervals, hills, threshold).
4. Pace-prescription formulas, sensitivity to race performance and cross-distance predictions.
5. How deload and taper rules interact with interruptions, schedule availability and long-run caps.
6. Whether plan regeneration is deterministic at fixed settings and app version.

## Garmin Run Coach: adaptive decision inputs

Garmin's technology page says Run Coach adapts recommendations to training performance and context. The following is **vendor-documented behavior**; exact proprietary functions and weights remain UNKNOWN.

| Topic | Documented behavior |
| --- | --- |
| Training load | EPOC-derived exercise load; acute load reflects recent activity; chronic load reflects roughly four weeks of training. [G1][G2] |
| Load focus | Considers low-aerobic, high-aerobic and anaerobic contributions rather than merely total load. A missing category does *not* automatically trigger that category next. [G1] |
| Adaptive inputs | Performance, recorded heart rate and pace, recovery/sleep, missed workouts and additional sessions can influence workout recommendations. [G1] |
| Periodization | For a dated event: base, build, peak, taper and race phases. Garmin describes an approximately 10-day taper and phased durations that depend on the event timeline. [G1] |
| Goal-less coaching | Training follows a more general fitness-improvement pattern with harder and easier periods. [G1] |
| Cross-training | Garmin encourages recording non-running activities with heart rate, where supported, to account for their training impact. [G1] |
| Load mathematics | Garmin separately explains that some recent-load features use a weighted moving average normalized to a seven-day view rather than a simple unweighted 7-day sum. Exact handling is device/feature-dependent. [G2] |

**Do not conflate:** Garmin **Run Coach**, **Daily Suggested Workouts (DSW)**, and Garmin's named **Expert Coach plans**. Record which system generated every observed prescription; their logic and device availability may differ.

### Garmin unknowns worth isolating

- How competing drivers are prioritized: recovery vs load focus vs event phase vs available time.
- When it changes workout *type* compared with workout *duration*, intensity or rest intervals.
- How many observations it needs to update ability estimates and targets.
- How it incorporates missed sessions, unplanned workouts and non-running load.
- What is unavailable on different watches (do not assume every Garmin metric is present on Forerunner 165).

## Controlled Runna experiment design

**Reference synthetic runner** (example, not a real identity):
- Recent 5K 27:00; 40 km/week; four run days/week; recent longest run 14 km; intermediate; 12-week 5K goal.
- Hold plan type, target race date, difficulty, volume preference, equipment and calendar constraints constant unless explicitly varied.
- Change **one independent input per comparison**:
  1. Weekly mileage 25 vs 40 vs 65 km.
  2. Longest recent run 10 vs 14 vs 20 km.
  3. Training days 3 vs 4 vs 5.
  4. Recent 5K 25:00 vs 27:00 vs 30:00.
  5. Volume preference Gradual vs Steady vs Progressive.
  6. Difficulty Comfortable vs Balanced vs Challenging.
  7. Plan length 12 vs 16 weeks.

Record *exact* submitted inputs, plan type/version, access date, any rejected input bounds, exported output, units, and workout-by-workout prescriptions. Repeat baseline generation if permitted to assess stability.

Output fields: week index, session date/type, prescribed distance/duration, warm-up/cooldown, work/recovery repetitions, intensity/pace target, total hard-session time, long-run share, deload/taper indicators, and any alterations after missed workouts.

**High-value test for StrideLab:** Two runners with identical 27:00 5K performance but different sustained training histories (25 vs 65 km/week). Compare **starting mileage, weekly peak, long-run volume, hard-workout selection, and pace targets** separately. Do not interpret higher volume as evidence of greater speed ability.

## Garmin observational protocol

A Garmin recommendation is an **output**; the completed FIT activity is not itself a recommendation. Capture the prescription **before** exercise, then snapshot available context (date, exact coach system, device/version, recent sessions, exercise load if available, recovery time, sleep/readiness if available, race date and phase), the workout actually completed, and the next recommendation. Mark missing/unavailable metrics explicitly; do not impute.

Avoid intentionally impairing sleep, recovery, or training just to induce a different suggestion. Observational associations are not causal proof, particularly when training load and recovery change together.

## StrideLab implications — proposals, not adopted engine rules

1. **Separate training capacity from performance.** Mileage history, long-run history, consistency and ability to tolerate sessions must not be replaced by a single 5K/VO2max-derived estimate.
2. **Separate prescription dimensions.** Volume, hard-session dose, intensity targets and scheduling need independently testable constraints.
3. **Recovery is structural.** Treat deload, taper, interruptions and long-run safeguards differently; avoid crude uniform percentage reductions.
4. **Keep athlete approval.** Proposal/acceptance must be clear for any substantial plan revision.
5. **Personal aerobic-efficiency research.** For the initial athlete, measure matched-heart-rate speed and drift using comparable routes and conditions; distinguish road runs from tight loops, and account for heat/humidity, hills and fatigue. These are *StrideLab research proposals*, not Runna/Garmin algorithms.
6. **Engine version policy.** Candidate low-risk deterministic rules may inform Engine 1.5 only after independent validation; physiological adaptive models belong in Engine 2.0 until evaluated. Preserve Engine 1.0 comparison behavior and avoid touching pending Engine 2.0 work.

## Source quality and unverified historical sample analysis

Runna's JustGiving partnership publishes eight example plans covering 5K through marathon [P1]. These provide useful reference material, **not** matched-input generated plans. Previously discussed numerical summaries of sample weekly mileage and long-run shares are **not accepted as validated results in this document**; they require independent re-extraction with exact PDFs, units, session totals and week/race handling before inclusion.

Each future observed finding should record:
- Source/plan URL or consented export identifier, retrieved date, app version if known.
- Exact input configuration and output fields.
- Calculation method and reproducibility checks.
- Hypothesis, confounders, contradicting evidence, confidence and source class.
- Whether medically relevant coaching constraints have been independently reviewed.


## Additional verified findings (2026-11-02)

### Runna: separate adaptation channels

**Vendor-documented; exact detection thresholds UNKNOWN.**

| Channel | Qualifying evidence | Trigger / output | Constraints |
| --- | --- | --- | --- |
| **Mileage Insights** | Planned sessions **and** free/instant runs, including completed mileage | States such as on-track, ahead, behind or significantly behind; may recommend changes to weekly mileage, long-run distance, run days, or rebuilding | Currently race plans only. Recommendations can exceed limits allowed by ability level and scheduled days. Accepting/dismissing or manually editing a plan resets insight state. [R9] |
| **Pace Insights** | Eligible target-pace sessions: intervals, tempos, time trials and some long runs; consistency of target vs achieved reps | Suggests higher/lower pace targets only after patterns; runner must accept | Free runs/manual uploads are not eligible; updates **only pace targets**, not session schedule; does not yet account fully for weather, terrain, poor sleep or fueling. [R8] |
| **Plan Realignment** | More than 3 missed workouts or a missed week, with sessions left uncompleted rather than explicitly skipped; usually checks around Monday | May offer skip, rearrange, extend, rebuild or restart depending on plan type, length and absence | Options are situational; not proof of automatic workout-type adaptation; previous action cannot necessarily be undone. [R7] |

**Interpretation (INFERRED design decomposition):** planned workload, pace performance, and interruption recovery appear to be *separate observable decision problems*. This does not prove the vendor's internal code architecture.

**Additional training-preference constraints:** v7.2+ eligible plans can cap easy-run, long-run and hard-session length separately; set hard-session count and structured long-run difficulty; volume and difficulty settings do **not** directly change interval pace targets. Some preferences are rejected when the combination cannot meet a near-term race goal. The documented default is Progressive volume + Challenging difficulty; these defaults should not be assumed physiologically optimal for all athletes. Runna's non-running strength/yoga sessions are not changed by these preferences. [R3]

**Researchable state-machine hypothesis:** Maintain three independent status estimators:
1. **Capacity / exposure:** actual vs prescribed recent mileage and longest-run history.
2. **Performance calibration:** achieved vs prescribed work-interval pace with consistent repeated evidence.
3. **Plan validity:** interrupted training and feasibility against race date, recent exposure and available run days.

Never reduce these into a single arbitrary readiness score without validation. This is a proposed StrideLab research design, **not** verified Runna source logic.

### Garmin: weighted load and event phase constraints

**Vendor-documented; device/version support varies.**

- Garmin's newer acute-load explanation describes a **weighted moving average** of workout load whose contribution gradually expires over about **10 days** and is normalized to a **7-day** perspective. Older devices may use an unweighted rolling 7-day sum. This is not identical to counting mileage, and a FIT workout alone does not expose all proprietary load computations. [G2]
- Garmin Customer Support additionally describes **chronic load** as a weighted average of acute load across the preceding **28 days**. Interpretations must be tagged by device and firmware since not every watch exposes these metrics. [G3]
- Garmin Run Coach's event-oriented periodization describes **base 3–9 weeks; build 3–9; peak 3–6; taper 10 days**. When an event is more than six months away, Garmin says event-specific periodization begins in the final six months, with general development beforehand; closer events condense the phases. [G1]
- Garmin's documented Run Coach plan offerings can cover **6–52 weeks** with eligible race/custom-distance goals and may use pace or HR targets; availability and details depend on device. The named expert plans and DSW are distinct outputs. [G4]
- Garmin's load-focus balancing is not simply an instruction to schedule the most underrepresented intensity category next. Recovery, race period and session sequencing are plausible interacting factors, but exact priority/weights are **UNKNOWN**. [G1]

### Critical comparability notes

- Vendor documentation is not the same thing as an observed controlled plan. Record exact access date, product version and plan type.
- **Garmin Run Coach, DSW and Garmin Expert Coach must never be pooled as one algorithm** without evidence they behave identically.
- Runna free runs may influence mileage recommendations but not Pace Insights; such eligibility differences matter when designing test datasets.
- Garmin device-level EPOC load cannot be reproduced exactly from distance/HR alone; use available measured values with explicit missingness and never claim proprietary equivalence.
- The published eight Runna/JustGiving plan examples are not matched-input controlled experiments; previously quoted weekly-mileage numbers remain UNVERIFIED in this repository until independently re-extracted.


## Further source review — Pace Insights, heat, target modes and plan feasibility (2026-11-02)

The observations below are based on vendor-published support material, not instrumented access to private app code. **Do not interpret documented functionality as recovered source code or exact numerical thresholds.**

### Runna Pace Insights: more than average completion pace

Runna's [Pace Insights Chart](https://support.runna.com/en/articles/10751290-pace-insights-chart) describes:
- **Five most recent eligible speed workouts** in the displayed chart, with fastest and slowest qualifying repetitions highlighted. This is a *chart window*, not proof that the underlying recommendation model uses only five workouts.
- **Normalization across repetitions and workouts** to compare different lengths and prescribed targets. Exact normalization formula UNKNOWN.
- **Outlier filtering**, with some unusual reps omitted from the display; missed/incomplete repetitions are also excluded.
- **Prior-recommendation isolation:** workouts marked `Outdated` belong to a previous pace recommendation and no longer influence current pace status.
- Pace status is based on **repeated patterns**, so a single on-target workout does not necessarily override earlier faster/slower evidence.

These imply a plausible, but **INFERRED** candidate for StrideLab: a rolling eligible-session performance ledger with work-interval deviations, dispersion and post-target-change cohort reset, rather than a simple latest-run average.

Runna's [Pace Insights overview](https://support.runna.com/en/articles/14656203-what-are-pace-insights-and-how-do-they-work) confirms that accepting a pace recommendation changes *pace targets*, not the entire session schedule, and that its performance model cannot fully account for weather, terrain, sleep and fueling. This qualification concerns **Pace Insights**, not all Runna features.

### Runna Pace Insights: explicit exclusion / data validity

Runna's [ineligible workout explanations](https://support.runna.com/en/articles/10855216-why-am-i-not-receiving-pace-insights) include manual linkage from a free run, legacy or instant workouts, substantial planned-vs-actual distance mismatch, incomplete reps, no valid repetitions, inconsistent GPS/interval distance, missing pace targets, extreme elevation and unreliable elevation sources.

**Research implication:** Use `data_validity`, `eligibility_reason`, and `confidence` on *each* activity before trying to recalibrate fitness. Keep actual activity ingestion broad, but narrow target-pace calibration to validated eligible work segments. A failed eligibility check must not silently become a negative performance signal. Exact Runna rejection thresholds UNKNOWN.

**Personal athlete use case:** The initial runner frequently uses hilly roads and a compact rectangular loop. This is a reason to compare similar-route segments, mark hard turns/elevation as confounders, and retain raw FIT samples; **it is not proof that any particular run contains bad GPS**.

### Runna's new Adapt for Heat: contextual changes, not pace fitness revision

Runna's [Adapt for Heat documentation](https://support.runna.com/en/articles/15647483-how-does-runna-adapt-my-workouts-for-heat-and-humidity) (Aug 31, 2026) establishes:
- Uses **forecast feels-like temperature** (including humidity) and **expected workout duration**; a rough recommendation threshold is **20°C feels-like**, with longer sessions eligible at slightly cooler conditions. The precise adjustment function is UNKNOWN.
- Displays prospective suggestions **the day before or day of** the session; athlete selects an hour and must choose **Accept** or **Keep as planned**.
- Can lower pace targets, lower easy-run conversational cap, replace a structured long run with an unstructured conversational long run, or discourage outdoor running in extreme conditions.
- A hotter forecast, moved session time/date or plan modification can **invalidate an existing accepted adaptation**, requiring re-review or clearing.
- Not universally available (rollout, plan-type exclusions, outdoor-only; hill sessions are effort/time-based and generally don't receive a pace adjustment).
- The vendor claims matched intended training effect, but **no mathematical or independent physiological verification** is provided.

**Important distinction:** It is *not contradictory* that Pace Insights lacks environmental corrections while Adapt for Heat makes **future-session weather adjustments**. These should be treated as separate observable channels.

**Potential architecture (INFERRED, original design):** baseline target prescription -> reversible contextual effort adaptation -> athlete approval -> watch sync, with change invalidation when forecasts or schedules shift. Do not change a long-term performance estimate merely because a one-day heat adjustment slowed planned pace. Heat-related safety needs more than copying Runna's rough trigger temperature.

### Runna secondary-event adjustment: workload preservation vs disruption

Runna's [B-race manager](https://support.runna.com/en/articles/10856802-how-to-add-and-manage-your-b-race) describes three user-selectable efforts (`Run for fun`, `Strong & steady`, `Going all out`) and a disruption assessment (`low`, `medium`, `high`), conditional on A-race, B-race distance, duration and ability.

Possible modifications: reposition deloads, lower previous-week volume, substitute taper-oriented sessions, reduce run frequency in race week, and add recovery following the secondary race. The system can refuse conflicting races (too close to the goal race, insufficient build-up, key workouts displaced, outside plan dates or longer than goal distance).

**Documentation caveat:** another Runna [plan-creation FAQ](https://support.runna.com/en/articles/15443877-how-to-create-a-training-plan-in-runna) states B-races can be the *same distance or shorter*; the dedicated B-race manager says *shorter than A-race*. Treat this eligibility rule as **SOURCE-CONFLICT / version-dependent** until tested on the actual app. The dedicated manager documents app version 7.31.2+ and plan v11 prerequisites, which can explain behavioral differences across observations.

The approach suggests an **INFERRED** original `event_disruption` constraint problem, not merely insertion of an extra hard session: preserve essential race-specific training while allocating recovery around a secondary event.

### Runna duration and session classification

Runna's [plan-creation guidance](https://support.runna.com/en/articles/15443877-how-to-create-a-training-plan-in-runna) states plans are primarily **distance-based**, capped at **26 weeks** (even though individual intervals may specify time). It recommends minimum lengths including 8 weeks for 5K improvement, 10 for 10K, 12 for half marathon and 16 for marathon; these are **recommendations**, not necessarily universally enforced minima.

Its [tempo-vs-interval explanation](https://support.runna.com/en/articles/13848642-what-s-the-difference-between-a-tempo-and-interval-running-session) distinguishes **session structure** (`tempo`, `interval`) from **physiological target** (`threshold`). A threshold stimulus may appear in either format. The vendor describes intervals often using shorter reps (roughly 400 m–1.6 km), with tempo blocks around 2–5 km or longer. These are descriptive ranges, **not** confirmed generator thresholds.

**Original modeling implication:** Encode `session_structure` and `physiological_objective` as separate fields. A StrideLab threshold session could be continuous or broken into repeats without being misclassified by labels alone.

### Garmin Run Coach: workout target-type algorithm differs by intensity

Garmin's [Running Plans FAQ](https://support.garmin.com/en-US/?faq=IkvWNeIoSd48GIYCjkhlo7) distinguishes:
- `Heart Rate`: HR targets for most workouts, **verbal effort guidance for anaerobic/sprint**, where heart-rate targeting is unreliable.
- `Pace`: pace prescriptions across workout types.
- `Auto`, on **compatible devices supporting supplemental strength workouts**: HR for base/recovery workouts; pace for tempo, threshold, VO2 max, anaerobic and sprint workouts.

The exact supported target options on each watch model must be confirmed rather than extrapolated from this generic FAQ.

**Original StrideLab implication:** A workout's best target modality may depend on *workout purpose and duration*. A fast 15-second sprint cannot be controlled through instantaneous HR in the same manner as a 45-minute easy run. Separate HR, pace, RPE, and effort-only options with user preference and sensor capability checks.

### Garmin Run Coach: feasibility and scheduling constraints

The same Garmin FAQ documents:
- Run Coach plans **6–52 weeks**, time/HR/pace goals, and optional supplemental strength on compatible devices.
- Preferred training and long-run days, but **no manual per-workout rescheduling** for Run Coach: daily prescriptions adapt automatically. This differs from Garmin named Expert Coach plans, which support manual rescheduling.
- If average weekly distance is markedly low relative to the chosen race distance, a **time-goal option may be withheld**; a completion goal may be available. This is a **workload-history eligibility guardrail**, not a determination that the user lacks speed.
- For Run Coach, event **end date and distance cannot be modified** after setup (per the support page); the plan can be edited for available days, workout targets, training goal, and other permitted settings.

These are strong examples of **plan-feasibility checks preceding workout selection**. Do not infer the numerical thresholds for mileage/time-goal eligibility without controlled observations.

### Interpretation and what is still unknown

We now have several **documented, separable decisions**:
1. **Data eligibility:** Is this completed workout reliable enough for pace calibration?
2. **Performance recalibration:** Do repeated eligible reps justify changing intensity targets?
3. **Environmental adjustment:** Should today's pace/format change due to forecast heat and duration?
4. **Event disruption:** Can a secondary race fit while preserving the primary plan?
5. **Goal feasibility:** Do mileage and training constraints permit a time-oriented event goal?
6. **Workout target modality:** Should this intensity be expressed by HR, pace, or perceived effort?

None establishes the original vendor's internal object model. Exact weights, progression formulas and decision thresholds are still unknown. **No new controlled app-generated plans or time-stamped Garmin Run Coach snapshots were obtained in this review.**

### Additional primary sources (all vendor-published)

- [R10] Runna, *Pace Insights Chart* (Aug 30, 2026). https://support.runna.com/en/articles/10751290-pace-insights-chart
- [R11] Runna, *Why Am I Not Receiving Pace Insights?* (updated Oct/Nov 2026). https://support.runna.com/en/articles/10855216-why-am-i-not-receiving-pace-insights
- [R12] Runna, *How Does Runna Adapt My Workouts for Heat and Humidity?* (Aug 31, 2026). https://support.runna.com/en/articles/15647483-how-does-runna-adapt-my-workouts-for-heat-and-humidity
- [R13] Runna, *How to Add and Manage Your B-Race* (updated 2026). https://support.runna.com/en/articles/10856802-how-to-add-and-manage-your-b-race
- [R14] Runna, *How to Create a Training Plan in Runna*. https://support.runna.com/en/articles/15443877-how-to-create-a-training-plan-in-runna
- [R15] Runna, *What's the Difference Between a Tempo and Interval Running Session?* (Aug 31, 2026). https://support.runna.com/en/articles/13848642-what-s-the-difference-between-a-tempo-and-interval-running-session
- [G5] Garmin, *Garmin Running Plans*. https://support.garmin.com/en-US/?faq=IkvWNeIoSd48GIYCjkhlo7


## Research roadmap — sequential dependencies, no deadlines

Research proceeds by **evidence quality and dependency**, not a fixed calendar. Each phase can be extended when conclusions remain underdetermined. This roadmap is not authorization to change the Android application.

### Phase A — Establish reproducible evidence and schemas

**Tasks:** Inventory primary support pages with retrieval dates, revision notes and applicability; separate vendor claims from observed outputs; define a normalized `AthleteInput`, `PlanSession`, `WorkoutStep`, `ObservationContext`, and `AdaptationDecision` schema. Build parsing/check rules for session totals, pace units, rep structures, week indexing, race-day inclusion and source provenance.

**Output:** Source register; input/output schema; claim registry with DOCUMENTED / OBSERVED / INFERRED / UNKNOWN statuses.

**Quality gate:** Every numerical claim can be reproduced from source evidence and recorded transformations. No silent normalization from planned to actual training.

### Phase B — Runna black-box generation (highest immediate value)

**Tasks:** Use legitimately generated and shareable plans. Begin with identical 5K ability but radically different *sustained* mileage histories (e.g., 25 vs 65 km/week) at fixed goal and schedule. Change one of mileage, longest recent run, frequency, race pace, plan length, volume preference, difficulty and maximum session bounds at a time. Repeat at least some identical inputs to check reproducibility; treat unavailable UI choices as constrained observations rather than inventing values.

**Measure:** initial weekly distance, peak and weekly trajectory, long-run fraction, workout type and hard work minutes, rep/recovery patterns, target intensities, deloads/taper, and whether controls influence orthogonal outcomes.

**Output:** Matched input/output plan dataset, clear effect-size comparisons, candidate constraints and competing explanations.

**Quality gate:** Withhold some generated plans as unseen evaluation cases; require observed effects to replicate, and explicitly report counterexamples. Avoid reverse-engineering via private APIs.

### Phase C — Runna changes over time (adaptation)

**Tasks:** Observe separately (a) Pace Insights after legitimate target-pace workouts, (b) Mileage Insights after actual training exposure, and (c) Plan Realignment after naturally occurring interruptions. Record *recommendation before acceptance*, user selection, and the changed future prescriptions. Do not fabricate logged workouts to force a recommendation or game safety limits.

**Output:** Eligibility matrix; state/decision transition hypotheses; exact differences in speed targets vs workout structure vs volume and calendar.

**Quality gate:** Establish which adaptation channel generated a recommendation and what evidence it used. Preserve original schedules for before/after comparisons.

### Phase D — Garmin Run Coach longitudinal observation

**Tasks:** Record the **prescription before the session** and context snapshot (which Garmin system, watch/model/firmware, target race and phase, pace/HR goal, recent training, exercise/acute/chronic load *only if supported*, recovery and sleep *only if available*). Record actual completed session plus following prescription. Where possible include naturally occurring missed or extra training. Never deliberately compromise recovery for research.

**Measure:** change in workout category, duration, work/recovery structure, target pace/HR, long-run length and weekday allocation. Separate same-day adjustments from periodization effects and natural week-to-week cycling. Use matched weeks and change-point analysis cautiously; correlation is not causation.

**Output:** Timestamped observation sequence, alternative hypotheses for key changes and confidence statements.

**Quality gate:** Don't assert causal decision weights without a controlled counterfactual. Document unavailable metrics rather than assigning inferred Garmin values.

### Phase E — Independent model inference and challenge tests

**Tasks:** Compare interpretable candidate algorithms for:
- Workload starting point and progression with independent long-run caps.
- Workout-category selection under goal, recovery and scheduling constraints.
- Pace-target calibration independent from weekly volume changes.
- Time-dependent training-load estimators and workout prioritization.
- Feasible plan changes after an interruption.

Use transparent rules/optimization/statistical models before attempting opaque ML. Test on withheld plans and athlete profiles unlike the initial user (low-volume novices, returners, constrained schedules, high-volume but slower racers). Evaluate **prescription appropriateness independently** using exercise-science checks and human review; competitor similarity is only one metric.

**Output:** Hypothesis and benchmark report with accuracy by subgroup, failure modes, stress tests and provenance.

**Quality gate:** No unreviewed safety-critical prescription; no assumption that a proprietary app is scientific ground truth.

### Phase F — Engine 1.5 and 2.0 promotion rules

**Engine 1.5 candidate areas:** Better baseline mileage history handling, independent volume/pace controls, session-distance constraints, long-run progression safeguards, deloads, basic missed-workout proposals, and explainability. Ship only if the deterministic 1.0 comparison and quality/safety evaluations improve or remain acceptable; retain user consent for major changes.

**Engine 2.0 research areas:** Adaptive workout categorization, time-dependent recovery-aware load, individual response and aerobic-efficiency trends, longitudinal evaluation, cross-discipline interference and uncertainty-aware proposals. Do not ship research-derived physiological policies before longitudinal and independent validation.

**Output:** Separate decision log for each accepted/rejected candidate rule with evidence links, test results, risks and rollback plan. No automatic PR merging or code promotion.

### Priority within the roadmap

1. **First:** Runna generation mechanisms and mileage-vs-race-ability separation; fastest route to actionable 1.5 insights.
2. **Alongside:** Garmin recommendation snapshots collected passively, because causal interpretation requires a longitudinal baseline.
3. **Then:** Runna adaptation channels and Garmin workout selection/periodization comparisons.
4. **Later:** independent generalized model testing and safe promotions into 1.5/2.0.

**Personal performance lens:** The initial StrideLab athlete is a high-volume, developing-speed runner seeking improved easy aerobic speed at similar effort. Use this as an important edge case but never let one athlete set default workloads for other users. FIT histories are useful for physiological trend analysis but are not a substitute for vendor-prescribed workout captures.

### Additional primary sources

- [R9] Runna, *What Are Mileage Insights?* (Sep 10, 2026). https://support.runna.com/en/articles/11794078-what-are-mileage-insights
- [G3] Garmin, *What Is Training Load on My Garmin Device?*. https://support.garmin.com/en-SG/?faq=SEkNpdGyhR917js0qQL3Q6
- [G4] Garmin, *Garmin Running Plans*. https://support.garmin.com/en-US/?faq=IkvWNeIoSd48GIYCjkhlo7



## Independently recalculated 5K samples and cross-discipline gaps — November 2, 2026

### Verified observation from actual published Runna plan output

I examined the **one-page original PDFs, including page screenshots**, for the published Runna/JustGiving **8-week 5K beginner (3 runs/week)** and **8-week 5K advanced (4 runs/week)** examples. These are **published sample plans**, not controlled personalized app-generated plans, and their creation date/version may precede Runna's current 2026 rules.

Sources:
- [P2] https://www.jg-cdn.com/docs/librariesprovider10/runna-running-for-good/runna-justgiving_sample_8-week-5k-improvement-plan_beginner_3_km.pdf?sfvrsn=acfee4b0_3
- [P3] https://www.jg-cdn.com/docs/librariesprovider10/runna-running-for-good/runna-justgiving_sample_8-week-5k-improvement-plan_advanced_4_km.pdf?sfvrsn=e5a82516_3

Distances below were recalculated by summing the bold scheduled top-line km for each day (not substrings in workout names), rounded to one decimal. Week 8 **includes the scheduled 5K race**, so it must not be interpreted as a normal training week.

| Week | Beginner km | Advanced km | Beginner long run km | Advanced long run km |
|---:|---:|---:|---:|---:|
| 1 | 11.2 | 32.2 | 5 | 10 |
| 2 | 13.0 | 35.0 | 6 | 12 |
| 3 | 15.2 | 37.4 | 7 | 14 |
| 4 | 10.1 | 30.0 | 4 | 10 |
| 5 | 17.0 | 40.0 | 8 | 14 |
| 6 | 18.5 | 44.4 | 8 | 16 |
| 7 | 15.2 | 35.0 | 7 | 10 |
| 8 | 13.0 | 17.0 | *5K race, not a long-run prescription* | *5K race, not a long-run prescription* |

**Directly observed**:
- Week 4 recovery reduction from week 3: beginner **−33.6%**, advanced **−19.8%** (on total km).
- Week 6 is the highest pre-race week: **18.5** and **44.4 km**.
- Week 6 long-run allocation is **43.2%** (8/18.5 km) in beginner and **36.0%** (16/44.4 km) in advanced.
- Weeks 1–7 of beginner typically have **one faster/quality, one easy, one long** run. Advanced includes **two faster/quality, one easy, one long**. Ability level **and** frequency differ together; causal attribution to either factor is not possible from these two examples.
- Tempo and interval structure rotates even while the number of weekly runs is constant. This is **not proof** of a precise workout selection rule.

**Context distinction:** Runna's 2026 [Training Focus](https://support.runna.com/en/articles/11598487-how-to-choose-your-training-focus) guide says **Endurance Focus** long runs are typically 25–35% of weekly distance; its published 5K race examples frequently exceed this. **Do not claim inconsistency without testing like-for-like plan types**: the 2026 focus is available for general training and instant workouts, whereas the PDFs are race-specific examples.

### NEW: Focus selection is a separate prescription dimension (vendor-documented)

Runna's 2026 [Training Focus](https://support.runna.com/en/articles/11598487-how-to-choose-your-training-focus) documentation identifies **Speed**, **Endurance**, **Balanced** for *general training* plans and instant workouts:
- **Speed:** shorter/faster intervals, hills, VO2max, reduced long-run emphasis.
- **Endurance:** long runs, threshold/tempo, and substantial conversational running; the guide describes 25–35% long-run share as typical for this *specific focus*.
- **Balanced:** tempo + shorter intervals + moderate long runs.

This focus choice is logically distinct from race time/pace setting, volume preference and difficulty preference. Its **exact changes to work-rep counts, hard-session time or volume remain UNKNOWN** without matched-input plan exports.

**Research priority for personal user:** Compare `Endurance` vs `Balanced` at the *same* starting 60–70 km/week, running frequency, recent 5K and availability, using outcomes **matched-heart-rate pace, aerobic durability, and preserved threshold quality**. Do not assume a speed focus is automatically best for improving easy pace.

### NEW: Runna's running plan excludes strength from adaptation (vendor-documented)

Runna's [Adding Strength Training](https://support.runna.com/en/articles/15624879-adding-strength-training-to-your-runna-plan) (Sep 22, 2026) states explicitly:
- Strength is an **additional calendar plan**. It offers Running Focus vs All Round strength, ability, equipment and session duration/frequency selections, but **adding strength does not change running sessions**.
- Strength sessions **sit outside adaptive running plans** and do **not affect running-plan adherence, intensity guidance or adaptation**. Manual logging of strength activities is supported; that is not evidence they affect running plan generation.
- The vendor advises separating demanding lower-body sessions from key intervals/long runs, but the documented product does not claim a full integrated muscular-fatigue prescription.
- Runna currently doesn't offer a strength-only plan as an independent planning product and doesn't let users swap individual exercise choices in an existing strength session via its plan controls.

**Potential StrideLab differentiation (HYPOTHESIS, not demonstrated superiority):** Coordinate lower-body muscular work with interval/long-run scheduling and evaluate training outcomes while retaining discipline-specific constraints. We should not simply add strength minutes to EPOC-style running load or claim one cross-discipline fatigue score is validated.

**Contrast with Garmin:** Garmin's [Running Plans FAQ](https://support.garmin.com/en-US/?faq=IkvWNeIoSd48GIYCjkhlo7) documents **optional supplemental strength on compatible Run Coach devices**, including the **Forerunner 165 series** in its current table, and `Auto` HR/pace target controls when available. **No verified evidence yet establishes whether supplemental strength completion materially changes Garmin's next running prescription.** Treat it as UNKNOWN, not an advantage or deficiency.

### NEW: Runna's workouts require nested step structure

Runna's [Understand Your Runna Workouts](https://support.runna.com/en/articles/15690947-understand-your-runna-workouts) documents a workout with warm-up, nested/repeated **sets** of distance- or time-based work and recovery, between-set rests, and cool-down. The step transition may be automatic (road) or manual (track), and warm-up can be open-ended. Hills use *effort-based* uphill repetitions and return-down recovery, not a fixed speed target.

**Implication for StrideLab:** Plan generation and a structured workout player should share one normalized interval-tree representation with nested sets, heterogeneous recovery types, and an optional manual transition mode. This is a data-structure requirement, *not* proof of how Runna chooses workout dosage.

### NEW: Garmin recovery numbers are not simple summation

Garmin's [Recovery Time support](https://support.garmin.com/en-PH/?faq=8ImmxVkZMh4EYYq5Zp2bR8) describes recovery-time estimates as re-evaluated after additional activities, not hours simply added together. Available drivers may include training effect, remaining recovery estimate and (on compatible devices) sleep, stress and activity levels. **It does not disclose exact weights, and it is not evidence that Garmin Run Coach directly consumes every recovery-time input.**

**Research interpretation:** If modeling multi-session fatigue, an unvalidated sum of recovery hours is inappropriate. Retain separate observed measurements and investigate the relationship to future coaching prescriptions.

### Most valuable next research (not scheduled milestones)

1. **Controlled Runna focus/frequency study:** Same race ability and workload; change *only* run days, then *only* Speed/Endurance/Balanced focus, then difficulty. Measure workout category, volume, actual hard-work minutes and long-run share. Compare to the published samples without treating their app version as current.
2. **High-volume developing-speed athlete:** Match recent race pace, vary verified 4-week mileage and recent longest run. Quantify separate changes in start volume, peak, long runs and pace targets. Include the personal athlete range (~60–70 km/week) but retain beginner and low-volume counterexamples.
3. **Garmin target selection and real adaptation:** Record *before-run* recommendations and available context on a specific Run Coach plan and compatible watch; compare base/recovery vs faster work under HR/Pace/Auto where supported. Record fitness/recovery/phase states without attributing causal weights.
4. **Cross-discipline interaction:** Investigate what the Garmin optional supplemental strength feature does to run recommendations; distinguish calendar co-location, load recording and actual adaptive changes. Develop original StrideLab hypothesis and test separately using exercise-science constraints.
5. **Workout grammar:** Compare interval sets, work/recovery target values and progression across successive Runna schedules. Preserve rep-level structure rather than just "tempo 7 km" labels.
6. **Independent evaluation:** Review long-run concentration, deload pattern and hard-session distribution against independent evidence, not just competitor agreement.

**Limitations:** No fresh matched-input Runna generation, native Garmin Run Coach recommendation stream, or controlled strength-interference outcomes obtained in this review. Published samples verify what *those samples prescribe*, not the exact current proprietary algorithm.



## Runna workout-construction investigation: exercise objective, repetition structure, recovery and pace calibration (2026-11-02)

**Evidence types:** [VENDOR] published support guidance; [SAMPLE] original Runna/JustGiving overview PDF, visually inspected via original page screenshots; [INFERRED] competing explanatory hypothesis; [UNKNOWN] cannot be recovered without detailed structured plan exports.

### 1. Separate physiological objective from workout format — VENDOR

Runna explicitly states that `threshold` is a physiological training objective, whereas `interval` and `tempo` describe workout structures. A threshold stimulus can use continuous running or repetitions; not all intervals target threshold. The vendor describes:

| Structure | Descriptive, not enforced, rep range | Typical effort | Recovery |
| --- | --- | --- | --- |
| Intervals | ~400 m–1.6 km; 200–400 m examples may exceed threshold | often threshold/5K–10K pace, RPE 8–9 | walk or stationary, sometimes active |
| Tempo | ~2–5 km or longer; may be continuous | usually 10K–HM effort, RPE 7–8 | none, jog, or float |
| VO2max intervals | shorter and above threshold | very hard | depends on session |
| Fast anaerobic/sprint | short, substantially above threshold | high neural/power demand | longer, more complete recovery |

Sources: [R16] https://support.runna.com/en/articles/13848642-what-s-the-difference-between-a-tempo-and-interval-running-session and [R17] https://support.runna.com/en/articles/8608203-is-my-workout-a-threshold-interval-session-or-tempo-run.

**Not a formula:** These typical distance/pace ranges are coaching descriptions, not observed hard-coded planning rules. In particular the public PDFs include a workout named **200m Tempo Repeats**, so one must not classify `tempo` exclusively by long-rep duration.

**Original StrideLab schema implication [INFERRED]:** `physiological_objective` (threshold, VO2max, endurance, neuromuscular, etc.) must be distinct from `session_structure` (continuous, repetitions, pyramids, alternating float, progressive, mixed). The workout label alone is insufficient.

### 2. Recovery is an independent prescription variable — VENDOR

Runna distinguishes stationary, walking, light-jog and moderate **float** recovery. It says active recoveries often last **60–120 seconds**, varying by work-rep distance and rep count, with additional distance-based examples. Published illustrations include:
- **6 × 1 km at 5K pace, 90 s walking**.
- **1200 m at marathon effort + 400 m float**.
- **1000 m at half-marathon effort + 400 m float**.
- **60 s uphill effort followed by an easy jog downhill**.

These examples are coaching illustrations, **not extracted, personalized app prescriptions**. The examples do **not** reveal a universal 90 s recovery rule, work:rest function or eligibility threshold.

Source: [R18] https://support.runna.com/en/articles/12353274-how-to-recover-between-running-intervals.

**INFERRED model to test:** `recovery_mode`, `recovery_duration_or_distance`, `recovery_intensity` depend on `physiological_objective`, work duration, prescribed effort and overall work dose. A float is not equivalent to a walk merely because both are labeled recovery. Track total work, float distance and truly low-intensity recovery separately.

### 3. Dose and structure vary independently — VENDOR

Runna's tempo article says **3 × 2 km** can be run faster than **1 × 6 km** even with identical total faster-running distance. This is *not* confirmation of a specific pace offset. A prescribed target must account for work-rep length, recovery, and full-session context.

Source: [R19] https://support.runna.com/en/articles/8911204-what-is-a-tempo-run-a-complete-guide-for-runners.

Runna's training preferences article says lowering workout difficulty can reduce rep count while leaving **target pace constant**; changing the current estimated race time changes **pace targets**, not mileage. These are observable **separable controls**, not proof that its code uses a specific algorithmic decomposition. Sources: [R3], [R20].

### 4. One current-race-ability input drives cross-distance pace targets — VENDOR

Runna [R20] says one current estimated race time is sufficient to update estimates for 5K, 10K and half marathon, and it may change the estimate when benchmark distance changes. It suggests conservative estimates where races are outdated.

- [R20] https://support.runna.com/en/articles/6205998-adjusting-your-estimated-race-time-and-pace-targets
- [R21] https://support.runna.com/en/articles/6251445-how-is-my-target-race-time-predicted

**Research question:** What cross-distance transformation and pace-zone boundaries are used? Runna does not disclose the actual formulas. A single estimated 5K result may fail to characterize an athlete with atypical endurance relative to short-distance speed; this is a potential *modeling limitation to test*, not a demonstrated Runna defect. StrideLab could explore multi-distance calibration while keeping training-volume capacity independent from race speed.

### 5. Directly observed sample-PDF workout-template reuse — SAMPLE

I visually checked [P2] and [P3] (8-week 5K beginner and advanced), [P4] (10-week advanced 10K), [P5] (12-week advanced half marathon), and [P6] (16-week beginner marathon).

| Published sample | Specific observed workout names | What can be supported |
| --- | --- | --- |
| Beginner 5K (3 days/week) | Drop Set; Broken Miles; Progressive Mile Repeats; Pyramid Intervals; 800m into 400m | One higher-intensity category per typical pre-race week; varied named templates |
| Advanced 5K (4 days/week) | Tempo 2km Blocks; Descending Intervals; Tempo 1200s; 400s into 200s; 200m Tempo Repeats | Two different named harder sessions per typical week; labels alone do not expose actual interval dosage |
| Advanced 10K (4 days/week) | Rolling 500s; 800m Repeats; 1km Repeats; Over and Unders 2km; Tempo 3km Blocks; 200m Tempo Repeats | A mix of short and long named workouts, even for an identical race distance |
| Advanced half marathon (4 days/week) | Shorter Intervals; 1km Repeats; Mile Repeats; 2km Repeats; Tempo 1200s; 200m Tempo Repeats; Tempo 3km Blocks | Similar template families recur across distances and phases, not strictly monotonic longer reps for longer races |
| Beginner marathon (3 days/week) | Rolling 500s; Tempo 1200s; Broken Miles; Varied Tempo 2kms; Descending Intervals; 200m Tempo Repeats | Shorter-rep named sessions are still present in marathon training; no "only marathon-pace workouts" assumption |

**Caution 1:** The PDFs show **session totals**, not the complete warm-up, recovery, rep count or pace target. A label like `200m Tempo Repeats` does **not** reveal the actual recovery or physiological intensity and must not be interpreted as a 200m all-out sprint.

**Caution 2:** The schedules are historical **sample plans**, not current, personalized, matched-input 2026 generator outputs. The fact that template names recur across distances **suggests a workout-template library is plausible [INFERRED]**; it does not prove Runna's implementation reuses templates algorithmically.

**Caution 3:** Four-day advanced versus three-day beginner also changes ability and volume, so apparent changes in hard-session frequency cannot be causally attributed to run days alone.

**Original PDF sources:**
- [P2] https://www.jg-cdn.com/docs/librariesprovider10/runna-running-for-good/runna-justgiving_sample_8-week-5k-improvement-plan_beginner_3_km.pdf?sfvrsn=acfee4b0_3
- [P3] https://www.jg-cdn.com/docs/librariesprovider10/runna-running-for-good/runna-justgiving_sample_8-week-5k-improvement-plan_advanced_4_km.pdf?sfvrsn=e5a82516_3
- [P4] https://www.jg-cdn.com/docs/librariesprovider10/runna-running-for-good/runna-justgiving_sample_10-week-10k-plan_advanced_4_km.pdf?sfvrsn=fed7effe_3
- [P5] https://www.jg-cdn.com/docs/librariesprovider10/runna-running-for-good/runna-justgiving_sample_12-week-half-marathon-plan_advanced_4_km.pdf?sfvrsn=ca6f314_5%2F
- [P6] https://www.justgiving.com/wp-content/uploads/2025/10/runna-justgiving_sample_16-week-marathon-plan_beginner_3_km.pdf

### 6. Workout execution semantics are separate from workout construction — VENDOR

Runna supports nested sets, work/recovery steps, optional between-set rest, open warm-up, manual track progression and manually progressed hill intervals. These execution semantics must be represented even if a plan-generation model only emits a brief workout title.

Source: [R16b] https://support.runna.com/en/articles/15690947-understand-your-runna-workouts.

**Recommended original validation data:** 
`session_id, session_category, physiological_objective, plan_week, prescribed_total_m, warmup_steps, set_groups[{reps, work_steps, recovery_steps, between_set_rest}], cooldown_steps, per_step_target_basis, actual_hard_work_m, total_active_recovery_m, recovery_mode, exact_export_source`. Don't assume work-step distances sum to session distance unless warmups/cooldowns and recovery are included. Always retain original units/steps for audit.

### 7. Gaps: what cannot yet be reverse-engineered from the public schedule

1. **Template selection scoring:** when is `Tempo 1200s` chosen instead of `Tempo 2km Blocks`?
2. **Work dose:** how many total meters/minutes at threshold, VO2max, 5K or 10K effort for the same athlete?
3. **Prescription adjustments:** exact relationship between run-day count, user-selected difficulty, current racing fitness and repetitions.
4. **Pacing conversion:** precise conversion from a 5K/10K/HM estimate to each work-step pace.
5. **Recovery construction:** why a float vs walk, and when rest is 60 vs 90 vs 120 seconds?
6. **Progression vs novelty:** do workout templates change to produce a controlled stimulus or for variety?
7. **Within-week sequencing:** whether the placement of quality sessions and long runs is determined by a specific scoring function.

**Next highest-value research:** Actual **detailed exported workout prescriptions** with *matched-input differences*, not more weekly-plan overview titles. A rigorous next test holds race estimate, training history, available days, and plan week constant, changing only `Difficulty`. It evaluates `rep_count`, `total_hard_work_time`, `recovery_type/duration`, `step_target_pace` and `session_total_distance`. Then change only the current race-time estimate, and separately only endurance-vs-speed focus in eligible general training plans. This can falsify the observable independence claims and locate boundary effects.

**Status:** No controlled personalized Runna plans or full workout-step exports captured in this phase; only direct original-PDF overview observations and current public vendor explanations. No proprietary Runna implementation reconstructed.



## Continued model separation research: pace calibration, predicted outcomes, progression guardrails and hardware availability (2026-11-02)

### Key discovery: three different Runna estimates (VENDOR-DOCUMENTED)

Runna's **estimated current race time** calibrates prescribed workout pace targets. The **estimated goal-race outcome** is a different forecast based on provided athlete information, training schedule, ability, and observed outcomes from comparable runners. A third variable, **Training Volume / Difficulty**, affects the prescribed workload and can change the *predicted future result* without changing the *current workout pace targets*.

These are not interchangeable. Vendor sources:
- [R22] https://support.runna.com/en/articles/6205998-adjusting-your-estimated-race-time-and-pace-targets
- [R23] https://support.runna.com/en/articles/6251445-how-is-my-target-race-time-predicted
- [R3] https://support.runna.com/en/articles/10393191-how-to-use-training-preferences

Runna recommends small manual race-time changes of **15–30 seconds** when recalibrating targets, but this is **general usage guidance, not proof of the software's required increment or pace-to-time formula**. Importantly, Race Prediction is vendor-described as using **other comparable runners' data**; a deterministic physics-only formula would be an incomplete explanation for that specific forecast. Runna notes that its race prediction assumes relatively flat conditions and good weather, and some terrain-dependent races may lack a displayed prediction. This is not a proven or exact cohort model.

**Hypothesized StrideLab design (NOT copied implementation):**
1. `current_performance_estimate` + uncertainty -> workout target recommendations.
2. `training_plan_dose` -> volume/difficulty progression, distinct from paces.
3. `forecast_performance_distribution` -> estimated future outcomes under the plan, with uncertainty and explicit assumptions.
4. `data_quality` -> controls confidence in all three, especially if only one race distance is known.

For the initial athlete, avoid inferring that a high current weekly volume must entail a correspondingly fast 5K time; retain pace fitness and tolerated workload as separate variables.

### New algorithmic detail: Runna's different *plan families* have different progression functions (VENDOR-DOCUMENTED)

From [R1] https://support.runna.com/en/articles/15231838-how-does-runna-build-your-training-plan-around-your-current-fitness:

- **Race/distance plans**: an increasing mileage curve subject to a mileage-dependent weekly ceiling, with separately controlled long-run progression; more experienced athletes receive higher absolute targets, **not necessarily a faster build rate**.
- **Run Faster / Run Further / Maintain / Train Your Way**: a *fixed percentage progression* depending on ability and running frequency. Counterintuitively, **fewer running days allows a slightly higher percentage increase**, while more days leads to a lower percentage progression. This is documented behavior, not a safe universal recommendation; test interactions with total starting mileage and session length.
- **New to Running / Return to Running / Path to parkrun**: a **double constraint** caps weekly progression by the more conservative of two limits. Walk-run changes gradually over stages; Return to Running uses a transition across quarters of the plan. Exact caps/transition formulas UNKNOWN.
- **Post-race/post-injury/postnatal**: deliberately conservative, coach-built rules-based progression.
- **Pace targets** are not available on some return/beginner programs, so a single universal pace-driven generator would mismatch these plan families.

**Interpretation:** Plans should have explicit **regimes** or policy families, not one mileage increase equation. The source does not prove that Runna code contains separate submodules; it proves documented behavioral differences.

**New controlled tests:** Hold athlete's ability, current mileage and target duration fixed, vary 3/4/5 days on Run Further; observe whether percentage growth is larger with fewer days *while per-session long-run and hard-session constraints remain feasible*. Separately compare identical athlete inputs under race plan vs Train Your Way to see whether the progression curve differs. A range of mileage starting points is required to estimate the documented ceiling; do not assume a 10% rule.

### Garmin readiness != performance; avoid using absent device metrics (VENDOR-DOCUMENTED)

Garmin's [Training Readiness explanation](https://www.garmin.com/en-PH/garmin-technology/running-science/physiological-measurements/training-readiness/) explicitly distinguishes **readiness to benefit from another training stimulus** from **race-day performance readiness**. A poor night's sleep may reduce training readiness without proving an athlete's performance potential deteriorated.

**Critical device-specific constraint:** Garmin's [comparison table](https://www.garmin.com/en-US/which-watch/best-buy/running/compare/) does **not** list **Training Readiness** or **Training Status** for the **Forerunner 165**, while listing them for more advanced models. The [Forerunner 165 regional specification](https://www.garmin.com.sg/products/wearables/forerunner-165-black/) explicitly lists supported **HRV Status, Recovery Time, Sleep, VO2 Max and Training Effect**. Therefore a single-user study using the 165 must **not** require watch-native Training Readiness, Training Status or load-focus values. Capture only verified available values (sleep score, HRV, recovery time, recorded workouts, pace/HR, relevant wellness context).

Garmin's [Running Plans FAQ](https://support.garmin.com/en-US/?faq=IkvWNeIoSd48GIYCjkhlo7) currently lists **Forerunner 165 as compatible with Run Coach and Expert plans**, but **does not list it for Supplemental Strength Workouts** (which are listed for 170/255/265 and some others). **Correction to earlier research text if it assumed 165 could access Garmin Run Coach supplemental strength:** this is NOT established for 165. Availability is device/version-specific.

This distinction matters: Run Coach's general technical page describes EPOC-based training-load concepts across supported Garmin devices. It does **not** prove that the 165 exposes every such proprietary metric. We must avoid fabricated load or Training Readiness snapshots.

### Next experiment priorities, evidence limits

- **Pace calibration test:** matched Runna plan/ability/volume/difficulty, change current 5K estimate only and examine every work-step target. Compare observed change to several *candidate* pace transformation functions, not just a flat seconds/km shift.
- **Plan difficulty test:** hold race estimate constant, change difficulty only; inspect rep count, hard work duration and rest, checking Runna's documented prediction that assigned pace does not change.
- **Frequency-progress test:** matched Run Further athlete, varying run days only, analyze weekly relative progression versus peak absolute distance and feasible session allocation.
- **Garmin athlete-state observation:** on 165, capture real Run Coach prescriptions before running with only available metrics, distinguish performance target changes from recovery-dependent workout changes; collect wearable/model/firmware.
- **Prediction vs actual:** a future race prediction needs outcome comparisons across enough real runners; documented cohort use cannot be recreated from one athlete's FIT history.

**Status:** Additional **vendor-documented** decision boundaries discovered. No current matched Runna workout-step export, exact pace formula or Garmin internal weights recovered. No claims of proven source-level reverse engineering or improved athlete outcomes.



## Additional evidence review: feedback isolation, athlete classes, plan-rebuild discontinuity and observation reliability (2026-11-02)

**Methods:** Primary Runna/Garmin support and technical pages checked on 2026-11-02; one community report used **only** to flag a possible data collection confound. No new controlled personalized plan outputs, secret endpoints, or proprietary source algorithms inspected. Claim status is **VENDOR DOCUMENTED** unless identified otherwise.

### Runna: AI feedback versus algorithmic plan adjustment are different products

Runna's [Workout Insights](https://support.runna.com/en/articles/10494265-what-are-workout-insights) analyzes completed planned activities and produces AI-generated, individualized natural-language advice (including comparisons to prescribed workout and, per vendor, weather/elevation context); the vendor states unambiguously that this feedback **does not affect the training plan**. Workout Insights is limited to some eligible scheduled workout types, requires premium and an opt-in to third-party processing, and is absent for free runs and strength/yoga/Pilates/stretch activities.

This must **not be conflated** with **Pace Insights** (eligible repetition-level performance -> possible pace proposal), **Mileage Insights** (actual running volume -> possible volume proposal), or **Plan Realignment** (missed plan continuity). Runna's statement in Pace Insights that the plan only adapts via pace recommendations is feature-scoped in tension with the vendor's separate mileage/interruption features; avoid interpreting it as a universal description of all plan adjustment logic.

**Original architectural test for StrideLab:** distinguish:
- `observational_explanation`: explains past performance but has no authority to change plan.
- `fitness_estimate_update`: adjusts statistical athlete-state estimates, with validation/confidence.
- `prescription_proposal`: computes possible workout/volume change with explicit reasons and constraints.
- `approval_transaction`: athlete authorizes significant plan change; immutable before/after snapshots.

An articulate language model does not itself establish a valid coaching prescription. This is a **proposal**, not Runna's recovered internal implementation.

### Runna: ability tiers and mileage history are not speed classes

Runna's [Running Ability page](https://support.runna.com/en/articles/6205993-adjusting-your-running-ability) defines:
- Beginner: continuously run 5 km, under 60 min.
- Intermediate: regularly complete at least 5 km but without structured plan.
- Advanced: regularly complete at least 10 km and use structured workouts.
- Elite: regularly complete half marathon or longer, with structured work.
- Elite Plus: designed for consistent **80–100 km/week**.
- The word *regularly* means longest reasonably comfortable **single run**, not aggregate weekly mileage.

These qualitative categories inform allowed workout/long-run lengths and volume. The page states explicitly that changing Ability **rebuilds the plan**, and may create **back-to-back hard or easy weeks and skip key workouts**. The vendor recommends delaying an ability increase until after week 2–3. Such a rebuild warning is important evidence that regeneration can affect **periodization continuity**, not just magnitudes.

A separate [Current Mileage page](https://support.runna.com/en/articles/11598676-how-to-use-and-manage-your-current-mileage-settings) describes weekly mileage as the realistically repeatable first-week distance, informed by four recent weeks, days/week and plan length. It says to use the **lowest mileage week** if the four-week history includes a recovery week. This instruction could be overconservative when an intentional deload is a substantial outlier, so it is **vendor practice, not an independently validated optimal estimator**. The input also includes longest repeatable recent run; no formula linking these inputs to max workout length is disclosed.

**Critical independent design experiment:** capture a plan at week k, change ability or volume, and compare *preceding/following three-week total load, quality-session days, taper and deload alignment* before/after. A StrideLab proposal may preserve protected training-block invariants to avoid accidental two-hard-week sequences. Requires validation.

### Runna: target type can change to RPE without changing the physiology objective

Runna's [Adjusting Training Units](https://support.runna.com/en/articles/6206133-adjusting-your-training-units) now documents selecting **RPE** instead of prescribed pace targets under Workout Targets. In this mode the workout shows effort guidance (e.g., RPE 4) **rather than numerical pace**. The vendor specifically mentions humid/hot weather as a reason to use effort, alongside its separate *Adapt for Heat* pace-target feature. Neither choice proves equivalent training outcomes; both are alternative **target modalities**.

This qualifies the simplification that Runna is strictly pace-only: it **uses race-time-based paces when pace guidance is selected**, but supports an effort-based presentation mode.

**Stridelab testable architecture:** Preserve `physiological_objective` and `workout_structure` independent of `target_modality` in `{pace, heart_rate, RPE, effort_only}`. Switching target mode should preserve plan structure unless a documented adjustment is explicitly accepted. Avoid converting RPE values to exact heart-rate thresholds without athlete calibration.

### Runna: forecast sensitivity is not measured effectiveness

In [Training Preferences](https://support.runna.com/en/articles/10393191-how-to-use-training-preferences), Runna says more demanding settings can yield approximately **10% faster *predicted progress*** compared with default settings, with substantial individual variability. This is **a vendor forecast claim**, **not** evidence from controlled training outcomes, and not a reason to increase training load for a given athlete. Do not use this number as a StrideLab physiological model coefficient.

### Garmin: distinguish *device-local recovery* from synced account values

Garmin [Recovery Time FAQ](https://support.garmin.com/en-PH/?faq=8ImmxVkZMh4EYYq5Zp2bR8) defines Recovery Time as readiness for another **similarly intense** workout, typically from 0 hours to 4 days depending on workout/device. Repeated workouts trigger **re-evaluation**, not addition of independent recovery-hour totals. It lists prior remaining recovery time, training effect and (on supported devices) sleep, stress, training intensity and daily activities among inputs.

**New methodological warning:** Garmin says device Recovery Time can update continuously while Garmin Connect reflects only the latest sync. Devices without Training Readiness may show Recovery Time **only on the watch**. For this research on Forerunner 165, do not assume a connector's account summary includes an up-to-date Recovery Time value or any unsupported Training Readiness / Training Status / load-focus numbers. Record watch-time snapshots if available, including last-sync status and firmware.

In a [Garmin forum report](https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-mobile-andriod/395835/garmin-run-coach-suggested-runs-not-in-sync-between-7x-watch-and-mobile-web/1865558), at least one user describes watch-vs-Connect differences in suggested workout type and duration, sometimes fixed by syncing. **ANECDOTAL / unverified generality:** this report is not proof of widespread bugs or different algorithmic models; it is a reason to record `recommendation_origin`, exact `captured_at`, sync recency and device/app version when investigating a workout change.

### Garmin: environmental performance calibration requires compatibility checks

Garmin's [Heat and Altitude Acclimation](https://www.garmin.com/en-PH/garmin-technology/running-science/physiological-measurements/heat-and-altitude-acclimation/) documentation says **select** devices can consider temperature, altitude, and estimated acclimation to preserve interpretation of VO2max/performance under environmental strain; it discusses triggers above approximately **22°C** or **800 m** (not universal thresholds). This does **not establish support on Forerunner 165**. Do not equate observed improvement in matched-HR pace with improvement in aerobic fitness until route/heat and recovery are considered.

### Garmin: deliberate constraints on output target type

Garmin's [Run Coach support table](https://support.garmin.com/en-US/?faq=IkvWNeIoSd48GIYCjkhlo7) says HR mode uses *effort guidance* for anaerobic and sprint work because HR is not suitable to prescribe those short efforts; pace mode may be used for all workout classes. **Auto** mode (where compatible) selects HR for base/recovery and pace for faster categories. These are *policy-level* target rules, not evidence of a recovered pulse-response model.

A separate [Garmin blog about daily suggested workouts](https://www.garmin.com/en-US/blog/fitness/daily-workout-suggestions-for-runners/) suggests sprint training is avoided when significant recovery is still needed, but **the article is about DSW**. Do **not** import it as verified Run Coach selection logic without Run Coach-specific evidence.

### Updated research questions with highest information value

1. Does manually changing Runna Ability *mid-plan* cause training-block discontinuities in controlled current app outputs, and how severe? Quantify 3-week workload jumps and recovery placement.
2. Does switching Runna pace targets to RPE preserve workout-set count, rest periods and session length? Distinguish target display vs regenerated prescription.
3. With identical race performance, do Runna Ability vs Current Mileage independently alter plan limits, frequency and speed-session dose? Test a high-volume developing-speed runner plus low-volume control.
4. What factors drive *actual* Garmin Run Coach suggestion changes when synchronisation is controlled? Snapshot watch recommendation and Connect recommendation with exact times and source, and note firmware and supported metrics.
5. Can independent StrideLab rules avoid such plan-regeneration discontinuities without losing goal feasibility? Validate against exercise science; competitor behavior is not inherently optimal.

**Evidence status:** Vendor-documented behavior and one explicitly anecdotal Garmin sync report. Still **UNKNOWN**: exact pace formulas, workout-choice scoring, Garmin model weights, whether an individual device compensates for heat, and quantitative Plan Regeneration effects on original app outputs. No controlled fresh Runna personal plans or original Garmin Run Coach recommendations acquired this round.

## Primary source register

- [R1] Runna, *How Does Runna Build Your Training Plan Around Your Current Fitness?* https://support.runna.com/en/articles/15231838-how-does-runna-build-your-training-plan-around-your-current-fitness
- [R2] Runna, *How to Use and Manage Your Current Mileage Settings* (Aug 30, 2026). https://support.runna.com/en/articles/11598676-how-to-use-and-manage-your-current-mileage-settings
- [R3] Runna, *How To Use Training Preferences*. https://support.runna.com/en/articles/10393191-how-to-use-training-preferences
- [R4] Runna, *How Does Runna Build Recovery Into Your Training Plan?* (Aug 31, 2026). https://support.runna.com/en/articles/15272605-how-does-runna-build-recovery-into-your-training-plan
- [R5] Runna, *Run to Maintain Target Mileage Settings* (Aug 30, 2026). https://support.runna.com/en/articles/11598719-how-to-use-and-manage-your-target-mileage-settings-on-a-run-to-maintain-plan
- [R6] Runna, *How to Adjust Your Plan Around Holidays* (Sep 10, 2026). https://support.runna.com/en/articles/10225691-how-to-adjust-your-plan-around-holidays
- [R7] Runna, *How to Use the Plan Realignment Feature* (Sep 10, 2026). https://support.runna.com/en/articles/10026375-how-to-use-the-plan-realignment-feature
- [R8] Runna, *What Are Pace Insights and How Do They Work?* (Aug 31, 2026). https://support.runna.com/en/articles/14656203-what-are-pace-insights-and-how-do-they-work
- [G1] Garmin, *Garmin Run Coach Technology*. https://www.garmin.com/en-GB/garmin-technology/garmin-coach/garmin-run-coach/
- [G2] Garmin, *Understanding Training Load*. https://www.garmin.com/en-US/garmin-technology/cycling-science/physiological-measurements/training-load/
- [P1] JustGiving, *Runna Training Plans (published examples)*. https://www.justgiving.com/hub/running-for-good/training-plans

_Last reviewed 2026-11-02. Links and observed product behavior may change. This is an original research note, not affiliated with Runna or Garmin._
