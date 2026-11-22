---
scope: engine-1.5
tags: [stridelab, engine15, training-science, guardrails, difficulty]
last_updated: 2026-11-22
---

# Engine 1.5 Rules & Coaching Guardrails

Back to [[index]] · Related: [[architecture]] · [[screens]]

## Non-Negotiable Invariants
- **Weekly Mileage Budget:** `sum(week.distanceKm) == week.proposedKm` within `1e-6` (enforced via clamped water-filling + tenths reconciliation in `Engine15DailyLoadShaper` and audited by `Engine15M6IndependentPeriodizationReview`).
- **Immutable Baseline & Determinism:** `PlannedSession.difficultyBaseline` stores the unadjusted session; `validateSaved(plan)` deterministically recomputes expected output purely from `plan.request` (no `activities` argument) and verifies `Engine15WorkoutDifficultyAdjuster.matchesAdjusted`.
- **Pure Previews:** Weekly reviews and coaching suggestions never mutate plans until explicit atomic user approval (`Engine15WeeklyReview.accept`).

## Baseline Provenance & Zero-Mileage Beginners
- `Engine15IndependentPlanGenerator.previewAutoReconciled` never fabricates synthetic `runDays * 4.0` km or promotes `CONFIRMED_ZERO`, `UNKNOWN`, or `PEAK_WEEK_ONLY` mileage to `SELF_REPORTED_AVERAGE` (positive-km `LEGACY_UNSPECIFIED` retains backward compatibility).
- `CONFIRMED_ZERO` beginners—including those with positive `targetWeeklyKm`, `qualityCount > 0`, or combined strength/calisthenics—route to `Engine15TimeOnlyRunWalkBuilder` (`RunningPrescription` v4 `"TIME_ONLY_NO_DISTANCE_TARGET"`, `distanceKm = null`).

## Volume, Long-Run Caps & Race Tapers
- **`Engine15WeeklyVolumeSkeleton.multiFactorCeiling` (used by `Engine15IndependentPlanGenerator`):** Replaces the old `1.10x`/`1.15x` ceiling with compound loading progression grounded in recent sustainable mileage, `runDays`, `experience` (`3.5%–8.0%`/loading week ramp), race peak caps (`Marathon` / `Half` / `10K` / `5K`), 30-day single-session exposure bounds (`longestKm`, `SINGLE_SESSION_30D_EXPOSURE_CAP`), and 4th-week `0.80x` recovery cutbacks (`isSuppressedPreTaperRecovery` prevents double-deload before tapers). Legacy `ceiling(...)` remains untouched for shadow parity.
- **`Engine15IndependentVolumePlanner`:**
  - Returning runners start at `0.75 * longest` (`5%` progression cap when non-race) and `0.60x..0.72x` volume (`returnCeilingKm`).
  - Race goals use dual long-run caps: Week 1 `minOf(longest + 1.0, longest * 1.10)`, then `minOf(effectiveLongMaxKm, prev + stepKm, prev * 1.10)` (Marathon `34 km`, HM `22 km`, 10K `18 km`, 5K `16 km`).
  - Tapers: Marathon (12+w) `0.85/0.70/0.55`, Half/10K (10+w) `0.80/0.60`, 5K (6+w) `0.85` (`0.78` if landing on recovery week).
- **`Engine15M5WorkoutComposer`:** Taper long-run share drops to `0.28` (`0.34–0.38` Marathon/HM loading, `0.30` other, hard cap `0.40`, plus cross-week `<= 1.10 * peakLongUnits` cap so post-recovery weeks never spike `>10%`); 2-day race plans allow `minOf(longMaxUnits, tenths(weekExposure * 0.85))`. `PlanRequest.longRunMinutes` supports separate weekend long-run duration caps under `timeBudgetEnabled`.
- **Taper Quality:** `Engine15QualityFamilySelection` + `Engine15QualityProgressionPolicy` cap taper quality to `<= 1` session/week with `RACE_TAPER_REDUCTION` sharpening.

