# Native UI reconstruction — verified checkpoint

Date: 2026-10-26. Native UI/UX Reconstruction is implemented before new engine/features. Genuine-account Phase 2 and physical-phone acceptance remain pending.

## Exact verified source

- Source: 092d1f6884cfac58271d11f6a07af28a436f708f.
- Recovery: checkpoint/native-ui-verified-092d1f6, pinned to that exact tested commit.
- [Successful final CI 40321549037](https://github.com/Isshhin/stridelab-development/actions/runs/40321549037).
- [Private installable APK package](https://github.com/Isshhin/stridelab-development/actions/runs/40321549037/artifacts/12649602076).
- [Source, reports and 17 screenshots](https://github.com/Isshhin/stridelab-development/actions/runs/40321549037/artifacts/12650236021).
- Source ZIP: a79c86c9e666d2a8a8440258304f9bb48e7325a4bd50749f5ff7bf43415c809a. All 103 manifest entries and their exact Git blob bytes independently verified after download.
- APK: 7fe8831ac1afe8a47debe403e81496200864faebaa5994b5c42ed8a05e5cd8a9.
- Certificate SHA-256: 2e066d09b344efb4dc2dd463e17c429357cef05f508b560813b3e28b8415bd06. Downloaded APK independently passes apksigner verification.
- Identity: io.stridelab.privatealpha, versionCode 5, 0.4.0-native-signal.

CI artifacts expire after 30 days. Git recovery is durable. This documentation commit follows the tested source without modifying executable code.

## Actual verification

32 JVM tests and 41 API 35 emulator tests passed, zero failures/errors/skips. The legacy seed passed once; all four explicit probes passed (anonymous HTTPS/RLS boundary, synthetic seed, forced offline process restart/details, reconnection/deduplication). Nine APK compatibility/packaging tests passed. The seven UI tests are included in the 41-test suite, not additional integration acceptance.

Screenshot tests cover populated/empty screens, missing metrics, navigation, real callback wiring via synthetic actions, source settings, loading/offline/failure states, narrow 200% text and bounded wide layout. Synthetic fixtures exist in test sources only. Capture waits for the rendered frame and uses Android display screenshots because direct Compose-root captures omitted retained graphics layers. User-style visual review inspected the actual legacy HTML, earlier emulator captures, and final native captures, then refined insets, form focus stability, activity cards and metric alignment. No obvious aesthetic regression remained in reviewed primary states.

Core color-pair calculations: primary text/background 18.06:1; muted text/card 8.83:1; discipline text/card at least 8.65:1; signal button ink/background 17.06:1. This is not full physical-display or screen-reader acceptance.

## Preserved lineage and contracts

Recovered feature/native-real-data-phase2-20261026 from 12ace6b, including f2079bf. Original checkpoint/native-phase2-verified-f23719ea remains unchanged at f23719eafae60749951df28e4676cc9605d1f721. No existing commits or tests were removed. Native gateway, repository, metric contracts, workers, backend configuration, shared SQLite/Keystore/legacy code and legacy HTML/assets are unchanged. Password UI state additionally clears on background/disposal. Native auth fields remain composed together through keyboard transitions.

The accepted visual source is Phase 4D Final packaged into Phase 4E, not the rejected earlier Library candidates. The native visual system uses its black/warm-white/signal palette, cohesive typography, layered cards, native bottom navigation and branded vector icon/splash. Recorded-duration bars use actual cached elapsed durations only; no sample route, readiness score or fabricated workout is displayed by the native UI.

No main merge, public release, database/schema change, uninstall/data clearing, package-ID change, engine implementation or project restart occurred.

## Install and continue

The private APK uses the CI run's ephemeral debug key. Its same-key emulator upgrade tests do not establish compatibility with the phone's installed APK. Use an isolated emulator now; compare installed/candidate certificates using the bundled read-only check_apk_update.py before a phone update. Retain the installed app if Android rejects the update; do not uninstall, clear its storage or change package ID. Original signing identity is still required for an in-place compatible update.

Pending factual acceptance: actual athlete login, genuine history completeness, real Intervals sync, offline phone reopen, physical update and daily athlete use. TalkBack, physical gesture/display/keyboard behavior and hardware performance remain manual device checks. The wide fixture is a density/layout simulation, not a physical tablet result. No genuine athlete credentials were supplied or used.

Continue on the same feature branch; use the verified source checkpoint for recovery. Review the private APK through its secure sign-in UI. Keep engine/features deferred until the athlete reviews this native milestone. Do not mistake synthetic UI screenshots or anonymous HTTPS checks for genuine authenticated integration. See NATIVE-UI-RECONSTRUCTION-20261026.md for design implementation and NATIVE-PHASE2-DEVICE-VALIDATION.md for real-account checks.
