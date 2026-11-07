> **2026-11-07 v5 independent review — current:** [`P2F_CUSTODY_AUTHORITY_V5_DESIGN_NEEDS_CORRECTION`](../phase2/p2-f-custody-authority-v5-independent-security-review-2026-11-07.md). Review found unresolved coordinator-to-witness finality, independent trust bootstrap and witness lifecycle, withdrawal/output fencing, canonical schema, and unfrozen preflight artifact. No preflight authorization or target access; next is a new correction proposal and independent review. PR #34 remains open, draft, unmerged; private data admission, Gate 2 PASS and P2-B remain blocked. Reviewed head `ab572a78da5dbeb3a3aa26075a43454afdb8f9cc`.

> **2026-11-07 v4 custody update — current:** [v4 desk study](../phase2/p2-f-custody-authority-correction-protocol-v4-2026-11-07.md) and [preflight v3](../phase2/p2-f-custody-authority-readonly-preflight-plan-v3-2026-11-07.md): `P2F_CUSTODY_AUTHORITY_V4_CORRECTIONS_INCOMPLETE`; `AUTHORITY_CONTINUITY_ARCHITECTURE_NOT_JUSTIFIED`; `WITHDRAWAL_OUTPUT_SERIALIZATION_INCOMPLETE`; `CANONICAL_AUTHORITY_FORMAT_INCOMPLETE`; `READONLY_PREFLIGHT_PLAN_INCOMPLETE`; `OFFLINE_AUTHORITY_SENSITIVE_OPERATION_NOT_SUPPORTED`. etcd plus an independent witness and sole output gateway is a candidate only; actual rollback/fork protection and externally visible byte ordering remain unproven. Local Windows storage is cache only. All authority and sensitive output CLOSED; no target/preflight execution, private data, integrated qualification, P2-B or Gate 2 PASS. Next: resolve independent continuity and the actual irreversible release boundary, finish canonical state schemas, and freeze a reviewable two-call preflight artifact/capture plan; then seek independent review. Historical sections below remain history.

> **2026-11-07 v3 custody update — current:** The additive [v3 design study](../phase2/p2-f-custody-authority-correction-protocol-v3-2026-11-07.md) and [preflight v2](../phase2/p2-f-custody-authority-readonly-preflight-plan-v2-2026-11-07.md) are incomplete. `P2F_CUSTODY_AUTHORITY_V3_CORRECTIONS_INCOMPLETE`; `REPLACEMENT_ANCHOR_NOT_YET_JUSTIFIED`; `WITHDRAWAL_COMPLETENESS_MODEL_INCOMPLETE`; `WINDOWS_DURABILITY_CONTRACT_INCOMPLETE`; `READONLY_PREFLIGHT_PLAN_INCOMPLETE`. TPM witness remains abandoned. All authority and outputs CLOSED; no preflight, target access, private data or integrated qualification authorized. Next: resolve independent anchor continuity, actual output/withdrawal ordering, durability/canonicalization and preflight caller/side effects, then a new independent design/security review. Historical sections below may describe superseded next steps and are preserved as history.

# Engine 2.x P2-F current session handoff

## Custody/authority correction protocol v1 — 2026-11-07

The [conditional correction protocol](../phase2/p2-f-custody-authority-correction-protocol-2026-11-07.md) specifies the target boundary, sole-issuance witness, current inventory/tombstones, restrictive transitions and recovery, key custody and trusted-time decision. It is ready for a **new independent design/security review**; TPM witness feasibility, actual host and time gateway remain unverified and may require redesign. Integrated target qualification has NOT run; private pilot NOT READY; human/provider admission NO; `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Custody/authority target-qualification design review — 2026-11-07

`PRIVATE_PILOT_CUSTODY_AUTHORITY_DESIGN_NEEDS_CORRECTION`: see the [review and target evidence plan](../phase2/p2-f-custody-authority-design-qualification-review-2026-11-07.md). Correct target boundary, sole issuance, receipt/recovery, inventory, key and trusted-time design, then obtain separately staffed independent review before integrated invented-data target qualification. Synthetic passes unchanged; private pilot NOT READY; human/provider admission NO; `P2_F_GATE_2_NOT_READY`, `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

## Private-pilot qualification started — 2026-11-07

