# M6 Stage B — independent quality-opportunity spacing preflight

Date: 2026-11-17. Frozen before M6B implementation, based on the M6A Q3 validation `BUILD SUCCESSFUL in 29s` for M6A, M5A-C, M4A-B, M3, Q2/Q3 and R0.

## Scope
Build a **shadow-only quality-opportunity calendar review** stacked on M6A PR #117, reusing independently composed M4B -> M5A -> M5B typed results and M6A distance-only dated slots. No Engine1, CoordinatedEngine1, MultiSessionEngine1, Engine15.generate, PlanSnapshot, persistence, or production activation.

## Hard rules
1. Recompute M5B quality preview and M6A calendar from the same typed M4B result. Match the complete M5B base weeks to the M6A dated slots exactly. Never consume the old generator sessions.
2. Exactly one M5B quality preview per build week maximum; each quality date must be the date of the ORIGINAL eligible EASY slot, not a new workout nor an added volume entry. All M4B/M5A weekly budgets remain unchanged.
3. Require minimum 2 calendar days between a quality preview date and every long-run date, **including prior/next week long runs**. Configurable minima limited to 2..3 days. No automatic moving or relaxation. Recovery weeks remain quality-free under M5B.
4. Require minimum 2 calendar days between any quality preview dates, including cross-week (2..3 days configurable), and no duplicate quality date. Overlapping boundaries must be checked by absolute LocalDate differences, not ISO weekday-only arithmetic.
5. Reject explicitly blocked quality dates, optional independently supplied known hard-session dates at configurable 1..3 day separation, and quality proposals that fall on a LONG slot.
6. Unknown external hard-session calendar and unknown pre-horizon exposure remain clearly flagged unresolved; even passing this candidate is not proof of conflict-free training or permission to execute quality. When known hard-session dates are supplied, enforce them; do not treat an absent list as an empty certified schedule.
7. Keep all source weeks, typed workouts and original M5B previews equal and unchanged. Output date-annotated opportunity references only; `sourceEvidenceVerified=false`, `qualityDoseApproved=false`, `distanceReconciled=false`, `fullyCrossDisciplineReviewed=false`, `executable=false`, `authorizedForProduction=false`.

## Prespecified scenarios
- Eight-week 75->80 km six-day calendar, Thursday free, Sunday LONG. Reviewed quality on slot 2 (Tuesday) in weeks 1 and 2 gives distinct quality dates exactly seven days apart, with two days from preceding Sunday's long run in week 2.
- A Saturday slot 5 conflicts with Sunday's LONG (1 day); a Monday quality in week 2 conflicts with previous Sunday's LONG (1 day). Defer; do not move the slot or change its time/distance.
- A quality slot blocked explicitly as quality (while easy running itself remains available) defers. External hard-session date on the quality day or one day prior defers when minimum separation is two days.
- Reject missing M5B reviewed dose, recovery week, LONG slot and returning runners via the upstream stage. Reject tampered M4B and infeasible M6A date constraints.
- Distances and step structures match M5A/M6A exactly; quality timed previews remain optional alternatives, never extra weekly mileage.
- Distinct time, source and authenticity claims stay unverified even if the opportunity passes spacing.

## Gate
Nine or more focused tests plus M3/M4A/M4B/M5A/M5B/M5C/M6A/Q2/Q3/R0 regressions in the correct Q3 validation checkout. Never activate new Engine 1.5 running plan generation or remove Engine 1.0 before later independent acceptance.
