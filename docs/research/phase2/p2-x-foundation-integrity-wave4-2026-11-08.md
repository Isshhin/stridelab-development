# P2-X Foundation Integrity Review — Wave 4

Date: 2026-11-08 (Asia/Manila)
Base: `4424d3dd33993354bff5d1e8dbb4a90d4766714a`
Isolated branch: `research/p2x-integrity-wave4-20261108`

## Scope and authority

This is a new *author-side* adversarial software-contract review and correction of the P2-X DEV-2 synthetic lineage adapter. It is **not independent external validation**. No provider, athlete, FIT/Garmin, clinical or private data was admitted. P2-F custody authority and PR #34 are unchanged. No operational permissions, credentials, athlete approvals or plan writes were created.

## Defects reproduced against the previous exact Git blob

Previous `research/validation/p2x_lineage.py` blob: `e651bbca4d65c16a9c26cf547fe93cb56d95cd01`.

1. **HIGH — Byte-identical duplicate native evidence allowed.** The per-track event index rejected an event ID only when its repeated payload differed. Identical duplicate native events from each of P2-A, P2-C, P2-D, P2-E were silently collapsed and a synthetic consistency verdict could still be produced. Native event multiplicity matters even when payloads match; verified ingestion must handle deduplication explicitly.
2. **HIGH — Missing ledger disposition lists treated as empty.** Removing any of `withdrawal_event_ids`, `revoked_sources`, or `corrected_event_ids` from a caller-supplied projection left the synthetic consistency verdict unchanged. The DEV-1 projector's actual contract includes all three.
3. **MEDIUM — Future-outcome and policy-win label filter differed from the cross-track input contract.** The DEV-2 filter rejected several future labels, but still allowed `outcome`, `future_performance`, `model_accuracy` and `best_policy` into the synthetic review bundle. This is a label-leakage risk in decision-time evaluation, not evidence that a model was trained.

**Fail-first proof:** The 18 new tests were run against the old source; **15 tests failed** exactly where the missing rejections were expected, and 3 controls passed. This was verified in a separate isolated hosted directory. The previous full foundation test suite passed **463/463** before these new negative tests were added, showing the coverage gap.

## Corrections

Only `research/validation/p2x_lineage.py` changed:

- Reject *all* duplicate native `event_id` rows within a track. Keep the previous distinct conflicting-payload error and add `DUPLICATE_IDENTICAL_NATIVE_EVENT_ID` for byte-identical duplicates. The same ID string may still appear in separate track namespaces with independent bindings.
- Require the three DEV-1 ledger disposition collections explicitly as lists of nonempty, unique identifiers; missing/null/string/malformed/duplicated values raise `LEDGER_DISPOSITION_LIST_INVALID`. A valid empty list is allowed only if explicitly provided.
- Bring input-time forbidden label keys into parity with the existing P2-X cross-track comparison contract. Presence of `outcome`, `future_performance`, `model_accuracy` or `best_policy` now raises `FUTURE_OUTCOME_LABEL_FORBIDDEN`.

Added `research/validation/test_p2x_qual_boundaries.py`: 18 tests (15 newly demonstrated failure cases, plus controls for conflicting duplicates, cross-track ID namespacing, and no-authority output).

## Hosted regression and source identities

Exact Git blob SHA-1 for code tested and uploaded:
- `research/validation/p2x_lineage.py`: `2c6a768d5eb3b6c56d2b3f9c3644ad17ad8e4b04`
- `research/validation/test_p2x_qual_boundaries.py`: `4f10509899497424885029a2c6cc4e425fa20145`

Hosted Python 3.13.5 combined unittest discovery over inherited P2-A/C/D/E/X modules plus Wave 3 and Wave 4 tests:
- `PYTHONHASHSEED=7`: **481/481 PASS**, 0 failures, 0 errors.
- `PYTHONHASHSEED=213`: **481/481 PASS**, 0 failures, 0 errors.
- Python `compileall`: PASS.

These are author-side synthetic regression runs, **not exact-head GitHub Actions or an independent software/security verdict**.

## CI blocker: evidence only

[Research CI run #42482594419](https://github.com/Isshhin/stridelab-development/actions/runs/42482594419) concluded failure before assigning a runner or running any job step. GitHub's job-steps endpoint returned an empty list and the job log returned a missing redirected blob; combined commit statuses did not reveal a rejection reason. A separate Android GitHub-hosted workflow succeeded during the same period, so the evidence does not establish a GitHub-wide outage. Repository owner Actions policy/billing/runner diagnostics were not accessible through the available GitHub connector. **No exact cause or CI resolution is claimed**, and no repeat job was triggered just to recreate the same unexplained failure. The user's PC and Windows self-hosted runners must not be used as a workaround.

## Required external acceptance and next actions

An independently scoped reviewer should evaluate the complete exact-head code and the fresh 18-case negative-test packet, then issue a documented verdict of PASS / CORRECTION_REQUIRED / INCOMPLETE. They should test an untrusted caller rewriting both plan and ledger digests, historical replay and rollback, P2-C migration misbinding, source revocation masking, admission/approval authority, and multi-source identity collisions.

No reviewer has yet provided such a verdict. The P2-F v7 protocol remains separately gated. None of these synthetic fixtures proves authenticated source, athlete or plan custody, real-world recovery estimates, coaching effectiveness, or clinical clearance. **Gate 2 stays CLOSED and no production writes are allowed.**

**Author-side checkpoint:** `P2X_WAVE4_SYNTHETIC_INTEGRITY_481_HOSTED_PASS_EXTERNAL_ACCEPTANCE_PENDING`.
