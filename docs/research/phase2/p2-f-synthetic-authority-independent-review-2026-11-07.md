# Independent adversarial review — synthetic authority/custody

Verdict: `SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION`. Review date 2026-11-07 (Asia/Manila). Implementation `6adab6de58ed8ee39360ffb849c7cd500d21becd`; reviewed live branch head `3a494ae612aff4a775d550579b16d772699daacb`. The implementation-to-head diff contains only two handoffs and the implementation record. No real athlete/provider data, real TPM, keys or credentials were accessed. This is an independent agent review of the implementation, not a professional security certification or separately staffed human signoff.

## Findings

1. **[P1] Data rollback after authorization is published.** `Broker.read` checks `data_versions[obj] == version` once before the phase loop (`p2f_private_custody.py:69-70`). A callback after `authorize` can replace `data_versions['object']` with an older version while the inventory remains current. Subsequent phase/chunk checks validate the inventory but never the data version again; release, decrypt and publish complete. This violates the accepted data-currentness boundary. Existing `test_changed_inventory_between_authorize_and_release_denies` mutates the inventory, not the modeled data version. Minimal correction: under the same synchronized validation boundary, revalidate the actual modeled object version against the independent inventory immediately before release, decryption and each publication; account for concurrent changes, not just dictionary comparison once. Do not infer real immutable-storage guarantees from this in-memory result.
2. **[P1] BaseException can leave a pending restrictive intent readable.** `_transition` catches `Exception`, not `BaseException` (`p2f_private_custody.py:160-162`). Inject `KeyboardInterrupt` in `Anchor.advance` before increment: `ledger.intent` is populated, the exception escapes, `busy` clears, but `closed` remains false and an active read publishes. The tested injected crashes are `Denied` exceptions, so they exercise the catch path but not interruption. Minimal correction: make interrupted restrictive transitions close and require channel/intent reconciliation even for non-`Exception` exits; test interruptions before and after anchor advancement. This is a caught/intercepted interrupt in a still-running process, not a claim about disk durability.
3. **[P2] A caught nested read can publish.** `Broker.read` fails to acquire `_gate` and raises `Denied('contention')` at entry without marking `_violation`; a callback can catch that denial during decrypt and the outer read publishes. Existing `test_reentrant_mutation_cannot_be_swallowed_then_publish` covers nested `transition`, while nested `read` is not checked. The requested model says caught nested failures cannot allow the outer protected operation to publish. Correct entry handling while preserving ordinary concurrent-reader semantics and avoid letting unrelated contention corrupt another operation unless that is the defined contract.
4. **[P2] Repeated withdrawal receipt double-counts already escaped output.** `receive_withdrawal` appends the entire accumulated `key.output` on every receipt (`p2f_private_custody.py:107-111`). One published chunk followed by two identical receipts yields two escaped entries. Existing `test_repeated_withdrawal_ack_does_not_advance_again` checks acknowledgement, not repeated receipt or exposure accounting. Record each emitted chunk once, including a failed partial read followed by withdrawal; do not erase evidence of actual earlier publication.

Review-only executable reproduction: `research/validation/review_p2f_authority_adversarial.py`. Run `py -3.12 research/validation/review_p2f_authority_adversarial.py` from repository root. At the reviewed head six controlled schedules ran: two passed (head mutation denial, uncertain-advance readback); four failed exactly as findings 1–4. This intentionally failing script is not in default `test_*.py` discovery. No harness implementation correction was made; the user requested a bounded follow-up fix rather than a substantial change during review.

## Graphify and source verification

