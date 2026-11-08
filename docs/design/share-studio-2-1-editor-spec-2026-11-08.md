# StrideLab Share Studio 2.1 — editor refinement specification

**Status:** design specification / implementation **BLOCKED** until the owner accepts the combined 2.0 build on a physical device.
**Design authority:** actual Android StrideLab (AMOLED black, lime accent, current Compose components). The screenshot supplied 2026-11-08 is an *interaction inspiration*, not a pixel copy or design-system replacement.
**Visual exploration:** Claude canvas `https://claude.ai/artifact/H4XJjYZ6HWPCxVZ1deFXSz`. The original multi-screen screenshots and the owner's/Claude's written corrections were reviewed in chat; the **final revised canvas has NOT been independently opened/rendered**, so a fresh visual audit remains pending.
**Baseline:** `integration/modern-ui-lime-builder-share2-20261108`, commit `2c43bdcb01941b253658222a57b7ba8b9953ab20` (draft PR #53). This already includes Share Studio 2.0, Stage 1 GPS privacy, large SQLite record handling, current three-tab Today/Training/More UI and approved lime Plan Builder. Do not merge or overwrite unrelated app surfaces. The cleanup/guard PR #54 is separate and unmerged.

## 0. Release gate and non-goals

**Do not begin 2.1 Kotlin/Compose implementation before the owner accepts the combined 2.0 build on their physical phone.** The reported isolated Stage 2 source results (393 JVM tests, 13 focused Android tests) have not been rerun on the combined integration. Full instrumentation compilation was previously blocked by unrelated tests; document those failures accurately and do not remove or disable tests.

This document freezes *design intent*, not release readiness or a verified final rendering.

2.1 is a **presentation-layer editor refinement** built on existing rendering, privacy, persistence and source contracts. Keep the existing activity-to-poster source, account boundaries, Garmin data, MapLibre integration, offline rendering, preset storage, export and background behavior. No training engine changes or historical activity data mutation.

**Deferred to 2.2:** draggable/reorderable metric blocks, unconstrained freeform resize/position, alignment grids/snapping, undo/redo, freeform custom text, photo overlays, new Race Day/Long Run templates, cloud preset sync. A tap on a canvas element may open a corresponding tool in 2.1; it does **not** enable freeform canvas editing.

## 1. The default editor, shown immediately

Open Share Studio straight into a finished **Route Focus** poster. No initial template chooser or "Start Editing" confirmation. If there is no GPS geometry, produce a truthful route-free, stats-only fallback without creating a synthetic line; the user must still be able to export.

**Visual hierarchy at phone size:**

1. Top bar: Close; compact `Share Studio` title/context; **Presets** entry; primary lime **Export** action. The bar may use compact icon-plus-label or put Presets in an overflow/tool sheet at 360dp or large font sizes; Export must stay discoverable and the tap targets must not collide.
2. Center: dominant, fully visible poster with complete bounds, intentional negative space, and safe export margins. Never allow the tool dock to obscure the actual poster. Show the selected template using a concise non-exported caption and neighboring previews only where they do not clutter small screens.
3. Poster editor-only status: a subtle **"Route trimmed · 200 m"** indicator linked to the selected privacy option. The indicator MUST NOT be burned into PNG, saved in a shareable screenshot, or interpreted as proof of anonymity. When GPS is absent or route display is hidden, show truthful status instead of claiming a visible trimmed route.
4. Bottom: persistent, accessible tool dock. Proposed tools are **Template, Layout, Route, Stats, Style, Export**. Open one context sheet above the dock; the poster remains visible above the sheet. Tap a route/stats region to open the respective tool as a convenience, but every tool must be reachable from the permanent dock without discovery-by-guessing.
5. Modals, system insets, keyboard, and sheet dismiss gesture must not strand the user or lose unsaved in-memory edits. Preview must always reflect the current canonical state, not stale prior-privacy data.

**Typography and interaction targets:** minimum 44–48dp effective tap targets; labels generally at least 11–12sp; test font scale 1.0, 1.3, and 1.5. Ensure the sheet can scroll independently without moving the underlying image unexpectedly. Strong contrast in dark and lime-on-dark states.

## 2. Three visually distinct poster compositions

- **Route Focus — default:** genuine privacy-filtered GPS route is the hero (when available), large readable distance beneath, a compact real-stat footer, clear StrideLab branding. Avoid synthetic route loops and invented achievements. Preserve GPS shape/aspect ratio; use current map renderer or truthful route-only fallback.
- **Performance:** editorial scoreboard. Strong hierarchy for available distance, time, pace and HR; a small genuine route only if present; a restrained lime identity band. The template must remain useful when some metrics are missing; never show a zero or fabricated number in their place.
- **Minimal:** generous negative space; restrained white/lime route and dominant distance; one compact secondary facts line. Ensure the thin route and unit labels remain readable after downscaling and on a dark background.

Templates must differ through composition, whitespace and typography, not through an explosion of per-template sliders. Do not add Race Day, PR, Record, Achievement, or Long Run labels without explicitly supported source data and owner approval.

## 3. Context tool behavior and 2.1 boundaries

| Tool | 2.1 controls | Explicit boundary |
| --- | --- | --- |
| Template | Route Focus, Performance, Minimal; preview/selection; retain ratio and privacy where compatible | No onboarding screen or newly invented templates |
| Layout | 9:16, 4:5, 1:1; approved layout choices, safe margins, preset route positions | No arbitrary draggable blocks, grids or snapping |
| Route | Style, Map, **Privacy** tabs; existing route mode, color, stroke, zoom/position; privacy options **200 / 400 / 800 m** with default 200 | No "Off"; do not bridge privacy cuts; markers hidden by default |
| Stats | Whole-row filled/outlined selection state, no checkmarks; 1–4 featured available metrics, explicit featured metric | No visual drag handles, custom ordering, or fake unavailable stats |
| Style | Current compatible background, typography, branding and layout intensity controls; avoid low contrast | No photo background unless independently authorized and sourced |
| Export | Device-compatible PNG save/share; Story 1080×1920, Portrait 1080×1350, Square 1080×1080 defaults; transparent PNG **off** by default | High-res 1440×2560 Story *proposed*, only after memory profiling and verified bounds |

Avoid diverging from the actual Stage 2 renderer/state if an item is not yet supported. Implement a narrow adapter or additive state migration with tests rather than replacing source data and persistence.

### Route privacy and rendering rules

- Existing `SharePrivacy`: `STANDARD=200m`, `EXTRA=400m`, `MAXIMUM=800m`. No zero-distance, 100m, or 500m choice, and no silent downgrade of previously chosen privacy when changing template/preset.
- Existing `ShareRenderableActivity.from` calls `shareRoutePresentation(..., privacy.meters, ...)` to filter source GPS before the render. **Keep filtering ahead of rendering, camera framing, preview caching and PNG output.** Never render the untrimmed source even for a frame during state transitions.
- Start and finish markers are hidden by default. If a future visible-marker option is explicitly introduced, it must use only already-privacy-filtered endpoints, never original track endpoints. The proposed 2.1 control can show a disabled/non-operative state until this is securely implemented; do not display a working-looking toggle that changes nothing.
- Paused GPS points may be visually joined where existing approved behavior permits; **privacy cuts must never be rejoined**. Don't claim endpoint trimming guarantees anonymity.
- Transparent export is route-and-stats only, without a map background, opaque scrim, or accidental bitmap fill. The UI's non-exported trim indicator must also be absent.

### Source-backed metric names

- **Duration:** current Share Studio 2.0 renders `ShareMetric.DURATION` from `ActivityRecord.durationS` (`duration_s`) through `ActivityRecord.duration()`. Display **"Duration"**; do **not** replace it with "Moving time", because `ActivityRecord.movingTimeS` is a distinct field and the current share renderer does not use it for that metric.
- Pace from `ActivityRecord.recordedPace`; HR from `averageHr`; elevation from `elevationM`; distance from `distanceM`. Missingness stays missing; no invented source fields, PR tags, race flags or location geocoding.
- If a separate Moving Time metric is added later, it requires its own data/format validation and tests, with no relabeling of current duration.

## 4. Presets bottom sheet — specified missing screen

The existing versioned local preset functionality must survive intact; 2.1 improves discoverability and edit flow without changing the meaning of saved presets.

**Entry:** labeled `Presets` in top bar at widths where it fits, otherwise a clearly labeled entry in the tool sheet/overflow. Preserve `Export` as the primary action. The Presets sheet should expose a title, close/back affordance and a compact description (`Reuse a saved poster setup`). When open, the poster remains visible above it.

**Suggested sheet layout (not a freeform canvas):**

```text
┌───────────────────────────────┐
│            ───                │
│ Presets                   [×] │
│ Reuse a saved poster setup    │
│                               │
│ [ Save current as preset   + ]│
│                               │
│ YOUR PRESETS                  │
│ [thumbnail]  Sunday Long Run  │
│              Route Focus · 9:16│
│              [Apply] [•••]    │
│ [thumbnail]  Minimal Story    │
│              Minimal · 9:16   │
│              [Apply] [•••]    │
│                               │
│                    (scroll)   │
└───────────────────────────────┘
```

- Each saved row has a name, derived template/ratio description, current-state thumbnail **only if it can be rendered truthfully and efficiently**, explicit Apply, and an overflow with **Rename / Update with current settings / Delete**. Avoid multiple ambiguous icons per row and avoid checkmark badges; an active row uses whole-card highlighting.
- **Save current:** request an editable name (1–60 characters, nonblank), explain that the poster *configuration* is saved, not a copy of the athlete's private GPS. Save only after confirmation; respect the current 100-preset cap and storage errors.
- **Apply:** reapply the stored layout/options and update the actual activity-specific preview. If the current activity lacks a preset-selected metric or route mode, use existing `forActivity` missingness/fallback behavior and explain unavailable fields rather than inventing data.
- **Rename** edits only the name, preserving the preset ID and configuration. **Update** confirms before replacing stored configuration. **Delete** requires confirmation and must not erase the activity or any other preset.
- Existing preset JSON `version=1` and `SharePresetCodec` must continue to decode and round-trip. New 2.1 presentation changes should not force a storage migration; if new options need persistence, write backward-compatible tests and avoid destructive rewrites.
- Presets are offline/local by default; **no automatic cloud sync**. Never store raw activity samples, map tiles, access tokens, or preview bitmaps inside presets.
- **Empty state:** `No saved presets yet` with `Save current as preset`. **Error:** loading failure must not overwrite existing stored presets with an empty list; provide a retryable message.
- Touch/keyboard state: confirm dismissal, avoid accidental double saves, and keep the sheet scroll position when returning from rename/delete dialogs.

## 5. Acceptance checklist (before any 2.1 merge)

### Gate A — **2.0 physical-device acceptance** (precondition; not yet passed)
- [ ] Exact combined PR #53 build runs on the owner's Xiaomi using safe signer/version-compatible install, **without deleting app data**.
- [ ] Current three-tab Today/Training/More UI is visually correct; Plan Builder works.
- [ ] A real saved Garmin long activity opens offline (SQLite fix), and Share Studio 2.0 opens it.
- [ ] Existing Stage 1 privacy (200/400/800), pausing/continuity and absence of cut endpoints verified; tests for genuine route and export.
- [ ] All current preset configurations load/save and PNG save/share can target a real app.
- [ ] Record failures and owner sign-off. If Gate A fails, fix 2.0 separately before starting 2.1.

### Gate B — **2.1 native layout / accessibility / behavior**
- [ ] Actual Android Studio screenshots (360dp, 412dp; font scales 1.0, 1.3, 1.5) compare with the owner-approved current Android aesthetic and revised concept. Verify sheets, dock, preview and top bar **without clipping or horizontal scrolling**.
- [ ] First frame opens a complete polished poster; template switch preserves real data and privacy. No extra chooser step.
- [ ] Canvas keeps its full bounds above each sheet; all tools reachable from dock; canvas taps optional.
- [ ] Selected metric rows highlighted as whole cards, **no checkmarks or drag handles**. Only recorded metrics selectable.
- [ ] Privacy selector defaults to 200m; 200/400/800 options work; trim indicator editor-only; start/finish hidden.
- [ ] No disclosure of original endpoints in map snapshots, thumbnails, route-only PNG, transitions, presets or share previews.
- [ ] No stale content on rapid privacy/template/ratio switches; preview and export share the same current state, renderer and privacy snapshot.
- [ ] Presets select/save/rename/update/delete and error states work offline; v1 preset compatibility verified.
- [ ] Export Story 1080×1920, Portrait 1080×1350, Square 1080×1080 with correct alpha and no crop. High-res optional **only after profiling** on constrained devices.
- [ ] Test extreme routes, no GPS, missing metrics, unusual aspect ratios, very long numeric labels, canceled/export failures, app recreation and long-run samples (25k+) for memory, smoothness and correctness.
- [ ] Verify map tiles online/offline, physical phone airplane mode, gallery output and actual Android share targets.
- [ ] Test real persisted activities and activity-account isolation; no engine, Garmin, source data, offline or SQLite regression.

### Gate C — explicit owner acceptance
- [ ] Owner reviews final Android screenshots and physical-device gestures.
- [ ] Only after this review may the 2.1 implementation PR be considered for merge.

## 6. Visual review remaining

The revised Claude canvas after the latest corrections was not independently visually fetched. Before treating it as a pixel-level implementation contract, capture the revised **editor, Stats, Route Privacy, Export and Presets sheet**, including the crowded 360dp top bar and both 360dp/412dp sheet-scroll states. Match the source Android theme and type/spacing; do not use Figma as the authoritative UI source.

The Route Focus, Performance, and Minimal mockups contain *illustrative* route data and metrics, not real Garmin evidence. Final implementation may visually differ where truthful activity values or no-GPS states require it.
