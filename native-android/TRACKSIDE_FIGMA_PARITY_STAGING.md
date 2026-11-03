# Trackside 3.0 — Figma 1:1 parity staging work

**State:** Uncompiled and unverified source reconstruction. **Do not build an APK, dispatch Gradle, open a PR, or merge** until the user lifts the current build hold. This independent staging branch exists because the existing PR #36 branch auto-builds on push.

**Staging branch:** `feature/trackside3-figma-parity-staging-20261103`. Parent: `0bbc0fdb325e61138904f0ae218698b9ac3ce7ca`. Production coaching engines and existing persistent-record contracts are unchanged.

## Approved master-gallery mapping

| Area | Figma master-gallery node(s) | Staged native surface |
|---|---|---|
| A. Floating navigation, shell | `551:6`, `736:554` | `TracksideParitySurfaces.kt` (`TracksideFloatingNavigation`), `TracksideComponents.kt`, `NativeMainActivity.kt` |
| B. Today premium / active | `551:6`, `653:842` | `TracksideParitySurfaces.kt` (`TracksideTodayHero`, `TracksideTodayActiveSummary`) |
| C. Training Day/Agenda draft + active, compact | `614:500`, `614:618`, `614:797`, `614:915`, `795:839`, `795:675` | `TracksideFigmaTraining.kt`, `TrainingScreens.kt`, `TracksideTrainingCalendar.kt` |
| D. Manage Plan and state variants | `736:554`, `736:640`, `736:785`, `736:839`, `736:870` | `TracksideManagePlan.kt`, `TracksideFigmaManageStates.kt` |
| E. Activity atmospheric overview | `551:235`; matte alternative `551:365` | `TracksideFigmaActivity.kt`, `RecordedActivityDetail.kt`, `NativeMainActivity.kt` |

## Original Figma assets (NOT screenshots of composed pages)

* `drawable-nodpi/trackside_approved_atmosphere.png` — original artwork node `551:15`, exported at original 378×160.
* `drawable-nodpi/trackside_approved_map_art.png` — original decorative street-grid art `551:33`, approximately 412×269.
* `drawable-nodpi/trackside_approved_route_halo.png` — original approved illustrative visual route halo `551:50`, approximately 139×214. **Never represent this as GPS collected from a user.**
* `drawable-nodpi/trackside_activity_street_grid.png` — approved atmospheric Activity artwork `551:237`, 412×950. No fabricated recorded route.

Resources were exported from the user's connected approved Figma file rather than substituted with icon/stock graphics. They are internal private-alpha design assets; verify any Canva-source distribution rights before public release.

## Source-level scope

* A: 67dp, approximately 388dp floating custom bottom navigation at 412dp, five accessible root tabs and no hidden labels at 1.3× system font. Today/Training use source-specific heading hierarchy.
* B: approved premium running glass hero, atmospheric red field, 232dp decorative map slot, 91dp three-metric strip and real planned-duration values; missing HR and pace targets display an explicit em dash. Active-plan Today state has data-driven completed session counts and progress.
* C: 162×44 Day/Agenda switch, active versus draft entry points, prescribed-minute stacked chart, 360px seven-day layout, source workout cards and draft approval UI. No workout segment durations invented.
* D: fixed manage action-card hierarchy, weekly overview, paused/draft/uncompleted state surfaces, advanced archive behind progressive disclosure. Existing approval/pause/archive operations remain in native PlanActions.
* E: atmospheric source map texture, recorded distance/moving-time/pace triplet, operational Overview/Splits/Charts navigation and genuine GPS trace below primary metrics. Missing training-effect/streams remain unavailable; no inferred values.

## Acceptance gates (NOT YET PASSED)

- [ ] No further Kotlin/native source changes until scoped static inspection of imports, braces, callback types, SHA fixture and new packaged resource paths.
- [ ] Once the user explicitly lifts the compilation hold: **compile only**, then resolve any Kotlin/Gradle errors; do not assume these unchecked source edits compile.
- [ ] Render and compare Figma vs Android screenshots at 412×950 and 360×800 for the full screen, including status bar/insets, typefaces, nav, icon geometry, card height, padding, glows, route overlay and state behavior.
- [ ] Compare active/draft/paused/missed, day/agenda, and Activity Overview/Splits/Charts separately, not one screenshot per feature.
- [ ] Restore exactly approved licensed font assets where available. Current app uses Android SansSerif/Roboto, which may not be a pixel-identical substitute for Figma Inter.
- [ ] Audit responsive accessibility, dynamic data, touch targets and TalkBack.
- [ ] User visual acceptance before merging/release. Existing CI success from PR #36 is not evidence this staging branch works or is 1:1.

**Important:** Functional improvements in this branch are intentionally isolated from release, Engine 1.5, Engine 2.0, any signed APK updates, and persisted athlete history.
