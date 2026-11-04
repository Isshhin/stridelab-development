# StrideLab Engine 2.x parallel competitive research program

Date approved: 2026-11-04 (Asia/Manila)

Status: **PLANNED, research first. No production Engine 1.5, Trackside, or APK changes.**

Research branch: `research/runna-garmin-coaching-20261102`  
Research PR: #34, open, draft, unmerged  
Evidence baseline before this program: `517ae9daca458e7c2a7d9b6a36a828176800373f`

This program extends the focused evidence agenda in `docs/research/2026-11-04-focused-engine2-evidence-and-experiment-agenda.md`. It does not replace prior experiments, evidence classes, E007/E008 protocols, real-data provenance work, or unfavorable findings.

## 1. Priority decision

Engine 2.x research is the primary StrideLab workstream.

Trackside remains in maintenance and verification mode while the engine research matures. Engine 1.5 remains feature-frozen as the stable fallback. A new APK can wait until the research produces a more mature Engine 2.x integration target.

The goal is not to copy one vendor, one researcher, or one training philosophy. The goal is to determine what StrideLab must demonstrate to become first one tier behind, then genuinely competitive with mature coaching systems.

## 2. Phase 1 structure

Run seven specialist research sessions in parallel, then one synthesis session.

| Session | Focus | Main deliverable |
| --- | --- | --- |
| A | Competitive coaching audit | Capability matrix and ranked competitive gaps |
| B | Athlete state and training response | Evidence-based athlete-state model proposal |
| C | Progression, deload, and re-entry | Adaptive progression and interruption state machine |
| D | Aerobic efficiency and environment | Aerobic-efficiency and environmental-normalization specification |
| E | Adaptive prescription and session modification | Training-intent-preserving adaptation framework |
| F | Real data and validation | Lawful data-source catalogue and validation protocol |
| H | Researchers, coaching frameworks, and competing physiological models | Framework decomposition, evidence audit, contradictions, and testable hypotheses |
| G | Synthesis and Engine 2.x architecture | Unified evidence map, parity scorecard, and implementation roadmap |

Sessions A-F and H may run independently and in parallel. Session G begins after the seven discovery sessions have produced substantial results.

## 3. Shared evidence classes

Every session must keep these evidence classes separate:

| Class | Meaning | Does not establish |
| --- | --- | --- |
| SCIENCE | Published human study, review, consensus, or validated measurement evidence | Vendor implementation or universal individual benefit |
| VENDOR | Official product documentation or product policy | Independent effectiveness or proprietary internals |
| OBSERVATION | Authenticated dated app/device behavior with input/version provenance | Physiological correctness or undisclosed algorithm |
| ATHLETE | Authorized measured human records with clear provenance and outcomes | Causality by itself |
| SYNTHETIC | Constructed fixtures, simulations, software invariants | Real coaching effectiveness or injury prevention |
| HYPOTHESIS | Proposed StrideLab interpretation, model, or rule | Validated intervention |

## 4. Reliability and anti-bias rules

Every specialist session must:

1. Prefer systematic reviews, meta-analyses, randomized or prospective human evidence, strong cohort evidence, and primary vendor documentation where appropriate.
2. Search for contradictory or null evidence, not only evidence that supports the candidate idea.
3. Record study population, training status, sex distribution when reported, sample size, intervention duration, sport, setting, outcome, and important limitations before generalizing.
4. Mark evidence as full-text reviewed, abstract-only, preprint, inaccessible, vendor claim, or secondary summary.
5. Distinguish statistical association, physiological mechanism, prediction, and causal intervention. Do not silently convert one into another.
6. Distinguish group averages from individual decision rules. A population relationship is not automatically a safe per-athlete threshold.
7. Treat researcher or coach frameworks as decomposable hypotheses. Reputation, popularity, or credentials do not make every component evidence-based.
8. Separate mechanism evidence from prescription evidence. A plausible mechanism does not prove a specific training rule improves outcomes.
9. Preserve unfavorable findings, failed replications, weak effects, and unresolved contradictions.
10. State what evidence would falsify or materially weaken each proposed Engine 2.x rule.
11. Avoid false precision. Do not invent exact cutoffs, weights, correction factors, or confidence scores when the evidence supports only a direction or range.
12. For high-impact rules, seek independent corroboration from more than one research group or evidence stream when feasible.
13. Reuse existing StrideLab research instead of re-running the same question without a reason.
14. Treat synthetic tests as software validation only.
15. Keep raw sensitive athlete records out of Git and use only purpose-authorized data.
16. Keep Engine 1.5 production code, Trackside production UI, and release/APK infrastructure untouched.
17. Keep PR #34 draft and unmerged until an explicit later decision.

