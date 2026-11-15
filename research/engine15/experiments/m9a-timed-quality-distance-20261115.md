# Engine 1.5 M9a — Timed quality mileage reconciliation

Date: 2026-11-15. Research-only; stacked on draft PR #85 (M8b). No Android production changes.

## Specific gap

M4/M6a allocates exact running kilometers to EASY and LONG days, while M5a creates separate **time-based** threshold/tempo candidate workouts. M5a explicitly cannot equate these seconds to distance without additional evidence. If a quality overlay is simply added to the weekly total, StrideLab would double-count volume; if quality seconds are silently converted to kilometers at a guessed pace, StrideLab would invent prescription distance and intensity.

M9a addresses only the **arithmetic accounting** gap. It does *not* solve authenticating interval intensity or deciding whether a runner should execute the session.

## Design and behavior

\`IndependentTimedQualityDistanceReconciliation.review\` needs:
1. A **checked M6a** multiweek distance calendar and its typed EASY/LONG sessions.
2. An **M6b quality-slot proposal** specifying the exact day.
3. An **M5a timed threshold/tempo quality preview** on the same week/day and with the original allocated distance budget.
4. One separately supplied positive **distance in meters for each chronological workout segment** (including warm-up, each work repetition, each between-work recovery, and cool-down), plus a caller assertion of external review.

The new pure Kotlin component expands \`IndependentWorkoutComposition\` and \`IndependentIntervalGroup\`, correctly includes only \`N−1\` recovery intervals for \`N\` work repetitions, checks purpose and THRESHOLD/TEMPO target consistency, checks that all segments are timed and that their durations match the original preview, then checks that the **sum of explicitly supplied segment distances equals the original M6a day's kilometers**.

If all arithmetic matches, the component returns a \`MathematicallyReconciled\` preview that **retains the original full-week mileage**. The time-based quality sequence is a **hypothetical replacement** for one existing EASY session, never an additional run.

Unknown or invalid segment distances, missing external review, a different budget, mixed time/distance contract, a long-run day, recovery week or unselected M6b day result in a visible reject. **No pace is calculated from seconds and no effort is inferred.** Caller-provided distances and review references are **assertions, not proof**. Even an arithmetically reconciled output retains:
- \`sourceDistancesVerified=false\`
- \`intensityVerified=false\`
- \`executable=false\`
- \`authorizedForProduction=false\`

A runnable workout remains blocked until trusted source evidence, dose/pace or effort calibration, program-periodization decisions and persistence/player integration have been independently qualified.

## Test inventory (committed, not executed)

New implementation \`IndependentTimedQualityDistanceReconciliation.kt\`; new \`IndependentTimedQualityDistanceReconciliationTest.kt\` with **13 focused JUnit tests**. Covers: 3 × 4 min threshold and exactly two recoveries; unchanged M6a week and original day totals; tempo warm/work/cool duration; missing, invalid and contradictory distances; missing review; different distance budget; long/recovery day guards; unselected quality date; mismatched family target; mixed-unit preview denied by the existing M5a constructor; and deterministic no-mutation output.

**M9a tests have not run.** Stacked M8a/M8b tests are also newly committed and have not yet been reported passing. An earlier owner-reported successful M6/M7 test and debug-build run was in an already-existing worktree; verify its HEAD before tying that result to an exact commit.

## Safe research validation, without physical phone QA

Use a **new empty directory**. Do not delete/modify earlier \`E:\stridelab-validation\`, \`E:\stridelab-m7-validation\` or \`E:\stridelab-phone-qa-20261115\`.

```powershell
cd E:\stridelab-validation
git fetch origin research/engine15-m9a-timed-quality-distance-reconciliation-20261115
git worktree add --detach E:\stridelab-m9a-check-20261115 origin/research/engine15-m9a-timed-quality-distance-reconciliation-20261115
cd E:\stridelab-m9a-check-20261115\native-android
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
"sdk.dir=$($sdk.Replace('\', '/'))" | Set-Content ".\local.properties" -Encoding ASCII
git rev-parse --short HEAD
.\gradlew.bat :app:testDebugUnitTest --tests '*IndependentLongitudinalShadowTest' --tests '*IndependentGoalAwareTrajectoryReplayTest' --tests '*IndependentTimedQualityDistanceReconciliationTest'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## Next steps and final acceptance sequence

- **M9b**: capability and dose-bound review for the full selected quality session, including meaningful easy-volume replacement and long-term progression. Explicitly qualify intensity and source provenance before promoting any candidate.
- **M10**: integrate the research engine into the Android Plan Builder, training calendar, persistence and offline signed-out ownership. Review and reconcile the UI improvements from PR #78, without making the signed-in gate a feature prerequisite.
- **M10.1**: Android unit/instrumentation/emulator tests after integration (user explicitly wants these **before Astra's final coach audit**).
- **M10.5**: Astra scans and tests the integrated Android algorithm as a critical running coach on realistic profiles, adverse scenarios and longitudinal coaching choices. Report defects and justified recommendations, not merely test counts.
- **M10.6**: repair Astra findings and repeat affected tests/validation.
- **M11**: final packaging, regression closure, and then the user's physical-phone installation and QA. No phone test or production activation before this gate.

No modifications to Engine 2.x, production Engine 1.5, source imports, account database, existing saved plans or installed phone.
