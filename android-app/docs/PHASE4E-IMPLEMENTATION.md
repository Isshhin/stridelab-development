# Phase 4E — Single integrated private Android pre-alpha

Status: **implementation candidate, not an installable APK or device-accepted build until GitHub Android CI and physical phone tests pass**. Prepared 2026-10-26. No Claude or ChatGPT Work needed.

## Proven upstream

Original private GitHub release tag `phase4d-final-candidate` at `Isshhin/stridelab-development` has confirmed success in [GitHub Actions 40254593565](https://github.com/Isshhin/stridelab-development/actions/runs/40254593565). Its ZIP SHA-256 is `915c9c37e3816291bfc9ab3ead1f37b400711886056a43fd1af101b602cdaa6b`. This Android project bundles only the *accepted* runnable HTML and 21 assets (including manifest and license); the only HTML difference removes an external font request to permit complete offline launch. No activity state, source JS, workout engine, imagery or exercise library is revised.

## Implementation

**4E-A:** Native Java Android Activity (min API 26) with AndroidX WebViewAssetLoader local HTTPS origin, no Internet permission, no file/content access from JavaScript, disabled popups and mixed content, trusted-app-only navigation and explicit external browser credit links. Fixed application ID `io.stridelab.privatealpha`, origin `https://appassets.androidplatform.net/assets/www/index.html`. App label indicates PRIVATE PRE-ALPHA. Android Back attempts existing modal Escape, otherwise asks before exiting.

**4E-B:** WebView DOM storage persists after closing and Android app updates with same app ID, signing certificate and origin (subject to device behavior). Manual SAF Backup and Restore uses versioned JSON `io.stridelab.privatealpha.backup` v1, with <=2MiB cap, fixed `afterglow2` state key, byte-exact `stateJson` snapshots with SHA-256 integrity, safety validation and explicit replacement confirmation. Browser `localStorage` setItem is atomic; import confirms readback and reloads state. Unsupported future schemas, wrong app ID, corrupted JSON and oversized files are rejected without mutation. No automatic cloud backup/device-transfer. Save JSON to a private location; backups are plaintext and contain user training history. Android user may lose everything when uninstalling, clearing app data or doing factory reset without a backup. There is no native server/sync.

**4E-C:** Gradle 8.13 + Android Gradle Plugin 8.11.2 + JDK17 + Android SDK 36. GitHub one-gate workflow validates provenance, compiles debug APK, executes JUnit backup tests, runs Android 35 emulator install/launch/recreate and permission tests, and uploads APK/evidence. Optional signed release APK built only if user supplies one long-lived keystore through GitHub encrypted secrets. A stable private update path requires identical app ID and signing certificate and monotonically increasing versionCode; debug APKs built on separate runner instances generally cannot update one another. GitHub Actions artifacts are private and ephemeral, not automatic OTA updates. **Physical phone acceptance cannot be honestly certified by an emulator.**

## What is NOT implemented

- Garmin, intervals.icu, FIT, Health Connect or network/cloud synchronization (existing 4D offline JSON is retained).
- Silent auto-updates, public distribution, Play Store, background notifications or background training engine execution.
- Device-specific WebView vendor regressions, screen-reader/manual hardware verification.
- Cross-install import of browser `localStorage` from desktop prototype (requires future export adapter). Native backups restore the installed Android app's data only.
- A stable signed release APK until the developer supplies secure signing secrets and Android CI successfully builds it.
- Full database migrations for future schema versions: incompatible backups are rejected, not guessed/converted.

## Testing and honest acceptance

`python3 scripts/verify_phase4e.py /path/to/StrideLab-Phase4D-Final-candidate.zip` checks immutable ZIP, original HTML, protected engine, included asset bytes, Android offline/permission/security posture. Java Gradle and Android emulator tests can only execute in a correctly installed Android SDK environment: **this container does not have one**. Therefore all APK/build/device claims are conditional on the official CI result. Do not mark Phase 4E accepted or distribute as stable before the exact candidate and device tests pass.

## Native device manual protocol

1. Build/install **signed private release** (recommended for lasting data) from private CI artifact, not a differently signed debug APK. Confirm the app opens in airplane mode and source images render with no data usage.
2. Create a workout, modify a generated plan, import a JSON activity, link/unlink it, close app, restart and ensure state remains.
3. Back up via ⋮ menu. Copy the JSON to a secure location. Confirm its envelope includes `format`, `schemaVersion`, `appId`, `stateKey` and `stateJson`, and `stateSha256`; **never upload it publicly**.
4. Change a saved exercise/plan, restore the saved backup, confirm restore warning and expected previous state reappears. Test invalid/future/oversized backups; they must not overwrite state.
5. Rotate the phone, press system Back on an open sheet, use 200% font/system display size, switch apps and return; look for data loss, focus failures and layout overflow.
6. Install a **higher versionCode** signed with the SAME certificate over the installed app, without uninstalling. Confirm saved data remains. Do not clear app data during updates.
7. Test uninstall/reinstall only after securely backing up; confirm no claim is made about preserving WebView data across uninstall.
8. Note the device model, Android version, WebView version, observed issues and signing fingerprint; archive evidence privately.
