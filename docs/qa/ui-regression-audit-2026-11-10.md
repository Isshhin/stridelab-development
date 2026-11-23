# StrideLab UI regression audit — 2026-11-10

## Scope and baseline

Source-level review of `integration/validated-lime-ui-20261110` against `integration/modern-ui-lime-builder-share2-20261108` (modern-UI reference branch) and the retained native Compose application. The user's actual Android app implementation is the visual authority; older Figma references are not. This audit does not claim emulator pass/fail beyond logs supplied separately. No Activity Detail or Garmin data code was modified during the audit.

## Retired Library entry point

**Confirmed and addressed:** The More screen contained a **Workout library** entry that navigated to the legacy `library` and `practice/{key}` routes in `NativeMainActivity.kt`. The Library UI itself is implemented in `NativeLibrary.kt`; its `SignalCard` uses the older shared `TracksideGlassCard` style. `TracksideBatch345UiTest.curatedLibraryUsesVerifiedOnDeviceMetadata` also directly rendered `LibraryScreen`, which could display it during instrumentation tests.

Commit `f8151931fbc466bca66e6fff66ebf0807608d3f1` removes the More entry, the two routes, and the old test's UI rendering, while retaining `NativeLibrary.kt`, curated catalog assets, library data, and the metadata assertions. `TracksideTabTest` now checks that the More screen does not expose Workout library. No data migration or deletion was performed.

## Activity Detail findings

1. **The main Activity Detail sheet is not wholly reverted.** `TracksideActivityDetailSurface.kt` and `TracksideScrollActivityContent.kt` retain map/sheet/section composition, persistent map handling, overview/splits/charts, and related test tags. Therefore the screenshot alone does not demonstrate a complete rollback.

2. **Confirmed source-level regression: long-recording UI composition.** The modern reference `TracksideFigmaActivityDetailSurface.kt` adds a `heavyRecording` threshold (`recorded.samples.size > 4_000`) and delays composition of the continuous chart subtree until first sheet expansion. The validation branch's renamed `TracksideActivityDetailSurface.kt` does not contain this optimization. This may cause extra work on detailed long-run records, but needs runtime profiling to determine severity.

3. **Confirmed source-level regression: GPS route presentation.** The validation branch's `TracksideParityActivity.kt` displays `gpsContinuityLabel` and an extra gray `GPS gap` legend under some conditions. In the modern reference, it displays a simpler `Recorded route` label instead. The validation branch `TracksideGeographicMap.kt` renders dashed gap indicators and uses `route.toGeoJson()` directly. The newer source uses `displayGeoJson()`, `displaySegments` and `renderSegments` helpers in `RecordedGpsRoute.kt`, with continuity as **presentation only** and source GPS evidence unchanged. The validated branch lacks these presentation helpers. This is a genuine difference from the approved continuous-route visual requirement.

4. **Offline-map compatibility must be preserved.** The reference `TracksideGeographicMap.kt` also introduces `online` gating around some map paths; the validation branch has later offline-map features. Blindly replacing the map or route file risks breaking offline behavior. Any fix must separately retain raw recorded points, segmentation evidence, pause handling, privacy cuts, and offline route fallback.

5. **Garmin load-button predicate differs.** In `TracksideScrollActivityContent.kt`, the validation branch adds `!state.needsSignIn` to the enable predicate for Garmin GPS loading; the modern branch omits it. This is a behavior difference to review against current authentication policy, not proven defective from source inspection alone.

## Other visual-version differences

- `TracksideComponents.kt` still contains a blue-tinted `TracksideGlassCard` gradient (`#193544`, `#101F2A`) and blue border, despite the updated AMOLED/lime app shell. This can make older cards visibly inconsistent outside Library; no shared-card redesign was made in this scoped change.
- `ShareStudioScreen.kt` differs from the modern Share Studio reference, including template-selection UI. It is not part of the Activity Detail fix and should be reviewed in its own integration pass.
- The modern builder colors were separately ported in prior commits on this branch. That does not mean every other UI screen was rebased onto the modern-UI branch.

## Validation and next safety gates

- Compile and run `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` after the Library-route change.
- On a dedicated Android 15 / API 35 emulator, run navigation and Activity Detail tests (especially `TracksideTabTest`, `TracksideActivityScrollTest`, `TracksideApprovedBuilderActivityUiTest`, and `TracksideResponsiveQaTest`). API 37 / Android 17 exposed a known Espresso `InputManager.getInstance` initialization incompatibility in the user's prior run.
- A prior `connectedDebugAndroidTest` invocation was reported failed but its **current individual assertion failures were not provided**. Do not label specific UI assertions as failed or fixed without its XML report.
- For Activity Detail remediation, port only vetted rendering/performance improvements under a new separately tested commit; do not replace the map wholesale or silently change recorded metrics. Compare screenshot states: collapsed/map-only/expanded, map gestures, long recordings, splits, charts, and sign-out/offline cases.
- Keep the installed phone build and data untouched until signing certificate and version-code compatibility checks pass.
