# Independent coordinated-state Batch 3 review

Reviewed and live branch head at inspection: `d545f7616708dfb46e9a3af9bd8b66c6eec37644`. Hosted Graphify was indexed at the same SHA, with 1,491 nodes and 4,091 edges. Its impact and file-neighbor results were navigation aids; implementation conclusions were checked against the source. GitHub's exact-head tree blob IDs matched the local checkout for both model files and all three principal test modules. PR #34 was open, draft and unmerged.

Verdict: `PASS_FOR_SYNTHETIC_AUTHORITY_CUSTODY`, within the supported cooperative in-process synthetic model and the exercised schedules. Batch 3 is accepted for this narrow milestone. This is not a proof against arbitrary modification of Python private internals or a real custody, authority, hardware or persistence assessment.

## Source and correction assessment

Reviewed `research/validation/p2f_private_authority.py`, `p2f_private_custody.py`, `test_p2f_coordinator.py`, `test_p2f_private_authority.py`, `test_p2f_epoch_publication.py`, the five existing `review_p2f_authority_*.py` scripts, the accepted coordinated-state design, Batch 3 record, and current handoffs. The five corrections have concrete supporting paths and tests:

1. **Busy/read lifecycle:** `busy` is read-only; same-owner nested transitions mark invalidation, unrelated competing readers are rejected without poisoning the owner, and `read` clears busy, owner and gate in `finally`. Existing tests exercise nested, competing and exception paths.
2. **Output/exposure aliases:** `owned(chunk)` recursively copies supported values before the locked publication check; output and escaped accessors deep-copy. Nested input, returned output and returned exposure mutations do not change stored history in the supplied tests. Unsupported custom stored payloads fail closed.
3. **Fake-key effects:** normal `step` is absent; broker invokes the fixed internal `_step` implementation. The key's supported fault setter uses the coordinator. Calling private `_step` or monkeypatching internals remains outside this cooperative model, not an enforced Python isolation boundary.
4. **Authority facades:** `Ledger` requires exact `Anchor`/`Issuer`; broker requires exact coordinator, ledger, inventory, key, audit and broker classes. Replacement setters check type/coordinator. Supplied fixtures continue to pass.
5. **Read initialization:** owner/busy assignment, invalidation reset and initial output capture occur in the `try` after gate acquisition. The existing injected `KeyboardInterrupt` and the additional `SystemExit` schedule verify cleanup and subsequent successful read. This does not establish behavior under process termination or every possible asynchronous bytecode interruption.

## Ownership, epoch and publication

Anchor identity/generation/fault, issuer flags/commitments/intents, ledger anchor/issuer/head/intent/operations, inventory generation/object/fault, and modeled versions are stored privately and changed by their coordinated methods; each supported mutation attempt increments the shared epoch under the reentrant lock before its action, including failed and no-op attempts. Read-only properties or copies expose their values. The broker coordinates closed, enrollment, scope, restored-head, withdrawal and invalidation changes; its read owner, busy flag and gate are internal lifecycle state. Key fault and audit append are coordinated; output and exposure use owned/copy-out values and internal bookkeeping. Intent/operation maps and recovery/reconciliation use coordinated setters. Public views of the head, operations, issuer commitments/intents, inventory and versions do not expose writable internal aliases. No unexplained supported bypass was identified in the reviewed paths.

The private state fields, direct private gate/owner observations and monkeypatches used for deterministic interruption/cleanup tests are diagnostics, not supported mutation APIs. Their existence means this is a cooperative model; arbitrary code with private access is not contained. Issuer hidden-fork detection remains an assumption, not a property established by this review.

Initial coordinator epoch is zero. In the standard fixture the epoch was 10 before one successful read: invalidation reset increments it to 11, the read token is 11 through authorize/release/decrypt, internal audit append increments it to 12 and the broker explicitly advances the expected token to 12; publish and append leave it 12. A representative final-inventory callback starts with token 12 after audit, makes a coordinated failed issuer-fault attempt, advances epoch to 13, catches the inner denial, and is rejected at the subsequent epoch comparison; no output is appended. The exact values depend on fixture history. A mutation between chunks invalidates the remaining operation while retaining and accounting for the earlier valid output.

The final locked publication path computes the owned candidate before locking; after final validation it obtains the private coordinator and internal sink, checks the sink is an exact list, compares the private built-in integer epoch with the token, and calls `list.append(sink, candidate)`. With the exact supported `StateCoordinator`, `Broker`, `FakeKey` and built-in list, no caller hook, iterator, hash, equality or caller mapping runs between the final epoch comparison and built-in append. Prior validation may run callbacks; a mediated mutation changes the epoch and is detected. This conclusion does not cover replacing private fields or monkeypatching classes.

Withdrawal, nested reads and operation invalidation retain their explicit checks. The five existing review scripts use coordinated methods; private gate and owner checks and patched interruption points are intentional diagnostics. Their state-ordering schedule remains 3/3. The cleanup checks cover normal denial, epoch mismatch, nested read, unrelated competitor, withdrawal, interruption, `KeyboardInterrupt`, `SystemExit`, fake-effect failure and partial-output accounting in the exercised schedules.

## Independent verification

Windows Python 3.12: coordinator **16/16**, authority/custody **81/81**, publication **27/27**, durable **54/54**, P2 **272/272**. Existing review scripts: **6/6, 8/8, 11/11, 6/6 and 3/3**, or **34/34** including state-ordering **3/3**. Full research: **436 run, 435 passed, one Windows symlink skip**.

The new review-only `research/validation/review_p2f_batch3_independent.py` adds three passing deterministic schedules: caught failed mutation during final inventory validation, `SystemExit` during read initialization followed by successful reuse, and internal-audit epoch accounting. These three are separate from the 34 existing review schedules and do not alter implementation.

Exact-head [workflow 42344992649](https://github.com/Isshhin/stridelab-development/actions/runs/42344992649) was **pending**, with no conclusion, at the last check; it is not a passing CI result.

## Boundary and next step

The previous synthetic journal durability pass remains unchanged. No human/provider data is admitted. Private-pilot readiness remains **NOT READY**, `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged. This review does not establish TPM or rollback behavior, CNG/DPAPI, real key handling, process/disk crash durability, real-device validity or private custody.

Next: preserve this exact-head synthetic acceptance and, only under separately authorized work, address the existing real private-pilot authority, custody, independent review, admission and device/reference prerequisites. Do not begin P2-B or admit data based on this result.
