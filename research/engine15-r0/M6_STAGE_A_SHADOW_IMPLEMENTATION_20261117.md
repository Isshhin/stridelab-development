# M6 Stage A — shadow independent date-placement implementation

Date 2026-11-17. Branch `feature/engine15-m6a-independent-calendar-20261117`, stacked on M5C PR #116. See frozen spec `M6_STAGE_A_PREFLIGHT_20261117.md`.

## Delivered
- `Engine15M6IndependentCalendar.schedule(M4B.Result, Engine15M6CalendarInput)` independently recomputes M5A and assigns calendar dates to M5A distance-only EASY and LONG slots.
- Explicit Monday start, ISO availability set, preferred long day, blocked exact dates and optional easy weekday preferences. At 3+ days, LONG stays on requested weekday; with two days no LONG is invented.
- Deterministic distribution uses preferred easy weekdays, maximum minimum cyclic separation, then lowest spacing variance, then lexical ISO weekday order. This is a placement heuristic, not recovery/athlete safety approval.
- Full-week run count and exact M4B budget retained. Every M5A typed workout is carried through unchanged and assigned exactly once. No hidden reduction, catch-up, or guessed running pace.
- Blocked long day, unavailable long day, too few run days, wrong start weekday, tampered budget, missing exposure and unknown/held workload return explicit deferrals.
- Quality overlays remain *entirely unscheduled*. `calendarAssigned=true` describes a candidate preview; `qualityScheduled=false`, `executable=false` and `authorizedForProduction=false`.
- No Engine 1.0, current Engine15.generate, backup, saved plans, Android UI, approval or production code changed.

## Focused acceptance tests
Nine test methods cover: 6-day Thursday-off Sunday-long eight-week example; 3-day 54.6km exposure cap; 2-day easy-only; blocked easy alternative; blocked/unavailable long day; insufficient availability; invalid Monday and tampered M4B; missing/hold/infeasible budget; deterministic returning-runner schedule and preservation of typed workouts.

## Validation
Source and tests committed; no Gradle test executed by the author yet. M6 Stage A is implemented, **not validated**. On the Q3 validation checkout after merging:

    .\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15M6IndependentCalendarTest" --tests "io.stridelab.privatealpha.Engine15M5TimedDistanceReconcilerTest" --tests "io.stridelab.privatealpha.Engine15M5QualityPreviewComposerTest" --tests "io.stridelab.privatealpha.Engine15M5WorkoutComposerTest" --tests "io.stridelab.privatealpha.Engine15M4BIndependentVolumePlannerTest" --no-daemon

Follow with M3/M4A/Q2/Q3/R0 regression. Production adoption must wait for M6 quality-spacing, cross-discipline conflict management, source/dose authority, and subsequent Android acceptance. Do not retire Engine 1.0 now.