## Quality Workouts, Dosing Ceilings, 14-Archetype Library & Reconciliation Ladder
- **Families, Dosing & Energy-System Recovery (`Engine15QualityProgressionPolicy`, `Engine15QualityFamilySelection`):**
  - **Weekly Hard-Work Ceilings:** `VO2_INTERVAL` `6–8%` weekly km (`12–24 min`; capped at `18 min` when 2 quality/week), `THRESHOLD` `8–10%` (`16–36 min`), `TEMPO`/`STEADY`/`FARTLEK` `10–12%` (`20–45 min`), `HILLS` `8–12 min` (`<= 10 min` when 2 quality/week).
  - **Energy-System Recovery Ratios (no flat `2 min` default):** `STRIDE` `1:3–1:4` (`20s` → `60–75s` walk/jog), `HILL` `1:2–1:3` (`45–60s` → `90–120s`, `90–120s` → `2:00–2:30`), `VO2_INTERVAL` `1:0.5–1:1` (`400m` → `200m`/`70s`, `800m` → `400m`/`2:00`, `1km`/`4m` → `400m`/`2:30`), `THRESHOLD` `1:0.2–1:0.3` (`1km`/`4–5m` → `60s`, `2km`/`7–8m` → `90s`), `BROKEN_TEMPO` `1:0.15–1:0.2` (`10–15m` → `90–120s`).
  - **Beginner & Returning Rules:** `experience == 0` and `returning == true` cap at `1` quality/week, hold entry rung dose for 2 loading weeks before stepping up, and avoid track VO2max reps in favor of Fartlek, Short Hills, Continuous Tempo, and Strides (unless explicitly overridden by `allowedQualityTypes`).
- **14-Archetype Library (`Engine15WorkoutArchetype`, `RunningPrescription.archetypeId`):**
  - *Aerobic & Neuromuscular:* `Recovery run` (`Easy max + 15–30 s/km`), `Easy Aerobic Run`, `Easy Run + Strides` (`4–8 × 20s` `STRIDE`), `Aerobic Fartlek` (`4–7 × 2–3m`).
  - *Hills:* `Short Hill Sprints` (`6–8 × 45–60s`), `Long Aerobic Hills` (`4–6 × 90s–2m`) — explicit uphill effort/grade cues and uphill-adjusted pace bounds slower than flat interval pace.
  - *Threshold & Tempo:* `Continuous Tempo` (`12–30m`), `Broken Tempo` (`2×10m`–`3×15m`), `Threshold Cruise Intervals` (both distance-based `1km`/`2km` and time-based `4–8m`).
  - *VO2max & Speed Endurance:* `Short Track Repeats` (`6–12 × 400m`), `Classic VO2max Intervals` (distance-based `800m`/`1km` and time-based `3–4m`), `Speed-Endurance Pyramid` (`400-800-1200-800-400m` / `1km-2km-1km-800m`, mixed `VO2_INTERVAL` + `THRESHOLD` intents).
  - *Long Runs:* `Steady Aerobic Long Run`, `Fast-Finish / Race-Pace Long Run` (75/25 `STEADY`, `4–10 km` HM `RACE_PACE`, or `6–14 km` Marathon `RACE_PACE` finish on Build/Peak loading weeks only; never on recovery, taper, or returning-runner plans).
  - *Goal & Train Your Way Selection:* `5K`, `10K`, `Half marathon`, `Marathon`, plus `Train Your Way` (`Aerobic foundation` → Fartlek/Hills/Strides/Steady Long; `General fitness` → Threshold/Broken Tempo/Fartlek/VO2max/Fast-Finish Long).
- **Persisted Targets (`RunningStepV2` & `RunningSegment` in `EngineContracts.kt`):** Persists `stepIntent` (`RunningStepIntent`), `targetPaceMinSecPerKm`, `targetPaceMaxSecPerKm` (`Engine15PaceZoneEstimator.paceBoundsForIntent`), and strictly optional `targetHrMinBpm` / `targetHrMaxBpm` (populated only from verified 5-zone athlete HR profiles via `hrBoundsForIntent`, never invented `220 - age`). Distance-based `RunningStepV2` steps keep `meters != null && seconds == null` while timed WU/CD/recoveries reconcile exact session `meters`. Pre-Step-01/02 snapshots decode safely (`null` defaults) and pass `validateSaved` via `RunningPrescription.matchesStructuralBaseline`.
- **4-Step Quality Reconciliation Ladder (`previewAutoReconciled`):**
  1. Reconcile rounding and flexible weekly quality-slot sizing (`Engine15DailyLoadShaper` `minQualitySlotKm`, respecting `timeBudgetEnabled`) + release conflicting `qualityDays` if needed.
  2. Adjust warm-up and cool-down strictly within safe physiological floors (**`>= 8 min` warm-up / `>= 5 min` cool-down**, combined `13..90 min`, verified by `Engine15M5TimedDistanceReconciler.bookendSafetyWarning()`), falling back from distance-based to time-based rungs when slot size requires.
  3. Step down to the highest safe lower-dose rung in the same quality family ladder when current dose cannot fit safely.
  4. Return explicit `Deferred("QUALITY_RECONCILIATION_UNSAFE_OR_INFEASIBLE", ...)` for review rather than silently erasing `qualityCount = 0`.
