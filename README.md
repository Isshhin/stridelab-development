# StrideLab

**One place for the training you actually do.**

A training week doesn't always fit neatly into one sport. You might run in the morning, work on strength later in the day, and make time for calisthenics or conditioning somewhere in between. Keeping track of all that shouldn't mean juggling several apps and trying to make their plans work together.

That's why we're building **StrideLab**: an Android-first training companion that brings running, strength training, calisthenics, and conditioning into a single, practical workflow. The aim is to make training easier to plan, follow, and understand—while leaving important decisions in the athlete's hands.

StrideLab is still in active development. This repository is a working project, **not a finished public release**.

## What we're building

StrideLab is designed around a few everyday needs:

- **A training plan that fits your week.** Set goals, choose when you can train, and arrange sessions across disciplines—including more than one session on a day when it makes sense.
- **Workouts you can actually follow.** Review planned sessions, move through exercises and sets, record what you completed, and keep your training history.
- **Running and strength in the same picture.** Easy runs, quality sessions, long runs, lifting, and bodyweight work should be considered together rather than competing for space on separate calendars.
- **A useful exercise library.** Exercises and guidance for strength, calisthenics, conditioning, mobility, and running.
- **Your data remains yours.** Offline-first operation and local records are important design priorities. Changes to an approved training plan should be understandable and require your approval.

These aren't all equally finished. Plan scheduling, guided workouts, local training records, file-import foundations, and the exercise library have implementations in the Android development work. The experience is still being refined and tested.

## How the app is taking shape

Our current Android direction is called **Trackside 3.0**. It's a more focused interface built with Jetpack Compose, with five main destinations:

| Section | What it's for |
| --- | --- |
| **Today** | See what's planned for today and catch up on recent activity. |
| **Training** | Look through your week, open workouts, and manage your plan. |
| **Activity** | Review recorded training and activity details. |
| **Library** | Find exercises and guidance. |
| **More** | Access settings and supporting tools. |

The visual direction uses a dark, information-rich layout, restrained glass-like surfaces, and distinct colors for different types of training. We're working from the [Trackside design in Figma](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE), but **the current native UI is not yet a one-to-one reproduction**. Visual checks, responsiveness, accessibility, and device testing remain part of the work.

## Coaching: practical first, more advanced later

StrideLab's coaching system is being developed in stages rather than presented as an all-knowing algorithm.

**Engine 1.0** is the current baseline for deterministic plan creation and scheduling. It provides an understandable starting point and keeps existing plan behavior stable.

**Engine 1.5** is a separate, opt-in development effort to improve running prescriptions, apply more conservative progression limits, and suggest changes based on explicitly recorded training feedback. These suggestions are meant to be reviewed, not silently applied. Engine 1.5 is **not enabled in the main app experience**.

**Engine 2.0** is a longer-term research project. Its independent research and athlete-simulation work explores how coaching decisions can be tested across different athletes and circumstances before they are trusted in the app. It is **not a production coaching engine**.

We care about explaining *why* a workout is suggested as much as what the workout says.

## Technology and project structure

StrideLab's native Android work uses **Kotlin, Jetpack Compose, and Gradle**. The project also includes local persistence and backup logic, workout and activity models, automated Android/JVM tests, and Python validation tools for the exercise library.

The main places to look in the development branches are:

- `native-android/` — native Android app, Compose screens, coaching and training logic, tests, and library validation tools.
- `android-app/` — legacy/shared Android sources used by the native build.
- `docs/` — technical decisions, research notes, QA findings, and integration handoffs.
- `.github/workflows/` — automated build and verification checks.

Some paths are **not present on `main` yet**. The most recent Android work is being reviewed in separate branches and draft pull requests.

## Working with the Android development build

To inspect the verified pre-Trackside Android integration work, start from its development branch rather than assuming `main` contains the full app:

```bash
git clone https://github.com/Isshhin/stridelab-development.git
cd stridelab-development
git switch integration/native-pre-trackside3-verified-20261030
```

Open `native-android/` in Android Studio with an Android SDK and **JDK 17** configured. From that directory, the development build and unit-test commands are:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

For the exercise-library checks, install the development dependency `jsonschema==4.26.0` and run:

```bash
python tools/validate_exercise_library.py
```

These are developer commands, not a packaged installation process. Building may require the dependencies and environment used by the project's CI. Do not treat an assembled APK as a reviewed release.

## Where things stand

As of **3 November 2026**, the main ongoing work is tracked separately so changes can be tested without risking saved training data:

- [Native Android integration baseline — PR #32](https://github.com/Isshhin/stridelab-development/pull/32): the verified pre-Trackside foundation.
- [Trackside 3.0 interface — PR #36](https://github.com/Isshhin/stridelab-development/pull/36): screens, navigation, responsive behavior, and Figma fidelity. Still under review.
- [Coaching Engine 1.5 — PR #37](https://github.com/Isshhin/stridelab-development/pull/37): incremental coaching improvements and safety validation. Kept separate from the UI work.
- [Runna and Garmin coaching research — PR #34](https://github.com/Isshhin/stridelab-development/pull/34): evidence review for future coaching design, not production code.

The next priorities are to finish and verify the native interface, protect existing offline data and workout history, and introduce coaching improvements only when their tests and review gates are satisfied. Direct wearable integrations and broader sync remain future work; they shouldn't be mistaken for supported live connections today.

---

**The goal is simple:** build a training app that's useful on an ordinary Tuesday, not just impressive in a feature list.