# Independent security/design review: P2-F custody and authority v6

Reviewed exact proposal head: `6bd4abfd55ac85a0d2528892f204427fa113ccac`, PR #34, 2026-11-07. Desk review of v6, preflight v5, prior v5 review and handoffs. This is an adversarial design analysis, not a deployment, implementation, test, proof of security, or target qualification. No target access, preflight, TPM/TBS operations, key provisioning, infrastructure deployment, or participant/provider/private data was used. Prior research-validation run 42363546016 was pending at review; Graphify mirror failure 42363540846 is separate and no evidence shows it changed reviewed files.

## Decision and findings

**P1 F1 — Certified checkpoints do not yet specify a valid authority history.** V6 §Authority finality makes quorum inclusion the sole finality condition, but does not say who verifies that *every* included candidate is an allowed unique state transition in the namespace and current epoch, or how witnesses verify a complete append-only extension and apply per-namespace predecessor/CAS rules. A checkpoint can contain two appended candidates with the same previous-final digest, a stale epoch, duplicate ID, or output after a withdrawal; inclusion alone is not a valid state machine. Unwitnessed append may later acquire a quorum after a retry is declared new. Specify exact validation before certification and independently on replay; bind checkpoint/log identity, sequence, hashes, membership proofs and ordering, and reconcile pending/duplicate candidates without treating absence in the final prefix as proof that a live candidate cannot later finalize. Provide atomicity and recovery semantics. No unsupported claim that a quorum alone validates application semantics.

**P1 F2 — Bootstrap, freshness, witness lifecycle and epochs remain asserted rather than executable.** A pinned manifest describes fields, not an actual pinned immutable manifest and distribution/authentication procedure; current-policy discovery, independent witness endpoints, checkpoint/extension verification, bridge message and old/new policy activation ordering are not defined. Durable checkpoint storage must resist *undetected rollback*, not merely atomic write; silently restored stale witness state cannot be automatically detected by a key whose original state was also restored. A reset counts as Byzantine until independently detected and replaced. Honest witnesses must verify actual consistency, serialize signing and persist before release. A fresh challenge demonstrates a recent signature, not necessarily the latest quorum-certified state: if checkpoint C received signatures from A/B/C, a recovery quorum B/C/D may include witnesses that have advanced to a later unwitnessed extension; signatures must be gathered on an extension of every honest signer's known prefix and the complete certified prefix recovered. The 3-of-4 intersection prevents *conflicting certified forks* only under the stated honest-witness rules; it does not by itself establish currentness of an arbitrary checkpoint or detect all rollback. Specify new epoch uniqueness, predecessor and current-epoch validation at every signer/gateway, including concurrent races and stale writers. Manifest replacement needs a root-authenticated rule or bridged policy history with an immutable genesis root. Common administration invalidates the assumed fault boundary.

**P1 F3 — Withdrawal cannot establish quiescence.** A local temporary block upon arrival is not a durable global receipt or a fence on other gateways; it may vanish on crash and an unacknowledged request can be lost. Define honest user-visible receipt semantics, retry, broadcast/fencing and fail-closed recovery without promising acceptance before finality. V6 does not specify binding output to bytes/root, recipient, purpose, one-shot send instance, gateway identity and epoch, nor globally enforce one gateway/sender or one-shot anti-replay. A final escape then withdrawal then a delayed live gateway send remains possible. V6 accounts the entire content as potentially disclosed, but does not decide whether already committed sends must stop/drain or may finish, what prevents further bytes after terminal classification, or what `WITHDRAWAL_EFFECTIVE` promises. `ESCAPED_UNKNOWN` records uncertainty; it does not stop a live sender or queued retry. Gateway restart text forbids *automatic* replay and reuse after restrictive state, but leaves other resends and stale live instances ambiguous. Specify enforceable revocation/termination or explicitly limited semantics, linearization and proof of quiescence before claiming effective withdrawal.

**P1 F4 — Canonical schema is not frozen.** V6 §Canonical CBOR v1 explicitly defers exact event payload and enum tables. It also lacks the request schema/normalization, strict numeric bounds for each field, allowed CBOR tags/simple values, genesis/sentinel rules, output bindings, bridge/challenge schemas and canonical test vectors. Deterministic CBOR gives unique bytes for a chosen data model, not equivalent requests the same semantic meaning. Define integer values, exact fields and variants, integer ranges, map/array ordering where meaningful, allowed values, absence/null rules, text prohibition or normalization, version migration and domain-separated bytes. Reject duplicates and noncanonical encodings. No implementation may be inferred from a top-level table.

