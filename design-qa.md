# Trackside 3.0: Strength Setup — rendered Design QA

**Current acceptance status (2026-11-04): ACCEPTED for Strength Setup only.** Exact-head Windows CI and emulator responsive verification passed at `a6b0fd0c0e34b68376dae6f72ef34de1db436c6a` (run `41770631185`, artifact `13295156293`). See iteration 3 below. The blocked statuses in iterations 1 and 2 are preserved as historical findings.

**Source of truth:** Figma file `v73TsYo6gKxUoiUock2sSE`, node `698:3` (APPROVED Strength Setup Customize 412×950).
**Android baseline:** GitHub Actions run `41665277059`, commit `0dee06b676a0aca8545843429357f0b7cfe0e1b5`.
**Screenshot artifact IDs:** `13245703652` (412dp/100%), `13246567602` (360dp/100%), `13246123658` (412dp/150%), `13246972320` (360dp/130%).
**Screenshot paths:** `qa-screenshots/figma-412dp-font100/StrideLabBatchB/builder-strength-placed-412dp-font100.png`, `qa-screenshots/figma-360dp-font100/StrideLabBatchB/builder-strength-setup-360dp-font100.png`; within the corresponding artifact ZIPs.
**Normalization:** Figma frame is 412×950 px at 1×. Android screen 824×1900 px at 2× (412×950 dp). Compare at 412×950 after 50% resampling; Android Compose root captures do not draw simulated system status/navigation bars. Compare app content, excluding unmatched system chrome. Figma is the manual-placed state, whereas previous Android `builder-strength-setup` is automatic and `builder-strength-placed` includes expanded schedule editor. This state mismatch precludes precision pixel-diff claims until a matched capture is made.

## Findings from genuine emulator renders (iteration 1)

- **P1 — Preference card subtitles clipped at 130% and 150% font scale.** The visible second lines run below fixed 59dp cards. Target: dynamic minimum card height with measured content and padding, preserving 59dp at normal font.
- **P2 — Mode selector label clipped at scaled fonts.** `Let StrideLab decide` is truncated at 130% and 150%. Target: multiline mode chip with intrinsic height while retaining the single-line 100% appearance.
- **P2 — Non-matching Figma preference icons.** The approved goal, emphasis and gear icons were approximated with text glyphs. Target: exact vector paths from Figma `698:74`, `698:81`, `698:90`.
- **P2 — Weekly preview/card proportions remain taller than the 412×950 design.** Need a matched-state screenshot before changing the card's footer and day cells; preserve touch target size.
- **State alignment gap:** The previously captured placed screenshot had its editor expanded and scrolled. Add an exact manual placed state screenshot with Monday Upper, Thursday Lower, Saturday unavailable and the editor closed. Sample equipment and unsupported muscle emphasis text may legitimately differ because the current Engine 1 request lacks the Figma demonstration fields.

## Iteration 1 change and verification status

The pending source update changes only Strength Setup's fixed-height rows, selector wrapping, preference icon assets and the responsive QA screen capture. It does not change the coaching engine, plans, assignments or the Figma source.
**Required rerender:** CI at the new exact commit must produce four updated screenshot sets; inspect `builder-strength-figma-reference` at 100% and both accessibility scales, compare to Figma at matching crop/state, and recheck P0/P1/P2.
**Focus regions:** header/body vertical rhythm, mode chips, three preference card icons/text, weekly day cells/footer and CTA. Figma static assets are 23×23 SVG paths, rendered in 37dp icon tiles.
**Final result: blocked.** Acceptance is not possible until the new build and screenshot comparison pass. Do not mark the screen 1:1.


## Iteration 2 — exact-head 9bd611d rendered comparison

