# Resolved defects

## QA-20261105-R1 — occupied Running weekday changed the wrong session

- Original failure: tapping an occupied weekday applied that day to the currently selected slot, changing another session instead of selecting the session already displayed there.
- Root cause: compact weekday clicks reused the assignment callback rather than selecting the owning slot.
- Correction: occupied days select their owning session; only the explicit editor assigns/moves a weekday.
- Commit: `12c75faf31be6e1382d644485585d894b95ec60d`.
- Regression: `TracksideApprovedBuilderActivityUiTest` covers every occupied slot, explicit Easy-session move, review identity, and drag/reorder behavior.
- Verified: focused red/green tests, full class 16/16, JVM suite 262/262, four responsive profiles 96/96 at that commit.

## QA-20261105-R2 — Activity details behaved like a fixed page

- Original failure: the detail content used fixed top padding rather than a real draggable sheet; sheet position did not change and map persistence/back behavior was not represented.
- Root cause: visual layering approximated the Figma layout without a sheet state/anchor contract.
- Correction: Material 3 `BottomSheetScaffold` with partial and expanded anchors, independent nested scroll, fixed map layer, semantic expand/collapse, and collapse-first Back.
- Commit: `12c75faf31be6e1382d644485585d894b95ec60d`.
- Regression: Activity drag/anchor, tab state, map persistence, accessibility, and Back tests in `TracksideApprovedBuilderActivityUiTest`; related Activity/navigation/visual tests.
- Verified: focused red/green tests, full class 16/16, JVM suite 262/262, responsive profiles 96/96 at that commit.

## QA-20261105-R3 — Activity tab selection lacked tab semantics

- Root cause: selectable controls did not publish `Role.Tab` and selected state.
- Correction: tab role and selected semantics added.
- Commit and verification: same as QA-20261105-R2.
