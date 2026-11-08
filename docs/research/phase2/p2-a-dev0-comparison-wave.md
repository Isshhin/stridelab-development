# P2-A DEV-0 — Batches 4–6: synthetic comparison and foundation checkpoint

Date: 2026-11-08 (Asia/Manila)
Scope: isolated synthetic-only research branch `research/p2a-dev0-batches123-20261108`, building on Batches 1–3. PR #34's research head stays separate and unchanged. No personal/athlete/provider data, FIT, Garmin, workstation setup, Work, Codex or production integration.

## Batch 4 — Explicit feature-readiness comparator

The additive `research/validation/p2a_comparison.py` uses the previous `p2a_state.interpret` as its decision-time eligibility gate and evaluates the exact same synthetic opportunity denominator for three candidate **feature sets**, not fitted models:

- `A_HISTORY`: registered comparable synthetic benchmarks or quality-session history;
- `B_HISTORY_CHECKIN`: A plus an eligible fresh fatigue self-report;
- `C_HISTORY_CHECKIN_WEARABLE`: B plus a registered, sufficiently recent, traced HRV or sleep observation.

A record that lacks a required feature is `ABSTAIN` with a reason; it is never a negative athlete outcome. Per-opportunity results are sorted for deterministic replay. The implementation rejects duplicate opportunity identities for one athlete, cross-athlete source identities (through Batch 1's interpreter), altered duplicate events, naive clocks and any provided `outcome`, `target_outcome`, `next_session_result` or related future-label field anywhere in the input. Future or delayed observations cannot enter a historical decision. Each optional wearable input must match a synthetic registered family, device epoch, acquisition protocol, baseline ID, maximum age, source and lineage root. Correlated HRV/sleep samples sharing a lineage root are explicitly reported rather than treated as independent physiological corroborations.

The result contains **no numerical readiness, performance prediction, fitted model, winner, error metric or workout change**: `predictive_skill=null`, `best_candidate=null`, `coaching_authority=NONE_SYNTHETIC_RESEARCH`, `gate2=CLOSED`. `FEATURES_READY` means the synthetic feature contract is fulfilled; it is not an assertion that an algorithm is accurate, ethical, deployable, or clinically useful.

## Batch 5 — Hosted synthetic testing and reviewer limitations

New `research/validation/test_p2a_comparison.py` contains **40 deterministic synthetic tests** covering three candidate gates, identical denominators, abstention, chronology, stale uploads, future labels, duplicate event/opportunity IDs, cross-athlete boundaries, device/protocol/baseline changes, malformed values/registrations, missing lineage, correlated wearables, reproducibility, and refusal to infer future outcomes. All 40 passed in the hosted Python runtime on the exact source committed with this checkpoint. This is author-side execution, **not independent code review**, not GitHub Actions CI and not operational security evidence. Previous Batches 1–3 had 31 separate hosted tests; they were not re-executed as part of this new 40-case run.

Known intentional limitations and review questions:

1. A/B/C compare only feature availability. They have no predicted outputs, accuracy, calibration, error intervals, actual outcomes, intervention effects or substantive winner; **higher feature coverage cannot prove greater coaching quality**.
2. The history contract currently uses registered repeated benchmark/synthetic quality-session observations, not a validated general training-exposure history model. Sparse history causes abstention.
3. The supplied `registered_*` declarations are **invented, trusted fixtures**. They are not independently verified device baselines, lawful grants, source provenance or calibrated physiological margins. No actual sensor has been qualified.
4. The 24-hour freshness limits and other values in the tests are invented test parameters, not recommended thresholds. They must not be copied into the Android coach.
5. No independent review of this new implementation has occurred; failure on future adversarial review would require corrections and a new test run. No guarantee is made about arbitrary malformed non-JSON data, integration or external dependency behavior.
6. The research test run used an isolated hosted filesystem. Do not claim its result is exact-head GitHub CI. The branch is outside the main research workflow's push trigger specifically to avoid running the user's self-hosted `MARC` runner.

## Batch 6 — Foundation checkpoint decision

**Author-side milestone:** `P2A_DEV0_BATCHES_1_TO_6_SYNTHETIC_IMPLEMENTED_HOSTED_TESTS_PASS`.

Promotion to an independently accepted synthetic contract remains **pending a separately scoped code/conformance review**, followed by repeatable CI on infrastructure that does not use the user's PC. DEV-1 real predictive comparisons remain blocked by purpose-specific lawful P2-F acquisition/linkage, required P2-B measurement repeatability for matched-efficiency claims, independent future athlete outcomes, predefined usefulness/false-restriction margins, uncertainty/abstention accounting and track-specific Gate 2 approval. The frozen Session G baseline, Engine 1.5, Trackside/Android, P2-F custody work, P2-B DEV-0, existing research tests, and PR #34 remain unchanged.

## Next research work (not executed in this checkpoint)

Review new P2-A DEV-0 code independently, then design independent outcome-reference and model-ablation protocols using the frozen Session G requirements. P2-C progression/re-entry synthetic scaffolding can be planned independently without claiming that P2-A's descriptive features are predictive. No automatic training-plan modification is authorized.
