# P2-F custody and authority correction protocol v6 — 2026-11-07

Status: additive design proposal ready for a new independent review. Documentation only. No implementation, preflight execution, target access, TPM/TBS operation, key provisioning, service deployment, participant/provider data, or Gate 2 evidence was used.

## Final status

`P2F_CUSTODY_AUTHORITY_V6_READY_FOR_INDEPENDENT_REVIEW`

This status means only that the design is frozen enough for a new independent review. It does not authorize execution, target access, qualification, or a security PASS.

## Corrections from v5

The v5 review identified six blockers. V6 resolves them at the design level by making the coordinator/witness finality boundary explicit, defining a non-circular bootstrap and witness lifecycle, stating quorum availability assumptions, fencing withdrawal and output, freezing the authority envelope, and specifying the two-call preflight artifact.

Self-decisions:

| Area | Decision |
|---|---|
| Coordinator/witness finality | `COORDINATOR_WITNESS_FINALITY_READY_FOR_INDEPENDENT_REVIEW` |
| Witness lifecycle/bootstrap | `WITNESS_LIFECYCLE_BOOTSTRAP_READY_FOR_INDEPENDENT_REVIEW` |
| Withdrawal/output | `WITHDRAWAL_OUTPUT_MODEL_READY_FOR_INDEPENDENT_REVIEW` |
| Canonical schema | `CANONICAL_AUTHORITY_SCHEMA_READY_FOR_INDEPENDENT_REVIEW` |
| Preflight artifact | `READONLY_PREFLIGHT_ARTIFACT_SPEC_READY_FOR_INDEPENDENT_REVIEW` |

## Authority finality state machine

Every authority-changing operation follows this lifecycle:

```text
REQUEST → PREPARED → CANDIDATE_LOGGED → WITNESSED_FINAL → MATERIALIZED → ACKNOWLEDGED
```

- `PREPARED` is a coordinator reservation only and is not authoritative.
- `CANDIDATE_LOGGED` is an appended candidate and is not authoritative.
- `WITNESSED_FINAL` means the event is included in a valid checkpoint carrying the current-policy 3-of-4 witness quorum. This is the sole authoritative transition.
- `MATERIALIZED` reconstructs coordinator and cache state from final history.
- `ACKNOWLEDGED` is the first point at which success may be returned.

Nothing unwitnessed may be acknowledged as authoritative. etcd supplies atomic ordering and conditional commit for the live coordinator; an etcd `FINALIZED` flag, local SQLite state, broker memory, or coordinator write is not finality. At most one unresolved authority-changing candidate is permitted per namespace in the v6 pilot design.

If finality exists, recovery derives from witnessed history. If a coordinator write never became witnessed-final, it was never authoritative and may be reconciled or retried.

## Trust bootstrap and witness lifecycle

The pinned Genesis Trust Manifest v1 contains: log identity/public key/origin; four witness public keys; quorum `3-of-4`; policy epoch; coordinator trust identity/key reference; output-gateway trust identity/key reference; protocol version; approved hash algorithms; and canonical CBOR profile identifier.

The non-circular bootstrap chain is:

```text
pinned genesis/policy keys → witnessed log history → current coordinator epoch → derived coordinator state → local caches
```

Coordinator state never authenticates the history that validates the coordinator.

There are exactly four independently administered witnesses. A witness durably persists an accepted checkpoint before success and updates it atomically. A lost persistent checkpoint is a failed/compromised witness; it must not silently reuse its key. Replacement uses a new key and an explicit witnessed policy bridge. Unless a later review approves another rule, a bridge requires both the old 3-of-4 quorum and the new 3-of-4 quorum. If the old quorum cannot be reconstructed, remain `CLOSED`.

Any two 3-of-4 quorums intersect in at least two witnesses. Under the stated assumption of at most one Byzantine/reset/compromised witness, at least one overlap witness is honest and rejects inconsistent history. One unavailable witness leaves three usable witnesses. Two unavailable witnesses means `CLOSED`. One malicious plus one unavailable may cause availability loss if the malicious witness withholds; safety wins. If one administrator can reset/control all four, the independence assumption is false and the continuity guarantee collapses.

## Runtime and persistent authority state

`CLOSED` and `OPEN` are runtime states only. They are not authority states.

Persistent authority states are:

```text
UNISSUED → ACTIVE → WITHDRAWAL_FENCED → WITHDRAWN → DELETED
```

