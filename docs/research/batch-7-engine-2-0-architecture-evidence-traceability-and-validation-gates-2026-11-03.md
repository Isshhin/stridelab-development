# Batch 7 — Unified Engine 2.0 candidate architecture, source-to-test traceability and validation gates

**Reviewed:** 2026-11-03 (Asia/Manila). **Repository:** private `Isshhin/stridelab-development`, isolated research PR #34. **Status:** design synthesis + evidence audit + registered-test mapping, **NOT implementation or verification of a coaching model**. **Production changes:** none. **Decision:** proceed to *validation planning* for a modest deterministic hybrid research candidate, **not** promote Engine 2.0.

## 0. Read this first: what was actually established?

- **Six preceding batches** describe vendor-facing features, methodological literature, hypotheses and experiment protocols; they **do not** recover proprietary code or prove superiority of any coaching policy.
- **Experiment 002** contains **16** current Runna mobile-app controlled input cases, **0/16 executed**. Historical published Runna PDFs in Experiment 001 do **not** substitute for current matched generator output.
- **Experiment 003** has **18** load-policy counterfactual scenarios; **Experiment 004** has **22** cross-discipline scenarios; **Experiment 005** has **18** state/load cases. All **58/58 are registered and NOT_RUN**, not tested features.
- Across Experiments 002–005 there are **74 distinct qualified case identities**. Two *unqualified* registries contain different case `L10` entries (Runna longest-run input versus load-policy phase comparison). Never join on bare case ID. Use `E002:L10` and `E003:L10`. The linked machine-readable [24-requirement traceability matrix](experiments/experiment-006-source-to-test-traceability.csv) maps all 74 qualified cases.
- At this checkpoint **no real Garmin Run Coach before/after controlled pairs** and **no prospective real-athlete effectiveness study** are available. Athlete Simulation Lab is a separate local Python project; do not claim these GitHub documents modify or test it.
- The existing daily 09:00 Asia/Manila Runna/Garmin evidence automation is active; its last recorded run (Nov 3, 2026) documented separation of Workout Insights from plan modification and walking-rest distance. There is no reason to create a duplicate schedule.

### Research files and source map

| Batch | Source-of-evidence focus | Relevant file | Confidence boundary |
| --- | --- | --- | --- |
| 1 | Runna hard-work dose, periodization and Garmin Run Coach adaptation | [Batch 1](batch-1-hard-dose-periodization-garmin-adaptation.md) | Vendor behavior; no algorithm thresholds |
| 2 | Internal vs external load, intensity-distribution evidence, easy pace at matched HR | [Batch 2](batch-2-external-internal-load-and-easy-pace.md) | Laboratory economy, pace-at-HR and performance are not equivalent |
| 3 | Kaizen load-first idea, Runna missed workouts/recovery modes, Garmin/DSW separation | [Batch 3](batch-3-kaizen-load-and-interruption-adaptation-2026-11-03.md) | No proprietary weight/load equation |
| 4 | Distance-first vs scalar vs hybrid and adversarial protocol | [Batch 4](batch-4-load-first-vs-distance-first-architecture-2026-11-03.md) | Original policy hypotheses, no results |
| 5 | Fitbod, Thenx, Calistree, Freeletics, concurrent training | [Batch 5](batch-5-cross-discipline-progression-and-coordination-2026-11-03.md) | Static Thenx curricula do not demonstrate individual adaptive engine |
| 6 | TrainerRoad 2026 AI, RP Hypertrophy set allocation, TrainingPeaks load metrics | [Batch 6](batch-6-predictive-workout-selection-strength-autoregulation-load-monitoring-2026-11-03.md) | Vendor product updates, no independently validated accuracy |
| Addendum | Runna Workout Insights **does not** edit plan; walking rests count toward completed mileage | [Nov 3 addendum](2026-11-03-workout-feedback-and-recovery-distance.md) | Observed completed distance is not future planned distance |

