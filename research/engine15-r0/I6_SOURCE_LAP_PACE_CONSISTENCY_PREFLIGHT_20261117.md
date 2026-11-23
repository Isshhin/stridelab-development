# I6 — Source-Linked Work-Lap Pace & Distance Consistency (Preflight)

Frozen 2026-11-17 before implementation. Stacked on I5 draft PR #125. User validated I5 plus all selected M3–M6, I1–I4, Q2/Q3/R0 regressions: `BUILD SUCCESSFUL in 26s`. This stage is **shadow-only**.

## Gap and basis
- M5C verifies caller-asserted distance arithmetic only. I5 verifies an allocated EASY slot lies within caller-supplied speed envelopes, not that source observations justify those envelopes.
- I3 checks two distinct linked FIT/Garmin work-lap durations. I4 checks work-lap HR signal coverage and an optional lap HR average. The existing `ActivityLap` also has recorded distance in meters and pace in sec/km; `ActivitySample` has elapsed seconds and pace sec/km, but no authenticated owner or verified effort intensity.

## I6 contract
1. Recompute I5 from the original M4B Result + M5B request + I3 source evidence + I5 speed windows. Any I5/I4/I3/M6B deferral propagates before pace analysis.
2. Inspect ONLY the original two bound work-lap recordings (from I4's I3 bindings). Validate matching ActivityDetail and unique selected lap, positive finite recorded lap metres and seconds, no pause overlap (already enforced by I3). Recompute raw lap average `seconds*1000/metres` descriptively; if source lap pace is present, require agreement within max(20 sec/km, 12% of raw average). Reject contradictions, never replace data.
3. Require increasing sample timestamps and index (inherited I4), use only samples inside selected work lap. A valid pace sample is finite 60..7200 sec/km (extreme numeric input sanity only, NOT athlete-specific). Missing pace samples reduce coverage. Consecutive valid pace pairs separated <=30 s contribute time; no interpolation over longer gaps or missing data. Each selected work lap must have >=80% source pace coverage.
4. Compute time-weighted mean recorded sample speed (m/s) using trapezoidal pairs `1000/pace`, and compare to the recorded lap speed `distance/seconds` within 15% relative tolerance (data-coherence heuristic, not medical guidance). An implausible/nonfinite/out-of-range sample within a selected lap is a deferral, not silently excluded.
5. Each observed work-lap average speed from both distinct linked sessions must fall in at least one of the original caller-asserted WORK-phase speed windows (±0.05 m/s numeric tolerance). This is a *contradiction* check only: historical lap speed cannot verify threshold/tempo intensity or guarantee that future bounds are safe. Do not derive new limits from observed speeds.
6. Return original, unchanged I5 + I4/I3/M5B/M6B outcomes and descriptive lap metrics (lap meters, seconds, source-reported lap pace if supplied, derived lap pace, observed speed and coverage). Never infer a new phase distance or rewrite the independent M4B weekly mileage.
7. Keep `lapAndPaceSignalsConsistent=true` (descriptive), but `sourceOwnerAuthenticated=false`, `paceCalibrationVerified=false`, `qualityIntensityVerified=false`, `actualFutureSegmentDistanceVerified=false`, `athleteResponseVerified=false`, `doseAuthorized=false`, `executable=false`, `authorizedForProduction=false`. Synthetic thresholds are not a coaching prescription.

## Prespecified tests
- Two distinct synthetic source-linked FIT/Garmin activity details with selected 240s/1000m work laps at 240 sec/km, HR 150, samples every 10s and explicitly asserted WORK 2.8–5.0 m/s: descriptive pass only; no verified pace/intensity or production permission.
- Missing lap distance, missing/sparse pace stream, nonfinite or impossible pace sample, contradictory lap summary pace, sample-vs-lap speed mismatch and selected source details from wrong ID defer.
- Asserted work-phase speed ranges excluding 4.167m/s reject even if I5 mathematical daily distance range still contains the original budget.
- I5 arithmetic impossibility, unreviewed quality, missing I4 HR and duplicate I3 source cannot bypass the gate.
- Repeatability and unchanged original M4B/M5A workouts.

## Exit criterion
Focused I6 Kotlin unit tests plus I5/I4/I3/IndependentQualitySourceBinding/I1/I2/M3–M6/Q2/Q3/R0 selected regressions on Q3. Passing is not source authentication, verified threshold/tempo intensity, dose approval, or live Engine 1.5 activation. M9 Android QA/production admission remains separate.
