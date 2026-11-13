# P2-F custody/authority v7 correction closure

Status: `P2F_CUSTODY_AUTHORITY_V7_CORRECTIONS_INCOMPLETE`. The bounded protocol/schema/vector corrections are recorded for the next independent review; the review's independent binary-inspection blocker remains open, so the strict all-blockers decision rule does not permit overall READY. This is v7, not v8, implementation permission, independent acceptance, or preflight permission.

Starting head: `37a58e927601522200c31aa08c6b7fd21902a1d6`. Review: [independent v7 review](p2-f-custody-authority-v7-independent-security-review-2026-11-07.md), unchanged. PR #34 must remain OPEN, DRAFT, UNMERGED.

## 1. Authority and scope

This document is the **only normative v7 protocol/schema/validation specification**. Its generated [vector catalogue](p2-f-custody-authority-v7-closure-test-vectors-2026-11-07.md) and adjacent JSON fixture are normative expected bytes/results subordinate to these rules. In a discrepancy, fail closed and correct the fixture; do not choose whichever interpretation accepts. The earlier canonical-protocol-v1, closure-supplement, canonical-test-vectors-v1 and v1–v6 proposals are historical, superseded in full for protocol conformance. Historical `current` status lines in handoffs have no precedence over their first current entry. Independent reviews remain immutable evidence, not protocol definitions. The frozen preflight packet remains reference evidence, never execution authorization.

The model remains separate transparency witnesses and authority validators, each 3-of-4; immutable genesis; certified control/namespace history; one-shot gateway release; no terminal namespace reactivation. The review exposed an undefined cross-head serialization boundary. The narrow correction is **one pending next log slot across this trust domain**. Each checkpoint adds exactly one event to the previous final checkpoint. An unfinished slot cannot be skipped. This deliberately sacrifices availability to prevent certification against a stale control head or a fence racing a start. It adds no recovery quorum, alternative authority, or new event type.

All rules below are MUST/reject rules. A verifier is an offline deterministic decision procedure on complete supplied history. An old proof can establish historical finality only, never current authority. A live signer must additionally use its independently retained durable high-water marks/locks; an offline consumer without a fresh certified challenge and retained anchor remains CLOSED for sensitive use. No claim of undetectable rollback detection or genuine human consent is made.

## 2. Primitive encoding, hashes and signatures

