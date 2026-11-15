# Engine 1.5 final integration acceptance — 2026-11-15

## Scope and candidate

Read-only integration QA was performed on `integration/engine15-full-acceptance-20261115`, based on `origin/dev` at `f66ac0232bc3dc757163ab2ddc50ab9dd7c2be6a`. The candidate includes the PR #94/#95 stack; neither PR was merged, and `main` was not changed. No physical device was accessed. Emulator data used synthetic fixtures on the disposable `codex-engine15-clean-20261113` AVD (`emulator-5554`, API 35, 1080×2424).

## Earlier six failures and findings

| Failure | Classification | Evidence and correction |
|---|---|---|
| Compose `captureToImage()` failed with `AssertionError: Failed waiting for PixelCopy!` in weekly/history coach-control cases | Screenshot-capture/test-window boundary; not a coaching interaction failure | Stack ends in Compose `WindowCapture_androidKt.generateBitmapFromPixelCopy`, with no app exception. The affected screenshots were artifacts, not visual assertions. Those two capture calls were removed while preserving semantics and state assertions. Navigation screenshot captures passed on the same AVD. Evidence does not expose PixelCopy's result code, so the specific cause (per-window readiness versus surface/graphics behavior) remains unresolved; the app itself is not implicated by the trace. |
| Repeated schema-1 backup restore returned false | Application repository defect | Existing plans were correctly excluded from additions, but the repository still sent two empty lists to `TrainingStore.restoreReviewedBackup`, which rejects an empty transaction. `TrainingRepository.restorePlans` now returns success with a no-new-records message before opening the store transaction. Repeated restore, existing plan identity, completed workout history, owner mismatch, transactional conflict tests, and schema-2 mixed plan/research restore all pass. |
| Signed-out Today hero test expected sign-in callback | Test expectation defect | Current signed-out hero intentionally opens the offline Plan Builder. Test now verifies the plan-builder callback and verifies sign-in is not called. |
| Navigation test requested removed `nav-activities` / Activities route | Stale test selector | Test now exercises current Today → Training → More navigation and checks Training survives Activity recreation. |
| Garmin title assertion found two matching text nodes | Test selector ambiguity | Both the activity preview and detail showed the same valid activity title. Assertion is now scoped to `activity-preview-title`; it still verifies the title in Activity Detail. |
| Activity intelligence test expected removed `activity-analysis-current`, `ACTIVITY HIGHLIGHTS`, and Activities return route | Stale test assumptions | Test now opens the recorded item from the Training timeline, expands current `activity-insights`, verifies comparison/fitness caveats, then verifies return to the Training timeline. |

## Process-death harness

The earlier wrapper output ended in `INSTRUMENTATION_RESULT: shortMsg=Process crashed` without an Android fatal-exception trace. Direct fixture preparation passed. A rerun located the wrapper failure at `adb shell monkey`: PowerShell treated harmless ADB diagnostic stderr as terminating under `$ErrorActionPreference='Stop'`. The harness now temporarily permits native-command stderr during only the launcher invocation while still checking ADB's exit code and `Events injected: 1`.

The corrected `native-android/tools/run-engine15-process-restart.ps1` passed all seven scenarios: `draft`, `approved`, `equal`, `completed`, `feedback`, `accepted`, and `dismissed`. Every scenario verified the process was absent after force-stop, relaunched the app, and verified saved state offline from a new instrumentation process. This is emulator persistence evidence, not phone acceptance.

## Final validation

Normal candidate build, explicit Engine 1.5:

```powershell
.\gradlew.bat -PstrideEngine15Active=true -PstrideVersionCode=16 :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m'
```

- JVM suite: **770 tests, 0 failures, 0 errors, 0 skipped** (99 suites).
- Generated `BuildConfig.ENGINE15_ACTIVE=true`; the packaged-policy test confirms new plans route to Engine 1.5.
- Debug APK and Android instrumentation APK assembled successfully.
- Sequential instrumentation run: **71 tests passed, 0 failed**. Coverage included coach controls, plan generation and approval, backup/repository behavior, research-review separation, offline persistence, navigation, Garmin guest activity history, Today/Training/More, Activity Detail, builder route restoration, and workout flows.
- Process-death harness: **7/7 scenarios passed** as detailed above.

