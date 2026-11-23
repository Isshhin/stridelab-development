# Private QA installation (non-engine, opt-in)

This procedure builds a **separate** StrideLab QA application. It is intended for manual, disposable acceptance of features such as Share Studio and activity analytics, **not** a migration of the owner's existing installation.

## Identities and safety

| Build | Android application ID | Launcher label | Storage |
| --- | --- | --- | --- |
| Ordinary debug / release | `io.stridelab.privatealpha` | StrideLab | Existing owner data |
| Debug with `-PstrideIsolatedQaInstall=true` | `io.stridelab.privatealpha.qa` | StrideLab QA | Separate, initially empty |

The opt-in Gradle property only applies to **debug** builds. The normal application ID, version-code policy, signing configuration, backup behavior, Engine 1.5 flags, and release routing are unchanged. The Share Studio FileProvider uses `${applicationId}.share-studio` so its authority also differs for QA.

**Never assume QA can access the original installation's Garmin imports, offline maps, credentials, database, or backups.** A successful QA install proves only installation isolation. Backups and their restore/migration compatibility require independent testing before owner data is moved.

## Build (PowerShell, from the repository root)

```powershell
cd .\native-android
.\gradlew.bat :app:assembleDebug -PstrideIsolatedQaInstall=true -PstrideEngine15Active=false -PstrideEngine15IndependentRunningActive=false
if ($LASTEXITCODE -ne 0) { throw "QA build failed" }
```

The generated APK is `native-android/app/build/outputs/apk/debug/app-debug.apk`, with application ID `io.stridelab.privatealpha.qa`. An isolated QA install **does not require raising versionCode to match the owner's separate production/private-alpha application**.

## Explicit wireless ADB target

Select the device ID from the current `adb devices -l` output. Never use an unqualified `adb install` when multiple devices (including an emulator) are connected.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
$serial = "PASTE_THE_SELECTED_DEVICE_SERIAL"
& $adb -s $serial install -r ".\app\build\outputs\apk\debug\app-debug.apk"
if ($LASTEXITCODE -ne 0) { throw "QA installation failed" }
& $adb -s $serial shell pm path io.stridelab.privatealpha.qa
& $adb -s $serial shell pm path io.stridelab.privatealpha
```

The last two commands should show two different installed packages when the original app is installed. These checks do not inspect or modify activity data.

## Acceptance gate before any normal-app update

1. On the Android emulator, compile the QA build and check that the distinct package, launcher label, and FileProvider authority are correct.
2. Install QA alongside an existing normal StrideLab installation. Verify that installing/reinstalling QA does not change the normal app's version or activities.
3. Test clean-start behavior, permissions, Share Studio PNG saving/sharing, and map tiles. Note that activity fixtures or deliberately imported QA test data are needed in its empty storage.
4. Exercise backup/export and restore on **disposable QA data** and verify data counts, identifiers, deduplication, source provenance, and version compatibility. Never overwrite an unverified original backup.
5. Separately verify signing compatibility, migration behavior, and reliable rollback before proposing any update to `io.stridelab.privatealpha`.

**Automated Android QA remains emulator-only.** A physical-phone QA installation is a separate owner-authorized manual acceptance step.

The QA package can be removed independently in device settings when it is no longer needed. Never uninstall or clear the original `io.stridelab.privatealpha` app to work around a version-code or signature error.
