# Active continuation update — Phase 1 native reconstruction

The later master directive requires complete accepted Phase 4E native parity, with functional Engine 1.0 inside reconstruction. Advanced Engine 2.0 and benchmarking follow later. Read `NATIVE-PHASE1-PARITY.md` and `ENGINE1-BASELINE.md` first. The historical stage list below describes the previous verified S1A/S2A baseline; it is not the current implementation status.

Current working increment adds four roots, accepted Bricolage typography and licensed offline library assets, a versioned deterministic multi-discipline generator, reviewed draft/approval, calendar, workout records and private feedback. Use exact commit/CI evidence before promotion. Plans and logs are local owner-scoped transactional records; plan cloud synchronization is not implemented. Never claim the whole engine or reconstruction is accepted from generation-only tests. Preserve all checkpoint branches and inspect uncommitted work before changing the checkout.

## Earlier continuation record (retained)

# Continue Native Feature Reconstruction 1.0 in this master project

Repository: Isshhin/stridelab-development. Master branch: feature/native-reconstruction-1-20261026. This is an incremental reconstruction, not completion of all five requested stages. Do not restart or merge main. The upstream feature/native-real-data-phase2-20261026 remains at 12e0594e59244cd21d73b24deed4f021434d467f; its native navigation, native UI and Phase 2 checkpoints remain unchanged.

## Implemented working increments

S1A: explicit authenticated source-sample/lap loading from activity detail; joined owner filtering and local validation; transactional, owner-scoped offline detail cache; pace/HR/elevation time-series with units, accessible descriptions, missing/gap handling and chart direction; real-coordinate GPS trace without paid/network map tiles; recorded laps; cautious same-provider/same-discipline historical median pace comparison. No fabricated coordinates, streams, kilometer splits or fitness conclusions.

S2A: Home's seven-local-day recorded duration/running-distance totals, daily bars, distinct active-day count, discipline distribution, previous-seven-day comparison and partial-data disclosures; real optional wellness retrieval and offline persistence, source/unit/date retention, distinct HRV methods and honest missing-data state. No readiness scores or training adjustments inferred from these values.

Signing: private post-gate developer-signing workflow with temporary key cleanup, secret-only passwords, certificate fingerprint pin and explicit unknown installed-phone compatibility. The disposable debug build remains separately labeled. No durable key availability, signed release artifact or physical upgrade acceptance is claimed without its actual successful opt-in job and device check.

## Exact next implementation work

1. Finish S1B against a securely signed-in athlete: compare cached source summaries/samples/laps to the real provider, verify histories and sensor missing-value behavior. Confirm deployed provider route-fallback action before adding it; the inspected Intervals importer supplies null GPS coordinates. Keep the existing genuine-coordinate trace and no-GPS state. Add chart inspection/selection, useful axes and route interactions, with source provenance and offline failure tests. Evaluate bounded sample memory/render performance on hardware; do not mistake synthetic fixtures or anonymous HTTPS checks for athlete acceptance.
2. Finish S2B: refine compact trend/distribution interactions, recent-activity browsing and wellness freshness presentation from authentic source records. Distinguish an empty cache from a known zero. Retain local-date, timezone/DST, partial metric and future-record tests. Do not infer recovery from isolated HRV/sleep values.
3. Stage 3 vertical slice: native goal/discipline/day-count plan construction with editable history-informed mileage, user-selected quality days and exactly one long-run day. Persist a real dated calendar and planned/suggested/completed status. Add conflict handling, missed-session reasons, explained bounded adaptations and owner-isolated offline durability. Inspect actual backend plan contracts before any remote write. Reuse tested transactional TrainingStore/outbox paths; add tested non-destructive migrations only if needed. Never overwrite actual workouts with suggestions or silently intensify training.
4. Stage 4 vertical slice: port accepted executable workout/library data from preserved Phase 4E assets, keeping licensed images and attribution. Include running, strength, calisthenics and conditioning descriptions, progression/equipment alternatives and accessible native browsing/detail. Add working sets/reps/load entry and workout execution/logging with process-restart durability, owner separation, duplicate-safe reconciliation and explicit conflict handling. Reference-only guides must remain clearly distinguished from executable workouts. Do not fabricate image provenance or populate production history with examples.
5. Stage 5 vertical slice: integrate concise paragraph-form descriptive coaching across the actual histories, plan, logs and valid wellness evidence. Explain comparison eligibility, recommendations, uncertainties and conservative safeguards. Do not label heuristics as advanced Unified Coaching Engine 2.0.

