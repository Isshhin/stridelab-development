# Retire Intervals.icu from active StrideLab Android integration
Date: 2026-11-05
Repository: `Isshhin/stridelab-development`
Branch: `fix/trackside-usability-garmin-20261105`

## Decision and scope

The owner explicitly requested removal after cross-provider priority, duplicate-history and GPS-source issues interfered with Garmin verification. The **native Trackside Android app** now treats Garmin as its only enabled connected wearable source. Existing manual, user-authorized FIT and Health Connect records remain supported.

**Important boundary:** This removes the native app connection UI, sync endpoint and provider participation in active history. It does **not** delete past Intervals.icu activities, saved source metadata, wellness entries, local credentials, shared backend data, or revoke any external service authorization. A separate server-side disconnect/revocation would require checking the relevant account and permissions. The preserved legacy prototype app and server are not modified here.

## Code changes

- `NativeMainActivity.kt`: removes the Intervals.icu status/check/sync card from Settings. Activity and Today are sourced only from currently accepted records. History, charts and workout detail do not choose Intervals.icu over Garmin. Garmin stays the primary source and the FIT GPS button remains available on Garmin-only Detail.
- `ActiveActivitySources.kt`: central policy rejects `source="intervals"` (case insensitive) from the active Android experience; no deletion occurs.
- `TrainingRepository.kt`: cached Intervals.icu activity/wellness rows remain in account-scoped SQLite, but are excluded from State and detailed-stream memory. Incoming Intervals.icu backend history rows are skipped while still advancing the pagination cursor; no new retired-source data is stored. Existing activity/detail records are never removed. The direct Intervals status/sync methods and connection-state restoration were removed.
- `StrideApplication.kt`: removes exposed ViewModel methods for Intervals status and synchronization.
- `SupabaseGateway.kt`: removes `TrainingGateway.intervals` and the native call to `/functions/v1/stridelab-intervals`; no native code path can issue a provider sync.
- `ActivityDisplayConsolidation.kt`: removes cross-provider matching entirely; one visible item per accepted source record and no combined source labels. Garmin summaries and matching Garmin GPS detail stay together.
- `TracksideFigmaActivityDetailSurface.kt`: removes obsolete supplemental Intervals.icu messaging.
- Retired `IntervalConnection.kt`, `IntervalConnectionTest.kt` and unused `ActivityRecordedSourceSelection.kt` files. Record parsing for legacy sources remains for backwards-compatible preservation.

`NativeActions` temporarily retains two **inert** positional callback parameters to avoid breaking older Compose test fixtures; production supplies no-op lambdas and no UI invokes them. These are not an integration or network path.

## Regression coverage updated

- `ActivityDisplayConsolidationTest`: retired-source rows are hidden, raw data remains unchanged, Garmin and manual/FIT/Health Connect histories are supported, and seven-day/28-day volume ignores retired sources.
- `TrainingRepositoryTest`: remote Intervals activities and wellness do not become active; an existing archived source row survives offline reopen unchanged in SQLite; Garmin import still works and does not merge.
- `NativeVisualTest`: Garmin connection card exists, retired connection/sync buttons absent.
- `ActivityIntelligenceUiTest` and synthetic gateway fixtures use live supported source types.
- Older source parsing remains supported specifically so no local data must be destroyed.

## Required acceptance before claiming DONE

**No exact-head Gradle build or Android emulator verification has been executed from the connected GitHub editing session.** The owner's earlier successful APK build predates these changes.

From the owner’s `native-android` terminal, without uninstalling the app or clearing data:

```powershell
git status --short
git pull --ff-only
& $gradle --no-daemon :app:testDebugUnitTest --tests "io.stridelab.privatealpha.ActivityDisplayConsolidationTest" --tests "io.stridelab.privatealpha.DashboardSummaryTest" --tests "io.stridelab.privatealpha.Garmin*Test" --tests "io.stridelab.privatealpha.RecordedGpsRouteTest"
if ($LASTEXITCODE -eq 0) { & $gradle --no-daemon :app:assembleDebug }
```

Then launch the freshly installed APK on the **existing** emulator: Settings → Connections should show Garmin, not Intervals.icu; Activity and Today should show no Intervals source cards, merged source labels or duplicate mileage. Garmin Batis Running should display PACE, offer **Load Garmin GPS**, and display a real map only after a valid FIT route is loaded. Signed-in/offline reopen must retain visible Garmin runs. Verify an older Intervals raw row still exists in SQLite through the isolated instrumentation test, not by erasing it from the device.

Run isolated instrumentation tests `TrainingRepositoryTest` and `NativeVisualTest` when the device runner is available. Existing unrelated `ExerciseLibraryV2Test` failures are separate and remain open. Record the exact HEAD, compile result, failures and emulator screenshots in QA documentation before closing this item.

## Non-goals

- Do not revoke external Intervals.icu authorization, remove the shared backend integration, or delete historical data in this source change.
- Do not alter Engine 1.5, Engine 2.x research, existing workouts, signing keys or database schemas.
- Do not invent GPS routes when Garmin FIT retrieval cannot return valid coordinates.
