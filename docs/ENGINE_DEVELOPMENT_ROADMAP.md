# StrideLab Coaching Engine Development Roadmap

**Roadmap baseline:** 2026-11-17  
**Owner-approved scope addition:** 2026-11-18 — marathon Training Intensity Distribution evaluator  
**Decision:** Stabilize and complete Engine 1.5 before beginning Engine 2.x implementation.  
**Status:** Authoritative development sequence; retain until the owner explicitly changes direction or a material safety/engineering finding requires review.

This document is the canonical **development-priority roadmap**, not evidence that every listed capability exists. The Engine 1.5 execution ledger, source code, and test reports establish implementation status. Earlier Engine 2.x-first priorities in historical notes are superseded by the policy below.

## 1. Product and engineering objective

Build StrideLab into a dependable offline-capable, multi-discipline training coach.

- **Engine 1.5:** Owns independent static plan construction, structured workouts, athlete-history interpretation for **initial** prescriptions, supported running/strength/calisthenics/conditioning programming, scheduling, explanations, and validation.
- **Engine 2.x:** Eventually owns **ongoing adaptation** after the plan is created, such as responding to completed or missed workouts, accumulating workload, and changing recovery/readiness evidence. It must reuse Engine 1.5's trusted workout construction and plan validation rather than recreate another planner.
- **Engine 1.0:** Retained only where needed for explicit rollback and historical compatibility; never used as a hidden fallback inside the independent Engine 1.5 candidate.

**Non-negotiable sequencing rule:** No Engine 2.x implementation, production cutover, or feature expansion is the primary workstream until Engine 1.5 passes the acceptance gate in Section 6. Existing Engine 2.x research and prototypes remain preserved and clearly non-authoritative.

## 2. Verified Engine 1.5 baseline — 2026-11-17

