# Engine 1.5 M10b — Signed-out owner-isolated local plans

Date: 2026-11-15. Stacked on M10a draft PR #89. M10b is **source committed, NOT built/tested**. Physical-device tests remain deferred.

## Goal and actual implementation

StrideLab must not require account sign-in to build, approve, review or log training, and sign-in should remain optional for cloud services. The existing Android PlanBuilder and TrainingRepository blocked plan creation when TrainingState.account was null despite the separate, already working signed-out Garmin cache.

### Local plan identity and persistence

- New \`LocalPlanOwnership\` creates a persistent \`local-plan-<UUID>\` profile in existing per-install private SharedPreferences under \`local-plan-owner-v1\`. It is **independent** from \`local-garmin-<UUID>\` and any authenticated cloud account.
- TrainingState includes \`localPlanOwner\`. While signed out, the repository loads plans, templates, shelf metadata and in-progress workout-entry drafts from **only that local-plan namespace**. It merges the existing local Garmin history separately, as before.
- The repository's ordinary generation, approval, plan mutation, manual feedback, workout progress, missed sessions, template creation and local entry-draft staging now resolve the active owner as \`account ?: localPlanOwner\`. Every plan mutation checks the *exact active owner* and retains the plan's original \`owner\` / engine / policy / revision.
- **Critical sync boundary:** local plan/template/shelf records are written via \`TrainingStore.applyRemotePage(localOwner,kind,rows,...)\`, which commits directly to local SQLite without adding them to the network outbox. Signed-in account writes still use the existing \`store.enqueue\` cloud-replay semantics. Local workout-entry drafts were already stored through a local SQLite path; they now use the effective owner.
- Sign-in resets the repository state and loads **only cloud-account plans**; existing guest plans stay on disk, unmodified and inaccessible in the signed-in plan list. Signing out restores only guest-local plans plus local Garmin activity summaries. **No silent owner reassignment, cloud upload, cross-account merging or reparenting.**
- Backup export remains a manual user action and exposes only the current profile's visible plans. Manual import validates the exact profile owner; a same-owner guest backup may be restored as one transactional local SQLite page with no cloud outbox, and a foreign owner or opaque raw-ID collision fails closed. Cloud-account restores keep their prior enqueue flow.

### Android visibility

- Remove the PlanBuilder's sign-in wall. Normal \`PlanActions.generate\` still invokes the current production Engine 1.5; M10a experimental research remains optional and read-only.
- Update \`TrainingScreen\`, \`StrideLabUnifiedTrainingTimeline\`, legacy \`TracksideTrainingSchedule\`, and \`StrideLabTodayScreen\` to display repository-scoped local plans while signed out, not to force cloud authentication. Training's plan-tools action opens review rather than sign-in; guest Today's primary call to action opens offline plan creation, not sign-in.
- The no-history signed-out hero keeps its existing visual size/layout, with the action label changed to **Create plan**. Local plans use the same reviewed plan UI, session management and recording flows as account plans.

### Expected behavior to test after M10 integration

1. New install, no cloud account: generate and approve a plan; create a workout-entry draft; save feedback; close and reopen; the exact original owner, plan revision, completed/logged session and draft remain visible.
2. All guest records are stored under a unique \`local-plan-<UUID>\`; the \`outbox\` has **zero** pending mutations for that owner after creation, edits and local import.
3. Sign in to account A: its plans are shown and guest plans are hidden, not uploaded. Sign out: original guest plans reappear. Switch to account B: no A/guest plans leak.
4. A manual backup from the **same device-only identity** can add new plans without modifying existing ones; a different account's or different installation's owner-ID backup is rejected. Unsupported existing raw records are not overwritten by a restore.
5. Guest-created plans are visible in Today and Training, and workout/player actions do not redirect to sign-in.

## New tests, not executed yet

- \`LocalPlanOwnershipTest.kt\`: **4 JVM tests** covering unique device owner generation, invalid/cloud owner rejection, account isolation and distinct local profiles.
- \`LocalPlanRepositoryTest.kt\`: **5 Android instrumentation tests**, not yet run (and intentionally deferred to M10.1), covering local SQLite persistence and approval/reopen, entry drafts/feedback, guest/cloud isolation on sign-in/out, matching-owner backup restore, and opaque-record preservation.

The stacked M8–M10a research tests also still require exact-SHA validation. GitHub commit presence does not imply that Kotlin compiles or tests pass.

## Known limitations and non-negotiable next gate

- **Device identity is not portable by default**. A manual plan export contains the original owner and cannot be imported on a new installation with a different local-plan UUID. M10c/M10d must add a *separate, explicitly user-approved* export/import/migration flow with scoped reparenting or a reviewed secure identity transfer before calling the backup experience complete. Do not silently rewrite archived plan owners or allow foreign plans into the current cache.
- SQLite data is still stored in app-private local storage; uninstalling, clearing app data or losing the device may erase local plans. Sign-in does NOT automatically enable cloud backup for existing guest records.
- Source provenance, beginner/returning runner rules, race-specific periodization, mixed-discipline load and M9-quality workout intensity are still unqualified. M10b does not declare an executable experimental coaching engine.
- Review new local-storage policy for any remaining UI/navigation sign-in-only surfaces during M10d. Test date/owner isolation and backups on the Android emulator after full M10.

## Agreed remaining roadmap

M10c: explicit experimental engine selection with a versioned saved-plan contract and safe rollback, or a clearly bounded review-only fallback where workouts remain unverified.

M10d: reconcile PR #78 modern Week Preview UI, owner/migration/export workflow, and other signed-out UI entrypoints, without overwriting approved saved plans.

**M10.1:** run Android JVM/instrumentation/emulator tests after all M10 integration is in place, repair regressions.

**M10.5:** Astra independently tests the integrated Android engine as a running coach (real generated plan trajectories, multiple profiles, training adaptations), then recommends corrections.

**M10.6:** implement and retest Astra findings.

**M11:** only then package and install on user's physical phone. No automatic production merge/activation yet.
