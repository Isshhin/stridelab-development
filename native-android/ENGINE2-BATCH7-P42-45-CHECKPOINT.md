# StrideLab Batch 7 candidate — Steps 42–45

**Branch base:** stacked Engine 2.0 draft PR #20, SHA `9e868ca0fe689c87a3084b71cfa0c86c043b8087`. All ancestral PRs #13–20 remain unmerged and unverified by exact-head Gradle/emulator. **This checkpoint does not activate Engine 2.0 in Android UI.**

## Phase 42 — Read-only repository projection

- `Engine2RepositoryReadProjection.kt` maps trusted account-scoped `TrainingState` activity summaries, completed/in-progress native logs and supported wellness readings into `Engine2ContextAssembler`.
- Actual source values only: distance, duration, average HR, performed reps/loads/holds; not inferred RPE, FTP, injury risk or generic readiness.
- Linked cached source runs and the original manual session log are not counted as two training events. Unsupported source types and unknown plan policies remain in original saved records; projection issues are returned rather than writing a substitute plan.
- Undifferentiated HRV methods are withheld from `Engine2Metric.HRV_MS` until units/method provenance can be represented.
- Tests: `Engine2RepositoryReadProjectionTest.kt` (actual strength set, linked source event, mixed-account denial, HRV method limitations).

## Phase 43 — Conservative cross-discipline workload rule

- `Engine2CrossDisciplineAdvisor.kt` uses existing Engine1 `lower`, `quality` and `kind=long` metadata, the preexisting `Engine2ScheduleAuditor.LOWER_BEFORE_LONG` issue, and confirmed prior running + strength actual observations to propose a reviewable **shorter lower-body session before a long run**.
- It does not infer current fatigue from prior work, prescribe intensity increases, move workouts automatically, edit historical actuals or create an injury risk score. Rejects missing evidence or completed targets.
- Tests: `Engine2CrossDisciplineAdvisorTest.kt`.

## Phase 44 — Proposed approval slice, not writable activation

- The advisor returns an `Engine2DurationProposal`. Existing `Engine2ReviewPreview` gives before/after weekly minutes.
- **No durable approval binding or active Compose click handler was enabled.** Draft PR #20's isolated ledger still does not commit `TrainingStore.records` approved plans and proposal receipts in the same SQLite transaction. Phase 28's single-authority ADR and Batch 4 link/migration acceptance must be resolved first.
- Running adaptations, gym/calisthenics progressive overload, catalog impact/muscle graph, and longitudinal charts are NOT implemented in this slice.

## Phase 45 — Deterministic explanations and honest status

- `Engine2CoachExplanations.kt` provides a source-labeled, account-scoped evidence list, original/proposed session duration and week minutes, with uncertainty warnings and a stale-revision block.
- No LLM, network, fabricated VO2max or unsupported readiness analysis.
- Tests: `Engine2CoachExplanationsTest.kt`.

## Remaining required integration gates

1. Compile exact-head Kotlin/JVM and Android instrumentation for **all stacked ancestor PRs**; correct any model contract errors first.
2. Preserve all Engine1 actual histories; implement a single-DB, account-scoped plan revision + proposal receipt transaction. No cross-database dual mutable authority.
3. Test two concurrent approvals, replay/stale proposal, reject/postpone, offline reopen and account change with delayed callback. Do not activate approval UI without passing these.
4. Validate the interference rule with real source-backed historical runs/strength logs and, where available, qualified coaching review. A conservative scheduling warning is not proof of performance benefit.
5. Integrate accepted logging/link/UI/import branches only after their own exact-head checks and owner approval. Do not merge wholesale.
6. No CI/workflow dispatch, APK, source baseline edits, production SQLite schema change, web feature development, or live Garmin authentication was performed.

**Status classification:** Source-only draft candidate. Engine 2.0 integrated private-alpha coach is NOT complete.
