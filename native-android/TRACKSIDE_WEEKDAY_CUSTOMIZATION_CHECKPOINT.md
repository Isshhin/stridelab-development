# StrideLab Trackside 3.0 — user-controlled weekly placement checkpoint

Updated 2026-11-03. Branch: `design/trackside3-figma-parity-no-build-20261103`, checkpoint commit before this note: `6c4fb8c008d300c1a20c65968101a6ebbf8b8748`.

## User problem and required behavior

The earlier screenshot showed six "placed" runs but the weekday cells did not really let the athlete choose which days to train. That was deceptive. The user explicitly requires customizable scheduling for **every discipline**, not just an E/Q/L visual or a calendar of automatic suggestions.

## Committed source changes

- `WeeklyPlacement.kt` supports exact indexed weekday slots per discipline (`run`, `str`, `cal`, `cond`), including assigning, moving, swapping, clearing, resizing, and reset-to-automatic. Index 0 of running is long, next `qualityCount` slots are quality, and subsequent slots are easy. Zero is an intentionally *unassigned* temporary value and cannot pass engine validation.
- `EngineContracts.kt` serializes optional `weeklySlots` through `PlanRequest.encode/decode`. Empty map preserves legacy automatic scheduling and saved plans. `preferredQuality` / `allowedQualityTypes` are saved advisory preferences and **not yet enforced as workout style prescriptions**.
- `Engine1.kt` and `MultiSessionEngine1.kt` honor actual fixed dates through their existing template IDs. Unachievable recovery/spacing or daily-budget combinations fail with an explanation; the engine does not silently move user-pinned sessions. `Engine1Engines.kt` keeps coordinated alternate prescription variants from incorrectly inheriting pinned slots.
- `TracksideFigmaRunningSetup.kt`: select **Long run**, **Quality**, or a numbered **Easy** session, then tap the target weekday. Occupied weekdays swap sessions. Supports Saturday and the user's own preferred rest day; a new plan's Saturday-unavailable default is *not a permanent lock*. Continue is blocked when a manually selected session remains unassigned.
- `TracksideFigmaOtherDisciplineSlots.kt` and `TracksideFigmaBuilderSchedule.kt`: identical day assignment for Strength, Calisthenics and Conditioning; independent session steppers; optional automatic placement per discipline; explicit compatible two-a-day choice; provisional capacity and incompatible-pair checks. The engine's existing same-day compatible pairing is easy running + calisthenics only.
- `PlanBuilder.kt`: connects all controls to saved request state, removes pinned slots and stale goals when disciplines are switched off, avoids inheriting incompatible pinned sessions when opening a new single-discipline builder, synchronizes weekly volume from athlete controls, reports generation failures on the review screen, and **waits for confirmed approval** before leaving it.
- `TracksideFigmaWeekReview.kt`: shows requested custom days before generation, real generated sessions after generation, and groups seven-day plan weeks starting at the **actual user-selected date**, not always Monday (which previously hid sessions for midweek start dates).

## Source-only tests staged, not executed

- `WeeklyPlacementTest.kt`: JSON roundtrip, legacy request compatibility, swaps, Saturday choice, incomplete/duplicate rejection, exact weekday preservation across weeks, recovery conflict rejection, strength/calisthenics assignments, compatible/incompatible two-a-day pairings, switching to auto mode, and Wednesday-start week grouping.
- `TracksideApprovedBuilderActivityUiTest.kt`: Compose interactions for choosing/swapping run days and assigning distinct strength/cali weekdays, plus prior builder, availability, activity tests.
- The user previously requested **no APK compilation**. No Gradle build, Kotlin type-check, emulator test execution, screenshot matching, or APK packaging was run by the assistant for this branch. Tests are written but not green.

## Remaining high-priority work — do NOT mark complete

1. Run appropriate Kotlin compilation and Android instrumentation only **after the user permits** builds; fix any actual compiler/runtime failures rather than claiming source-only checks establish correctness.
2. Compare rendered 412×950 and 360×800 previews against the approved Figma Master Gallery. Source changes improve interactions but **pixel parity is not established**.
3. The no-running Step 1 route still falls back to the older generic goals/ability chips. Figma provides approved Strength Goal `687:3` and Strength Setup `698:3`; these require a native visual/functional implementation. Dedicated Calisthenics/Conditioning flows need similar treatment; Conditioning page `724:2` is FOR REVIEW, not signed off.
4. The current Engine 1 policy does **not** enforce every training style or strength split choice. Do not label PPL, muscle emphasis, target finish time, PR history, target race date, or quality-type whitelists as supported coaching decisions until an engine actually implements and verifies them. Keep Engine 2.0 separate until its integration contract is ready.
5. Maintain athlete-data compatibility, explicit approval, offline persistence and rollback. PR #36 remains draft and must not be merged just because code was committed.

## Next session entry

Read this document, pull the exact branch head, inspect `PlanBuilder.kt`, `WeeklyPlacement.kt`, both schedulers and the two test suites. Verify compiler and UI only after the user's build hold is lifted. Continue the real multi-discipline Figma parity work and preserve the distinction between editable preferences and enforced engine rules.

## 2026-11-03 continuation: native non-running goals and safe restored states

- Commits `a5ca091`, `af23cc9`, `3b77f13` and `b7a2d50` on the same no-build design branch implement confirmation-driven removal of occupied training days, native cross-training goal/ability routes for Strength/Calisthenics/Conditioning, and source regression coverage. Running-only setup remains intact.
- The user can now explicitly make a previously pinned weekday unavailable; its occupied manual slots become unassigned rather than silently changing dates, and generation stays disabled until resolved. This updates the selected weekday set without changing the workout count.
- The Strength Goal screen follows Figma `687:3` in Compose with four approved visual choices. Engine 1 uses only `General strength`, `Muscle development` and `Strength endurance`; Physique Development and Maintain Fitness are mapped to existing prescriptions with a disclosure. Muscle emphasis and training splits are not claimed as implemented coaching behavior. Figma's provisional goal artwork is not yet final.
- Multi-discipline plans now visit native cross-training goals after running setup. They no longer skip the selected secondary discipline's goal and ability edits.
- The legacy chip-based four-stage builder fallback has been replaced with a full-screen recovery route if a saved draft disappears or a step fails to restore. Inputs remain available for a new review rather than being silently discarded.
- Existing Compose preview calls remain source-compatible with the new availability callback. No compiler, emulator, screenshots, UI tests, Gradle or APK build was run. These are **source-level changes only**; they require explicit permission before the build hold is lifted.
- Remaining: source-level parity for Strength Setup `698:3` (equipment, goal link, split presentation without falsely promising engine enforcement), Calisthenics scheduling detail, visual comparison at 412×950 and 360×800, backed-by-device state checks, and final icon/font assets.
