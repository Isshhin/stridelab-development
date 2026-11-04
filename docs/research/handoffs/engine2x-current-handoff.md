# StrideLab Engine 2.x current coordinating handoff

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

## Exact next step for a fresh session

1. Verify live research branch and PR #34.
2. Read the Session G independent re-review and frozen Phase 2 protocol.
3. Prepare P2-F feasibility/admission under frozen baseline `26a8331`.
4. Maintain a P2-F handoff under the project continuity protocol.
5. Do not open confirmatory held-out outcomes until the P2-F Gate 2 manifest is frozen and independently reviewed.
