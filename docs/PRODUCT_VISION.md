# StrideLab Product Vision

Updated: 3 November 2026

## Brand promise

**Train the whole athlete.**

StrideLab is being built for athletes whose performance depends on more than one type of training.

The product should help the athlete understand what matters now, coordinate the work that supports it, complete the training, and learn from what actually happened.

## Mission

Help athletes improve performance through adaptive, evidence-based training across endurance, strength, movement, conditioning, and recovery.

## The problem

Athletes rarely train in perfectly isolated categories.

A runner may also lift. A calisthenics athlete may need conditioning. Strength work can help one goal while interfering with another if it is scheduled badly. Recovery, available time, recent training, and the athlete's actual response all affect what the next useful session should be.

Most tools solve one slice of that problem.

StrideLab's long-term goal is to coordinate the slices around the athlete.

## Core model

The long-term coaching model is:

**Athlete state → performance needs → adaptation targets → training prescription → completed training → updated athlete state**

### Athlete state

What is currently true about the athlete?

Examples include:

- recent training volume
- training consistency
- performance history
- current capabilities
- discipline experience
- completed and missed sessions
- recovery signals
- available training days
- time available per day
- current goals
- recent interruptions or return-from-break status

The athlete should not be reduced to a single race time, fitness score, or experience label.

### Performance needs

What currently limits the athlete's progress toward the goal?

Examples might include:

- aerobic durability
- threshold ability
- speed
- running economy
- maximal strength
- strength endurance
- movement capacity
- skill development
- conditioning
- recovery capacity

The same performance result can come from very different athlete profiles, so the plan should eventually respond to the underlying needs rather than only the headline result.

### Adaptation targets

What should training try to improve next?

The coaching system should translate performance needs into a small number of useful adaptation targets rather than prescribing sessions without a reason.

### Training prescription

Which sessions best create those adaptations while fitting the athlete's real week?

This includes:

- session selection
- frequency
- duration
- volume
- intensity
- exercise choice
- ordering
- spacing
- double-day compatibility
- progression
- deloading
- recovery decisions

### Completed training

The system should care about what the athlete actually did, not only what was planned.

Completed work should remain connected to the plan so future decisions can use real evidence.

### Updated athlete state

Training should change the model of the athlete over time.

The long-term goal is a feedback loop, not a static plan generator.

## What StrideLab is today

StrideLab is currently an Android-first multi-discipline training app under active development.

The current implementation focuses on:

- running
- strength
- calisthenics
- conditioning
- weekly planning
- multi-session scheduling
- workout execution
- activity history
- offline-first persistence
- reviewable coaching changes

The current UI generation is Trackside 3.0.

The current release-reliability coaching work is Engine 1.5.

Engine 2.0 is a separate research program.

The product should not claim capabilities that are still only part of the long-term vision.

## Near-term product goal

The immediate milestone is not a public launch.

The current strategic priority is to mature and validate Engine 2.x before the next major dogfood build.

The user explicitly accepts delaying the compiled APK if that delay materially improves coaching quality.

The near-term sequence is:

1. complete and review the Engine 2.x synthesis architecture
2. validate data admission and plan-to-performance linkage
3. validate repeatable athlete measurements
4. test athlete-state, progression, adaptive-prescription, and cross-discipline decisions
5. build research-only Engine 2.x candidates
6. run shadow and prospective validation
7. harden a production candidate while preserving Engine 1.5 as fallback
8. integrate the mature engine into Trackside
9. compile the next major dogfood APK

The eventual dogfood loop still must be trustworthy:

1. open StrideLab
2. understand today's training
3. review the week
4. adjust availability or placement when life changes
5. complete or import the workout
6. preserve what actually happened
7. update the plan safely
8. close and reopen the app without losing trust in the schedule

Reliability remains non-negotiable, but the current project direction no longer treats the APK calendar as more important than coaching-engine maturity.

## Coaching architecture

StrideLab should evolve through three conceptual layers.

### 1. Athlete Model

Represents the athlete's current state, history, constraints, and capabilities.

### 2. Coaching Engine

