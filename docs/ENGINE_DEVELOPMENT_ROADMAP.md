# StrideLab Engine Development Roadmap

Updated: 2026-11-05

This document is the canonical roadmap for StrideLab coaching-engine development from Engine 1.5 through Engine 2.x, including the planned post-Engine 2.x UI revamp.

## Current position

### Trackside 3.0

Trackside 3.0 is accepted and frozen for Engine 1.5 integration.

- Frozen Trackside head: `0b06bb894d791ee7c51f355f79d7c32316cb0e3a`
- Verified UI parent: `8c700344c646582b1ad8be91d36d1b1195a49ed8`
- Week Preview: accepted
- Draft Approval: accepted
- Smoke: 22/22
- Responsive QA: 88/88 across four profiles
- Engine 1 rules preserved

Trackside should not be reopened for broad cosmetic reconstruction during Engine 1.5 integration.

### Engine 1.5

Engine 1.5 is feature-frozen and integration-ready.

- Source branch: `feature/engine15-versioned-foundation-20261103`
- PR: #37
- Last verified source head before integration: `ff9cfdd03a9fe7ff7ff1ef7b5ccc37d8c9296ce6`
- Engine 1.0 remains the fallback/rollback path

Current integration work:

- Integration branch: `integration/trackside3-engine15-20261105`
- First integration commit: `46a543d3f84f59b7719b64c2c265147e73b6fac6`

### Engine 2.x

Engine 2.x remains a separate research program.

- Research branch: `research/runna-garmin-coaching-20261102`
- Research PR: #34
- Production activation is out of scope until the research and validation gates in this roadmap are complete

---

# Phase 1 - Finish Engine 1.5 integration

## Objective

Produce a real, usable StrideLab application with frozen Trackside 3.0 connected to Engine 1.5.

## Required outcomes

- Trackside plan-builder inputs feed the real Engine 1.5 path
- Week Preview shows genuine Engine 1.5 output
- Draft Approval persists the approved plan correctly
- Training reopens persisted Engine 1.5 plans
- Workout locking works
- Availability mutations and safe relocation work
- Unsupported conflicts remain unresolved rather than being silently forced
- Mixed-discipline scheduling and interference-aware double days remain protected
- Reviewed feedback, accept/dismiss, and stale-feedback protection work
- Offline reopen works
- Account-scoped persistence works
- Engine 1.0 fallback remains verified

## Promotion gate

Engine 1.5 becomes the active Trackside engine only after the full integration regression is green.

Target:

```text
TRACKSIDE_3_0 = FROZEN
ENGINE_1_5 = ACTIVE
ENGINE_1_0 = VERIFIED_FALLBACK
ENGINE_2_X = RESEARCH_ONLY
```

## Release checkpoint

After the exact integrated commit passes:

- JVM/unit verification
- Android unit/integration tests
- persistence and reopen tests
- fallback tests
- API 35 smoke
- Trackside responsive regression

build the release variant from that exact verified commit.

If signing material is unavailable, record the signing blocker without weakening security.

---

# Phase 2 - Personal alpha and Engine 1.5 stabilization

## Objective

Use the integrated application as the stable known-good baseline before promoting Engine 2.0.

Validate through real use:

- plan creation
- plan approval
- daily Training use
- workout opening and locking
- availability changes
- persistence across restarts
- offline behavior
- feedback review
- crash-free operation
- data integrity
- actual training usability

Fix real bugs incrementally.

Engine 1.5 remains the reference implementation against which Engine 2.0 is judged.

---

# Phase 3 - Finish Engine 2.0 research

Engine 2.0 research is not complete until the remaining evidence, validation, and specification work is closed.

## 3.1 Close Phase 2 evidence gaps

Complete:

- lawful real-data source admission
- rights, consent, governance, and provenance checks
- parser and reconciliation validation
- real-world repeatability protocols
- admitted wearable/device data validation
- matched-HR and comparable-condition evaluation
- contradictory and null evidence review

