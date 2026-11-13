# Independent defensive review — synthetic publication and withdrawal

Verdict: `SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION`. Reviewed live head `6e09172158fe4bb4e5819c23c872eb69b1e034d2`, correction `b6e8fe96581c3f6990838491e09b292e8c9827f5`. This is an invented-data, in-process model review, not separately staffed human certification or evidence of private-pilot safety. No broker implementation was modified, no TPM, real keys, provider credentials or private data were accessed. Preserve the previous review records.

## P1 synthetic finding: withdrawal during final validation still allows append

The correction fixes the previously reported nested-read `_violation` schedule, including a callback from `__hash__`, `__eq__`, or data lookup: it rechecks `_violation` after validation before append. However, `_state` is an `RLock`. An accepted `str` subclass or `int` subclass invoked by `Inventory.verify`, or a key lookup in `Versions`, can call `receive_withdrawal` on the **same thread** during final validation. That call re-enters `_state`, sets `withdrawal=True` and returns. The initial withdrawal check at `p2f_private_custody.py:123` already passed; the new final check addresses only `_violation`, not withdrawal. The broker appends a chunk and only afterward notices withdrawal and raises `Denied`. This violates the synthetic rule that a verified receipt closes subsequent publication. No claim is made that real keys, devices, users or production systems were exposed.

Deterministic reproductions in `research/validation/review_p2f_authority_withdrawal.py`:

1. During the seventh inventory `verify`, an accepted object-ID `str` subclass calls `receive_withdrawal` from `__hash__`. Expected no output; actual `key.output == ['published']`; outer operation denies only after append.
2. The same schedule through an accepted version `int` subclass `__eq__` likewise appends after receipt.
3. A first legitimate chunk is published; during final lookup for the second, the object-ID `__hash__` receives withdrawal. Expected `['first']`; actual `['first', 'bad']`. The first remains legitimate; the second is post-receipt output.

The six-schedule review file records these three failures plus three passing controls (benign custom object, ordinary generator with competing reader, previously reported nested-hash denial). Run `py -3.12 research/validation/review_p2f_authority_withdrawal.py`; at the reviewed head it exits nonzero: 6 run, 3 passed, 3 failed. It is intentionally outside normal `test_*.py` discovery. The seventh-check selection is a deterministic fixture detail, not a public API promise.

Minimum follow-up: in the synthetic publication block ensure **all** publication invariants, including withdrawal and violation, are re-established after the last callback-capable validation/comparison and immediately before append while holding the shared fence; account for `RLock` same-thread reentrancy and avoid unrelated-reader poisoning. Add these three failing cases, prior-output preservation/escape accounting and clean-read controls to the focused suite, then independently re-review. If a validation operation may itself change another invariant, it is not enough to recheck only `_violation`. Do not implement or claim real privacy infrastructure as part of that fix.

## What passed independently

The original nested-hash reentrancy case, equality reentrancy and data-lookup reentrancy are denied without the poisoned chunk, and benign custom values remain usable in the tested focused cases. Earlier generator-before-first-yield and between-yields behavior, normal generators, unrelated-reader contention, pre-yield withdrawal, data/authority change, iterator exception, repeated receipt and owner/gate cleanup pass in their existing controlled schedules. They do not exercise withdrawal originating *inside* final inventory/data validation.

Windows CPython 3.12: focused authority/custody 75 passed; original schedules 6 passed; correction schedules 8 passed; prior final schedules 11 passed; durable-store 54 passed; P2 223 passed; full research 387 run, 386 passed and one Windows symlink test skipped. Additional review-only schedules 6 run, 3 passed and 3 failed as above. Existing regression suite covers stale grant/generation, ledger/inventory rollback, wrong scope, anchor faults, modeled fork/advance/recovery, replay, withdrawal, audit and interrupt paths, within its limited synthetic abstraction; no general concurrency or real crash proof is claimed.

## Graphify, source and CI

Hosted Graphify `Isshhin/stridelab-engine2-graph` indexed the reviewed head `6e09172158fe4bb4e5819c23c872eb69b1e034d2`, with 1,321 nodes and 3,497 edges. Used rank-files, impact and linked-test discovery for broker reads, withdrawal, transition/reconcile, `Versions`, inventory and anchor; direct source was used for callback behavior. The graph identifies the focused test surface but its `Versions` linked-test query returns no tests; verify coverage in the source. The known `Anchor.advance`/`Inventory.verify` name collision remains a graph caveat; source calls `Anchor.verify`.

Direct GitHub blobs at correction `b6e8fe96581c3f6990838491e09b292e8c9827f5`: `p2f_private_custody.py` `13cb82835cb92d70dc569debf65bd0436f013abc`, `p2f_private_authority.py` `72a4c9efa37d9e0b8688bfef66ec0e3d3cb534d9`, `test_p2f_private_authority.py` `43dfd071f6dec9d0d5b9fb2a29ad3ff8c5aa63db`. The 6/8/11 review files, prior review records, implementation record and both handoffs were inspected at the pinned checkout. Source diff from `3388928` contains one broker line, four focused tests and documentation. CI run `42335896033` was queued at last check, not passed; no cause is asserted.

## Next step and boundaries

A subsequent bounded synthetic-only task should implement and test a final publication fence covering withdrawal and violation after callback-capable checks, including all three review schedules, a no-output first chunk, preservation of a legitimate first chunk, correct escaped accounting, cleanup and unrelated-thread contention. Run focused, 6/8/11/6 review schedules, durable, P2 and full suites and obtain independent re-review. The previous `PASS_FOR_SYNTHETIC_DURABILITY` remains unchanged for the separately reviewed journal. Private pilot NOT READY, human/provider data admission NO, `P2_F_GATE_2_NOT_READY`, `P2_B_PROTOCOL_READY_DATA_NOT_READY`. This review does not establish real TPM/TBS, CNG/DPAPI, cryptographic custody, disk persistence, physical deletion, provider authorization or FIT correctness.