- **3-Tier Intensity Zones (`Engine15PaceZoneEstimator`):** (1) Explicit race/TT benchmark → (2) 56-day historical run blend (Riegel exponent `1.06`) → (3) `request.planningPace`.

## Daily Load Shaping & Multi-Discipline Scheduling
- **`Engine15DailyLoadShaper`:** Shapes non-long runs with `QUALITY 1.15x`, `AEROBIC_EASY 1.10x`, `STANDARD_EASY 1.00x`, `RECOVERY_POST_HARD 0.76x/0.82x`, `RECOVERY_PRE_LONG 0.76x/0.85x`, `RECOVERY_SANDWICHED 0.68x` (including Sunday Long → Monday Recovery).
- **`Engine15StaticMultiDisciplinePlanner`:** Splits `place` → `reshapeCombinedRuns` → `annotateCombinedNotes`; enforces Tier-3 `sameDay.size < 2` and `dailyMinutes` (when `timeBudgetEnabled`), ranks same-day pairs (`empty < easy+upper < easy+hard < hard+hard`), and passes `externalHardDates` + `maxMinutesBySessionId` into `Engine15DailyLoadShaper` and `validateSaved`.
- **Non-Running Composer Limits:** Strength (`str`: 5 patterns, 1–2 sets, 4-week rep cycle), Calisthenics (`cal`: 8 curated IDs, `Handstand foundations` always defers), and Conditioning (`cond`: single `cond-step-jack` movement, `15–21 min`) are static, reject overlapping existing plans (`EXTERNAL_SCHEDULE_REVIEW_REQUIRED`), reject free-text `limitations`/`recoveryNotes`, and are never targeted by `Engine15Feedback`/`Engine15WeeklyReview`.

## Difficulty Scaling (`Engine15WorkoutDifficultyAdjuster`)
- Reversible steps `-2` (*Much easier*) .. `0` (*Coached*) .. `+2` (*Much harder*), always recomputed from `session.baselineSession()` (zero cumulative rounding drift, exact `Reset` on `0`, zero catch-up mileage).
- Quality `-2` converts to `"Recovery run (eased)"`; `-1/+1/+2` scales interval reps/duration/distance; resistance workouts scale sets/reps + append RIR cues (`ExerciseAlternatives` provides easier/harder swaps).

## Adaptation, Linking & Shadow Modules
- **Preview-Only Local Adaptation:** `Engine15Feedback` (next 1–4 days, single session) and `Engine15WeeklyReview` (next 7 days, running targets only) attach `SessionSuggestion` time-only overlays after explicit user approval—never multi-week replanning and never triggered by unlinked Garmin runs.
- **Weekly Review Guardrails:** Blocks `Direction.PROGRESS` on scheduled recovery (`weekIndex % 4 == 3`, excluding suppressed pre-taper peak) and race taper weeks; clamps `long`-run progression within `<= 10%` of recent longest distance; requires explicit `effort in 1..5` RPE corroboration.
- **Manual-Only Activity Matching:** Zero automated workout-to-activity matcher exists; `ActivityPlanLink.eligiblePlannedRunsForLink` offers manual linking strictly within `recordedDate - 3d .. recordedDate` (running only). `Engine15WorkoutDetection` classifies laps heuristically for initial planning context only. **Rule:** Any future lightweight auto-matching introduced for UI/coaching suggestions must remain strictly separated from verified workout adherence (`status == "completed" || linkedActivityId != null`).
- **Shadow Research Modules:** `Engine15I3..I10`, `IndependentAdaptiveWeek`, `IndependentConstrainedMultiweekPlanner`, and `Engine15HistoryInformedPlanner` are read-only/research fixtures not wired into `TrainingRepository.generatePlan`.
- **Aerobic Efficiency (`RunningAerobicEfficiency`):** Requires `>= 6` qualifying runs (`>= 3 km` with HR), `>= 3` weeks, and **`>= 3` runs in both `earlyWeeks` and `lateWeeks`** before reporting `percentChange`.
