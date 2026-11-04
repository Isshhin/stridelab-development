# StrideLab Development Roadmap

Updated: 4 November 2026

StrideLab is currently a private developer project. The strategic priority has changed from rushing a November dogfood APK to maturing and validating Engine 2.x first.

The long-term direction is defined in [PRODUCT_VISION.md](PRODUCT_VISION.md).

Project-level current direction and recovery context is maintained in GitHub Issue #42:

https://github.com/Isshhin/stridelab-development/issues/42

## Current milestone

The current milestone is:

> Build and validate a trustworthy adaptive coaching architecture before integrating the next major coaching generation into the dogfood app.

The core question is still:

> What could stop the athlete from trusting StrideLab with tomorrow's training?

But the current answer is broader than UI reliability. Trust now depends on both software reliability and defensible coaching decisions.

## Priority order

Current priority:

1. Engine 2.x architecture and validation
2. Engine 2.x Phase 2 experiments and real-data calibration
3. research-only Engine 2.x implementation candidates
4. controlled production-candidate hardening with Engine 1.5 fallback
5. Trackside integration around the mature engine
6. end-to-end dogfood APK
7. broader whole-athlete expansion after the running foundation is credible

This supersedes the earlier roadmap that placed the dogfood APK before increased Engine 2.x research effort.

The user explicitly accepts delaying the compiled APK when that delay materially improves coaching maturity.

## Active workstreams

### Engine 2.x research and validation

Branch:

research/runna-garmin-coaching-20261102

Draft PR:

