# StrideLab master roadmap index

**Current long-term Engine 1.5 coaching development roadmap (2026-11-14):** [Engine 1.5 roadmap v0.2](engine15-long-term-roadmap-v0.2-20261114.md). No date-based release target applies. [Gate B review rubric](qa/engine15-gate-b-rubric-v1.md) is frozen before research-harness output; implementation status remains distinct from this plan. The historical 2026-10-26 roadmap is preserved below for provenance.

---

# Active master milestone — native reconstruction and Engine 1.0

Updated 2026-10-26. This section supersedes the historical sequence below. Complete the accepted Phase 4E functionality in native Compose, including a functional **StrideLab Engine 1.0** baseline, before substantial Advanced Unified Coaching Engine 2.0 development or benchmarking. Follow [the live parity inventory](NATIVE-PHASE1-PARITY.md) and [Engine 1.0 contract](ENGINE1-BASELINE.md). Keep the same master branch `feature/native-reconstruction-1-20261026`, package `io.stridelab.privatealpha`, all checkpoint branches, accepted assets and native security/cache architecture.

Current increment implements actual draft generation, explicit approval, dated calendars, resistance prescriptions, workout logging, missed reasons, optional conservative adaptations, feedback and owner-isolated offline snapshots. This is **implementation under verification**, not completion of reconstruction or Engine 1.0 acceptance. Full exact-source CI, visual review, process restart, and secure real-device acceptance remain gates. Missing parity is tracked explicitly; no public release is authorized.

## Historical roadmap (retained)

# Current top priority

Native UI/UX Reconstruction precedes new engine/features. See [native milestone](NATIVE-UI-RECONSTRUCTION-20261026.md). Real-account Phase 2 acceptance remains a separate pending gate.

# StrideLab Development Roadmap

Updated: 2026-10-26. **Private developer-only project. No public launch is planned.**

## Current milestone

**Phase 4D final accepted:** [Run 40254593565](https://github.com/Isshhin/stridelab-development/actions/runs/40254593565) completed **273/273 official Playwright/axe passes, 0 failed/skipped/flaky**, protected source and archive integrity green.

**Phase 4E source implementation complete; CI and physical device acceptance pending:** [Status](PHASE4E_STATUS.md), ZIP `StrideLab-Phase4E-Final-candidate.zip` SHA-256 `d1d061d8be9d9dd752a71a40867e2bc00f0fb15b97d88032c0b95f45e4e1c3b3`. Offline Android WebView, same accepted 4D prototype, SAF data backup/restore and private APK build/sign workflow are implemented. The Android emulator/build has not yet run. Phone-level update safety requires a durable signing key and explicit on-device checks.

**Next:** one combined [Android build/emulator gate](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4e-final-android.yml) followed by private phone testing, then Engine 2.0 design/validation. No public release planned.

**Phase 4D.3A-A Running Intelligence foundation accepted as the last VERIFIED checkpoint**, [official GitHub Actions run 40250751272](https://github.com/Isshhin/stridelab-development/actions/runs/40250751272): **258/258 passed (0 failed/skipped/flaky)** including available axe checks, 629/629 manifest hashes, rebuilt HTML and protected engine/baseline verified. Exact private source: [`phase4d3a-a-fix1`](https://github.com/Isshhin/stridelab-development/releases/tag/phase4d3a-a-fix1), SHA-256 `424063a8400e57eebd89012cd71b55bf38218678b78a4ee0f34206619cc81e87`.

- Phase 4D.2 Workstreams 1–5: automated acceptance complete; final WS5-D run 40247262671 was 253/253.
- Phase 4D.3A-A: data-derived demo run insights, direct chart-to-split selection, keyboard support, honest missing-data and simulated route labeling; **accepted 258/258**.
- **Phase 4D combined final candidate IMPLEMENTED; official verification pending:** 4D.3A running insights, 4D.3B manual Link Run + validated offline JSON import, 4D.3C honest planned/actual comparison. One integrated candidate ZIP with 15 new tests (expected 273), 630/630 manifest, protected hashes unchanged. [Candidate verification workflow](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4d-final-playwright.yml). Do not mark final accepted before official CI.
- Subsequent: Phase 4D.3B planned-to-recorded Link Run, 4D.3C planned-versus-actual summaries, then private Android pre-alpha before Engine 2.0.

Manual Android, screen-reader, expert coaching/content, map tiles and genuine imported activity validations remain open. No public launch planned.

## Next phases

- **Phase 4D.3A in progress:** Checkpoint A has data-derived demo split analysis, direct chart taps and accessible keyboard selection, and truthful map/no-GPS labeling. Five added official tests (expected 258). Original run 40249781541 passed 257/258; one incorrect keyboard-toggle assertion repaired in test-only Fix1. New CI is pending before promotion. Later 4D.3A checkpoints can improve interactions/data validation without altering protected engine.
- Phase 4D.3B: Link Run, stable planned/recorded IDs, matching, manual unlink/correction, persistence.
- Phase 4D.3C: planned-versus-actual comparisons (not adaptive coaching yet).
- Phase 4E **before Engine 2.0**: private Android developer pre-alpha; stable app ID and signing, offline data, backups/migrations, GitHub-versioned user-approved APK updates; do not expose private APKs via public Releases without explicit approval.
- Phase 5: audit and validate unified adaptive coaching engine, planning, load/recovery, periodization and cross-discipline scheduling. Preserve old engine as reference until tests establish improvements.
- Phase 6: independently benchmark using public docs from Runna (running structure), Run Kaizen (history-informed flexible load/adaptation), Freeletics (multi-discipline exercise programming), plus independent exercise-science research.
- Phase 7: add mobility, then plyometrics into same engine, following core validation.
- Later: longitudinal personal testing; no public release assumed.

## Repositories

- `Isshhin/stride-lab`: original Next.js / Supabase / Cloudflare application (keep untouched until a deliberate integration task).
- `Isshhin/stridelab-development`: private Afterglow prototype, workstream code, evidence, checkpoint manifests, and references to archived ZIPs.
