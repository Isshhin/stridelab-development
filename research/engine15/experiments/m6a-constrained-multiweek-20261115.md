# Engine 1.5 M6a — Constraint-aware multiweek mileage and base workout research

Date: 2026-11-15. Branch: `research/engine15-m6-constrained-multiweek-allocation-20261115`. Based on draft PR #79, at `82ef95eb24940111c799f02e75cf89958279af4a`.

## What this batch changes

The previous M4 research model proposed a multweek progression but chose daily mileage before checking per-day ceilings, while Astra's improved `IndependentAdaptiveWeek.allocate` repaired a **single week only**. The new `IndependentConstrainedMultiweekPlanner` connects the two: generate a complete M4 weekly-volume progression, then reallocate each week's exact mileage through the constrained allocator, check each successive week's long-run continuity against the preceding accepted builds, ensure recovery runs cannot exceed the immediately preceding week, run the full M4 feasibility checker on every accepted prefix, and compose all weeks into M5a EASY/LONG typed distance workouts.

The allocator retains the original M4 **weekly kilometer totals and recovery-week flags**, the requested mileage target and all supplied per-day/long-run caps. It defers on infeasible days/continuity instead of silently reducing mileage, dropping workouts or assuming time, HR, pace or verified quality work. Caller-provided `approved...` inputs are **assertions**, not independently verified readiness.

`IndependentAdaptiveWeek.allocate` now accepts an optional `IndependentWeeklyMileagePolicy` so its daily long-run share and M4 generation use the **same** policy; existing two-argument call sites retain the previous defaults.

### Bounded synthetic example

With 60 km starting mileage, a requested 80 km goal, six running days (Mon/Tue/Wed/Fri/Sat/Sun), Saturday long, Monday capped at 5 km, other days capped at 25 km, and separately supplied previous/absolute/increment long-run limits, the original M4 distribute-then-check candidate conflicts with Monday's limit. The new model seeks an allocation on every week **without changing the M4 totals**:

| Week | Baseline retained weekly km | Recovery? | Experimental status |
|---|---:|---|---|
| 1 | 60.00 | no | Reshuffle within all supplied limits |
| 2 | 63.00 | no | Reshuffle and check long-run build increment |
| 3 | 66.15 | no | Reshuffle and check long-run build increment |
| 4 | 56.22 | yes | Reshuffle; do not grow the recovery long run |
| 5 | 69.45 | no | Reshuffle using last accepted build as the long-run reference |

These are *expected fixture outputs*, not clinical targets, completed results, or evidence of real-world feasibility for any specific runner.

## Validation / no-phone-QA gate

- New source: `native-android/app/src/main/java/io/stridelab/privatealpha/IndependentConstrainedMultiweekPlanner.kt`.
- New tests: `IndependentConstrainedMultiweekPlannerTest.kt` with **12 focused JUnit cases**.
- Adapter policy consistency change: `IndependentAdaptiveWeek.kt`.
- Focus: infeasible Monday vs repaired five-week plan, exact total/workout reconciliation, deload-long-run continuity, lower long-run ceiling, truly infeasible caps, missing approvals, two-day/no-long-run plans, custom policy, 12-week deterministic forecasts and unchanged inputs.
- **New tests were committed but NOT run in the GitHub-connected environment.** Last owner-reported full JVM/debug validation passed in 12 seconds for the preceding PR #79 implementation. Do not extrapolate that result to this branch.
- From a **separate clean Git worktree** (do not disturb user's modified validation checkout) run:
  ```powershell
  cd E:\stridelab-validation
  git fetch origin research/engine15-m6-constrained-multiweek-allocation-20261115
  git worktree add --detach E:\stridelab-m6a-validation origin/research/engine15-m6-constrained-multiweek-allocation-20261115
  cd E:\stridelab-m6a-validation\native-android
  $sdk = "$env:LOCALAPPDATA\Android\Sdk"
  $env:ANDROID_HOME = $sdk
  "sdk.dir=$($sdk.Replace('\', '/'))" | Set-Content ".\local.properties" -Encoding ASCII
  .\gradlew.bat :app:testDebugUnitTest --tests '*IndependentConstrainedMultiweekPlannerTest'
  .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
  ```

## Explicit limits and next milestones

**Not a production coaching engine.** It still uses M4's unvalidated fixed progression and deload heuristic, produces only EASY/LONG baseline distance workouts, and does **not** know future athlete tolerance or true completion evidence. Neither M3 source provenance nor intensity-zone calibration is established; quality alternatives still have unknown distance. The new preview is in an isolated research branch, not called by `Engine15.generate`, `PlanBuilder`, or saved-plan repositories. No signed-out ownership changes, Android UI, phone APK or user data changes.

**Next M6b:** design a multiweek workout-planning decision model that balances quality and easy spacing without inventing intensity or speed, and add a source-backed workout-completion/adaptation contract. **Later integration gate:** identify reliable plan/session mapping and reconcile timed quality workouts to distance allocations, then a reviewable experimental path through the app. **Phone QA: deferred until the end of the agreed roadmap**, after integration, JVM/emulator tests and packaging checks.
