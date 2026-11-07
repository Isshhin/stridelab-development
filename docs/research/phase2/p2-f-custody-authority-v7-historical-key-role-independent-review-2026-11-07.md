# Independent review of v7 historical key-role correction

Date: 2026-11-07 (Asia/Manila). Repository: `Isshhin/stridelab-development`. Branch: `research/runna-garmin-coaching-20261102`. PR #34. Exact starting/reviewed head: **`4f3805f84ba9fb5828eb94d513be643fc37cf9c9`**.

**Result: `P2F_CUSTODY_AUTHORITY_V7_DESIGN_ACCEPTABLE_FOR_PREFLIGHT_AUTHORIZATION_REVIEW`.** The permanent historical key-role correction closes F1a/F1b, preserves expressly allowed current same-role retention, and passes this new independent conformance review. No new blocking finding or regression was found within this scope. This accepts the bounded fixture oracle's corrected role admission and catalogue, preserves the prior accepted written protocol and frozen binary findings, and permits ONLY a separate preflight authorization review.

This is a new review pass, not adoption of author-side test results, a separately staffed human audit, an independently operated custody system, or production security certification. Fresh adversarial proposals and a separate message/signature checker were constructed outside the repository; no design, implementation, catalogue, collector, workflow or packet was modified to manufacture acceptance. Prior reviews remain unchanged.

**Collector NEVER EXECUTED in this review.** No execution, emulation, debugging, loading as executable code, rebuild, target access/qualification, TPM/TBS operations, operational key provisioning, deployment or participant/provider/private data was performed. Synthetic signatures use only published invented fixture seeds. Outside actors' behavior cannot be proven by static inspection. PR #34 stays **OPEN, DRAFT, UNMERGED**. Private admission NO; `P2_F_GATE_2_NOT_READY`; `P2_B_PROTOCOL_READY_DATA_NOT_READY`. Engine 1.5, Trackside, APK/release, frozen Session G and unrelated research remain untouched.

## Scope and authority

Reviewed correction commits `ff95e55ef15852607ce66c6f12e600e4bb2bd66e`, `ae6b434b869e683ed9eefea0da3c20c07b9c2024`, `4f3805f84ba9fb5828eb94d513be643fc37cf9c9` against parent `8645e2fd4a8ed264a2184df72711355c2d50a1df`. Exactly the six requested verifier/test/catalogue/handoff paths changed. Read the complete affected verifier flow, fixture construction, all eight vector tests, sole normative [correction supplement](p2-f-custody-authority-v7-correction-closure-2026-11-07.md), [prior independent correction review](p2-f-custody-authority-v7-correction-independent-security-review-2026-11-07.md), catalogue/JSON and both current handoffs. Searched all research Python callers/references of `policy_check`, `keyroles`, the removed `epochkeys` and the generator; no additional affected production caller was found.

The supplement remains the sole protocol/schema/validation authority; catalogue bytes/results are subordinate. Its historical correction-author completion/status paragraphs do not revoke the later independent frozen-binary acceptance or establish current authorization. The first current handoff entry controls current task status. No normative text was rewritten. The connected Graphify index was at `49824dc1c0e4c4825398f76ac5f5ea71bd145ba5`, not this review head; it was unsuitable for these files. Direct pinned Git source controlled this review. The reported mirror billing/startup failure is infrastructure-only, not a protocol failure or proof of current source coverage.

## Historical registry and reconstruction

`initial` verifies independently pinned genesis and root, validates disjoint initial roles, then initializes the exact raw32-key map for log, genesis-root, four validators, four witnesses and both roots. `policy_roles` is called only after policy shape/key-set/disjointness checks in the admission path. Its dictionary cannot conceal an internally duplicated role key because `policy_check` rejects that proposal first.

