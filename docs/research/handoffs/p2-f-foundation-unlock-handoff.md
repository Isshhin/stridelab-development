# P2-F Foundation Unlock handoff

## Independent coordinated-state review — 2026-11-07

The [independent review](../phase2/p2-f-coordinated-state-independent-review-2026-11-07.md) accepts the coordinated state model at `d545f7616708dfb46e9a3af9bd8b66c6eec37644` as `PASS_FOR_SYNTHETIC_AUTHORITY_CUSTODY` for the cooperative synthetic model. The historical design and correction entries below describe earlier states. Private pilot remains NOT READY, human/provider admission NO, `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

## Coordinated synthetic state design — 2026-11-07

The [design review](../phase2/p2-f-coordinated-state-mutation-design-2026-11-07.md) selects controlled mutation ownership, one shared lock and an epoch for a future synthetic-only correction and independent re-review. The model is not yet implemented; `SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION` and all private-pilot, Gate 2 and P2-B restrictions remain.

## Synthetic state-ordering review — 2026-11-07

`SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION` at reviewed head `e21d0b2e0afe4677253bff5de2865f4b95b69760`. See the [review record](../phase2/p2-f-synthetic-state-ordering-review-2026-11-07.md) for two reproducible stale anchor/inventory publication cases, verification and scope. No broker implementation changed. Private pilot remains blocked; human/provider admission NO; `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

Date: 2026-11-06 (Asia/Manila)  
Repository: `Isshhin/stridelab-development`  
Research branch / PR: `research/runna-garmin-coaching-20261102`, PR #34; keep open, draft and unmerged.  
Live starting SHA: `ac1b0e27c2b16caae32e7fe4d226b7c7860c0135`  
Audited implementation ending SHA: `062efc2dc5215e0a12321e65e22cdb7a53d18484`  
Frozen Session G architecture: `26a8331e642c888dd73acb94755dded063baab8d`

The ending SHA above is the exact code/test head used for the final 257-test research validation run. This handoff is a documentation follow-up and does not broaden that implementation result.

## Outcome

The sprint completed the bounded work that could be justified without human admission or architectural expansion:

- audited P2-F1/F2/F3, P2-B0, P2-FI and all newer branch work through the live starting head;
- froze additive F2.2 for the canonical Training Exposure Ledger, decision-time/current-validity replay, typed missingness, alias/conflict handling, correction/withdrawal invalidation and minimum trace fields;
- added a research-only synthetic reference projector and trace validator;
- added adversarial coverage for Garmin+FIT aliases, similarity without dedupe, late/offline sync, stale revisions, partial/manual-only actuals, corrections, source conflict, missing-vs-zero HR, malformed clocks, cross-account references, duplicate aliases, unknown intent, withdrawal and causal availability;
- preserved F2.1, the private pilot adapter and the frozen Session G architecture.

No human data, participant file, sealed outcome, production Engine 1.5 behavior, Trackside production UI, APK or release path was touched.

## Artifacts

Created:

- `docs/research/phase2/p2-f-foundation-unlock.md`
- `research/validation/p2f_foundation.py`
- `research/validation/test_p2f_foundation.py`
- `docs/superpowers/plans/2026-11-06-engine2-p2f-foundation-unlock.md`
- this handoff

Updated:

- `docs/research/phase2/p2-f2-linkage-contract.md` with the F2.2 additive freeze
- `docs/research/handoffs/engine2x-current-handoff.md`

Implementation commits before this handoff:

- `ba845e86b1a022db9e154639ea2e6c9a0a953350` — contract and audit
- `6f18d22862910c4deb44dd819614483725beead8` — synthetic reference and adversarial tests
- `fbdea1adf36db4b45a44da8322ca9a9904990a2c` — causal-order hardening found during adversarial review
- `062efc2dc5215e0a12321e65e22cdb7a53d18484` — fail-closed handling found during final self-review

## Verification

Environment: CPython 3.12.10, the branch's pinned private-audit runtime.

- Focused F2.2 reference: 16 passed.
- P2 subset: 94 passed.
- Full `research/validation` suite: 258 passed, 1 skipped because symlink creation is unavailable on this Windows runner; 0 failures/errors.
- The initial default Python 3.15 run was intentionally rejected by the private-audit 3.12 version guard. It is not counted as branch evidence.