`C(x)` is deterministic CBOR under [RFC 8949](https://www.rfc-editor.org/rfc/rfc8949.html), using ONLY unsigned integers, bstr, arrays and integer-key maps. All lengths definite and minimal, all integers shortest form, maps strictly ascending unsigned keys, no duplicate keys, unknown/missing fields or defaults, no text, negative integers, tags, floats, null, bool, other simple values, indefinite encoding or trailing bytes. Parse the whole input; reject nested noncanonical objects as well. `u64=0..2^64-1`; additions reject overflow. Versions and algorithm IDs equal integer 1. Enums accept only listed values. `B` means exactly 32-byte bstr, `S` exactly 64-byte bstr, `R` a bstr containing exact canonical object bytes, `A(T)` an array of T. IDs, keys and ordinary digests cannot be ZERO32; ZERO32 is only allowed in positions explicitly listed below. Raw content length/count may be zero.

For each ASCII label L, `H(L,x)=SHA256(ASCII("StrideLab/P2F/v1/"+L) || 00 || x)`. The separator is ONE NUL octet, not a backslash and zero. Labels: `request`, `event`, `cert`, `policy`, `genesis`, `log-id`, `validator-set`, `witness-policy`, `checkpoint`, `transparency`, `accounted-outputs`. There are no omitted fields or implicit normalization. `log-id=H("log-id", C([origin,log_public_key]))`; `validator-set=H("validator-set",C(ordered_validator_keys))`. `genesis` hashes the entire signed manifest. Checkpoint digest hashes the entire signed checkpoint, excluding all witness approvals. Ordinary object equality means exact bytes; digest equality uses all 32 bytes.

All signatures use **pure Ed25519**, [RFC 8032](https://www.rfc-editor.org/rfc/rfc8032.html), not Ed25519ph/ctx, over `ASCII("StrideLab/P2F/v1/"+domain) || 00 || exact_message_bytes`. No prehash outside Ed25519. Raw public keys are 32 octets, raw signatures 64; no DER, PEM, base64 or key-ID lookup in wire objects. Reject noncanonical point encodings, identity/small-order/non-prime-subgroup public keys and R, and S >= group order; require the uncofactored verification equation. Key index refers to raw-key ascending order, never an operator name. Distinct role keys are required; a bridge may retain unchanged keys within the same role. The application CBOR checkpoint/signature profile is not a C2SP signed-note transport and must not be represented as such.

| Signed role/object | Signature domain | Exact message bytes |
|---|---|---|
| Genesis root | `sig/genesis` | GenesisBodyV1 bytes |
| Log checkpoint | `sig/checkpoint` | CheckpointBodyV1 bytes |
| Transparency witness | `sig/witness` | `C({0:checkpoint_bytes,1:witness_policy_digest})` |
| Ordinary validator | `sig/validator` | CertificateBodyV1 bytes |
| Bridge old quorum | `sig/bridge-old` | SAME CertificateBodyV1 bytes |
| Bridge new quorum | `sig/bridge-new` | SAME CertificateBodyV1 bytes |
| Current/new coordinator | `sig/coordinator` | `C({0:request_bytes,1:event_bytes})` |
| Coordinator root | `sig/coordinator-root` | same request/event pair |
| Current/new gateway | `sig/gateway` | same request/event pair |
| Gateway root | `sig/gateway-root` | same request/event pair |

Verification order: canonical parsing and schema; immutable genesis; exact request binding and already-final retry lookup; current artifact/policy binding; replay/transition checks; role authentication; checkpoint signature/inclusion/extension/witness checks; exact certificate reconstruction; approval sets. No mutation on failure. Validators execute the same order without the not-yet-created approval sets, persist locks, then sign. No wrong-domain/object/policy/checkpoint fallback is permitted.

## 3. Immutable genesis, policy and C2SP artifact

`GenesisBodyV1={0:1,1:trust_domain B,2:origin bstr,3:log_public_key B,4:log_id B,5:initial_policy R,6:genesis_root_public_key B,7:1 /*SHA256*/,8:1 /*CBOR profile*/,9:1 /*Ed25519*/}`.
`GenesisTrustManifestV1={0:genesis_body R,1:root_signature S}`. Origin is 1..255 ASCII octets from `0x21..0x7e`, no plus; it is immutable and compared byte-for-byte. Initial PolicyV1 epoch is exactly 0. Its domain, log ID and origin equal genesis; its witness policy defines the four initial witnesses. Genesis defines algorithms and log; initial policy defines initial validator/witness/coordinator-root/gateway-root keys. There is no genesis operational coordinator/gateway epoch.

Distribution MUST pin both exact signed-manifest digest and root public key out of band in independently controlled read-only installation configuration. A supplied manifest's self-signature alone is insufficient. Verify the root signature and both pins before replay. Missing pins/manifest/policy artifact => CLOSED. Installation replacement cannot change the pins or erase a previously observed high-water mark. There is no in-band genesis replacement or log rotation in v1. Production pins, independence and custody remain unprovisioned/unqualified by this document; fixture pins are public test material only.

`WitnessPolicyV1={0:1,1:trust_domain B,2:log_id B,3:witness_public_keys A(B)[4],4:3,5:1 /*Ed25519*/}`. Keys distinct, strictly ascending. Its digest uses `witness-policy`.

`PolicyV1={0:1,1:trust_domain B,2:policy_epoch u64,3:c2sp_artifact_sha256 B,4:validator_public_keys A(B)[4],5:3 /*authority quorum*/,6:coordinator_root_public_key B,7:gateway_root_public_key B,8:1 /*Ed25519*/,9:1 /*SHA256*/,10:1 /*CBOR profile*/,11:validator_set_digest B,12:witness_policy R,13:log_id B,14:origin bstr,15:c2sp_policy_artifact bstr}`.

Keys 8–10 now have named meanings. Validator keys distinct and ascending; key 11 recomputed; witness domain/log match policy/genesis; all role keys disjoint (log, genesis root, coordinator root, gateway root, witnesses, validators). Check both old/new policies' algorithm literals and quorums; v1 cannot downgrade/change these. C2SP artifact is the **policy configuration**, not the C2SP prose specification or a mutable URL. Its exact bytes are embedded at key 15, so an offline verifier needs no network. Key 3 is raw SHA256 over those bytes, with no prefix or newline conversion.

Freeze the restricted artifact syntax as the following ASCII LF-only template, with no comments, URLs, blank lines, CR, BOM or extra spaces; final LF mandatory:

```text
log <log-vkey>
witness w0 <w0-vkey>
witness w1 <w1-vkey>
witness w2 <w2-vkey>
witness w3 <w3-vkey>
group witnesses 3 w0 w1 w2 w3
quorum witnesses
```

This is a restricted [C2SP tlog-policy](https://c2sp.org/tlog-policy) trust-policy projection. Vkey is `name + "+" + lowerhex8(key_id) + "+" + canonical_padded_base64(type_byte || raw_key)`; key_id is first four SHA256 octets of `ASCII(name)||0a||type_byte||raw_key`, per [signed-note](https://c2sp.org/signed-note). Log name=origin, type=01; witness name=`w` plus its raw-key-order index, type=04. The keys/quorum are cross-checked against genesis/WitnessPolicyV1, and validators MUST reconstruct the exact template bytes, not accept semantic equivalents. Type04 records the witness key's C2SP trust-policy representation; P2-F witness evidence is verified solely by the application domain/schema above, not passed to a C2SP note verifier. No generic C2SP parser, online lookup, timestamp assurance, or interoperability claim is implied. A different artifact byte, mismatched keys, unavailable artifact, or unsupported syntax => CLOSED.

## 4. Exact request, envelope and all payloads

`RequestV1={0:1,1:event_type u64,2:operation_id B,3:trust_domain B,4:namespace_id B,5:payload map}`.

`EventEnvelopeV1={0:1,1:event_type,2:trust_domain B,3:policy_epoch u64,4:policy_digest B,5:control_sequence u64,6:control_head_digest B,7:namespace_id B,8:namespace_sequence u64,9:authority_generation u64,10:coordinator_epoch_id B,11:gateway_epoch_id B,12:previous_final_event_digest B,13:operation_id B,14:request_digest B,15:payload map}`.

Request/event equality: request[0]=event[0]=1; request[1]=event[1], [2]=event[13], [3]=event[2], [4]=event[7], and `C(request[5])=C(event[15])` with EVERY key checked. Event[14]=H(request,exact RequestV1 bytes). No payload-only request digest is accepted. No payload fields are omitted as “derived.” The complete payload table below applies identically to BOTH objects; all keys required, no extras.

| Type | Event | Exact payload (B unless annotated) |
|---:|---|---|
|1|CONTINUITY_CHALLENGE|`{0:challenge_id,1:challenged_checkpoint_digest,2:nonce32,3:expected_control_sequence u64}`|
|2|COORDINATOR_EPOCH_START|`{0:new_epoch_id,1:predecessor_control_digest,2:new_coordinator_public_key}`|
|3|GATEWAY_EPOCH_START|`{0:new_epoch_id,1:predecessor_control_digest,2:new_gateway_public_key}`|
|4|GATEWAY_EPOCH_REVOKE|`{0:gateway_epoch_id,1:revoke_reason enum}`|
|5|POLICY_BRIDGE|`{0:old_policy_digest,1:new_policy_digest,2:old_epoch u64,3:new_epoch u64,4:activation_control_sequence u64}`|
|16|AUTHORITY_ISSUED|`{0:grant_id,1:subject_ref,2:scope_digest}`|
|17|ESCAPE_COMMITTED|`{0:output_id,1:content_root,2:content_length u64,3:recipient_ref,4:purpose enum,5:release_id,6:explicit_authorization_id}`|
|18|SEND_STARTED|`{0:output_id,1:release_id,2:send_instance_id}`|
|19|SEND_COMPLETED|`{0:output_id,1:release_id,2:send_instance_id,3:completion_digest}`|
|20|SEND_STOPPED|`{0:output_id,1:release_id,2:send_instance_id,3:stop_reason enum}`|
|21|SEND_UNKNOWN|`{0:output_id,1:release_id,2:send_instance_id,3:uncertainty_reason enum}`|
|22|WITHDRAWAL_FENCE|`{0:withdrawal_id,1:fence_reason enum}`|
|23|WITHDRAWAL_ACCOUNTED|`{0:withdrawal_id,1:accounted_output_digest,2:accounted_count u64}`|
|24|DELETE|`{0:tombstone_id,1:deletion_reason enum}`|

Purpose=1 USER_EXPORT,2 BACKUP,3 PUBLICATION,4 DERIVED_OUTPUT,5 API_RESPONSE,6 SYNC. Stop=1 WITHDRAWAL,2 GATEWAY_REVOKED,3 OPERATOR_STOP. Unknown=1 CRASH_AFTER_START,2 TRANSPORT_UNKNOWN,3 EVIDENCE_INCOMPLETE. Revoke/fence/deletion reasons each=1 USER_REQUEST,2 SECURITY,3 ADMINISTRATIVE. No unspecified enum values. Continuity has no wall-clock expiry; [1] equals previous checkpoint digest, [3] equals the preceding control sequence. Both checkpoint/control predecessor are ZERO32 only before any log/control event respectively. Newly allocated IDs are fresh/nonzero; references repeat the exact prior ID. Challenge ID/nonce cannot be reused. Cross-category equality of opaque IDs has no meaning; freshness registries are per named category except epochs (shared coordinator/gateway category) and public keys (role separation across the complete history).

`subject_ref` and `recipient_ref` are opaque fixed-width identifiers whose equality is byte equality, not a mutable name lookup. `scope_digest=SHA256(exact immutable application scope artifact bytes)`; `content_root=SHA256(exact entire output bytes)` (a flat commitment, not an unspecified Merkle format); `completion_digest=SHA256(exact classification-evidence bytes)`. The gateway MUST compare actual output bytes/length/recipient/purpose against the final escape before sending. Validators bind these signed commitments; they do not infer real subject identity, scope suitability, consent, transport delivery or recipient behavior from a digest. Missing required application artifacts for actual use keeps that use CLOSED. Fixtures identify only invented artifacts and do not establish these operational checks.

Control types 1–5: event[7]=ZERO32, [8]=[9]=0, [12]=current control event digest or ZERO32 at genesis; [5]=current control sequence+1, [6]=that current control digest. Namespace types 16–24: nonzero namespace; [5]=current control sequence, [6]=current control event digest; [8]=previous namespace sequence+1 (1 initially), [12]=previous namespace event digest (ZERO32 only on issue); [9]=previous authority generation+1 on EVERY namespace event (1 initially). A generation is a monotone authority revision, not a state enum.

Event[10] is current coordinator epoch on every event except the first coordinator start, where ZERO32 is required; a later coordinator start binds the OLD current epoch and declares the new one in payload[0]. Before coordinator bootstrap, only type2 is allowed. Event[11] is current nonrevoked gateway epoch for ESCAPE_COMMITTED and SEND_STARTED; the epoch of the referenced final SEND_STARTED for terminal types19–21 (possibly now revoked); ZERO32 for all other types, including gateway control events. ZERO32 in payload fields is forbidden except type1[1] and type2/3[1] at the true empty predecessor.

`DetachedAuthenticationV1={0:role enum,1:epoch_id B,2:public_key B,3:signature S}`. Role=1 coordinator,2 coordinator-root,3 gateway,4 gateway-root. Root roles use ZERO32 epoch. Array sorted by (role,epoch_id,public_key), unique tuples, exact required entries only. Ordinary events require current coordinator authentication; first coordinator start requires root2 plus new coordinator1 possession proof; later starts require root2, old coordinator1 and new coordinator1. Type3 additionally requires gateway-root4 and new gateway3 possession proof; type4 additionally gateway-root4. Type18 additionally current gateway3; terminal classification needs only current coordinator1, allowing recovery after gateway loss/revoke. Epoch payload key is the actual public key, never a key ID. New keys cannot equal ANY key previously used by another role; coordinator/gateway epoch starts require fresh keys and epoch IDs not seen before. Unavailable required roots/current coordinator => CLOSED; no unsigned administrative replacement.

## 5. Complete transparency and authority finality objects

`CheckpointBodyV1={0:1,1:trust_domain B,2:log_id B,3:origin bstr,4:tree_size u64,5:tree_root B,6:policy_epoch u64,7:policy_digest B,8:previous_signed_checkpoint_digest B}`.
`CheckpointV1={0:checkpoint_body R,1:log_signature S}`. A checkpoint is not a witness approval. First candidate size=1, previous digest=ZERO32. Later size=last final size+1, previous digest=H(checkpoint,last final CheckpointV1 bytes). Header policy is the pre-event (old for bridge) policy. Genesis empty tree root=SHA256(empty bytes), no signed empty checkpoint; genesis is the only empty anchor.

Merkle leaf=SHA256(00 || exact EventEnvelopeV1 bytes); parent=SHA256(01 || left32 || right32). Tree shape and inclusion order follow [RFC 9162 §2.1](https://www.rfc-editor.org/rfc/rfc9162.html#section-2.1): split a non-singleton at the largest power of two strictly less than its leaf count. No event-digest substitution for leaf bytes.

`InclusionProofV1={0:checkpoint_digest B,1:leaf_index u64,2:tree_size u64,3:path A(B)}`. Index MUST be size-1 (the next slot), size MUST equal checkpoint size, digest MUST equal its exact signed checkpoint. Recurse using the split above; consume sibling path bottom-up, appending the opposite subtree root at each recursion. Recompute root; reject missing/extra path nodes, wrong direction/index/size/root.

`ConsistencyProofV1={0:previous_checkpoint_digest B,1:previous_size u64,2:previous_root B,3:new_checkpoint_digest B,4:new_size u64,5:new_root B,6:prior_leaf_hashes A(B)}`. This profile intentionally uses a **complete-prefix extension proof**, not an underspecified compact audit path. Array length=previous_size, order=log order; Merkle root of array equals previous_root. Append the candidate leaf hash and recompute new_root; new_size=previous_size+1. Match all six scalar fields to the retained anchor and current checkpoint. For previous_size=0 only previous digest=ZERO32, root=SHA256(empty), array=[] accepted. This O(n) proof is exact and independent of hash-preimage disclosure; a compact proof requires a future explicit profile, not a permissive parser.

`WitnessApprovalSetV1={0:witness_policy_digest B,1:approvals A([index u64,signature S])}`. Exactly 3 or 4 indexes, strictly ascending, within0..3; verify ALL supplied signatures, never ignore an invalid extra. Each signs the same exact checkpoint bytes plus this witness-policy digest under `sig/witness`. Witnesses verify the extension from their independently retained checkpoint and durably advance before signature release; never sign inconsistent roots, same-slot alternative roots, or lower size. Distinct old/new witness policies on a bridge require one approval set EACH over the SAME checkpoint. If the witness policy bytes are unchanged, exactly one set serves both; no duplicate set. Ordering is old first, then new when distinct.

`TransparencyBundleV1={0:checkpoint R,1:inclusion_proof R,2:consistency_proof R,3:witness_policies A(R),4:witness_approval_sets A(WitnessApprovalSetV1)}`. Policy/evidence arrays have identical order and length (one normally, one/two on bridge as above). Supplied witness-policy bytes MUST equal the policy[12] bytes, not merely have a valid independent quorum. `transparency_digest=H(transparency,exact bundle bytes)`.

`CertificateBodyV1={0:1,1:trust_domain B,2:policy_epoch u64,3:policy_digest B,4:checkpoint_digest B,5:candidate_event_digest B,6:previous_final_event_digest B,7:control_head_digest B,8:namespace_id B,9:control_sequence u64,10:namespace_sequence u64,11:authority_generation u64,12:coordinator_epoch_id B,13:gateway_epoch_id B,14:event_type,15:1 /*VALID_SUCCESSOR*/,16:transparency_digest B,17:authentication_digest B,18:genesis_digest B}`.

`authentication_digest=SHA256(C(authentication_array))` (raw SHA256, no domain, because the cert field itself and signature domain bind its meaning). Keys1–3=event2–4; key4=H(checkpoint,bundle[0]); key5=H(event,event bytes); keys6–14=event[12], [6], [7], [5], [8], [9], [10], [11], [1] respectively; key16=H(transparency,bundle bytes); key17 as above; key18=pinned genesis digest. Reconstruct all 19 keys and compare exact canonical bytes. Thus checkpoint, inclusion, extension, witness policy, witness signatures, event, request reference and authentication cannot be mixed after approval.

`ApprovalSetV1={0:approval_policy_digest B,1:approvals A([validator_index u64,signature S])}`. Same index/count/order/verify-all rule as witness sets. Ordinary events exactly one set under current policy and `sig/validator`. Bridge exactly two sets, old then new, with policy digests identifying independently ordered key arrays; different arrays never share index interpretation. Both sets approve identical CertificateBodyV1 bytes using their respective old/new signature domains.

`FinalityProofV1` (alias **AuthorityFinalityProofV1**, identical type) `={0:1,1:request R,2:event R,3:transparency_bundle R,4:certificate_body R,5:approval_sets A(ApprovalSetV1),6:authentications A(DetachedAuthenticationV1),7:policies A(R),8:genesis_digest B}`. Policies exactly [current] or [old,new] for bridge; genesis digest=pinned manifest digest=cert[18]. No independent “certificate only” result is authoritative. Exact request bytes travel in the proof; event digest binds their full digest. Exact policy bytes travel in the proof; artifact bytes and witness policy are embedded in each. No detached evidence from a different proof may fill a missing field.

## 6. Single normative validation and replay algorithm

Inputs: out-of-band root/genesis pins; signed genesis; complete ordered FinalityProofV1 history from size1 through retained final size; current candidate proof inputs (approval sets absent while signing); local durable high-water marks, candidate/slot locks and old-policy seals for a live signer. No cached namespace/epoch/policy values are trusted inputs. All parsers implement §§2–5 before using values.

1. Authenticate genesis and initial policy (§3). Initialize size0/empty root/ZERO32 checkpoint and control head, control_sequence0, no coordinator/gateway, empty operation/ID registries, empty append-only namespace allocation index, current policy epoch0.
2. Replay each final proof in consecutive log order. Verify it from this reconstructed prior state using steps3–11. Apply step12 only after all signatures pass. No gap, duplicate slot, alternative root/event, missing bridge, unknown policy, truncated history below a retained anchor, or inconsistent index is recoverable from cache; CLOSED. Recompute roots from full certified event history and cross-check the extension's supplied leaf hashes. A proof in the log without quorum is pending and stops authoritative replay at the preceding prefix; never skip it to a later certificate.
3. Before current-policy checks, parse and check every RequestV1/envelope binding in §4. Operation ID globally unique within this domain: an identical previously final request returns that exact stored prior proof without signing/appending; a different request under the same ID is DUPLICATE_OPERATION_CONFLICT. A supplied completed proof claiming to be that prior result must also be byte-identical to the stored proof, or reject. This lookup may return a historical result after policy/epoch changes, but MUST NOT authorize another send, increment state, or imply currentness. A changed operation ID does not reset release/state/epoch/namespace constraints. A new pending request never overrides an old slot lock.
4. Require event domain/policy=genesis/current policy; exact policy bytes match reconstructed bytes. For bridge, also validate next policy as §7. Reject stale policy, same-epoch reuse or downgrade. Require the next global log slot and checkpoint predecessor; replay position, not wall clock, defines order.
5. Derive current control head and sequence solely from replayed types1–5; enforce event[5]/[6]/[12] and payload predecessor/expected sequence. Derive namespace sequence/generation/state and all outputs/releases solely from the namespace's replayed events, including control allocation/tombstone index. Enforce exact next predecessor/sequence/generation, no overflow, and ZERO32 rules. Types1–5 cannot masquerade as namespace events. No absent namespace is ACTIVE.
6. Derive coordinator from last final type2 and gateway from last final type3/type4, retaining revoked records and historical started-send epoch bindings. Check envelope epochs and required authentication identities/keys; stale coordinator always rejects, stale/revoked gateway rejects starts/escapes. Type2/3 key/ID freshness, predecessor and bootstrap rules apply. Materialization success is not a protocol input.
7. Enforce event transitions in §8, release semantics in §9, withdrawal/accounting in §10, and namespace allocation/tombstone reconstruction in §11. Recompute accounted outputs from history, never supplied counts/caches. A legal signature cannot make an illegal transition valid.
8. Verify all required coordinator/root/gateway signatures on the exact request/event pair, with exact role/epoch/key tuple list. Reject extra/missing/duplicate authentication, wrong role domain/key/object. Application authorization intent is explicit in type17's signed request/authorization ID; this protocol cannot establish that a compromised authorized signer acted for a real human.
9. Verify log signature, exact domain/log/origin/policy/previous checkpoint; inclusion and complete-prefix consistency; applicable old/new witness policies and signatures; every equality in §5. All evidence must refer to this checkpoint and event. A witness proof over a different checkpoint or proof prefix rejects even if cryptographically valid.
10. Reconstruct exact CertificateBodyV1. Its digest is H(cert,body bytes). Require equality with supplied body; do not copy supplied opaque body fields into the expected body.
11. A live validator, serialized against its own concurrent requests, checks BOTH per-event predecessor lock `(trust_domain,namespace_id,previous_event_digest)->event_digest` and global next-slot lock `(log_id,previous_checkpoint_digest)->cert_digest`, plus its high-water marks and policy seals. No conflicting lock, past slot, unfinalized earlier slot or stale policy is healthy participation. In one durable transaction persist the candidate, exact request/event/bundle/body/auth bytes, both locks, high-water marks and any bridge seal BEFORE signing/releasing approval. Crash dispositions §12 apply. To verify a completed proof, require exact approval-set schema and 3 valid signatures under the current policy (both policies on bridge), with all same-body/domain checks. Invalid extra signatures reject the whole proof.
12. Only then classify WITNESSED_FINAL / VALID_SUCCESSOR, advance global checkpoint and event prefix, update control head or namespace head/generation, allocation/tombstones, operation registry, epochs, releases and terminal state; activate a bridge at this slot. Persist the final proof and high-water mark before returning success or permitting sensitive use. Return the exact prior result for a byte-identical already-final operation; never generate a second event for it. Any failure returns a specific rejection if known, otherwise CLOSED_PENDING_RECONCILIATION, with no authority/output/state mutation or signature release. An invalid fixture's presence is not authority.

Live currentness: all authority-sensitive operations, including EACH SEND_STARTED, must obtain a fresh complete next-slot certificate. A stale consumer cannot act merely because an old proof is valid. A restarting signer recovers a complete supplied prefix using the exact FinalityProofV1 objects and its retained durable anchor/locks, then requests a fresh unpredictable continuity challenge through the SAME next-slot certification procedure. There is no separate trusted frontier/status-message format. A signer with a later durable final/pending slot refuses the shorter proposal; missing objects must be recovered, never assumed absent. The challenge binds the recovered checkpoint/control sequence and does not erase a pending lock. Missing state/quorum/independence => CLOSED. Fresh nonce proves this round's participation, not trusted wall time or that a malicious signer revealed all state. Safety rests on quorum intersection and durable locks, not a claim of perfect rollback detection.

## 7. Policy bridge

At an old-policy control predecessor, payload old digest/epoch equal current; new epoch=old+1; new digest recomputed from exact new policy bytes; activation sequence=current control_sequence+1=event[5]. Current old coordinator and all ordinary control checks apply. Policy arrays are [exact old bytes,exact new bytes]. Preserve trust domain/log ID/origin/log key/genesis/algorithm IDs/quorums/profile. Roots and validator/witness keys may change only through this bridge, with role separation/key freshness for replacements; same role retained keys are allowed. New initial policy cannot be selected independently.

Checkpoint and certificate header use OLD epoch/digest; certificate event digest binds BOTH payload policy hashes/epochs and activation sequence, and transparency_digest binds both applicable witness-policy evidences. Old/new authority sets sign the IDENTICAL full certificate body under `sig/bridge-old`/`sig/bridge-new`. Both 3-of-4 sets mandatory even when validator keys are unchanged. Each set index is interpreted under its own policy. Both witness-policy approvals are mandatory when policies differ; same policy bytes need only one witness set.

Before old bridge approval release, an old validator durably seals old policy from signing any other candidate at this slot or any later slot. Pending bridge cannot be abandoned. Finality activates new policy for the NEXT slot; the bridge itself belongs to old policy. Replays return the prior proof only; no same-epoch reuse, reverse bridge, new-only bootstrap or log-origin change. Old quorum loss or an unrecoverable split means permanent unavailability; root credentials are not an override. Epoch materialization failure does not undo activation.

## 8. State, coordinator and gateway transitions

Namespace state: 0 UNISSUED ->16->1 ACTIVE ->22->2 WITHDRAWAL_FENCED ->23->3 WITHDRAWN ->24->4 DELETED. Types17/18 require ACTIVE; types19–21 require an existing nonterminal final SEND_STARTED and allow ACTIVE or WITHDRAWAL_FENCED only. Other transitions reject. No terminal event after WITHDRAWN/DELETED is needed/allowed because accounting already required every started send terminal. Type23 withdrawal_id must equal the namespace's final fence withdrawal_id. Type24 allocates a permanent tombstone. Reasons do not change permissions.

Type2: root-authenticated fresh epoch/key, old coordinator authentication if one exists, new possession authentication; updates only after finality. Crash after certificate before local materialization: the SAME certified new epoch remains authoritative; replay/recover its materialization only. Do not mint a competing epoch or treat the old credential as current. Losing current/root authentication makes rotation unavailable under v1.

Type3: current coordinator plus gateway-root plus fresh gateway possession proof; replacing a live gateway atomically marks its old epoch revoked for new escape/start. Type4 references the current nonrevoked gateway, marks it revoked; repeated revoke with a new operation rejects. Revoked key/ID remains in history forever. Envelope[11] for type3/4 is ZERO32, payload[0] selects the epoch. Type17/18 bind the actual current gateway epoch. A rotation/revoke after a prior final start cannot erase consumption or exposure. Types19–21 classify that start using its OLD gateway epoch and send ID, authenticated by the current coordinator; no revoked gateway signature is required or sufficient. Stale processes cannot create final starts after rotation/fence because validators reconstruct the next-slot current state.

## 9. Explicit one-shot release and exposure

Type17 is the **explicit release authorization event** as well as ESCAPE_COMMITTED, not an automatic reservation/retry. Its request is a fresh deliberate user/application authorization with fresh explicit_authorization_id and release_id, authenticated by the current coordinator. Both IDs must be globally unused in this domain. Release uniqueness is `(namespace_id,output_id,release_id)` and release_id itself is globally fresh; operations cannot create aliases. Reusing an output_id for a new release requires identical content_root,length,recipient_ref,purpose and is a NEW exposure with NEW explicit authorization, never recovery of the prior send. A caller must not mint a new release/authorization on reconnect/restart/retry; require a separate explicit application/user decision. Compromise of that decision-maker is outside the consent claim.

Type18 requires the matching final type17, same current gateway epoch as that escape, unused tuple and a fresh globally unique send_instance_id. Exactly one final SEND_STARTED consumes it, including before first byte. Each start requires current next-slot finality, not a reusable signed permit. No bytes may leave before BOTH escape and start are final. Escape already conservatively counts the whole content as authorized exposure; started sends count full content even if zero/partial bytes were emitted. Gateway rotation between escape and start closes that release permanently without sending; no transplant to a new epoch.

Types19–21 require the exact output/release/send ID and envelope gateway epoch of final start, exactly one terminal event. Crash/lost completion/disconnect/uncertainty after start -> SEND_UNKNOWN; consumed forever. A changed operation ID never bypasses that state. Type19 completion_digest attests classification evidence only, not recipient erasure. Historical process-binding/send_instance fields on type17 and process-binding on type18 are superseded and rejected as unknown keys; the detached gateway signature proves key use, not process identity.

## 10. Withdrawal serialization, accounting and race table

Finality order is log slot order. A final fence makes any subsequent new type17/type18 or release authorization illegal. The global slot lock prevents later certification against an older control/namespace view. Pending starts/escapes before fence must either finalize first or block the fence slot; there is no assumption that an unobserved pending approval can be safely discarded.

| Schedule | Frozen disposition |
|---|---|
| Fence final before escape | reject escape/new authorization; no release |
| Escape final, then fence before start | reject start; escape remains exposure ledger entry, unstarted/closed |
| Fence races start | whichever is the unique next certified slot first determines result; if start wins, full exposure; if fence wins, start rejects; pending split closes progress |
| Fence after start | full bound content exposure, no resend; allow one terminal classification |
| Live stream at fence finality | gateway MUST stop enqueueing sensitive bytes when it observes a valid fence; already queued/OS/network/recipient bytes cannot be recalled |
| Gateway learns fence late | stop when learned; already-final start remains full exposure; no claim of instantaneous transport quiescence |
| Terminal after fence | types19–21 allowed only for pre-fence final starts, exact old epoch/send binding |
| SEND_UNKNOWN after fence | terminal consumed/full exposure; no new release authorized by retry |
| Stale gateway after fence | cannot get next-slot SEND_STARTED finality; old proof/credential insufficient |
| Missing terminal/accounting evidence | remain WITHDRAWAL_FENCED/CLOSED, never WITHDRAWN |

For every pre-fence ESCAPE_COMMITTED, reconstruct one accounting entry `[output_id,release_id,escape_event_digest,terminal_event_digest]`. For never-started escapes only, terminal_event_digest=ZERO32; the fence closes them permanently. For started escapes it MUST equal the digest of the one certified terminal type19/20/21 event, including terminals after fence. No zero terminal for a started send. Sort by raw output_id||release_id, reject duplicate pairs, count=len(entries), `accounted_output_digest=H(accounted-outputs,C(entries))`. This preserves the accepted digest construction and specifies the previously missing unstarted case. No event after fence adds an escape. Type23 accepted only if the complete replay-derived list and every started terminal match, withdrawal ID matches, count/digest match. Type24 only after type23. This makes incomplete accounting non-WITHDRAWN; it does not promise data erasure from transports/recipients.

## 11. Namespace allocation, tombstones and cache reconstruction

The append-only certified global prefix is the allocation index. At type16, insert namespace_id into a permanent allocated-ID set only if absent from ALL prior certified type16 events; initialize its state/head/sequence/generation. The type16 proof contains current control head and its unique global slot, so allocation is authenticated by control history as well as namespace history. Never remove the allocation entry, even after withdrawal/deletion. Type24 appends a tombstone `(namespace_id,tombstone_id,delete_event_digest)` and sets terminal state, never deletes allocation metadata. Grant/tombstone/withdrawal IDs are also globally unique within their categories.

Reinstall/cache rollback reconstructs policy/control/epochs, allocation/tombstone index, namespace heads and releases by replay from pinned genesis through the complete retained certified prefix; namespace-only snapshots cannot prove absence of a previous allocation. Caches contain derived values only. A missing allocation, bridge, event, terminal, checkpoint or witness proof; unexpected generation/sequence; fork; high-water regression; or unavailable quorum/history => CLOSED, no issue/send. No reuse after delete, no reconstruction from a plaintext cache assertion of ACTIVE. IDs of deleted namespaces remain public opaque metadata; actual private payload custody/erasure is outside these fixtures.

## 12. Durable candidate lock, crash and lifecycle

Locks have no TTL. A live validator's durability assumption is independent, nonrollback persistence of exact proposal bytes, both §6 locks, high-water marks and bridge seals, atomically before any approval release. Failure to obtain an affirmative durable commit => no signature. The protocol specifies an obligation, not a verified disk/hardware implementation.

| Crash/failure point | Required behavior |
|---|---|
| Before durable lock commit | no approval released; retry allowed against reconstructed state |
| Durable lock, response lost | return/continue ONLY identical candidate/body/checkpoint/auth bytes |
| Approval released, then crash | same locks survive; reproduce only same deterministic approval/result |
| Same event, different checkpoint/evidence/body | global slot/body lock rejects; cannot substitute new evidence |
| Pending candidate without quorum | nonauthoritative, CLOSED_PENDING_RECONCILIATION; gather identical missing evidence only |
| 2-vs-2 candidate split | never unlock, never change candidate; absent identical-candidate quorum, permanent domain/namespace unavailability |
| Known restored/equivocating validator | quarantine as Byzantine; its old key cannot silently count as healthy; recovery requires a new key and valid bridge |
| Irrecoverable durable history/old quorum loss | permanent unavailability, no admin/root unlock or quorum reduction |

No manual unlock/adjudication override exists in v1. Reconciliation means recover identical durable records/proofs, not erase locks. A lost/rolled-back instance may be unable to detect its own rollback; any such signer consumes the at-most-one Byzantine budget until replaced. Equivocation evidence is two valid signatures for conflicting same-slot/predecessor candidates, retained with both objects. Detection is not guaranteed; no retrospective safety claim when two compromised signers existed.

Witness and validator lifecycle EACH requires at most one Byzantine/reset/rolled-back/compromised member at a time. One known malicious member is excluded from healthy quorum; with one other unavailable, only two healthy responsive remain: CLOSED. A concealed malicious member may cooperate but cannot be relied upon for availability; withholding produces the same CLOSED result. Two unavailable => CLOSED; two compromised => assumption violated, no security claim. Lost durable state/reset requires old key retirement, a fresh replacement key and valid dual policy bridge; silent rejoin forbidden. Replacement still needs old quorum; if lost, permanent unavailability. Old/new witness proofs and old/new validator approvals remain mandatory during replacement. Keys on a common host, under common administrators, restore snapshots, credentials or failure domain do not meet independence. These operational facts must be independently established before any deployment; fixture success is not evidence for them.

## 13. Review finding closure map and retained evidence blockers

| v7 review blocking design area | Normative closure | Required fixture classes |
|---|---|---|
|1 Transparency conjunction|§5 exact bundle/body/finality bindings|wrong checkpoint, inclusion, extension, witness policy/signature|
|2 Deterministic validator|§§4,6,8–11|all14 events, request digest/equality, stale heads/epochs|
|3 Candidate lock|§§6,12|conflicting slot/body; durable/lost-response/split schedules|
|4 State machine|§§8,10,11|terminal/fence/accounting/delete/reuse schedules|
|5 Genesis/policy|§§2,3|signed genesis, epoch0/next policies, artifact mismatch|
|6 Bridge|§7|both full approval sets, wrong domain/body/epoch|
|7 Lifecycle|§12|unavailability dispositions; explicit unqualified operational premise|
|8 Coordinator|§§4,8|start, stale epoch, same certified epoch after materialization failure|
|9 Gateway|§§4,8|start/revoke, stale start, terminal after revoke|
|10 One-shot|§9|duplicate start, unknown-resend, operation-ID bypass|
|11 Withdrawal|§10|race ordering, terminal after fence, incomplete accounting|
|13 Schemas|§§2–5|duplicate/nonminimal/unknown/wrong length/ZERO32/prohibited CBOR|
|14 Vectors|generator/catalogue/JSON|complete bytes, actual cryptographic verification and rejection results|

Review §12 accepted accounted-output digest is retained. Review §15 source/static acceptability is not changed. Review §§16–17 binary independent hash/PE inspection and target/runtime qualification remain EVIDENCE BLOCKED; the earlier reviewer explicitly did not independently inspect binary bytes. Existing static build/artifact IDs/hashes are reference evidence only. Do not rebuild, modify or execute the collector. No target access, TPM/TBS, key provisioning, private data, deployment, Gate2 or P2-B. Even complete design vectors do not close operational/binary evidence gates.

## 14. Validation and current completion record

The generator is fixture-only, using deterministic PUBLIC test seeds and the installed Node crypto implementation from Python. No operational keys, collector, services, target interfaces or live data are accessed. It verifies generated signatures with the crypto library, checks a published Ed25519 known-answer vector, parses canonical bytes, replays valid schedules, and actually rejects mutated schemas/proofs/transitions. It is not a production verifier or an independent security review. Full-object bytes and explicit starting-history references live in JSON; the Markdown catalogue records counts and expected outcomes. Regeneration must be byte-identical. The research unittest entry invokes the self-check and artifact drift check, so exact-head research CI covers these fixtures without changing a workflow.

Completion evidence and any uncovered requirement must be recorded here and in both current handoffs after validation. The strict all-blockers rule requires frozen design/vector corrections and exact-head research PASS, and retains the independent review's unresolved binary-inspection finding. Thus overall READY is not assigned in this pass; retain CORRECTIONS_INCOMPLETE. Target/operational gates also remain separately blocked. No next independent review is performed in this correction task.

The at-most-one Byzantine assumption is a conservative joint admission limit across the validator/witness populations as well as a per-set limit. This pass makes no security claim if two members are compromised/reset/rolled-back simultaneously, even across different roles. The protocol cannot itself prove that an undetected compromise or reset has not occurred.

### Bounded correction result

- Normative rules now address review design areas 1–14 with the exact schemas, domains, validation order, dual bridge, epoch/release/fence/accounting/reconstruction rules above. These are correction-author self-checks, not an independent closure verdict.
- Generated set: **363 object records; 34 accepted full-finality scenarios covering all 14 event types; 51 actual rejection cases; 1 byte-identical prior-final replay; 8 executable synthetic lock/lifecycle schedules**. RFC8032 known-answer sign/verify/wrong-message checks PASS. Full catalogue replay from named histories and no-mutation-on-rejection checks PASS.
- Deterministic generator and separate `--check` process PASS. Catalogue wrapper hashes use canonical Git LF bytes; Windows checkout newline conversion is normalized for wrapper drift only. Every embedded protocol hex byte string remains exact.
- Local Python3.12 full research unittest discovery: **440 run, 439 passed, 1 existing Windows symlink skip**, no expected failures. Synthetic registry/contract runner: **58 PASS, 0 FAIL**; real-engine results remain **58 BLOCKED**, not real-engine validation. An initial Python3.15 run had 2 failures/1 error in existing tests whose command explicitly requires Python3.12; rerunning with the specified interpreter resolved them without unrelated edits.
- Baseline exact-head research run [42374535784](https://github.com/Isshhin/stridelab-development/actions/runs/42374535784) was independently fetched as SUCCESS for starting SHA `37a58e927601522200c31aa08c6b7fd21902a1d6`. Correction exact-head run is recorded in the delivery report after publication, never inferred from this baseline. Graphify's queried index was at `49824dc` (2026-11-05), stale/unhelpful for these artifacts; live files were used. The separate mirror's billing startup failure is infrastructure evidence, not a protocol failure or a CI pass.
- Collector source, workflow and frozen packet unchanged. No rebuild or binary download/inspection was done in this correction pass. **Collector NEVER EXECUTED**. Static binary reference remains run42373365386 / artifact13573973275 / SHA256 `96cd9069205a891c383f23d085e1c10c0f678f4d132b9c25cb7185902af3f8dc`; prior independent reviewer did not inspect bytes. No independent binary PASS is claimed.
- Remaining blockers: independent review of these new protocol/vector corrections; independent static inspection of the frozen binary bytes before any separate preflight authorization review; all production pins, custody/durability/independence and target/runtime qualification remain unestablished and outside this task. No target access/qualification, TPM/TBS, operational key provisioning, private data or deployment occurred.
- Final v7 status remains **`P2F_CUSTODY_AUTHORITY_V7_CORRECTIONS_INCOMPLETE`** under the all-blockers rule. PR34 remains OPEN/DRAFT/UNMERGED; Gate2/P2-B/private admission unchanged.
- Exact next permitted task: **a new, separately scoped independent v7 security/design review of this correction supplement and complete fixtures, including independent static hash/PE inspection of the existing frozen artifact if accessible.** Do not rebuild or execute it; no target access, implementation/deployment, new keys, private data, merge or ready transition. This review is NOT performed here.
