# StrideLab Engine 2.x current coordinating handoff

Last updated: 2026-11-04 (Asia/Manila)

Repository: `Isshhin/stridelab-development`  
Research branch: `research/runna-garmin-coaching-20261102`  
Latest verified branch head at this handoff: `e4ae7dfba8d4633cfab316903266b9e567a8ec23`  
Research PR: #34, verified open, draft, unmerged.

## Purpose of this handoff

This file preserves the current coordinating-chat context so another ChatGPT session can continue without asking the user to reconstruct the conversation.

Before continuing, verify the live branch and PR. Preserve anything newer than the checkpoint above.

## Current project priority

Engine 2.x research is the primary StrideLab workstream.

Current priority order:

1. Engine 2.x research
2. Engine 2.x validation
3. Engine 2.x implementation candidates
4. Trackside integration
5. APK / dogfood

Engine 1.5 remains feature-frozen as the safe fallback.
Trackside remains in maintenance/verification mode.
The user explicitly accepts delaying the next APK to mature Engine 2.x first.

Do not modify production Engine 1.5, Trackside production UI, or release/APK infrastructure during the current research/synthesis phase.

## Research program status

Phase 1 specialist discovery is complete and reviewed:

- Session A: Competitive coaching audit
- Session B: Athlete state and training response
- Session C: Progression, deload, and re-entry
- Session D: Aerobic efficiency and environment
- Session E: Adaptive prescription and session modification
- Session F: Real data and validation
- Session H: Researchers, coaching frameworks, and competing physiological models

All seven passed the coordinating pre-synthesis review.

Pre-synthesis review commit:
`faf98c5595598d41d643a1dfff39145e3f01791e`

Review verdict:
`READY_FOR_SESSION_G_WITH_TARGETED_SYNTHESIS_CORRECTIONS`

Review document:
`docs/research/2026-11-04-engine2x-pre-synthesis-review.md`

No specialist report requires a full rerun.

## Main findings already established

The research is considered fruitful because it narrowed the architecture and removed weak assumptions rather than merely adding features.

Key conclusions to preserve:

- Athlete state should be multidimensional, not one opaque readiness score.
- HRV, sleep, resting HR and wearable readiness are contextual signals, not standalone workout controllers.
- Pace at comparable HR is a promising field measurement but must pass within-athlete repeatability and context control before it gates progression.
- Maffetone-style comparable-HR monitoring is testable; the 180 Formula is not established as an individualized physiological threshold.
- No universal polarized, pyramidal or threshold-heavy winner has been established.
- Lactate-defined thresholds are not automatically equivalent to consumer watch Zone 2 labels.
- The universal 10% rule and ACWR should not become hard personal safety rules.
- Single-session exposure should be tracked separately from weekly volume, but observational association does not establish an injury-prevention cutoff.
- Taper, ordinary deload and interruption/re-entry are different decisions.
- Environment must be separated from fitness interpretation.
- Concurrent-training effects are endpoint and context dependent; do not use blanket running-strength interference rules.
- Adaptive prescription should preserve training intent rather than merely make sessions easier.
- Synthetic validation establishes software behavior, not coaching effectiveness.
- Authorized real-athlete data and prospective validation are now major bottlenecks.

## Required corrections for Session G

Session G must explicitly preserve these four findings from the pre-synthesis review:

### 1. Two-axis maturity model

Separate functional capability from evidence maturity.

Capability maturity:
- ABSENT
- RESEARCH_PROTOTYPE
- IMPLEMENTED_CANDIDATE
- INTEGRATED

Evidence maturity:
- HYPOTHESIS
- SYNTHETIC_ONLY
- OBSERVATIONAL
- PROSPECTIVELY_VALIDATED
- EFFECTIVENESS_VALIDATED

Do not equate feature presence with validated parity.

### 2. Explicit abstention state

The Engine 2.x architecture needs a control state such as:

`ABSTAIN_NEED_MORE_INFORMATION`

or

`HOLD_AUTOMATION`

This is different from KEEP.

Unknown or conflicting evidence must not silently become KEEP, REST or REDUCE.

### 3. Multidimensional athlete state

Internally preserve simultaneous dimensions such as fitness trend, fatigue, recovery, aerobic efficiency, quality tolerance, long-run tolerance, interruption status, environment, cross-discipline strain, data quality and uncertainty.

A decision layer may emit one primary action, but the internal athlete model must not reduce everything to one exclusive label.

### 4. Practical promotion gates

Do not promote models based only on statistical significance.

Require relevant combinations of:
- meaningful effect size
- calibration
- coverage
- false restriction/escalation rates
- abstention rate
- athlete burden
- overrides/rejections
- forward-time validation
- athlete-held-out validation
- device/climate robustness
- subgroup behavior
- structural safety invariants

