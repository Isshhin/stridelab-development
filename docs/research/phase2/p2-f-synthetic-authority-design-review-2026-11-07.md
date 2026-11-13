# Independent design review: synthetic authority/custody harness

Date: 2026-11-07 (Asia/Manila)
Reviewed authoritative branch head: `97dd48cd35e2088f43f50fe4e5332f9ec6a826e2`
Hosted Graphify index: `ee3f69553ad932631a24db2a065722a140c07837`; mirror main: `97dd48cd35e2088f43f50fe4e5332f9ec6a826e2`. The diff between indexed and reviewed revisions contains only the authority contract and two handoffs; implementation context was checked in Graphify and the current contract was read directly. This is a separate technical assessment of the contract, not a separately staffed or professionally certified independent security signoff.

**Verdict for the narrowly bounded synthetic harness:** `PRIVATE_PILOT_CONTRACT_READY_FOR_IMPLEMENTATION`. This permits implementing invented-data models only. It does not approve a real pilot, device, hardware adapter, authorization service, crypto deployment, or intake. The existing `PASS_FOR_SYNTHETIC_DURABILITY` remains unchanged. Private-pilot admission remains blocked; `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

## Workstation evidence reviewed

The user reported TPM 2.0 present, initialized, ready for storage/attestation, enabled, activated and owned, with no lockout or clear-needed condition; AMD firmware `3.95.2.5`; and Secure Boot enabled. TBS context creation and close succeeded, and a read-only `TPM2_GetCapability` submission returned success and advertised `TPM2_NV_DefineSpace` and `TPM2_NV_Increment`. The earlier WMI command-block result was inconclusive. These observations were not independently repeated in this review. They establish neither permission to execute those NV commands under the proposed identity nor actual counter provisioning, policy, increment persistence, exhaustion, crash recovery or rollback resistance. No NV state, TPM policy or keys were changed here.

## Security findings and mandatory model clarifications

1. **A counter authenticates ordering, not ledger contents or issuer uniqueness.** At generation N, equality with one authenticated ledger head does not alone prove that another authenticated head at N was never issued or hidden. The candidate requires a trusted, non-equivocating sole issuer with durable, nonrollback issuance/commitment evidence and a protected enrollment identity. That guarantee is an explicit *unproven real-system assumption*, not a property inferred from a fake counter, TPM counter, DPAPI or file signature. The model must distinguish the counter from the issuer/commitment oracle, permit fault injection of issuer equivocation and hidden alternatives, deny on detected ambiguity, and report the undetectable single-presented-head case as an assumption failure rather than claiming detection. Real deployment is blocked until the actual enforcement and recovery are independently established.
2. **Withdrawal versus publication needs a precise fence.** The contract says both stop publication upon receipt and allow a read that wins to finish. Resolve this for the harness as follows: a verified withdrawal receipt immediately closes admission and future publication; if complete output was published before receipt, that prior output cannot be recalled. If receipt precedes or interrupts publication, no further output bytes are emitted, remaining plaintext is discarded, and withdrawal acknowledgement waits for in-flight work/key release to finish. If revoke wins before the read gate, no unwrap/decrypt/output occurs. A partially delivered output is a recorded exposure, not treated as unpublished. Gate ordering, receive and publish events must be testable separately. No reliance on a callback that has already escaped the broker.
3. **Anchor and ledger cannot be committed as one ordinary database transaction.** The harness must inject failures on every boundary: intent durability, advance request/uncertain response, readback, ledger commit, audit and acknowledgement. Before advance, a grant cannot be inferred and withdrawal receipt may be lost without a separately reconciled channel. After advance with no matching authenticated head, remain closed; only a verified restrictive intent may be completed under controlled recovery, never an active grant. After commit but before acknowledgement, restart reconciles the same operation ID and current anchor/ledger; do not increment or issue twice. Unknown anchor outcome is read back and reconciled, never blindly retried. Missing/rolled-back intent, changed anchor identity or irreconcilable state stays closed.
4. **Data rollback needs current object inventory.** A valid current authority head and matching counter do not prove restored data is current. The harness must model an independently current inventory/tombstone state bound to the authority generation, not infer it from the restored data. Missing, inconsistent, rolled-back, unknown or untrusted inventory denies reads and derived output. The trust and durable binding of that inventory remain real-pilot blockers.
5. **No security claim from abstractions.** The existing process-local lock and plaintext synthetic journal are not the proposed broker, authenticated ledger, hardware anchor, key fence, or custody system. Fake authentication, encryption and anchor interfaces may test orchestration and fail-closed decisions only. Do not use invented crypto, pseudo-random security, or synthetic anchor behavior as evidence of TPM, DPAPI, CNG, real deletion or anti-rollback guarantees.

## Authority, recovery, keys and time

Bootstrap starts CLOSED with no ordinary read, unwrap, grant or restored-authority activation. Enrollment requires a separate explicitly supplied trusted anchor identity and initial issuer/commitment state; restoring an old database cannot choose, replace or initialize it. Generations are monotonic, never reused, and equality is necessary but not sufficient: authenticate the exact head, its scope, anchor binding, state commitment and unique issuance. Missing/unavailable/corrupt anchor, unavailable issuer assurance, wrong anchor identity, mismatch, duplicate head, unknown inventory or ambiguous recovery denies. An anchor loss/reset or reinstall remains CLOSED; do not create a convenient new generation or reactivate old grants. A restored pre-withdrawal or pre-deletion copy remains denied before key release; stale data under current authority is quarantined by current inventory. After every restart, no read until the model receives an explicit successful independent withdrawal-channel reconciliation; ambiguous pending requests stay CLOSED. Acknowledgement and data deletion are separate from suppression.

An invented key boundary records authorize -> unwrap -> authenticate/decrypt -> publish ordering and can fail at every step; it is not encryption. For real Windows integration, CNG AEAD nonce uniqueness and associated-data binding, user-scope DPAPI service identity/profile/ACLs, key separation, Windows privilege boundaries, object and key inventory, recovery and destruction must be verified independently. Microsoft documents that machine-scope DPAPI permits any user on that machine to decrypt, and that TBS applies command allow/block policy; capability enumeration alone is not successful authorization or NV provisioning. [Microsoft DPAPI](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata), [Microsoft TBS command blocking](https://learn.microsoft.com/en-us/windows/win32/tbs/command-blocking). The user's non-destructive TPM/TBS observations are reported evidence, not independently repeated here. No real TPM NV operation was performed.

No trusted network time or NTS is required for the initial synthetic generation-ordering model. Model expiry with an injected trusted-time outcome: absent, uncertain, stale, rolled back or unauthenticated time denies any time-bound decision. Real grant/retention deadline enforcement needs separately reviewed clock bootstrap, uncertainty, offline behavior and lawful deployment; NTS by itself does not prove time accuracy or authorized disclosure. Hardware boundary: expose `read()`, `advance(expected)`, and `verify(expected)` with explicit unavailable/corrupt/mismatch/ambiguous outcomes and fault injection. The fake anchor must not promise real TPM atomicity, endurance or rollback resistance.

## Required synthetic acceptance matrix

`DENY` means no key release, plaintext or output; `CLOSED` means no subsequent protected operation until independently reconciled. Every denial is reason-coded without exposing foreign account existence. Tests use invented objects and explicitly controlled schedules; no real hardware, key, credential, file or athlete input.

| Case | Required synthetic result |
|---|---|
| Stale grant | DENY against current anchor/ledger. |
| Stale generation | DENY; never select older signed state. |
| Ledger rollback | Anchor/head mismatch -> CLOSED. |
| Data rollback | Current inventory/tombstone rejects stale object -> DENY. |
| Generation mismatch | CLOSED; no convenient head selected. |
| Missing anchor | CLOSED; no bootstrap from backup. |
| Unavailable anchor | CLOSED; no cached grace. |
| Corrupt authority | Authentication/shape failure -> CLOSED. |
| Concurrent read/revoke | One gate orders operations; no post-receipt publication, no completed revoke acknowledgement before prior work is fenced. |
| Revoke during read | Stop admission/publication, cancel or finish only pre-receipt published output; discard remainder before acknowledgement. |
| Revoke during output | Record already emitted bytes as escaped; emit no further bytes and do not acknowledge until fenced. |
| Crash before revoke commit | CLOSED on restart; external request reconciliation is mandatory, and an unrecorded receipt cannot be claimed durable. |
| Crash after anchor advance before ledger completion | CLOSED; recover only verified restrictive intent or stay closed. |
| Crash after ledger commit before acknowledgement | CLOSED on restart; reconcile same operation ID/head, no duplicate increment, then acknowledge only after fence/audit. |
| Restore before withdrawal | Fresh live anchor, authority and inventory required; only a current authorized scope opens. |
| Restore after withdrawal | Suppression wins before unwrap; deny and invalidate descendants. |
| Restore after deletion | Tombstone/inventory denies; dispose or isolate copy; no claim of physical erasure. |
| Copied store | Without independently enrolled matching anchor, issuer and inventory -> CLOSED. |
| Account mismatch | DENY before key use, no foreign identifiers disclosed. |
| Wrong purpose | DENY before key use. |
| Duplicate/replayed operation | Same ID and same intent is idempotent only after reconciliation; changed payload/collision DENY; never re-advance. |
| Restart | CLOSED until fresh state and withdrawal channel reconciled. |
| Reinstall/bootstrap | CLOSED until separately authenticated enrollment and current state; never recreate active grants from backup. |
| Failed key release | DENY, no decrypt/output. |
| Failed audit write | No output publication and no completed revocation acknowledgement; CLOSED when required state uncertain. |
| Reentrant mutation | Reject/fail closed; no partial transition or publication. |
| Contention | Bounded deterministic fail-closed or explicitly bounded retry; no deadlock or unbounded retry. |
| Duplicate authenticated generation / hidden fork | Detected fork -> CLOSED; model hidden fork as a broken issuer assumption, never falsely claim counter equality detects it. |
| Lost/ambiguous advance response | Read back and reconcile; no blind re-advance; mismatch or unverifiable state -> CLOSED. |

## Exact next Codex task

Implement only a research-only synthetic harness, preferably `research/validation/p2f_private_authority.py`, `p2f_private_custody.py` and focused `test_p2f_private_*.py`. Define a deterministic monotonic-anchor interface, an explicit non-equivocating issuer/commitment *assumption* with failure modes, authenticated-ledger and current-inventory models, scope-bound fake key boundary, one broker gate with separate receipt/read/decrypt/publication events, durable transition-intent/commit/ack and restart/reconciliation model. `current(scope)`, `transition(scope, expected_generation, intent)`, `with_authorized_read(scope, callback)` and `restore_closed(snapshot)` are candidate boundaries, not permission to connect existing private ingestion. Use fail-closed states and deterministic fault scheduling; add tests for every matrix row before declaring completion. Preserve existing synthetic journal and frozen Session G. Test only generated fixtures and abstractions; no TPM/TBS mutation, DPAPI/CNG, networking, credentials, Garmin, real data or production paths. Run focused tests, P2 suite and full research suite; request a separate independent adversarial review of the exact resulting head. Actual private-pilot implementation remains separately gated on admission, authority uniqueness/antirollback, authenticated broker and encrypted key custody, trusted time for deadlines, inventory/deletion/restore drills, independent security and device/reference qualification.
