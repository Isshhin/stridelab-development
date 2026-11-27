---
title: "Storage, Schemas & FIT Contracts"
area: architecture
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "android-app/android/app/src/main/java/io/stridelab/privatealpha/TrainingStore.java"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15M1SafetyRestrictions.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15M1VersioningAndStorage.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ManualDisciplineStore.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/EngineContracts.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/SharePresetStore.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ActivityThumbnailCache.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/GarminFitArchive.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/GarminFitWorkoutEncoder.kt"
tags: [stridelab, storage, sqlite, fit, caching, contracts]
---

# Storage, Schemas & FIT Contracts

StrideLab separates durable athlete data across purpose-built local SQLite databases, encrypted credential vaults, and bounded caches so offline training, manual logging, and Garmin recordings never interfere with one another.

## 1. Primary SQLite Store (`TrainingStore.java`)
- **File:** `android-app/android/app/src/main/java/io/stridelab/privatealpha/TrainingStore.java` (compiled into `native-android/`).
- **Database:** `stridelab-athlete-v1.db` (DB version 1, WAL enabled, foreign keys enabled).
- **Corruption Policy:** Throws `SQLiteDatabaseCorruptException` instead of silently deleting corrupt databases so unsynced athlete records remain recoverable.
- **Tables:**
  - `records(account, kind, record_id, payload, server_version, synced_at)`
  - `outbox(seq, operation_id, account, kind, record_id, payload, base_version, parent_operation_id, state, attempts, last_error, created_at)`
  - `conflicts`, `cursors`, `resolutions`
- **Local-Only `kind = "workout"` Records (Never Queued to `outbox`):**
  - `activity-match-*` (`.FIT` auto-matcher records)
  - `execution-analysis-*` (post-run execution verdicts)
  - `athlete-diagnostic-*` (SHA-256 keyed rolling diagnostics)
  - `plan-adaptation-*` (adaptation proposals and rollback `previousPlanJson` revisions)
  - `player-exec-*` (durable guided workout execution checkpoints and completion receipts)
  - `m1-restriction-*` (M1-D persistent athlete safety restrictions; never silently wiped on corrupt/future schema)
  - `m1-assessment-*` (M1-E/F versioned derived M1 assessment cache; rebuildable without touching restrictions)
- **Large FIT Detail Ceiling:** `MAX_LOCAL_RECORDING_BYTES = 16 MiB` UTF-8 JSON for local Garmin FIT recordings, read in UTF-8 byte windows so dense GPS files never hit the shared 2 MiB record cap or create cloud outbox entries.

## 2. Plan & Prescription Contracts (`EngineContracts.kt`)
- **`PLAN_SCHEMA = 1`:** Stable serialized plan snapshot schema. Structured cross-training goals and optional Step 01–06 fields (`stepIntent`, pace/HR bounds, support dose/placement flags) decode backward-compatibly with `null` defaults without a `PLAN_SCHEMA` bump.
- **`validateSaved(plan)`:** Deterministically replays plan generation from `plan.request` (no `activities` argument) and verifies `PlannedSession.difficultyBaseline` plus `Engine15WorkoutDifficultyAdjuster.matchesAdjusted`.

## 3. Isolated Manual & Freeform Store (`ManualDisciplineStore.kt`)
- **Database:** `stridelab-standalone-sessions-v1.db` (DB version 4, backup archive schema 5; schemas 1–4 remain readable).
- **Tables:** `manual_templates`, `freeform_workouts` (indexed by `(owner, modified_at DESC)`), `custom_schedules`.
- **Isolation Contract:** Owner-scoped (`account ?: localPlanOwner`), never writes to `TrainingStore` or Garmin FIT files. Restore runs in a single transaction and rejects conflicting completed records.

## 4. Share Studio & Auxiliary Local Stores
- **`SharePresetStore.kt`:** SharedPreferences `"share-studio-v1"` (`"designs"` JSON, version 1 codec, max 100 presets). Stores visual design preferences only — never source activity data, GPS coordinates, user photos, or account identifiers.
- **Two-Tier GPS Thumbnail Cache (`ActivityThumbnailCache.kt`):** 6 MB in-memory `LruCache<String, ImageBitmap>` backed by local disk PNGs under `cacheDir/gps_thumbs` (`TodayCachedGpsThumbnail`). Synchronous memory read on first frame eliminates reload flashes across `Today` and `Training`.
- **Credential & Archive Stores:** `SessionVault` / `GarminCredentialVault` (encrypted), `GarminFitArchive`, `FitImportCache`, `LocalActivityArchive`, `GarminZonePreferenceStore`, `UserActivityWorkoutLabelStore`, `StrideOfflineMapStore`.

## 5. Garmin `.FIT` Ingestion & Export Contracts
- **Import (`FitFileIntake.kt`, `FitContainerReader.kt`, `FitActivityDecoder.kt`):** Decodes FIT sessions, laps, records, and optional running dynamics (ground contact time, vertical oscillation, step length, vertical ratio) without mutating raw `.FIT` archives.
- **Export (`GarminFitWorkoutEncoder.kt`):** Pure-Kotlin FIT Protocol 2.0 workout encoder mapping prescribed time/distance steps and compressing consecutive identical work/recovery pairs into Garmin repeat steps. Explicit BPM targets require verified cached 5-zone settings; output writes via Android's document picker directly to user-chosen storage (e.g. `/GARMIN/NewFiles/`) without mutating plans or sync state.

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[Engine-1.5]] · [[Hard-Guardrails]] · [[Garmin-and-Activity-Import]] · [[Freeform-Logger]] · [[Share-Studio]]
