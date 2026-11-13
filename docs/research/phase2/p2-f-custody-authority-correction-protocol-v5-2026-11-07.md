# P2-F custody and authority correction protocol v5 — 2026-11-07

Status: design proposal ready for independent security review. Documentation only. No coordinator, log, witness, gateway, target, TPM/TBS state, key, participant/provider data, or implementation was used or changed by this proposal.

## Decision

The v5 architecture separates three claims that earlier versions conflated:

1. an etcd-backed coordinator orders accepted operations atomically;
2. an append-only authority history records the ordered events; and
3. independently administered witnesses detect rollback or fork of that history.

The local Windows cache is recovery material only. Authority-sensitive offline operation is unsupported. Any unavailable or ambiguous dependency leaves the authority `CLOSED`.

The design decisions for independent review are:

| Area | Decision |
|---|---|
| Authority continuity | `AUTHORITY_CONTINUITY_ARCHITECTURE_READY_FOR_INDEPENDENT_REVIEW` |
| Withdrawal/output | `WITHDRAWAL_OUTPUT_MODEL_READY_FOR_INDEPENDENT_REVIEW` |
| Canonical format | `CANONICAL_AUTHORITY_FORMAT_READY_FOR_INDEPENDENT_REVIEW` |
| Read-only preflight | `READONLY_PREFLIGHT_PLAN_READY_FOR_INDEPENDENT_REVIEW` |

These are review-readiness decisions, not implementation, qualification, Gate 2, or target-access approvals. The final branch baseline after remote reconciliation is the requested `a4ca8d37fb10d8ed5c063b77d0267d976ff9c250`.

## Scope and threat boundary

The proposal assumes four independently administered witnesses and at most one malicious witness. A 3-of-4 quorum is required for continuity checkpoints. This provides rollback/fork detection only under that assumption; it does not semantically validate every application operation.

The proposal does not claim protection against a compromised privileged coordinator administrator, witness administrators beyond the stated quorum assumption, gateway administrators, Windows SYSTEM/kernel compromise, stolen privileged credentials, or a compromised client binary. Those cases can forge, suppress, or expose data unless a separately reviewed control supplies protection. The gateway is a sole-path application boundary, not a hardware or kernel information-flow boundary.

