> **2026-11-07 v5 independent review — current:** [`P2F_CUSTODY_AUTHORITY_V5_DESIGN_NEEDS_CORRECTION`](../phase2/p2-f-custody-authority-v5-independent-security-review-2026-11-07.md). Review found unresolved coordinator-to-witness finality, independent trust bootstrap and witness lifecycle, withdrawal/output fencing, canonical schema, and unfrozen preflight artifact. No preflight authorization or target access; next is a new correction proposal and independent review. PR #34 remains open, draft, unmerged; private data admission, Gate 2 PASS and P2-B remain blocked. Reviewed head `ab572a78da5dbeb3a3aa26075a43454afdb8f9cc`.

> **2026-11-07 v4 custody update — current:** [v4 desk study](../phase2/p2-f-custody-authority-correction-protocol-v4-2026-11-07.md) and [preflight v3](../phase2/p2-f-custody-authority-readonly-preflight-plan-v3-2026-11-07.md): `P2F_CUSTODY_AUTHORITY_V4_CORRECTIONS_INCOMPLETE`; `AUTHORITY_CONTINUITY_ARCHITECTURE_NOT_JUSTIFIED`; `WITHDRAWAL_OUTPUT_SERIALIZATION_INCOMPLETE`; `CANONICAL_AUTHORITY_FORMAT_INCOMPLETE`; `READONLY_PREFLIGHT_PLAN_INCOMPLETE`; `OFFLINE_AUTHORITY_SENSITIVE_OPERATION_NOT_SUPPORTED`. etcd plus an independent witness and sole output gateway is a candidate only; actual rollback/fork protection and externally visible byte ordering remain unproven. Local Windows storage is cache only. All authority and sensitive output CLOSED; no target/preflight execution, private data, integrated qualification, P2-B or Gate 2 PASS. Next: resolve independent continuity and the actual irreversible release boundary, finish canonical state schemas, and freeze a reviewable two-call preflight artifact/capture plan; then seek independent review. Historical sections below remain history.

> **2026-11-07 v3 custody update — current:** The additive [v3 design study](../phase2/p2-f-custody-authority-correction-protocol-v3-2026-11-07.md) and [preflight v2](../phase2/p2-f-custody-authority-readonly-preflight-plan-v2-2026-11-07.md) are incomplete. `P2F_CUSTODY_AUTHORITY_V3_CORRECTIONS_INCOMPLETE`; `REPLACEMENT_ANCHOR_NOT_YET_JUSTIFIED`; `WITHDRAWAL_COMPLETENESS_MODEL_INCOMPLETE`; `WINDOWS_DURABILITY_CONTRACT_INCOMPLETE`; `READONLY_PREFLIGHT_PLAN_INCOMPLETE`. TPM witness remains abandoned. All authority and outputs CLOSED; no preflight, target access, private data or integrated qualification authorized. Next: resolve independent anchor continuity, actual output/withdrawal ordering, durability/canonicalization and preflight caller/side effects, then a new independent design/security review. Historical sections below may describe superseded next steps and are preserved as history.

# StrideLab Engine 2.x current coordinating handoff

## Custody/authority correction protocol v1 — 2026-11-07

The [conditional correction protocol](../phase2/p2-f-custody-authority-correction-protocol-2026-11-07.md) specifies target roles, counter plus write-once commitment witness, inventory/tombstones, fail-closed recovery, key custody and trusted time. It is ready for a **new independent design/security review**, not qualification: TPM slot semantics, sole-writer policy, actual target and time gateway are unverified; unsupported assumptions require redesign. Integrated target qualification has NOT run; private pilot NOT READY, human/provider admission NO, `P2_F_GATE_2_NOT_READY`, `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Custody/authority target-qualification design review — 2026-11-07

`PRIVATE_PILOT_CUSTODY_AUTHORITY_DESIGN_NEEDS_CORRECTION`: the [review](../phase2/p2-f-custody-authority-design-qualification-review-2026-11-07.md) identifies unresolved target boundary, sole issuance, receipt/recovery, independently current inventory, key and trusted-time design. This is not separately staffed independent security signoff. Correct and independently review the design before integrated invented-data target qualification; no hardware provisioning or participant/provider data. Synthetic passes unchanged; private pilot NOT READY; `P2_F_GATE_2_NOT_READY`, `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Private-pilot qualification started — 2026-11-07

The [qualification register](../phase2/p2-f-private-pilot-qualification-register-2026-11-07.md) separates the completed synthetic milestone from remaining independent authority/custody review, environment, time, key, restore, admission and source/reference evidence. All real-pilot items remain open. Private pilot NOT READY; human/provider admission NO; `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` unchanged.

## Independent coordinated-state review — 2026-11-07

`PASS_FOR_SYNTHETIC_AUTHORITY_CUSTODY` for the cooperative in-process model at `d545f7616708dfb46e9a3af9bd8b66c6eec37644`; Batch 3 is accepted for synthetic scope. The [independent review](../phase2/p2-f-coordinated-state-independent-review-2026-11-07.md) records ownership, publication, aliases, cleanup and local Python 3.12 verification. Exact-head CI was pending at last check. Prior synthetic journal durability pass unchanged; private pilot NOT READY, human/provider admission NO, `P2_F_GATE_2_NOT_READY`, `P2_B_PROTOCOL_READY_DATA_NOT_READY`.
## P2-F custody/authority v5 design — 2026-11-07

