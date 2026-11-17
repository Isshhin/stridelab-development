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
| `Engine15.validate` | Validates Engine 1.5 request, then delegates multi-session requests to `MultiSessionEngine1.validate`, all other requests to `Engine1.validate` | Direct Engine 1.0 policy gate | New preview generator validates only its bounded running-only input contract. Production `Engine15.validate` is unchanged. |
| `Engine15.generateProgram` single discipline | Calls `Engine1.generate`, then `Engine15RunningProgram.compose` refines its run sessions | Engine 1.0 creates the calendar and initial workouts; Engine 1.5 does not independently build the baseline | M3 baseline + M4B weekly volume + M5A workout slots + M6A calendar, M6C interference review and M6D periodization audit now assemble one run-only read-only candidate. |
| Multi-session route | Calls `MultiSessionEngine1.generate`; that creates each discipline's templates by calling `Engine1.generate` | Direct and transitive Engine 1.0 generation, validation and scheduling policy | No complete independent mixed-discipline replacement exists. Candidate returns `MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT`; no generated sessions are dropped or substituted. |
| Coordinated-goal route | Calls `CoordinatedEngine1.generate`; it calls `Engine1.validate`, generates the shared schedule and per-discipline template variants via `Engine1.generate` | Direct Engine 1.0 construction and policy | No independent cross-discipline workout catalog/composer exists. Candidate defers. |
| Interference route | `Engine15InterferenceScheduling.generateBase` creates disposable per-discipline templates by calling `Engine1.generate`, then applies its own placement heuristic | Direct Engine 1.0 templates plus shared `MultiSessionScheduling` conflict rules | M6C can review a caller-supplied complete cross-discipline calendar but does not generate other-discipline workouts. Candidate supports running only. |
| Running refinement | `Engine15RunningProgram.compose` assigns purposes, volume, quality and feedback to inherited sessions | Requires an existing legacy-created session/date skeleton; it cannot independently construct the complete calendar | Candidate uses independent M5A easy/long distance construction; quality stays deferred. M10.6's history-aware running policy remains separate and is not treated as an independent calendar generator. |
| Plan/schedule validation | `PlanScheduleValidation` uses `Engine1.conflict` for ordinary and mixed-policy historical plans and combines it with `Engine15InterferenceScheduling.conflict` for interference plans | Legacy conflict policy remains in shared saved-plan validation | The candidate performs M6C/M6D validation on its independent run-only calendar. Shared import/backup validation is unchanged. |
| Request entry and app route | `TracksideCoaching.request` calls `Engine1.validate` before selecting Engine 1.5 for coordinated setup; `TrainingRepository` calls `Engine15.generateWithHistory` | Legacy validation and Engine 1.5's legacy-backed baseline remain on the app's normal path | Not rewired. This is a required future cutover batch after independent mixed-discipline construction and compatibility work. |

## Shared infrastructure retained

`PlanRequest`, `PlanSnapshot`, `PlannedSession`, workout/run prescription contracts, `RecordPolicy` identifier checks, offline repository and backup code, activity linking, workout player, approval-first operations and existing saved-plan readers are application infrastructure. The new candidate is represented with `PlanSnapshot`/`PlannedSession` types but uses an unknown shadow policy ID. A round-trip stays read-only (`Engine1Engines.canManage == false`), and owner mismatch is rejected. The candidate is not written to the repository or exposed as an approved/executable workout.

M10/M10.6 documentation reviewed: `experiments/m106-whole-week-coaching-20261115.md`, `experiments/m10a-android-research-preview-20261115.md`, `experiments/m10b-signed-out-local-plans-20261115.md`, `experiments/m10c-review-only-engine-selection-20261115.md` and `experiments/m10d-android-reconciliation-20261115.md`. These confirm that the existing coach remains the production generation route; M10 research previews and saved reviews are separate non-plan records and do not activate experimental workouts.

## Independent candidate decision owners and boundaries

1. `Engine15AthleteBaseline` assesses explicitly reported mileage provenance and the independently supplied training fields.
2. `Engine15IndependentVolumePlanner` (M4B plus the existing Engine 1.5 weekly envelope) owns proposed weekly volume, return-from-break bounds and recovery weeks.
3. `Engine15M5WorkoutComposer` owns exact easy/long distance slots and distance reconciliation.
4. `Engine15M6IndependentCalendar` owns dated running-day placement.
5. `Engine15M6IndependentInterferenceReview` and `Engine15M6IndependentPeriodizationReview` recheck calendar and weekly constraints.
6. `Engine15IndependentPlanGenerator` assembles those outputs into a complete one-to-sixteen-week run-only preview and checks identity, date, weekly sum and extent invariants.

The assembled candidate has no quality session, no measured pace/HR target and no production authority. It explicitly defers requested quality, partial-only or legacy-unclassified mileage, conflicting mileage history, recovery holds, unmapped locks, arbitrary weekday assignments, time-budget conflicts, non-Monday starts and every mixed-discipline request. The preview's duration is a display estimate from the supplied planning pace; the pace is not made a workout target.

## Missing capabilities and cutover blockers

- Independent strength, calisthenics and conditioning workout construction, plus a complete cross-discipline scheduling/spacing policy.
- Stable mapping of app `weeklySlots`, persisted `lockedSessionDays`, double-day consent and non-Monday starts into the M6 calendar contract.
- A first-party connector from activity/source coverage evidence and persistent owner-scoped feedback into M3/M4, without promoting partial imports or caller assertions.
- A production-authorized independent workout contract and an approved storage/persistence path. The current candidate is deliberately unknown-policy/read-only.
- Qualified family-specific intensity and cumulative workload rules. I8/I10 remain review-only; no quality family is executable and two-quality requests are deferred.
- Exact owner-preserving mappings for historical plans, backups, activity links, approval and rollback, followed by full compatibility/regression suites.

## Acceptance and regression coverage added

`Engine15IndependentPlanGeneratorTest` covers an eight-week 75→80 km six-day plan with Thursday unavailable, Saturday long and Sunday easy; a sixteen-week/four-recovery-cycle plan; exact weekly and workout-distance reconciliation; pace-derived duration limits; beginner/returning, intermediate and advanced request shapes; missing, legacy, partial and contradictory mileage; persistent recovery hold; one/two quality requests; mixed discipline; weekday locks; non-Monday start; deterministic output; unknown-policy saved-plan read-only behavior and owner isolation; and static legacy-call checks over the candidate's generation call graph.

All cases are synthetic. Passing tests establish contract and arithmetic behavior only; they do not validate coaching efficacy, athlete response or a safe production prescription.

## Verified conclusion

The direct and indirect Engine 1.0 dependencies in the existing production Engine 1.5 path are confirmed. A complete independent running-only preview now exists on the new feature branch, but it is not integrated into `TrainingRepository` or the Trackside request route. Mixed-discipline generation is not independent, and production `Engine15.validate`/`generateProgram` still invoke Engine 1.0-derived policy. Therefore phases 2–4 are partial, phase 3 is incomplete, and phase 6 development cutover is **BLOCKED**. Do not activate the candidate or represent this checkpoint as satisfying the final success condition.
