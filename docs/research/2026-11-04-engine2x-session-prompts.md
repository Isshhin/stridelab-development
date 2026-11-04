# StrideLab Engine 2.x specialist research session prompts

Date: 2026-11-04 (Asia/Manila)

These prompts implement the research program in `docs/research/2026-11-04-engine2x-parallel-research-program.md`.

All sessions are research-only. Verify the live research branch before writing. Preserve newer work. Keep PR #34 open, draft, and unmerged. Do not modify Engine 1.5 production code, Trackside production UI, or release/APK infrastructure.

Each session must reuse relevant existing StrideLab research rather than restarting from zero, especially:

- `docs/research/2026-11-04-focused-engine2-evidence-and-experiment-agenda.md`
- `docs/research/2026-11-04-engine2x-parallel-research-program.md`
- existing E007 Runna and E008 Garmin protocols
- prior Batch 1-8 research when relevant

Every session must separate SCIENCE, VENDOR, OBSERVATION, ATHLETE, SYNTHETIC, and HYPOTHESIS evidence; actively search for contradictory/null evidence; grade applicability and confidence; avoid false precision; and state what would falsify each proposed rule.

---

## Session A prompt - Competitive coaching audit

Continue StrideLab Engine 2.x research in repository `Isshhin/stridelab-development`.

Research branch: `research/runna-garmin-coaching-20261102`  
Research PR: #34

Before doing research:
1. Verify the current remote branch head and PR #34 state.
2. Inspect commits after the last documented handoff.
3. Read:
   - `docs/research/2026-11-04-engine2x-parallel-research-program.md`
   - `docs/research/2026-11-04-focused-engine2-evidence-and-experiment-agenda.md`
4. Reuse prior Runna/Garmin and Batch 1-8 findings instead of duplicating them.

Your assignment is **Session A: Competitive Coaching Audit**.

Primary systems:
- Runna
- Garmin Run Coach
- Garmin Daily Suggested Workouts as a separate Garmin system where relevant
- Kaizen / Run Kaizen
- TrainerRoad adaptive concepts
- Fitbod
- RP Hypertrophy

You may expand to other coaching systems if they materially improve the audit. Explain why each added system is relevant.

Research what each system actually uses and adapts:
- athlete inputs
- plan generation
- progression
- schedule changes
- volume/intensity changes
- workout substitution
- readiness/recovery
- missed training
- illness/interruption/re-entry
- environmental adaptation
- strength adaptation
- cross-discipline handling
- user control and review
- explainability
- validation or evidence claims

Be skeptical of marketing. Separate official vendor documentation from authenticated observation and from inference. Do not infer proprietary formulas.

For every major capability, compare current StrideLab evidence and classify:
- BEHIND_SIGNIFICANTLY
- BEHIND_SLIGHTLY
- NEAR_PARITY
- POTENTIAL_ADVANTAGE
- INSUFFICIENT_EVIDENCE

Required deliverable:
1. Question and scope
2. Current StrideLab behavior
3. Mainstream comparison
4. Scientific evidence where relevant
5. Contradictory/null evidence
6. Exact competitive gaps
7. Candidate concepts StrideLab should investigate
8. What would falsify those concepts
9. Applicability grade
10. Confidence grade
11. Data needed
12. Experiments needed
13. Unresolved questions
14. Adjacent findings for another session

Produce a detailed capability matrix and rank gaps by:
- likely coaching value
- evidence strength
- engineering difficulty
- validation cost

Research only. Do not modify production Engine 1.5, Trackside, or APK infrastructure. Keep PR #34 draft/open/unmerged. Commit only research artifacts to the research branch and update the TickTick Session A item when the work reaches a meaningful checkpoint.

---

## Session B prompt - Athlete state and training response

Continue StrideLab Engine 2.x research in repository `Isshhin/stridelab-development`.

Research branch: `research/runna-garmin-coaching-20261102`  
Research PR: #34

