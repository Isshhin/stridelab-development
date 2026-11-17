# Engine 1.5 M6 Stage C — independent cross-discipline conflict review (shadow)

Date: 2026-11-17. Stacked on M6B PR #118; original gate in `M6_STAGE_C_PREFLIGHT_20261117.md`. No new code in production routing.

## Delivered
- `Engine15M6IndependentInterferenceReview.review(M4B.Result, M6CReviewInput)` recomputes M6A for dated easy/long runs, and optionally recomputes M6B for reviewed quality *opportunities*. Does not import Engine 1.0 schedules or create non-running prescriptions.
- Caller must explicitly assert external calendar coverage and supply a list of typed, uniquely identified STRENGTH/CALISTHENICS/CONDITIONING sessions with UPPER_BODY/LOWER_BODY/FULL_BODY_HARD/EASY_GENERAL load labels. Null or unasserted calendars defer; this assertion does NOT verify actual completeness or evidence.
- Run/external same-day combinations defer by default. With explicit opt-in the only provisionally compatible pairing is one UPPER_BODY strength or calisthenics entry with an EASY run that has no reviewed quality opportunity. LONG, quality, conditioning, lower-body and hard combinations are not approved for same-day pairing.
- All lower/full-body hard external sessions are separated by at least two calendar days from each LONG and reviewed quality opportunity across week boundaries. Adjacent external hard dates and multiple external sessions on the same date require separate review.
- Supplied external dates are restricted to horizon plus one adjacent day at either end. Missing dates are not invented or silently filtered.
- M4B weekly budgets, typed M5A workouts, M6A dates, optional M5B timed quality and M6B opportunities are untouched. No automatic relocation, reduction, additional distance, or quality promotion.
- All candidate flags remain false: externalCalendarVerified, sourceEvidenceVerified, timeBudgetValidated, qualityScheduled, executable and authorizedForProduction.

## Tests
Eleven focused scenarios: Thursday strength with Thursday-free running, same-day upper opt-in and no time certification, same-day reviewed quality conflict, Monday lower prior to Tuesday quality, cross-week Monday lower after Sunday long, Saturday lower before Sunday long, adjacent lower/hard external sessions, same-date external duplicates, missing/invalid external calendars, tampered M4B and upstream quality-review deferral, deterministic unchanged workouts and quality opportunities.

## Gate C validation
No local Gradle run was performed when this code was committed. Q3 validation checkout required. From `native-android`:

    .\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15M6IndependentInterferenceReviewTest" --tests "io.stridelab.privatealpha.Engine15M6IndependentQualitySpacingTest" --tests "io.stridelab.privatealpha.Engine15M6IndependentCalendarTest" --tests "io.stridelab.privatealpha.Engine15M5TimedDistanceReconcilerTest" --tests "io.stridelab.privatealpha.Engine15M5QualityPreviewComposerTest" --tests "io.stridelab.privatealpha.Engine15M5WorkoutComposerTest" --tests "io.stridelab.privatealpha.Engine15M4BIndependentVolumePlannerTest" --no-daemon

Also run M3, M4 Stage A, Q2, Q3 and R0 regression suites. Even a passing Gate C does not establish source authenticity, time-duration feasibility, fatigue safety, independent external discipline composition, or end-to-end Android eligibility. Remain shadow-only; do not retire Engine 1.0.
