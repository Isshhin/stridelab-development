---
title: "Screens Map & Navigation Routes"
area: ui
status: verified
last_verified_commit: "36a5eeb8"
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
  - Uses a 3-section AMOLED layout: `TodayDateContextHeader` + `TodayDailyTrainingCard` (Planned / Recorded / RestDay / Companion CTA + `180 ms` neutral skeletons), centered 3-column `TodayTrainingOverviewCard` (`Run km`, `Run hours`, `Runs` + non-running pills), and hairline-separated `TodayRecentActivitiesSection` (with `TodayCachedGpsThumbnail`, zero redundant circular shoe icons, and `See all` opening the `Activity` tab).
  - Surfaces pending `.FIT` auto-match cards (`All` / `Running`) and controlled adaptation review banners (`All`).
- **`training` → Training (`StrideLabUnifiedTrainingTimeline.kt`, `TracksideParityTraining.kt`):**
  - Focused on forward-looking plans, calendar-first weekly scheduling (`training-week-prev` / `training-week-next`, centered `"Return to Today"` / `"Show week"` pill separated by `StrideLabHairline`), custom schedules, Coaching Diagnostics, and athlete-approved plan adaptations.
- **Center `[ + ]` Quick-Log (`QuickLogSheet.kt`):**
  - Opens a `#121212` `ModalBottomSheet` (`quick-log-sheet`) directly over any primary tab: conditional `In progress` drafts (resume / `48.dp` discard), 2×3 discipline empty-workout grid (`freeform/new/{discipline}` with lazy no-ghost-draft persistence), and direct shortcuts to `Workout templates` & Workouts hub.
- **`activities` → Activity (`ActivityHistoryTabScreen.kt`):**
  - Primary read-only history tab backed by `WorkoutEvidence.kt` (`projectWorkoutEvidence`) unifying Garmin / `.FIT`, Coached, Custom, and Freeform completions with horizontal discipline filter chips (`>= 48.dp`), tabular numerals, `TodayCachedGpsThumbnail` route previews for imported runs, and direct tap routing to `activity/{id}`, `workout/{planId}/{sessionId}`, or `freeform/history/{kind}/{id}` (signed-out capable per [[ADR-03-Offline-First-Independent-Local-Ownership]]).
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
