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

Session G has passed independent re-review and defines the frozen Phase 2 dependency order.

### Stage 1: Session G architecture

Status:

**REVIEW_PASSED / PHASE_2_BASELINE_FROZEN**

Frozen baseline:

`26a8331e642c888dd73acb94755dded063baab8d`

Independent re-review:

https://github.com/Isshhin/stridelab-development/blob/research/runna-garmin-coaching-20261102/docs/research/2026-11-04-engine2x-session-g-independent-rereview.md

The architecture, state contracts, candidate register, control states, contradiction handling, experiment-ID map and Phase 2 dependency graph are frozen for Phase 2 planning.

Future changes require evidence, a rights/safety issue, a measurement failure, or implementation impossibility that materially invalidates the frozen contract.

### Stage 2: P2-F feasibility and admission/linkage

Current authorization:

`P2-F_FEASIBILITY_PREPARATION_AUTHORIZED`

`CONFIRMATORY_HELD_OUT_EVALUATION_BLOCKED_PENDING_GATE_2_MANIFEST`

Architecture approval does not authorize opening sealed confirmatory outcomes.

Before confirmatory evaluation, P2-F must freeze and independently review its Gate 2 manifest.

### Stage 3: P2-F data admission and linkage

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

### Stage 4: P2-B aerobic-efficiency repeatability

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

### Stage 5: P2-A athlete-state estimation

Compare:

- training history only
- history + simple subjective check-in
- optional multimodal state model

Wearable signals such as HRV, sleep and resting HR must demonstrate useful incremental value before they can independently alter decisions.

### Stage 6: P2-C progression and re-entry

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

### Stage 7: P2-D adaptive prescription

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

### Stage 8: P2-E cross-discipline and strength interaction

Test endpoint-specific coordination across:

- running
- strength
- calisthenics
- conditioning

Avoid blanket rules such as "leg day before intervals is always bad."

Model local muscle demand, eccentric novelty, goal, order, spacing, priority, and subsequent session quality separately from long-term adaptation.

### Stage 9: research-only executable Engine 2.x

Build isolated candidate logic capable of replaying histories and producing shadow recommendations.

Do not silently update production Trackside plans.

### Stage 10: adversarial and real-data shadow validation

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

### Stage 11: prospective athlete validation

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

### Stage 12: Engine 2.x production candidate

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

### Stage 13: Trackside integration

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

### Stage 14: integrated dogfood APK

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


## External training and cross-discipline adaptation rule

Engine 2.x must treat training performed outside a StrideLab-generated plan as real athlete exposure when provenance and confidence are sufficient.

The engine must distinguish at minimum:

- `PLANNED_BY_STRIDELAB`
- `PLANNED_EXTERNALLY` when the source/intent is known
- `OBSERVED_EXTERNAL_TRAINING` when the activity is detected but was not prescribed by StrideLab
- `UNPLANNED_OR_UNKNOWN_INTENT` when intent is unclear

StrideLab must never pretend it prescribed an external session.

### Core behavior

If a user consistently performs training outside the StrideLab plan, Engine 2.x should learn that pattern conservatively and account for it when planning every other discipline.

Examples:

- Consistent external running should contribute to running frequency, volume, intensity, long-run exposure, fatigue, and lower-body load when Strength, Calisthenics, Conditioning, or Recovery are planned.
- Consistent external strength training should affect lower-body fatigue, interference risk, recovery spacing, and the placement/dose of running quality sessions, long runs, hills, sprints, plyometrics, calisthenics legs, or conditioning.
- External calisthenics or conditioning should likewise contribute to relevant muscular, metabolic, and scheduling load when running or strength is prescribed.
- The same principle applies vice versa across all supported disciplines.

The engine should reason about the athlete's actual training life, not only the sessions StrideLab generated.

### Pattern recognition

Do not infer a stable routine from one isolated activity.

Repeated-pattern detection should consider, as evidence allows:

- frequency over multiple weeks;
- repeated weekday/time patterns;
- repeated discipline/session type;
- completed dose;
- similarity of intensity and duration;
- consistency of source/device;
- explicit user confirmation when available.

Until the pattern is sufficiently supported, treat it as observed exposure rather than a stable planned routine.

### Adaptation rules

External training may influence:

- total weekly load;
- discipline-specific load;
- intensity distribution;
- long-run exposure;
- quality-session spacing;
- lower-body interference;
- recovery requirements;
- double-day feasibility;
- progression ceilings;
- deload/re-entry decisions;
- schedule placement;
- confidence in future prescriptions.

However, external training must not automatically trigger aggressive reductions or progressions. The engine should adapt proportionally to evidence quality and uncertainty.

### Safeguards

Engine 2.x must preserve:

- provenance of every observed session;
- source and ownership boundaries;
- no double-counting of imported and manually logged copies of the same activity;
- no silent conversion of external training into StrideLab-planned training;
- no silent takeover or rewriting of an external plan;
- user review for major plan adaptations caused by external training;
- explicit uncertainty when intent is unknown;
- `ABSTAIN_NEED_MORE_INFORMATION` when the engine cannot safely infer the role of external training;
- rollback and versioned persistence of adaptations.

If the user follows an external running or strength program consistently, StrideLab's goal is to coordinate around it safely and intelligently rather than force the athlete into a competing StrideLab-only schedule.

This is a core whole-athlete requirement for Engine 2.x and should be designed at the state/model/contract level before implementation.



## Engine 2.x reasoning foundations and visualization-first product rule

The following foundations are approved for Engine 2.x research/specification and should be defined before broad implementation.

### Coaching modes

Engine 2.x should explicitly know its role for each athlete and, where useful, per discipline:

- `PRIMARY_COACH`: StrideLab owns plan generation and adaptation.
- `COORDINATOR`: another plan/coach may own the discipline; StrideLab coordinates around it.
- `OBSERVE_ONLY`: StrideLab analyzes state and training but does not prescribe changes.
- `HYBRID`: StrideLab owns some disciplines while external systems own others.

The engine must not silently take control of an externally owned plan.

### Universal Training Exposure Ledger

All training should enter one canonical exposure history regardless of source.

The ledger should be able to represent:

- StrideLab-prescribed sessions;
- externally planned sessions;
- imported wearable/device activities;
- manually logged workouts;
- unplanned/unknown-intent training.

Prescribed work and performed work must remain separate.

Each exposure should preserve, where available:

- source/provenance;
- discipline;
- session intent;
- date/time;
- duration;
- volume/distance/load;
- intensity;
- completion;
- relevant muscle/structural demand;
- environment;
- confidence/data quality;
- plan ownership.

No duplicate activity should be counted twice.

### First-class training intent

The engine should understand what a session is trying to accomplish, not only its raw metrics.

Examples may include:

- EASY_AEROBIC
- LONG_RUN
- THRESHOLD
- VO2
- SPRINT
- MAX_STRENGTH
- HYPERTROPHY
- POWER
- SKILL
- CONDITIONING
- RECOVERY

Intent classification must retain uncertainty when the purpose cannot be determined safely.

### Separate observations, inference, and decisions

Engine 2.x must preserve three conceptual layers:

```text
OBSERVATIONS
-> INFERRED ATHLETE STATE
-> COACHING DECISION
```

Example:

```text
Observation: HRV value is below recent baseline
Inference: possible recovery suppression, confidence MODERATE
Decision: no change yet because other signals remain stable
```

A raw metric must not directly become a coaching action without passing through the state/uncertainty layer.

### Multidimensional load vector

Do not reduce all training to one opaque readiness/load score.

Track relevant dimensions separately, such as:

- aerobic exposure;
- high-intensity running exposure;
- long-run exposure;
- lower-body muscular load;
- upper-body load;
- plyometric/power stress;
- metabolic conditioning;
- structural/impact exposure where supportable;
- cross-discipline interference.

