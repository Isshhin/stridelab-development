# StrideLab P2-X DEV-2 — Foundation review and unified synthetic plan/evidence lineage

Date: 2026-11-08 (Asia/Manila)
Parent: P2-X DEV-1 commit `0bb7440a4d90e18a6a777d81a734bbf87a8827ff`
Branch: `research/p2x-dev2-plan-lineage-review-20261108`
Scope: **research-only, invented observations; no Work/Codex/user PC, private athlete/provider/Garmin/FIT data, or production integration.**

## Batch 1 — Code and architecture review (author-side; independent acceptance still outstanding)

Compared the source contracts in `p2x_integration.py`, `p2x_dev1.py`, `p2c_exposure.py`, `p2d_shadow.py`, and `p2e_exposure.py`, together with the prior test and validation records. The review is separately scoped but **not an independent human or external expert signoff**.

Findings:

1. **High — P2-C native plan identity not established.** P2-C's DEV-0 exposure output carries a plan version, but lacks an authoritative native plan ID; matching versions in P2-D/E are insufficient. A synthetic mapping must never be promoted to authenticated linkage. Additive copy-on-write P2-C fixture migration is safe for experiment scaffolding but does not establish the generating planner's authority.
2. **High — Source ownership remains caller asserted.** P2-D/E source IDs, session IDs and fixture lineage roots can be consistent yet unauthenticated. DEV-1 SHA-256 hash chains and shadow receipts are unkeyed, mutable-fixture integrity checks, not digital signatures, proof of participant consent, independent rollback protection, or athlete approval.
3. **High — Shadow receipt recheck trusts the supplied review/context.** The DEV-1 receipt checker compares the caller-provided review and ledger projection; it does not independently redo the original cross-track decision at approval time or authenticate policy/plan issuers. Nothing may acquire a write capability from this flow.
4. **Medium — Cross-track event-ID collisions.** Different tracks can use identical native event IDs. An identity adapter must namespace ledger event IDs and refuse a cross-track alias collision instead of silently fusing or treating correlated signals as independent votes.
5. **Medium — Unknown evidence and invalidation must remain visible.** Corrections, withdrawals, source revocations, future/delayed observations, mismatched devices, and nonmatching source revisions must defer or reject. Passing author-side synthetic tests is not a real-data outcome, security or coaching-effectiveness validation.
6. **CI / review blocker — Exact-head GitHub Actions not green.** Prior GitHub-hosted Linux run [#42476987211](https://github.com/Isshhin/stridelab-development/actions/runs/42476987211) failed before runner assignment/job steps. Do not switch execution to the user's self-hosted Windows PC. The whole legacy research suite and the DEV-1/DEV-2 integrated combined tests have not yet been executed at a single exact head.

No original research source, Session G, Engine 1.5 or Android module was changed during this review. Independent expert acceptance is **pending**, not implied.

## Batch 2 — Unified synthetic identity and evidence contract

Added:

- `research/validation/p2x_lineage.py`
- `research/validation/test_p2x_lineage.py`

The new contract implements a strict synthetic-only manifest containing scoped athlete ID, plan ID, integer plan version, opportunity ID, UTC-normalized decision cutoff, caller-asserted revision digest, P2-C opportunity mapping, P2-D/E target session mapping, and separately namespaced native-to-ledger evidence bindings. Each binding carries a unique ledger event ID, native event ID, source ID, source revision, payload SHA-256 claim, observation/availability timestamps and lineage-root claim.

The adapter rejects mismatched P2-D/E native plan identities, incompatible P2-C versions, target-session and opportunity mismatches, cross-athlete records, conflicting native event IDs, unscoped aliases, duplicate evidence bindings, bad source revisions, payload digest disagreement, and future-outcome label injection. Delayed/future records and unmeasured records are excluded and defer; synthetic corrections, withdrawals or source-rights revocations defer. Reused lineage roots across tracks are explicitly reported as potentially correlated signals, never independent evidence votes.

**P2-C copy migration:** `migrate_p2c_fixture(snapshot, plan_id)` creates a new, unmodified-by-reference synthetic snapshot and applies a consistent plan ID to the P2-C snapshot, its opportunities and its events, rejecting foreign existing IDs or version mismatches. The existing P2-C engine code is intentionally unmodified. Merely adding a matching ID to one record fails. Even a completely consistent migrated fixture returns `SYNTHETIC_NATIVE_ID_CONSISTENT_UNAUTHENTICATED`, not a verified real-world plan authority.

The optional DEV-1-style ledger projection permits structural correlation of *caller-supplied* active record identity/digest with the declared bindings; without it the result is `DEFER_LEDGER_PROJECTION_ABSENT`. This is not a cryptographic attestation, independent validated projection, or authentication method. The contract accepts only invented fixtures and does not call or replace the native DEV-0 review pipeline. **A separately tested live adapter into the existing code and independently anchored source/plan identity are still pending.**

All outputs retain `safe_to_auto_apply=false`, `can_write_plan=false`, `authenticated_athlete_approval=false`, `coaching_authority=NONE_SYNTHETIC_RESEARCH`, and `gate2=CLOSED`.

### Hosted validation

An isolated, non-user-PC Python runtime executed the new source/test files with:

- `python -m unittest -q test_p2x_lineage`: **50 passed**, no failures/errors.
- Repeated with `PYTHONHASHSEED=7`: **50 passed**.
- Repeated with `PYTHONHASHSEED=213`: **50 passed**.
- `python -m compileall -q p2x_lineage.py test_p2x_lineage.py`: PASS.
- Exact tested bytes match Git blob IDs: module `e5b8140980629eb46a7999ab2bce2f8947806828`, tests `b03128397e307102326d17d9e7fd54b1b0b638b7`.

The existing 340-case DEV-0 combined suite and 50 DEV-1 tests were **not rerun together with these 50 new tests**. No claim of 440 passing tests in one run is authorized. No GitHub Actions CI pass or independent security audit is claimed.

## Next stages requested, not executed in this checkpoint

**Batch 3 — Combined exact-head regression and allowed CI:** execute P2-A/C/D/E/X DEV-0, DEV-1 and DEV-2 on one exact head. Resolve the prior GitHub-hosted runner allocation error without using the user's PC; record all failures and make only bounded fixes.

**Batch 4 — Foundation acceptance checkpoint:** independent code/conformance review of all contracts, evidence reconciliation and limitations, freeze a tested head only after no critical findings remain. Real-data custody, source rights and Gate 2 stay separate and CLOSED. Do not merge PR #34 or connect to Engine 1.5 without separate approval.

Author-side status: `P2X_DEV2_BATCH1_REVIEW_AND_BATCH2_SYNTHETIC_IDENTITY_50_PASS`. Independent acceptance NOT granted.