Synthetic data remains useful for falsification and edge cases, but is not sufficient proof by itself.

## 3.2 Freeze the athlete-state model

Formalize an explicit state model using evidence such as:

- fitness and fatigue
- acute and chronic response
- HRV relative to individual baseline
- resting HR
- sleep
- subjective fatigue
- soreness
- mood/stress
- session RPE
- internal load
- completion quality
- submaximal pace/HR response
- threshold response
- adherence
- interruptions
- illness context
- signal disagreement
- missing data
- measurement uncertainty

Do not reduce this to one opaque readiness score.

## 3.3 Freeze progression, deload, and re-entry policy

Formalize deterministic state transitions for:

- PROGRESS
- MAINTAIN
- REDUCE
- deload
- interruption
- race recovery
- illness
- return to long runs
- return to threshold
- return to intervals/speed
- frequency progression
- single-session spike control

Avoid universal assumptions such as a fixed 10 percent increase.

## 3.4 Freeze adaptive prescription decisions

Define when the engine chooses:

- KEEP
- REDUCE_VOLUME
- REDUCE_INTENSITY
- SHORTEN_INTERVALS
- REDUCE_REPS
- SUBSTITUTE
- MOVE
- RECOVERY
- REST

Each decision must define:

- required evidence
- confidence requirement
- explanation
- effect on the remaining week
- uncertainty behavior
- fallback behavior

## 3.5 Freeze aerobic-efficiency and environmental interpretation

Formalize:

- pace at comparable HR
- HR at comparable pace
- cardiac drift and decoupling
- durability
- route comparability
- heat
- humidity
- grade
- wind where useful
- GPS uncertainty
- HR sensor uncertainty
- accumulated fatigue
- fueling/hydration context

The engine must not interpret every slower run as a fitness decline.

## 3.6 Finish framework and product synthesis

Consolidate evidence from:

- Runna
- Garmin Run Coach
- Garmin Daily Suggested Workouts
- Run Kaizen
- TrainerRoad
- Fitbod
- RP Hypertrophy
- Stephen Seiler
- Phil Maffetone
- Iñigo San Millán
- George Brooks
- Martin Buchheit
- Carl Foster
- Iñigo Mujika
- Andrew Jones
- heat/environment researchers
- concurrent-training researchers

Classify material claims as:

- SUPPORTED_FOR_IMPLEMENTATION
- EXPERIMENTAL
- REJECTED
- UNRESOLVED

## 3.7 Freeze hard safety constraints

Production safeguards must be explicit before implementation.

Examples:

- progression ceilings
- impossible-schedule prevention
- interference constraints
- stale-state protection
- persistence integrity
- invalid prescription prevention
- uncertainty-based conservatism
- rollback behavior
- compatibility boundaries

## 3.8 Produce the implementation-ready Engine 2.0 specification

The specification must define:

- inputs
- outputs
- state dictionary
- transitions
- formulas where evidence supports them
- decision rules/state machines
- uncertainty handling
- persistence requirements
- safety invariants
- fallback behavior
- versioning
- test invariants
- promotion gates

The implementation should not need to invent new coaching logic.

## 3.9 Independent adversarial review

Before research freeze, explicitly ask:

- Which assumptions remain unsupported?
- Where is the engine overconfident?
- Which signals are too noisy?
- Which decisions could create excessive load?
- Which models contradict each other?
- Which behaviors lack real-data validation?
- What breaks under missing or conflicting data?

## Research freeze gate

Research is considered complete only when:

```text
ENGINE_2_RESEARCH = COMPLETE
ARCHITECTURE = FROZEN
IMPLEMENTATION_SPEC = READY
VALIDATION_PROTOCOL = FROZEN
SAFETY_RULES = FROZEN
REMOTE_CONFIG_BOUNDARY = FROZEN
```

---

