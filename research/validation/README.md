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
