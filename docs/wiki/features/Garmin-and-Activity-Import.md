---
title: "Garmin Integration & Activity Import"
area: feature
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/GarminPersonalApi.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/GarminPersonalSource.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/GarminHistoryImport.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FitFileIntake.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FitActivityDecoder.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ActivityDisplayConsolidation.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/GarminFitWorkoutEncoder.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksideActivityDetailSurface.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksideScrollActivityContent.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ActivityChartMovingTimeAxis.kt"
tags: [stridelab, feature, garmin, fit, activity-detail, charts]
---

# Garmin Integration & Activity Import

StrideLab supports full signed-out Garmin Personal API syncing, local `.FIT` file ingestion, multi-source activity deduplication, continuous moving-time telemetry analysis, and offline `.FIT` workout export to Garmin watches.

## Signed-Out Ingestion & Multi-Source Consolidation
- **Sync Path:** `GarminPersonalApi` / `GarminPersonalSource` → `GarminHistoryImport` → `FitFileIntake` / `FitContainerReader` / `FitActivityDecoder` → `ActivityRecord` + `ActivityDetail`.
- **Credential & Archive Independence:** Uses `localGarminOwner` and `GarminCredentialVault`; functions cleanly with `state.account == null`. `GarminAutoSyncWorker` refreshes in the background even when signed out. See [[ADR-03-Offline-First-Independent-Local-Ownership]].
- **Deduplication (`ActiveActivitySources.kt`, `ActivityDisplayConsolidation.kt`):** Consolidates matching Garmin/manual/cloud records for presentation without deleting or rewriting raw `.FIT` recordings.
- **Local Detail Caching:** Validated `.FIT` details write up to `16 MiB` UTF-8 JSON locally and read in UTF-8 byte windows without inflating cloud sync `outbox` limits. Running dynamics (ground contact time, vertical oscillation, step length, vertical ratio) populate optional nullable fields; derived estimates are explicitly labeled.

## Activity Detail, Moving-Time Charts & Route Maps (`TracksideActivityDetailSurface.kt`, `TracksideScrollActivityContent.kt`)
- **Peek & Continuous Sheet:** Collapsible peek height tracks actual summary content height; expanded sheet provides 4 scrolled sections (`Overview`, `Analysis`, `Splits`, `Charts`) with `TracksideActivitySectionPill` (`pinnedTapSection` keeps short-list tab selections sticky until user scroll moves the offset).
- **Pause-Compressed Moving-Time Charts (`ActivityChartMovingTimeAxis.kt`, `ActivityChartDisplayScale.kt`):** Pace, HR, elevation, and cadence render as continuous source-measured curves over active moving time, omitting pause gaps (`> 30 s`) and dropouts from time-weighted comparisons while leaving source samples immutable.
- **Geographic Route (`TracksideGeographicMap.kt`):** Draws chronological moving GPS fixes across timer pauses, drops `(0, 0)` invalid fixes, and handles antimeridian cuts. Route colors support Mode (`Route`, `Pace`, `Elevation`, `HR`) and split-selection highlighting.

## Watch Workout Export (`GarminFitWorkoutEncoder.kt`, `RunningWorkoutFitExport.kt`)
- Encodes current [[Engine-1.5]] running prescriptions (including approved duration/progression targets) directly to FIT Protocol 2.0 wire bytes in pure Kotlin.
- Compresses consecutive identical Work/Recovery pairs into Garmin repeat steps and guards BPM HR targets behind verified 5-zone cached settings.
- Writes directly via Android's Storage Access Framework document picker (`/GARMIN/NewFiles/`) without altering plans or sync state.

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[Storage-and-FIT-Contracts]] · [[Hard-Guardrails]] · [[Engine-1.5]] · [[Progress-and-History]] · [[Share-Studio]] · [[ADR-03-Offline-First-Independent-Local-Ownership]]
