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
- **State Projection:** `TrainingViewModel` exposes `state: StateFlow<TrainingState>`, `online`, and `planActions(): PlanActions` (including activity-match confirmation/rejection, adaptation approve/dismiss/rollback, plan edits, workout recording, and weekly review). `TrainingState` projects `activityMatches`, `executionAnalyses`, `activeAthleteDiagnostic`, `activeAdaptationProposal`, and per-plan `adaptationRevisions` to Compose.
- **UI Presentation Layer:** `Engine15SessionPresentation.forSession(plan, sessionId)` computes the active display `Target` (`ORIGINAL`, `PENDING_REVIEW`, `ACCEPTED_TIME_ONLY`, `ACCEPTED_REST`, `ACCEPTED_PROGRESSION`) without mutating saved prescriptions.
- **Steps 04–06 Coaching UI:** Today and its Running discipline page display pending `.FIT` match candidates; Today and Training provide reviewable adaptation proposals; Running and Training show confidence-gated diagnostics; Training offers confirmation-gated rollback. Workout Detail renders linked run execution verdicts, evidence/quality status, only-present telemetry metrics, and expected-versus-observed step summaries. Every mutation routes through `PlanActions` to the corresponding `TrainingRepository` method; Compose never edits `PlanSnapshot`.
- **Compose UI Tests:** Seven `Engine15CoachingUiTest` cases cover pending-match visibility/delegation, execution verdicts and hidden null HR metrics, insufficient diagnostics/easy-pacing lock, proposal apply/dismiss/read-only behavior, rollback visibility, and running-step labels. The JVM suite remains 148 suites / 1,157 tests (0 failed/skipped); Android tests were compiled with `:app:compileDebugAndroidTestKotlin` and not device-run.
