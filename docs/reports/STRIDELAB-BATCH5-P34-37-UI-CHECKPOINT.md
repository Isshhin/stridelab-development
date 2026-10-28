# StrideLab Batch 5 UI candidate — Steps 34–37

**Status:** Source committed, **NOT** compiled or emulator-verified. **Base:** verified checkpoint `25be8f3828374d1e539c7e825afa20fbcb652b56`; independent of unverified Batch 4 migration branch.

## Implemented source candidates

- **P34:** `ActivityDetailPresentation.kt` supplies a typed, source-truthful read-only view-model for either `ActivityRecord` + optional `ActivityDetail` or `PlanSnapshot` + `WorkoutLog`, preserving missing values and avoiding fabricated streams. `ActivityDetailPresentationTest.kt` adds source identity, missingness and actual-set scenarios.
- **P35:** `LoggedActivityDetail.kt` shows actual strength/calisthenics sets and missing metrics, not planned targets disguised as actuals. Activities list routes local logged sessions to a distinct read-only detail view. `RecordedActivityDetail.kt` now offers Charts, Splits and Route sections using the **existing authentic recorded samples** and offline GPS trace, with no street-map claim. `NativeVisualTest.kt` includes new tab navigation and local-set presentation assertions.
- **P36:** Reuses existing `WeeklyDashboard`, `HomePlanHero`, Training week/month calendar and activity history; shows an independently identified last recorded run only when different from the latest activity. No invented fitness score, no duplicated overview card for the same run.
- **P37:** Existing search/discipline/equipment filters and educational-guide distinction are preserved. `LibraryImage` now provides a neutral explanation for guide-only entries and failed/loading image assets; it does not invent or download unlicensed exercise imagery.

## Grounded constraints

- `native-library/catalog.json`: 44 entries; 23 non-guide image-backed exercises, 21 guide-only entries without images, current catalog not widened.
- Native recorded detail model has optional source samples, laps, GPS, pace, HR and elevation. No native FIT parser is required to render source-backed Activity Detail.
- Existing screen tokens and Material 3 theme remain authoritative; no wholesale layout redesign or replacement of preserved data.
- No feature dependency on Engine2 approval storage or a live Garmin account.

## Remaining work / acceptance

- Gradle compilation, JVM and Android instrumentation on this exact branch; adapt any UI regressions exposed by the new tab navigation.
- Verify navigation when saved account or local logged session disappears; check TalkBack/focus and large text scaling, tablet widths, and empty stream/route states.
- Full visual reconstruction of the prototype, improved charts, progressions, analytics and licensed catalog expansion remain later scoped work.
- No SQLite schema change, CI workflow dispatch, package upload, APK installation or merge was performed.

This branch is a **minimal usable UI iteration candidate**, not a fully verified or visually final StrideLab app.
