---
title: "App Architecture & Data Flow"
area: architecture
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/StrideApplication.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/PlanActions.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksideFloatingNavigation.kt"
tags: [stridelab, architecture, compose, mvvm, offline-first]
---

# App Architecture & Data Flow

StrideLab is structured as a unidirectional, offline-first Jetpack Compose application where all persistence, encryption, network sync, and coaching computation are serialized behind a single repository mutex.

## End-to-End Pipeline
```text
Local SQLite (TrainingStore + ManualDisciplineStore) + Encrypted Vaults + Garmin FIT/API
        │
        ▼
TrainingRepository (Mutex + Dispatchers.IO) ──► Engine 1.5 Pure Planners & Validators
        │
        ▼  StateFlow<TrainingState>
TrainingViewModel (StrideApplication.kt) ──► PlanActions / NativeActions / FreeformAccess
        │
        ▼
NativeMainActivity NavHost ──► Jetpack Compose Screens (3-Tab Shell + Detail Routes)
```

## Navigation Shell (`NativeMainActivity.kt`)
- **3 Retained Tab Roots** hosted beneath `TracksideFloatingNavigation` (`68.dp` floating capsule, animated lime indicator):
  1. `home` → **Today** (`StrideLabTodayScreen.kt`, `TodayAdaptiveDashboard.kt`, `TodayDisciplineDashboard.kt`)
  2. `training` → **Training** (`StrideLabUnifiedTrainingTimeline.kt`, `TracksideParityTraining.kt`)
  3. `settings` → **More** (`TracksideMoreScreen.kt`, `MoreFeatureUi.kt`)
- **Full-Screen Detail & Workflow Routes** (`showFloatingDock == false`): Workout Detail, Guided Workout Player, Activity Detail, Share Studio dialog, Plan Builder / Blueprint preview, Freeform Logger / Templates / Custom Planner, and More subpages. See [[Screens-Map]].

## State Management & Ownership
- **`TrainingRepository.kt`:** Owns a single Kotlin `Mutex` on `Dispatchers.IO` that serializes all SQLite reads/writes, encrypted vault access, Garmin/Supabase sync, and [[Engine-1.5]] plan mutations. Publishes immutable `TrainingState` snapshots via `MutableStateFlow`.
- **Independent Local Namespaces:** `localGarminOwner` and `localPlanOwner` (`LocalPlanOwnership`) operate independently of StrideLab cloud sign-in (`state.account == null`). See [[ADR-03-Offline-First-Independent-Local-Ownership]].
- **UI Presentation Boundary:** Compose screens never mutate `PlanSnapshot` directly. All mutations route through `PlanActions`, `NativeActions`, or `FreeformAccess`. `Engine15SessionPresentation.forSession(plan, sessionId)` projects active session targets (`ORIGINAL`, `PENDING_REVIEW`, `ACCEPTED_TIME_ONLY`, `ACCEPTED_REST`, `ACCEPTED_PROGRESSION`) without mutating saved prescriptions.
- **Cold-Start Performance (`TodayStartup.kt`):** Owner-scoped plan query resolves the Today card immediately while scalar activity summaries and session restoration run on `Dispatchers.IO`. Secondary caches and bounded GPS route thumbnails hydrate independently via [[Storage-and-FIT-Contracts]].

## Background Work (`StrideApplication.kt`)
- **`HistoryRefreshWorker` (1 h, connected):** Refreshes cloud history only when signed in (`account != null && !needsSignIn`).
- **`GarminAutoSyncWorker` (6 h, connected):** Runs `automaticGarminAndBackup(foreground = false)` even when signed out, syncing summary pages first and then a bounded batch of run recordings (up to 3 per auto-sync, 24 per explicit sync).
- **Offline Map Store (`StrideOfflineMapStore.kt`):** App-scoped MapLibre offline region manager that seeds the APK style before download, tracks progress across screen changes, enforces Wi-Fi checks, and handles stall retries.

## Build & Verification Environment
- **SDK & Tooling:** Kotlin, Jetpack Compose BOM `2025.10.00`, Material 3, Navigation Compose `2.9.5`, WorkManager `2.10.1`, OkHttp `4.12.0`, Garmin FIT SDK `21.218.0`, MapLibre GL `13.7.1`.
- **Sequential JDK 21 Gradle Checks** (run from `native-android/`):
  - `:app:testDebugUnitTest`
  - `:app:assembleDebug`
  - `:app:compileDebugAndroidTestKotlin`
  - `:app:compileReleaseKotlin`

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Storage-and-FIT-Contracts]] · [[Hard-Guardrails]] · [[Screens-Map]] · [[ADR-03-Offline-First-Independent-Local-Ownership]]
