# Garmin E01/E02 and on-device Activity history sync — 2026-11-05

**Status: E01/E02/E18 source corrections extended; real Garmin activity has appeared in owner emulator. Latest-head unit, instrumentation and refreshed duplicate-display acceptance are NOT YET VERIFIED.**

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

- `ActivityDisplayConsolidation.kt`: strictly match Garmin and Intervals.icu **running** activities only when start times differ by <=60 seconds, durations by <=max(30 seconds,1%), distances by <=max(30 m,0.5%), and there is a unique 1:1 candidate in both directions. Prefer **Garmin** for the visible summary, dates, metrics and training totals; retain matched Intervals.icu as a clearly labeled **supplemental source for GPS/charts**. Retain both originals unchanged.
- `NativeMainActivity.kt`, `TracksideToday.kt`: show a single Activity card/Today item with combined source label for a confidently matched pair.
- `DashboardSummary.kt` and `PlanBuilder.kt`: count that run once in seven-day totals and 28-day mileage seeding, respectively.
- `ActivityDisplayConsolidationTest.kt`: four synthetic tests covering same-run one-card + total, 28-day volume, genuinely distinct runs, and ambiguous near matches. **Unexecuted** until owner runs targeted Gradle tests.
- Remaining limitation: grouping is a conservative **presentation calculation**, not proof that differently identified source records are the same underlying event. It does not merge/delete data or provide a full review/merge UX.
- Manual acceptance: After fast-forward pull and rebuild, verify the example appears as **one Activity card labeled Intervals.icu + Garmin**, with unchanged source data on disk; weekly distance and run count are not doubled; inspect a genuinely separate run and confirm it remains visible.

## Follow-up source corrections for Garmin priority 1 (2026-11-05)

The owner explicitly selected priority 1 (**E01/E02/E18**) for the next manual correction pass. The following new source corrections are committed on the same QA branch; **none of these new tests has been executed at the new branch head in this conversation**:

- **Token refresh**: `GarminPersonalSource.activities` now refreshes a stored bearer when under 60 seconds remain (instead of under one hour), avoiding excessive exchanges on paginated Garmin history. A still-valid stored bearer rejected with Garmin 401 gets **one new key exchange and one retry**. Rejection of a freshly exchanged bearer fails immediately; there is no infinite retry or secret logging.
- **Error precision**: malformed newly issued access tokens use `GarminTokenException` with a service-oriented status, not an activity-timestamp error. Actual rejected bearer uses authentication status, while malformed activity timestamps still use data status.
- **Timestamp integrity**: `startTimeGMT` accepts UTC ISO/space formats. When GMT is absent, `startTimeLocal` is accepted only if it carries an explicit `Z` or numeric UTC offset. Zone-less local times are **rejected with an actionable error**, not silently interpreted as UTC or device local time. This protects week/date comparisons and cross-provider matching.
- **E18 grouping performance and tolerance**: `ActivityDisplayConsolidation` uses a time-indexed lookup rather than scanning all Intervals.icu records for every Garmin record; a unique one-to-one running match accepts at most 60 seconds start-time difference plus the existing conservative duration and distance checks. Both provider originals remain in account-scoped storage. The Activity/Today card and dashboard/Plan Builder totals use one logical run.
- **New focused tests**: `GarminPersonalSourceTest.shortLivedButUsableTokenIsReusedAcrossHistoryPages`, `revokedStoredTokenGetsOneFreshExchangeAndRetry`, `newlyExchangedTokenRejectionDoesNotCauseRetryLoop`, `GarminPersonalApiTest.localOnlyTimeRequiresAnExplicitOffsetToAvoidWrongDay`, `ActivityDisplayConsolidationTest.sameDisplayedMinuteCanMatchDespiteDifferentProviderSeconds` and `manyUnrelatedProviderActivitiesKeepTheirOwnRecordsAndOrder`. Existing tests for malformed tokens were updated.

**Owner's earlier Android Gradle result:** Kotlin compilation passed and 274 unit tests completed, with seven failures all in `ExerciseLibraryV2Test` due to a shared `IllegalArgumentException`. That prior result **predates these new corrections** and does not verify the latest source. Full-suite Exercise Library failures remain open and must not be ignored or attributed to Garmin without the actual XML stack trace.

### Focused commands for the owner (PowerShell in `native-android`)