**Critical product-version boundary:** TrainerRoad's June 2026 description no longer treats older Workout/Progression Levels as direct workout selection inputs. They can still be capability/difficulty indicators. Preserve source date/version whenever using vendor behavior as a comparator. Primary vendor reference: https://www.trainerroad.com/blog/whats-new-with-trainerroad-ai/ . Fitbod's newer official explanations similarly distinguish actual logged performance from estimated strength/recovery: https://help.fitbod.me/hc/en-us/articles/43489869175063-How-does-Fitbod-decide-my-sets-reps-and-weight . These are **vendor statements**, not validated StrideLab design thresholds.

## 1. Architectural choice for research, not product approval

**Evaluate a constrained, interpretable hybrid (H1) against a simpler deterministic baseline (B0) and an explicit scalar-load research comparator (S0).** The goal is to learn whether H1 adds meaningful behavior under identical constraints, not to assert more variables must improve training.

- **B0 — simple deterministic baseline:** existing documented/confirmed constraints and independently verified local lab policies, without new speculative athlete load coefficients. Keep any older policy baseline immutable for regression comparisons.
- **S0 — scalar-load comparator:** if a legitimate transparent score is defined and calibrated, use it only as an experimental comparator. Do not name it Kaizen-, Garmin-, TrainingPeaks- or TrainerRoad-equivalent. Score missingness and sensitivity explicitly; otherwise mark comparator unavailable.
- **H1 — explainable multidimensional hybrid:** uses separate dose dimensions and conservative constraint arbitration; it may recommend `KEEP`, `SESSION_ALTERNATIVE`, `MOVE_SESSION_PROPOSED`, `WEEK_REVISION_PROPOSED`, `BLOCK_REBUILD_PROPOSED`, `RECOVERY_OPTION` or `DEFER_UNKNOWN`. This is **a proposed decision vocabulary**; no implementation exists in this GitHub research PR.
- **M2 — learned predictive selector:** research only. Do not train or claim a workout outcome forecaster without longitudinal opt-in observations, comparison baselines, leakage-safe time splits and calibration/uncertainty reporting. TrainerRoad vendor claims do not prove a predictive design is right for StrideLab.

**Rejected shortcuts:** one universal readiness or workout point; linear conversion of running kilometers to strength tonnage or calisthenics holds; translating watch HRR Zone 2 to laboratory LT1; using TrainingPeaks TSB or ACWR to diagnose injury risk or guarantee safety; copying a vendor's proprietary numeric coefficients.

## 2. Proposed architecture: one-way evidence flow

```text
Original planned prescriptions ──┐
Versioned observed activities ───┤
Athlete goals, phase, preferences ├─> A. SOURCE/TYPE VALIDATION
Environment + athlete feedback ──┘       │ (quality, timestamp, missingness)
                                         v
                                 B. OBSERVATION LEDGER
                              planned != observed != missing
                                         │
                                         v
                              C. DOMAIN-SPECIFIC SUMMARIES
                 running / strength / skills / conditioning / context
                                         │
                          ┌──────────────┴────────────────┐
                          v                               v
                 D. EXPLAINABLE B0                   E. PROPOSED H1
                 (control candidate)                 (research alternative)
                          └──────────────┬────────────────┘
                                         v
                              F. INDEPENDENT CONSTRAINTS
                      capacity, calendar, phase, overlap, source
                                         │
                                         v
                              G. COMPARISON + DIFF
                             KEEP / PROPOSE / DEFER_UNKNOWN
                                         │
                                         v
                            H. ATHLETE APPROVAL + AUDIT
                   no material plan change without versioned approval
                                         │
                                         v
                            I. FOLLOW-UP OBSERVATION
                  real outcome measurements; not assumed adaptation
```

**The arrows are conceptual and do not describe code already implemented.** Keep source ingestion idempotent; never allow a forecast, plan snapshot, or natural-language feedback to be mistaken for an actual completed workout. A source-of-truth plan remains unchanged until the athlete explicitly approves material revisions.

### A. Observation/event contract

