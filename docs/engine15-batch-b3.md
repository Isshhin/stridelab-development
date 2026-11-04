# Engine 1.5 Batch B3 — deterministic interference-aware scheduling

**Candidate:** `feature/engine15-versioned-foundation-20261103`, draft PR #37. Not merged, not active in Trackside or production.

## Problem and solution

B2 could place 5 runs and 2 calisthenics sessions over 6 available days but it generated strength templates in isolation, losing Engine 1.0's upper/lower alternation. Its only permitted same-day pair was easy running with calisthenics. B3 uses a separate `Engine15InterferenceScheduling` helper to place the existing Engine 1.0 workout templates with explicit conservative interference rules for Engine 1.5 running + 2+ strength requests requiring multi-session days.

A disposable one-run+strength Engine 1.0 draft supplies existing upper/lower exercise prescriptions; only the strength sessions are retained. This does not change the production Engine 1.0 scheduler, prescribe new exercises, or infer readiness.

## Policy and constraints

Immutable saved policy ID: `stridelab.engine15.interference-calendar-20261103.1`, separate from B2 `stridelab.engine15.multi-session-20261103.1`.

Hard: available weekdays, one chosen long-run weekday, selected quality weekdays, weekly counts, maximum two sessions/day, daily time budget, no duplicate IDs, no lower-body work adjacent to high-quality/long runs under the established conflict rule, no same-day long/quality or lower-body combination. Same-day easy running may pair with low-interference calisthenics or upper-body strength only. Adjacent-week conflicts are checked.

Soft: deterministic preference for single sessions and avoiding lower-body training exactly two days from higher-stress running when alternatives exist. No recovery recommendation is inferred from biometric measurements.

A safe solution yields all requested sessions. If none fits the existing conservative rules, `PlanConstraint` is returned with options to change available days, time budget, or counts. No silent redistribution, extra quality work, automatic catch-up or workout deletion.

The original running progression caps, reviewed feedback, persistence format, and Engine 1.0 fallback are unchanged. Policy-aware validators, movement checks and restore checks recognize the new exact policy. Existing B2 saved plans are not silently reinterpreted.

## Gate and boundaries

B3 synthetic tests check 5 runs + 2 strength + 2 calisthenics over six days, 4-week stable output and counts, one upper and one lower strength, three safe double days, availability, long-day, hard conflicts, daily budgets, owner-bound backup/round-trip, infeasible requests, version compatibility and B2 fallback.

No custom session locks, midweek availability changes, 7-running-day support or Trackside UI changes in this batch. Before release, require exact-head offline JVM/protected-file validation, Android Gradle unit suite, Android 35 emulator tests, and hands-on review of generated plans. Synthetic tests establish invariants, not physiological coaching efficacy.
