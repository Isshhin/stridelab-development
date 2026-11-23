---
scope: screens
tags: [stridelab, screens, navigation, ui-state, deprecations]
last_updated: 2026-11-23
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
  - `more/offline-maps`, `more/activity-backup`, `more/preferences`, `more/about`, `auth/*`
  - **More** shows the StrideLab account or Local Mode above Connections, Data & Maps, and App. A signed-out athlete can see “Not signed in · Saved on this device” on the account card and “Connected” on the Garmin Connect row at the same time. The standalone running analytics, run comparison, and strength and conditioning logging pages were retired.

## Key Screens & `UiState` Contracts
- **Shared App State (`TrainingState` in `TrainingRepository.kt`):** Holds `initializing`, `account`, `localGarminOwner`, `localPlanOwner`, `activities`, `plans`, `workoutDrafts`, `wellness`, `details`, `busy`, `message`.
- **Today (`StrideLabTodayScreen.kt`, `TodayAdaptiveDashboard.kt`):**
  - Preserves top `All` / `• Running` segmented toggle (`TodayDisciplineTabs`), Monday–Sunday swipeable week strip (`today-week-swipe`) with solid vs. outlined day markers, and `TracksideFloatingNavigation` (`112.dp` bottom clearance).
  - Follows the 3-section finalized P0 layout on `TodayAdaptivePalette.bg` (`#0B0D0F`):
    1. `TodayDateContextHeader` + `TodayDailyTrainingCard` (`TodayOverviewCalculator.buildDailyTrainingState`) — verified planned and completed sessions, imported activities, rest days, or Companion training mode. GPS previews load independently through the thumbnail provider.
    2. `TodayTrainingOverviewCard` (`TodayOverviewCalculator.computeWeeklyOverview`) — verified `Run km`, `Run hours` (`h:mm`), `Runs`, active non-running pills (`Strength`, `Calisthenics`, `Conditioning`), and conditional `Adherence` (omitted when no approved plan or 0 weekly sessions; never counts unlinked bonus runs).
    3. `TodayRecentActivitiesSection` — Option 1 compact `#14181C` list (`See all` → `activities`), deduplicated across imported records and native confirmed logs, sorted descending (`limit = 5`), with `48×32 dp` `TodayCompactGpsThumbnail` when GPS samples are cached.
  - The discipline bar, week strip and date header form the same shell during startup and in Local Mode. The card changes from loading to companion, scheduled or recorded content. Past empty no-plan dates show a compact status card.
  - A single recorded workout opens Activity Detail from the full card. The discipline, title and tabular metrics sit beside an 84×64 dp route preview.
  - Adds compact `.FIT` match confirmation cards on `All` and `Running`, plus the pending adaptation banner and review sheet on `All`; match and proposal actions go through `PlanActions`.
- **Training (`StrideLabUnifiedTrainingTimeline.kt`):** Chronological planned + recorded activity timeline, plan management, and weekly coach review. **Create another plan** is available with an active plan. Paused plans can still resume from plan tools.
  - The builder goes from **Configure** to **Preview** to **Activate** or **Discard preview**. Only activated plans enter Training. The six choices include Plyometrics and Isometrics as their own entries and Strength for Runners under Strength.
  - Separate discipline plans can run concurrently. The preview flags closely spaced hard efforts across separate plans. Running setup fills in an estimated typical week and the longest saved run in the last 30 days when enough distance data is available.
  - Shows `Coaching Diagnostics`, the pending adaptation diff sheet, pending activity matches, and a confirmation-gated `Undo Last Adaptation` action for the active plan.