```powershell
$gradle = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin" -Recurse -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName
& $gradle --no-daemon :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Garmin*Test" --tests "io.stridelab.privatealpha.ActivityDisplayConsolidationTest" --tests "io.stridelab.privatealpha.DashboardSummaryTest" --console=plain
if ($LASTEXITCODE -eq 0) { & $gradle --no-daemon :app:assembleDebug --console=plain }
```

Then use the already configured emulator (without clearing existing data) to confirm Garmin sync works, status messages distinguish errors, Santa Mesa appears **once** with both source labels, and 7-day/28-day totals are not doubled. Verify a second successful sync adds **zero** copies of previously saved Garmin IDs. Run `TrainingRepositoryTest` connected Android instrumentation when convenient; no code-only claim substitutes for that result.

## User-selected source precedence — Garmin first (2026-11-05)

The user explicitly prefers **Garmin as the primary activity source** when a run is confidently paired across Garmin and Intervals.icu.

- `ActivityDisplayConsolidation.groups` now keeps the Garmin record as `primary` for the Activity list, Today, seven-day summaries and recent-mileage planning hints; primary values are the actual Garmin record's title, start, distance, elapsed time and available source metrics. Group labels read **Garmin + Intervals.icu**.
- The matched Intervals.icu record is retained separately in the offline database and exposed through `detailedSource` only for recorded GPS, laps, detailed charts or older manually linked workouts. The UI labels those streams **Intervals.icu** rather than misattributing them to Garmin.
- On a Garmin-first Activity Detail, the existing Intervals.icu backend stream ID is used when available; the locally hashed Garmin summary ID is never sent to the Intervals.icu detail API. A Garmin-only summary continues to show **no GPS**.
- Existing manual plan links to either source ID are still discoverable after the presentation preference changes. No source data is deleted, overwritten or silently merged.
- `ActivityDisplayConsolidationTest` now asserts Garmin-first values, combined source order, preserved independent metrics, Intervals-only behavior, and no double-counting.
- **Verification pending:** targeted unit build, Android compile, loaded Intervals.icu GPS under Garmin-first detail, source attribution, existing manual links, and offline reopen. Do not mark accepted until verified.

## Garmin pace field correction and FIT route assessment (2026-11-05)

Owner's emulator showed an authentic Garmin Batis Running summary (13.69 km and moving time 1:37:12), with the activity hero accurately reporting **no recorded GPS track** and the metric card reporting **AVG PACE: Not recorded**. These are different issues.

**Pace implementation committed, device verification pending:**
- `GarminPersonalClient.decodeActivity` now reads Garmin source `averageSpeed` in meters/second when present. The normalized source and `GarminHistoryImport` store an authentic `avg_pace_sec_per_km = 1000 / averageSpeed` for pace-capable sports only.
- For **previously cached Garmin runs** with no average-speed metric, `ActivityRecord` can calculate a **moving pace** from available Garmin `movingTimeS` and `distanceM`. Both approved and matte Activity Detail card variants label this `MOVING PACE`, not `AVG PACE`, preventing false provenance. For the screenshot's displayed 13.69 km and 1:37:12, this is approximately **7:06/km**, but depends on the full source precision in the actual cache. When required values are absent, remain `Not recorded`.
- No existing Garmin summaries are overwritten or deleted, and the saved records can show the fallback without resyncing. Newly imported Garmin summaries use source speed where supplied. Two dedicated importer regression tests and a source-speed API fixture assertion were added. **No exact-head Android compile or test result yet**.

**Garmin-only GPS diagnosis and next step:**
- Garmin's maintained official Personal API example supports authenticated `GET /download-service/files/activity/{activityId}`, returning a ZIP of the athlete's own activity file, and Garmin says Personal API includes activity JSON and FIT. Official sources: https://github.com/garmin/connect-personal-api-python/blob/main/README.md ; https://developer.garmin.com/gc-developer-program/overview/ ; https://developer.garmin.com/gc-developer-program/personal/personal-faq .
- StrideLab's current `GarminPersonalClient` only calls the activity **list** endpoint. `GarminHistoryImport.payload` intentionally stores no GPS samples. `TracksideGeographicMap` cannot map a summary without GPS. `FitActivityDecoder` already supports source-validated FIT GPS point decoding, but is **not yet connected to Garmin's authenticated download**.
- The follow-up source implementation for bounded authorized FIT import and the `Load Garmin GPS` UI is documented below. **Not yet Android-build/device verified.** Do not claim a real route is available until a FIT download succeeds on the owner's emulator.
- For public/commercial multi-user integration, Garmin explicitly distinguishes its **personal-only** API from the Garmin Connect Developer Program business API. Do not repurpose a personal credential for a public multi-user release without appropriate Garmin authorization.