For a bridge, `verify` passes BOTH the permanent prior map and the current policy's exact role map to `policy_check`. Each proposed key must be unseen, or have the same historical role AND still occupy that role in the current policy. This permits unchanged retention, rejects retired same-role resurrection as a fresh replacement, and rejects all historical cross-role reuse. Updating the map after successful dual approvals adds new roles without deleting retired entries; accepted overlaps already have identical values, so update cannot relabel a key.

For type2/type3, `transition` requires a fresh shared-category epoch ID and a public key absent from the entire map, then records coordinator-epoch/gateway-epoch. This also prevents repeated use in the same operational role, as required by epoch freshness. Coordinator replacement, gateway replacement and type4 revoke never erase map entries. Genesis/log roles are additionally protected by policy-internal disjointness checks.

`verify` performs transitions on `deepcopy(prior)` and returns the copy only after all authentication, transparency and authority signatures pass. Bridge role additions occur after those checks. Exact already-final retries return an unchanged deep copy of the retained state/proof; they do not reapply roles. Full replay from pinned genesis derives the registry from certified events/policies. The map is derived in memory for this bounded oracle, not a trusted persisted cache: a caller-supplied forged state is outside this replay entry boundary and must never substitute for complete replay or live durable anchors.

**`HISTORICAL_KEY_ROLE_REGISTRY_ACCEPTABLE`**

## Original F1 counterexamples and signed fixtures

Independently reconstructed both original proposals from legitimate catalogue history with the original operation/epoch labels. Recomputed exact policy/bridge payloads and all signatures. No known-hash denylist or fixture-name branch exists in the verifier; errors arise from generic map admission.

| Case | Exact proof SHA-256 | Observed result |
|---|---|---|
| F1a: current coordinator epoch key used as next gateway-root | `5cda3294b4c2888e6d4c39e3a2e2fa33d0ec0e96ba7caabbe5d5e1ba2777ae87` | `ROLE_KEY_COLLISION`; prior state unchanged |
| F1b: retired old-policy validator used as gateway epoch key after bridge | `227c845f4f95e23e3a3b2ea195a21ca55e0753b71904c56a47d8752b7a83f059` | `EPOCH_KEY_REUSE`; prior state unchanged |

**`F1A_ACTIVE_CROSS_ROLE_COLLISION_REJECTED`**

**`F1B_RETIRED_CROSS_ROLE_COLLISION_REJECTED`**

Also independently replayed and checked the added catalogue proposals:

- `reject-policy-cross-role-active-coordinator-as-gateway-root`
- `reject-gateway-epoch-cross-role-retired-validator`

Each rejects with its stated role error and preserves the whole prior state. For all four proposals above, a diagnostic negative control deletes ONLY the one conflicting derived history-map entry from a copy; the identical signed proposal then passes the complete unchanged verifier and advances one leaf. This is a test isolation control, never an authorized replay/admission shortcut. Separately extracted signatures verify independently of the verifier's early role rejection. Together these establish that rejection is caused by historical role admission rather than an unrelated malformed object, stale field, missing quorum, invalid signature or hardcoded digest.

Both added cases are actual complete signed records, referenced by deterministic named-history scenarios and exercised by catalogue replay. Exactly 53 rejection scenarios exist; hashes merely pin reproductions, never determine verdicts.

**`HISTORICAL_KEY_ROLE_REJECTION_FIXTURES_ACCEPTABLE`**

## Broader policy and epoch challenges

The independent review harness replayed the complete prefix through `continuity-new-policy`, independently scanning initial/bridge/epoch raw objects to compute expected key roles without calling `policy_roles`. It compared that expected map after EVERY replayed proof. A second fresh initialization plus the full prefix produced an identical entire state. The prefix includes bridge, coordinator replacement, gateway revoke and gateway replacement; an exact old finality replay after these changes preserved the entire state.

For a broader matrix, the harness then revoked the replacement gateway and finalized a policy bridge replacing all policy keys, including both roots. This produced **34 historical keys**: one log, one genesis-root, 12 validators, 12 witnesses, two coordinator roots, two gateway roots, two coordinator epoch keys and two gateway epoch keys. Original policy roots and validators/witnesses were now retired; both operational gateway keys had been revoked/replaced. Every earlier role remained recorded.