Verify the live branch head first, inspect newer work, and read the parallel research program and focused evidence agenda before starting.

Your assignment is **Session B: Athlete State and Training Response**, the highest-priority scientific session.

Research how an adaptive endurance coach should estimate an athlete's current state without reducing everything to one opaque readiness number.

Study:
- fitness vs fatigue
- acute vs chronic training response
- HRV and individual baselines
- resting HR
- sleep duration and quality
- subjective fatigue
- soreness
- stress and mood
- session RPE
- internal load
- workout completion quality
- pace/HR and power/HR response
- submaximal performance
- threshold response
- adherence
- missed/partial sessions
- illness context
- conflicting signals
- missing data
- measurement noise
- confidence and uncertainty

Compare evidence for single-signal and multimodal approaches. Actively search for evidence where HRV, sleep, or other popular metrics fail to predict performance or where group relationships do not translate cleanly to individuals.

Investigate frameworks from relevant researchers when useful, but do not defer to authority. Decompose each framework into testable claims.

Design a candidate Engine 2.x internal state model that could represent concepts such as:
- estimated fitness state
- estimated fatigue state
- recovery state
- aerobic-efficiency trend
- quality-work tolerance
- long-run tolerance
- consistency/adherence
- environmental stress
- adaptation trend
- confidence/uncertainty

Do not invent weights or thresholds that the evidence cannot support. Where calibration is required, say so explicitly.

Required deliverable:
1. Question
2. Current StrideLab behavior
3. Mainstream comparison
4. Scientific evidence
5. Contradictory/null evidence
6. Exact gap
7. Proposed state variables and decision inputs
8. What would falsify the model
9. Applicability grade
10. Confidence grade
11. Data needed
12. Validation experiments
13. Missing-data and conflicting-signal behavior
14. Adjacent findings for other sessions

Research only. Do not modify production code. Preserve PR #34 draft/open/unmerged and update the TickTick Session B item at meaningful checkpoints.

---

## Session C prompt - Progression, deload, and re-entry

Continue StrideLab Engine 2.x research on the existing research branch and PR #34. Verify the live branch first and read the current parallel research program plus focused evidence agenda.

Your assignment is **Session C: Progression, Deload, and Re-entry**.

Research the evidence and coaching logic for:
- weekly running-volume progression
- training-frequency progression
- long-run progression
- quality-session progression
- intensity exposure
- single-session distance spikes
- deload timing and magnitude
- race recovery
- short interruptions
- longer detraining
- illness
- missed weeks
- return to long runs
- return to threshold/interval work
- injury-return principles where appropriate
- novice vs experienced athlete differences

Explicitly challenge simplistic rules such as:
- universal 10% weekly increase
- one acute:chronic workload threshold
- automatic make-up of missed training
- immediate return to previous quality dose after interruption

Reuse the prior Buist, session-spike, Runna frequency, interruption, and detraining research where relevant. Search for contradictory and null evidence.

Develop a candidate progression/re-entry state machine. It should distinguish:
- progress
- maintain
- reduce
- deload
- rebuild exposure
- defer intensity
- restore quality
- restore long-run exposure

Do not turn observational injury associations into personal injury probabilities or prevention guarantees.

Required deliverable:
1. Question
2. Current StrideLab behavior
3. Mainstream comparison
4. Scientific evidence
5. Contradictory/null evidence
6. Exact gap
7. Candidate progression/re-entry states
8. Candidate exposure variables
9. What would falsify each rule
10. Applicability grade
11. Confidence grade
12. Data needed
13. Experiments needed
14. Safety and uncertainty boundaries
15. Adjacent findings for other sessions

Research only. Do not modify Engine 1.5 or Trackside. Keep PR #34 draft/open/unmerged. Update the TickTick Session C item as progress is verified.

---

## Session D prompt - Aerobic efficiency and environment

