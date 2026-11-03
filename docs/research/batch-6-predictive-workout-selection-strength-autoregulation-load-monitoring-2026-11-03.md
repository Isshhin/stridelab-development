# Batch 6: Predictive workout selection, strength autoregulation, and longitudinal load monitoring

**Date checked:** 2026-11-03 (Asia/Manila). **Track:** Independent StrideLab Unified Coaching Engine 2.0 Research Lab. **Status:** VENDOR-DOCUMENTATION REVIEW + LITERATURE REVIEW + REGISTERED EXPERIMENTS. **App-account experiments:** none. **Simulator executions:** none. **Production code and datasets:** unchanged.

Companions: [Batch 4 load policy comparison](batch-4-load-first-vs-distance-first-architecture-2026-11-03.md), [Batch 5 discipline coordination](batch-5-cross-discipline-progression-and-coordination-2026-11-03.md), [Experiment 005 cases](experiments/experiment-005-adaptation-state-and-load-metrics.csv).

**Evidence labels:** VENDOR = vendor's publicly documented behavior; SCIENCE = published research, limited to its data and design; PROPOSED = original StrideLab engineering/research hypothesis; UNKNOWN = unverified implementation/causal outcome. We have no authenticated input/output traces for these vendor apps. A commercial feature description is not proof of physiological effectiveness.

## 1. TrainerRoad: important 2026 distinction from older Adaptive Training documentation

**VENDOR:** TrainerRoad's updated 2026 product documentation describes a **28-day (four-week) Training Simulation Window**. Completing, skipping, adding, or editing workouts can cause future recommendations and projected fatigue to update; yellow "Avoid Intensity" and red "Rest Recommended" states can replace a planned session with an easier session or rest. Restored alternatives remain available. [TR1, TR2]

**VENDOR:** The June 23, 2026 updated blog states that *current TrainerRoad AI workout selection does not rely on Workout/Progression Levels as direct selectors* the way the older adaptive system did. It describes multi-option simulation using training history, power, HR, RPE, goals and schedule. The exact model, number of simulation states actually used per athlete, optimization objective and calibration have not been independently checked. [TR1]

**VENDOR:** Related **Athlete Levels** (support terminology, 1–10, per zone) summarize individual capability and adjust with successful workout execution, FTP changes and inactivity; **Workout Levels** (1–10, workout attributes) describe a workout independent of the person and consider interval structure, length, repetitions and recovery. Workout Levels should not be treated as strictly exchangeable between exercises, even within the same zone. **AI Predicted Difficulty** is a personal forecast of a particular workout and its six subjective outcome bins; the difficulty display is *informational* and by itself does not change the session. [TR3, TR4]

**IMPORTANT VERSION BOUNDARY:** Historical articles describe Progression Levels driving stepwise selection. That is not an accurate description of the newer June 2026 AI selection claim. Keep previous comparisons time/version stamped, and distinguish *workout objective difficulty*, *athlete capability*, *personal predicted difficulty*, *actual outcome*, and *future plan selection*. Do not borrow an arbitrary 1–10 zone score as a StrideLab training threshold.

**UNKNOWN:** Whether any user's real 28-day simulation is deterministic, the actual adaptation/forecast accuracy, complete data weighting, and how sensor gaps are treated. No real account test was performed. TrainerRoad is primarily a cycling reference, not a source of verified running prescription coefficients.

**PROPOSED ARCHITECTURE TEST:** Compare (A) reactive stepwise progression and (B) multiweek, phase-constrained proposal simulation on the *same held-out synthetic athlete histories*, ensuring the proposed forecasts are visibly labeled uncertain and no exact performance gains are claimed. A true training benefit needs independent real-world validation, not just better fit to the simulator's own generative assumptions.

## 2. RP Hypertrophy: muscle-group dose and per-exercise allocation

**VENDOR:** RP's official app and Google Play descriptions say it uses logged reps/loads and the athlete's pump, soreness and workload feedback to adapt sets/reps and week-to-week hypertrophy training. The Meso Builder permits muscle-group priority choices, but no published vendor source establishes a reliable universal physiological mapping of one pump or soreness rating to additional sets. [RP1, RP2]

