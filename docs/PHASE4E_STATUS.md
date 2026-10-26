# Phase 4E — Private Android Pre-alpha

> **Phase 4E CI fix (2026-10-26):** [First Android run 40256554078](https://github.com/Isshhin/stridelab-development/actions/runs/40256554078) failed **before compilation** in `android-actions/setup-android@v3` because its default SDK package list requested removed `tools` (`Failed to find package 'tools'`). Source ZIP CRC, 41/41 manifest and accepted Phase 4D provenance checks passed. Upgraded repository workflow to **`android-actions/setup-android@v4`** with `packages: 'platform-tools'`; no source ZIP, version code or accepted Phase 4D files changed. **Re-dispatch the same [Phase 4E Android workflow](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4e-final-android.yml) on `main`, versionCode `1`.** No APK, emulator or unit-test success is claimed before the next run. This is an SDK setup-only failure, not an app runtime failure.


**2026-10-26: complete Android source candidate packaged; APK build and emulator tests NOT YET RUN.** Phase 4D Final is independently accepted in [GitHub Actions run 40254593565](https://github.com/Isshhin/stridelab-development/actions/runs/40254593565): **273 passed, zero failed/skipped/flaky**, 630/630 manifest and protected engine unchanged.

## Exact source and limitations

- Final Android source archive: `StrideLab-Phase4E-Final-candidate.zip` (387,845 bytes), SHA-256 `d1d061d8be9d9dd752a71a40867e2bc00f0fb15b97d88032c0b95f45e4e1c3b3`; ZIP CRC and **41/41 manifest file hashes** passed.
- GitHub private prerelease tag to create: `phase4e-final-candidate`. Attach the exact ZIP under the exact filename above.
- One [Phase 4E Android acceptance workflow](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4e-final-android.yml) verifies this archive, immutable accepted Phase 4D source, compiles a debug APK, executes JUnit backup checks and Android emulator persistence/permission checks. On success it uploads private APK artifact `stridelab-4e-debug-apk`; optionally a stable signed release APK if private GitHub signing secrets exist. **Do not claim APK exists or was installed before this run passes.**
- Source accepted upstream: `StrideLab-Phase4D-Final-candidate.zip`, SHA-256 `915c9c37e3816291bfc9ab3ead1f37b400711886056a43fd1af101b602cdaa6b`; bundled application HTML differs only by removal of an external Google Fonts request. All 21 assets byte-identical; protected coaching engine SHA-256 `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2` unchanged.
- **4E-A**: Offline Android shell using appassets HTTPS WebView isolation, fixed application ID `io.stridelab.privatealpha`, local resources and no Internet permission, Android Back and native options menu.
- **4E-B**: Versioned manual Android SAF JSON backup/restore of `afterglow2` localStorage, exact string integrity SHA-256, bounded size, schema/app identity validation, atomic replacement and explicit consent. No automatic cloud restore. Training backups contain private plaintext; keep secure.
- **4E-C**: GitHub Android SDK 36/Gradle 8.13 + Android Gradle Plugin 8.11.2 build, unit/emulator tests, private disposable debug APK or optionally *durably signed* release APK using secret-managed keystore; same signing certificate and monotonically increased versionCode mandatory for updates.
- **Not verified**: Android SDK unavailable in authoring container, therefore no local Android compile, actual APK or physical phone. User must perform signing setup and manual phone/Android WebView testing before full device acceptance; package is intended for private pre-alpha only. JSON manual import remains, not Garmin/Health Connect/FIT cloud integration.

## Next action

1. Upload exact ZIP to **private prerelease tag `phase4e-final-candidate`** in `Isshhin/stridelab-development`.
2. Manually run the [official Phase 4E combined Android workflow](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4e-final-android.yml) on `main`; default versionCode `1` for first APK.
3. Examine Android build, unit and emulator test report; repair any failures without touching the accepted Phase 4D source. Download private debug APK for disposable trial, or configure four private GitHub signing secrets for durable update-safe release APK.
4. Complete the physical device backup/restore, offline, upgrade, navigation and display-scale checklist included in the source ZIP. **Only then** record device acceptance and progress to Engine 2.0.

Private repository `Isshhin/stride-lab` (existing production Next.js code) remains untouched.

