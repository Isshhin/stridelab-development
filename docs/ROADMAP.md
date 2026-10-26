# StrideLab Development Roadmap

Updated: 2026-10-26. **Private developer-only project. No public launch is planned.**

## Current milestone

**Phase 4D.2 Workstream 5 completed for automated acceptance.** Final accepted checkpoint is [WS5-D `ws5-d-candidate`](https://github.com/Isshhin/stridelab-development/releases/tag/ws5-d-candidate), [GitHub Actions run 40247262671](https://github.com/Isshhin/stridelab-development/actions/runs/40247262671): **253/253 passed (0 failed/skipped/flaky)** with available axe, 607/607 integrity manifest and protected engine/baseline unchanged.

- WS1: modal scroll lock, accepted.
- WS2: mobile Guided Plan Builder, accepted.
- WS3: monthly calendar, arbitrary start dates, multi-workout saves, stable plan/session identity; accepted WS3-E.
- WS4: mobile UI consistency and accessibility, accepted WS4-D 228/228 in run 40205353927.
- WS5-A: Workout Library asset/provenance audit.
- WS5-B: Library metadata, diagram fitness disclosures, running session structure, interface; accepted Fix2 237/237 in run 40210796282.
- WS5-C: 21 educational guides, carrying and running drills, calisthenics progressions; accepted Fix1 248/248 in run 40245684800.
- WS5-D: final Library content/a11y correction and five regression tests; **accepted 253/253 in run 40247262671**.

**Next: Phase 4D.3A — Running Intelligence 3.0.** Manual device, screen-reader, exercise guidance and licensing reviews remain open; not falsely recorded as completed.

## Next phases

- Phase 4D.3A: improve Running Intelligence insights, clickable chart split markers, segmented Pace / HR / Cadence / Elevation controls, simplify map controls.
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