Continue StrideLab Engine 2.x research on the existing research branch and draft PR #34. Verify the live branch first and reuse existing matched-HR, heat/humidity, grade, and pace-provenance work.

Your assignment is **Session D: Aerobic Efficiency and Environment**.

Research:
- pace at comparable HR
- HR at comparable pace
- submaximal fitness monitoring
- cardiac drift
- pace/HR decoupling
- aerobic durability
- physiological resilience during prolonged exercise
- threshold trends
- required observation windows
- within-person typical error
- sensor reliability
- GPS noise
- chest strap vs wrist HR considerations where evidence exists
- heat
- humidity
- dew point or vapor pressure where useful
- solar load
- wind
- hills and grade
- route comparability
- altitude vs elevation gain
- fueling and hydration as confounders
- accumulated fatigue as a confounder

Critically study relevant frameworks such as Maffetone's MAF test, Seiler's low-intensity concepts, San Millán/Brooks metabolic work, durability research, and other directly relevant methods. Do not assume that a named framework is correct.

For Maffetone specifically, separate:
- the concept of repeated submaximal pace-at-HR testing
- low-intensity aerobic training
- the 180 Formula
- health/status adjustments

Evaluate each separately. Do not treat the 180-age rule as validated simply because the MAF framework is popular.

Determine which measurements are suitable for:
- descriptive athlete feedback
- trend detection
- coaching decisions
- progression gating

and which are too noisy or indirect.

Required deliverable:
1. Question
2. Current StrideLab behavior
3. Mainstream/framework comparison
4. Scientific evidence
5. Contradictory/null evidence
6. Exact gap
7. Candidate aerobic-efficiency measurements
8. Environmental/context variables
9. Minimum repeatability requirements
10. What would falsify the proposed metric
11. Applicability grade
12. Confidence grade
13. Data needed
14. Experiments needed
15. Adjacent findings for other sessions

Research only. No universal heat, grade, HR, or MAF correction should be added without validation. Keep PR #34 draft/open/unmerged and update TickTick Session D at meaningful checkpoints.

---

## Session E prompt - Adaptive prescription and session modification

Continue StrideLab Engine 2.x research on the existing branch and draft PR #34. Verify the live branch head, preserve newer work, and read the research program before starting.

Your assignment is **Session E: Adaptive Prescription and Session Modification**.

Research how a coach should modify today's or this week's training when the athlete's current state does not support the original dose while preserving the intended training stimulus whenever possible.

Study decision logic for:
- KEEP
- REDUCE_VOLUME
- REDUCE_INTENSITY
- SHORTEN_INTERVALS
- REDUCE_REPS
- SUBSTITUTE
- MOVE
- RECOVERY
- REST

Investigate:
- when a session should stay unchanged
- how to preserve training intent
- when reducing volume is preferable to reducing intensity
- when an interval workout should be shortened vs replaced
- when an easy run should become rest
- when moving a session is better than changing it
- what happens to later sessions after one adaptation
- how missed/partial training affects the rest of the week
- how confidence should limit aggressive changes
- how mainstream systems and human coaching approaches handle these decisions

Use Session B/C/D evidence where already available, but do not assume those sessions' hypotheses are validated.

Develop a decision framework that explicitly distinguishes:
- intended stimulus
- athlete state
- constraints
- modification
- expected tradeoff
- confidence
- user approval requirement

Required deliverable:
1. Question
2. Current StrideLab behavior
3. Mainstream comparison
4. Scientific/coaching evidence
5. Contradictory/null evidence
6. Exact gap
7. Candidate adaptation taxonomy
8. Decision preconditions
9. Blockers and fail-safe behavior
10. What would falsify each rule
11. Applicability grade
12. Confidence grade
13. Data needed
14. Experiments needed
15. Adjacent findings for other sessions

Research only. Do not build production adaptation logic yet. Keep PR #34 draft/open/unmerged and update TickTick Session E at meaningful checkpoints.

---

## Session F prompt - Real data and validation