Hosted graph: `Isshhin/stridelab-engine2-graph`, indexed SHA `3a494ae612aff4a775d550579b16d772699daacb`, 1,221 nodes / 3,329 edges. Queried rank-files for authority/custody reads, transitions and recovery; symbol-find for `Anchor`, `Issuer`, `Ledger`, `Inventory`, `Broker` and `FakeKey`; impact/callees for `Broker.read`, `Broker.transition`, `Broker.reconcile`, `Inventory` and `Anchor.advance`; and linked tests for those and issuer/withdrawal paths. Graphify identified `test_crash_at_each_transition_boundary`, `test_replayed_operation_idempotent_and_collision_denied`, `test_changed_inventory_between_authorize_and_release_denies`, and `test_publication_and_withdrawal_have_shared_fence` among related tests. These edges are navigation aids, not coverage proofs. Notable discrepancy: Graphify resolved `Anchor.advance`'s `verify()` callee to `Inventory.verify`; live source shows `Anchor.verify`. Its `Broker.read` call listing also omitted dynamic inventory verification; source was inspected directly.

Live GitHub files fetched at implementation SHA: `research/validation/p2f_private_authority.py`, `research/validation/p2f_private_custody.py`, and `research/validation/test_p2f_private_authority.py`. The implementation record, design review, contract and canonical handoffs were read from the exact checked-out live branch. Impact of corrections centers on broker read/transition/receipt behavior, `AuthorityMatrix`, and synthetic acceptance tests; no production integration is authorized.

## 30-case acceptance mapping audit

Mapping references test names in `test_p2f_private_authority.py` with `test_` omitted. **Covered** means the named test reaches the modeled path and asserts the stated narrow result, not that the entire semantic case or real infrastructure is proven. **Weak** identifies a missing schedule found here. Cases are counted individually even where the implementation record groups them.

| # | Design acceptance case | Mapped test(s) | Audit |
|---|---|---|---|
| 1 | Stale grant | `stale_grant_and_generation` | Covered: restored prior head is rejected. |
| 2 | Stale generation | `stale_grant_and_generation` | Covered: stale anchor verification fails. |
| 3 | Ledger rollback | `ledger_rollback_and_generation_mismatch` | Covered: generation mismatch closes. |
| 4 | Data rollback | `data_rollback_and_removed_unknown_descendants`, `stale_data_with_current_authority_is_rejected` | **Weak:** pre-read inventory mutations tested; post-authorization data rollback publishes (finding 1). |
| 5 | Generation mismatch | `stale_inventory_generation_denies_before_key_release` | Covered: stale inventory rejects before fake release. |
| 6 | Missing anchor | `missing_anchor_object_denies_before_key_release` | Covered: no fake release. |
| 7 | Unavailable anchor | `missing_unavailable_corrupt_and_wrong_anchor` | Covered for fake faults and re-entry. |
| 8 | Corrupt authority | `corrupt_authority_and_unavailable_issuer`, `authenticated_head_fields_must_match_commitment` | Covered for modeled field/issuer checks; not cryptographic authentication. |
| 9 | Concurrent read/revoke | `threaded_read_and_withdrawal_are_fenced` | Covered for deterministic decrypt-phase schedule; not all schedules. |
| 10 | Revoke during read | `withdrawal_during_read_and_during_output`, `withdrawal_after_authorization_prevents_key_release` | Covered for named modeled phases. |
| 11 | Revoke during output | `withdrawal_after_partial_publication_records_escape` | **Weak:** one receipt; repeated receipt double-counts (finding 4). |
| 12 | Crash before revoke commit | `crash_before_durable_intent_requires_channel_reconciliation`, `crash_at_each_transition_boundary` | **Weak:** `Denied` fault only; intercepted `BaseException` permits read (finding 2). |
| 13 | Crash after anchor advance | `crash_after_advance_recovers_only_restrictive_intent` | Covered for injected `Denied`; actual process/disk crash not modeled. |
| 14 | Crash after ledger commit | `crash_after_ledger_before_ack_is_idempotent` | Covered for modeled fault and replay. |
| 15 | Restore before withdrawal | `restore_restart_reinstall_and_copied_store` | Covered for invented same-authority state, not physical backup. |
| 16 | Restore after withdrawal | `restore_cannot_replace_live_authority_or_inventory` | Covered for modeled stale restored head. |
| 17 | Restore after deletion | `restore_after_delete_never_reactivates_data` | Covered for modeled tombstone. |
| 18 | Copied store | `restore_restart_reinstall_and_copied_store` | Covered only for fake identity/inventory and explicit enrollment; not real copies or hardware. |
| 19 | Account mismatch | `wrong_account_purpose_and_untrusted_inventory`, `wrong_scope_never_releases_key` | Covered for modeled account/scope before fake key release. |
| 20 | Wrong purpose | `wrong_account_purpose_and_untrusted_inventory` | Covered for modeled purpose denial. |
| 21 | Duplicate/replayed operation | `replayed_operation_idempotent_and_collision_denied`, `replayed_operation_with_changed_expected_generation_is_collision` | Covered for selected same-ID cases; not all concurrent transitions. |
| 22 | Restart | `new_broker_restart_requires_explicit_enrollment_and_reconciliation`, `restart_during_read_cancels_before_publication` | Covered for explicit in-process model, not OS restart persistence. |
| 23 | Reinstall/bootstrap | `bootstrap_needs_explicit_trusted_enrollment`, `fresh_enrollment_remains_closed_until_reconciled` | Covered for fake enrollment; not actual OS reinstall. |
| 24 | Failed key release | `failed_key_audit_time_and_mutation` | Covered for injected fake failure, not real key custody. |
| 25 | Failed audit write | `failed_audit_cannot_acknowledge_revoke`, `audit_failure_cannot_be_bypassed_by_restart_reconcile` | Covered for injected `key.fault`; no real audit failure/durability model. |
| 26 | Reentrant mutation | `reentrant_mutation_cannot_be_swallowed_then_publish`, `restore_and_reconcile_cannot_run_inside_read` | **Weak:** mutation examples pass; caught nested read publishes (finding 3). |
| 27 | Contention | `contention_fails_closed`, `threaded_read_and_withdrawal_are_fenced` | Covered for selected two-thread schedules; no broad liveness proof. |
| 28 | Duplicate generation/detected fork | `two_authenticated_heads_same_generation_fail`, `detected_and_hidden_forks` | Covered for the modeled issuer oracle. |
| 29 | Hidden fork | `detected_and_hidden_forks` | Correctly reports violated issuer assumption rather than claiming counter detection. |
| 30 | Lost/ambiguous advance | `lost_advance_response_is_reconciled_without_retry`, `ambiguous_before_advance_requires_readback` | Covered for modeled denial/readback; real counter persistence and ambiguous hardware behavior unproven. |

