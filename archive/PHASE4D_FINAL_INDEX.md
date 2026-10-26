# Combined Phase 4D final archive index

**2026-10-26: all remaining Phase 4D.3 implementation components are packaged together as ONE candidate. This candidate is NOT officially accepted until its single GitHub Actions gate passes.** The accepted rollback remains [4D.3A-A Fix1, 258/258](https://github.com/Isshhin/stridelab-development/actions/runs/40250751272).

## Final candidate and integrity

- File: `StrideLab-Phase4D-Final-candidate.zip` (20,267,625 bytes).
- SHA256: `915c9c37e3816291bfc9ab3ead1f37b400711886056a43fd1af101b602cdaa6b`.
- ZIP CRC: pass; **630/630** candidate manifest payload hashes match.
- Deterministically rebuilt HTML: `d43ae2539d2c4d0da2e289ab6fec270366d7351e5d973d6495cc57d7627e4466`.
- Protected coaching engine: `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2`, unchanged.
- Protected Phase 4D.1 HTML baseline: `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d`, unchanged.
- Recoverable WIP: `StrideLab-Phase4D-Final-WIP.zip` (20,245,831 bytes) SHA256 `bba587f63154d1bdbcbb2fdc84e788754e95e98218e05b90b899f86b1fbb49d4`; use the **final candidate**, not WIP, for acceptance.

## Implemented workstreams

- **4D.3A Running Intelligence:** existing data-derived pacing, accessible graph/split/map interaction; supports validated user-provided running splits and carefully qualified historical same-distance comparisons. No invented missing HR/cadence/route/fitness benchmarks.
- **4D.3B Link Run:** offline JSON imports to Activities with explicit source disclosure, strict split/distance/time/GPS validation; manual plan links, date-scoped repeated sample IDs, stable generated session/plan IDs, duplicate prevention, unlink/correction, browser-local persistence, deletion and failure-safe writes. Linked workouts can be recognized on their planned dates; stale plan associations are suspended rather than silently matched.
- **4D.3C Planned vs actual:** actual/imported distance vs explicit plan km (when available), approximate plan duration vs recorded moving time with comparability caveats, and explicit absence of inferred pacing, workout compliance or intensity targets.
- **Map and responsive behavior:** imported GPS coordinate diagrams show automatically when provided; no-GPS entries never fabricate a route. 200% text/360–430px wrapping improved; original 23 executable workouts, 21 reference-only guides, accepted chart controls, calendar and scroll-lock maintained.
- **Offline data limit:** JSON pasted/imported manually (up to 40 activities), not FIT/GPX/Garmin/intervals.icu connector, not map tiles and not native Android. These are later integration/device requirements, not silently declared finished.

## Local evidence vs official acceptance

- Deterministic build, protected integrity check, inline JavaScript syntax, new Playwright spec syntax passed.
- Focused local Chromium: **47/47** checks passed on 360/390/430px, plus 200%-text sheet overflow probes (all three widths passed). The local browser uses set_content, cannot test file-backed media or a real origin.
- **15 newly authored official Playwright tests**, preserving the accepted 258 tests; combined gate requires **273 passes, 0 failed, 0 skipped, 0 flaky** with available axe checks. **No official Phase 4D final test run has yet occurred.**

## One-shot official verification instructions

1. Upload the **exact** ZIP as an asset of a **private prerelease** tagged `phase4d-final-candidate`, asset name `StrideLab-Phase4D-Final-candidate.zip`. Target the repo `Isshhin/stridelab-development`, branch `main`.
2. Manually dispatch [Phase 4D final Playwright/axe workflow](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4d-final-playwright.yml) on `main`. The workflow checks exact archive SHA256, ZIP CRC, 630/630 manifest files, deterministic HTML, protected hashes, and all 273 official tests.
3. If green, record evidence, accept Phase 4D within the tested offline-prototype scope, and proceed to Phase 4E private Android pre-alpha. If the gate identifies a defect, preserve the accepted rollback, repair the specific issue and run a **new** official gate. A first-run pass cannot be guaranteed.
4. Manual physical Android, screen-reader, expert workout/content, real source ingestion, real map basemap/route alignment and persistence migration/backup are explicitly unverified and belong to Phase 4E or dedicated integrations.

For reproducibility, the ZIP includes `PHASE4D-FINAL-IMPLEMENTATION.md`, `PHASE4D-FINAL-HANDOFF.md`, `tools/p4d_final_integration.py`, `tools/p4d_final_probe.py`, `suite/tests/p4d3-final-integration.spec.js`, JSON example and evidence.

