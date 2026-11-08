# P2-X Foundation Qualification — Adversarial Review Wave 3

Date: 2026-11-08 (Asia/Manila)
Base: `fa4e308b1c0f96205b7d7e3a88c1d4ee5187150c`
Isolated branch: `research/p2x-roster-qualification-20261108`
Scope: **author-side independent-scope test-design review**, synthetic fixture data only. This is NOT an independent external review, source/plan authentication, formal acceptance, validated coaching policy, pilot, or Gate 2 permission.

## Reproduced findings

The prior `research/validation/p2x_lineage.py` used existential checks (`any(...)`) to establish that a declared P2-C planned opportunity or P2-D/P2-E session identifier existed, without verifying that the identifier was unique across the roster.

Six negative tests failed against the exact original blob `906d17c1641ac7a873efe0f776333ef84e944fbd`:

1. Duplicate target P2-C `opportunity_id` accepted even when two opportunities have the same ID.
2. Duplicate target P2-D `plan.sessions[].id` accepted.
3. Duplicate target P2-E `plan.sessions[].id` accepted.
4. A non-target P2-C opportunity without an `opportunity_id` accepted.
5. Duplicate non-target P2-D session ID accepted.
6. Duplicate non-target P2-E session ID accepted.

These are **plan-identity ambiguity** defects, not independently demonstrated fitness or security breaches. They allow a synthetic consistency declaration where the target cannot be uniquely identified. Two positive controls require distinct, valid non-target P2-C opportunities and P2-D/P2-E sessions to remain accepted.

## Correction and result

Added `_roster_ids(rows, id_field, label)` and applied it to all three discipline-native plan rosters before target presence/match checking. Rejects malformed/non-list rosters, blank/missing IDs, repeated IDs (even identical rows), and missing target references. Existing target-link mismatch errors remain stable.

Files:

- `research/validation/p2x_lineage.py`, exact hosted-tested Git blob: `e651bbca4d65c16a9c26cf547fe93cb56d95cd01`.
- `research/validation/test_p2x_qual_roster.py`, exact hosted-tested Git blob: `01bcabc32d731b2fa60354b3b64fef47e5b29f7c`.

Hosted regression: combined synthetic **463/463 PASS**, 0 failures, repeated independently under `PYTHONHASHSEED=7` and `PYTHONHASHSEED=213`. `python -m compileall -q p2x_lineage.py test_p2x_qual_roster.py` returns exit 0. The inherited 455 tests and 8 new cases were run in the same discovery execution; results are not merely arithmetically combined. This is sandbox execution, **not exact-head GitHub Actions CI**.

## Open qualification gates

- This is not an independent reviewer. An outside reviewer must evaluate the corrected exact head and issue their own recorded verdict and negative-test evidence. Author-side tests cannot substitute for that.
- Native snapshots, plan version, ledger projection, plan revision SHA and source record hashes still come from invented/caller-controlled values. They do not prove authoritative plan lineage, consent, source custody, rollback resistance or privileged athlete approval.
- P2-F v7 custody/authority correction closure remains the current normative reference for custody research, independently gated and not implemented here; historical canonical-protocol-v1 is superseded. This wave neither alters nor qualifies P2-F v7.
- Device qualification, approved participant/source admission, independently observed outcomes, real-world coaching effectiveness and Engine 2.x write authorization have not begun.
- Gate 2 remains CLOSED. Keep PR #34 OPEN/DRAFT/UNMERGED; no Engine 1.5, Android, Trackside or user-PC changes.

**Author-side checkpoint:** `P2X_ROSTER_REVIEW_WAVE3_SYNTHETIC_463_HOSTED_PASS_EXTERNAL_REVIEW_PENDING`.
