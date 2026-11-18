# StrideLab Development Roadmap

**Updated: 2026-11-18.** Private, developer-only Android application; no public release authorized.

This file is the repository's **project roadmap index and proposed batch schedule**. The [November 17 canonical coaching-development roadmap](https://github.com/Isshhin/stridelab-development/blob/docs/engine-development-roadmap-20261105/docs/ENGINE_DEVELOPMENT_ROADMAP.md) sets the original ten-stage order and detailed acceptance criteria. That canonical file is on a separate, unmerged documentation branch. The October 26 Android/WebView roadmap is retained below as historical context. Candidate code does **not** imply coaching qualification or release readiness.

**Established architecture — already implemented (November 17 checkpoint):** The independent Engine 1.5 generator is **already separate from Engine 1.0**. Engine 1.0 has **already been retired as a fallback within the independent Engine 1.5 planning path**; unsupported requests must defer explicitly rather than generate an Engine 1.0 substitute. This is a completed architectural decision, **not another development batch or a pending Engine 1.0-removal task**. The current repository still contains legacy policy handlers and flag-controlled older routing for compatibility and verification, so this statement does not claim that every Engine 1.0 source file or inactive route has been deleted. The remaining work is Engine 1.5 **acceptance, integration and release gating**, not making its planner independent. The experimental independent route remains debug-only pending release acceptance.

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
- At PR #141 commit `00167cc5`, the externally orchestrated **process-restart harness passed all eight scenarios**: draft, approved, equal target, completed workout, feedback, accepted adjustment, dismissed adjustment and activity label. Each included force-stop, relaunch and offline reopen. The accepted/dismissed synthetic fixture now maintains an eligible three-day evidence-to-target interval. No production coaching safeguards changed.
- Other PR #141 results: 11 focused feedback JVM tests passed; targeted Android shards passed (repository 69, coach controls 15, route restoration 1, approved builder 15, Plan Builder integration 2, responsive Generate 1, calendar management 4). Debug/instrumentation/release APK builds passed; release `ENGINE15_INDEPENDENT_RUNNING_ACTIVE=false`. **Responsive suite 25/28 passed:** one weekly-summary/legend overlap assertion and two ambiguous-mileage mixed-plan fixtures remain unresolved. **Full Android instrumentation and manual Tempo/Threshold entry → approval → restart → offline reopen are still pending.** Engine 1.5 remains independent, with no Engine 1.0 fallback inside its planning path. See [PR #141](https://github.com/Isshhin/stridelab-development/pull/141) for current acceptance evidence.
- The detailed [Engine 1.5 execution ledger](https://github.com/Isshhin/stridelab-development/blob/feature/engine15-batch27-android-acceptance-20261118/ENGINE15_EXECUTION.md) is on the **unmerged** Engine 1.5 branch. Its development notes should not be mistaken for code merged into `main`.

## Proposed batch implementation sequence

These batches are planning labels, **not claims that the work is done**. Multiple stages can advance concurrently; stage-completion status is defined by evidence and acceptance criteria.

| Batch | Stage(s) | Scope | Acceptance gate |
| --- | --- | --- | --- |
| **27A — restart acceptance** (completed on PR #141) | 4 | Eligible synthetic accepted/dismissed fixtures, eight process-death scenarios | **8/8 passed** on disposable Android 15 emulator; no production coaching changes |
| **27B — remaining acceptance checkpoint** (current) | 1–4, 9 groundwork | Address three responsive failures, finish remaining class-sharded/full Android suite, manual Tempo/Threshold generation/approval/offline reopen and release gating | Reproducible green evidence, source-accurate regression reports and documented issues before integration |
| **Private integration checkpoint** (after 27B) | 4, 9 groundwork | Wire the **already-independent Engine 1.5 generator** into the ordinary private app's controlled debug/test flow; preserve its explicit deferrals and existing historical-plan compatibility | Not a production cutover; validate actual UI routing, persisted plans, and realistic athlete histories |
| **28 — workout selection and library** | 2 | Broaden goal-aware family selection; add Interval only where dosage, eligibility and plan integrity are supportable | Deterministic goal/family cases, complete segment fixtures, safe deferrals |
| **29 — dosage and progression** | 3 | Evidence-backed work doses, effort/intensity guidance and hold/progression rules | No automatic increase from elapsed weeks or unsupported performance claims |
| **30 — complete-plan robustness** | 4 | Larger scenario matrix across plan length, conflicts, rest, deload/taper, edits and approvals | Whole-plan consistency and lifecycle regression matrix |
| **31 — automatic workout detection** | 5 | Garmin/manual/import linkage and classification suggestions with uncertainty, deduplication and override | Correct and reversible classification, offline and owner-isolation tests |
| **32 — athlete effort baselines** | 6 | Auditable effort, performance and historical exposure summaries; explicit missing-data handling | Provenance and sufficient-evidence tests; no invented threshold/readiness values |
| **33 — history-informed initial plans** | 7 | Source-labeled athlete evidence and conservative initial-prescription progression/hold decisions based on history | Traceable, bounded new-plan choices; no automatic revision of approved plans |
| **34 — marathon TID research evaluator** | 8 | Source-zone HR description and a separate research-compatible 3-zone analysis only when athlete-specific evidence supports it; read-only, non-blocking plan review | No imposed 80/20 rule, no fabricated critical-speed thresholds, no automatic prescription changes |
| **35 — multi-discipline refinement and stabilization** | 9 | Strength/calisthenics/conditioning qualification, full JVM/Android/manual QA, historical-plan compatibility, offline/restart, private coaching review and exact release-route verification | Formal Stage 9 acceptance before production promotion; do not reopen completed Engine 1.5 architectural separation |
| **36+ — Engine 2.x adaptation** (future label) | 10 | Garmin-style dynamic workload/recovery adaptation and broader workout variety | New research, safety gates and separate approval |

### Decision gates

1. **Now:** Batch 27A restart verification is complete; finish **27B** acceptance blockers before joint private integration. This checkpoint is **not** completion of Stages 2–9. Share Studio work is isolated in [draft PR #143](https://github.com/Isshhin/stridelab-development/pull/143), stacked on PR #141.
2. **After Batch 27B:** optional **experimental private-app integration** of the already-independent Engine 1.5 generator; test UI selection, saved plans, offline behavior and real histories. No fallback is added to the independent path. This is not a production cutover. Additional batches may continue afterward.
3. **After Stage 9 passes its complete acceptance gate:** seek an explicit owner go/no-go decision to promote the already-independent Engine 1.5 pipeline through the production/release route. Verify no old-policy substitution remains in that route and preserve historical-plan readability. Do not describe Engine 1.0 fallback retirement as unfinished engine development. Engine 2.x remains future work.

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
