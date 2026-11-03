# Engine 1.5 · Batch E3 isolated Trackside review candidate

Created 3 November 2026. **This is a pre-approval, NON-ROUTED design implementation; not the live Engine 1.5 UI.** It is a separate draft PR #38 built from the exact Trackside PR #36 head (0bbc0fdb). Engine 1.5 backend PR #37 has not been copied, merged, or otherwise altered.

## Source references and Figma mapping

- [Editable five-screen Figma review page](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-2).
- Preview [834:5](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-5) → `TracksideReviewStage.PREVIEW`.
- Pending explicit decision [834:69](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-69) → `PENDING`.
- Accepted shorter time-only [834:133](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-133) → `ACCEPTED_TIME`.
- Accepted rest [834:190](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-190) → `ACCEPTED_REST`.
- Stale source or plan [834:247](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-247) → `STALE`.
- Source Trackside approved Plan Management [732:3](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=732-3) and the native existing floating navigation.

## Implemented on isolated branch

- `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideEngine15ReviewCandidate.kt`: Android Compose candidates, matching Figma frame layout at 412×950dp, five annotated Compose previews, reusable gradients/cards/metric rows/chips/actions. Fixed preview-only navigation is inert and excluded from accessibility semantics; when integrated into the actual app shell the caller must pass `showPreviewNavigation=false`. This source is **not** added to any route or tab.
- `TracksideEngine15ReviewCandidateModel.kt`: pure, validated read-only state model and content projection. The original time/distance are always preserved for context; active distance is `null` for accepted shorter time or accepted rest, and original structured steps must not be treated as active. Preview, pending and stale states leave original targets active.
- Rest (suggested minutes **zero**) is supported during PREVIEW and PENDING as an explicit decision with `Accept rest` / `Dismiss`; there is no automatic application even when the athlete reports an illness. The completed state shows a rest day with zero active minutes. The Figma sample uses 45 minutes but the component also handles zero-minute proposals as a substate.
- `TracksideEngine15ReviewCandidateModelTest.kt`: 8 pure JUnit test methods cover preview/pending/stale unchanged targets, explicit shorter time, accepted rest, zero-minute pending rest, invalid proposed values, missing original distance, and stale copy.
- `native-android/tools/run_library_jvm_checks.py`: includes the exact pure model class in the pinned independent JVM source list, retaining all prior checks.
- `.github/workflows/engine15-review-preview-ci.yml`: builds only `:app:compileDebugKotlin` and `:app:testDebugUnitTest`, plus pinned pure JUnit twice and schema/Python tests. **It does not call assembleDebug, connectedDebugAndroidTest, create an APK or install anything.**

All preview dates, distance and RPE samples are illustrative and private to Compose `@Preview` functions. They do not represent user data, medical inference, or actual coaching recommendations.

## Known limitations and next gates

1. The E2 Figma review frames are still **unapproved** by the user. Do not wire this screen into production navigation, expose an opt-in, change Engine 1.0 default, or merge PR #38 until an explicit visual approval.
2. Android Gradle **compilation does not prove screenshot parity**. After approval, conduct actual 412dp and 360dp screenshot comparison against all Figma states and 1.3× font scale. Verify text wrapping, scrolling, 48dp action targets, touch semantics, and nav clearance. Real Android screenshot tests require an emulator/app build and have not been authorized at this stage.
3. The candidate is presentation-only, not a repository adapter. After approval, create a reviewed integration that combines the Engine 1.5 versioned policy and repository source from PR #37 with Trackside screens deliberately, rather than blindly merging diverged branches.
4. Bind PREVIEW with pure `previewEngine15Adjustments` (no mutation), PENDING with revision-checked `stageEngine15Adjustment`, Accept with `acceptSuggestion` (revalidated evidence), Dismiss with `dismissEngine15Adjustment` or `dismissStagedEngine15Adjustment`. Never infer missing activity as poor adherence; never auto-push changes to Garmin or providers.
5. On accepted time-only, the current display target has **no required old distance or structured-interval pace**. Historical prescriptions remain attached to the saved plan. A stale decision returns to fresh review; do not silently retry.
6. After source integration, rerun all pure-JVM, full Gradle, Android 35 offline SQLite/device tests, and Trackside responsive/legacy upgrade UI tests; then private-device acceptance after authorization.

**Status:** PR #38 draft and unmerged. PRs #36 and #37 remain draft/unmerged and unchanged. No APK assembled and no app entrypoint changed. Record the exact-head GitHub workflow result separately; do not assume a past green run applies to a later edited commit.


## E3.1 — 360px responsive-width and enlarged-font review (3 November 2026)

Created **five additional editable, separate Figma study frames**, without modifying the previously approved page 35 or the initial 412px review page 37:

- [360px study gallery](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=840-2)
- [Preview / 360px](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=840-3)
- [Pending Accept / Dismiss / 360px](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=840-67)
- [Accepted time only / 360px](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=840-131)
- [Accepted rest / 360px](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=840-188)
- [Stale evidence / 360px](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=840-245)

Source originals are `834:5`, `834:69`, `834:133`, `834:190`, `834:247` respectively. These are editable **360×950px WIDTH study artboards**, not assertions about 360×800px viewport behavior. Narrowed content width to 326px, cards to 326px, floating nav to 336px, reflowed text and adjusted metric rows/buttons. Rendered and visually inspected all five screenshots. No visible horizontal clipping or collisions in these Figma artboards.

Added paired `@Preview(widthDp = 360, heightDp = 800, fontScale = 1.3f)` annotations to the five existing Compose preview functions, plus a sixth 360×800 / 1.3× preview showing a **pending 0-minute rest** that still requires explicit approval. The existing 412×950 native preview annotations remain. The new preview annotations are compile-only test inputs, not visible application destinations.

**Verification still required:** CI success on the exact Kotlin preview-annotation commit; actual Android preview render/screenshot comparison on 412dp and 360dp; fontScale 1.3× scroll-to-actions, 48dp touch targets, status accessibility and fixed navigation clearance on a device. The Figma 360×950 screenshots cannot establish Android screenshot parity or behavior at 360×800 and enlarged font settings. No APK or emulator invoked for this review.

**Status:** design width study completed; **visual approval still pending**. PR #38 stays draft/unmerged, and the review screen is still unconnected to production navigation or Engine 1.5 runtime.