- **CI:** UI foundation/JVM `41669665861` passed, emulator smoke `41669666148` passed. Four-profile responsive `41669666065`: 412dp/100% passed 10/10; 360dp/100% passed 10/10; 412dp/150% passed 10/10; 360dp/130% failed 1/10 (`approvedBuilderStrengthGoalAndSetupRenderAtCurrentViewport`: Continue remained disabled after the second weekday tap).
- **Post-fix PNG artifacts:** `13249106309` (412dp/100%), `13247863330` (360dp/100%), `13248306750` (412dp/150%), `13247848309` (360dp/130%).
- **Matched-state PNG:** `qa-screenshots/figma-412dp-font100/StrideLabBatchB/builder-strength-figma-reference-412dp-font100.png` (412dp/100%, manual Monday UB, Thursday LB, Saturday unavailable, editor closed). Figma source `698:3` is 412×950. The Android image is 824×1900 @2x and was downsampled to 412×950 for review.
- **Source vs rendered layout (Figma node 698:3):** Figma preference cards are 59px high (y296–355, y363–422, y429–487); Android 412dp/100% cards occupy roughly y309–381, y387–460, y466–539, approximately 73px. The subsequent weekly card starts around y553 (Figma y501), measures approximately 277px (Figma 237px) and pushes the split toward the fixed CTA.
- **P1:** Training split partially hidden behind the fixed Continue bar at 412dp. **P2:** Preference card heights, gap before the weekly card, and weekly footer/layout collectively exceed the Figma rhythm. **P2:** At 360dp/130% the existing interaction test still cannot complete both manual placements; exact root cause pending staged tap-state diagnostics.
- **Improved:** All three Figma vector icons now render correctly. At 412dp/150%, subtitles and the wrapped `Let StrideLab decide` label are readable in the screenshot.
- **Root cause isolated for visual geometry:** `StrengthPreferenceRow` vertical padding was 10dp instead of a content-fitted minimum 59dp card; the pre-weekly gap was 14dp and weekly card had a 13dp top + 13dp bottom padding plus an extra 13dp gap before its 48dp editor control. The next patch removes excess non-interactive space without shrinking day cells, steppers or edit-button touch targets.
- **Interaction debugging:** The failing test already used `performScrollTo()`; therefore the next run captures a screenshot immediately after both placement taps and verifies `1 of 2 placed` then `2 of 2 placed` to pinpoint whether a tap/state transition is lost.
- **Iteration 2 corrected render:** pending exact-head build and screenshot rerun. **Final result: blocked.**

## Iteration 3 — Windows self-hosted acceptance (2026-11-04)

