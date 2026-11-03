# StrideLab Development Roadmap

Updated: 3 November 2026

StrideLab is currently a private developer project. The immediate goal is a stable **November 2026 dogfood build**, not a public launch.

The long-term direction is defined in [PRODUCT_VISION.md](PRODUCT_VISION.md).

## Current milestone

The current milestone is:

> Build a version of StrideLab that can be trusted for normal daily training.

That means the complete loop must work:

**plan → review → perform/import → preserve completed work → adjust safely → reopen later and trust the result**

The project should not be judged by feature count during this milestone.

The release question is:

> What could stop the athlete from trusting StrideLab with tomorrow's training?

## Active workstreams

### Trackside 3.0 reconstruction

Active branch:

`design/trackside3-figma-parity-no-build-20261103`

Last inspected head:

`0dee06b676a0aca8545843429357f0b7cfe0e1b5`

The current goal is faithful native Android reconstruction of the approved Trackside 3.0 Figma.

Current work includes:

- native plan-builder screens
- running and cross-training goals
- session counts
- availability
- editable weekly placement
- Strength Setup
- Calisthenics setup
- equipment selection
- Today
- Activity Detail
- responsive Android verification
- accessibility-scale verification

The next acceptance gate is rendered Android evidence, not source-code similarity.

At the last roadmap update:

- UI foundation checks at `0bfa1c1` passed
- emulator smoke tests at `0bfa1c1` passed
- exact-head responsive QA for `0dee06b` was still in progress

The reconstruction should not be called complete until the rendered screenshots have been compared against Figma and actionable P0/P1/P2 mismatches are resolved.

The current Figma is a baseline for implementation, not a permanent final design.

### Engine 1.5 release reliability

Active branch:

`feature/engine15-versioned-foundation-20261103`

Draft PR:

