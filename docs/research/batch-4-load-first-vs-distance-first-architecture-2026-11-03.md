# Batch 4 — Load-first vs distance-first coaching, decision boundaries, and simulation protocol

**Reviewed:** 2026-11-03 (Asia/Manila). **Track:** StrideLab independent Coaching Algorithm Research Lab, Engine 2.0. **Status:** EVIDENCE REVIEW + PREREGISTERED DESIGN / NO MODEL EXECUTION. **Production code:** untouched. **Current personalized competitor outputs:** none.

**Parent:** [Batch 3](batch-3-kaizen-load-and-interruption-adaptation-2026-11-03.md); [Batch 2](batch-2-external-internal-load-and-easy-pace.md); [Experiment 002](experiments/experiment-002-runna-controlled-generator.md). Machine-readable NOT-RUN test registry: [Batch 4 test cases](experiments/experiment-003-load-policy-counterfactuals.csv).

## 1. Decision to investigate

**PROPOSED RESEARCH DIRECTION, NOT AN IMPLEMENTATION DECISION:** compare three policy families on the same athlete histories and constraints:
- **P-D (distance-first):** set feasible weekly km, frequency, long-run and explicit quality-step prescription first; observe internal response without forcing a universal physiological load score.
- **P-L (load-first proxy):** choose an *explicitly defined, independently implemented* weekly scalar load target first and translate to distance/session structure. For a scientifically defensible comparison, record its exact inputs and calibration; a dummy score is an **experimental comparator, not a coaching recommendation**. Do **not** represent it as Kaizen/Garmin or infer their formula.
- **P-H (multidimensional hybrid):** maintain separate prescriptions for weekly volume/duration, easy-aerobic time, quality **work-step time**, long-run dose, available days, spacing, and constraints; monitor internal response (HR, RPE, wellness) and contextual route/heat as separate observations. Proposed changes remain reversible, explained, and user-approved if material.

The architecture should retain the sport-specific *stimulus* (why a workout is prescribed), not just the apparent weekly load total.

### Evidence ledger (relevant claims, status, limits)

| ID | Finding | Status and limitation |
| --- | --- | --- |
| V-K1 | Kaizen says it sets a weekly load target, converts it into a changing *distance-equivalent* guide, and adjusts for running actually done. Its public app listing mentions distance, pace, elevation and supported cross-training. | **VENDOR-DOCUMENTED** [K1,K2]; current app output/coefficients and predictive accuracy not independently validated. |
| E-1 | External work and internal physiological/perceived response are different constructs. | **LITERATURE FRAMEWORK** [S1]; neither is itself a validated injury predictor. |
| E-2 | A 2026 peer-reviewed paper proposes multidimensional impulse-response modeling because equal one-number loads can conceal different intensity/duration stimuli. | **MODELING PROPOSAL** [S2], not demonstrated superior outcome prediction; authors note no experimental efficacy validation and conflicts of interest. Do **not** copy 3-system equations into the runner coach. |
| E-3 | HR/TRIMP is useful in some contexts but can be distorted by HR inertia, cardiovascular drift, heat, arousal and intervals. | **DOCUMENTED LIMITATIONS** [S2,S3]; HR reserve percentages are not physiological LT1 thresholds. |
| E-4 | Garmin publishes distinct concepts for cardiometabolic Training Load and biomechanical Running Tolerance/Impact Load, which also considers slope, cadence and speed. | **VENDOR-DOCUMENTED** [G1] for supported hardware; its numeric equivalents/weights are proprietary. No confirmation that Forerunner 165 has Running Tolerance. |
| E-5 | Acute:chronic workload ratio alone is unsuitable for identifying a safe load band or making causal injury-prevention recommendations. | **METHODOLOGICAL CRITIQUE** [S4]; newer team-sport observational meta-analysis [S5] finds context-specific association but high heterogeneity, not stand-alone prediction. Neither provides validated running-specific thresholds. |
| E-6 | Session-RPE and coached/observed RPE may diverge particularly for 'easy' training; evidence certainty is low. | **SYSTEMATIC REVIEW** [S6]; collect athlete perception rather than treating coaching intent as actual strain. |

