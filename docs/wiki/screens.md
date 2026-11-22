---
scope: screens
tags: [stridelab, screens, navigation, ui-state, deprecations]
last_updated: 2026-11-22
---

# Screens, UI State & Removed Elements

Back to [[index]] · Related: [[ui-system]] · [[architecture]] · [[engine-rules]]

## Primary Navigation Graph (`NativeMainActivity.kt`)
- **3 Retained Tab Roots** (hosted beneath `NavHost` with `TracksideFloatingNavigation`):
  - `home` → **Today** (`StrideLabTodayScreen.kt`, `TodayDisciplineDashboard.kt`)
  - `training` → **Training** (`StrideLabUnifiedTrainingTimeline.kt`, `TracksideParityTraining.kt`)
  - `settings` → **More** (`TracksideMoreScreen.kt`, `MoreFeatureUi.kt`)
- **Detail & Workflow Routes** (`showFloatingDock == false` on detail/builder/workout flows):
  - `workout/{planId}/{sessionId}` → `WorkoutScreen.kt`
  - `activity/{id}` & `activities` → `TracksideActivityDetailSurface.kt`, `TracksideStationaryActivityDetail.kt`
  - `builder/{discipline}` → `PlanBuilder.kt`, `TracksideBuilderScreens.kt`
  - `manage/{planId}/{date}`, `move/{planId}/{sessionId}`, `review-move/...`, `missed/...` → plan scheduling sheets
  - `more/analytics`, `more/analytics/compare`, `more/offline-maps`, `more/manual-logs`, `more/activity-backup`, `more/preferences`, `more/about`, `auth/*`

## Key Screens & `UiState` Contracts
- **Shared App State (`TrainingState` in `TrainingRepository.kt`):** Holds `initializing`, `account`, `localGarminOwner`, `localPlanOwner`, `activities`, `plans`, `workoutDrafts`, `wellness`, `details`, `busy`, `message`.
- **Today (`StrideLabTodayScreen.kt`, `TodayAdaptiveDashboard.kt`):**
  - Preserves top `All` / `• Running` segmented toggle (`TodayDisciplineTabs`), Monday–Sunday swipeable week strip (`today-week-swipe`) with solid vs. outlined day markers, and `TracksideFloatingNavigation` (`112.dp` bottom clearance).
  - Follows the 3-section finalized P0 layout on `TodayAdaptivePalette.bg` (`#0B0D0F`):
    1. `TodayDateContextHeader` + `TodayDailyTrainingCard` (`TodayOverviewCalculator.buildDailyTrainingState`) — verified `Planned`/`Completed` sessions (linked strictly via `status == "completed" || linkedActivityId != null`), unlinked `Recorded` activities, `RestDay`, or `NoPlanCompanion` (`Build a Running Plan`), plus offline `RecordedGpsRoute` hydration via `onLoadCachedDetail`.
    2. `TodayTrainingOverviewCard` (`TodayOverviewCalculator.computeWeeklyOverview`) — verified `Run km`, `Run hours` (`h:mm`), `Runs`, active non-running pills (`Strength`, `Calisthenics`, `Conditioning`), and conditional `Adherence` (omitted when no approved plan or 0 weekly sessions; never counts unlinked bonus runs).
    3. `TodayRecentActivitiesSection` — Option 1 compact `#14181C` list (`See all` → `activities`), deduplicated across imported records and native confirmed logs, sorted descending (`limit = 5`), with `48×32 dp` `TodayCompactGpsThumbnail` when GPS samples are cached.
  - Preserves `TodaySignedOutHero` when signed out with no local history and `TodayCompactEmptyDay` (`< 110.dp`) on past empty no-plan dates; 6 `360.dp` `@Preview`s live in `TodayAdaptiveDashboard.kt`.
- **Training (`StrideLabUnifiedTrainingTimeline.kt`):** Chronological planned + recorded activity timeline, draft approval dock (`TracksideParityDraftApprovalDock`), plan management (`TracksideManagePlan`), and weekly coach review (`TracksideWeekReview`, `Engine15CoachControls`).
- **Workout Detail (`WorkoutScreen.kt`, `TracksidePlannedRunningPreview.kt`, `TracksideRunningStepCards.kt`):**
  - Uses `Engine15SessionPresentation.forSession(plan, session.id)` for active targets (`ORIGINAL`, `ACCEPTED_TIME_ONLY`, `ACCEPTED_REST`, `ACCEPTED_PROGRESSION`).
  - Planned runs render in a dedicated Material3 `Scaffold` (`#0A0C0E`) with `WorkoutDetailTopBar`, 3-column symmetric hero metrics (`Planned distance`, `Estimated duration`, `Avg Pace`), unified `WorkoutProfileAndDifficultyCard`, synced `WORKOUT STEPS` timeline (`selectedStepIndex` hoisted in `WorkoutScreen`), optional `Workout recommendations` accordion, and a sticky `START WORKOUT` CTA (`workout-log-toggle`) that toggles the actual-log recording form.
  - Non-running guided sessions launch `WorkoutPlayer` (with `ExerciseAlternatives` easier/harder swaps and rest timer).
  - Previews: `WorkoutDetailIntervalPreview` and `WorkoutDetailSteadyStatePreview` (`360×800 dp`) in `TracksidePlannedRunningPreview.kt`.
- **Activity Detail & Analytics:** MapLibre GPS route (`TracksideGeographicMap.kt`), moving-time pace/HR charts, manual plan-session linking (`ActivityPlanLink.kt`), and multi-run comparison (`ActivityRunComparisonScreen.kt`).

## Deprecated & Removed Elements (Do Not Reintroduce)
- **Concentric Weekly TSS Rings & Elevated Telemetry Feed on Today (`All`):** Replaced by the verified P0 3-section layout (`TodayDailyTrainingCard`, `TodayTrainingOverviewCard`, Option 1 `TodayRecentActivitiesSection`) to avoid fabricated multisport TSS scores, fake workout-to-activity adherence guessing, and heavy scrolling cards.
- **Coach's Notes on Workout Detail:** Removed (`workout-planned-coach-notes`, `workout-planned-notes-toggle`, `notesExpanded`, and `coachingReason` parameter in `TracksidePlannedRunningPreview`). *Why:* Cluttered the workout execution screen and duplicated step effort cues.
- **Private Plan Feedback on Workout Detail:** Removed (`workout-private-feedback`, `privateFeedbackOpen`, and `PlanFeedbackForm` calls in `WorkoutScreen.kt`). *Why:* Feedback belongs on plan-level review flows, not inside individual workout execution.
- **Floating Bottom Nav Dock on Workout Detail:** Explicitly excluded for `workout/{planId}/{sessionId}` in `NativeMainActivity.kt` (`showFloatingDock`). *Why:* Keeps the sticky full-width `START WORKOUT` CTA cleanly anchored above `navigationBarsPadding()` with zero overlap.
- **Retired Library Tab & Intervals.icu Provider:** `nav-library` is permanently absent from the dock and More screen; legacy Intervals.icu config remains dormant on disk and must never trigger syncs.