## Garmin-only FIT GPS source implementation (2026-11-05, pending Android acceptance)

The missing map in the owner's **Batis Running** Garmin screenshot is caused by missing GPS samples, not a MapLibre positioning problem. In the same screenshot the calculated moving pace displays correctly. The owner requested a shorter **PACE** label, now committed. The source facts remain unchanged: if Garmin provides official `averageSpeed`, use that value; if only saved distance and moving time exist, calculate pace and preserve a separate provenance note in Activity Detail.

**New implementation on `fix/trackside-usability-garmin-20261105`:**
- `GarminPersonalApi.kt` now has a bounded authenticated binary download for the official `GET /download-service/files/activity/{numericActivityId}`. Only owner-authorized bearer tokens are used, with existing 401 refresh / typed failure handling. ZIP downloads are capped at 20 MiB.
- `GarminPersonalSource.kt` resolves the numeric ID for older saved UUID-based Garmin records by searching the current owner's activity list (bounded to 2,000 results). Newly imported summary records include the numeric ID in metadata for direct lookup. No existing summaries are overwritten or bulk exported.
- `GarminFitArchive.kt`: bounded ZIP entry count and expanded size, safe names, exactly one FIT activity, and 16 MiB FIT cap. `FitActivityDecoder` verifies FIT file integrity and one-session constraints and decodes recorded GPS coordinates.
- `GarminGpsDetail.kt`: checks athlete ownership, sport, start time, source distance and elapsed duration before building the saved `ActivityDetail`; rejects trackless FIT files and invents no GPS. Encoded coordinates/HR/pace/laps are source-derived. Stored under the Garmin summary's original hashed ID, account-scoped, without changing the source summary.
- `TrainingRepository.loadGarminGps` stores the validated detail offline only after all checks pass. An existing stored detail is preserved. The foreground UI loads once when opening a Garmin-only activity and offers **Load Garmin GPS** to retry. Matched Intervals.icu details remain separately attributed.
- The main map hero automatically uses the stored real GPS samples to show the geographic route. There is no need to replace the map SDK or change any Engine 1.5 code.
- Tests added for downloaded binary endpoint, UUID lookup and numeric-ID reuse, ZIP safety, real FIT-to-ActivityDetail GPS and cross-account/different-run rejection. Test execution is **pending**.

**Important:** All of the above is **source code committed, not a successfully executed Garmin activity download**. An emulator run against the owner's live Garmin account is required. Garmin may return no FIT/GPS for indoor or unsupported recordings; the honest no-map state must remain. No unbounded history downloading, credentials in logs, source deletion or token sharing.

**Suggested local verification:**
```powershell
git pull --ff-only
& $gradle --no-daemon :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Garmin*Test" --tests "io.stridelab.privatealpha.ActivityDisplayConsolidationTest" --tests "io.stridelab.privatealpha.RecordedGpsRouteTest"
if ($LASTEXITCODE -eq 0) { & $gradle --no-daemon :app:assembleDebug }
```
Then install/run without clearing app data, open **Batis Running**, verify **PACE** heading, the source-accurate calculated value, automatic single FIT request and real map. If unavailable, tap **Load Garmin GPS** and capture the exact reported Garmin error. Close only after real Garmin track loads, offline reopen works, Garmin-first summary unchanged, route aligns with known geography, and the glass sheet overlaps correctly.

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
- **Cross-provider display consolidation added after owner emulator report:** When Garmin and Intervals.icu runs have a strict, unambiguous one-to-one time/duration/distance match, Activity and Today present **one card with both source labels**, preferring the **Garmin** summary while using Intervals.icu only as a labeled source of additional streams when available. Neither original record is deleted, overwritten, merged on disk, or auto-linked to a planned workout. Ambiguous matches, missing metrics, and genuinely different workouts remain separate. The 7-day dashboard and Plan Builder recent mileage use the same logical run count. This functionality is committed but not yet exact-head build/device verified.
- Garmin's Personal API is meant for the key owner's individual use, not a multi-user public product. Treat any public release separately under authorized Garmin developer agreements.
- CI on this QA branch does not automatically run the integrated Windows workflow. An actual exact-head build and emulator run are necessary before marking E01, E02 or history integration resolved.
