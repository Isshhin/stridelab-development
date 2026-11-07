# Synthetic authority and custody harness — 2026-11-07

## Publication-fence correction — 2026-11-07

Starting head `9dbb37e01740812ced317ee08de9436a92b1874d`; correction implementation `00257401e66ec2be8dbd1b0fc8168cd2e4c21d31`. Final pushed head is the documentation commit containing this entry (resolve its SHA with Git). The independent [re-review](p2-f-synthetic-authority-correction-rereview-2026-11-07.md) found that an iterator could catch a nested-read denial before yielding and cause the poisoned chunk to be emitted before the violation check. The broker now checks the violation inside the existing publication lock before every append, after obtaining each candidate chunk. Iterator code remains outside that lock. Authority, inventory, data-version and withdrawal checks remain in the publication boundary.

Eight focused regressions cover nested reads before first yield and between chunks, normal iteration, competing-thread contention, withdrawal, data-version and authority changes before yield, and iterator failure after partial output. Before the fix, both nested-generator focused tests and the re-review's nested-generator schedule failed by observing the forbidden output. After the fix: focused 71 passed; original review 6 passed; correction re-review 8 passed; durable 54 passed; P2 219 passed; full research 383 run, 382 passed and one Windows symlink skip. Prior CI run `42317715858` was cancelled, not passed; the new exact-head CI result is not available at this documentation commit.

Status `SYNTHETIC_AUTHORITY_CUSTODY_PUBLICATION_FENCE_CORRECTED` awaits independent adversarial re-review; it is not `PASS_FOR_SYNTHETIC_AUTHORITY_CUSTODY`. In-process, invented-only abstractions do not prove physical crash persistence, cryptography, hardware security or private custody. The separate `PASS_FOR_SYNTHETIC_DURABILITY` is unchanged; private-pilot readiness remains blocked, human/provider admission NO, `P2_F_GATE_2_NOT_READY`, and `P2_B_PROTOCOL_READY_DATA_NOT_READY`. Next: refresh the hosted graph after push and independently re-review the exact correction head and publication schedules.

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
