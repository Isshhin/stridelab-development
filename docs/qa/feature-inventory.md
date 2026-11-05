# Feature inventory

This inventory describes implemented behavior at the current handoff. “Covered” names automated evidence, not phone or live-service acceptance. Approved Trackside design authority is Figma file `v73TsYo6gKxUoiUock2sSE` plus the linked repository specifications.

| Component | Implemented behavior and state owner | Current regression evidence | Limits / authority |
|---|---|---|---|
| Authentication and account state | Email/password sign-in, refresh, verify, local session vault, sign-out hiding cached data, account-scoped SQLite records | `SupabaseGatewayTest`, `TrainingRepositoryTest`, `NativeNavigationTest` | Fixture gateway and emulator only; no hosted-auth acceptance in this audit |
| Root navigation | Today, Training, Activity, Library and More/Settings roots; detail routes restore their owning tab and Back stack | `TracksideTabTest`, `NativeTabRootTest`, `NativeNavigationTest` | `NativeMainActivity.kt`; Trackside navigation designs |
| Today | Active-plan status, next eligible workout, same-day sessions and recent recorded activities; opens selected workout/activity IDs | `TracksideTodayTest`, `Engine1UiTest`, responsive suite | No physical-phone touch acceptance |
| Training calendar | Week/day/agenda state, multiple sessions per date, selected-date restoration and Manage route | `TracksideTrainingCalendarTest`, `TracksideTrainingCalendarUiTest`, `TracksideManageNavigationTest` | Figma node `602:2` and current calendar implementation |
| Plan Builder | Discipline, goals, ability, preferences, plan dates, availability, weekly placement, review, draft generation and explicit approval | `TracksideApprovedBuilderActivityUiTest`, `TracksideResponsiveQaTest`, `TracksideEngineBuilderIntegrationTest` | Engine 1.0 is packaged default; Figma builder nodes indexed in the Trackside audit |
| Running weekly setup | Occupied weekdays select their owning session; only the explicit editor moves/swaps/clears; identity persists into review/generation | `TracksideApprovedBuilderActivityUiTest`, `WeeklyPlacementTest` | Figma node `548:95` |
| Strength/calisthenics/conditioning setup | Discipline goals, ability/preferences and independent weekly slots; safety validation before generation | builder UI suite, `WeeklyPlacementTest`, engine validation tests | Conditioning has less dedicated UI outcome coverage than running/strength/calisthenics |
| Manage Plan | Open selected workout, pause with confirmation, archive, lock, move, missed-workout review and revision-checked changes | `TracksideManagePlanTest`, `TracksideEngine15ManagementUiTest`, `Engine15FeedbackRepositoryTest`, `TracksideParityPlanStateTest` | Archive confirm has source implementation but no dedicated UI outcome test |
| Workout player and completion | Step navigation, explicit actual-set confirmation, timers, draft persistence, review and completion validation | `WorkoutPlayerTest`, `WorkoutEntryDraftTest`, `AutonomousReliabilityRegressionTest`, `Engine1RepositoryTest` | Automated emulator/SQLite evidence only |
| Activity history/details | Account-scoped activity list/detail, source-backed metrics, fixed map behind anchored sheet, tabs, inner scroll, accessible expand/collapse and collapse-first Back | `TrainingRepositoryTest`, `ActivityDetailTest`, `ActivityIntelligenceUiTest`, `TracksideApprovedBuilderActivityUiTest`, responsive suite | Recorded GPS only; no fabricated map path |
| Activity/workout matching | Explicit same-source identity and file hash; cross-source similarities are review candidates; manual links are unique/reversible | `ActivityImportMatcherTest`, `Engine1RepositoryTest` | No automatic cross-source merge |
| Intervals.icu | Existing backend status/sync action, cached account-scoped connection state and activity refresh | `IntervalConnectionTest`, `TrainingRepositoryTest`, `NativeVisualTest` | No live connected-account verification in this audit |
| FIT foundation | Official SDK decoding, bounded validation, original-file cache, corruption/interruption/account isolation and safe URI intake helpers | `FitActivityDecoderTest`, `FitContainerReaderTest`, `FitImportCacheTest`, `FitFileIntakeTest` | No user-facing picker/share manifest route and no canonical history transaction; not an end-to-end importer |
| Garmin Personal API foundation | Official API-key token exchange, activity paging, token lifecycle, secure account-scoped credentials, provenance and explicit dedupe classification | `GarminPersonalApiTest`, `GarminPersonalSourceTest`, `GarminCredentialVaultTest` | Fetched pages are not persisted; live key not supplied |
| Exercise Library and templates | Educational on-device catalog, filters/favorites/collections, guides, reusable reviewed templates; catalog entries do not silently prescribe workouts | `ExerciseLibraryV2Test`, tooling validator, `Engine1UiTest`, `TracksideBatch345UiTest` | Catalog/media provenance report remains authoritative |
| Backup, restore and upgrade | JSON export/open-document restore UI, owner/version/integrity validation, atomic non-destructive restore, legacy DB/outbox/Keystore retention | `BackupIntegrityTest`, `Engine1RepositoryTest`, `NativeUpgradeTest`, `CorruptCacheTest` | No release APK or signer/device update test in this audit |
| Offline reliability | Cached history/plans/logs/details/wellness, durable outbox, repository recreation and failure recovery | `TrainingRepositoryTest`, `Engine1RepositoryTest`, `OutboxOfflineRecoveryTest`, feedback repository tests | Synthetic offline mode, not network-condition field testing |
| Engine boundaries | Engine 1.0 handles new drafts by default; explicit research builds may enable frozen Engine 1.5; persisted versions remain immutable; Engine 2.x is separate | `TracksideCoachingIntegrationTest`, Engine 1.5 architecture/matrix suites, packaged rollback gate | Do not enable Engine 1.5 in production or change coaching during UI QA |

## Not implemented as user-facing functionality

- TCX import.
- GPX import.
- A complete user-facing FIT picker/share target that commits a decoded activity to canonical history.
- Garmin-to-`TrainingRepository` persistence and live Garmin verification.
- Automatic cross-provider deduplication or deletion.

These are coverage/scope boundaries, not defects in controls that claim to provide the behavior.