`P2F_CUSTODY_AUTHORITY_V5_READY_FOR_INDEPENDENT_REVIEW`: the additive [v5 proposal](../phase2/p2-f-custody-authority-correction-protocol-v5-2026-11-07.md) separates etcd ordering, append-only authority history and independent witness continuity. It freezes a 4-witness/3-of-4 quorum under an at-most-one-malicious-witness assumption, fresh restart challenges, conservative `ESCAPE_COMMITTED` output accounting, restrictive withdrawal ordering and deterministic CBOR profile. The [read-only preflight v4 plan](../phase2/p2-f-custody-authority-readonly-preflight-plan-v4-2026-11-07.md) remains exactly two unexecuted information calls. This is documentation ready for independent review only: no implementation, target access, TPM/TBS operation, key provisioning, data admission, Gate 2 PASS or P2-B activation is claimed. Baseline is `a4ca8d37fb10d8ed5c063b77d0267d976ff9c250`. PR #34 remains open, draft and unmerged. Exact-head CI for the resulting commit must be checked after push.

## Coordinated state Batch 3 audit — 2026-11-07

`COORDINATED_STATE_BATCH3_CORRECTED_CANDIDATE`: the local audit found five material correctness gaps, now locally corrected with regressions. All 34 review schedules and the documented Python 3.12 suites passed; see the [Batch 3 audit](../phase2/p2-f-coordinated-state-batch3-2026-11-07.md). Batch 3 remains pending independent acceptance. Next: refresh hosted Graphify for this candidate and obtain independent Work review. Private-pilot, Gate 2 and P2-B statuses remain blocked.

## Coordinated state Batch 2 — 2026-11-07

`COORDINATED_STATE_BATCH2_PUBLICATION_COMPLETE` at implementation `1d1fc967d8e40bf20beba2493f632493b50c9c49`, starting from `c0cd31d022fd044de6b5d36cd0627b1396ce6d0f`. The [Batch 2 record](../phase2/p2-f-coordinated-state-batch2-2026-11-07.md) details admission ownership, epoch/audit accounting, the final check-to-append interval and synthetic limits. State-ordering review 3/3 and prior review schedules 31/31 pass; coordinator 15, authority 81, publication 23, durable 54 and P2 267 pass. Full research: 431 run, 430 passed and one Windows symlink skip. Final pushed head is the documentation commit containing this entry; exact-head CI was unavailable at documentation time. Next: separately perform Batch 3 review-fixture migration/cleanup and independent re-review. No authority/custody pass or private-pilot readiness is claimed; prior journal durability pass, human/provider admission NO, `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

## Coordinated state Batch 1 — 2026-11-07

`COORDINATED_STATE_BATCH1_FOUNDATION_COMPLETE` at implementation `46981678123dd709c7cb8641ade808dcc2a7d370`, starting from `a6b0ecdc510efcb8ba6a51b8b1489e401a6d5ee8`. The [Batch 1 record](../phase2/p2-f-coordinated-state-batch1-2026-11-07.md) inventories shared-lock/epoch mutation ownership, copy-out protections, uncovered broker paths, fixture migration and exact results. Coordinator 15 and authority 81 passed; prior review groups 6/8/11/6 passed; durable 54 and P2 244 passed; full research 408 run, 407 passed and one Windows symlink skip. The two state-ordering reproductions remain failing; `Broker.read` epoch publication integration is deliberately deferred. Final pushed head is the documentation commit containing this entry; CI unavailable at documentation time. Next: separately implement Batch 2, including broker/admission ownership, internal audit epoch handling and callback-free publication. No authority/custody pass or private-pilot readiness is claimed; prior journal durability pass and blocked Gate 2/P2-B statuses remain unchanged.

## Coordinated synthetic state design — 2026-11-07

The [design review](../phase2/p2-f-coordinated-state-mutation-design-2026-11-07.md) selects controlled mutation ownership, one shared reentrant lock and a monotonic mutation epoch for a bounded synthetic correction. Next: separately implement and test that model, then obtain independent re-review. This is design only; `SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION` remains. No human/provider admission; private pilot blocked; `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` unchanged.

## Synthetic state-ordering review — 2026-11-07

`SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION` at reviewed head `e21d0b2e0afe4677253bff5de2865f4b95b69760`. The [review record](../phase2/p2-f-synthetic-state-ordering-review-2026-11-07.md) documents two controlled callbacks that change previously validated anchor or inventory state while output still appends. Review-only reproductions: two failures and one pass; existing 31 schedules and Python 3.12 focused, durable, P2 and full research suites pass with the documented Windows skip. No broker implementation changed. Exact-head CI was last observed queued; current state is unverified. Private pilot remains blocked, human/provider admission NO, `P2_F_GATE_2_NOT_READY`, `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Synthetic final withdrawal fence — 2026-11-07