**VENDOR, precise new observation:** RP's release notes for version **1.3.1 on July 13, 2026** explicitly change next-week **set allocation across exercises within a muscle group**. Previously the system distributed the group's assigned sets evenly; now it *preserves the prior week's exercise distribution* before adding or removing sets. This is a documented behavior change, not evidence about how the total muscle-group set count was computed. [RP3]

**VENDOR:** Version 1.3.10 (Aug 19) introduced visual distinction between **skipped and completed** workouts; version 1.3.6 (Aug 3) fixed display of an already entered **joint-pain rating**; version 1.3.37 (Oct 26) distinguished official/community templates. These release observations support accurate feedback provenance and type, not an inference that joint-pain ratings automatically trigger a specific algorithm. [RP3]

**SCIENCE:** A 2025 systematic review and network meta-analysis examines autoregulated methods (APRE, RPE, velocity based) for maximal strength, but its outcome **is not muscle hypertrophy** and the relative treatment rankings are not a reliable app-progression policy. A separate 2025 systematic review (26 studies) finds RIR estimates more accurate closer to failure and with higher relative loads; self-reported RIR should therefore be stored with uncertainty and context, not translated into precise objectively measured muscle fatigue. [S1, S2]

**PROPOSED STATE SEPARATION:** Record (i) group-level weekly target sets, (ii) actual sets with exercise IDs / primary-secondary muscle roles, (iii) per-exercise allocated sets, (iv) rep/load/RIR and subjective pump/soreness/joint-pain evidence independently, and (v) recovery/phase context. If only the allocated exercise distribution changes, total group dose should not silently change; if the group total changes, preserve and explain the evidence and explicit preference. These are proposed software invariants, not claimed RP internal checks.

**UNKNOWN:** RP total-sets selection thresholds, precise pain/soreness weighting, actual regeneration for particular inputs, and hypertrophy/strength results attributable to its personalized algorithm. No logged-in app experimentation here.

## 3. TrainingPeaks: useful temporal metrics with documented limits

**VENDOR:** TrainingPeaks' Performance Management Chart tracks training stress from intensity and duration using sport/data-appropriate estimates. Cycling power TSS, running pace/elevation rTSS, swimming sTSS, HR-derived hrTSS, TRIMP-based tTSS and manually entered TSS* are distinct methods, with separate requirements/limitations. TrainingPeaks explicitly says hrTSS/tTSS are less accurate for highly variable efforts, and that rTSS requires accurate time series/grade and running threshold pace. [TP1]

**VENDOR:** By default, **CTL** (longer-term load) uses a 42-day exponentially weighted daily TSS estimate; **ATL** (short-term load) uses a 7-day exponential estimator. **TSB/Form for today = yesterday's CTL minus yesterday's ATL**, *not* same-day CTL minus same-day ATL. TrainingPeaks explicitly cautions TSB is not a predictor of performance. CTL may include forecast planned TSS in some surfaces until actual workout differs. [TP2, TP3, TP4]

**VENDOR, newer interface:** TrainingPeaks September 10, 2026 "Athlete Home Performance Insights" exposes particular ramp-rate and TSB label bands and adds Recovery Insights (HRV/RHR/sleep) and Training Insights (RPE/Feeling). These are **product-specific display bands**, not general injury-risk classifications or medically validated readiness cutoffs. Do not transplant thresholds such as TSB −30 into StrideLab safety rules. [TP5]

**VENDOR:** TrainingPeaks itself describes limitations of TSS and IF in hard interval training; an identical single composite stress number can conceal different workout step structures. As an example, a 60-min mixed-intensity workout is not automatically equivalent to 60 min easy because a composite total happens to match. [TP6]

**PROPOSED TEMPORAL ACCOUNTING MODEL:** If numerical stress is ever calculated by StrideLab, retain *method, units, threshold calibration date, data completeness, data source, plan-vs-actual status, and confidence*, and keep its 7/42-day EMA as **diagnostic estimates**, not a single readiness gate. Do not compute a "TrainingPeaks-compatible" proprietary rTSS from FIT files without a verified formula or license. We can implement a plainly named, fully documented **independent generic EMA** to compare time-constant behavior (e.g., EMA_now = EMA_prev + (X_now-EMA_prev)/N) in synthetic experiments; **this is arithmetic methodology, not their proprietary implementation**.

