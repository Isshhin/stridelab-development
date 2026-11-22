---
scope: architecture
tags: [stridelab, architecture, data-flow, garmin, viewmodel]
last_updated: 2026-11-22
---

# Architecture & Data Flow

Back to [[index]] · Related: [[engine-rules]] · [[screens]] · [[ui-system]]

## End-to-End Pipeline
```
Local SQLite (TrainingStore) + Encrypted Vaults + Garmin FIT/API
        │
        ▼
TrainingRepository (Mutex + Dispatchers.IO) ──► Engine 1.5 Pure Planners & Validators
        │
        ▼  StateFlow<TrainingState>
TrainingViewModel (StrideApplication.kt) ──► PlanActions / NativeActions
        │
        ▼
NativeMainActivity NavHost ──► Jetpack Compose Screens
```

## 1. Local Storage & Offline Ownership
- **`TrainingStore`** (`SQLite`): Persists cached activities, `PlanSnapshot`s, `WorkoutEntryDraft`s, `WellnessRecord`s, `ActivityDetail`s, and `SessionTemplate`s.
- **Independent Local Namespaces (`TrainingRepository.kt`):**
  - `localGarminOwner` (`local-garmin-<uuid>`) and `localPlanOwner` (`LocalPlanOwnership`) operate independently of cloud sign-in (`state.account == null`).
  - **Why:** Garmin Personal API sync, local FIT/GPS decoding, and Engine 1.5 planning must always function offline and signed-out; cloud auth (`SessionVault` + `SupabaseGateway`) only gates optional cloud backup/sync.
- **Auxiliary Stores:** `GarminCredentialStore` / `GarminCredentialVault`, `GarminFitArchive`, `FitImportCache`, `LocalActivityArchive`, `ManualDisciplineStore`, `StrideOfflineMapStore`, `GarminZonePreferenceStore`.

## 2. Garmin & FIT Ingestion
- **Sync Path:** `GarminPersonalApi` / `GarminPersonalSource` → `GarminHistoryImport` → `FitFileIntake` / `FitContainerReader` / `FitActivityDecoder` → `ActivityRecord` + `ActivityDetail`.
- **Consolidation:** `ActiveActivitySources` + `ActivityDisplayConsolidation` deduplicate/group multi-source records without mutating or overwriting original FIT source files.
- **Background Workers (`StrideApplication.kt`):**
  - `HistoryRefreshWorker` (1 h, connected): refreshes cloud history only when signed in (`account != null && !needsSignIn`).
  - `GarminAutoSyncWorker` (6 h, connected): runs `automaticGarminAndBackup(foreground = false)` even when signed out.
  - GPS FIT fetch (`TrainingViewModel.loadGarminGps`) is strictly foreground and on-demand—never bulk or background-scheduled.

## 3. Engine 1.5 & Repository Mutation Flow
- **`TrainingRepository`** serializes all DB, encryption, network, and Engine 1.5 mutations behind a single `Mutex` on `Dispatchers.IO`, publishing immutable snapshots via `MutableStateFlow<TrainingState>`.
- **Routing:** `Engine1Engines.resolve(request)` routes running plans to `Engine15IndependentPlanGenerator`, multi-discipline plans to `Engine15StaticMultiDisciplinePlanner`, and single non-running plans to Strength/Calisthenics/Conditioning composers.
- **State Projection:** `TrainingViewModel` exposes `state: StateFlow<TrainingState>`, `online`, and `planActions(): PlanActions` (generate, approve, move, adjustDifficulty, record, saveRunResponse, missed, swap/swapHarder, previewWeek/acceptWeek).
- **UI Presentation Layer:** `Engine15SessionPresentation.forSession(plan, sessionId)` computes the active display `Target` (`ORIGINAL`, `PENDING_REVIEW`, `ACCEPTED_TIME_ONLY`, `ACCEPTED_REST`, `ACCEPTED_PROGRESSION`) without mutating saved prescriptions.
