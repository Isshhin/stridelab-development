---
scope: architecture
tags: [stridelab, architecture, data-flow, garmin, viewmodel]
last_updated: 2026-11-23
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
- **Offline maps:** Seed the APK style into MapLibre’s resource cache before creating or retrying a region. The renderer and downloader share that style URL. The app-scoped store owns the region, observer, Wi-Fi checks and stall timeout across screen changes.

## 2. Garmin & FIT Ingestion
- **Today cold start:** The owner-scoped plan query resolves the training card while scalar activity summaries and session restoration run on IO. The feed publishes cloud and local Garmin summaries together in newest-first order. Secondary caches hydrate separately. Perfetto sections identify the plan query, summary load, secondary caches and individual thumbnail reads.
- **Route thumbnails:** Visible activity cards request bounded, downsampled previews on workers without populating the full detail cache or invoking coaching analysis. SQLite reads long recordings in UTF-8 byte windows. Activity Detail still loads the full recording on demand.
- **Sync Path:** `GarminPersonalApi` / `GarminPersonalSource` → `GarminHistoryImport` → `FitFileIntake` / `FitContainerReader` / `FitActivityDecoder` → `ActivityRecord` + `ActivityDetail`.
- **Consolidation:** `ActiveActivitySources` + `ActivityDisplayConsolidation` deduplicate/group multi-source records without mutating or overwriting original FIT source files.
- **Offline Garmin recordings:** Validated FIT details use a dedicated local SQLite write capped at 16 MiB of UTF-8 JSON. Dense recordings can exceed the shared 2 MiB record limit even when their original FIT is small. Existing recordings are read in chunks, and local writes do not create outbox entries. Optional Garmin cloud backup retains its independent upload limit.
- **Activity Detail hydration:** Missing and empty placeholders can be refetched. Unreadable or mismatched caches retain the original and surface an activity-specific error with Retry. After hydration, the repository publishes the recording and refreshes linked execution analysis. Evicting a detail from memory does not discard previously analyzed evidence.
- **Running dynamics:** FIT session averages and record-level contact time, oscillation, step length and vertical ratio go into optional local recording fields. A Garmin refresh can fill missing fields on older recordings after sample and lap identity checks. Paused spans and long gaps are excluded from time-weighted analysis.
- **Offline workout export:** `GarminFitWorkoutEncoder` writes FIT Protocol 2.0 workout files in pure Kotlin. It maps prescribed time and distance directly, and compresses consecutive identical work/recovery pairs into Garmin repeat steps. Explicit BPM targets require cached verified five-zone settings. The Android document picker writes bytes directly to the chosen destination. Export leaves plan and sync storage untouched.
- **Background Workers (`StrideApplication.kt`):**
  - `HistoryRefreshWorker` (1 h, connected): refreshes cloud history only when signed in (`account != null && !needsSignIn`).
  - `GarminAutoSyncWorker` (6 h, connected): runs `automaticGarminAndBackup(foreground = false)` even when signed out.
  - Garmin sync saves summary pages first, then attempts a bounded batch of run recordings (up to 24 per explicit sync or 3 per automatic sync). Activity Detail can request one recording on demand.

