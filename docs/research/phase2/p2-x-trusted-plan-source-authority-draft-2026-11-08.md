# P2-X Trusted Plan and Source Authority — Research Admission Contract (Draft v0)

Date: 2026-11-08
Status: **RESEARCH DESIGN ONLY / NO IMPLEMENTATION OR REAL-DATA ADMISSION AUTHORIZED**.
Normative custody reference for P2-F remains `docs/research/phase2/p2-f-custody-authority-v7-correction-closure-2026-11-07.md` and its subordinate current vectors. Do **not** substitute its historical canonical v1 document for v7. The v7 closure currently records unresolved independent review blockers; this proposal does not amend, supersede, implement or operationally activate v7.

## Why a new contract is needed

Current P2-X synthetic joins verify matching caller-provided plan IDs, versions, event digests and ledger projections. An attacker, a mistaken importer or a stale coordinator can supply a mutually consistent but fabricated set. The P2-X plan revision digest is format-checked, not authenticated to an independently witnessed plan history. No amount of self-consistent SHA-256 checks establishes source ownership, athlete consent, rollback resistance, or write authority.

## Proposed separation of responsibilities

Four **logically separate** authorities are needed before real source use. The exact trust/certification mechanism must inherit only from an independently accepted P2-F v7 protocol rather than inventing a competing custody authority.

1. **Plan authority:** identify a stable plan namespace, unique non-reused plan ID, monotonic immutable revision number, canonical serialized plan revision digest, full revision parent and an independently verifiable finality/availability reference. Every P2-A/C/D/E snapshot must explicitly bind that plan revision or fail the join. A P2-C migration from a mere version number is *not* a proof and cannot silently upgrade historical records.
2. **Source authority:** bind provider/device account namespace, data rights grant, immutable source observation ID and revision, acquisition provenance, original observation timestamp and *independently established availability* timestamp. Raw source identity cannot be inferred from an activity title, source label or FIT filename. Corrected/withdrawn entries must invalidate prior references and derived model features through an auditable event chain.
3. **Decision authority:** bind the precise plan revision, policy/model version, decision cutoff, complete eligible evidence-set commitment (not just the subset selected), purpose, athlete restrictions, unresolved conflicts and abstention reasons. Every omitted eligible observation must be visible as an explicit missing/unverified condition. A historical proof demonstrates only historical scope, not current approval.
4. **Approval authority:** keep human approval separate from data custody and shadow proposal receipts. A proposal must be revalidated against the latest finalized plan/source state at the instant of any permitted commit; actor identity, authorization scope, expiring challenge, one-shot identity, revocation and no-replay semantics require independent design. **Research-only P2-X must never generate a write token or execute a workout edit.**

## Required state transitions (illustrative, not approved code)

`UNQUALIFIED -> QUALIFIED_SOURCE -> DATA_ADMITTED -> SHADOW_REVIEWED -> AWAITING_APPROVAL -> APPROVAL_VALIDATED -> CONDITIONAL_COMMIT`.

Every transition is fail-closed; any source right withdrawal, evidence correction, plan revision change, stale decision, insufficient source witness or untrusted roster reverts to `REVIEW_BLOCKED`. No `APPROVAL_VALIDATED` status may be obtained solely by a synthetic hash or a boolean in the input fixture. Clinical stop/restriction always precedes ordinary scheduling proposals. A failed conditional commit is never silently retried using stale authority.

## Minimum adversarial evidence packet for independent review

1. Different plans with identical numeric versions must fail identity.
2. Valid source record from another athlete or account must fail scope.
3. Same event ID with altered bytes or provider-revision number must invalidate decisions.
4. Source withdrawal, revocation and backdated upload must not be hidden by later synthetic snapshots.
5. Missing eligible event and extra active ledger entry must never report complete agreement.
6. Rollback of coordinator state or a stale certified checkpoint must not reauthorize data admission or writing.
7. Forged plan digest plus forged source digest must fail when the external trust anchor is withheld.
8. Replay of approved proposal after a plan revision change or approval withdrawal must fail.
9. No reviewer should infer recovery, injury risk or plan effectiveness from only structural contract success.
10. Provider/FIT decoding accuracy and lawful participant consent require separate evidence; none is present in this synthetic branch.

## Gate decision

This is a **gap-closure proposal for independent P2-F and P2-X reviewers**, not a ready protocol, source onboarding process, approved data migration or implementation instruction. Real-data Gate 2 remains closed until independent qualification, source/custody authorization, device qualification and athlete-outcome protocols have been separately accepted.
