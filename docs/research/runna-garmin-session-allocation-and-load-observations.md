# Runna and Garmin: Session allocation, long-run taxonomy, and observed physiological load

**Research date:** 2026-11-02  
**Evidence status:** Vendor-documented product behavior with specific illustrative calculations; **not** recovered proprietary source code.  
**Relationship:** Companion to [Runna and Garmin Coaching Algorithm Research](runna-garmin-coaching-algorithm.md) in StrideLab draft PR #34.

## Why this investigation matters

The planning problem consists of at least four non-identical tasks:

1. **Set the weekly constraints** (experience, feasible frequency, availability and total planned training).
2. **Select workout objectives** (low-aerobic, threshold, race-specific practice, etc.).
3. **Construct session structure** (continuous effort, progression, blocks, interval/recovery trees).
4. **Evaluate measured training stimulus** after completion (which may differ from intended classification).

Runna gives clearer documentation for the *first three*; Garmin documents the *fourth* and longer-term load balance. They should not be assumed to share the same algorithm. Differentiating these is a candidate **original StrideLab architecture**, not an assertion about vendor code.

## Finding 1: Four Runna long-run classes are independently documented (VENDOR-DOCUMENTED)

Runna [RLR] describes:

| Class | Format | Intended stimulus | Decision implication |
| --- | --- | --- | --- |
| **Unstructured** | Conversational and easy throughout, no harder targets | Aerobic base, time-on-feet, less stress | Useful between demanding long-run weeks |
| **Progression** | Start easy and increase pace in blocks (example every 3 km) | Controlled harder finish, fatigue pacing | Need a late-run fast-dosage field |
| **Blocks** | Easy running interleaved with steadier/Zone 3 effort | Additional aerobic challenge, moderate rather than race-pace stress | Avoid treating all km as identical intensity |
| **Race-pace practice** | Race-pace work reps within a longer run, broken up by easy running | Specificity to race speed | Count this as additional high-intensity exposure when distributing weekly sessions |

The vendor explicitly says unstructured runs appear *between harder long runs*. It does **not** disclose a reliable deterministic sequence like easy-progressive-blocks-race every four weeks, nor the exact selection thresholds. Those remain **UNKNOWN**.

**Original independent hypothesis:** The long-run planner should produce `{length, class, easy_distance, steady_distance, race_pace_distance, recovery_spacing}`, not `{length}` alone.

**Critical recovery nuance:** [RLR] warns that moving a long run between weekdays can bring two successive long runs close together. A per-calendar-week allocation algorithm can miss this risk unless it checks *elapsed days* since the prior significant long-run dose. This is a reason to validate rolling recovery spacing independently; it does not establish Runna's actual internal checks.

Source [RLR]: https://support.runna.com/en/articles/9357249-understanding-the-long-runs-in-your-runna-plan (Aug 31, 2026).

## Finding 2: Frequency/ability limits and schedule edits are separately constrained (VENDOR-DOCUMENTED)

Runna [RSCHED] provides these accepted run-frequency ranges:

| Ability category | Runs/week |
| --- | --- |
| New to Running | 2–4 |
| Beginner | 2–4 |
| Intermediate | 2–5 |
| Advanced | 2–6 |
| Elite | 2–7 |
| Elite Plus | 2–7 |

Runna also recommends selecting the currently sustainable frequency or at most one extra, and documents a minimum of two days/week for its plans. When runners set *preferred days* and a *preferred long-run day*, the plan is arranged around these constraints. It distinguishes a **global schedule preference update** (applies across plan) from a **one-off calendar drag-and-drop** (temporary workout move).

**Interesting distinction:** A global preference edit may require regenerating future day assignments, whereas a one-off move is a local calendar change. If a plan engine treats both operations identically, it may accidentally change too much training.

**Inferred StrideLab design/test:** Keep `available_days`, `target_runs_per_week`, `preferred_long_run_day`, and `manual_calendar_overrides` as separate provenance. Confirm ability constraints and recovery-in-days before scheduling, not just a maximum number of sessions per ISO week.

Source [RSCHED]: https://support.runna.com/en/articles/6206024-adjusting-your-running-schedule (Sep 7, 2026).

## Finding 3: Workout labels conceal real exposure; a complete workout-step tree is necessary (VENDOR-DOCUMENTED + arithmetic)

Runna's [RWORK] illustrative workout is:
- Warm-up **800 m**
- Repeat three times: **400 m faster + 400 m easier**
- Then **90-second walking rest**
- Cool-down **800 m**

Its displayed faster-rep target is 6:20/km and easier rep 6:55/km; this is vendor example data, **not an athlete-derived prescription**.

**Reproducible calculation from source illustration:**
- Faster running = 3 × 400 m = **1,200 m**
- Recovery running inside repeated set = 3 × 400 m = **1,200 m**
- Warm-up + cool-down = 1,600 m
- Total prescribed running distance = 1,200 + 1,200 + 1,600 = **4,000 m**
- Faster-running distance fraction = **30%** of prescribed running distance.
- The separate 90-second walk is **time**, not a fixed distance: it must not be silently added to the total distance.

This matters because a summarizer that interprets `3 × 400 m` as the **entire** workout loses 70% of the programmed distance, and one that interprets an easier 400 m step as idle rest loses load.

A structured workout should preserve *nested repetitions, set-level rest vs per-rep rest, manual/automatic step progression, warm-up/cool-down and target modality*.

Source [RWORK]: https://support.runna.com/en/articles/15690947-understand-your-runna-workouts (2026, recently updated).

## Finding 4: Garmin's intended workout category is not identical to measured Training Effect (VENDOR-DOCUMENTED)

