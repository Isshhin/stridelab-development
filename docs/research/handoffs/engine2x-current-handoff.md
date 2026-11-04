# StrideLab Engine 2.x current coordinating handoff

Last updated: 2026-11-04 (Asia/Manila)

Repository: `Isshhin/stridelab-development`  
Research branch: `research/runna-garmin-coaching-20261102`  
Research PR: #34, keep open, draft and unmerged.

Before continuing, verify the live branch and preserve anything newer than this handoff.

Project-level direction is maintained in GitHub Issue #42:

https://github.com/Isshhin/stridelab-development/issues/42

## Current priority

Engine 2.x remains the primary StrideLab workstream.

Current strategic order:

1. finish Session G corrections and freeze the Phase 2 baseline
2. P2-F data admission/linkage
3. P2-B aerobic-efficiency repeatability
4. dependent P2-A/P2-C/P2-D/P2-E experiments
5. research-only Engine 2.x executable candidates
6. adversarial and real-data shadow validation
7. prospective athlete validation
8. Engine 2.x production candidate with Engine 1.5 fallback
9. Trackside integration
10. dogfood APK

The user explicitly accepts delaying the APK to improve coaching maturity.

## Phase 1 discovery

Sessions A-F + H are complete and passed pre-synthesis review.

Pre-synthesis review:

`docs/research/2026-11-04-engine2x-pre-synthesis-review.md`

## Session G

Synthesis commit:

`4b734f1b97a54e6c15ba3bf0662d456de5c68413`

Artifacts:

- `docs/research/2026-11-04-engine2x-session-g-synthesis.md`
- `docs/research/2026-11-04-engine2x-session-g-state.md`
- `docs/research/2026-11-04-engine2x-session-g-candidates.md`
- `docs/research/2026-11-04-engine2x-session-g-phase2.md`

Independent review:

`docs/research/2026-11-04-engine2x-session-g-independent-review.md`

Verdict:

`PASS_WITH_REQUIRED_CORRECTIONS_BEFORE_FREEZE`

Session G status:

`REVIEW_REQUIRED_CORRECTIONS`

Phase 2 status:

`BLOCKED_PENDING_G_CORRECTIONS`

## Required G corrections

### G-R1
Expand every state variable into an explicit implementation/validation contract including baseline, missingness, conflict behavior, evidence/applicability/confidence, direct-decision permission, validation status and Phase 2 owner.

### G-R2
Formalize separate control semantics and reason codes for:
- KEEP
- ABSTAIN_NEED_MORE_INFORMATION
- NO_AUTOMATED_DECISION
- hard hold / supplied restriction

### G-R3
Add current mechanism location to the capability scorecard so Engine 1.5/shared foundations are not mistaken for already implemented Engine 2.x behavior.

### G-R4
Add an explicit Phase 2 baseline-freeze/readiness checklist before held-out evaluation or active data collection under the research schema.

### G-R5
Classify potential differentiators explicitly as EXISTING, PROTOTYPE, HYPOTHESIS or VALIDATED.

### G-R6
Create one authoritative map between P2-A through P2-F and E009-E012/legacy experiment IDs.

No broad research rerun is required.

## Architecture that survived review

The core architecture is accepted directionally:

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
→ state update

Preserve:

- multidimensional state rather than one readiness score
- explicit abstention
- training-intent-preserving edits
- separate capability and evidence maturity
- practical promotion gates
- contradictory/null evidence
- Engine 1.5 reliability principles as fallback constraints

## Research conclusions to preserve

- HRV/sleep/RHR are contextual, not standalone workout controllers.
- Matched-HR pace is promising but not yet a progression gate.
- MAF 180 Formula is not a validated individual threshold.
- No universal polarized/pyramidal/threshold-heavy winner.
- Watch Zone 2 is not automatically LT1/VT1/Fatmax/lactate flux.
- 10% and ACWR are not universal personal safety thresholds.
- Session and weekly exposure remain separate.
- Taper, deload and re-entry are distinct decisions.
- Environment must be separated from fitness interpretation.
- Concurrent-training interference is endpoint/context dependent.
- Active recovery is optional, not mandatory.
- Synthetic correctness is not coaching-effectiveness evidence.
- Real-athlete data and prospective validation are the major next bottlenecks.

## Exact next step

1. Verify live research head and PR #34.
2. Read the G independent review.
3. Resolve G-R1 through G-R6 without broadening scope.
4. Update the G artifacts and G handoff.
5. Independently re-review the changed contracts.
6. If all corrections pass, mark:
   `REVIEW_PASSED / PHASE_2_BASELINE_FROZEN`
7. Start P2-F first.
8. Start P2-B only when the linkage/admission gate permits.
9. Keep P2-A/C/D/E blocked until their prerequisites pass.

## Boundaries

- Engine 1.5 production remains feature-frozen except reliability fixes.
- Trackside production/UI work stays on its own branch.
- No APK/release infrastructure changes.
- No merge of PR #34.
- No parity/injury-prevention/readiness/heat/MAF/adaptive-effectiveness claim without required evidence.
- Raw sensitive athlete data stays outside Git.

## Continuity

All substantial StrideLab work now follows project-wide continuity, not only research continuity.

Canonical docs branch contains:

- `docs/PROJECT_CONTINUITY.md`
- `docs/HANDOFF.md`
- `docs/ROADMAP.md`
- `docs/PRODUCT_VISION.md`

A fresh session should read Issue #42 plus the relevant branch handoff, verify live state, preserve newer work, and continue the exact next step without asking the user to reconstruct prior chats.
