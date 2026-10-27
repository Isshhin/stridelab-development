# Native Guided Workout and Multi-Session Integration

## Status

The integration is implemented on `feature/native-guided-multisession-integration-20261027`. It is not a verified checkpoint until the complete native Android workflow passes on the exact pushed head.

## Verified sources and ancestry

| Source | Branch | Verified commit |
|---|---|---|
| Guided workout player (baseline) | `checkpoint/native-guided-workout-verified-23726a` | `23726a9045b21d7bef82510fdb7bccab8e55337c` |
| Multi-session scheduling | `feature/multi-session-scheduling-copilot-20261027` | `8df75bd436de0a8f5426e7b368bef8e47a9f93f2` |
| Common ancestor | `checkpoint/native-engine1-goals-verified-7d5acdf` | `7d5acdf4bec15f8d95ee07d75fe152532e765d95` |

The integration started from Source A. The current implementation/test commit before this report was `14ac6cf8891a6cde9e13a71a393fecd038bfb5c1`. The report commit and the exact head tested by GitHub Actions are tracked in the branch history and final CI run.

Source A's guided player, offline entry drafts, stale-write protection, licensed movement illustrations, and actual-performance semantics remain the integration baseline. Source B's reviewed scheduling commits were applied in order: `d250633f7b680505b58134d32bb1080105b0e9d9`, `22ed62bce9add8ddbb5605cf21f031ad25688193`, and `8df75bd436de0a8f5426e7b368bef8e47a9f93f2`.

## Conflict resolution and integration decisions

- The only textual cherry-pick conflict was the saved-request default in `PlanBuilder.kt`. The resolution retains Source A's `historyWeeklyKm(..., plans=state.plans)` behavior and recognizes the multi-session policy when reusing an existing saved plan request.
- `Engine1.kt` is unchanged from the verified baseline. Multi-session generation and validation live in the separate versioned `MultiSessionEngine1` policy; the Plan Builder routes validation through `Engine1Engines.validate`.
- `EngineContracts.kt` retains Source A's `logRevision` and workout-draft contract alongside the policy identifier and daily time budget. Older serialized requests keep their existing defaults.
- `PlanOperations.kt` uses policy-aware same-day checks, keeps recovery conflicts conservative, and checks other approved plans and shared multi-session daily budgets when moving a session.
- `TrainingRepository.kt` passes the saved plans to movement validation. Its SQLite/outbox, owner isolation, encryption, existing plan IDs, and offline draft behavior remain intact.
- `WorkoutScreen.kt` retains Source A's guided player and draft staging. The scheduling change only narrows move-date error handling so validation errors remain visible instead of being reported as invalid date input.
- The new combined UI regression creates and approves an eight-session week, verifies the paired calendar cards and rejected movement, then independently logs a run and completes a calisthenics session through the guided player with entered actual repetitions.
- The native workflow's push allowlist includes only this integration branch, so the complete native verification can run on its exact pushed head without broadening CI to other branches.

## Verification performed locally

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | Passed: 94 tests, 0 failures |
| `:app:assembleDebug` | Passed |
| `:app:assembleDebugAndroidTest` | Passed |
| `:app:connectedDebugAndroidTest` | Passed on isolated `emulator-5554`: 79 tests reported, 0 failures, 1 CI-gated legacy-upgrade test skipped |
| Updated `Engine1UiTest` class | Passed on isolated `emulator-5554`: 10 tests, 0 failures |
| Python verification suite | Passed: 14 tests |
| `git diff --check` | Passed |

The first local instrumentation install was rejected because the emulator already had app version code 16 while the default candidate was code 15. No app data was cleared. Instrumentation was rerun on the same isolated emulator with the temporary Gradle property `-PstrideVersionCode=17`. No physical device was connected or modified.

The exact-head GitHub Actions run, including the legacy upgrade seed and process/offline probes, is still required. The locally skipped upgrade test is exercised only through the workflow's upgrade fixture.

## APK and signing

Local verification produced a debug APK from integration implementation commit `14ac6cf8891a6cde9e13a71a393fecd038bfb5c1` with application ID `io.stridelab.privatealpha`, version name `0.6.6-guided-workout`, and the temporary test version code 17.

- APK SHA-256: `63DBD7AC10C9A4660CEFE58EA1C2E2CCE05EFDCCC970D850E7C8FF23AB1D9AC4`
- Signing certificate SHA-256: `62725b9237a0a7f182d99503b16f20d7b2f47be5f99ce07fc59b5e30cd8dd270` (Android Debug)

Existing installed-phone signing compatibility is unknown; this APK is unsuitable for an in-place phone update. No phone installation was attempted, and emulator results are not physical-device acceptance.

## Known limitations and next action

- Do not create a verified checkpoint branch until the complete exact-head GitHub Actions run succeeds.
- If exact-head CI fails, classify the evidence, fix genuine integration regressions, push only this feature branch, and rerun CI against the new head.
- After exact-head success, create a uniquely named verified checkpoint branch and a new draft PR targeting `checkpoint/native-engine1-goals-verified-7d5acdf`. Do not merge it or alter PR #2.
- Garmin, Engine 2.0, web frontend, and visual redesign work are out of scope.

## Resume instructions

1. Run `git worktree list` and continue in the existing integration worktree if present; do not switch the source branches into the integration worktree.
2. Confirm `git -C <integration-worktree> status --short --branch` is clean and inspect `git -C <integration-worktree> rev-parse HEAD`.
3. Check GitHub Actions for the native workflow run whose head SHA exactly matches the current integration branch head. Do not mark the checkpoint verified unless every workflow job passes.
4. If the branch head changed after verification, treat the checkpoint as unverified until the new exact head passes.
