# StrideLab development roadmap

**Updated:** 2026-11-21  
**Scope:** Private Android development; no public release or production promotion authorized.  
**Source of truth:** [Agent policies](../AGENTS.md), [Engine 1.5 roadmap](ENGINE_DEVELOPMENT_ROADMAP.md), [Engine execution tracker](../ENGINE15_EXECUTION.md), actual branch HEAD, and per-PR QA evidence.

This page replaces the obsolete October 26 Engine 1.0/WebView reconstruction roadmap as the **current** planning index. Historical Phase 4D/4E records remain available in Git history and existing evidence documents but must not be interpreted as instructions for current Android development.

## Current project state

- **`main`:** owner-approved stable release branch; not equivalent to the latest Android development.
- **`dev`:** integration branch. PR #165 integrated the modern non-engine QA stack and experimental Engine 1.5 code, **inactive** by default. `ENGINE15_ACTIVE=false` and `ENGINE15_INDEPENDENT_RUNNING_ACTIVE=false` on normal debug; release and isolated QA force both false.
- **Owner-local Garmin QA/Running worktree:** `C:/Users/marcs/stridelab-garmin-qa`, `feat/garmin-coach-ui-20261120`, as reported by the owner; exact local HEAD and dirty files are not verifiable from this remote branch. It reportedly contains a new Running dashboard and code from drafts [#166](https://github.com/Isshhin/stridelab-development/pull/166), [#167](https://github.com/Isshhin/stridelab-development/pull/167) and [#169](https://github.com/Isshhin/stridelab-development/pull/169). These are **not automatically merged or accepted**.
- **Engine 1.5:** independent static generation exists; Engine 1.0 is retired as its normal generator fallback. Multi-discipline scheduling and eight-week Tempo/Threshold candidate work exist, but comprehensive quality-dose progression, coaching and Android acceptance, and release activation remain incomplete. See [engine roadmap](ENGINE_DEVELOPMENT_ROADMAP.md).
- **Garmin Section 1:** HR-zone caching/source selection, FIT performed-workout classification, manual corrections and descriptive adherence are implemented in draft #169. At `f3ea569c`, owner-reported isolated QA passed **1,150 JVM / 3 skipped** and **275 Android / 14 skipped**. Live Garmin Personal API, real FIT variations and final manual visual acceptance remain open.
- **Current constraints:** no verified structured-workout push to Garmin watches and no complete native run recorder. Keep signed-out/offline, source provenance and owner-isolated data flows reliable.

## Immediate product workstream — Running dashboard

**Phase 1 (approved, scoped to the owner-local QA worktree):**
1. Weekly labels for actual plan phase: Base, Build, Recovery, Deload, Taper, with honest unknown state.
2. Deterministic, evidence-backed weekly coach review (positives, attention points, next focus), not autonomous engine changes.
3. Planned/completed session and mileage summaries; defensible completion denominator and incomplete-history coverage.
4. Running page accessible with an approved plan, any run, or no history, while signed out or offline. `TodayDisciplineAnalytics.available()` must be an analytics sufficiency gate, **not a navigation gate**.
5. Reuse the existing AMOLED/lime Android UI, preserve Today/Training/Activity/More and all domain/storage constraints.

**Protected:** Leave local dirty/untracked `RunningHistoryTools.kt` untouched. No lap-pace interpolation or Phase 2 analytics in Phase 1. Source edits only; owner runs Android Studio builds/tests/emulator. Do not promote code or start Phase 2 without approval.

**Phase 2 (separate approval):** weekly and historical athlete analytics; intensity distribution; comparable workout efficiency; consistency and completion history; journal and goals; optional shoe mileage. Use measured data and explicit coverage; do not infer absent metrics.

## Engine 1.5 — ten-stage engineering roadmap

Detailed requirements, acceptance gates and research references: [`ENGINE_DEVELOPMENT_ROADMAP.md`](ENGINE_DEVELOPMENT_ROADMAP.md).

| Stage | Status at documentation review | Next acceptance focus |
| --- | --- | --- |
| 1. Structured Plan Builder workouts | Experimental candidate/partial | Multi-block editing, round-trip, offline reopen and full manual UI acceptance |
| 2. Coach-generated quality selection | Experimental Tempo/Threshold candidates | Family selection, exposure constraints, supported eligibility and explicit deferrals |
| 3. Initial dose, intensity, progression | Basic conservative experimental prescriptions | Evidence-bounded dosing, distinct loading/recovery policies and complete distance accounting |
| 4. Full-plan validation | Partial; PR #141 acceptance open | Resolve 3 responsive cases, finish complete Android and real-world plan review |
| 5. Performed-workout detection | Partial in Garmin draft #169 | Live Garmin, varied real FIT, provenance and manual-correction acceptance |
| 6. Relative effort and athlete baselines | Exploratory/descriptive only | Reliable personal comparisons and missing-data/confounder policy |
| 7. History-informed **initial** plans | Preliminary evidence constraints | Complete source coverage, credible qualification and transparent holds |
| 8. Marathon TID research evaluator | Approved research, not executable coaching policy | Advisory-only evidence with explicit distinction from Garmin HR zones |
| 9. Multi-discipline reliability/stabilization | Candidate static scheduling, incomplete qualification | Comprehensive safety, compatibility, offline and release gates |
| 10. Engine 2.x automatic adaptation | Deferred | Begin only after separate acceptance of stable Engine 1.5 |

**Architectural boundary:** Engine 1.5 constructs and validates static plans; Engine 2.x is reserved for future post-completion/recovery adaptation. No hidden Engine 1.0 fallback, no automatic production activation, and no revival of Exercise Library 2.0.

## Next engineering sequence

1. Complete the separately approved **Running UI Phase 1** without modifying the coaching engine or Garmin ingestion.
2. Close Engine 1.5 Batch 27B/PR #141 Android acceptance blockers and reconcile newer candidate HEADs.
3. Validate and integrate Garmin Section 1 #169 with authorized live API, historical FIT, manual zone/classification correction and offline owner-isolation evidence.
4. Continue Engine 1.5 stages 1–4: qualified quality-workout selection, safe dosing/progression and whole-plan acceptance; mark milestones complete only with exact-commit evidence.
5. Finish staged Engine 1.5 history and multi-discipline qualification (stages 5–9), including advisory marathon TID research.
6. Request explicit owner go/no-go on Engine 1.5 release routing. Engine 2.x remains deferred.

## Documentation and cleanup policy

- `docs/ROADMAP.md` is the **project index**; `docs/ENGINE_DEVELOPMENT_ROADMAP.md` is the **coaching roadmap**; `ENGINE15_EXECUTION.md` is the **chronological test/implementation ledger**; `AGENTS.md` is the **agent policy**.
- Old October 2026 Phase 4D/4E claims are historical, **not current actions**. Their evidence and engine contracts remain available in version control and historical documentation.
- Preserve all `research/` directories, experimental records, datasets, citations, migration schemas, compatibility providers and fixtures unless an explicitly reviewed deletion proves safe.
- Cleanup disposition and deferred candidates: [`docs/qa/repository-cleanup-audit-20261121.md`](qa/repository-cleanup-audit-20261121.md).
