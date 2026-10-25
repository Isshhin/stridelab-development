# StrideLab Development Roadmap

Updated: 2026-10-25. **Private developer-only project. No public launch is planned.**

## Current milestone

Phase 4D.2 WS3-E is the latest accepted workstream-3 package. Local official Playwright report: **221 passed, 0 failed, 0 skipped**. Real Android, other browser engines, manual contrast checks, and production coaching appropriateness are not established by those tests.

- WS1: modal scroll lock, checkpoint 2.
- WS2: mobile guided Plan Builder, checkpoint 3.
- WS3: monthly calendar, arbitrary start dates, multi-workout same-day save safety, stable plan/session identity, WS3-E.
- WS4 **next**: mobile UI consistency, accessibility/responsiveness; preserve stable behavior.
- WS5: original exercise artwork / Workout Library refinements.

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
