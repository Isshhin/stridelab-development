---
scope: project-index
tags: [stridelab, wiki, index]
last_updated: 2026-11-22
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
- `native-android/app/src/test/java/io/stridelab/privatealpha/` — JVM unit tests (`148` suites / `1,157` tests, including Step 01–06 `Engine15CoachingAcceptanceTest`, `Engine15ActivityAutoMatcherTest`, `Engine15ExecutionAnalyzerTest`, `Engine15AthleteDiagnosticsEngineTest`, and `Engine15PlanAdaptationEngineTest`)
- `native-android/app/src/androidTest/java/io/stridelab/privatealpha/` — Compose & instrumentation UI tests (includes 7 Engine 1.5 Steps 04–06 coaching UI cases; compiled, not device-run)
- `docs/wiki/` — Persistent Obsidian-compatible LLM memory hub (this wiki)
- `research/engine15/` — Generated Engine 1.5 coaching audit reports (e.g., `M28A_COACHING_AUDIT_20261121.md`)

## Topic Pages
- [[architecture]] — Local storage, offline Garmin FIT ingestion, Engine 1.5 pipeline, `TrainingRepository`/`TrainingViewModel`, and Compose state flow.
- [[engine-rules]] — Core Engine 1.5 invariants, mileage/taper caps, warm-up/cool-down clamping, intensity zones, difficulty scaling, and weekly review guardrails.
- [[ui-system]] — Dark AMOLED theme tokens (`#0A0C0E`, `#14181C`, `#232A31`, lime/intensity accents), typography guards, and shared Compose components.
- [[screens]] — Navigation routes, primary screens, `TrainingState` contracts, and removed/deprecated UI sections not to reintroduce.

## Verification Commands (`native-android/`, JDK 21, run sequentially)
```powershell
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:testDebugUnitTest
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:assembleDebug
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:compileDebugAndroidTestKotlin
.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:compileReleaseKotlin
```