`WITHDRAWN → ACTIVE` and `DELETED → ACTIVE` are invalid. A later authorization requires a new namespace identity. A namespace can be authority `ACTIVE` while runtime `CLOSED` when continuity is temporarily unproven. Restore/reinstall only rediscovers witnessed-final state; it never creates `ACTIVE`.

## Restart, restore and coordinator epochs

Recovery starts runtime `CLOSED`, loads the pinned genesis/current policy, obtains current log/checkpoint evidence, generates a fresh 256-bit random challenge, appends a continuity/restart event with the expected predecessor, and obtains a fresh 3-of-4 quorum over a checkpoint extending prior accepted history and containing that challenge. It then reconstructs authority state and the complete operation registry, appends a new random coordinator epoch ID, witness-finalizes `COORDINATOR_EPOCH_START`, and only then sets runtime `OPEN`.

Rolled-back, forked, missing, inconsistent, or quorum-unavailable history remains `CLOSED`. Fresh challenges are not a completeness mechanism for unwitnessed coordinator history; only witnessed-final history is authoritative.

Every later authority candidate binds the current coordinator epoch. A stale epoch cannot create a valid candidate.

## Operation identity and retry behavior

The complete witnessed log prefix is the authoritative operation registry. Coordinator indexes are derived caches.

| Condition | Result |
|---|---|
| Same operation ID, same request digest, final result | `RETURN_PRIOR_FINAL_RESULT` |
| Same operation ID, same digest, pending candidate | Resume/reconcile its witness process |
| Same operation ID, different request digest | `CLOSED_MANUAL_ADJUDICATION` |
| No operation ID in complete witnessed history | May create `PREPARED` |
| Crash before witnessed finality | `NOT_AUTHORITATIVE_RETRY_ALLOWED` |
| Lost response after witnessed finality | Recover and return the existing final result |

Operation ID and request digest are bound into final event history. Missing, stale, or corrupt local SQLite never proves non-consumption.

## Withdrawal and output fencing

Withdrawal immediately blocks new output at runtime, then creates a `PREPARED WITHDRAWAL_FENCE`, logs it, and obtains a witness quorum. Only witnessed-final `WITHDRAWAL_FENCE` is acknowledged. After earlier outputs are terminally classified and restrictive state is protected, `WITHDRAWAL_EFFECTIVE` transitions `WITHDRAWAL_FENCED → WITHDRAWN`.

Output states are:

```text
OUTPUT_RESERVED → ESCAPE_COMMITTED → ESCAPED
         |                    └──────→ ESCAPED_UNKNOWN
         └──────────────→ ABORTED_BEFORE_ESCAPE
```

`ESCAPE_COMMITTED` itself must be witnessed-final before the controlled gateway intentionally releases any sensitive byte. It means the entire output is conservatively treated as potentially disclosed, even if delivery is partial or fails. A crash before it creates no exposure authorization. A crash after it, or a gateway crash mid-send, yields `ESCAPED_UNKNOWN` unless exact idempotent completion is later proven. Do not automatically replay a final release after gateway restart; reconcile authority first. If current authority is `WITHDRAWAL_FENCED`, `WITHDRAWN`, or `DELETED`, an old release cannot be reused as a post-withdrawal permit.

Both withdrawal and output events use the same predecessor chain. If `ESCAPE_COMMITTED` becomes final first, it is pre-withdrawal potential exposure. If `WITHDRAWAL_FENCE` becomes final first, later `ESCAPE_COMMITTED` is rejected. Restrictive transitions dominate stale permissive transitions. No recall of already escaped or potentially escaped data is claimed.

All export, publication, backup, derived output, API response, file copy, and sync paths must use the controlled gateway. This remains an application requirement, not an implemented or kernel-level guarantee.

## Offline policy and local Windows state

`OFFLINE_AUTHORITY_SENSITIVE_OPERATION_NOT_SUPPORTED`. If coordinator, log/witness continuity, or the controlled gateway is unavailable, authority-sensitive work remains `CLOSED`; ordinary non-sensitive functionality may remain offline only when independently separated.

Windows SQLite/files are cache and recovery support only: missing, stale, or corrupt cache is rebuilt/discarded or remains closed; rolled-back cache cannot roll back witnessed authority; power-loss ambiguity is resolved against witnessed history. NTFS durability is not the authority anchor. Trusted wall time is not needed for ordering; deadline-dependent authority remains disabled. CNG AES-GCM plus user-scope DPAPI remains `EVIDENCE_BLOCKED_PENDING_INTEGRATED_TARGET_QUALIFICATION`.