### Applicability grading

Each proposed finding should state applicability to StrideLab as:

- `DIRECT`: population, measurement, and decision context closely match the intended Engine 2.x use.
- `PARTIAL`: relevant but requires translation, calibration, or population caution.
- `INDIRECT`: useful mechanistically or conceptually but not suitable as a direct rule.
- `NOT_APPLICABLE`: should not drive the engine.

### Confidence grading

Each candidate conclusion should receive:

- `HIGH`: converging strong evidence with limited important contradiction for the stated use.
- `MODERATE`: useful evidence, but meaningful uncertainty or translation remains.
- `LOW`: limited, indirect, inconsistent, or mostly hypothesis-generating evidence.
- `INSUFFICIENT`: not enough evidence to implement.

No `LOW` or `INSUFFICIENT` finding becomes a production coaching rule without targeted validation.

## 5. Adjacent exploration rule

Each specialist session is encouraged to explore beyond the initial app, researcher, dataset, or paper list when the adjacent material can materially answer its assigned question.

Examples include another coaching platform with a relevant adaptation mechanism, a stronger scientific review, a researcher with competing findings, a dataset with better longitudinal fields, a measurement method used in another endurance sport, or a negative study that changes the interpretation.

When expanding scope, the session must state:

- what new source, researcher, system, or method was added,
- why it is relevant,
- whether it changes the current StrideLab gap assessment,
- whether it contradicts another source,
- and which session should inherit any cross-topic finding.

This permission is for useful adjacent evidence, not unrelated feature discovery.

## 6. Session A - Competitive coaching audit

### Core systems

- Runna
- Garmin Run Coach
- Garmin Daily Suggested Workouts as a separate Garmin system where relevant
- Kaizen / Run Kaizen
- TrainerRoad adaptive concepts
- Fitbod
- RP Hypertrophy

Other systems may be added under the adjacent exploration rule.

### Required questions

- What athlete inputs does each system use?
- What does it adapt: schedule, volume, intensity, workout type, exercise selection, recovery, pacing, or goals?
- Does it react daily, weekly, or only at plan regeneration?
- How does it handle missed training, illness, fatigue, schedule changes, and re-entry?
- How much user control and review is provided?
- Which claims are official documentation, which are direct observations, and which are marketing claims?
- Where is StrideLab clearly behind, close, or potentially stronger?

### Deliverable

Capability matrix covering at minimum:

- athlete-state estimation
- training-response evaluation
- adaptive progression
- readiness/fatigue
- environment
- interruption/re-entry
- workout-dose adaptation
- load and injury safeguards
- longitudinal personalization
- strength adaptation
- cross-discipline interference
- explainability and user control
- real-data validation

Each capability receives one status: `BEHIND_SIGNIFICANTLY`, `BEHIND_SLIGHTLY`, `NEAR_PARITY`, `POTENTIAL_ADVANTAGE`, or `INSUFFICIENT_EVIDENCE`.

## 7. Session B - Athlete state and training response

This is the highest-priority scientific session.

Research:

- fitness vs fatigue state
- acute vs chronic training effects
- HRV and individual baseline interpretation
- resting HR
- sleep duration and quality
- subjective fatigue, soreness, mood, stress, and RPE
- session RPE and internal load
- workout completion quality
- submaximal pace/HR response
- threshold response
- adherence and missed training
- conflicting signals
- missing-data behavior
- confidence and uncertainty

The goal is not one opaque readiness score. Propose an internal state representation with evidence, confidence, baseline rules, and fail-safe behavior when signals disagree or are absent.

## 8. Session C - Progression, deload, and re-entry

Research:

- weekly volume progression
- frequency progression
- long-run exposure
- intensity and quality-session progression
- single-session spikes
- deload timing
- race recovery
- short interruptions
- longer detraining
- illness
- injury return where evidence is appropriate
- return to quality and speed work
- differences between novice, intermediate, and experienced runners where supported

