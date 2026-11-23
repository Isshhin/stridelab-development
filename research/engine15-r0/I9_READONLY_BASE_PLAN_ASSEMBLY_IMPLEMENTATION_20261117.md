# Engine 1.5 I9 — Read-only base running plan assembly

2026-11-17. Branch `feature/engine15-i9-readonly-base-plan-assembly-20261117`, stacked on I8 PR #128. User Q3 validation passed the selected 22-class I8/I1–I7/M3–M6/Q2/Q3/R0 regression in 25 seconds. Preflight frozen in `I9_READONLY_BASE_PLAN_ASSEMBLY_PREFLIGHT_20261117.md`.

## Delivered
- New `Engine15I9ReadOnlyBasePlanAssembly.assemble(M4B.Result, I9 input)` recomputes M6D and, through M6C/M6A/M5A, all original weekly mileage, exposure, calendar, external session and periodization checks.
- Caller must explicitly acknowledge `explicitBaseOnlyReview`; any caller-supplied quality requests cause `UNQUALIFIED_QUALITY_REQUEST_NOT_ADMITTED`. I9 does NOT silently downgrade or replace unqualified quality sessions.
- Returns exact M5A typed distance-only workouts as date-sorted EASY/LONG entries inside typed read-only weeks. Original M6D includes week phase, recovery schedule, future-week reassessment checkpoint, target shortfall and calendar metadata. No time, pace, HR or quality-intensity inference.
- No `PlannedSession` or `PlanSnapshot` created; no live generation, training repository, Android screen, persisted settings or Engine1 fallback changed. No extra running days or silent calendar substitutions.
- All authority indicators remain false: qualityIncluded, externalCalendarAuthenticated, sourceEvidenceVerified, coachingDoseApproved, executable, authorizedForPlanMutation, authorizedForProduction. `calendarArithmeticReconciled=true` is only a read-only structural statement, not athlete safety approval.

## Tests (synthetic, not run)
Nine test methods: eight-week 75→80 km six-day plan, 48 dated runs and M6D expected deload; all typed workout identity and distance precision; base-only acknowledgement and nonempty quality hard deferral; missing external calendar; blocked Sunday LONG/non-Monday start; same-day external workout conflict; upper-body on free Thursday retained only in audit metadata; M4B tampering; deterministic unchanged recovery and checkpoint.

## Validation
I9 source and 9 test methods committed, **not yet locally compiled/run**. Q3 checkout must fetch I9 and run focused I9 with M6D/M6C/M6B/M6A/M5/M4 plus I8–I1 and Q2/Q3/R0 selected regressions. A passing gate permits considering a later read-only UI preview adapter only; it is not authorization to generate workouts or retire Engine 1.0. Real intensity/issuer and full athlete eligibility remain explicitly unresolved.