Minimum fields, with `null` for unknown rather than imputed zero:
- `athlete_pseudonym`, `session_id`, `discipline`, `source`, `source_version`, `recorded_at_tz`, `plan_version`, `policy_version`, `data_confidence`.
- `completion_status`: `planned|completed|partial|skipped|unplanned|unknown`. Distinguish planned future workout from confirmed skipped or recorded zero-work rest.
- `planned_step_tree` versus `observed_step_tree`: actual duration, timer vs elapsed, pauses/walking rests, warmup/work/cooldown, and supported heart rate/pace/gradient. GPS circle/loop pace reliability and heat/source condition are explicit.
- `report`: RPE/RIR, tiredness, symptoms/pain if voluntarily reported; typed, timestamped, not a measured clinical state.
- `proposal`: action, before/after dose vector, rationale/evidence refs, uncertainties, approval state and revision identity. Reject or rollback **must not erase completed workout history**.

### B. Discipline-native dose vector

| Domain | Separate candidate dimensions | Critical guard |
| --- | --- | --- |
| Running | easy time, distance, threshold/hard **work step** time, recovery, long-run km/min, recent repeatable capacity, run frequency and phase | A ten-kilometer threshold session is NOT ten kilometers of hard work |
| Strength | exercise ID/muscle roles, load, reps, actual sets, subjective RIR, total *muscle-group* weekly sets, per-exercise allocation | Total group-set change ≠ just rearranging sets across exercises |
| Calisthenics | skill node, prerequisite/regression alternatives, completed reps/holds, assistance, technique confidence | Higher rep count ≠ demonstrated new skill mastery |
| Conditioning | work/rest time, movement/impact, completed rounds, relevant muscle overlap | Timer length and jumps are not interchangeable with running aerobic exposure |
| Recovery/context | history with timestamps, HR/RPE when trustworthy, heat/gradient/route, symptoms and missingness | No universal physiological 'fully recovered' percent |

No weights for combining these dimensions are established. Mechanical and cardiometabolic demands should remain interpretable even when one dimension is unavailable. Optional device TSS/Training Effect fields retain source/method/threshold calibration rather than becoming a StrideLab injury or readiness classification.

### C. Candidate selection and explanation

A proposed research selector can:
1. Preserve plan goals, race date, periodization, validated weekly capacity, athlete availability and time budget. Do not infer capacity from race performance alone.
2. Detect deliberate reductions, recent load overlap, meaningful missed/partial exposure, last quality work and uncertain sensor/feedback state; avoid assigning false recovery from missing data.
3. Generate **zero or more** feasible alternative session/placement proposals rather than rewriting the entire plan in response to a single observation.
4. Reject hard-stop conflicts before ranking. Treat qualitative overlap flags as warnings, not medically validated recovery-hour restrictions.
5. Explain exactly **what changed**: step-level hard seconds, easy minutes, long-run km, gym sets/RIR, skills and conditioning interval/impact changes. Keep original state, proposal and acceptance separate.
6. If evidence is incomplete, choose `DEFER_UNKNOWN` or a transparent conservative option. A model's numerical forecast must never masquerade as confirmed performance.

## 3. Machine-readable traceability and open coverage gaps

The [traceability CSV](experiments/experiment-006-source-to-test-traceability.csv) contains **24 requirements** with source-document labels, evidence class, named scenario IDs, earliest validation gate, readiness and remaining limitations. All **74** qualified IDs from Experiments 002–005 are represented at least once. This is **cross-reference completeness only**: not proof that a case passes, truly isolates its variable, covers an entire requirement, or reflects athlete physiology.

**Qualified ID convention:**
- `E002:<case_id>`: 16 Runna current-app generator comparisons (requires authorized mobile account; 0 executed).
- `E003:<case_id>`: 18 load/distance policy scenarios (NOT_RUN).
- `E004:<case_id>`: 22 multi-discipline scheduling scenarios (NOT_RUN).
- `E005:<case_id>`: 18 temporal state/autoregulation scenarios (NOT_RUN).

