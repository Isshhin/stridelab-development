# StrideLab research session continuity and handoff protocol

Date adopted: 2026-11-04 (Asia/Manila)

Applies to: all StrideLab Engine 2.x research sessions, synthesis sessions, Phase 2 experiments, and later research-only implementation/validation sessions working from the shared research branch.

## Purpose

Long research chats can be interrupted by context limits, connection loss, or a deliberate move to a new session. No important research state should exist only inside one chat.

Every substantial StrideLab research session must therefore leave a durable GitHub continuation record that allows a fresh session to resume without reconstructing the conversation from memory.

This is a default requirement. The user should not need to ask for it again in every session.

## When to write or update a handoff

Create or update the handoff when any of the following occurs:

1. The session reaches a meaningful research checkpoint.
2. A major decision, architecture change, experiment design, or evidence judgment is made.
3. The branch advances substantially.
4. The session is likely becoming long enough that interruption risk is material.
5. The session is about to stop, switch focus, or hand work to another session.
6. A blocker prevents completion.
7. The user explicitly asks to preserve context.

Do not wait until the final message if significant work has already accumulated.

## Required handoff contents

The continuation record must include:

### 1. Live repository state
- repository
- branch
- latest verified branch head
- PR number and state
- whether the PR is open/draft/unmerged
- important commits added during the session

### 2. Session objective
- assigned session/experiment
- original question
- scope boundaries
- what was intentionally excluded

### 3. Work completed
- research performed
- files created or updated
- experiments registered or executed
- major evidence reviewed
- tracker updates
- relevant commit SHAs

### 4. Current findings
- strongest supported conclusions
- contradictory/null findings
- unresolved questions
- evidence classes
- applicability/confidence where relevant
- anything that must remain research-only

### 5. Decisions already made
Record decisions that a new chat must preserve unless new evidence justifies revisiting them.

Examples:
- chosen architecture
- rejected approach
- evidence gate
- experiment order
- terminology
- safety boundary
- source exclusion
- validation rule

### 6. Exact next step
State the next best action clearly enough that a new session can proceed without asking the user what to do.

Include:
- files to read first
- live state to verify
- next experiment/review
- expected output
- stopping condition

### 7. Do-not-modify boundaries
Repeat current boundaries when relevant, including:
- Engine 1.5 production code
- Trackside production UI
- APK/release infrastructure
- unrelated research branches
- PR merge state

### 8. Stale-state warning
Always state that the new session must verify the live branch and preserve anything newer than the handoff checkpoint.

A handoff is a continuation aid, not authority to overwrite newer work.

## Naming

Use a stable path when one session owns a single continuation file:

`docs/research/handoffs/<session-or-track>-handoff.md`

Examples:
- `docs/research/handoffs/session-g-synthesis-handoff.md`
- `docs/research/handoffs/p2-a-athlete-state-handoff.md`
- `docs/research/handoffs/p2-b-aerobic-efficiency-handoff.md`

For a broad coordinating chat, use:
- `docs/research/handoffs/engine2x-current-handoff.md`

Update the existing handoff instead of creating many nearly identical files unless history itself is important.

Git history preserves previous versions.

## Handoff quality rule

A good handoff should let a fresh session answer these questions immediately:

- What am I working on?
- Where is the source of truth?
- What has already been done?
- What conclusions are already established?
- What remains uncertain?
- What must I not change?
- What is the exact next step?
- What branch/commit must I verify before continuing?

If the new session would still need the user to reconstruct the previous chat, the handoff is incomplete.

## Research integrity

The handoff must distinguish:
- verified repository state
- completed research
- working hypotheses
- future plans
- unexecuted experiments
- user preferences/directives

Do not describe planned work as completed.

Do not claim a result that exists only in a chat unless it has been verified or explicitly marked as uncommitted context.

## Tracker synchronization

When a session updates its handoff, it should also update the relevant TickTick research tracker when practical so GitHub and TickTick do not disagree about whether a session is NOT_STARTED, IN_PROGRESS, REVIEW, BLOCKED, or COMPLETE.

GitHub remains the durable technical source of truth for detailed continuation context.

## Default instruction for future sessions

Every future StrideLab research prompt should be interpreted as also containing:

> Maintain a durable GitHub handoff for this session. Update it at meaningful checkpoints and before ending so another chat can resume from the live branch without asking the user to reconstruct context. Verify the live branch before every continuation and preserve newer work.

This requirement remains in force until explicitly changed by the user.