**Independent generation candidate:** branch feature/engine15-independent-generation-20261117, [draft PR #131](https://github.com/Isshhin/stridelab-development/pull/131), verified head 42f22ee447f97a39a9b90f3522861f2e75bd960c (Batch 16).

Delivered for supported development scenarios:

- Independent static running plans with weekly mileage, easy/long runs, requested schedule, recovery weeks, and user-directed mileage policy in debug.
- Standalone static strength, calisthenics, and foundation-level conditioning composers using supported offline exercise-catalog contracts.
- An independent combined calendar for **tested** discipline combinations, weekday assignments, locks, long-run placement, permitted double days, basic time and interference checks, and explicit deferral of infeasible schedules.
- A versioned, **user-defined** structured-running-workout contract with warm-up, repetitions, recovery, multi-block transitions, cooldown, mixed time/distance steps, explicit distance equivalents, reconciliation to scheduled mileage, preview rendering, and saved-plan round trips.
- Engine-neutral request/plan/persistence contracts; managed draft and approval flow; owner-scoped offline reopen for tested cases.
- Debug independent-route default and explicit legacy rollback. **Release independent-route flag remains disabled**, and PR #131 is draft/unmerged.

Batch 16 reported 1,033 JVM tests across 131 suites with no failures/errors and one skipped legacy quality-capture test in both candidate and rollback configurations; 24 focused generator tests and 13/13 selected Android 15 emulator controls passed. Debug/Android-test/release APK builds passed. **These are software verification results, not clinical or real-athlete coaching-effectiveness evidence.** Full instrumentation, broad historical backup compatibility, hosted CI, and real-world coaching validation remain open.

**Known gaps:** the Plan Builder does not yet author structured steps; coach-selected quality families and athlete-specific intensity are not qualified; the conditioning catalog is narrow; some mixed-discipline schedules defer; the current candidate is not production-ready. Family labels such as Threshold/Tempo/Intervals/Hills/Fartlek/Progression/Strides in user-defined requests do not imply autonomous prescriptions for those families.

## 3. Authoritative implementation order

Complete stages sequentially, with small reversible, test-backed batches. A stage can be marked partial when individual supported subsets pass; do not broaden claims beyond validated cases.

### Stage 1 — Structured workout authoring in the Android Plan Builder
**Status: next implementation; not verified delivered.**

- Make existing structured contracts usable through Compose UI: add/edit/remove/reorder steps, warm-up, work, recovery, transitions, cooldown, repeat groups, and multiple blocks.
- Support distance and time steps, explicit distance equivalents for timed steps where reconciliation requires them, and optional user-entered pace/HR/effort targets only where contracts permit.
- Distinguish user-entered workout intent from a qualified coach recommendation.
- Verify draft creation, preview, approval, offline reopen, versioned decoding, owner isolation, and narrow-screen usability without redesigning unrelated screens.

**Exit:** An end user can author, review, save, and reopen a multi-block quality workout through the normal app flow, with exact preserved step identities and valid totals.

### Stage 2 — Independent coach-generated quality-workout selection
**Status: planned.**

- Reuse the existing Engine 1.5 facade and quality research; do not build a parallel engine or inherit Engine 1.0 selection rules.
- Prioritize qualified continuous tempo, threshold/cruise intervals, distance-based intervals, and time-based intervals. Add progression, fartlek, hills, strides, and other families only when their construction and selection evidence is sufficient.
- Base decisions on stated goal/event, available days, running frequency, planned volume, reported and recorded training evidence, known exposure, long-run placement, and scheduling constraints.
- Keep family selection separate from intensity calibration. If evidence or a family policy is unqualified, defer with a specific reason; never invent pace, HR or session-dose prescriptions.
- Support one or two quality sessions only when a validated plan structure can contain them; prevent silent omission.

**Exit:** Supported full-plan scenarios automatically select and construct appropriately explained quality sessions; unsupported scenarios defer explicitly.

### Stage 3 — Initial intensity, dose, and multiweek progression
**Status: planned.**

- Qualify session work doses, recovery, warm-up/cooldown, intensity targets and training-phase placement using traceable athlete inputs and evidence-confidence rules.
- Preserve user-defined targets as **user-directed**; do not call them validated coach prescriptions.
- Reconcile every workout segment, including timed steps with explicitly provided equivalents, against session and weekly distance.
- Apply loading/recovery-week structure, avoid unjustified jumps, and account for interactions with long runs and supported other disciplines.
- Keep provisional athlete-capacity estimates advisory in explicit private user-directed mode where appropriate, while preserving structural and safety-critical constraints and transparent warnings.
- Do not introduce automatic post-workout, HRV, sleep, or fatigue-driven plan adaptation.

**Exit:** Generated progression is deterministic, explainable, internally complete, and supported by both software and coaching-policy validation.

### Stage 4 — Complete-plan quality and regression validation
**Status: planned.**

- Validate entire 8–16-week plans, not only single workouts, across supported 5K/10K/half-marathon/marathon goals, varied training histories, one/two quality slots, recovery weeks, fixed/locked days and mixed-discipline schedules.
- Assert exact mileage/duration accounting, pace/HR target provenance, workout structure, spacing, scheduling feasibility, no dropped sessions, and transparent deferrals.
- Run full JVM/build checks and targeted Android emulator draft/approval/offline reopen tests. Verify legacy rollback and historical compatibility.
- Evaluate representative plans against explicit coaching expectations; passing unit tests alone does not prove effectiveness.

**Exit:** Core supported cases are coherent and regression-safe, with remaining qualification gaps documented.

### Stage 5 — Restore and modernize automatic run-workout detection
**Status: planned; legacy TypeScript implementation must be located and audited first.**

- Review the legacy TypeScript detector before porting behavior; reuse only validated rules and compatible data contracts in Kotlin.
- For imported or recorded activities, distinguish **what was performed** (Easy, Recovery, Long, Quality, Mixed/Unknown as appropriate) from **how hard it was**.
- Prefer trustworthy recorded structured laps/workout steps or a verified match to the planned session; use pace, duration, splits, HR and distance heuristics when those details are absent.
- Preserve workout intent, detected class, confidence, evidence/source and user correction separately. A fast-finish long run must not be silently relabeled as purely threshold work.
- Handle missing HR, pauses, GPS issues, elevation, hills, partial activities, outlier splits and uncertainty. Do not overwrite original Garmin/FIT data or fabricate signals.

**Exit:** Deterministic tests and recorded-run fixtures show useful classifications, explainable uncertainty and reversible manual corrections.

### Stage 6 — Relative effort, performance comparison, and athlete baselines
**Status: planned.**

- Create an **original StrideLab effort interpretation**, not a copy or opaque reverse-engineering of a competitor's proprietary score.
- Assess time at HR intensity, session duration, RPE when present, HR versus pace, HR drift/decoupling where conditions permit, splits, distance, training history and consistency.
- Maintain individual comparable-condition baselines, distinguish **session effort** from **unusual relative strain**, and express evidence confidence and provenance.
- Avoid assuming one hard session means fatigue or overtraining; consider heat/humidity, terrain/grade, sensor quality, hydration and other confounders when data exist.
- Apply minimum-evidence and missing-data rules; keep privacy, offline operation, account scoping and user control intact. No new telemetry or personal-data collection is authorized by this roadmap.

**Exit:** Reliable, interpretable effort and baseline summaries exist with uncertainty handling and no diagnostic claims.

### Stage 7 — Athlete-history-informed initial plan generation
**Status: planned.**

- Connect workout classification, actual completed session structure, relative effort and demonstrated exposure to a versioned athlete-evidence profile.
- Use credible history to inform **initial** weekly volume, quality frequency, family eligibility, workout intensity/dose and realistic progression.
- Clearly distinguish recorded/verified, athlete-reported, inferred and unknown observations; do not fabricate threshold pace, HR zones or readiness from sparse data.
- Preserve manual review, user-directed private-testing requests, and meaningful alternatives when evidence is incomplete or conflicts with a target.
- Keep Engine 1.5 static: it may analyze history **when creating a new plan** but does not autonomously revise an approved plan after each new activity.

**Exit:** Different real-world histories yield appropriately different initial prescriptions with testable reasoning and confidence.

### Stage 8 — Training Intensity Distribution (TID) research evaluator
**Status: owner-approved, planned after athlete profiling and before Engine 1.5 final stabilization.**

**Research reference:** Muniz-Pumares D, Hunter B, Meyler S, Maunder E, Smyth B. [The Training Intensity Distribution of Marathon Runners Across Performance Levels](https://doi.org/10.1007/s40279-024-02137-7). *Sports Medicine*. 2025;55(4):1023–1035. [PubMed PMID 39616560](https://pubmed.ncbi.nlm.nih.gov/39616560/).

**Scientific boundary:** This observational study examined training during the 16 weeks before 151,813 marathon performances by 119,452 runners. Higher-performing groups tended to accumulate more volume, notably at lower intensity, and pyramidal distributions were more prevalent. Associations are **not causal training prescriptions**, individual mileage limits or proof of a universally optimal percentage split. The study's three intensity zones use estimated critical speed (CS), with the Z1/Z2 boundary estimated at 82.3% CS; they are **not equivalent** to StrideLab/Garmin's five heart-rate zones. The original Strava athlete-level dataset was made available under a restricted research license, not as an open importable resource.

- Introduce a versioned, deterministic, **advisory-only** TID evaluator that consumes Stage 6–7 shared athlete evidence and produces a report. Do not introduce a new plan generator, automatic adaptation loop, telemetry collection or athlete data export.
- Calculate recorded **time at intensity** using reliable moving-time segments, matching threshold provenance and defensible zone boundaries. Keep unknown/unsupported time separate; expose coverage and data-quality flags. Do not infer an athlete's critical speed or first threshold from average HR or convert five HR zones into the paper's three CS-based zones without a validated method.
- Support two clearly labeled outputs: (a) a **source-zone HR distribution**, where available, for descriptive display, and (b) a **research-compatible three-zone distribution** only where athlete-specific threshold evidence and measurements truly justify the conversion. If not justified, return `not comparable / insufficient evidence` rather than manufactured percentages.
- For a static marathon plan, evaluate **planned intensity time** using only explicitly represented, reviewed workout steps and qualified intensity targets. Account for warm-ups, recoveries, cooldowns, uncertain time-to-distance conversions and unclassified segments. Compare with the athlete's observed distribution only when the measurement methods and coverage are comparable.
- Flag large unexplained differences in planned versus demonstrated workload/intensity for **coach review**, based on separately reviewed policy; avoid mandating 80/20, a fixed pyramidal split, performance-group mileage averages, arbitrary escalation or session cancellation. Distinguish `informational observation`, `insufficient evidence`, and `review recommended`.
- Keep conclusions **marathon-specific**; extending this paper's findings to shorter races or non-running disciplines requires independent evidence review.
- Test missing HR, sensor gaps, pauses, incomplete historical coverage, deduplication, missing CS/threshold, mixed-intensity sessions, different zone models, sparse samples, and uncertain prescribed steps. Verify immutable source data, offline behavior, owner isolation and accessible explanations.
- The paper alone **cannot qualify** automatic threshold/tempo/interval selection, pace/HR targets, safe work dosage or week-to-week progression. Engine 1.5 remains static, quality prescription gates stay closed where unqualified, and Engine 2.x stays deferred.

**Exit:** An explainable, versioned TID report distinguishes source-derived exposure from missing or estimated data, provides transparent athlete-history and marathon-plan observations, and adds only reviewed **non-blocking** plan-quality advisories until independent policy qualification.

### Stage 9 — Multi-discipline refinement and Engine 1.5 stabilization
**Status: planned; follows the TID evaluator and other qualified initial-plan evidence.**

- Improve static coordination of running, strength, calisthenics and conditioning; separate structural impossibilities from overly conservative advisory scheduling preferences.
- Expand supported strength/calisthenics/conditioning prescriptions only with catalog coverage, clear progression and qualification. Improve time constraints, exercise compatibility, optional double days and complete-plan fidelity.
- Validate historical plans, backups, process-death/offline persistence, owner isolation, update compatibility, UI usability, and migration or rollback boundaries.
- Conduct independent coaching review, adversarial scenarios, owner field evaluation and regression on the exact release candidate.
- Resolve all critical correctness and plan-integrity defects; document bounded limitations rather than pretending every user goal or combination is supported.

**Exit:** All Engine 1.5 acceptance conditions in Section 6 are met, and an explicit owner-approved release candidate exists.

### Stage 10 — Engine 2.x (DEFERRED)
**Status: not an active implementation priority.**

No implementation should begin solely to accelerate version progression. Preserve existing research artifacts, competing-framework synthesis, datasets and experimental prototypes as **research-only** until Engine 1.5 is stable.

When separately authorized after the Engine 1.5 gate:

1. Freeze a versioned interface for athlete evidence, plan construction, revision proposals and validation.
2. Introduce an Engine 2.x adaptation/decision layer that proposes updates from completed/missed workouts, evolving load and recovery signals; have Engine 1.5's trusted construction and validation contracts produce valid revised plans.
3. Keep changes explainable, reviewable and reversible; do not silently overwrite approved sessions or invent evidence.
4. Validate with offline replays, controlled comparison, longitudinal field tests, persisted versions and immediate rollback.
5. Consider remote-configurable *supported parameters* only after engine interfaces and safety boundaries are proven; remote config must never bypass compiled constraints. New executable behavior, migrations or permissions still require an APK.
6. Promote only after independent acceptance. Legacy compatibility remains until migration and rollback are proven.

## 4. Development flow and working agreements

- **One active engine-development priority:** Engine 1.5 until its release-candidate gate passes; avoid parallel Engine 2.x implementation streams and duplicative generation logic.
- **Authoritative execution surface:** PR #131's isolated branch/worktree for current independent Engine 1.5 changes. Keep the independent facade and existing offline app contracts as the source of execution; research previews remain non-executable unless independently qualified.
- **Development batches:** Inspect current HEAD and repository guidance; write a failing contract/regression test; implement the smallest complete change; run targeted checks; run appropriate full JVM/build and emulator acceptance; then commit and update the existing execution ledger. Advance continuously through eligible batches; mark blocked work honestly.
- **Debug versus release:** Private experimental user-directed policies and independent route must remain explicitly scoped to debug/dev until release gates pass. Do not silently activate release behavior.
- **Emulator-only test discipline:** Verify emulator identity before ADB install/instrumentation. Do not target a physical device. Do not wipe owner data.
- **Data integrity:** Preserve approved plans, owner isolation, historical backup formats, versioned workout decoding and Engine 1.0 rollback where required. No session omission, target substitution or fallback to legacy generation without explicit policy/visibility.
- **Verification standard:** Distinguish passed tests, compiled-only tests, blocked hosted CI, synthetic evidence and real-athlete effectiveness. No production-ready claim from green unit tests alone.
- **Documentation:** Keep source-level implementation details and test counts in ENGINE15_EXECUTION.md and the independence audit; this file is the stable *prioritization contract*, not a running commit log.
- **Promotion:** No PR merge, release activation, device install or engine promotion without independent review and explicit owner decision.

## 5. Workstream boundaries

| Concern | Engine 1.5 | Engine 2.x |
| --- | --- | --- |
| Independent multi-discipline static plan construction | Owns | Reuses |
| Workout structure, schedule, constraints and persistence | Owns | Reuses/validates |
| Historical workout detection and effort baselines | Owns initial evidence | Reuses/refreshes evidence |
| Marathon TID measurement and advisory static-plan evaluation | Owns verified initial evidence and qualified non-blocking review | May reuse evidence, not bypass policies |
| Athlete-specific first prescription | Owns | May request a new validated prescription |
| Static week-to-week progression and recovery weeks | Owns | Can propose changes later |
| Automatic response to completed or missed sessions | Out of scope | Core |
| Ongoing fatigue, load or HRV-based adaptation | Out of scope | Core |
| Autonomous modification of an approved plan | Out of scope | Proposed/reviewed policy |
| Remote configuration of supported adaptive parameters | Not a priority | Possible after stable safety gate |

**Do not conflate** planned recovery weeks and conservative initial-volume decisions with dynamic adaptation. Engine 1.5 still needs athlete awareness to make a good **first plan**; it does not need to continuously respond to new workouts.

## 6. Engine 1.5 release-candidate acceptance gate

All mandatory items must be explicitly assessed; a check is not passed merely because a code path exists.

- [ ] No hidden Engine 1.0 generation/validation dependencies in the selected independent pipeline; explicit rollback intact.
- [ ] Supported running plans produce structurally complete, coach-qualified tempo/threshold/interval quality sessions and appropriate explanations; unsupported families defer.
- [ ] Android Plan Builder can author and persist user-defined structured workouts end-to-end.
- [ ] Athlete-specific initial intensity and workload prescriptions have traceable evidence and uncertainty handling.
- [ ] Workout classification distinguishes observed type, intended type and effort; user corrections and unknown cases work.
- [ ] Relative-effort and pace/HR baselines are individually contextualized and do not overclaim fatigue/fitness.
- [ ] Marathon TID evaluator reports coverage, threshold provenance and non-equivalence of research CS zones versus HR zones; missing evidence never produces invented distributions or unreviewed prescriptions.
- [ ] Historical athlete evidence can safely inform initial Engine 1.5 prescriptions without fabricating measurements.
- [ ] Supported multi-discipline combinations satisfy complete scheduling, workout and progression contracts; infeasible requests explain why.
- [ ] Weekly and step-level mileage/duration totals reconcile; no silently dropped sessions or altered user goals.
- [ ] Recovery weeks, restrictions, conflicting schedules and partial/missing data are handled explicitly.
- [ ] Draft, approval, offline reopen, process restart, owner isolation, historical backup and legacy compatibility checks pass.
- [ ] Full applicable JVM, build, targeted Android and broader regression/acceptance gates pass on an isolated emulator; blocked hosted checks are disclosed.
- [ ] Representative real-athlete histories and independently reviewed multiweek plans have acceptable coaching findings, with limitations and failure cases documented.
- [ ] Exact-head release/installation/signing compatibility and owner acceptance are confirmed before production promotion.

**Stage-gate decision:** Engine 2.x implementation remains paused until this Engine 1.5 release-candidate gate passes and the owner expressly authorizes the next phase. Engine 1.5 need not become perfect or support every possible workout, but it must be dependable for clearly defined supported scenarios.

## 7. Longer-term UI and configuration outlook

- Preserve the existing Android app as the source of truth; avoid unrelated redesign while coaching contracts are changing.
- Make targeted Plan Builder and coaching-display improvements as needed to validate Engine 1.5; a **broad visual revamp** remains separately gated on stable engine/product contracts and owner approval, rather than being mixed into coaching implementation.
- Preserve the historical idea of a versioned EngineConfigProvider for later Engine 2.x experimentation, with validated remote -> last known-good -> bundled-safe-default fallback, independent app/runtime/config versions, strict validation and offline operation. This is **not** an Engine 1.5 implementation request.
- The prior November 30 personal-testing objective is a separate product milestone, not permission to bypass Engine 1.5 acceptance or deploy experimental prescriptions.

## 8. Immediate next steps

1. Continue the already-scoped **continuous stages 1–4** work: Plan Builder authoring -> coach quality selection -> intensity/progression -> full-plan verification.
2. Then audit and restore the **legacy TypeScript automatic-workout detector** in Kotlin.
3. Add **relative effort, comparable-condition athlete baselines, and evidence confidence**.
4. Integrate the resulting history profile into **initial** Engine 1.5 plan generation.
5. Implement the **marathon TID evaluator** from Muniz-Pumares et al. as a provenance-aware, non-blocking analysis and static-plan review component; preserve differences between HR-zone metrics and CS-based research zones.
6. Refine other disciplines and finish full coaching/Android/offline acceptance.
7. Freeze and review the Engine 1.5 candidate before considering Engine 2.x.

**Roadmap change policy:** Treat this sequencing decision as stable. Update this file only for explicit owner re-scoping, a formal milestone/acceptance decision, or a material correctness/safety finding. Routine progress and test evidence belong in the execution ledger rather than repeatedly rewriting this roadmap.
