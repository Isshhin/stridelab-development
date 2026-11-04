# StrideLab Engine 2.x Session G - final synthesis prompt

Date: 2026-11-04 (Asia/Manila)

Repository: `Isshhin/stridelab-development`  
Research branch: `research/runna-garmin-coaching-20261102`  
Research PR: #34  
Last reviewed pre-synthesis checkpoint: `faf98c5595598d41d643a1dfff39145e3f01791e`

Do not assume the checkpoint is still current.

## Before synthesis

1. Verify the current remote branch head.
2. Verify PR #34 remains open, draft and unmerged.
3. Inspect commits after `faf98c5`.
4. Preserve all newer research work.
5. Read:
   - `docs/research/2026-11-04-engine2x-parallel-research-program.md`
   - `docs/research/2026-11-04-engine2x-pre-synthesis-review.md`
   - `docs/research/2026-11-04-focused-engine2-evidence-and-experiment-agenda.md`
   - `docs/research/2026-11-04-session-a-competitive-coaching-audit.md`
   - `docs/research/2026-11-04-session-b-athlete-state-and-training-response.md`
   - `docs/research/2026-11-04-session-c-progression-deload-and-re-entry.md`
   - `docs/research/2026-11-04-session-d-aerobic-efficiency-and-environment.md`
   - `docs/research/2026-11-04-session-e-adaptive-prescription.md`
   - `docs/research/2026-11-04-engine2x-session-f-real-data-and-validation.md`
   - `docs/research/2026-11-04-session-h-competing-coaching-frameworks.md`
   - relevant prior Batch 1-8 research.
6. Treat the pre-synthesis review as a required correction layer over the specialist reports.
7. Do not restart broad research unless a specific unresolved contradiction blocks an architectural or experimental decision.

# Assignment: SESSION G - ENGINE 2.x SYNTHESIS AND ARCHITECTURE

Turn the completed discovery research into a coherent, testable Engine 2.x architecture and Phase 2 experimental roadmap.

This is not another general literature review and is not permission to implement production coaching logic yet.

Determine:
- what Engine 2.x should represent internally;
- what observations it should trust;
- what decisions it should make;
- when it should abstain;
- which rules are evidence-supported;
- which rules remain hypotheses;
- what requires real-data calibration;
- what moves into Phase 2 experiments;
- what remains research-only;
- what would qualify StrideLab as one tier behind mainstream;
- what would qualify it as a credible on-par candidate.

## Core evidence principles

Preserve these evidence classes:
- SCIENCE
- VENDOR
- OBSERVATION
- ATHLETE
- SYNTHETIC
- HYPOTHESIS

Do not collapse them into one confidence score.

Do not promote a rule because a famous researcher recommends it, a commercial app uses it, it sounds plausible, it is popular, it passes synthetic tests, one study is statistically significant, or repeated papers share the same unvalidated assumption.

Preserve contradictory and null evidence. If uncertainty remains unresolved, document it instead of forcing a conclusion.

## Required correction 1: two-axis maturity model

For every major coaching capability evaluate separately:

### Capability maturity
- ABSENT
- RESEARCH_PROTOTYPE
- IMPLEMENTED_CANDIDATE
- INTEGRATED

### Evidence maturity
- HYPOTHESIS
- SYNTHETIC_ONLY
- OBSERVATIONAL
- PROSPECTIVELY_VALIDATED
- EFFECTIVENESS_VALIDATED

Also retain competitive classification:
- BEHIND_SIGNIFICANTLY
- BEHIND_SLIGHTLY
- NEAR_PARITY
- POTENTIAL_ADVANTAGE
- INSUFFICIENT_EVIDENCE

Do not call StrideLab behind only because evidence is immature when a mechanism already exists. Do not call it near parity merely because a feature exists without validated coaching value.

## Required correction 2: explicit abstention state

The architecture must include a non-action state such as:

`ABSTAIN_NEED_MORE_INFORMATION`

or

`HOLD_AUTOMATION`

This is different from KEEP.

KEEP means the current prescription appears appropriate with enough evidence to leave it unchanged.

ABSTAIN means the engine lacks reliable evidence to justify a coaching change.

Examples:
- conflicting HRV and performance;
- unknown interruption reason;
- unmatched environment;
- insufficient matched-HR observations;
- uncertain sensor provenance;
- unknown illness status;
- incomplete plan-to-performed linkage;
- device/protocol change without a bridge;
- contradictory athlete-state evidence.

When abstaining:
- do not manufacture a recommendation;
- preserve the approved plan unless safety/hard constraints require action;
- expose the uncertainty;
- identify information that could resolve it;
- request extra input only when useful and low-burden;
- re-evaluate when evidence arrives.

