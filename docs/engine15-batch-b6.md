# Engine 1.5 Batch B6 — Edited-Plan Integrity

Status: candidate, draft PR #37. Engine 1.0 and Trackside are unchanged.

## Root cause

The earlier `PlanOperations.approve` validated edits against other approved plans but did not recheck conflicts **within** the Engine 1.5 candidate after nonstandard edits. A new test intentionally put two easy running sessions on the same date and demonstrated that `PlanConstraint` was not raised (213/214 JVM tests passed; one targeted failure).

## Narrow production correction

Before returning an Engine 1.5 approved snapshot, reuse `PlanScheduleValidation.validatePlan(candidate)`, which already applies B3 interference, daily budget, manual lock and availability checks. No new coaching rules, session rewriting or Engine 1.0 behavior changes. The owner-scoped repository still rejects stale revisions and leaves a conflicting draft unapproved.

B6 regression checks combine session locks, actual completion logs, B5 availability reconciliation, stable IDs, preserved distances, immutable history, JSON roundtrip, owner-scoped backup and duplicate-safe restore. An additional emulator test proves unsafe draft approval fails without changing offline SQLite records.

## Release gates

Do not claim B6 complete until exact-head offline JVM/protected hashes, Android unit, Android 35 emulator/offline persistence and code review results are inspected. Draft PR #37 remains unmerged and Engine 1.5 is not production-default.