### Decision matrix (research hypotheses; no winner established)

| Criterion | Distance-first P-D | Scalar load-first P-L | Hybrid P-H |
| --- | --- | --- | --- |
| Transparent prescribed distance and easy minutes | High | Depends on load-to-distance conversion | High |
| Preserves threshold hard-work seconds and long-run task | With explicit steps | **Not guaranteed** by scalar total | Designed to preserve separately |
| Responds to hills/heat/RPE | Needs observational overlay | Often possible if input supports it | Adds context without collapsing exposure into one number |
| Handles absent/unreliable HR | Feasible | Depends on metric | Designed to fall back to observed km/time + UNKNOWN internal load |
| Protects mechanical exposure | Basic km/hill proxies | Not guaranteed by physiological scalar | Separate duration, distance, descent, and warning/context |
| Athlete understanding | Straightforward | Dynamic km goal may surprise | More detailed, needs compact rationale |
| Implementation/data complexity | Lower | Medium to high/calibration dependent | Higher; must establish marginal value |
| Risk of equal-load wrong-stimulus substitution | Moderate | High unless constrained | Explicit safeguards proposed |
| Demonstrated long-term effectiveness | **NOT ESTABLISHED** | **NOT ESTABLISHED** | **NOT ESTABLISHED** |

**Research target:** P-H should win only if it has *measurable value* under independent tests and remains understandable; do not assume more metrics automatically mean better coaching.

## 2. Minimum record and policy input contract

**Source and version:** athlete_id pseudonym, generator/policy version, scenario seed, source type `synthetic` vs `real_opt_in`, timestamps and timezone, device/model if available. Do not export private FIT recordings or identifying data.

**Athlete state:** goal type, target date (or none), discipline mix, validated recent repeatable run capacity, preferred/available days, min/typical/maximum observed weekly km with their evidence quality, longest reasonably repeatable run, qualitative training age, injury/illness/under-recovery flags when volunteered, actual known threshold calibration vs unknown.

**Per-session planned external:** workout id/type, ordered step tree, duration vs distance per step, work target modality, warm-up/cool-down, recovery mode and duration, planned total duration/km if derivable, hard-step seconds, intended aerobic easy minutes, long-run distance, terrain expectation.

**Per-session observed external:** recording status `completed|partial|skipped|unplanned|unknown`, actual distance/time (timer vs elapsed distinct), elevation/ascent/descent, trusted recording segments, pace distribution, pauses, source quality and incomplete/missing fields (null rather than fabricated 0).

**Per-session observed internal:** timestamped HR if trustworthy, session RPE with scale and timing, fatigue/sleep/self-report if available, device-derived load if returned with clear origin **as an input, not a trusted transferable target**.

**Context:** temperature and humidity source, route class (road, tight loop, trails), gradient, surface, heat/acclimation proxy unknown unless independently available; previous day load and schedule.

**Policy output:** `KEEP|LOCAL_EDIT_PROPOSED|REBUILD_PROPOSED|DEFER_UNKNOWN|RECOVERY_OPTION`, before/after prescription, which dimensions change, explanation, confidence/limitations, capacity or spacing violations, approval state, exact counterfactual input and source.

### Missing-data rules

- `skipped` must be explicit; no activity record is **UNKNOWN**, not proof of a miss, rest, low load, or full recovery.
- Missing HR does not mean zero internal strain, absence of elevation does not mean a flat route, and missing RPE does not mean easy.
- If current data cannot justify a numeric load equivalence, report **NOT_COMPUTABLE** and prefer cautious unchanged schedule or a request for more input; do not auto-fill a 'fitness score.'
- Counterfactuals vary exactly one named factor at a time where possible, except for explicitly coupled feasibility constraints.

