# M4 Stage A — Extract the weekly budget without changing coaching policy

Date: 2026-11-17. Candidate branch: feature/engine15-m4-weekly-volume-skeleton-20261117. Stacked on M3 PR #111.

## Scope completed in source

- Added **Engine15WeeklyVolumeSkeleton** with a clean `outline(Engine15AthleteCapacity, targetKm, weekCount)` interface independent of the old Engine 1.0 calendar. This provides week roles and ceilings, not schedules, workouts, fitness verification or target guarantees.
- Moved the **exact legacy** `runningCeiling` formula to that module; `Engine15.runningCeiling` remains a delegating compatibility entry point.
- Consolidated the existing per-week intersection (Engine 1.0 scheduled baseline, Engine 1.5 ceiling, 100 m flooring, and previous-delivered-week 80% recovery cap) in `existingWeekBudget`. Engine15RunningProgram delegates to it without changing dose, spacing, hard work or time-based support steps.
- Existing R0 workload-ledger keys/stages remain so after-change diagnostics can be compared directly to user-executed R0.
- Added **Engine15M4WeeklyVolumeSkeletonTest**: numerical current-equation matrix, predeclared 75→80 and 50→80 values, current recovery intersection, separate profile-availability states, confidence-not-a-multiplier, and frozen executed R0 plan/trace regression.

## Explicit limitations

**This is not a complete independent weekly planner and does not repair the 70.533 km peak.** Engine 1.0 still supplies the upstream session/weekly baseline to the hybrid generator, and the current 1.5 ceiling still limits the week. M4 Stage A merely establishes a single location for those legacy numeric constraints and a clean athlete-profile interface for a future independent generator.

No catch-up mileage, longer cool-downs, higher quality work, relaxed 40% long-run ratio, revised fatigue policy, modified plan persistence or new telemetry is authorized. The existing M3 self-reported average remains provisional; the new profile does not drive production generation until a reviewed Stage B transition.

## Validation and gate

Execution status: **NOT YET TESTED IN THE USER'S GRADLE WORKTREE.** Source committed and draft PR pending targeted validation.

From `native-android`:

```powershell
.\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15M4WeeklyVolumeSkeletonTest" --no-daemon
```

If successful, compare baseline output to the R0 executed records stored locally. The R0 synthetic profile's expected allocated weeks are **67.5, 71.2, 75.0, 54.1, 71.2, 75.0, 78.7, 56.4 km**, with final Q2 mixed-unit estimated weeks **62.283, 64.673, 67.633, 54.1, 64.783, 67.773, 70.533, 56.4 km**.

M4 Stage B then requires **predeclared alternate volume trajectories** for 75→80, 50→80, 30→35, novice, returning, three-day and fatigued runners. The approved alternative must explain what replaces the Engine 1.0 upstream baseline, how unverified data constrains the early envelope, which safety invariants are retained and how outputs compare to R0. This review must precede wiring the new independent planner into production workout generation.
