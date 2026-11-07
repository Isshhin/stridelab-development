# P2-F v7 closure canonical test vectors

Normative fixtures subordinate to [the v7 correction closure](p2-f-custody-authority-v7-correction-closure-2026-11-07.md). Earlier payload-only vectors and six-event output are historical/non-authoritative.

Complete bytes, digests, signatures and starting-history references: [JSON fixture](p2-f-custody-authority-v7-closure-test-vectors-2026-11-07.json). PUBLIC invented test material only; never deploy these keys/IDs.

Regenerate: `python research/tools/p2f_v7_closure_vectors.py`; check drift: add `--check`. Python stdlib + installed Node crypto. No package installation, operational key provisioning, collector or hardware.

## Exact self-check results

```json
{
  "accepted_finalities": 34,
  "event_types": [
    1,
    2,
    3,
    4,
    5,
    16,
    17,
    18,
    19,
    20,
    21,
    22,
    23,
    24
  ],
  "exact_replays": 1,
  "lock_lifecycle_schedules": 8,
  "objects": 365,
  "rejections": 53,
  "rfc8032_known_answer": "PASS"
}
```

PASS: every accepted finality is parsed, request/state/proof-bound and signature-verified; each negative actually rejects at its named check. RFC8032 empty-message known-answer sign/verify and wrong-message rejection PASS. Synthetic lock/lifecycle records specify obligations, not real durability/independence tests.

JSON raw UTF-8 SHA256: `94b77f53680d46ed9f685c6e63ae581f33ece8e016228434395df03777b9090d`.

## Scenario catalogue

