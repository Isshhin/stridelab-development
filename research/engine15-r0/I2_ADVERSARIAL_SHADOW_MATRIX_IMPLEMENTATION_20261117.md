# I2 — Adversarial Shadow Matrix Implementation (2026-11-17)

Branch: `research/engine15-i2-adversarial-shadow-matrix-20261117`, stacked on the I1 draft PR #121 after a test-only nullable report-directory warning fix (`73421afe`). I1 local results supplied by user: `BUILD SUCCESSFUL in 21s`; CSV showed 144 profiles, 127 ACCEPT, 17 DEFER, all 17 `UPSTREAM_CALENDAR_BASE_TWO_DAY_EXPOSURE_CONFLICT`. Thus I1's main 144-profile grid did not quantify diverse failure outcomes, although its separate tests did exercise several safeguards.

## Delivered
- Test-only `Engine15I2AdversarialShadowMatrixTest.kt`, no additions to `src/main/`, no Android/live engine mutation.
- 39 prespecified cross-stage fixtures: 3 accepted positive controls and 36 adverse scenarios. Failure families BASELINE, WORKLOAD, CALENDAR, EXTERNAL and QUALITY. Distinct expected reason codes are fixed in source; a generic rejection for the wrong reason fails.
- Positive controls: six-day Tue quality + Thu upper strength, two-day 20→25 EASY foundation, and returning four-day without quality. Original typed M4B weeks remain unchanged; all production/executable/verification flags false.
- Invalid profiles: unknown/peak-only/legacy/zero/contradictory baselines, recovery hold, missing longest; infeasible two-day allocation, tampered M4B; blocked/unavailable/insufficient calendar; missing/duplicate/invalid/out-of-range external events, coincident easy/upper events without opt-in, hard before long and adjacent external hard; unreviewed/unknown-dose/duplicate/recovery/long/blocked/near-long/near-external quality, returning/two-day quality and time conflict.
- Every case re-enters the full independent M6D review, which recomputes M6C, M6A and optional M5B/M6B. This is *integration diagnostic validation*, not an outcome trial.
- A second test checks the three positive cases are deterministic and keep fitness, intensity, actual distance, time-budget, production and execution flags false.

## Evidence
After the local Gradle test passes, synthetic CSV should appear at:

`native-android/app/build/reports/engine15-i2/adversarial-synthetic-matrix.csv`

Expected 39 rows plus header, with `case_id,family,expected_decision,actual_decision,expected_reason,actual_reason`; 3 accepted, 36 deferred, at least 19 distinct reason codes. These are test definitions, not observed test results until run.

## Validation / release guard
**I2 has not been executed locally**. Run I2 + I1 + M3/M4A/B/M5A/B/C/M6A/B/C/D/Q2/Q3/R0 selected regressions in Q3. Review compile warnings, CSV group counts and any mismatch. A passing I2 remains shadow-only and cannot enable Engine1.5 quality prescription, replace Engine1, or certify athlete safety. Next priorities are source/dose authority, realistic session-duration feasibility, broader admissible plan generation and Android acceptance.
