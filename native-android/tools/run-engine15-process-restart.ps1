param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$Adb
)
$ErrorActionPreference = 'Stop'
# PowerShell 7 can turn harmless adb stderr into terminating errors.
$PSNativeCommandUseErrorActionPreference = $false
if ($Serial -notmatch '^emulator-\d+$') { throw 'A disposable emulator serial is required.' }
$avd = (& $Adb -s $Serial emu avd name 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $avd -notmatch '(?m)^(?:codex-engine15-(?:clean-20261113|quality-clean-20261118)|Pixel_9)\s*$') {
    throw 'The expected disposable Engine 1.5 AVD is not selected.'
}
$package = 'io.stridelab.privatealpha'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$class = 'io.stridelab.privatealpha.Engine15ProcessRestartTest'
$run = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
foreach ($scenario in @('draft', 'approved', 'equal', 'completed', 'feedback', 'accepted', 'dismissed', 'activity-label')) {
    # Fixture IDs accept lowercase ASCII alphanumerics only (scenario may contain a hyphen).
    $fixture = ($scenario -replace '[^a-z0-9]', '') + $run
    $seed = (& $Adb -s $Serial shell am instrument -w -r -e class "$class#prepare" -e fixture_id $fixture -e scenario $scenario $runner 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $seed -notmatch 'OK \(1 test\)') { throw "Prepare failed for ${scenario}: $seed" }
    & $Adb -s $Serial shell am force-stop $package
    if ($LASTEXITCODE -ne 0) { throw "Force-stop failed for $scenario" }
    $afterStop = (& $Adb -s $Serial shell pidof $package 2>&1 | Out-String).Trim()
    if ($afterStop) { throw "App process remains after force-stop: $afterStop" }
    $ErrorActionPreference = 'Continue'
    try { $launch = (& $Adb -s $Serial shell monkey -p $package -c android.intent.category.LAUNCHER 1 2>&1 | Out-String); $launchCode = $LASTEXITCODE }
    finally { $ErrorActionPreference = 'Stop' }
    if ($launchCode -ne 0 -or $launch -notmatch 'Events injected: 1') { throw "Launcher failed for ${scenario}: $launch" }
    $appProcessId = ''
    for ($attempt = 0; $attempt -lt 40 -and -not $appProcessId; $attempt++) {
        Start-Sleep -Milliseconds 500
        $appProcessId = (& $Adb -s $Serial shell pidof $package 2>&1 | Out-String).Trim()
    }
    if (-not $appProcessId) { throw "App did not relaunch for $scenario" }
    $verify = (& $Adb -s $Serial shell am instrument -w -r -e class "$class#verifyAfterRestart" -e fixture_id $fixture $runner 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $verify -notmatch 'OK \(1 test\)') { throw "Verification failed for ${scenario}: $verify" }
    Write-Output "$scenario`: prepare PASS, process absent after force-stop, launcher PID $appProcessId, offline verification PASS"
}
