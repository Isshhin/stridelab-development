# Native UI/UX Reconstruction — 2026-10-26

Priority: finish and validate native presentation before new engine or feature work.

## Source and design provenance

Recovered remote feature/native-real-data-phase2-20261026 at 12ace6bd421d8e71366f657b90b41f61bd496dcd, including f2079bf. Recovery checkpoint checkpoint/native-phase2-verified-f23719ea remains unchanged at f23719eafae60749951df28e4676cc9605d1f721. Work continues on the same feature branch with additive commits.

Visual source: android-app/android/app/src/main/assets/www/index.html, accepted Phase 4D Final as packaged in Phase 4E; see PHASE4E_STATUS.md for immutable archive provenance. Inspected actual CSS and rendered HTML, not the earlier rejected Phase 4B candidates. Near-black #050506, warm white #F4F1EA, signal lime #D6FF4F; blue running, orange strength, purple other disciplines. Inspected original f23719ea CI screenshots from run 40308952951, artifact 12644227850. No repository AGENTS.md or separate design instruction file was present.

## Implementation

Fully native Compose Home, Activities, details, Settings, authentication and navigation share custom type, layered surfaces, borders, rounded cards, discipline signals and recorded metric tiles. Latest activity has dashboard priority. Empty history and sign-in states use branded explanatory cards. Settings separates account, legacy backup and validation information. Loading and offline states remain explicit; result messages announce politely to accessibility services. Scrollable screens honor IME resize, native Back, minimum platform touch targets and large-text metric stacking; content width is bounded for larger screens. Existing Navigation Compose transitions follow system animation settings.

Vector brand mark uses two forward stride strokes. Adaptive launcher foreground/background, Android 13 monochrome themed icon and Android 12 splash use matching geometry. Application ID is unchanged; versionCode 5 / 0.4.0-native-signal. No fabricated workouts, derived readiness, routes, maps or sample charts. Count means cached activities only; not a lifetime total. Source records remain the authority for metrics.

Unchanged: native authentication/gateway, account-scoped SQLite, Keystore, background work, source synchronization, backend/schema contracts, legacy HTML/assets/engine, private storage and backup entry. No uninstall/data clearing, no main merge, no public release.

## Verification and acceptance boundaries

NativeVisualTest adds immutable synthetic activity/card/metric screenshots, click behavior, absent heart rate and narrow 200% text reflow. Existing navigation, password restoration, repository, encryption, corruption retention, update and forced-process/offline probes remain required. UI fixtures cannot establish athlete integration. CI evidence and visual review are recorded in the completion report after execution.

Real-account Phase 2 acceptance remains pending: phone sign-in, genuine history completeness, actual provider sync and daily athlete use. No genuine account credentials were supplied or used. The CI ephemeral debug key does not establish compatibility with an existing phone installation. Preserve the installed package and compare signing identity with check_apk_update.py; do not uninstall or clear data to bypass an update rejection.

## Continuation

Keep engine and new feature development deferred. Review the final private APK and screenshots, then test real-account behavior directly on the device via its secure sign-in UI. Original signing identity is required for an in-place phone update. Native source credential setup/backfill, legacy conversion, workout execution, sample series and maps remain outside this presentation milestone.