Prefer simpler models when added complexity does not produce meaningful value.

## Session G status and instructions

Session G has been requested in another chat but had not committed research output when this handoff was written.

The final G prompt is preserved at:

`docs/research/2026-11-04-engine2x-session-g-final-prompt.md`

If Session G is not yet complete:
- verify the live branch;
- read the final G prompt;
- read the pre-synthesis review and all A-F + H reports;
- perform the synthesis;
- commit research artifacts only to this research branch;
- keep PR #34 open/draft/unmerged;
- update the tracker and a Session G handoff.

If Session G has completed since this handoff:
- do not rerun it blindly;
- inspect its commits and outputs;
- review G against the final prompt and pre-synthesis corrections;
- then freeze the synthesis architecture if review passes.

## What G is supposed to do

G consolidates the seven discovery streams into one defensible Engine 2.x architecture.

Expected concepts include:

`OBSERVATIONS`
-> provenance / quality / eligibility
-> `MULTIDIMENSIONAL STATE ESTIMATION`
-> uncertainty
-> `SAFETY AND CONSTRAINT GATES`
-> `TRAINING INTENT`
-> `DECISION OR ABSTENTION`
-> downstream-week review
-> athlete review
-> execution
-> outcome
-> feedback into future state estimation

G must also define:
- internal state schema;
- confidence/uncertainty behavior;
- decision precedence;
- progression/re-entry architecture;
- adaptive prescription architecture;
- aerobic-efficiency role;
- environmental role;
- cross-discipline architecture;
- real-data validation architecture;
- contradiction register;
- competitive parity scorecard;
- Phase 2 experiment dependency/order;
- production-promotion gates.

G must not claim current parity with Runna/Garmin.

## What happens after G

Do not start the main Phase 2 experiments before G is complete and reviewed.

Current expected post-G path:

1. Review G once.
2. Freeze the synthesis architecture as the Phase 2 baseline.
3. Launch the highest-priority Phase 2 experiments.

Likely first foundation tracks, subject to G ranking:

- P2-A Athlete-state prototype
- P2-B Aerobic-efficiency repeatability
- P2-F Real-data calibration/admission pipeline

Then likely:

- P2-C Progression/re-entry
- P2-D Adaptive prescription
- P2-E Cross-discipline interference

After the experimental foundation:

- research-only executable Engine 2.x candidates;
- adversarial validation;
- shadow-mode validation on authorized real data;
- prospective real-athlete validation;
- Engine 2.x production candidate;
- Trackside integration around the mature engine;
- new dogfood APK.

Do not treat the expected P2 order as final until G resolves dependencies.

## Durable continuity protocol

The user explicitly requested that session context be preserved automatically because long chats may be interrupted.

This is now a project-wide default, documented at:

`docs/research/2026-11-04-research-session-continuity-protocol.md`

The master research program and stored session prompts inherit this protocol.

Every substantial current/future research session must maintain a durable handoff at meaningful checkpoints and before ending or when interruption risk becomes material.

Preferred paths:

- `docs/research/handoffs/<session-or-track>-handoff.md`
- coordinating context: `docs/research/handoffs/engine2x-current-handoff.md`

The user should not need to request this again.

## Important source files

Read these first when resuming:

1. `docs/research/handoffs/engine2x-current-handoff.md`
2. `docs/research/2026-11-04-research-session-continuity-protocol.md`
3. `docs/research/2026-11-04-engine2x-parallel-research-program.md`
4. `docs/research/2026-11-04-engine2x-pre-synthesis-review.md`
5. `docs/research/2026-11-04-engine2x-session-g-final-prompt.md`

Then inspect the live branch for newer Session G or Phase 2 outputs.

## User working preference for this program

- Use connected GitHub directly for research documentation.
- Preserve newer work before writes.
- Do not ask the user to choose the next step when evidence supports a clear best next action.
- Keep PR #34 draft/open/unmerged during research.
- Research quality is prioritized over rushing the APK.
- The user is willing to spend substantial model/research allowance to make Engine 2.x competitive with mainstream systems.
- Researcher frameworks are hypotheses to evaluate, not doctrines to implement.
- Real data must be lawful/authorized; public visibility is not permission to scrape.
- Keep raw sensitive athlete data outside Git.

## Exact next action for a fresh coordinating session

1. Verify live head and PR #34 state.
2. Inspect commits after `e4ae7df`.
3. Determine whether Session G has committed its synthesis.
4. If not, allow/continue Session G using the preserved final prompt.
5. If yes, review G against the pre-synthesis requirements.
6. Only after G passes review, freeze the architecture and prepare the Phase 2 experiment briefs in the dependency order G recommends.

Do not ask the user to reconstruct this conversation unless the live repository contradicts this handoff.
