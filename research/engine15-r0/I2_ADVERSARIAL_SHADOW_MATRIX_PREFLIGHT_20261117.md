# Engine 1.5 — I2 Adversarial Shadow Matrix Preflight

Frozen 2026-11-17 before implementing I2 tests. Stacked on I1 draft PR #121 at commit `73421afe`. The user reports I1 `BUILD SUCCESSFUL in 21s`, 144 scenarios: 127 ACCEPT, 17 DEFER. All 17 I1 sweep deferrals were `UPSTREAM_CALENDAR_BASE_TWO_DAY_EXPOSURE_CONFLICT`. This is a useful feasibility outcome but leaves the *sweep's* rejection variety narrow. I1's separate focused tests covered more cases; I2 must additionally make those failures countable in an explicit ledger.

## Intent and scope
Add TEST-ONLY deterministic adversarial integration cases for the existing M3/M4B/M5A/B/C/M6A/B/C/D pipeline. No new training formula, no production Kotlin source, no UI, no Engine1 fallback, no saved-plan schema, no internet or athlete activity data.

## Prespecified failure families
1. BASELINE: missing average, peak-only, legacy-unconfirmed, confirmed-zero, contradictory reported peak, explicit recovery hold, missing longest-session exposure.
2. WORKLOAD/EXPOSURE: infeasible high-volume two-day schedule, tampered M4B week.
3. CALENDAR: unavailable preferred long day, blocked preferred long day, too few available weekdays, unavailable week after blocked easy day, invalid non-Monday start.
4. EXTERNAL: missing external source, unasserted coverage, duplicate/blank IDs, out-of-horizon date, same-date external duplicates, same-day run+upper-body without opt-in, Saturday hard session before Sunday long, adjacent outside hard sessions.
5. QUALITY: no dose review, missing dose exposure, duplicate same-week quality, quality on long day, recovery-week quality, explicitly blocked quality day, Saturday quality before Sunday long, Monday quality after prior Sunday long, external hard before Tuesday quality, incompatible same-day upper-body+quality despite easy-upper opt-in, returning runner quality hold, two-day quality hold, inadequate time budget.

## Acceptance
- Each adverse fixture has a predetermined diagnostic code; a generic rejection alone is NOT sufficient. The complete independent M6D audit must defer with that exact code, with a nonblank explanation. Unexpected candidate = failure.
- Positive reference fixtures must remain accepted while retaining exact M4B week budgets, no executable or production authorization, and identical independent M6C original calendar. Include six-day reviewed quality + Thu strength, feasible two-day 20-to-25 EASY-only and returning four-day without quality.
- Write synthetic-only CSV with `case_id,family,expected_decision,actual_decision,expected_reason,actual_reason` and one row per declared fixture. Assert row count and multiple distinct reason codes. No PII, no real activity or location.
- No category or rule can be marked successful until its focused acceptance test passes in Q3 validation. I2 is a shadow technical counterexample gate, not source verification or athlete-safety evidence.

## Local tests
Run the I2 test class plus I1 and M3/M4A/M4B/M5A/B/C/M6A/B/C/D/Q2/Q3/R0 regressions. After Gradle success, inspect grouped I2 CSV reason distribution and report both gate pass/fail and total distinct failure codes. I2 remains draft and does not authorize production.