## 3. Engine 1.5 & Repository Mutation Flow
- **Guided execution:** `WorkoutPlayerEngine` reduces typed events against immutable exercise targets. The native workout route dispatches through `TrainingViewModel` and the repository mutex. Owner-scoped `player-exec-*` JSON checkpoints use existing local SQLite `workout` records. Confirming a set and entering rest commit together. Timer display refreshes do not write.
- **Recovery and completion:** Running timers use elapsed realtime on the same boot and a wall-clock anchor across reboots. Paused timers restore their saved remaining time. Finalization freezes one compatibility log under a stable ID before saving the plan. A retry reconciles the exact completed log and records a local receipt. Skipped sets stay out of performed rows. Draft text and execution checkpoints remain local, with the existing plan sync policy applied on completion.
- **Execution outcome:** The checkpoint records lifecycle and set coverage independently, with confirmed and skipped counts. Startup projects matching local receipts into `TrainingState` by plan and session ID. Historical logs without a matching receipt have unknown coverage. A checkpoint whose frozen log matches a completed plan can be recovered even if the process died before writing the receipt flag.
- **Plan lifecycle:** `TrainingState.planPreview` is transient and is separate from the saved plan list. Activation checks the current calendar before saving. Discard or process restart drops the preview. Legacy saved plan drafts are ignored when loading or restoring; unfinished workout entries and research reviews use separate storage.
- **Concurrent calendars:** Generation checks existing approved sessions and can choose another repeating pattern for unpinned solo plans. Combined placement consults existing workouts directly. Activation rechecks the latest calendar.
- **`TrainingRepository`** serializes all DB, encryption, network, and Engine 1.5 mutations behind a single `Mutex` on `Dispatchers.IO`, publishing immutable snapshots via `MutableStateFlow<TrainingState>`.
- **Routing:** `Engine1Engines.resolve(request)` routes running plans to `Engine15IndependentPlanGenerator`, multi-discipline plans to `Engine15StaticMultiDisciplinePlanner`, and single non-running plans to Strength/Calisthenics/Conditioning composers.
- **State Projection:** `TrainingViewModel` exposes `state: StateFlow<TrainingState>`, `online`, and `planActions(): PlanActions` (including activity-match confirmation/rejection, adaptation approve/dismiss/rollback, plan edits, workout recording, and weekly review). `TrainingState` projects `activityMatches`, `executionAnalyses`, `activeAthleteDiagnostic`, `activeAdaptationProposal`, and per-plan `adaptationRevisions` to Compose.
- **UI Presentation Layer:** `Engine15SessionPresentation.forSession(plan, sessionId)` computes the active display `Target` (`ORIGINAL`, `PENDING_REVIEW`, `ACCEPTED_TIME_ONLY`, `ACCEPTED_REST`, `ACCEPTED_PROGRESSION`) without mutating saved prescriptions.
- **Session debrief:** `Engine15DebriefEngine` dispatches six read-only analyzers. Running builds the narrative, Training Effect and dynamics cards in one background pass over a shared moving-time stream. Recorded work laps or an accepted plan link can supply interval comparisons. Cross-training uses completed workout logs and effective prescriptions. Stationary Activity Detail requires an exact link to that log. `DebriefCopyTemplates.kt` holds the athlete-facing copy.
- **Training Effect:** Activity Detail computes StrideLab scores from cached moving samples on a background dispatcher. Scores and optional weather context are transient. The weather fields are reserved for future analysis.
- **Steps 04–06 Coaching UI:** Today and its Running discipline page display pending `.FIT` match candidates; Today and Training provide reviewable adaptation proposals; Running and Training show confidence-gated diagnostics; Training offers confirmation-gated rollback. Workout Detail renders linked run execution verdicts, evidence/quality status, only-present telemetry metrics, and expected-versus-observed step summaries. Every mutation routes through `PlanActions` to the corresponding `TrainingRepository` method; Compose never edits `PlanSnapshot`.
- **Structured Cross-training:** `Engine15DisciplineProgression` shares 3:1 phases with Running's macrocycle and fits Strength, Calisthenics and Conditioning prescriptions into the existing discipline composers and calendar. The chosen goal identifies the validation contract without a `PLAN_SCHEMA` migration. Dose-reduction proposals use the same repository approval and revision storage as running, with full candidate validation and discipline-specific diff notes.
- **Verification:** JVM coverage includes guided workout transitions, EMOM boundaries, undo, outcome classification, checkpoint recovery, final-save reconciliation, and cross-training debrief compatibility. Android coverage follows a partial workout through finalization, Today, Training, and Workout Detail. See [[index]] for current results.
