# M6 Stage C — independent cross-discipline conflict review (preflight)

Frozen 2026-11-17 before implementing the stage. Stacked on M6B draft PR #118. M6B was reported successful in the Q3 validation checkout (26s) with 11 selected test suites. **This is SHADOW-ONLY**, not production admission.

## Scope
Inspect independent M4B→M5A→M6A dated EASY/LONG sessions, plus optional independently recomputed M5B→M6B quality opportunities, against explicitly supplied nonrunning sessions. This reviewer does not generate strength, calisthenics or conditioning workouts and cannot infer missing schedules or physiological workload.

## Admission boundaries
1. Recompute M6A from original typed M4B and calendar data; if reviewed quality requests are supplied recompute M6B from those same inputs, feeding asserted external lower-body/hard dates into M6B's spacing checks. Never accept caller-supplied M6A/M6B approved objects and never invoke Engine1, Engine15.generate or legacy PlanSnapshot.
2. Every supplied external item must have unique nonblank identifier, exact LocalDate, category STRENGTH/CALISTHENICS/CONDITIONING, and load label UPPER_BODY/LOWER_BODY/FULL_BODY_HARD/EASY_GENERAL. These are asserted caller labels, NOT authenticated sessions or estimates of fatigue.
3. External calendar coverage must be explicitly asserted by the caller; missing evidence fails closed. Even an asserted complete list is NOT verified. Reject entries outside the inspected horizon and one immediately adjacent day on either side; no silent filtering of invalid or out-of-range entries.
4. On the same calendar day, reject all run/external pairings by default. Only if explicitly enabled, allow exactly one upper-body STRENGTH or CALISTHENICS session alongside an EASY run *without* a quality opportunity. Never allow pairing with LONG, quality, LOWER_BODY, FULL_BODY_HARD or CONDITIONING.
5. Lower-body and full-body hard external sessions must be at least two calendar days from all LONG runs and all proposed quality opportunities, including week transitions. No surprise shifts, dropped run days or new distance. Reject multiple external sessions on one day; reject adjacent (one-day gap) external hard sessions.
6. No elapsed-time budget is inferred or enforced; even a permitted easy+upper pairing is unverified for actual duration, recovery or safety. Other external workouts are not synthesized; missing external details remain reported as source-unverified.
7. Every accepted result holds original M6A calendar, optional M6B quality opportunities, unchanged M4B weekly distance, and original external session list. `sourceEvidenceVerified=false`, `externalCalendarVerified=false`, `timeBudgetValidated=false`, `qualityScheduled=false`, `executable=false`, `authorizedForProduction=false`. This is a conflict REVIEW candidate, not completed multidisciplinary planning.

## Prespecified cases
- Eight-week six-day running, Thursday off, Sunday LONG; independently claimed easy strength Thursday and upper strength Tuesday. No quality: Thursday external session can be accepted, but same-day upper strength + EASY requires explicit opt-in.
- With reviewed Tuesday quality, same-day upper strength must be rejected even with opt-in; Monday lower-body before Tuesday quality is rejected, and Monday lower after previous Sunday's long is rejected.
- Saturday lower-body before Sunday LONG is rejected. Nonadjacent upper-body strength on Thursday may coexist with Tuesday quality without influencing mileage.
- Two external hard sessions on adjacent days or two external sessions on one date are rejected.
- Missing external coverage claim, duplicate identifier, invalid horizon dates, blocked M6A long day, tampered M4B budgets or unreviewed M5B quality are deferred.
- With no quality requests, M6A-only path works and never invents quality. All results remain non-executable, with unchanged original typed workout steps.

## Gate C
Focused tests plus M3/M4A/M4B/M5A/M5B/M5C/M6A/M6B/Q2/Q3/R0 regression in Q3 checkout. Do not merge into production or retire Engine 1.0; authentic external scheduling sources, cross-discipline workout generation and full time/approval validation remain future gates.