Unknown must not silently become KEEP, REST or REDUCE.

## Required correction 3: multidimensional athlete state

Do not reduce the athlete to one readiness label.

Evaluate candidate dimensions such as:
- fitness trend;
- acute fatigue;
- chronic adaptation trend;
- recovery trajectory;
- aerobic-efficiency trend;
- quality/threshold tolerance;
- long-run tolerance;
- fast-running exposure;
- recent running exposure;
- recent strength exposure;
- consistency/adherence;
- interruption/re-entry status;
- environmental stress;
- cross-discipline strain;
- local soreness;
- subjective fatigue;
- sleep context;
- HRV context;
- resting-HR context;
- illness/restriction context;
- data coverage;
- measurement quality;
- uncertainty/confidence.

Do not assume every field deserves production use.

For each state variable define:
- observable inputs;
- measured versus latent;
- time horizon;
- discipline relevance;
- baseline requirement;
- missing-data behavior;
- conflict behavior;
- evidence class;
- applicability;
- confidence;
- falsifier;
- required Phase 2 experiment.

Several dimensions may be active simultaneously. The decision layer may still emit one primary reviewed action.

## Required correction 4: practical promotion gates

Statistical significance alone is not sufficient.

Where applicable require:
- practically meaningful effect size;
- calibration;
- prediction error;
- interval coverage;
- false escalation rate;
- false restriction rate;
- unnecessary edits;
- missed necessary interventions;
- abstention rate;
- useful case coverage;
- athlete override/rejection rate;
- athlete burden;
- missing-data robustness;
- forward-time validation;
- athlete-held-out validation;
- device robustness;
- climate robustness;
- subgroup behavior;
- sensitivity to modeling choices;
- structural safety invariants;
- rollback/review compatibility.

Prefer the simpler model when added complexity does not yield meaningful benefit.

Use the right validation question: superiority, noninferiority, equivalence, calibration, construct validity, prediction or intervention effectiveness.

## Required contradiction register

At minimum preserve these unresolved areas:

### HRV/readiness
Contextual value is plausible. Standalone control is not established. Require incremental value beyond simple history + check-in before independent workout modification.

### Matched-HR aerobic efficiency
Promising measurement candidate. Repeatability and context dependence unresolved. Descriptive/trend use first.

### Maffetone 180 Formula
Comparable-HR monitoring is testable. The 180 Formula is not established as an individualized physiological threshold. Do not automate it.

### Polarized vs pyramidal vs threshold-heavy
No universal winner. Effects depend on population, phase, classification method and outcome. Avoid one doctrine.

### Lactate / Zone 2
Consumer labels are not automatically LT1, VT1, Fatmax or lactate flux. Preserve test/protocol definitions.

### 10% rule / ACWR / session spikes
Universal 10% prevention unsupported. ACWR has methodological/intervention limitations. Single-session spike association is stronger but observational. Use as exposure descriptors, not personal injury probabilities.

### Deload / taper / re-entry
These are distinct problems and must not share one reduction rule.

### Concurrent training
Interference is outcome/timing/modality/context dependent. Avoid blanket running-strength penalties.

### Active recovery
No strong basis for mandatory active recovery over rest.

### Environment
Heat/humidity affect strain, but universal personal pace correction is not validated. Context matching first.

For every contradiction state:
- which sources disagree;
- why;
- current engine behavior;
- resolving experiment;
- interim safe behavior.

## Candidate Engine 2.x flow to evaluate

`OBSERVATIONS`
-> provenance / quality / eligibility
-> `STATE ESTIMATION`
-> multidimensional estimates + uncertainty
-> `SAFETY AND CONSTRAINT GATES`
-> restrictions, stop requests, availability, locks, data quality, interruption, exposure, cross-discipline conflicts
-> `TRAINING INTENT`
-> easy aerobic / long aerobic / threshold / VO2 / speed / race-specific / strength / calisthenics / conditioning / recovery
-> `DECISION`
-> KEEP / REDUCE_VOLUME / REDUCE_INTENSITY / SHORTEN_INTERVALS / REDUCE_REPS / SUBSTITUTE / MOVE / RECOVERY / REST / ABSTAIN_NEED_MORE_INFORMATION
-> `DOWNSTREAM WEEK REVIEW`
-> adjacent quality, long run, strength interference, locks, accumulated exposure, next week, no hidden training debt
-> `ATHLETE REVIEW`
-> what changed, why, supporting/contrary evidence, confidence, uncertainty, tradeoff, affected sessions, alternatives
-> `EXECUTION`
-> `OUTCOME`
-> actual performed dose, partial/skipped/substituted state, RPE, symptoms/context, environment, subsequent response
-> feedback into state estimation.

Refine the flow if evidence supports a better architecture.