## Adversarial review

| Failure mode | Result | Boundary |
| --- | --- | --- |
| Future-data leakage | PASS for synthetic reference | Late/offline activity and sources cannot enter earlier views; early source visibility is rejected. No real provider clock has been audited. |
| Duplicate counting | PASS for explicit synthetic aliases | One canonical activity counts once; heuristic similarity never deduplicates. Real Garmin/FIT identity still needs independent device audit. |
| Provenance loss | PASS structurally | Source IDs, aliases, digests, conflict sources, event-set digest and invalidated sources remain visible. Private hashes remain outside Git in real use. |
| Account contamination | PASS synthetically | Foreign entity references fail closed and withdrawal leaves the sibling account usable. No hosted multi-account store exists. |
| Unknown versus zero | PASS synthetically | Typed nulls and literal zero remain distinct; conflicting sources yield `conflict`, not zero. |
| Withdrawal failures | PASS for in-memory and existing private controls | Returned projections become invalid and new replay is denied. Physical deletion, processors and backups still require steward evidence. |
| Overconfident inference | PASS as a scope refusal | The module emits exposure/measurement structure only; no P2-B estimate or coaching decision. Effectiveness remains untested. |

## Gate status

- **P2-F feasibility: PASS — synthetic engineering feasibility only.** The canonical contract and bounded reference are coherent and executable.
- **Gate 2: BLOCKED / `P2_F_GATE_2_NOT_READY`.** No lawful human source, supported real-device path, independent reconciliation denominator, coverage/burden margin, frozen manifest or signatures.
- **P2-B: BLOCKED / `P2_B_PROTOCOL_READY_DATA_NOT_READY`.** The trace contract is defined and synthetically validated, but there are no authorized, independently audited repeated eligible segments. No repeatability estimate was run.

This is not Gate 2 readiness, active P2-B readiness or evidence of coaching effectiveness.

## Remaining blockers

1. Complete a purpose-specific S1 or other lawful human development admission in the private environment.
2. Pin one device/firmware/export family and independently reconcile every development-pilot opportunity and incoming source.
3. Implement the durable F2.2 store with live authorization/suppression before reads, immutable lineage, transactional append, restore and migration tests.
4. Build a source-specific adapter into the F2.2 trace contract; do not promote the current FIT extraction probe or narrow TCX reader by assumption.
5. Observe device-specific linkage/measurement coverage, critical mismatch rate and audit burden.
6. Freeze the P2-F Gate 2 manifest and obtain separate independent review.
7. Only then collect the prospectively registered repeated segments needed by P2-B0 and freeze P2-B's own margins/manifest.

## Exact next implementation prompt

> Work from `Isshhin/stridelab-development`, branch `research/runna-garmin-coaching-20261102`, PR #34, preserving all work newer than audited implementation head `062efc2dc5215e0a12321e65e22cdb7a53d18484`. Keep the PR open, draft and unmerged. Read `docs/research/phase2/p2-f-foundation-unlock.md`, F2.1/F2.2, the private custody/runbook artifacts and this handoff. Implement only a research-only durable F2.2 store behind the existing synthetic `p2f_foundation` behavior: account-partitioned immutable events, transactional/idempotent append, decision-time and current-validity projections, live grant/suppression checks before decrypt/read, explicit alias adjudication, per-field conflicts, correction/link-revocation/withdrawal invalidation, derived lineage, schema/projector versioning, restore and replay migration. Write failing tests first for crash rollback, stale concurrent writers, withdrawal before restore, cross-account keys, duplicate aliases, late/offline events, corrections and old-projector replay. Do not ingest human files, add provider credentials, claim device support, calculate P2-B repeatability, alter Engine 1.5/Trackside/release code, open held-out outcomes or change the frozen Session G architecture. Stop after the durable synthetic store and implementation-ready adapter boundary unless a separately admitted private pilot and independent reference are explicitly supplied. Report exact SHAs, test counts and Gate 2/P2-B as blocked unless new evidence actually satisfies them.