The [qualification register](../phase2/p2-f-private-pilot-qualification-register-2026-11-07.md) records open evidence and dependencies following the synthetic pass. Private pilot NOT READY; human/provider admission NO; `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` unchanged.

## Independent coordinated-state review — 2026-11-07

`PASS_FOR_SYNTHETIC_AUTHORITY_CUSTODY` for the cooperative in-process model at `d545f7616708dfb46e9a3af9bd8b66c6eec37644`; Batch 3 accepted for synthetic scope. See the [independent review](../phase2/p2-f-coordinated-state-independent-review-2026-11-07.md). Exact-head CI was pending at last check; private pilot NOT READY, human/provider admission NO, `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` unchanged.
## P2-F custody/authority v5 design — 2026-11-07

`P2F_CUSTODY_AUTHORITY_V5_READY_FOR_INDEPENDENT_REVIEW`: see the additive [v5 design](../phase2/p2-f-custody-authority-correction-protocol-v5-2026-11-07.md) and [preflight v4 plan](../phase2/p2-f-custody-authority-readonly-preflight-plan-v4-2026-11-07.md). The design is documentation-only and leaves the coordinator, log, witnesses and gateway unimplemented. It requires fresh 3-of-4 continuity proof after restart/restore/reinstall, treats local Windows persistence as cache-only, disallows authority-sensitive offline operation, and conservatively classifies ambiguous output release as `ESCAPED_UNKNOWN`. Independent review, target evidence, TPM/TBS, key qualification, participant/provider admission, Gate 2 and P2-B remain blocked. Baseline is `a4ca8d37fb10d8ed5c063b77d0267d976ff9c250`. PR #34 remains open, draft and unmerged.

## Coordinated state Batch 3 audit — 2026-11-07

`COORDINATED_STATE_BATCH3_CORRECTED_CANDIDATE`: the local audit found five material correctness gaps, now locally corrected with regressions. All 34 review schedules and the documented Python 3.12 suites passed; see the [Batch 3 audit](../phase2/p2-f-coordinated-state-batch3-2026-11-07.md). Batch 3 remains pending independent acceptance. Next: refresh hosted Graphify for this candidate and obtain independent Work review. Private-pilot, Gate 2 and P2-B statuses remain blocked.

## Coordinated state Batch 2 — 2026-11-07

`COORDINATED_STATE_BATCH2_PUBLICATION_COMPLETE` at implementation `1d1fc967d8e40bf20beba2493f632493b50c9c49`, starting from `c0cd31d022fd044de6b5d36cd0627b1396ce6d0f`. The [Batch 2 record](../phase2/p2-f-coordinated-state-batch2-2026-11-07.md) details admission ownership, epoch/audit accounting, the final check-to-append interval and synthetic limits. State-ordering review 3/3 and prior review schedules 31/31 pass; coordinator 15, authority 81, publication 23, durable 54 and P2 267 pass. Full research: 431 run, 430 passed and one Windows symlink skip. Final pushed head is the documentation commit containing this entry; exact-head CI was unavailable at documentation time. Next: separately perform Batch 3 review-fixture migration/cleanup and independent re-review. No authority/custody pass or private-pilot readiness is claimed; prior journal durability pass, human/provider admission NO, `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

## Coordinated state Batch 1 — 2026-11-07

`COORDINATED_STATE_BATCH1_FOUNDATION_COMPLETE` at implementation `46981678123dd709c7cb8641ade808dcc2a7d370`, starting from `a6b0ecdc510efcb8ba6a51b8b1489e401a6d5ee8`. The [Batch 1 record](../phase2/p2-f-coordinated-state-batch1-2026-11-07.md) inventories shared-lock/epoch mutation ownership, copy-out protections, uncovered broker paths, fixture migration and exact results. Coordinator 15 and authority 81 passed; prior review groups 6/8/11/6 passed; durable 54 and P2 244 passed; full research 408 run, 407 passed and one Windows symlink skip. The two state-ordering reproductions remain failing; `Broker.read` epoch publication integration is deliberately deferred. Final pushed head is the documentation commit containing this entry; CI unavailable at documentation time. Next: separately implement Batch 2, including broker/admission ownership, internal audit epoch handling and callback-free publication. No authority/custody pass or private-pilot readiness is claimed; prior journal durability pass and blocked Gate 2/P2-B statuses remain unchanged.

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

## Private-pilot contract and review boundary — 2026-11-07

[Authority and custody contract](../phase2/p2-f-private-pilot-authority-custody-contract.md): `PRIVATE_PILOT_CONTRACT_NEEDS_CORRECTION`, because this proposal has not received independent security review and the concrete independent nonrollback authority, bootstrap/recovery, revocation fence, key custody and operator environment remain unselected and unverified. Reviewed branch head `ee3f69553ad932631a24db2a065722a140c07837`; hosted graph index `8012c93d55a26ff60a85730a42aca51da561d945` differs only in handoff/mirror infrastructure, with relevant implementation unchanged.

Decisions: default deny; current authority outside the data restore domain; no offline cached authorization; authenticated account/source/purpose/partition with pre-decryption authorization plus output-time fence; explicit crash/withdrawal/restore/deletion and descendant rules; one candidate direct Garmin device FIT original, pending separate source, firmware, parser, terms and independent-reference validation. Existing TCX command is not a FIT adapter. The contract lists the adversarial synthetic acceptance matrix and independent deployment drill. It does not authorize any real input.

Next: independent security/custody review and choose/prove the actual authority anchor, currentness, recovery, clock and fence; then implement only a synthetic authority/custody harness on the research branch, with no private data or production integration. Synthetic durability remains passed within its prior scope; private pilot remains not ready; `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

