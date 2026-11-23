# Engine 1.5 B5 Safe Availability Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On an explicit availability change, relocate only affected unstarted Engine 1.5 workouts when a safe deterministic arrangement exists; leave unschedulable workouts unassigned for review, never silently delete or cram.

**Architecture:** Extend the existing `Engine15Availability.preview` as a read-only plan repair proposal. A bounded same-week search schedules displaced and previously unassigned sessions around immutable unaffected sessions and other approved plans using the same B3 policy conflict evaluator. `PlanOperations.updateAvailability` applies only the returned dates/statuses, preserving stable IDs, logs, locks and revisions. No new engine, schema migration, UI or Engine 2.0 dependency.

**Tech Stack:** Kotlin/JVM, JUnit4, Android Kotlin repository / SQLite, GitHub Actions Android 35.

**Spec:** User-provided StrideLab Engine 1.5 B5 behavioral contract, 2026-11-03; `docs/engine15-batch-b5.md`.

## Global Constraints

- Work only on `feature/engine15-versioned-foundation-20261103`, PR #37 draft, unmerged; never modify Trackside or Engine 2.0.
- Preserve Engine 1.0 and B2 policy behavior, stable plan/session IDs, manually locked sessions, completed facts and all requested weekly counts.
- Enforce selected availability, maximum two sessions/day, per-day and shared approved-plan time budgets, long/quality-day constraints and B3 interference rules.
- Never move an unaffected session, alter weekly training mileage or insert make-up sessions.
- Do not push production changes until regression tests were observed failing for the missing behavior.

## Review Focus

- Existing dates representing `unassigned` provenance must not count as active placements.
- Locked sessions, in-progress workouts and cross-plan approved workouts are immovable.
- Long/quality preference lost by removing a day must produce unresolved sessions, not rule-breaking relocation.
- Partial best-effort placement must preserve the maximum feasible number of workouts and be deterministic even when candidate ordering varies.
- Paused plans, edited plans and owner-bound backup/reopen must survive without accidentally reverting placements.

---

### Task 1: Domain reconciliation tests (RED)

**Files**
- Create: `native-android/app/src/test/java/io/stridelab/privatealpha/Engine15AvailabilityReconciliationTest.kt`
- Modify outdated assertions only after proving they conflict with the new supported behavior: `Engine15AvailabilityTest.kt`

**Interface**
- Add to `Engine15Availability.Impact`: `relocations: Map<String,LocalDate>`, `unresolvedSessionIds: List<String>` (default empty for backwards compatibility).
- `Engine15Availability.preview(plan:PlanSnapshot,days:Set<Int>, otherPlans:List<PlanSnapshot> = emptyList()):Impact` remains pure.

- [ ] Write real domain tests for one/multiple safe relocations, one/day and two/day caps, long/quality/lower-body protection, missing long preference, limited daily time, approved cross-plan collision, partial assignment, locks, history, new day availability, deterministic retries, request counts, owner-bound backup and Engine 1.0 isolation.
- [ ] Run failing targeted/offline Kotlin JVM suite against the existing B5 implementation. Expected: failing assertions that safe relocations are absent; do not modify production first.

### Task 2: Minimal bounded reconciliation (GREEN)

**Files**
- Modify: `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15Availability.kt`
- Modify: `native-android/app/src/main/java/io/stridelab/privatealpha/PlanOperations.kt`
- Modify: `native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt`
- Update protected-file hash manifest only for intentional protected source modifications.

**Interface**
- `Engine15Availability.preview(plan,days,otherPlans)` returns deterministic exact-date proposals and remaining unresolved IDs.
- `PlanOperations.updateAvailability(plan,days,otherPlans = emptyList())` applies them within existing revision-checked repository operations.

- [ ] Evaluate candidates only in each workout's original week; never move unaffected active sessions or completed/locked/in-progress facts.
- [ ] Use bounded deterministic backtracking to maximize number of placed candidates, with safe pairwise B3 policy conflicts and cross-plan approved conflict/time checks, max two per day, no unsupported same-day pairing, long/quality weekday constraints.
- [ ] Mark unplaceable displaced workouts `unassigned`; status of an affected approved plan becomes `paused` for review, even when all proposed moves fit. Unassigned retains previous date solely as provenance.
- [ ] Reuse `Impact` in `updateAvailability`, preserve IDs, dates/actuals of untouched sessions, and return clear explanations of proposed moves and unresolved conflicts.
- [ ] Run full offline JVM, Android unit and exact-head emulator CI; fix only evidenced regressions by TDD.

### Task 3: Repository integration and review gate

**Files**
- Modify: `native-android/app/src/androidTest/java/io/stridelab/privatealpha/Engine15FeedbackRepositoryTest.kt`
- Modify: `docs/engine15-batch-b5.md`
- Update PR #37 body.

- [ ] Test stale revision, lock conflict, owner-bound offline reopen, paused-plan backup and deterministic relocation after a newly available day.
- [ ] Check exact-head Android unit, offline JVM and Android 35 emulator workflows all have `completed/success`.
- [ ] Conduct independent/fresh-context review if available, otherwise identify self-review limitation. Record exact commit and test links, keeping PR draft.

## Execution Note

This extends, rather than replaces, existing B5 unassignment logic. No bulk calendar rebuild, change to Engine 1.0, automatic quality progression or cross-week volume changes are in scope.
