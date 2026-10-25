# StrideLab Workstream 4 — Status

**Status: Checkpoint C delivered; Workstream 4 NOT accepted.** Updated 2026-10-25.

Source: the inner `StrideLab-WS4-C.zip` packaged inside uploaded `stridelab ws4 c.zip`. See `archive/WS4_ARCHIVE_INDEX.md` for checksums.

## Scope implemented in C

- Consistent Athlete Fitness-style segmented controls for Library tabs, workout categories, difficulty, equipment and generator goal/day selection.
- Old Library difficulty `select` replaced with segmented buttons; one Playwright spec migrated with behavior assertions retained.
- Focus restores to activated controls following UI updates.
- Responsive grids for 360 / 390 / 430px and 200% root text, with wrapping in place of clipped labels.
- Current UI consistent with approved Afterglow design; engine and original baseline unchanged.

## Evidence and limits

- Package archive and inner manifests independently validated; protected script and baseline SHA-256 match; deterministic builder reproduces candidate `f565a67241c66717d0495d276e583abaf5a535518fab16fcd5628b559cb6f753`.
- Claude reports a direct Playwright/Python responsive probe over 60 screen states: 0 findings after 13 clipping fixes. Keyboard/selection probe passed.
- **No official WS4 Playwright or axe run has been completed**. The prior 221/221 official result belongs to **WS3-E only**.
- In an additional attempt, `npm ci --offline` failed because `playwright-core` was uncached; `npm ci` failed resolving `registry.npmjs.org`. Local browser navigations were blocked by runtime policy. These are test-environment blockers, not product test passes or failures.
- Manual screen-reader / contrast and physical Android device testing remain outstanding.

## WS4-D acceptance tasks

1. Extract WS4-C, verify archive manifest/hashes and reproduce HTML build.
2. Run `npm ci` and `npx playwright install chromium` in `suite/` on a Windows computer with internet.
3. Set `PROTO` to the absolute HTML path; run `npx playwright test` including axe.
4. Migrate outdated selectors for changed Library/generator controls; do not remove or weaken meaningful assertions.
5. Audit start-date peek row and 36px `.ml .chip` targets, responsive saved view, 200% text, keyboard/focus/scroll locking.
6. Fix confirmed regressions, update hashes, produce WS4-D checkpoint and final test evidence.
7. Do not start WS5, Phase 4D.3 integration, Engine 2.0 or APK packaging until relevant branch safeguards are satisfied.

Keep the original Next.js application in `Isshhin/stride-lab` separate from the standalone Afterglow prototype.

## Private ZIP archive delivery

GitHub's browser repository uploader cannot accept this ~50 MB archive. Store `StrideLab-WS4-C.zip` as an asset of a **private pre-release** tagged `ws4-c` (not in Git source history). The manual GitHub Actions workflow `.github/workflows/ws4-c-playwright.yml` now downloads the attached release asset using the repository-scoped `GITHUB_TOKEN`, checks SHA-256, then executes the official suite. The release is only a storage checkpoint, not a public app release or software acceptance.