## Core internal state

Produce a proposed Engine 2.x state schema. For each field define:
- name;
- meaning;
- type/units;
- time horizon;
- inputs;
- freshness;
- confidence;
- applicability;
- missing-data behavior;
- interactions;
- direct workout-change permission;
- validation status.

Avoid a global readiness score as the engine's center. If one exists for presentation, it must be derived from transparent components and must not replace them.

## Decision precedence

Determine evidence-based precedence. Evaluate:
1. hard safety/restriction gates;
2. explicit athlete constraints/stop requests;
3. data validity/provenance;
4. interruption/re-entry;
5. training intent;
6. recent tolerance/response;
7. environment;
8. cross-discipline interaction;
9. optional physiological/recovery signals;
10. progression opportunity;
11. user review.

Define non-overridable cases, such as favorable HRV never overriding concerning symptoms, old PRs not overriding stale long-run exposure, hot/hilly runs not automatically downgrading fitness, missing data not equaling missed training, and unknown interruption reason not automatically producing a graded return.

## Confidence and uncertainty

Avoid arbitrary 0-100 scores unless later calibrated.

Consider:
- completeness;
- recency;
- sensor quality;
- baseline stability;
- protocol consistency;
- independent-signal agreement;
- evidence maturity;
- athlete-specific sample size;
- environment comparability;
- model calibration.

Define when the engine is:
- confident enough to recommend;
- tentative/review strongly advised;
- insufficient information;
- no automated decision.

## Training response

Synthesize separately:
- easy/aerobic response;
- threshold response;
- interval/speed response;
- long-run durability;
- strength adaptation;
- cross-discipline tolerance;
- adherence;
- accumulated fatigue.

Separate performance improvement, execution quality, fatigue, environmental penalty and measurement noise. Completion alone is not adaptation.

## Progression/re-entry

Evaluate separately:
- weekly distance/time;
- frequency;
- longest session;
- quality minutes;
- interval duration;
- hard-session frequency;
- strength volume;
- cross-discipline exposure;
- interruption status;
- recent tolerated exposure.

Candidate decision states may include:
- PROGRESS
- MAINTAIN
- REDUCE
- DELOAD
- REBUILD_EXPOSURE
- RESTORE_LONG_RUN
- RESTORE_QUALITY
- DEFER_INTENSITY

These are not exclusive internal athlete states.

Do not encode universal 10%, universal ACWR, automatic make-up, automatic every-N-week deload, or automatic return to previous intensity without later validation.

## Aerobic-efficiency role

Classify each as descriptive only, state input, progression input, decision modifier or research-only:
- speed at comparable HR;
- HR at comparable speed;
- cardiac drift;
- pace/HR decoupling;
- durability;
- threshold trends.

Require repeatability before promotion.

Keep measurement feasibility, criterion agreement, prediction and coaching benefit as separate gates.

## Environment

Determine handling for:
- temperature;
- humidity;
- dew point/vapor pressure;
- solar load;
- wind;
- hills/grade;
- route direction;
- altitude/elevation gain;
- surface;
- acclimation;
- fueling/hydration.

Distinguish harder conditions from fitness decline. Prefer context before correction. Any correction model needs held-out validation, coverage, subgroup robustness, uncertainty and fallback.

## Cross-discipline model

Coordinate running, strength, calisthenics and conditioning.

Consider:
- local muscle involvement;
- eccentric demand;
- strength goal;
- explosive vs hypertrophy vs maximal strength;
- priority;
- order;
- spacing;
- accumulated fatigue;
- experience;
- next-session quality;
- chronic outcome.

Separate acute workout-quality interference from chronic adaptation. Exact spacing rules remain research-only unless validated.

## Real-data and validation pipeline

Map each capability to:
- data required;
- source;
- rights;
- N;
- missing fields;
- bias risks;
- validation design;
- simple baseline;
- forward-time split;
- athlete-held-out split;
- failure criteria.

Prioritize:
1. authorized direct device exports;
2. matched-HR repeatability;
3. plan-to-performed linkage;
4. athlete-state prediction;
5. adaptive decision shadow evaluation;
6. controlled effectiveness evaluation.

Keep software correctness, predictive accuracy, construct validity, coaching effectiveness and safety association separate.

## Phase 2 experimental program

Design and rank the post-G tracks. Likely candidates:

### P2-A Athlete-state prototype
Compare history alone vs history + subjective check-in vs history + wearable signals with ablations.

### P2-B Aerobic-efficiency repeatability
Test matched-HR speed, HR at comparable speed, drift/decoupling, environment matching and typical error.

### P2-C Progression/re-entry
Compare fixed progression, exposure-aware progression and reason-aware interruption logic.

