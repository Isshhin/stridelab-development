# Independent security/design review: StrideLab P2-F custody and authority v7

**Reviewed head:** `b5d149d2e9d6d327f4ea92fe25c55f857ec8a77c`  
**Branch / PR:** `research/runna-garmin-coaching-20261102` / #34  
**Review date:** 2026-11-07 (Asia/Manila)  
**Scope:** Desk review of the v7 closure supplement, v1 canonical protocol, v1 and v7 closure vectors, v6 preflight packet, v6 independent review, collector source, and current handoffs. No implementation, target access, collector execution, TPM/TBS operation, key provisioning, infrastructure deployment, or participant/provider/private data.

## Decision

The v7 changes address several v6 findings at the level of intent: authority validation is separated from log consistency, the transition model and one-shot send are narrowed, and a static artifact packet is recorded. They do **not** yet provide a deterministic, independently verifiable protocol. Several security-critical wire and verification rules are still absent or contradictory. The review therefore does not clear the design for a separate preflight authorization review under the requested end-to-end criterion.

`P2F_CUSTODY_AUTHORITY_V7_DESIGN_NEEDS_CORRECTION`

This is not an execution decision. The collector remains unexecuted. PR #34 remains open, draft, and unmerged.

## Major-area classifications

| Area | Classification |
|---|---|
| Transparency consistency vs application authority | `DESIGN_CORRECTION_REQUIRED` |
| Deterministic authority validator | `DESIGN_CORRECTION_REQUIRED` |
| Candidate lock / unique successor | `DESIGN_CORRECTION_REQUIRED` |
| Persistent authority state machine | `DESIGN_CORRECTION_REQUIRED` |
| Genesis / policy / bootstrap | `DESIGN_CORRECTION_REQUIRED` |
| Policy bridge | `DESIGN_CORRECTION_REQUIRED` |
| Witness / validator lifecycle | `DESIGN_CORRECTION_REQUIRED` |
| Coordinator epoch | `DESIGN_CORRECTION_REQUIRED` |
| Gateway epoch | `DESIGN_CORRECTION_REQUIRED` |
| One-shot output / send | `DESIGN_CORRECTION_REQUIRED` |
| Withdrawal semantics | `DESIGN_CORRECTION_REQUIRED` |
| Accounted output digest | `DESIGN_ACCEPTABLE_FOR_NEXT_REVIEW_STAGE` |
| Canonical schemas | `DESIGN_CORRECTION_REQUIRED` |
| Canonical test vectors | `DESIGN_CORRECTION_REQUIRED` |
| Collector source | `DESIGN_ACCEPTABLE_FOR_NEXT_REVIEW_STAGE` |
| Frozen binary packet | `EVIDENCE_BLOCKED_PENDING_TARGET_QUALIFICATION` |
| Capture/execution plan | `DESIGN_ACCEPTABLE_FOR_NEXT_REVIEW_STAGE` |
| Target qualification / runtime claims | `EVIDENCE_BLOCKED_PENDING_TARGET_QUALIFICATION` |

## 1. Transparency consistency vs application authority

The separation is conceptually correct: log consistency alone cannot authorize an application transition, and an application quorum alone cannot establish consistent inclusion. However, the closure supplement does not freeze enough proof data or verification rules to make the conjunction effective. The protocol says a certificate body binds a checkpoint digest and candidate event digest, and says validators check inclusion, but it does not define the exact signed checkpoint object, witness cosignature encoding, inclusion-proof encoding, extension-proof encoding, witness-set threshold, or the relationship between the checkpoint verified for inclusion and the checkpoint whose consistency extension is verified.

The canonical protocol says `WITNESSED_FINAL` requires consistency plus matching authority certificates. The supplement says the AuthorityFinalityProof carries approval sets but does not serialize or normatively bind the transparency proof into that proof. A consumer can therefore accept an authority certificate over checkpoint X alongside valid but unrelated transparency evidence for checkpoint Y unless an omitted protocol rule prevents it. A log can include an invalid application event; only complete validator transition validation prevents it. Conversely, valid application approval without consistent inclusion must remain non-final. Reused old transparency evidence must fail when the certificate body/checkpoint differs, but the required equality checks are not completely specified.