For each historical key, construct a fresh epoch3 policy with fresh roots, then insert that key into each of validator, witness, coordinator-root and gateway-root roles. Re-sort sets and recompute validator-set digest, witness policy, exact C2SP artifact/hash, policy digest, bridge payload, old/new witness approvals and old/new validator approvals. All **136** proposals had otherwise valid signatures:

- **126 rejected** generically for historical/intrinsic role separation, including retired same-role resurrection.
- **10 accepted**: each currently installed policy key retained in its existing role. A separate bridge retaining ALL current policy roles together also accepted and left the map unchanged.
- For the 118 rejected non-log/non-genesis proposals, deleting only the conflicting map entry in a diagnostic copy permitted the identical complete proposal. The eight immutable log/genesis-root collisions remain rejected by internal policy disjointness independently of history-map admission.

This includes every requested challenge: old validator -> new gateway-root; old witness -> new validator; retired coordinator-root -> gateway-root; active/retired coordinator epoch -> every policy role; revoked gateway epochs -> every policy role; log/genesis-root -> every policy role. No role is exempted because its old holder is retired.

For both coordinator and gateway epoch starts, challenge each of the same **34** historical raw keys using a fresh ID and otherwise valid predecessor/authentication. All **68** proposals rejected with `EPOCH_KEY_REUSE`, including policy roles, opposite epoch role, retired coordinator and revoked gateways, and same operational-role reuse. Each identical proof passed only after its conflicting map entry was diagnostically removed. Fresh coordinator and fresh gateway keys/IDs both accepted.

Every rejection compared the whole prior state against a deep copy. Additional late-failure controls corrupted a final authority signature AFTER preparing an otherwise fresh coordinator start, gateway start and bridge: all three rejected without committing any new role or other state. This exercises provisional history changes beyond the early admission failures.

**`SAME_ROLE_RETENTION_SEMANTICS_ACCEPTABLE`**

**`POLICY_HISTORY_KEY_SEPARATION_ACCEPTABLE`**

**`EPOCH_HISTORY_KEY_SEPARATION_ACCEPTABLE`**

**`KEY_ROLE_REPLAY_DETERMINISM_ACCEPTABLE`**

## Canonical bytes, cryptography and regression evidence

Fresh local Python 3.12 checks:

| Check | Result |
|---|---|
| Independent generation compared to parsed tracked JSON | Exact equality |
| Separate generator `--check` process | PASS; exact JSON/Markdown regeneration, no tracked rewrite |
| Object raw SHA-256 and independently assembled domain hashes | All 365 records checked |
| Catalogue replay / rejection no-mutation | PASS via fresh focused suite and independent role challenges |
| RFC8032 known-answer sign/verify/wrong-message | PASS |
| Independently extracted signatures | **2,868 verified**, including both original F1 proposals, both added negatives, baseline history and matrix candidates |
| Focused `test_p2f_v7_vectors.py` | **8 passed** |
| Full research unittest discovery | **444 run: 443 passed, 1 existing Windows symlink skip**, no failures |
| Baseline preservation versus `8645e2fd` | All **363 objects, 86 scenarios, 8 schedules** exactly preserved |

The separate signature checker directly extracts genesis/checkpoint/authentication/witness/old-new authority messages and constructs the literal domain prefix plus ONE NUL. It calls Node's standard Ed25519 implementation directly, without `verify`, `approval_check`, `h` or `Crypto.verify` from the reviewed oracle. A changed-message negative control fails. This independently tests message extraction/bindings, not an independent cryptographic backend: both paths ultimately use installed Node crypto. It is not a new proof of the backend's subgroup implementation or production crypto admission; the unchanged strict point/encoding checks and prior accepted signature-model review remain relevant.