## Independent synthetic durability clearance — 2026-11-07

Independent Work review has returned:

`PASS_FOR_SYNTHETIC_DURABILITY`

Scope of that pass:

- synthetic F2.2 durable journal;
- SQLite durability boundary;
- shared process-local `SyntheticAuthority`;
- projector 3 duplicate-membership correction;
- projector 4 future-data quarantine;
- pinned replay behavior;
- current-validity linearization barrier;
- thread/process contention and re-entrant mutation behavior.

Reviewed implementation:

`e716f338ac52fe727e2a7a3e7d9f1495875170bd`

Live branch head at independent review:

`6255cef86aef3d7899da9bfcf49095fb39a266f7`

The reviewer independently reproduced the old race on `9f23075...` and verified that the new SQLite writer reservation plus shared authority guard closes the tested final-window race. The protected consistency check is accepted as the synthetic linearization point. Returned projections can become stale after that point, so callers must use `assert_current` before later reuse.

Independent verification:

- durable store: **54 passed**
- existing F2.2: **16 passed**
- P2 suite: **148 passed**
- full research suite: **312 run — 311 passed, 1 Windows symlink skip**
- new synthetic correctness defects found: **none**

Exact-head CI:

`42257993818`

Status at last verified check: **QUEUED**. Implementation-head run `42257251591` was cancelled, not passed.

Current status:

- P2-F synthetic feasibility: **PASS**
- P2-F synthetic durability: **PASS_FOR_SYNTHETIC_DURABILITY**
- private-pilot readiness: **NOT READY**
- Gate 2: `P2_F_GATE_2_NOT_READY`
- P2-B: `P2_B_PROTOCOL_READY_DATA_NOT_READY`
- human/provider data admission: **NO**

Important boundary:

The current authority is still an in-memory, process-local test double and the synthetic store/backups are plaintext. This synthetic pass does **not** authorize real athlete/provider data.

### Exact next phase

Move from synthetic durability into **private-pilot custody and authority preparation**.

The next Work task should define and independently review the minimum private-pilot requirements before Codex implements them. That review should cover:

- authenticated principal/account authority;
- persistent/nonrollback grant and withdrawal state;
- encrypted local custody;
- key management/separation;
- suppression before read/decrypt/exposure;
- backup/restore semantics;
- deletion and descendant-copy handling;
- restore after withdrawal;
- audit evidence;
- lawful admission boundary;
- one pinned device/firmware/export family;
- independent real-device reconciliation requirements.

Do not ingest human/provider data during this design stage.

After Work freezes a bounded private-pilot contract, use Codex for implementation, then return it to Work for adversarial review before any private data is admitted.

### Graphify note

During the clearance review, Graphify's available index was older than the durable-store implementation, so the reviewer correctly verified the relevant live GitHub files/tests directly. Continue to use Graphify first when current enough, but fall back to targeted live-file verification when its index lags the branch.

## Current live state — Codex race fix awaiting independent review — 2026-11-07