## Independent verification and assessment

CPython 3.12 on Windows: authority/custody suite 57 passed; existing durable suite 54 passed; P2 suite 205 passed; full research 369 run, 368 passed and one Windows symlink test skipped. These independently repeated the implementation report's counts. Additional review schedules: 6 run, 2 passed, 4 failed. GitHub Actions run `42287780335` was cancelled; it is not a pass. No assertion is made about the reason for cancellation.

Anchor: selected modeled stale/ambiguous and readback tests pass; the interruption defect prevents a complete fail-closed verdict. Issuer: detected and hidden fork distinction is preserved in the idealized oracle; real non-equivocation is not demonstrated. Inventory: a current inventory detects stale data at entry but not data-version mutation during the read. Revoke/read: tested receipt fence works for selected schedules, but repeated receipt corrupts exposure accounting and caught nested read can publish. Transition/recovery: deterministic `Denied` fault cases and idempotence tests pass; interrupted pending intent is unsafe. No cryptographic, TPM, independent inventory persistence, disk crash, key custody, deletion, provider or device property is established.

## Exact next step

Run a bounded synthetic-only fix task against the live branch: preserve newer work; add the four failing review schedules to the normal focused suite; correct data-version revalidation, interruption closure, nested read handling and idempotent escape accounting; include all corresponding before/after/partial-publication and restart schedules. Re-run focused, durable, P2 and full research suites, then obtain separate adversarial review. Do not use real TPM, DPAPI/CNG, provider data, credentials, Engine 1.5, Trackside, production or release paths. `PASS_FOR_SYNTHETIC_DURABILITY` remains limited to its previously reviewed journal; private-pilot readiness is NOT READY, human/provider admission NO, `P2_F_GATE_2_NOT_READY`, `P2_B_PROTOCOL_READY_DATA_NOT_READY`.
