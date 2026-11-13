# Independent review of corrected P2-F custody/authority v7

Date: 2026-11-07 (Asia/Manila). Review only. Reviewed exact head: `2104f1ff2ba8be79a54c1700c65f10e2152dfc72`, branch `research/runna-garmin-coaching-20261102`, repository `Isshhin/stridelab-development`, PR #34.

**Result: `P2F_CUSTODY_AUTHORITY_V7_DESIGN_NEEDS_CORRECTION`.** Independent static binary-byte inspection is now accepted. The corrected written protocol resolves the previous review's missing definitions, but the fixture conformance oracle accepts two concrete violations of the mandatory all-history role-key separation rule. The protocol-plus-vector closure therefore does not pass the requested all-blockers gate.

This is a new review pass of the exact corrected head, not adoption of the author's self-check. It used fresh artifact bytes, direct source inspection, independent hash/PE parsing, full static disassembly, separate byte/signature checks and new adversarial proposals. It is not a separately staffed human audit or a claim of independent organizational custody. No design, implementation, fixture, collector, workflow or frozen packet was changed to obtain a pass. The prior independent review remains immutable.

Collector **NEVER EXECUTED** in this review; the supplied history and inspected build workflow/log also record no collector execution. Static reading cannot prove an absolute negative about every outside actor. No collector invocation, emulation, debugging or loading as executable code occurred. No target access/qualification, system-information query against a target, TPM/TBS operation, operational key provisioning, private/participant/provider data, infrastructure deployment, Engine 1.5, Trackside, APK/release, Session G, P2-B or Gate 2 PASS occurred or is authorized.

## Scope, evidence and precedence

Reviewed the [sole normative correction supplement](p2-f-custody-authority-v7-correction-closure-2026-11-07.md), its [catalogue](p2-f-custody-authority-v7-closure-test-vectors-2026-11-07.md) and adjacent JSON, `research/tools/p2f_v7_closure_vectors.py`, `research/validation/test_p2f_v7_vectors.py`, both current Engine 2.x handoffs, the [prior independent v7 review](p2-f-custody-authority-v7-independent-security-review-2026-11-07.md), superseded canonical/closure records, the frozen preflight packet/source/build workflow and remote build evidence.

The supplement §1 is explicit: it alone defines current protocol/schema/validation; catalogue/JSON are subordinate normative expected bytes/results; disagreement must close and be corrected. Earlier canonical, closure and v1-v6 proposals are superseded for conformance. Independent reviews are evidence, not protocol. The checked earlier canonical and closure documents prominently redirect to this authority. The first current handoff entry supersedes historical current/next-action wording. Historical imperative language does not override these explicit rules. No conflicting current source of normative authority was found.

