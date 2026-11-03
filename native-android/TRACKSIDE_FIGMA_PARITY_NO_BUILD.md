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
| D — Manage Plan | `736:554`, `736:640`, `736:785` | Revised activity distribution graph and action-card copy/geometry. Dedicated paused state in `TracksideParityPausedPlan.kt`. `TracksideParityMoveWorkoutSheet.kt` gives a seven-day chooser, destination-capacity preview, visible pending recovery caveat, and an explicit second confirmation invoking unchanged revision-gated `PlanActions.move` | Core states coded; `736:839` missed decision and `736:870` coaching-checks review are **not yet fully matched**; no automatic recovery approval |
| E — Activity | `551:235` | `TracksideParityActivity.kt` adds a full-width atmospheric map-first backdrop, a three-metric glass sheet, and functional Overview/Splits/Charts tabs. A route is drawn **only from recorded GPS samples**. Native source cache and activity intelligence remain intact | Atmospheric option staged, not pixel verified; alternative approved matte option `551:365` unimplemented |

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
