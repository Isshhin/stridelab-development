# QA environment

## Android toolchain

- Windows x64
- JDK 17
- Android SDK with platform/build tools needed by compile SDK 36 and target SDK 35
- Gradle 8.13 compatible with the project
- API 35 x86_64 emulator/AVD with hardware acceleration when available
- Gradle/Maven dependencies warmed before broad offline-sensitive runs

Before a full suite, confirm `JAVA_HOME`, `ANDROID_HOME`/`ANDROID_SDK_ROOT`, `adb devices`, the AVD, and dependency resolution with a focused test.

## Self-hosted GitHub Actions runner

- Runner name: `MARC`
- Verified workflow labels: `[self-hosted, Windows, X64]`
- Do not assume `MARC` is a custom label; it is the runner name.
- Inspect `.github/workflows/trackside-engine15-windows.yml` before editing or dispatching.
- Preserve existing workflow permissions and security controls.
- Do not create/register another runner.
- Serialize emulator jobs. Do not run multiple Android emulators concurrently on this machine.
- If the runner is offline or a job cannot execute, record the blocker and do not claim verification.

Never document runner registration tokens, signing values, API keys, access tokens, personal athlete data, or other secrets. Machine-specific absolute paths belong only in ephemeral local notes, not repository documentation.
