# StrideLab

**Train the whole athlete.**

StrideLab is an Android-first training app for athletes whose training does not fit into one discipline.

Running, strength work, calisthenics, conditioning, recovery, and future performance work should not feel like separate systems competing for space in the same week. StrideLab is being built to help the athlete plan those demands together, follow the work, review what actually happened, and make better training decisions without taking control away from the athlete.

The long-term goal is broader than putting workouts in one calendar. StrideLab is intended to grow into an adaptive performance system that understands the athlete across disciplines and coordinates training around what that athlete needs to improve.

The project is still in active development. This repository is a working development project, **not a finished public release**.

## Product direction

The durable product vision is documented in [PRODUCT_VISION.md](docs/PRODUCT_VISION.md).

The short version is:

> Help athletes improve performance through adaptive, evidence-based training across endurance, strength, movement, conditioning, and recovery.

The core idea is:

**Athlete state → performance needs → adaptation targets → training prescription → completed training → updated athlete state**

That model is intentionally broader than a race-plan generator or workout tracker. It gives StrideLab room to support more of the athlete over time without turning the app into a random collection of fitness features.

## What StrideLab is trying to solve

Most training apps are designed around one primary type of training. That works until the athlete's week includes several competing demands.

StrideLab is being built around the way mixed-discipline training actually works:

- **Plan the full week.** Choose goals, available days, disciplines, and session counts in one planning flow.
- **Coordinate multiple disciplines.** Running, strength, calisthenics, and conditioning can share the same schedule.
- **Support safe double days.** Some athletes run and lift on the same day, so scheduling must account for interference and recovery.
- **Respect the athlete's choices.** Manual placements, locked sessions, and major coaching changes should not disappear silently.
- **Follow workouts inside the app.** Planned work should lead naturally into the workout itself.
- **Connect planned and completed training.** Training history should remain useful for future decisions.
- **Work offline.** Local training data and offline use are part of the core architecture.
- **Explain coaching changes.** Adaptation should be understandable, reviewable, and evidence-based.

## Trackside 3.0

The current native Android interface is called **Trackside 3.0**.

It is built with Kotlin and Jetpack Compose. The current reconstruction work is focused on bringing the native Android implementation into faithful alignment with the approved [Trackside 3.0 Figma design](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE).

The interface currently revolves around five main areas:

| Section | Purpose |
| --- | --- |
| **Today** | See what matters for today's training. |
| **Training** | Review the week, open workouts, and manage the plan. |
| **Activity** | Review recorded training and activity details. |
| **Library** | Browse exercises and training guidance. |
| **More** | Settings and supporting tools. |

The current reconstruction uses a compact dark visual system with subtle glass-like surfaces and discipline-specific color cues.

The approved Figma is the source of truth for the current reconstruction phase. It is not assumed to be the final long-term visual design. Once the app is stable enough to use every day, future design changes should be informed by real dogfooding rather than redesigning the interface before the current baseline is trustworthy.

## Coaching engines

StrideLab's coaching system is being developed in stages so reliability can improve without mixing experimental research into the app used for real training.

### Engine 1.0

Engine 1.0 is the stable compatibility baseline.

It remains important because it provides a known reference for plan generation, scheduling, persistence, and existing data behavior.

### Engine 1.5

Engine 1.5 is the release-reliability engine currently under development in [PR #37](https://github.com/Isshhin/stridelab-development/pull/37).

Its job is not to become the smartest possible coach. Its job is to become a dependable engine that is:

- deterministic
- conservative
- explainable
- safe
- compatible with existing data
- reliable offline

Current work includes bounded running progression, deload behavior, reviewable feedback, multi-session scheduling, mixed-discipline interference rules, durable session locks, and safe handling of availability changes.

Engine 1.5 is **not yet enabled in the Trackside production path**.

### Engine 2.0

Engine 2.0 is the longer-term coaching research program.

It is intentionally separate from Engine 1.5 so new ideas can be tested without destabilizing the dogfood build.

The research direction includes:

- athlete-state modeling
- progression and periodization
- recovery and adaptation decisions
- workout selection
- cross-discipline coordination
- real-data validation
- comparison against established coaching systems

Engine 2.0 is **not a production coaching engine**.

## Current milestone

The immediate milestone is a **November 2026 dogfood build** that can be trusted for normal daily training.

The current order of work is:

1. finish Trackside 3.0 rendered parity and interaction verification
2. finish Engine 1.5 reliability gates
3. integrate Trackside with Engine 1.5 in a controlled way
4. verify the complete training loop
5. use StrideLab for real training
6. fix failures discovered through actual use
7. resume Engine 2.0 research more aggressively once the daily-use path is stable

See [ROADMAP.md](docs/ROADMAP.md) for the current execution roadmap.

## Exercise Library

StrideLab's exercise library supports more than gym training.

Current library work covers:

- strength
- calisthenics
- bodyweight training
- conditioning
- mobility
- running-related exercises and drills

The library has automated validation because its data is shared by several parts of the app.

## Activity and workout data

The project includes foundations for:

- planned workouts
- completed workout records
- local persistence
- activity import
- planned-to-recorded matching
- backups and restoration
- FIT-based activity work

Broader live integrations with Garmin, COROS, Huawei, Zepp, Samsung, Apple, Intervals.icu, and similar services remain longer-term targets. They should not be treated as fully supported integrations today.

## Technology

The native Android app currently uses:

- **Kotlin**
- **Jetpack Compose**
- **Gradle**
- **AndroidX**
- **local Android persistence**
- **Garmin FIT SDK**
- **JUnit and Android instrumentation tests**
- **Python validation tools**

The Android project currently targets Android API 35, compiles with API 36, and uses JDK 17.

## Repository structure

Important areas include:

- `native-android/` for the native app, Compose UI, coaching logic, tests, and validation tools
- `android-app/` for legacy and shared Android sources still used by the native build
- `docs/` for product direction, technical decisions, research, QA, and handoff material
- `.github/workflows/` for automated build, test, emulator, and validation workflows

Much of the current work remains on isolated development branches while it is being verified.

## Current development branches

As of **3 November 2026**, the main active workstreams are:

- **Trackside 3.0 reconstruction:** `design/trackside3-figma-parity-no-build-20261103`
- **Engine 1.5:** `feature/engine15-versioned-foundation-20261103`, tracked in [PR #37](https://github.com/Isshhin/stridelab-development/pull/37)
- **Coaching research:** `research/runna-garmin-coaching-20261102`, tracked in [PR #34](https://github.com/Isshhin/stridelab-development/pull/34)
- **Trackside foundation PR:** [PR #36](https://github.com/Isshhin/stridelab-development/pull/36) remains an open draft foundation branch while the newer parity reconstruction continues separately

Always verify the current remote head before continuing work on an active branch.

## Running the verified Android baseline

The verified pre-Trackside Android integration work remains available on:

`integration/native-pre-trackside3-verified-20261030`

```bash
git clone https://github.com/Isshhin/stridelab-development.git
cd stridelab-development
git switch integration/native-pre-trackside3-verified-20261030
```

Open `native-android/` in Android Studio with JDK 17 and the required Android SDK installed.

From `native-android/`:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

The exercise-library validator can be run with:

```bash
python tools/validate_exercise_library.py
```

Development builds and branch artifacts should not automatically be treated as reviewed releases.

## Why StrideLab exists

StrideLab is not trying to collect every possible fitness feature.

The product is trying to answer a more useful question:

> What does this athlete need next, and how should the rest of their training week support it?

For the current dogfood milestone, success is simpler:

**Open the app, understand the training, complete it, record it, and trust that the plan will still make sense tomorrow.**
