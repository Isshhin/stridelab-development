# Engine 1.5 B6 Edited-Plan Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for this plan. Use test-driven-development and verify each exact-head gate.

**Goal:** Keep edited Engine 1.5 plans intact across lock, actual record, availability reconciliation, approval, JSON/offline reopen and backup/recovery. Reject internally unsafe edited drafts before approval.

**Architecture:** Keep PlanSnapshot, PlanOperations, TrainingRepository, PlanBackup and PlanScheduleValidation; no new engine, UI state, schema migration or physiological rules. Enforce existing immutable B3 calendar constraints at Engine 1.5 approval, then test stored snapshots and revision-checked operations together.

**Tech Stack:** Kotlin, JUnit4, Android 35 SQLite instrumentation, GitHub Actions.

**Spec:** The user's Engine 1.5 Batch B6 reliability instructions and B5 plan `docs/superpowers/plans/2026-11-03-engine15-b5-safe-availability-reconciliation.md`.

## Global Constraints

- Existing PR #37 remains draft/unmerged; Engine 1.0 semantics and production default unchanged.
- Preserve session IDs, completed logs, manual locks, accepted adjustments, daily time budgets, explicit unassigned state and owner scope.
- Never automatically regenerate when reopening a saved plan; no catch-up volume.

## Review Focus
- Drafts modified outside safe repository movement must not bypass B3 interference during approval.
- A paused edited plan restored from backup must preserve unresolved sessions without treating their old dates as active.
- An already completed workout must not be erased by availability updates or save/reopen.
- A stale repository revision must reject lock or calendar changes atomically.
- A backup sharing a plan ID with an existing record must not overwrite it.

---

### Task 1: Reproduce edited-plan approval hole (RED)

**Files:**
- Create: `native-android/app/src/test/java/io/stridelab/privatealpha/Engine15EditedPlanIntegrityTest.kt`.

- [ ] Test intentionally conflicting Engine 1.5 draft with two easy runs moved to one date, then request approval. Expected: `PlanConstraint`. Existing `approve` is missing within-plan validation, so the test must fail before a fix.
- [ ] Run pinned offline Kotlin JUnit suite on the existing branch and capture expected failure.

### Task 2: Minimal approval guard (GREEN)

**Files:**
- Modify: `native-android/app/src/main/java/io/stridelab/privatealpha/PlanOperations.kt`
- Update `native-android/tools/fixtures/baseline-protected-files.json` only if the source is protected.

- [ ] Construct proposed approved snapshot, call existing `PlanScheduleValidation.validatePlan` for Engine 1.5 only, and return it if safe. Do not change Engine 1.0 approval.
- [ ] Rerun all JVM, Android unit and source integrity checks. Do not suppress existing regression tests.

### Task 3: Edited persistence and offline repository verification

**Files:**
- Extend `Engine15EditedPlanIntegrityTest.kt`.
- Modify `native-android/app/src/androidTest/java/io/stridelab/privatealpha/Engine15FeedbackRepositoryTest.kt` only for additional cross-edit persistence/stale-revision evidence.
- Update `docs/engine15-batch-b6.md` and PR #37 after verified.

- [ ] Prove lock + actual log + B5 relocation preserves stable IDs and total prescriptions after JSON and owner-bound backup roundtrip; old plan IDs never overwrite existing on restore.
- [ ] Prove marked-completed history unchanged, and stale revision and lock conflicts fail without partially mutating the plan.
- [ ] Confirm exact-head offline JVM, Android unit, Android 35 emulator and independent code review or disclose if only self-review is available.

## Release Gate

Do not claim B6 verified until all exact-head checks succeed. Do not merge, produce APK or enable Trackside.
