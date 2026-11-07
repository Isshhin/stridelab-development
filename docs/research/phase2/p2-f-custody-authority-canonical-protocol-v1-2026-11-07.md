# P2-F Custody/Authority Canonical Protocol v1

Status: additive v7 correction; ready for independent review only. This document freezes the application protocol; it does not authorize implementation or execution.

## Encoding and identity

All protocol objects use deterministic CBOR under [RFC 8949](https://www.rfc-editor.org/rfc/rfc8949), with definite-length maps, arrays, byte strings and text strings only. Maps use integer keys in ascending numeric order. Duplicate or unknown keys, tags, floats, simple values, null, booleans, indefinite lengths, non-minimal integers, and unknown enum values are rejected. Core objects contain no free-form text. Required fields are never defaulted; optional fields are absent, not null. `u64` means 0..2^64-1; `u32` means 0..2^32-1; fixed digests and IDs are 32-byte bstr values unless stated otherwise.

The profile identifier is `StrideLab/P2F/v1`. A semantic field change requires a new protocol/profile version; v1 has no forward-compatible unknown-field behavior.

Domain-separated digests are exactly:

```text
request = SHA-256("StrideLab/P2F/v1/request\0" || request_bytes)
event   = SHA-256("StrideLab/P2F/v1/event\0" || event_bytes)
cert    = SHA-256("StrideLab/P2F/v1/cert\0" || certificate_without_signatures_bytes)
policy  = SHA-256("StrideLab/P2F/v1/policy\0" || policy_bytes)
```

The exact canonical structured request, including `operation_id`, is the request identity. Different operation IDs or canonical requests are distinct unless a future protocol explicitly defines a higher-level deduplication rule.

## Trust and finality

The immutable Genesis Trust Manifest v1 authenticates `trust_domain_id`, log identity/key, four transparency-witness identities/keys, four authority-validator identities/keys, quorum 3-of-4, initial `policy_epoch`, approved SHA-256 and CBOR profile, coordinator and gateway trust roots, and protocol version. Distribution pins the manifest out of band; mutable coordinator state cannot select or replace it.

The bootstrap chain is:

```text
genesis manifest -> certified control history -> current policy
-> coordinator epoch -> gateway epochs -> namespace histories -> caches
```

Transparency consistency proves only that a checkpoint is an append-only extension under the current transparency policy. A StrideLab Authority Certificate proves that the candidate is the unique legal successor of the prior certified state. `WITNESSED_FINAL` requires both proofs and identical Authority Certificates from at least 3 of 4 authority validators. The certificate role is distinct from generic log witnessing, preferably with distinct keys.

Each validator reconstructs prior certified state and checks genesis, policy, log origin/checkpoint, inclusion position, namespace, predecessor, sequence/generation, coordinator/gateway epochs, request digest, schema, transition, output/send state, withdrawal and replay rules before signing. Result classes are: `VALID_SUCCESSOR`, `INVALID_SCHEMA`, `INVALID_PREDECESSOR`, `INVALID_STATE_TRANSITION`, `STALE_POLICY`, `STALE_COORDINATOR_EPOCH`, `STALE_GATEWAY_EPOCH`, `DUPLICATE_OPERATION_CONFLICT`, `SEND_ALREADY_CONSUMED`, `WITHDRAWAL_FENCE_VIOLATION`, and `CLOSED_PENDING_RECONCILIATION`.

For every predecessor digest, a validator durably stores `latest_final_certificate` and at most one `locked_pending_candidate`. Before signing it atomically persists `predecessor -> candidate_digest`; it never signs a different candidate for that predecessor. The lock survives restart under the stated durability assumption. A 2-vs-2 split closes the namespace for manual adjudication or permanent unavailability; there is no automatic unlock. A log inclusion without final certification is not authoritative.

## State machines and epochs

Authority states are `0 UNISSUED`, `1 ACTIVE`, `2 WITHDRAWAL_FENCED`, `3 WITHDRAWN`, `4 DELETED`. The only authority transitions are `UNISSUED --AUTHORITY_ISSUED--> ACTIVE`, `ACTIVE --WITHDRAWAL_FENCE--> WITHDRAWAL_FENCED`, `WITHDRAWAL_FENCED --WITHDRAWAL_ACCOUNTED--> WITHDRAWN`, and `WITHDRAWN --DELETE--> DELETED`. `WITHDRAWN` and `DELETED` never reactivate; a later authorization uses a new, never-recycled `namespace_id`. `OPEN` and `CLOSED` are runtime conditions, not authority states.

`COORDINATOR_EPOCH_START` is certified, has a fresh fixed-length random `epoch_id`, references the current certified control predecessor, and is subject to the same unique-successor lock. Every candidate binds the currently certified epoch; stale epochs are rejected. `GATEWAY_EPOCH_START` and `GATEWAY_EPOCH_REVOKE` follow the same control rules. Credentials alone do not establish currentness.

`POLICY_BRIDGE` binds old/new policy digests and epochs, activation control sequence, both validator/witness sets, and exact profile compatibility. It requires old-policy 3-of-4 and new-policy 3-of-4 approvals on the same bridge payload/checkpoint. If old quorum cannot be reconstructed, remain closed/permanently unavailable. Rollback of durable state may be invisible to the restored instance and consumes the one-Byzantine budget. Replacement requires a new key and bridge; validator signing is serialized and state is persisted before release. Shared administration, restore credentials or hosting violate independence.

Output state is separate: `OUTPUT_RESERVED` (non-final), `ESCAPE_COMMITTED`, `SEND_STARTED`, `SEND_COMPLETED`, `SEND_STOPPED`, `SEND_UNKNOWN`. An output authorization binds exactly `output_id`, `namespace_id`, `content_root`, `content_length`, `recipient_ref`, `purpose`, `release_id`, gateway/coordinator epochs, request identity, `send_instance_id`, and gateway process/ephemeral binding. `ESCAPE_COMMITTED` must be final before sensitive bytes leave the gateway and conservatively treats the whole bound content as exposed. Exactly one final `SEND_STARTED` is permitted per output/release key and consumes the attempt; uncertain outcome becomes `SEND_UNKNOWN` and is never resent. Restart cannot reuse a permit. A new attempt needs a new release authorization. No escape or send certifies after withdrawal fence finality.

`WITHDRAWAL_FENCE_FINAL` stops new releases and sends. `WITHDRAWAL_ACCOUNTED` is allowed only after every pre-fence output is terminally `SEND_COMPLETED`, `SEND_STOPPED` or `SEND_UNKNOWN`. The protocol does not claim recall or erasure from OS, network or recipient buffers.

## Event envelope and payloads

Every event envelope is a map with keys `{0:1 /*version*/, 1:event_type, 2:trust_domain_id, 3:namespace_id-or-empty, 4:namespace_sequence, 5:authority_generation, 6:coordinator_epoch, 7:gateway_epoch-or-empty, 8:previous_final_digest-or-zero32, 9:operation_id, 10:request_digest, 11:payload}`. Empty namespace/gateway values are the fixed zero32 sentinel only where the event table permits them. Payloads are maps with the exact integer-key sets below; all IDs/digests are fixed 32-byte bstrs, `sequence`/`generation`/epochs are u64, `length` is u64, and `purpose` is the enum below.

| Event value | Event | Allowed predecessor/state | Exact payload keys |
|---:|---|---|---|
| 1 | CONTINUITY_CHALLENGE | control predecessor | `0 challenge_id, 1 challenged_log_checkpoint, 2 nonce32, 3 expires_at` |
| 2 | COORDINATOR_EPOCH_START | control predecessor | `0 epoch_id, 1 predecessor_control_digest, 2 coordinator_key_id` |
| 3 | GATEWAY_EPOCH_START | control predecessor | `0 gateway_epoch_id, 1 predecessor_control_digest, 2 gateway_key_id` |
| 4 | GATEWAY_EPOCH_REVOKE | current gateway epoch | `0 gateway_epoch_id, 1 reason` |
| 5 | POLICY_BRIDGE | current policy | `0 old_policy_digest, 1 new_policy_digest, 2 new_policy_epoch, 3 activation_control_sequence, 4 old_validator_set, 5 new_validator_set, 6 old_witness_set, 7 new_witness_set, 8 profile_version` |
| 16 | AUTHORITY_ISSUED | UNISSUED | `0 grant_id, 1 authority_subject_ref, 2 authority_scope_digest` |
| 17 | ESCAPE_COMMITTED | ACTIVE; no prior escape for release | `0 output_id, 1 content_root, 2 content_length, 3 recipient_ref, 4 purpose, 5 release_id` |
| 18 | SEND_STARTED | final ESCAPE_COMMITTED; unused release | `0 output_id, 1 release_id, 2 send_instance_id, 3 gateway_process_binding` |
| 19 | SEND_COMPLETED | SEND_STARTED | `0 output_id, 1 release_id, 2 send_instance_id, 3 completion_digest` |
| 20 | SEND_STOPPED | SEND_STARTED | `0 output_id, 1 release_id, 2 send_instance_id, 3 stop_reason` |
| 21 | SEND_UNKNOWN | SEND_STARTED with uncertain outcome | `0 output_id, 1 release_id, 2 send_instance_id, 3 uncertainty_reason` |
| 22 | WITHDRAWAL_FENCE | ACTIVE | `0 withdrawal_id, 1 fence_reason` |
| 23 | WITHDRAWAL_ACCOUNTED | WITHDRAWAL_FENCED and all sends terminal | `0 withdrawal_id, 1 accounted_output_digest, 2 accounted_count` |
| 24 | DELETE | WITHDRAWN | `0 tombstone_id, 1 deletion_reason` |

Enums: `OutputPurpose = 1 USER_EXPORT, 2 BACKUP, 3 PUBLICATION, 4 DERIVED_OUTPUT, 5 API_RESPONSE, 6 SYNC`; `SendStopReason = 1 WITHDRAWAL, 2 GATEWAY_REVOKED, 3 OPERATOR_STOP`; `UncertaintyReason = 1 CRASH_AFTER_START, 2 TRANSPORT_UNKNOWN, 3 EVIDENCE_INCOMPLETE`; `Disposition = 1 VALIDATE_AND_CERTIFY, 2 NOT_AUTHORITATIVE_RETRY_ALLOWED, 3 RETURN_PRIOR_FINAL_RESULT, 4 SEND_ALREADY_CONSUMED, 5 CONSERVATIVELY_CLASSIFY_SEND_UNKNOWN, 6 CLOSED_PENDING_RECONCILIATION, 7 CLOSED_MANUAL_ADJUDICATION, 8 PERMANENT_NAMESPACE_UNAVAILABLE`.

Canonical requests are separate maps: authority issue `{0:1,1:operation_id,2:namespace_id,3:subject_ref,4:scope_digest}`, escape `{0:1,1:operation_id,2:namespace_id,3:output_id,4:content_root,5:content_length,6:recipient_ref,7:purpose,8:release_id}`, and send `{0:1,1:operation_id,2:namespace_id,3:output_id,4:release_id,5:send_instance_id,6:gateway_process_binding}`. A request digest must exactly match its canonical bytes.

## Sources and limits

The encoding rules follow [RFC 8949](https://www.rfc-editor.org/rfc/rfc8949); transparency terminology follows the applicable [C2SP witness specifications](https://c2sp.org/); Windows API names are documented by [RtlGetVersion](https://learn.microsoft.com/windows-hardware/drivers/ddi/wdm/nf-wdm-rtlgetversion) and [GetNativeSystemInfo](https://learn.microsoft.com/windows/win32/api/sysinfoapi/nf-sysinfoapi-getnativesysteminfo), with PE inspection governed by [PE/COFF](https://learn.microsoft.com/windows/win32/debug/pe-format). These sources support terminology and formats, not target qualification or security evidence.
