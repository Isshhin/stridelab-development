# E19 — Geographic OpenStreetMap-backed Activity Map
Date: 2026-11-05

**Status: SOURCE IMPLEMENTATION COMMITTED; exact-head Gradle build, tests, network map load and owner emulator acceptance NOT RUN.**

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

## Important limitations

1. Garmin E01/E02 imported activity **summaries**, which contain no lat/lon track. They cannot show a mapped Garmin-only route. Garmin detailed track import remains separate stage 3 and must use authorized real sources. An E18 matched Intervals.icu record can show a route when it has actual detailed GPS samples.
2. Maps need working online OpenFreeMap style/tile requests. Offline mode shows the recorded trace on a neutral background, without any representation of streets. The app does not download map areas, nor invent map data.
3. Source modifications are committed but **not compiled or exercised on the owner's Android emulator as of this handoff**. Existing ExerciseLibraryV2 unit-test failures remain separate and open.

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
- Open a recorded Intervals.icu run with known GPS samples. Let "Load recorded streams" complete (or load from offline cache). Confirm the red route follows real streets and camera bounds, pans/zooms, and includes attribution in the visible map area.
- Expand/collapse the existing glass sheet, confirm map stays behind it and drag gestures work on the exposed region. MapView uses TextureView specifically for this.
- Disconnect network and reopen the same saved GPS activity. Confirm an honest coordinate trace without fabricated geographic streets.
- Open a Garmin summary with no stored track. It must clearly say no recorded GPS rather than draw a fake vector route or show an arbitrary map city.
- Compare with an activity having GPS gaps or missing coordinates; verify no line is invented across gaps or the antimeridian.
- Run focused Compose/instrumentation checks that cover the Activity screen, then screenshot and record actual outcomes. **Do not declare E19 closed before passing build, functional map load and emulator acceptance.**

## Source and provider references

- MapLibre Android: https://maplibre.org/maplibre-native/android/
- OpenFreeMap: https://openfreemap.org/
- OSM standard tile policy (not used): https://operations.osmfoundation.org/policies/tiles/
