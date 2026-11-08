# P2-X Foundation Qualification Wave — Scoped Audit, Corrections, Regression, and Decision

Date: 2026-11-08 (Asia/Manila)  
Branch: `research/p2x-foundation-qualification-20261108`  
Baseline: `7c5e0efdbfb525277ad530c169a10f7189c781ea`  
Correction commit: `10c2e285f03f82e8fde657d9781c7ba5097e3a4b`

**Scope:** Research-only synthetic foundation (P2-A, P2-C, P2-D, P2-E, P2-X). A newly scoped *internal code/adversarial review*, not an independent external audit, source authorization, physiological assessment or a qualified deployment.

## Batch 1: Audit findings and reproduced defects

1. **HIGH — Native payload could change without invalidating its evidence binding.** The previous `p2x_lineage.assess` compared caller-supplied binding `payload_sha256` with caller-supplied ledger active record digest, but did **not** hash the native event itself. Reproduction: mutate `bundle["p2a"]["observations"][0]["value"]` to 999999, leave manifest and projection unchanged; previous output remained `SYNTHETIC_ASSERTED_CONSISTENCY_ONLY`.
2. **HIGH — Incomplete manifest could be reported as consistent.** Reproduction: retain just 1 of 4 eligible evidence bindings and leave 3 native observations unbound; previous output remained `SYNTHETIC_ASSERTED_CONSISTENCY_ONLY`.
3. **MEDIUM — Source revision and lineage root were only optionally asserted natively.** The previous code accepted a binding revision without a corresponding native revision field, and did not compare native lineage root against its binding.
4. **MEDIUM — Unrecognized string status was not rejected; an unhashable status was only partially defended.** A status other than the expected discipline/track statuses could proceed to validation. Defensive status typing is now track-specific.

These results were reproduced on the exact previous committed source and not inferred solely from passing test counts. Baseline hosted combined regression: **440/440 PASS**, demonstrating the missing negative cases.

## Batch 2: Applied changes

Changed only:

- `research/validation/p2x_lineage.py`: SHA-256 of canonical JSON for the **actual synthetic native event** must equal its manifest binding digest; malformed non-canonical JSON fails closed. Native source revision and lineage root are required to qualify evidence. Missing/mismatched source information defers or rejects. Only known track-native statuses qualify. Every decision-available eligible native event must have a binding before the manifest can report synthetic consistency.
- `research/validation/test_p2x_lineage.py`: fixtures now use native-record-derived SHA-256 rather than constant fabricated payload hash; 8 new regression cases cover payload tampering, omitted binding coverage, absent revision, wrong lineage, altered session payload, extra unbound event, unhashable status and unknown status. Existing 50 cases updated for correct native payload digests in migrations and deliberately correlated lineage.

**Backward-compatibility constraint:** New requirements may defer older synthetic snapshots that lack native source_revision / lineage_root_id or canonical payload digests. That is intentional fail-closed behavior, **not** evidence of a verified real-source migration. Device/provider ingestion, legacy snapshot migration and approved schema compatibility remain untested.

**Cryptographic limit:** The checksum is unkeyed and all fields are caller-supplied; it is not a signature, provider attestation, anti-rollback witness or proof of lawful authorization.

## Batch 3: Exact source and hosted test evidence

- Combined eight-module P2-A/C/D/E/X synthetic regression in isolated hosted Python: **448 tests passed, 0 failed** with `PYTHONHASHSEED=7` and `213`; an additional `PYTHONHASHSEED=33` replay passed.
- `python -m compileall -q *.py` passed.
- Tested baseline: 440 passing cases; after correction: **448 passing** cases (8 new).
- Exact Git blob hashes of the two test-run source files match this branch: `p2x_lineage.py` = `be9022bfc335562c18c1448be15940c3efcd76b9`; `test_p2x_lineage.py` = `55faef586aaf51adbce27d55c9851a94b556305b`.
- GitHub-hosted Linux workflow `.github/workflows/research-p2x-qualification-ubuntu.yml` uses only `ubuntu-24.04` (not self-hosted) and enforces Linux/github-hosted environment. Run [#42482594419](https://github.com/Isshhin/stridelab-development/actions/runs/42482594419) **failed before any job steps started or runner assignment was reported**. Logs were unavailable; root cause remains unconfirmed. **Do not claim exact-head GitHub CI PASS or test failure.** No fallback to the user's PC.
- This hosted regression was not an independent expert audit, validated model trial, or approved private-data pilot.

## Batch 4: Qualification decision and remaining blockers

**Decision: NOT ACCEPTED for independent foundation qualification or Gate 2.**

Author-side synthetic software milestone: `P2X_FOUNDATION_QUALIFICATION_WAVE_INTERNAL_FIXES_448_HOSTED_PASS`.

Outstanding issues requiring distinct gates:

1. **Independent reviewer:** A separately scoped reviewer must inspect SHA/fixture canonicalization, source ownership/correction semantics, incomplete evidence, rollback and plan-identification assumptions. Self-review is not independent acceptance.
2. **Source and plan authority:** `ledger_projection`, `plan_revision_sha256`, source IDs, native plan IDs and event hashes remain caller-supplied assertions. P2-C's native plan-ID migration is synthetic-only; it does not derive from an authenticated plan authority. An out-of-band trusted source/plan custody design is needed before real-data admission.
3. **Runner allocation:** Github-hosted job allocation failed before steps for the new isolated workflow, as in earlier isolated attempts. Resolve independently of user's workstation. Re-run exact head on permitted infrastructure and preserve logs.
4. **Cross-track scientific validation:** These 448 tests verify deterministic software behavior, not fitness prediction, injury prevention, coaching efficacy, meaningful load adjustment, or generalization to real Garmin/FIT data.
5. **Operational authorization:** P2-F real source admission/custody, ethical permission, device/decoder verification, individualized outcome protocols, independent athlete-safety review and track-specific Gate 2 decisions are not open.
6. **Write authority:** No authenticated athlete approval, no plan write capability, no production integration, and no clinical clearance has been issued.

PR #34 remains OPEN/DRAFT/UNMERGED. Engine 1.5, Android, Trackside, frozen Session G and P2-F are unchanged. No Work, Codex or user-PC configuration/execution occurred.

**Next recommended work:** Establish independent external code/design assessment of the new exact head; investigate CI allocation with GitHub Actions repo settings and billing/hosted-runner controls (without using PC), then run exact-head CI. In parallel draft the authenticated plan/source authority proposal as research, not executable deployment.