Latest implementation head:

`e716f338ac52fe727e2a7a3e7d9f1495875170bd`

Starting branch head for this implementation pass:

`7eca72cb48400e5e2334955b63415c9dbe386ca8`

Codex reports the known current-validity concurrent-writer race as fixed. The synthetic implementation now reserves SQLite's writer slot and acquires the shared synthetic-authority guard before the final journal/authority consistency check. Contention or re-entrant mutation fails closed without retry. The former expected-failure regression is now a normal passing test.

Local validation at `e716f338...`:

- durable store: **54 passed**
- existing F2.2: **16 passed**
- P2 suite: **148 passed**
- full research suite: **311 passed, 1 skipped**
- expected failures: **0**
- skipped test: Windows symlink limitation

Exact-head remote CI:

`42257251591`

Status at last verification: **QUEUED**. Do not count it as a pass until GitHub reports success.

Current verdict:

- P2-F synthetic feasibility: supported for tested synthetic cases
- synthetic durability: **PENDING INDEPENDENT WORK REVIEW**
- private-pilot readiness: **NOT READY**
- Gate 2: `P2_F_GATE_2_NOT_READY`
- P2-B: `P2_B_PROTOCOL_READY_DATA_NOT_READY`
- human/provider data: **DO NOT ADMIT**

The in-memory/process-local authority guard is only a synthetic test mechanism. It is not authenticated, durable, cross-system or nonrollback authority.

### Exact next action

Return `e716f338...` to the same independent Work review session that reproduced the race.

Work should independently reproduce the old race, attack the new linearization/consistency barrier with timing variations, check thread/process writers, simultaneous authority mutations, deadlock/livelock, fail-closed behavior, historical replay isolation, metadata/digest correctness and reopened-journal behavior, then attempt a final verdict:

`PASS_FOR_SYNTHETIC_DURABILITY`

Do not begin private-pilot infrastructure, human data intake, device validation, P2-B repeatability, held-out evaluation, Engine 1.5 changes or Trackside integration yet.

### Repository-navigation preference

When Work/Codex needs repository navigation, use **Graphify first** to narrow call paths, dependencies, change impact and relevant tests in `Isshhin/stridelab-development`. Then verify only the exact live files/functions/tests identified by Graphify before modifying or judging code. Avoid broad repository rereads unless Graphify and live code disagree.

## Synthetic current-validity barrier continuation — 2026-11-07

