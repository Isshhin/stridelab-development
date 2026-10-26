# Private device validation — gated by signing compatibility

This procedure is still pending. No physical device or genuine-account native login has been tested in this session. Do not paste passwords, JWTs, signing keys or refresh tokens into chat, source files or issue reports.

## Preserve the installed prototype first

1. Keep `io.stridelab.privatealpha` installed; do not uninstall it or clear its data.
2. In its App options menu choose **Back up my data**. Save the `stridelab-backup.json` outside app storage and keep a private second copy. Preserve the original file unchanged. The backup has the legacy format/schema and exact-state checksum; native record conversion is not implemented.
3. If backup export fails, stop replacement plans and retain the installed app. Do not assume synchronization includes local-only workout logs or imported prototype data.

## Read-only APK comparison

With Android SDK platform-tools/build-tools available, compare an approved candidate against the installed package:

```sh
python3 native-android/tools/check_apk_update.py --candidate /private/path/stridelab.apk --serial DEVICE_SERIAL --aapt /sdk/build-tools/35.0.0/aapt --apksigner /sdk/build-tools/35.0.0/apksigner
```

The tool reads `pm path`, pulls only the installed base APK into a temporary directory, and compares application ID, version code and all signer SHA-256 digests. It never installs, uninstalls, clears data or signs. `--installed-apk /private/path/old.apk` supports an existing APK copy, but does not prove that the copy matches the APK currently on the phone.

`metadataChecksPassed: true` means those metadata checks pass; `physicalUpgradeValidated` remains false. This does not replace a backup or monitored device upgrade test. A differing signing certificate blocks this tool's recommendation. Signing-key rotation/lineage is not evaluated; keep the old app and obtain a verified signing path. No alternate app ID, uninstall/reinstall or destructive migration is authorized as a workaround here.

CI's legacy/native APKs share an ephemeral debug key within a job. That proves the tested emulator update path, not compatibility with the user's existing installation. A release candidate needs the original compatible signing key supplied through secure build configuration; never commit it. Do not treat a fresh debug build as an update-safe candidate.

## Genuine-account workflow to validate

Only after backup/signing checks and a suitable private test installation path are established:

1. Record the installed app version and a few existing prototype records. Verify those records and its backup tools remain after the update.
2. Sign in through the native app using the existing StrideLab account. Confirm the account identifier and compare several activity titles, source integrations, dates and units with the existing backend/source service.
3. Refresh history. Record the cached count and last successful refresh time. Check older dates, equal-timestamp activities, missing HR, elapsed versus moving time and maximum HR. Strength/training details must not display running pace.
4. In Settings use **Check connection**. Confirm genuine Intervals connection state and its check timestamp. Synchronize only if connected; compare new/updated records and ensure a second refresh does not duplicate IDs.
5. Close and reopen the app. Disable Wi-Fi/mobile data, reopen again, and check cached history and activity details. Record missing metrics honestly; no demo history should appear.
6. Force-stop the app using Android Settings, reopen offline, and repeat the checks. Do not clear app data. Interrupted-sync testing must retain previously saved pages and the previous completed-refresh timestamp.
7. Restore connectivity and refresh. Verify no duplicates, unchanged source units and a new successful refresh timestamp only after completion.
8. Verify logout hides the account/history and removes its saved local session even when the service is unreachable. Reauthentication to the same account must restore its retained cache. Do not claim that offline logout immediately revokes a server-side access token.

Record pass/fail for each step, device/Android version, candidate and installed certificate digests, app version and relevant activity IDs privately. Do not include credentials in the report. Any remaining genuine-account/device step keeps the real-world acceptance milestone incomplete.