Explicitly test assumptions such as the 10% rule rather than adopting them as truth.

Deliver a progression and re-entry state machine with evidence grades, safety boundaries, uncertainty handling, and variables that still require calibration.

## 9. Session D - Aerobic efficiency and environment

Research:

- pace at comparable HR
- HR at comparable pace
- submaximal monitoring
- cardiac drift and decoupling
- aerobic durability
- threshold trends
- required observation windows
- measurement noise and typical error
- sensor effects
- heat
- humidity
- solar load and wind where evidence supports inclusion
- hills and grade
- route comparability
- altitude vs elevation gain
- fatigue and fueling as confounders

Specify which measurements are suitable for longitudinal coaching decisions and which should remain descriptive only.

No universal heat, grade, MAF, or HR correction should be introduced without validation.

## 10. Session E - Adaptive prescription and session modification

Research how a coach should preserve the intended training stimulus when the athlete's current state does not support the original dose.

Candidate action taxonomy:

- `KEEP`
- `REDUCE_VOLUME`
- `REDUCE_INTENSITY`
- `SHORTEN_INTERVALS`
- `REDUCE_REPS`
- `SUBSTITUTE`
- `MOVE`
- `RECOVERY`
- `REST`

Research when each action is justified and what evidence should block an adaptation.

Distinguish preserving workout intent, changing dose, changing session type, moving the session, and abandoning the original stimulus. Define how later sessions in the week should be reconsidered after a change.

## 11. Session F - Real data and validation

Research lawful, practical sources of real longitudinal evidence.

Prioritize:

- explicitly consented original device exports
- academic or institutional datasets with verified terms
- prospective planned-to-performed observations
- provider data only where the applicable agreement permits the intended use
- collaboration or opt-in data where public datasets cannot answer the question

For every candidate source record:

- provenance
- license or DUA
- consent scope where applicable
- available fields
- longitudinal depth
- missingness
- population
- device/sensor information
- environment fields
- subjective fields
- outcome fields
- commercial/research restrictions
- which Engine 2.x claim it can actually test

Validation should include athlete-disjoint or time-forward testing where possible, simple baseline comparators, calibration checks, uncertainty reporting, sensitivity analyses, and predeclared failure criteria.

Do not collect data simply because it is available. First state which model or claim it can test.

## 12. Session H - Researchers, coaching frameworks, and competing physiological models

Session H studies coherent frameworks proposed by influential researchers and coaches, then decomposes them into separately testable claims.

Starting points include, but are not limited to:

- Phil Maffetone: low-intensity aerobic development, MAF testing, 180 Formula
- Stephen Seiler: intensity distribution, polarized and pyramidal training
- Iñigo San Millán and George Brooks: lactate metabolism, metabolic flexibility, submaximal aerobic intensity
- Martin Buchheit: HRV, monitoring, readiness, repeated-measures interpretation
- Carl Foster: session-RPE and internal training load
- Iñigo Mujika: tapering, detraining, interruption, re-entry
- Andrew Jones and related durability researchers: physiological resilience/durability during prolonged exercise
- heat/environment researchers where their frameworks materially affect training decisions
- concurrent-training researchers relevant to later multi-discipline adaptation

The session must actively search for additional researchers and competing schools when they materially improve the question.

For each framework:

1. State the original claim as accurately as possible from primary work.
2. Separate measurement method, physiological mechanism, coaching prescription, and claimed outcome.
3. Find independent supporting evidence.
4. Find independent contradictory, null, or limiting evidence.
5. Identify populations and situations where the claim may or may not generalize.
6. Compare it with current mainstream coaching behavior where relevant.
7. Compare it with current StrideLab assumptions.
8. Classify each component as `SUPPORTED_CANDIDATE`, `PLAUSIBLE_NEEDS_VALIDATION`, `CONTESTED`, `WEAK_OR_UNSUPPORTED`, or `NOT_SUITABLE_FOR_ENGINE_RULE`.
9. Turn promising components into specific experiments rather than direct production rules.

Examples:

