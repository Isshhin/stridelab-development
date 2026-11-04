# Engine 1.5 B7 — Deterministic Release Scenario Matrix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline) and superpowers:test-driven-development for any production bugfix. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a reproducible release-hardening scenario matrix for the existing Engine 1.5 policies and edited-plan contracts, without new coaching intelligence.

**Architecture:** Build curated table-driven JUnit4 suites using the actual `Engine15.generate`, `PlanOperations`, `PlanScheduleValidation`, `PlanBackup` and `Engine1Engines`. Cases carry stable IDs and serialized input values. Classify known unsupported requests explicitly as `PlanConstraint`. Test existing SQLite/offline behavior with the dedicated Android 35 workflow rather than duplicating persistence in a fake repository.

**Tech Stack:** Kotlin/JUnit4, pinned standalone JVM harness, Gradle Android unit suite, Android 35 emulator GitHub Actions.

**Spec:** User's "STRIDELAB ENGINE 1.5 — RELEASE RELIABILITY CONTINUATION" B7 instructions, 2026-11-03. Related B3–B6 docs in `docs/engine15-batch-*.md`.

## Global Constraints

- Modify only `feature/engine15-versioned-foundation-20261103`, draft PR #37. Do not merge or mark ready; do not enable in Trackside; do not create release APK; no Engine 2.0 work.
- Never change Engine 1.0 or relax B3 hard rules to make a matrix green.
- Explicit `PlanConstraint` is a valid outcome for impossible/unsupported constraints; unexpected failure on a pre-declared supported case is not.
- The current Engine 1.0/1.5 input validator caps `runDays <= 6`: cover `runDays=7` as a clearly unsupported request, not a generated seven-run plan.
- A test for return-from-break must use `returning`, `experience` and `weeklyKm` as supported inputs; do not invent continuity metrics the engine doesn't collect.
- Run tests at exact Git SHA, capture total scenarios and category counts; never interpret synthetic tests as physiological validation.

## Review Focus

1. Generated plans may respect totals but violate adjacent-week recovery constraints; check all active pairs across week boundaries.
2. Deload and return behavior may remove hard quality while retaining session count; assert both.
3. A valid high-volume calendar may have a long run exceeding the final 40% bound after rounding; test weekly aggregates.
4. A saved plan with accepted edits may be reapproved without rechecking cross-plan conflicts; exercise approval, relocation and backup paths.
5. Deterministic same-request generation may hide unstable session identities if IDs rely on current time; pass stable `id`, `owner`, `Instant`, `LocalDate`.
6. Unsupported seven-run and tight-budget requests must fail with meaningful constraints without dropping requested workouts.

---

### Task 1: Curated single-discipline generation matrix

**Files:**
- Create `native-android/app/src/test/java/io/stridelab/privatealpha/Engine15B7SingleDisciplineMatrixTest.kt`
- Existing references: `Engine15ValidationMatrixTest.kt`, `Engine15CoachingTest.kt`, `Engine1.kt`.

**Interfaces:**
- `Engine15.generate(request, owner, id, now):PlanSnapshot`
- `Engine15.runningCeiling(week, weeklyKm)`
- `PlanScheduleValidation.validatePlan(approved)`

- [ ] Test table with explicit case IDs crossing running frequency 2–6, weekly mileage 18/35/65, quality requests 0/1/2 where valid, weeks 1/4/8, beginner/intermediate/high-mileage and returning states. Do not assert quality count equals requested; conservative Engine 1.5 may downshift quality to easy.
- [ ] For each declared supported case, verify exact per-week run count, one long, unique stable session IDs, allowed days, no illegal overlap, <= daily budget, mileage ceiling and <=40% long-run share for >=3 runs, deload/no early quality where policy applies, repeat generation equality and JSON/owner backup roundtrip.
- [ ] Add explicit negative cases: 7 run days, quality days on unavailable weekday, too many quality sessions, nonfinite mileage, wrong owner and unsupported policy. Assert deterministic clear `PlanConstraint`, not a partially filled plan.
- [ ] Run entire pinned JVM suite (`python3 native-android/tools/run_library_jvm_checks.py`). If a newly added valid case fails, use Superpowers Systematic Debugging; determine if input is genuinely invalid or if engine has a defect. Never silently skip.

### Task 2: Mixed-discipline, interference and constrained budgets

**Files:**
- Create `native-android/app/src/test/java/io/stridelab/privatealpha/Engine15B7MixedScheduleMatrixTest.kt`
- Reuse `Engine15InterferenceSchedulingTest.kt`, `Engine15MultiScheduleTest.kt`, `Engine15AvailabilityReconciliationTest.kt` as fixed reference cases.

- [ ] Curated cases for running-only, running+strength, running+cal, 5+2+2 over six days, 5+2 cal over six days, varied day availability and 90/120/150-minute budgets.
- [ ] Assert exact discipline counts, unique IDs, at most two/day where B3, per-day budget, no unsupported pairings, all `PlanScheduleValidation.conflict` pairs false, stable regeneration and backup.
- [ ] Assert explicit rejection for infeasible long/quality weekday, low daily time and >2/day capacity; include one declared fail case that must not silently drop a session.

### Task 3: Edited, owner-bound and historical reality matrix

**Files:**
- Create `native-android/app/src/test/java/io/stridelab/privatealpha/Engine15B7EditedScheduleMatrixTest.kt` OR extend existing B4–B6 classes without duplicating identical tests.
- Leave `native-android/app/src/androidTest/java/io/stridelab/privatealpha/Engine15FeedbackRepositoryTest.kt` unchanged unless a fresh failing repository invariant requires a new test.

- [ ] Test explicit lock then attempt conflicting move, completed-log preservation, safe availability update, impossible relocation, edited draft approval conflict, wrong-owner import and duplicate-safe backup.
- [ ] Test stale revision and SQLite offline reopen by retaining existing dedicated Android 35 checks; add only uncovered scenario.
- [ ] Every test failure reports the stable case ID, policy, request summary and resulting conflict; no arbitrary seeded retries.

### Task 4: Verified checkpoint, review and stopping

**Files:**
- Create `docs/engine15-batch-b7.md`.
- Update draft PR #37 body with exact verified SHA, scenario/test counts, CI links and review limitations.

- [ ] Run full offline/JVM + Android unit + protected-source CI, and Android 35 emulator where any persistence/runtime change is involved.
- [ ] Inspect PR review submissions and inline threads. Resolve Critical/Important findings by a failing regression test before correction. If no independent reviewer exists, perform an explicitly labeled self-review; do not claim independent signoff.
- [ ] Manually inspect representative generated schedules before future Trackside dogfooding; record observations without claiming coaching quality.
- [ ] STOP new Engine 1.5 features after B7. Preserve draft, open, unmerged. Trackside integration and dogfood build are separate later approved tasks.

## Runtime and reporting contract

Keep new tests to a curated ~30–70 table entries plus bounded edit cases, rather than 1000 random profiles. Use existing 64-profile and 300-engine scenario tests as regression context, but count them separately. An unexpected failure is reproduced by exact case ID + request values + version/policy. Every success claim cites a completed exact-head workflow.
