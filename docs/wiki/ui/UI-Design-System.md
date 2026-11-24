---
title: "UI Design System (AMOLED Compose)"
area: ui
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUiPrimitives.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabVisualKit.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksideUi.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksidePlannedRunningPreview.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksideRunningStepCards.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksideFloatingNavigation.kt"
tags: [stridelab, ui, compose, design-system, amoled, accessibility]
---

# UI Design System (AMOLED Compose)

StrideLab uses a pure AMOLED black design system (`StrideLabSurfaceTokens` in `StrideLabUiPrimitives.kt`) emphasizing typographic hierarchy, hairline separation over nested bordered boxes, tabular numerals, and restrained lime-yellow accents.

## Core Surface & Color Tokens (`StrideLabSurfaceTokens`)
| Token | Hex | Usage |
| :--- | :--- | :--- |
| **AMOLED Canvas** | `#000000` | Primary background across Today, Training, Workouts Hub, Freeform Editor, Templates, Share Studio header/stage, and More |
| **Elevated Surface** | `#121212` | Selective elevation for active set rows, modal bottom sheets, Today's hero training card, Share Studio bottom tray, and anchored footers |
| **Subtle Surface** | `#181A1D` | Compact input fields, discipline/template tiles, pill chips |
| **Hairline Divider** | `#1F242B` | `1.dp` `StrideLabHairline` horizontal dividers replacing nested card borders |
| **Lime Accent** | `#D8FB70` | Reserved strictly for active tab/tool states, primary CTAs (`#080A08` text), confirmed checkmarks, active rest timer cues, adherence bars, and GPS trails |
| **Discipline Accents** | `#D8FB70` / `#A855F7` / `#2DD4BF` / `#FB923C` | Running / Strength / Calisthenics / Conditioning markers |
| **Intensity Accents** | `#4AD3DE` / `#45D4C8` / `#F6D32D` | Warm-up & recovery teal / easy teal / threshold & work interval amber |
| **Text Hierarchy** | `#F5F7FA` / `#A0A8B3` / `#6B737E` | Primary ink / secondary labels & metrics / muted captions & headers |

## Typography, Spacing & Touch Discipline
- **Tabular Numerals:** `TabularTextStyle` / `.tabular()` applies `fontFeatureSettings = "tnum"` across weekly metrics, calendar dates, Freeform set grids, rest countdowns, Share Studio counters, and exported share cards.
- **8-Point Grid & Flat Primitives:** Standard spacing tokens (`4`, `8`, `12`, `16`, `24.dp`), `StrideLabSectionHeader`, `StrideLabHairline`, and `StrideLabFlatRow`.
- **Accessibility:** Primary actions, list rows, chip filters, and icon buttons enforce `>= 48.dp` (`minTouchTarget`), tested on `320 dp`, `360 dp`, and `412 dp` viewports up to `1.3x` font scale.

## Zero-Recomposition Neutral Skeletons (`StrideLabUiPrimitives.kt`)
- `neutralSkeletonBrush` draws animated neutral shimmer (`#16181B` / `#23272E` — never lime-tinted) inside `drawWithCache` + `onDrawBehind` to avoid state-driven recomposition loops.
- Gated behind `rememberDelayedSkeletonVisible` (`180 ms` cold-start delay) so cached reads never flash placeholders, and switches cleanly to a static fill when `ANIMATOR_DURATION_SCALE == 0f`.
- Exposes `SkeletonText`, `SkeletonMetric`, `SkeletonWorkoutRow`, `SkeletonExerciseRow`, `SkeletonCalendar`, and `SkeletonActivityRow`.

## Shared Visualization Components
- **Today Route Thumbnails (`ActivityThumbnailCache.kt`, `TodayStartup.kt`):** Synchronously checks the 6 MB in-memory `LruCache` on frame 1 before touching disk cache, eliminating tab-switch flicker.
- **Workout Profile & Step Timeline (`TracksidePlannedRunningPreview.kt`, `TracksideRunningStepCards.kt`):** Pure-Compose proportional bar chart (`flattenProfileBars` + `-2..+2` difficulty selector) synced with `SingleStep` / `RepeatGroup` cards.
- **Floating Navigation Capsule (`TracksideFloatingNavigation.kt`):** `68.dp` pill hosting `Today`, `Training`, and `More`; hidden automatically on full-screen detail, editor, and studio flows.
- **Anatomical Muscle Map (`AnatomicalMuscleMap.kt`, `ExerciseInfoSheet.kt`):** Pure-Compose side-by-side `FRONT` / `BACK` vector silhouettes using `#181A1D` / `#1F242B` base geometry and the unified `#D8FB70` lime luminance scale (`0.28f..0.95f` alpha), responsive legend stacking below `340 dp`, and a `#121212` `ExerciseInfoSheet` modal. See [[Anatomical-Muscle-Map]].

## Related Nodes
- [[00-StrideLab-Index]] · [[Screens-Map]] · [[Freeform-Logger]] · [[Share-Studio]] · [[Hard-Guardrails]] · [[App-Architecture]]
