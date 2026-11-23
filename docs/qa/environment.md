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
- Signed personal-QA delivery reuses the registered `.github/workflows/trackside-candidate-apk.yml`; the Trackside QA branch selects its MARC-only job while preserving the prior candidate job for other refs.
- Preserve existing workflow permissions and security controls.
- Do not create/register another runner.
- Serialize emulator jobs. Do not run multiple Android emulators concurrently on this machine.
- If the runner is offline or a job cannot execute, record the blocker and do not claim verification.

Never document runner registration tokens, signing values, API keys, access tokens, personal athlete data, or other secrets. Machine-specific absolute paths belong only in ephemeral local notes, not repository documentation.

## Signing and APK delivery

- The Android package is `io.stridelab.privatealpha`; default version code/name are `15` / `0.6.6-guided-workout` with min/target SDK `26` / `35`.
- Gradle supports an optional durable private-alpha release signer through environment-only credentials, but no repository/environment durable-signing secrets or expected signer variable were configured for the 2026-11-05 build.
- The QA workflow uses only MARC's pre-existing debug keystore and fails before Gradle if it is absent; this prevents silent generation of a substitute key.
- Never upload the keystore or passwords. Artifact evidence may contain only public certificate fingerprints, package metadata, checksums and run identity.
- The recorded version-14 certificate is `fbf186fc95d790c566583830bfca6942c017e77c4968dace023bf490114efe95`. The 2026-11-05 MARC QA certificate is `62725b9237a0a7f182d99503b16f20d7b2f47be5f99ce07fc59b5e30cd8dd270`; therefore that APK cannot update the recorded installation.
- A valid `apksigner` result proves APK integrity, not compatibility with the installed phone. Use the read-only `native-android/tools/check_apk_update.py` gate before any in-place update.
