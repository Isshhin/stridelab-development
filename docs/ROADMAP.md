# StrideLab Development Roadmap

Updated: 2026-10-25. **Private developer-only project. No public launch is planned.**

## Current milestone

Phase 4D.2 **WS5-B Fix2 verified** (intermediate checkpoint) by [GitHub Actions run 40210796282](https://github.com/Isshhin/stridelab-development/actions/runs/40210796282): **237 official Playwright tests passed, 0 failed/skipped/flaky**, including available axe checks. Exact checkpoint in private prerelease `ws5-b-fix2`. **WS5-C candidate implemented, official verification pending; then WS5-D final Workstream 5 acceptance.**

- WS1: modal scroll lock, checkpoint 2.
- WS2: mobile guided Plan Builder, checkpoint 3.
- WS3: monthly calendar, arbitrary start dates, multi-workout same-day save safety, stable plan/session identity, WS3-E.
- WS4: mobile UI consistency and accessibility, verified in official [WS4-D run 40205353927](https://github.com/Isshhin/stridelab-development/actions/runs/40205353927) (228/228).
- WS5-A: completed Workout Library and exercise-image provenance audit.
- WS5-B: metadata, accurate image labels, running session structure and Library UX; Fix2 verified 237/237 official tests.
- WS5-C **candidate delivered; official CI pending**: 21 reference-only guides, five movement ladders, responsive modal discovery. Not added to tracked workout engine; 11 new tests await CI, target 248/248.
- WS5-D: final integration, independent test evidence, handoff and acceptance.

Manual screen-reader, physical Android, other browsers, final coaching-domain verification and full WS5 completion remain outstanding.

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