Review harness SHA-256: `9ad1ec9581316f70dfb8fb6aff4e06c7da5176c9163e0066920d69cfecfbc133`. External per-case evidence JSON SHA-256: `7d7e70df4b8d808a4a0d5963d969ebfe95453986d11b62e7b251d68fa0f7e272`. These review-only artifacts were produced outside the repository; the JSON records exact hashes/results for the four F1/fixture cases and 194 broader rejections. No normative fixture or repository test was added by the reviewer.

Reproduction uses `py -3.12 -B research/tools/p2f_v7_closure_vectors.py --check`, `py -3.12 -B -m unittest discover -s research/validation -p 'test_p2f_v7_vectors.py' -v`, and `py -3.12 -B -m unittest discover -s research/validation -p 'test_*.py'`. The matrix above specifies the additional proposal construction and diagnostic controls; it deliberately extends beyond the checked-in eight tests. Lock schedules remain bounded synthetic design checks, not disk crash/rollback experiments.

Accepted catalogue totals: **365 exact object records; 34 accepted complete finality scenarios; all 14 event types; 53 actual rejections; 1 exact replay; 8 lock/lifecycle schedules**. The two added rejected records/scenarios explain the entire increase from the preserved baseline.

**`CANONICAL_TEST_VECTORS_ACCEPTABLE`**

## Previously accepted areas: regression disposition

Regression scope follows the narrow diff, surrounding verifier flow, unchanged normative rules and preserved baseline bytes/scenarios; it is not a redesign or an exhaustive new audit of every operational assumption.

| Previously accepted area | Current disposition |
|---|---|
| Normative precedence | ACCEPTABLE preserved; sole normative supplement unchanged |
| Transparency + authority binding | ACCEPTABLE preserved; bundle/certificate/checkpoint binding unchanged |
| Signature domains and strict encoding | ACCEPTABLE preserved; no domain/message/admission changes |
| Deterministic validator procedure | ACCEPTABLE preserved; history admission corrected generically, mutation still committed after signatures |
| Global next-slot serialization | ACCEPTABLE preserved; checkpoint/prefix/slot semantics unchanged |
| Candidate/crash locks | ACCEPTABLE preserved as conditional persistence obligations; no implementation or durability qualification claimed |
| Genesis/policy | ACCEPTABLE preserved; registry initialized after existing pinned/genesis validation |
| Policy bridge | ACCEPTABLE preserved; historical admission strengthens conformance without changing dual-quorum activation |
| Witness/validator lifecycle | ACCEPTABLE preserved under existing independence/Byzantine assumptions; retirement no longer loses role history |
| Coordinator/gateway epochs | ACCEPTABLE preserved; fresh-key and permanent history rules now enforced against all roles |
| One-shot send | ACCEPTABLE preserved; release/start/terminal logic unchanged |
| Withdrawal/accounting | ACCEPTABLE preserved; fence/terminal/accounted construction unchanged |
| Namespace/tombstones | ACCEPTABLE preserved; allocation/deletion/replay logic unchanged |
| Canonical schemas | ACCEPTABLE preserved; no wire/schema changes |
| Frozen binary packet | ACCEPTABLE carried forward as below |

No new regression found. Availability sacrifices, full-prefix size costs, conditional quorum assumptions and lack of real custody/runtime evidence remain as previously recorded. This review does not extend those earlier claims.

**`AUTHORITY_VALIDATION_MODEL_ACCEPTABLE`**

## Frozen binary carry-forward

Git comparison from prior independently reviewed `2104f1ff2ba8be79a54c1700c65f10e2152dfc72` through this head shows only review/handoff and the stated correction files. The collector source, preflight build workflow, frozen packet/evidence and normative supplement are unchanged. The binary itself is an external frozen artifact, not replaced by these commits; no new build, upload, binary download or reinspection was performed. There is no correction-related reason to reopen the prior accepted independent static-byte review.

