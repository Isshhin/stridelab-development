# Full Application Functional QA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete an evidence-based whole-application Android QA audit from the verified Trackside head, repair only newly reproduced defects, and publish a durable readiness report.

**Architecture:** Treat the existing automated suites and exact-head MARC artifact as reusable evidence for unchanged code, then map each implemented surface and critical journey to source, persistence owner, and test. Run focused checks only where the map exposes a high-risk gap; any correction follows RED-GREEN TDD before a single final broad checkpoint.

**Tech Stack:** Kotlin, Jetpack Compose, Android instrumentation/JUnit, SQLite/Android Keystore persistence, Python integrity checks, GitHub Actions on Windows x64.

**Spec:** User-provided `STRIDELAB — FULL APPLICATION FUNCTIONAL QA AND RELIABILITY AUDIT`; durable project constraints are indexed by `docs/qa/README.md`.

## Global Constraints

- Baseline is verified branch head `8dc606fec3209824fc02f3ba5196290be2b11705`; preserve all newer work.
- Engine 1.0 remains default; Engine 1.5 stays frozen and Engine 2.x is untouched.
- Do not build or publish a release APK, change signing, weaken gates, or change runner configuration.
- Distinguish source review, fixture, JVM, emulator, phone, and live-provider evidence.
- Reuse exact-head evidence only while relevant application/test code is unchanged.
- No production correction without a focused test observed failing for the reproduced defect.

## Review Focus

- Account switching, logout, damaged sessions, and restore must never expose or overwrite another owner's data.
- Move/edit/complete actions must target the selected workout and remain durable after offline repository recreation.
- File/provider imports must fail closed on corruption, retain missing metrics as missing, and never deduplicate across accounts.
- Back, nested scrolling, large fonts, and compact layouts must preserve reachable controls and state.
- Existing plans must keep their engine/version/prescriptions when Engine 1.0 is selected for new drafts.

---

### Task 1: Freeze follow-up baseline and evidence

**Files:**
- Update: `docs/qa/current-handoff.md`
- Create: `docs/reports/FULL-APPLICATION-FUNCTIONAL-QA-20261105.md`

**Interfaces:**
- Consumes: remote branch ancestry, existing QA knowledge base, MARC runs `41946839417` and `41948579450`.
- Produces: exact immutable evidence table and explicit boundaries for later classifications.

- [ ] Record remote heads, branch ancestry, working-tree state, runner labels, workflow/job/artifact URLs and exact SHAs.
- [ ] Record counts from final-head XML/log evidence without double-counting copied result directories.
- [ ] Separate phone/live-provider gaps from automated evidence.
- [ ] Commit the audit baseline with no application changes.

### Task 2: Build the complete implemented-feature and journey map

**Files:**
- Update: `docs/qa/feature-inventory.md`
- Update: `docs/qa/regression-coverage.md`
- Update: `docs/reports/FULL-APPLICATION-FUNCTIONAL-QA-20261105.md`

**Interfaces:**
- Consumes: production routes, manifests, repositories, importers, backup logic, tests, approved Trackside authority.
- Produces: implemented behavior, persistence owner, journey coverage, environment, commands, and explicit unimplemented/gap classifications.

- [ ] Map onboarding/auth, navigation, Today, Training, Builder, Manage, workout player, Activity, imports, Library, Settings, backup and offline behavior.
- [ ] Map critical journeys A-H to exact tests and evidence; label partial or untested steps rather than inferring success.
- [ ] Identify every user-facing control with no outcome assertion and every implemented importer/provider boundary.
- [ ] Add a feature-to-test selection index for future changes.
- [ ] Commit documentation-only inventory changes.

### Task 3: Close high-risk evidence gaps and repair reproduced defects

**Files:**
- Modify only the smallest owning production/test files for a newly reproduced P0/P1/P2 defect.
- Update: `docs/qa/known-defects.md`
- Update: `docs/qa/resolved-defects.md`

**Interfaces:**
- Consumes: uncovered high-risk journey steps from Task 2.
- Produces: focused pass evidence, or a documented blocked/untested limitation; verified fixes only after RED-GREEN and adjacent regression.

- [ ] Run focused existing tests for account isolation, offline persistence, workout movement/completion, file import, Activity sheet, and Back/navigation.
- [ ] For each failure, diagnose root cause before editing; write and observe the minimal failing behavioral test.
- [ ] Implement only the smallest safe correction and run the focused plus adjacent suite.
- [ ] Do not add functionality for behavior that is genuinely unimplemented; classify it separately.
- [ ] Commit each independently verified defect class.

### Task 4: Final regression, readiness report, and self-review

**Files:**
- Update: `docs/reports/FULL-APPLICATION-FUNCTIONAL-QA-20261105.md`
- Update: `docs/qa/current-handoff.md`
- Update as needed: `docs/qa/known-defects.md`, `docs/qa/regression-coverage.md`

**Interfaces:**
- Consumes: final source head and Tasks 1-3 evidence.
- Produces: final verdict, exact counts/links, P0-P3 list, Garmin/offline findings, phone checklist, and continuation boundary.

- [ ] If application/test code changed, run focused checks followed by one exact-head MARC workflow; otherwise reuse run `41948579450` for unchanged code.
- [ ] Verify Engine 1.0 default and frozen Engine 1.5/Engine 2.x boundaries from source plus regression evidence.
- [ ] Grade every finding and state passed, failed, blocked, and untested checks explicitly.
- [ ] Perform a whole-branch self-review against the five Review Focus items and correct any Critical/Important documentation or code finding under TDD.
- [ ] Commit and push the durable report without claiming automated evidence proves complete application correctness.
