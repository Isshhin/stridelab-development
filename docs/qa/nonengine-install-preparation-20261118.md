# Non-engine Android QA installation preparation

The side-by-side Debug profile uses `io.stridelab.privatealpha.qa`, version code 37 for this test, and the local Android Debug signer. The QA build type overrides both Engine 1.5 fields to false. The normal package remains `io.stridelab.privatealpha`. The application ID also scopes the Share Studio FileProvider authority. Version code 37 does not establish signer compatibility or data migration safety for the existing version 36 installation.

The candidate is stacked on PRs #143–#162 (#145 is an issue). The Engine 1.5 Threshold checkpoint `9a279117` is an ancestor of this candidate and is absent from the inspected `origin/dev` and `origin/main`. Keep the draft stack unmerged. Any future production integration needs an independently reviewed port of the non-engine changes onto an approved production base.

## Windows PowerShell 5.1

From the reviewed QA worktree:

```powershell
Set-Location "$env:USERPROFILE\stridelab-nonengine-full-qa\native-android"
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jbr-21.0.11"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$qaArgs = @(
    '-PstrideEngine15Active=false',
    '-PstrideEngine15IndependentRunningActive=false',
    '-PstrideIsolatedQaInstall=true',
    '-PstrideVersionCode=37'
)
& .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest @qaArgs
if ($LASTEXITCODE -ne 0) { throw 'QA build or tests failed.' }
$apk = (Resolve-Path '.\app\build\outputs\apk\debug\app-debug.apk').Path
$tools = Join-Path $env:ANDROID_HOME 'build-tools\36.0.0'
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
$manifest = & (Join-Path $tools 'aapt.exe') dump badging $apk
if ($LASTEXITCODE -ne 0 -or $manifest[0] -notmatch "^package: name='io\.stridelab\.privatealpha\.qa' versionCode='37'") {
    throw 'Unexpected APK package or version.'
}
& (Join-Path $tools 'apksigner.bat') verify --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
Get-FileHash -Algorithm SHA256 $apk
```

For a future first install, after reviewing the QA decision, use the **validated artifact** and repeat the package, signer and hash checks above on that file. In Android Wireless debugging, obtain the separate pairing and connection ports. Do not use these commands for the normal package or to update an existing QA copy.

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$apk = (Resolve-Path '.\build\nonengine-qa\StrideLab-QA-v37.apk').Path
$expectedHash = '534B5A3965F61BBE90659CD80C9B742940FA8552D404D2628938DAA290B98FCE'
if ((Get-FileHash -Algorithm SHA256 $apk).Hash -ne $expectedHash) {
    throw 'APK does not match the emulator-tested artifact.'
}
$pairAddress = Read-Host 'Wireless debugging pairing IP:port'
$targetSerial = Read-Host 'Wireless debugging connection IP:port'
& $adb pair $pairAddress
if ($LASTEXITCODE -ne 0) { throw 'Pairing failed.' }
& $adb connect $targetSerial
$state = & $adb -s $targetSerial get-state
if ($LASTEXITCODE -ne 0 -or $state -ne 'device') { throw 'Selected device is unavailable.' }
& $adb -s $targetSerial shell getprop ro.product.model
$installed = & $adb -s $targetSerial shell pm list packages io.stridelab.privatealpha.qa
if ($LASTEXITCODE -ne 0 -or $installed -match '^package:io\.stridelab\.privatealpha\.qa$') {
    throw 'Package check failed or a QA copy is already installed.'
}
& $adb -s $targetSerial install $apk
if ($LASTEXITCODE -ne 0) { throw 'Side-by-side QA install failed.' }
```

The new QA copy uses separate local data. Cross-package migration, real Garmin sign-in and personal backup restore require separate acceptance.

## Candidate evidence

- Base `f9191517fc5ba08e14b5e9a26412b8067fc861a2`. Built normal and isolated Debug APKs with JDK 21 and Gradle Wrapper. Kotlin, Java, Android resources and the isolated instrumentation APK compiled. The manifests report `io.stridelab.privatealpha.share-studio` and `io.stridelab.privatealpha.qa.share-studio`, respectively. The isolated generated BuildConfig still held both Engine 1.5 flags false when both were deliberately requested as true.
- Full JVM: 1,125 passed, 3 skipped, no failures or errors. Android on the disposable Pixel 9 profile, API 35, serial `emulator-5556`: repository/storage/FIT/cache/coverage shard 65 passed. The seeded legacy upgrade probe is a separate build and was not executed. Share Studio shard 18 passed, including a live OpenFreeMap snapshot in all three ratios and transparent exports. The QA signer certificate SHA-256 is `62725b9237a0a7f182d99503b16f20d7b2f47be5f99ce07fc59b5e30cd8dd270`.
- Removed two invalid Compose test imports and pointed archive scrolling assertions at real semantics nodes. The clipped screenshot came from a retired test-only Today hero, not the current Today screen. The responsive test now mounts the current Today screen and verifies that the planned workout action reaches the saved session.
- Preserve the checked artifact and raw logs under `native-android/build/nonengine-qa`. Live tile rendering and synthetic offline coverage geometry are separate results. A real downloaded-region offline snapshot, real Garmin authentication and sync, long-history performance benchmarking, and the seeded version-36 in-place migration are still unverified. The QA package is for synthetic side-by-side use. Do not replace the original application or restore personal data based on these results.

## Legacy UI audit and disposition

| Surface | Reachability and change |
| --- | --- |
| Today, Training, More | These are the three production tab roots. Removed the optional diagnostic tab content, unused old Today hero, empty and active variants, the pre-unified Training schedule, old home plan card and obsolete dock icons. Retained the schedule grouping helper and its JVM tests. |
| Standalone Activities | Removed the duplicate history, sync status and logged-workout UI. Retained `activities` only as a redirect to Training. The analytics result row remains because current history uses it. |
| Activity Detail | Preserved the live scrolling sheet, MapHero and Share Studio entry. Removed the alternate matte components, retired overview, tab and lap layouts, sample graph screen and unused source glyph. |
| More | Removed the duplicate Garmin page and its callbacks. Preserved the live Garmin sheet, Preferences, About and archive. Both `more/garmin` and `more/data` redirect to the current More root. |
| Other unreachable UI | Removed PracticeBuilder, old weekly dashboard and wellness UI, unused plan frequency and day chips, Today stat and card helpers, section header and old running legend. Removed seven unreferenced Today/Activity illustrations and old icons. |
| Historical WebView | Normal and side-by-side QA APKs exclude the obsolete activity class and web bundle. The legacy project and the explicit seeded upgrade test retain access to the historical implementation when the existing upgrade runner argument is enabled. |

Deleted only the UI tests that rendered retired layouts or diagnostic placeholders. The three-tab navigation tests now exercise real roots; the responsive workout test uses current Today. Stored records, archive decoders, account partitioning and database migrations remain in place. Redirect instrumentation enters each legacy route directly; a force-stop relaunch checks process restart separately. Cross-version restoration from a real version-36 saved stack is still outside this acceptance run.

Final emulator totals: 130 passed, zero failed or skipped in the selected shards (65 storage and repository, 39 navigation and UI, 18 Share Studio, 4 each at 360dp and 412dp with font scale 1.3). The seeded legacy probe compiled separately and was not executed. Actual launcher captures are in `current-shell/native-ui-evidence` with a three-tab contact sheet at `current-tabs.png`. The final isolated package relaunched after force-stop from PID 9715 to PID 9763. This confirms a process restart, not cross-version restoration of the old navigation stack.
