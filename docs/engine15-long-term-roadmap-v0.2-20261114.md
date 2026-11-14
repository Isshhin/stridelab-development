# StrideLab Engine 1.5 — Long-term Coaching Roadmap v0.2

**Status:** Owner-directed implementation roadmap, adopted 2026-11-14. Milestone-gated, not date-gated.
**Scope:** Independent, offline-capable, research-informed running-coach generation and later adaptation.
**Integration target:** dev. Main remains the owner-approved release; no direct edits to main.
**Current verification:** This document is a plan, not evidence that new functionality has shipped. See ENGINE15_EXECUTION.md and docs/qa for exact historical test results.

## Product outcome

StrideLab should generate a credible, progressive plan from a runner's demonstrated capacity and goals; create appropriately challenging, executable workout steps; explain feasibility and rule decisions; and use actual training and athlete feedback to propose changes without silently rewriting approved workouts. A watch, cloud connection or StrideLab sign-in must not be required for mature core coaching. No November 2026 deadline applies.

Training research informs the constraints and alternatives; synthetic compliance checks prove only software behavior, not individual injury prevention, fitness outcomes or physiological safety. Use three evidence labels: **supported** (source-backed within its scope), **heuristic** (unvalidated policy choice) and **needs validation** (missing relevant evidence). Store citations/provenance with each rule.

## Development governance

- **Maximum two active implementation workstreams:** A = production Android reliability, B = isolated coaching-policy research and generation. Each has at most one primary deliverable in progress; review and merge a batch before expanding its scope. Ongoing reading does not authorize a third implementation stream.
- **Review cadence:** after each meaningful batch, review observed tests and representative generated plans; periodically revisit milestone status when evidence warrants it, not on a release-date countdown. Log new requirements with an affected milestone and trade-off.
- **Branch discipline:** use short-lived branches targeting dev. Never push to main directly. Never mutate an approved/saved historical plan merely because a generator, flag or schema changes. Honor AGENTS.md and current release policy.
- **Production rollout:** new generator is opt-in / feature-flagged until its full relevant deterministic suites, prespecified Gate B independent review and critical Android device flows pass; zero unresolved critical defects; a documented personal-use observation period of owner-approved duration; and explicit owner acceptance of the exact commit/config/version. Do not invent an observation-duration requirement. Default change applies to **new plans only**.
- **Legacy retirement:** (1) stop generating new plans with a previous policy; (2) continue supporting compatible historical saved snapshots; (3) remove legacy code only after validated migration or proof no compatible snapshots require it. Each is a separate owner-approved decision.
- **Outside testers:** require appropriate informed consent, working pain/illness prompts, ability to disable/ignore coaching proposals, accessible stop/report mechanism, and a predeclared pause/withdrawal rule for material adverse feedback; Gates A/B/C must pass for the relevant tested paths. Gate D's longitudinal effectiveness evidence is collected *through* responsible testing, not a precondition to all testing.
- **Not-now list:** machine-learned training decisions, automatically prescribed HRV readiness, inferred proprietary Garmin load scores, unsupported weather-derived physiology, injury diagnosis/medical clearance, fully optimized multi-discipline physiological load, and cloud-required generation. To re-enter, an item needs a demonstrated athlete need, source/evidence assessment, realistic ownership and maintenance capacity, effect on current milestones, acceptance tests and a logged owner decision.
- **First goal path:** Train Your Way / target weekly mileage is a **provisional selected path**. Reopen only if M3 capacity-profile results or M1 adversarial output demonstrate that another path is a better engineering or athlete-testing foundation. Do not leave it indefinitely open without a trigger.
- **Initial plan shape:** complete initial fixed plan, optionally accompanied by a read-only projected weekly roadmap. Genuine 1–2-week rolling commitment, regeneration and reviewed future-week changes come later. Projected weeks must not masquerade as accepted session prescriptions.

## Parallel workstreams and milestones

