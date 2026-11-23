# Engine 1.5 M10a — Android read-only experimental research preview

Date: 2026-11-15. Stacked on Engine 1.5 research PR #88. **M10 is not yet complete.** This is the first Android integration slice, not an activated coaching engine.

## Implementation

The previously isolated M6a/M6b models are **now callable from the actual Android Plan Builder's Week Preview** through:
- \`IndependentAndroidResearchBridge.preview(actual PlanRequest, explicitly entered constraints)\` — a pure adapter, no fake PlanSnapshot or user owner.
- \`IndependentAndroidResearchPreviewPanel\` — a collapsed-by-default, optional real Compose panel inside \`TracksideWeekReview\` when the *pre-generation* request is visible. It is tagged for later Android automated UI tests and uses the existing AMOLED/dark plan-builder palette.
- New \`IndependentAndroidResearchBridgeTest\` — 11 JVM test methods covering real request mapping, explicit limits, mileage retention, fallback/deferral reasons, goal separation, pinned weekdays, no-quality mode and no saving.

The preview requires users to **enter** running weekdays and mileage ceilings. The first UI iteration uses one entered per-day km cap applied across those weekdays; it does not pretend the app knows individual acceptable caps. It also requires starting weekly km, weekly ceiling, and for 3+ running days: previous long-run distance, maximum long run, and maximum increment. Source evidence is NOT authenticated; the data are simply assertions the user supplies for a hypothetical comparison.

### What is shown

- M6a's actual unconcealed week-by-week km candidate, day-by-day mileage, and recovery-week markers.
- M6b's possible quality DAYS only, without prescribing tempo, threshold paces or intensity.
- An explicit \`Deferred(ruleId,explanation)\` when inputs are missing, ambiguous, impossible, race-specific, beginner/returning, mixed-discipline, not Monday-start, conflicting with pinned running/quality weekdays, or outside the supported research bounds.

### Explicit safety constraints

- **No change to** \`Engine15.generate\`, \`Engine1Engines.resolve\`, \`TracksideCoaching.request\`, \`PlanActions.generate\`, \`TrainingRepository.savePlan\`, approved plans, plan schema, imported activities, owner credentials, backup, or application ID.
- The real **Generate My Plan** button still runs the existing Engine 1.5 production path; M10a is an independent, opt-in, read-only comparison alongside it. The panel never writes a synthetic \`PlanSnapshot\`, requests a cloud sign-in or modifies sessions.
- The \`IndependentAndroidPreviewDecision.ReadOnly\` result carries \`sourceEvidenceVerified=false\`, \`executable=false\`, \`authorizedForPlanMutation=false\`, \`authorizedForProduction=false\`.
- M9a/M9b/M9c quality dose, segment distance and complete-week admission remain separate unverified gates. M10a intentionally does not claim to have wired these to an executable Android workout.
- **Race-specific periodization** (5K, 10K, half marathon, marathon), mixed-discipline collision modeling, beginners, returning runners, and non-Monday-start plans currently defer rather than silently inherit a generic weekly-mileage research heuristic.

## Critical blockers before M10 can be declared complete

1. **Signed-out/local-owner**: Existing \`PlanBuilder.kt:19\` still returns a sign-in gate if \`TrainingState.account == null\`; \`TrainingRepository.generatePlan\` and \`savePlan\` also require an account. This is a pre-existing contradiction of StrideLab's agreed signed-out offline use. M10b needs a separately designed local identity, migration, owner isolation and backup rules. Do **not** use a fabricated shared account or change existing saved plan ownership in place.
2. **Real experiment selection and persistence**: M10c must integrate only authenticated/admissible outputs with actual approved PlanSnapshot session contracts and rollback/compatibility, or retain research mode as preview-only if evidence cannot qualify. The Plan Builder must not silently switch engines.
3. **Workout and goal parity**: M9c cannot yet establish threshold pace/heart rate, authentic performed quality dose, race periodization, or valid mixed-strength load; resolve or clearly defer. Do not convert timed workouts to kilometers using a guessed pace.
4. **PR #78 Week Review UI**: M10a works against the stacked #88 research ancestry; reconcile newer UI work from PR #78 before full M10 release. Do not overwrite either source blindly.
5. **Validation**: 11 new JVM tests committed but **not executed**. Earlier M8/M9 worktree test runs were not supplied. Unit tests/full debug compile on the **exact commit** must precede Android M10.1; no green status inferred from the previously passing M7 branch.

## Safe source/test validation (not emulator or physical phone QA)

Run only in a **new unused worktree path**. No reset/clean of modified \`E:\stridelab-validation\`, \`E:\stridelab-m7-validation\` or any earlier phone-QA worktree.

```powershell
cd E:\stridelab-validation
git fetch origin integration/engine15-m10a-research-preview-20261115
$path = "E:\stridelab-m10a-validation-20261115"
if (Test-Path $path) { throw "Validation directory exists; choose a different empty path." }
git worktree add --detach $path origin/integration/engine15-m10a-research-preview-20261115
cd "$path\native-android"
git rev-parse HEAD
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
"sdk.dir=$($sdk.Replace('\', '/'))" | Set-Content ".\local.properties" -Encoding ASCII

.\gradlew.bat :app:testDebugUnitTest --tests '*IndependentAndroidResearchBridgeTest' --tests '*IndependentWeeklyProgramReviewTest' --tests '*IndependentQualityDoseProgressionTest'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Do not install the debug APK on a phone. The user specifically scheduled Android instrumentation/emulator end-to-end tests *after all of M10 integration*.

## Final agreed sequence

M10b–M10c remaining integration and owner/persistence design → **M10.1 Android automated/emulator regression tests** → **M10.5 Astra running-coach audit of actual Android-generated plans** → M10.6 fixes/retests → **M11 physical-phone installation and QA**. Experimental previews must be clearly labeled until evidence and safety qualification are satisfied.