Carry forward exactly artifact **13573973275**, `p2f-preflight-static-artifact`, **3072 bytes**, binary SHA-256 **`96cd9069205a891c383f23d085e1c10c0f678f4d132b9c25cb7185902af3f8dc`**. Prior accepted static imports/source-binary provenance and full static-byte inspection remain valid for that frozen artifact. This is carry-forward of that recorded review, not a fresh binary inspection, reproducible-build attestation or runtime qualification.

**`READONLY_PREFLIGHT_BINARY_PACKET_ACCEPTABLE`**

## Authorization readiness and remaining evidence

F1a/F1b reject; complete historical role admission is generic; current same-role retention and fresh epochs work; vectors pass; no scoped regression was found; accepted frozen-binary evidence remains unchanged. Therefore **`READONLY_PREFLIGHT_READY_FOR_SEPARATE_AUTHORIZATION_REVIEW`**.

This verdict authorizes no execution or target access. Remaining evidence includes independently controlled production genesis/root pins; validator/witness host, administrator, credential, storage and restore-domain independence; actual durable commit/lock/high-water/rollback behavior; production root/key custody and replacement ceremonies; sole gateway enforcement and application/human authorization artifacts; target identity/runtime/console/privilege/capture constraints and incidental OS/AV/EDR/audit effects. None was measured or provisioned here. These operational questions cannot be answered by public synthetic fixtures. Gate 2 and P2-B remain blocked; private-data admission remains NO.

**Exact next permitted task:** a NEW, separately scoped preflight authorization review of the already frozen read-only packet, its proposed target/capture boundaries and remaining prerequisites. That task must decide whether any later execution can be authorized. Do not perform the authorization review or execute the collector under this task. No target qualification, TPM/TBS, key provisioning, deployment, private data, P2-B, merge or ready transition follows automatically.

## Publication and top-level verdicts

- Starting/reviewed head: `4f3805f84ba9fb5828eb94d513be643fc37cf9c9`.
- Sole review commit/final publication head: resolve the commit introducing this record with `git log -1 --format=%H -- docs/research/phase2/p2-f-custody-authority-v7-historical-key-role-independent-review-2026-11-07.md`. Exact publication SHA is recorded in the external completion report, avoiding a self-referential commit hash.
- Only this NEW additive record and the two current Engine 2.x handoffs are changed. Prior reviews remain immutable; no design/vector/collector/build change.
- PR #34 independently observed OPEN, DRAFT, UNMERGED at the exact starting head; publication must preserve all three states with normal fast-forward push only.
- Starting-head CI independently queried: [42380552446](https://github.com/Isshhin/stridelab-development/actions/runs/42380552446) SUCCESS at the exact SHA; [42380556064](https://github.com/Isshhin/stridelab-development/actions/runs/42380556064) SUCCESS at the same SHA. The second job log independently confirms **444 tests, OK, no skips**. The one skip applies only to the local run.
- Publication-head CI is observed separately after push and recorded in the completion report. Starting-head success must never be relabeled as publication-head success. No self-hosted run or preflight workflow is manually dispatched by this review.
- Historical key-role model: **`HISTORICAL_KEY_ROLE_MODEL_ACCEPTABLE`**.
- Canonical vectors: **`CANONICAL_TEST_VECTORS_ACCEPTABLE`**.
- Authority validation: **`AUTHORITY_VALIDATION_MODEL_ACCEPTABLE`**.
- Frozen binary: **`READONLY_PREFLIGHT_BINARY_PACKET_ACCEPTABLE`**.
- Preflight readiness: **`READONLY_PREFLIGHT_READY_FOR_SEPARATE_AUTHORIZATION_REVIEW`**.
- Collector **NEVER EXECUTED**; operational/target evidence remains outstanding; no Gate 2 PASS or P2-B activation.

P2F_CUSTODY_AUTHORITY_V7_DESIGN_ACCEPTABLE_FOR_PREFLIGHT_AUTHORIZATION_REVIEW
