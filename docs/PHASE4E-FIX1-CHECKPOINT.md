# Phase 4E Fix1 verified checkpoint

26 October 2026. Production Isshhin/stride-lab untouched.

Original Gradle evaluation failure reproduced with JDK 17 and Gradle 8.13 stack trace. Only Android source changes: explicit assignment for versionCode and versionName in android/app/build.gradle. Offline HTML, protected engine and 21 assets unchanged.

Private source release: [phase4e-fix1-candidate](https://github.com/Isshhin/stridelab-development/releases/tag/phase4e-fix1-candidate).
Source ZIP SHA256: a3e5f0e8fead1f9673bcfbfce4e61c3408da4768652dc3707437301bb5df6275. Manifest: 41/41 hashes; ZIP CRC passed.

[Successful CI run 40258790376](https://github.com/Isshhin/stridelab-development/actions/runs/40258790376), workflow commit f62653017514ae186288eea0229479ef28db02d1. Exact workflow .github/workflows/phase4e-fix1-android.yml. Passed provenance, compilation, 5/5 unit tests, 2/2 API 35 emulator tests and APK signature verification. Downloaded APK contains byte-identical HTML and 21 assets. Emulator tests cover offline WebView origin, storage across activity recreation, and no Internet permission.

Artifact 12623503159 (debug APK ZIP) SHA256: 8137ed7a3e21e996c35ce3e980077e1ead9fa29d5373eab8c0dc821143caa379.
Artifact 12624036209 (test evidence ZIP) SHA256: 0d7306a290aec9f643841d93fd16496546765c61805bb0aa8ba4aa6d5ebdab09.
Extracted CI APK SHA256: e7c6009650caedc79868dcfc8f3cc8b436a2196442a1e917d7621204cf1ce8b2.
Artifacts expire 9 November 2026; copies were retrieved locally.

First Fix1 run 40257685805 stopped at an old manifest filename; corrected in d1bbba273ba31f35ff434593adbf62517bed8a2e. Superseded run 40257798996 built and passed unit tests but stalled on emulator boot; cancellation requested after replacement succeeded. The successful workflow adds documented KVM permissions on the ephemeral Linux runner, forces acceleration, and disables snapshots.

Signing secrets were absent; durable signed release APK was skipped. No reusable signing key was generated or published. Debug signing is disposable and may differ across runners. Physical-device testing and human acceptance remain pending.
