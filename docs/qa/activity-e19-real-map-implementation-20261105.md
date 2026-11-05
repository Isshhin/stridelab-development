# E19 — Geographic OpenStreetMap-backed Activity Map

**CURRENT DECISION (2026-11-05): Garmin is the only active wearable provider in native Android.** Intervals.icu activity/details are now archival and excluded from the UI; the geographic map can render genuine Garmin FIT GPS when downloaded and verified. Historical references below to supplemental Intervals.icu routes are superseded. [Retirement handoff](intervals-icu-native-retirement-20261105.md).
Date: 2026-11-06

**Status: GARMIN FIT/GPS REPAIR COMMITTED AND LIVE EMULATOR VERIFIED; READY FOR OWNER ACCEPTANCE. Owner acceptance remains required before E19 is closed.**

Repository: `Isshhin/stridelab-development`
Branch: `fix/trackside-usability-garmin-20261105`

## User-approved outcome

Activity Detail's decorative street grid must become a real, interactive, geographically located street map, with a recorded GPS route accurately overlaid. No illustrated routes pretending to be GPS. Existing glass sheet, Back control, Activity tabs, offline record history, Garmin source importer and Engine 1.5 remain intact. Owner explicitly declined ChatGPT Work; repository changes made directly via GitHub.

## Source implementation

- `native-android/app/build.gradle`: MapLibre Native Android SDK `org.maplibre.gl:android-sdk:13.7.1` from Maven Central.
- `RecordedGpsRoute.kt`: derives continuous line segments strictly from `ActivityDetail.samples` with valid actual recorded latitude/longitude. Splits at missing samples, temporal gaps exceeding 120 seconds, invalid Web Mercator coordinates and longitude jumps crossing the dateline. GeoJSON is `MultiLineString` in longitude/latitude order. No synthetic route, coordinates, location permissions or background GPS tracking.
- `TracksideGeographicMap.kt`: Android MapLibre `MapView` in Compose `AndroidView` with lifecycle observer. Uses `MapLibreMapOptions().textureMode(true)` so the map remains behind the existing independently scrolling Compose bottom sheet. Uses native interactive gestures, `GeoJsonSource` and red `LineLayer`. Zooms to actual track bounds after layout. If offline or the style has not loaded, shows a simple **coordinate-derived track on a blank background**, not a fake street map.
- Basemap is OpenFreeMap's OSM-derived `https://tiles.openfreemap.org/styles/liberty` style, requiring no API key. Provider: https://openfreemap.org/ ; complete visible attribution `© OpenStreetMap contributors · © OpenMapTiles · OpenFreeMap`. No `tile.openstreetmap.org` public-raster usage, no bulk/prefetch/offline tile download feature. MapLibre/provider handles in-view tile caching. Before public release, re-review provider terms, accessibility and third-party viewport privacy.
- `TracksideParityActivity.kt`: replaces `trackside_activity_street_grid` and decorative route illustration in the live Activity Detail hero; preserves existing chrome and overlays truthful GPS/no-GPS and attribution text near the visible top of the map. When `recorded` lacks actual GPS (including Garmin summary imports), no geographic map coordinate or fake route is shown.
- `TracksideFigmaActivityDetailSurface.kt`: passes the existing `online` connectivity state into the hero. The glass/matte switch and sheet-height issues remain separate E14/E12 work; no unexpected UI changes to them.
- `RecordedGpsRouteTest.kt`: four source-data tests covering exact GeoJSON coordinates, missing/large time gaps, antimeridian crossings and Garmin summary/no-GPS behavior.

## Garmin-only GPS follow-up source checkpoint (2026-11-05)

The first owner emulator showed the genuine Garmin **Batis Running** summary with **NO RECORDED GPS TRACK**. The original Garmin summary search endpoint doesn't contain route coordinates. This is a source-data gap, not a reason to draw a fake map.

A new **owner-initiated/on-open** Garmin FIT detail path has now been committed:
- Garmin Personal API authenticated activity ZIP download, strict size/path validation and FIT checksum parsing
- Legacy UUID-to-numeric Garmin activity ID lookup without rewriting original activity summaries
- Source/account/start time/distance/duration checks before account-scoped offline `ActivityDetail` persistence
- Automatic single attempt when opening Garmin-only Activity Detail and a manual **Load Garmin GPS** retry
- Genuine FIT GPS points now feed the existing MapLibre map when available; no GPS still means no map

## Garmin FIT/GPS repair and live verification (2026-11-06)

Implementation commit: `743f9b2` (`fix(garmin): repair FIT download and paused activity validation`).

Verified root causes:

1. StrideLab requested the download with only `Accept: application/zip`. Garmin's maintained Personal API client requests `application/zip, application/x-zip-compressed, application/octet-stream`. The narrower StrideLab request produced the observed HTTP 406. The endpoint, positive numeric activity ID and bearer authorization were already correct.
2. After the request fix, the real **Batis Running** FIT downloaded and decoded but was rejected because StrideLab compared Garmin summary `duration` (`5841.134765625s`) with FIT `total_elapsed_time` (`6903.84814453125s`). Garmin's summary matches FIT `total_timer_time` (`5841.134765625s`); elapsed time includes paused/stopped time. Validation now uses timer duration and falls back to elapsed only when timer duration is absent.

Exact repair files:

