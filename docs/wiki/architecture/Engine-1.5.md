---
title: "Engine 1.5 — Deterministic Training Coach"
area: architecture
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/EngineContracts.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine1Engines.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15IndependentPlanGenerator.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15MacrocyclePeriodization.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15DailyLoadShaper.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15WorkoutDifficultyAdjuster.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15ActivityAutoMatcher.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15ExecutionAnalyzer.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15AthleteDiagnosticsEngine.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15PlanAdaptationEngine.kt"
tags: [stridelab, engine15, periodization, coaching, adaptation]
---

# Engine 1.5 — Deterministic Training Coach

Engine 1.5 (`ENGINE15_VERSION = "1.5.0"`) is StrideLab's pure-Kotlin deterministic training plan generator, daily load shaper, difficulty scaler, post-run execution analyzer, and athlete-approved adaptation engine.

## Routing & Multi-Discipline Planning
- **`Engine1Engines.resolve(request)`** routes running plans to `Engine15IndependentPlanGenerator`, combined plans to `Engine15StaticMultiDisciplinePlanner`, and standalone resistance/conditioning tracks to `Engine15StrengthWorkoutComposer`, `Engine15CalisthenicsWorkoutComposer`, and `Engine15ConditioningWorkoutComposer`.
- **Plan Lifecycle:** Builder drafts live transiently in `TrainingState.planPreview`. Activation validates against the live approved calendar and commits atomically; legacy saved plan drafts are ignored.
- **Hybrid Plan Builder & Runner Support:** Generates independent snapshots for up to 6 tracks (Running, Strength, Plyometrics, Isometrics, Calisthenics, Conditioning), coordinates heavy lower-body/plyometric placement away from key runs, respects compatible fixed pairs and daily time limits, and activates all tracks atomically.

## Running Periodization, Volume & Quality Workouts
- **Zero-Mileage Beginners (`Engine15TimeOnlyRunWalkBuilder`):** `CONFIRMED_ZERO` beginners route to progressive timed Run/Walk cycles (`RunningPrescription` v4 `"TIME_ONLY_NO_DISTANCE_TARGET"`, `distanceKm = null`). Engine 1.5 never fabricates synthetic `runDays * 4.0` km baselines.
- **Proportional Macrocycle (`Engine15MacrocyclePeriodization`):** Scales `BASE` / `BUILD` / `PEAK` / `TAPER` proportionally across short (`4–6w`), medium (`7–11w`), and long (`12–16+w`) horizons. Beginners (`experience == 0`) and `returning == true` runners receive extended `BASE` (`50–65%` of loading weeks) and `0` `PEAK` weeks. "Train Your Way" uses `3:1` loading-to-recovery cycles with zero taper.
- **Volume & Long-Run Caps (`Engine15WeeklyVolumeSkeleton`, `Engine15IndependentVolumePlanner`, `Engine15M5WorkoutComposer`):** Enforces compound loading caps (`3.5%–8.0%`/loading week), 4th-week `0.80x` recovery cutbacks (suppressing double-deloads before taper), dual `10%` long-run progression caps (`Marathon 34 km`, `HM 22 km`, `10K 18 km`, `5K 16 km`), returning-runner `0.75 * longest` starts, and race tapers (`Marathon 0.85/0.70/0.55`, `HM/10K 0.80/0.60`, `5K 0.85`).
- **Day-to-Day Load Shaping (`Engine15DailyLoadShaper`):** Shapes non-long runs (`QUALITY 1.15x`, `AEROBIC_EASY 1.10x`, `STANDARD_EASY 1.00x`, `RECOVERY_POST_HARD 0.76x/0.82x`, `RECOVERY_PRE_LONG 0.76x/0.85x`, `RECOVERY_SANDWICHED 0.68x` including Sunday Long → Monday Recovery).
- **14-Archetype Library & 4-Step Reconciliation:** Covers Easy, Recovery, Strides, Fartlek, Short/Long Hills, Continuous/Broken Tempo, Threshold Cruise, Track Repeats, Classic VO2max, Speed-Endurance Pyramid, Steady Long, and Fast-Finish/Race-Pace Long Runs with energy-system recovery ratios and a 4-step reconciliation ladder preserving physiological warm-up (`>= 8 min`) and cool-down (`>= 5 min`) floors.
- **3-Tier Intensity Zones (`Engine15PaceZoneEstimator`):** (1) Explicit race/TT benchmark → (2) 56-day run blend (Riegel `1.06`) → (3) `request.planningPace`. Optional HR bounds populate strictly from verified 5-zone athlete profiles (never `220 - age`).

