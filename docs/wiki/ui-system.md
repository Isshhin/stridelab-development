---
scope: ui-system
tags: [stridelab, compose, design-system, amoled, components]
last_updated: 2026-11-24
---

# UI Design System (Jetpack Compose)

Back to [[index]] · Related: [[screens]] · [[architecture]]

## Dark AMOLED Theme & Color Tokens
Defined across `StrideLabSurfaceTokens` (`StrideLabUiPrimitives.kt`), `StrideTheme` (`NativeMainActivity.kt`), `WorkoutDetailPalette` (`TracksideRunningStepCards.kt`), `TracksidePalette` (`TracksideUi.kt`), `TodayAdaptivePalette` (`TodayAdaptiveDashboard.kt`), and `StrideLabVisualPalette` (`StrideLabVisualKit.kt`):

| Token | Hex | Usage |
| :--- | :--- | :--- |
| AMOLED Canvas | `#000000` | Primary background across Today, Training, Workouts Hub, Freeform Editor, Templates, and More |
| Elevated Surface | `#121212` | Selective elevation for active set rows, modal bottom sheets, Today's hero card, and sticky footers |
| Subtle Surface | `#181A1D` | Compact input fields, discipline sheet tiles, and subtle chips |
| Hairline Divider | `#1F242B` | `1.dp` horizontal dividers (`StrideLabHairline`) replacing nested bordered cards |
| Sparing Lime-Yellow | `#D8FB70` | Reserved strictly for active tabs/selections, primary CTAs, completed set checkmarks, active rest timer cues, adherence progress, and GPS trails |
| Discipline Accents | `#D8FB70` / `#A855F7` / `#2DD4BF` / `#FB923C` | Running / Strength / Calisthenics / Conditioning bar/dot markers |
| Warm-up / Easy / Recovery | `#4AD3DE` / `#45D4C8` / `#4ADBA8` | Warm-up, Recovery, Easy, and Cool-down bars, dots, and zone pills |
| Threshold / Work Amber | `#F6D32D` / `#E0B416` | Work bars, `RepeatGroup` header gradient, `Z4 · Threshold / Hard` pills |
| Text Hierarchy | `#F5F7FA` / `#A0A8B3` / `#6B737E` | Primary ink / secondary labels & metrics / muted captions & table headers |

## Shared UI Primitives, Tabular Numerals & Skeletons (`StrideLabUiPrimitives.kt`)
- **Typography & Touch Targets:** System `SansSerif` via `StrideTheme`, tabular numerals (`TabularTextStyle` / `fontFeatureSettings = "tnum"`) on numeric metrics, calendar numbers, set rows, and countdowns, 8-point spacing rhythm, and `48.dp` minimum touch targets on primary actions and icon buttons.
- **Flat List Primitives:** `StrideLabHairline`, `StrideLabSectionHeader`, and `StrideLabFlatRow` replace stacked bordered panels across hubs, timelines, Today metrics, and recent activities.
- **Zero-Recomposition Neutral Skeletons:** `neutralSkeletonBrush` uses `drawWithCache` + `onDrawBehind` (neutral `#16181B`/`#23272E`, never lime-tinted), gated by `rememberDelayedSkeletonVisible` (`180 ms` delay) and static when `ANIMATOR_DURATION_SCALE == 0f`: `SkeletonText`, `SkeletonMetric`, `SkeletonWorkoutRow`, `SkeletonExerciseRow`, `SkeletonCalendar`, `SkeletonActivityRow`.

### Freeform Workout Logger

- **Workouts Hub:** `Start empty workout` opens a `#121212` `ModalBottomSheet` 2×3 discipline grid so `Workout templates` remains visible above the fold. `In progress` draft rows expose a one-tap trailing `48.dp` discard icon.
- **Flattened Logger:** Empty starts open to `+ Add exercise` plus quick-add chips for recent same-discipline exercises (never auto-inserting movements). Each exercise renders directly on `#000000` separated by `StrideLabHairline` with a compact tabular `Set | Prev | metrics | ✓` grid, `#121212` active-set focus, dimmed completed rows with `#D8FB70` checkmarks, and an unobtrusive non-blocking `FreeformRestTimerBar` above the footer.
- **Custom Scheduling & Baseline Suggestions:** Scheduling and templates share the flat hairline layout and bottom-sheet discipline picker. `HistoryBaselineCard` provides confirmed weekly/longest run distance suggestions inside the Running builder.

