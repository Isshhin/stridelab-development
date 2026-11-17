# M5 Stage B — independent quality preview shadow implementation

Date: 2026-11-17. Branch: feature/engine15-m5b-m4b-quality-previews-20261117. Stacked on M5 Stage A PR #114.

## Implemented
- Engine15M5QualityPreviewComposer.compose(M4B.Result, reviewedRequests) independently re-runs the M4B-to-M5A baseline composition before offering a quality alternative.
- A preview may be attached to an existing EASY slot in a BUILD week, never to a LONG slot or a recovery week. One quality preview per build week maximum.
- THRESHOLD and TEMPO variants use the research IndependentQualityFitnessAdapter with separately asserted recent work seconds, positive explicit work ceiling, optional externally permitted increment, available time and reviewer reference.
- Returning runners and two-day weeks are deferred; unknown/contradictory M4B evidence, infeasible base mileage and timed dose conflicts also defer.
- The result returns ORIGINAL M5A distance-only slot candidates unchanged, plus a separate timed quality preview. Timed segments are NOT automatically converted to kilometers, nor counted as an extra workout.
- Caller asserted review/reference does not verify Garmin/FIT segment intensity or authorise an actual prescription; all source evidence, distance reconciliation, date assignment, execution and production flags remain false.
- The live Engine15 generator, Engine 1.0 routing, saved plans, approvals, Android screens and Q2/Q3 paths are unchanged.

## Test scenarios
Nine focused M5B tests: candidate threshold, candidate tempo, missing/unauthorized exposure and ceilings, too-short time window, recovery/long day, returning/two-day rules, no request/duplicate/out-of-range requests, tampered M4B/zero/recovery hold, and multiweek invariant checks.

## Required validation on the Q3 checkout

From C:\Users\marcs\stridelab-q3-validation\native-android after merging this branch:

    .\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15M5QualityPreviewComposerTest" --tests "io.stridelab.privatealpha.Engine15M5WorkoutComposerTest" --tests "io.stridelab.privatealpha.Engine15M4BIndependentVolumePlannerTest" --no-daemon

Then run the M3, M4 Stage A, Q2, Q3 and R0 regression suites. Inspect any failures before approval. This branch has not been executed locally by the author; Gate B is OPEN pending Gradle results.

## Next
Do not activate until M5 timed-quality dose authority, M5 distance reconciliation and M6 cross-session calendar spacing are independently validated. Engine 1.0 cannot be retired for live run plans at this stage.
