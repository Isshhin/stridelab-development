# Trackside 3.0: Strength Setup — rendered Design QA

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