[PR #34](https://github.com/Isshhin/stridelab-development/pull/34)

Latest verified head at this roadmap update:

4b734f1b97a54e6c15ba3bf0662d456de5c68413

Phase 1 is complete:

- Session A: competitive coaching audit
- Session B: athlete state and training response
- Session C: progression, deload and re-entry
- Session D: aerobic efficiency and environment
- Session E: adaptive prescription
- Session F: real data and validation
- Session H: researchers and competing coaching frameworks
- Session G: synthesis and Engine 2.x architecture

Session G outputs:

- docs/research/2026-11-04-engine2x-session-g-synthesis.md
- docs/research/2026-11-04-engine2x-session-g-state.md
- docs/research/2026-11-04-engine2x-session-g-candidates.md
- docs/research/2026-11-04-engine2x-session-g-phase2.md

Current Engine 2.x direction:

observations → provenance/quality → multidimensional athlete state → uncertainty → safety/constraint gates → training intent → decision or abstention → downstream-week review → athlete review → execution → outcome → updated state

The athlete must not be reduced to one opaque readiness score.

The architecture includes explicit:

ABSTAIN_NEED_MORE_INFORMATION

Unknown or conflicting evidence must not silently become KEEP, REST, REDUCE, or progression.

No research result becomes production behavior merely because it is plausible, popular, used by a vendor, or passes synthetic tests.

### Engine 1.5 reliability fallback

Branch:

feature/engine15-versioned-foundation-20261103

Draft PR:

[PR #37](https://github.com/Isshhin/stridelab-development/pull/37)

Latest verified head:

66fd5cf75226d8785fe599986f3c6f2d18be7668

Status:

**FEATURE-FROZEN FOR INTEGRATION / SAFE FALLBACK**

Engine 1.5 remains the conservative control engine while Engine 2.x is researched and validated.

Preserve:

- deterministic generation
- Engine 1.0 compatibility/fallback
- bounded running progression
- conservative quality prescriptions
- mixed-discipline scheduling
- explicit coaching review
- stale-state rejection
- session locks
- availability reconciliation
- completed-history preservation
- persistence
- backup/rollback

Do not add new coaching intelligence merely to keep pace with Engine 2.x research.

Do not enable Engine 1.5 in Trackside without controlled integration verification.

### Trackside 3.0

Branch:

design/trackside3-figma-parity-no-build-20261103

Latest verified head at this roadmap update:

ebdf670766eb45d3ba1910d6f3cf6f995b572d1b

Trackside remains a secondary active reconstruction and verification track.

Its purpose is to preserve and improve the accepted Android interface while Engine 2.x matures.

Current rules:

- use the approved Figma as the current visual source of truth
- preserve accepted parity work
- validate through rendered Android evidence
- keep small-screen and larger-font usability
- do not let current UI assumptions constrain Engine 2.x architecture
- no release APK unless explicitly assigned

The current Figma remains a baseline, not a permanent design ceiling.

## Phase 2 sequence

Session G defines the new research dependency order.

### Stage 1: review and freeze Session G

Status:

**Session G complete; coordinating review next.**

Review the synthesis against:

- the final Session G prompt
- the pre-synthesis review
- the A-F + H specialist findings

If it passes, freeze its architecture, candidate register, contradiction register, state dictionary, and Phase 2 dependency graph as the research baseline.

After the freeze, change the architecture only when experiments falsify or materially weaken an assumption.

### Stage 2: P2-F data admission and linkage

Start here.

Before sophisticated adaptive models can be evaluated, establish trustworthy authorized data.

Required outcomes include:

- lawful/consented data admission
- source and parser correctness
- plan version captured before the decision
- performed dose linked to the plan
- reason for partial/missed/substituted work where available
- later outcome linkage
- duplicate/mislink protection
- account isolation
- withdrawal and provenance handling

A public activity is not automatically permission to use it.

### Stage 3: P2-B aerobic-efficiency repeatability

After the data pipeline is trustworthy, validate repeated within-athlete measurements.

Test:

- speed at comparable HR
- HR at comparable speed
- cardiac drift as a descriptive measure
- pace/HR decoupling as a descriptive measure
- environmental matching
- device/route continuity
- typical error
- eligible coverage

Repeatability does not itself authorize progression or prove training benefit.

### Stage 4: P2-A athlete-state estimation

Compare:

- training history only
- history + simple subjective check-in
- optional multimodal state model

Wearable signals such as HRV, sleep and resting HR must demonstrate useful incremental value before they can independently alter decisions.

### Stage 5: P2-C progression and re-entry

Test:

- fixed progression
- exposure-aware progression
- reason-aware interruption logic

Keep separate:

- weekly distance/time
- longest session
- hard minutes
- frequency
- threshold exposure
- speed exposure
- race recovery
- ordinary deload
- illness/injury restrictions
- nonclinical interruption

No universal 10%, ACWR, automatic make-up, or calendar deload rule.

### Stage 6: P2-D adaptive prescription

Evaluate in shadow mode first:

- KEEP
- REDUCE_VOLUME
- REDUCE_INTENSITY
- SHORTEN_INTERVALS
- REDUCE_REPS
- SUBSTITUTE
- MOVE
- RECOVERY
- REST
- ABSTAIN_NEED_MORE_INFORMATION

The action should preserve training intent when possible and re-evaluate the surrounding week.

### Stage 7: P2-E cross-discipline and strength interaction

Test endpoint-specific coordination across:

- running
- strength
- calisthenics
- conditioning

Avoid blanket rules such as "leg day before intervals is always bad."

Model local muscle demand, eccentric novelty, goal, order, spacing, priority, and subsequent session quality separately from long-term adaptation.

### Stage 8: research-only executable Engine 2.x

Build isolated candidate logic capable of replaying histories and producing shadow recommendations.

Do not silently update production Trackside plans.

### Stage 9: adversarial and real-data shadow validation

Test difficult cases such as:

- missing HR
- conflicting HRV and subjective feedback
- heat/humidity/hills
- partial workouts
- illness/restrictions
- long interruptions
- stale data
- device changes
- noisy sensors
- cross-discipline conflicts
- duplicate activities
- outdated plan versions
- user rejection of a proposal

### Stage 10: prospective athlete validation

Measure:

- calibration
- useful coverage
- false escalation
- false restriction
- unnecessary workout changes
- missed interventions
- athlete burden
- overrides/rejections
- adherence
- next-session tolerance
- independent performance outcomes

A statistically significant but practically useless improvement is not enough.

### Stage 11: Engine 2.x production candidate

Promote only components that pass their specific gates.

Preserve Engine 1.5 principles:

- determinism where practical
- explicit review
- rollback
- persistence
- completed-history integrity
- stale-state rejection
- no silent plan corruption

Keep Engine 1.5 available as fallback during controlled promotion.

### Stage 12: Trackside integration

Update Trackside around the mature Engine 2.x model.

The UI should eventually expose, where useful:

- what changed
- why
- supporting evidence
- contradictory evidence
- uncertainty
- affected sessions
- alternatives
- accept/reject
- longitudinal trends

### Stage 13: integrated dogfood APK

Only after the engine and UI are ready, compile the next major dogfood build.

The minimum trustworthy loop remains:

1. configure athlete
2. choose goals
3. choose availability
4. generate/review plan
5. edit and lock sessions
6. save/reopen reliably
7. use Today
8. perform/import activity
9. connect actual work to intended work
10. update future training safely
11. preserve state offline and across restart

The APK is intentionally later than the earlier November target if that is required to mature the coaching engine.

## Competitive milestones

### One tier behind mainstream

Require:

- reproducible goal-based generation
- multidimensional athlete-state estimation
- response-aware progression
- reason-aware interruption/re-entry
- adaptive workout dose
- environment-aware interpretation
- aerobic-efficiency trend monitoring with safeguards
- explicit uncertainty/abstention
- deterministic reviewable revisions
- real-data calibration for core decisions

### On-par candidate

Additionally require:

- longitudinal athlete-specific adaptation
- robust handling of missing/conflicting inputs
- mature strength and cross-discipline decisions
- forward-time validation
- athlete-held-out validation
- prospective shadow use
- controlled outcome evaluation for major adaptations
- no regression of Engine 1.5 reliability principles

Parity must be demonstrated, not assigned from feature count.

## Whole-athlete expansion after the running foundation

Once the running foundation is credible, deepen:

- strength
- calisthenics
- conditioning
- mobility
- plyometrics
- isometrics
- movement preparation
- recovery support

Each domain should share athlete state and adaptation logic rather than become an isolated feature module.

## Scope rules

### Build/research now

- Engine 2.x Phase 2 validation
- data provenance and linkage
- athlete-state and response experiments
- progression/re-entry experiments
- adaptive prescription experiments
- cross-discipline experiments
- branch-specific reliability/verification fixes
- Trackside parity work that does not constrain the engine

### Defer

- public launch work
- major commercial infrastructure
- broad unrelated feature expansion
- aggressive new integrations without research/usage need
- production promotion of unvalidated Engine 2.x rules
- APK work purely to satisfy the old calendar

## Continuity and roadmap maintenance

The project-level source for current direction is GitHub Issue #42:

https://github.com/Isshhin/stridelab-development/issues/42

Every substantial StrideLab session should:

1. verify the relevant live branch before acting
2. read the project handoff and branch-specific handoff
3. preserve newer work
4. update the branch handoff at meaningful checkpoints
5. update Issue #42 and these canonical docs when priorities, roadmap, branch roles, or major milestones materially change
6. leave an exact next step for a fresh session

The user should not need to request continuity documentation again in every chat.

## Historical development records

Older Phase 4D/4E documents remain historical evidence and should not be rewritten to match the current roadmap.

Relevant files include:

- [PHASE4D3A_STATUS.md](PHASE4D3A_STATUS.md)
- [PHASE4D_FINAL_STATUS.md](PHASE4D_FINAL_STATUS.md)
- [PHASE4E_STATUS.md](PHASE4E_STATUS.md)
- [PHASE4E-FIX1-CHECKPOINT.md](PHASE4E-FIX1-CHECKPOINT.md)
- [WS4_STATUS.md](WS4_STATUS.md)
- [WS5_STATUS.md](WS5_STATUS.md)

Those files document earlier checkpoints. This roadmap defines the current direction.
