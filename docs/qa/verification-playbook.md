# Verification playbook

Run from `native-android/` with JDK 17 and the Android SDK configured. On Windows invoke `gradle.bat` (or the checked wrapper when available).

## Focused JVM

```powershell
gradle.bat :app:testDebugUnitTest --tests=io.stridelab.privatealpha.GarminPersonalApiTest --no-daemon
```

Use the same form for a specific class. Run `:app:testDebugUnitTest` without `--tests` at the JVM checkpoint.

## Focused instrumentation

```powershell
gradle.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.stridelab.privatealpha.TracksideApprovedBuilderActivityUiTest' --no-daemon
```

Confirm exactly one API 35 emulator is connected before the run. Do not start or dispatch a second emulator job.

## Checkpoints

1. Focused failing test reproduces the defect.
2. Focused test passes after correction.
3. Related test class passes.
4. JVM suite passes.
5. Integration and navigation tests pass.
6. Four responsive profiles pass when UI/layout changed.
7. Full instrumentation runs once at a broad checkpoint.
8. Exact-head workflow runs on `MARC` after local focus checks.

Record test count, failures/skips, commit SHA, device/profile, and workflow/report URL. A locally passing dirty tree is useful development evidence but not exact-commit release evidence.

## Evidence rules

- Keep fixture, emulator, localhost, CI, hosted/authenticated, and phone evidence distinct.
- Do not claim a live provider result from mocks.
- Do not call a workflow successful until its final conclusion is observed.
- Do not weaken or ignore tests to obtain green output.
