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
