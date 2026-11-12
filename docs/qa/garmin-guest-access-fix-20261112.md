# Garmin Connect without StrideLab sign-in — implementation handoff (2026-11-12)

## Goal

Fix the regression where **More → Garmin Connect** redirects signed-out users to StrideLab authentication. Garmin Personal API authorization remains required, but a StrideLab cloud account must not be required for local Garmin connection, sync, history, FIT/GPS, title edits or automatic checks. Preserve the AMOLED black/lime UI and existing GPS/plan behavior.

## Implementation

- Use one persisted local Garmin owner (`local-garmin-<UUID>`) in `TrainingRepository`, separate from optional cloud user ID in `TrainingState.account`.
- Keep legacy cloud activity caches and other users' data account-isolated. Merge only the device's explicit local Garmin records into displayed history. After cloud sign-out, hide cloud records while keeping local Garmin history visible.
- Preserve legacy encrypted Garmin authorization when a currently authenticated user signs out by copying its credentials to the local Keystore namespace **only when the local vault is empty**. Never delete or log the original credentials.
- Allow a Garmin source to sync only for the local owner or the current authenticated legacy owner. Retain source ID, original activity and FIT payload; no replacement of existing summaries. For local recordings, read cached FIT via the existing chunked API.
- More opens the same Garmin modal signed in or signed out. In signed-out mode, suppress all cloud-backup controls and show why optional cloud sign-in is separate. On device, Today and Training show local Garmin history without exposing cloud records.
- Cloud backup requires StrideLab sign-in and per-cloud-account opt-in. Upload only validated, copied payloads with cloud owner substituted; don't rewrite local source data. Cloud restore is insert-only into the local Garmin namespace with revalidation. Keep backup upload receipts and restore cursors scoped to the cloud account.

## Required owner-run validation (Pixel 9 only)

The source changes have **not yet been built or executed on Android**. Main is unchanged. On Android Studio's Pixel 9 API 35 emulator:

```powershell
cd E:\stridelab-validation
git fetch origin
git merge --ff-only origin/dev
cd native-android
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest "-PstrideVersionCode=30" --no-daemon --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest "-PstrideVersionCode=30" '-Pandroid.testInstrumentationRunnerArguments.class=io.stridelab.privatealpha.TrainingRepositoryTest,io.stridelab.privatealpha.TracksideGarminModalTest,io.stridelab.privatealpha.GarminCredentialVaultTest,io.stridelab.privatealpha.GarminAutomaticSyncPreferencesTest,io.stridelab.privatealpha.TracksideActivityScrollTest,io.stridelab.privatealpha.TracksideParityStatesUiTest' --no-daemon --console=plain
```

Manual acceptance: launch without StrideLab login; More → Garmin Connect opens a modal, permits Garmin API key entry, import and sync; activity appears on Today and Training, opens Activity Detail; renamed title survives restart and repeat sync. Signing into StrideLab must not replace local Garmin or auto-enable cloud backup. Signing out must hide cloud account records but leave local Garmin activities visible. Verify automatic Garmin sync has no StrideLab sign-in gate, no regressions in activity map, and cloud controls are available only with a valid cloud login and opt-in.

## Restrictions / non-goals

- Do not edit `main` or promote until explicit owner approval.
- Do not uninstall or clear the data-bearing phone app; tests are on Pixel 9 emulator.
- Original cloud-only activity rows remain in their previous cloud account namespace and are not bulk-migrated. If a user signs out before importing locally, those cloud-only rows remain hidden until Garmin re-sync; they are not deleted.
- This patch targets Garmin history and cloud-backup isolation; other account-dependent coaching/plan workflows have not been converted to a fully local profile.
- No real Garmin API key, network source or personal data was accessed during source edits.

Report actual compile/test results, exact failing assertions if any, and do not mark ready for main without device acceptance.
