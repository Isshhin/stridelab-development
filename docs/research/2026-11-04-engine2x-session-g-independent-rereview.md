# Engine 2.x Session G independent re-review

Reviewed: 2026-11-04 (Asia/Manila)

Repository: `Isshhin/stridelab-development`  
Research branch: `research/runna-garmin-coaching-20261102`  
Correction commit reviewed: `26a8331e642c888dd73acb94755dded063baab8d`  
Previous reviewed head: `2c6e663fcb286573070b347159f3faeb68b60067`  
PR #34: verified open, draft, unmerged at the correction commit.

## Verdict

**REVIEW_PASSED / PHASE_2_BASELINE_FROZEN**

The Session G architecture baseline is frozen at:

`26a8331e642c888dd73acb94755dded063baab8d`

No broad Session G rerun is required.

The freeze covers the Session G architecture, state contracts, capability/candidate register, control-state contract, contradiction handling, experiment-ID map, Phase 2 dependency graph, and baseline-readiness process.

This does **not** validate any coaching policy, physiological model, competitive advantage, or intervention effect.

## Independent checks performed

The correction commit is exactly one commit ahead of the prior reviewed head and has no divergence.

The live correction changed only the six expected research documentation files:

- parallel research program
- Session G candidate register
- Session G Phase 2 protocol
- Session G state dictionary
- Session G synthesis
- Session G handoff

All named native/research mechanisms used by G-R3 were independently verified to exist at their pinned locations.

The state contract contains exactly 24 state fields.

The control contract contains the four required distinct outcomes:

- `KEEP`
- `ABSTAIN_NEED_MORE_INFORMATION`
- `NO_AUTOMATED_DECISION`
- `HARD_HOLD`

The mechanism provenance table contains all 17 capability IDs.

The Phase 2 readiness manifest contains 10 required freeze/sign-off areas.

The differentiator table contains nine individually graded candidate differentiators and no validated comparative advantage.

The authoritative Phase 2 map contains all six tracks:

- P2-F
- P2-B
- P2-A
- P2-C
- P2-D
- P2-E

E009-E013 were independently verified as registered protocol identifiers in the focused evidence agenda. The shared use of E012/E013 is explicitly defined to avoid double-counting.

## G-R1 acceptance

**PASS**

All 24 state variables now have explicit:

- reference/baseline requirement;
- missing-data behavior;
- conflict behavior;
- evidence lineage/class;
- applicability grade;
- confidence grade;
- decision permission;
- validation state;
- Phase 2 owner;
- falsifier.

The permission levels are also defined as upper bounds:

- `CONTEXT_ONLY`
- `CANDIDATE_MODIFIER`
- `RESEARCH_ONLY`
- `BLOCK_ONLY`

No state field independently authorizes a production dose increase.

The special HIGH confidence on `illness_restriction` is correctly scoped to honoring a supplied stop/restriction rather than claiming high-confidence physiological inference.

## G-R2 acceptance

**PASS**

The four control outcomes are operationally distinct.

### KEEP

Requires affirmative support for preserving the approved intent/dose after constraints pass. It is not the default state for uncertainty.

### ABSTAIN_NEED_MORE_INFORMATION

Preserves the approved plan but withholds automated recommendation/endorsement when a decision-critical, resolvable fact is missing or conflicting.

### NO_AUTOMATED_DECISION

Covers unsupported domains, invalid lineage, or unvalidated policy. It cannot be cleared by ordinary athlete signals alone.

### HARD_HOLD

Blocks affected execution/recommendations under an explicit athlete stop or supplied restriction. Favorable physiology and elapsed time cannot automatically clear it.

The precedence and reason-code model are sufficiently explicit for later implementation and UI work.

## G-R3 acceptance

**PASS**

All 17 capabilities now identify:

- the nearest current mechanism location;
- whether Engine 2.x should REUSE, PORT, or create a NEW_IMPLEMENTATION;
- the boundary between the existing mechanism and the proposed Engine 2.x capability.

Pinned source verification confirmed the referenced native files and research modules exist, including:

- Engine1.kt
- MultiSessionEngine1.kt
- Engine15.kt
- Engine15InterferenceScheduling.kt
- Engine15Feedback.kt
- TrainingRepository.kt
- contract_model.py
- week_change_contract.py
- policy_candidates.py
- multiweek_reconciliation.py
- difference_review.py
- original_lab_admission.py
- proposal_authority.py
- sqlite_ledger.py

