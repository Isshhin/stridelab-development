# Session G synthesis handoff

4 November 2026.

## Final status

`REVIEW_PASSED / PHASE_2_BASELINE_FROZEN`

Frozen Session G baseline:

`26a8331e642c888dd73acb94755dded063baab8d`

Independent re-review:

`docs/research/2026-11-04-engine2x-session-g-independent-rereview.md`

PR #34 must remain open, draft and unmerged.

## What passed

G-R1 through G-R6 passed independent re-review:

1. all 24 state fields now have explicit implementation/validation contracts;
2. KEEP, ABSTAIN_NEED_MORE_INFORMATION, NO_AUTOMATED_DECISION and HARD_HOLD have separate semantics;
3. all 17 capabilities identify their current mechanism location and 2.x transfer path;
4. the Phase 2 architecture freeze and track-specific held-out release gates are explicit;
5. candidate differentiators are explicitly classified with no validated comparative advantage;
6. P2-A through P2-F are authoritatively mapped to E009-E013 and earlier experiment IDs.

The core architecture is frozen for Phase 2 planning. Do not redesign it casually during experiments.

## Current authorization

`P2-F_FEASIBILITY_PREPARATION_AUTHORIZED`

`CONFIRMATORY_HELD_OUT_EVALUATION_BLOCKED_PENDING_GATE_2_MANIFEST`

Architecture approval does not authorize opening sealed test outcomes.

Before confirmatory evaluation, the relevant track must freeze and independently review its Gate 2 manifest, including rights/source scope, linkage schema, comparator, endpoint, practical margin process/value, false restriction/escalation handling, coverage/burden limits, abstention accounting, subgroup/negative controls, leakage boundaries and stopping authority.

## Next track

Start P2-F admission and linkage first.

P2-F should establish:

- source rights and purpose;
- minimal data schema;
- parser/source correctness;
- plan version before decision;
- plan/revision/decision/actual/outcome linkage;
- duplicate and account-isolation behavior;
- missing/unknown codes;
- withdrawal/retention handling;
- manually reconciled pilot audit.

P2-B starts only after P2-F supplies eligible, correctly linked repeated segments.

P2-A, P2-C, P2-D and P2-E remain dependency-gated.

## Frozen architecture boundaries

Preserve:

- multidimensional athlete state;
- explicit uncertainty;
- KEEP distinct from ABSTAIN;
- NO_AUTOMATED_DECISION distinct from HARD_HOLD;
- training-intent-preserving edits;
- downstream-week review;
- athlete review/approval;
- separate capability and evidence maturity;
- practical promotion gates;
- contradiction register;
- Engine 1.5 reliability principles as fallback constraints.

A baseline change requires explicit evidence that an experiment, rights/safety issue, measurement problem or implementation impossibility materially invalidates the frozen contract.

If held-out outcomes have already been accessed, document whether the change requires a fresh untouched test set.

## Boundaries

- Engine 1.5 remains feature-frozen fallback except reliability fixes.
- Trackside production UI stays separate.
- No APK/release infrastructure changes.
- No merge of PR #34.
- Raw sensitive athlete data stays outside Git.
- No parity, readiness, injury-prevention, personalized heat, MAF-prescription or adaptive-effectiveness claim without the required evidence.

## Exact next step for a fresh session

1. Verify the live research branch and PR #34.
2. Preserve anything newer than this handoff.
3. Read the Session G re-review and frozen Phase 2 protocol.
4. Prepare P2-F feasibility/admission under the frozen `26a8331` baseline.
5. Do not open confirmatory held-out outcomes until the P2-F Gate 2 manifest is complete and independently reviewed.
