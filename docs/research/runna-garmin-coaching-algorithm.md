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
