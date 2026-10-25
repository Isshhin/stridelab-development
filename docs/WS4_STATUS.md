# StrideLab Workstream 4 — Status

**Status: WS4-C CI accepted (221/221); WS4-D candidate DELIVERED, verified for integrity, official D tests pending.** Updated 2026-10-25.

Source: the inner `StrideLab-WS4-C.zip` packaged inside uploaded `stridelab ws4 c.zip`. See `archive/WS4_ARCHIVE_INDEX.md` for checksums.

## New WS4-D candidate (not accepted yet)

- Received `ws4 d.zip` containing `StrideLab-WS4-D-WIP.zip` and `StrideLab-WS4-D-candidate.zip`.
- Candidate inner archive SHA-256 `885eaac1a9ccb399a0fa0f81d530f6ba1133444d4e7d7710200b4ea6c9d3a972`; CRC passed and 556/556 manifest entries match.
- Rebuilt candidate `23717f3a7a6cd1a16ff9a1c7669026c4818b522d9632474a3dc2de15b2fe9b85` matches provided HTML exactly; protected 4D.1 and coaching-engine hashes passed integrity script.
- D fixes bottom-navigation landmarks/current-tab semantics; Whole route touch target; 360px/200% calendar dates; adds 7 official Playwright tests. Reported targeted browser probes passed; the official D run is still pending.
- **Next:** Upload the extracted 19 MB `StrideLab-WS4-D-candidate.zip` to a private prerelease tagged `ws4-d-candidate`, then manually trigger [dedicated D verification](https://github.com/Isshhin/stridelab-development/actions/workflows/ws4-d-playwright.yml). Workflow downloads that exact release asset, verifies checksum/build and requires 228 official passes, no flaky/failed/skipped cases.
- D's remaining known UI exception: 12 weekly-load bars are 25–31px wide; accessible alternative 44px previous/next week buttons exist. Gradient/nav contrast, manual screen reader and Android device tests remain outside verified coverage; don't silently call them passes.

See [WS4-D archive index](../archive/WS4_D_ARCHIVE_INDEX.md).

## Scope implemented in C

- Consistent Athlete Fitness-style segmented controls for Library tabs, workout categories, difficulty, equipment and generator goal/day selection.
- Old Library difficulty `select` replaced with segmented buttons; one Playwright spec migrated with behavior assertions retained.
- Focus restores to activated controls following UI updates.
- Responsive grids for 360 / 390 / 430px and 200% root text, with wrapping in place of clipped labels.
- Current UI consistent with approved Afterglow design; engine and original baseline unchanged.

## Official GitHub Actions verification — 2026-10-25

Run: https://github.com/Isshhin/stridelab-development/actions/runs/40203635958

- **Official Playwright: 221 passed, 0 failed, 0 skipped, 0 flaky** on the uploaded **WS4-C** archive, not merely inherited WS3-E figures.
- GitHub-hosted Ubuntu runner, Playwright Chromium, 4 workers, approx. 2.9 minutes of tests.
- Available axe-related suites executed and passed.
- Outer and inner archive SHA-256, ZIP CRC, 540/540 manifest hashes, deterministic rebuild, protected 4D.1 baseline checks passed.
- The tests do **not** substitute for manual contrast, screen-reader, physical Android or the remaining WS4 touch-target/start-date audits.
- **WS4-D final package has not been produced.** Continue with the remaining audits and preserve new regression evidence before final acceptance.

## Evidence and limits

- Package archive and inner manifests independently validated; protected script and baseline SHA-256 match; deterministic builder reproduces candidate `f565a67241c66717d0495d276e583abaf5a535518fab16fcd5628b559cb6f753`.
- Claude reports a direct Playwright/Python responsive probe over 60 screen states: 0 findings after 13 clipping fixes. Keyboard/selection probe passed.
- Previous verification blocker resolved: official Playwright and available axe checks completed on WS4-C (221 passed / 0 failed). See run above.
- Historical local setup attempt had unavailable npm dependencies and browser runtime restrictions; GitHub Actions subsequently ran the official suite successfully.
- Manual screen-reader / contrast and physical Android device testing remain outstanding.

## WS4-D acceptance tasks

1. Official WS4-C CI suite completed green on Ubuntu Chromium; preserve this run as independent evidence.
2. Audit start-date peek row, 36px `.ml .chip` touch targets, bottom-nav label wrap, manual contrast, and responsive/keyboard/focus issues not fully captured by existing automated checks.
3. Add targeted regression tests only where current WS4 changes lack meaningful coverage; do not weaken assertions.
4. Fix confirmed defects if any, update manifests/hashes, and produce WS4-D final checkpoint with acceptance report.
5. Physical Android and manual screen-reader checks remain an explicit follow-up (not silently treated as passed).
6. Do not start WS5 or integration until WS4 handoff and review are complete.

Keep the original Next.js application in `Isshhin/stride-lab` separate from the standalone Afterglow prototype.

## Private ZIP archive delivery

GitHub's browser repository uploader cannot accept this ~50 MB archive. Store `StrideLab-WS4-C.zip` as an asset of a **private pre-release** tagged `ws4-c` (not in Git source history). The manual GitHub Actions workflow `.github/workflows/ws4-c-playwright.yml` now downloads the attached release asset using the repository-scoped `GITHUB_TOKEN`, checks SHA-256, then executes the official suite. The release is only a storage checkpoint, not a public app release or software acceptance.