**Coverage gaps, not disguised as completed validations:**
- `R11` Garmin Run Coach adaptation: no controlled Run Coach-specific paired case, model/firmware/sync captures still required.
- `R22` athlete longitudinal outcomes: cannot be established by generative simulation, even if all structural cases pass.
- `R24` atomic approve/reject/rollback: no standalone registered test yet for revision transactionality/history preservation.
- `R08` feedback vs revision and `R17` scope of changes have indirect cases, but still need targeted plan approval/undo tests.
- No specific dedicated experiment has independently demonstrated a predictive model improves real performance, adapts safely to illness, or correctly forecasts injury risk.

**Unregistered next test proposals (NOT added to the 58-case count):**
- `PROPOSE-REV1`: change workout feedback text only; plan version/workouts unchanged.
- `PROPOSE-REV2`: proposal pending/rejected; all persisted plan and workout history unchanged.
- `PROPOSE-REV3`: approved multi-session revision + rollback restores exact previous plan without deleting recorded activities.
- `PROPOSE-GAR1`: time-stamped Garmin *Run Coach* before/after comparison with sync/device version and true completion source.
- `PROPOSE-OUT1`: prospective multiweek matched-condition easy-HR speed and threshold tracking with measurement-quality exclusions.

These are future experiment designs, not executed or registered results.

## 4. Verification gates: what each gate does and does NOT establish

| Gate | Input/dependency | Test and stop condition | Permitted interpretation |
| --- | --- | --- | --- |
| **G0 — research integrity** | Actual local lab checkout, current manifest/fixtures, policy versions and protected source | No writes to original dataset/private FIT; prior policy baselines reproducible; missing local source disclosed | We know what research code/data is being tested, not whether it coaches well |
| **G1 — structural reliability** | Typed input contract and implemented B0/H1 candidates; 58-case registry plus future approval cases | No known hard-stop constraint violations; no missing-as-zero; source fidelity; no unapproved revisions; no plan-vs-actual leakage; deterministic repeated seeds | Software behavior is internally consistent under tested cases, **not physiologically safe** |
| **G2 — behavioral comparison** | G1 passed; independent adversarial and withheld cases, declared baseline, clear metrics | Pre-register acceptance metrics; assess unnecessary changes, return-to-quality, dose balance, scheduling overlap, confusion/unknown fallback, explanations and ablations | Candidate's behaviors are testable/plausible and differences attributable to policy, not proven performance advantage |
| **G3 — external/vendor observation** | Authorized Runna mobile account; supported Garmin snapshots; consent and exact app versions | Current Runna 0/16 and Garmin none; capture full trace, handle refusals and version changes; no proprietary formula claims | Describes **observed** competitor behavior where access allows, not its clinical correctness |
| **G4 — real-world outcome evaluation** | G1/G2 plus consented repeated field measurements, comparable route/environment/HR and threshold checks | Report missingness, matched-condition variability, adherence, durability and adverse reports; prospective temporal splits and uncertainty; do not infer causality from uncontrolled N=1 | Evidence of measured longitudinal associations only; true comparative effectiveness needs stronger design |
| **G5 — release admission (future)** | Independent efficacy and usability review, opt-in safeguards, integration tests, offline rollback, Android compatibility | Versioned approvals, non-destructive migration, policy fallback, security/privacy and accessible explanations; separate product decision | Candidate can be considered for release, not automatically approved |

**Never set numerical physiological thresholds simply to make a synthetic engine pass.** The software invariant "zero structural hard stops in the preregistered test set" is a useful engineering criterion, but is not proof of safe training, avoided injury or superior aerobic development.

### Decision about learned/predictive coaching

For now, **defer M2**. TrainerRoad's reported simulation selection is a product reference, not evidence that the StrideLab lab has forecast calibration or suitable data. A rules-first constrained H1 is a smaller *research candidate*, not an accepted release requirement. If H1 provides no independently measurable improvement over B0, keep B0 and do not promote complexity.

## 5. Next batch: execution handoff, not another competitor catalog