## Difficulty Scaling, Matching, Diagnostics & Controlled Adaptation
- **Reversible Difficulty (`Engine15WorkoutDifficultyAdjuster`):** Steps `-2` (*Much easier*) .. `0` (*Coached*) .. `+2` (*Much harder*) always recompute from immutable `PlannedSession.difficultyBaseline` with zero cumulative drift and zero catch-up mileage. `ExerciseAlternatives` supports easier/harder resistance swaps.
- **Read-Only Strength & Calisthenics Progression (`StrengthCalisthenicsProgression.kt`):** Preserves static plan blocks (`PLAN_SCHEMA = 1`) while projecting next-session overlays from confirmed `WorkoutLog` and freeform history keyed by canonical exercise ID (`ExerciseProgressionHistoryReader`). `StrengthProgressionProjector` delivers Session 1 effort-anchored benchmarks, double progression, and bounded 3–10 rep Epley e1RM dual options snapped to `2.5 kg` / `5 lb` plates; `CalisthenicsProgressionProjector` offers opt-in `ExerciseAlternatives.harder` upgrades when rep/hold ceilings are met; `ExerciseMuscleCatalog` supplies disjoint `primaryMuscles` / `secondaryMuscles` tags across library and built-in Plyometrics/Isometrics movements.
- **Confidence-Scored `.FIT` Auto-Matcher (`Engine15ActivityAutoMatcher`):** Scores open running sessions within `[-1, +1]` days (`0.45` date + `0.40` volume + `0.15` plausibility). `>= 0.80` same-day + `>= 0.15` margin → `AUTO_LINKED`; `0.50..0.79` or contention → `NEEDS_CONFIRMATION`; `< 0.50` → `UNMATCHED`. Never overwrites `MANUALLY_LINKED` or `MANUALLY_REJECTED`.
- **Post-Run Execution Analyzer (`Engine15ExecutionAnalyzer`):** Grades linked runs across evidence tiers (`LAP_AND_RECORD` → `RECORD_WINDOW` → `SUMMARY_ONLY`) into intent-specific verdicts (`INTENSITY_MISMATCH_TOO_FAST`, `VOLUME_ONLY_MATCH`, `PARTIALLY_EXECUTED`, etc.) with optional verified-zone HR compliance and `Pa:Hr` decoupling.
- **Confidence-Gated Diagnostics (`Engine15AthleteDiagnosticsEngine`):** Read-only 28d/7d/30d/42d evaluation across `VolumeTolerance`, `QualityExecution`, `EasyDiscipline`, and `AerobicEfficiency`. Physical load counts all runs; quality/easy grading uses linked analyses only; `hiddenFatigueRisk` (`>= 50%` easy gray-zone creep) blocks faster pace checks.
- **Controlled Adaptation & Rollback (`Engine15PlanAdaptationEngine`):** Never auto-mutates active plans or alters past/completed/linked sessions (`effectiveFromDate = asOfDate.plusDays(1)`). Supports 5 playbooks (`MISSED_WEEK_RECOVERY` 3 tiers, `PROACTIVE_DELOAD_SWAP`, `EASY_PACING_LOCK`, `PACE_RECALIBRATION_UP`, `PACE_RECALIBRATION_DOWN`). Every candidate must pass untouched `validateSaved`; `approveAdaptationProposal` / `revertLastAdaptation` archive and restore exact `previousPlanJson` locally.

## Protected Boundaries
Engine 1.5 is completely isolated from [[Freeform-Logger]] and [[Templates-and-Custom-Planner]] (see [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]). Shadow research modules (`Engine15I3..I10`, `IndependentAdaptiveWeek`, `Engine15HistoryInformedPlanner`) remain unwired from production generation.

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[Storage-and-FIT-Contracts]] · [[Hard-Guardrails]] · [[Guided-Workout-Player-and-Debrief]] · [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]
