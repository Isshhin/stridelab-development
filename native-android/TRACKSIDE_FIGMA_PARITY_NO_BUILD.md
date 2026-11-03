# Trackside 3.0 — Figma parity reconstruction (NO BUILD)

**Status:** source-level implementation in progress; **NOT visually approved or 1:1 verified**.  
**Branch:** `design/trackside3-figma-parity-no-build-20261103` (separate from draft PR #36).  
**Source authority:** Figma approved master gallery page `551:2`, file `v73TsYo6gKxUoiUock2sSE`.  
**Hard hold:** The user explicitly instructed **do not compile the APK yet**. Do not merge into the auto-building Trackside feature branch, run Gradle, request GitHub Actions, package, or distribute a new installation until authorized.

## Implemented source work

| Batch | Approved reference | Source change | Acceptance state |
| --- | --- | --- | --- |
| A — Shared shell | `551:6`, `736:554` | `TracksideFloatingNavigation.kt`: 388×67 floating five-tab glass pill, 19dp Figma glyphs, highlighted active item, semantics and original navigation callbacks; `NativeMainActivity.kt` uses it instead of Material3 NavigationBar | Source complete; screenshots unverified |
| B — Today | `551:6`, `653:842` | `TracksideParityToday.kt` draws a premium atmospheric card with Figma isolated decorative art, original illustrative map panel, prescribed minutes and honest unknown HR/pace. `TracksideParityTodayActive.kt` renders persisted multi-session completion, without synthesized workouts. `TracksideToday.kt` delegates running hero | Source complete for primary states; exact font/map slot verification pending |
| C — Training | `614:500`, `614:618`, `614:797`, `614:915`, `795:839`, `795:675` | `TracksideParityTraining.kt` replaces generic Day/Agenda chips and workout cards with the two-option pill, approved 155dp run/232dp exercise-card minimums and read-only real prescriptions. Existing seven-day stacked chart retained. Draft review CTA now anchored as a dock via `TracksideParityDraftApprovalDock.kt` and user approval callback | Requires rendered Day/Agenda and 360dp parity checks; workout card microgeometry not yet pixel verified |
| D — Manage Plan | `736:554`, `736:640`, `736:785` | Revised activity distribution graph and action-card copy/geometry. Dedicated paused state in `TracksideParityPausedPlan.kt`. `TracksideParityMoveWorkoutSheet.kt` gives a seven-day chooser, destination-capacity preview, visible pending recovery caveat, and an explicit second confirmation invoking unchanged revision-gated `PlanActions.move` | Missed and review states implemented and routed via current-engine preflight; source-only, visual acceptance still pending |
| E — Activity | `551:235` | `TracksideParityActivity.kt` adds a full-width atmospheric map-first backdrop, a three-metric glass sheet, and functional Overview/Splits/Charts tabs. A route is drawn **only from recorded GPS samples**. Native source cache and activity intelligence remain intact | Atmospheric option staged, not pixel verified; alternate matte option `551:365` implemented with variant switch; screenshot acceptance pending |

## Source-only verification completed

- Inspected approved full-frame images for `551:6`, `614:500`, `736:554`, `551:235` and extracted their actual geometry.
- Imported Figma-isolated decorative elements into three Android `drawable-nodpi` files (Today atmospheric field, Today's clearly illustrative route panel, Activity decorative street-grid texture). This is source artwork for internal parity review only; any third-party Canva artwork rights/licensing must be confirmed before production distribution. Never present illustrative example GPS or demo HR/pace as actual values.
- Checked the 13 touched/added Kotlin source files for balanced brackets and missing `R.drawable` references (0 findings). **This is not a Kotlin compilation or rendered UI test.**
- Kept the coaching engines, saved-plan models, physical backups, existing athlete records and actual network APIs unchanged. One UI source fixture SHA-256 was updated to reflect the intentional presentation-only edit.
- No APK compilation, no automated device run and no CI workflow triggered by this non-building branch.

## Fidelity blockers and next acceptance gate

1. Obtain and verify final reusable source vectors/graphics and rights, rather than depending on flattened static previews, and match exact Inter/Roboto type metrics and spacing. No user font file should be exported.
2. Compare each approved frame with native render at **412×950** and **360×800**, including OS insets, safe areas, scrolling, selected/draft/active/empty/offline/multi-session states and controls; close all discrepancies rather than passing functional tests alone.
3. Finish strict parity for Manage missed/review flows and Activity matte variant if that variant is selected. Avoid fabricating source-dependent recovery or training-impact evidence.
4. Resolve the earlier Activity rule "map below primary details" against the later explicit Figma 1:1 approved atmospheric map-first design. The source branch currently follows the **later Figma map-first composition**, with actual route only when GPS exists; do not silently declare the UX conflict resolved.
5. Before any APK/build, obtain the user's explicit permission to lift the build hold, then run instrumentation, screenshot parity checks, responsive QA, seed-upgrade tests and a separate appropriately signed physical-phone backup-preserving update. Preserve draft PR #36.

**Do not claim 1:1 parity complete until an approved screenshot comparison and live Android verification exist.**


## 2026-11-03 visual rejection: corrected root causes, pending render verification

The user's **actual Android Studio screenshots** showed (1) a generic "Goals & ability" plan-builder page full of policy/frequency chips and the five-tab dock, and (2) an Activity sheet scrolled above its map, with a flattened metric/tabs hierarchy. **These are not the approved 1:1 design.** Functional green checks on an earlier branch do not imply visual approval.

### Exact corrected source routes

- The previous `PlanBuilder.kt` rendered old `Screen(...)` + `LinearProgressIndicator` + `FrequencyChips` / `ChoiceChips` for all steps. Those components caused the user's rejected screenshot. It now routes standard running-plan creation through native approved frames:
  - `551:580` Choose Disciplines — multi-select full cards, no checkboxes, no progress bar
  - `551:603` Running Goal Selection — five full-height goal cards
  - `551:796` Race / PR intent and compact distance selection when race is selected
  - `551:865` Current Running Ability — experience choices plus real-data weekly/longest mileage controls
  - `720:546` Plan dates and duration
  - `614:88` Shared availability and *explicitly preliminary* capacity checks
  - `614:412` Week Preview — proposed sessions are **not fabricated** before generation; afterward the saved draft is displayed using actual workout dates and a week pager, with manual approval
- The existing Engine 1.0 policy and detailed advanced configuration remain accessible as a legacy fallback; the visual work does **not** substitute Engine 2.0 or change the stored PlanRequest model.
- The five-tab bottom bar is now hidden on the builder route, matching Figma's fixed full-screen bottom CTA.
- The previous `activity/{id}` route put the map, details and tabs as sequential lazy-list items. Scrolling hid the map, creating the user's rejected Activity screenshot. The new `TracksideFigmaActivityDetailSurface.kt` renders a fixed 425dp map layer and an independently scrolling overlapping glass sheet at its approved initial snap. It preserves two approved variants (`551:235`, `551:365`), recorded splits/charts, data provenance and the link to planned runs.
- A Figma-isolated **illustrative route vector** is shown when GPS data is absent, explicitly labeled **NOT A GPS RECORDING**. When real points are available, only actual recorded samples are plotted, labeled as a schematic trace rather than a geographical tile map.

### Acceptance still pending

- This is **not a claim of 1:1 parity**. The user has not yet checked screenshots of these newest commits, and no Kotlin type-check, device launch, or screenshot comparison has run on the reconstruction branch.
- Advanced multi-discipline builder preferences still use preserved legacy controls; some plan-building states and activity highlight geometry are not exact.
- The approved running builder typography is Roboto (available on Android), while the atmospheric Activity frame uses Inter. Inter typography and all artwork rights need verification before any 1:1 sign-off.
- Staged `TracksideApprovedBuilderActivityUiTest.kt` tests goal-screen navigation and map-sheet composition. Existing responsive tests remain; tests have not been executed.
- Keep the design branch isolated from draft PR #36, production/athlete data, Engine 2.0 and any auto-building branch. No CI run / APK compilation was initiated for this design correction.

**Release gate:** Pull this branch and compare *newly rendered* Android screen captures against the exact approved Figma frames at 412×950 and 360×800; then close visible discrepancies. The user's earlier request to avoid further APK compilation by the assistant remains in effect.