No etcd snapshot restore is safe by itself. etcd transactions supply ordering and conditional commit; snapshot restore can reproduce an older logical state. Fresh witness continuity evidence is required before reopening authority-sensitive work. See [etcd transactions](https://etcd.io/docs/v3.5/learning/api/) and [etcd disaster recovery](https://etcd.io/docs/v3.5/op-guide/recovery/).

## Components and authority boundaries

### Coordinator

The coordinator owns the authoritative operation registry, current authority state, inventory state, output state, withdrawal state, and expected event/checkpoint linkage. It uses etcd compare-and-swap transactions to serialize conditional transitions. A successful transaction assigns the operation's authoritative revision/term/epoch reference; a client response is not the commit point.

The coordinator does not, by itself, prove that its history was never restored. Its state is one input to continuity reconciliation.

### Transparency-style authority history

Every finalized authority transition appends one canonical event to an append-only log. The event includes a digest of the previous event, so the accepted history is a linked chain. A checkpoint commits the namespace, event range, event-root digest, inventory root, authority root, and operation-root digest.

The log is an accountability and reconciliation record. It is not assumed to be independent merely because it is stored on another local volume. Its durable custody, replication, retention and access roles require later independent review.

### Witnesses

Each witness stores its latest accepted checkpoint and refuses to cosign a checkpoint that does not extend the previously accepted history. A checkpoint proposal must contain the prior checkpoint reference, log root, range, authority/inventory/operation roots, and any fresh restart challenge. The broker accepts only a fresh 3-of-4 quorum.

Witnesses attest continuity of the checkpoint structure they receive. They do not attest that a client request was lawful, that a participant consented, or that application semantics are correct.

## Canonical authority event

The event is encoded as deterministic CBOR under RFC 8949. The application profile is frozen as follows:

- deterministic encoding with definite-length maps, arrays, byte strings and text strings only;
- canonical integer representation; no floating-point values;
- unique map keys; duplicate fields are rejected;
- unknown critical fields are rejected; unknown non-critical extension fields are permitted only in an explicitly namespaced extension area;
- bounded lengths for identifiers, digests, records, and extension areas;
- `schema_version` changes are explicit and migration rules are versioned; no silent reinterpretation;
- event hashing uses a domain-separation prefix distinct from request and checkpoint hashing;
- request digests cover the normalized logical request, operation ID, namespace, purpose, input references and relevant policy version, so identical logical requests produce the same digest;
- byte strings and identifiers are not normalized after digesting; the profile defines UTF-8 and identifier constraints before hashing.

Each event binds at least:

```text
schema_version
namespace_id
event_sequence / authority_generation
operation_id
operation_type
request_digest
previous_event_digest
authority_root
inventory_root
disposition
issuer/coordinator_identity_reference
coordinator_revision/term/epoch_reference
continuity/checkpoint_reference, when applicable
```

An event is not accepted merely because it parses. The coordinator validates the expected prior event/root and operation registry inside the same transaction that establishes the disposition. The profile is based on [RFC 8949 deterministic CBOR](https://www.rfc-editor.org/rfc/rfc8949.html); a separate implementation review must verify the chosen library's duplicate-key and canonical-encoding behavior.

## Operation identity and retries

The authoritative registry is the only source of operation consumption. The required behavior is:

| Situation | Required result |
|---|---|
| First operation ID with valid request | Validate, transactionally record, append/bind the event, and return the established disposition. |
| Exact retry | Return the already-established disposition and event/checkpoint references; do not consume again. |
| Same ID, different request digest | Fail closed, record a collision/investigation disposition, and do not reinterpret the operation. |
| Lost client response after commit | Retry by ID; return the committed result from the registry. |
| Crash before coordinator commit | No authoritative consumption is assumed; retry must re-enter normal validation. |
| Crash after coordinator commit | Reconcile the registry/history and return the committed disposition; never create a second operation. |
| Restored local database | Treat it as stale or ambiguous cache; reconcile from coordinator, log and fresh witness proof. |
| Reinstall | Start `CLOSED`; no local artifact can recreate `ACTIVE`. |
| Competing operation IDs | Coordinator ordering decides; restrictive transitions dominate permissive ones. |
| Concurrent permissive/restrictive operations | The first accepted restrictive transition prevents later permissive commits; an already committed escape remains accounted for. |

The operation ID is bound into the event and checkpoint roots. A local cache cannot prove that an operation was never consumed.

## Authority state machine

```text
CLOSED -> ACTIVE -> WITHDRAWAL_PENDING -> WITHDRAWN -> DELETED
   ^          |              |                 |
   |          +--------------+-----------------+
   +-------- failed continuity/reconciliation
```

- `CLOSED`: initial, restored, reinstalled, failed, or uncertain state. No authority-sensitive operation or output.
- `ACTIVE`: only after fresh continuity proof and reconciliation. New authorized operations may be ordered.
- `WITHDRAWAL_PENDING`: restrictive withdrawal is recorded and new escape commits are rejected. Existing outputs are classified.
- `WITHDRAWN`: restrictive state is committed and continuity-protected. No new authority-sensitive output.
- `DELETED`: terminal disposition after the separately approved deletion/retention procedure. It cannot be restored to `ACTIVE` by local state.

Legal transitions are coordinator transactions. A restore/reinstall operation can only enter `CLOSED`. On any conflicting transition, restrictive state wins; uncertainty also enters `CLOSED`.

## Restart, restore and reinstall continuity procedure

1. Start `CLOSED`.
2. Obtain the current coordinator state and latest checkpoint.
3. Generate a fresh unpredictable challenge and bind it to this restart attempt.
4. Append a challenge/continuity event using the current expected state. A stale restored state cannot append it successfully.
5. Obtain a fresh 3-of-4 witness quorum over a checkpoint containing the challenge and extending each witness's previously accepted history.
6. Reconcile authority, inventory and operation registry, including restrictive events and all output dispositions.
7. Reopen only if all checks and quorum succeed; otherwise remain `CLOSED`.

An old witness signature without the fresh challenge is insufficient. Missing, forked, rolled-back, inconsistent or unavailable history is a closed-state result. The fresh challenge detects replay of a stale proof; it does not make compromised witnesses honest.

## Output and withdrawal model

The sole-path output gateway is the only allowed application path for authority-sensitive exports, publications, backups and derived outputs. It must not release sensitive bytes before the coordinator commits `ESCAPE_COMMITTED`.

Output states:

```text
OUTPUT_RESERVED -> ESCAPE_COMMITTED -> ESCAPED
         |                  |
         +--> ABORTED_BEFORE_ESCAPE
                            +--> ESCAPED_UNKNOWN
```

`ESCAPE_COMMITTED` has a deliberately conservative meaning: from this point the entire output is considered potentially exposed, even if the subsequent transmission crashes, is partial, or never reaches recipient observation. Socket completion is not treated as recipient observation.

Withdrawal events are `WITHDRAWAL_RECEIVED`, `WITHDRAWAL_EFFECTIVE`, and `WITHDRAWAL_ACKNOWLEDGED`.

- If `ESCAPE_COMMITTED` wins first, the output is counted as pre-withdrawal potentially exposed.
- If `WITHDRAWAL_RECEIVED` wins first, a new `ESCAPE_COMMITTED` transition is rejected.
- Withdrawal does not recall already escaped or potentially escaped data.
- `WITHDRAWAL_EFFECTIVE` is committed only after all pre-existing outputs are terminally classified as `ESCAPED`, `ABORTED_BEFORE_ESCAPE`, or `ESCAPED_UNKNOWN`, and restrictive authority state is committed and continuity-protected.
- If a crash leaves release ordering ambiguous, use `ESCAPED_UNKNOWN`; never claim no disclosure.

The gateway must not provide a bypass through retries, backups, derived outputs, alternate publication paths or a restored cache. That is an application invariant, not a claim against a privileged host compromise.

### Failure matrix

| Failure | Required disposition |
|---|---|
| Broker crash before `ESCAPE_COMMITTED` | No bytes may be released; reconcile reservation, then abort or retry by operation ID. |
| Crash after `ESCAPE_COMMITTED` | Treat whole output as potentially exposed; classify `ESCAPED` or `ESCAPED_UNKNOWN`. |
| Gateway crash mid-send | `ESCAPED_UNKNOWN`; never retry as if unexposed without the same authoritative disposition. |
| Lost output response | Retry by operation ID and return the registry disposition. |
| Withdrawal races output | Coordinator order decides; restrictive winner blocks later escape commits. |
| Destination unavailable before commit | Remain reserved or abort; no bytes released. |
| Coordinator unavailable | Remain `CLOSED`/non-output; no offline authority-sensitive operation. |
| Witness quorum unavailable | Do not reopen after restart or restrictive transition; remain closed. |

## Offline policy and local Windows durability

Authority-sensitive offline operation is explicitly unsupported. Cached grants, event fragments, SQLite, NTFS behavior, recovery files and local clocks cannot establish currentness, non-consumption or withdrawal status.

The local Windows store may support cache and recovery diagnostics only:

- missing cache: reconcile;
- stale cache: discard/reconcile;
- corrupt cache: discard or remain closed until reconciliation;
- rolled-back cache: it cannot roll back coordinator or witness history;
- power-loss ambiguity: reconcile from coordinator, log and fresh continuity proof.

Unresolved filesystem durability remains later target evidence, not an authority anchor. Trusted wall time is not required for ordering; deadline-dependent authority remains disabled. CNG AES-GCM plus user-scope DPAPI remains `EVIDENCE_BLOCKED_PENDING_INTEGRATED_TARGET_QUALIFICATION`.

## Read-only preflight v4 boundary

The first preflight remains two information calls only: `RtlGetVersion` and `GetNativeSystemInfo`. It must not be executed as part of this document task. The independent reviewer must approve or reject the collector artifact and its incidental-effect envelope before execution.

Required artifact constraints:

- source provenance, pinned executable hash/signature and reproducible build record;
- no-install, standard non-elevated principal;
- exactly the two information calls, with exact import list reviewed;
- in-memory or observer capture; no explicit filesystem writes;
- no registry enumeration, network, TPM/TBS, key provisioning, service account or target data access;
- no claim of zero incidental OS/security-software/audit writes; enumerate expected observation effects and stop conditions;
- stop on unexpected import, elevation, handle/child process, network, file/registry activity or output outside the approved capture envelope.

The collector is not an authority proof and cannot qualify integrated target behavior.

## Independent-review gates and remaining blockers

The independent security reviewer must still challenge the coordinator transaction boundaries, event/log custody, witness administration and quorum failure model, CBOR implementation profile, gateway bypasses, crash classification, privileged-host assumptions, key/cipher evidence, deletion/retention semantics, and operational recovery.

Target evidence remains outstanding: integrated Windows qualification, TPM/TBS behavior, key provisioning and recovery, CNG/DPAPI behavior, filesystem durability, gateway deployment, real crash testing, deletion verification, participant/provider admission, and any Gate 2 evidence. No source admission, held-out outcome access, P2-B activation, or Gate 2 PASS follows from this proposal.

## Primary sources and evidence posture

- [etcd API guarantees and transactions](https://etcd.io/docs/v3.5/learning/api/): specification support for conditional ordering, not rollback immunity.
- [etcd snapshot restore](https://etcd.io/docs/v3.5/op-guide/recovery/): restore behavior motivates fresh external continuity evidence.
- [RFC 8949](https://www.rfc-editor.org/rfc/rfc8949.html): deterministic CBOR encoding basis; application bounds above are StrideLab profile decisions.
- [C2SP transparency policy](https://c2sp.org/tlog-witness/): witness/transparency concepts; this proposal's four-witness quorum and authority semantics remain StrideLab design decisions requiring review.

Specification support is not implementation evidence. No target operation, preflight execution, cryptographic qualification, or security certification is claimed.

## Final status

`P2F_CUSTODY_AUTHORITY_V5_READY_FOR_INDEPENDENT_REVIEW`