## Canonical CBOR authority envelope v1

The v1 profile is deterministic CBOR under RFC 8949. The top-level map uses exactly these integer keys and types:

| Key | Field | Type |
|---:|---|---|
| 0 | `protocol_version` | uint = 1 |
| 1 | `namespace_id` | bstr(32) |
| 2 | `policy_epoch` | uint |
| 3 | `namespace_sequence` | uint |
| 4 | `event_type` | uint enum |
| 5 | `operation_id` | bstr(32) |
| 6 | `request_digest` | bstr(32) |
| 7 | `previous_final_digest` | bstr(32) |
| 8 | `authority_generation` | uint |
| 9 | `authority_state` | uint enum |
| 10 | `authority_root` | bstr(32) |
| 11 | `inventory_root` | bstr(32) |
| 12 | `coordinator_epoch` | bstr(32) |
| 13 | `coordinator_revision` | uint |
| 14 | `disposition` | uint enum |
| 15 | `event_payload` | fixed-schema map |

V1 permits definite-length items only, deterministic encoding, integer keys only, no floats, no indefinite-length items, no duplicate or unknown keys, no implicit defaults, no free-form text in the authority core, fixed byte lengths, strict integer bounds, explicit enum values, and canonical request normalization. Event payload maps are separately fixed and reject unknown keys. A change requiring new core fields is v2, not an ambiguous v1 extension. Hashes are:

```text
SHA-256("StrideLab/P2F/v1/event\0" || deterministic_CBOR(event))
SHA-256("StrideLab/P2F/v1/request\0" || deterministic_CBOR(request))
```

The exact event-payload and enum tables must be frozen by the independent implementation review before any implementation work; this document does not claim those values are implemented.

## Adversarial outcomes

| Case | Outcome |
|---|---|
| Coordinator restored before witnessing | `NOT_AUTHORITATIVE_RETRY_ALLOWED` |
| Coordinator restored after witnessed finality | `RECOVER_FROM_WITNESSED_HISTORY` |
| Log restored before a final event | Event was not authoritative; reconcile/retry |
| One malicious witness | Safety retained under assumption |
| One unavailable witness | Continue with remaining three |
| One malicious + one unavailable | May lose availability; remain safe/closed |
| Two unavailable witnesses | `CLOSED_PENDING_RECONCILIATION` |
| Witness loses checkpoint state | Fail witness; replacement needs new key/bridge |
| Stale witness key reuse | Reject; continuity is not proven |
| Policy/witness replacement | Old and new quorums witness bridge |
| Fresh challenge on rolled-back history | `CLOSED_PENDING_RECONCILIATION` |
| Crash after finality before reply | `RETURN_PRIOR_FINAL_RESULT` |
| Same operation ID, same request | `RETURN_PRIOR_FINAL_RESULT` |
| Same operation ID, different request | `CLOSED_MANUAL_ADJUDICATION` |
| Withdrawal races output | Authoritative predecessor order decides |
| Crash before `ESCAPE_COMMITTED` | `NOT_AUTHORITATIVE_RETRY_ALLOWED` |
| Crash after `ESCAPE_COMMITTED` | `CONSERVATIVELY_CLASSIFY_ESCAPED_UNKNOWN` |
| Gateway crash mid-send | `CONSERVATIVELY_CLASSIFY_ESCAPED_UNKNOWN` |
| Withdrawal during send | Preserve final ordering; no recall claim |
| Gateway restart with old release | Reconcile; never automatically replay |
| Local cache restored | `CLOSED_PENDING_RECONCILIATION` |
| Reinstall | Start `CLOSED`; rebuild from witnessed history |
| Stale coordinator epoch | Reject candidate |
| Quorum permanently unavailable | `PERMANENT_NAMESPACE_UNAVAILABLE` |

Fail-closed availability loss is not a security PASS.

## Review and evidence boundary

The design is supported conceptually by etcd transaction/restore documentation, C2SP witness/policy material, and RFC 8949. Those sources do not prove this application design or its implementation. Remaining evidence includes target qualification, TPM/TBS, key custody, CNG/DPAPI, filesystem durability, gateway enforcement, crash testing, deletion/retention, and lawful data admission. No P2-F Gate 2 PASS is claimed.

## Exact next permitted task

Obtain a new independent security/design review of this v6 proposal and the frozen v5 preflight artifact specification. Do not execute the preflight or implement coordinator, log, witness, gateway, or target infrastructure until that review explicitly authorizes the next bounded design-to-implementation step.
