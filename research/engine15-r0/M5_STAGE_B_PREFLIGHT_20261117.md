# Engine 1.5 M5 Stage B — frozen preflight (2026-11-17)

**Status: proposed shadow experiment, NOT approved for executable or production training.** Stacked on M5 Stage A PR #114 (which was reported passing in the independent Q3 validation checkout). M5 Stage A retains its unscheduled EASY/LONG distance slots; no modifications to production or Engine 1.0 routing.

## Scope and boundaries
- Consume a typed M4B independent planner Result and independently recompute the M5A base slots; NEVER consume Engine 1.0 sessions or Engine15.generate output.
- Provide **one optional explicitly reviewed quality preview per BUILD week** on an existing EASY slot, not a new workout added to the mileage plan.
- First preview library: THRESHOLD intervals and continuous TEMPO only, using the preexisting IndependentQualityFitnessAdapter and IndependentWorkoutComposition types. No arbitrary intensity pace, zone, physiological readiness or new dosing algorithm.
- The request must carry an explicit reviewed work-dose reference, confirmed recent work seconds, an independently supplied positive work ceiling, bounded permitted increase and available session time. These are **caller assertions**, not proof from the M3 profile. A successful candidate MUST report sourceEvidenceVerified=false, distanceReconciled=false, scheduled=false, executable=false, authorizedForProduction=false.
- No preview if request contradicts recovery hold, returning-runner flag, 2-day week, M5A base infeasibility, recovery week, LONG slot, missing review evidence, duplicate quality week, unsupported dosing or time budget.
- A timed threshold/tempo preview does NOT automatically replace a kilometre-prescribed easy workout; it is an alternative representation tied to the original slot, with distance unknown. Original M4B/M5A week totals remain unchanged. No extra weekly distance is counted.
- M6 owns actual day-of-week choice, cross-week spacing and calendar interference; Q3 provenance and a later distance-reconciliation gate remain independent. No silent Engine 1.0 fallback or production plan mutations.

## Prespecified tests
1. 75->80 km, 6 days, first BUILD week, reviewed threshold preview on EASY slot: uses bounded typed time steps; work dose never exceeds exposure + allowance, ceiling or available seconds; all M5A km remain unchanged.
2. Reviewed TEMPO preview on eligible EASY slot: work seconds and total seconds reconcile to typed steps, but seconds do NOT establish kilometres.
3. Unreviewed/missing reference, unauthorized dose, missing recent quality work, or no ceiling: defer with stable reason.
4. Recovery-week, LONG-slot, returning-runner and 2-day constraints: defer.
5. More than one quality request in a week, duplicate slot, invalid week or slot: defer before emitting candidates.
6. If M4B inputs were mutated, recomputation of base M5A prevents generating quality previews.
7. Assert all result production/executable and verification flags remain false; no Engine1, PlanSnapshot, saved-plan, calendar or Android mutation.

## Gate
Source+tests must compile and pass locally on the Q3 validation checkout. Re-run previous M3/M4A/M4B/M5A/Q2/Q3/R0 regression suites. Until then Stage B status is implemented/unvalidated, NOT complete. Even a passing shadow gate does not mean quality workouts can be delivered, since kilometres, historical dose evidence and scheduling remain unresolved.