## 3. Proposed non-negotiable policy test invariants (original research criteria)

1. **Prescribed capacity:** never exceed a *verified, configured* weekly maximum due to progressive target multiplication or rounding. If no capacity ceiling is known, do not infer one from a race performance alone.
2. **Hard-work nonfungibility:** equality of km or one score never automatically makes threshold seconds or recovery spacing equivalent. No replacing missed threshold with an unplanned easy run or vice versa without a separately justified new prescription.
3. **No auto 'debt repayment':** a skipped workout is not a debt. Missed hard work cannot silently double later repetitions, collapse recovery, or extend the long run.
4. **Phase protection:** taper, deliberate recovery, and return-from-break are separate states; a goal date must not be kept at the cost of an unsupported sharp load jump.
5. **Source fidelity:** record prescribed step time, actual step time, timer vs elapsed, walking-recovery movement, true unknowns and sensor confidence; no GPS loop estimates as ground truth.
6. **Independent load axes:** never convert uncalibrated HR load into mechanical impact or aerobic minutes. If one metric rises from heat alone, retain the environmental context and do not automatically reduce useful aerobic exposure to chase an arbitrary scalar.
7. **User agency:** significant prescription changes require reason + reversible preview + approval; mere natural-language workout feedback does not silently change a plan.
8. **Duration/frequency feasibility:** workouts fit available days and time budgets, no duplicate long run, no hidden quality adjacency; configurable rather than universal hard-coded day-count safety thresholds.
9. **Reproducibility:** identical complete state and seed yield identical proposal and reason; differences in source data and version are explicitly surfaced.
10. **No injury safety guarantees:** avoid "safe," "injury-free" or validated readiness classifications from ACWR/TRIMP/HRV alone.

These criteria are *software and methodological safeguards*. They are not established individualized medical or sports-science thresholds; empirical dose limits and efficacy require future evaluation.

## 4. Experiment 003: policy counterfactual tests (not executed)

### Comparison setup
- Use the existing local Athlete Simulation Lab when available, but **verify its current checkout and prior dose baselines** first; do not assume historical local Git commits are pushed or the policy API supports these fields yet.
- Keep test data and original synthetic source read-only. No production repo changes in this batch; the linked CSV is a **test specification**, not executable test code.
- Athlete fixtures: **A70** (60–70 km/week experienced runner, long run 15–20 km, higher aerobic durability than speed); **A25** (25 km/week developing runner); **AN** (incomplete history); **AC** (cross-training). These are **fictional**, not the actual user's clinical record.
- Compare P-D/P-L/P-H against preserved lab policy baselines only if interfaces are actually compatible. Do not attribute any result to real Kaizen/Runna/Garmin implementations.
- Register control and counterfactual input states; evaluate repeated seeds across all policies plus one-factor contrasts. Use exact numeric work-step and duration fields only if observed or part of a synthetic fixture. All scenarios registered `NOT_RUN`.
- **Key falsification:** If a hybrid policy cannot preserve required invariants, has unexplained changes, or needs unusable inputs, reject it as a candidate even if its own scalar test score looks good.

### Outcome report, with denominators and uncertainty
**Structural hard stops:** over configured capacity, infeasible time/day, contradictory session totals, unsupported missing-as-zero, hard-work double-up after skip, hidden material revision. Count scenario-level hard-stop failures (target **zero** for a candidate in held-out registered cases; this is a **software acceptance criterion**, not a guarantee of real-world safety).

**Behavioral diagnostics (no assumed 'right prescription'):** delta total km, easy minutes, threshold/hard seconds, long-run km/share, easy/long/quality placement, moved/deleted sessions, recovery time, percentage of outputs requesting human approval, percentage `DEFER_UNKNOWN`, sensitivity to heat/hills, source provenance preserved, and explanation completeness.

**Observational outcomes (only with genuine athlete follow-up):** distribution of speed at comparable easy HR across repeated matched-condition road efforts, validated threshold performance and long-run durability; adherence, RPE, and adverse symptom reporting when available. Synthetic data cannot verify fitness gains.

