# StrideLab

**One place for the training you actually do.**

Training rarely fits into one neat category. Some weeks include easy runs, hard sessions, long runs, strength work, calisthenics, and whatever else life leaves room for. Keeping track of all of it should not require several different apps or a lot of manual planning.

That is the idea behind **StrideLab**.

StrideLab is an Android-first training app that brings running, strength training, calisthenics, and conditioning into one place. The goal is simple: make it easier to plan your week, follow your workouts, review what you actually did, and make better training decisions without taking control away from the athlete.

The app is still in active development. This repository is a working project, **not a finished public release**.

## What StrideLab is trying to solve

Most training apps are built around one type of training. That works until your week includes more than one discipline.

StrideLab is being built around the way people actually train:

- **Plan a full training week.** Choose your goals, available days, and the types of sessions you want to include.
- **Mix different disciplines.** Running, strength, calisthenics, and conditioning can live in the same schedule instead of separate calendars.
- **Support more than one session in a day.** Some athletes run and lift on the same day, so the planner needs to handle that properly.
- **Follow workouts inside the app.** Open a session, move through exercises or sets, and record what you completed.
- **Keep a useful training history.** Planned sessions and completed work should stay connected instead of becoming separate pieces of data.
- **Keep important changes under your control.** Coaching suggestions should be understandable and reviewed before they change an approved plan.
- **Work well offline.** Local training records and offline use are important parts of the app, not afterthoughts.

Some of these parts are already implemented in the Android development branches. Others are still being refined, tested, or redesigned.

## Trackside 3.0

The current Android redesign is called **Trackside 3.0**.

It is being built with Jetpack Compose and focuses on a cleaner, more compact layout that gives useful training information without filling the screen with unnecessary text.

The app currently revolves around five main sections:

| Section | What it is for |
| --- | --- |
| **Today** | See today's planned sessions and recent activity. |
| **Training** | Review your week, open workouts, and manage your plan. |
| **Activity** | Look back at recorded training and activity details. |
| **Library** | Browse exercises and training guidance. |
| **More** | Open settings and other supporting tools. |

The visual direction uses a dark interface, subtle glass-like surfaces, and different colors for running, strength, calisthenics, and recovery-related information.

We are building from the [Trackside design in Figma](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE). The native Android version is still being brought in line with that design, so it should not be considered a one-to-one reproduction yet. Responsive behavior, accessibility, device testing, and visual fidelity are still being worked on.

## Coaching

StrideLab's coaching system is being developed in stages.

We would rather improve it gradually and test each step than hide a lot of behavior behind a system that nobody can explain.

### Engine 1.0

Engine 1.0 is the current baseline for plan creation and scheduling.

It is intentionally predictable. The main purpose is to provide a stable foundation for building plans, scheduling sessions, and preserving existing training data.

### Engine 1.5

Engine 1.5 is an ongoing development effort built on top of that foundation.

The work currently focuses on things such as:

- safer running progression
- more conservative workload limits
- better handling of return-from-break periods
- clearer quality-session prescriptions
- training feedback based on workouts the athlete explicitly recorded
- suggestions that still require review before they affect the plan

Engine 1.5 is **not currently enabled in the main app experience**.

### Engine 2.0

Engine 2.0 is the longer-term research direction.

It is being developed separately from the production app so we can study coaching behavior, athlete scenarios, progression rules, and adaptation logic without experimenting on the main training system.

The research work includes an independent athlete simulation and validation setup. It is there to help us test ideas before they become app features.

Engine 2.0 is **not a production coaching engine**.

## Exercise Library

StrideLab also includes an exercise library for more than just gym training.

The current library work covers:

- strength training
- calisthenics
- bodyweight exercises
- conditioning
- mobility
- running-related exercises and drills

The library has its own validation tools and automated checks because exercise data is used by several parts of the app.

## Activity and workout data

The project already includes foundations for recording workouts, storing plans locally, importing activity data, matching imported activities to planned sessions, and preserving backups.

FIT file support is part of the Android development work.

Broader live integrations with Garmin, COROS, Huawei, Zepp, Samsung, Apple, Intervals.icu, and similar services are part of the longer-term direction. They should not be treated as fully supported live integrations today.

## Technology

The native Android app currently uses:

- **Kotlin**
- **Jetpack Compose**
- **Gradle**
- **AndroidX**
- **local Android persistence**
- **Garmin FIT SDK**
- **JUnit and Android instrumentation tests**
- **Python validation tools** for exercise-library and repository checks

The Android project currently targets Android API 35, compiles with API 36, and uses JDK 17.

## Repository structure

Most of the current Android development work lives outside the repository's `main` branch while it is being reviewed.

Important areas include:

- `native-android/` for the current native Android app, Compose UI, coaching logic, tests, and validation tools
- `android-app/` for legacy and shared Android sources still used by the native build
- `docs/` for research notes, technical decisions, QA reports, and handoff documents
- `.github/workflows/` for automated build, test, emulator, and validation workflows

Because the project is being developed through isolated branches and draft pull requests, some of these paths are not available on `main` yet.

## Running the Android development build

The verified pre-Trackside Android integration work is currently on its own branch.

```bash
git clone https://github.com/Isshhin/stridelab-development.git
cd stridelab-development
git switch integration/native-pre-trackside3-verified-20261030
```

Open `native-android/` in Android Studio and make sure JDK 17 and the required Android SDK are installed.

From the `native-android/` directory:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

The exercise-library validator can be run with:

```bash
python tools/validate_exercise_library.py
```

It currently uses `jsonschema==4.26.0` in the development and CI environment.

These commands are for development and testing. An APK built from a development branch should not automatically be considered a reviewed release.

## Current development status

As of **3 November 2026**, the major workstreams are being kept separate so each one can be reviewed and tested without interfering with the others.

- [PR #32: Native Android integration baseline](https://github.com/Isshhin/stridelab-development/pull/32) contains the verified pre-Trackside Android foundation.
- [PR #36: Trackside 3.0](https://github.com/Isshhin/stridelab-development/pull/36) contains the current UI redesign, navigation work, responsive behavior, and Figma implementation.
- [PR #37: Coaching Engine 1.5](https://github.com/Isshhin/stridelab-development/pull/37) contains the next coaching-engine work and its safety tests.
- [PR #34: Coaching research](https://github.com/Isshhin/stridelab-development/pull/34) contains research on Runna and Garmin Run Coach that may inform future coaching decisions.

The current priority is to finish the native Android experience, keep existing training data safe, and only introduce coaching changes once they have passed the required tests and review.

## Why StrideLab exists

StrideLab is not meant to be a collection of every fitness feature we can think of.

The goal is to build something useful for people who train across different disciplines and want one place where their week makes sense.

If the app can help someone open it on a normal training day, understand what they are supposed to do, complete the session, and move on with their day, then it is doing its job.