The earlier current-validity final-check race is addressed in the continuation: a SQLite writer reservation and the guarded synthetic authority jointly protect the final validation point; contention or reentrant changes fail closed without retry. The prior expected-failure regression is now a normal test, with additional thread/process and authority mutation cases. See the [synthetic durable-store boundary](../phase2/p2-f-durable-synthetic-boundary.md) for exact semantics and limitations. The continuation started from `7eca72cb48400e5e2334955b63415c9dbe386ca8`. Its final local checks ran 54 durable-store tests, 16 F2.2 tests, 148 P2 tests, and 312 full research tests (311 passed, one Windows symlink skip); no expected failures remained. Earlier blocker descriptions below are historical. Independent Work review remains required; do not claim a final synthetic-durability pass. This is not private-pilot infrastructure. `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.


> **2026-11-07 v7 handoff — current:** v7 correction records are additive at `f87395120afb383fec503716a5b13896feb4181f` plus the uncommitted working changes. Authority validation, candidate locking, persistent state, bootstrap/lifecycle, policy bridge, coordinator/gateway epochs, one-shot send and withdrawal semantics are documented for independent review. The vector set is not complete and the binary identity is blocked; therefore `P2F_CUSTODY_AUTHORITY_V7_CORRECTIONS_INCOMPLETE`. No execution or target access occurred. Keep PR #34 open, draft and unmerged. Exact next permitted task is to complete the vector set and obtain a new independent review.

Date: 2026-11-07 (Asia/Manila)

Repository: `Isshhin/stridelab-development`  
Branch: `research/runna-garmin-coaching-20261102`  
PR: #34 — keep **open, draft, and unmerged**  
Frozen Session G architecture baseline: `26a8331e642c888dd73acb94755dded063baab8d`

This handoff documents the Engine 2.x state through reviewed engine/research head:

`9f23075e5522e0d47e5f01325c2d62995863b9a4`

Documentation-only commits newer than that head do not change the engine/research verdict below.

## How to continue

Before any new work:

1. Verify the live remote branch head and preserve anything newer than the head above.
2. Confirm PR #34 remains open, draft, and unmerged.
3. Inspect the latest exact-head research workflow.
4. Read this handoff plus `engine2x-current-handoff.md`.
5. Continue from the current blocker unless a newer commit has already resolved it.

User workflow preference:
- **Work** = independent/adversarial review, research contracts, gate decisions.
- **Codex** = bounded implementation and regression tests.
- Default economical model choice: **GPT-6.1 Terra, Medium**.
- After Codex implements an important fix, return it to Work for independent adversarial review.
- When reporting a Work/Codex result to the user, explain it in layman terms and clearly classify it as **GOOD**, **BAD**, or **MIXED**.

## Engine 2.x roadmap position

Engine 2.x is currently near the end of the **P2-F synthetic data-foundation stage**.

Roadmap order:

1. Phase 1 research / architecture — mostly complete and frozen.
2. P2-F trustworthy training history / provenance / replay — **current stage**.
3. Private custody + authenticated/nonrollback authority.
4. Real-device development pilot and independent reconciliation.
5. P2-F Gate 2.
6. P2-B aerobic-efficiency measurement repeatability.
7. P2-A athlete-state estimation.
8. P2-C progression / deload / re-entry.
9. P2-D adaptive prescription.
10. P2-E cross-discipline coordination.
11. Executable Engine 2.x candidate.
12. Shadow comparison against Engine 1.5.
13. Prospective athlete validation.
14. Production Engine 2.x candidate, then Trackside integration / dogfood.

Do not skip directly to later phases while P2-F gates remain blocked.

## Why P2-F matters

P2-F exists so Engine 2.x can reliably reconstruct:

- what was planned;
- what actually happened;
- where each record came from;
- whether records duplicate one another;
- when information became available;
- whether it was later corrected or withdrawn;
- which account owns it;
- what the engine legitimately knew at a historical decision time;
- whether that historical decision can be reproduced later.

This is the evidence foundation for later sport-science interpretation and adaptive coaching. A more sophisticated coaching algorithm is not trustworthy if the history underneath it is wrong.

## Important milestones from this session

### Foundation unlock

`080581e19aab79b385cacebc1499a6688c11e3d8`

P2-F F2.2 foundation/handoff. Established the canonical Training Exposure Ledger, provenance, account ownership, decision-time/current-validity replay, missing-vs-zero semantics, alias/conflict handling, correction/withdrawal invalidation, and minimum trace contract.

### First durable synthetic F2.2 store

`92e2ed968fa0418bb6738d402e31fd4465a069b6`

Added the research-only durable journal and durable-store tests.

### Projector 3 — duplicate membership correction

`25937a1370c43603c663b8ec32bcbaca9e005fd9`

Independent review found that two distinct link events joining the same activity and opportunity could expose duplicate activity membership such as `['a1', 'a1']`. Overall exposure count stayed one, but downstream consumers could still double count.

Projector 3 corrected membership while preserving both link identities. Projectors 1 and 2 intentionally retain historical behavior.

### Projector 4 — future activity-end protection

`44b01f030204877201a7f6c7478dd239d6d5a045`

Independent review found that an activity available at minute 20 could expose an end time at minute 50 to a decision view at minute 25.

Projector 4 quarantines that future-data case. Earlier projector behavior remains historically available.

### Pinned replay + current-validity metadata hardening

`5aaeadb8fc305392da404bf4e485be6e7c246b69`

Projectors 3 and 4 were moved to separately pinned replay logic instead of depending directly on mutable `FoundationLedger._project`.

Projector 4 current-validity results were expanded to record:
- explicit as-of time;
- authority instance and epoch;
- authority digest;
- suppression digest;
- invalidation digest and IDs;
- projector and schema versions;
- event/journal digests;
- watermark;
- exclusions/reasons.

A pre-projector-3 journal was reopened and replayed without rewriting its events.

### Latest independent review

`9f23075e5522e0d47e5f01325c2d62995863b9a4`

Work independently verified much of the pinned replay behavior, found and corrected another mutable-reference validation-helper dependency, and reproduced a new current-validity concurrency race.

This is the **current unresolved synthetic blocker**.

## Confirmed defects and disposition

1. **Duplicate activity membership across multiple links**
   - Found by independent Work review.
   - Fixed in projector 3.
   - Historical projectors 1/2 retain old behavior.

2. **Future activity-end leakage**
   - Found by independent Work review.
   - Fixed/quarantined in projector 4.

3. **Mutable replay dependencies**
   - Main mutable projector dependency isolated/pinned.
   - Additional mutable validation-helper dependency found by independent review and corrected.
   - Pinned replay is substantially stronger but remains subject to the current concurrency blocker below.

4. **Concurrent current-validity journal race**
   - **CURRENT UNRESOLVED BLOCKER.**
   - Reproduced by independent Work review.
   - Documented with an expected-failure regression.

## Current blocker in layman terms

Current-validity replay can race with another journal writer.

Example:

1. Engine starts calculating current state from journal generation 100.
2. It validates authority/withdrawal state.
3. Another writer commits journal generation 101 during the final validation window.
4. The engine can still return the result calculated from generation 100 as “current”.

The result is coherent for generation 100, but it is no longer truly current.

The required correction is a consistency barrier that proves the journal state and relevant authority state did not change during the final validation window. If either changes, replay must fail closed or retry according to the frozen contract.

## Current verification at reviewed head `9f23075e...`

- Durable-store tests: **42 run — 41 passed, 1 expected failure**
- Existing F2.2 tests: **16 passed**
- P2 validation suite: **136 run — 135 passed, 1 expected failure**
- Full research suite: **300 run — 298 passed, 1 skipped, 1 expected failure**
- Expected failure: the known concurrent current-validity race.
- Skipped test: Windows symlink limitation.

Exact-head workflow recorded during the review:

`42253476323`

It was **queued** at the last verified check. Do not claim it passed unless a later check confirms success.

## Current status labels

- P2-F synthetic feasibility: **supported for tested synthetic cases**
- Synthetic durability: **BLOCKED**
- Current synthetic blocker: **concurrent current-validity journal race**
- Private-pilot readiness: **NOT READY**
- Gate 2: `P2_F_GATE_2_NOT_READY`
- P2-B: `P2_B_PROTOCOL_READY_DATA_NOT_READY`
- Human/provider data admission: **NO**

## Private-pilot blockers

Even after synthetic durability clears, the synthetic journal is not a human-data/private-pilot system.

Still required before private use:
- purpose-specific lawful admission;
- authenticated principal identity;
- persistent/nonrollback scoped authority;
- durable withdrawal/suppression;
- encrypted custody;
- separately held keys / key management;
- verified backup/restore controls;
- deletion / descendant-copy handling;
- independent adverse restore drill;
- pinned device / firmware / export path;
- independent real-device reconciliation.

The current plaintext synthetic store/backups and test authority must not contain human or provider data.

## Exact next action

Return to **Codex**.

Codex must first verify/sync to the live remote branch head and preserve everything newer than `9f23075e5522e0d47e5f01325c2d62995863b9a4`.

The task is narrowly scoped:

**Fix the concurrent current-validity journal race.**

The existing expected-failure regression must become a normal passing regression.

Conceptually the implementation needs to establish a final consistency barrier:

1. establish the journal generation/snapshot;
2. establish the authority snapshot;
3. calculate/replay;
4. before exposure, atomically or equivalently prove the journal generation has not changed;
5. prove the relevant authority state has not changed;
6. expose only if both still match;
7. otherwise fail closed or retry according to the frozen contract.

Do not broaden this correction into private-pilot infrastructure.

After Codex commits and pushes the fix, return the new head to the **same independent Work review session**. Work should reproduce the old race, try timing variations, check for deadlock/stale-read regressions, rerun durable/F2.2/P2/full suites, and attempt a final synthetic-durability verdict.

Desired verdict:

`PASS_FOR_SYNTHETIC_DURABILITY`

If Work finds another concrete synthetic defect, repeat the Codex → Work loop.

## Do not move to these yet

Do not start:
- human/provider data intake;
- Garmin/provider credentials for this store;
- real-device private pilot;
- P2-B repeatability evaluation;
- held-out outcome access;
- production Engine 2.x;
- Engine 1.5 behavior changes;
- Trackside Engine 2.x integration.

Engine 1.5 remains the dependable production/control/fallback engine.

## Scientific trust boundary

Current P2-F work is about **engineering trustworthiness of training history**, not yet sport-science validity.

Current status:
- training-history engineering: increasingly strong under synthetic/adversarial testing;
- real-device measurement validity: not established;
- athlete-state estimation: not established;
- adaptive coaching validity: not established;
- coaching effectiveness: not established.

The purpose of P2-F is to ensure later sport-science intelligence receives trustworthy, reproducible evidence.

## Competitive framing

StrideLab is substantially closer to Runna/Garmin on **initial plan construction** than on **continuous adaptive coaching**.

The larger remaining competitive gap is the feedback loop:

training history  
→ reliable measurements  
→ athlete state  
→ uncertainty  
→ progression/recovery decision  
→ adaptive prescription  
→ cross-discipline coordination  
→ observed outcome  
→ longitudinal learning

That work follows the P2-F and P2-B evidence gates.
> **2026-11-07 v6 custody update — current:** The additive [v6 correction proposal](../phase2/p2-f-custody-authority-correction-protocol-v6-2026-11-07.md) and [preflight v5 artifact specification](../phase2/p2-f-custody-authority-readonly-preflight-plan-v5-2026-11-07.md) are ready for a new independent review: `P2F_CUSTODY_AUTHORITY_V6_READY_FOR_INDEPENDENT_REVIEW`; self-decisions are `COORDINATOR_WITNESS_FINALITY_READY_FOR_INDEPENDENT_REVIEW`, `WITNESS_LIFECYCLE_BOOTSTRAP_READY_FOR_INDEPENDENT_REVIEW`, `WITHDRAWAL_OUTPUT_MODEL_READY_FOR_INDEPENDENT_REVIEW`, `CANONICAL_AUTHORITY_SCHEMA_READY_FOR_INDEPENDENT_REVIEW`, and `READONLY_PREFLIGHT_ARTIFACT_SPEC_READY_FOR_INDEPENDENT_REVIEW`. V6 is documentation-only: no implementation, preflight execution, target access, TPM/TBS, keys, private data, integrated qualification, Gate 2 PASS or P2-B activation. Starting head supplied: `c641351200e8fa7549cd3065987c6ddf65302589`; live checkout: `ab572a78da5dbeb3aa26075a43454afdb8f9cc`; exact-head research-validation run `42362271819` was queued at handoff. PR #34 remains open, draft and unmerged. Next: obtain a new independent review; do not execute the preflight.

> **2026-11-07 v6 publication refresh — current:** Published repaired head `68321ff67c5686b2b10e0d99668f345995dad251` is a direct descendant of preserved v5 review base `c641351200e8fa7549cd3065987c6ddf65302589`. Exact-head research validation `42363421567` is QUEUED; Graphify mirror check `42363418320` is FAILED. PR #34 remains open, draft and unmerged. No research, preflight execution, target access, implementation, or private-data work is authorized; next is independent review of v6.


> **2026-11-07 independent v6 review — current:** `P2F_CUSTODY_AUTHORITY_V6_DESIGN_NEEDS_CORRECTION` at reviewed head `6bd4abfd55ac85a0d2528892f204427fa113ccac`. See [independent v6 review](../phase2/p2-f-custody-authority-v6-independent-security-review-2026-11-07.md): finality validation, witness bootstrap/lifecycle, output quiescence, complete canonical schema, and the actual frozen preflight artifact remain deficient. Next: bounded v7 design correction and frozen artifact packet, then independent review; no preflight execution or implementation is authorized. PR #34 remains open, draft, unmerged; no target access, keys, private data, Gate 2 PASS, or P2-B.


## v7 preflight workflow repair — 2026-11-07

Commit `2e1f4fcb35c9f4e479ff4045e8b5d18fafdb845e7` repairs the research-only manual workflow by replacing inline `with` mappings with block mappings for checkout and MSVC setup; it remains `workflow_dispatch` only on `windows-2025`. Run `42368016764` reached a real build job but was blocked before startup by account billing/spending-limit policy. No static binary evidence or artifact exists; the collector was never executed. PR #34 remains open, draft, and unmerged. Status remains `P2F_CUSTODY_AUTHORITY_V7_CORRECTIONS_INCOMPLETE`.