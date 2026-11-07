# Engine 2.x P2-F current session handoff

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