`SYNTHETIC_AUTHORITY_CUSTODY_FINAL_WITHDRAWAL_FENCE_IMPLEMENTED` at `ddb7973fa60902a106c6b36c0ece0b314227db9b`, starting from `22b42eb5e941a3e132b05498312aecedd57f0b55`. The [implementation record](../phase2/p2-f-synthetic-authority-harness-2026-11-07.md#final-withdrawal-fence--2026-11-07) details the post-validation withdrawal recheck and six added regressions. Final pushed head is the documentation commit containing this entry (resolve with Git); exact-head CI was unavailable at documentation time. Local results: focused 81; independent schedules 6/8/11/6; durable 54; P2 229; all passed. Full research: 393 run, 392 passed, one Windows symlink skip. Prior CI 42335896033 was cancelled. Next: independent adversarial re-review; no authority/custody pass is claimed. Prior journal `PASS_FOR_SYNTHETIC_DURABILITY` unchanged; private pilot blocked, human/provider admission NO, `P2_F_GATE_2_NOT_READY`, `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Synthetic custody withdrawal-fence re-review — 2026-11-07

Verdict: `SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION`. The [defensive re-review](../phase2/p2-f-synthetic-authority-withdrawal-rereview-2026-11-07.md) checked correction `b6e8fe96581c3f6990838491e09b292e8c9827f5` at `6e09172158fe4bb4e5819c23c872eb69b1e034d2` with Graphify indexed at that same SHA. The prior hash/equality nested-read defect is fixed in its tested schedules. A P1 synthetic withdrawal case remains: a callback from final inventory hash/equality or data lookup can call `receive_withdrawal` under the same `RLock` after the initial withdrawal check; the broker then appends a chunk before noticing withdrawal. Review-only tests: six run, three passed and three failed. No broker implementation was modified in this review.

Independent local counts: focused 75 passed; prior review schedules 6/6, 8/8 and 11/11; durable 54 passed; P2 223 passed; full research 387 run / 386 passed / one Windows symlink skip. CI 42335896033 was queued at last check. Next: a bounded synthetic-only final withdrawal/violation fence after callback-capable validation, the three failing regression schedules and separate re-review. Prior synthetic durability pass remains scoped to the journal; private pilot NOT READY; human/provider admission NO; `P2_F_GATE_2_NOT_READY`; `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Synthetic final publication recheck — 2026-11-07

`SYNTHETIC_AUTHORITY_CUSTODY_FINAL_PUBLICATION_RECHECK_IMPLEMENTED` at implementation `b6e8fe96581c3f6990838491e09b292e8c9827f5`, starting at `338892889c61c205805aaa260c73951bcdffd171`. The [implementation record](../phase2/p2-f-synthetic-authority-harness-2026-11-07.md#final-publication-recheck-implementation--2026-11-07) describes the one-line post-validation fence and hash/equality regression cases. Final pushed head is the documentation commit containing this entry (resolve with Git); exact-head CI is not available at documentation time. Local results: focused 75, review schedules 6/8/11, durable 54 and P2 223 passed; full research 387 run, 386 passed, one Windows symlink skip. Independent final re-review remains required. The earlier `PASS_FOR_SYNTHETIC_DURABILITY` is unchanged; private pilot blocked, human/provider admission NO, `P2_F_GATE_2_NOT_READY`, `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Synthetic authority final publication-fence re-review — 2026-11-07

Verdict: `SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION`. The [independent re-review](../phase2/p2-f-synthetic-authority-final-rereview-2026-11-07.md) checked correction `00257401e66ec2be8dbd1b0fc8168cd2e4c21d31` at `ae0f5b093e5190fb185d59bd2f7295891747d3c2` with Graphify indexed at that same SHA. The prior generator-before-yield bug is fixed and all 6 and 8 earlier review schedules pass. A new P1 synthetic edge remains: a `str` subclass's `__hash__` invoked during the final inventory validation can catch a same-owner nested-read denial after the violation check; the broker then appends a chunk before noticing the poison. A separate review-only script contains 11 schedules: 10 pass and this one fails. No implementation change was made in review.

Local verification: focused 71 passed; prior review 6 and 8 passed; durable 54 passed; P2 219 passed; full research 383 run / 382 passed / one Windows symlink skip. CI 42322328925 was queued at last check, not passed. Next: bounded synthetic-only validation-to-append fence correction with normal regression tests for reentrant hash/equality or equivalent validation callbacks, then independent re-review. Earlier synthetic durability pass remains scoped to its journal. Private pilot NOT READY; human/provider admission NO; `P2_F_GATE_2_NOT_READY`; `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Synthetic publication-fence correction — 2026-11-07

`SYNTHETIC_AUTHORITY_CUSTODY_PUBLICATION_FENCE_CORRECTED` at implementation `00257401e66ec2be8dbd1b0fc8168cd2e4c21d31`, starting from `9dbb37e01740812ced317ee08de9436a92b1874d`. The [implementation record](../phase2/p2-f-synthetic-authority-harness-2026-11-07.md#publication-fence-correction--2026-11-07) records the iterator defect, locked pre-append check, eight regressions, counts and limits. Final pushed head is the documentation commit containing this entry (resolve with Git); new exact-head CI was not yet available at documentation time. Local tests: focused 71, original review 6, correction review 8, durable 54 and P2 219 passed; full research 383 run, 382 passed and one Windows symlink skip. Previous CI `42317715858` was cancelled. Independent adversarial re-review remains required; this is not authority/custody clearance. Prior `PASS_FOR_SYNTHETIC_DURABILITY` is unchanged; private pilot blocked, human/provider data admission NO, `P2_F_GATE_2_NOT_READY`, `P2_B_PROTOCOL_READY_DATA_NOT_READY`. Next: refresh hosted Graphify and independently re-review the correction.

## Synthetic custody correction re-review — 2026-11-07

Verdict: `SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION`. The [re-review](../phase2/p2-f-synthetic-authority-correction-rereview-2026-11-07.md) independently checked correction `618a3c66f9e589245503618134e640c4234d5e3d` at live head `c5cb61d9577e40898556f0a1b1d8e30092a8f358` with hosted Graphify at that exact indexed head. All six original reproductions now pass, but a new P1 synthetic publication-fence case fails: a chunk generator catches a nested-read denial and yields a chunk; `Broker.read` emits it before rechecking its violation flag. The review-only script captures this and seven other passing schedules; no implementation correction was made.

Independent results: focused 63 passed, original review schedules 6 passed, durable 54 passed, P2 211 passed, full research 375 run/374 passed/one Windows symlink skip, additional schedules 8 run/7 passed/1 failed. CI run 42317715858 was queued at last check, not passed. Next: bounded synthetic-only publication-fence fix, promote iterator tests to the normal suite and obtain separate adversarial re-review. Prior synthetic durability pass remains limited to its reviewed journal; private pilot NOT READY; human/provider data admission NO; `P2_F_GATE_2_NOT_READY`; `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Synthetic authority/custody correction — 2026-11-07

Correction implementation `618a3c66f9e589245503618134e640c4234d5e3d` addresses the four findings in the [independent review](../phase2/p2-f-synthetic-authority-independent-review-2026-11-07.md): synchronized modeled data-version checks at each release/publication boundary; close on interrupted transitions before reconciliation; reject same-thread nested reads without poisoning unrelated competing reads; and count escaped chunks once by output position. Local Python 3.12: focused 63 passed; review schedules 6 passed; durable 54 passed; P2 211 passed; full research 375 run, one Windows symlink skip. These are synthetic in-process results, not proof of real storage, cryptography, key custody, TPM, or crash persistence. The corrected implementation awaits independent adversarial re-review; do not treat the former `SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION` review as a clearance. The separate `PASS_FOR_SYNTHETIC_DURABILITY` remains unchanged; private-pilot readiness remains blocked, human/provider admission NO, `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY`. Next: refresh the hosted graph after push, then independently re-review the exact correction implementation and its concurrency, recovery and exposure boundaries.

## Independent synthetic authority/custody implementation review — 2026-11-07

Verdict: `SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION`. The [independent adversarial review](../phase2/p2-f-synthetic-authority-independent-review-2026-11-07.md) examined implementation `6adab6de58ed8ee39360ffb849c7cd500d21becd` at branch head `3a494ae612aff4a775d550579b16d772699daacb`, using hosted Graphify at the same indexed head. Four review schedules fail: data version can roll back after authorization and still publish; a `BaseException` during restrictive transition can leave reads open; a caught nested read can permit outer publication; repeated withdrawal receipts double-count escaped output. No implementation fix is included in this review.

Independent checks: focused 57 passed, durable 54 passed, P2 205 passed, full research 369 run / 368 passed / one Windows symlink skip; six additional deterministic review schedules: two passed and four failed. CI run 42287780335 was cancelled, not passed. The next task is a bounded synthetic-only correction and separate adversarial review; the review-only script and acceptance-mapping audit are in the review record. The earlier `PASS_FOR_SYNTHETIC_DURABILITY` is unchanged within its own journal boundary. Private pilot NOT READY; human/provider admission NO; `P2_F_GATE_2_NOT_READY`; `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Synthetic authority/custody implementation — 2026-11-07

`SYNTHETIC_AUTHORITY_CUSTODY_HARNESS_IMPLEMENTED` at implementation head `6adab6de58ed8ee39360ffb849c7cd500d21becd`. The [implementation record](../phase2/p2-f-synthetic-authority-harness-2026-11-07.md) maps all 30 acceptance cases to tests, lists boundaries and limitations, and records local counts: focused 57, durable 54, P2 205, full 369 (one Windows symlink skip). Added `research/validation/p2f_private_authority.py`, `p2f_private_custody.py`, and `test_p2f_private_authority.py`; the journal and production paths are unchanged. This is an in-process synthetic model, not real custody or crash-persistence evidence. Prior `PASS_FOR_SYNTHETIC_DURABILITY` remains. Private-pilot readiness remains BLOCKED, human/provider admission NO, `P2_F_GATE_2_NOT_READY`, and `P2_B_PROTOCOL_READY_DATA_NOT_READY`. Next: independent adversarial review of the exact implementation head.

## Synthetic authority/custody design review — 2026-11-07

[Synthetic-only design review](../phase2/p2-f-synthetic-authority-design-review-2026-11-07.md): `PRIVATE_PILOT_CONTRACT_READY_FOR_IMPLEMENTATION` **only for an invented-data harness**. Reviewed source `97dd48cd35e2088f43f50fe4e5332f9ec6a826e2`; Graphify index `ee3f69553ad932631a24db2a065722a140c07837` (current implementation, contract/handoffs read from source); mirror main `97dd48cd35e2088f43f50fe4e5332f9ec6a826e2`. User-reported TPM/TBS capability evidence was reviewed, not independently repeated; it proves neither NV provisioning nor real custody. Counter freshness must not be mistaken for unique issuer/content binding; a non-equivocating issuer/commitment assumption is explicit and unproven. Withdrawal receipt closes subsequent output, with already delivered bytes treated as escaped. NTS is excluded from the initial synthetic model; time-bound decisions use an injected time result and fail closed on uncertainty. No real TPM, crypto, credentials, data or production integration is authorized. The review is not a separately staffed professional security signoff. Real private-pilot readiness remains BLOCKED, human/provider admission NO, `P2_F_GATE_2_NOT_READY`, and `P2_B_PROTOCOL_READY_DATA_NOT_READY`; prior `PASS_FOR_SYNTHETIC_DURABILITY` is preserved.

Exact next step: implement the invented-data authority/custody harness and complete the review's adversarial acceptance matrix, then obtain a separate independent implementation review. Actual TPM provisioning, authenticated authority, custody, source qualification and private intake require later separate authorization and review.


## Windows workstation qualification and next synthetic milestone — 2026-11-07

The user-reported, non-destructive workstation checks establish: TPM 2.0 is present, ready, enabled, activated and owned; AMD firmware reports `3.95.2.5`; Secure Boot is `True`. TBS context creation and closure succeeded. A raw `TPM2_GetCapability` submission through TBS returned TBS result `0x00000000` and TPM response code `0x00000000`; the returned command capabilities include `TPM2_NV_DefineSpace` (`0x12A`) and `TPM2_NV_Increment` (`0x134`). These are reported observations, not an independently repeated workstation audit. Capability enumeration does not prove that Windows permits those commands for this application, that an NV index can be provisioned with the required policy, or that counter access, persistence, recovery and issuance are secure. No NV state was provisioned or modified by these checks. The earlier WMI `IsCommandBlocked` result was inconclusive.

**Next implementation milestone: synthetic authority and custody harness.** After the separate independent security/custody design review and acceptance required by the [authority contract](../phase2/p2-f-private-pilot-authority-custody-contract.md), implement the contract's bounded research-only harness with replaceable simulated counter and key unwrapper, serialized authorization/read and transition handling, closed restore, and crash/recovery fault injection. Exercise the contract acceptance matrix with invented data, including stale authority/data, duplicate generation, concurrent revoke/read and restart reconciliation. Deliver code, adversarial tests and a separate review with exact revision and results; do not equate simulated behavior with TPM rollback resistance. If review finds a design defect, correct and re-review before implementation. No real TPM provisioning, credentials, provider/human data, production integration or held-out evaluation is authorized.

**Status unchanged:** `PRIVATE_PILOT_CONTRACT_BLOCKED`, private-pilot readiness blocked, human/provider admission NO, `P2_F_GATE_2_NOT_READY`, and `P2_B_PROTOCOL_READY_DATA_NOT_READY`. Actual protected NV provisioning/policy and access, authority uniqueness and crash recovery, time bootstrap, current inventory, independent review, source and device qualification remain unverified.


## Private-pilot concrete authority decision — 2026-11-07

The [custody and authority contract](../phase2/p2-f-private-pilot-authority-custody-contract.md#9-concrete-local-first-decision-review--2026-11-07) now selects a conditional local-first candidate: a single Windows broker using an application-owned TPM 2.0 NV counter via TBS, a broker-authenticated authority ledger, user-scope DPAPI key wrapping and CNG authenticated encryption. A separately controlled NTS time-only gateway is proposed only for grant/retention deadlines; offline/clock uncertainty denies access. The exact reviewed source head was `09f5018a00ff6af36510cae9b47d025e5cbdb66f`; hosted Graphify index `8012c93d55a26ff60a85730a42aca51da561d945` was behind only docs/infrastructure, not the relevant implementation.

Verdict: `PRIVATE_PILOT_CONTRACT_BLOCKED`. The actual TPM NV/TBS provisioning and access, authority issuance uniqueness, crash/recovery ordering, trusted time bootstrap, current data inventory and independent security review are unverified. The Garmin-device FIT path, firmware, parser package and independent reference remain candidates, not approved. No human/provider data or credentials were accessed; prior synthetic durability pass is unchanged. `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

Next: obtain separate independent security/custody review and select the actual environment; verify TPM/NV support and failure behavior non-destructively in an authorized synthetic test environment, without clearing TPM or changing policy. Resolve authority/clock/recovery and source/reference gates. Only after independent acceptance implement the documented synthetic-only authority/custody harness. Do not admit human data or modify production/release paths.

## Private-pilot authority and custody proposal — 2026-11-07

The [private-pilot authority and custody contract](../phase2/p2-f-private-pilot-authority-custody-contract.md) records the reviewed source head `ee3f69553ad932631a24db2a065722a140c07837`, Graphify index `8012c93d55a26ff60a85730a42aca51da561d945`, concrete trust boundaries, threat cases and implementation tests. Verdict: `PRIVATE_PILOT_CONTRACT_NEEDS_CORRECTION`: independent security review and a specific nonrollback authority/anchor and recovery design remain open. This is self-reviewed documentation, not independent signoff. The proposed next implementation is a synthetic-only authority/currentness and custody harness after those decisions and review; no human/provider intake. The direct Garmin FIT source is only a candidate, not established device or parser support. Existing pilot command is TCX-only. `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

## Hosted Graphify mirror — verified 2026-11-07

A dedicated private mirror now provides a current hosted Graphify index for the unmerged Engine 2.x research branch:

- source of truth: `Isshhin/stridelab-development`
- source branch: `research/runna-garmin-coaching-20261102`
- Graphify mirror: `Isshhin/stridelab-engine2-graph`
- mirror default branch: `main`
- verified mirrored/indexed commit: `8012c93d55a26ff60a85730a42aca51da561d945`
- hosted graph at verification: **1,102 nodes / 2,961 edges / 37 communities**
- verified symbols/files: `SyntheticAuthority`, `research/validation/p2f_durable.py`, and `research/validation/test_p2f_durable.py`

Use the mirror repository for hosted Graphify analysis of Engine 2.x. The original `stridelab-development` repository remains authoritative; never develop directly in the mirror.

Before relying on Graphify in Work/Codex, compare its indexed commit with the current research-branch head. If they differ, treat the graph as stale until the mirror has synced and re-indexed.

Local Graphify remains a fallback for local Codex, but hosted mirror Graphify is preferred for shared Work/cloud Codex use.

## Graphify branch-indexing workflow — 2026-11-07

Hosted Graphify currently indexes the repository default branch, not the unmerged Engine 2.x research branch. Its available graph was observed at commit `49824dc1c0e4c4825398f76ac5f5ea71bd145ba5`, which does not contain the later P2-F durable-store implementation.

Use this workflow going forward:

- First check Graphify freshness / indexed commit.
- If the hosted graph contains the target implementation, use hosted Graphify first for call paths, impact analysis and linked tests.
- If the task targets newer unmerged research-branch code, Codex should use a local Graphify index from the checked-out branch (refresh with `graphify update .`; optionally install the repository hook once).
- Work should not trust a stale hosted graph for feature-branch implementation review; use Graphify PR review/current-head context where available, then targeted live GitHub verification.
- Never force broad repository rereads when a fresh graph or targeted live verification is sufficient.

This is a workflow correction, not an Engine 2.x code change. No production or research behavior changed.

## Current continuation — P2-F synthetic durability cleared — 2026-11-07

Independent review returned:

`PASS_FOR_SYNTHETIC_DURABILITY`

Reviewed implementation:

`e716f338ac52fe727e2a7a3e7d9f1495875170bd`

Independent review head:

`6255cef86aef3d7899da9bfcf49095fb39a266f7`

The previous current-validity concurrent-writer race was independently reproduced on the old implementation and not reproduced against the new SQLite writer reservation + shared process-local authority guard. The protected validation point is accepted as the synthetic linearization point. Callers must use `assert_current` before reusing a previously returned projection.

Independent validation: 54 durable, 16 F2.2, 148 P2, and 312 full-suite runs (311 passed, one Windows symlink skip). No new synthetic correctness defect was found.

Exact-head CI run `42257993818` was still queued at the last verified check. Do not report it as passed until GitHub confirms success.

Current gates:

- P2-F synthetic feasibility: PASS
- P2-F synthetic durability: PASS
- private-pilot readiness: NOT READY
- Gate 2: `P2_F_GATE_2_NOT_READY`
- P2-B: `P2_B_PROTOCOL_READY_DATA_NOT_READY`
- human/provider data admission: NO

The next phase is **private-pilot custody and authenticated authority design/review**, followed by bounded implementation and adversarial review. Do not ingest real athlete/provider data yet.

See [`engine2x-p2f-current-session-handoff.md`](engine2x-p2f-current-session-handoff.md) for the full milestone history, boundaries, and exact continuation instructions.

## Current continuation — race fix implemented, independent clearance pending — 2026-11-07

Latest implementation head: `e716f338ac52fe727e2a7a3e7d9f1495875170bd`.

Codex reports the previously reproduced current-validity concurrent-writer race as fixed with a bounded synthetic consistency barrier. Local validation is green: 54 durable, 16 F2.2, 148 P2, and 311 full-suite passes with one Windows symlink skip and no expected failures. Exact-head CI run `42257251591` was still queued at the last check.

Synthetic durability is **not yet independently cleared**. The exact next action is independent Work re-review of `e716f338...`, preferably using Graphify first to narrow repository call paths/tests and then verifying only the exact live implementation surface. Private-pilot readiness, Gate 2, P2-B and human/provider-data admission remain blocked.

See the canonical session handoff below for the full history and continuation instructions.

## Synthetic current-validity barrier continuation — 2026-11-07

The earlier current-validity final-check race is addressed in the continuation: a SQLite writer reservation and the guarded synthetic authority jointly protect the final validation point; contention or reentrant changes fail closed without retry. The prior expected-failure regression is now a normal test, with additional thread/process and authority mutation cases. See the [synthetic durable-store boundary](../phase2/p2-f-durable-synthetic-boundary.md) for exact semantics and limitations. Earlier blocker descriptions below are historical. Independent Work review remains required; do not claim a final synthetic-durability pass. This is not private-pilot infrastructure. `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.


## Canonical current P2-F session handoff — 2026-11-07

For the latest session-level state, blocker history, verified milestone SHAs, test counts, user workflow preferences, and exact continuation instructions, read:

[`engine2x-p2f-current-session-handoff.md`](engine2x-p2f-current-session-handoff.md)

That handoff documents engine/research state through `9f23075e5522e0d47e5f01325c2d62995863b9a4`. Verify the live branch before continuing and preserve anything newer.

## Independent synthetic replay follow-up — 2026-11-07

Projectors 3/4 no longer depend on the mutable reference helpers in the durable wrapper. A synthetic concurrent writer can still change the journal during the final authority check without the returned current-validity projection noticing; an expected-failure regression demonstrates this. Synthetic durability remains blocked until that bounded race is resolved and independently verified. The in-memory authority and plaintext storage remain unsuitable for human/provider data. Gate 2 and P2-B remain not ready.

## P2-F pinned replay and validity continuation — 2026-11-07

The [synthetic durable-store boundary](../phase2/p2-f-durable-synthetic-boundary.md) records the separately pinned projector 3/4 replay path and projector 4 validity metadata for authority epoch, suppression and invalidation digests, as-of time, schema, event set and exclusions. This remains synthetic-only and needs independent adversarial review. The synthetic in-memory authority is not a durable nonrollback custody service; private-pilot intake is not authorized. `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

## P2-F durability correction and audit — 2026-11-06

The [synthetic durability review](../phase2/p2-f-synthetic-durability-review-2026-11-06.md) records the reproduced duplicate-link defect, projector 3 correction, versioned replay and remaining custody limitations. Projectors 1 and 2 retain historical behavior and can duplicate activity membership. Projector 3 fails closed on unsupported supersession and ambiguous timing. This implementation-session review is not an independent signed clearance. `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

## P2-F synthetic durable-store continuation — 2026-11-06

The [synthetic durable-store boundary](../phase2/p2-f-durable-synthetic-boundary.md) describes account/view/purpose/partition-scoped event storage, synthetic authority checks, replay and restore limits. This is research-only synthetic software evidence; it is not a private-pilot custody system or device validation. Legacy unscoped stores cannot be migrated without independently supplied metadata. `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

## P2-F Foundation Unlock Sprint — 2026-11-06

The [Foundation Unlock handoff](p2-f-foundation-unlock-handoff.md) records the additive F2.2 canonical exposure-ledger contract, synthetic reference implementation, adversarial review and exact audited implementation range `ac1b0e27c2b16caae32e7fe4d226b7c7860c0135..062efc2dc5215e0a12321e65e22cdb7a53d18484`.

Current verdicts remain deliberately separate:

- P2-F synthetic feasibility: PASS
- Gate 2: `P2_F_GATE_2_NOT_READY`
- P2-B: `P2_B_PROTOCOL_READY_DATA_NOT_READY`

The next coding boundary is a durable research-only F2.2 store and admitted source adapter. Human admission, independent real-device reconciliation, frozen margins and separate Gate 2 review remain required before active P2-B or held-out access.

## Parallel public-data research

The [P2-R handoff](p2-r-public-data-handoff.md) records 13 public RR windows and the outstanding dataset-specific experiments. S1 and Gate 2 remain separate.

## Current private pilot operator kit

Start with the [operator handoff](p2-f-private-pilot-operator-handoff.md). It points to the Windows setup procedure, synthetic restore drill, private audit command and single-session checklist. The next action takes place on the steward's selected machine before participant approval. No source has been admitted.

## Historical action after private pilot hardening

Use the [private pilot readiness handoff](p2-f-private-pilot-readiness-handoff.md). A steward must complete the private particulars and verify the actual staging controls before intake. The participant can then decide on the specific grant. No human source or supported device has been established. Gate 2 and P2-B remain blocked.

## Historical action after feasibility implementation

Consult the [implementation handoff](p2-f-feasibility-implementation-handoff.md) for the current pilot decision, verification scope and remaining prerequisites. The earlier wave instructions below are historical. Gate 2 remains unsigned and P2-B remains protocol-only.

Last updated: 2026-11-04 (Asia/Manila)

Repository: `Isshhin/stridelab-development`  
Research branch: `research/runna-garmin-coaching-20261102`  
Research PR: #34, keep open, draft and unmerged.

Project-level direction:
https://github.com/Isshhin/stridelab-development/issues/42

Before continuing, verify the live branch and preserve anything newer than this handoff.

## Current priority

Engine 2.x remains the primary StrideLab workstream.

Current strategic order:

1. P2-F data admission/linkage feasibility
2. P2-B aerobic-efficiency repeatability after P2-F linkage gate
3. dependent P2-A/P2-C/P2-D/P2-E experiments
4. research-only Engine 2.x executable candidates
5. adversarial and real-data shadow validation
6. prospective athlete validation
7. Engine 2.x production candidate with Engine 1.5 fallback
8. Trackside integration
9. dogfood APK

The user explicitly accepts delaying the APK to improve coaching maturity.

## Phase 1 status

Sessions A-F + H passed pre-synthesis review.

Session G synthesis was produced at:

`4b734f1b97a54e6c15ba3bf0662d456de5c68413`

G-R1 through G-R6 were corrected at:

`26a8331e642c888dd73acb94755dded063baab8d`

Independent re-review:

`docs/research/2026-11-04-engine2x-session-g-independent-rereview.md`

Final G status:

`REVIEW_PASSED / PHASE_2_BASELINE_FROZEN`

Frozen architecture baseline:

`26a8331e642c888dd73acb94755dded063baab8d`

Do not casually redesign the frozen architecture during experiments. Change it only when evidence, rights/safety, measurement failure, or implementation impossibility materially invalidates a contract.

## Current Phase 2 authorization

`P2-F_FEASIBILITY_PREPARATION_AUTHORIZED`

`CONFIRMATORY_HELD_OUT_EVALUATION_BLOCKED_PENDING_GATE_2_MANIFEST`

Architecture approval does not authorize opening sealed held-out outcomes.

Each confirmatory track must separately freeze and independently review its Gate 2 manifest before held-out access.

## Frozen Engine 2.x architecture

`OBSERVATIONS`
→ provenance / quality / eligibility
→ multidimensional athlete state
→ uncertainty
→ safety and constraint gates
→ training intent
→ decision or abstention
→ downstream-week review
→ athlete review
→ execution
→ outcome
→ updated athlete state

Control outcomes include:

- KEEP
- ABSTAIN_NEED_MORE_INFORMATION
- NO_AUTOMATED_DECISION
- HARD_HOLD

The athlete is not reduced to one opaque readiness score.

## Key preserved conclusions

- HRV, sleep and RHR are contextual, not standalone workout controllers.
- Matched-HR pace is promising but not a progression gate until repeatability passes.
- MAF 180 Formula is not a validated individual threshold.
- No universal polarized/pyramidal/threshold-heavy winner is established.
- Watch Zone 2 is not automatically LT1/VT1/Fatmax/lactate flux.
- Universal 10% and ACWR rules are not personal safety thresholds.
- Session and weekly exposure remain separate.
- Taper, deload and re-entry are distinct decisions.
- Environment must be separated from fitness interpretation.
- Concurrent-training interference is endpoint/context dependent.
- Active recovery is optional, not mandatory.
- Synthetic correctness is not coaching-effectiveness evidence.
- Real-athlete data and prospective validation are now the main bottlenecks.

## Immediate next track: P2-F

Prepare and execute only the feasibility/admission portion allowed by Gate 1.

P2-F should establish:

- lawful source rights and intended use
- minimal acquisition schema
- parser/source correctness
- plan version before decision
- plan/revision/decision/actual/outcome linkage
- duplicate handling
- account isolation
- explicit missing/unknown codes
- retention/withdrawal behavior
- manually reconciled pilot audit

Before any confirmatory held-out evaluation, freeze and independently review the P2-F Gate 2 manifest.

P2-B begins only after P2-F supplies eligible, correctly linked repeated segments.

P2-A/C/D/E remain dependency-gated.

## Boundaries

- Engine 1.5 remains feature-frozen fallback except reliability fixes.
- Trackside production UI remains separate.
- No APK/release infrastructure changes.
- PR #34 stays open, draft and unmerged.
- Raw sensitive athlete data stays outside Git.
- No parity, readiness, injury-prevention, personalized heat, MAF-prescription or adaptive-effectiveness claim without the required evidence.

## Wave 1 preparation checkpoint

The four workstreams reached their preparation stopping points on the existing research branch. The last workstream commit was `ae4f34bc254beda6b3f369fe9233ab06624a3323`. No human records were admitted or evaluated.

| Workstream | Published research | Handoff | Commit |
|---|---|---|---|
| F1 | [Source decisions and purpose restrictions](../phase2/p2-f1-rights-admission.md) | [Rights handoff](p2-f1-rights-handoff.md) | `48a060e` |
| F2 | [Decision-time linkage contract](../phase2/p2-f2-linkage-contract.md) | [Linkage handoff](p2-f2-linkage-handoff.md) | `aa4e39a` |
| F3 | [Parser verification and audit procedure](../phase2/p2-f3-parser-reconciliation.md) | [Reconciliation handoff](p2-f3-reconciliation-handoff.md) | `ae4f34b` |
| B0 | [Comparable-easy repeatability protocol](../phase2/p2-b0-repeatability-protocol.md) | [Protocol handoff](p2-b0-protocol-handoff.md) | `0898aab` |

## Exact next step for a fresh session

Verify the live branch head and PR #34, then run only the P2-FI assignment in the [Wave 1 prompts](../2026-11-04-engine2x-phase2-wave1-prompts.md) under the [multi-session program](../2026-11-04-engine2x-phase2-multisession-program.md). Read all four artifacts and handoffs, the independent Session G re-review and the frozen Phase 2 protocol.

The new reviewer owns the separate feasibility and Gate 2 manifest verdicts. No pilot has been authorized by this checkpoint. Human-source permission, manual reconciliation on authorized events and real-device coverage remain outstanding. Active P2-B requires eligible linked repeated segments. Confirmatory outcomes remain sealed pending a frozen manifest and independent review.

## P2-FI integration review

Wave 1 integration review is complete.

Review:
`docs/research/phase2/p2-f-integration-review.md`

Handoff:
`docs/research/handoffs/p2-f-integration-handoff.md`

Verdicts:

- `P2_F_FEASIBILITY_READY` — synthetic engineering feasibility only
- `P2_F_GATE_2_NOT_READY`
- `P2_B_PROTOCOL_READY_DATA_NOT_READY`

Main blockers:
- no admitted human source;
- no real-device reconciliation;
- current parser path lacks B0 trace-level measurement support;
- F2 linkage remains unproven on an admitted human pilot;
- Gate 2 coverage/burden/device margins remain unset;
- Gate 2 manifest remains unsigned/unfrozen.

Next wave:
1. lawful human feasibility admission;
2. minimal F2 linkage implementation;
3. trace-level measurement support;
4. expanded synthetic adversarial tests;
5. independently reconciled real-device development pilot;
6. freeze Gate 2 manifest for separate review.

P2-A/C/D/E remain dependency-gated.
