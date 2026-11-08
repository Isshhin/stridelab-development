# P2-X DEV-1 — Synthetic provenance and stale-review fencing

Date: 2026-11-08 (Asia/Manila)
Branch: `research/p2x-dev1-provenance-20261108`
Parent: P2-X DEV-0 isolated checkpoint `41b8e2f5d2b2239f846e8d0bcb0e5c4c4daec472`
Scope: **synthetic-only research contract**. No actual athlete, provider, FIT, Garmin or user-PC data; no Work or Codex. No production Android or Engine 1.5 changes. Does not authorize real source admission, keys, participant approval, or Gate 2.

## Batch 1 — Versioned synthetic evidence ledger

New `research/validation/p2x_dev1.py` validates a scoped, append-only fixture ledger bound to athlete ID, plan ID/version and opportunity. It uses contiguous sequence numbers, previous-event digests, event IDs, source IDs, strict offset-aware observed/available timestamps and SHA-256 content digests. Old decisions include only records available at the decision cutoff. Repeated ID, malformed values, hash alteration, source/plan mismatch and backdated appends fail closed.

**Critical limitation:** These are caller-supplied invented fixtures. A SHA-256 hash without external, authenticated trust anchors is not a signature, verified source custody, lawful authorization or rollback proof.

## Batch 2 — Correction, withdrawal and source-rights fences

Events support ORIGINAL, CORRECT, WITHDRAW and REVOKE_SOURCE. Corrections must target the current entry from the same source. Superseded entries no longer count as active. Withdrawn entries are removed from the active synthetic projection. Source-rights revocation removes that source's active entries, and another entry from a revoked source cannot silently reinstate its rights.

Any corrected or withdrawn evidence triggers a separate defer/reconciliation category rather than an apparently safe training-plan change. Future changes remain invisible in historical projections, while becoming visible at later receipt checks.

## Batch 3 — Non-authorizing shadow-receipt fences

The module takes the previous `p2x_integration.audit` result through `inspect_bundle(bundle, context)`, retaining its plan/athlete/decision-time boundaries. A new shadow receipt captures plan identity/version, policy version, decision cutoff, previous audit digest and ledger tip. A changed plan, policy, reviewed audit, appended evidence, correction or withdrawal invalidates the receipt.

**The receipt is NOT an athlete approval.** It is an unkeyed synthetic replay checksum and never carries a write capability. Validity means only that the invented review fixture appears unchanged. `can_write_plan=false`, coaching authority NONE, and Gate 2 CLOSED remain mandatory. P2-C's missing independently established native plan ID remains unresolved; even if an asserted P2-C plan ID happens to match, lineage stays unverified.

## Batch 4 — Hosted adversarial tests and checkpoint

New `research/validation/test_p2x_dev1.py` contains **50 unit tests**, all passing in an isolated hosted Python environment. Validated behaviors include:

- chain, sequence, source, plan and athlete scoping; future availability and reproducibility;
- correction chains, noncurrent target refusal, cross-source correction refusal, source-rights withdrawal and no automatic restoration;
- explicit reconciliation deferrals and inability to upgrade unverified P2-C plan lineage;
- stale shadow receipts after corrections, source revocation, new evidence, policy changes, plan-version changes and altered reviews;
- unmodifiable input, timestamp normalization and absence of any write authority.

Hosted verification: `python -m unittest discover -s ... -p test_p2x_dev1.py -q` **50/50 PASS**, repeated with `PYTHONHASHSEED=7` and `PYTHONHASHSEED=213`, also PASS. `python -m compileall -q` PASS. Uploaded Git object hashes match exact tested bytes:

- `p2x_dev1.py`: `d5d335f3e22aebd81eb34e9fd84fb647663d7900`
- `test_p2x_dev1.py`: `1e944394eae72864376fd6564adcab816ab8ce99`

The adapter's import into the previous P2-X module is tested using a controlled synthetic test double. **The original 340-case P2-A/C/D/E/P2-X suite has not been rerun on this branch**, nor has an actual authenticated plan-lineage bridge been implemented. These new 50 passing tests must not be added to previous counts and described as one combined run.

**GitHub Actions:** Prior GitHub-hosted Linux [run #42476987211](https://github.com/Isshhin/stridelab-development/actions/runs/42476987211) failed before any job steps or runner allocation. That infrastructure blocker is not considered fixed; no attempt will be redirected to the user's PC. No independent expert/code/security review has approved DEV-1.

## Verdict and roadmap gate

Author-side status: `P2X_DEV1_SYNTHETIC_PROVENANCE_RECEIPT_FENCE_50_PASS`.

**Not promoted:** production authority, real encrypted custody, durable rollback resistance, source attestation, participant approval, automatic coaching, physiological prediction, effectiveness, Gate 2, or integration into Engine 1.5. Existing Session G, P2-F, P2-B DEV-0, and PR #34 remain unaffected.

Next batches: (1) independent cross-track source/code/security review and corrections; (2) genuinely authenticated plan/source authority architecture and P2-C native plan ID migration, research-only; (3) combined exact-head tests and CI on permitted non-PC infrastructure; (4) revalidate the data-rights, device, stewardship and Gate 2 prerequisites before any real-world pilot.