The 71-test instrumentation set was run as one sequential ADB instrumentation session with the normal Engine 1.5 app and test APKs installed on the disposable AVD:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$classes = @(
  'io.stridelab.privatealpha.ActivityIntelligenceUiTest',
  'io.stridelab.privatealpha.Engine15CoachControlsTest',
  'io.stridelab.privatealpha.Engine15FeedbackRepositoryTest',
  'io.stridelab.privatealpha.Engine1RepositoryTest',
  'io.stridelab.privatealpha.IndependentResearchDraftRepositoryTest',
  'io.stridelab.privatealpha.LocalPlanRepositoryTest',
  'io.stridelab.privatealpha.NativeNavigationTest',
  'io.stridelab.privatealpha.PlanBuilderRouteRestorationTest',
  'io.stridelab.privatealpha.TracksideEngineBuilderIntegrationTest',
  'io.stridelab.privatealpha.TracksideGarminModalTest',
  'io.stridelab.privatealpha.TracksideManageNavigationTest',
  'io.stridelab.privatealpha.TracksideTabTest',
  'io.stridelab.privatealpha.TracksideTodayTest',
  'io.stridelab.privatealpha.TracksideTrainingCalendarUiTest',
  'io.stridelab.privatealpha.WorkoutPlayerTest'
) -join ','
& $adb -s emulator-5554 shell am instrument -w -r -e class $classes io.stridelab.privatealpha.test/androidx.test.runner.AndroidJUnitRunner
```

Process-death harness invocation:

```powershell
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\native-android\tools\run-engine15-process-restart.ps1 -Serial emulator-5554 -Adb $adb
```

Separate Engine 1.0 rollback build:

```powershell
.\gradlew.bat -PstrideEngine15Active=false -PstrideVersionCode=16 :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m'
```

- JVM suite: **770 tests, 0 failures, 0 errors, 0 skipped** (99 suites).
- Generated `BuildConfig.ENGINE15_ACTIVE=false`; the variant-aware policy test confirms new drafts use Engine 1.0 while saved plans remain manageable.
- Rollback debug APK assembled. It was not installed on the emulator or any device.

The final 71-test instrumentation run used the normal Engine 1.5 app and test APKs. Gradle and ADB sessions ran sequentially. Hosted GitHub Actions status is separate; repository Actions billing/spending restrictions remain an external CI limitation.

## APK and signing

Final normal candidate artifact:

- Path: `native-android/app/build/outputs/apk/debug/app-debug.apk` (copied to `%TEMP%\stride-m106-final-e15\app-debug-engine15.apk` before the rollback build replaced shared Gradle outputs).
- Package: `io.stridelab.privatealpha`.
- Version: `0.6.6-guided-workout`, version code **16**.
- Engine setting: `ENGINE15_ACTIVE=true`.
- APK SHA-256: `46AE6306BB73D91C95C803F0B82C4664BC397C6367B5A0C8FE6A105C6A06AADA`.
- Certificate: Android Debug; SHA-256 `62725b9237a0a7f182d99503b16f20d7b2f47be5f99ce07fc59b5e30cd8dd270`.

Rollback debug APK (separate, not installed): `%TEMP%\stride-m106-engine10-rollback.apk`; APK SHA-256 `9DAD1A3B44C817AFDB9F746666B54B26BCC3F7E3FEAA4EF843D3987898B9FF81`; generated Engine setting was false. Candidate and rollback use the same local Android Debug signing identity.

No verified APK or signer metadata for the owner's installed physical-device package was available. Therefore update signature compatibility, installed version-code comparison, data-preserving in-place update, and rollback on the owner's device are **UNVERIFIED**. Do not infer compatibility from emulator installation. The candidate is not installation-ready until the owner compares installed signer/version read-only and confirms an appropriate backup/recovery path.

Read-only owner-side comparison commands (run locally with the phone already authorized; these commands do not install, uninstall, clear, or alter app data):

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$signer = "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat"
$candidate = "$env:TEMP\stride-m106-final-e15\app-debug-engine15.apk"
$base = (& $adb shell pm path io.stridelab.privatealpha | Select-Object -First 1).Replace('package:', '')
& $adb shell dumpsys package io.stridelab.privatealpha | Select-String 'versionCode|versionName'
& $signer verify --print-certs $candidate
& $adb pull $base "$env:TEMP\stridelab-installed-base.apk"
& $signer verify --print-certs "$env:TEMP\stridelab-installed-base.apk"
```

If reading the installed base APK is denied, use the owner's original installed APK or verified release-signing metadata instead. Matching signer and an acceptable version-code update path must be confirmed before installation. No physical phone was touched by this audit.

## Delivery and verdict

The candidate branch is retained for review. A draft PR may describe unresolved signing and hosted-CI limits; this report does not claim owner-device acceptance or real-athlete coaching effectiveness. The integration stack and QA corrections remain unmerged; PRs #94 and #95 remain intact, draft, and unmerged.

- Engine 1.5 integration acceptance on disposable emulator: **PASS**.
- JVM tests and both engine configurations: **PASS**.
- Existing synthetic saved-plan/workout/backup owner-isolation and transaction checks: **PASS**.
- Physical-device signing/update compatibility: **UNVERIFIED**.
- Personal-phone installation readiness: **NOT READY** pending read-only signer/version comparison.