- Maffetone pace-at-comparable-HR monitoring may become a measurement experiment, while the 180-age formula must be validated separately rather than inherited.
- Seiler-style intensity distribution should be compared with pyramidal and other distributions by population, phase, and outcome rather than declared universally superior.
- Lactate-based Zone 2 concepts must not be assumed equivalent to generic watch HR zones.
- HRV-guided training should be evaluated as one input among several, not treated as a single readiness oracle.

Session H should emphasize disagreements between frameworks because those disagreements reveal where Engine 2.x needs uncertainty handling instead of fixed dogma.

## 13. Session G - Synthesis and Engine 2.x architecture

Session G reconciles A-F and H. It is not another independent literature sweep.

Required output:

1. Unified capability gap matrix.
2. Conflicts between vendor behavior, researcher frameworks, and scientific evidence.
3. Proposed Engine 2.x state variables and decision flow.
4. Ranked implementation candidates by expected coaching value, evidence strength, engineering difficulty, and validation cost.
5. Explicit areas that remain research-only.
6. Competitive parity scorecard.
7. Phase 2 experiments.
8. Production-promotion gates.
9. A contradiction register showing where evidence remains unresolved.
10. A provenance map linking each candidate rule to evidence, validation, and current confidence.

No coaching rule moves toward production merely because it is popular, mechanistically plausible, or passes synthetic tests.

## 14. Standard deliverable format for A-F and H

Each specialist report should contain:

1. **Question**
2. **Current StrideLab behavior**
3. **Mainstream or framework comparison**
4. **Scientific evidence**
5. **Contradictory/null evidence**
6. **Exact gap**
7. **Candidate model or decision rule**
8. **What could falsify it**
9. **Applicability grade**
10. **Confidence grade**
11. **Data needed**
12. **Experiment needed**
13. **Unresolved questions**
14. **Adjacent findings worth handing to another session**

This common structure is required so Session G can synthesize results efficiently.

## 15. Progress tracking

Initial Phase 1 state:

| Session | Status |
| --- | --- |
| A - Competitive audit | NOT_STARTED |
| B - Athlete state and response | NOT_STARTED |
| C - Progression, deload, re-entry | NOT_STARTED |
| D - Aerobic efficiency and environment | NOT_STARTED |
| E - Adaptive prescription | NOT_STARTED |
| F - Real data and validation | NOT_STARTED |
| H - Researcher/framework audit | NOT_STARTED |
| G - Synthesis | BLOCKED_ON_A_TO_F_AND_H |

Each session should update its status to `IN_PROGRESS`, `BLOCKED`, `REVIEW`, or `COMPLETE` in its report and in the TickTick parent tracker.

## 16. Competitive milestones

### Milestone 1 - One tier behind mainstream

Engine 2.x should demonstrate, with evidence and real validation where needed:

- athlete-state estimation with uncertainty
- response-aware progression
- interruption and re-entry handling
- environment-aware interpretation
- adaptive workout dose
- aerobic-efficiency tracking with measurement safeguards
- safe deterministic behavior
- explainable user-reviewable changes

### Milestone 2 - On-par candidate

In addition to Milestone 1:

- longitudinal athlete-specific adaptation
- robust calibration on authorized real data
- strong handling of missing/conflicting signals
- validated cross-discipline interference decisions
- strength adaptation mature enough for unified coaching
- adversarial and real-world failure testing
- no regression of Engine 1.5 reliability, approval, persistence, and rollback principles

Parity is a demonstrated property, not a percentage assigned from feature count.

## 17. Phase 2 after synthesis

Likely parallel experimental tracks, subject to Session G:

- athlete-state prototype
- adaptive progression prototype
- aerobic-efficiency and environment prototype
- adaptive-prescription prototype
- real-data calibration
- researcher-framework hypothesis tests
- cross-discipline interference model

These remain research candidates until their specific evidence and validation gates pass.

## 18. Current boundaries

- Engine 1.5 stays frozen and available as the safe fallback.
- Trackside can preserve accepted work but should not drive coaching architecture during this research program.
- No APK is required during Phase 1.
- Existing E007 Runna matched-frequency and E008 Garmin Run Coach prospective evidence remain valuable and should be consumed by the relevant sessions rather than duplicated.
- Existing real-data provenance and focused-evidence work should be reused rather than restarted.
- Commit `517ae9d` remains the baseline focused evidence synthesis and ranked-experiment input for this program.
