# Engine 1.5 I10 Read-Only Assembly Plan

> **For agentic workers:** Execute as a focused TDD batch in the existing isolated research worktree. No production activation.

**Goal:** Pair a validated I9 distance calendar with the I8 quality external-review result only when both independently recomputed branches identify the same dated EASY slot.

**Architecture:** I10 accepts one M4B source plus the I9 base and I8 quality inputs, then recomputes both branches from that same source. Base-only returns remain valid without quality; an I8 deferral propagates; a positive result checks every I9/M6B week-slot-date-role-distance identity and returns the existing I5/M6B opportunity as a separate quality alternative. It never merges timed workout distance into the base budget or authorizes execution.

**Tech Stack:** Kotlin internal pure functions, existing I8/I9/I5/M6 types, JUnit.

**Spec:** User's Engine 1.5 independent-candidate and quality-generation requirements; reconciliation in `research/engine15/COHESIVE_CANDIDATE_RECONCILIATION_20261117.md`.

## Global Constraints

- Preserve Engine 1.0 rollback, M10.6 policy, saved plans, offline behavior and all production flags.
- Keep quality separate from base weekly mileage; never infer exact segment distance from a speed envelope.
- I8 and I9 evidence remains caller asserted/research only; all execution and production flags stay false.
- No second Android planning interface, physical-device install or merge to dev/main.

## Review Focus

- M6B/I9 calendar mismatch → I10 defers instead of pairing different dates or budgets.
- Missing/failed I8 review → I10 propagates a reason rather than returning a misleading base-only success.
- Same-day/nearby asserted cross-discipline sessions → I10 defers the quality alternative even when M6C had allowed an easy upper-body pairing.
- Matched successful I8/I9 → quality remains an alternative on the original EASY slot, unscheduled and unreconciled.

### Task 1: I10 typed assembler

**Files:** Create `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15I10ReadOnlyCandidateAssembly.kt`; modify `native-android/app/src/test/java/io/stridelab/privatealpha/Engine15I8CalibrationArtifactIntegrityTest.kt`; update `ENGINE15_EXECUTION.md` and reconciliation report.

- [x] Add a test for successful matched I8/I9 inputs and assert the alternative's slot/date/distance identity and false authority flags.
- [x] Add tests for I8 deferral and a conflicting I9/M6B calendar.
- [x] Add tests for same-day external sessions and lower/full-body hard work the day before quality.
- [x] Add a 16-week Saturday-long recovery-cycle scenario and beginner/established dose-bound examples.
- [x] Run focused I8/I9/M5/template tests: 40 passed, zero failures/errors/skips.
- [x] Run full verification: 996 JVM tests, 0 failures/errors, 1 skipped; debug and instrumentation APK compiles passed; targeted persistence/owner/rollback/offline tests passed 58/58.
- [ ] Commit the validated batch.
- [ ] Record exact commit, tests and remaining risks in the execution ledger.