| ID | Milestone | Key deliverable and exit evidence |
| --- | --- | --- |
| **M0 (A)** | Android reliability | Isolate builder E2E hang, finish full emulator/instrumentation evidence, review account isolation, backup/restore, offline reopen and owner-run physical device acceptance. **Quality-eligibility UI/current-baseline mismatch is an isolated behavioral fix with dedicated regression**, not a generic reliability refactor. Protected data and appearance remain unchanged. |
| **M1 (B)** | Research harness and review rubric | Freeze Gate B rubric **before** generating candidate outputs; provenance-aware synthetic/manual/real-opt-in replay contracts; source ledger; deterministic candidate-policy harness with per-week rule IDs, binding caps, conflicts, infeasible targets and strict missing-data behavior. Tests and reviewable plan traces, not physiological claims. |
| **M2 (B)** | Independent Kotlin generator / step contract | Pure deterministic offline pipeline: capacity -> weekly skeleton -> session allocation -> structured steps -> scheduler -> final validator. Define machine-readable warm-up/work/recovery/cool-down steps **now**, before quality budgets and snapshot changes. Version policies and backward-compatible saved-plan schema; Engine 1.0 does not clamp independent plans. |
| **M3 (B)** | Capacity profile | Provenance and confidence for four-week activity volume, recent two weeks, weekly consistency, frequency, repeatable long run, quality exposure, breaks, optional pace calibration. Compare self-report with confirmed records; unknown != zero; downward or unresolved conflicts default conservatively. Review real/synthetic/manual replay separately. |
| **M4 (B)** | Target-mileage volume skeleton | Constrained weekly ramp using independent configurable relative and absolute ceilings, recent verified workload and time/frequency feasibility. Separate single-session/long-run progression and share warnings; 3+1 recovery as comparator; quality eligibility continuous with evidence; explicit target feasibility states and projected dates. Final combined constraint stage must recheck totals/rounding; never silently relax an invariant. |
| **M5a (B)** | Core executable workouts | First families: easy, easy long, strides, threshold and tempo. Prescribed work dose, progression, purposeful recovery and validated total distance/time. Wire step model into Android guided player. This requires coordinating A/B ownership, never a third concurrent stream. |
| **M5b (B)** | Workout library extension | Intervals, hills, fartlek, progression runs and specialized long-run types. Add only where the stimulus and progression model differ meaningfully; test replayed full plans. |
| **M6 (B)** | Goal-specific periodization | 5K, 10K, half and marathon paths; explicit raceDate for event plans; fixed race and taper anchoring, explained feasibility, goal-specific dose and recovery. No invented pace precision. |
| **M7 (B)** | Feedback-led adaptation | Optional completion and direct easier/right/harder-than-prescribed comparison, contextual reason tags, separate pain/illness prompt. Preserve present reviewed short-term reduce/rest; deferred full positive adaptation and future-week holds require new explicit proposal contracts. Missing feedback is unknown; context does not erase actual effort. |
| **M8 (B)** | Rolling horizon, approval and history | Full-period projection plus near-term committed detail; bounded week-N regeneration with before/after diff, review/accept/dismiss and revision conflict checks. Completed and previously committed workouts, manual locks and race dates protected; no unapproved rewrite. |
| **M9 (A+B)** | Controlled rollout and longitudinal validation | Full matrix of software, coaching, Android and field evidence; owner-controlled flag/default transition, monitored real use, independent review and explicit remaining limits. No vendor parity or coaching-efficacy claim without appropriate evidence. |
| **M-X (A)** | Signed-out local identity and migration | **Starts when M0 frees A's workstream slot; not a third parallel stream.** Persistent device-local plan owner, safe optional account link/backup, cross-account separation, read/restore and logout behavior. Must pass before any outside runners are invited. Design its migration and serialization dependencies with M2; schedule implementation under A after M0. |

Milestones are dependencies, not a requirement that every research test waits for all Android work. M0 and M1 may proceed in parallel, within the two-stream cap.

## Volume and coaching policy contract

**VOL-01 Baseline:** comparison of complete recent four weeks and two weeks, consistency and data provenance, athlete confirmation. A reliable downward trend or unresolved recorded-versus-declared disagreement uses the lower supported figure; no-data state remains explicit. Confidence affects early progression envelope. **Heuristic**, parameterized.

**VOL-02 Progression:** both relative and absolute km limits; feasibility outcomes: feasible within requested window / feasible later (arithmetic projected date) / not supported under configured constraints. Do not increase the cap to meet the target date. **Heuristic**, no finalized constants.

**VOL-03 Long run:** independently constrain repeatable recent longest-session exposure, next-session step and share of weekly total; flag unusual spikes. In a three-day plan, share conflicts must be reported, not silently overridden. Evidence for examining session spikes is observational, not a validated individual safety cutoff. **Heuristic**.

