# StrideLab Handoff / Session Recovery

Updated: 4 November 2026

This is the canonical high-level recovery document for StrideLab.

For long-term product identity and principles, read [PRODUCT_VISION.md](PRODUCT_VISION.md).

For execution order, read [ROADMAP.md](ROADMAP.md).

For branch-independent current direction, read GitHub Issue #42:

https://github.com/Isshhin/stridelab-development/issues/42

Historical Phase 4D/4E files remain valid as historical evidence, but they are not the current roadmap.

## Current project direction

**StrideLab**

**Train the whole athlete.**

Current mission:

> Help athletes improve performance through adaptive, evidence-based training across endurance, strength, movement, conditioning, and recovery.

The project direction changed on 4 November 2026.

The current priority is no longer:

Trackside → Engine 1.5 → APK → then more Engine 2.x research.

The current direction is:

**Engine 2.x research and validation → Engine 2.x production candidate → Trackside integration → dogfood APK**

The user explicitly accepts delaying the next compiled APK if that delay materially improves coaching maturity.

## Current priority order

1. Engine 2.x validation and Phase 2 experiments
2. real-data calibration and plan-to-performance linkage
3. research-only Engine 2.x candidates
4. controlled production-candidate hardening
5. Trackside integration around the mature engine
6. end-to-end dogfood APK
7. broader whole-athlete expansion

Engine 1.5 remains the stable fallback/control engine.

Trackside remains a secondary active UI reconstruction and verification track.

## Engine 2.x

Branch:

research/runna-garmin-coaching-20261102

Draft PR:

