# StrideLab Development Roadmap

Updated: 2026-10-26. **Private developer-only project. No public launch is planned.**

## Current milestone

**WS5-C Fix1 accepted** as the latest Phase 4D.2 intermediate checkpoint after [GitHub Actions run 40245684800](https://github.com/Isshhin/stridelab-development/actions/runs/40245684800): **248/248 Playwright Chromium tests passed (0 failed/skipped/flaky)** with available axe suites; 602/602 manifest hashes and protected engine/baseline verified. Exact recoverable ZIP is [private release `ws5-c-fix1`](https://github.com/Isshhin/stridelab-development/releases/tag/ws5-c-fix1).

- WS1: modal scroll lock.
- WS2: mobile guided Plan Builder.
- WS3: calendar, multi-workout date/save identities; historical WS3-E.
- WS4: mobile UI consistency and accessibility, verified 228/228 in run 40205353927.
- WS5-A: exercise/asset provenance audit.
- WS5-B: metadata, image-fit honesty, running session structure, UI; fix2 verified 237/237 in run 40210796282.
- WS5-C: 21 reference-only guide entries, running drills, carries and calisthenics progression pathways; Fix1 verified **248/248** in run 40245684800.
- **WS5-D candidate delivered, official CI pending:** final Workout Library quality, corrected reverse-lunge image-fit accuracy, guide live count semantics, five new regression tests, packaged as immutable recovery ZIP. Complete workstream acceptance only after the exact candidate passes 253 official Playwright/axe cases.

Manual domain review, screen-reader, real Android and other-browser checks remain documented limitations. No public launch is planned.

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
