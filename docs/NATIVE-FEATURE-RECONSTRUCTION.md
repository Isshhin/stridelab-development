# Unified Native Feature Reconstruction — required behavior

Navigation and synchronization-feedback corrections are prerequisites for the upcoming unified native feature milestone. Carry these behaviors and their regression tests forward; do not restore the old tab-stack or persistent-success behavior while reconstructing additional features.

## Navigation

- Every primary bottom tab opens its root destination. Activities always opens the list, even after a detail was opened before switching away.
- Discard transient detail/auth entries before saving a tab stack. Keep Navigation Compose root saveState/restoreState and single-top navigation, including saved Activities list position.
- All activity entry points, including Home's latest activity, establish Activities as the parent before opening detail. Android Back returns to the list.
- Repeated tab selection/switching must not accumulate duplicate roots. Normalize old restored stacks to their selected root.

## Synchronization feedback

- Shared successful refresh code must not publish a global success banner. This covers the background worker, sign-in history refresh, manual refresh and provider synchronization.
- Manual refresh updates the existing last-refreshed timestamp in place. A brief unobtrusive confirmation remains optional; no persistent success notice is required.
- Preserve actionable errors, interrupted/unsupported-data warnings, offline status, cached records and authentication recovery. Successful sign-out is quiet; failed remote revocation remains visible.
- Do not add generic debug-message controls to standard Settings. Developer diagnostics must remain outside the production UI.

## Required regression coverage

NativeTabRootTest exercises Activities → Detail → Home → Activities, retained list position, repeated primary-tab switching/reselection, and Android Back from both list-opened and Home-opened detail. TrainingRepositoryTest checks silent successful shared refresh/provider sync, cache retention and meaningful failure messages, then silent successful recovery. Existing NativeVisualTest retains visible error/offline assertions, and the full CI keeps SQLite, Keystore, auth, same-key update and offline forced-process probes.

These are synthetic UI/repository tests and preservation checks, not genuine-athlete integration acceptance. Retain the current native design, application ID, gateway/schema contracts and all existing GitHub checkpoints. No main merge or public release is part of these corrections.

## Verified corrections checkpoint — 2026-10-26

