# Garmin E01/E02 and on-device Activity history sync — 2026-11-05

**Status: Garmin source implementation and E18 grouping committed. User emulator has displayed a Garmin-sourced run; focused exact-head regression, reliable status/timestamp acceptance, and E18 refreshed UI acceptance remain OPEN.**

**CURRENT PRIORITY:** (1) E01/E02 real Garmin source reliability and error classification, with E18 display/totals regression; (2) E19 real OpenStreetMap-backed route for existing GPS; (3) Garmin detailed GPS import if supported and authorized. Engine 1.5 follows afterward. See [current master roadmap](engine15-manual-remediation-roadmap-20261105.md).

**Scope:** User requested correcting Garmin's misleading expired-auth message (E01), invalid activity timestamp (E02), and actually seeing Garmin activities inside StrideLab's existing Activity tab. This is a personal-device integration, not Engine 1.5 activation or a Garmin-to-server history backfill.

Branch: `fix/trackside-usability-garmin-20261105`. Initial source checkpoint: `d4e862c0172fbb2035be2f8392a1963e54f0edbb`. All changes after that checkpoint are confined to the listed Garmin files and the minimum repository/ViewModel/UI wiring and tests. **The original source-implementation session did not execute Gradle or the Garmin API. Since then, the owner reported a local Kotlin compile and unit run (274 tests, seven ExerciseLibraryV2 failures) and showed a Garmin-sourced activity in the emulator. That establishes that some live import path works, but not that the latest source head, timestamp edge cases, status-classification tests, or E18 pairing all pass.**

## Implemented source behavior

- `GarminPersonalApi.kt`: Accept official source GMT timestamps using ISO `T`, date-time separated by a space, optional fractional seconds and explicit UTC offsets. Invalid or missing timestamps still fail closed without fabricated measurements. Add distinct HTTP-service failure classification.
- `GarminPersonalSource.kt` + `GarminSettings.kt`: Distinguish authentication, source data, service, network, rate-limit and secure-storage errors, instead of claiming every failure means expired credentials.
- `GarminHistoryImport.kt` + `WearableProviderContracts.kt`: Preserve provider `garmin`, original upstream ID, title, date/time, distance, duration, source moving duration and average HR when present. Stable SHA-256 provider-prefixed record ID prevents overwrite of Intervals.icu records and keeps account ownership scoped.
- `TrainingRepository.syncGarmin`: Foreground user-triggered sync, up to 100 pages / 10,000 entries with safe bounded pagination; validate a full page before writing, retain completed pages on later failure, skip same-source reimports, reject conflicting IDs and duplicate page entries. Records are saved to existing SQLite `activity` cache without making a cloud/outbox change. Reopen and sign-out behavior continues to follow existing account-scoped repository rules. Never auto-merge, delete or overwrite cross-provider activities.
- `StrideApplication.kt`, `NativeMainActivity.kt`, `GarminSettings.kt`: More → Connections → **Sync to Activity** now invokes shared TrainingRepository through the ViewModel. The Activity tab and Today use existing state flow and display locally persisted summaries.
- `NativeMainActivity.kt`, `TracksideFigmaActivityDetailSurface.kt`: Garmin summary-only records do not incorrectly request detailed samples from the StrideLab backend using an unrelated hashed ID. GPS traces, source laps and high-frequency charts are NOT included; the app displays a truthful summary-only message.

## New named regression coverage — UNEXECUTED

- `GarminPersonalApiTest.acceptsSpaceSeparatedGmtWithFractionAndExplicitOffsetWithoutLocalTimeShift`
- `GarminPersonalApiTest.trulyInvalidOrMissingActivityTimestampStillFailsClosed`
- `GarminPersonalSourceTest.dataFailureIsNeverMisclassifiedAsAuthenticationExpiry`
- `GarminHistoryImportTest.durableSummaryPreservesGarminProvenanceAndActualSourceMetrics`
- `GarminHistoryImportTest.absentMetricsRemainAbsentAndWrongProviderOrOwnerIsRejected`
- `TrainingRepositoryTest.garminSummarySyncAppearsInActivityTabAndSurvivesOfflineReopen`
- `TrainingRepositoryTest.duplicateGarminIdsWithinOnePageDoNotPartiallyWriteHistory`

