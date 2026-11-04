# Engine 1.5 Batch B4 — durable explicit session locks (candidate)

Branch: `feature/engine15-versioned-foundation-20261103`, draft PR #37. Never merge/enable without complete checks.

New optional `PlanRequest.lockedSessionDays` maps stable workout IDs to exact ISO dates. It is serialized only for Engine 1.5; older saved plans decode the absent field to an empty map. The PlanRepository accepts explicit lock/unlock actions through its existing optimistic revision machinery, preserving account scoping and offline snapshots. Locking does not mutate a workout, and moving a locked workout to another date fails until the user unlocks it.

The B3 interference-aware solver restricts candidate days for locked workouts. Invalid dates, unavailable dates, missing session IDs, incompatible recovery/time budgets and regeneration that cannot retain a lock all fail with `PlanConstraint`, without silently moving the locked session. The B2/single-session paths must already generate the saved locked date or fail; they do not fabricate a different placement. Backup validation rejects inconsistent approved snapshots.

This does not implement a new visual flow or automatic availability-change rescheduling. Locking is a domain API for future Trackside handoff. No Engine 1.0 behavior, existing workouts or Engine 2.0 research is modified.

Acceptance: exact-commit pure JVM, Gradle unit, Android emulator/repository persistence and non-destructive restore; manual review of locked B3 week and conflict messages. Test fixtures are synthetic correctness checks only.