**P1 F5 — Actual preflight artifact is not frozen.** Preflight v5 §Approval packet requires future source, hash, exact toolchain, binary, imports, signature/provenance, manifest, capture and call trace, but supplies none. It says minimal runtime is preferred rather than chosen. No concrete output schema, dynamic import audit, precise observer/stop procedure or artifact identity exists. The two named queries and incidental OS/AV acknowledgement are useful scope but do not constitute the requested frozen two-call artifact. Produce a verifiable packet for a later separate authorization review; do not run it.

### Major-area classification and exact verdict

| Area | Classification | Specific decision |
|---|---|---|
| Coordinator-to-witness finality | DESIGN_CORRECTION_REQUIRED | COORDINATOR_WITNESS_FINALITY_NEEDS_CORRECTION |
| Runtime versus persistent state | DESIGN_CORRECTION_REQUIRED | Separation and no terminal reactivation are sound intent; transition validity, tombstone/namespace identity and history completeness remain unspecified. |
| Genesis chain | DESIGN_CORRECTION_REQUIRED | BOOTSTRAP_CHAIN_NEEDS_CORRECTION |
| Witness quorum | DESIGN_CORRECTION_REQUIRED | WITNESS_QUORUM_MODEL_NOT_ESTABLISHED |
| Witness lifecycle/bootstrap | DESIGN_CORRECTION_REQUIRED | WITNESS_LIFECYCLE_BOOTSTRAP_NEEDS_CORRECTION |
| Fresh restart challenge | DESIGN_CORRECTION_REQUIRED | FRESH_CONTINUITY_PROOF_NEEDS_CORRECTION |
| Coordinator epoch | DESIGN_CORRECTION_REQUIRED | Witness-final epoch is appropriate, but stale-epoch validation and racing epochs are not specified. |
| Operation ID / retries | DESIGN_CORRECTION_REQUIRED | OPERATION_RETRY_MODEL_NEEDS_CORRECTION |
| Withdrawal fence | DESIGN_CORRECTION_REQUIRED | WITHDRAWAL_FENCING_NEEDS_CORRECTION |
| Escape model | DESIGN_CORRECTION_REQUIRED | ESCAPE_COMMITTED_MODEL_NEEDS_CORRECTION |
| Withdrawal/output order | DESIGN_CORRECTION_REQUIRED | WITHDRAWAL_OUTPUT_ORDERING_NEEDS_CORRECTION |
| Effective withdrawal | DESIGN_CORRECTION_REQUIRED | EFFECTIVE_WITHDRAWAL_MODEL_NEEDS_CORRECTION |
| Gateway restart/replay | DESIGN_CORRECTION_REQUIRED | No universal one-shot or stale-live-gateway fence; unknown is not quiescence. |
| Sole-path gateway | DESIGN_ACCEPTABLE_FOR_NEXT_REVIEW_STAGE | DESIGN_ACCEPTABLE_PENDING_IMPLEMENTATION_EVIDENCE: requirement only, no enforcement established. |
| Offline policy | DESIGN_ACCEPTABLE_FOR_NEXT_REVIEW_STAGE | OFFLINE_POLICY_ACCEPTABLE, conditional on verified separation and sole-path enforcement. |
| Canonical schema | DESIGN_CORRECTION_REQUIRED | CANONICAL_AUTHORITY_SCHEMA_NEEDS_CORRECTION |
| Windows local cache | DESIGN_ACCEPTABLE_FOR_NEXT_REVIEW_STAGE | Cache-only removes NTFS from the proposed authority anchor only if independent continuity is actually established; corrupt/missing state remains CLOSED. |
| Preflight artifact | DESIGN_CORRECTION_REQUIRED | READONLY_PREFLIGHT_NEEDS_CORRECTION |
| CNG AES-GCM / user-scope DPAPI and target | EVIDENCE_BLOCKED_PENDING_TARGET_QUALIFICATION | No target, cryptographic custody, key or integrated qualification evidence. |
| Threat boundaries | DESIGN_CORRECTION_REQUIRED | Explicit actor and operator privileges, independent control and output enforcement need specification. |

Top-level withdrawal/output: `WITHDRAWAL_OUTPUT_DESIGN_NEEDS_CORRECTION`.

## Analysis and conditional results

Any two distinct 3-of-4 signer sets intersect in at least 3+3-4=2 signers. At most one Byzantine signer implies at least one honest overlap, so incompatible certified forks cannot both succeed **if** honest signers authenticate and validate complete append-only extensions, durably preserve nonrollback state, and atomically serialize signing. It is not evidence these operational conditions exist. One unavailable still permits three available honest signers; two unavailable cannot form a quorum. A malicious plus unavailable signer permits progress only if the malicious signer cooperates; do not rely on that. Independence includes administrative, credential, restore and hosting failure domains. If old quorum is permanently unavailable, a safe bridge may be impossible and the namespace permanently unavailable.