Validator and witness sets are also not reconciled at a precise protocol level: the text permits distinct roles/keys, but does not define whether the witness set is fixed by genesis, policy, or both for each checkpoint, or exactly which set signs each checkpoint. A checkpoint change after application validation must invalidate approvals over the previous checkpoint digest; this relies on exact certificate-body binding that is underspecified for bridge events.

**Required invariant:** one canonical finality object must bind the exact event, policy digest/epoch, log identity/origin, signed checkpoint bytes, inclusion proof, consistency-extension proof, applicable witness policy, and the same certificate body approved by the authority quorum. Failure of either proof must produce no authoritative result.

`TRANSPARENCY_AUTHORITY_SEPARATION_NEEDS_CORRECTION`

## 2. Deterministic authority validator

The protocol states the right categories of checks, but the validator cannot implement them deterministically from the frozen information. There is no complete normative definition of the genesis manifest encoding or signature, checkpoint/witness proofs, signature algorithms and signed-message domains, proposal authentication, request-to-envelope equality rules for every event, and exact control-vs-namespace history reconstruction. The supplement refers to an “event table and enum values” in an external v7 closure contract that is not present as the named correction-protocol-v7 document at the reviewed head. The v6 canonical table remains in the tree and conflicts with the supplement: e.g. CONTINUITY_CHALLENGE payload and expiry semantics differ, and SEND_STARTED's old process-binding field is removed by the supplement.

A legal-looking event with a wrong request digest is rejected only if a verifier has exact request bytes and verifies the complete request digest plus all duplicated semantic fields. The required equality relation is not stated. Similarly, a stale control head, predecessor, sequence, generation, coordinator epoch, or gateway epoch must be rejected, but the exact authority source for “current” and ordering between control and namespace heads is not frozen. The base protocol's three request examples do not cover every event and do not include exact authentication format. A digest over only a payload must not pass; the closure supplement correctly says full request/event bytes, but lacks conformance vectors for all bindings.

**Required invariant:** specify a single byte-level validation algorithm, all trust/proof inputs, exact signature algorithms/domains, and field-by-field binding checks. Add rejection cases that prove each check is actually observable.

`AUTHORITY_VALIDATION_MODEL_NEEDS_CORRECTION`

## 3. Candidate lock / unique successor

The v1 canonical protocol requires durable `predecessor_digest -> candidate_digest` locking before a validator signs and forbids a different candidate for that predecessor. This is a sound safety rule only if “durably” includes rollback-resistant persistence or rollback detection, and if the validator signs exactly the locked digest after restart. The closure supplement does not restate the lock's crash protocol or define how a validator distinguishes a lost response from a never-released approval.

A 2-vs-2 split must not be resolved by automatic unlock. But “manual adjudication or permanent unavailability” does not define a safe manual adjudication procedure or whether adjudication can supersede old locks without allowing a fifth/split history. A pending candidate can remain forever; an included but non-quorate candidate is explicitly non-authoritative, yet validators may remain locked to it indefinitely. The only clearly safe result in that case is closed pending reconciliation or permanent namespace unavailability unless a separately specified, quorum-authorized recovery protocol is added. A stale restored validator must not sign a competing candidate; its inability to detect rollback is admitted, but the protocol does not enforce the claimed one-Byzantine budget at the lock layer.

Crash before lock persistence permits retry; lock persisted/response lost must return or continue the same candidate; crash after approval release must retain lock; malicious validator signing both candidates consumes the Byzantine allowance and cannot be treated as available quorum. These dispositions are not normatively enumerated in the closure supplement.

`UNIQUE_SUCCESSOR_MODEL_NEEDS_CORRECTION`

## 4. Persistent authority state machine

The monotonic state chain and prohibition on reactivation/reuse are clear at an abstract level. The v7 material does not define a globally authoritative namespace-allocation/tombstone index that makes “never recycled” enforceable after cache restore, reinstall, or control-history replay. It also does not define exactly which terminal events remain legal after a gateway epoch is revoked or after the namespace is deleted, or the atomicity boundary between final fence certification and stopping release issuance.

