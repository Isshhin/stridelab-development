# Week Preview clarity and accessibility candidate

2026-11-15 Asia/Manila. Baseline `de7614d68c27d714d79a6d2db42ab4d017d13075` (PR #76).
Branch `ui/week-review-clarity-20261115`, independent of coaching experiment branch.

## Source audit and scope

Current Android implementation is design authority. Read PlanBuilder, TracksideWeekReview, builder frame/palette, production Engine15 and its feedback path. No Figma reconstruction. No running device or configured AVD was available, so this is a **source audit and implementation candidate**, not visual acceptance of the current or changed app.

Week Preview displayed `request.weeklyKm` with caption “km target”, even though that field describes recent mileage and a distinct `targetWeeklyKm` exists. Generated preview continued showing it across weeks rather than the selected week's prescriptions. Four narrow metric columns used 9 sp captions; session cards used 31 dp minimum height and one-line 10 sp labels. Week buttons forced white text even when disabled. These are concrete presentation concerns without needing a new design language.

Implemented:

- Before generation, distinguish requested target, recent mileage and no running.
- After generation, sum only displayed running prescriptions. If any running distance is absent/invalid, show unknown rather than a partial sum. These are saved planned distances, not completed measurements or a guarantee of timed-workout reconciliation.
- Display requested weekly target separately from the selected week's planned distance.
- Two-column metric grid, 12 sp captions and merged metric semantics. Existing surfaces, borders, lime selection and black foundation retained.
- Session cards have minimum 48 dp height and wrapping 12 sp titles. Existing session IDs/callbacks are unchanged.
- Week navigation row can grow above 48 dp; buttons inherit disabled-state colors. Error text uses a polite live region.

Preserved: generation/approval, dates, request and plan contracts, session navigation, persistence, Garmin, offline/account ownership, floating navigation and all engines. No new dependency. Existing signed-out PlanBuilder gate is a separate unresolved functional finding; no blind removal of account guards.

## Verification

New `WeekReviewDistanceTest`: three JVM methods cover selected-row distance sums, excluding strength, unknown/nonfinite/negative distance, and an empty running week. Test-first compile failed for the missing helper, then passed after implementation.

New `TracksideResponsiveQaTest.weekPreviewShowsSelectedWeekDistanceAndPreservesNavigation`: synthetic two-week plan; asserts planned distance, separate target, unknown second-week distance, previous/next callbacks and disabled boundaries. Independent source review identified retained mileage targets after deselecting running; the blueprint preserves “no running” and generated review suppresses the stale target. `weekPreviewHidesRetainedRunningTargetAfterDeselectingRunning` covers both states. Both Compose tests are **compiled, not executed**.

Command from native-android with explicit Temurin JDK21 and ANDROID_HOME:

```
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8"
```

Initial candidate run passed all tasks in 48s. Final source validation: **530 JVM tests, zero failures/skips; app debug and instrumentation APK builds passed in 25s**, 11 tasks executed and 61 up-to-date. No emulator instrumentation was run: `adb devices -l` returned no devices, `emulator -list-avds` returned none. No physical phone installation or data access. Existing baseline warnings retained.

## Remaining acceptance and risk

Run the new method and existing week-preview tests on an isolated emulator at 360/412 dp and normal/1.3/1.5 font scales. Verify long titles, double-session rows, scrolling to approval, unknown-distance state, disabled navigation, TalkBack grouping/error announcement, back and session-detail return. Larger content can increase scrolling; layout and touch behavior remain visually unverified. The summary only reports stored distance, whose physiological accuracy is outside UI scope.

No broad app audit, screenshot acceptance, live Garmin/map verification or signed-out persistence validation is claimed. This draft shares the requested research baseline for a small review diff; integrate only the reviewed UI commit into the appropriate current dev lineage after checking drift. Do not merge the research stack into dev merely to obtain these UI changes.

## Follow-up review correction (2026-11-15)

The generated Week Preview previously displayed `0.0 planned km` for a plan with no running and a retained target. The metric now preserves `no running` whenever running is deselected and no saved running prescription exists, in both blueprint and generated states. If saved rows actually contain running sessions despite a disabled request, the preview reports their real saved-distance status rather than hiding conflicting data. The new Compose assertions cover the generated no-running state. **This additional Compose case is compiled-only until an emulator executes it**; no device evidence is claimed.
