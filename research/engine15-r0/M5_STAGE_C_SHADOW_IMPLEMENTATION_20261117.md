# M5 Stage C — shadow-only timed-quality distance arithmetic

Date: 2026-11-17. Branch: `feature/engine15-m5c-timed-distance-ledger-20261117`, stacked on M5B PR #115. See `M5_STAGE_C_PREFLIGHT_20261117.md` for the frozen acceptance contract.

## Implementation
- `Engine15M5TimedDistanceReconciler.reconcile(M4B.Result, M5B requests, segment claims)` recomputes M5B, which independently recomputes M5A and M4B. No Engine 1.0 or live Engine15 generator call.
- Flattens threshold intervals and tempo into an ordered list of timed segments, with recovery strictly between work repetitions. Does NOT derive metres from seconds or a guessed pace.
- Each segment must have explicit finite, positive caller-asserted metres, and exactly one claim per quality-preview slot with a nonempty review reference.
- All asserted segment distances must sum to the unchanged original M5A easy-run allocation within 0.01 m. A mismatch rejects the candidate; no catch-up or redistribution.
- Accepted results are called `ArithmeticOnly` and copy original base weeks and quality preview references unchanged. `sourceDistancesVerified`, `qualityClassificationVerified`, `scheduled`, `executable` and `authorizedForProduction` remain false.
- An external review reference is an asserted note, NOT proof of Garmin/FIT evidence or actual threshold, effort, time-to-distance conversion, or athlete safety.

## Tests
Seven new focused tests: threshold repetition expansion, tempo three-phase comparison, claim coverage/duplicate/slot/reference, missing or invalid segment distances, overbudget rejection, upstream tamper/recovery/return gate, and multiple build-week invariants.

## Validation status
Source, research preflight and tests committed to GitHub; NO local M5C Gradle run yet. M5 Stage C's gate remains pending.

From the `stridelab-q3-validation/native-android` checkout after merging this branch:

    .\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15M5TimedDistanceReconcilerTest" --tests "io.stridelab.privatealpha.Engine15M5QualityPreviewComposerTest" --tests "io.stridelab.privatealpha.Engine15M5WorkoutComposerTest" --tests "io.stridelab.privatealpha.Engine15M4BIndependentVolumePlannerTest" --no-daemon

Then rerun the M3/M4A/Q2/Q3/R0 regression tests. This is not an actual workout-distance estimator; production must remain untouched until independent verified evidence and M6 scheduling complete their respective gates.