The connected repository index returned no `p2f_v7` matches; no graph result was used as evidence for this head. Exact-head Git files controlled the review. Public C2SP [tlog-policy](https://c2sp.org/tlog-policy) and [signed-note](https://c2sp.org/signed-note@v1.1.0) were checked for the restricted template/vkey projection. P2-F explicitly disclaims signed-note transport interoperability; its raw application signatures must not be verified as C2SP notes. Live external prose was contextual only; embedded artifact bytes and the local frozen rules control conformance.

## Blocking finding F1: fixture verifier forgets role-key history

**Priority P1 for this closure gate; fixture-only defect, no deployed exploit claimed.** The written protocol requires role separation across complete history (§§2,4,7), including operational epoch keys. The executable oracle does not implement that requirement.

- `policy_check` (generator lines 257-269) compares only keys inside the proposed policy plus immutable genesis/log keys. It never compares proposed policy roles with current or historical coordinator/gateway epoch keys.
- `transition` (lines 350-356) compares a new epoch key with current policy roles and the `epochkeys` set. That set retains coordinator/gateway keys but not retired policy validator/witness/root roles.
- Bridge handling in `verify` (lines 437-442, 472-473) checks the new policy in isolation, then replaces the current policy. Replay has no complete historical role-key map. The `initial` state has no such registry.

Two fully signed counterexamples were independently constructed with published invented fixture seeds. Both pass the unchanged `verify`, including log, witnesses, request binding, old/new authority approvals and state transition checks:

| Counterexample | Construction and observed wrong result | Exact proof SHA-256 |
|---|---|---|
| F1a active cross-role collision | Replay `policy-bridge` scenario history; set next policy key7 (gateway root) to the **current coordinator key**, recompute its policy digest and bridge payload, sign the same body with both quorums. `verify` accepts; resulting gateway-root key equals the still-current coordinator key. | `5cda3294b4c2888e6d4c39e3a2e2fa33d0ec0e96ba7caabbe5d5e1ba2777ae87` |
| F1b retired cross-role collision | Replay the legitimate bridge, then propose type3 with a fresh epoch ID but public key equal to `old_policy[4][0]`, a retired validator. Required coordinator/root/possession authentication is valid. `verify` accepts and installs that retired validator key as gateway key. | `227c845f4f95e23e3a3b2ea195a21ca55e0753b71904c56a47d8752b7a83f059` |

These violate already-frozen rules; they do not require changing the design or assuming a broken signature. They show why existing self-check/CI success cannot establish complete v7 conformance. Signature domains prevent message substitution, but do not enforce the separate custody/key-role separation requirement. The new proposals were not added to normative fixtures in this review.

**Required bounded correction:** reconstruct a permanent raw-key-to-role history from genesis, every policy bridge and every operational epoch start. Check each proposed policy/epoch against it before acceptance; permit only the expressly allowed same-role retention, reject cross-role reuse, and preserve history after retirement/revoke. Add actual signed rejection fixtures for both cases and recheck deterministic regeneration, catalogue replay and all existing tests. Do not weaken the normative separation rule to fit the oracle. Obtain another independent review afterward.

Minimal reproduction using unchanged helpers (run with Python 3.12 in an isolated exact-head checkout; synthetic fixtures only):

```python
import importlib.util
from pathlib import Path

path = Path('research/tools/p2f_v7_closure_vectors.py')
spec = importlib.util.spec_from_file_location('v', path)
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)
with v.Crypto() as c:
    data = v.suite(c)  # Registers PUBLIC deterministic test seeds only.
    obj = {x['name']: bytes.fromhex(x['hex']) for x in data['objects']}
    state = v.initial(obj['genesis'], bytes.fromhex(data['genesis_pin_hex']),
                      bytes.fromhex(data['root_pin_hex']), c)
    case = next(x for x in data['scenarios'] if x['name'] == 'policy-bridge')
    for name in case['history']:
        state = v.verify(obj[name], state, c)
    old = v.decode(state['policy'])
    new = v.decode(obj['new-policy'])
    new[7] = state['coordinator'][1]
    newraw = v.encode(new)
    payload = {0: v.h('policy', state['policy']), 1: v.h('policy', newraw),
               2: old[2], 3: new[2], 4: state['cs'] + 1}
    raw = v.make(state, 5, payload, 'independent/cross-role-bridge', c,
                 newpolicy=newraw)
    after = v.verify(raw, state, c)  # WRONG: must reject cross-role key reuse.
    assert v.decode(after['policy'])[7] == after['coordinator'][1]
    bridged = v.verify(obj['policy-bridge'], state, c)
    raw = v.make(bridged, 3,
                 {0: v.ident('independent/epoch'), 1: bridged['control'],
                  2: old[4][0]},
                 'independent/retired-validator-to-gateway', c)
    after = v.verify(raw, bridged, c)  # WRONG: must reject retired-role reuse.
    assert after['gateway'][1] == old[4][0]
```

The assertions deliberately confirm the observed incorrect acceptance. After correction these proposals must reject; do not preserve the assertions as positive conformance tests.

## Protocol review and previous finding dispositions

The verdicts in this section assess the **written normative model**, conditional on honest participants implementing ALL its requirements, including the role-history rule missed by F1. They are not a pass for the fixture oracle, production implementation, deployed independence or availability. F1 blocks vector acceptance and the combined closure even though the normative rule itself is unambiguous.

| Area | Review conclusion and attempted falsification | Verdict |
|---|---|---|
| Normative precedence | Sole supplement, subordinate bytes, explicit fail-closed conflict rule, historical demotion and immutable reviews resolve earlier competing definitions. | `NORMATIVE_PRECEDENCE_ACCEPTABLE` |
| Transparency + authority | §5 fixes all checkpoint/inclusion/complete-prefix/witness/bundle/certificate/finality schemas. Reconstructing all 19 certificate fields binds the exact event, request reference, checkpoint, evidence, policy, genesis and authentication. Mixing X/Y checkpoint, unrelated prefix, old witness policy, stale proof or legal signatures over illegal transition rejects. A witnessed log alone is not authority. | `TRANSPARENCY_AUTHORITY_SEPARATION_ACCEPTABLE` |
| Signatures | Raw32/raw64, pure Ed25519, exact NUL-separated role domains and exact messages; canonical/prime-subgroup key and R admission, S<L and uncofactored equation mandatory. No wrong-domain fallback. Old/new bridge quorums sign identical body bytes in distinct domains. F1 is role admission, not signature forgery. | `SIGNATURE_BINDING_MODEL_ACCEPTABLE` |
| Deterministic authority validator | §§4-6 specify complete replay from pinned genesis, schema/request equality, prior-result lookup, policy/heads/predecessor/sequence/generation, epochs, legal transitions, authentication, transparency, reconstructed certificate, durable locks, approvals, then commit. Wrong request, stale head/epoch, rollback, malformed CBOR or conflicting operation reuse cannot mutate/sign. Identical finalized retry returns historical proof only. Normative role-history requirement is mandatory despite F1 in oracle. | `AUTHORITY_VALIDATION_MODEL_ACCEPTABLE` |
| Global slot | One event per final checkpoint; predecessor checkpoint to certificate digest lock; no pending-slot skip. Control/namespace, start/fence, bridge/ordinary proposals compete for the same next slot. Different body/evidence cannot evade the lock. Stalled slot may halt the whole domain forever. | `GLOBAL_SLOT_SERIALIZATION_ACCEPTABLE` |
| Candidate/crash locks | Both predecessor and global locks persist atomically before approval, no TTL/unlock; response loss and post-approval crash permit identical bytes only. Split cannot create an alternative successor. This is a persistence obligation, not measured storage behavior. | `UNIQUE_SUCCESSOR_MODEL_ACCEPTABLE` |
| Genesis/policy | Signed exact manifest plus independently pinned root AND digest; epoch0; explicit algorithm/quorum fields, validator-set digest, exact embedded ASCII/LF artifact and witness/log/origin equality. Missing pins/artifact, semantic rewrite, downgrade or self-signed substitute closes. | `BOOTSTRAP_POLICY_MODEL_ACCEPTABLE` |
| Bridge | Old policy owns slot/header; new epoch=old+1; exact [old,new] policy bytes, both 3-of-4 authority sets over one body, applicable old/new witness sets, preserved log/origin, old durable seal and next-slot activation. New-only bridge or lost old quorum cannot recover availability. Normative all-history role checks remain required; F1 disproves oracle conformance. | `POLICY_BRIDGE_MODEL_ACCEPTABLE` |
| Lifecycle | §12 explicitly closes with one malicious plus one unavailable or two unavailable; two compromised breaks premise. Lost state retires old key, requires fresh key/valid bridge; no silent rejoin/admin unlock. Joint at-most-one compromise limit is explicit. Shared host/admin/credential/restore domains invalidate independence. No unsupported rollback-detection guarantee remains. | `WITNESS_VALIDATOR_LIFECYCLE_ACCEPTABLE` |
| Coordinator epochs | Actual raw key, root/current/new possession proofs and fresh shared epoch IDs; old coordinator required on later starts. Failed materialization recovers same final epoch, never a competing epoch. Missing root/current authority closes. | `COORDINATOR_EPOCH_MODEL_ACCEPTABLE` |
| Gateway epochs | Root/new possession plus coordinator authentication; fresh keys/IDs, replacement/revoke blocks new starts; old final starts retain historical epoch for one coordinator-authenticated terminal. Stale process cannot certify new SEND_STARTED. F1 shows the oracle omits historical key-role rejection. | `GATEWAY_EPOCH_MODEL_ACCEPTABLE` |
| One-shot release | Type17 explicitly authorizes fresh global release/authorization IDs; type18 requires matching escape/current gateway and consumes once before any byte. Unknown/crash/reconnect/new operation cannot restore release. New release for same descriptor requires NEW explicit exposure decision; rotation before start permanently closes old release. | `ONE_SHOT_OUTPUT_MODEL_ACCEPTABLE` |
| Withdrawal | §10 fixes fence/escape/start races in global slot order, pending-start blocking, live-stream stop on observing fence, late learning and one terminal after fence. Counts full committed content; no instant quiescence, packet recall or recipient erasure claim. | `WITHDRAWAL_OUTPUT_DESIGN_ACCEPTABLE` |
| Accounting | Complete replay-derived four-element entries, ZERO32 terminal only for never-started escapes, exact terminal for started sends, raw output||release ordering, duplicate rejection, exact count/domain digest. Missing terminal stays fenced, never withdrawn. | `WITHDRAWAL_ACCOUNTING_DIGEST_ACCEPTABLE` |
| Namespace state/cache | Permanent allocated-ID set from all type16 history, no reuse after delete, permanent tombstone, full-prefix reconstruction; gaps/forks/high-water regression close. No cache can establish ACTIVE or nonallocation. | `AUTHORITY_STATE_MACHINE_ACCEPTABLE` |
| Canonical schemas | §§2-5 define manifest/policy/request/envelope/all14 payloads/checkpoint/inclusion/extension/witness/bundle/body/approvals/finality/authentication and bridge interpretation. Integer keys, raw lengths, ZERO32 exceptions, closed enums, no unknown/default fields, full-object hashes and recursive canonical CBOR are specified. | `CANONICAL_AUTHORITY_PROTOCOL_V1_ACCEPTABLE` |
| Vectors | Existing byte/signature/rejection checks reproduce, but F1a/F1b are accepted despite normative rejection. Completeness/conformance is not established by the existing positive catalogue or CI. | `CANONICAL_TEST_VECTORS_NEED_CORRECTION` |

Top-level one-shot output/withdrawal written model: `ONE_SHOT_OUTPUT_WITHDRAWAL_MODEL_ACCEPTABLE`. This remains conditional on a sole honest gateway and compliant authority signers. Compromised authorized applications/gateway/kernel can act outside the protocol; human consent and transport behavior are not inferred from signatures.

The complete-prefix extension is explicitly O(n) per append; retaining/replaying complete history and repeated extension evidence grows work/storage. This is an admitted availability/scale cost, not evidence of a security pass under history truncation or permission to deploy a compact proof.

## Vector and cryptographic evidence actually checked

Python 3.12 generator `--check` reproduced **363 objects, 34 accepted full finalities covering all14 events, 51 actual rejections, 1 exact replay, 8 synthetic lock/lifecycle schedules**. Four `research.validation.test_p2f_v7_vectors` tests passed, including catalogue replay from named histories and no prior-state mutation on rejection. No tracked fixture regenerated/changed.

A separate review-only decoder/encoder, not importing generator code, recomputed all 363 raw SHA-256 values, every recorded domain digest and the canonical encodings of CBOR object records (excluding intentionally malformed records and raw C2SP text). No mismatch was found. Separate message construction plus local OpenSSL 3.5.7 `pkeyutl -verify -rawin` independently verified **51 Ed25519 signatures**, each also rejecting a changed message. Samples: genesis, coordinator-start, start-a, withdrawal-accounted, policy-bridge and coordinator-rotation; they include checkpoint, witness, ordinary validator, bridge-old/new, coordinator/root and gateway signatures. This used the OpenSSL executable separately from the generator's Node interface, not a claim of independent crypto-library implementation families.

Representative full-body/request/event/checkpoint/transparency/authentication/genesis bindings were recomputed in that separate check. Bridge old/new sets were verified on the same exact body. Existing actual negatives were replayed for malformed CBOR, wrong request/head/policy/epoch, checkpoint/inclusion/consistency/witness mismatch, wrong signature domain, duplicate start, unknown-resend, post-fence escape/start and namespace reuse. Strict subgroup admission was inspected in `prime_point`; the independent signature samples do not exhaust all malicious curve encodings. Eight lifecycle records are bounded in-memory obligation checks, not disk crash, concurrent multi-process, key custody or operational independence evidence.

F1 was found by going beyond the enumerated fixtures. The two newly generated signed proofs had valid cryptography and were accepted by the unchanged oracle; accepting their hashes/signatures does not make their transitions conforming.

## Independent frozen binary-byte inspection

**Verdict: `READONLY_PREFLIGHT_BINARY_PACKET_ACCEPTABLE`.** Downloaded existing artifact ID **13573973275**, `p2f-preflight-static-artifact`, associated by GitHub metadata with build run **42373365386**, source head `449e87743c96b87caf6d78c1ea8df54a9ac2a30b`. The connector successfully exposed the artifact; authenticated GitHub CLI downloaded the same artifact directly for local static byte inspection. No rebuild was performed.

Exact ZIP contents, no additional entries:

| Archive entry | Uncompressed bytes |
|---|---:|
| `stridelab_p2f_preflight.exe` | 3072 |
| `docs/research/phase2/preflight/stridelab_p2f_preflight.c` | 1821 |

Independent ZIP SHA-256: `710b58988d8d12294def3bcb97c7fa41b0dd97e945ceac607d776962e42f0915`, matching GitHub artifact metadata.

Independent binary SHA-256: **`96cd9069205a891c383f23d085e1c10c0f678f4d132b9c25cb7185902af3f8dc`**, **3072 bytes**.

Artifact source SHA-256 (CRLF): `478c2a1b7049b6a5d30d527b8d36623064346560e561f45d7852a6fc891157a6`. Converting CRLF to LF gives exact reviewed Git source bytes/hash `540674b092c0a6960400f6435facbc92806652d003f48625d68bcdb8cdf49bef`. No other source normalization was applied.

Both an independent read-only PE parser and MSVC `dumpbin /headers /imports /disasm` inspected the bytes:

- PE32+, machine **0x8664 x64**, entry RVA **0x1060**, preferred image base **0x140000000**. Entry is directly the main function, consistent with `/ENTRY:mainCRTStartup /NODEFAULTLIB`; decimal helper at RVA0x1000, main at0x1060. No CRT startup path/helper imports.
- DLL characteristics **0x8160**: DYNAMICBASE, NXCOMPAT, high-entropy VA and terminal-server-aware flags. Three sections: `.text` (RVA0x1000, virtual size0x1fe), `.rdata` (RVA0x2000), `.pdata` (RVA0x3000). No TLS, delay-import, CLR or certificate directory; unsigned. Base relocation directory is empty. Header flags are observed static properties, not demonstrated runtime relocation/mitigation behavior.
- Exactly one static DLL, **KERNEL32.dll**, and exactly these imports in table order: **ExitProcess, GetNativeSystemInfo, GetModuleHandleW, GetProcAddress, WriteConsoleA, GetStdHandle**. No ordinal imports, CRT/runtime DLLs, filesystem/registry/network/process-spawn/elevation APIs or unexpected DLLs.
- `RtlGetVersion` is an embedded symbol string, **absent from static imports**. Wide `ntdll.dll` literal is present. The disassembly at0x1081 calls GetModuleHandleW, at0x109a calls GetProcAddress with that exact symbol, and at0x10b4 invokes the resulting function pointer with a 0x114-byte OS-version structure. No other dynamic symbol lookup was found.
- At0x10c2 calls GetNativeSystemInfo; at0x10cd retrieves STD_OUTPUT_HANDLE (-11); at0x1163/0x11d7 performs two WriteConsoleA calls and checks success plus exact written count. Decimal formatting is local arithmetic/byte reversal with calls only to0x1000. ExitProcess(0) at0x11eb; failed module/symbol/version/stdout/write checks converge on ExitProcess(1) at0x11f7.
- Inspected the entire 510-byte executable `.text`, not just the import names. It contains those paths, bounded stack formatting and padding traps. No alternate payload, extra executable section, runtime initializer or additional indirect-call source was found. ASCII/UTF-16 strings showed expected DLL/symbol/PE bookkeeping and output labels; no obvious sensitive identifiers, secrets, filesystem paths or embedded PDB path. Machine instructions can look like incidental printable strings; this is not a proof of absence of every possible covert encoding.

Remote build log matches `/O1 /GS- /TC`, direct console entry, `/NODEFAULTLIB /DYNAMICBASE /NXCOMPAT /MACHINE:X64`, kernel32 only, compiler19.51.36260/toolset14.51.36231/linker14.51.36260/SDK10.0.26100.0 and runner MARC. The packet records Build Tools18.11.3. No discrepancy with source/build provenance was found. Build log version probes and runner identity are remote records, not fresh target queries. Matching hashes, source identity, call-path disassembly and recorded provenance do not constitute a reproducible-build attestation or target qualification. Rebuilding was outside this review.

The prior review's independent binary-byte evidence blocker is therefore closed for this exact frozen packet. It does not authorize execution.

## Required adversarial schedules

These are normative dispositions under the stated trust/durability assumptions. “Reject” below means no new authority/signature/output; recover identical pending bytes or stay closed. Fixture evidence covers named cases as above; concurrency/crash/lifecycle rows are design analysis unless specifically in the eight bounded synthetic schedules. No availability failure is counted as successful service.

| Schedule | Required disposition and limit |
|---|---|
| Two candidates same predecessor | One durably locked candidate only; conflicting request `CLOSED_PENDING_RECONCILIATION`. No second successor. |
| 2-vs-2 split | `CLOSED_PENDING_RECONCILIATION`; absent identical quorum, `PERMANENT_NAMESPACE_UNAVAILABLE` (global domain also stalls). No unlock. |
| Stalled pending slot | `CLOSED_PENDING_RECONCILIATION` indefinitely; cannot skip it, even for withdrawal/control. |
| Stale validator rollback | Quarantine if known; consumes Byzantine budget even if undetected. No automatic healthy rejoin; replacement bridge required. |
| One malicious validator | `VALIDATE_AND_CERTIFY` only if three healthy valid approvals are obtained; withholding alone cannot supply authority. Availability not guaranteed by malicious cooperation. |
| One malicious + one unavailable | CLOSED; only two healthy available, no quorum. |
| Two unavailable | CLOSED; no quorum. |
| Two compromised | `ASSUMPTION_VIOLATED_NO_SECURITY_CLAIM`. |
| Policy bridge old quorum loss | `PERMANENT_NAMESPACE_UNAVAILABLE`; new quorum/root cannot override old quorum. |
| Bridge mismatched old/new body | Reject both-body mismatch/invalid signature; no activation. |
| Stale coordinator | Reject; only reconstructed current epoch authenticates ordinary events. |
| Competing coordinator epochs | Unique slot/predecessor locks; one final or split closure. Failed materialization recovers that same certified epoch. |
| Stale/revoked gateway | Reject new escape/start; retain old final start for terminal classification. |
| Competing gateway epochs | Unique slot; one final or split closure. No credential-only currentness. |
| Duplicate SEND_STARTED | Identical completed operation `RETURN_PRIOR_FINAL_RESULT` only; different operation `SEND_ALREADY_CONSUMED`. No second send. |
| Crash before first byte | Before final start: `NOT_AUTHORITATIVE_RETRY_ALLOWED` only under existing locks/state. After final start: `CONSERVATIVELY_CLASSIFY_SEND_UNKNOWN`, consumed even if zero bytes. |
| Crash mid-send | `CONSERVATIVELY_CLASSIFY_SEND_UNKNOWN`, full bound-content exposure and consumed release. |
| SEND_UNKNOWN then resend | `SEND_ALREADY_CONSUMED`; changed operation/reconnect/restart cannot mint automatic release. |
| Withdrawal before escape | Fence wins: reject new escape/release. Pending earlier escape must finalize or blocks fence. |
| Withdrawal after escape before start | Reject start; permanent unstarted escape entry, ZERO32 terminal for accounting. |
| Withdrawal racing start | Unique next slot wins; start-first full exposure, fence-first start rejects; split blocks both. |
| Withdrawal after start | Full exposure; allow exactly one terminal; stop enqueueing upon observing valid fence. No instantaneous recall. |
| Stale gateway after fence | Cannot obtain a fresh final SEND_STARTED; old proof never a reusable send permit. |
| Late gateway learns fence/live stream | Stop when learned; account full earlier start. No instantaneous transport quiescence claim. |
| Terminal / SEND_UNKNOWN after fence | Allowed for exact pre-fence started send/epoch once; release stays consumed. |
| Missing terminal/accounting | Stay fenced/CLOSED; cannot certify WITHDRAWAL_ACCOUNTED. |
| Malformed CBOR | Reject before state mutation/signature. |
| Wrong request digest | Reject complete request/envelope mismatch before signing. |
| Wrong checkpoint | Reject identity/body/bundle/inclusion mismatch. |
| Unrelated consistency proof | Reject retained-anchor/full-prefix mismatch, even with valid external signatures. |
| Log rollback | Reject versus retained checkpoint/locks; missing independent retained state closes. No perfect rollback-detection claim. |
| Local cache rollback/reinstall | Complete certified replay and retained-anchor check; no namespace reuse/cache-authoritative state. Missing history closes. |
| Permanent quorum loss | `PERMANENT_NAMESPACE_UNAVAILABLE`, no reduced quorum/admin unlock. |
| F1a/F1b cross-role key reuse | Normative reject; **actual oracle accepts**. Blocking conformance defect. |

## Authorization readiness and remaining evidence

`READONLY_PREFLIGHT_NEEDS_CORRECTION`. Although the exact frozen binary packet is acceptable as static evidence, the requested combined protocol-plus-fixtures gate is not satisfied. The existing packet may not advance under this review to separate execution authorization, and this review grants no execution or target-access authority.

Remaining operational/target evidence is unchanged: independently controlled immutable pins; real host/admin/storage/credential/restore-domain independence; actual durable transaction/crash/rollback/key-retirement behavior; production key/root custody and replacement ceremony; sole gateway enforcement and human/application authorization artifacts; target identity/runtime/console/privilege/capture constraints and incidental OS/AV/EDR/audit effects. None was measured here. Private admission NO; `P2_F_GATE_2_NOT_READY`; `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Publication and completion record

- Starting/reviewed head: `2104f1ff2ba8be79a54c1700c65f10e2152dfc72`.
- Final publication head/sole review commit: the commit introducing this new record and prepending the two current handoff entries (resolve with `git log -1 --format=%H -- <this path>`); exact SHA is recorded in the external completion report to avoid a self-referential commit hash.
- Only three tracked paths may change: this new record, `docs/research/handoffs/engine2x-current-handoff.md`, `docs/research/handoffs/engine2x-p2f-current-session-handoff.md`. No fixture/design/collector/build modification.
- PR #34 was independently observed OPEN, DRAFT, UNMERGED at reviewed head. Publication must preserve those states and use normal fast-forward push only.
- Reviewed-head research run [42377619429](https://github.com/Isshhin/stridelab-development/actions/runs/42377619429) independently fetched: SUCCESS, head2104f1ff, log confirms440 tests. This pass also ran the four focused fixture tests and generator drift check locally. CI success does not cover the newly discovered missing cases.
- Reviewed-head Graphify mirror [42377614558](https://github.com/Isshhin/stridelab-development/actions/runs/42377614558) failed before steps. Treat the recorded billing/spending startup failure as infrastructure-only; no evidence of changes to reviewed files.
- Documentation-only review paths do not match the research workflow path filter. No new self-hosted run was dispatched, especially because this task prohibits target access. Reviewed-head success must not be relabeled as a run on the review publication commit. Final-head checks are recorded separately in the external completion report.
- Top-level authority validation: `AUTHORITY_VALIDATION_MODEL_ACCEPTABLE` (written normative model only, not F1-affected oracle conformance).
- Top-level lifecycle: `WITNESS_VALIDATOR_LIFECYCLE_ACCEPTABLE` (conditional operational assumption, not deployed qualification).
- Top-level one-shot output/withdrawal: `ONE_SHOT_OUTPUT_WITHDRAWAL_MODEL_ACCEPTABLE`.
- Top-level canonical protocol: `CANONICAL_AUTHORITY_PROTOCOL_V1_ACCEPTABLE` (written schemas).
- Top-level binary: `READONLY_PREFLIGHT_BINARY_PACKET_ACCEPTABLE` (exact frozen bytes only).
- Top-level preflight readiness: `READONLY_PREFLIGHT_NEEDS_CORRECTION`.
- Exact next permitted task: **a bounded fixture-oracle correction for F1's complete historical role-key admission and the two signed rejection fixtures, followed by exact-head validation and a NEW independent conformance/security review.** Keep the normative design and frozen collector packet unchanged. Do not execute that next task under this review; do not execute the collector, access/qualify the target, deploy services, admit private data, start P2-B, merge or mark PR #34 ready.

P2F_CUSTODY_AUTHORITY_V7_DESIGN_NEEDS_CORRECTION
