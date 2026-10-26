# Phase 4D.3A — Running Intelligence 3.0

**Current status: Checkpoint A candidate packaged. Independent official CI is PENDING.** Updated 2026-10-26.

## Starting evidence and protected source

- Last officially accepted baseline: [Phase 4D.2 Workstream 5 final WS5-D run 40247262671](https://github.com/Isshhin/stridelab-development/actions/runs/40247262671): **253/253 official Playwright tests passed** with available axe checks, 0 failures/skips/flaky.
- Source: private [`ws5-d-candidate` GitHub release](https://github.com/Isshhin/stridelab-development/releases/tag/ws5-d-candidate), immutable original ZIP. Do not edit the original production repo `Isshhin/stride-lab` without an integration assignment.
- Protected coaching engine hash: `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2`.
- Protected original Phase 4D.1 baseline hash: `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d`.

## Checkpoint A deliverables

- `StrideLab-4D3A-A-WIP.zip`: SHA-256 `340a6e0195cea0947171e4ab8c43d6eaf76732438b6a631c25934f409fbb8d00`, 20,204,466 bytes, **623/623** WIP manifest, ZIP CRC passed.
- **`StrideLab-4D3A-A-candidate.zip`**: SHA-256 `fd347ff2cc904750d6ed3cb9e4d4d88b1f6316e725477d74fe82afd56ec1862c`, 20,229,146 bytes, **626/626** candidate manifest and ZIP CRC passed. The archive contains the complete runnable source, scripts, assets, historical evidence, new tests and reports.
- Candidate deterministic HTML rebuild hash: `286f82e08099c13c974845e8a1f4de8b82c2d275b81c241e457ada260ae7ba0b`.
- Standalone patch `tools/p4d3a_run_intelligence.py` appended to the deterministic Phase 4D.2 pipeline; no protected source bytes modified.

## Implemented and scoped

1. Replaces static prewritten analysis in modern activity detail with descriptive full-kilometre split comparisons: early-versus-late pacing and range, heart-rate and cadence observations only when recorded, explicit no-HR and no-baseline limitations.
2. Includes careful, goal-contextual next-session guidance; no claims about quantified aerobic drift, calibrated HR zones, elite/recreational benchmark standings or comparisons with historical sessions that do not exist.
3. **Direct graph selection**: tapping the chart selects the corresponding split, synchronizing row/detail/map highlights. Keyboard arrows/Home/End navigate and Enter/Space select or toggle. Redundant visible Select Split and point arrow controls removed; two inherited keyboard tests migrated without dropping their purpose.
4. Existing Pace/Heart Rate/Cadence/Elevation metric tabs, inverted pace axis, automatic demo route when GPS samples exist and truthful treadmill no-GPS state remain intact. **The route and values are simulated, not real athlete data.**
5. Added five new Playwright tests. Full official suite expected **258 tests** (253 inherited, two intentionally migrated tests, five added).

## Test evidence and limits

- Local rebuild and protected-engine/baseline validation passed. Node syntax checks passed for all three generated inline script blocks and modified/new JS tests.
- Focused Python Chromium at 390px confirmed analysis text, chart pointer selection, keyboard selection, elevation interaction, missing-HR tab and no observed uncaught JS errors.
- Broader multi-viewport local smoke test timed out in sandbox. **Do NOT report all mobile states, official Playwright, or axe as passing.**
- Manual Android, screen reader, chart contrast, expert training guidance validation and real activity ingestion remain unverified. Device/app integration, external map tiles and Link Run belong to later phases.

## Next action

1. Upload the **exact** `StrideLab-4D3A-A-candidate.zip` as an asset to a **private prerelease** tagged `phase4d3a-a`; filename must not change.
2. Trigger [Phase 4D.3A-A official Playwright and axe verification](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4d3a-a-playwright.yml) on `main`.
3. The gate verifies SHA-256, ZIP CRC, 626/626 manifest, deterministic rebuild, protected engine/baseline, and **258 passed, 0 failed, 0 skipped, 0 flaky**. Record the actual run URL. Fix any failures without silently weakening tests.
4. Once accepted, continue 4D.3A interaction polish/data validation; 4D.3B Link Run; 4D.3C planned versus recorded summaries. Complete private Android pre-alpha before Engine 2.0.

**No acceptance claim before the official run.**
