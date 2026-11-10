# Trackside Figma-name cleanup — source-only migration (2026-11-10)

## Scope
The current Android app is the visual authority. Rename live Kotlin UI sources and functions formerly prefixed `TracksideFigma` to `Trackside`; keep their bodies, public argument lists, navigation, design, training-plan behavior and persisted data contracts intact.

- `TracksideFigmaActivityDetailSurface.kt` → `TracksideActivityDetailSurface.kt`
- `TracksideFigmaAdvancedRunning.kt` → `TracksideAdvancedRunning.kt`
- `TracksideFigmaBuilderSchedule.kt` → `TracksideBuilderSchedule.kt`
- `TracksideFigmaBuilderScreens.kt` → `TracksideBuilderScreens.kt`
- `TracksideFigmaCalisthenicsSetup.kt` → `TracksideCalisthenicsSetup.kt`
- `TracksideFigmaCrossTrainingGoals.kt` → `TracksideCrossTrainingGoals.kt`
- `TracksideFigmaGuidedRunning.kt` → `TracksideGuidedRunning.kt`
- `TracksideFigmaOtherDisciplineSlots.kt` → `TracksideOtherDisciplineSlots.kt`
- `TracksideFigmaRunningSetup.kt` → `TracksideRunningSetup.kt`
- `TracksideFigmaStrengthSetup.kt` → `TracksideStrengthSetup.kt`
- `TracksideFigmaWeekReview.kt` → `TracksideWeekReview.kt`

Every `TracksideFigma*` declaration/call was changed with the same mechanical prefix substitution. Callers updated: `NativeMainActivity.kt`, `PlanBuilder.kt`, and four Android instrumentation tests (`ShareStudioUiTest.kt`, `TracksideActivityScrollTest.kt`, `TracksideApprovedBuilderActivityUiTest.kt`, `TracksideResponsiveQaTest.kt`).

## Removed
`TracksideFigmaPreviewGallery.kt` was an unused, synthetic `@Preview` comparison gallery tied to older Figma artwork, with no production calls and no references outside itself. No live screens or runtime data were deleted.

## Do not remove
- `android-app/android/app/src/main/java`: the current `native-android/app/build.gradle` still includes this Java source set. Removing it would break the app's database and core implementation.
- Active `TracksideParity*` classes, engines, screens, MapLibre/FIT/Garmin code, and storage. Their names are historical but they remain live.
- Older designs/docs retained as historical evidence, not as UI implementation authority.

## Validation boundaries
A source audit covered Kotlin main, unit tests, and Android tests in `native-android/app/src`, locating all live `TracksideFigma` symbol references. The transformation only renames identifiers/filenames and deletes the isolated synthetic preview gallery. This is not evidence of a successful compile, test run, or device acceptance.

Owner-run follow-up when convenient: Android Studio Gradle sync; `native-android` `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and UI instrumentation for plan-builder navigation, Activity Detail and Today. Do not install a new APK until package/signature/data-safety gates are checked.