Restoring an ACTIVE cache after withdrawal, replaying a pre-delete snapshot, or rolling back sequence/generation must be rejected by reconstructing certified history, not by trusting cache state. The cache is described as derived, but no full reconstruction/failure algorithm is frozen. Terminal namespaces must reject output and remain unavailable; namespace IDs must never be reused. Without a canonical append-only allocation/tombstone rule these are requirements rather than demonstrated transition invariants.

`AUTHORITY_STATE_MACHINE_NEEDS_CORRECTION`

## 5. Genesis / policy / bootstrap

The out-of-band immutable genesis is the right direction and avoids allowing the mutable coordinator to select its own root. Yet the genesis manifest has no exact canonical schema, digest vector, signature/distribution format, or explicit rules for unavailable/replaced trust artifacts. PolicyV1's listed fields do not include a log identity/origin, witness set, explicit algorithm identifiers, or validator-set identity beyond an ordered four-key array; these seem partly delegated to genesis, while the closure supplement says a bridge preserves log identity/origin. That division is not unambiguous.

The stated C2SP policy digest hashes “exact immutable artifact bytes” but no exact artifact/version/URI or normalization rule is frozen. Semantic-equivalent text reformatting will change the digest by design; the protocol must say the artifact bytes are pinned and retrievable, and that a mismatch/unavailable artifact fails closed. The PolicyV1 keys 8, 9, and 10 are literal `1` without named meanings, while the claimed policy definition relies on interpretable values. This blocks independent policy decoding. Validator key sorting is a useful canonicalization rule, but public-key lengths and algorithm types are unspecified. Policy downgrade, epoch increment and stale-policy rejection are stated outcomes without an exact policy-history validation algorithm.

`BOOTSTRAP_POLICY_MODEL_NEEDS_CORRECTION`

## 6. Policy bridge

The intended old/new dual quorum on one bridge body is reasonable, and old-quorum loss correctly means closed/unavailable rather than a new-only bridge. However, CertificateBodyV1 contains one `policy_epoch` and one `policy_digest`, while ApprovalSet carries a policy digest and a bridge has both old and new policies. The exact certificate-body values for a bridge, signature message, old/new activation interpretation, and binding of both exact PolicyV1 objects to the body are not specified. “Same body” is not enough unless both approval sets cryptographically sign identical canonical bytes and validators independently verify both policy objects and both epochs.

No bridge vector includes serialized AuthorityFinalityProof with both approval sets/signatures; no exact old/new quorum key-index interpretation is given for a changed validator set. Same-epoch reuse, downgrade, wrong control head, mismatched bridge payloads, replay, and split candidate locks are named risks but lack normative reject/closure rules. The protocol must preserve log identity/origin and independently validate old and new transparency policy evidence; the supplement asserts preservation but does not define those proof fields.

`POLICY_BRIDGE_MODEL_NEEDS_CORRECTION`

## 7. Witness / validator lifecycle and rollback

The closure states that one compromised/reset/rolled-back validator consumes the one-Byzantine budget, and that shared administration/restore domains violate independence. It correctly admits a restored process cannot necessarily detect its own rollback. This is honest but not a lifecycle control. There is no frozen reset/restore detection, key invalidation, operator credential separation, replacement ceremony, common-host failure handling, or proof that witness and validator identities do not share one failure domain. Silent key reuse after reset is not detectable from local durable state alone.

One malicious validator plus one unavailable validator leaves only two honest available approvals and therefore no quorum: closed, never a security PASS. Two unavailable means no progress. Two compromised invalidates the premise. Replacement without a valid old/new policy bridge is forbidden, but the exact recovery path under old quorum loss is permanent unavailability. These availability consequences are not all stated as mandatory dispositions in the v7 closure.

`WITNESS_VALIDATOR_LIFECYCLE_NEEDS_CORRECTION`

## 8. Coordinator epoch

