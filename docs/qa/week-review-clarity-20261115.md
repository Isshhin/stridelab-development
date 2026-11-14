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

## Owner-controlled physical phone QA setup (isolated application ID)

**Goal:** Test the Week Preview UI before any merge, without replacing the owner's currently installed `io.stridelab.privatealpha` app or its database. The optional Gradle property `-PstridePhoneQaIsolation=true` builds a distinct debug package **`io.stridelab.privatealpha.qa`** while the default build identity remains unchanged. The isolated QA app has separate storage and may require separate sign-in or provider setup. Do not assume its Garmin OAuth behavior represents the production installation. Neither plugin push nor PR creation installs anything on a device.

### Local safety/validation sequence (PowerShell 5.1)

1. In the local StrideLab validation clone, check `git status --short`; **preserve uncommitted work**. Fetch this UI candidate and switch to an isolated validation branch from `origin/ui/week-review-clarity-20261115`. Do not merge the research PR stack to obtain a Week Preview UI change.
2. From `native-android`, run `.\\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest -PstridePhoneQaIsolation=true`. This assembles `app/build/outputs/apk/debug/app-debug.apk` (APK file name stays the same).
3. Inspect APK package ID using SDK build-tools `aapt.exe dump badging ...`. **Verify the APK says `io.stridelab.privatealpha.qa` before installation**. Never proceed if it says `io.stridelab.privatealpha` or anything unexpected.
4. Enable USB debugging on the phone, connect and authorize it, inspect `adb devices -l`, and select the exact phone serial with `adb -s SERIAL` if multiple devices appear. Install with `adb -s SERIAL install -r ...` only after the package ID verification and owner's approval. No uninstall of the main package; no clearing production data.
5. In the QA app, verify signed-out entry, navigation and Today/Training/More; test Plan Builder's Week Preview before/after generating a draft, 0-running scenario, valid/unknown mileage, next/previous week, two sessions/day, long titles, 1.3/1.5 system font scale, approval scrolling, back navigation, sheet/map opening, share/export controls. **The existing PlanBuilder sign-in gate may prevent signed-out plan generation and is an open functional issue, not fixed by this UI PR.**
6. Capture screenshots and `adb logcat -d` snippets of errors with private identifiers removed. Confirm the original installed StrideLab app is still present and its data unchanged. Note that the QA app cannot prove cross-account backup/restore or production Garmin integration equivalence.

No connected phone, emulator instrumentation or manual screenshot validation was executed during this GitHub-only preparation. Compile/packaging is still pending the owner's local Gradle run.
