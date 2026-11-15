# Engine 1.5 M8b — Goal-aware historical trajectory replay

Date: 2026-11-15. Stacked on M8a draft PR #84, research-only.

## Purpose

M8a compares only the most recent synthetic response against one constrained baseline. M8b now replays **multiple already-observed weekly claims** as successive independent next-week checkpoints. This exposes whether a policy changes decisions appropriately after missed weeks, rebuilt mileage, planned recovery and unresolved evidence. It also prevents Train Your Way's weekly-distance target from silently becoming a race preparation goal.

## Implemented

\`IndependentGoalAwareTrajectoryReplay.replay\` consumes:
- A versioned **goal identity**: \`TRAIN_YOUR_WAY_MILEAGE\` with one explicit target weekly distance, or \`RACE_PREPARATION\` with race event/distance.
- The existing M79 \`IndependentMultiweekResponseInput\`: 3–8 consecutive completed-week **caller assertions**, requested M4/M6 limits, and a planning Monday.
- An M7b plan-linked evidence ledger.

For Train Your Way, the target must appear independently and agree (within 0.00001 km) with the volume request; race fields are refused. Race requests are **deferred**, not redirected into the mileage planner: the race-specific periodization engine is not qualified yet.

M8b feeds each prefix of **only weeks that had already elapsed at that checkpoint** into M8a. For N observed weeks, it returns N−1 ordered checkpoints. Each displays the comparison or exact deferral rule, requested target, whether missing evidence was encountered and the synthetic next-week distance if one was available. A deferred intermediate checkpoint is **not silently omitted** and an earlier decision is not allowed to see a later recorded week.

Accepted shadow comparisons remain **unverified** and **non-executable** even if all claimed activity links appear complete. No replayed proposal is fed back as a real completed week; every checkpoint needs input history that was independently supplied. Production \`Engine15.generate\`, Android UI, plan persistence, imports, approved plans, and previous PR #78 UI changes remain untouched.

### Synthetic scenarios covered

- Four completed 60 km build weeks => 3 "hold 60 km" preview checkpoints, no automatic growth.
- Two 60 km planned weeks with 30 km completed each, then two 30 km rebuild weeks => 30 km reduced-repeat, mixed/rebuild review, then 30 km hold; this detects inappropriate instant restoration to 60 km.
- Completed 60 km build plus reviewed 40 km recovery => resume 60 km without catch-up; if followed by a single 60 km build after recovery, defer mixed-intent adjustment for explicit review.
- Unknown earlier reported tolerance continues to block the unqualified replay; source links do not authenticate training response.
- Missing M7b week's audit explicitly defers affected checkpoints, while a later fully audited two-week window may still produce an **unverified shadow**.
- Train Your Way target 40 km is retained as the target instead of becoming a 5K/10K/marathon outcome, and the trace enforces no proposal above that value.
- Race goal with event date/distance returns \`RACE_SPECIFIC_PERIODIZATION_NOT_QUALIFIED\` rather than inventing race-specific workouts.

## New validation (unexecuted)

Source: \`native-android/app/src/main/java/io/stridelab/privatealpha/IndependentGoalAwareTrajectoryReplay.kt\`.

Tests: \`native-android/app/src/test/java/io/stridelab/privatealpha/IndependentGoalAwareTrajectoryReplayTest.kt\`, **13 focused JUnit cases**: 4-week history, repeated missed/rebuild, planned recovery, missing audit window, unknown tolerance, no future lookahead, target cap, race-mode guard, absent/mismatched targets, ledger mismatch/failure, insufficient/stale history, deterministic unauthorized outputs.

**These 13 cases have been committed but NOT executed.** Last reported owner validation passed Gradle focused M6/M7 tests and the full JVM/debug build from an existing \`E:\stridelab-m7-validation\` directory, whose HEAD wasn't yet confirmed. That does not validate M8a/M8b.

### Safe focused + full JVM check, no phone testing

Choose a **new empty path** for a clean research worktree. Do not delete the user's existing validation or phone-QA directories.

```powershell
cd E:\stridelab-validation
git fetch origin research/engine15-m8b-goal-aware-trajectory-replay-20261115
git worktree add --detach E:\stridelab-m8b-verify-20261115 origin/research/engine15-m8b-goal-aware-trajectory-replay-20261115
cd E:\stridelab-m8b-verify-20261115\native-android
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk
$sdkPath = $sdk.Replace('\', '/')
"sdk.dir=$sdkPath" | Set-Content ".\local.properties" -Encoding ASCII
git rev-parse --short HEAD
.\gradlew.bat :app:testDebugUnitTest --tests '*IndependentLongitudinalShadowTest' --tests '*IndependentGoalAwareTrajectoryReplayTest'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The expected M8b head will be recorded on draft PR #85 when opened.

## Remaining explicit gates

- **M8c** independently scrutinize error and uncertainty classification, build-vs-recovery transition state, and mode-specific decision requirements before claiming coaching intelligence. Do not treat arbitrary completion ratios as medical thresholds.
- **M9** complete executable workout recipes and reconcile distance-allocated easy/long workouts with quality work duration, effort, warm-up/cool-down and correct totals, without inventing measured training thresholds.
- **M10** integrate the experimental coaching engine into Android with offline signed-out support, isolated ownership, rollback and reviewed Week Preview, without merging research branches wholesale.
- **M10.1** run JVM and Android instrumentation/emulator end-to-end tests and repair regressions.
- **M10.5** Astra independently scans the **integrated, Android-tested** engine from the perspective of a critical running coach and tests realistic athlete scenarios, then review/qualify and fix identified weaknesses.
- **M10.6** implement Astra's corrections and rerun affected verification suites.
- **M11** final packaging, source/backup/account offline regressions and *then* owner's physical phone installation and QA.

No production promotion or Android device testing before these gates.