Fresh IDs and certified unique successors are specified, and credentials alone are not currentness. This blocks ordinary stale coordinator messages only if validators compare against one fully reconstructed control head and verify a coordinator signature over the canonical request/event. The signature scheme, exact signed bytes, coordinator public-key binding and operation are not fully frozen. The canonical protocol's `COORDINATOR_EPOCH_START` payload has an epoch ID, predecessor digest, and key ID; it does not encode the coordinator public key directly, and no exact resolution from key ID to authenticated key material is defined in the closure supplement. A certified epoch followed by materialization failure must remain final and recover from the same event, never mint a competing epoch, but this procedure is not defined.

`COORDINATOR_EPOCH_MODEL_NEEDS_CORRECTION`

## 9. Gateway epoch

The gateway epoch has an ID/key ID and revoke event, and SEND_STARTED binds the gateway epoch through the envelope plus a detached gateway signature in the supplement. But the detached signature schema, signature domain, key encoding, exact signed message, and proof-to-certificate binding are absent. A stale process retaining a private key must be rejected based on certified current epoch at the validators and gateway; local credentials alone do not suffice.

Competing gateway starts must be serialized. Rotation during an active send and terminal classification after revocation need explicit rules: revocation must prevent new sends under the old epoch, while the already-final SEND_STARTED must still be classifiable exactly once. A revoked gateway waking after withdrawal cannot create a new send. These are not completely specified.

`GATEWAY_EPOCH_MODEL_NEEDS_CORRECTION`

## 10. One-shot output / send

Binding output, content root/length, recipient, purpose, release ID and epochs into ESCAPE_COMMITTED is sound in concept; consuming a release at SEND_STARTED and treating uncertainty as consumed is conservative. But the canonical v1 and v7 supplement conflict: v1 includes `send_instance_id` and `gateway_process_binding` in ESCAPE_COMMITTED and a process binding in SEND_STARTED; the v7 supplement removes the process-binding field and substitutes a detached gateway signature without freezing its schema. The event's gateway epoch alone does not prove which process used the key.

The unique key is stated as (namespace, output, release), not globally output or content. A second release ID can therefore authorize another send of the same output, and a new operation ID can evade deduplication unless a new release is itself a distinct user authorization with explicit duplicate-exposure semantics. This may be an intentional fresh release, but the protocol does not say so. Gateway crash before first byte, mid-send, lost completion, disconnect, reconnect and SEND_UNKNOWN must all map to consumed state; same-release retries are correctly forbidden. A new release must never be presented as retry/recovery for an uncertain prior send.

`ONE_SHOT_OUTPUT_MODEL_NEEDS_CORRECTION`

## 11. Withdrawal semantics

The narrowed guarantee is appropriately limited: a final fence prevents new ESCAPE_COMMITTED and SEND_STARTED finality and new release authorization; a prior final SEND_STARTED is conservatively exposure counted in full. It makes no packet-recall, buffer-erasure, recipient-erasure or transport-quiescence claim.

The remaining gap is serialization and completion rules. The protocol does not define exact authority ordering when fence races escape/send; whether gateway stops a live stream immediately or only prevents subsequent starts; how a late gateway learns a fence; how a partial send transitions; whether terminal sends may finalize after fence (they must, for accounting); and the precise set of pre-fence sends used in withdrawal accounting. The required disposition for incomplete terminal accounting is closed, not WITHDRAWN. A stale gateway after fence must not start any new send even if it has not learned of fence, which requires every SEND_STARTED to obtain a fresh final certificate against the current head.

`WITHDRAWAL_OUTPUT_DESIGN_NEEDS_CORRECTION`

## 12. Accounted output digest

The definition `SHA-256("StrideLab/P2F/v1/accounted-outputs\0" || deterministic_CBOR(array))`, fixed-width IDs/digests, pairwise duplicate prohibition and lexicographic ordering by raw `output_id || release_id` are unambiguous in the requested contract. Count must equal array length; every pre-fence output must appear exactly once, and every terminal digest must identify its certified terminal event. These inclusion checks still depend on the incomplete history reconstruction described above, but the digest construction itself is acceptable for the next review stage.

