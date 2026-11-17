# Engine 1.5 independent generation audit — 2026-11-17

## Verified repository and branch state

- Repository: `Isshhin/stridelab-development`.
- PR #130 remains open, draft and unmerged at `d164a75211f0cce6ffe4a2e721f8306cb56ec163`; its base PR #129 remains open/draft at `cf209b710268f116feac96154f53cb17779dbf73`.
- M10.6 PR #94, UI follow-up #95 and integration candidate #96 remain open drafts. PR #130 has no inline review threads.
- The original `StudioProjects/stridelab-development` checkout was dirty with owner changes. It was not edited. Work began in a new linked worktree/branch `feature/engine15-independent-generation-20261117`, based on PR #130's exact head.
- Engine 2.x was not inspected or changed.

## Generation call graph and policy ownership

| Path | Current decision/construction | Legacy coupling | Independent replacement/status |
|---|---|---|---|
| `Engine15.validate` | Legacy method still delegates multi-session requests to `MultiSessionEngine1.validate`, other requests to `Engine1.validate` | Retained for explicit legacy compatibility and rollback | Debug default resolves and validates the independent facade directly. The opt-in app path does not call this legacy method. |
| `Engine15.generateProgram` single discipline | Legacy method still calls `Engine1.generate`, then `Engine15RunningProgram.compose` | Retained only outside the independent route | Debug default uses M3–M6/I1–I10 running assembly and Engine 1.5 discipline composers. This legacy method is not called by the independent route. |
| Multi-session route | `MultiSessionEngine1.generate` remains the explicit legacy implementation | Direct/transitive Engine 1.0 planning | Independent static coordination now supports feasible running+calisthenics, running+strength, running+conditioning and strength+calisthenics+conditioning cases. Infeasible calendars explicitly defer. |
| Coordinated-goal route | `CoordinatedEngine1.generate` remains the explicit legacy implementation and uses Engine 1.0 | Direct Engine 1.0 construction and policy | The independent facade uses discipline-specific goal validation/composition without delegating to this route. |
| Interference route | `Engine15InterferenceScheduling` remains part of legacy multi-discipline planning | Direct Engine 1.0 templates plus legacy shared conflict rules | The independent coordinator constructs its own sessions and checks fixed weekdays, locks, double-day consent, daily limits and hard-session spacing. No call to the legacy interference scheduler occurs. |
| Running refinement | `Engine15RunningProgram.compose` remains on the legacy route | Requires legacy-created session/date skeleton | Independent running uses M3 baseline, M4B volume, M5 workouts, M6 scheduling/interference/periodization; quality stays deferred. M10.6 feedback-driven policy remains outside this static generator. |
| Plan/schedule validation | Legacy policies still use their historical conflict checks | Legacy conflict policy remains for explicit legacy plans | Independent policy IDs dispatch to the independent saved-plan validator and static combined calendar rules. Shared import/backup/repository contracts remain in use. |
| Request entry and app route | Debug default `Engine1Engines.resolve/validate` selects the facade; `TracksideCoaching.request` skips the Engine 1.0 pre-validation when the independent route is active; `TrainingRepository` creates the draft; saved approval/backup checks dispatch by policy ID | Flag-off rollback route remains legacy; release hard-codes independent flag false | Exact-head debug route, offline approval/reopen, and release flag were verified. The independent path avoids `Engine1.generate/validate`, `MultiSessionEngine1`, `CoordinatedEngine1`, and `Engine15InterferenceScheduling`. |

## Shared infrastructure retained

`PlanRequest`, `PlanSnapshot`, `PlannedSession`, workout/run prescription contracts, `RecordPolicy` identifier checks, offline repository and backup code, activity linking, workout player, approval-first operations and existing saved-plan readers are shared application infrastructure. The internal research preview retains an unknown policy ID and stays read-only; the development generator emits recognized Engine 1.5 policy IDs as managed draft snapshots, which can be explicitly approved and reopened offline. Owner isolation is preserved. Unqualified quality sessions are never added to executable plans.

