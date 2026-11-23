# M5 Stage C — timed quality distance arithmetic, preregistered gate

Date: 2026-11-17. Branch: `feature/engine15-m5c-timed-distance-ledger-20261117`, stacked on M5B PR #115. Stage B has local Q3 validation `BUILD SUCCESSFUL in 24s` with selected M3/M4/M5/Q2/Q3/R0 regression. Stage C has NOT been validated.

## Goal
Reconcile every selected timed quality segment with the EXISTING, distance-only M5A EASY slot in the independent M3 -> M4B -> M5A -> M5B pipeline without changing the weekly kilometre budget or inventing pace, HR, time-to-distance conversions, or extra workouts.

## Hard constraints
1. Inputs must include typed M4B Result, M5B reviewed preview requests, and an explicit per-segment asserted-distance claim for each generated timed preview. The Stage C pipeline MUST recompute M5B from M4B rather than trusting a caller-provided preview.
2. Expand ordered WorkoutStep and IndependentIntervalGroup repetitions; between N WORK repeats there are exactly N-1 RECOVERY segments. Warm-up first; cooldown last. Timed segments have original unit SECONDS throughout.
3. Require exact one-to-one claim coverage for each quality preview, with unique week+slot keys, a nonblank external review reference, and finite positive distances for EVERY expanded segment; no fill-in or distance inferred from duration.
4. Compare sum of asserted segment metres with the original easy slot distance using a strict 0.01 m tolerance; reject mismatches without reducing the session, increasing other runs, or changing the M4B weekly budget.
5. Preserve original M5A baseWeeks and original M5B quality preview objects byte-for-byte/equality unchanged. A matched sum is ARITHMETIC_ONLY; sourceDistancesVerified=false, qualityClassificationVerified=false, scheduled=false, executable=false, authorizedForProduction=false.
6. No Engine1/CoordinatedEngine1/MultiSessionEngine1, no Engine15.generate, no live Android plans, no persistence, no calibration from unverified data and no silent fallback. A review reference is a CALLER ASSERTION, not source authentication.

## Prespecified cases
- THRESHOLD preview, 75 to 80 km, six days: flatten all work/recovery/warm-up/cooldown timed steps in sequence, assert N-1 recoveries, and accept supplied positive segment distances summing exactly to preexisting EASY slot allocated meters.
- TEMPO preview: flat warm-up/tempo/cool-down, segment distances must sum to unchanged slot budget; no pace assignment or duration-derived kilometre conversion.
- Missing claim, duplicated claim, claim not attached to quality preview, blank review reference, incomplete/extra segment count, NaN/zero/negative distance, sum mismatch or overlaid distance: reject explicitly.
- Recovery hold, missing baseline, two-day/returning-runner quality holds and tampered M4B must remain deferred from upstream.
- Multiweek reviewed previews must each have distinct claim matches and preserve exactly the original independent M4B weekly budgets; no production/executable flag.

## Gate C
Run focused tests and M3/M4A/M4B/M5A/M5B/Q2/Q3/R0 regression from the correct Q3 validation checkout. Stage C remains shadow-only even after passing; actual FIT/Garmin source authenticity, segment-intensity classification, scheduling/periodization and delivery require separate gates.
