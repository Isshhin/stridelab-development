# CURRENT — Batch 8E (2026-11-03)

[**Full Batch 8E source-integrity and workout-change report**](../../docs/research/batch-8e-source-attestation-and-explicit-workout-revision-contract-2026-11-03.md). [**Verified GitHub Actions run 41588077190**](https://github.com/Isshhin/stridelab-development/actions/runs/41588077190): 109 Python tests PASS (0 failures) at commit `f9d592a`. Previous distinct structural suites remain 55 B0/H1 PASS/3 forecast BLOCKED, 288 adversarial PASS and 4 research multiweek profiles PASS.

New research-only boundaries: `source_link_gate.py` refuses uploaded self-claimed session IDs and requires separately signed attestations; `week_change_contract.py` makes versioned, non-auto-applied workout or calendar proposals and enforces declared day/time/dose constraints; `proposal_authority.py` binds signed source observations to proposals rather than trusting a caller's "verified" label; `adapter_probe.py` reports original local Athlete Simulation Lab `BLOCKED_SOURCE_NOT_CONNECTED`. New `test_*.py` files cover forged links, stale evidence, unauthorized increases, missing target days, approval/rollback, and integration. 

**This is not provider account/OAuth verification, athlete authentication, adaptive training efficacy or production Android integration.** The proposal authority intentionally blocks real athlete requests until an authenticated app boundary exists. Signing keys and test data are synthetic. PR #34 stays draft/unmerged.

Run all existing tests with `python -m unittest discover -s research/validation -p 'test_*.py' -v`. The separate adapter-probe JSON is included in the Actions artifact.

---

# Batch 8D — current checkpoint

[GitHub Actions verification](https://github.com/Isshhin/stridelab-development/actions/runs/41585527016): four synthetic profiles passed over 32 athlete-weeks; 66 unit tests passed. Research B0/H1 frozen cases remain 55 structural passes, three predictive cases blocked.

[Full Batch 8D report](../../docs/research/batch-8d-multiweek-event-reconciliation-and-policy-lifecycle-2026-11-03.md).

New files: `multiweek_reconciliation.py`, `multiweek_runner.py`, `test_multiweek_reconciliation.py`, `test_multiweek_runner.py`. The runner checks source replay, missing-versus-skipped observations, partial interval work, conflicting upload identities, plan approval and rollback. Cross-source identity links are synthetic assumptions, not a verified production authentication design.

Run the multiweek scenario suite with `python research/validation/multiweek_runner.py --output research-multiweek-results.json`. GitHub Actions also uploads this JSON. Results test accounting and audit invariants, not coaching effectiveness. Existing local lab and Android Engine 2.0 remain untested. PR #34 stays draft/unmerged.

---

# CURRENT — Batch 8C cloud validation (2026-11-03 Asia/Manila)

**Latest verified Actions result:** [run 41583816601](https://github.com/Isshhin/stridelab-development/actions/runs/41583816601), 288/288 new seeded adversarial structural checks passed across 24 cohorts, 42/42 unit/negative-control/persistence tests passed, and the earlier 58-case new B0/H1 frozen comparison remains at 55 structural PASS / 0 FAIL / 3 BLOCKED for uncalibrated predictions. All 14 B0/H1 action differences are individually enumerated and remain unvalidated for coaching benefit.

**[Batch 8C report and all 14 decision reviews](../../docs/research/batch-8c-heldout-adversarial-sqlite-crash-and-decision-audit-2026-11-03.md).**

Additional modules: [heldout_runner.py](heldout_runner.py) produces seeded synthetic adversarial fixtures independent of the original fixed 58, [difference_review.py](difference_review.py) records B0/H1 decisions and their limitations, and [sqlite_ledger.py](sqlite_ledger.py) plus [test_sqlite_ledger.py](test_sqlite_ledger.py) test **temporary research SQLite files** including abrupt process termination and reopening. This does not validate Android Room or the user's separate local Athlete Simulation Lab.

Reproduce all lanes on a copy of the research branch:

```bash
python -m unittest discover -s research/validation -p 'test_*.py' -v
python research/validation/runner.py --output research-validation.json
python research/validation/policy_runner.py --output research-policy-comparison.json
python research/validation/heldout_runner.py --seed 82603 --per-cohort 12 --output research-heldout-results.json
python research/validation/difference_review.py --output research-decision-differences.json
```

All four JSON artifacts are attached to the Actions workflow run. **No real athlete performance improvement, injury prevention, protected athlete activity processing, actual Python lab policy validation, or production Engine 2.0 implementation is established.** Keep PR #34 draft and unmerged.

---

## Historical Batch 8B/8A information

# CURRENT — Batch 8B research policy validation

**Verified CI:** [B0/H1 comparison run 41582744370](https://github.com/Isshhin/stridelab-development/actions/runs/41582744370) reported **55 structural PASS, 0 FAIL, 3 BLOCKED**, and **14 scenarios where B0 and H1 propose different actions**. **21 negative-control/harness/revision tests passed.** These are new, independent *research prototype* policies, not the original local Athlete Simulation Lab or Android Engine 2.0.

**Read the scope and limitations:** [Batch 8B report](../../docs/research/batch-8b-frozen-B0-H1-comparison-and-revision-validation-2026-11-03.md).

## Executable research modules

| Module | Purpose |
| --- | --- |
| [policy_candidates.py](policy_candidates.py) | Newly written distance/capacity B0 and type-aware conservative H1 selection candidates; no proprietary coefficients or measured performance forecast |
| [frozen_policy_cases.json](frozen_policy_cases.json) | 58 frozen synthetic paired inputs and 157 independent structural expectations |
| [policy_runner.py](policy_runner.py) | Executes B0 and H1 on both sides of each pair; produces separate PASS / FAIL / BLOCKED, comparison actions and SHA256 provenance |
| [test_policy_runner.py](test_policy_runner.py) | Registry integrity and negative-control mutations for capacity violations/unapproved writes |
| [revision_ledger.py](revision_ledger.py) and [test_revision_ledger.py](test_revision_ledger.py) | Pure in-memory explicit proposal/approve/reject/rollback with optimistic version and non-destructive workout history |
| [contract_model.py](contract_model.py), [checks.py](checks.py), [runner.py](runner.py) | Prior Batch 8A *toy* contract suite, still independently run and reported separately |
| [GitHub Actions workflow](../../.github/workflows/research-engine2-contract-validation.yml) | Python 3.12 cloud execution and two JSON artifact reports |

Run without external dependencies:

```bash
python -m unittest discover -s research/validation -p 'test_*.py' -v
python research/validation/runner.py --output research-validation.json
python research/validation/policy_runner.py --output research-policy-comparison.json
```

**Important status distinction:** `research-validation.json` says actual pre-existing engine integration is blocked. `research-policy-comparison.json` evaluates **newly written** B0/H1 candidates and is not an execution of the user's local Python lab. Neither gives clinical injury safety or demonstrated running improvements; the three predictive-difficulty cases remain blocked. All plan changes are proposals and require explicit approval. The ledger is in-memory only, not validated SQLite/Android transactional persistence.

---

## Historical Batch 8A toy-harness documentation (retained for provenance)

# StrideLab Engine 2.0 — Isolated Cloud Validation Harness (v0.1)

**Research only. Not a production coaching engine.** This package runs on GitHub-hosted Ubuntu using Python 3.12 and the standard library. It does not depend on or modify a user PC, Android app, live athlete data, or the independent local Python Athlete Simulation Lab.

## What currently runs

- [Canonical E003 registry](../../docs/research/experiments/experiment-003-load-policy-counterfactuals.csv) — 18 load-policy test specifications, with **18 explicitly implemented synthetic contract checks**.
- [Canonical E004 registry](../../docs/research/experiments/experiment-004-multidiscipline-coordination.csv) — 22 cross-domain specs, with **22 synthetic contract checks**.
- [Canonical E005 registry](../../docs/research/experiments/experiment-005-adaptation-state-and-load-metrics.csv) — 18 temporal/load specs, with **18 synthetic contract checks**.
- [Five independent harness self-tests](test_harness.py) — check case counts and deliberately corrupt skipped-run credit, session capacity, day-indexed TSB, and invalid GPS-based scoring to verify tests reject obvious errors.
- The GitHub workflow [research-engine2-contract-validation.yml](../../.github/workflows/research-engine2-contract-validation.yml) runs both kinds of checks on the isolated research branch/PR, with read-only repository permissions and a downloadable JSON artifact.

The [runner](runner.py) refuses missing/duplicate registry IDs, changed registry counts, unmatched case implementations, or unexpected source status. It outputs per-scenario identifiers, requirements, PASS/FAIL, explicit **BLOCKED real engine** status, canonical registry SHA-256 hashes, harness SHA-256 hashes, and the CI checkout SHA. This ensures a report can be tied to exact inputs and test definitions.

## What a successful result proves

It means **the toy synthetic contract model** in [contract_model.py](contract_model.py) satisfied the 58 explicit assertions and the harness rejected deliberately incorrect behavior in negative controls.

It does **NOT** prove:
1. Engine 1.0, 1.5, or 2.0 has passed any of those tests; no production/real engine adapter is connected.
2. The pre-existing independent Python lab's policies or datasets were executed. They are **not present in this research repository**.
3. H1 outperforms B0 in any realistic simulation; actual comparative policies, plausible frozen fixtures, and independent evaluators are not yet connected.
4. Medical safety, injury prevention, true fatigue/recovery estimation, or any running/strength performance advantage.
5. Runna E002 (0/16 authenticated tests) or Garmin Run Coach paired observations were reproduced.

**Reporting contract:** Display synthetic contract `PASS` separately from real engine `BLOCKED_NO_REAL_ENGINE_ADAPTER`. Never re-label a toy pass as a real Engine 2.0 validation. The canonical research registry entries remain `NOT_RUN` for *real candidate-engine executions*; these independent checks are a separate evidence lane.

## Running locally in a safe checkout

```bash
python -m unittest discover -s research/validation -p 'test_*.py' -v
python research/validation/runner.py --output research-validation.json
```

No secrets, athlete data, or installation are required. Runs in a checkout of this GitHub research branch using Python 3.12+. Generated `research-validation.json` is a machine-readable status report. The CI job also creates a summary and uploads it as `research-validation-results` for 30 days.

## Future actual-engine validation: explicit adapter gate

The *next separate engineering batch*, not part of v0.1:

1. Inspect a **real** versioned candidate B0 and H1 policy implementation, potentially from the existing local independent Athlete Simulation Lab if the user has explicitly made its source accessible. Never assume the older Windows paths resolve in GitHub Actions.
2. Define a **small typed adapter interface** taking an immutable synthetic athlete state, planned session steps, confirmed prior observations and a seed, and returning dose vectors, decisions, reason strings and proposed (not auto-applied) changes. Keep `source_record`, `policy_version`, `fixture_hash` in output.
3. Construct representative **numerical controlled pairs** for all 58 registry contrasts with independent acceptance checks. Separate decision contract checks from training-outcome forecasts. Register extra revision approve/reject/rollback tests for uncovered R24, plus sensor-null and source-absence edge cases. Do not merely call the toy oracle from both the evaluated policy and the test evaluator.
4. Run B0 and H1 on the exact same frozen fixtures. Report outcomes as `ENGINE_PASS`, `ENGINE_FAIL`, `BLOCKED_UNSUPPORTED_CASE` or `BLOCKED_MISSING_DATA`; add independent performance and structural regressions, holdout inputs, and honest discrepancies.
5. Only after a separate G1/G2 validation and eventual opt-in athlete data may anyone investigate improvements to matched-HR running pace, threshold performance, strength or skill ability. No structural test can demonstrate coaching effectiveness.

## Safety and project boundaries

This is isolated under `research/validation/` and a research-only CI workflow. The existing Android production code, Engine 1.0/1.5, Trackside work, private athlete activities and protected dataset are **untouched**. PR #34 stays **draft/unmerged** pending separate review. Its evidence and source-to-test map are documented in [Batch 7](../../docs/research/batch-7-engine-2-0-architecture-evidence-traceability-and-validation-gates-2026-11-03.md).

Research CI results can be viewed in the repo's **Actions** tab or in PR #34's checks. No GitHub Actions secrets are needed. The workflow is deliberately manual-dispatchable too; a push or PR change to the specified paths runs it automatically.

**Audit status:** The first cloud milestone is executable synthetic *contract testing*, not complete Engine 2.0 validation.
