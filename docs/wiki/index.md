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
- `native-android/app/src/test/java/io/stridelab/privatealpha/` — JVM unit tests (`157` suites / `1,230` tests, including independent plan generation, running history estimates, workout checkpoint recovery, FIT and debriefs)
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

## Verification Commands (`native-android/`, JDK 21, run sequentially)
```powershell
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:testDebugUnitTest
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:assembleDebug
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:compileDebugAndroidTestKotlin
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:compileReleaseKotlin
```
