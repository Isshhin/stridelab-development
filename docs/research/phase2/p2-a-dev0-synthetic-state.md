# P2-A DEV-0 | Synthetic athlete-state interpretation | Batches 1–3

Date: 2026-11-08 (Asia/Manila)
Scope: **research-only synthetic contract and adversarial tests**. This is NOT Engine 1.5, a production Engine 2.0 implementation, real-data admission, a calibrated readiness model, or Gate 2 evidence.

## Objective and dependencies

The frozen Session G P2-A track requires decision-time observations and independent follow-up before validating predictions. The earlier E005 synthetic contract checks confirm structural assertions but explicitly retain NOT_VALIDATED_NO_MODEL. This additive DEV-0 wave implements a separate, deliberately **descriptive** interpretation contract. It neither modifies E005/B0/H1 nor consumes P2-B's unadmitted measurements. No participant, provider, GPS, FIT, credential, device, or local workstation evidence was used.

## Implementation batches

1. **Batch 1 — contract and availability.** \`research/validation/p2a_state.py\` takes a supplied synthetic snapshot with a scoped athlete ID, offset-aware decision-time cutoff, versioned observation identity, event/source/protocol metadata, observed and available timestamps, measurement status and quality. Exact duplicate event IDs count once. Conflicting duplicate IDs and cross-athlete rows reject the entire snapshot. Records unavailable by the cutoff, backdated availability, non-measurements, suspect quality, missing provenance and missing values are excluded with explicit reasons. The function has no I/O and does not mutate input.
2. **Batch 2 — independent descriptive dimensions.** It returns \`fitness_trend\` (actually registered comparable *benchmark-speed* observations, not inferred fitness), \`recent_fatigue\` (fresh athlete self-report only), \`workout_tolerance\` (the last two registered comparable quality-session reports), and \`evidence_quality\`. A benchmark comparison requires at least three measurements on three UTC dates under one registered comparison group, device epoch and protocol, plus a positive registered detectable-change bound. Trend direction is reported only for monotonic observations beyond that bound; changes inside the bound are labeled within error, not stable. A quality-session group must also be registered and must match its device epoch and protocol. HRV/sleep remain context, not votes for readiness; favorable HRV never overrides reported fatigue. The synthetic 0.10 m/s bound and 24-hour check-in window in tests are invented fixture settings, not recommended biological thresholds.
3. **Batch 3 — adversarial cases.** \`research/validation/test_p2a_state.py\` covers empty/missing history, monotonic benchmark observations, mixed sessions, new device epoch, missing comparison registration, bad values, high subjective fatigue versus favorable HRV, stale and simultaneous contradictory reports, future outcomes, delayed uploads, duplicate identity, cross-athlete exposure, unverified quality, invalid clocks, permutation determinism, non-mutation and prohibition of autonomous prescriptions.

## Contract outputs and invariant

\`interpret(snapshot)\` returns \`schema=p2a-dev0-synthetic-state-v1\`, \`athlete_id\`, normalized \`as_of\`, three independent dimensions with explicit state/reason/evidence IDs, exclusion counts and context warnings. Regardless of input, successful returns include:

- \`prediction_status=NOT_VALIDATED_NO_MODEL\`;
- \`predicted_performance=null\`;
- \`proposed_workout_change=null\`;
- \`coaching_authority=NONE_SYNTHETIC_RESEARCH\`.

Unknown is never silently interpreted as zero, a skipped workout, recovery or proof of fitness decline. Prescribed training is never modified. A pattern in benchmark speed is not proven physiological adaptation.

## Validation and limits

Hosted Python 3 synthetic unit test run: **29 tests passed** after correcting two test-exposed contract defects (unregistered session comparisons and protocol mixing). This does **not** establish GitHub Actions exact-head success or independent expert review. The new branch was deliberately separated from PR #34's push-triggered self-hosted Windows workflow so no GitHub Actions job would run on the user's workstation.

This is an intentionally narrow oracle, not a general ingest API. The supplied observation and comparator registration are synthetic/trusted fixture inputs; there is no device authentication, source-rights assessment, lawful custody, real time-source proof, independently calibrated repeatability margin, longitudinal model fitting, athlete-outcome prediction, statistical validation, or external applicability. Three repeated synthetic benchmarks are a software precondition, **not** evidence that three real measurements establish fitness change. No thresholds here are authorized for athlete-facing use.

## Next milestone

**P2-A DEV-1 (research-only)** should develop and independently review a preregistered comparison of A: history-only, B: history+check-in, and C: optional wearable-augmented inputs. In synthetic-only work, test availability, identical denominators, abstention bookkeeping, source-lineage duplication and absence of future labels; do not assert predictive wins from invented outcomes. Real comparison, calibration, generalization or coaching usefulness remain blocked by P2-F purpose-specific data admission, P2-B repeatability where required, a signed track-specific Gate 2 manifest, and independent prospective outcomes.

PR #34 stays open, draft and unmerged. Frozen Session G, P2-B DEV-0, P2-F custody, Trackside, Android and Engine 1.5 remain unchanged.