`WITHDRAWAL_ACCOUNTING_DIGEST_ACCEPTABLE`

## 13. Canonical request / event / certificate / policy schemas

The canonical protocol freezes deterministic-CBOR restrictions, domain-separated full-object request/event/certificate/policy digests, integer-key ordering, and broad field tables. The v7 closure supplement improves full Request/EventEnvelope/CertificateBody definitions. However, it does not freeze all objects requested: Genesis Trust Manifest, transparency checkpoint, inclusion/consistency proofs, AuthorityFinalityProof wire representation, cryptographic signature algorithms/domains, C2SP policy artifact, coordinator authentication, and detached gateway signatures lack exact schemas. ApprovalSet identifies policy digest and index/signature pairs but signature bytes lack an algorithm and signed-message format. PolicyV1 contains unnamed literal fields. ZERO32 placement is inconsistent across the v1 table and v7 supplement, and the existing vector file remains a historical payload-only set. No complete per-event request schemas or rules bind request payload to envelope payload.

The failure cases listed in prose are not sufficient without exact parser rejection fixtures and expected errors/objects. A malformed but legal-looking payload must fail before any authority state mutation.

`CANONICAL_AUTHORITY_PROTOCOL_V1_NEEDS_CORRECTION`

## 14. Canonical test vectors

The v7 closure vector document covers full request, EventEnvelope and CertificateBody byte strings/digests for CONTINUITY_CHALLENGE, AUTHORITY_ISSUED, ESCAPE_COMMITTED, SEND_STARTED, WITHDRAWAL_FENCE and POLICY_BRIDGE, plus old/new PolicyV1 bytes/digests. This is a material improvement over the historical four payload vectors. The generator self-check reported in the packet is implementation-side evidence, not independent verification; no independent digest recomputation was performed in this desk review.

Required coverage remains incomplete: AuthorityFinalityProof is only shown as a schematic map, without a canonical byte vector or actual dual approval sets over one exact certificate body. No signed checkpoint/witness proof vector exists. Rejection fixtures are a prose list, not canonical malformed-byte fixtures with expected parser/result values. The test vector coverage also lacks complete SEND_COMPLETED/STOPPED/UNKNOWN, WITHDRAWAL_ACCOUNTED, DELETE, coordinator/gateway start/revoke and terminal/replay cases. The prior payload vector artifact explicitly says POLICY_BRIDGE and withdrawal additions were required and is still in the tree; the supplement says the generated closure vector is authoritative but does not clearly deprecate that file.

`CANONICAL_TEST_VECTORS_NEED_CORRECTION`

## 15. Pre-flight collector source

Static source review only; the collector was not executed. Source at the reviewed head performs exactly the two requested information calls: dynamically resolved `RtlGetVersion` and `GetNativeSystemInfo`. The remaining APIs are symbol lookup/module lookup, standard-output retrieval, console writes and process exit. There is no CRT, heap, filesystem, registry, network, child process, elevation, or identifier use in the source. Decimal formatting is bounded and correct for the Windows unsigned fields used, including zero; reversal advances the left pointer once per iteration. The 96-byte line buffer is sufficient for the fixed labels plus three 32-bit decimal components and CRLF. Failure exits nonzero before fabricating a successful line. The planned direct console capture is consistent with WriteConsoleA; redirection is intentionally excluded.

This is a source review, not proof of target behavior or independent binary disassembly.

`READONLY_PREFLIGHT_SOURCE_ACCEPTABLE`

## 16. Frozen binary evidence

The packet records the expected 3,072-byte binary SHA-256, six KERNEL32 imports, dynamic RtlGetVersion lookup, no CRT helpers, unsigned status, toolchain, workflow/job/runner, artifact ID, archive digest, and normalized CRLF/LF source identity. It clearly distinguishes artifact identity from runtime/target qualification and states the collector was NEVER EXECUTED. The CRLF-vs-LF explanation is coherent.

However, this review did not download/re-hash the binary artifact or independently inspect the PE; the connected repository evidence available here is the static-build report, not the binary bytes. Therefore binary content/provenance is not independently confirmed in this review. Target execution and target assumptions remain unqualified and must be part of later authorization/qualification gates.

