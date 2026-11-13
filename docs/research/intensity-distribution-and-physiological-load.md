# Intensity Distribution and Physiological Load: Runna, Garmin, and Independent Evidence

**Research date:** 2026-11-02  
**Status:** Vendor-documented behaviors, peer-reviewed evidence, and explicitly hypothetical arithmetic. No proprietary algorithm weights recovered.  
**Scope:** Research-only companion to [Runna and Garmin Coaching Algorithm Research](runna-garmin-coaching-algorithm.md).  
**Production change:** None.

## Executive observations

1. Training **session type/count**, **prescribed hard-step duration**, **actual heart-rate time-in-zone**, **pace time-in-zone**, and **perceived effort** are **different measures**. Treating them as interchangeable can distort Runna plan comparisons and any athlete-specific inference.
2. Runna's public 80/20 article treats the rule as **flexible based on run frequency and other training** and explicitly excludes warm-ups/cool-downs/easy interval recoveries from the hard portion. This is **vendor advice**, not proof of the current generator's hard-work budget.
3. Garmin's exercise load and Training Effect are **EPOC-based, fitness-sensitive estimates**, so neither kilometers nor minutes in an HR zone alone can exactly reconstruct its load values.
4. A Garmin post-run **primary benefit label** reflects measured/estimated stimulus, not necessarily the Run Coach workout's original objective. It is not a definitive causal outcome.
5. Our independently designed StrideLab algorithm should preserve each metric's provenance and estimate disagreement/uncertainty, especially when pace and HR diverge in tropical heat.

## Finding A — Runna's 80/20 guidance has multiple denominators (VENDOR-DOCUMENTED)