Continue StrideLab Engine 2.x research on the existing research branch and PR #34. Verify the live branch first and reuse the current provenance, data-admission, FIT/TCX/GPX, Groningen, PhysioNet, consent, and field-pilot research.

Your assignment is **Session F: Real Data and Validation**.

Find lawful, practical, high-value real-world data sources and design the validation strategy for Engine 2.x.

Prioritize:
- explicitly consented original device exports
- academic/institutional datasets with verifiable terms
- prospective plan-to-performed observations
- opt-in athlete studies
- provider data only where the applicable agreement allows the intended use
- collaboration when public datasets cannot answer the question

Do not scrape public activities or treat public visibility as permission for model development.

For every candidate dataset/source record:
- owner and provenance
- original URL/DOI
- license or DUA
- consent/ethics scope where applicable
- commercial/research restrictions
- available fields
- population
- longitudinal depth
- missingness
- device/sensor data
- HR
- pace/power
- elevation/route
- environment
- subjective feedback
- planned workout information
- completed workout information
- outcomes
- whether identity leakage is possible
- exactly which Engine 2.x hypothesis the source can test

Design validation for:
- athlete-disjoint evaluation
- time-forward evaluation
- simple baseline comparisons
- calibration
- sensitivity analysis
- missing data
- subgroup/generalization checks
- uncertainty
- predeclared failure criteria
- negative controls
- prospective validation when needed

Explicitly separate:
- structural software correctness
- predictive accuracy
- physiological construct validity
- coaching effectiveness
- safety associations

Required deliverable:
1. Data-source catalogue
2. Rights/provenance status
3. Question each source can answer
4. Question each source cannot answer
5. Proposed validation design
6. Bias/leakage risks
7. Baseline comparators
8. Failure criteria
9. Applicability grade
10. Confidence grade
11. Highest-value data acquisition next steps
12. Adjacent findings for other sessions

Research only. Do not collect or upload private data without explicit authorization. Keep raw sensitive data out of Git. Keep PR #34 draft/open/unmerged and update TickTick Session F at meaningful checkpoints.

---

## Session H prompt - Researchers, coaching frameworks, and competing physiological models

Continue StrideLab Engine 2.x research in repository `Isshhin/stridelab-development`.

Research branch: `research/runna-garmin-coaching-20261102`  
Research PR: #34

Verify the live branch head before starting. Read the parallel research program and existing focused evidence review. Preserve prior findings and do not duplicate work without reason.

Your assignment is **Session H: Researchers, Coaching Frameworks, and Competing Physiological Models**.

The purpose is not to decide which famous researcher is right. The purpose is to identify coherent frameworks, decompose them into testable claims, compare them against independent evidence, and generate high-value Engine 2.x experiments.

Starting points:
- Phil Maffetone
- Stephen Seiler
- Iñigo San Millán
- George Brooks
- Martin Buchheit
- Carl Foster
- Iñigo Mujika
- Andrew Jones and related durability/resilience researchers
- heat/environment researchers
- concurrent-training researchers relevant to future unified coaching

Do not stop at this list. Discover additional researchers, coaches, consensus groups, or competing schools when they materially improve the assigned question.

For every framework:
1. Find the primary source for the original claim.
2. Separate measurement method, mechanism, coaching prescription, and claimed outcome.
3. Find independent supporting evidence.
4. Find independent contradictory/null evidence.
5. Record population and setting.
6. Identify whether the claim generalizes to recreational runners, trained runners, different sexes/ages, strength athletes, or mixed-discipline athletes.
7. Compare the claim with mainstream app behavior when relevant.
8. Compare it with current StrideLab assumptions.
9. Classify each component:
   - SUPPORTED_CANDIDATE
   - PLAUSIBLE_NEEDS_VALIDATION
   - CONTESTED
   - WEAK_OR_UNSUPPORTED
   - NOT_SUITABLE_FOR_ENGINE_RULE
