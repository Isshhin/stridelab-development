---
scope: project-index
tags: [stridelab, wiki, index]
last_updated: 2026-11-23
---

# StrideLab Engineering Wiki

Offline-first native Android running and multi-discipline training coach built around **Engine 1.5** (deterministic training plan generation, daily recovery shaping, reversible workout difficulty controls, bidirectional weekly reviews) and signed-out-capable **Garmin Personal API / FIT** ingestion.

## Tech Stack
- **Language & UI:** Kotlin, Jetpack Compose (BOM `2025.10.00`), Material 3, Navigation Compose (`2.9.5`), Lifecycle Compose (`2.9.4`)
- **Android SDK:** `compileSdk 36`, `targetSdk 35`, `minSdk 26`, JVM target `17` (Gradle built with JDK 21)
- **Storage & Sync:** Local SQLite (`TrainingStore`), encrypted `SessionVault` / `GarminCredentialVault`, WorkManager (`2.10.1`), OkHttp (`4.12.0`), optional Supabase cloud backup (`SupabaseGateway`)
- **Telemetry & Maps:** Garmin FIT SDK (`21.218.0`), MapLibre GL Android SDK (`13.7.1`)

## Repository Map (`F:\StrideLab\Current-App\stridelab-engine15-dev`, branch `dev`)
- `native-android/app/src/main/java/io/stridelab/privatealpha/` — Active Kotlin/Compose app, Engine 1.5, Garmin/FIT pipeline, and UI
- `native-android/app/src/test/java/io/stridelab/privatealpha/` — JVM unit tests (`164` suites / `1,269` tests, including Blueprint, Training aggregation, hybrid activation, FIT and debriefs)
- `native-android/app/src/androidTest/java/io/stridelab/privatealpha/` — Compose and instrumentation coverage for Activity Detail and guided execution. The five AMOLED player tests passed on the API 35 emulator at 360 dp and approximately 412 dp with font scales 1.0 and 1.3.
- `docs/wiki/` — Persistent Obsidian-compatible LLM memory hub (this wiki)
- `research/engine15/` — Generated Engine 1.5 coaching audit reports (e.g., `M28A_COACHING_AUDIT_20261121.md`)

## Topic Pages
- [[architecture]] — Local storage, durable guided workout execution, offline Garmin FIT ingestion and workout export, Engine 1.5 pipeline, and Compose state flow.
- [[engine-rules]] — Core Engine 1.5 invariants, training load and intensity rules, verified-HR Training Effect, and debrief evidence thresholds.
- [[ui-system]] — Dark AMOLED theme tokens (`#0A0C0E`, `#14181C`, `#232A31`, lime/intensity accents), typography guards, and shared Compose components.
- [[screens]] — Navigation routes, discipline-specific AMOLED Workout Player and timer cues, and the grouped More layout with separate StrideLab and Garmin statuses. The legacy running analytics, run comparison, and standalone strength and conditioning logging routes were retired.

## Workout Player Milestone
- **Phases 0–3 complete:** Integration audit, durable local checkpoints, AMOLED player, and debrief evidence verification. The Phase 3 JVM suite restores serialized checkpoints with a fixed clock and checks that all five non-running debriefs use confirmed work only.
- Two Phase 3 acceptance tests passed on the API 35 emulator: SQLite recovery after the plan save but before the completion receipt, and exactly one Coach's Debrief card for each completed discipline.
- Local workout receipts track how a session ended and how many sets were performed. See [[architecture]] for recovery and [[screens]] for the partial-workout display.
- Workout outcome verification passed 15 focused API 35 emulator tests. Three separate app processes also verified checkpoint recovery before save, finalization, and receipt recovery after save.

## Multi-Plan Today

- The week strip, selected day, and weekly adherence share the same discipline filter across all approved plans. Session identity and recording deduplication are independent. See [[screens]].
- All eight `TracksideTodayTest` cases passed on the API 35 Pixel 9 emulator, including concurrent Running and Strength plans on the same date.

## Running Prescription Validation

- The M28A audit reports distance reps in meters with a labeled work time estimate. The full-plan JVM fixtures cover 8-week beginner, 16-week intermediate, and 12-week advanced 10K plans, plus Garmin FIT decoding of the benchmark workouts. See [[engine-rules]] for quality entry and phase rules.

## Runner Support

- The plan blueprint reviews concurrent support dates and doses against the Running calendar. Strength and plyometrics use confirmed local workout evidence for optional progression. Review and Undo are available in Training. See [[engine-rules#Runner Support]].
- API 35 repository acceptance verified concurrent support preview, receipt based progression, reload and undo, with the approved Running snapshot and FIT bytes preserved.

## Blueprint Workout Preview

- Draft workout cards open the current Blueprint prescription in a read-only detail route. The builder retains its selected week on return. Gear profiles are stored per owner on this device and take precedence over saved plan defaults. See [[screens]].
- Training defaults to the shared calendar when multiple plans are approved. Filtering, management, coaching and matches use the same tab. The seven-day chart and repeat cards are documented in [[ui-system]].
- The hybrid builder generates a separate snapshot for each of up to six tracks. Blueprint reviews the shared calendar before one local activation transaction. See [[engine-rules#Hybrid Plan Builder]].
- Train Your Way collects the running baseline before targets. Hybrid Schedule honors compatible fixed pairs and applies the optional daily limit independently. See [[screens]] and [[engine-rules#Hybrid Plan Builder]].
- The four JDK 21 checks passed. API 35 acceptance passed 12 integration tests on `emulator-5554`, including collision rollback, backup restore and exact FIT bytes. The 320, 360 and 412 dp profiles at font scales 1.0 and 1.3 passed four tests each, covering Sunday visibility, grouped steps, Training filters and six-track selection.

- Pre-trial polish passed all four JDK 21 checks. The API 35 emulator passed six focused cases at 412 dp and font scale 1.5, including six-track activation and identical FIT export. The two new builder cases also passed at 360 dp and font scale 1.0.

## Freeform Workout Logger

- Training opens the offline logger for Running, Strength, Plyometrics, Isometrics, Calisthenics, and Conditioning. Signed-out and signed-in profiles have independent drafts and history.
- Checkpoint A includes logging, save and resume, Freeform history, and local backup. Checkpoint B adds reusable templates and a compact set editor. Custom scheduling is the next checkpoint. See [[screens]] and [[architecture]].

- Checkpoint A passed the four required JDK 21 checks, including 1,265 JVM tests. The logger passed 19 API 35 executions across smoke, process restart, and the 320, 360, and 412 dp profiles at font scales 1.0 and 1.5.
- The broader Android regression run passed 54 and failed 7. All seven failures reproduced on the clean dev baseline (six legacy Workout Player fixtures and one Garmin sign-out test).

- Checkpoint B passed all four JDK 21 tasks and 1,269 JVM tests. Its 52 API 35 executions cover the smoke suite, six responsive profiles, force-stop recovery, and focused Training, guided Workout Player, manual storage, and Garmin regressions.

## Verification Commands (`native-android/`, JDK 21, run sequentially)
```powershell
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:testDebugUnitTest
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:assembleDebug
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:compileDebugAndroidTestKotlin
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:compileReleaseKotlin
```