Garmin Run Coach [GRC] classifies recommendations by intended physiological objective, including base/recovery, tempo, threshold, VO2max, anaerobic and sprint work. Garmin's Training Effect documentation [GTE] warns that the resulting *post-run* training-effect label can differ from intended workout category; its example says that if HR does not sufficiently decrease between intense reps, the reported effect may be labeled Tempo rather than Threshold.

**This is a methodological boundary, not proof of an algorithm defect.** Post-run Training Effect is a device-derived summary of actual physiology and input data; a prescribed workout title describes intended stimulus.

**Original StrideLab implication:** Preserve separately:
- `prescribed_objective` (the coach's intent)
- `planned_workout_steps`
- `actual_step_trace` (recorded HR/pace where available)
- `post_run_vendor_training_effect` (as reported by Garmin, if present)
- `observed_stimulus_model` (our separately validated estimate, if any)
- `data_quality/confidence`

Do not automatically downgrade an athlete's threshold performance or their next pace because the *device label* on one workout was different.

Sources:
- [GRC] https://www.garmin.com/en-GB/garmin-technology/garmin-coach/garmin-run-coach/
- [GTE] https://support.garmin.com/en-ZA/?faq=Vi2undejXR5Mmq662o4lO9

## Finding 5: Garmin DSW and Garmin Run Coach data can overlap but must not be pooled (VENDOR-DOCUMENTED)

Garmin [GDSW] says that its **Daily Suggested Workouts** are calculated separately on each device (with some shared data on Unified Training Status devices), and an active Garmin Coach plan or third-party structured training plan takes precedence in the **prompt** over DSW recommendations. Therefore, the recommendation the runner sees first is not necessarily the same source across time.

On the Forerunner 165, DSW support exists; however, not every Garmin higher-end physiological metric is available. Garmin's [GLOAD] load-focus screen is listed for some higher-end watches, **not the 165**. Do not fabricate Training Load Focus when analyzing 165-only history.

**Observation design:** Every snapshot needs `system=RUN_COACH|DSW|EXPERT`, `watch_model`, `firmware`, `capture_time`, `active_plan_id/type`, `last_sync` and `metric_availability`. A transition from a DSW base recommendation to a scheduled Run Coach threshold prompt may reflect **prompt precedence**, not any adaptive fitness change.

Sources:
- [GDSW] https://support.garmin.com/en-GB/?faq=oYknGZ910l1pfBNzkDHX6A
- [GLOAD] https://support.garmin.com/en-GB/navionics/faq/SEkNpdGyhR917js0qQL3Q6/

## Finding 6: The load-focus classification is a rolling physiological estimate, not hard-session counting (VENDOR-DOCUMENTED)

Garmin's [GFOCUS] supports three load focus categories over approximately four weeks: low aerobic, high aerobic and anaerobic. The vendor says load focus reflects detected physiological impact, not the number of sessions bearing an intensity label.

Its [GRC] documentation explicitly warns that a shortage of anaerobic load does *not* imply that the algorithm should prescribe several sprint sessions in immediate succession. Thus:
- Total exercise count does not equal training load.
- Total distance does not equal physiological load.
- Labeling a workout 'threshold' does not guarantee that the resulting recorded session contributes high-aerobic load as intended.
- Garmin's exact EPOC-based scoring and decision thresholds remain proprietary.

Garmin [GLOAD] says load focus takes **at least seven days** to begin informing category feedback and recommends approximately **four weeks of data** for better characterization. Device support varies.

**Original coaching hypothesis to test:** Proposed hard workouts should have a constraint-aware selection score that respects session sequencing and recovery as well as longer-term load distribution, rather than selecting the most underrepresented category in isolation. This is a testable design idea, not recovered Garmin code.

Sources:
- [GFOCUS] https://www.garmin.com/en-US/garmin-technology/running-science/physiological-measurements/training-load-focus/
- [GRC] https://www.garmin.com/en-GB/garmin-technology/garmin-coach/garmin-run-coach/
- [GLOAD] https://support.garmin.com/en-GB/navionics/faq/SEkNpdGyhR917js0qQL3Q6/

## Updated research questions and experimental comparisons

The most informative questions now are **not** answered by more general marketing text:

1. **Runna long-run class selection:** At matched training history and race goal, which weeks get unstructured vs progression vs blocks vs race-pace long runs? Does selecting lower difficulty change subtype or only dose?
2. **Runna within-week schedule:** When preferred long-run day changes, are intensity sessions moved to preserve recovery intervals? How does a temporary one-off move differ from a persistent setting change?
3. **Runna workout-dose accounting:** Compare detailed work/recovery steps for equivalent workout objective across ability, difficulty, volume preference, and plan week. Do not infer dose from a title or total distance alone.
4. **Garmin measured vs intended training:** For repeated, legitimately recorded prescribed workouts, compare planned category against actual Training Effect and HR-recovery behavior, while tracking environmental and sensor-quality confounders.
5. **Garmin Coach vs DSW:** Confirm whether the active plan changes which recommendation is surfaced, and record each system separately; don't call a suppressed prompt an adaptive substitution.
6. **Long-run risk and aerobic-efficiency lens:** On the initial high-volume StrideLab athlete, test how a steady easy long run versus a structured long run changes late-run pace/HR drift and recovery, using naturally occurring workouts; cannot assert which is more effective from one athlete without controlled comparisons.

**Status:** These six findings are *vendor-supported behavioral descriptions* plus a reproducible arithmetic example, not the exact private generator. No fresh controlled personalized Runna plan outputs, Garmin Run Coach prescription snapshots or physiological causal outcomes were acquired in this round. Comparing like-for-like exported prescriptions remains necessary.

_Last source review: 2026-11-02. No app production-code changes authorized or made._
