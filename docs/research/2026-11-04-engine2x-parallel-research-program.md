# StrideLab Engine 2.x parallel competitive research program

Date approved: 2026-11-04 (Asia/Manila)

Status: **PLANNED, research first. No production Engine 1.5, Trackside, or APK changes.**

Research branch: `research/runna-garmin-coaching-20261102`  
Research PR: #34, open, draft, unmerged  
Planning baseline: `517ae9daca458e7c2a7d9b6a36a828176800373f`

This program extends the existing focused evidence agenda in `docs/research/2026-11-04-focused-engine2-evidence-and-experiment-agenda.md`. It does not replace prior experiments or evidence classifications.

## 1. Priority decision

Engine 2.x research is now the primary StrideLab workstream.

Trackside remains in maintenance and verification mode while the engine research matures. Engine 1.5 remains feature-frozen as the stable fallback. A new APK can wait until the research produces a more mature Engine 2.x integration target.

The goal is not to copy one vendor. The goal is to determine what StrideLab must demonstrate to become first one tier behind, then genuinely competitive with mature coaching systems.

## 2. Phase 1 structure

Run six specialist research sessions in parallel, then one synthesis session.

| Session | Focus | Main deliverable |
| --- | --- | --- |
| A | Competitive coaching audit | Capability matrix and ranked competitive gaps |
| B | Athlete state and training response | Evidence-based athlete-state model proposal |
| C | Progression, deload, and re-entry | Adaptive progression and interruption state machine |
| D | Aerobic efficiency and environment | Aerobic-efficiency and environmental-normalization specification |
| E | Adaptive prescription and session modification | Training-intent-preserving adaptation framework |
| F | Real data and validation | Lawful data-source catalogue and validation protocol |
| G | Synthesis and Engine 2.x architecture | Unified evidence map, parity scorecard, and implementation roadmap |

Sessions A-F may run independently and in parallel. Session G begins after the first six have produced substantial results.

## 3. Shared research rules

Every session must:

1. Separate evidence into SCIENCE, VENDOR, OBSERVATION, ATHLETE, SYNTHETIC, and HYPOTHESIS.
2. Prefer peer-reviewed human evidence and primary vendor documentation over secondary summaries.
3. Record limitations, population, setting, and access restrictions instead of turning one study into a universal rule.
4. Distinguish what a vendor documents from what has been directly observed in an authenticated app.
5. Never infer proprietary thresholds, formulas, or model weights without evidence.
6. Treat synthetic tests as software validation only, not proof of physiological benefit or coaching parity.
7. Keep real-athlete data purpose-limited, consented or lawfully licensed, privacy-safe, and outside Git when raw records are sensitive.
8. Preserve unfavorable findings. Do not tune conclusions only toward evidence that makes StrideLab look stronger.
9. Keep Engine 1.5 production code, Trackside production UI, and release/APK infrastructure untouched.
10. Keep PR #34 draft and unmerged until an explicit later decision.

### Adjacent exploration rule

Each specialist session is encouraged to explore beyond the initial app or paper list when the adjacent material can materially answer its assigned question.

Examples include another coaching platform with a relevant adaptation mechanism, a stronger scientific review, a dataset with better longitudinal fields, a measurement method used in another endurance sport, or a conflicting study that changes the interpretation.

When expanding scope, the session must state:

- what new source or system was added,
- why it is relevant to the assigned research question,
- whether it changes the current StrideLab gap assessment,
- and whether the finding belongs in another specialist session or in synthesis.

This permission is for useful adjacent evidence, not unrelated feature discovery.

## 4. Session A - Competitive coaching audit

### Core systems

- Runna
- Garmin Run Coach
- Garmin Daily Suggested Workouts as a separate Garmin system where relevant
- Kaizen / Run Kaizen
- TrainerRoad adaptive concepts
- Fitbod
- RP Hypertrophy

Other products may be added under the adjacent exploration rule.

### Required questions

