# Engine 1.5 I9 — Read-only base running plan assembly preflight (2026-11-17)

Stacked on I8 draft PR #128. The user's selected I8 + I1–I7/M3–M6/Q2/Q3/R0 JVM regression passed (BUILD SUCCESSFUL, 25s). I9 is a pure Kotlin, **shadow-only base-running preview** and does not authorize workouts.

## Problem
Independent M4B/M5A/M6A/M6D already emit typed mileage budgets, EASY/LONG slots, dated placements and periodic recovery audits. Those results are not yet assembled into one stable, explicitly read-only, week/day model for later controlled UI integration. The I3–I8 chain does NOT independently authorize quality-session intensity, so this stage must not covertly include threshold/tempo sessions.

## Frozen contract
1. Caller must acknowledge `explicitBaseOnlyReview=true`; otherwise defer. The original M6C review input must contain **zero** quality requests. Nonempty quality request is a hard deferral, not silently converted to EASY nor rendered as an approved workout. No claim that we have inspected original user intent outside M4B.
2. Recompute M6D from the original typed M4B Result and M6C caller-supplied external calendar, which itself recomputes M6C, M6A and M5A and checks all existing exposure, conflict and periodization boundaries. Propagate any M6D deferral.
3. Render every original M6A dated EASY/LONG slot as typed read-only day entry, preserve its original M5A distance-only IndependentWorkoutComposition exactly; no minutes, pace or HR targets inferred. Sort by week/date; maintain unique dates/slots, exact positive 100m precision and exact original week totals and run counts.
4. Retain the original M6D phase, recovery-week treatment, target-mileage shortfall, review-after-completed-week checkpoint and externally asserted sessions counts. No new policies, automatic extension or quality downgrades.
5. Output `ReadOnlyBasePreview` with `calendarArithmeticReconciled=true`, `explicitBaseOnly=true`, `qualityIncluded=false`, and false for `externalCalendarAuthenticated`, `sourceEvidenceVerified`, `coachingDoseApproved`, `executable`, `authorizedForPlanMutation`, `authorizedForProduction`. Do not expose actual PlannedSession, PlanSnapshot or persisted plan IDs.
6. Engine 1.0 and live Engine 1.5, Android screens, repositories, device-test routing and all training data stay unchanged. Future I10 may adapt this typed read-only shape to a UI preview after further validation, not activate automatic plan generation.

## Synthetic acceptance
- A 75→80 km, 6-day, 8-week plan has 48 placed run previews; expected mileage series 67.5, 71.2, 75.0, 60.0, 71.2, 75.0, 78.7, 62.9 km; no Thursday runs when availability excludes Thursday; Sunday LONG; recovery weeks 4 and 8; no quality and all authority flags false.
- Unacknowledged base-only scope, attempted quality request, external calendar missing or unasserted, blocked long day, unrelated same-day external-workout interference, non-Monday start and corrupted M4B typed source must defer.
- Explicit isolated upper-body workout on a non-running day may remain in M6D audit counts but cannot be written to the run preview as another day entry.
- Deterministic repeated inputs preserve the original M6D candidate exactly.

## Validation required
New I9 test class and selected I8/I7/I6/I5/I4/I3/source binding/I2/I1/M3–M6/Q2/Q3/R0 suites on the user's Q3 validation checkout. I9 passing does not mean actual plan-generation readiness or any independent authorization of intensity.