# Phase 4 - Establish the stable engine boundary

Before Engine 2.0 becomes a production dependency, Trackside should communicate through a stable engine contract rather than depending directly on engine-specific internals.

Target:

```text
Trackside
    |
    v
CoachingEngine
    |-- Engine 1.5
    |-- Engine 2.0
```

The purpose is to let Engine 2.0 be introduced, tested, and rolled back without reconnecting the entire UI.

Engine 1.5 remains the known-good production implementation while Engine 2.0 matures.

---

# Phase 5 - Implement Engine 2.0 separately

Engine 2.0 should initially be a candidate implementation behind the stable coaching-engine contract.

Requirements:

- deterministic behavior where practical
- explicit explanations
- explicit uncertainty
- strong unit tests
- state-machine tests
- scenario tests
- persistence/version tests
- invariant tests
- adversarial tests
- offline behavior
- failure-safe defaults

Initial state:

```text
Engine 1.5 = production/default
Engine 2.0 = candidate
```

Do not immediately replace Engine 1.5.

---

# Phase 6 - Add Engine 2.x remote configuration

This is intentionally deferred until Engine 2.0.

Do not retrofit it into the current Engine 1.5 integration.

Target:

```text
Trackside
    |
    v
CoachingEngine
    |
    v
Engine 2.x runtime
    |
    v
EngineConfigProvider
    |-- bundled safe defaults
    |-- previous/last known-good config
    |-- validated remote config
```

## Purpose

Frequent Engine 2.x coaching experiments should not require a full Android rebuild and reinstall when the installed runtime already supports the behavior being tuned.

## Remote-configurable examples

Potential remotely configurable values include:

- progression parameters
- deload parameters
- workout selection weights
- workout templates
- intensity boundaries
- environmental coefficients
- fatigue-response thresholds
- confidence thresholds
- experiment flags
- supported rule parameters

## Changes that still require an APK

A new APK remains required for changes such as:

- new Kotlin/Java executable logic
- algorithms unsupported by the installed runtime
- database/schema migrations
- new Android permissions
- new sensors
- new wearable integrations
- new UI capabilities
- new persistence structures
- engine API changes
- security-critical runtime changes

## Stability rules

Remote configuration must never disable hard compiled safety constraints.

Every config must be:

- versioned
- schema-validated
- range-validated
- engine-version compatible
- authenticated or integrity checked
- staged before activation
- rollback capable

Fallback order:

```text
validated remote config
    -> last known-good local config
    -> bundled safe defaults
```

The network must not be required for normal coaching operation.

## Version separation

Track independently:

```text
App version
Engine runtime version
Engine config version
```

Example:

```text
App: 1.4.0
Engine runtime: 2.0.0
Engine config: 2.0.8
```

A config-only update can therefore be tested without rebuilding the APK.

---

# Phase 7 - Integrate Engine 2.0 alongside Engine 1.5

Do not perform an immediate hard replacement.

Initial integration:

```text
Engine 1.5 = production baseline
Engine 2.0 = candidate/shadow
```

Trackside should be able to exercise both engines through the same stable contract.

Persistence and versioning must identify which engine/config produced a plan.

---

# Phase 8 - Shadow comparison and replay validation

Run Engine 1.5 and Engine 2.0 against identical scenarios.

Compare:

- plan structure
- weekly progression
- long-run progression
- quality-session selection
- deload behavior
- adaptation
- environmental response
- interruption/re-entry
- feedback decisions
- uncertainty handling
- schedule feasibility

Validation sources:

- deterministic synthetic scenarios
- historical datasets
- admitted academic/institutional datasets
- authorized wearable/device exports
- owner training history
- prospective owner testing

Disagreements should be investigated rather than automatically treated as Engine 2.0 improvements.

---

# Phase 9 - Owner field testing

After shadow validation is acceptable:

```text
Engine 2.0 = active for owner testing
Engine 1.5 = immediate fallback
```

