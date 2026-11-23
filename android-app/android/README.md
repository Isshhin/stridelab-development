# StrideLab Private Android Pre-alpha

A native offline Android shell around the **exact Phase 4D accepted prototype**. Uses WebViewAssetLoader with the https appassets origin; app includes all HTML and diagram assets. The only HTML delta removes the Google Fonts internet stylesheet so it starts offline (system font fallback). Application JavaScript, including the protected coaching engine, is byte-identical to Phase 4D final.

- Application ID: `io.stridelab.privatealpha` **never change after distributing a signed release**.
- WebView origin: `https://appassets.androidplatform.net/assets/www/index.html` **never change without data migration**.
- Local storage key: `afterglow2`; not synced remotely.
- Minimum Android 8.0 (API 26), target API 35, compiled API 36.
- Native three-dot menu: Back up / Restore / About.
- Backups: Android Storage Access Framework JSON, format v1, 2MiB upper bound. Restoring requires confirmation; writes to one state key atomically and confirms readback.
- No `INTERNET` permission; external source/license links open a browser only on user clicks.
- Automatic device cloud backup disabled: you must make explicit backups before uninstalling, clearing storage, switching phones or factory reset.
- `applicationId`/signature preserved across updates to retain Android app data. Debug builds are signed with a machine-generated debug key; do **not** use them for persistent install-updates across GitHub runners.

## Build on desktop

Open the **`android`** folder in Android Studio with JDK 17 and Android SDK 36. Gradle 8.13 with AGP 8.11.2. If using CLI, use `gradle --version` to check your Gradle environment; then `gradle :app:assembleDebug` (builds debug APK). Do not mistake assembling an APK for real-device acceptance.

## Release signing and private updates

Generate one durable keystore outside the repo, back it up securely. Store base64 keystore as GitHub repository secret `STRIDELAB_KEYSTORE_B64`, and its password/alias/key password as `STRIDELAB_STORE_PASSWORD`, `STRIDELAB_KEY_ALIAS`, `STRIDELAB_KEY_PASSWORD`. The CI workflow builds the release APK *only when all secrets are provided*. Keep the keystore private. Never commit the keystore, embed it in an archive, share it as an attachment, or place it in a public release. Keep the exact same signing certificate and application ID for each update, and increment `versionCode`; signing with a new certificate usually makes in-place updates impossible.

Users can install an artifact-produced private APK with Android package installer after reviewing device security prompts. This project does not automate updates or silently sideload APKs. The release APK is only intended for personal private pre-alpha testing.

## Important limits

Android SDK / emulator are not installed in the local ChatGPT container. Java/Gradle compile, APK signing and physical device tests can *only* be verified by GitHub Actions and the user's phone. The Phase 4D 273-test browser suite is kept in its verified immutable release and is not reimplemented by this shell. No FIT/Garmin/Health Connect integration, backend or Play Store deployment is introduced. Device uninstall/clear-data erases browser storage; manually back up first.