[PR #34](https://github.com/Isshhin/stridelab-development/pull/34)

Latest verified head at this handoff:

4b734f1b97a54e6c15ba3bf0662d456de5c68413

Status:

**SESSION G REVIEW PASSED / PHASE 2 BASELINE FROZEN**

Sessions A-F + H are complete and reviewed.

Session G passed independent re-review. The frozen architecture baseline is `26a8331e642c888dd73acb94755dded063baab8d`.

Key outputs:

- docs/research/2026-11-04-engine2x-session-g-synthesis.md
- docs/research/2026-11-04-engine2x-session-g-state.md
- docs/research/2026-11-04-engine2x-session-g-candidates.md
- docs/research/2026-11-04-engine2x-session-g-phase2.md

The current architecture is:

observations
→ provenance / quality / eligibility
→ multidimensional athlete state
→ uncertainty
→ safety and constraint gates
→ training intent
→ decision or abstention
→ downstream-week review
→ athlete review
→ execution
→ outcome
→ updated athlete state

Do not reduce the athlete to one opaque readiness score.

The architecture includes:

ABSTAIN_NEED_MORE_INFORMATION

Unknown or conflicting evidence must not silently become KEEP, REST, REDUCE, or progression.

### Immediate Engine 2.x next step

1. Prepare P2-F admission/linkage feasibility under frozen baseline `26a8331`.
2. Verify source rights, acquisition schema, parser/source correctness, plan-to-actual linkage, duplicate/account isolation and pilot audit.
3. Freeze and independently review the P2-F Gate 2 manifest before confirmatory held-out access.
4. Start P2-B only after P2-F supplies eligible, correctly linked repeated segments.
5. Keep P2-A/C/D/E dependency-gated.

Independent Session G re-review:
https://github.com/Isshhin/stridelab-development/blob/research/runna-garmin-coaching-20261102/docs/research/2026-11-04-engine2x-session-g-independent-rereview.md

Current Phase 2 status:

P2-F_FEASIBILITY_PREPARATION_AUTHORIZED

CONFIRMATORY_HELD_OUT_EVALUATION_BLOCKED_PENDING_GATE_2_MANIFEST

Do not begin production implementation simply because Session G is complete.

## Phase 2 sequence

### P2-F first

Establish:

- authorized data admission
- parser/source correctness
- plan version before decision
- plan-to-performed linkage
- reason for partial/missed/substituted work
- later outcome linkage
- duplicate and account isolation
- rights/withdrawal handling

### P2-B second

Validate:

- speed at comparable HR
- HR at comparable speed
- drift/decoupling as descriptive measures
- environmental matching
- sensor/route continuity
- typical error
- usable case coverage

Repeatability is not yet permission to progress training.

### Then dependent tracks

- P2-A athlete-state estimation
- P2-C progression and re-entry
- P2-D adaptive prescription
- P2-E cross-discipline and strength interaction

Follow the dependency graph in Session G rather than starting all models immediately.

## Engine 1.5

Branch:

feature/engine15-versioned-foundation-20261103

Draft PR:

[PR #37](https://github.com/Isshhin/stridelab-development/pull/37)

Latest verified head:

66fd5cf75226d8785fe599986f3c6f2d18be7668

Status:

**FEATURE-FROZEN FOR TRACKSIDE INTEGRATION / SAFE FALLBACK**

Do not add new coaching features.

Preserve:

- deterministic behavior
- bounded progression
- conservative quality
- mixed-discipline scheduling
- reviewable changes
- completed-history integrity
- session locks
- availability reconciliation
- stale-state rejection
- offline persistence
- backup/rollback
- Engine 1.0 fallback

Engine 1.5 should be treated as the conservative control while Engine 2.x is validated.

Do not enable it in Trackside until controlled integration is explicitly tested.

## Trackside 3.0

Branch:

design/trackside3-figma-parity-no-build-20261103

Latest verified head at this handoff:

ebdf670766eb45d3ba1910d6f3cf6f995b572d1b

Status:

**SECONDARY ACTIVE RECONSTRUCTION / VERIFICATION**

Trackside may continue advancing in parallel, but it must not drive Engine 2.x architecture.

Use the approved Figma as the current visual source of truth.

Preserve accepted work and verify rendered Android output.

Do not create a release APK unless explicitly assigned.

The current Figma is a baseline, not a permanent design ceiling.

## Coaching findings that should survive session changes

Unless new evidence materially changes them:

- athlete state is multidimensional
- HRV, sleep and resting HR are contextual signals, not standalone controllers
- matched-HR pace is promising but needs repeatability before progression use
- MAF-style monitoring is testable, but the 180 Formula is not a validated personal threshold
- no universal polarized/pyramidal/threshold-heavy winner is established
- watch Zone 2 labels are not automatically LT1, VT1, Fatmax or lactate flux
- universal 10% and ACWR rules should not become personal safety cutoffs
- session exposure and weekly exposure must remain separate
- taper, deload and interruption/re-entry are distinct decisions
- environment must be separated from fitness interpretation
- concurrent-training interference is context and endpoint dependent
- active recovery should not be mandatory
- synthetic correctness is not athlete-benefit evidence
- real-athlete data and prospective validation are now the main bottlenecks

## Competitive milestones

### One tier behind mainstream

Require:

- reproducible plan generation
- multidimensional athlete-state estimation
- response-aware progression
- reason-aware interruption/re-entry
- adaptive workout dose
- environment-aware interpretation
- aerobic-efficiency monitoring with safeguards
- explicit uncertainty/abstention
- deterministic reviewable revisions
- real-data calibration

### On-par candidate

Additionally require:

- longitudinal athlete-specific adaptation
- robust missing/conflicting-signal handling
- mature strength/cross-discipline decisions
- forward-time validation
- athlete-held-out validation
- prospective shadow testing
- controlled outcome evaluation for major adaptations
- no regression of Engine 1.5 reliability

Do not describe StrideLab as on par from feature count.

## Whole-athlete direction

The long-term product remains broader than running.

After the running coaching foundation is credible, deepen:

- strength
- calisthenics
- conditioning
- mobility
- plyometrics
- isometrics
- movement preparation
- recovery support

New domains must share athlete state, scheduling, adaptation logic, and evidence rules rather than become isolated modules.

## External training and cross-discipline adaptation rule

Engine 2.x must treat training performed outside a StrideLab-generated plan as real athlete exposure when provenance and confidence are sufficient.

The engine must distinguish at minimum:

- `PLANNED_BY_STRIDELAB`
- `PLANNED_EXTERNALLY` when the source/intent is known
- `OBSERVED_EXTERNAL_TRAINING` when the activity is detected but was not prescribed by StrideLab
- `UNPLANNED_OR_UNKNOWN_INTENT` when intent is unclear

StrideLab must never pretend it prescribed an external session.

### Core behavior

If a user consistently performs training outside the StrideLab plan, Engine 2.x should learn that pattern conservatively and account for it when planning every other discipline.

Examples:

- Consistent external running should contribute to running frequency, volume, intensity, long-run exposure, fatigue, and lower-body load when Strength, Calisthenics, Conditioning, or Recovery are planned.
- Consistent external strength training should affect lower-body fatigue, interference risk, recovery spacing, and the placement/dose of running quality sessions, long runs, hills, sprints, plyometrics, calisthenics legs, or conditioning.
- External calisthenics or conditioning should likewise contribute to relevant muscular, metabolic, and scheduling load when running or strength is prescribed.
- The same principle applies vice versa across all supported disciplines.

The engine should reason about the athlete's actual training life, not only the sessions StrideLab generated.

### Pattern recognition

Do not infer a stable routine from one isolated activity.

Repeated-pattern detection should consider, as evidence allows:

- frequency over multiple weeks;
- repeated weekday/time patterns;
- repeated discipline/session type;
- completed dose;
- similarity of intensity and duration;
- consistency of source/device;
- explicit user confirmation when available.

Until the pattern is sufficiently supported, treat it as observed exposure rather than a stable planned routine.

### Adaptation rules

External training may influence:

- total weekly load;
- discipline-specific load;
- intensity distribution;
- long-run exposure;
- quality-session spacing;
- lower-body interference;
- recovery requirements;
- double-day feasibility;
- progression ceilings;
- deload/re-entry decisions;
- schedule placement;
- confidence in future prescriptions.

However, external training must not automatically trigger aggressive reductions or progressions. The engine should adapt proportionally to evidence quality and uncertainty.

### Safeguards

Engine 2.x must preserve:

- provenance of every observed session;
- source and ownership boundaries;
- no double-counting of imported and manually logged copies of the same activity;
- no silent conversion of external training into StrideLab-planned training;
- no silent takeover or rewriting of an external plan;
- user review for major plan adaptations caused by external training;
- explicit uncertainty when intent is unknown;
- `ABSTAIN_NEED_MORE_INFORMATION` when the engine cannot safely infer the role of external training;
- rollback and versioned persistence of adaptations.

If the user follows an external running or strength program consistently, StrideLab's goal is to coordinate around it safely and intelligently rather than force the athlete into a competing StrideLab-only schedule.

This is a core whole-athlete requirement for Engine 2.x and should be designed at the state/model/contract level before implementation.

## Engine 2.x reasoning foundations and visualization-first product rule

The following foundations are approved for Engine 2.x research/specification and should be defined before broad implementation.

### Coaching modes

Engine 2.x should explicitly know its role for each athlete and, where useful, per discipline:

- `PRIMARY_COACH`: StrideLab owns plan generation and adaptation.
- `COORDINATOR`: another plan/coach may own the discipline; StrideLab coordinates around it.
- `OBSERVE_ONLY`: StrideLab analyzes state and training but does not prescribe changes.
- `HYBRID`: StrideLab owns some disciplines while external systems own others.

The engine must not silently take control of an externally owned plan.

### Universal Training Exposure Ledger

All training should enter one canonical exposure history regardless of source.

The ledger should be able to represent:

- StrideLab-prescribed sessions;
- externally planned sessions;
- imported wearable/device activities;
- manually logged workouts;
- unplanned/unknown-intent training.

Prescribed work and performed work must remain separate.

Each exposure should preserve, where available:

- source/provenance;
- discipline;
- session intent;
- date/time;
- duration;
- volume/distance/load;
- intensity;
- completion;
- relevant muscle/structural demand;
- environment;
- confidence/data quality;
- plan ownership.

No duplicate activity should be counted twice.

### First-class training intent

The engine should understand what a session is trying to accomplish, not only its raw metrics.

Examples may include:

- EASY_AEROBIC
- LONG_RUN
- THRESHOLD
- VO2
- SPRINT
- MAX_STRENGTH
- HYPERTROPHY
- POWER
- SKILL
- CONDITIONING
- RECOVERY

Intent classification must retain uncertainty when the purpose cannot be determined safely.

### Separate observations, inference, and decisions

Engine 2.x must preserve three conceptual layers:

```text
OBSERVATIONS
-> INFERRED ATHLETE STATE
-> COACHING DECISION
```

Example:

```text
Observation: HRV value is below recent baseline
Inference: possible recovery suppression, confidence MODERATE
Decision: no change yet because other signals remain stable
```

A raw metric must not directly become a coaching action without passing through the state/uncertainty layer.

### Multidimensional load vector

Do not reduce all training to one opaque readiness/load score.

Track relevant dimensions separately, such as:

- aerobic exposure;
- high-intensity running exposure;
- long-run exposure;
- lower-body muscular load;
- upper-body load;
- plyometric/power stress;
- metabolic conditioning;
- structural/impact exposure where supportable;
- cross-discipline interference.

A single aggregate score may be shown for convenience only if the underlying dimensions remain available and it is not treated as ground truth.

### Confidence-aware adaptation and adaptation budget

Every meaningful adaptation should carry confidence.

Low-confidence evidence should generally produce smaller changes, observation, or abstention.

Engine 2.x should also use an adaptation budget: do not rewrite many training variables at once unless safety requires it.

Prefer the smallest change that preserves training intent.

### User overrides as preference evidence

Repeated user overrides can become evidence of stable preference or constraint.

Examples:

- preferred gym days;
- unavailable days;
- preferred double-day structure;
- recurring external-plan sessions.

Preferences may guide planning but must never weaken hard safety constraints.

### Trend persistence before adaptation

Avoid large changes from one anomalous workout.

Require persistence, repeated evidence, or strong safety evidence before making major progression/regression decisions.

Single-session anomalies should usually be interpreted conservatively.

### State freshness and decay

Evidence should have an explicit freshness model.

Recent soreness, fatigue, HRV, sleep, missed sessions, threshold performance, or external training should not carry the same weight indefinitely.

Stale evidence should decay in influence or expire.

### Explanation trace

Every meaningful coaching change should leave a structured internal trace that records:

- decision;
- confidence;
- evidence used;
- important evidence ignored/rejected;
- training intent being protected;
- what changed;
- what did not change;
- fallback/abstention reason if applicable.

This trace supports debugging, research, user review, and future auditability.

### Engine 1.5 as a counterfactual baseline

During Engine 2.x development and shadow validation, compare important decisions against what Engine 1.5 would have done.

Engine 2.x should not receive credit merely for being more complex or different.

When it diverges from Engine 1.5, the additional evidence/reasoning should justify the change.

### Confidence calibration

Engine confidence should be evaluated for calibration, not only correctness.

A decision reported as high confidence should actually prove highly dependable across similar cases.

Overconfidence is a failure mode.

### Longitudinal athlete-specific memory

Engine 2.x should learn durable athlete-specific patterns over time where evidence supports them.

Examples:

- tolerance for quality-session frequency;
- response to heavy lower-body strength before running quality;
- usual recovery time;
- environmental sensitivity;
- long-run durability pattern;
- scheduling preferences;
- recurring external training.

Longitudinal learning must preserve provenance, uncertainty, reversibility, and user control.

### Visualization-first product rule

StrideLab should expose Engine 2.x sophistication primarily through visuals, not text-heavy screens.

The product should prefer concise visual communication such as:

- timelines;
- training calendars;
- load bars;
- trend charts;
- compact comparison views;
- badges/chips;
- icons;
- confidence indicators;
- overlays;
- cross-discipline relationship views;
- before/after plan previews;
- color/shape/state encoding;
- progressive disclosure.

Text should be:

- short;
- actionable;
- secondary to the visual hierarchy;
- used for the minimum explanation needed to make a decision understandable.

Long rationale, evidence traces, and technical detail should be available on demand rather than permanently filling the screen.

Examples:

- show a visual weekly load/interference pattern first, then let the user open “Why?” for the detailed reasoning;
- show external vs StrideLab-owned sessions visually in the calendar instead of explaining ownership in paragraphs;
- show adaptation confidence with a compact indicator and concise label, with detailed evidence behind a tap;
- show athlete-state trends as charts/visual signals rather than a wall of metric cards and text.

The post-Engine 2.x UI revamp must treat visualization-first communication as a foundation requirement.

Before production reconstruction, define:

- visual grammar for athlete state;
- visual grammar for load and interference;
- visual grammar for confidence/uncertainty;
- external-vs-StrideLab ownership states;
- adaptation/review interaction patterns;
- progressive-disclosure rules;
- accessibility equivalents for every visual encoding.

The engine may be complex internally. The user experience should remain clear, compact, visual, and understandable.

## Foundation-first implementation rule

**Standing rule for all future StrideLab sessions: foundation first, implementation second.**

Before any major feature, engine generation, integration, redesign, persistence change, or release-architecture change, define the foundation first.

As applicable, establish:

- architecture and module boundaries
- stable interfaces/contracts
- source-of-truth data model
- persistence and versioning
- compatibility and migration
- fallback/rollback
- safety invariants
- branch/workstream ownership
- test strategy and acceptance gates
- CI/device verification
- explicit out-of-scope items
- freeze/promotion criteria

Preferred sequence:

```text
foundation
-> contracts
-> tests and gates
-> implementation
-> integration
-> verification
-> freeze/promotion
```

The purpose is to make implementation bounded and predictable, reduce rework and merge conflicts, and avoid wasting Codex/Work allowance on architecture discovery during implementation.

Do not start substantial coding until shared contracts, persistence implications, rollback needs, and verification gates are understood.

Small isolated bug fixes or low-risk cosmetic corrections do not require a full architecture phase, but they must preserve existing contracts and verified behavior.

Specific future applications:

- Engine 2.x: freeze the research/specification, `CoachingEngine` contract, persistence/versioning, safety boundaries, validation gates, and remote-config boundary before broad production implementation.
- Post-Engine 2.x UI revamp: define the design system, information/navigation architecture, reusable components, state contracts, responsive/accessibility rules, migration strategy, and acceptance matrix before production reconstruction.

Treat this as the default project engineering principle unless the user explicitly changes it.

## Project-wide continuity rule

The user should not need to reconstruct the roadmap or current direction after a chat interruption.

Every substantial StrideLab session should:

1. read GitHub Issue #42
2. read this handoff and the relevant branch-specific handoff
3. verify the live branch before acting
4. preserve anything newer than the recorded checkpoint
5. document meaningful work at checkpoints
6. update the branch handoff before ending or when interruption risk becomes material
7. update Issue #42 and canonical roadmap/handoff docs when priorities, branch roles, major milestones, or project direction change
8. leave the exact next best action for a fresh session

This applies to:

- Engine 2.x research
- Engine 1.5
- Trackside UI
- integration
- validation
- release/dogfood planning
- roadmap changes

Do not maintain continuity only for research.

## Development boundaries

- Do not mix Engine 1.5, Engine 2.x and Trackside changes in one branch.
- Do not build a release APK unless explicitly assigned.
- Do not call a research hypothesis validated because a synthetic test passed.
- Do not claim visual or device verification without evidence.
- Preserve user edits, completed training and session identity.
- Avoid silent data/session loss.
- Verify branch heads before continuing.
- Prefer newer live repository state over stale handoff text.

## Recovery procedure for a new chat

1. Open Issue #42.
2. Read docs/HANDOFF.md and docs/ROADMAP.md from the canonical docs branch or main once merged.
3. Verify the relevant live branch.
4. Inspect commits newer than the handoff.
5. Read the branch-specific handoff/research artifacts.
6. Continue the exact next step unless newer repository state supersedes it.
7. Do not ask the user to recreate prior context unless there is a genuine contradiction that cannot be resolved from the repository.

## Historical recovery documents

Older development checkpoints remain preserved:

- [PHASE4D3A_STATUS.md](PHASE4D3A_STATUS.md)
- [PHASE4D_FINAL_STATUS.md](PHASE4D_FINAL_STATUS.md)
- [PHASE4E_STATUS.md](PHASE4E_STATUS.md)
- [PHASE4E-FIX1-CHECKPOINT.md](PHASE4E-FIX1-CHECKPOINT.md)
- [WS4_STATUS.md](WS4_STATUS.md)
- [WS5_STATUS.md](WS5_STATUS.md)

Those files are historical. This handoff and ROADMAP.md define the current project direction.