**Accepted verified build:** `a6b0fd0c0e34b68376dae6f72ef34de1db436c6a`, self-hosted Windows run [41770631185](https://github.com/Isshhin/stridelab-development/actions/runs/41770631185), artifact ID `13295156293` (`trackside-strength-windows-evidence`, 21-day retention). The last Strength Setup production code change remains `f9b882cadae0d3a5c307057a4769340b8024d763`; later commits only updated the Windows workflow and responsive instrumentation. No engine or release infrastructure was changed.

### Exact-head verification results

- Shared exercise-library schema check: passed (the unrelated UI protected-source immutability guard was intentionally excluded from Trackside QA).
- Android JVM/unit tests, debug Android app compilation, and debug instrumentation APK compilation: passed.
- Android 35 Pixel 9 x86_64 emulator smoke: **22/22 passed**.
- Responsive Compose device suites: **412dp/100% 10/10 passed; 360dp/100% 10/10 passed; 360dp/130% 10/10 passed; 412dp/150% 10/10 passed**.
- Screenshots: **22 PNGs per profile**, including the matched Figma reference, an after-first-tap diagnostic, a two-tap diagnostic, placed and auto modes. All uploaded successfully.
- Primary matched screenshot: `qa-screenshots/figma-412dp-font100/StrideLabBatchB/builder-strength-figma-reference-412dp-font100.png`. Downsample 824×1900 by 50% for comparison to Figma node `698:3` (412×950).
- Scaled placement diagnostics: `qa-screenshots/compact-360dp-font130/StrideLabBatchB/builder-strength-after-first-tap-360dp-font130.png` and `builder-strength-two-taps-360dp-font130.png` in the same folder.

### Rendered visual review against approved Figma 698:3

- **P1 resolved:** At 412dp/100%, the complete TRAINING SPLIT module and all three split choices appear above the fixed Continue area. There is no CTA obstruction of this module.
- **P2 resolved to a close visual match:** Preference cards are approximately 59dp high at baseline, rather than the earlier ~73dp; weekly preview is approximately 243dp (Figma ~237px), a ~6dp difference, rather than the earlier ~277dp. The pre-weekly space is small and consistent, without pushing Training Split under the action area.
- All approved goal, muscle-emphasis and equipment vector icons remain present at the expected 23dp within their 37dp tiles. Mode chips, weekday cells, stepper and editor targets retain the readable baseline appearance and intended dimensions.
- Text clipping checks and height assertions passed in the responsive test matrix. The 412dp/150% and 360dp/130% screens use their scrollable body for below-fold sections; the Continue action remains fixed and reachable.
- The Android screenshot excludes simulated status/navigation chrome and uses live supported Engine 1 values rather than unsupported Figma demo fields: the muscle-emphasis subtitle is `Not available in Engine 1`, and equipment names reflect the request. These intentional semantic differences are not visual P1/P2 defects. This is close layout parity, **not a claim of a perfect pixel-by-pixel match**.

### 360dp/130% interaction root cause and verification

- Previous failing test clicked `builder-strength-weekday-1` while that weekday cell was **below the scroll viewport**; before-tap screenshots independently confirmed the cell was offscreen. The first placement did not occur, so `1 of 2 placed` was absent. That is an instrumentation interaction defect, not evidence that persisted assignment or Continue logic is incorrect.
- Corrected test commit `bccf4ba8d246e4624bca9f4b1ff0e20b116190bb` performs `performScrollTo()`, `assertIsDisplayed()`, and then `performClick()`, capturing evidence before asserting the placement count. It retains the second weekday scroll, `2 of 2 placed` assertion, and Continue enablement assertion. Workflow trigger update `a6b0fd0c0e34b68376dae6f72ef34de1db436c6a`.
- The successful 360dp/130% screenshots explicitly show Upper Body after tap 1, Upper Body and Lower Body after tap 2, `2 of 2 placed`, and the enabled red Continue CTA. All ten tests passed with no weakening of placement checks.

**Final Strength Setup outcome: ACCEPTED.** Exact-head compilation, JVM/unit, smoke, 4-profile responsive interaction, screenshots, and visual P1/P2 checks pass. No actionable P0/P1/P2 issues remain within the requested Strength Setup acceptance scope. Calisthenics Setup is the next screen to verify; this acceptance does not approve the full Plan Builder, Trackside or a release APK.


## Calisthenics Setup - accepted Windows verification (2026-11-04)

**Status: ACCEPTED for Calisthenics Setup only.** Approved Figma source: file `v73TsYo6gKxUoiUock2sSE`, node `614:335` (412 × 950). Android source: `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideFigmaCalisthenicsSetup.kt`.

**Verified exact head:** `5389cdbe00fdf83f59288fce38044fc56ff52a0c`. Windows self-hosted run [41773842247](https://github.com/Isshhin/stridelab-development/actions/runs/41773842247) completed successfully. Screenshot and instrumentation artifact ID: `13295619254` (`trackside-strength-windows-evidence`). The workflow keeps its existing name for now, but covers Calisthenics Setup and Strength regression checks.

### CI and responsive result

- Exercise-library shared schema validation, Android JVM/unit tests, debug app compilation and instrumentation APK compilation: **passed**.
- Pixel 9 Android API 35 x86_64 emulator smoke: **22/22 passed**.
- Responsive instrumentation: **412dp/100% 11/11; 360dp/100% 11/11; 360dp/130% 11/11; 412dp/150% 11/11**.
- **23 PNG screenshots per profile**, collected on the actual emulator and retained with Android instrumentation reports. No production release APK generated.
- Primary matched-state screenshot: `qa-screenshots/figma-412dp-font100/StrideLabBatchB/builder-calisthenics-figma-reference-412dp-font100.png`. Other profiles use the same basename in `figma-360dp-font100`, `compact-360dp-font130`, and `standard-412dp-font150`.
- Figma/Android comparison: reference is 412 × 950 at 1×; Android screenshot is 824 × 1900 at 2×, visually normalized to 412 × 950. Android root screenshot excludes simulated system status/navigation chrome; compare application content rather than unmatched system bars.

### Rendered acceptance review

- Matched the approved manual example: Wednesday Push, Friday Pull, Saturday unavailable, Wednesday edit-session controls, Starting Levels below the weekly card, and a fixed Continue action.
- At 412dp/100%, the weekly card measures approximately **467dp**, close to Figma's **470px**; Starting Levels is approximately **90dp**, close to Figma's **86px**. Both were oversized in the preceding render. The mode selector and weekly card start positions closely match the reference.
- Approved card hierarchy, weekday selection cells, session chips, stepper sizes, editable schedule content, and action buttons remain intact. The labels now show **Suggested days · editable** and **Remove**, matching the Figma source.
- At 130% and 150% fonts, **Let StrideLab decide** wraps inside its selector. Both text-bound assertions pass. The body scrolls to Starting Levels at reduced viewport heights without losing the fixed Continue action.
- The previously inserted explanatory paragraph below Pin/Remove was not in the approved 470px card. It was removed from that visible region; the existing coaching-support advisory on focus selection remains, so unsupported Engine 1 focus enforcement is not misrepresented.
- The responsive suite also verifies the manual placed-state label and enabled Continue. The prior accepted Strength Setup regression tests remain green in the same run.
- The fixed CTA's absolute vertical placement differs from Figma because the Android root screenshot does not render the simulated system chrome. Dynamic request data and Engine 1-supported controls may also differ from demonstration copy. These do not create an actionable P0/P1/P2 defect.

### Changes and scope

- `702f559e3ddfcbf8f784e2625d66c60e98f801f9`: added matched Figma state and screenshot capture across all four profiles.
- `f7763f1c237e49275b2c97cb204c2bc2d0b002ab`: added accessible multiline mode labels.
- `5cf249c85e0453861dec94000463cf9c41085625`: added mode-label clipping assertions.
- `5b27af37c5aa54be442006c020a3df0dfe056c8d`: restored Figma weekly/Starting Levels proportions and source labels while retaining advisory behavior.
- `5389cdbe00fdf83f59288fce38044fc56ff52a0c`: tightened normal-font height and approved-label regression checks.

**Acceptance result:** Calisthenics Setup is complete for this Trackside 3.0 screen. No actionable P0/P1/P2 issues remain within its reviewed scope. This is close rendered parity, not a perfect pixel-by-pixel identity. Strength Setup acceptance remains unchanged. Engine 1.5 and Engine 2.0 were not modified or integrated. The next acceptance target is the remaining Plan Builder screens and review states, not Engine 1.5 integration.


## Plan Builder: Discipline Selection — accepted rendered QA (2026-11-04)

**Status: ACCEPTED for the discipline-selection screen only.** Approved Figma reference: `v73TsYo6gKxUoiUock2sSE`, node `551:580` (412×950). Verified production implementation at `c37084c7c31230f2d0961721dca24b5cf03a4294`, with an additional reachability-only instrumentation change at `5aa0094b165c28d54bee8aba68e2315a8633c84b`.

**Windows self-hosted evidence:** successful run [41775319723](https://github.com/Isshhin/stridelab-development/actions/runs/41775319723) attempt 2, artifact `13303820362`; final reachability run [41791067053](https://github.com/Isshhin/stridelab-development/actions/runs/41791067053), artifact `13303228531` (`trackside-strength-windows-evidence`). Both checked out their exact commit SHA. The final run passed JVM/unit tests, debug app compilation, debug instrumentation compilation, 22/22 API 35 Pixel 9 emulator smoke tests, and **12/12 responsive tests** at each of 412dp/100%, 360dp/100%, 360dp/130%, and 412dp/150%. **25 PNGs per profile** were uploaded.

**Primary visual reference:** `qa-screenshots/figma-412dp-font100/StrideLabBatchB/builder-disciplines-figma-reference-412dp-font100.png`. Compared the actual 824×1900 emulator-root screenshot normalized to 412×950 against approved Figma node `551:580`. Content is vertically shifted relative to the Figma mock status bar, as expected, but card positions, 87dp heights, 12dp gaps, selected Running/Strength colors, and four distinctive 24dp icons now match closely. The new vectors are faithful to Figma nodes `571:6`, `571:24`, `571:39`, and `571:60`, with no generic icon substitutes. The selected count reads `2 of 4` and Continue remains enabled.

**Accessibility:** At 360dp/130%, the fourth Conditioning card extends below the fold in the initial viewport. The scroll-reachability test at `5aa0094` invokes `performScrollTo()`, asserts the card displays, and captures `qa-screenshots/compact-360dp-font130/StrideLabBatchB/builder-disciplines-last-card-reachable-360dp-font130.png`. The actual after-scroll screenshot displays the complete Conditioning name and description above the fixed footer; the count and Continue button remain visible. All four responsive profiles passed. Card hit areas were preserved and the accepted Strength and Calisthenics screen checks remained green.

**Outcome:** No actionable P0/P1/P2 issue remains in Discipline Selection. This is close rendered Figma parity, not pixel-for-pixel identity. Next acceptance target: Running Goal Selection (Figma `551:603`); later Plan Builder and draft-review screens are not yet accepted. No Engine 1.5/2.0 integration or release APK changes.
