# M5 Stage A — Gate A preflight: M4B-to-workout composition

**Frozen before M5A implementation, 2026-11-17.** Status: proposed shadow experiment, NOT approved for production. Stack on M4 Stage B PR #113 (commit 2d7141eb).

## Scope

Build a separate Engine 1.5 workout-composition candidate that consumes **only the independently computed M4B Result**. It must NOT call Engine1/CoordinatedEngine1/MultiSessionEngine1, Engine15.generate, legacy allocated runs, current calendar generation, or production plan persistence. M3 and M4B are prerequisites. Live generation remains unchanged.

Stage A composes **distance-only EASY and LONG slots**, not dated sessions. M6 owns weekdays and spacing. No quality intensity, threshold pace, session duration, total elapsed time, recovery readiness, target-achievement certification, or training adaptation may be inferred from mileage alone. Existing IndependentWorkoutComposition/WorkoutStep is the typed research contract; do not generate PlannedSession or mutate saved plans.

Reject, rather than invent or downscale, if a week's M4B budget cannot be reconciled to exactly N nonzero 100 m slots subject to all the following:
- For >=3 run days, exactly one LONG slot, always EASY intensity; maximum LONG distance is min(reported longest run +1 km, floor(40% of weekly budget to 100 m)). The other slots are EASY and each <= floor(80% of (reported longest +1 km) to 100 m).
- For exactly two run days, two EASY slots with no distinguished LONG; each must respect the same non-long bound. One running day is out of Stage A scope.
- Every proposed distance is represented as a single METERS, WORK, EASY, 1-repetition typed workout step. All distances in 100 m increments; totals EXACTLY equal the M4B weekly budget with no hidden catch-up or unallocated mileage.
- If M3's longest-run exposure is missing, no numeric workout proposal. Caller-supplied M4B Result must be recomputed and match current M4B policy (tampering or stale calculations defer).
- Recovery weeks remain within M4B's single previous-budget cap. Stage A may not lift a weekly, return, recovery, or session-exposure bound. For a recovery week, also do not increase the designated long-run candidate above the previous week.
- No quality sessions yet. M5 Stage B will need verified recent-quality history and separate dose/spacing evidence; time-to-distance reconciliation is likewise a later validation gate.

## Prespecified acceptance scenarios

1. Experienced reported 75 -> target 80 km, six running days, longest 20 km, eight weeks. Exact M4B weekly budgets 67.5, 71.2, 75.0, 60.0, 71.2, 75.0, 78.7, 62.9 km; six slots per week. No long slot >21.0 km; no easy slot >16.8 km; recovery week remains 60.0 and 62.9 km. Target NOT certified reached (peak budget 78.7 before M5 quality).
2. Three-day reported 75 -> target 80 km, longest 20 km. Session-exposure-limited week of 54.6 km is feasible only as 21.0 LONG + 16.8 EASY +16.8 EASY (or a provably equivalent arrangement); exact reconstruction.
3. Two-day 75 -> 80 km, longest 20 km. M4B's 37.8 km upper bound is **not** automatically schedulable as two easy runs (16.8+16.8 <37.8). Return a deterministic deferred decision; do not violate per-session exposure or create a long day by assumption.
4. Two-day 20 km baseline, longest 20 km, target 25 km: W1 18.0 km can be represented as two 9 km EASY slots; no long run fabricated.
5. Unknown baseline, confirmed zero, legacy unconfirmed and recovery hold: no numeric workout sessions.
6. Missing longest exposure on an otherwise numeric M4B profile: defer with explicit reason, never silently take an arbitrary session length.
7. Tampered/stale M4B Result: defer. No bypass by modifying proposed weekly kilometers or constraints.
8. Sweep multiple mileage, frequency and return profiles: every emitted candidate has exactly one typed distance step per slot, integer 100 m units, bound-preserving week totals, no unauthorized production/schedule flag.

## Gate A decision

Only focused M5A tests, prior M3/M4A/M4B/R0/Q2/Q3 regression, and inspection of counterexamples can close this **shadow** gate. M5A cannot authorize workout delivery. M5 Stage B quality composition and M6 independent scheduling remain separate milestones.