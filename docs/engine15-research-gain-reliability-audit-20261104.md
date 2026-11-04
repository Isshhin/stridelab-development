# Engine 1.5 | Research-gain reliability review (4 November 2026)

**Branch:** `feature/engine15-versioned-foundation-20261103`  
**PR:** [#37](https://github.com/Isshhin/stridelab-development/pull/37), open/draft/unmerged  
**Starting frozen checkpoint:** `66fd5cf75226d8785fe599986f3c6f2d18be7668`  
**Narrow reliability correction:** [`10f17e5089d00004347812fd6209ca4aad232501`](https://github.com/Isshhin/stridelab-development/commit/10f17e5089d00004347812fd6209ca4aad232501)

## Verification truth table

At entry, the remote branch was identical to the last known B7 frozen checkpoint: zero newer commits. There were no PR review submissions or inline review threads.

| Evidence | Result | Interpretation |
|---|---|---|
| Initial head `66fd5cf7`: [cloud run 41767468775](https://github.com/Isshhin/stridelab-development/actions/runs/41767468775), [device run 41767462459](https://github.com/Isshhin/stridelab-development/actions/runs/41767462459) | Three jobs failed, **zero steps** | Pre-execution GitHub Actions / runner blocker, **not source test failure** |
| Correction head `10f17e5`: [cloud run 41825670988](https://github.com/Isshhin/stridelab-development/actions/runs/41825670988), [device run 41825663694](https://github.com/Isshhin/stridelab-development/actions/runs/41825663694) | Three jobs failed, **zero steps** | Same pre-execution blocker; no exact-head tests executed |
| Earlier [Batch D green cloud 41582368182](https://github.com/Isshhin/stridelab-development/actions/runs/41582368182) and [device 41582362399](https://github.com/Isshhin/stridelab-development/actions/runs/41582362399) | Historical pass: offline JVM, Android unit, Android 35 emulator | Useful regression baseline, **not** verification of the new correction |
| Current PR review | No submitted reviews or inline threads | Independent review remains a later integration gate |

Do not count GitHub infrastructure's zero-step failures as a code failure **or** a passing test suite. Preserve the exact head in every test record.

## Bounded adversarial scenario matrix

Evidence: **SRC** = current Engine 1.5 source read and inspected; **TST** = existing test source read; **DOC** = previously recorded test/handoff evidence; **R-C**, **R-E**, **R-G** = research-only scenario inspiration from [Session C](https://github.com/Isshhin/stridelab-development/blob/research/runna-garmin-coaching-20261102/docs/research/2026-11-04-session-c-progression-deload-and-re-entry.md), [Session E](https://github.com/Isshhin/stridelab-development/blob/research/runna-garmin-coaching-20261102/docs/research/2026-11-04-session-e-adaptive-prescription.md), [Session G](https://github.com/Isshhin/stridelab-development/blob/research/runna-garmin-coaching-20261102/docs/research/2026-11-04-engine2x-session-g-synthesis.md) and the [single-session-spike evidence review](https://github.com/Isshhin/stridelab-development/blob/research/runna-garmin-coaching-20261102/docs/research/2026-11-04-running-frequency-and-session-spike-evidence.md). Research findings motivate test cases, not new physiological constants.

| Scenario | Conservative expectation | Current behavior / evidence | Defect / severity / decision |
|---|---|---|---|
| Isolated missed run | Record status/reason, no catch-up | Engine 1.5 `PlanOperations.missed` does not stage legacy suggestion; generation does not redistribute missing distance (SRC/TST/R-E) | No demonstrated defect; retain |
| Multiple missed days / missed week | No silent load repayment | No automated miss-derived mileage growth; no individual interruption inference (SRC/R-C) | No demonstrated defect; personalized model is **ENGINE_2X_ONLY** |
| Partial run, shortened quality | Do not infer completion or a revised stimulus | Feedback evidence requires completed status, nonzero log revision, manual effort; incomplete records do not trigger proposals (SRC/TST/R-E) | No demonstrated defect; stimulus-preserving choices are **ENGINE_2X_ONLY** |
| Shortened long run | Retain actual performed record, do not forge original plan | `WorkoutLog` remains separate from saved prescription; no automatic catch-up (SRC/TST) | No demonstrated defect |
| Return after interruption | Remain conservative and avoid immediate full-dose return | User-supplied `returning` suppresses quality for initial weeks; caps remain bounded (SRC/TST/R-C) | No demonstrated defect; inferred re-entry dose is **ENGINE_2X_ONLY** |
| Illness / recovery | Do not diagnose or issue automatic clearance | Explicit missed reason may offer reviewed rest; acceptance requires athlete action (SRC/R-E/G) | No demonstrated defect; illness-clearance inference **ENGINE_2X_ONLY** |
| Deload and taper | No catch-up or added intensity | Four-week running ceilings, quality suppression and long-run caps (SRC/TST) | No demonstrated defect |
| Single-session distance spike | Existing bounded distance and longest-run inputs, no invented injury cutoff | Long-run share cap with 3+ runs, prior longest-run bounded by declared reference (SRC/TST/R-C) | No demonstrated defect; historical fitted risk threshold **ENGINE_2X_ONLY** |
| Quality / long within one interference plan | Reject adjacent demanding sessions | `Engine15InterferenceScheduling.conflict` and existing B3/B7 tests (SRC/TST) | No demonstrated defect |
| **Quality / long / lower-body across mixed policy IDs** | Keep interference-policy hard spacing without relaxing baseline cross-policy rules | **Before fix**, `PlanScheduleValidation.conflict` fell through to `Engine1.conflict` when just one policy was interference; adjacent long-long was accepted by this predicate (SRC) | **P1: FIXED IN SOURCE**; require executed regression |
| Legacy cross-plan same-day behavior | Do not silently allow previously forbidden pairings | Mixed-policy fix computes baseline conflict **OR** interference conflict; never weaker than prior baseline (SRC/new test) | No new permissive behavior |
| Sudden availability loss | Relocate only feasible unstarted sessions, otherwise leave unassigned and pause for review | `Engine15Availability.preview`, `updateAvailability` retain IDs/history and bounded same-week placement (SRC/TST) | No demonstrated defect |
| Athlete locks and manual moves | Keep approved locks; reject invalid moves | `Engine15SessionLocks`, `PlanOperations.move`, edited-plan tests (SRC/TST) | No demonstrated defect |
| Unresolved placement, invalid day | Block approval until reviewed | `Engine15Availability.requireReady` and edited-plan validation (SRC/TST) | No demonstrated defect |
| Safe double days, strength/run conflict | Enforce two/day and daily minutes; keep recovery checks | Interference scheduler and B7 mixed matrix (SRC/TST) | No demonstrated defect |
| Stale, duplicate, already-reviewed feedback | Never apply proposal without fresh evidence and explicit accept | `Engine15Feedback.preview/stage/requireCurrentPendingAcceptance` and Batch C/E1 tests (SRC/DOC) | No demonstrated defect |
| Duplicate linked source | Ambiguous activity linkage cannot be used as effort evidence | Linked source de-duplication in preview (SRC/TST) | No demonstrated defect |
| Account isolation, backup/reopen, rollback | Reject cross-owner and unsafe restored schedules atomically | Batch D Android 35 device checks and current restoration validator (DOC/SRC) | No demonstrated defect; **new exact-head test execution pending** |
| Engine 1.0 fallback | Default stays 1.0, 1.5 explicit opt-in, opaque versions untouched | `Engine1Engines.resolve` selects 1.5 only for `policyChoice="engine15"`; Trackside UI unexposed (SRC/DOC) | No demonstrated defect |
| Missing HRV / sleep / environment / individualized threshold | No speculative adaptation | No such new logic added (SRC/R-G) | **ENGINE_2X_ONLY** |

**Audit limitations:** This is a targeted source/research and existing-regression review, not an exhaustive formal proof of every item. A row marked “no demonstrated defect” does not claim a new exact-head runtime test passed. The session-spike literature does not justify a universal 10% rule or injury prediction.

## Narrow fix

`PlanScheduleValidation.conflict(a,b,policyA,policyB)` now applies:
1. **Both** interference policies: original Engine 1.5 interference checker, unchanged.
2. **Exactly one** interference policy: `Engine1.conflict(a,b) || Engine15InterferenceScheduling.conflict(a,b)`; this only adds a rejection where one of the existing policies already considered that pairing unsafe, retaining baseline restrictions.
3. **Neither** interference policy: exact original policy branch, unchanged.

This applies to existing approval, movement, availability and backup/restoration validation callers through their shared calendar predicate. No generator, state schema, persisted snapshots, policy ID, progression formula, lock semantics, feedback algorithm, Engine 1.0 implementation, or Trackside code was modified.

**Two JUnit test methods** were added to `Engine15InterferenceSchedulingTest.kt`: mixed-policy long/quality/lower-body adjacency in both argument orders across four supported alternative policy IDs, conservative same-day backward compatibility, and rejected approval/restoration for adjacent cross-policy long runs. Execution is pending.

## Final scope check

- No new physiological inference or hidden readiness.
- No HRV/RHR/sleep/heat/MAF/LT1/matched-HR/adaptive prescription.
- No increased mileage, new automatic plan adjustments or missed-session repayment.
- No automatic relocation, hidden override of an athlete lock or unreviewed change.
- No Engine 2.x, research-branch, Figma, Compose or APK changes.
- PR #37 remains open, draft, unmerged; Engine 1.0 remains default.

## Verification and refreeze gate

`ENGINE_1_5_STATUS = RESEARCH_GAIN_RELIABILITY_VERIFICATION_PENDING`

**Do not mark `RESEARCH_GAIN_RELIABILITY_REFROZEN` until all final-head requirements pass:** independent offline-JVM/Exercise Library checks (including integrity and offline repeat), Android unit/compile, real SQLite + offline reopen, source revision/rollback, duplicate-source and account isolation, mixed-discipline, locking, availability and interference regression on Android 35, plus review of the final diff. If GitHub-hosted CI is unavailable, run the same gate on the planned Windows self-hosted runner, identifying any unavailable emulator features honestly.

Exact next integration prerequisite: execute tests successfully on the final head, resolve actual failures if any, document review and refreeze, then obtain separate explicit Trackside integration approval with approved Figma review screens. No activation before that.
