# M6 Stage A — Independent distance-only calendar assignment preflight

Frozen 2026-11-17 BEFORE implementation and execution. Shadow-only; not production authorization. Branch `feature/engine15-m6a-independent-calendar-20261117` stacked on M5C PR #116.

## Intent
Attach actual LocalDate values to preexisting M4B -> M5A distance-only EASY/LONG candidate slots. The app's current Engine15 -> Engine1 production generator stays untouched. M5B time-based quality previews, M5C caller-asserted kilometre arithmetic and all quality coaching authority remain outside this stage. The existing earlier IndependentConstrainedMultiweekPlanner uses a different M4 research contract and must not be silently substituted as the new engine.

## Hard constraints
1. Recompute and validate M5A using original typed M4B.Result; never trust or mutate old sessions. M3/M4B numeric eligibility, return/recovery and exposure caps remain unchanged.
2. Input includes Monday start date, weekly available ISO weekdays, preferred long-run ISO weekday, optional day-specific blocked dates and preferred easy weekdays. ISO Monday=1 through Sunday=7. No implicit calendar-day changes or workout additions.
3. Each week must assign exactly runningDays distinct permitted dates, no blocked dates or unavailable weekdays. For >=3 run days exactly one LONG on the explicitly requested weekday, else explicit deferral. For 2-day weeks both sessions EASY; no fabricated long run.
4. Each placed slot must retain the original M5A typed workout and its exact 100m-aligned distance. Every week sums exactly to its M4B budget, including every fourth recovery week. No automatic shift to a different mileage amount, missed-work catch-up or duration estimate.
5. Weekday selection must be deterministic, with basic distribution over the week. It is a placement heuristic, not a statement that the runner can tolerate an adjacent long and easy run. Explicitly label calendar review still required.
6. No quality threshold/tempo preview is promoted into a scheduled prescription by attaching calendar dates to an EASY slot. Quality-spacing, cross-week interference, strength days, injury response and approved-plan conflict checks remain future gates.
7. The result must remain shadow-only: calendarAssigned=true for the candidate, executable=false, qualityScheduled=false, authorizedForProduction=false, sourceEvidenceVerified=false. No PlanSnapshot, persistence, approval or runtime Engine1 fallback edits.

## Prespecified tests
- Six-day 75 -> 80 km, eight-week result, Monday Nov 16 2026, allowed Mon/Tue/Wed/Fri/Sat/Sun, preferred long Sun: six unique dated sessions/week, Thursday always free, Sunday LONG, unchanged budgets 67.5/71.2/75.0/60.0/71.2/75.0/78.7/62.9 and all typed steps intact.
- Three-day 75 -> 80 km with longest 20: 54.6 km week remains 16.8 + 16.8 + 21.0, with exactly one dated long Sunday and nonconsecutive/evenly distributed weekdays when possible.
- Two-day feasible 20 -> 25 km example assigns two EASY dates and no long; infeasible 75 -> 80 km two-day upper-bound remains a deferral.
- An explicitly blocked easy day is not scheduled if alternatives exist. A blocked or unavailable preferred long day defers instead of moving the long run silently.
- Insufficient available run days, invalid Monday start, tampered M4B, baseline missing, hold and unknown session exposure all defer.
- Deterministic repeated inputs return identical dated candidates. All accepted results have production/executable/quality-authorization flags false.

## Gate A
Run M6A focused tests in Q3 validation worktree, then regression for M3/M4A/M4B/M5A/B/C/Q2/Q3/R0. No Android/emulator/prod activation at this stage.