`READONLY_PREFLIGHT_BINARY_PACKET_NEEDS_CORRECTION`

## 17. Pre-flight capture / execution plan

The future procedure is bounded: standard non-elevated console, exact hash check, direct invocation, no wrappers/redirection/logging helper, two visible output lines, manual transcription, stop on unexpected behavior, record incidental OS/AV/EDR/audit effects separately. The procedure does not imply execution authority in this review.

The package may be taken to a separate authorization review only after the binary evidence can be independently inspected and the missing protocol blockers are scoped away from that decision. This review finds that the requested combined closure is not yet coherent enough to clear that gate.

`READONLY_PREFLIGHT_NEEDS_CORRECTION`

## 18. Threat model and adversarial schedule dispositions

Threat model limits: under at most one Byzantine validator and genuinely independent durable validator state, 3-of-4 approvals can preserve integrity, while two unavailable validators remove availability. One malicious plus one unavailable likewise prevents quorum; it is closed, not a pass. Two compromised validators invalidate the assumption. Compromised coordinator/log administrators can suppress service but must not create valid authority alone if honest validators verify the complete state. Gateway-admin, SYSTEM, kernel, or compromised client execution can bypass application-level controls or produce outputs outside the protocol; the protocol cannot prove genuine human consent. Ordinary users and same-token processes remain within their actual OS access boundary; broker credential compromise is stronger than ordinary process compromise. Common administrators, hosts, storage, credentials or restore paths defeat independence.

| Adversarial schedule | Required disposition |
|---|---|
| Two candidates, same predecessor; validator locks one | `CLOSED_PENDING_RECONCILIATION`; never certify the other automatically |
| Two validators lock A, two lock B | `CLOSED_MANUAL_ADJUDICATION` only under a separately specified recovery; otherwise `PERMANENT_NAMESPACE_UNAVAILABLE` |
| Pending candidate never reaches quorum | `CLOSED_PENDING_RECONCILIATION`; no unlock or different successor |
| Stale validator rollback / backup restore | Treat as Byzantine; reject conflicting approval if detectable; no availability PASS |
| One malicious validator | At most one bad signature; require 3 valid approvals |
| One malicious + one unavailable | No quorum; remain closed |
| Two unavailable | No quorum; remain closed |
| Old policy quorum lost during bridge | No bridge; permanent closed/unavailable |
| Old/new bridge payloads or bodies differ | Reject; no activation |
| Stale coordinator epoch | Reject candidate |
| Competing coordinator epochs | Unique-successor lock; loser closed pending reconciliation |
| Stale gateway epoch | Reject new send |
| Competing gateway epochs | Unique-successor lock; loser closed pending reconciliation |
| Duplicate SEND_STARTED | Reject; return prior final result or `SEND_ALREADY_CONSUMED` |
| Gateway crash before first byte after SEND_STARTED | `SEND_UNKNOWN`; send consumed |
| Gateway crash mid-send | `SEND_UNKNOWN`; count full bound content as exposed |
| SEND_UNKNOWN then retry under same release | `SEND_ALREADY_CONSUMED` |
| Withdrawal before ESCAPE_COMMITTED finality | Reject escape |
| Withdrawal after ESCAPE_COMMITTED but before SEND_STARTED | Reject new send; release remains unused/closed |
| Withdrawal after SEND_STARTED finality | Count full output; allow only terminal classification |
| Stale gateway after withdrawal | Reject new SEND_STARTED |
| Malformed CBOR / wrong request digest | Reject before mutation |
| Stale control head / wrong certificate checkpoint | Reject; no finality |
| Log rollback after certified event | Detect via pinned checkpoint history; otherwise fail closed |
| Local cache rollback / reinstall | Rebuild from certified history; no cache-authoritative state |
| Permanent quorum loss | `PERMANENT_NAMESPACE_UNAVAILABLE`; never downgrade quorum |

These are the safe intended dispositions, not proof that the frozen protocol can enforce them. In particular, the missing cryptographic proof and recovery schemas block conformance.

