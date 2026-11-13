# Batch 8B — Executable B0/H1 research candidates, frozen scenarios, and approval/rollback tests

**Verified:** November 3, 2026 (Asia/Manila). **Repo:** `Isshhin/stridelab-development`. **Scope:** isolated research PR #34 only. **CI:** [successful GitHub Actions PR run 41582744370](https://github.com/Isshhin/stridelab-development/actions/runs/41582744370), commit `5e4b325ce641a7903d8e3959f51f3829aa511e95`. **Status:** new research policy executables validated for a defined set of synthetic *structural* behaviors; not the existing local Python Athlete Simulation Lab, not an Android coaching engine or a demonstrated fitness improvement.

## 1. Work actually added

- [Independent B0 / H1 research policies](../../research/validation/policy_candidates.py). `B0_distance_capacity_v1` retains the base weekly mileage/day-capacity decision. `H1_constrained_multidose_v1` adds conservative proposal rules for source certainty, recent cross-discipline overlap, unavailable equipment, skill prerequisites, self-reported pain and return-from-break uncertainty. Both share a typed observation summary and preserve plan vs actual workout state. Neither applies a proposal without approval. **These are original new policies, not ported versions of the user's historical local lab or commercial products.**
- [Frozen synthetic inputs and separately authored expected outputs](../../research/validation/frozen_policy_cases.json): **58 paired counterfactual scenarios**, a synthetic default athlete state, per-case patches, and **157 explicit independent property assertions**, each mapped exactly to E003 18, E004 22, E005 18 from the canonical registries. Inputs contain NO real athlete recordings, personal information, or private synthetic dataset content.
- [B0/H1 comparison runner](../../research/validation/policy_runner.py): executes B0 and H1 for base and changed states; evaluates fixture assertions, checks for forbidden unapproved changes, records exact input hashes, candidate file hash, evaluator hash, per-case actions, and qualitative B0/H1 disagreements. A policy and its own evaluator are **not the same function**; however test authoring and prototype design came from the same research effort and still require external audit.
- [Negative controls](../../research/validation/test_policy_runner.py): fail when a capacity-violating proposed action is deliberately changed to KEEP, when an unapproved proposal is deliberately marked applied, on missing registered cases, and on invalid/missing expected output paths.
- [Copy-on-write revision ledger](../../research/validation/revision_ledger.py) and [10 ledger tests](../../research/validation/test_revision_ledger.py): explicit proposed/approved/rejected/rolled-back status, monotonically versioned approved changes, stale-write rejection, prohibition on duplicate approval, and non-destructive recorded workout history. **In-memory proof-of-concept only**, not persistence, atomic Android database behavior, or multi-client concurrency safety.
- Updated [.github/workflows/research-engine2-contract-validation.yml](../../.github/workflows/research-engine2-contract-validation.yml) so the frozen policy run and toy contract suite execute in the same GitHub-hosted Python 3.12 CI, with separate statuses and JSON output artifacts.

## 2. Results verified from GitHub logs

| Check | Result | What is and is not established |
| --- | --- | --- |
| Synthetic toy-contract suite (previous Batch 8A) | **58 PASS, 0 FAIL** | Structural tests against the *older toy contract model*, not the new B0/H1 research policies |
| NEW executable B0/H1 comparison | **55 PASS, 0 FAIL, 3 BLOCKED** | 55 cases satisfy frozen input/output structural properties of newly implemented candidate policies, with three predictive-workout cases unsupported |
| B0/H1 decision disagreements | **14 scenarios** | There are behavioral differences; no evidence the hybrid action is more physiologically correct |
| Harness, negative-control and revision ledger unit tests | **21 PASS** | In-memory tests including deliberately broken rules that the validator catches |
| E003 / E004 / E005 candidate checks | E003 **18 PASS**, E004 **22 PASS**, E005 **15 PASS + 3 BLOCKED** | **Structural scope only**; 'PASS' does not mean original complete scientific hypothesis is resolved |
| The user's pre-existing separate local Python Athlete Simulation Lab | **NOT ACCESSED / NOT VERIFIED** | No assertion about local policies, baseline engine checkpoint, or personal protected datasets |
| Original Android Engine 1.0 / 1.5 / future release Engine 2.0 | **NOT TESTED** | Production Kotlin code unchanged; no promotion |
| Runna Experiment 002 and Garmin Run Coach controlled outputs | **0/16 Runna, no paired Garmin cases** | Still access-gated |

### Three intentionally blocked cases

- **E005:AL02** — individualized predicted workout difficulty for two athletes with identical objective workout level.
- **E005:AL03** — whether a recent hard effort correctly changes calibrated personal forecast probability.
- **E005:AL07** — whether a new 2026 predictive workout selector is empirically different/better than an older levels-driven selector.

