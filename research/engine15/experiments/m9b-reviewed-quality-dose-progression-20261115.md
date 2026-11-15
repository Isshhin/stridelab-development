# Engine 1.5 M9b — Reviewed next-week quality-dose progression

Date: 2026-11-15. Research-only, stacked on M9a. No Android activation or plan mutation.

## What changed

M9a can reconcile the *arithmetic* of caller-supplied timed-workout segment distances with an existing M6a easy-run distance allocation. It does not decide whether a runner should receive a threshold/tempo workload, nor account for two hard sessions within one week. The earlier M2 \`IndependentQualityFitnessAdapter\` can select feasible time-based threshold/tempo structures but trusts its \`confirmedRecentWorkSeconds\` input and has no weekly multi-slot work-dose accounting.

M9b introduces \`IndependentQualityDoseProgression\` as a **research-only next-week policy boundary** layered on M6a constrained multiweek distances, M6b quality-day spacing, and M2 fitness adapter. It creates \`IndependentQualityDayPreview\` entries for the **one upcoming week** only. These can later be submitted to M9a arithmetic reconciliation after per-segment kilometer assertions are supplied.

### Requirements checked

1. The M6a week exists, is not recovery, and its typed distance allocation matches the M6a planned kilometers. A requested quality slot must exactly match a selected M6b EASY run; long-run days and blocked days are never repurposed.
2. All historic quality-exposure records carry one caller-asserted owner; future/current-week records and duplicate planned-session/source IDs are rejected. **This checks internal consistency, not owner/source authentication.**
3. For **each requested family**, use the **two most recent distinct dates** with recorded WORK seconds in the 28 days preceding plan week. An unknown completed dose, reported difficult response, unknown tolerance, or two same-day claims defers instead of inventing an exercise baseline. The proposal uses the **lower of the two claims**, not the higher peak.
4. Every requested slot carries an independent **per-session work ceiling**, a time-availability envelope, and an explicit quality-allowed decision. If no increase is separately approved, M2 cannot propose more work than the lower of two observations.
5. An additional work-dose step requires an **explicit external review reference** and an **independently supplied maximum step**. Proposed increments above the reviewed single-step cap are rejected. A low or contradictory ceiling also requires review.
6. For up to two proposed quality days, **sum all selected WORK seconds** and check against a separately reviewed weekly ceiling/reference. The planner refuses to silently distribute or reduce over-cap work.
7. No quality progression is assumed for week 2 or later from week 1's recorded history. This next-week-only model must be rerun with new real completed history after the week has elapsed.

**No physiological evidence is inferred** from summary activity duration, title, distance, generic workout logs, planned threshold structure or weekly mileage. The \`recordedWorkSeconds\` field is only a caller assertion. \`IndependentQualityDoseReviewDecision.Preview\` always has:

- \`sourceEvidenceVerified=false\`
- \`distanceReconciled=false\`
- \`intensityVerified=false\`
- \`authorizedForProduction=false\`

Each requested candidate retains the originally allocated EASY run distance; the quality workout is a **hypothetical replacement**, not an additional workout. Even when a reviewed claim raises a 12-minute threshold work example to a feasible 14-minute structure, it remains **non-executable** until source provenance, dose review and M9a time/distance reconciliation are independently established. This does **not** prescribe 14 minutes to a real athlete.

### Example synthetic scenario

- M6a: six days running, Saturday long, Monday daily ceiling 5 km; 60 km week one, valid longitudinal mileage allocation.
- M6b: one candidate quality day on Tuesday.
- Two distinct threshold claims, 12 minutes each, reportedly tolerated and with caller-asserted source identities.
- M9b: no explicit dose increment → repeat 12-minute WORK preview; an externally reviewed +2-minute step within a separate +3-minute maximum → 14-minute WORK candidate *if M2 can select a compatible structure*. Both are only research previews.
- Two candidate quality days must each meet its family-specific exposure/session cap and the *combined* external weekly quality work-dose ceiling. Inconsistent or unavailable evidence holds the whole proposal.

## Files and testing

- New \`IndependentQualityDoseProgression.kt\`: review algorithm and explicit rejection reasons.
- New \`IndependentQualityDoseProgressionTest.kt\`: **18 focused JUnit methods** for repeat/increment, min-of-two evidence, chronology, two families and weekly ceilings, unknown/old/duplicate/difficult/unknown-tolerance claims, requested later weeks, external approval gaps, selected EASY day, recovery/return hold, insufficient time and non-mutation.
- **18 tests have been committed but NOT run.** The M8a/M8b/M9a stacked branch tests have also not yet been locally validated. No BuildConfig, app code path, Android plan builder, plan storage, production algorithm or installed APK has been changed.

### Safe isolated JVM verification (not physical-phone or Android instrumentation QA)

Use a **new empty** validation folder; leave existing worktrees and uncommitted files alone.

```powershell
cd E:\stridelab-validation
git fetch origin research/engine15-m9b-quality-dose-progression-20261115
git worktree add --detach E:\stridelab-m9b-validation-20261115 origin/research/engine15-m9b-quality-dose-progression-20261115
cd E:\stridelab-m9b-validation-20261115\native-android

$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
"sdk.dir=$($sdk.Replace('\', '/'))" | Set-Content ".\local.properties" -Encoding ASCII

git rev-parse --short HEAD
.\gradlew.bat :app:testDebugUnitTest --tests '*IndependentLongitudinalShadowTest' --tests '*IndependentGoalAwareTrajectoryReplayTest' --tests '*IndependentTimedQualityDistanceReconciliationTest' --tests '*IndependentQualityDoseProgressionTest'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Review any failed test report from the **actual checkout SHA** before treating the batch as verified. Earlier successful Gradle reports from another checkout do not validate these changes.

## Remaining coaching roadmap

- **M9c:** qualify the combined weekly program (distribution, independently reviewed different quality families, progression vs lighter weeks, cross-discipline interactions, timing and distance accounting), close data-trust gaps and prepare explicit Android integration contracts. A valid M9a arithmetic sum alone does not authorize a target pace/heart-rate zone.
- **M10:** integrate the experimental generator into StrideLab Android without replacing approved plans, while preserving signed-out/offline core functionality, ownership isolation, recovery behavior and reviewed UI.
- **M10.1:** first run Android instrumentation + emulator end-to-end testing **after M10 integration**, then resolve regressions.
- **M10.5:** Astra independently evaluates the Android-tested integrated algorithm as a running coach; inspect practical plans for different runner profiles, training adaptations and questionable prescriptions, not only test pass counts.
- **M10.6:** implement Astra findings and rerun full affected Android and JVM tests.
- **M11:** final physical-phone installation/QA and promotion gate.

No production activation, merges, Android installation or phone QA before final gates. Engine 2.x research remains separate.
