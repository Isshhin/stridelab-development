# StrideLab Project Continuity Protocol

Updated: 4 November 2026

This protocol applies to all substantial StrideLab work, not only research.

It exists so a new ChatGPT session can recover:

- the product direction
- current roadmap
- active priorities
- branch roles
- accepted decisions
- technical progress
- unresolved risks
- exact next step

without requiring the user to reconstruct previous chats.

## Canonical sources

Read these first:

1. GitHub Issue #42: current branch-independent project direction
   https://github.com/Isshhin/stridelab-development/issues/42
2. `docs/HANDOFF.md`: high-level recovery state
3. `docs/ROADMAP.md`: execution order and milestones
4. `docs/PRODUCT_VISION.md`: long-term product identity and principles
5. relevant branch-specific handoff or research artifact

The live repository always outranks stale documentation. Verify before acting.

## Applies to

This protocol is mandatory for substantial work on:

- Engine 2.x research and validation
- Engine 1.5
- Trackside UI
- Figma parity and design reconstruction
- integration
- CI/test infrastructure
- persistence/data work
- real-data validation
- APK/dogfood planning
- release work
- product roadmap
- product direction
- whole-athlete expansion

The user should not need to request continuity documentation separately in each chat.

## At the start of a session

1. Read Issue #42.
2. Read `docs/HANDOFF.md` and `docs/ROADMAP.md`.
3. Read the relevant branch-specific handoff.
4. Verify the live branch head and relevant PR state.
5. Inspect commits newer than the recorded checkpoint.
6. Preserve newer work.
7. Do not assume the previous chat's last message is current if GitHub advanced.

## During a session

Update durable context when:

- a milestone changes
- a branch advances materially
- a design is accepted/rejected
- an engine state changes
- a research conclusion changes
- a major blocker is discovered
- the roadmap changes
- priority changes
- the session becomes long enough that interruption risk is material

Do not keep important project state only inside the chat.

## Every branch/track handoff should record

- repository and branch
- current verified head
- PR and merge/draft state
- purpose of the branch
- work completed
- accepted decisions
- unresolved issues
- evidence/verification status
- files/commits to read
- do-not-modify boundaries
- exact next step
- stale-state warning

## Project-direction updates

Update Issue #42 and, when appropriate, the canonical docs whenever any of these changes:

- priority order
- major milestone
- release strategy
- engine strategy
- Trackside role
- branch role
- integration sequence
- dogfood timing
- competitive target
- scope boundaries
- long-term product direction

A technical handoff is not enough when the project strategy itself changed.

## Current direction at adoption

The current strategic sequence is:

1. P2-F data admission/linkage feasibility
2. P2-B aerobic-efficiency repeatability after the P2-F linkage gate
3. dependent P2-A/P2-C/P2-D/P2-E experiments
4. research-only Engine 2.x candidates
5. adversarial and real-data shadow validation
6. prospective athlete validation
7. Engine 2.x production candidate with Engine 1.5 fallback
8. Trackside integration
9. integrated dogfood APK
10. broader whole-athlete expansion

Session G is:

`REVIEW_PASSED / PHASE_2_BASELINE_FROZEN`

Frozen baseline:

`26a8331e642c888dd73acb94755dded063baab8d`

Current Phase 2 authorization:

`P2-F_FEASIBILITY_PREPARATION_AUTHORIZED`

`CONFIRMATORY_HELD_OUT_EVALUATION_BLOCKED_PENDING_GATE_2_MANIFEST`

Do not revert to the older APK-first roadmap unless the user explicitly changes direction.

## Status vocabulary

Use clear states instead of ambiguous prose:

- NOT_STARTED
- IN_PROGRESS
- REVIEW
- REVIEW_REQUIRED_CORRECTIONS
- BLOCKED
- REVIEW_PASSED
- FROZEN_BASELINE
- COMPLETE

Do not mark work COMPLETE merely because an artifact was produced if independent review is still pending.

## Continuity quality test

A fresh session should be able to answer:

- What is StrideLab trying to become?
- What matters most right now?
- Why is that the priority?
- What workstreams are active?
- Which branches are safe/frozen/research-only?
- What was already decided?
- What is not yet validated?
- What exactly should I do next?
- What must I not touch?

If those answers require the user to repeat the previous chat, continuity documentation is incomplete.

## Conflict resolution

If documentation disagrees:

1. verify live branch/PR state
2. prefer the newest explicitly dated project-direction decision
3. preserve accepted work
4. do not silently infer that an older roadmap still applies
5. update canonical docs after resolving the conflict

## End-of-session requirement

Before a substantial session ends or becomes vulnerable to interruption:

1. update the track handoff
2. update Issue #42 if direction/status changed
3. update ROADMAP/HANDOFF if the canonical project state changed
4. update the relevant tracker
5. leave an exact next action

This remains the default until the user explicitly changes it.
