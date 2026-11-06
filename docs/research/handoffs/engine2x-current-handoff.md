# StrideLab Engine 2.x current coordinating handoff

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
