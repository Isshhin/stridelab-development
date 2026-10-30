# StrideLab — Android-first private pre-alpha

StrideLab is an **offline-first Android fitness/coaching application** built with Kotlin and Jetpack Compose. Running, strength, calisthenics and conditioning are intended to share a unified athlete history and approval-first coaching workflow. It is **not** a public release or a medically validated coaching service. The web prototype is maintenance-only.

> **Source navigation warning:** this repository's historical `main` branch is **not** the canonical verified Android integration head. Do not create a new Android feature or APK from `main` by assumption.

## Verified Android checkpoint

- Branch: [`checkpoint/native-guided-multisession-verified-25be8f3`](https://github.com/Isshhin/stridelab-development/tree/checkpoint/native-guided-multisession-verified-25be8f3)
- Exact SHA: `25be8f3828374d1e539c7e825afa20fbcb652b56`
- [Integration evidence / PR #3](https://github.com/Isshhin/stridelab-development/pull/3): guided workout player and multi-session plan/calendar, passing exact-head native CI, emulator/offline tests and a private APK artifact.
- APK at that checkpoint: `io.stridelab.privatealpha`, version `0.6.6-guided-workout` (code `15`). Its ephemeral CI signing key is **not automatically compatible** with an app already installed on a phone. Do not uninstall or clear old app data to force an update.

## Current draft queue (snapshot: 2026-10-30)

- [#27 — QA guards, official FIT decoding and offline source cache](https://github.com/Isshhin/stridelab-development/pull/27): locally/emulator-tested integration candidate; not a live Garmin integration or finished import UI. Exact-head GitHub CI is blocked by a job-start/account issue.
- [#28 — Exercise Library 2.0](https://github.com/Isshhin/stridelab-development/pull/28): independent 66-record offline companion, with preserved old catalog; new records are not yet offered automatically by coaching or the native screens. Exact-head GitHub CI remains blocked.
- [#29 — protect retained opaque plan IDs during backup restore](https://github.com/Isshhin/stridelab-development/pull/29): isolated draft based on #27 with source guard and real-SQLite regression test; **not Android-tested or merged**. See the [repository-wide audit](https://github.com/Isshhin/stridelab-development/blob/fix/native-opaque-backup-id-guard-20261030/docs/reports/REPOSITORY-SOURCE-REVIEW-20261030.md).
- [#13–#20](https://github.com/Isshhin/stridelab-development/pull/20) and [#25](https://github.com/Isshhin/stridelab-development/pull/25): experimental Engine 2.0 stack. Do not make its separate proposal database a second writable approved-plan authority.
- [#8](https://github.com/Isshhin/stridelab-development/pull/8) / [#23](https://github.com/Isshhin/stridelab-development/pull/23): UI/accessibility work held pending reviewed native reconstruction.

**Next integration handoff:** [`docs/reports/NATIVE-INTEGRATION-HANDOFF-20261030.md`](docs/reports/NATIVE-INTEGRATION-HANDOFF-20261030.md), including exact SHAs, PR deduplication map, CI blockers, recommended test sequence, signing/data-protection gates and Work re-entry instructions.

## Development safety rules

1. Keep a clean, exact native baseline; use isolated feature/integration branches and review existing overlapping PRs before cherry-picking.
2. No training plan may be considered approved without explicit user confirmation. Never convert an educational exercise guide into a prescription or infer completion without actuals.
3. Maintain account-scoped offline data, provenance, recoverable originals, and historical records; validate restores and migrations before writes.
4. Treat emulator, synthetic fixture, cloud Gradle, GitHub Actions, APK and real-device evidence as **different validation levels**.
5. Keep APK distribution private and opt-in. Verify certificate fingerprint and version code against the installed application before attempting an in-place update.

This README is a navigation aid. For a new Work/CI session, first recheck live GitHub branches and the latest acceptance evidence; do not assume this snapshot remains current.
