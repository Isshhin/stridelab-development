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

## QA-20261105-R4 — current UI regressions asserted superseded routes and labels

- Original failure: navigation, visual and `Engine1UiTest` assertions still expected UI that had been replaced by the authoritative Trackside flow.
- Root cause: regression maintenance did not accompany earlier navigation and layout changes.
- Correction: assertions now exercise the current Trackside routes and outcomes; no test was ignored or suppressed.
- Commit: `d0d6224efd8b3743f4875960700dd44579acd371`.
- Verified: exact-head MARC run `41946839417`, including 84 integration-smoke tests and 96 responsive tests.

## QA-20261105-R5 — Windows verification stopped at the protected-source gate

- Original failure: MARC run `41946014635` stopped before Android execution because five intentionally changed protected files retained old baseline hashes.
- Root cause: the protected-file manifest and pure-JVM Garmin source boundary were not synchronized with the reviewed changes.
- Correction: update only the five reviewed hashes; move the credential contract to a platform-neutral file and include all Garmin pure sources in the JVM harness.
- Commit: `f4078f40db9d161731b9e13551b963326a62ed9b`.
- Verified: schema/tooling checks, 247 pure-JVM tests, 269 Gradle unit tests, and exact-head MARC run `41946839417` all passed.

## QA-20261105-R6 — packaged Android default enabled frozen Engine 1.5

- Severity: P1.
- Original failure: a new unqualified Trackside draft used Engine 1.5 because `strideEngine15Active` defaulted to `true`, contrary to the required Engine 1.0 production default.
- Root cause: the build flag used the integration-candidate value as its fallback instead of requiring an explicit research-build property.
- Correction: default `ENGINE15_ACTIVE` to `false`; retain the explicit `-PstrideEngine15Active=true` path and all frozen Engine 1.5 tests.
- Regression: `TracksideCoachingIntegrationTest.packagedDefaultSelectsTheConfiguredPolicy` now asserts the packaged flag is false and the generated policy/version are Engine 1.0.
- RED/GREEN: the new flag assertion failed against the prior default, then passed after the one-line fallback correction.
- Verified so far: focused engine/default test, 56 focused reliability tests, 269/269 Gradle unit tests, 247/247 pure-JVM tests, 31/31 tooling tests and catalog validation. Exact-head MARC emulator verification remains required before final acceptance.