**Illustrative arithmetic only, not TrainingPeaks app output:** for yesterday CTL 50, ATL 55 and today's **hypothetical** 70-unit training score, generic updates with N=42 and N=7 give approximately CTL 50.476 and ATL 57.143. Today's TSB from prior state = −5. Next day's TSB from updated state = about −6.667. A computation that uses −6.667 for today's TSB commits an off-by-one-day error. No health or training advice follows from these illustrative values.

**UNKNOWN:** numerical comparability of load methods across sports/athletes, effectiveness of TrainingPeaks UI label ranges, behavior after large threshold-calibration changes, and how missing uploads influence an athlete's actual configured display. No authenticated athlete account was used.

## 4. Independent Engine 2.0 research design (PROPOSED, not approved for production)

### Four state layers
1. **Evidence and observation** — immutable actual session records, completed/partial/skipped/unknown states, workout step trees, source and measurement confidence.
2. **Capabilities / state estimates** — domain-specific running aerobic efficiency and threshold exposure, strength exercise performance/RIR, calisthenics skill evidence and conditioning tolerance; uncertainty explicit. A comparative workout difficulty score is a *property of a particular designed workout*, not a person.
3. **Near-term candidate selection** — constrained alternatives conditioned on goal phase, recent workload, time, equipment, fatigue reports and user approval; candidate outcomes are forecasts, not guaranteed performance.
4. **Plan continuity and audit** — proposed changes have scope (one session, week or phase), before/after hard-step time, easy minutes, long run distance, strength muscle sets and progression edges; preserve user sign-off and rollback.

### Key design hypotheses to falsify
- A user with high easy aerobic volume but developing threshold ability must not be labeled capable of any duration threshold workout merely because total km is high.
- A forecast of "hard" or "likely to fail" is *not itself evidence that an athlete failed*, and must not silently change plans.
- An external activity can affect future fatigue estimation, but a synced run with unreliable HR/pace cannot acquire fictional TSS or an exact muscle-recovery percentage.
- A single additional subjective feedback answer is evidence **about the user's experience**, not a measured muscle-growth outcome.
- Workouts with equal TSS-like score, session duration or count may have very different aerobic/anaerobic exposure and movement overlap.
- No automatic "make-up" or abrupt jump in work dose after missed sessions, travel or deload; prioritize feasibility and documented user approval.
- Keep planned load forecasts separate from completed load; avoid lookahead using future sessions as already observed.
- When a daily stress score is missing, preserve UNKNOWN; do not automatically infer a rest day or fill with zero unless explicitly confirmed no training and the calculation's policy permits zero.

### Experiment 005: registered counterfactuals only

[Machine-readable registry: 18 cases](experiments/experiment-005-adaptation-state-and-load-metrics.csv). Every case is currently **NOT_RUN**. Evaluate a simple reactive progression baseline, a multiweek proposal simulator *if implemented*, and the existing Engine Research Lab baselines only after checking the actual local lab version and interfaces. Record simulator assumptions and independent violation checks; avoid optimizing and grading on the same synthetic generative score.

Suggested reporting:
- Structural invariants: forbidden unapproved plan changes, missed-as-completed, lookahead, faulty today vs yesterday TSB, wrong exercise-specific vs group-dose allocation, arbitrary equivalence of unlike loads, over-capacity weeks and session scheduling violations.
- Diagnostic sensitivity: what changes after one missed workout, changed RIR report, new strength performance, newly logged ride, changed threshold calibration or missing HR; include false change and false stability rates.
- Forecast evaluation: only when real opt-in observed completion and outcome data become available, use calibration by individual and dose zone, proper train/test temporal split and outcome-specific error; do not claim predictive effectiveness from synthetic-only regression.
- For the user-priority running endpoint, repeated speed at *matched easy HR and comparable road conditions* is separate from CTL/TSB, performance simulations, FTP, and exercise difficulty.

## 5. Next steps and blockers

**Next independently executable batch (public sources):** consolidate prior batches into a single **Engine 2.0 candidate architecture + source-to-test traceability matrix**. Select a minimal model that is explainable, offline-friendly, and compatible with user approval. Identify which metrics need real data, which are research-only, and which can be validated structurally. Do not implement a prototype simply because sources discuss AI simulations.

