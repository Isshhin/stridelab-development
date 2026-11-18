# StrideLab Development Roadmap

**Updated: 2026-11-18.** Private, developer-only Android application; no public release authorized.

This file is the repository's **project roadmap index and proposed batch schedule**. The [November 17 canonical coaching-development roadmap](https://github.com/Isshhin/stridelab-development/blob/docs/engine-development-roadmap-20261105/docs/ENGINE_DEVELOPMENT_ROADMAP.md) sets the original ten-stage order and detailed acceptance criteria. That canonical file is on a separate, unmerged documentation branch. The October 26 Android/WebView roadmap is retained below as historical context. Candidate code does **not** imply coaching qualification or release readiness.

**Owner decision (November 17):** Remove Engine 1.0 planning and generation dependencies from the independent Engine 1.5 pipeline **now**. Engine 1.5 must never silently fall back to Engine 1.0 for new plans; unsupported requests must explicitly defer. Keep old runtime code isolated temporarily for explicit rollback during development and historical-plan compatibility/migration; **retire the legacy runtime only after Engine 1.5 has passed acceptance**. Pre-acceptance private debug integration is **not** production cutover; independent experimental generation remains debug-only.

## Engine 1.5 and 2.x — 10-stage roadmap

| Stage | Current status | Progress | Required outcome |
| --- | --- | --- | --- |
| 1. Structured workout authoring | Candidate UI and contracts implemented; full manual verification pending | Partial | Author/edit/reorder steps and round-trip multi-block workouts through Plan Builder, approval and offline reopening |
| 2. Automatic workout selection | Tempo + Threshold functional in debug for eligible inputs | Partial | Goal/experience-specific decisions, safe deferral and more supported workout families |
| 3. Intensity, dosage and progression | Introductory conservative policies | Partial | Evidence-bounded quality dosage and progression; no fabricated HR, pace or threshold |
| 4. Complete-plan validation | Batch 27 Android acceptance underway | Partial | Whole-week/multiweek integrity, placement, recovery, approval, restart and offline reopening |
| 5. Automatic workout detection | Manual evidence labeling foundation | Early | Reliable workout/activity linking, detection with ambiguity and correction safeguards |
| 6. Effort and performance baselines | Some descriptive evidence available | Early | Grounded effort/performance baseline from sufficient observations, honest unknowns |
| 7. History-informed planning | Initial evidence constraints implemented | Early | Reliable training-history coverage and progression/hold decisions backed by evidence |
| 8. Training Intensity Distribution (TID) research evaluator | Research planned; not qualified | Planned | Advisory-only marathon TID reports, transparent missing data, and no conflation of critical-speed research zones with HR zones |
| 9. Multi-discipline refinement and Engine 1.5 stabilization | Not complete | Planned | Qualified running/strength/calisthenics/conditioning programming, broad Android/offline/migration validation and private coaching review; no hidden legacy generation |
| 10. Engine 2.x adaptation | Separate future development | Deferred | Dynamic workload/recovery adaptation only after Engine 1.5 is stable |

### Current checkpoint: Batch 27 / draft PR #141

