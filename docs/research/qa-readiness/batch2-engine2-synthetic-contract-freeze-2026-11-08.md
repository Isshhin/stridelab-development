# StrideLab QA Readiness — Batch 2: Engine 2.x Synthetic Contract Freeze

Date: 2026-11-08 (Asia/Manila)
Scope: **candidate-interface / acceptance-criteria freeze for November 12 validation**.
Base implementation: `6142d7a8f8976f9ea4647850ff9e6ea51ff289df`
Preceding Batch 1: `docs/research/qa-readiness/batch1-foundation-consolidation-2026-11-08.md`.

**Interpretation of freeze:** Do not expand synthetic features or casually rename research interfaces while preparing QA. The candidate is **not independently accepted, not a production ABI, not a normative P2-F protocol revision, and not ready for Gate 2**. Any safety defect uncovered by verification overrides the administrative freeze and requires an isolated correction and new reviewed SHA.

## 1. Frozen candidate modules and boundary

| Boundary | Entry points / schema | Role and permitted result |
|---|---|---|
| P2-A | `p2a_state.py`, `p2a_comparison.py` | Invented athlete-state and comparison; no clinical diagnosis |
| P2-C | `p2c_exposure.py`, `p2c_progression.py` | Invented progression/re-entry; no authenticated native plan ID |
| P2-D | `p2d_shadow.py` | Proposal/review **shadow**, not executable plan edit |
| P2-E | `p2e_exposure.py`, `p2e_coordination.py` | Multi-discipline exposure coordination under synthetic scenarios |
| P2-X DEV-0 | `p2x_integration.audit`; `p2x-dev0-cross-track-v1` | Same-athlete/cutoff/plan-version consistency, restrictions and deferrals; no action |
| P2-X DEV-1 | `p2x_dev1.ledger_state`, `inspect_cross_track`, `inspect_bundle`, `issue_shadow_receipt`, `check_shadow_receipt`; `p2x-dev1-fixture-provenance-v1` | Invented append-only correction/withdrawal chain and stale shadow-receipt fencing. Hashes are unkeyed |
| P2-X DEV-2 | `p2x_lineage.assess`, `migrate_p2c_fixture`; `p2x-dev2-synthetic-lineage-v1` | Caller-supplied plan/evidence manifest, byte-digest checks, roster identity and ledger completeness; source authentication NOT established |

Frozen **exact code blob** examples at implementation candidate:
- `p2x_integration.py`: `df8ea9e189ae2ada60f3d2f2ad309e7bfa86dac6`
- `p2x_dev1.py`: `d5d335f3e22aebd81eb34e9fd84fb647663d7900`
- `p2x_lineage.py`: `2c6a768d5eb3b6c56d2b3f9c3644ad17ad8e4b04`
- `test_p2x_lineage.py`: `9b381ea94afc61c61894e6c6594c9f56edd4af37`
- `test_p2x_qual_roster.py`: `01bcabc32d731b2fa60354b3b64fef47e5b29f7c`
- `test_p2x_qual_boundaries.py`: `4f10509899497424885029a2c6cc4e425fa20145`

Original supporting research files on earlier branches must be verified against this **final candidate tree**, not assumed solely from historical reports.

## 2. Contract invariants (must remain true)

| ID | Required behavior | Fail/accept evidence |
|---|---|---|
| X-01 | Synthetic-only: every admitted snapshot and manifest explicitly identifies synthetic provenance | Missing/false synthetic flag: reject, never silently treat as real-data admissible |
| X-02 | Athlete, plan, opportunity, source and session scopes must agree; avoid version-only cross-plan conflation | Foreign athlete/plan/session/revision: reject; unauthenticated P2-C plan migration never upgrades identity authority |
| X-03 | Every time is valid and offset-aware; decision cutoff prevents future observations/outcomes leaking backward | Backdated availability, malformed clock, unavailable-at-cutoff records: reject or defer according to contract; explicit future label keys rejected |
| X-04 | Native event rows and planned-session/opportunity roster IDs are unique within their respective namespaces | Byte-identical and conflicting duplicate rows: reject; missing/duplicate planned roster IDs: reject |
| X-05 | Manifest's native event digests are recalculated from the canonical synthetic native bytes | Changed event values/quality/revisions without consistent bindings: reject |
| X-06 | All eligible native rows are represented, all active ledger rows are reconciled | Unbound rows, omitted eligible evidence, extra active ledger events: defer; never claim complete consistency |
| X-07 | Corrected, withdrawn or revoked evidence is explicit and cannot silently turn into a normal completed session | Missing correction/withdrawal/revocation lists: reject; real disposition events: defer reconciliation |
| X-08 | Ledger append sequence, predecessor digest, exact duplicate event ID, availability ordering and source revision semantics remain checked | Gap/reorder/rollback attempt: reject or defer; never supply custody authority |
| X-09 | A shadow review/receipt is not athlete approval; modified plan, policy, ledger tip, cutoff, or review invalidates prior receipt | Stale receipt -> stale/defer; **can_write_plan=false** always |
| X-10 | Safety/stop/restriction data takes precedence over ordinary training progression or convenience scheduling | Hard stop, unknown/missing safety evidence -> abstain/review; no automatically authorized dose or medical clearance |
| X-11 | Original inputs are not modified by assessment; P2-C fixture migration produces a copy | Pre/post snapshots equal unless returned new copy is separately inspected |
| X-12 | Unkeyed fixture hashes and caller-asserted plan digests are described as **consistency only** | Must retain `source_authentication=NOT_AUTHENTICATED` and `gate2=CLOSED`; do not report external proof, lawful rights or operational trust |

