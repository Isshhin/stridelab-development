---
title: "Share Studio"
area: feature
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareStudioScreen.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareCardRenderer.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareStudioModels.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareRoutePresentation.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareMapSnapshot.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/SharePhotoImport.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/SharePresetStore.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareImageExport.kt"
tags: [stridelab, feature, share-studio, privacy, maplibre, canvas]
---

# Share Studio

Share Studio renders fixed-resolution (`1080 × {1920, 1350, 1080}`) athletic share cards where preview and exported PNG pixels match 1:1.

## Templates, Variants, Ratios & Backgrounds (`ShareStudioModels.kt`)
- **4 Templates & 8 Variants:**
  - `ROUTE` (*Route Focus*: `CLEAN`, `PERFORMANCE`, `STORY`)
  - `STATS` (*Stats Focus*: `BALANCED`, `RUN_RECAP`, `PERFORMANCE`)
  - `MINIMAL` (*Minimal*: `POSTER`, `SILHOUETTE`, `DARK_CLEAN`)
  - `WORKOUT` (*Workout Recap*: `RUN_RECAP`, `BALANCED`, `PERFORMANCE`)
- **3 Export Aspect Ratios (`1080 px` width):** `STORY` (`9:16`, `1920 px`), `PORTRAIT` (`4:5`, `1350 px`), `SQUARE` (`1:1`, `1080 px`).
- **5 Backgrounds:** `MAP` (MapLibre Activity Dark snapshot), `DARK` (`#080B0A`), `GRADIENT` (atmospheric dark poster linear + subtle radial glow), `TRANSPARENT` (corner `(0, 0)` strictly alpha `0`), and `PHOTO` (in-memory center-cropped photo with dual directional scrims).

## Editorial Graphic Renderer (`ShareCardRenderer.kt`)
- **Locked Bounding Geometry (`ShareCardRenderer.geometry`):** Preserves safe map/route minimum height (`>= 152 px`), separation between route and stats, and footer margins across all `routeBalance` (`0..100`) and `layout` (`AIRY` / `COMPACT`) settings.
- **Typography & Photo Scrims:** Uses tabular figures (`tnum`), uppercase tracked eyebrows/labels, subtle shadow contrast on `PHOTO`/`TRANSPARENT`, and dual directional `LinearGradient` scrims (top header + bottom metrics) over a `0x16000000` veil — keeping center-upper photos vivid (`(540, 340)` stays vivid red on red fixtures) with `paint.alpha = 255` before applying gradient shaders.
- **Multi-Pass Coordinate Route:** Draws ambient outer glow, dark contrast halo (non-transparent cards), core route or `PACE`/`ELEVATION`/`HR` gradient trace, plus hollow-start / lime-finish markers derived exclusively from trimmed display segments.
- **Workout Recap Provenance Honesty:** `WORKOUT` cards surface exact lap provenance (`RECORDED_ONLY` / classified source, recording origin) and never claim laps are verified `WORK/RECOVERY` intervals unless classified.

## Studio Editor & Privacy Controls (`ShareStudioScreen.kt`, `ShareRoutePresentation.kt`)
- **GPS Endpoint Privacy Trimming:** Defaults to `SharePrivacy.STANDARD` (`200 m` removed from start and finish), with `400 m` / `800 m` options and an explicit amber warning (`share-trim-off-warning`) when toggled off (`SharePrivacy.OFF`). Full-activity summary stats remain unchanged. See [[ADR-02-GPS-200m-Default-Privacy-Trimming]].
- **Map & Offline Fallback:** Uses MapLibre dark snapshot when online or when `StrideOfflineMapStore.hasCompleteCoverage(route)` covers the full trimmed route; otherwise falls back cleanly to a coordinate-only route trace. Never paints opaque overlays over `g.route`.
- **Canvas-First Studio Layout:** Pure `#000000` top bar (plus sticky `share-workout-provenance` when `WORKOUT`), `#0A0B0D`-bezeled responsive canvas stage (capped at `300.dp` height under `340.dp` width so resolution/section controls fit above the fold at `320 dp` `font_scale 1.3`), preview-only Story safe-area guides, `#121212` tool tray with `Canvas` 2×2 wireframe template previews, up to 100 local presets in [[Storage-and-FIT-Contracts]], and an anchored `#121212` `Save Image` / `Export PNG` / `Share` footer (`>= 48.dp` targets).

## Related Nodes
- [[00-StrideLab-Index]] · [[UI-Design-System]] · [[Hard-Guardrails]] · [[Storage-and-FIT-Contracts]] · [[Garmin-and-Activity-Import]] · [[ADR-02-GPS-200m-Default-Privacy-Trimming]]