- What athlete inputs does each system use?
- What does it adapt: schedule, volume, intensity, workout type, exercise selection, recovery, pacing, or goals?
- Does it react daily, weekly, or only at plan regeneration?
- How does it handle missed training, illness, fatigue, schedule changes, and re-entry?
- How much user control and review is provided?
- Which claims are official documentation, which are direct observations, and which are marketing claims?
- Where is StrideLab clearly behind, close, or potentially stronger?

### Deliverable

A capability matrix covering at minimum:

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

## 5. Session B - Athlete state and training response

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

The goal is not one opaque readiness score. The deliverable should propose an internal state representation with evidence, confidence, baseline rules, and fail-safe behavior when signals disagree or are absent.

## 6. Session C - Progression, deload, and re-entry

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

The deliverable is a progression and re-entry state machine with evidence grades, safety boundaries, uncertainty handling, and variables that still require calibration.

## 7. Session D - Aerobic efficiency and environment

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

The deliverable should specify which measurements are suitable for longitudinal coaching decisions and which should remain descriptive only.

No universal heat, grade, or HR correction should be introduced without validation.

## 8. Session E - Adaptive prescription and session modification

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

The deliverable must distinguish:

- preserving workout intent,
- changing the dose,
- changing the session type,
- moving the session,
- and abandoning the original stimulus.

It should also define how later sessions in the week are reconsidered after a change.

## 9. Session F - Real data and validation

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
- whether it can answer a specific Engine 2.x question

The validation design should include athlete-disjoint or time-forward testing where possible, simple baseline comparators, calibration checks, uncertainty reporting, and failure criteria.

Do not collect data simply because it is available. First state which model or claim it can test.

## 10. Session G - Synthesis and Engine 2.x architecture

Session G reconciles A-F. It is not a seventh independent literature sweep.

Required output:

1. Unified capability gap matrix.
2. Conflicts between vendor behavior and scientific evidence.
3. Proposed Engine 2.x state variables and decision flow.
4. Ranked implementation candidates by expected coaching value, evidence strength, engineering difficulty, and validation cost.
5. Explicit areas that remain research-only.
6. Competitive parity scorecard.
7. Phase 2 experiments.
8. Production-promotion gates.

No new coaching rule should move toward production merely because several synthetic tests pass.

## 11. Standard deliverable format for A-F

Each specialist report should contain:

1. **Question**
2. **Current StrideLab behavior**
3. **Mainstream comparison**
4. **Scientific evidence**
5. **Exact gap**
6. **Candidate model or decision rule**
7. **What could falsify it**
8. **Data needed**
9. **Experiment needed**
10. **Confidence**
11. **Unresolved questions**
12. **Adjacent findings worth handing to another session**

This common structure is required so Session G can synthesize results efficiently.

## 12. Progress tracking

Initial Phase 1 state:

| Session | Status |
| --- | --- |
| A - Competitive audit | NOT_STARTED |
| B - Athlete state and response | NOT_STARTED |
| C - Progression, deload, re-entry | NOT_STARTED |
| D - Aerobic efficiency and environment | NOT_STARTED |
| E - Adaptive prescription | NOT_STARTED |
| F - Real data and validation | NOT_STARTED |
| G - Synthesis | BLOCKED_ON_A_TO_F |

Each session should update its status to `IN_PROGRESS`, `BLOCKED`, `REVIEW`, or `COMPLETE` in its report and in the TickTick parent tracker.

## 13. Competitive milestones

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

## 14. Phase 2 after synthesis

Likely parallel experimental tracks, subject to Session G:

- athlete-state prototype
- adaptive progression prototype
- aerobic-efficiency and environment prototype
- adaptive-prescription prototype
- real-data calibration
- cross-discipline interference model

These remain research candidates until their specific evidence and validation gates pass.

## 15. Current boundaries

- Engine 1.5 stays frozen and available as the safe fallback.
- Trackside can preserve accepted work but should not drive coaching architecture during this research program.
- No APK is required during Phase 1.
- Existing E007 Runna matched-frequency and E008 Garmin Run Coach prospective evidence remain valuable and should be consumed by the relevant sessions rather than duplicated.
- Existing real-data provenance and focused-evidence work should be reused rather than restarted.
