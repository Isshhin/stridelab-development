---
title: "Screens Map & Navigation Routes"
area: ui
status: verified
last_verified_commit: "bef05a7c"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabTodayScreen.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TodayAdaptiveDashboard.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TodayDisciplineDashboard.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUnifiedTrainingTimeline.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksideMoreScreen.kt"
tags: [stridelab, ui, screens, navigation, routes]
---

# Screens Map & Navigation Routes

All navigation routes live under `NativeMainActivity.kt` (`NavHost`) reading immutable `TrainingState` (`TrainingRepository`) and dispatching through `PlanActions`, `NativeActions`, and `FreeformAccess`.

## 1. Primary Tab Roots & Center Quick-Log (`showFloatingDock == true`)
- **`home` → Today (`StrideLabTodayScreen.kt`, `TodayAdaptiveDashboard.kt`, `TodayDisciplineDashboard.kt`):**
  - Top discipline filter chips scope the Monday–Sunday strip (`TodayWeekCalendar`), selected-day training card, and adherence across all approved plans (`All`, `Running`, `Strength`, `Calisthenics`, `Conditioning`).
  - Uses an AMOLED layout on `All`: `TodayDateContextHeader` + full-card tappable `TodayDailyTrainingCard` (Planned / Recorded / ConfirmedLog / RestDay / Companion CTA with `strideLabPressFeedback`, no `"View Workout"` button, plus `180 ms` neutral skeletons), centered 3-column `TodayTrainingOverviewCard` (`Run km`, `Run hours`, `Runs` + non-running pills), multi-discipline `28-DAY CONSISTENCY` heatmap (`ConsistencyHeatmapStrip`, splitting days with multiple disciplines into color-coded segments + dynamic discipline legend), and hairline-separated `TodayRecentActivitiesSection` (with `TodayCachedGpsThumbnail`, press highlight, zero redundant circular shoe icons, and `See all` opening the `Activity` tab).
  - Surfaces pending `.FIT` auto-match cards (`All` / `Running`) and controlled adaptation review banners (`All`); `Running` omits the standalone Coaching Diagnostics card and per-discipline Consistency card.
- **`training` → Training (`StrideLabUnifiedTrainingTimeline.kt`, `TracksideParityTraining.kt`):**
  - Focused on forward-looking plans, calendar-first weekly scheduling (`training-week-prev` / `training-next`, centered `"Return to Today"` / `"Show week"` pill separated by `StrideLabHairline`), custom schedules, visual-first `AthleteDiagnosticsCard` / `Engine15CoachReviewCard` (5-segment fatigue bar + 7-day unavailability strip), and `Engine15ScheduleReview` inside `Manage plan`. When no active training plan exists, the top `"Training"` header renders a high-contrast lime `"+ Create plan"` CTA on the right (`training-create-plan-header`). `"Workout recommendations"` UI is removed from `TracksideManagePlan.kt`, `WorkoutScreen.kt`, and `TracksideParityPausedPlan.kt`. The header `Add Workout` button is removed so the center `[ + ]` Quick-Log sheet is the single primary logger.
- **Center `[ + ]` Quick-Log (`QuickLogSheet.kt`):**
  - Opens a `#121212` `ModalBottomSheet` (`quick-log-sheet`) directly over any primary tab: conditional `In progress` drafts (resume / `48.dp` discard), a direct universal `"Start empty workout"` CTA (`quick-log-start-empty`, no upfront discipline category picker and no Running), and direct shortcuts to `Workout templates` & Workouts hub.