**Local test lane:** Experiment 003 (18 cases), Experiment 004 (22 cases), Experiment 005 (18 cases) all NOT_RUN; execute only when independent Python lab checkout and frozen data manifest can be inspected. The local lab is separate from this GitHub cloud research branch. Existing synthetic-source data remain read-only. Use local scenario IDs/seeds, holdouts and regression baselines.

**Vendor access lane:** Runna Experiment 002 still 0/16 authenticated app cases; Garmin Run Coach paired before/after still unavailable. TrainerRoad/RP/TrainingPeaks account-controlled cases also not conducted. Do not report observed adaptations without records.

**Controls:** PR #34 draft/unmerged. Engine 1.0/1.5, Trackside UI PR #36, saved athlete data and local personal FIT files untouched. Keep daily 09:00 Asia/Manila Runna and Garmin research automation; do not duplicate.

## 6. Source ledger (primary/official where possible)

- [TR1] TrainerRoad, *What's New With TrainerRoad AI?*, reviewed Jan 21 and updated Jun 23, 2026: https://www.trainerroad.com/blog/whats-new-with-trainerroad-ai/
- [TR2] TrainerRoad Support, *What is TrainerRoad's Fatigue Detection?*, updated Mar 6, 2026: https://support.trainerroad.com/hc/trainerroad-support/articles/20753792083995-what-is-trainerroad-s-fatigue-detection
- [TR3] TrainerRoad Support, *Athlete Levels*, updated Jan 21, 2026: https://support.trainerroad.com/hc/trainerroad-support/articles/4404665021595-athlete-levels
- [TR4] TrainerRoad Support, *TrainerRoad AI* (links to Workout Levels, AI Predicted Difficulty, 28-day Simulation): https://support.trainerroad.com/hc/trainerroad-support/en/categories/general-usage-trainerroad-ai
- [RP1] RP Strength, *Hypertrophy App* (vendor): https://rpstrength.com/pages/hypertrophy-app
- [RP2] RP Strength Google Play listing, updated Sep 8, 2026: https://play.google.com/store/apps/details?id=com.rp.hypertrophy
- [RP3] RP Strength, *Hypertrophy release notes*, v1.3.1 Jul 13, v1.3.6 Aug 3, v1.3.10 Aug 19, v1.3.37 Oct 26, 2026: https://releases.rpstrength.com/hypertrophy
- [TP1] TrainingPeaks Help, *Training Stress Scores Explained*: https://help.trainingpeaks.com/hc/en-us/articles/204071944-Training-Stress-Scores-TSS-Explained
- [TP2] TrainingPeaks Help, *Fitness (CTL)*: https://help.trainingpeaks.com/hc/en-us/articles/204071884-Fitness-CTL
- [TP3] TrainingPeaks Help, *Fatigue (ATL)*: https://help.trainingpeaks.com/hc/en-us/articles/204071894-Fatigue-ATL
- [TP4] TrainingPeaks Help, *Form (TSB)*: https://help.trainingpeaks.com/hc/en-us/articles/204071764-Form-TSB
- [TP5] TrainingPeaks Help, *Athlete Home Performance Insights*, Sep 10, 2026: https://help.trainingpeaks.com/hc/en-us/articles/41077784209165-Athlete-Home-Performance-Insights
- [TP6] TrainingPeaks, *Understanding the Limitations of TSS and IF During HIIT Training*, updated Jun 9, 2026: https://www.trainingpeaks.com/blog/understanding-the-limitations-of-tss-and-if-during-hiit-training/
- [S1] Huang et al. (2025), systematic review/network meta-analysis of autoregulated maximal strength (not hypertrophy): https://pubmed.ncbi.nlm.nih.gov/40791980/
- [S2] Russo et al. (published online Oct 11, 2025), systematic review of 26 studies on RIR estimate accuracy: https://doi.org/10.1080/10833196.2025.2564026

**Audit:** All primary references are vendor self-descriptions; no reverse-engineered code, controlled paid-app user plan output, empirical forecasts, local simulation execution or causal athlete-performance advantage was established by this batch.