Authority ACTIVE plus runtime CLOSED is coherent. Old ACTIVE database/cache cannot restore authority if full verified witnessed history establishes WITHDRAWN/DELETED; these must never reactivate within the same identity. New namespace requires authenticated identity uniqueness and anti-aliasing/tombstones. Restarts and reinstall start CLOSED. An old log restored *before an event already witnessed-final* does not undo that event; recover authenticated evidence from independent holders or remain closed. The v6 adversarial table's unconditional “Event was not authoritative” for a log restored before a final event is ambiguous and wrong if the event was already final before restore. Before finality no acknowledgment or sensitive send is authorized; after finality materialization and acknowledgment can be retried only after validated history reconstruction. Duplicate append and partially witnessed candidates need explicit reconciliation. Losing the final response is not failure.

Fresh random 256-bit challenge must bind identity, policy, namespace, attempt and checkpoint; verify fresh quorum signatures and independently verified full extension before opening. The challenge cannot prove unwitnessed writes. A quorum-certified final event survives coordinator snapshot loss, even if the event is absent from a coordinator DB. A challenge alone cannot identify history missing from all surviving witnesses. New coordinator epoch must itself be witnessed-final and checked at the authoritative serialization/validation point, not merely placed in candidate bytes. Two coordinators racing cannot both be allowed to establish valid successors of one predecessor; this requires actual enforced transition validation.

A same-ID/same-digest final retry should return a verified prior result; same ID/different digest requires adjudication. Same semantics under different IDs are distinct unless separate business-level deduplication is defined. Incomplete request normalization prevents proving equivalent encodings produce the same digest. A pending append outside the certified prefix is not safely forgotten while it can later finalize.

No sensitive byte may intentionally leave before a valid final escape. The entire bound output is potentially exposed immediately after that point, including OS buffers and even a crash before first byte. Destination receipt is not established by `ESCAPED`. Lost response or partial send cannot downgrade exposure. Effective withdrawal can use `ESCAPED_UNKNOWN` only after the design proves no further unaccounted emission/replay is possible or limits its claimed guarantee. A withdrawal fence and escape must share validated total order; quorum intersection alone is not semantic transition enforcement. V6 does not settle what a previously authorized ongoing send does after fence.

No trusted wall clock is used for authority ordering in the proposal; deadline-dependent authority remains disabled. This desk review performed no key provisioning; CNG/DPAPI remain evidence blocked.

## Threat model consequences

| Actor | Consequence |
|---|---|
| Ordinary interactive user | Limited only by verified identity, ACL and authorization boundaries; no isolation assumed. |
| Same-token process | May access broker credentials, DPAPI material or IPC; same-user security isolation unproven. |
| Stolen broker credential | Can impersonate within its privileges; witnesses do not validate genuine consent. |
| Workstation administrator, SYSTEM, kernel | Can inspect/alter processes and bypass application gateway; integrity and confidentiality against them are not claimed. |
| Coordinator administrator | Can restore, suppress, forge within held credentials or deny service; independent witnesses conditionally protect final-history consistency, not semantic authorization. |
| Coordinator cluster member | Crash/quorum behavior depends on cluster assumptions; an arbitrary Byzantine member is not covered by ordinary crash-fault consensus. |
| Log administrator | Can suppress, restore or equivocate; honest durable witnesses can reject inconsistent certified forks, availability may fail. |
| Witness administrator | One compromised/reset signer consumes Byzantine budget; common control can compromise several and defeat intersection assumptions. |
| Output-gateway administrator | Can bypass or exfiltrate under its privileges unless independently enforced; no guarantee against privileged compromise. |
| Network attacker | Authenticated endpoints and replay-resistant messages required; disruption closes operations. |
| Compromised client executable | Can misuse its permissions, misstate operations or leak data; witness history does not prove consent or correctness. |

## Adversarial schedules

These are required conservative dispositions, not executed tests; recoveries require the missing rules above and are not a PASS.