Implemented source: 8c5dae4e013f81113246db4d7278b5aa4033753e. [Verified checkpoint](https://github.com/Isshhin/stridelab-development/tree/checkpoint/native-navigation-sync-verified-8c5dae4).

[Android CI 40326759340](https://github.com/Isshhin/stridelab-development/actions/runs/40326759340) passed: 32 JVM tests, 45 emulator tests, zero failures/errors/skips; one legacy seed, all four lifecycle/network probes, and nine APK compatibility/packaging tests passed. Local targeted coverage passed 29 tests with zero failures/skips. Existing error/offline UI assertions remain in the suite.

[Private versionCode 6 / 0.4.1-native-navigation APK package](https://github.com/Isshhin/stridelab-development/actions/runs/40326759340/artifacts/12653116281). APK SHA-256: 6480f2781fcdd497d63bdcdc2c380571d024f68b610a5f2c42833a3465057471. Certificate SHA-256: 20bc17e151abf55ec3c7ed0dffbfd7ef27e465e87da4daf9f43d4e71cd7e0c8d. Downloaded APK signature and checksum independently verified. The CI debug certificate differs from the previous test APK; phone in-place update compatibility is not established. Preserve the installed app and data; use an isolated emulator or an appropriately signed compatible build.

[Source and full CI evidence](https://github.com/Isshhin/stridelab-development/actions/runs/40326759340/artifacts/12653111253). Source ZIP SHA-256: d1285afbf535f422f50eedecaea1f1c232e005105cce745faaed7919ee3451bb. All 106 manifest payloads independently verified against the exact Git commit after download. Artifacts have 30-day retention; the source checkpoint remains in Git.

The existing phase-2 and native-UI checkpoints are unchanged. Gateway, worker, shared SQLite/Keystore/legacy code and design resources are unchanged. Authentication and real synchronization control flow remain unchanged; repository changes affect completion-message presentation only. No standard Settings debug toggle, main merge, public release, data clearing or uninstall was introduced. Genuine-athlete and physical-phone acceptance remain pending and separate from synthetic regression evidence.

## Native Feature Reconstruction 1.0 — unified master project

Baseline recovered from remote feature/native-real-data-phase2-20261026 at 12e0594e59244cd21d73b24deed4f021434d467f, including tested 8c5dae4 and run 40326759340. Working branch: feature/native-reconstruction-1-20261026. Original checkpoints are immutable. This master project contains five incremental stages; no stage is accepted merely because UI exists.

| Stage / Phase 4E capability | Native implementation and acceptance |
| --- | --- |
| 1: activity intelligence, charts, laps, route, history comparisons | Increment S1A implemented: owner-filtered sample/lap reads, validation, transactional offline detail cache, pace/HR/elevation Canvas graphs, GPS trace only from real coordinates, source laps, bounded historical pace comparisons. Local build and JVM tests pass; full CI pending. Full street maps, source route fallback and genuine athlete acceptance remain open. |
| 2: rich dashboard and wellness | Existing all-history totals/recent-duration overview retained. Last-seven-day volume, discipline distribution, consistency, trends and authentic wellness retrieval remain to implement. |
| 3: coordinated plans/calendar | Preserved original HTML/engine remains accessible. Native construction, scheduling, conflicts, preferences, offline edits and adaptation remain to implement. |
| 4: exercise library/execution/logging | Original accepted HTML and licensed assets preserved. Native library, execution, loads, logging and reconciliation remain to implement. |
| 5: concise integrated coaching | S1A descriptive running comparisons only; integrated multidisciplinary coaching remains to implement. Advanced Engine 2.0 is separate. |

### S1A contracts and limits

Read-only owning source inspected: Isshhin/stride-lab main, supabase/migrations/001_foundation.sql (blob f186d0299348bb13daabd78dd41a7190ae733561). activity_samples uses activity_id/sample_index/elapsed_s, heart_rate (bpm), pace_sec_per_km, altitude_m and optional latitude/longitude. activity_laps uses lap_index/distance_m/duration_s/avg_hr/avg_pace_sec_per_km. Native requests join activities!inner(user_id), filter the session owner and activity, verify each returned owner, and validate activity identity/order/finite metric ranges before caching. Existing backend RLS derives nested ownership from activities. No production database changes or authenticated athlete requests were made. Hosted deployment/schema parity remains unverified.

Details load explicitly from activity detail while online. Entire bounded response must validate before replacing its prior cached snapshot; failures preserve previous data. Cache kind activity_detail is additive in RecordPolicy, using the existing version-1 generic records table. No schema migration/destructive SQL. Account switch/sign-out clears visible detail state. Cached detail reloads on process/repository recreation. Pagination continues through short pages until empty; more than 20,000 samples/1,000 laps is rejected rather than silently truncated. Payload limits still apply.

Graphs show recorded measurements against elapsed time, expose textual ranges/units/counts, and leave missing values or gaps over two minutes blank. GPS is an offline north-up coordinate trace, not a street basemap. It never invents points; missing coordinates and antimeridian crossings are disconnected. The inspected existing Intervals stream importer stores latitude/longitude as null; FIT imports may supply coordinates. Source laps are not invented kilometer splits; first 100 displayed with an explicit count if more exist.

Running comparison: at least three distinct strictly earlier runs, same recorded discipline/provider, distance within 10%, preceding 90 days, most recent 12 maximum; positive recorded pace and >=1 km required. Median pace is descriptive, never a fitness/readiness/medical inference. Terrain, effort, weather and stops caveats are displayed. No pace inference from elapsed duration; strength/walking do not acquire running analysis.

### Recovery and remaining work

Continue in the SAME master project and branch. Fetch origin, inspect git status and remote head, and compare against the latest verified checkpoint before changing anything. Run JDK 17 / Gradle 8.13 / SDK 36 build-tools 35.0.0. Local gate: gradle -p native-android :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest. Full gate: private native-android Actions workflow, including existing navigation/sync regressions, lifecycle/auth/cache/offline, legacy update probes and Python packaging tests. native-android/checkpoint.py generates the tracked-source ZIP and SHA-256 manifest. Do not label this stage passed until that exact executable commit passes CI.

Next S1 increment: securely test actual athlete data, verify deployed contract/stream completeness and provider GPS-route fallback availability, refine chart/route interaction and physical accessibility/performance. Then implement stages 2–5 in sequence with independent passing checkpoints. No main merge, public release, phone uninstall/data clearing, package identity change or fabricated records. All synthetic records remain test-only.