## Required verdicts

- Transparency/application separation: `TRANSPARENCY_AUTHORITY_SEPARATION_NEEDS_CORRECTION`
- Authority validation: `AUTHORITY_VALIDATION_MODEL_NEEDS_CORRECTION`
- Candidate lock: `UNIQUE_SUCCESSOR_MODEL_NEEDS_CORRECTION`
- State machine: `AUTHORITY_STATE_MACHINE_NEEDS_CORRECTION`
- Bootstrap/policy: `BOOTSTRAP_POLICY_MODEL_NEEDS_CORRECTION`
- Policy bridge: `POLICY_BRIDGE_MODEL_NEEDS_CORRECTION`
- Witness/validator lifecycle: `WITNESS_VALIDATOR_LIFECYCLE_NEEDS_CORRECTION`
- Coordinator epoch: `COORDINATOR_EPOCH_MODEL_NEEDS_CORRECTION`
- Gateway epoch: `GATEWAY_EPOCH_MODEL_NEEDS_CORRECTION`
- One-shot send: `ONE_SHOT_OUTPUT_MODEL_NEEDS_CORRECTION`
- Withdrawal: `WITHDRAWAL_OUTPUT_DESIGN_NEEDS_CORRECTION`
- Accounting digest: `WITHDRAWAL_ACCOUNTING_DIGEST_ACCEPTABLE`
- Canonical protocol: `CANONICAL_AUTHORITY_PROTOCOL_V1_NEEDS_CORRECTION`
- Canonical vectors: `CANONICAL_TEST_VECTORS_NEED_CORRECTION`
- Preflight source: `READONLY_PREFLIGHT_SOURCE_ACCEPTABLE`
- Preflight binary packet: `READONLY_PREFLIGHT_BINARY_PACKET_NEEDS_CORRECTION`
- Separate authorization readiness: `READONLY_PREFLIGHT_NEEDS_CORRECTION`

## Blocking corrections and remaining evidence

1. Freeze a complete canonical root, checkpoint, witness proof, signature, AuthorityFinalityProof and per-event request/authentication schema, including exact domains and equality bindings.
2. Reconcile the v1 canonical event table with the v7 closure supplement and identify the authoritative protocol artifact at the reviewed head.
3. Specify lock persistence/recovery and safe terminal outcomes for crashes, lost responses, split locks, pending inclusion and rollback. No automatic unlock.
4. Specify PolicyV1 fields and immutable C2SP artifact identity; freeze bridge-body semantics and old/new key-index/signature validation.
5. Define globally non-reusable namespace allocation/tombstoning and the exact post-fence/post-revocation terminal-send rules.
6. Add canonical full-object AuthorityFinalityProof and dual-approval vectors, signed checkpoint/witness proof vectors, and malformed-byte rejection fixtures; independently recompute representative digests.
7. Make binary bytes available for independent hash/PE verification before a separate authorization review. Any future target qualification, if authorized separately, must remain read-only and bounded.

## Evidence and task state

- Starting head: `b5d149d2e9d6d327f4ea92fe25c55f857ec8a77c`
- Final head: recorded in the completion report after this additive review is committed.
- PR #34: open, draft, unmerged at review start; preserve these states.
- Exact-head CI at starting head: research validation run `42373666929` passed (436 tests); Graphify mirror run `42373662496` failed at startup under the separate billing/spending limit. The aggregate commit status remained pending because statuses were empty; do not describe all checks as green.
- Files changed: this new independent review record and, if necessary, the two current Engine 2.x handoffs only.
- No v7 design, collector, vectors, frozen packet, workflow or implementation was modified.
- Collector: NEVER EXECUTED.
- No target access, target qualification, TPM/TBS, keys, private data, Engine 1.5, Trackside, APK/release, Session G, P2-B, or Gate 2 PASS.
- Exact next permitted task: correct the documented protocol/schema/vector defects, then obtain a new independent review. Do not execute the preflight collector.
- Overall: `P2F_CUSTODY_AUTHORITY_V7_DESIGN_NEEDS_CORRECTION`