| Schedule | Disposition |
|---|---|
| Coordinator commit before append, or restore before witnessing | NOT_AUTHORITATIVE_RETRY_ALLOWED only after pending candidate is reconciled and cannot later finalize; otherwise CLOSED_PENDING_RECONCILIATION. |
| Append before quorum, partial observation or duplicate append | CLOSED_PENDING_RECONCILIATION; preserve candidates, do not infer finality or absence. |
| Quorum then coordinator crash/materialization failure/old DB/lost registry | RECOVER_FROM_WITNESSED_HISTORY after verified complete history; otherwise CLOSED_PENDING_RECONCILIATION. |
| Quorum then lost client response | RETURN_PRIOR_FINAL_RESULT after identity/digest and continuity validation. |
| Log restore before already-final event | CLOSED_PENDING_RECONCILIATION; recover event from certified independent evidence or remain closed. |
| Log equivocation or inconsistent witnessed states | CLOSED_MANUAL_ADJUDICATION; never pick convenient fork. |
| One malicious including signing both forks | CLOSED_PENDING_RECONCILIATION on disagreement; no two incompatible certified forks under the honest-intersection conditions. |
| One unavailable | RECOVER_FROM_WITNESSED_HISTORY if three honest available sign and checks pass; otherwise CLOSED_PENDING_RECONCILIATION. |
| One malicious plus one unavailable | CLOSED_PENDING_RECONCILIATION if malicious withholds; potential quorum only with its cooperation. |
| Two unavailable | CLOSED_PENDING_RECONCILIATION. |
| Witness loses checkpoint | CLOSED_PENDING_RECONCILIATION; treat compromised, new key plus valid bridge. |
| Witness restores stale backup or silently reuses old key | CLOSED_MANUAL_ADJUDICATION if detected; otherwise it is Byzantine, consuming fault budget, and cannot be assumed detectable. |
| Replacement/policy rotation | CLOSED_PENDING_RECONCILIATION until verified old 3-of-4 and new 3-of-4 bridge; bridge semantics remain unspecified. |
| Shared witness administrator compromised | CLOSED_MANUAL_ADJUDICATION; >1 compromised invalidates guarantee. |
| Stale broker/reinstall/cache rollback | CLOSED_PENDING_RECONCILIATION until verified reconstruction; never reactivate terminal namespace. |
| Fresh challenge on old/fork history | CLOSED_PENDING_RECONCILIATION if honest witness has newer incompatible state; never assume challenge itself proves latest. |
| Predictable or replayed challenge | CLOSED_PENDING_RECONCILIATION; generate fresh bound challenge. |
| Challenge quorum reached but response lost | RECOVER_FROM_WITNESSED_HISTORY if original attempt is verified; otherwise CLOSED_PENDING_RECONCILIATION. |
| Challenge appended then coordinator crashes | CLOSED_PENDING_RECONCILIATION; reconcile candidate and witnessed history before fresh epoch. |
| Stale coordinator epoch/old process or credential | CLOSED_PENDING_RECONCILIATION; reject only when epoch validation at every relevant authority point is established. |
| Two coordinators race new epochs | CLOSED_PENDING_RECONCILIATION unless one validated successor becomes final; detect competing candidates and reconcile. |
| Same ID different digest | CLOSED_MANUAL_ADJUDICATION; preserve prior record. |
| Different IDs same semantics | CLOSED_PENDING_RECONCILIATION if duplicate would be unsafe and no semantic deduplication exists. |
| Withdrawal and output race / conflicting predecessors | CLOSED_PENDING_RECONCILIATION until validated ordering and candidate finality; if escape final first account full exposure, if fence first reject later escape. |
| Withdrawal with escape candidate pending | CLOSED_PENDING_RECONCILIATION; no emission before final valid escape; resolve candidate ordering and eventual finalization. |
| Withdrawal received, witness unavailable or broker crashes before finality | CLOSED_PENDING_RECONCILIATION; no acceptance acknowledgment; ephemeral fence may be lost and receipt needs safe retry. |
| Crash before final escape | NOT_AUTHORITATIVE_RETRY_ALLOWED only after proving no final escape or emission and preventing pending finalization; otherwise CLOSED_PENDING_RECONCILIATION. |
| Crash after final escape / before first byte | CONSERVATIVELY_CLASSIFY_ESCAPED_UNKNOWN; full content potentially disclosed, prevent replay. |
| Gateway crash mid-send / partial destination acknowledgment / buffers | CONSERVATIVELY_CLASSIFY_ESCAPED_UNKNOWN; full exposure accounted, prove no further unaccounted emission. |
| Withdrawal during send or delayed gateway after fence | CONSERVATIVELY_CLASSIFY_ESCAPED_UNKNOWN; stop/drain or define limited semantics, do not assert effective withdrawal until quiescence established. |
| Gateway restart with old release | CLOSED_PENDING_RECONCILIATION; no automatic replay; no reusable permit under restrictive authority; universal one-shot enforcement still missing. |
| Permanent quorum loss / old quorum unavailable for bridge | PERMANENT_NAMESPACE_UNAVAILABLE; no untrusted override. |

## Next permitted task

Prepare a bounded v7 design correction and a concrete frozen preflight artifact packet addressing F1–F5, then obtain a new independent review. The packet is for separate review only; this decision does not authorize preflight execution or implementation. Retain PR #34 open, draft and unmerged; no private data, target access, Gate 2 PASS or P2-B. Remaining evidence includes actual independent witness operations and rollback resistance, gateway sole-path/replay/termination controls, authenticated fault recovery and adversarial crash tests, cryptographic custody, deletion/retention, Windows behavior and integrated target qualification.

## Final verdict

`P2F_CUSTODY_AUTHORITY_V6_DESIGN_NEEDS_CORRECTION`