**If an expected negative-case category changes**, record the precise old/new behavior and reviewer approval; do not make tests green by weakening stop rules or adding catch-all success handling.

## 3. Validation command and evidence checklist

Execute only when a full permitted environment is available (e.g. on the usage reset). Run from the root of a clean checkout of the **implementation candidate SHA**. Two explicit Linux example commands:

`PYTHONPATH=research/validation PYTHONHASHSEED=7 python -m unittest -q test_p2a_state test_p2a_comparison test_p2c_wave test_p2d_shadow test_p2e_wave test_p2x_integration test_p2x_dev1 test_p2x_lineage test_p2x_qual_roster test_p2x_qual_boundaries`

`PYTHONPATH=research/validation PYTHONHASHSEED=213 python -m unittest -q test_p2a_state test_p2a_comparison test_p2c_wave test_p2d_shadow test_p2e_wave test_p2x_integration test_p2x_dev1 test_p2x_lineage test_p2x_qual_roster test_p2x_qual_boundaries`

Expected **historical** combined count at tested candidate: **481 tests**. Do not claim this count will remain unchanged if source/test files are added. Exact-head CI must provide executed steps, runner identity, test count, failures, error log, SHA and a retained artifact. The prior 481 number is from two separate hosted Python author-side executions, **not GitHub Actions success**.

A distinct broader research suite may include P2-F, Windows-symlink, policy and external artifact tests; it must be separately defined and its outcome labeled. Android QA has its own CI and emulator checks and cannot substitute for this suite.

Decision classes: **PASS only for the executed contract and exact head**; **FAIL** for an executed failing assertion; **BLOCKED** if no runner, missing rights or unavailable evidence; **NOT_RUN** if deliberately deferred. Never count BLOCKED or NOT_RUN as PASS.

## 4. Acceptance levels / stop rules

- Level A, author-side synthetic: 481 previously passed, **no re-run in this docs-only Batch 2**.
- Level B, independent synthetic conformance: **PENDING** until distinct reviewer supplies SHA, original adversarial probes, findings disposition and explicit verdict.
- Level C, exact-head GitHub-hosted Linux reproducibility: **BLOCKED**; hosted research workflows failed before job steps; another Android workflow ran successfully, so GitHub-wide outage and billing explanation remain unconfirmed.
- Level D, source/plan authorization and P2-F preflight: **NOT AUTHORIZED**. P2-F v7 latest design review is **acceptable only for separate preflight authorization review**; collector never to be executed by these batches.
- Level E, real athlete physiology/coaching study and Gate 2: **NOT READY / CLOSED**.
- Level F, production Android Engine 2.x integration: **NOT AUTHORIZED**; Engine 1.5 stays active and rollback remains intact.

Any safety-critical failure, evidence source mismatch, unsigned/unauthorized plan attempt, use of real/provider/private data without rights, or stale approval requires **stop / documented defer**, not conditional success.

## 5. Change control after freeze

1. Change the source only for reproduced defect or explicitly approved protocol/QA need.
2. Put each correction on its own isolated branch based on the actual currently reviewed commit and record both commit and blob SHAs.
3. Add fail-first regression, rerun appropriate suite, update this freeze if schema/acceptance criteria change.
4. Keep author-side implementation, independent review, reproducible CI, source admission and production authorization as **separate gates**. A passing gate never automatically unlocks the next.
5. No edits to original Engine 2.x PR #34, Engine 1.5, Garmin production syncing, Android UI, frozen Session G or P2-F v7 normative text in this preparatory branch.

**Batch 2 verdict:** `B2_SYNTHETIC_CONTRACT_CANDIDATE_FROZEN_FOR_QA_PLANNING_ONLY`.