M10/M10.6 documentation reviewed: `experiments/m106-whole-week-coaching-20261115.md`, `experiments/m10a-android-research-preview-20261115.md`, `experiments/m10b-signed-out-local-plans-20261115.md`, `experiments/m10c-review-only-engine-selection-20261115.md` and `experiments/m10d-android-reconciliation-20261115.md`. These confirm that the existing coach remains the production generation route; M10 research previews and saved reviews are separate non-plan records and do not activate experimental workouts.

## Independent candidate decision owners and boundaries

1. `Engine15AthleteBaseline` assesses explicitly reported mileage provenance and the independently supplied training fields.
2. `Engine15IndependentVolumePlanner` (M4B plus the existing Engine 1.5 weekly envelope) owns proposed weekly volume, return-from-break bounds and recovery weeks.
3. `Engine15M5WorkoutComposer` owns exact easy/long distance slots and distance reconciliation.
4. `Engine15M6IndependentCalendar` owns dated running-day placement.
5. `Engine15M6IndependentInterferenceReview` and `Engine15M6IndependentPeriodizationReview` recheck calendar and weekly constraints.
6. `Engine15IndependentPlanGenerator` assembles those outputs into a complete one-to-sixteen-week run-only preview and checks identity, date, weekly sum and extent invariants. M6 weeks are rolling seven-day windows from the request start; persisted running IDs ending in `-w<week>-run<slot>` map to exact lock dates.

The assembled running candidate has no quality session, no measured pace/HR target and no production authority. It explicitly defers requested quality, partial-only or legacy-unclassified mileage, conflicting mileage history, recovery holds, unmappable locks, unsupported assignments, double days on running-only requests and time-budget conflicts. The combined static coordinator independently schedules supported discipline outputs; it defers any request whose locks, availability, spacing, workload or time limits cannot fit without dropping sessions. The preview's duration is a display estimate from the supplied planning pace; the pace is not made a workout target.

## Continuation batch — independent calendar dates and running locks

At PR #131's starting head, M6 only accepted a Monday `startMonday` despite the app request carrying an arbitrary start date. Commit `5b4a8c7055a2f00526db2544702b23e6ced0fd85` changes M6 to treat each seven-day training window relative to `PlanRequest.start`, maps available ISO weekdays to a date in that window, and retains blocked-date checks. The independent generator maps lock IDs by their week/slot suffix and retains a locked ID on the matching preview session so exact date validation can run. Malformed, duplicate, stale, out-of-range or impossible locks defer with a specific reason.

Focused verification passed 119 tests across the independent generator, M6 calendar/interference/periodization/quality spacing and I1/I2/I5–I9 suites. Tests cover non-Monday starts, exact running locks, malformed/stale locks and unchanged distance arithmetic. The full JVM suite passed 1,009 tests across 130 suites with no failures/errors and one skipped legacy capture (`Engine15Q1QualityBaselineTest.captureActualGeneratorOutputAcrossEightWeeks`; no skip message). Debug and Android instrumentation APK compilation passed; no instrumentation ran. This change does not alter the legacy app route. Cross-discipline scheduling, weekly slot assignment, double sessions, app integration, and Engine 1.0 removal from `Engine15.validate`/`generateProgram` remain incomplete; cutover stays **BLOCKED**.

## Remaining boundaries after Batch 12

- Requested quality running sessions remain explicitly deferred until family, dose, and athlete-specific selection are qualified.
- The shared exercise catalog and conservative strength/calisthenics/conditioning prescriptions are bounded; handstand preparation and unavailable/excluded movement cases defer without substitutions.
- Existing active workouts in the requested window are conservatively deferred instead of merged; external hard-session coordination across separate plans is not yet automated.
- Some combined schedules, including the current all-four weekly fixture, cannot fit the conservative recurring hard-session spacing and explicitly defer. Broaden supported combinations only with validated constraints and complete-plan tests.
- Continue saved-plan/backup compatibility checks across historical schemas and strengthen automated transitive route checks. Explicit Engine 1.0 rollback remains available and separately tested.
- Dynamic changes based on completed/missed workouts, training load, HRV, sleep or readiness are deferred to Engine 2.x.
- Production readiness remains gated on coaching qualification and the repository's release review process. Debug development default is independent; release remains false.

## Acceptance and regression coverage added

