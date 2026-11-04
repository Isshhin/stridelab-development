# Engine 2.x pre-synthesis review

Reviewed: 2026-11-04 (Asia/Manila)

Research branch: `research/runna-garmin-coaching-20261102`  
Reviewed head: `90ec7af85e8b52d7cc13b1e34f3dc164220dd3ed`  
PR #34: open, draft, unmerged

Scope: review Sessions A, B, C, D, E, F and H against the reliability rules in `2026-11-04-engine2x-parallel-research-program.md`. This is a pre-synthesis quality review, not a new systematic review and not a full independent re-audit of every cited paper.

## Overall verdict

**READY_FOR_SESSION_G_WITH_TARGETED_SYNTHESIS_CORRECTIONS**

No specialist report needs a full rerun. The seven reports are substantially consistent on the most important points:

- real-data admission precedes physiological calibration;
- matched-HR and related aerobic-efficiency measures require within-athlete repeatability before they can gate training;
- HRV, sleep and wearable readiness should be contextual inputs, not single-signal authorities;
- weekly volume, single-session exposure, intensity and interruption history must remain separate;
- the 10% weekly rule and ACWR should not be treated as validated personal safety thresholds;
- environmental strain can change pace/HR interpretation without proving fitness loss;
- MAF pace-at-HR concepts are testable, while the 180 Formula is not established as a personal physiological threshold;
- adaptive prescription should preserve training intent when possible and never create hidden training debt;
- synthetic correctness is not evidence of athlete benefit;
- commercial feature presence is not evidence of coaching effectiveness.

Targeted external spot-checks during this review were consistent with the reports: Buist's 532-runner trial found no injury-prevention benefit from its 10%-rule program; the 5,205-runner Garmin cohort associated single-session distance spikes with overuse injury while week-to-week change was null; the Maffetone/Høeg paper is an initial formula-development study rather than independent out-of-sample threshold validation; the concurrent-training meta-analysis found no overall compromise in maximal strength or whole-muscle hypertrophy but a small explosive-strength penalty, stronger for same-session training; and PhysioNet currently lists the Málaga v1.0.1 dataset under Contributor Review Health Data License and DUA 1.5.0.

## Session-by-session review

| Session | Verdict | Strengths | Required handling before or during G |
| --- | --- | --- | --- |
| A - Competitive coaching audit | PASS WITH CLARIFICATION | Strong separation of vendor documentation from observation, no proprietary-formula inference, explicit gaps and falsifiers | Do not use its single status column as a literal product-quality score. It sometimes mixes missing validation with missing capability. G should split **functional capability** from **evidence/validation maturity** before declaring a gap |
| B - Athlete state and response | PASS | Strong contradiction register, multimodal state concept, signal lineage, missing-data handling, individual baselines, abstention and prospective validation | Use B as the primary state-estimation foundation. Do not collapse its dimensions into one readiness score or fixed weighted sum without later calibration |
| C - Progression, deload and re-entry | PASS WITH ARCHITECTURE CAUTION | Rejects universal 10% and ACWR thresholds, separates session types and interruption reasons, has clinical boundaries and falsifiable states | Its "one primary state" is useful for decision output but may be too restrictive as the internal athlete model. G should permit overlapping dimensions/gates such as rebuilding + fatigue + environmental stress, then select one reviewed action |
| D - Aerobic efficiency and environment | PASS | Excellent separation of description, trend, durability inference and coaching action; explicit sensor/environment confounding; MAF components evaluated separately; repeatability before prescription | Keep matched-HR as a measurement candidate until typical error and held-out validity are established. No fixed 5% decoupling threshold, heat correction or MAF formula should enter production |
| E - Adaptive prescription | PASS WITH REQUIRED CONTROL STATE | Strong intent-preserving taxonomy, explicit tradeoffs, no hidden make-up, review requirements and action-specific falsifiers | G must add an explicit **ABSTAIN / NEED_MORE_INFORMATION / HOLD_AUTOMATION** control state. KEEP means the existing session remains appropriate; it must not be overloaded to mean "the engine cannot tell" |
| F - Real data and validation | PASS WITH STATISTICAL CAUTION | Best source-rights separation so far, plan-to-performance priority, leakage controls, athlete/time holdouts, shadow logging and distinct correctness/prediction/effectiveness/safety gates | Do not make statistical significance the only promotion criterion. G should require practical effect size, calibration, coverage and decision utility. Use superiority, noninferiority or equivalence designs according to the actual claim |
| H - Researchers/frameworks | PASS | Strongest protection against doctrine. Separates mechanism from prescription, includes competing/null evidence, rejects automatic MAF/Zone 2/HRV/ACWR conclusions, produces falsifiable handoffs | Use H to constrain claims, not to create a new weighted "best philosophy." Additional coaches/researchers can be added later only when they address an unresolved G decision |

## Critical synthesis corrections

### 1. Split competitive capability from evidence maturity

Session A correctly avoids outcome ranking, but some labels still combine two different questions:

1. Does StrideLab have the functional mechanism?
2. Has that mechanism been validated well enough to trust?

Example: StrideLab already has deterministic goal-based generation concepts and scheduling foundations, while the report appropriately notes that matched Runna/Garmin quality is not demonstrated. G should therefore maintain two axes:

- **Capability maturity:** absent, prototype, implemented candidate, integrated.
- **Evidence maturity:** hypothesis, synthetic only, observational, prospectively validated, effectiveness validated.

