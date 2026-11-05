# Regression coverage

## Test selection index

| Feature | Test file / method | Verifies | Type / environment | Selection |
|---|---|---|---|---|
| Running occupied-day identity | `TracksideApprovedBuilderActivityUiTest` / all-slot identity and explicit move tests | Tapping an occupied day selects its owner; editor moves only the selected session | Compose instrumentation; API 35 emulator | Run the class |
| Activity sheet | `TracksideApprovedBuilderActivityUiTest` / drag, map, tabs, accessibility and Back tests | Partial/expanded anchors, independent scroll, fixed map, tab semantics, collapse-first Back | Compose instrumentation | Run the class |
| Four responsive profiles | `TracksideResponsiveQaTest` | 412/360 dp and font scales 1.0/1.3/1.5, reachability and screenshots | Serialized API 35 emulator profiles | Windows workflow or profile commands |
| Builder to persisted plan | `TracksideEngineBuilderIntegrationTest` | Real generation, approval, database persistence, offline reopen | Instrumentation + local database | Run the class |
| Legacy/current end-to-end behaviors | `Engine1UiTest` | Generation, same-day independent logs, library/templates, large text, draft restoration | Compose instrumentation | Run after builder/library/navigation changes |
| Navigation and visuals | `NativeNavigationTest`, `NativeVisualTest` | Current routes, Settings reachability, source-backed visual states | Compose instrumentation | Run after root navigation/layout changes |
| Garmin official client | `GarminPersonalApiTest` | Token contract, expiry, auth/rate/protocol/network failures, parsing, provenance, dedupe | JVM unit | `:app:testDebugUnitTest --tests=...GarminPersonalApiTest` |
| Garmin source lifecycle | `GarminPersonalSourceTest` | Connect, token reuse/re-exchange, paging, disconnect, account isolation, offline error | JVM unit | `:app:testDebugUnitTest --tests=...GarminPersonalSourceTest` |
| Garmin secure storage | `GarminCredentialVaultTest` | Keystore encryption, restart read, account isolation, clear, redaction | Android instrumentation | Run the class |
| Import dedupe | `ActivityImportMatcherTest`, `FitActivityDecoderTest` | Explicit IDs, hashes, possible same event, no cross-account match | JVM unit | Run both classes |
| Authentication/session lifecycle | `SupabaseGatewayTest`, `TrainingRepositoryTest` | Sign-in validation, refresh, owner mismatch, logout, damaged session and cached-data privacy | JVM + instrumentation | Run both classes after auth/session changes |
| Root navigation and Back | `TracksideTabTest`, `NativeTabRootTest`, `NativeNavigationTest` | Tab roots, detail ownership, list position, Back, recreation and password non-restoration | Instrumentation; API 35 emulator | Run all three after route/shell changes |
| Today selected-item routing | `TracksideTodayTest`, `Engine1UiTest` | Known targets only, opens existing workout, same-day logs stay independent | Instrumentation | Run after Today card/action changes |
| Training calendar and Manage return | `TracksideTrainingCalendarTest`, `TracksideTrainingCalendarUiTest`, `TracksideManageNavigationTest` | Multiple sessions/day, week/day state and selected-date restoration | JVM + instrumentation | Run after calendar/manage routing changes |
| Manage plan actions | `TracksideManagePlanTest`, `TracksideEngine15ManagementUiTest`, `Engine15FeedbackRepositoryTest` | Edit without mutation, pause confirmation, locks, relocation, stale review and offline durability | Instrumentation | Run relevant class, then all three for management changes |
| Workout recording/completion | `WorkoutPlayerTest`, `WorkoutEntryDraftTest`, `AutonomousReliabilityRegressionTest`, `Engine1RepositoryTest` | Explicit actuals, invalid/duplicate rejection, unfinished drafts, completion and offline reopen | JVM + instrumentation | Run focused player/model class, then repository class |
| Activity history/detail | `TrainingRepositoryTest`, `ActivityDetailTest`, `ActivityIntelligenceUiTest` | Owner-scoped pagination/detail cache, missing metrics, recorded comparison and offline reopen | JVM + instrumentation | Run after activity repository/detail changes |
| FIT validation/cache/intake | `FitContainerReaderTest`, `FitActivityDecoderTest`, `FitImportCacheTest`, `FitFileIntakeTest` | File bounds/CRC, official decode, missingness, dedupe/account scope, interrupted cache and safe URI handling | JVM + instrumentation | Run all four after FIT changes |
| Intervals.icu state/sync | `IntervalConnectionTest`, `TrainingRepositoryTest`, `NativeVisualTest` | Truthful configured state, account-scoped offline cache, sync outcomes and Settings action | JVM + instrumentation | Run after provider/repository/Settings changes |
| Backup/restore/upgrade | `BackupIntegrityTest`, `Engine1RepositoryTest`, `NativeUpgradeTest`, `CorruptCacheTest` | Owner/version validation, atomic restore, retained legacy state and fail-closed corruption | JVM + instrumentation | Run before persistence/schema/update changes |
| Offline outbox | `OutboxOfflineRecoveryTest`, `TrainingRepositoryTest` | Durable operations, interrupted ack, wrong-account protection and recovery | Instrumentation | Run after database/outbox/sync changes |
| Engine packaged default | `TracksideCoachingIntegrationTest.packagedDefaultSelectsTheConfiguredPolicy` | Packaged default routes new drafts to Engine 1.0; explicit flags remain separately testable | JVM with generated `BuildConfig` | Run without `strideEngine15Active`; workflow also runs explicit false gate |
| Engine version preservation | `TracksideCoachingIntegrationTest`, Engine 1.5 architecture/matrix suites | Rollback affects new drafts only; persisted versions/prescriptions remain unchanged | JVM | Full JVM suite after engine boundary changes |

## Selection guide

- Pure model/parser change: focused JVM class, then the complete JVM suite.
- One Compose component: its focused instrumentation class, then related navigation/responsive class.
- Builder, persistence, navigation, database, or source boundary change: focused tests, integration test, then full instrumentation.
- Density/font/layout change: focused UI tests plus all four responsive profiles.
- Emulator jobs on `MARC`: one at a time. Never dispatch concurrent emulator workflows to the same runner.

## Current gaps

- No live Garmin test without an owner-provided Personal API key.
- Garmin activity-page persistence into main StrideLab history is intentionally not enabled in the foundation.
- Physical-phone smoke acceptance remains distinct from emulator evidence.
- Emulator screenshots establish reachability and layout, not touch ergonomics on a real phone.
- FIT intake helpers and cache are not wired to a user-facing canonical-history import transaction.
- TCX and GPX import are not implemented.
- Archive confirmation lacks a dedicated UI outcome regression; repository/archive behavior is covered indirectly.