Evaluate:

- prescription usefulness
- progression
- fatigue response
- environmental interpretation
- adaptation quality
- schedule changes
- false-positive recovery decisions
- overly aggressive decisions
- overly conservative decisions

Rollback to Engine 1.5 must remain simple.

---

# Phase 10 - Promote Engine 2.0

Engine 2.0 becomes the default only after research, replay validation, regression, and field testing justify promotion.

Target:

```text
Engine 2.0 = default
Engine 1.5 = fallback
Engine 1.0 = legacy compatibility where still required
```

Older engines should be retired only after migration and rollback behavior are proven safe.

---

# Phase 11 - Major UI revamp after Engine 2.x is stable

The broad StrideLab UI revamp is deliberately deferred until Engine 2.x is done and stable.

Do not start the major visual/product redesign while Engine 2.0 is still changing its state model, inputs, outputs, persistence, or adaptation behavior.

## Revamp gate

The UI revamp starts only when:

- Engine 2.x research is frozen
- Engine 2.x implementation is complete enough for normal use
- the stable `CoachingEngine` contract exists
- Engine 2.x persistence/versioning is stable
- shadow/replay validation is acceptable
- owner field testing has demonstrated stable behavior
- Engine 1.5 fallback works
- major Engine 2.x data-contract changes are no longer expected

Target gate:

```text
ENGINE_2_X = STABLE
ENGINE_2_X_DEFAULT_OR_PROMOTION_READY = YES
ENGINE_1_5_FALLBACK = VERIFIED
ENGINE_CONTRACT = STABLE
UI_REVAMP = START
```

## Why the revamp is deferred

Waiting until Engine 2.x is stable avoids redesigning the same screens multiple times while engine requirements are still moving.

The post-Engine 2.x UI revamp can then be designed around the final product capabilities, including:

- richer athlete-state presentation
- clearer coaching explanations
- adaptation/review flows
- environmental context
- confidence and uncertainty where appropriate
- improved Training and Today experiences
- improved plan management
- refined multi-discipline presentation
- stronger accessibility and responsive behavior
- a more polished visual system than the current Trackside 3.0 bridge

Trackside 3.0 should therefore be treated as the stable Engine 1.5/early Engine 2.x product shell, not the final long-term visual design.

---

# Phase 12 - Future Engine 2.x development

Once the remote-config boundary is stable, routine coaching tuning should usually follow:

```text
research
-> config/rule parameter change
-> automated validation
-> publish config version
-> app retrieves candidate
-> validate
-> activate
-> owner tests
```

This avoids a full APK cycle for every supported coaching adjustment.

A new APK remains appropriate when the executable runtime itself changes.

---

# Promotion principles

1. Known-good behavior is more important than newest behavior.
2. A new engine must earn promotion through evidence and tests.
3. Missing or conflicting data should reduce confidence, not create false certainty.
4. Remote configuration may tune supported behavior but cannot bypass compiled safety limits.
5. Offline operation must remain functional.
6. Persistence must record enough version information to reproduce and explain decisions.
7. A fallback engine/config remains available until the replacement is proven.
8. Trackside should not need to be rebuilt from scratch for every engine generation.
9. Major UI redesign should follow a stable product/engine contract, not precede it.

---

# Immediate sequence

```text
Finish Engine 1.5 integration
-> full integrated regression
-> release/personal alpha build
-> stabilize Engine 1.5 baseline
-> finish Engine 2.0 research
-> freeze Engine 2.0 specification
-> establish stable CoachingEngine boundary
-> implement Engine 2.0 candidate
-> add versioned EngineConfigProvider
-> shadow/replay validation
-> owner field test
-> promote Engine 2.x when justified
-> begin major UI revamp
-> continue Engine 2.x config/runtime evolution
```

Update this roadmap whenever a phase is formally frozen, promoted, or materially re-scoped.
