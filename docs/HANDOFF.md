> **Latest source: WS5-C Fix1 (CI PENDING).** Previous WS5-C candidate failed one scroll-lock test (247/248). Fix1 moves the guide invite after original exercise cards, preserving their scroll geometry. Package SHA256 `d781ae49fcad50571f1893f414df498ee8c1588cdb581e25aebbe0dc73c49704`; 602/602 manifest and protected hashes passed. Upload as private prerelease `ws5-c-fix1` and run [WS5-C Fix1 CI](https://github.com/Isshhin/stridelab-development/actions/workflows/ws5-c-fix1-playwright.yml) before acceptance. WS5-B Fix2 237/237 remains last verified checkpoint; WS5-D is final WS5 gate.

> **Latest source: WS5-C candidate; NOT officially accepted.** [WS5-B Fix2](https://github.com/Isshhin/stridelab-development/releases/tag/ws5-b-fix2) remains the last accepted checkpoint (237/237 official tests). WS5-C ZIP SHA-256 `91d6b79ce1ebebb9001bcc5b18070352c7752c4595330fb6f95426c741aed7fa`, 600/600 manifest. Additional 21 educational guides (no tracked workouts), 11 new official test cases awaiting the [WS5-C GitHub Actions gate](https://github.com/Isshhin/stridelab-development/actions/workflows/ws5-c-playwright.yml) (248 expected). Exact private prerelease tag must be `ws5-c-candidate`. WS5-D still the final workstream gate. See [status](WS5_STATUS.md) and [archive](../archive/WS5_ARCHIVE_INDEX.md).

> **Accepted intermediate checkpoint (2026-10-25): WS5-B Fix2.** Official GitHub Actions [40210796282](https://github.com/Isshhin/stridelab-development/actions/runs/40210796282) confirms **237 passed, 0 failed, 0 skipped, 0 flaky** on the exact private prerelease `ws5-b-fix2`. ZIP SHA-256 `9c2fe6f720f99f79f79bdc84752ecd30f55e9413d0746ed3fbdf9cf1c70d3940`; 578/578 manifest integrity, deterministic rebuild and protected engine/baseline checks passed. **WS5-C next; full Workstream 5 acceptance is reserved for WS5-D.** See [current status](WS5_STATUS.md) and [WS5 archive](../archive/WS5_ARCHIVE_INDEX.md).

# StrideLab Handoff / Session Recovery

## Current accepted source

**StrideLab-WS3-E.zip** is the authoritative current Phase 4D.2 WS3 candidate, **not** historical A, B, C or D. Its recovery ZIP is a separate supplemental archive, not a replacement. The archive ZIP files are **not yet uploaded to this repository** unless/until present under `archives/`.

- WS3-E ZIP SHA-256: `4afde2aa145031244a73926bc24db34b9571065c27a1411e3a9bd731f4d78671`
- Candidate HTML SHA-256: `d5dd7a8da04646995d3e3f1b78f171207115cbf095b9cc7436b8b92bb1d657ec`
- Protected coaching engine raw script SHA-256: `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2`
- Protected 4D.1 HTML SHA-256: `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d`

Reported accepted WS3-E checks: official Playwright 221/221, all accessible axe checks without automatic violations; manual contrast and physical-device testing remain outstanding.

## Parallel work assignments

- Claude: WS4 (mobile control consistency/accessibility) and then WS5 (Workout Library), as separate recoverable checkpoints.
- ChatGPT: keep archive manifests and GitHub docs current, then integration-ready 4D.3A activity changes separately after checking latest source.

## Rules for every session

1. Read this handoff and current roadmap first; verify input SHA-256 and inspect original ZIP integrity.
2. Never edit protected 4D.1 baseline or raw coaching-engine script. Respect protected hash checks.
3. Work in a separate branch/candidate; do not directly overwrite original `Isshhin/stride-lab`.
4. Implement, **deliver an actual recoverable WIP checkpoint**, then test broadly, fix and deliver the final checkpoint.
5. Include HTML and assets, scripts, test results, SHA-256 manifest, report, screenshot evidence and updated handoff.
6. Distinguish local tests vs actual Android, and historical reported results vs newly executed tests. Preserve meaningful assertions.
7. Avoid storing API keys, `.env`, keystores, personal athlete exports or secrets in GitHub, even if private.
8. No public release, APK release, Engine 2.0 overhaul, or codebase integration unless expressly assigned.
