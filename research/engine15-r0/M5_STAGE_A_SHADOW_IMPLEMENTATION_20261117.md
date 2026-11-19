# M5 Stage A — shadow implementation
Date: 2026-11-17. Stacked on M4 Stage B PR #113. Preflight constraints: M5_STAGE_A_PREFLIGHT_20261117.md.

## Scope delivered
- Engine15M5WorkoutComposer consumes the typed independent M4B Result, rechecks its contents against recomputation, and returns unscheduled workout slots.
- Exactly N positive 100 m-aligned distance-only sessions must sum to the original M4B weekly budget; otherwise the whole result is deferred.
- For three or more run days, one EASY-intensity LONG slot is capped at both reported longest + 1 km and 40% of week volume. All remaining EASY slots are capped at 80% of reported longest + 1 km.
- For two run days, no LONG slot is invented. Both EASY sessions must fit the non-long exposure limit.
- Unknown longest exposure and non-distance M4B statuses defer. Recovery long-run exposure cannot increase versus previous week.
- Typed steps reuse IndependentWorkoutComposition and WorkoutStep. No Engine1 or live Engine15 generation, dates, pace, quality workouts, time estimates, persistence, approval, or production activation.

## Prespecified cases
- 75 to 80 km, 6 days: exact weekly budgets 67.5 / 71.2 / 75.0 / 60.0 / 71.2 / 75.0 / 78.7 / 62.9 km.
- 75 to 80 km, 3 days: first 54.6 km composed as 16.8 EASY + 16.8 EASY + 21.0 LONG.
- 75 to 80 km, 2 days: 37.8 km M4B upper-bound budget conflicts with a 16.8 km per EASY session limit; defer without silent reduction or LONG invention.
- 20 to 25 km, 2 days: 18.0 km as 9.0 + 9.0 km EASY.
- Baseline/longest missing, time-only, recovery hold, legacy-unconfirmed, tampered source: defer.

## Gate status
Committed to GitHub; focused and regression Gradle tests have NOT been executed for M5A yet. The M5A shadow validation gate remains pending.

Run on a checkout that contains this branch (from native-android):

    .\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15M5WorkoutComposerTest" --tests "io.stridelab.privatealpha.Engine15M4BIndependentVolumePlannerTest" --no-daemon

Existing M3/M4A/Q2/Q3/R0 regression must also remain passing. Passing these tests would not authorize production; M5 quality and M6 independent scheduling still need dedicated acceptance.
