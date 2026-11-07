# P2-F v7 closure supplement

> HISTORICAL / SUPERSEDED IN FULL by the [v7 correction closure](p2-f-custody-authority-v7-correction-closure-2026-11-07.md). All schemas and readiness/next-action wording below describe the earlier reviewed proposal, not current authority. Collector evidence remains reference evidence only; execution remains blocked.

Status: bounded v7 closure; ready for a new independent review only. This supplement supersedes conflicting v7 wording while preserving v1-v6 history. It does not authorize execution, target access, TPM/TBS, key provisioning, implementation, deployment, Gate 2, or P2-B.

## Frozen type and object rules

All IDs and digests listed by the v7 request are exactly `bstr(32)`; `ZERO32` is permitted only where the envelope/event table says so. Numeric fields are unsigned integers: protocol version `u8=1`, event/reason/purpose/result enums `u8`, profile version `u16=1`, and policy/control/namespace/generation/length/count values `u64`. Deterministic CBOR is RFC 8949 definite-length integer-key maps with ascending keys, minimal integers, and no duplicate/unknown keys, tags, floats, null, booleans, text in bstr positions, or indefinite lengths.

## PolicyV1

`{0:1,1:trust_domain_id,2:policy_epoch,3:c2sp_policy_digest,4:authority_validator_public_keys[4],5:3,6:coordinator_root_public_key,7:gateway_root_public_key,8:1,9:1,10:1}`. Validator keys are unique and strictly ascending raw bstr order. `policy_digest = SHA-256("StrideLab/P2F/v1/policy\\0" || policy_bytes)`. The C2SP digest is over exact immutable artifact bytes. A v1 bridge preserves transparency-log identity/origin and requires old/new policy authority approvals.

## Request, EventEnvelope, and certificate

Request: `{0:1,1:event_type,2:operation_id,3:trust_domain_id,4:namespace_id,5:request_payload}`; control namespace is `ZERO32`. The payload is exactly the semantic event payload, excluding derived predecessor/state fields. `request_digest = SHA-256("StrideLab/P2F/v1/request\\0" || full_request_bytes)`.

EventEnvelope: `{0:1,1:event_type,2:trust_domain_id,3:policy_epoch,4:policy_digest,5:control_sequence,6:control_head_digest,7:namespace_id,8:namespace_sequence,9:authority_generation,10:coordinator_epoch_id,11:gateway_epoch_id,12:previous_final_digest,13:operation_id,14:request_digest,15:payload}`. Control namespace sequence/generation are zero; gateway ID is `ZERO32` when not bound. `event_digest = SHA-256("StrideLab/P2F/v1/event\\0" || full_envelope_bytes)`.

CertificateBodyV1: `{0:1,1:trust_domain_id,2:policy_epoch,3:policy_digest,4:checkpoint_digest,5:candidate_event_digest,6:previous_final_digest,7:control_head_digest,8:namespace_id,9:control_sequence,10:namespace_sequence,11:authority_generation,12:coordinator_epoch_id,13:gateway_epoch_id,14:event_type,15:1}`. `cert_digest = SHA-256("StrideLab/P2F/v1/cert\\0" || body_bytes)`. ApprovalSet is `{0:approval_policy_digest,1:[[validator_index,signature64],...]}` with 3 or 4 unique ascending indexes. AuthorityFinalityProof is `{0:certificate_body,1:[approval_sets]}`; normal events have one set, POLICY_BRIDGE has exactly old and new sets over the same body. Checkpoint digest hashes exact log-signed checkpoint bytes before witness cosignature lines.

## Event payload closure

The complete event table and enum values are frozen in the task's v7 closure contract and represented in the generated vector fixture. `CONTINUITY_CHALLENGE` has only challenge ID, challenged checkpoint digest, nonce32, and expected control sequence: no wall-clock expiry. `POLICY_BRIDGE` carries old/new policy digests, old/new epochs, and activation sequence; exact policy objects are separately available and hashed. `SEND_STARTED` has no process-binding field; its certified gateway epoch and detached gateway signature bind the send. Withdrawal fence/accounting explicitly provide no packet recall, transport quiescence, recipient erasure, or durable-recipient claim.

## Vectors and artifact

Run `research/tools/p2f_v7_closure_vectors.py` in a non-target environment. It regenerates full request, full EventEnvelope, and CertificateBody bytes/digests for continuity, authority issue, escape, send start, withdrawal fence, and policy bridge, and self-checks every digest. Fixture keys/IDs are deterministic and test-only. The prior payload-fragment vector file remains historical; the generated closure file is authoritative for v7 review.

The collector source was replaced by a no-CRT console-only source. Prior source SHA-256 `00d06f56ce8ed62bab00fe72822dcb6f39c17022a7a0d804dbebf741e7bd4eff` is superseded. The replacement dynamically resolves `RtlGetVersion`, calls exactly `RtlGetVersion` and `GetNativeSystemInfo`, formats two lines, and writes with `GetStdHandle`/`WriteConsoleA`. It has no filesystem, registry, network, child-process, elevation, identifier, or CRT behavior. The research-only `windows-2025` workflow performs static PE inspection and must never execute the EXE.

## Decisions and status

- `AUTHORITY_VALIDATION_MODEL_READY_FOR_INDEPENDENT_REVIEW`
- `WITNESS_VALIDATOR_LIFECYCLE_READY_FOR_INDEPENDENT_REVIEW`
- `ONE_SHOT_OUTPUT_WITHDRAWAL_MODEL_READY_FOR_INDEPENDENT_REVIEW`
- `CANONICAL_AUTHORITY_PROTOCOL_V1_READY_FOR_INDEPENDENT_REVIEW`
- `READONLY_PREFLIGHT_PACKET_READY_FOR_INDEPENDENT_REVIEW` only after hosted build metadata is captured; until then binary identity remains blocked
- Overall: `P2F_CUSTODY_AUTHORITY_V7_READY_FOR_INDEPENDENT_REVIEW` if the generator self-check and hosted static build succeed; otherwise `P2F_CUSTODY_AUTHORITY_V7_CORRECTIONS_INCOMPLETE`

The next permitted task is one new independent v7 review. The collector remains unexecuted.