- Experimental **eight-week Tempo and Threshold** candidates use runner-asserted segment distance, fixed quality durations, scheduled recovery weeks, weekday and hard-day spacing rules, plus explicit review/approval. This does **not** establish athlete-specific threshold physiology, pace, dosage progression or complete workout-library coverage.
- At the Batch 27 source checkpoint: **54 focused JVM passed; 1,075 candidate JVM passed (1 skipped); 1,073 tests in historical rollback-mode configuration passed (3 skipped);** no reported failures. Final candidate repository instrumentation passed **24 tests**.
- On [PR #141](https://github.com/Isshhin/stridelab-development/pull/141), the corrected Plan Builder Generate test passed **1** case, and the repository test class passed **24** cases on disposable Android 15 emulator `emulator-5554`.
- The externally orchestrated **process-restart harness passed five of eight scenarios**: draft, approved, equal target, completed workout and feedback. **Accepted** failed while preparing because the expected coaching-adjustment preview was empty; **dismissed** and **activity-label** were not reached. A date-dependent synthetic fixture is a plausible explanation, not a verified fix.
- Still open: rerun all eight restart scenarios, broader Android instrumentation by class (prior run ended after 19 of 250 discovered tests), and manual Tempo/Threshold full form → approval → restart → offline reopen. Preserve historical-plan compatibility and the release-disabled guard while preparing Engine 1.5 as the sole new-plan generator; do not silently route unsupported plans into Engine 1.0.
- The detailed [Engine 1.5 execution ledger](https://github.com/Isshhin/stridelab-development/blob/feature/engine15-batch27-android-acceptance-20261118/ENGINE15_EXECUTION.md) is on the **unmerged** Engine 1.5 branch. Its development notes should not be mistaken for code merged into `main`.

## Proposed batch implementation sequence

These batches are planning labels, **not claims that the work is done**. Multiple stages can advance concurrently; stage-completion status is defined by evidence and acceptance criteria.

| Batch | Stage(s) | Scope | Acceptance gate |
| --- | --- | --- | --- |
| **27A — restart acceptance** (current) | 4 | Repair and validate accepted/dismissed adjustment test fixture; run all eight restart scenarios | All eight scenarios pass prepare, force-stop/relaunch and offline verification with no skips |
| **27B — full acceptance checkpoint** (next) | 1–4, 9 groundwork | Shard Android UI tests; manual Tempo/Threshold switching/generation/approval/offline reopening; release gating and historical-plan compatibility | Reproducible green evidence and documented issues before feature-freeze checkpoint |
| **Private integration checkpoint** (after 27B) | 4, 9 groundwork | Exercise Engine 1.5 as the only new-plan generator inside an explicit private **debug/test route**, without hidden fallback; keep legacy code isolated pending final validation | Not production cutover; validate historical-plan compatibility and realistic athlete histories |
| **28 — workout selection and library** | 2 | Broaden goal-aware family selection; add Interval only where dosage, eligibility and plan integrity are supportable | Deterministic goal/family cases, complete segment fixtures, safe deferrals |
| **29 — dosage and progression** | 3 | Evidence-backed work doses, effort/intensity guidance and hold/progression rules | No automatic increase from elapsed weeks or unsupported performance claims |
| **30 — complete-plan robustness** | 4 | Larger scenario matrix across plan length, conflicts, rest, deload/taper, edits and approvals | Whole-plan consistency and lifecycle regression matrix |
| **31 — automatic workout detection** | 5 | Garmin/manual/import linkage and classification suggestions with uncertainty, deduplication and override | Correct and reversible classification, offline and owner-isolation tests |
| **32 — athlete effort baselines** | 6 | Auditable effort, performance and historical exposure summaries; explicit missing-data handling | Provenance and sufficient-evidence tests; no invented threshold/readiness values |
| **33 — history-informed initial plans** | 7 | Source-labeled athlete evidence and conservative initial-prescription progression/hold decisions based on history | Traceable, bounded new-plan choices; no automatic revision of approved plans |
| **34 — marathon TID research evaluator** | 8 | Source-zone HR description and a separate research-compatible 3-zone analysis only when athlete-specific evidence supports it; read-only, non-blocking plan review | No imposed 80/20 rule, no fabricated critical-speed thresholds, no automatic prescription changes |
| **35 — multi-discipline refinement and stabilization** | 9 | Strength/calisthenics/conditioning qualification, full JVM/Android/manual QA, historical-plan migration, offline/restart and private coaching review | Formal Stage 9 acceptance before sole-generator production cutover and final legacy runtime retirement |
| **36+ — Engine 2.x adaptation** (future label) | 10 | Garmin-style dynamic workload/recovery adaptation and broader workout variety | New research, safety gates and separate approval |

### Decision gates

1. **Now:** stop adding features until Batch 27A–27B acceptance is closed. This is a feature-freeze checkpoint, **not** completion of Stages 2–9.
2. **After Batch 27B:** optional **experimental debug/test-route integration** with Engine 1.5 as the only new-plan generator in that route and real-history testing; no hidden fallback. This is not production cutover; isolated legacy runtime code remains only until validation. Additional batches may continue afterward.
3. **After Stage 9 passes its complete acceptance gate:** seek an explicit owner go/no-go decision for production cutover to Engine 1.5 as the sole normal new-plan generator. Only then retire legacy new-plan generation; preserve historical-plan reading/migration. Engine 2.x remains future work.

## Historical Android / WebView roadmap

The October 26 milestones below are retained unchanged apart from the section heading. Their older “current” and “next” statements are not authoritative for the coaching roadmap above.

---

# Historical app roadmap (2026-10-26)

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