[PR #37](https://github.com/Isshhin/stridelab-development/pull/37)

Last inspected head:

`1d673bb5d63acc118dde6f24d666490a1bb1779a`

Engine 1.5 is the conservative release engine.

Its purpose is reliability, not maximum coaching sophistication.

Completed or substantially implemented work includes:

- versioned Engine 1.0 compatibility
- bounded running progression
- conservative quality prescriptions
- deload and return-from-break handling
- explicit review before coaching adjustments
- evidence-gated feedback
- offline persistence and backup validation
- multi-session scheduling
- mixed-discipline interference rules
- safe compatible double days
- durable session locks
- midweek availability review and relocation work

At the last roadmap update:

- `5979b7a` had passed both main verification and Android device verification
- `1d673bb` contained the latest availability-relocation implementation
- exact-head verification for `1d673bb` was still running

Do not call a new Engine 1.5 batch complete until the exact-head JVM/Android/device gates are green.

Do not enable Engine 1.5 in Trackside until the reliability batches are finished and integration is explicitly tested.

### Engine 2.0 research

Active branch:

`research/runna-garmin-coaching-20261102`

Draft PR:

[PR #34](https://github.com/Isshhin/stridelab-development/pull/34)

Last inspected head:

`b5e82ff6f1912576167e075dee73e0fb2ee9a7d4`

The research branch is separate from the release engine.

Current research themes include:

- athlete-state modeling
- progression and periodization
- recovery/adaptation decisions
- workout selection
- cross-discipline coordination
- fail-closed validation
- original-source provenance
- future real-world outcome readiness
- comparison against Runna and Garmin Run Coach

The latest inspected Engine 2.0 research workflow at `b5e82ff` passed.

Research findings must not automatically become release behavior.

## November execution sequence

### Phase 1: Finish Trackside rendered parity

Goal:

Make the current UI implementation trustworthy enough that visual implementation is no longer the main unknown.

Required outcomes:

- Plan Builder core flow renders correctly
- Strength Setup passes visual QA
- Calisthenics/Conditioning setup passes visual QA
- Today passes visual QA
- Activity Detail passes visual QA
- 360dp and 412dp layouts are usable
- larger font scales remain usable
- navigation and major interactions work
- compile/unit/emulator checks remain green

### Phase 2: Finish Engine 1.5 reliability

Goal:

Stop adding major Engine 1.5 intelligence once the release-reliability requirements are met.

Remaining emphasis:

- complete availability reconciliation
- edited-schedule persistence
- broad scenario matrix
- release hardening
- exact-head Android verification

Engine 1.5 should finish as:

- conservative
- deterministic
- explainable
- reliable
- safe

### Phase 3: Controlled Trackside + Engine 1.5 integration

Goal:

Connect the verified UI and verified release engine without destabilizing either branch.

Required checks:

- plan generation
- weekly scheduling
- manual placement
- session locks
- availability changes
- save/reopen
- backup/restore
- reviewable coaching changes
- Engine 1.0 fallback where required

Do not merge simply because the app compiles.

### Phase 4: End-to-end dogfood loop

The app should support:

1. profile/configuration
2. goals
3. availability
4. plan generation
5. plan review
6. manual edits
7. save
8. Today
9. perform or import activity
10. connect completed work to the plan
11. update safely
12. survive restart/offline reopen

This is the actual November milestone.

### Phase 5: Dogfood and hardening

Use StrideLab for real training.

Record failures such as:

- confusing UI
- unreliable schedule edits
- lost state
- wrong workout placement
- misleading coaching suggestions
- activity-matching problems
- persistence failures
- slow or awkward daily workflow

Only fix issues that real use or release verification justifies.

Avoid feature creep.

### Phase 6: Increase Engine 2.0 research effort

Once the dogfood path is stable, shift more effort toward Engine 2.0.

Suggested focus:

1. athlete-state modeling
2. progression and periodization
3. recovery/adaptation decisions
4. workout selection
5. real-data validation
6. comparative evaluation against established coaching systems

Engine 1.5 should remain the stable control/baseline engine.

### Phase 7: Running coaching parity

The first major coaching-quality target is running.

The goal is not feature-count parity.

The goal is competitive plan quality and adaptation behavior, including:

- aerobic development
- threshold development
- mileage progression
- quality-session selection
- long-run progression
- heat/humidity/hill context where relevant
- robustness across different athlete profiles
- sensible behavior when real life interrupts the plan

### Phase 8: Broader whole-athlete development

Only after the running foundation is credible should StrideLab expand deeper into the broader athlete model.

Potential domains include:

- strength
- calisthenics
- conditioning
- mobility
- plyometrics
- isometrics
- movement preparation
- recovery support

Each domain should be integrated through shared athlete state and adaptation logic rather than added as an isolated feature set.

## Scope rules

During the November dogfood milestone:

### Build now

- Trackside parity and interaction fixes
- Engine 1.5 reliability
- persistence
- scheduling safety
- end-to-end training loop
- integration
- test/CI reliability

### Backlog

- major visual redesign
- broad new training domains
- new live integrations that are not required for dogfooding
- commercial/public launch work
- unrelated feature expansion

### Research separately

- Engine 2.0
- competitive coaching behavior
- athlete modeling
- real-data validation methods
- advanced adaptation logic

## Release gate

A November dogfood build is acceptable when:

- no known critical data-loss issue exists
- the main training loop is usable
- planned and completed work remain connected
- availability and manual edits are preserved safely
- Engine 1.5 behavior is bounded and reviewable
- the app survives offline use and restart
- Trackside major screens are visually and functionally acceptable
- emulator/device verification is green
- real use no longer requires rebuilding core foundations every day

## Historical development records

The older Phase 4D/4E documents remain historical evidence and should not be rewritten to match the current roadmap.

Relevant files include:

- [PHASE4D3A_STATUS.md](PHASE4D3A_STATUS.md)
- [PHASE4D_FINAL_STATUS.md](PHASE4D_FINAL_STATUS.md)
- [PHASE4E_STATUS.md](PHASE4E_STATUS.md)
- [PHASE4E-FIX1-CHECKPOINT.md](PHASE4E-FIX1-CHECKPOINT.md)
- [WS4_STATUS.md](WS4_STATUS.md)
- [WS5_STATUS.md](WS5_STATUS.md)

Those files document earlier checkpoints. This roadmap defines the current direction.
