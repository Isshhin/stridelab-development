> **LATEST (2026-10-26): Phase 4D.3A-A Fix1 package ready, CI PENDING.** Original candidate [run 40249781541](https://github.com/Isshhin/stridelab-development/actions/runs/40249781541) was 257/258; last test's Enter toggle expected wrong state. Fix1 changes test/docs only. Exact ZIP `StrideLab-4D3A-A-fix1.zip`, SHA-256 `424063a8400e57eebd89012cd71b55bf38218678b78a4ee0f34206619cc81e87`, 629/629 authoritative manifest, same app HTML and protected engine. Upload to private prerelease `phase4d3a-a-fix1` and run [official 258-test Fix1 gate](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4d3a-a-fix1-playwright.yml). **Do not promote before passing CI.** WS5-D (253/253) remains last verified baseline. See [4D.3A status](PHASE4D3A_STATUS.md). Prior banners below are historical.

> **Latest in-progress work (2026-10-26): Phase 4D.3A-A Running Intelligence foundation candidate packaged, NOT ACCEPTED.** The last accepted source remains WS5-D ([253/253 GitHub Actions run 40247262671](https://github.com/Isshhin/stridelab-development/actions/runs/40247262671)). Download exact `StrideLab-4D3A-A-candidate.zip` (SHA-256 `fd347ff2cc904750d6ed3cb9e4d4d88b1f6316e725477d74fe82afd56ec1862c`, manifest 626/626) from conversation artifacts or later private GitHub release tagged `phase4d3a-a`. New dynamic demo run insights and direct chart split taps added; five tests, official 258-case suite pending. Run [Phase 4D.3A-A Actions](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4d3a-a-playwright.yml) **after uploading the release asset**. Keep protected engine and Phase 4D.1 baseline untouched. Next phases 4D.3B Link Run, 4D.3C comparison, then private Android before Engine 2.0. See [Phase 4D.3A status](PHASE4D3A_STATUS.md) and [archive](../archive/PHASE4D3A_ARCHIVE_INDEX.md). Older handoff banners are historical.

> **AUTHORITATIVE HANDOFF (2026-10-26): Phase 4D.2 Workstream 5 has passed final automated acceptance.** Official [WS5-D GitHub Actions run 40247262671](https://github.com/Isshhin/stridelab-development/actions/runs/40247262671): **253 passed, 0 failed/skipped/flaky**, available axe suites green; 607/607 manifest, deterministic build and protected hashes verified. Recover exact 20,115,804-byte `StrideLab-WS5-D-candidate.zip` from the [private release](https://github.com/Isshhin/stridelab-development/releases/tag/ws5-d-candidate), SHA-256 `451d53c3387362b9dd17f61457197373fdd66a8399fd8c58e59d60c267de2271`. **Next: Phase 4D.3A Running Intelligence 3.0.** Manual Android, screen-reader and expert exercise-content review remain open. Any contrary historical banners below are superseded; do not reopen WS5 without a confirmed regression.

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