**Calibration/ablation:** compare hybrid with and without RPE, HR, heat, gradient, and prior-run outcome; ablation should change decisions only when the removed field is relevant and trustworthy. All missingness contrasts should remain conservative and auditable. Don't tune scoring thresholds and evaluate them on the same generated scenarios.

**Performance test:** deterministic repeated run for same seed/input, traceability of every used field and reason, no fabricated numeric load, bounded complexity for offline Android eventual integration. Actual runtime benchmarks **not run**.

### Interpretation rule
Claim `STRUCTURALLY_VALID` only if tested checks pass; `BEHAVIORALLY_PLAUSIBLE` only after adversarial human review; `COACHING_EFFECTIVE` only after external longitudinal validation. Passing any earlier gate cannot imply the next.

## 5. Next executable work and blocked branches

1. **Public-source Batch 5-next** (does not need private credentials): evidence-review Fitbod strength autoregulation, Thenx/Calistree progressions, Freeletics mixed-conditioning adaptation; separate observed behavior from marketing and validate supported plan controls. Add modality-specific load dimensions, not a joint "workout points" score.
2. **Runna Experiment 002** current mobile Drafts: **0/16** controlled personalized cases as of this document; authorized mobile captures required. Baseline repeat first, then one-factor variants; log rejections. Do not fabricate.
3. **Garmin Run Coach** controlled recommendation traces: no current paired output yet. Requires compatible watch/Connect timestamped screenshots; distinguish DSW and Expert. Device metrics cannot be assumed.
4. **Local lab Experiment 003 execution** when checkout is accessible and consented: port only the preregistered cases with baseline checks, maintain read-only data, report failures and invariant violations. No automatic Engine 2.0 promotion.
5. If pursuing numeric training-load models, separately register method, calibration cohort, units, sample applicability, failure modes, outcome and comparative baselines; never adopt Kaizen or Garmin proprietary numeric weights.

## 6. Source register

- **[K1]** Kaizen, *How It Works* (vendor, consulted 2026-11-03): https://runkaizen.com/how-it-works
- **[K2]** Kaizen iOS store description (vendor text): https://apps.apple.com/us/app/kaizen-adaptive-run-training/id1638679224
- **[S1]** Impellizzeri, Marcora and Coutts (2019), *Internal and External Training Load: 15 Years On*: https://pubmed.ncbi.nlm.nih.gov/30614348/
- **[S2]** Kontro et al. (2026), *The three-dimensional impulse-response model*; theoretical framework, acknowledged untested empirical superiority and competing interests: https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0341721
- **[S3]** 2014 internal-load comparison noting interval HR-averaging limitation, swimming sample (not directly generalizable to running): https://pubmed.ncbi.nlm.nih.gov/24942164/
- **[G1]** Garmin Philippines (2025), *How to Quantify the Impact Load of Running?* (vendor, Forerunner 970 launch context): https://www.garmin.com/en-PH/blog/running/how-to-quantify-the-impact-load-of-running/
- **[S4]** Impellizzeri et al. (2020), *Acute:Chronic Workload Ratio: Conceptual Issues and Fundamental Pitfalls*: https://pubmed.ncbi.nlm.nih.gov/32502973/
- **[S5]** 2026 team-sport ACWR multilevel meta-analysis, large heterogeneity and not stand-alone predictive: https://pubmed.ncbi.nlm.nih.gov/42662491/
- **[S6]** 2022 systematic review of coach-vs-athlete session-RPE, certainty very low: https://pmc.ncbi.nlm.nih.gov/articles/PMC8897524/

**Audit statement:** Original analysis and preregistration only. No controlled Runna/Kaizen/Garmin user-account activity; no new lab simulation or athlete performance outcomes; no normalized scalar or thresholds derived in this batch. All vendor claims and independently proposed safeguards are labeled by type.
