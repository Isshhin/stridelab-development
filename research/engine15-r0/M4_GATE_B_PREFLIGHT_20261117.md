# Gate B / M4 — Predeclared weekly-volume migration checks

Date: 2026-11-17. **Written before M4 executable implementation or local test output.**

## Authorization boundary

R0 found measured weekly-ceiling binding and a Q2 time-to-distance estimate shortfall. Gate B authorized M3 metadata, not a change to progression rates, quality-work share, long-run share, recovery holds, or catch-up mileage. M4 Stage A is authorized **only as a behavior-preserving ownership migration**, not a new coaching progression policy.

The new module must become the **single source** of the pre-existing Engine 1.5 running ceiling formula and week-budget/recovery intersection. The Engine 1.0 baseline still supplies scheduled sessions and may remain a separate upstream volume ceiling until a further reviewed M4 Stage B change. Do **not** describe Stage A as fully eliminating competing workload planners.

## Numeric precommitment (current policy; NOT new clinical recommendations)

For reported 75 km/week, target 80 km/week, current-policy weekly ceiling (before Engine 1.0 and recovery intersections) over eight weeks, expected in km:
- W1 67.5, W2 71.25, W3 75.0, W4 60.0,
- W5 71.25, W6 75.0, W7 78.75, W8 63.0.

With the existing 100 m downward rounding: 67.5, 71.2, 75.0, 60.0, 71.2, 75.0, 78.7, 63.0 before previous-week recovery reduction. Those are **ceiling values, not finished weekly plan mileage**.

For 50 km/week reported, target 80 over four weeks: existing ceiling 45.0, 47.5, 50.0, 40.0 km. No promise of 80 km attainment.

For the observed 75→80 R0 synthetic fixture, existing total weekly allocations before Q2 are 67.5, 71.2, 75.0, 54.1, 71.2, 75.0, 78.7, 56.4 km. Stage A should preserve these and complete Q2/Q3 session prescriptions exactly. The existing Q2 estimated peak 70.533 km remains unchanged.

## Acceptance (fail closed)

1. **Exact policy parity**: all tested legacy target and untargeted ceilings for zero, intermediate, returning and high mileage, including week 3/4 and week 7/8 boundaries, are equal to the old Engine 1.5 function (within double precision tolerance 1e-9).
2. **Existing budget intersection**: the new weekly decision applies baseline output, weekly ceiling, the optional previous-week 0.8 recovery limit, and 100 m flooring in the **same order** as current code. Never add a new safety exception, force catch-up or exceed the upstream baseline.
3. **Profile status**: typed M3 known self-reported average, recorded-partial, unknown, explicit zero and peak-only statuses are distinguishable. Unknown/peak-only without a confirmed typical average yields no numeric volume skeleton. An explicit zero receives time-only prescription status, not a zero-kilometre workout guarantee.
4. **No M3 confidence override**: a higher confidence label cannot increase the numerical ceiling. M4 Stage B may propose independent confidence-aware envelopes only after separate pre-registration and Gate B review.
5. **Production parity**: unchanged request/owner/ID/timestamp must yield identical PlanSnapshot values before/after Stage A. Existing focused Q2 and Q3 tests must not regress.
6. **No historical rewriting**: saved plans, completed workouts, backup codec and approvals remain unchanged; no schema bump, telemetry, analytics or automatic upload.

**Outcome status:** PENDING focused local test execution. An M4 implementation passing source review alone is not an executed acceptance result.

## Stage B is separately gated

After Stage A proves parity, Gate B must review a candidate that removes Engine 1.0's mileage cap from the volume skeleton, specifies a quantified confidence-dependent progression envelope, and compares 75→80, 50→80, 30→35, 0→20, returning, fatigued and three-day profiles against the frozen R0 reports. No numeric policy changes in Stage A.