| Scenario | Event | Expected result |
|---|---|---|
| coordinator-start | COORDINATOR_EPOCH_START | `VALID_SUCCESSOR` |
| continuity | CONTINUITY_CHALLENGE | `VALID_SUCCESSOR` |
| gateway-start | GATEWAY_EPOCH_START | `VALID_SUCCESSOR` |
| authority-issued | AUTHORITY_ISSUED | `VALID_SUCCESSOR` |
| escape-a | ESCAPE_COMMITTED | `VALID_SUCCESSOR` |
| start-a | SEND_STARTED | `VALID_SUCCESSOR` |
| completed | SEND_COMPLETED | `VALID_SUCCESSOR` |
| escape-b | ESCAPE_COMMITTED | `VALID_SUCCESSOR` |
| start-b | SEND_STARTED | `VALID_SUCCESSOR` |
| stopped | SEND_STOPPED | `VALID_SUCCESSOR` |
| escape-c | ESCAPE_COMMITTED | `VALID_SUCCESSOR` |
| start-c | SEND_STARTED | `VALID_SUCCESSOR` |
| gateway-revoke | GATEWAY_EPOCH_REVOKE | `VALID_SUCCESSOR` |
| unknown-after-revoke | SEND_UNKNOWN | `VALID_SUCCESSOR` |
| gateway-rotation | GATEWAY_EPOCH_START | `VALID_SUCCESSOR` |
| escape-c-new-explicit | ESCAPE_COMMITTED | `VALID_SUCCESSOR` |
| start-c-new-explicit | SEND_STARTED | `VALID_SUCCESSOR` |
| new-exposure-completed | SEND_COMPLETED | `VALID_SUCCESSOR` |
| escape-d | ESCAPE_COMMITTED | `VALID_SUCCESSOR` |
| start-d | SEND_STARTED | `VALID_SUCCESSOR` |
| withdrawal-fence | WITHDRAWAL_FENCE | `VALID_SUCCESSOR` |
| unknown-after-fence | SEND_UNKNOWN | `VALID_SUCCESSOR` |
| withdrawal-accounted | WITHDRAWAL_ACCOUNTED | `VALID_SUCCESSOR` |
| delete | DELETE | `VALID_SUCCESSOR` |
| policy-bridge | POLICY_BRIDGE | `VALID_SUCCESSOR` |
| coordinator-rotation | COORDINATOR_EPOCH_START | `VALID_SUCCESSOR` |
| continuity-new-policy | CONTINUITY_CHALLENGE | `VALID_SUCCESSOR` |
| exact-final-replay-after-policy-change | negative fixture | `RETURN_PRIOR_FINAL_RESULT` |
| wrong-request-digest | negative fixture | `WRONG_REQUEST_DIGEST` |
| request-envelope-mismatch | negative fixture | `REQUEST_ENVELOPE_MISMATCH` |
| wrong-predecessor | negative fixture | `INVALID_PREDECESSOR` |
| stale-control-head | negative fixture | `STALE_CONTROL_HEAD` |
| stale-coordinator-epoch | negative fixture | `STALE_COORDINATOR_EPOCH` |
| stale-gateway-epoch | negative fixture | `STALE_GATEWAY_EPOCH` |
| wrong-policy-digest | negative fixture | `STALE_POLICY` |
| wrong-sequence | negative fixture | `INVALID_SEQUENCE_GENERATION` |
| wrong-generation | negative fixture | `INVALID_SEQUENCE_GENERATION` |
| wrong-zero32-placement | negative fixture | `WRONG_ZERO32` |
| wrong-signature-domain | negative fixture | `INVALID_SIGNATURE` |
| wrong-validator-domain | negative fixture | `INVALID_SIGNATURE` |
| wrong-checkpoint | negative fixture | `WRONG_CHECKPOINT` |
| inclusion-other-checkpoint | negative fixture | `WRONG_INCLUSION_CHECKPOINT` |
| inclusion-wrong-path | negative fixture | `INVALID_INCLUSION_PATH` |
| consistency-unrelated-checkpoint | negative fixture | `WRONG_CONSISTENCY_CHECKPOINT` |
| consistency-wrong-prefix | negative fixture | `WRONG_CONSISTENCY_CHECKPOINT` |
| wrong-witness-policy | negative fixture | `WRONG_WITNESS_POLICIES` |
| bad-witness-signature | negative fixture | `WRONG_ZERO32` |
| bridge-mismatched-old-new-body | negative fixture | `INVALID_SIGNATURE` |
| bridge-wrong-old-epoch | negative fixture | `BRIDGE_BINDING_MISMATCH` |
| reject-policy-cross-role-active-coordinator-as-gateway-root | negative fixture | `ROLE_KEY_COLLISION` |
| reject-gateway-epoch-cross-role-retired-validator | negative fixture | `EPOCH_KEY_REUSE` |
| duplicate-send-started | negative fixture | `SEND_ALREADY_CONSUMED` |
| send-unknown-then-same-release | negative fixture | `SEND_ALREADY_CONSUMED` |
| output-after-fence | negative fixture | `WITHDRAWAL_FENCE_VIOLATION` |
| send-after-fence | negative fixture | `WITHDRAWAL_FENCE_VIOLATION` |
| namespace-reuse-after-delete | negative fixture | `NAMESPACE_REUSE` |
| incomplete-accounting | negative fixture | `INCOMPLETE_ACCOUNTING` |
| revoked-gateway-start | negative fixture | `STALE_GATEWAY_EPOCH` |
| revoked-gateway-send-start | negative fixture | `STALE_GATEWAY_EPOCH` |
| unknown-event-enum | negative fixture | `UNKNOWN_ENUM` |
| unknown-event-key | negative fixture | `UNKNOWN_OR_MISSING_KEY` |
| wrong-length-bstr | negative fixture | `WRONG_LENGTH_BSTR` |
| bridge-missing-old-quorum | negative fixture | `INVALID_AUTHORITY_QUORUMS` |
| duplicate-validator-index | negative fixture | `INVALID_APPROVAL_INDEX` |
| authority-quorum-too-small | negative fixture | `INVALID_QUORUM` |
| certificate-checkpoint-substitution | negative fixture | `CERTIFICATE_BINDING_MISMATCH` |
| history-gap-missing-send-start | negative fixture | `INVALID_PREDECESSOR` |
| duplicate-map-key | negative fixture | `DUPLICATE_OR_UNSORTED_KEY` |
| non-minimal-integer | negative fixture | `NON_MINIMAL_INTEGER` |
| tag-prohibited | negative fixture | `PROHIBITED_CBOR` |
| float-prohibited | negative fixture | `PROHIBITED_CBOR` |
| null-prohibited | negative fixture | `PROHIBITED_CBOR` |
| bool-prohibited | negative fixture | `PROHIBITED_CBOR` |
| indefinite-map | negative fixture | `PROHIBITED_CBOR` |
| indefinite-array | negative fixture | `PROHIBITED_CBOR` |
| indefinite-bstr | negative fixture | `PROHIBITED_CBOR` |
| text-prohibited | negative fixture | `PROHIBITED_CBOR` |
| negative-prohibited | negative fixture | `PROHIBITED_CBOR` |
| trailing-data | negative fixture | `NON_CANONICAL_CBOR` |
| terminal-after-fence-19 | SEND_COMPLETED | `VALID_SUCCESSOR` |
| terminal-after-fence-20 | SEND_STOPPED | `VALID_SUCCESSOR` |
| fence-before-escape-a | WITHDRAWAL_FENCE | `VALID_SUCCESSOR` |
| late-fence-before-escape-a | negative fixture | `WITHDRAWAL_FENCE_VIOLATION` |
| accounted-fence-before-escape-a | WITHDRAWAL_ACCOUNTED | `VALID_SUCCESSOR` |
| fence-before-start-a | WITHDRAWAL_FENCE | `VALID_SUCCESSOR` |
| late-fence-before-start-a | negative fixture | `WITHDRAWAL_FENCE_VIOLATION` |
| accounted-fence-before-start-a | WITHDRAWAL_ACCOUNTED | `VALID_SUCCESSOR` |
| competing-start-a | SEND_STARTED | `VALID_SUCCESSOR` |

## Reproduction and limits

Object `hex` contains complete bytes; `sha256` hashes those bytes. `domain_digest` includes the specified P2-F prefix and ONE NUL. Requests, envelopes, signed checkpoints, inclusion/extension proofs, bodies, witness approvals, authority approvals and authentication are indexed separately as well as embedded in finalities. Bridge includes both independent old/new ordered validator/witness sets on one identical body.

Initialize each scenario from pinned signed genesis, replay its `history` object names, then validate `input`. Parser-only fixtures bypass history. No historical proof authorizes current output. A negative candidate is fully re-signed except when its particular check deliberately corrupts a signature or encoding.

Wrapper documents use Git LF bytes for the stated file hash; Windows CRLF checkout translation is normalized only for wrapper drift checking. Embedded canonical objects are always compared as exact bytes.

This bounded fixture oracle is implementation-side evidence, not a production verifier or independent review. Strict point admission is checked with fixture-only field arithmetic; signatures use platform crypto. Neither establishes deployed parser/crypto handling, disk durability, operational independence, genuine human consent or target qualification. Binary independent inspection/preflight/runtime gates remain blocked. Collector NEVER EXECUTED.