- `native-android/app/src/main/java/io/stridelab/privatealpha/GarminPersonalApi.kt`: use Garmin's supported archive media types without changing authentication, endpoint or response-size validation.
- `native-android/app/src/main/java/io/stridelab/privatealpha/GarminGpsDetail.kt`: compare saved Garmin duration with FIT timer duration while retaining account, sport, timestamp and distance validation.
- `native-android/app/src/test/java/io/stridelab/privatealpha/GarminPersonalApiTest.kt`: request-contract regression for endpoint, numeric ID, bearer redaction and exact archive `Accept` value.
- `native-android/app/src/test/java/io/stridelab/privatealpha/GarminGpsDetailTest.kt`: paused-activity regression proving a matching timer duration is accepted without weakening identity checks.
- `native-android/app/src/androidTest/java/io/stridelab/privatealpha/TracksideResponsiveQaTest.kt`: update the existing Activity Detail fixture for current source-selection inputs so Android tests compile.

Verification performed:

- The request-contract regression failed against the old header, then passed after the one-header correction.
- The paused-duration regression failed against elapsed-time validation, then passed after timer-duration validation.
- `:app:testDebugUnitTest`: **296 tests, 0 failures, 0 errors**.
- `:app:assembleDebug`: **BUILD SUCCESSFUL** with JDK 17 / Gradle 8.13.
- `:app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**.
- Live owner emulator: Batis Running downloaded from Garmin, decoded, passed source/account/start/distance/duration validation, persisted under its existing summary ID, and rendered genuine coordinates. The original summary remained unchanged.
- OpenFreeMap style, sprite, font and visible vector-tile requests returned HTTP 200. The camera initially fit the recorded route; direct pan and double-tap zoom changed the map viewport while the sheet stayed fixed. Attribution remained visible.
- Offline reopen displayed the persisted coordinate-derived trace with `RECORDED GPS TRACE · OFFLINE (NO STREET TILES)` and no street-map claim. Connectivity was restored after the check.

Two focused Compose gesture tests compile but cannot execute on the current API 37 emulator because this Espresso/test stack calls the removed `android.hardware.input.InputManager.getInstance()` method before reaching StrideLab assertions. This is a test-harness compatibility blocker, not a passing gesture result. Manual pan/zoom and sheet-overlay checks were performed, but the owner must still complete acceptance.

Garmin Personal API keys remain personal-use credentials; public multiple-user integrations require the appropriate Garmin agreements.

## Important limitations

1. Garmin activity summaries contain no lat/lon track. A map appears only after the owner-authorized FIT detail succeeds and passes identity validation. Activities whose FIT has no usable continuous GPS remain in the truthful no-GPS state.
2. Maps need working online OpenFreeMap style/tile requests. Offline mode shows the recorded trace on a neutral background, without any representation of streets. The app does not download map areas, nor invent map data.
3. Owner acceptance and the API-37 instrumentation compatibility issue remain open. The Activity Detail redesign, Engine 1.5/2.x and Plan Builder were not modified.

## Contract for the future Figma reconstruction

- Reuse `ActivityDetail.samples` and `RecordedGpsRoute`; never create display-only coordinates or join recorded gaps.
- Keep the map independent of sheet layout and retain the `TextureView` overlap requirement, native pan/zoom region and visible attribution.
- Preserve the truthful states: loading, online geographic tiles plus recorded route, offline coordinate trace without street tiles, and no recorded GPS/no map location.
- Preserve account-scoped offline detail storage keyed to the existing Garmin summary ID. Do not replace the summary, merge retired Intervals.icu data, bulk-download FIT files or expose credentials.
- Keep source/account/activity identity, timestamp, distance and timer-duration checks before persistence.

## Acceptance procedure

From the owner's Android Studio PowerShell terminal in `native-android`, on the latest branch head, with JDK 17 and Gradle 8.13:

```powershell
git status --short
git pull --ff-only
$gradle = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin" -Recurse -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName
& $gradle --no-daemon :app:testDebugUnitTest --tests "io.stridelab.privatealpha.RecordedGpsRouteTest" --console=plain
if ($LASTEXITCODE -eq 0) { & $gradle --no-daemon :app:assembleDebug --console=plain }
```

On the existing **data-bearing emulator**, do not uninstall/clear app or alter signing keys:
- Open Batis Running. Confirm the already persisted Garmin route follows its recorded geography, the camera initially fits it, pan/zoom work, and attribution remains visible.
- Expand/collapse the existing glass sheet, confirm map stays behind it and drag gestures work on the exposed region. MapView uses TextureView specifically for this.
- Disconnect network and reopen the same saved GPS activity. Confirm an honest coordinate trace without fabricated geographic streets.
- Open a Garmin summary with no stored track. It must clearly say no recorded GPS rather than draw a fake vector route or show an arbitrary map city.
- Compare with an activity having GPS gaps or missing coordinates; verify no line is invented across gaps or the antimeridian.
- Re-run the focused Compose/instrumentation checks on a supported emulator/test stack, then screenshot and record actual outcomes. **Do not declare E19 closed before owner acceptance.**

## Source and provider references

- MapLibre Android: https://maplibre.org/maplibre-native/android/
- OpenFreeMap: https://openfreemap.org/
- Garmin Personal API official client: https://github.com/garmin/connect-personal-api-python
- OSM standard tile policy (not used): https://operations.osmfoundation.org/policies/tiles/