[Runna 80:20 principle](https://support.runna.com/en/articles/9551650-80-20-training-principle) (2026-08-31) says the split varies by run frequency, level and cross-training. It suggests a larger easy majority for 5–7 runs/wk, and that a higher hard-session proportion may be practical when only running 2–3 days/wk. It explicitly says to exclude easy warm-up, cool-down and jog recovery inside hard sessions from the *hard-running* fraction, and says some steady or marathon-intensity work may not be counted as hard when appropriate for the athlete. This distinction is effort-dependent, not a rigid category boundary.

**Important:** The article informally switches between **counts of workouts** and **amount of time**. Statements such as "4–5 easy sessions and 2–3 harder sessions" should **not be mathematically equated** to an exact 80/20 time split. Do not infer that every six-run week contains two speed sessions.

[Runna intensity progression article](https://support.runna.com/en/articles/13868246-how-to-increase-your-running-mileage-safely) also advises not increasing total volume and intensity simultaneously. This is published coaching guidance, **not observed proof** that the generator enforces a strict no-simultaneous-increase constraint across all plan types.

### Fully reproducible illustration (NOT source plan output)

| Metric | Hypothetical week A | Hypothetical week B |
| --- | ---: | ---: |
| Total running duration | 420 min | 420 min |
| Designated quality sessions | 2 | 2 |
| Actual hard-work steps | 35 min | 75 min |
| Hard-time share | 35/420 = **8.33%** | 75/420 = **17.86%** |
| Non-hard running | 385 min | 345 min |

**Implication:** Quality-session count does not uniquely determine hard-work duration. Even hard-work minutes do not capture that 75 minutes near threshold and 75 minutes substantially above threshold are different prescriptions.

## Finding B — Published research confirms denominator and sensor effects (PEER-REVIEWED)

[Bellinger, Arnold & Minahan (2020)](https://pubmed.ncbi.nlm.nih.gov/31188692/) compared 695 sessions from 14 trained middle-distance runners. Their **same eight weeks** yielded substantially different low/moderate/high shares depending on the method:

| Training intensity quantification | Low % | Moderate % | High % |
| --- | ---: | ---: | ---: |
| Running speed | 79.9 | 5.3 | 14.7 |
| Heart rate | 79.6 | 17.0 | 3.4 |
| RPE | 39.6 | 31.9 | 28.5 |

These are **study-specific values**, not universal corrections. The investigation used zone cutoffs established for the participants, not arbitrary watch zone presets.

[Tønnessen et al. (2024), Norwegian endurance session models](https://pmc.ncbi.nlm.nih.gov/articles/PMC11560996/) likewise emphasizes that time-in-zone and session-goal classifications differ and that interval sessions require specifying work, relief intensity/duration, and between-set recovery.

[2025 IPD network meta-analysis](https://pubmed.ncbi.nlm.nih.gov/39888556/) found no significant overall advantage of polarized versus pyramidal distribution for VO2max or time-trial performance, while some response differences depended on athlete status. This cautions against assuming a strict 80/20 ratio is a scientifically proven optimum for every runner.

**Original design (hypothesis, not vendor behavior):** Preserve at least:
- `session_goal_class` (one class per session)
- `planned_seconds_by_objective` and `planned_seconds_by_workout_step`
- `observed_seconds_by_hr_zone` (explicit HR threshold version and missing-data flags)
- `observed_seconds_by_speed_zone` (explicit speed threshold version)
- `session_rpe`, when supplied
- `metric_confidence`, `weather_context`, and `route_context`
- `denominator_type`: per-session count, elapsed time, moving time, work time, or running distance.

Never display "80% easy" without stating *what* is 80%, and don't silently convert watch-defined HR Zone 2 to physiological below-LT1 time.

## Finding C — Garmin physiological load ≠ distance or intensity time (VENDOR/FIRSTBEAT-DOCUMENTED)

[Firstbeat, EPOC and Training Effect](https://www.firstbeat.com/en/science-and-physiology/epoc-and-training-effect/) explains that EPOC is a proxy for homeostatic disturbance/recovery demands and that its proprietary algorithm estimates EPOC from heartbeat patterns during activity. Training Effect is primarily EPOC, **scaled by athlete fitness/activity level**, on a 0–5 scale.

[Garmin, aerobic Training Effect](https://www.garmin.com/en-PH/garmin-technology/running-science/physiological-measurements/aerobic-training-effect/) gives the same example: a similar absolute EPOC dose can have a different Training Effect at different levels of fitness. [Garmin, training load](https://www.garmin.com/en-CA/garmin-technology/running-science/physiological-measurements/training-load/) describes recent EPOC-derived activity load as decaying over time rather than a simple sum of mileage.

[Garmin Training Effect support](https://support.garmin.com/en-ZA/?faq=Vi2undejXR5Mmq662o4lO9) states the output also depends on user profile, training history, HR, duration and intensity; anaerobic effect also uses speed or power. Its workout benefit labels can differ from intended target categories. Device and input provenance matter.

**Direct implication:** Do not infer "Garmin equivalent load" by `total_km * arbitrary coefficient`, `sum(minutes above HR threshold)`, or `sum(Training Effect values)`. Existing observed TE values may be useful **within-person context** but need validation before drawing coaching conclusions, and a TE label does not establish the athlete's actual adaptation.

### Conceptual separation to preserve

| Level | Source | Role |
| --- | --- | --- |
| External demand | Prescribed distance, pace and duration | What the plan asks |
| Executed movement | GPS/speed/time, stride, elevation | What was actually done |
| Internal response | HR, perceived effort, possible lactate data | Physiological response |
| Vendor estimated load | Garmin EPOC-load / Training Effect, where directly provided | Proprietary physiological-load proxy |
| Athlete outcome | Subsequent comparable performance and wellness | Evidence of adaptation or maladaptation |

A plan with a large *external* workload may not produce the same *internal* strain in two athletes. Conversely, two runs of the same distance can generate different physiological load due to pace, weather, terrain and recovery state.

## Finding D — Runna workout construction and Garmin load detection answer different questions

[Runna preferences](https://support.runna.com/en/articles/10393191-how-to-use-training-preferences) says Difficulty can alter number of reps/time running fast while target pace remains fixed; volume affects overall mileage. [Garmin Run Coach technical documentation](https://www.garmin.com/en-GB/garmin-technology/garmin-coach/garmin-run-coach/) says load composition influences future suggestions but should not trigger an immediate succession of workouts in a deficient category.

**Behavioral comparison that is testable (not yet observed):**
- Hold target paces constant, compare Runna `Comfortable` vs `Challenging`: quantify hard-work time and full-session distance from detailed *workout steps*.
- Compare subsequent **observed** Garmin exercise-load and Training Effect patterns for naturally completed workouts; record relative context and watch system, without fabricating a Garmin algorithm.
- Challenge a hypothetical StrideLab planner that balances `hard_session_count` against one that balances `hard_work_time + weekly strain + recovery constraints`. The latter may be more sensible, but only evaluation can establish this.

## First-user-specific question: faster truly easy running

For the initial high-volume StrideLab athlete, estimate *speed at a fixed narrow HR band* on comparable road segments and comparable fatigue/weather conditions. Maintain separate analysis for tight-loop vs road runs. Preserve raw FIT records and record missing HR/route/environment data.

- **Hypothesis:** Plan changes that affect hard-work dosage at unchanged weekly mileage might influence matched-HR speed over many weeks.
- **Counter-hypotheses:** Heat acclimation, route differences, HR sensor issues, fatigue, normal fitness progression, and concurrent strength sessions explain much of the movement.
- **Decision boundary:** One good run or one high Garmin Training Effect cannot establish increased aerobic fitness.

The initial N=1 history is excellent for personalization/edge-case evaluation but insufficient to train safe generalized defaults for a new runner.

## Research questions not yet answered

1. Do Runna-generated detailed plans actually maintain any **consistent work-time or effort-time budget**, when changing number of run days or Difficulty? We have no controlled current app output.
2. Does Runna use elapsed time, moving time, planned hard steps, distance or session-goal logic when describing an athlete's easy/hard proportions? Support pages are not a precise generator spec.
3. What is the effect of the *same step-level prescription* performed under different conditions on Garmin's proprietary load classification, holding other observable variables reasonably stable? Observational comparison cannot fully identify causal weights.
4. What data-quality and minimum observation window should a StrideLab adaptive policy require before increasing intensity, *without* interpreting one difficult workout as loss of long-term fitness?
5. Can a longitudinal benchmark distinguish changes to **matched-HR speed** from changes in heat tolerance or sensor drift?

**Evidence status:** Runna and Garmin vendor mechanisms documented, research evidence independently cited; hypothetical arithmetic explicitly labeled. No new current matched Runna exports, Garmin Run Coach prescriptive snapshots, or proprietary coefficients were obtained. No production code changes.

_Last reviewed November 2, 2026. Preserve uncertainty and source links when reusing._
