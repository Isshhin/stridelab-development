# Independent publication-fence re-review — 2026-11-07

Verdict: `SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION`. Reviewed live head `ae0f5b093e5190fb185d59bd2f7295891747d3c2`; one-line correction `00257401e66ec2be8dbd1b0fc8168cd2e4c21d31`. Scope is invented-data in-process orchestration, not real custody or security certification. Earlier reviews remain unchanged. No actual keys, TPM operations, provider data or human records were used.

## Finding: P1 synthetic publication fence can be poisoned *during* validation

The previous generator-before-yield defect is corrected: after `next(chunks)`, `Broker.read` enters `_state` and checks withdrawal and `_violation` before authority, inventory, data-version and output append. However, later validation can itself execute caller-controlled Python code **after the `_violation` check and before append**. `Inventory.verify` uses `self.objects.get(obj)` and `obj` is accepted without an exact immutable built-in type requirement. A `str` subclass can override `__hash__`. On the final inventory check under the reentrant `_state` lock, its `__hash__` attempts a same-owner `Broker.read`, catches `Denied`, and returns the ordinary string hash. The inner denial sets `_violation`; the pending inventory/data checks still pass, and the broker appends `published` before noticing `_violation` after the append. Reproduced outcome: outer call raises `Denied('reentrant mutation')`, but `key.output == ['published']` and `escaped == ['published']`. This is a new, narrower schedule than the corrected generator case. It demonstrates a violation in this synthetic Python API, not an exploit of a real application or a claim about real athlete data.

Relevant exact-head paths: `research/validation/p2f_private_custody.py:121-130`; `research/validation/p2f_private_authority.py:93-95`. Direct GitHub blobs checked at the reviewed correction/head: `p2f_private_custody.py` blob `dec805fb10ee0f8310e7f5710cef1e87de6938c0`, `p2f_private_authority.py` blob `72a4c9efa37d9e0b8688bfef66ec0e3d3cb534d9`, focused tests blob `daa50bdd05b7157673082905e4400edee073e16b`. The test triggers the seventh inventory verification deterministically; the precise count is a review fixture, not a public contract. A separate injected verifier callback produced the same result.

The prior correction is therefore **necessary but insufficient**. A bounded follow-up must fence against violation/withdrawal/state changes caused by every validation operation, including Python hashing/equality and fake dependency callbacks, immediately before append under the state lock; or make the relevant public inputs/dependencies provably non-callback-capable and reject unsupported forms before protected work. Review all applicable comparison/hash paths and ensure unrelated thread contention is not incorrectly poisoned. Add the failing `str`-subclass schedule and between-chunk variant to the normal tests. No implementation change was made in this review.

## Independent schedules and regression

- Focused synthetic authority/custody: 71 passed.
- Earlier independent adversarial file: 6 passed.
- Correction re-review file: 8 passed, including poisoned generator before first yield.
- Durable-store: 54 passed.
- P2: 219 passed.
- Full research: 383 run, 382 passed and one Windows symlink test skipped.
- New `research/validation/review_p2f_authority_final.py`: 11 deterministic schedules, 10 passed and 1 failed as above. It is deliberately outside `test_*.py` discovery. Covers nested first/later yields, normal generator, competing thread, withdrawal/data/authority change before yield, iterator exception and subsequent read, repeated equal-chunk receipt, owner/gate cleanup, and the additional locked-inventory callback. Run `py -3.12 research/validation/review_p2f_authority_final.py` from the repository root. Its expected nonzero exit is evidence of the outstanding defect.

Focused existing regressions cover scoped identity, stale authority/generation, inventory/ledger rollback, anchor faults, issuer fork assumptions, ambiguous advance readback, operation collision/idempotence, withdrawal, restore and audit within their fake model. The original generator and before-yield invalidation scenarios work in the tested schedules; this finding is specifically a callback invoked by validation after the new check. `_gate` and `_state` released in the tested paths and unrelated competing read did not poison its owner. This is not an exhaustive thread-safety proof; `RLock` allows same-thread reentry and output fence ordering remains materially defective.

## Graphify and CI

Hosted `Isshhin/stridelab-engine2-graph` indexed the exact live start `ae0f5b093e5190fb185d59bd2f7295891747d3c2`, 1,291 nodes / 3,450 edges. Used rank-files, symbol/impact and linked tests for `Broker.read`, `receive_withdrawal`, `transition`, `reconcile`, `Versions`, `Anchor.advance`, and `Inventory.verify`. The graph located broker and focused tests; `Versions` linked-test discovery returned none, so the focused test file was inspected directly. Its known `Anchor.advance -> Inventory.verify` resolution is incorrect: live source calls `Anchor.verify`. The independently inspected scope also included the prior review records, two earlier review scripts, implementation record and handoffs. The change from `9dbb37e...` consists of one broker line, eight focused tests and documentation; no unrelated production changes.

GitHub Actions run `42322328925` was queued at last check; it is not a pass. Independent local counts match the reported implementation counts, but the new schedule prevents the pass criteria. No inference about billing/runner cause is made.

## Next step and boundaries

Implement only the bounded synthetic validation-to-append fence or an equivalent carefully validated solution, with regression for a reentrant `__hash__`/`__eq__` or equivalent validation callback during the final check. Confirm no output/escape for a poisoned first chunk, preserve a prior legitimate chunk exactly once, test later clean read and unrelated-thread contention, and rerun the focused, 6/8/11 review schedules, durable, P2 and full suites. Obtain another independent adversarial review of that exact head. The existing `PASS_FOR_SYNTHETIC_DURABILITY` remains limited to the separately reviewed journal. Private pilot NOT READY; human/provider admission NO; `P2_F_GATE_2_NOT_READY`; `P2_B_PROTOCOL_READY_DATA_NOT_READY`. This does not test TPM, CNG, DPAPI, encryption, real crash persistence, physical deletion, provider terms or FIT validity.