## Required gates for every increment

Keep NativeTabRootTest, ActivityIntelligenceUiTest, NativeVisualTest and TrainingRepositoryTest; retain successful sync silence, actionable offline/errors, root tabs with saved list position, Back-to-Activities from either entry point and all existing lifecycle/auth/SQLite/Keystore/legacy-update probes. Keep package io.stridelab.privatealpha. Test data belongs only in synthetic fixtures/disposable emulators. Never uninstall or clear the user's phone data, publish a release, change package identity or replace core Compose UI with WebView.

Use JDK 17, Gradle 8.13, Android SDK 36 and build-tools 35.0.0. From the repository root: gradle -p native-android :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest. Run Python unittest discovery under native-android/tools/tests. The native-android Actions workflow runs the complete emulator/legacy/update/offline process gate and creates private APK/source/evidence artifacts. Preserve a new uniquely named checkpoint branch only after that exact increment passes. Do not rewrite existing checkpoints.

## Signing and secure athlete/device acceptance

Configure STRIDELAB_KEYSTORE_BASE64, STRIDELAB_STORE_PASSWORD, STRIDELAB_KEY_ALIAS and STRIDELAB_KEY_PASSWORD through secure repository Actions secrets, and public STRIDELAB_EXPECTED_SIGNER_SHA256 through repository variables. Never send credentials or keys in chat. Use the original installed signing identity if available; a new developer key cannot update an existing app signed with another key. Keep an encrypted/permission-restricted local keystore and separate secure backup; never commit either. Opt-in workflow_dispatch durable_signed_build uses signed_version_code >=8, which must also exceed the installed version. Its job follows the complete CI gate, fails without secrets/pin, and uploads only private Actions artifacts. No public release is authorized.

Before any phone update, preserve the installed app and its legacy backup. Run the read-only installed/candidate package/version/all-signer comparison; unknown or mismatched identity is not permission to uninstall or clear data. Secure sign-in happens in the actual app, followed by genuine history, provider sync, detail metrics, offline reopen/reconnect and physical-display/TalkBack/performance acceptance. No authentication impersonation or chat credentials.

## Broken-session recovery

Inspect git status before changing anything. Fetch origin, inspect the master branch and verified checkpoints, and recover the desired exact tested commit into a fresh checkout/worktree without resetting someone else's work. Re-read docs/NATIVE-FEATURE-RECONSTRUCTION.md and this file. Distinguish executable tested source from later documentation commits. GitHub artifacts expire after 30 days; checkpoint branches and source manifests provide durable recovery. Re-run only checks justified by new changes or unresolved failures, and keep any failed acceptance evidence visible.

## Verified resume anchor

Resume from checkpoint/native-reconstruction-s2a-final-e13ce3d at e13ce3d54f5b442ed19581b6d80b77e2467780b5. CI 40333913342 is successful (49 JVM / 50 emulator / 12 Python tests, legacy seed and all four probes passed). Exact source ZIP SHA-256 is 23d281f68f0d34f3e401bd7e3fb0d0c7e61ed5a043ec016696c01727bcbfc37e; all 119 payloads match Git. Later documentation-only head is safe to continue from after verifying its diff. The intermediate legacy-frame startup failure in run 40333093652 is retained; do not claim that all runs were failure-free. Full Stage 1/2 and human acceptance remain open, and Stages 3–5 remain pending.