## Additional owner finding: cross-provider duplicate display (E18)

The owner provided a screenshot showing **Santa Mesa Running** at the same local start time and distance (9.56 km), with Garmin duration 1:03:36 versus Intervals.icu duration 1:03:27. This confirms the live Garmin source appeared in local Activity history, but does not prove every imported record is correct or complete.

- `ActivityDisplayConsolidation.kt`: strictly match Garmin and Intervals.icu **running** activities only when start times differ by <=45 seconds, durations by <=max(30 seconds,1%), distances by <=max(30 m,0.5%), and there is a unique 1:1 candidate in both directions. Prefer the detailed Intervals.icu record; retain both originals unchanged.
- `NativeMainActivity.kt`, `TracksideToday.kt`: show a single Activity card/Today item with combined source label for a confidently matched pair.
- `DashboardSummary.kt` and `PlanBuilder.kt`: count that run once in seven-day totals and 28-day mileage seeding, respectively.
- `ActivityDisplayConsolidationTest.kt`: four synthetic tests covering same-run one-card + total, 28-day volume, genuinely distinct runs, and ambiguous near matches. **Unexecuted** until owner runs targeted Gradle tests.
- Remaining limitation: grouping is a conservative **presentation calculation**, not proof that differently identified source records are the same underlying event. It does not merge/delete data or provide a full review/merge UX.
- Manual acceptance: After fast-forward pull and rebuild, verify the example appears as **one Activity card labeled Intervals.icu + Garmin**, with unchanged source data on disk; weekly distance and run count are not doubled; inspect a genuinely separate run and confirm it remains visible.

## Required manual verification before DONE

1. From Android Studio on this branch, fetch/pull exact source head, sync Gradle with JDK 17 / Gradle 8.13 / Android API 36.
2. Run `:app:testDebugUnitTest` and `:app:assembleDebug`; fix compile or unit failures before proceeding. Do not assert pass from source inspection alone.
3. On a **disposable API 35 emulator** run `:app:connectedDebugAndroidTest` with `-Pandroid.testInstrumentationRunnerArguments.class=io.stridelab.privatealpha.TrainingRepositoryTest`; verify both new tests pass alongside the older offline/owner/persistence cases.
4. Open More → Connections → Garmin. With user's already saved credentials, select Sync to Activity; do not disconnect or regenerate credentials. Confirm a factual connected/data/network header and *new Garmin summary count*. Do not show a stale expired-auth message for a parsing failure.
5. Open Activity and Today. Confirm correct source label `Garmin`, dates, source distance/moving duration/HR. Repeat Sync to Activity and assert no duplicate Garmin records; previously saved Intervals.icu records remain intact. Reopen offline to confirm durable cache; sign out and confirm data isolation.
6. If still failing, capture **sanitized timestamp format and status**, not API key, bearer token, raw private activity payload or complete user profile. Record exact failing source record shape before broadening parser rules.
7. Preserve existing Engine 1.5 and UI/Figma acceptance scope. No APK signing changes, no release, no production multi-account Garmin integration claim.

## Explicit limitations and safety

- User-triggered local **summary** sync only: this does not create Garmin detail streams, mapped routes, FIT files, training effects, canonical cloud backfill or automatic planned-workout matching.
- **Cross-provider display consolidation added after owner emulator report:** When Garmin and Intervals.icu runs have a strict, unambiguous one-to-one time/duration/distance match, Activity and Today present **one card with both source labels**, preferring the Intervals.icu detail record. Neither original record is deleted, overwritten, merged on disk, or auto-linked to a planned workout. Ambiguous matches, missing metrics, and genuinely different workouts remain separate. The 7-day dashboard and Plan Builder recent mileage use the same logical run count. This functionality is committed but not yet exact-head build/device verified.
- Garmin's Personal API is meant for the key owner's individual use, not a multi-user public product. Treat any public release separately under authorized Garmin developer agreements.
- CI on this QA branch does not automatically run the integrated Windows workflow. An actual exact-head build and emulator run are necessary before marking E01, E02 or history integration resolved.
