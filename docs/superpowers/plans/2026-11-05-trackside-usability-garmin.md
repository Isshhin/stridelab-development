# Trackside 3.0 Usability and Garmin Foundation Implementation Plan

> **Product requirement supersession (2026-11-05):** See [approved Engine 1.5 primary / Engine 1.0 fallback / quality-plan gates](../../qa/engine15-primary-plan-quality-requirements-20261105.md). The tasks below document their original QA scope; a later correction to packaged engine routing and full plan-quality acceptance is required before the product can be accepted.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a behaviorally verified Trackside private-alpha with no known P0/P1 interaction defects after current verification and a bounded, official Garmin integration foundation.

**Architecture:** Preserve the frozen Engine 1.5 integration and correct interaction state at the smallest owning boundary. Add an anchored map-backed Activity sheet, explicit per-session weekday selection, outcome-focused production-route tests, and a provenance-bearing Garmin source adapter that cannot silently change coaching.

**Tech Stack:** Kotlin, Jetpack Compose, Android instrumentation/JUnit, SQLite-backed repositories, Python verification scripts, official Garmin Personal API documentation.

**Spec:** `docs/superpowers/specs/2026-11-05-trackside-usability-garmin-design.md`

## Global Constraints

- Starting head is `9d3c559e4b92a366a3b52934b19ea37fb0e65084`; preserve legitimate newer work and never reset it.
- Engine 1.5 is frozen; Engine 2.x remains separate and unmodified.
- Documentation, approved Figma, Android implementation, and behavioral tests must agree.
- Write a failing behavioral test and observe the expected failure before every production correction.
- Never fabricate GPS, Garmin credentials, tokens, health data, signing material, or athlete data.
- Keep Intervals.icu and FIT/TCX/GPX paths independent and intact.
- Do not weaken tests, replace permanent signing with debug signing, or claim install verification without an actual install.

## Review Focus

- Occupied weekday taps after the editor is already open must select the owning session without moving the previously selected slot.
- Sheet drag must cooperate with content at scroll offset zero and nonzero, including interrupted/high-velocity motion.
- Save/reopen and process recreation must retain placements, sheet/tab state where specified, plan ownership, and engine version.
- Large fonts and compact widths must leave destination controls, sheet actions, and Continue reachable at useful touch sizes.
- Duplicate/ambiguous multi-source activities must preserve provenance and never be silently deleted or double-counted.

---

### Task 1: Freeze baseline, specification, and functional matrix

**Files:**
- Create: `docs/reports/TRACKSIDE-FUNCTIONAL-BEHAVIOR-AUDIT-20261105.md`
- Create: `docs/superpowers/specs/2026-11-05-trackside-usability-garmin-design.md`
- Create: `docs/superpowers/plans/2026-11-05-trackside-usability-garmin.md`

**Interfaces:**
- Consumes: frozen integration head, current handoffs, approved Figma nodes, open PR/review state.
- Produces: one row per visible behavior with authority, owner, test, severity, root cause, and final result.

- [ ] Record remote head, branch containment, open Trackside/Engine 1.5 PRs, review threads, and current handoffs.
- [ ] Inspect every relevant approved Figma frame and record exact node/state intent.
- [ ] Inventory production routes, test tags, callbacks, state owners, persistence paths, and current tests.
- [ ] Populate the matrix before broad production fixes; keep final outcomes current through the pass.
- [ ] Commit the baseline documents.

### Task 2: Correct Running Weekly Setup selection and sibling slot pickers

**Files:**
- Modify: `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideFigmaRunningSetup.kt`
- Modify as evidence requires: `WeeklyPlacement.kt`, `TracksideFigmaOtherDisciplineSlots.kt`, Availability/Move controls.
- Test: `native-android/app/src/androidTest/java/io/stridelab/privatealpha/TracksideApprovedBuilderActivityUiTest.kt`
- Test: `native-android/app/src/test/java/io/stridelab/privatealpha/WeeklyPlacementTest.kt`

**Interfaces:**
- Consumes: `WeeklyPlacement.days/put/clear/resize/label` and PlanBuilder-selected slot state.
- Produces: occupied-day selection by owning slot; explicit destination move/swap; stable slot identities.

- [ ] Write the production-route failing test: Long Run Saturday, Easy 1 Wednesday, tap Wednesday, assert Easy 1 selected and Long Run unchanged; then move to Friday and recreate.
- [ ] Run it and confirm failure is the slot-0 mutation/selection defect.
- [ ] Implement occupied-day selection without mutation and explicit destination movement.
- [ ] Add Quality 1/2, Easy 2+, Saturday, unavailable, swap, clear, count-change, manual/automatic, and save/reopen cases.
- [ ] Audit and correct the same index/stale-selection class in other discipline/day pickers.
- [ ] Run targeted and full JVM tests; commit only after green evidence.

### Task 3: Implement a genuine Activity Detail anchored sheet