**VOL-04 Recovery:** 3 build weeks + recovery week is an initial comparator, not optimal physiology. Separate weekly distance, long-run and hard-work reductions; test earlier reviewed recovery after credible repeated poor tolerance. The exact week index must be consistent between lab and Kotlin. **Heuristic**.

**VOL-05 Quality:** replace current mileage cliffs with continuous recent exposure + capacity eligibility. Structured steps eventually support separate work-minute and recovery budgets, not a hard universal 80/20 percentage. Do not infer quality readiness from race time alone. **Heuristic**.

**ADAPT-01:** direct subjective comparison to prescription; do not confuse whole-session RPE with a step's target RPE. Structured feedback fields backward-compatible. **Alpha signal, no automatic progression**.
**ADAPT-02:** retain verified one-event short-term rest/reduction proposals; patterns support future hold/reduction proposals only after M7. Pain/illness trigger a cautious prompt, not app medical clearance. **Alpha signals; later reviewed adaptation**.
**ADAPT-03:** positive feedback alone cannot accelerate a plan; post-M7 positive proposals must clear the same workload envelope and require stronger independent evidence and explicit approval. **Post-alpha**.
**ADAPT-04:** revision-checked consent, transparent before/after and no rewrite of completed/approved history. **Required system invariant**.
**ADAPT-05:** reviewed re-plan from week N, with preserved locked/committed sessions and explanation. **Post-M8**.

No numeric policy parameter is a validated physiological threshold. Put chosen experimental values in versioned config with source/hypothesis label, keep competitor vendor claims separate from independent evidence, and review actual generated plans before policy promotion.

## Four distinct validation gates

- **Gate A, software:** deterministic replay; exact weekly/session/step totals; final combined cap validation; availability/spacing; missing != 0; revision, ownership, schema, backup, rollback and legacy compatibility.
- **Gate B, coaching plausibility:** independent human scoring against [precommitted rubric](qa/engine15-gate-b-rubric-v1.md), including failed/infeasible and contradictory cases, full plans and traces. Software green is necessary but never sufficient.
- **Gate C, Android:** real Compose builder, player, feedback, offline persistence, restore, emulator suite and owner-authorized physical device. A partial instrumentation run never counts as full acceptance.
- **Gate D, field:** longitudinal actual adherence, acceptable workload progression, perceived effort, adverse events and performance measures with caveats; no simulated improvement as outcome evidence.

## Implementation order

1. **Commit the roadmap and Gate B rubric first**, before generating new candidate plan output.
2. Batch 1: isolated Python harness, versioned hypothetical policy configs, synthetic fixture matrix, traceable binding/conflict/infeasibility reports, replay contract, tests. Do not change production Engine 1.5 or Engine 2.x research gates.
3. Independent review of a set of generated plans against the frozen rubric and original baselines.
4. M2 design-contract review, then M3/M4 Kotlin implementation behind a flag, with Android integration only after compatibility evidence.
5. Update this roadmap only when a test result, evidence change or owner decision justifies it; do not restart speculative cross-model planning cycles.

## Evidence register and implementation references

- docs/qa/engine15-continuous-handoff-20261113.md and ENGINE15_EXECUTION.md — actual Engine 1.5 acceptance gaps, current tests and builder hang.
- docs/qa/main-dev-release-policy-20261111.md — dev/main branch and owner acceptance contract.
- Isshhin/stridelab-coaching-lab: src/coaching_lab/planning.py, periodization.py, dose.py, validation.py, README.md — **independent synthetic research only**, known combined-capacity and quality re-entry errors; no verified coaching efficacy.
- research/runna-garmin-coaching-20261102 branch, docs/research/2026-11-04-session-c-progression-deload-and-re-entry.md and batch-1-hard-dose-periodization-garmin-adaptation.md — vendor/literature evidence with explicit limits; not part of production branch.
- Public evidence: Buist et al. novice progression RCT, https://pubmed.ncbi.nlm.nih.gov/17940147/; Frandsen et al. single-session spike observational study, https://doi.org/10.1136/bjsports-2024-109380; Runna published volume and recovery descriptions, https://support.runna.com/en/articles/15279082-how-does-runna-reduce-the-risk-of-injury-and-overtraining and https://support.runna.com/en/articles/11598695-how-to-use-and-manage-your-target-mileage-settings-on-run-further-and-train-your-way-plans. These motivate candidates; they do not establish safe absolute numerical rules.
