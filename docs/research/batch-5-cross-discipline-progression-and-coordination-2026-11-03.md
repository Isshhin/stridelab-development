# Batch 5 — Cross-discipline coaching: strength, calisthenics and conditioning

**Reviewed:** 2026-11-03 (Asia/Manila). **Track:** independent StrideLab Unified Coaching Engine 2.0 research. **Status:** evidence review plus original hypothesis/test preregistration; **NO COMPETITOR ACCOUNT EXPERIMENTS OR SIMULATOR RUNS**. **Production and dataset changes:** none. This research adds context to [Batch 4's multidimensional load comparison](batch-4-load-first-vs-distance-first-architecture-2026-11-03.md) and does **not** promote an engine implementation.

**Evidence convention:** `VENDOR-DOCUMENTED` is a public product statement or visible public program, not independently verified proprietary mechanism; `SCIENCE` is a cited academic result within its population and methods; `HYPOTHESIS` is StrideLab's original proposed design; `UNKNOWN` has no adequate observation. The linked [Experiment 004 registry](experiments/experiment-004-multidiscipline-coordination.csv) consists solely of `NOT_RUN` tests.

## Executive outcome

**HYPOTHESIS:** A cross-discipline coordinator should arbitrate *constraints and overlap* without replacing discipline-native progression logic. It should preserve separate running aerobic and quality exposures, strength work sets / load / rep reserve, calisthenics skill progression evidence, conditioning work/rest and impact exposure. There is **no established single universal 'workout point'** that converts these into equivalent physiological stimuli. More fields do not prove superior coaching; this architecture requires independent comparison against a simple scheduling baseline.

## 1. Fitbod: evidence for strength progression and estimated muscle recovery

**VENDOR-DOCUMENTED** from current Help Center:
- Exercise choice considers goals, experience, available equipment, split, recent muscle-group recovery, variation preference and user feedback. Skips, swaps, deletions and manual additions influence future recommendations. Equipment is a hard filter in the documented selector. [F1]
- Weight, sets and reps depend on *logged* training, with goal-specific ranges, RiR (reps in reserve) feedback and occasional Max Effort Days. Max Effort Days are structured calibration sessions, not a rule that every session is maximal. Sets/rep/weight outputs and proprietary mStrength weights are **not** disclosed; no current authorized account tests were run. [F2,F3]
- Its muscle recovery state is a **0–100% proprietary modeled estimate**, not a direct tissue recovery measurement. Fresh groups are prioritized but partially recovered groups may still appear with modified exercise selection. The user can override the percentage. [F4]
- Fitbod says imported workouts via Apple Health / Android Health Connect can influence muscle recovery; its more detailed legacy page gives the example of not recommending a hard leg workout immediately after a synced long run. Support and blog material describe full modeled recovery as *six* or *up to seven* days depending on page. **Do not pick either number as a universal physiological threshold**; this is a product-model convention and pages are not fully consistent. [F4,F5]
- One Fitbod blog says its selector plus capability recommender form two model components, but that label is not confirmation of source-code boundaries. [F6]
- Public marketing/support pages give inconsistent total historical workout counts (for example 87m and 400m); neither marketing count is needed for an Engine 2.0 inference. [F1,F6]

**Useful original inference:** Separate **exercise-level capability** (load/reps/RiR history) from **modeled muscle workload/recent exposure** and from an independently observed **athlete symptom report**. Never classify a muscle as medically "recovered" because a vendor-style percentage reaches 100.

**UNKNOWN:** actual dose progression equations, weighting of secondary muscles, imported run-to-muscle equivalence, whether skipped vs painful work is distinguished appropriately, and how Fitbod would behave on the same athlete after identical counterfactual input swaps.

## 2. Thenx: visible curriculum and skill ladders, not proven adaptive engine

**VENDOR PUBLICLY OBSERVABLE:** Thenx lists beginner/intermediate/advanced programs and technique guides. Its published **Introduction To Calisthenics** sequence has weekly skill-area days (pull-ups, push-ups, pistol squats, dips, L-sit), explicit rest days, and a later challenge week. A published one-arm pull-up technique guide has an ordered list from scapular preparation/holds, negatives and assistance toward the full skill. [T1,T2,T3]

The marketing site says it offers personalized training and step-by-step progressions. [T4] **This does NOT establish** that the visible weekly template dynamically regenerates for an individual after a failed set, moves rest days automatically, or uses a quantified recovery model. The public program content may be a designed curriculum. Different program pages and app versions could behave differently.

**Original inference:** Store a curated progression sequence and movement constraints; do not invent individual readiness thresholds from the static website. Provide regressions and technique guidance, but a successful rep count alone is not proof of advanced skill proficiency or safe form.

**UNKNOWN:** actual personalized generator input-output changes, criteria for earning mastery, whether difficulty is automatically changed by feedback, and personalization behavior beyond the catalog.

## 3. Calistree: skill graph and bounded progression

**VENDOR-DOCUMENTED:**
- Calistree presents a tree of easier/harder exercise variations and separate alternative variations, plus visible indications of exercise completion/mastery. [C1]
- Its Guided Training page says it uses logged experience in strength, cardio, flexibility, and balance to choose exercises; it may nudge **reps**, **hold seconds**, or **sets** within user-defined ranges and suggest an alternative if a movement stalls. [C2]
- The website states goals, training days, gear/location and time inform generated multiweek plans; logged training influences progression. The site supports multiple disciplines rather than only calisthenics. [C3]
- Historical exercise data can be manually entered to populate mastery estimates, according to its Help Center. [C4]

**Original inference:** Model `progression_edges` (direct prerequisites), `variation_edges` (parallel regressions/alternatives), `mastery_evidence`, `quality_report`, and `workload_units` separately. Every edge requires human-curated biomechanical validity; a diagram is not a universal prerequisite tree for every athlete.

**UNKNOWN:** precise thresholds for increase/hold/regress, how fitness-level estimates combine axes, quality of exercise movement detection, and observed current generator plans under controlled inputs.

## 4. Freeletics: user-controlled per-session change vs ongoing Coach history

**VENDOR-DOCUMENTED:**
- An **Adapt Session** workflow can regenerate an individual bodyweight session with less time, no equipment, no space-dependent exercises, quiet alternatives, limited muscle focus, or changed difficulty, depending on the selected Training Journey. Some changes require active internet access. [L1]
- The Freeletics product article explicitly states Adapt Session changes the **current day only**, not the entire training week. Separate training preferences can affect upcoming sessions. [L2,L3]
- Coach history and post-session feedback inform subsequent recommendations; Freeletics account level is **not** itself its workout-intensity selector. After a break longer than a month, the Coach may offer an easier session. [L4]
- Public Running Journey notes describe interval vs PB-focused workouts and adaptation by past performance and feedback, but no published fixed quantitative conversion to a running-only load is provided. [L5]
- Logging optional workouts *outside* a Training Journey is distinct from completing Coach-prescribed sessions. [L6]

**Original inference:** Make local `SESSION_ALTERNATIVE` proposals distinct from `WEEK_REPLAN` and `BLOCK_REBUILD` actions. A feasible replacement for a 60-minute bodyweight session is not automatically equal to its training dose; record what was actually dropped/changed and whether the user accepted it.

**UNKNOWN:** specific workout-scoring weights, long-term outcomes, actual step-tree effects of each adaptation and proprietary fatigue/recovery formulas.

## 5. Concurrent running and strength: what evidence can and cannot support

**SCIENCE, not vendor behavior:** A 2021 meta-analysis of 43 studies found that combined aerobic plus strength training generally did **not** meaningfully compromise maximal strength or hypertrophy compared with resistance training alone, but explosive strength gains were lower in some conditions, especially same-session training. A separate 2021 subgroup analysis reported strength interference in **trained** athletes when resistance and endurance were closely combined in the same session rather than spaced out, with substantial population/context limitations. [S1,S2] A 2026 umbrella review of 17 meta-analyses found concurrent training generally supports aerobic and strength outcomes, but highly trained/elite populations remain underrepresented. [S3]

**Boundary:** These results do not justify a universal "never leg day before threshold", "must wait 48h", or "two hours guarantees recovery" rule for all users. Sequence, performance priority, athlete history, strength exercise characteristics, time budget, exercise proximity and symptoms matter.

**Original research hypothesis for high-volume runners:** flag and explain *overlap* between hard running/long runs and recent lower-body high-demand strength, lower-body plyometrics, jump-heavy conditioning, or demanding calisthenics leg sessions. Preference is a **proposed scheduling conflict score with provenance**, NOT a validated physiological fatigue score and not a blanket prohibition. For an athlete targeting faster easy pace and threshold, protect key run quality and long-run continuity without unnecessarily deleting beneficial strength work.

## 6. Proposed Engine 2.0 cross-discipline data contract (HYPOTHESIS)

| Dimension | Planned and observed axes to retain | Distinguish from |
| --- | --- | --- |
| Running | easy running min; threshold/high-intensity **work step** min; warm-up/cooldown, km, long-run distance/duration, hills and route class, reported HR/RPE | general workout-card distance ≠ threshold-work distance; matched-HR pace ≠ lab economy |
| Strength/gym | exercise ID; muscle roles; movement pattern; sets x achieved reps x external load (when meaningful), relative intensity/RiR, recovery durations, equipment, eccentric/plyometric exposure if recorded | `sets × reps × load` is external tonnage, not a universal hypertrophy score |
| Calisthenics | skill node; progression prerequisites; support/assistance type; completed reps/holds; technique competency report; mobility/equipment requirements | a hold in seconds cannot be converted blindly into one-rep max or running km |
| Conditioning | work/rest intervals; exercise types; round completion; total moving time, jumps/landing count where valid, RPE/HR where valid | total timer min ≠ high-intensity time; jumping fatigue ≠ endurance volume |
| Cross-domain | timestamp; athlete priority; muscle/movement overlap, same-day minutes, session spacing, voluntary soreness/pain, reliability, missing data, user approval | modeled 'recovery percentage' ≠ objective tissue readiness |

**Separation of responsibilities (HYPOTHESIS):**
1. Discipline-native prescription engines produce independently legible running, strength, calisthenics and conditioning work.
2. An **arbiter** inspects constraints, same-day time budgets (up to two sessions/day if selected), recent observed exposure, available equipment, possible quality/long-run/strength overlap and athlete's stated priority.
3. It issues `KEEP`, `SWAP_EXERCISE`, `REDUCE_OR_REORDER_SESSION`, `MOVE_SESSION_PROPOSED`, `DEFER_UNKNOWN`, or `ASK_ATHLETE`; every proposal includes original vs revised step-level dose and confidence limitations.
4. Distinguish **local session adaptation** from weekly load changes and long-horizon plan regeneration. Require user approval for significant changes and preserve an immutable before/after record.
5. Do not infer "recovered" from missing logs or apply a vendor's muscle percentage as ground truth. Preserve manual feedback with timestamp and source.

## 7. Comparison baseline, invariants and falsifiable scenarios

**Baselines to compare later:** B-S (`separate domain plans + feasibility-only daily time rule`) vs B-O (`fixed qualitative overlap warnings`) vs B-H (`proposed context-aware arbitration`). These are **new hypothetical research baselines**, not current policies confirmed to exist in the local lab, and no implementation has been performed.

**Structural/test invariants, not physiological safety guarantees:**
- Do not add together running km, strength tonnage, holds, and conditioning rounds as interchangeable load.
- If equipment is absent, no proposed exercise may require it; substitution needs an annotated movement/goal match.
- A skill advance needs relevant completed evidence; unknown or poor reported form cannot be treated as mastery.
- Partial, missed, modified, unplanned and absent data retain distinct types.
- Do not silently remove running threshold steps, change a race taper, or replace a planned quality run with high-intensity conditioning while claiming the same stimulus.
- Schedule within available days and time budget. Same-day sessions maintain separate identities, logs and recovery.
- An upper-body skill session should not automatically cancel an easy run just because both are workouts; classify only relevant overlapping demands.
- A report of acute pain, injury or illness should prompt conservative deferral/appropriate advice, not a calculated "safe to train" number.
- Do not treat two sessions that satisfy similar total durations as equivalent workout prescriptions.
- Deterministic same input and version should yield same proposal and reason when the experiment uses a deterministic baseline; record all changes.

**Experiment 004:** [22 preregistered scenarios](experiments/experiment-004-multidiscipline-coordination.csv) are all **`NOT_RUN`**. Inputs span high-volume runners plus gym legs, short easy runs and calisthenics, unknown fatigue, manual feedback, missing workout data, two-session availability, skill progressions and regression, prescribed vs adapted duration, conditioning jump impact, and interrupted training. For each simulated policy retain complete input snapshots and output reasons. Avoid changing user activity to manufacture comparison evidence.

**Assessment:**
- Hard-stop structural findings per 22 preregistered cases (target zero where an invariant is enforceable; **software acceptance only**).
- Dose preservation: easy minutes, hard-step minutes, strength work sets/reps/RiR, skill progression decisions, conditioning work/rest, long-run continuity.
- Adaptation transparency: replacement explanation, user approval required, number of changed sessions, evidence confidence, missing data treatment.
- Longitudinal endpoints only in later opt-in real data: matched-condition easy HR speed, threshold output, strength trends, skill quality and adherence. Do not claim simulated structural passes predict injury or performance.

## 8. Next research batches / handoff

**Priority A (public source evidence, independent):** TrainerRoad adaptive cycling difficulty/Workout Levels, RP Hypertrophy App training-volume and progression feedback. Determine whether these add evidence beyond Fitbod; document vendor claims separately. Then connect to TrainingPeaks monitoring as a quantification reference. Do not inflate app comparisons without a new testable decision.

**Priority B (blocked until authorized mobile outputs):** Runna Experiment 002 current Drafts baseline-repeat and controlled variants (0/16 run); Garmin Run Coach recommendation before/after (0 verified controlled pairs); Fitbod/Calistree/Freeletics current-app counterfactuals if appropriate access exists. Do not fabricate numeric coefficients.

**Priority C (local lab, not cloud GitHub):** audit actual local independent Python Athlete Simulation Lab checkout and read-only synthetic corpus, confirm policy baseline/version, then port Experiment 003 (18 cases) and Experiment 004 (22 cases), each `NOT_RUN` presently. Keep protected dataset and personal FIT activities private and unchanged.

**Priority D (model evidence gate):** compare coherent baseline policies on held-out adversarial simulations and independently collected opt-in longitudinal outcomes before considering Engine 2.0 code, let alone a production promotion.

**Automation:** existing Runna + Garmin research task at 09:00 Asia/Manila daily; no duplicate automation. **Research PR #34** stays draft and unmerged; Android Trackside PR #36 and Engine 1.0/1.5 are separate.

## Source ledger

- [F1] Fitbod Help, *How does Fitbod choose which exercises to give me?*, updated Sep–Oct 2026: https://help.fitbod.me/hc/en-us/articles/43489509474455-How-does-Fitbod-choose-which-exercises-to-give-me
- [F2] Fitbod Help, *How does Fitbod decide my sets, reps and weight?*, updated 2026: https://help.fitbod.me/hc/en-us/articles/43489869175063-How-does-Fitbod-decide-my-sets-reps-and-weight
- [F3] Fitbod Help, *Max Effort Day* (2026): https://help.fitbod.me/hc/en-us/articles/360033675553-Max-Effort-Day
- [F4] Fitbod Help, *How does Fitbod know my muscles are still recovering?* (2026): https://help.fitbod.me/hc/en-us/articles/43492170863895-How-does-Fitbod-know-my-muscles-are-still-recovering
- [F5] Fitbod Help, *Muscle Recovery*, older documentation (updated date not confirmed): https://help.fitbod.me/hc/en-us/articles/360006269014-Muscle-Recovery
- [F6] Fitbod, *How Fitbod Generates Your Personalized Workouts* (June 9, 2026): https://fitbod.me/blog/fitbod-algorithm/
- [T1] Thenx, *Introduction To Calisthenics Program*, public course: https://app.thenx.com/programs/79
- [T2] Thenx, *One Arm Pull Up Follow Along*, public skill steps: https://app.thenx.com/programs/132
- [T3] Thenx, *Technique Guides* index: https://app.thenx.com/technique-guides
- [T4] Thenx official app product page: https://thenx.com/pages/app
- [C1] Calistree Knowledge Center, *Skill Trees*: https://calistree.app/knowledge?id=skillTrees
- [C2] Calistree Knowledge Center, *Guided Training*: https://calistree.app/knowledge?id=guidedTraining
- [C3] Calistree product overview: https://calistree.com/
- [C4] Calistree Knowledge Center, *Import previous workouts*: https://calistree.app/knowledge?id=aboutImport
- [L1] Freeletics Help, *Adapt your Bodyweight training session*: https://help.freeletics.com/hc/en-us/articles/360003933780-Adapt-your-Bodyweight-training-session
- [L2] Freeletics, *Adapt Today*, describes same-day-only scope: https://www.freeletics.com/en/blog/posts/quick-adapt/
- [L3] Freeletics Help, *Adjust your Running Journey preferences*: https://help.freeletics.com/hc/en-us/articles/4407828383250-Adjust-your-Running-Journey-preferences
- [L4] Freeletics Help, *Can I reset my Training Coach?*: https://help.freeletics.com/hc/en-us/articles/360011919479-Can-I-reset-my-Training-Coach
- [L5] Freeletics Help, *Understand your Running Training Session*: https://help.freeletics.com/hc/en-us/articles/4407778620946-Understand-your-Running-Training-Session
- [L6] Freeletics Help, *Log workout outside Training Journey*: https://help.freeletics.com/hc/en-us/articles/4403073844626-Log-an-exercise-workout-or-run-outside-of-your-Training-Journey
- [S1] Schumann et al. (2022), *Compatibility of Concurrent Aerobic and Strength Training for Skeletal Muscle Size and Function*, meta-analysis of 43 studies: https://link.springer.com/article/10.1007/s40279-021-01587-7
- [S2] Petré et al. (2021), trained vs less-trained subgroup meta-analysis: https://pmc.ncbi.nlm.nih.gov/articles/PMC8053170/
- [S3] *Maximizing Adaptations in Concurrent Training*, 2026 umbrella review (17 meta-analyses): https://pubmed.ncbi.nlm.nih.gov/41762427/

**Audit status:** Original source-based research writing and test registration only. No mobile accounts tested; no proprietary thresholds recovered; no exercise library, local lab, or production source modified; nothing here constitutes health clearance or evidence of an improved athlete outcome.