`Engine15IndependentPlanGeneratorTest` covers an eight-week 75→80 km six-day plan with Thursday unavailable, Saturday long and Sunday easy; a sixteen-week/four-recovery-cycle plan; exact weekly and workout-distance reconciliation; pace-derived duration limits; beginner/returning, intermediate and advanced request shapes; missing, legacy, partial and contradictory mileage; persistent recovery hold; one/two quality requests; mixed-discipline deferrals; weekday locks; non-Monday start; deterministic output; unknown-policy preview read-only behavior and owner isolation. `Engine15IndependentDevelopmentRouteTest` additionally covers 8-week standalone and combined plans, flexible strength locks, tested supported combinations, all-four spacing deferral, request routing and static dependency scans. Android repository instrumentation verifies combined draft creation, lock preservation, approval and offline reopen with owner/session continuity.

All cases are synthetic. Passing tests establish contract and arithmetic behavior only; they do not validate coaching efficacy, athlete response or a safe production prescription.

## Verified conclusion at initial research checkpoint

The direct and indirect Engine 1.0 dependencies in the existing production Engine 1.5 path are confirmed. A complete independent running-only preview now exists on the new feature branch, but it is not integrated into `TrainingRepository` or the Trackside request route. Mixed-discipline generation is not independent, and production `Engine15.validate`/`generateProgram` still invoke Engine 1.0-derived policy. Therefore phases 2–4 are partial, phase 3 is incomplete, and phase 6 development cutover is **BLOCKED**. Do not activate the candidate or represent this checkpoint as satisfying the final success condition.

## Follow-up implementation status — 2026-11-17

The scope was corrected after this audit: Engine 1.5 must ultimately generate static plans for Running, Strength, Calisthenics, Conditioning and supported combinations; only feedback-driven adaptation is deferred to Engine 2.x. The isolated PR #131 branch now has a development route behind `ENGINE15_INDEPENDENT_RUNNING_ACTIVE` (debug default true, release forced false). It validates requests through the independent M3–M6 and discipline composition paths, routes repository generation with owner-scoped saved-plan context, maps app plan/session contracts, and uses independent saved-plan checks. Unsupported or unqualified quality requests explicitly defer. Release behavior remains unchanged.

This follow-up remains **partial**. Standalone running, strength, calisthenics and conditioning composers and the static combined coordinator route through the same `Engine15IndependentPlanGenerator` facade. Debug defaults to the independent route; `-PstrideEngine15IndependentRunningActive=false` selects legacy rollback, and release hard-codes the flag off. Strength/calisthenics use shared offline exercise records; conditioning uses the foundation-level low-impact catalog step jack with static four-week blocks. Known conflicting saved sessions, unreviewed free-text limitations/recovery notes, unqualified quality and infeasible combinations explicitly defer. Handstand preparation is deferred because the shared catalog has no qualified preparation prescription. A valid lock outside a composer's default weekday is handled by the coordinator, while standalone date/lock validation remains strict. Exact-head full JVM suites passed in candidate and rollback configurations: 1,025 tests / 131 suites, zero failures/errors, one skipped legacy quality capture. Candidate debug/instrumentation APKs and the release APK compiled. The isolated Android 15 Pixel 9 AVD combined draft/approval/offline-reopen test passed after the scheduling correction. No personal device was used. See `ENGINE15_EXECUTION.md` for batch evidence.

### User-directed volume policy follow-up (2026-11-17)

Batch 13 adds a debug-only user-directed volume selection to the existing Plan Builder and the same independent M3–M6 running pipeline. Explicit targets govern loading weeks; the fourth-week 80% deload remains. Provisional per-session exposure, the existing coaching progression envelope, and return progression are disclosed as exceeded recommendations rather than capacity claims. A deterministic eight-week test verifies six days/week at a requested 110 km target with a 5 km or absent longest-run value. The saved request records this policy. Focused 30-test JVM selection, release Kotlin compilation and whitespace validation passed. It remains a development review draft, not a validated safe prescription; quality requests are still deferred, and no emulator or APK artifact was checked for this batch. The user-directed route still needs broader plan-integrity and UI/persistence validation before any cutover claim.