A single aggregate score may be shown for convenience only if the underlying dimensions remain available and it is not treated as ground truth.

### Confidence-aware adaptation and adaptation budget

Every meaningful adaptation should carry confidence.

Low-confidence evidence should generally produce smaller changes, observation, or abstention.

Engine 2.x should also use an adaptation budget: do not rewrite many training variables at once unless safety requires it.

Prefer the smallest change that preserves training intent.

### User overrides as preference evidence

Repeated user overrides can become evidence of stable preference or constraint.

Examples:

- preferred gym days;
- unavailable days;
- preferred double-day structure;
- recurring external-plan sessions.

Preferences may guide planning but must never weaken hard safety constraints.

### Trend persistence before adaptation

Avoid large changes from one anomalous workout.

Require persistence, repeated evidence, or strong safety evidence before making major progression/regression decisions.

Single-session anomalies should usually be interpreted conservatively.

### State freshness and decay

Evidence should have an explicit freshness model.

Recent soreness, fatigue, HRV, sleep, missed sessions, threshold performance, or external training should not carry the same weight indefinitely.

Stale evidence should decay in influence or expire.

### Explanation trace

Every meaningful coaching change should leave a structured internal trace that records:

- decision;
- confidence;
- evidence used;
- important evidence ignored/rejected;
- training intent being protected;
- what changed;
- what did not change;
- fallback/abstention reason if applicable.

This trace supports debugging, research, user review, and future auditability.

### Engine 1.5 as a counterfactual baseline

During Engine 2.x development and shadow validation, compare important decisions against what Engine 1.5 would have done.

Engine 2.x should not receive credit merely for being more complex or different.

When it diverges from Engine 1.5, the additional evidence/reasoning should justify the change.

### Confidence calibration

Engine confidence should be evaluated for calibration, not only correctness.

A decision reported as high confidence should actually prove highly dependable across similar cases.

Overconfidence is a failure mode.

### Longitudinal athlete-specific memory

Engine 2.x should learn durable athlete-specific patterns over time where evidence supports them.

Examples:

- tolerance for quality-session frequency;
- response to heavy lower-body strength before running quality;
- usual recovery time;
- environmental sensitivity;
- long-run durability pattern;
- scheduling preferences;
- recurring external training.

Longitudinal learning must preserve provenance, uncertainty, reversibility, and user control.

### Visualization-first product rule

StrideLab should expose Engine 2.x sophistication primarily through visuals, not text-heavy screens.

The product should prefer concise visual communication such as:

- timelines;
- training calendars;
- load bars;
- trend charts;
- compact comparison views;
- badges/chips;
- icons;
- confidence indicators;
- overlays;
- cross-discipline relationship views;
- before/after plan previews;
- color/shape/state encoding;
- progressive disclosure.

Text should be:

- short;
- actionable;
- secondary to the visual hierarchy;
- used for the minimum explanation needed to make a decision understandable.

Long rationale, evidence traces, and technical detail should be available on demand rather than permanently filling the screen.

Examples:

- show a visual weekly load/interference pattern first, then let the user open “Why?” for the detailed reasoning;
- show external vs StrideLab-owned sessions visually in the calendar instead of explaining ownership in paragraphs;
- show adaptation confidence with a compact indicator and concise label, with detailed evidence behind a tap;
- show athlete-state trends as charts/visual signals rather than a wall of metric cards and text.

The post-Engine 2.x UI revamp must treat visualization-first communication as a foundation requirement.

Before production reconstruction, define:

- visual grammar for athlete state;
- visual grammar for load and interference;
- visual grammar for confidence/uncertainty;
- external-vs-StrideLab ownership states;
- adaptation/review interaction patterns;
- progressive-disclosure rules;
- accessibility equivalents for every visual encoding.

The engine may be complex internally. The user experience should remain clear, compact, visual, and understandable.