- **Workout Detail (`WorkoutScreen.kt`, `TracksidePlannedRunningPreview.kt`, `TracksideRunningStepCards.kt`):**
  - Uses `Engine15SessionPresentation.forSession(plan, session.id)` for active targets (`ORIGINAL`, `ACCEPTED_TIME_ONLY`, `ACCEPTED_REST`, `ACCEPTED_PROGRESSION`).
  - Planned runs render in a dedicated Material3 `Scaffold` (`#0A0C0E`) with `WorkoutDetailTopBar`, 3-column symmetric hero metrics (`Planned distance`, `Estimated duration`, `Avg Pace`), unified `WorkoutProfileAndDifficultyCard`, synced `WORKOUT STEPS` timeline (`selectedStepIndex` hoisted in `WorkoutScreen`), optional `Workout recommendations` accordion, and a sticky `START WORKOUT` CTA (`workout-log-toggle`) that toggles the actual-log recording form.
  - Non-running guided sessions open the AMOLED `WorkoutPlayer` with segmented progress, audio mute, and a single exit sheet. Work views cover reps and load, plyometric contacts, holds, and conditioning rounds. Rest pins the next target above the bottom edge, with +15s, Skip Rest, and edit or undo controls for the previous set.
  - EMOM advances the work/rest clock at interval boundaries. AMRAP offers confirmation of the finished round and its actual reps. Expiry by itself never records a performed set. Review collects session effort and opens Coach's Debrief after confirmed logs are saved.
  - Work and rest keep the screen awake while foregrounded. Timer tones briefly duck audio, and haptics follow the same cue transitions. Returning to the workout resumes its saved execution checkpoint.
  - Previews: `WorkoutDetailIntervalPreview` and `WorkoutDetailSteadyStatePreview` (`360×800 dp`) in `TracksidePlannedRunningPreview.kt`.
  - Linked running executions show an `Execution Verdict` above the planned steps and expected-versus-observed step rows below; null HR/decoupling fields remain omitted.
  - **Export to Watch (.FIT)** opens a confirmation and the Android save picker. The resulting file can be copied to `/GARMIN/NewFiles/`. Reviewed time-only workouts export the current duration, and accepted progressions export the current overall target.
- **Activity Detail:** MapLibre GPS route (`TracksideGeographicMap.kt`), moving-time pace/HR charts, and manual plan-session linking (`ActivityPlanLink.kt`).
  - The route map and Today previews draw chronological moving fixes as one trail across timer pauses and timestamp gaps. They discard invalid zero coordinates and keep antimeridian cuts. Source telemetry retains its recorded timing.
  - **Workout structure** appears for linked structured targets or FIT laps mapped to named work or a work/recovery pattern. Steady auto-laps stay in Splits. Observed pace and planned targets are labeled separately.
  - Analysis opens with one **Coach's Debrief** when the recording supports it. The lead explains the run in context. Up to two highlighted observations and further coaching paragraphs connect available pacing, effort, terrain, intervals and stride evidence. Completed cross-training workouts use the same card, including stationary activities with an exact workout-log link. Sparse evidence leaves no placeholder.
  - Analysis shows **Training Effect** and **Running Dynamics** when evidence is available. Stride length and vertical ratio identify estimates when derived. The **How Did It Feel?** editor was removed from Analysis. Linked feedback remains on Workout Detail.
  - The detected workout row opens a correction sheet. The former How It Went section is removed. Coaching statistics exclude stop and dropout values without changing the original chart samples.
- **Offline Maps:** Panning or pinching the picker owns the touch. The shared store tracks download progress while the screen is closed. Unknown totals show a preparing state, and stalls or SDK errors show Retry on the existing saved area.

## Deprecated & Removed Elements (Do Not Reintroduce)
- **Concentric Weekly TSS Rings & Elevated Telemetry Feed on Today (`All`):** Replaced by the verified P0 3-section layout (`TodayDailyTrainingCard`, `TodayTrainingOverviewCard`, Option 1 `TodayRecentActivitiesSection`) to avoid fabricated multisport TSS scores, fake workout-to-activity adherence guessing, and heavy scrolling cards.
- **Coach's Notes on Workout Detail:** Removed (`workout-planned-coach-notes`, `workout-planned-notes-toggle`, `notesExpanded`, and `coachingReason` parameter in `TracksidePlannedRunningPreview`). *Why:* Cluttered the workout execution screen and duplicated step effort cues.
- **Private Plan Feedback on Workout Detail:** Removed (`workout-private-feedback`, `privateFeedbackOpen`, and `PlanFeedbackForm` calls in `WorkoutScreen.kt`). *Why:* Feedback belongs on plan-level review flows, not inside individual workout execution.
- **Floating Bottom Nav Dock on Workout Detail:** Explicitly excluded for `workout/{planId}/{sessionId}` in `NativeMainActivity.kt` (`showFloatingDock`). *Why:* Keeps the sticky full-width `START WORKOUT` CTA cleanly anchored above `navigationBarsPadding()` with zero overlap.
- **Retired Library Tab & Intervals.icu Provider:** `nav-library` is permanently absent from the dock and More screen; legacy Intervals.icu config remains dormant on disk and must never trigger syncs.