10. Convert promising components into experiments rather than production rules.

Specific questions to investigate include:
- Is Maffetone-style pace-at-comparable-HR monitoring repeatable and useful?
- How valid is the 180 Formula compared with individualized physiological thresholds?
- Polarized vs pyramidal vs threshold-heavy distributions: what outcomes, populations, and phases favor each?
- How should lactate-based "Zone 2" concepts translate, if at all, to field HR data?
- How useful is HRV-guided training compared with performance/RPE/multimodal approaches?
- What does session-RPE add beyond external load?
- What evidence supports tapering and detraining/re-entry models?
- What is durability/resilience, how can it be measured, and is it coachable?
- Where do major frameworks disagree, and should StrideLab respond with uncertainty rather than a fixed rule?

Required deliverable:
1. Framework map
2. Primary-source claims
3. Independent support
4. Contradictory/null evidence
5. Mechanism vs prescription distinction
6. Exact StrideLab relevance
7. Classification of each framework component
8. What would falsify each candidate
9. Applicability grade
10. Confidence grade
11. Proposed experiments
12. Cross-session handoffs

Research only. Named researchers are not authorities for production logic. Do not modify Engine 1.5, Trackside, or APK infrastructure. Keep PR #34 draft/open/unmerged and update TickTick Session H at meaningful checkpoints.

---

## Session G prompt - Synthesis and Engine 2.x architecture

Start this session only after Sessions A-F and H have produced substantial research outputs.

Continue StrideLab Engine 2.x research in repository `Isshhin/stridelab-development`, research branch `research/runna-garmin-coaching-20261102`, PR #34.

Before synthesis:
1. Verify the live branch head and PR state.
2. Read all completed or review-ready A-F and H reports.
3. Read the focused evidence agenda and the master parallel research program.
4. Reconcile duplicate claims and identify conflicts before proposing architecture.

Your assignment is **Session G: Synthesis and Engine 2.x Architecture**.

Do not perform another broad literature sweep unless a specific unresolved contradiction requires targeted follow-up.

Synthesize:
- vendor capability findings
- athlete-state evidence
- progression/re-entry evidence
- aerobic/environment evidence
- adaptive-prescription evidence
- real-data/validation findings
- researcher/framework findings
- prior StrideLab experiments and structural validation

Required outputs:
1. Unified Engine 2.x competitive gap matrix.
2. Clear status per capability:
   - BEHIND_SIGNIFICANTLY
   - BEHIND_SLIGHTLY
   - NEAR_PARITY
   - POTENTIAL_ADVANTAGE
   - INSUFFICIENT_EVIDENCE
3. Contradiction register showing unresolved scientific/vendor/framework disagreements.
4. Proposed internal Engine 2.x state variables.
5. Proposed decision flow from observations -> state estimate -> training intent -> adaptation -> review.
6. Evidence provenance map for every proposed rule.
7. Confidence and applicability for every proposed rule.
8. Ranked implementation candidates by:
   - expected coaching value
   - evidence strength
   - engineering difficulty
   - validation cost
9. Explicit rules that must remain research-only.
10. Phase 2 experimental plan.
11. Real-data calibration plan.
12. Production-promotion gates.
13. One-tier-behind mainstream acceptance criteria.
14. On-par candidate acceptance criteria.
15. Areas where StrideLab could intentionally differentiate rather than imitate.

A rule must not be promoted because it is popular, comes from a famous researcher, appears in a commercial app, has a plausible mechanism, or passes synthetic tests.

Preserve Engine 1.5 reliability principles:
- deterministic behavior where possible
- bounded safeguards
- explicit review for meaningful changes
- rollback
- persistence
- explainability
- no silent corruption of approved plans

Research only until the synthesis explicitly identifies implementation candidates and their validation gates. Keep PR #34 draft/open/unmerged. Do not modify production Engine 1.5, Trackside, or APK infrastructure during this synthesis.
