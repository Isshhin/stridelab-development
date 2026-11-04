# Engine 1.5 Batch B2 — safe multi-session scheduling

**Branch:** feature/engine15-versioned-foundation-20261103. **Review:** draft PR #37. Not merged, UI-enabled or released.

## Scope

The former Engine 1.5 generator reused the one-session-per-day Engine 1.0 scheduler even when requested counts exceeded available days. Engine 1.5 now selects the existing, separately tested multi-session Engine 1.0 scheduler for that case, keeping the Engine 1.5 conservative mileage ceilings and controlled quality prescriptions.

This path writes a distinct immutable policy ID `stridelab.engine15.multi-session-20261103.1`. The original Engine 1.0 and previous Engine 1.5 policies remain supported and unchanged for existing saved plans. Engine 1.5 requests now persist `dailyMinutes` for offline reload; old snapshots retain the prior 90-minute decoding default.

The verified multi-session constraints are: all selected disciplines and weekly counts are retained; long and quality day preferences apply; only compatible easy running and non-lower-body calisthenics share a day; no more than two sessions are possible on a day; daily time budgets and recovery spacing are checked, including across weeks. Approval, movement, backup and feedback use the correct policy-specific guards. Impossible schedules fail with a readable PlanConstraint instead of dropping sessions.

## Known limitations and gates

A 5-run, 2-strength, 2-calisthenics week across 6 days may still be rejected. The original multi-session policy intentionally disallows same-day strength/run combinations. Seven running days, individual locked workout-day placement, configurable maximum sessions per day and automatic missed-session rescheduling remain future reviewed work. Do not equate this with full scheduling customization.

Do not enable in Trackside, merge PR #37, or describe as release ready before exact-head pure-JVM, Android unit, protected integrity and emulator checks pass. No Engine 2.0 or UI source was changed.