Only the second axis can justify claims of coaching parity.

### 2. Add a non-action state

Sessions B, D and H repeatedly require abstention under missing, conflicting or noncomparable evidence. Session E's nine training actions do not explicitly represent this.

G should add a control state such as:

`ABSTAIN_NEED_MORE_INFORMATION`

This is not another workout prescription. It means:

- no automatic coaching change is justified;
- preserve the approved plan unless a separate safety/constraint rule requires otherwise;
- show what evidence is missing or conflicting;
- request a low-burden input only when it can resolve the decision;
- re-evaluate when a relevant observation arrives.

This prevents "unknown" from silently becoming KEEP, REST or a readiness downgrade.

### 3. Model overlapping athlete dimensions, then choose one action

Session C's state machine is useful for external decision behavior. Internally, however, the athlete can simultaneously be:

- rebuilding long-run exposure,
- showing elevated short-term fatigue,
- training in unusual heat,
- retaining threshold experience,
- missing reliable HR data.

G should preserve B's multidimensional state representation and C's safety/transition gates. The system may emit one primary reviewed action while retaining several independent state dimensions.

### 4. Promotion requires practical usefulness, not only statistical improvement

Session F's validation design is strong. G should extend promotion criteria to require:

- predeclared practically meaningful improvement;
- calibration where probabilities are used;
- acceptable false-restriction and false-escalation rates;
- sufficient coverage rather than performance only on easy cases;
- athlete burden and override rate;
- stable performance on forward-time and athlete-held-out data;
- subgroup and device/climate failure analysis;
- structural safety invariants.

A statistically detectable improvement that is too small, too burdensome or only works by abstaining on most cases is not enough.

## Cross-session contradiction register for G

These are not failures. They are decisions G must preserve as unresolved until experiments answer them.

| Topic | Current evidence position | G behavior |
| --- | --- | --- |
| HRV/readiness | Can add context; pooled performance advantage is uncertain; wearable composite readiness is not ground truth | Optional input with lineage and individual baseline. Require incremental value beyond simple history + check-in before it can change training |
| Pace at comparable HR | Promising field measurement, but fatigue and environment can move HR/pace in misleading directions | Descriptive/trend candidate first. Progression gating only after repeatability and independent outcome validation |
| MAF 180 Formula | Formula-development evidence exists, independent individual threshold agreement is insufficient | Do not use as automatic HR prescription. May remain an experiment comparator |
| Polarized vs pyramidal vs threshold emphasis | Evidence depends on denominator, population, phase and endpoint; no universal winner | Store training distribution explicitly and support multiple validated strategies rather than a doctrine |
| Lactate/Zone 2 | Lactate metabolism evidence does not make watch zone labels equivalent to LT1, VT1, Fatmax or lactate flux | Preserve test/protocol definitions. Do not translate labels without criterion validation |
| 10% rule / ACWR / session spikes | Weekly 10% prevention not established; ACWR has methodological and intervention problems; single-session spike association is stronger but observational | Treat as separate exposure descriptors. No personal injury probability or automatic prevention cutoff |
| Deload/taper | Planned taper and reactive fatigue management are different interventions | Keep race taper, ordinary deload and interruption rebuild as separate states |
| Concurrent training | Max strength/hypertrophy interference is often small/null; explosive strength and same-session contexts may differ | Avoid blanket running-strength penalties. Model endpoint, exercise, priority, order and spacing |
| Active recovery | Evidence does not justify making a recovery run mandatory | Athlete preference/context only. Rest remains a legitimate alternative |
| Environment adjustment | Heat/humidity clearly affect strain, but universal personal pace corrections are not validated | Match/contextualize first. Individual correction models require held-out calibration |

## Recommended Session G input order

1. Session F: define what evidence can actually be collected and trusted.
2. Session D: define which performance measurements are repeatable enough to exist as state inputs.
3. Session B: define the multidimensional athlete-state representation and uncertainty.
4. Session C: define progression, interruption and restoration gates.
5. Session E: define intent-preserving actions plus the new abstention control state.
6. Session H: constrain each proposed rule against competing frameworks and unresolved physiology.
7. Session A: map the resulting architecture back to mainstream capability gaps without turning feature count into efficacy.

## Recommended initial Engine 2.x architecture for G to evaluate

`OBSERVATIONS`
-> provenance / quality / eligibility
-> `STATE ESTIMATION`
-> multidimensional estimates + uncertainty
-> `SAFETY AND CONSTRAINT GATES`
-> clinical restrictions, availability, locks, exposure limits
-> `TRAINING INTENT`
-> purpose of the planned stimulus
-> `DECISION`
-> KEEP / dose change / substitute / move / recovery / rest / ABSTAIN
-> `REVIEW`
-> reason, evidence, confidence, tradeoff, affected sessions
-> `OUTCOME`
-> performed dose + athlete response
-> feed forward into future state estimation

Do not build a black-box global readiness score as the center of this architecture.

## G readiness decision

**Session G may now start.**

A-F and H are sufficiently mature for synthesis. The four corrections above should be treated as explicit G requirements, not reasons to rerun the specialist sessions.

G should not claim that StrideLab is on par with Runna, Garmin or another commercial coach from these reports. The discovery phase has identified credible gaps, candidate measurements and validation plans. Competitive efficacy remains to be demonstrated through Phase 2 and authorized real-athlete validation.