Decides what adaptations should be prioritized and how the plan should progress.

### 3. Session and Exercise Engine

Turns those priorities into concrete training across running, strength, calisthenics, conditioning, mobility, and future performance domains.

This separation matters because the product should not confuse "what the athlete needs" with "which workout happens to be available in the library."

## Current coaching strategy

### Engine 1.0

Keep as a stable compatibility baseline.

### Engine 1.5

Make it conservative, deterministic, explainable, and dependable enough for dogfooding.

Do not keep adding advanced intelligence once the release-reliability requirements are met.

### Engine 2.x

Use as the primary research and validation track for the next coaching generation.

Phase 1 discovery and synthesis now define a multidimensional athlete-state architecture with explicit uncertainty, abstention, training intent, reviewable decisions, and separate validation gates.

Current priorities include:

- authorized data admission and plan-to-performance linkage
- aerobic-efficiency measurement repeatability
- athlete-state modeling
- progression and re-entry
- adaptive session modification
- cross-discipline interference
- real-data calibration
- comparison against established coaching systems
- uncertainty and fail-closed behavior
- prospective shadow and outcome validation

Engine 2.x should replace simpler logic only when the relevant measurement, prediction, decision, and safety gates support the change.

## Product principles

### Athlete control

Major changes should be reviewable.

The app should advise, explain, and help the athlete make good decisions without silently rewriting an approved plan.

### Evidence before confidence

A plausible coaching rule is not automatically a good rule.

The system should distinguish:

- researched behavior
- validated behavior
- synthetic validation
- real-world validation
- assumptions
- unresolved uncertainty

### Reliability before sophistication

A simpler plan that survives edits, restarts, offline use, and schedule changes is more valuable than a clever plan that cannot be trusted.

### Multi-discipline by design

Running, strength, calisthenics, conditioning, and future training domains should be coordinated through shared athlete state and scheduling rules rather than added as disconnected modules.

### Explainability

The athlete should be able to understand why an important change was proposed.

### Offline first

Core training use should not depend on a live network connection.

### Real-world adaptability

The plan should survive:

- changed availability
- missed workouts
- moved sessions
- locked sessions
- return from illness or interruption
- limited time
- mixed training weeks
- double days
- incomplete activity data

### No silent loss

Sessions, edits, completed work, and important athlete choices should not disappear because the engine regenerated a plan.

## Design principles

Trackside should remain:

- compact
- information-dense
- visually calm
- fast to scan
- clear about the next action
- consistent across disciplines
- usable on small Android screens
- accessible at larger font scales

The current approved Figma is the source of truth for the Trackside 3.0 reconstruction.

It is a baseline, not a permanent design ceiling.

Future redesign should be driven by real usage and observed friction.

## Long-term scope

The whole-athlete vision may eventually include:

- running
- strength
- calisthenics
- conditioning
- mobility
- plyometrics
- isometrics
- movement preparation
- recovery support
- broader endurance or sport-specific work where justified

This is not permission to add every possible fitness feature.

A new domain belongs in StrideLab only if it improves the system's ability to understand or train the athlete coherently.

## Integrations

Longer-term integration targets include platforms and activity sources such as:

- Garmin
- COROS
- Huawei
- Zepp
- Samsung
- Apple
- Intervals.icu
- FIT
- GPX
- TCX

Integrations should reduce manual work and improve the athlete model. They should not become a substitute for a reliable offline core.

## Research posture

StrideLab's research program can learn from established coaching systems, exercise science, public documentation, controlled simulations, and ethically obtained real-world data.

The goal is not to copy another product.

The goal is to understand useful behavior, test it independently, and adopt only what survives StrideLab's own validation.

## What success looks like

### Near term

StrideLab develops a defensible, validated coaching architecture and then becomes trustworthy enough to use for real daily training with that architecture integrated safely.

### Medium term

Running plan generation and adaptation become competitive with strong dedicated running-coaching products while retaining mixed-discipline awareness.

### Long term

StrideLab becomes a coherent athlete-performance system that can answer:

> What does this athlete need next, and how should the rest of their training support it?

That is the standard the product should grow toward.
