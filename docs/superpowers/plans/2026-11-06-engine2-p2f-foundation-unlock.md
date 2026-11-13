# Engine 2.x P2-F Foundation Unlock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a research-only, implementation-ready P2-F foundation and close the safely bounded synthetic ledger/linkage/trace/replay gaps without changing production Engine 1.5, Trackside, release infrastructure, or the frozen Engine 2.x architecture.

**Architecture:** Preserve F2.1 and the existing private pilot adapter as historical and operational inputs. Add a small standard-library reference module for canonical exposure projection and trace-contract validation, driven by account-scoped append-only events and explicit adjudication rather than heuristic matching. Keep human admission, real-device validation, Gate 2 release, and P2-B activation outside the implementation.

**Tech Stack:** Python 3.12 standard library, `unittest`, Markdown research contracts.

**Spec:** `docs/research/phase2/p2-f2-linkage-contract.md`

## Global Constraints

- Start from live remote head `ac1b0e27c2b16caae32e7fe4d226b7c7860c0135`; preserve all newer work.
- Frozen Session G architecture remains `26a8331e642c888dd73acb94755dded063baab8d`.
- PR #34 remains open, draft, and unmerged.
- Research-only changes; no Engine 1.5 behavior, Trackside production UI, APK, release, or raw athlete-data changes.
- Unknown, missing, zero, inapplicable, conflict, withheld, and withdrawn remain distinct.
- No Gate 2, active P2-B, real-device, or coaching-effectiveness claim without evidence.

## Review Focus

- A late or offline event must not appear in an earlier decision-time replay.
- Explicit Garmin/FIT aliases may collapse to one bout, while resemblance alone must never deduplicate.
- A correction or withdrawal must invalidate current derived projections without rewriting the historical event journal.
- Cross-account identifiers and links must fail closed without revealing foreign metadata.
- Missing HR and stationary zero speed must remain different from zero HR and undefined pace.

---

### Task 1: Audit and canonical contract

**Files:**
- Create: `docs/research/phase2/p2-f-foundation-unlock.md`
- Modify: `docs/research/phase2/p2-f2-linkage-contract.md`

**Interfaces:**
- Consumes: frozen Session G, P2-F1/F2/F3, P2-B0, P2-FI review, current bounded implementation, and commits through the live head.
- Produces: contract version F2.2, module boundaries, invariants, version/migration rules, promotion gates, and an explicit implementation decision.

- [ ] **Step 1: Write the implementation audit**

Map every requested invariant and adversarial case to `implemented`, `specified-only`, or `blocked by external evidence`, with file/test evidence.

- [ ] **Step 2: Freeze F2.2 without rewriting history**

Define the canonical Training Exposure Ledger, event identities, decision-time and current-validity projections, duplicate/correction/withdrawal rules, minimum trace contract, and migration expectations as an additive version over F2.1.

- [ ] **Step 3: Verify document consistency**

Run: `rg -n "F2\.2|P2_F_GATE_2_NOT_READY|P2_B_PROTOCOL_READY_DATA_NOT_READY|26a8331e642c888dd73acb94755dded063baab8d" docs/research/phase2/p2-f-foundation-unlock.md docs/research/phase2/p2-f2-linkage-contract.md`

Expected: all four boundaries are explicit and no production-readiness claim appears.

- [ ] **Step 4: Commit**

Commit message: `docs(research): finalize P2-F foundation contract`

### Task 2: Canonical synthetic reference and adversarial cases

**Files:**
- Create: `research/validation/p2f_foundation.py`
- Create: `research/validation/test_p2f_foundation.py`

**Interfaces:**
- Consumes: F2.2 account-scoped event envelopes and trace records.
- Produces: `FoundationLedger.append(events)`, `FoundationLedger.replay(account_id, cutoff)`, `FoundationLedger.withdraw(account_id, reason)`, and `validate_trace_segment(segment)`.

- [ ] **Step 1: Write failing adversarial tests**

Add independent literal fixtures for Garmin+FIT duplicate aliases, late sync, stale revisions, partial workouts, manual-only completion, corrections, missing HR, malformed timestamps, cross-account mismatches, duplicate aliases, offline imports, unknown intent, conflicting sources, and withdrawal after derived views.

- [ ] **Step 2: Run tests to verify RED**

Run: `py -3.12 -m unittest research.validation.test_p2f_foundation -v`

Expected: FAIL because `p2f_foundation` does not exist.

- [ ] **Step 3: Implement the minimal reference**

Implement deterministic event ordering, account isolation, explicit alias adjudication, revision/delivery linkage, current-validity invalidation, reason-coded values, trace timestamp/HR/distance/timer/gap validation, and lineage-bearing projections. Do not add persistence, human intake, heuristic matching, coaching logic, or P2-B estimators.

- [ ] **Step 4: Run focused and P2 suites**

Run: `py -3.12 -m unittest research.validation.test_p2f_foundation -v`

Expected: all new tests PASS.

Run: `py -3.12 -m unittest discover -s research/validation -p "test_p2*.py" -v`

Expected: all P2 tests PASS.

- [ ] **Step 5: Commit**

Commit message: `research: add canonical P2-F ledger reference`

### Task 3: Adversarial review and coordinating handoff

**Files:**
- Create: `docs/research/handoffs/p2-f-foundation-unlock-handoff.md`
- Modify: `docs/research/handoffs/engine2x-current-handoff.md`

**Interfaces:**
- Consumes: Task 1 contract, Task 2 code/tests, exact git history and verification output.
- Produces: exact starting/ending SHAs, artifact inventory, PASS/BLOCKED gate matrix, remaining blockers, and a tightly scoped next implementation prompt if work remains.

- [ ] **Step 1: Perform adversarial review**

Review future-data leakage, duplicate counting, provenance loss, account contamination, unknown-vs-zero, withdrawal invalidation, and overconfident inference against the code and tests; record findings and scope limits.

- [ ] **Step 2: Update handoffs**

Record exact start SHA, implementation commits, verification counts, P2-F feasibility status, Gate 2 status, P2-B status, external blockers, and next action. Preserve newer research notes and PR boundaries.

- [ ] **Step 3: Run final verification**

Run: `py -3.12 -m unittest discover -s research/validation -p "test_*.py" -v`

Expected: full research validation suite PASS.

Run: `git diff --check && git status --short && git log --oneline -5`

Expected: no whitespace errors; only intended sprint artifacts before the final commit.

- [ ] **Step 4: Commit and push with lease**

Commit message: `docs(research): hand off P2-F foundation unlock`

Push the exact tested head to `research/runna-garmin-coaching-20261102` only if the live remote head still equals the preserved starting/expected head; otherwise fetch, reconcile, reverify, and preserve newer work.