**Priority 1 — inspect actual independent lab environment** (if accessible; it is a *different local project* from this GitHub repo):
- Local path historically `E:\Project\stridelab-copilot\stridelab-coaching-lab`; protected synthetic corpus historically `F:\StrideLab DataSets`. These details are prior handoff context, not verified present access.
- Check latest repo checkout, schemas, baseline policies, test runner, protected manifest checksums and real-data provenance without mutating originals. Previous local Copilot test counts/commits are not automatically verified in GitHub.
- Decide which tests run as-is versus need additional normalized fixtures/schema work. Log `BLOCKED_BY_SCHEMA` rather than fabricating a pass.

**Priority 2 — execute in safe independent batches** once runnable:
- **8A, evidence/contract tests:** status/unknowns, timestamp semantics, capacity, dataset immutability, deterministic seeds and candidate comparison snapshots.
- **8B, running E003:** all 18 cases; compare B0 vs H1 (and S0 only if legitimately defined); inspect scenario-independent evaluator and uncovered interactions.
- **8C, cross-discipline E004:** all 22 cases; exercise prerequisites, feasible time and movement overlap.
- **8D, monitoring E005:** all 18 cases; strength allocation, uncertainty and date-indexed load metrics.
- **8E, held-out validation + audit:** do not train and grade on the same generator assumptions; export pass/fail/blocked per case with exact version and reason.
These are **planned test phases**, not runs or commitments to local computer availability.

**Priority 3 — resolve coverage gaps** by registering and later running revision-state/rollback tests, then authorize external Garmin/Runna data collection separately. Do not manufacture current Runna/Garmin app output from public product descriptions.

**Priority 4 — athlete-specific real evidence:** build a field-measurement protocol prioritizing improved **speed at comparable genuinely easy HR**, not a proprietary VO2max estimate or tight-loop GPS pace; include heat/humidity, grade, fatigue, interval timer vs elapsed, and repeated baseline measures. No actual follow-up performance dataset was analyzed in this batch.

## 6. Protection and project boundaries

- Keep **PR #34 draft and unmerged**; research docs only. No Engine 1.0, 1.5, Trackside PR #36 or local independent Python lab changes.
- Never commit private Garmin FIT files, athlete identifiers, personal accounts or credentials; synthetic source and manifests are read-only.
- User approval for significant training changes is a planned product invariant. Do not silently take action just because an AI text explanation is confident.
- Existing daily Runna + Garmin automation stays active; do not duplicate.
- When continuing in another chat, inspect live PR HEAD and TickTick Handbook note **09. Engine 2.0 research handoff and next batches**; update that handoff with exact commit and remaining unrun cases.
- Evidence and tests are research inputs; no independent results today prove H1 is more effective than Engine 1.0/1.5.

## 7. Primary context and method references

Detailed source URLs, publication/product dates and limitations are in [Batch 1](batch-1-hard-dose-periodization-garmin-adaptation.md), [Batch 2](batch-2-external-internal-load-and-easy-pace.md), [Batch 4](batch-4-load-first-vs-distance-first-architecture-2026-11-03.md), [Batch 5](batch-5-cross-discipline-progression-and-coordination-2026-11-03.md), [Batch 6](batch-6-predictive-workout-selection-strength-autoregulation-load-monitoring-2026-11-03.md) and the [Nov 3 Runna addendum](2026-11-03-workout-feedback-and-recovery-distance.md). For fundamentals, distinguish external/internal training load per Impellizzeri et al., 2019 (https://pubmed.ncbi.nlm.nih.gov/30614348/) and avoid prescribing based on ACWR alone per Impellizzeri et al., 2020 (https://pubmed.ncbi.nlm.nih.gov/32502973/).

**Final audit:** Original design synthesis and source-to-test mapping only. **74/74 qualified scenario IDs traced, not 74/74 tests executed.** **0/58 independent simulation cases executed**, **0/16 current Runna generator cases executed**, **no verified Garmin paired outputs**, **no real-world Engine 2.0 performance effect established**.
