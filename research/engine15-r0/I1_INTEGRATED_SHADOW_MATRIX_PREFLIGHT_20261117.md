# Engine 1.5 — I1 Integrated Synthetic Shadow Acceptance Matrix

Frozen 2026-11-17 before implementing the I1 matrix. This is **I1**, not 'M7': the repository already contains separate prior M7/M7B research on completion attribution and evidence ledgers. I1 is a test-only gate stacked on M6D PR #120. All M3→M6D focused unit suites were reported successful in Q3 validation (M6D: BUILD SUCCESSFUL in 24s), but no integrated end-to-end acceptance has yet been established.

## Objective
Replay complete independent M3→M4B→M5A/B/C→M6A/B/C/D decision paths using only synthetic athlete profiles and asserted external sessions; prove selected internal invariants and fail-closed outcomes before source/dose authority or Android work. Do not activate production or compare shadow previews with executed coaching outcomes.

## Frozen acceptance behavior
1. Every accepted M6D report must retain the original M4B week count/index, exact 100m weekly budgets, M5A typed EASY/LONG steps, M6A dates, M6B preview-only quality opportunities, and M6C original external sessions. No extra mileage, fabricated pace, or silent drop of a run day.
2. Every fourth week is RECOVERY and <=80% of preceding planned week, no quality opportunities there. All original M4B existing-policy, session, returner, and recovery ceilings remain satisfied, not re-estimated.
3. The independent plan cannot claim a target was reached when its **proposed** peak is below target. No test treats target budget attainment as delivered mileage or athletic outcome.
4. Any M5C tested timed-quality distance arithmetic uses caller-asserted, explicitly synthetic segment distances and cannot authenticate GPS, intensity, source identity or executable workouts.
5. Seeded deterministic profile grid spans typical weekly volume, running-day frequencies, session exposure and returning status. Every outcome must either preserve the full contract or defer with explicit diagnostic code. The matrix must have both accepted and deferred outcomes; no forced success by relaxing ceilings.
6. Include predeclared accepted cases: experienced 75→80 km six days, Thursday free, Sunday long; 50→80 km large jump without invented target attainment; 30→35 km developing runner; 40→50 km returning runner under review; three-day exposure cap 54.6 km; and 20→25 km two-day EASY foundation.
7. Include predeclared deferred cases: 75→80 two-day exposure conflict; missing longest distance; unknown/peak-only/legacy/confirmed zero baselines; recovery hold; unavailable/blocked long day; asserted lower-body strength adjacent to Sunday long run; missing external calendar; unreviewed or recovery-week quality; tampered M4B budget. No automatic fallback to Engine1 or live Engine15.
8. When `externalCalendarCoverageAsserted=true`, it remains just a caller assertion, never authenticated source coverage or recovery tolerance; quality evidence review/reference is also not an executable quality-workout authorization.
9. A passing I1 test is a **synthetic technical shadow gate**, not physiological safety validation. Must not change any main-source Kotlin, live generator, engine version flags, PlanSnapshot persistence or user-visible planning.

## Evidence and reproducibility
Record deterministic seed and prespecified case ids; emit a CSV under the Gradle app test working directory `build/reports/engine15-i1/`. Report case id, accept/defer, diagnostic code, peak proposed km, target budget gap, and week count. Never record user-specific activities, GPS, personal data or estimated executed distances. Tests must assert the report exists and has the expected number of synthetic rows.

## Exit criterion
Run I1 test plus M3/M4A/M4B/M5A/B/C/M6A/B/C/D/Q2/Q3/R0 selected regressions on the Q3 validation checkout; inspect CSV and any failures. I1 remains a draft research PR and cannot authorize production. Subsequent gates must cover independent quality evidence/eligibility, genuine time-to-distance estimation (or explicitly unschedulable fallback), calendar variability, real-device and full Android integration acceptance.
