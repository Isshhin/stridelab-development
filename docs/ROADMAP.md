# StrideLab Development Roadmap

Updated: 2026-10-26. **Private developer-only project. No public launch is planned.**

## Current milestone

**Phase 4D.3A-A Running Intelligence foundation accepted as an intermediate checkpoint**, [official GitHub Actions run 40250751272](https://github.com/Isshhin/stridelab-development/actions/runs/40250751272): **258/258 passed (0 failed/skipped/flaky)** including available axe checks, 629/629 manifest hashes, rebuilt HTML and protected engine/baseline verified. Exact private source: [`phase4d3a-a-fix1`](https://github.com/Isshhin/stridelab-development/releases/tag/phase4d3a-a-fix1), SHA-256 `424063a8400e57eebd89012cd71b55bf38218678b78a4ee0f34206619cc81e87`.

- Phase 4D.2 Workstreams 1–5: automated acceptance complete; final WS5-D run 40247262671 was 253/253.
- Phase 4D.3A-A: data-derived demo run insights, direct chart-to-split selection, keyboard support, honest missing-data and simulated route labeling; **accepted 258/258**.
- **Next: Phase 4D.3A-B**, deeper insight accuracy/edge cases, responsive visualization, map and split interaction refinement. Build on immutable accepted 4D.3A-A Fix1 source, preserve all 258 tests and protected hashes.
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
