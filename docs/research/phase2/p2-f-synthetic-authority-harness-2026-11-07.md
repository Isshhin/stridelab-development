# Synthetic authority and custody harness — 2026-11-07

Implementation: `6adab6de58ed8ee39360ffb849c7cd500d21becd`. Status: `SYNTHETIC_AUTHORITY_CUSTODY_HARNESS_IMPLEMENTED`, awaiting independent adversarial review. The earlier `PASS_FOR_SYNTHETIC_DURABILITY` remains unchanged. This is an invented-data model, isolated from ingestion and the F2.2 journal.

`p2f_private_authority.py` models an anchor, issuer commitments, authority head and separate current inventory. `p2f_private_custody.py` models a gated broker, fake key steps, withdrawal fencing, intent/reconciliation and restore. The test file exercises injected failures and controlled thread schedules. Authority and inventory are rechecked at each read phase and output chunk; escaped output is recorded. Recovery begins closed and requires reconciliation. A hidden issuer fork is reported by the test oracle as a violated assumption; the broker cannot detect an unseen alternative.

The 30 acceptance cases map to `AuthorityMatrix` tests as follows (names below omit `test_`):

| Cases | Focused tests |
| --- | --- |
| 1–2 stale grant/generation | `stale_grant_and_generation` |
| 3–5 ledger/data rollback, generation mismatch | `ledger_rollback_and_generation_mismatch`, `data_rollback_and_removed_unknown_descendants`, `stale_inventory_generation_denies_before_key_release` |
| 6–8 missing/unavailable anchor, corrupt authority | `missing_unavailable_corrupt_and_wrong_anchor`, `missing_anchor_object_denies_before_key_release`, `corrupt_authority_and_unavailable_issuer` |
| 9–11 concurrent revoke, read and output | `threaded_read_and_withdrawal_are_fenced`, `withdrawal_during_read_and_during_output`, `withdrawal_after_partial_publication_records_escape` |
| 12–14 transition crashes | `crash_before_durable_intent_requires_channel_reconciliation`, `crash_after_advance_recovers_only_restrictive_intent`, `crash_after_ledger_before_ack_is_idempotent`, `crash_at_each_transition_boundary` |
| 15–18 restore before/after withdrawal/deletion, copied store | `restore_restart_reinstall_and_copied_store`, `restore_cannot_replace_live_authority_or_inventory`, `restore_after_delete_never_reactivates_data` |
| 19–20 account and purpose | `wrong_account_purpose_and_untrusted_inventory`, `inventory_binds_full_scope_not_only_account` |
| 21–23 operation replay, restart, bootstrap | `replayed_operation_idempotent_and_collision_denied`, `new_broker_restart_requires_explicit_enrollment_and_reconciliation`, `bootstrap_needs_explicit_trusted_enrollment` |
| 24–27 key, audit, reentrancy, contention | `failed_key_audit_time_and_mutation`, `failed_audit_cannot_acknowledge_revoke`, `reentrant_mutation_cannot_be_swallowed_then_publish`, `contention_fails_closed` |
| 28–30 duplicate/detected fork, hidden fork, lost/ambiguous advance | `two_authenticated_heads_same_generation_fail`, `detected_and_hidden_forks`, `lost_advance_response_is_reconciled_without_retry`, `ambiguous_advance_is_not_blindly_retried` |

Local Windows/Python 3.12 results at the implementation commit: focused 57/57; existing durable 54/54; P2 205/205; full research 369 run, 368 passed, one skipped because the runner cannot create symlinks. No remote CI success is implied by these results.

Limitations: state and transition durability are simulated in process, not a proof of disk crash persistence or tamper resistance. Authentication, commitments, key operations and trusted time are synthetic. Scoped withdrawal conservatively invalidates objects in the same modeled scope; it is not a general provenance graph or deletion system. The independent issuer/non-equivocation assumption is not established by a counter. Actual admission, authenticated authority, hardware/key custody, cryptography, independent inventory, source qualification, suppression/deletion and safe restore remain separate private-pilot blockers. `PRIVATE_PILOT_READY` is false; human/provider admission is NO; `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain in force. Next: independent adversarial review of the exact implementation commit, then resolve its findings before any separately authorized private-pilot work.
