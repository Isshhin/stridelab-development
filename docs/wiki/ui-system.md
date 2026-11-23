---
scope: ui-system
tags: [stridelab, compose, design-system, amoled, components]
last_updated: 2026-11-23
---

# UI Design System (Jetpack Compose)

Back to [[index]] · Related: [[screens]] · [[architecture]]

## Dark AMOLED Theme & Color Tokens
Defined across `StrideTheme` (`NativeMainActivity.kt`), `WorkoutDetailPalette` (`TracksideRunningStepCards.kt`), `TracksidePalette` (`TracksideUi.kt`), and `StrideLabVisualPalette` (`StrideLabVisualKit.kt`):

| Token | Hex | Usage |
| :--- | :--- | :--- |
| Deep Background | `#0B0D0F` / `#0A0C0E` | Today warm near-black canvas (`TodayAdaptivePalette.bg`) / Workout Detail canvas (`Training`/`More` remain `#000000`) |
| Card Surface | `#14181C` / `#161B20` | Elevated Today cards (`TodayDailyTrainingCard`, `TodayTrainingOverviewCard`, `TodayRecentActivitiesSection`) & Workout Detail cards |
| Raised Control | `#1C2229` | Compact pill buttons (`− Easier`, `+ Harder`) and timeline dot rings |
| Border / Hairline | `#232A31` / `#1E242B` | Standard `1.dp` card & pill borders (`#1E242B` for internal hairline dividers) |
| Sparing Lime-Yellow | `#D8FB70` | Reserved strictly for active top segment (`All`/`• Running`), selected day circle, active bottom nav pill (`Today`), primary CTAs (`START WORKOUT`, `View Workout`, `View Analysis`, `See all`), and running GPS routes (zero decorative lime card borders) |
| Discipline Accents | `#D8FB70` / `#A855F7` / `#2DD4BF` / `#FB923C` | Running / Strength / Calisthenics / Conditioning pills, icons, and session badges |
| Warm-up / Easy / Recovery | `#4AD3DE` / `#45D4C8` / `#4ADBA8` | Warm-up, Recovery, Easy, and Cool-down bars, dots, and zone pills |
| Threshold / Work Amber | `#F6D32D` / `#E0B416` | Work bars, `RepeatGroup` header gradient, `Z4 · Threshold / Hard` pills |
| Text Hierarchy | `#F5F7FA` / `#9BA4B0` / `#8B9298` | Primary ink / secondary metrics & subtitles / muted uppercase headers (`THIS WEEK`) |

## Typography & `360.dp` Responsive Guards
- **Typeface:** System `FontFamily.SansSerif` (Roboto) via `StrideTheme`.
- **Hierarchy:** Screen titles `27–29.sp` Bold; hero metric values `20.sp` Bold; micro-headers `10–11.sp` SemiBold/Bold uppercase (`letterSpacing = 1.0–1.2.sp`).
- **Clipping Prevention:** All 3-column hero metrics, zone pills, difficulty controls, and Today 3-column/multisport load legends enforce `maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Ellipsis`, and `Modifier.weight(1f)` for `360.dp` widths.

### Freeform Workout Logger

- The Training workout hub uses the AMOLED palette and one primary empty-workout action. Drafts and the routine library have distinct rows. Workout backup is a secondary action.
- Most disciplines use compact actual-entry rows with a shared metric header. Calisthenics retains extra room for hold, rep, and load inputs. Previous confirmed work and template targets have separate labels. Save draft and Finish workout share a single footer.

- The scheduling screen uses the same cards and footer as the logger. Calendar dots indicate scheduled or logged work, and source labels distinguish entries in the selected day. Coached chart totals use coached prescriptions.

- Workout history uses two segments, History and Progress. Source filters and labels stay readable without color. Frequency bars show their counts in text, and exercise comparisons label the dates of recorded sets.
- Baseline suggestions sit below the editable running fields. A use button applies one distance, and coverage text explains which finished weeks contributed to the weekly average.

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