**Files:**
- Modify: `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideFigmaActivityDetailSurface.kt`
- Modify if route state requires: `NativeMainActivity.kt`
- Test: `native-android/app/src/androidTest/java/io/stridelab/privatealpha/TracksideApprovedBuilderActivityUiTest.kt`
- Test: `native-android/app/src/androidTest/java/io/stridelab/privatealpha/TracksideResponsiveQaTest.kt`

**Interfaces:**
- Consumes: fixed map hero, selected tab, scroll state, route Back callback.
- Produces: saveable collapsed/expanded anchor state, nested-scroll arbitration, velocity-aware settle, accessibility actions, collapse-before-leave Back contract.

- [ ] Write failing production-route drag tests that measure sheet bounds before/after upward and downward gestures and assert the map remains rendered.
- [ ] Write failing nested-scroll, tab-retention, Back-collapse, and accessibility-action tests.
- [ ] Run tests and confirm the fixed-offset implementation cannot satisfy them.
- [ ] Implement the smallest anchored-sheet state/gesture correction using existing Compose dependencies.
- [ ] Verify inner Charts scrolling when expanded and sheet collapse only at inner offset zero.
- [ ] Run responsive profiles at 360/1.0, 360/1.3, 412/1.0, and 412/1.5; commit after green evidence.

### Task 4: Complete application-wide functional and adversarial audit

**Files:**
- Update: `docs/reports/TRACKSIDE-FUNCTIONAL-BEHAVIOR-AUDIT-20261105.md`
- Modify production/test files only for confirmed defects.

**Interfaces:**
- Consumes: Today, Training, Manage Plan, Activity, Builder, execution/logging, persistence, responsive, accessibility routes.
- Produces: PASS/FIXED/DEFERRED status for every visible interaction and regression coverage for every confirmed defect.

- [ ] Exercise forward/back, rapid taps, selected-item removal, empty/max inputs, incomplete assignments, and route restoration.
- [ ] Verify every user-facing interaction claim against actual behavior and correct misleading copy.
- [ ] Write and observe failing regression tests for each confirmed defect before its minimal fix.
- [ ] Re-run production-route instrumentation and responsive/accessibility checks after each defect class.
- [ ] Perform a skeptical final break-it pass and update matrix counts/statuses.

### Task 5: Reverify Engine 1.5 and persistence boundaries

**Files:**
- Tests only unless a proven integration compatibility defect exists.
- Update: current authoritative handoff and audit report.

**Interfaces:**
- Consumes: generation, approval, locking, availability reconciliation, feedback evidence, version classification, backups.
- Produces: fresh exact-head results for Engine 1.5 and Engine 1.0 rollback/account isolation.

- [ ] Run generation/approval/persistence/offline/lock/availability/relocation/conflict scenarios.
- [ ] Run feedback, stale rejection, account isolation, version classification, and Engine 1.0 rollback tests.
- [ ] Reopen state after repository recreation and verify no cross-account contamination.
- [ ] Record exact counts and any environment-limited gates.

### Task 6: Build the bounded official Garmin foundation

**Files:**
- Create/modify focused Garmin client, secure credential store, source adapter, normalization/dedupe, repository, and compact connection UI files under `native-android/app/src/main/java/io/stridelab/privatealpha/`.
- Add JVM/instrumentation fixtures and tests in corresponding test trees.

**Interfaces:**
- Consumes: official Garmin Personal API token/activity specifications available during implementation and existing `WearableImportProvider`/activity persistence boundaries.
- Produces: `source = GARMIN` normalized records, upstream IDs, secure credential lifecycle, sync/disconnect state, no direct Engine 1.5 mutation.

- [ ] Read only official Garmin documentation and record accessible endpoint/token contracts; do not invent unavailable semantics.
- [ ] Write failing tests for missing/invalid key, token success/expiry, network/malformed/rate errors, empty/import/duplicate, provenance, disconnect/restart/offline/account isolation, and secret redaction.
- [ ] Implement transport and Android secure storage only to documented contracts.
- [ ] Implement normalization, explicit-source dedupe, persistence, and compact UI status.
- [ ] Run all Garmin tests; use mocks unless a real personal key is available and report the live-key requirement.

### Task 7: Full exact-head verification, release evidence, and handoff

**Files:**
- Update authoritative Trackside handoff without rewriting history.
- Update audit report and add final phone-smoke checklist.

**Interfaces:**
- Consumes: final branch head and all earlier gates.
- Produces: precise verdict, counts, known defects, artifact metadata, and phone checklist.

- [ ] Run full Android unit, instrumentation, smoke, responsive, Engine 1.5, persistence, Garmin, Python/integrity, and adversarial suites on the exact head.
- [ ] Build exact-head release APK if practical; keep it unsigned when the permanent identity is unavailable.
- [ ] Record commit, variant, size, SHA-256, versionCode/versionName, signing status, and install status.
- [ ] Update handoff with every fixed/deferred/remaining defect and exact test counts.
- [ ] Re-read the specification and matrix line by line; report no completion claim without fresh command evidence.

