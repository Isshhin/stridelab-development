# Coordinated synthetic state: Batch 3 audit

Starting head `6ef64cef92c94c391f29d3374387d41e0583b98c`. Status: `COORDINATED_STATE_BATCH3_CORRECTED_CANDIDATE`. The audit found five material correctness gaps; all five were locally corrected with added regression coverage, and broad Python 3.12 validation passed. The candidate commit containing this record is identifiable through Git. Batch 3 remains pending independent acceptance; this is not independent review or a private-custody claim.

## Findings and local fixes

1. Publicly writable `Broker.busy` could bypass same-owner nested-transition invalidation. It now exposes a read-only property backed by private operational state. A regression checks denial and zero output.
2. Output and exposure accessors copied only their outer lists, and published mutable chunks retained the caller's alias. Candidate chunks are now converted to owned synthetic values before the locked final check; output and exposure accessors return deep copies. The final epoch-to-append interval remains the built-in append only. Unsupported custom stored payloads fail closed; read-input callback tests remain supported.
3. Public `FakeKey.step` allowed a caller to inject fake effect events outside the broker gate. It is now a private model implementation method; direct private access and monkeypatching remain outside the cooperative model.
4. `Ledger` accepted custom anchor/issuer facades with the same coordinator but potentially unmediated mutable state. Construction and replacement now require the exact synthetic facade classes (or `None` for the existing missing-anchor fault).
5. An interruption during read initialization after gate acquisition but before the old `try` block left busy/owner/gate state set. Initialization is now inside the cleanup boundary; the injected `KeyboardInterrupt` regression verifies release and subsequent use.

Each was reproduced by a focused regression before its local fix. No material unresolved defect was identified by the bounded checks after these fixes; the discovery of material gaps means Batch 3 is not yet independently accepted. The historical review records are unchanged.

## Mutation and alias audit

The P2-F validation tree was searched for public and private state assignments, mapping edits, `setattr`, and mutating collection methods. Coordinated `Versions` indexing and `update` are supported APIs: they call the shared coordinator and advance the epoch. Review schedules use `Anchor.advance`/`set_generation`/`set_fault`, `Ledger.set_head`, `Inventory.set_object`, `Broker.receive_withdrawal`, and `Versions.__setitem__`. There are no remaining unexplained supported raw edits in those review scripts. Private backing-field writes within the coordinator, authority, custody, and projector implementations are internal implementation operations, not supported external mutation paths. `p2f_foundation`, `p2f_durable`, linkage, and pinned projector internal mutations belong to their separately reviewed models and were not migrated.

Deliberately retained diagnostics: interruption tests use `patch.object` on `Anchor.advance` to simulate `KeyboardInterrupt`/`SystemExit`; other tests inspect or temporarily acquire the private gate and inspect owner/invalidation for cleanup. These are test instrumentation outside the supported coordinated mutation API, not a simulated real crash. Tests assigning to read-only fields intentionally verify rejection. The new initialization test patches coordinator `mutate` solely to inject an interrupt.

Ledger heads, operations, issuer commitments/intents, inventory objects and modeled versions use owned storage and copy-out; prior coordinator tests cover nested input and output aliases. Broker admission properties and busy are read-only; fake output and escaped records now copy nested owned data. Private field access and monkeypatching are outside the cooperative proof.

## Executable review inventory

| Script | Schedules | Coordinated paths / intentional raw diagnostics | Result |
| --- | ---: | --- | --- |
| `review_p2f_authority_adversarial.py` | 6 | version mapping, ledger `set_head`, anchor `set_fault`, withdrawal; patched advance interrupt | 6 passed |
| `review_p2f_authority_correction.py` | 8 | version mapping, withdrawal, nested calls; patched advance `SystemExit` | 8 passed |
| `review_p2f_authority_final.py` | 11 | version mapping, anchor `set_generation`, withdrawal; private owner/gate inspection | 11 passed |
| `review_p2f_authority_withdrawal.py` | 6 | broker withdrawal and nested read callbacks | 6 passed |
| `review_p2f_authority_state_ordering.py` | 3 | anchor `advance`, inventory `set_object`, broker withdrawal; private cleanup inspection | 3 passed |

Total: 34 deterministic schedules, all passed. The state-ordering scripts now explicitly assert `Denied` while preserving their actual state-mutation and output assertions. Review script docstrings were updated to describe historical regressions; no schedule or historical review document was removed.

Python 3.12: coordinator 16 passed; authority/custody 81 passed; publication 27 passed; durable 54 passed; P2 272 passed; full research 436 run, 435 passed and one Windows symlink skip. Exact-head CI for the candidate was not available when this record was written. The prior journal `PASS_FOR_SYNTHETIC_DURABILITY` is unchanged; private pilot is blocked, human/provider admission NO, `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

Next: refresh hosted Graphify for the pushed candidate and obtain an independent Work review. Do not treat these local checks as `PASS_FOR_SYNTHETIC_AUTHORITY_CUSTODY`.
