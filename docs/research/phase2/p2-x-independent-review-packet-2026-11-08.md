# P2-X External Independent Review Packet — Current Synthetic Foundation

Date: 2026-11-08
Candidate branch: `research/p2x-integrity-wave4-20261108`
Status: `READY_FOR_INDEPENDENT_REVIEW` in the narrow sense that the author-side synthetic code and test evidence are packaged. **Not accepted, not approved for real-world data and not Gate 2 ready.**

## Review boundary and preservation

- Repository: `Isshhin/stridelab-development`.
- Review the entire descendant synthetic P2-A/C/D/E/X lineage ending at this branch, including new code and tests. Explicit focus: `p2x_lineage.py`, `p2x_dev1.py`, `p2x_integration.py`, plus P2-C native snapshot/migration and all associated tests.
- Keep original PR #34 OPEN, DRAFT, UNMERGED; do not modify its branch. Do not touch Android, Trackside, Engine 1.5, Garmin, P2-F v7 design or production plans.
- Reviewer must not assume prior self-review is an external acceptance. The previous author's test cases prove only their scoped behaviors.
- No live source, private/provider/participant records, target workstation, TPM/TBS, keys, credentials or self-hosted Windows runner. Synthetic fixtures only; no plan execution or pilot.

## Independent review questions (all need explicit evidence)

1. Do source and athlete namespaces prevent identity confusion when events share IDs or differ only in casing/normalization?
2. Does the event index reject both conflicting and identical duplicates? Are deduplication/reconciliation responsibilities clearly separated?
3. Can an eligible event be omitted or a ledger active entry inserted without detection? Can partial/missing/future events create misleading training exposure or apparent improvement?
4. Are every plan and opportunity ID, plan version, source revision, as-of timestamp and availability timestamp checked on all native and manifest paths?
5. Are corrections, withdrawal histories and revoked-source lists required and complete rather than silently defaulted, including historical as-of projections?
6. Does the review reject outcome data or future policy-score labels that were unavailable at decision time?
7. Can the caller forge both manifest and ledger digest values so all structural tests pass? If yes, verify the output consistently says **unauthenticated**, with no athlete approval or write capability.
8. Could any path return a training dose, selected action, clinical clearance, an approved proposal, or claim Gate 2 authority through malformed input?
9. Are existing DEV-1 hash-chain assumptions consistent with the *normative current* P2-F v7 custody correction closure without accidentally adopting superseded v1 text?
10. Which remaining source-to-plan authority and irreversible witness requirements are missing from real-data acceptance?

## Required independent evidence

- Exact reviewed commit SHA, branch and code blob hashes.
- Reproduction command, Python version/platform, complete test pass/failure counts, and novel adversarial tests written independently of existing fixtures.
- Each finding categorized by severity, minimal reproduction, affected files, whether code was modified (review-only should not modify), and explicit unresolved blockers.
- Explicit conclusion among `PASS_SYNTHETIC_CONFORMANCE_ONLY`, `CORRECTION_REQUIRED`, and `INCOMPLETE_EVIDENCE`; **never** extrapolate this to real-source/clinical Gate 2 acceptance.
- For GitHub Actions, exact SHA and runner/step logs on permitted **GitHub-hosted** infrastructure if available. If no runner assignment, report a distinct CI infrastructure blocker, not a source-test failure.

## Current author-side evidence

Wave 3: 463 combined synthetic tests passed.
Wave 4: 18 new tests; 15 fail-first on prior committed source, then 481 combined tests pass twice under two hash seeds on corrected source. Full details: `docs/research/phase2/p2-x-foundation-integrity-wave4-2026-11-08.md`.

The packet is **prepared for an external reviewer**, but no such review has been performed by this author.