- **`activities` → Activity (`ActivityHistoryTabScreen.kt`):**
  - Primary read-only history tab backed by `WorkoutEvidence.kt` (`projectWorkoutEvidence`) unifying Garmin / `.FIT`, Coached, Custom, and Freeform completions with a StrideLab AMOLED capsule discipline filter strip (`34.dp` visual pill inside `>= 48.dp` touch target) and flat `#000000` hairline-separated `ActivityHistoryRow` items (no boxed cards) showing `Mon | Nov 23` (`EEE | MMM d`) date headers, real source origin labels (`GARMIN`, `FIT`, `HEALTH CONNECT` — never `"IMPORTED"`), `TodayCachedGpsThumbnail` route previews, and 3-column tabular metric grids (`DISTANCE | AVG PACE | AVG HR` for runs; duration/sets/discipline/HR for non-runs) with fluid transition and return-to-origin back navigation (`openWorkout`, `openActivity`, `freeform/new|edit`, and `manage` all pop cleanly back to the origin tab).
- **`settings` → More (`TracksideMoreScreen.kt`, `MoreFeatureUi.kt`):**
  - Account card (`AccountProfilePresentation.resolve`) displays user name or email (or `"StrideLab Account"` fallback, JWT claim extraction when needed) — never raw UUIDs as the primary title. When signed out, displays `"Not signed in · Saved on this device"` independently alongside Garmin Connect status.
  - Hosts Garmin Connection, `more/offline-maps`, `more/activity-backup`, `more/preferences`, `more/about`, and `auth/*`.

## 2. Full-Screen Detail & Workflow Routes (`showFloatingDock == false`)
- **`workout/{planId}/{sessionId}` (`WorkoutScreen.kt`):** Planned running detail (profile bar chart, `-2..+2` difficulty selector, `WORKOUT STEPS`, FIT watch export) or guided non-running [[Guided-Workout-Player-and-Debrief]] (`CoachedSetTable` default for Strength & Calisthenics with `ExerciseInfoSheet` triggers, collapsible `SessionMuscleFocusSection`, and scoped `WorkoutPlayer` hold-timer handoff; `WorkoutPlayer` default for timer-heavy disciplines).
- **`activity/{id}` & `activities` (`TracksideActivityDetailSurface.kt`, `TracksideStationaryActivityDetail.kt`):** MapLibre GPS route, moving-time telemetry charts (`Overview | Analysis | Splits | Charts`), Coach's Debrief, Training Effect, manual plan linking (plus confirmed-only `SessionMuscleFocusSection` when linked to a completed stationary workout), and Share launcher.
- **Share Studio Modal (`ShareStudioScreen.kt`):** Dialog hosted from Activity Detail. See [[Share-Studio]].
- **`builder/{discipline}` & `blueprint-workout/{planId}/{sessionId}/{generatedAt}/{loginGeneration}`:** Multi-step Engine 1.5 / Hybrid Plan Builder (`PlanBuilder.kt`, `TracksideBuilderScreens.kt`) and read-only Blueprint workout preview (`BlueprintWorkout.kt`, including `SessionMuscleFocusSection` for planned non-running workouts).
- **`freeform/*` (`FreeformScreens.kt`, `FreeformEditor.kt`, `WorkoutTemplateScreens.kt`, `CustomPlannerScreens.kt`):** Workouts hub, `freeform/new/{discipline}`, `freeform/edit/{id}`, `freeform/history/{kind}/{id}`, template editor, and custom scheduler. See [[Freeform-Logger]] and [[Templates-and-Custom-Planner]].
- **Schedule Management Sheets:** `manage/{planId}/{date}`, `move/{planId}/{sessionId}`, `review-move/...`, `missed/...`.

## 3. Retired Elements (Do Not Reintroduce)
- **`freeform/evidence` & `training-workout-history`:** Retired standalone `Workout history` screen; data stays unified in [[Progress-and-History]].
- **Concentric Weekly TSS Rings & Elevated Telemetry Feed on Today (`All`):** Replaced by verified 3-section `Today` overview.
- **Legacy Library Tab (`nav-library`), Standalone Running Analytics/Comparison Pages & Intervals.icu Sync:** Permanently retired. See [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]].

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[UI-Design-System]] · [[Freeform-Logger]] · [[Share-Studio]] · [[Hard-Guardrails]] · [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]]