The new H1 appropriately reports `NOT_VALIDATED_NO_MODEL` instead of fabricating personal predicted outcomes. Capturing distinct source histories/versions alone **does not satisfy** the original prediction hypotheses.

### Auditing the counts

- Actual GitHub Actions step printed `Ran 21 tests`, `NEW B0/H1 RESEARCH POLICY VALIDATION: 55 PASS, 0 FAIL, 3 BLOCKED`, and `CASES WITH B0/H1 ACTION DIFFERENCES: 14`.
- The workflow also prints `SYNTHETIC CONTRACT CHECKS: 58 PASS, 0 FAIL` and `REAL ENGINE VALIDATION: 0 PASS, 58 BLOCKED`. **The last line refers to the old toy runner's connection to the pre-existing user's real coaching implementation.** The new candidate comparison has its **own separate** JSON and pass count. Never add the two passes together or claim the pre-existing engine passed.
- The artifact `research-validation-results` contains `research-validation.json` (toy), and `research-policy-comparison.json` (new B0/H1). Report source SHA-256, fixture SHA-256, evaluator SHA-256, policy SHA-256 and individual case decisions. The exact CI run and commit link, not a headline without logs, are the authoritative evidence.

## 3. Interpretation and known limitations

1. **No athlete adaptation efficacy** was established. Fixed synthetic assertions measure whether policy *structures* behave as specified, not whether those actions improve aerobic pace, threshold, durability, hypertrophy, or skill performance. The data were constructed by the same research team as the proposed behavior, and independently held-out cases have not yet been evaluated.
2. **No clinical or injury safety claim** is possible. `PAIN_NEEDS_HUMAN_REVIEW` is a conservative deferral state, not medical diagnosis; flags for leg-strength/hard-run overlap are not validated recovery-hour prescriptions.
3. **The B0/H1 implementation is small by design.** It does not yet select detailed workouts, enforce physiologically validated limits, forecast real performance, adapt mesocycles, or reconcile a persisted training plan after reboots. Its strength and running dimension handling is mostly structural.
4. **Status can be granular:** passing a case such as E004:MD10 confirms unknown capability is represented differently from known data. It does not prove accurate ability estimation, progression, or selection of a particular exercise. Pass/fail is for the *listed frozen assertions*, not every natural-language premise.
5. **Approval ledger is in memory.** Atomicity and rollback under SQLite, interruptions, sync conflicts, and multiple clients remain open; the 10 tests are a starting point, not database qualification.
6. **No real source ingestion:** unavailable local Python lab, real FIT/GPX/TCX records, Garmin wearable data, and private synthetic dataset are not part of this research branch.

## 4. Next staged validation, not production integration

**8C-1 — independent adversarial review:** Generate new unseen synthetic athlete/time/sensor histories from a separate scenario generator, independently pre-register expected invariants, and re-evaluate B0 vs H1 without tuning on the held-out set. Measure pass rate *and* cases where H1 changes a decision unnecessarily; manually inspect the 14 behavior disagreements for false positives.

**8C-2 — actual existing local lab adapter:** Only after source is made accessible privately, inspect its actual policies, schemas, frozen manifest and baseline tests; port an adapter rather than assuming the new B0/H1 are the same policy. Preserve the protected original dataset and private activity recordings.

**8C-3 — persistence and approval tests:** Add a separate stateful persistence stress harness, simulated crashes during approval/rollback, competing/stale versions, and repeated restart idempotence. These are currently NOT RUN on SQLite/Android.

**8C-4 — add withheld high-mileage and lower-mileage conditions:** Preserve running threshold work-step dose, easy running minutes, heat/humidity/route confidence, long-run capacity and strength overlap. Train/test temporal split only if a learned model is pursued.

**8C-5 — real longitudinal assessment (later):** Repeated road easy-speed at matched HR under comparable environmental and load conditions; supported threshold performance and durability. This requires explicit permission, transparent uncertainty and meaningful follow-up; simulator output alone cannot establish improvements.

## 5. Boundaries and handoff

All work is isolated to `research/validation/`, this research note, and a dedicated GitHub Actions workflow in **draft PR #34**. Do not merge or modify production Engine 1.0/1.5, Trackside PR #36, the local Python lab, or any protected dataset. Keep existing daily 09:00 Manila Runna/Garmin research automation; do not duplicate.

**Next resumable prompt:** Inspect draft PR #34 current head and latest successful 8B CI report; review the `research-policy-comparison.json` artifact; then continue **8C** by independently reviewing 14 B0/H1 action disagreements and adding held-out tests. Maintain separate toy, new-candidate, local-lab and Android-validation statuses.