### P2-D Adaptive prescription
Test KEEP, REDUCE_VOLUME, REDUCE_INTENSITY, SHORTEN_INTERVALS, REDUCE_REPS, SUBSTITUTE, MOVE, RECOVERY, REST and ABSTAIN, first in shadow mode.

### P2-E Cross-discipline interference
Test running-strength interactions with endpoint-specific logic.

### P2-F Real-data calibration
Build the authorized evaluation and held-out validation pipeline.

Modify order if synthesis supports a better dependency graph.

For every experiment specify:
- hypothesis;
- comparator;
- population/data;
- primary/secondary endpoints;
- exclusions;
- missingness;
- negative controls;
- falsifier;
- practical promotion threshold;
- stopping rule;
- result that keeps it research-only.

## Competitive parity scorecard

Do not use a vague percentage.

Evaluate:
- plan generation;
- progression;
- athlete-state understanding;
- readiness/fatigue;
- workout adaptation;
- interruption/re-entry;
- environment;
- aerobic efficiency;
- long-run adaptation;
- quality adaptation;
- strength adaptation;
- cross-discipline coaching;
- explainability;
- athlete control;
- persistence/reliability;
- real-world validation.

For each show:
- capability maturity;
- evidence maturity;
- comparator;
- competitive status;
- required experiment;
- next gate.

### One-tier-behind criteria
Require at minimum:
- multidimensional athlete-state estimation;
- response-aware progression;
- interruption/re-entry;
- adaptive workout dose;
- environment-aware interpretation;
- aerobic-efficiency trend monitoring;
- explicit uncertainty/abstention;
- deterministic safe revisions;
- user-reviewable changes;
- real-data calibration for core decisions.

### On-par candidate criteria
Also require:
- longitudinal individual adaptation;
- robust real-athlete calibration;
- missing/conflicting-signal robustness;
- mature cross-discipline coordination;
- mature strength autoregulation;
- athlete-held-out and forward-time validation;
- real-world shadow testing;
- controlled outcome evaluation for major adaptations;
- no regression of Engine 1.5 reliability.

Do not declare parity in Session G. Define the gates.

## Potential StrideLab advantages

Evaluate:
- explicit uncertainty;
- evidence provenance;
- reviewed adaptations;
- transparent explanations;
- multi-discipline coordination;
- aerobic-efficiency monitoring;
- environment/fitness separation;
- user control;
- offline reliability;
- rollback;
- conservative missing-data behavior.

Classify each as existing, prototype, hypothesis or validated. Do not claim superiority without evidence.

## Required deliverables

Create a complete Session G report containing:

1. Executive synthesis
2. Live branch/PR checkpoint
3. Discovery-session summary
4. Pre-synthesis review corrections
5. Unified contradiction register
6. Competitive capability matrix
7. Capability maturity matrix
8. Evidence maturity matrix
9. Proposed Engine 2.x internal state schema
10. Proposed uncertainty/confidence model
11. Proposed decision architecture
12. Safety/constraint precedence
13. Progression/re-entry architecture
14. Adaptive prescription architecture
15. Aerobic-efficiency architecture
16. Environmental architecture
17. Cross-discipline architecture
18. Real-data/validation architecture
19. Phase 2 experimental program
20. Production-promotion gates
21. One-tier-behind criteria
22. On-par candidate criteria
23. Potential differentiators
24. Explicit research-only areas
25. Open questions
26. Recommended implementation order after Phase 2 validation

Also create a concise machine-readable or table-based implementation candidate register with:
- feature ID;
- capability;
- source sessions;
- evidence class;
- applicability;
- confidence;
- capability maturity;
- evidence maturity;
- candidate action;
- inputs;
- output;
- safeguards;
- abstention condition;
- falsifier;
- validation experiment;
- promotion gate;
- current status.

## Stopping rule

Do not research indefinitely.

When the synthesis can state the gap, evidence, contradiction, candidate solution, uncertainty, falsifier, required experiment and promotion gate, stop researching that question and move it into Phase 2.

Only do more literature work when a contradiction prevents architecture or experiment specification.

## Boundaries

Research only.

Do not modify:
- Engine 1.5 production code;
- Trackside production UI;
- APK/release infrastructure.

Do not build or release an APK.
Do not merge PR #34.
Keep PR #34 open/draft/unmerged.
Preserve Engine 1.5 as the safe fallback.

Do not claim coaching parity, injury prevention, validated readiness, validated personalized heat correction, validated MAF prescription or validated adaptive effectiveness unless the required real-world evidence exists.

Commit Session G research artifacts only to the existing research branch and update the TickTick Engine 2.x research tracker at a verified checkpoint.

The expected outcome is a defensible Engine 2.x architecture and Phase 2 roadmap, not production code.