## Shared Components
- **Today P0 Components (`TodayAdaptiveDashboard.kt`, `StrideLabTodayScreen.kt`):**
  - `TodayWeekCalendar`: Non-color-dependent state markers (solid lime dot = completed activity; outlined `1.2.dp` ring = scheduled/pending workout).
  - `TodayDateContextHeader`: Dynamic date label + `"Return to Today"` pill when viewing past/future dates.
  - `TodayDailyTrainingCard`: Unified `#14181C` daily session card (`Today's Training` / `<Weekday>'s Training`) handling `Planned` (steady Zone 2 lime bars for easy/long runs vs. stepped `flattenProfileBars` only for structured quality runs, or `TodayCompactGpsThumbnail` when linked & completed), unlinked `Recorded`/`ConfirmedLog`, `RestDay`, and `NoPlanCompanion` (`Build a Running Plan` CTA).
  - `TodayTrainingOverviewCard`: 3-column weekly running totals (`Run km`, `Run hours` `h:mm`, `Runs`), active-only non-running discipline pills, and conditional verified `Adherence` progress bar (no fabricated multisport TSS).
  - `TodayRecentActivitiesSection`: Option 1 compact rows inside a single `#14181C` container with `1.dp` `#1E242B` dividers, `Canvas` discipline icon badges, `See all`, and `48×32 dp` `TodayCompactGpsThumbnail` for runs with cached coordinates.
- **`TracksideFloatingNavigation` (`TracksideFloatingNavigation.kt`):** Persistent 3-tab floating capsule (`Today`, `Training`, `More`, `68.dp` height, `36.dp` corner radius) with animated lime indicator; hidden on detail/builder/workout routes (`showFloatingDock` in `NativeMainActivity.kt`).
- **Training Calendar:** Seven weighted columns with two-letter day labels keep Sunday inside the viewport. The same chart accepts a single plan or the filtered plans on the Training tab.
- **Hybrid Blueprint:** The track chips keep the selected week when switching prescriptions. Calendar notes show pairing order and support dose changes before activation.
- **Hybrid Schedule:** The training tracks summary uses the same dark card as Plan Length and Weekly Availability. Its count includes Strength, Plyometrics and Isometrics separately. The optional daily limit is available directly in the card.
- **`WorkoutProfileAndDifficultyCard` (`TracksidePlannedRunningPreview.kt`):**
  - Blueprint drafts reuse the running metrics, chart and step list without execution or editing controls. Workout unavailable states use the Workout Detail foreground and muted text tokens for contrast.
  - Pure-Compose `Row` bar chart (no external chart library): bar width proportional to `durationMinutes.coerceAtLeast(1f)`, height `fillMaxHeight(intensityFraction)`, vertical gradient fill + `3.dp` top sheen.
  - Flattens repeat intervals (`flattenProfileBars`) into alternating Work/Recover bars mapped to parent `stepIndex`.
  - Unselected bars dim to `alpha = 0.35f` (`animateFloatAsState`) when `selectedStepIndex != null`.
  - Bottom of the same card embeds single-row difficulty controls (`Coached`/`Harder (+1)`, `− Easier`, `+ Harder`, `Reset`) + single-line `Coached target:` delta summary (`Engine15WorkoutDifficultyAdjuster.changeSummary`).
- **`TracksideRunningStepCards` (`TracksideRunningStepCards.kt`):**
  - Sealed `WorkoutStepItem` (`SingleStep`, `RepeatGroup`) rendered alongside a continuous left vertical timeline rail (`TimelineRailIndicator`) with color-coded dots synced to `selectedStepIndex`.
  - `SingleStep`: `Row(Modifier.height(IntrinsicSize.Min))` with `4.dp` full-height left accent bar, bold title, right-aligned zone pill, and metric/effort lines.
  - `RepeatGroup`: Amber gradient header (`N × Interval Set` + `N REPS` badge) over stacked `Work`/`Recover` sub-cards.
  - **Why `groupDisplayMixedSteps` has `<= 1 m` tolerance:** `Engine15WorkoutDifficultyAdjuster` distributes integer meters across timed steps. Otherwise identical reps, including strides, stay grouped. Different pace or HR targets keep separate rows. Grouping only affects presentation.
- **Activity Telemetry Charts (`ActivityChartMovingTimeAxis.kt`, `ActivityChartDisplayScale.kt`):** Render continuous source-measured lines on a pause-compressed moving-time x-axis.
