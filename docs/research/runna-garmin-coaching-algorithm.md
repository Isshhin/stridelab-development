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
