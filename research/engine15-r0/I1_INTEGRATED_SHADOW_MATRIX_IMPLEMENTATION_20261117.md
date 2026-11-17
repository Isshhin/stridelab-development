# Engine 1.5 — I1 Integrated Synthetic Acceptance Matrix Implementation

Date: 2026-11-17. Research-only branch `research/engine15-i1-integrated-shadow-matrix-20261117`, stacked on M6D PR #120. Preflight: `I1_INTEGRATED_SHADOW_MATRIX_PREFLIGHT_20261117.md`.

## Scope completed
- New **test-only** `Engine15I1IntegratedShadowMatrixTest.kt`; no production or main-source Kotlin edited. It constructs original PlanRequest→M3 athlete baseline→M4B independent workload and reviews complete M6D results, which recompute M6C, M6A and optional M5B/M6B.
- Known accepted profiles: six-day 75→80 eight-week plan with Thursday free/Sunday LONG and reviewed Tue threshold/tempo previews plus external Thursday upper strength; 50→80 rapid target gap; developing 30→35; three-day 75→80 exposure-limited case; returning 40→50; and two-day EASY-only 20→25.
- Explicit rejections: 75→80 two-day exposure conflict, missing longest, unknown/peak-only/legacy/confirmed-zero/contradictory baselines, recovery hold, blocked Sunday long, missing external calendar assertion, unreviewed/recovery quality, external lower-body hard directly after Sunday LONG, and tampered M4B weekly budget.
- Standalone M5C arithmetic sidecar test uses invented, clearly marked synthetic per-segment metres for timed threshold and tempo; it asserts that matching arithmetic never grants quality source verification, real distance evidence or executable approval.
- Deterministic 144-scenario grid: six weekly-volume baselines × four running frequencies × three longest-run exposure ratios × two returning statuses, with 4/8/12 week horizons. Every scenario must yield a fully invariant-preserving M6D Candidate or an explicit nonblank Deferred code. Both outcomes are required. No automatic fallback or mileage inflation.
- Preserved invariants: exact 100 m weekly budget and workout step distances, one run per date, LONG roles, specified running availability, 4th-week ≤80% recovery cap, source M4B return/exposure ceilings, target budget shortfall, original external sessions, and all production/execution/source verification flags FALSE.

## Evidence file
The matrix test writes synthetic-only CSV to the app Gradle working directory:

`native-android/app/build/reports/engine15-i1/integrated-synthetic-matrix.csv`

Expected 145 lines: one header and 144 scenario records, with `synthetic_case,decision,reason,proposed_peak_km,target_budget_gap_km,week_count`. The per-case ids contain no PII. No activity records or real user locations are loaded.

## Pending gate
**The new I1 tests have NOT been executed.** Run the I1 matrix and prior M3/M4A/M4B/M5A/B/C/M6A/B/C/D/Q2/Q3/R0 regressions from Q3 validation. Review CSV accepted/deferred counts and failures; do not claim a coaching or clinical success from passing synthetic matrix tests.

I1 is not the existing separate M7/M7B completion-attribution research. It is a provisional integrated shadow-validation checkpoint and cannot retire Engine 1.0, generate executable quality sessions or authorize Android production.
