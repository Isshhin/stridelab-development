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


## Plan Builder: Running Goal Selection — accepted rendered QA (2026-11-04)

**Status: ACCEPTED for Running Goal Selection only.** Approved Figma source: file `v73TsYo6gKxUoiUock2sSE`, node `551:603` (412 × 950). Exact verified branch head `52f74d89911e683497741a40e91eb768595148af`. Windows API 35 self-hosted run [41792675823](https://github.com/Isshhin/stridelab-development/actions/runs/41792675823), artifact `13304457851` (`trackside-strength-windows-evidence`).

### Build and four-profile device evidence

- Shared exercise-library schema validation, JVM/unit tests, debug Android compilation and instrumentation compilation: passed.
- Pixel 9 Android API 35 x86_64 emulator smoke: **22/22 passed**.
- Responsive instrumentation: **412dp/100% 13/13, 360dp/100% 13/13, 360dp/130% 13/13, 412dp/150% 13/13**.
- **27 screenshots per profile**, uploaded with instrumentation reports.
- Primary matched screenshot: `qa-screenshots/figma-412dp-font100/StrideLabBatchB/builder-running-goal-figma-reference-412dp-font100.png`. Scrolled accessibility evidence: `qa-screenshots/compact-360dp-font130/StrideLabBatchB/builder-running-goal-last-card-reachable-360dp-font130.png` and analogous files in other profile folders.

### Figma 551:603 rendered comparison

- The actual 824×1900 emulator root was normalized to 412×950 and compared with approved Figma reference. Differences in status/nav-system chrome are excluded from parity.
- The five editable goal vectors now match Figma's race flag, faster runner, endurance chart, fitness heartbeat, and custom sliders. Their approved 25dp paths and selected/default treatment are preserved in Android VectorDrawables, not substituted with generic icons.
- The selected race card has the correct emphasis without an additional checkmark. Card sizing, gaps, copy hierarchy, and action area are close to Figma at 412dp/100%.
- At 360dp/130% and 412dp/150%, content expands vertically without truncating the titles or subtitles; the final `Train your way` card is fully reachable through the scrollable body, and Continue remains fixed.
- The test verifies all five card/icon nodes, baseline card placement, final-card scroll reachability, changing the selected goal to `faster`, and Continue enablement.
- No actionable P0/P1/P2 issue was observed in the rendered accepted scope. This is close rendered parity rather than pixel-by-pixel identity.

**Result: ACCEPTED.** This does not approve Race Goal Selection, Running Ability, other Plan Builder stages, or Engine 1.5 integration. Next target: Race & Performance (Figma `551:796`). No Engine 1.5/Engine 2.0 or release APK infrastructure change.


## Plan Builder: Race & Performance — accepted rendered QA (2026-11-04)

**Status: ACCEPTED for rendered Race Goal layout, not unsupported coaching inputs.** Approved Figma source: file `v73TsYo6gKxUoiUock2sSE`, node `551:796` (412×950). Current production correction `9a924f9e6fd60d0a0b6051e76d58625698002d2f`; additional regression test `559422fd2301850256c50a566f74cc45dbc9bf78`.

**Final exact-head Windows QA:** successful [run 41795914728](https://github.com/Isshhin/stridelab-development/actions/runs/41795914728), artifact `13305623206` (`trackside-strength-windows-evidence`). Shared schema validation, JVM/unit checks, debug Android and instrumentation compilation all passed. Pixel 9 API 35 x86_64 emulator smoke **22/22 passed**. Four responsive suites: **412dp/100% 14/14**, **360dp/100% 14/14**, **360dp/130% 14/14**, and **412dp/150% 14/14**. **29 screenshots per profile** uploaded.

### Screenshot comparison and issue resolution

- Figma reference: four 68px single-choice cards, compact 10K distance selector, optional PR panel, target-day field directly above fixed Continue. Matched reference PNG: `qa-screenshots/figma-412dp-font100/StrideLabBatchB/builder-race-goal-figma-reference-412dp-font100.png`. At normal width the Android emulator captured an 824×1900 root normalized to 412×950 for review; simulated Figma status/nav chrome is excluded.
- Initial screenshot at `f61c83c` passed 22/22 smoke and 14/14 responsive tests in all four profiles, but revealed a **P1**: the target-day field sat behind fixed Continue at 412dp/100%. Source-only and prior automated tests had missed the overlap. Artifact `13304968110` preserves that historical finding.
- Corrected source `9a924f9` allows an independent smaller post-description gap for Race Goal only (the shared Builder default for accepted screens remains 40dp). It also shortens the pre-distance gap and noninteractive PR-panel spacing while keeping Add PR's 44dp tap target. The corrected first card starts approximately at y258 in the normalized capture, matching Figma's y257. The optional PR panel is close to Figma's 116px height, with the target-day field fully above the fixed CTA.
- The P1 is now **resolved** in the final screenshot and is enforced by a direct 412dp/100% baseline assertion that both the PR panel remains within tolerance and `builder-race-target-day` ends above `builder-figma-continue`, rather than relying on a green interaction-only test.
- All four approved goal icons (trophy, timer, finish flag, benchmark), selected-state check, calendar and chevron use the exported editable Figma vector geometry. Distance choice, current-PR label, and date field are legible and proportioned closely to Figma.
- At 360dp and increased fonts, the scrollable page reveals the bottom target-day field; screenshots named `builder-race-goal-target-day-reachable-<profile>.png` demonstrate this while Continue stays fixed. Tests verify changing 10K to 21K, the current PR label update, and disabled Continue for unsupported `time` intent. Re-selecting `pr` restores Continue.
- Known semantic limitations of the current Engine 1 contract: Add PR and target-date actions display an explicit no-persistence advisory, and target-time coaching cannot proceed. Neither data field is silently accepted. These are **not** claims of full feature equivalence with the Figma mock.

**Result: ACCEPTED for Race Goal rendered and currently supported interactions.** No remaining actionable P0/P1/P2 issue in the reviewed scope. Strength, Calisthenics, Discipline Selection, and Running Goal acceptance stays intact; Engine 1.5 and Engine 2.0 were not changed or integrated. Next: Running Ability, Figma `551:865`.


## Plan Builder: Running Ability — accepted rendered QA (2026-11-04)

**Status: ACCEPTED for Running Ability and currently supported interactions.** Approved Figma file `v73TsYo6gKxUoiUock2sSE`, node `551:865` (412×950). The verified branch head is `bb5b1b0da0da4665ca8053115ec7882af77c86f6`. Final successful [Windows self-hosted run 41801617810](https://github.com/Isshhin/stridelab-development/actions/runs/41801617810), artifact `13309140047` (`trackside-strength-windows-evidence`).

### Verification and rendered evidence

- Shared schema validation, JVM/unit, debug Android application compilation and instrumentation compilation **passed**. API 35 Pixel 9 emulator smoke **22/22**. Responsive instrumentation **412dp/100% 15/15; 360dp/100% 15/15; 360dp/130% 15/15; 412dp/150% 15/15**.
- **31 PNGs per responsive profile** plus instrumented test results. Reference screenshot: `qa-screenshots/figma-412dp-font100/StrideLabBatchB/builder-running-ability-figma-reference-412dp-font100.png`. Scrolled interaction screenshots: `qa-screenshots/<profile>/StrideLabBatchB/builder-running-ability-optional-reachable-<profile>.png`.
- The test loads the Figma reference state (**Intermediate**, **30 km/week**, **14 km longest run**), verifies the experience choices, both sliders and the optional performance panel, and keeps Continue enabled.
- It also changes experience, sets weekly and longest-run slider values through the native semantics actions, checks the results, and verifies that Add result explains the current engine's lack of performance-result persistence instead of silently saving a value.

### Issues, corrections and acceptance

- The first real screenshot (`ad8eaa3`; failed run [41797753153](https://github.com/Isshhin/stridelab-development/actions/runs/41797753153), artifact `13306607305`) revealed **P1:** the optional Recent Performance panel was partly behind Continue at 412dp/100%. Both metric cards were also too tall. A direct overlap assertion failed, while the other responsive profiles passed.
- The first Ability-only correction `94783ec` adjusted card insets and spacing while retaining Material 3 slider interactions. Regression updates `ebdf670` added both metric-card height bounds and optional-panel height bounds; run [41799672198](https://github.com/Isshhin/stridelab-development/actions/runs/41799672198), artifact `13307012807`, passed all 22 smoke and all four 15-test profiles. The resulting screenshot resolved P1 but still showed oversized Material3 slider tracks and small cumulative vertical drift (P2).
- The final visual correction `7392e50` used Figma-specific 5dp tracks and 15dp circular thumbs through Material3's accessible Slider API, plus an Ability-only header position/line-height and body-spacing adjustment. Regression `f9b1c6a` added 412dp baseline card-position tolerances. The first new compile correctly failed on Material3 experimental API opt-in, which was fixed locally at `c8b64bc` without reverting behavior.
- CI trigger update `bb5b1b0` ensures future edits to Trackside Builder source, Running Setup source and Figma drawable assets trigger the Windows verifier; the old Ubuntu-only workflows are not a substitute while GitHub-hosted execution is unavailable.
- The **final actual 412dp screenshot** closely matches Figma `551:865`: eyebrow/headline, experience selection, 127dp metric cards, slider tracks/thumbs, and optional result panel geometry are within the enforced baseline tolerances. The optional panel is fully above fixed Continue, rather than obscured. Header and card rhythm are close, not perfectly pixel identical.
- **360dp/130% and 412dp/150%** scrolled screenshots show the complete Recent Performance panel and Add result button, with content readable and accessible before the fixed Continue button. No meaningful clipping or obstruction remains in the reviewed state.
- Retained the Advanced training settings entry for customization, even though the approved illustrative frame does not display this optional link. Current Engine 1 does not persist race results; the advisory stays explicit. This acceptance is not an Engine 1.5 integration decision.

**Result: ACCEPTED.** P1 overlap and actionable P2 geometry issues closed. Previously accepted Strength Setup, Calisthenics Setup, Discipline Selection, Running Goal and Race Goal remain unchanged and pass the same exact-head regression suite. No release APK generated; no Engine 1.5 or Engine 2.0 modifications. Next Plan Builder target: **Running Weekly Setup**, approved Figma node `548:95`; the current Android implementation requires structural reconstruction against its compact weekly-card and pinned/suggested layout.


## Plan Builder: Running Weekly Setup — accepted rendered QA (2026-11-04)

**RUNNING_WEEKLY_SETUP = ACCEPTED.** Figma reference: file `v73TsYo6gKxUoiUock2sSE`, node `548:95` (412×950), with 360dp responsive reference `553:107`. Final production/test head: `bf108de60fc7563145d4941151e5956a90379e44`. Exact-head Windows self-hosted [run 41828665830](https://github.com/Isshhin/stridelab-development/actions/runs/41828665830) **SUCCESS**; artifact `13322541552` (`trackside-strength-windows-evidence`). Shared schema, JVM/unit, app debug build, and androidTest instrumentation build passed. Pixel 9 API 35 emulator smoke **22/22**; responsive checks **412dp/100% 16/16, 360dp/100% 16/16, 360dp/130% 16/16, 412dp/150% 16/16**. **39 PNG screenshots per profile** (156 total) and instrumentation reports uploaded. Android debug-only verification; no release APK.

### Diagnosis and reachability classification

**BUG_CLASSIFICATION = TEST_SEMANTICS_SCROLL_DEFECT.** At `73101fa`, test failure was `builder-run-editor-weekday-row is not displayed` consistently across all four profiles. Previously, the failure targeted `builder-run-editor-day-6`. The editor is within the Builder frame's vertically scrollable body, while both session-selection and weekday-selection rows scroll horizontally. Earlier instrumentation reached a semantics node horizontally without reliably exposing the entire expanded editor vertically; test state and selected workout also needed alignment before day assignment.

This was investigated using the actual failed-run screenshot artifacts, including [run 41810402174](https://github.com/Isshhin/stridelab-development/actions/runs/41810402174), as well as final diagnostic screenshots. The later executed interaction tests at head `889e555` passed **360dp/100%, 360dp/130%, 412dp/150%: 16/16 each**; 412dp/100% failed only on the separate legend-bottom visual assertion, *not* on weekday reachability. In the final green run the weekday picker is visible within the scrolled body, above the fixed Continue control, at all responsive profiles. No evidence established a real user-reachability defect requiring production re-architecture.

The interaction tests retain visible UI opening of the inline editor, explicit `Choose my days` selection, workout selection, vertical scrolling to the expanded editor followed by horizontal scrolling of its weekday row, actual Saturday tap, `WeeklyPlacement.put` persistence, availability update, real slot swap preserving cardinality, assignment progress, Continue state, and draft-review propagation. No direct state mutation replaced a user's tap, and no visibility assertion was removed. The test-only corrections in `d165d91`, `e4613ca`, and the previous scrolling/semantics commits preserve those behavioral assertions. Screenshots include `builder-running-week-session-selector-visible`, `builder-running-week-editor-before-scroll`, `builder-running-week-editor-weekday-row-visible`, `builder-running-week-after-editor-assignment`, and `builder-running-week-manual-placement`, under each profile's `qa-screenshots/<profile>/StrideLabBatchB/` directory.

### Final rendered Figma comparison and P2 correction

The 412dp/100% matched-state screenshot `qa-screenshots/figma-412dp-font100/StrideLabBatchB/builder-running-week-figma-reference-412dp-font100.png` was inspected alongside Figma `548:95`. The weekly-count card, E/Q/L day colors, seven-day layout, 46dp touch areas, requested/placed summary, 143dp session legend, suggested/pinned note, and fixed Continue area remain close to the compact approved design. The expanded editor remains inline, not a popup; after-scroll screenshots prove workout and weekday controls are reachable even at 360dp/130% and 412dp/150%. Small variations from screenshot/system chrome, supported request data, and inline editor expansion are intentional.

A separate **P2 visual issue** remained in the 412dp baseline: the final `Long run` description approached the legend's bottom border. The earlier `f9b7e70` padding adjustment was insufficient for an explicit 14dp clearance assertion. Production correction `bf108de` keeps the reference-size 143dp legend card, uses 40dp normal-font legend rows and an 18dp reserved bottom inset, while leaving accessible larger-font rows content-aware. The final exact-head screenshot and passing clearance assertion confirm the subtitle no longer collides with the border. The same run passed previously accepted Strength, Calisthenics, Discipline Selection, Running Goal, Race Goal, and Running Ability regression checks.

**Final acceptance: no actionable P0/P1/P2 issue remains in the reviewed Running Weekly Setup scope.** Close rendered parity, not pixel-identical. Current Engine 1.5 remains frozen and disabled; no Engine 2.x or release infrastructure changes. The next target is the **remaining Plan Builder dates and availability/review states**, beginning with the Plan Dates stage; accept each screen through its own exact-head rendered matrix before any engine integration.


## Plan Builder: Plan Dates — initial responsive diagnostic (2026-11-04)

**Status: NOT ACCEPTED (visual icon parity pending).** Approved Figma node `720:546` in `v73TsYo6gKxUoiUock2sSE`. Current tested head `f0804016f8d0ac4e27aeae865eea24fcf9cd8fab` includes only a matched-state responsive test; production Plan Dates was not modified. Exact-head Windows [run 41830607364](https://github.com/Isshhin/stridelab-development/actions/runs/41830607364) **SUCCESS**, artifact `13322779218` (`trackside-strength-windows-evidence`). Shared schema, JVM/unit, debug app and instrumentation compilation passed. Pixel 9 API 35 smoke **22/22**; responsive QA **412dp/100% 17/17, 360dp/100% 17/17, 360dp/130% 17/17, 412dp/150% 17/17**, with **41 screenshots per profile**.

Reference screenshot: `qa-screenshots/figma-412dp-font100/StrideLabBatchB/builder-plan-dates-figma-reference-412dp-font100.png`. Additional `builder-plan-dates-time-limit-reachable-<profile>.png` files show the optional Time limits card reachable by scrolling where below the fold. The test initializes a valid future start date and eight weeks, actually taps 12 weeks, opens the optional time-limit card, checks the input exists, and retains an enabled Continue action. This establishes current interaction and scroll behavior, not final visual acceptance.

**Open visual P2:** the existing Start Date row uses a generic `▣` text glyph where Figma uses a specific calendar vector, and the Time limits card uses a `◷` text glyph instead of the approved clock vector. The date and duration panels also have small differences in rhythm/fill relative to the Figma screenshot. Preserve current valid-date gating and supported Engine 1 limits. Before acceptance, replace only the out-of-parity icons and, if necessary, minor local spacing; inspect new actual 412dp and large-font screenshots and rerun the exact-head Windows verifier. Do not change the shared Builder frame, previously accepted screens or engines.

**Next:** Plan Dates icon/geometry correction and rendered acceptance, then Availability and draft/review states. Running Weekly Setup remains ACCEPTED at `bf108de`, documented immediately above.