This resolves the earlier ambiguity between "StrideLab has a reusable foundation" and "Engine 2.x already implements the capability."

## G-R4 acceptance

**PASS**

The Phase 2 gate now separates:

### Gate 1: architecture freeze

This re-review satisfies Gate 1 for the Session G baseline.

Gate 1 authorizes preparation of the initial P2-F feasibility protocol.

It does **not** authorize opening sealed held-out outcomes.

### Gate 2: track-specific evaluation release

Before confirmatory validation, each track must freeze/sign off:

- source/admission scope;
- linkage schema;
- comparator and endpoint;
- smallest worthwhile effect process/value;
- false restriction/escalation limits where applicable;
- minimum coverage and burden;
- abstention accounting;
- subgroup and negative-control plan;
- leakage boundaries;
- stop/restart authority.

The protocol correctly allows feasibility/pilot evidence to set numerical margins rather than inventing universal thresholds in Session G.

## G-R5 acceptance

**PASS**

Nine differentiators are explicitly classified as EXISTING, PROTOTYPE, or HYPOTHESIS.

None is labeled VALIDATED as a comparative advantage.

This correctly separates an existing/prototype mechanism from demonstrated superiority over a competitor.

## G-R6 acceptance

**PASS**

The canonical P2 track IDs and legacy experiment IDs are now unambiguous.

The accepted interpretation is:

- P2-* = owning experimental track;
- E009-E013 = narrower retained protocol IDs;
- E001-E008 = earlier independent protocol IDs/competitive evidence where applicable;
- protocol ancestors do not become aliases for an entire Phase 2 track;
- shared E012/E013 observations must use the same protocol version and observation IDs, with no double-counting or implied replication.

The mapping is consistent with the focused evidence agenda, where E009-E013 are explicitly registered.

## Baseline freeze

The following files at `26a8331e642c888dd73acb94755dded063baab8d` form the frozen Session G Phase 2 architecture baseline:

- `docs/research/2026-11-04-engine2x-session-g-synthesis.md`
- `docs/research/2026-11-04-engine2x-session-g-state.md`
- `docs/research/2026-11-04-engine2x-session-g-candidates.md`
- `docs/research/2026-11-04-engine2x-session-g-phase2.md`

The baseline is frozen for Phase 2 planning.

Future changes require one of:

1. a Phase 2 result falsifies or materially weakens a baseline assumption;
2. a rights/safety/measurement problem requires correction;
3. an implementation feasibility result exposes an impossible or ambiguous contract;
4. materially stronger evidence changes the scientific interpretation.

Do not casually redesign the architecture during experiments.

Any baseline change should state:

- reason;
- affected G capability/state/track;
- evidence;
- whether held-out data have already been accessed;
- whether a fresh untouched test set is required.

## Phase 2 authorization state

Current state after this review:

`PHASE_2_BASELINE_FROZEN`

`P2-F_FEASIBILITY_PREPARATION_AUTHORIZED`

`CONFIRMATORY_HELD_OUT_EVALUATION_BLOCKED_PENDING_GATE_2_MANIFEST`

The immediate next track is P2-F admission/linkage.

P2-B begins only when P2-F provides eligible, correctly linked repeated segments.

P2-A, P2-C, P2-D and P2-E remain dependency-gated.

## What remains unvalidated

This review does not establish:

- coaching effectiveness;
- competitive parity;
- validated readiness;
- validated personalized heat correction;
- MAF prescription;
- injury prevention;
- useful matched-HR trend detection;
- useful athlete-state prediction;
- useful adaptive prescription;
- cross-discipline performance benefit.

Those remain Phase 2 or later questions.

## Boundaries

- Keep PR #34 open, draft and unmerged.
- Engine 1.5 remains feature-frozen fallback except reliability fixes.
- Trackside production UI remains separate.
- No release APK.
- Raw sensitive athlete data remains outside Git.
- Do not open confirmatory held-out outcomes until the relevant Gate 2 manifest is frozen and independently reviewed.

## Final Session G status

`REVIEW_PASSED / PHASE_2_BASELINE_FROZEN`

The next coordinating action is to prepare P2-F under the frozen `26a8331` baseline and satisfy its feasibility/admission requirements before any downstream model validation.
