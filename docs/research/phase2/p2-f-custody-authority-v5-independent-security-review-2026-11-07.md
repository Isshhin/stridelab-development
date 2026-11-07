# Independent security and design review: P2-F custody and authority v5

Reviewed commit: `ab572a78da5dbeb3a3aa26075a43454afdb8f9cc` (PR #34). Review-only desk analysis of the v5 protocol and preflight v4, informed by the previous independent review. No implementation, target access, preflight, TPM/TBS, keys, infrastructure, private data, or qualification performed. Findings are design claims and counterexample schedules, not observations of a deployed system. Exact-head research validation 42361105092 was queued at review start; it is not evidence of a passing review.

## Decision and major classifications

| Area | Classification | Specific verdict |
|---|---|---|
| Coordinator ordering | DESIGN_CORRECTION_REQUIRED | CAS ordering is conditional on one healthy cluster and controlled writers; commitment and witnessed finality are not reconciled. |
| Transparency history | DESIGN_CORRECTION_REQUIRED | No atomic cross-service finality or complete replay/recovery protocol. |
| Witness quorum | DESIGN_CORRECTION_REQUIRED | WITNESS_QUORUM_MODEL_NOT_ESTABLISHED |
| Restart challenge / bootstrap | DESIGN_CORRECTION_REQUIRED | FRESH_CONTINUITY_PROOF_NEEDS_CORRECTION |
| Operation ID and retry | DESIGN_CORRECTION_REQUIRED | Registry results and authoritative absence lack a continuity-protected finality boundary. |
| Authority state machine | DESIGN_CORRECTION_REQUIRED | Restrictive and closed states, reactivation and identity lifecycle require explicit transitions. |
| Output state / ESCAPE_COMMITTED | DESIGN_CORRECTION_REQUIRED | Conservative accounting is coherent in principle; irreversible release authorization/finality and post-withdrawal sends are insufficiently specified. |
| Withdrawal ordering and effective withdrawal | DESIGN_CORRECTION_REQUIRED | Coordinator order is necessary, not sufficient, across rollback, in-flight output and witnesses. |
| Sole-path gateway | DESIGN_ACCEPTABLE_FOR_NEXT_REVIEW_STAGE | Architectural requirement pending demonstrated implementation enforcement, not a deployed control. |
| Offline policy | DESIGN_ACCEPTABLE_FOR_NEXT_REVIEW_STAGE | Deny authority-sensitive offline work; prove separation later. |
| Canonical format | DESIGN_CORRECTION_REQUIRED | CANONICAL_AUTHORITY_FORMAT_NEEDS_CORRECTION |
| Windows local state | DESIGN_ACCEPTABLE_FOR_NEXT_REVIEW_STAGE | Cache-only is a sound conditional boundary, contingent on independent continuity. |
| Two-call preflight | DESIGN_CORRECTION_REQUIRED | READONLY_PREFLIGHT_NEEDS_CORRECTION: plan lists future artifact requirements but does not freeze an actual collector/capture/effects envelope. |
| Threat model | DESIGN_CORRECTION_REQUIRED | Security assumptions and privileged compromise boundaries need explicit operational treatment. |

Top-level: `AUTHORITY_CONTINUITY_DESIGN_NEEDS_CORRECTION`; `WITHDRAWAL_OUTPUT_DESIGN_NEEDS_CORRECTION`; `CANONICAL_AUTHORITY_FORMAT_NEEDS_CORRECTION`; `READONLY_PREFLIGHT_NEEDS_CORRECTION`.

## Blocking findings and falsification

**F1 — No transaction-to-witness finality protocol.** V5 calls an etcd transaction the authoritative commit, separately says each finalized transition is appended, and asks witnesses to checkpoint history, without specifying when a transaction may be returned as final, when a gateway may release, or how to reconcile a committed but unwitnessed transition. Counterexample: withdrawal is acknowledged after etcd commit but before durable witness checkpoint; restore coordinator and log to before it; a fresh challenge on that old history can obtain three signatures if witnesses never saw the withdrawal. Neither a fresh nonce nor revision detects an event that no independent durable party observed. Conversely, a witnessed event whose coordinator transaction was lost may not be discarded. Specify exact durable transaction/outbox, ordered event material, checkpoint/finality, receipt semantics, fail-closed intermediate states, gap detection and recovery. In particular, a committed but unwitnessed restrictive receipt must block release, and externally effective disclosure and final acknowledgments cannot rely on an unwitnessed tail surviving rollback. The actual solution needs review, not an assertion that append and etcd writes are atomic.

**F2 — Unproven trust bootstrap and rollback-resistant witness operations.** The proposed chain is: independently authenticated initial namespace/genesis and pinned witness identities -> independently durable witness latest checkpoints -> verified extension proofs and fresh challenge -> reconcile a known coordinator/log epoch and all roots/registry -> permit ACTIVE and gateway use. V5 names pieces but does not specify the genesis trust distribution, authenticated witness keys, checkpoint evidence/proofs, rollback-resistant witness storage, reset/replacement and membership epochs, independent coordinator identity recovery, or how a restored broker locates current witness state without trusting the coordinator/log it is checking. A coordinator-selected old checkpoint and self-asserted epoch cannot establish currentness. Required: independent witness queries and pinned trust anchors, proof verification and recovery rules, fail-closed ambiguity, and witness reset/rotation protocol that preserves intersection and durable history. If the same operator can reset all witnesses, continuity collapses. Correlated compromise counts against the stated threat model even if servers are physically separate.

**F3 — Quorum math is necessary but insufficient.** Two 3-of-4 sets intersect in at least two witnesses. With at most one malicious witness, at least one shared witness is honest, so conflicting *fully quorum-certified checkpoints* cannot both be accepted if each honest witness durably and atomically remembers its latest accepted chain, verifies real append-only consistency, signs only an extension, and cannot be reset/rolled back or impersonated. A witness signing a mere claimed previous hash without verifying the full extension does not prove consistency. Sequential forks, concurrent signing, replacement and old signatures require explicit rules. Three available honest witnesses work with one malicious or unavailable; two unavailable, or one malicious withholding plus one unavailable, cannot guarantee progress. One malicious plus one unavailable can produce a quorum only if the malicious witness cooperates; never rely on cooperation for availability. Divergence invokes closure/adjudication, not selecting a convenient quorum. Old signatures cannot satisfy a fresh challenge. The math alone cannot protect events not yet witnessed.

**F4 — Withdrawal/output lacks a complete enforceable fence.** The stated ordering is sensible: if escape CAS wins first count the entire output as potentially disclosed; if receipt CAS wins first reject a later escape CAS. But a stale or restored coordinator, an unwitnessed receipt, independently operating gateways, queue/backup workers, or a gateway that releases after a local/stale decision can violate it. Specify linearizable authoritative reads/transactions, writer fencing and epochs tied to continuity, exact transaction predicates, unique output/reservation bindings, crash recovery, release authorization and all path ownership. Pre-committed outputs may still be transmitting when withdrawal arrives: their *whole* content is already accounted as potentially exposed, but the design must explicitly decide whether to stop remaining bytes or allow completion, define which terminal state means no further emission can occur, and prove that effective withdrawal cannot precede that condition. Receipt is when accepted in authoritative order, not necessarily when a user sends it. Separate prompt receipt, effective withdrawal and acknowledgments; no promise of recalling previously escaped bytes.

**F5 — Canonical profile is not an executable unique schema.** RFC 8949 deterministic encoding constrains encodings, not application semantic equivalence. V5 does not freeze field keys/types, integer ranges, allowed simple values/tags, optional/default/absent-versus-null rules, UTF-8/Unicode identity policy, normalization algorithm, nested extension rules, exact bytes for hash framing and domain strings, operation/state schemas, or unknown-field semantics. Namespaced unknown non-critical extensions can be accepted only if fully specified, semantically inert, authenticated and deterministically interpreted; simpler v1 should reject all unknowns. Define RFC 8949 core deterministic ordering, exact schemas and test vectors, duplicate/invalid encodings rejection, digest algorithms and domain-separated input framing, and version migration. No claim that all unknown fields necessarily break RFC encoding.

**F6 — Preflight artifact is not frozen.** The plan requires future source, hash/signature, reproducible build, exact imports/call trace, identity, capture method and effects envelope, but supplies none. “Exactly two calls” must mean two approved *information queries* and distinguish unavoidable process startup/loader/observer system calls, rather than imply exactly two total OS calls. Freeze exact artifact, immutable identity and build verification, import/dynamic-resolution review, non-elevated principal and manifest, execution method, memory/observer capture, no explicit writes/network/TPM/TBS/registry enumeration, permitted incidental OS/security-software/audit effects, and observable stop criteria before a separate authorization review. Some runtime effects cannot be guaranteed detectable by the collector itself; identify observer responsibility. No execution is authorized here. This is an artifact completeness finding, not a claim that the two query APIs inherently mutate target state.

## Detailed analysis

### Coordinator ordering and history

`COORDINATOR_ORDERING`: a properly configured etcd cluster can serialize transactional compares/writes despite concurrent writers, leader changes and minority partitions; use linearizable reads and fail closed on loss of quorum, stale credentials or transaction failure. CAS must bind namespace, epoch, generation, state, operation ID/digest, latest roots and output/withdrawal predicates. Restrictive changes must invalidate earlier permissive authorizations, not retroactively undo an already committed escape. Client response loss is not proof of failure. Fencing for stale writers and coordinator replacement, credential revocation and privilege separation are not designed in sufficient detail.

`CONTINUITY_PROTECTION`: revisions/terms alone are not anti-rollback; a restored cluster can revert state. Independent, durable witness history plus validated extension and epoch reconciliation is needed; epoch identity must not be self-certified by the restored cluster. Log/event omission, duplicates, reorder and forks need sequence uniqueness, complete history, event/root and range validation, cross-check of transaction and certified checkpoints, and actual consistency proofs. An unwitnessed append is evidence to reconcile, not independently final. An event can become externally authoritative only at a defined coordinator-ordered, witnessed, reconciled finality point; v5 does not define one. How restrictive receipt fences operate before finality must also be specified.

### Witness and fresh-challenge result

`WITNESS_QUORUM_MODEL_NOT_ESTABLISHED`. Administrative independence, authenticated distinct identities and rollback-resistant state are security assumptions. Witness replacement or reset cannot silently act as a new honest signer; constrain old/new membership and preserve overlapping trusted durable state, otherwise close, possibly permanently. Honest witness availability and signer compromise are distinct.

`FRESH_CONTINUITY_PROOF_NEEDS_CORRECTION`. Fresh unpredictable challenge prevents replay of old signatures if authenticated, domain-separated and bound to namespace, attempt and checkpoint. It is not proof that events omitted before the latest witnessed checkpoint existed. A restored broker presenting a stale history should be rejected by sufficiently many durable witnesses that actually accepted a later checkpoint; this depends on F2 and does not cover an unwitnessed tail. Fresh challenge appended on a restored old log fails only if witnesses already possess a later incompatible checkpoint. The proposal's claim that stale state cannot append is not generally true when coordinator and log are restored together. Loss of challenge response requires checking the existing attempt and certified history rather than assuming absence; any unresolved crash stays CLOSED.

### Operation IDs and authority state

IDs and request digests must be included in complete witness-certified operation history, with authoritative uniqueness checked transactionally. A same-ID/same-digest retry returns a prior *confirmed* disposition only after fresh continuity and reconciliation; a same-ID/different-digest request fails closed without overwriting the original. Two IDs for semantically equivalent work are not deduplicated by ID alone: define whether duplicates are allowed and any independently enforced semantic idempotency key. Before-commit retry requires authoritative absence established after continuity; old restored registry absence is insufficient. Competing restrictive and permissive operations must compare the same generation/fence; no local cache decides.

The depicted CLOSED -> ACTIVE -> WITHDRAWAL_PENDING -> WITHDRAWN -> DELETED path does not specify all transitions. CLOSED is ambiguous as lifecycle state versus operational availability: a failed continuity check from WITHDRAWN must not erase the withdrawal or allow generic CLOSED -> ACTIVE. Define persistent monotonic restrictive lifecycle separately from operational closure, permissible initial activation/recovery, exact withdrawal/effective and deletion prerequisites, revoked/terminal namespace identity, tombstone retention and reuse/alias rules. No restore, reinstall, incomplete history, old backup or stale permissive request may create ACTIVE. Administrative recovery cannot silently relax a terminal state.

### Escape, withdrawal and effective status

The pre-release `ESCAPE_COMMITTED` accounting boundary is defensible: after a valid commit the entire bound output is potentially exposed even when no destination receives it. Socket buffering, disconnects, partial transmission, lost response and crashes never justify downgrading exposure. `ESCAPED` must not imply observed receipt; `ESCAPED_UNKNOWN` records uncertainty. A crash before a valid commit can be `ABORTED_BEFORE_ESCAPE` only if sole-path release enforcement is established and no earlier commit is possible; otherwise close. A repeated request must not accidentally retransmit sensitive bytes under a completed operation. Bind the exact content/root, destination or recipient scope, purpose and policy, and specify output completion and retry behavior. Withdrawal during derived generation or queued backup cancels or fences any uncommitted release; already committed work is accounted for and must become terminal with no further unaccounted emission. A stuck output means withdrawal remains pending or is conservatively classified unknown only after the system can prevent any additional unaccounted emission; unknown is not magic process termination. Witness protection of restrictive state and terminal classifications is necessary before effective acknowledgement.

Gateway coverage must include export, publication, backup, derived output, cache-to-external transfer, API responses, file copies and future network sync. This is an acceptable architectural requirement at this stage, not proof those paths are actually intercepted. Specify scope of trusted workers, privileged bypass, control-plane and data-plane boundaries, and verify later. Offline denial resolves stale offline authority only if no sensitive processing or output bypasses it; ordinary app functions may remain separately offline.

### Threat model

| Actor | Consequence and qualification |
|---|---|
| Ordinary interactive user | Isolation may limit access only with verified OS ACLs and distinct identities; same-user access is not inherently isolated. |
| Same-token process | May use accessible credentials, DPAPI material, files or IPC; excluded unless separate enforceable boundary is shown. |
| Stolen broker credential | Can impersonate authorized broker within granted privileges; reduce scope/fence/revoke; no blanket protection. |
| Workstation administrator / SYSTEM / kernel | Can bypass application gateway, inspect or alter process state and credentials; integrity/confidentiality cannot be claimed against them. |
| Coordinator administrator | Can alter/restore state; external witnesses can detect already certified rollback under assumptions, but privileged malicious changes and unwitnessed events are not automatically protected. |
| Coordinator cluster member | Quorum protocol handles a failed/non-Byzantine member under its deployment assumptions, not an arbitrary Byzantine member; theft/admin access may expand compromise. |
| Transparency-log administrator | Can suppress, fork or restore log; independently durable honest witnesses detect conflicting certified histories under F2/F3; availability can be lost. |
| Witness administrator | One malicious witness is tolerated for certified-fork intersection only with other independent honest durable witnesses. Common reset/compromise of multiple administrators breaks assumption. |
| Output-gateway administrator | Can bypass application boundary and leak; excluded absent independently enforced isolation. |
| Network attacker | Authenticated channels, server identity, replay protection and access control required; denial of service forces closure. |
| Compromised client executable | Can misrepresent authorized operations or expose data within its privileges; witness continuity does not validate consent or semantics. |

### Preflight and remaining evidence

The two proposed calls are environment inventory, not cryptographic or custody qualification. Artifact review must precede separate authorization. Later evidence remains necessary for actual witness and coordinator recovery, race and crash fault injection, operation semantics, gateway bypass and accounting, Windows isolation/local recovery, CNG/DPAPI and key custody, TPM/TBS where separately authorized, deletion/retention, and integrated target qualification. No target evidence is inferred from this desk review.

References: [etcd transaction API](https://etcd.io/docs/v3.5/learning/api/), [etcd disaster recovery](https://etcd.io/docs/v3.5/op-guide/recovery/), [RFC 8949 section 4.2](https://www.rfc-editor.org/rfc/rfc8949.html#section-4.2). These describe primitives, not a proof of this architecture.

## Required adversarial schedules

These are conservative required dispositions, not demonstrated tests. CLOSED may cause indefinite or permanent availability loss.

| Schedule | Disposition and reason |
|---|---|
| Coordinator restored to old snapshot | CLOSED_PENDING_RECONCILIATION; compare independently durable certified history, missing unwitnessed tail remains F1. |
| Transparency log restored to old history | CLOSED_PENDING_RECONCILIATION; recover only authenticated complete witnessed history, else CLOSED_MANUAL_ADJUDICATION. |
| One malicious witness | CLOSED_PENDING_RECONCILIATION if disagreement; 3 honest can certify only with F2/F3 controls. |
| One unavailable witness | RECOVER_FROM_AUTHORITATIVE_HISTORY only if remaining three validly certify and reconciliation succeeds; otherwise CLOSED_PENDING_RECONCILIATION. |
| One malicious plus one unavailable | CLOSED_PENDING_RECONCILIATION if malicious withholds; a quorum may exist only if it cooperates, with all proofs checked. |
| Two witnesses unavailable | CLOSED_PENDING_RECONCILIATION; no 3-of-4 quorum. |
| Broker restart with stale checkpoint | CLOSED_PENDING_RECONCILIATION; independently query trusted witnesses; never trust old cache. |
| Fresh challenge lost after append | CLOSED_PENDING_RECONCILIATION; resolve attempt from authenticated history; do not assume it failed. |
| Fresh challenge witnessed but response lost | RECOVER_FROM_AUTHORITATIVE_HISTORY after authenticated quorum evidence and coordinator reconciliation; otherwise closed. |
| Conflicting histories at witness subsets | CLOSED_MANUAL_ADJUDICATION; two certified incompatible histories violate assumptions, do not choose by convenience. |
| Coordinator commit, log append fails | CLOSED_PENDING_RECONCILIATION; durable outbox/recovery not yet specified; no permissive externally effective result. |
| Log append succeeds, witness quorum fails | CLOSED_PENDING_RECONCILIATION; append is not final; preserve evidence. |
| Witnessed event, coordinator reconciliation fails | CLOSED_MANUAL_ADJUDICATION; cannot discard witnessed history or activate stale coordinator. |
| Duplicate operation retry | RETURN_PRIOR_CONFIRMED_RESULT only after valid continuity and identical digest; otherwise CLOSED_PENDING_RECONCILIATION. |
| Same operation ID, different digest | CLOSED_MANUAL_ADJUDICATION; reject collision, preserve original disposition. |
| Output commit races withdrawal | RECOVER_FROM_AUTHORITATIVE_HISTORY after ordering and witness reconciliation; first valid escape is potentially exposed, later escape after receipt rejected; F1/F4 unresolved. |
| Crash before ESCAPE_COMMITTED | CLOSED_PENDING_RECONCILIATION; abort only once no commit and no emission are proven. |
| Crash after ESCAPE_COMMITTED | CONSERVATIVELY_CLASSIFY_ESCAPED; entire bound output potentially exposed; terminal status unknown if unresolved. |
| Crash during partial send | CONSERVATIVELY_CLASSIFY_ESCAPED; full output potentially exposed, ESCAPED_UNKNOWN until terminal. |
| Withdrawal during partial send | CONSERVATIVELY_CLASSIFY_ESCAPED; fence further new commits, stop/drain precommitted output under a specified policy before effective status. |
| Gateway unavailable | CLOSED_PENDING_RECONCILIATION; no alternate release path. |
| Coordinator unavailable | CLOSED_PENDING_RECONCILIATION; no authority-sensitive offline operations. |
| Local backup restored | CLOSED_PENDING_RECONCILIATION; cache never establishes authority. |
| Reinstall | CLOSED_PENDING_RECONCILIATION; no generic direct activation. |
| Corrupted local cache | CLOSED_PENDING_RECONCILIATION; discard/rebuild only after independent continuity. |
| Witness reset/replacement or all witnesses reset by one operator | CLOSED_MANUAL_ADJUDICATION; if independent prior history cannot be established, PERMANENT_NAMESPACE_UNAVAILABLE. |

## Next permitted task

Prepare a new correction proposal resolving F1–F6, including an independently reviewable frozen preflight artifact specification, followed by a new independent review. A separate authorization decision is not yet reached. PR #34 must remain open, draft, unmerged; no target, preflight, implementation, private pilot, source admission, P2-B or Gate 2 PASS.

## Final verdict

`P2F_CUSTODY_AUTHORITY_V5_DESIGN_NEEDS_CORRECTION`
