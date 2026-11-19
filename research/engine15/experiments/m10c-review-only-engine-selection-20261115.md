# Engine 1.5 M10c — Explicit review-only research route and versioned local artifacts

Date: 2026-11-15. Stacked on draft PR #90 (signed-out local owner). **M10 is still incomplete.** No tests or Android build have been executed for M10c.

## Why the experimental branch cannot yet be a normal saved plan

The existing Android \`PlanSnapshot\` contract is a complete playable/approvable collection of \`PlannedSession\` objects, each containing schedule, duration, distance, intensity/quality indicators, status and optional workout steps. The current research modules M6a/M6b have **daily kilometer allocations** and **possible** quality-day slots only. M9a/M9b/M9c can review caller-supplied duration/distance claims but explicitly cannot authenticate source evidence, training tolerance, intensity, or approved execution. Converting a distance-only hypothesis into a normal \`PlanSnapshot\` by inventing workout minutes or threshold paces would misrepresent both athlete safety and product readiness.

M10c therefore adds an actual **explicit route in Android Week Preview**:

- **Existing coach (default)**: runs the current \`TracksideCoaching.request\` → \`PlanActions.generate\` → \`TrainingRepository.generatePlan\` → \`Engine1Engines.resolve\` path. The legacy/active Engine 1.5 engine, approved plans, workout/player controls, account records and rollback behavior remain unchanged.
- **Research review (opt-in)**: disables the normal Generate My Plan CTA, exposes the previously implemented M10a mileage-constraints input and shows the M6a/M6b read-only calendar or a concrete deferral. If a valid preview is visible, users can explicitly **Save comparison on device (not a plan)**. It is impossible for this choice to call \`PlanActions.generate\` via the builder: \`IndependentEngineRouteGate.requireProduction\` is called again at the production callback boundary even if UI disabling were bypassed.

## Versioned, non-executable research snapshot

New \`IndependentResearchDraft.kt\` contains \`IndependentResearchDraft\`, \`IndependentResearchDraftWeek\`, \`IndependentResearchDraftDay\`, \`IndependentEngineRoute\`, and \`IndependentEngineRouteGate\`.

- Distinct record \`kind="engine15-research-review"\`, \`schemaVersion=1\`, policy \`stridelab.engine15.m10c-read-only-research-draft-v1\`.
- Contains the **actual PlanRequest**, explicit selected running days, original caller-supplied starting mileage, weekly ceiling, per-day ceilings and long-run continuity inputs; the resulting M6a week/day kilometer values, recovery-week markers, and proposed-only quality days; original creation time and the current exact owner ID.
- Checks structural integrity on both construction and reload: week count/sequence, Monday start, running-day identity, one long-run day for 3+ runs, finite positive numbers, exact daily-to-weekly totals, no quality on a recovery or long day, no distances above the entered per-day/weekly caps, and no unsupported race, beginner/returning or mixed-discipline goal identity. This is **mathematical consistency**, NOT scientific qualification of the *entered* caps.
- The JSON intentionally contains no \`PlannedSession\`, \`sessions\`, \`status\`, \`engineVersion\` or prescription minutes. It cannot be decoded through \`PlanSnapshot.decode\`. Attempting to set \`executable\`, \`sourceEvidenceVerified\`, \`authorizedForPlanMutation\` or \`authorizedForProduction\` to true during import fails.
- \`recordId="engine15-research-<UUID>"\`. Records are stored in SQLite \`records\` under the current exact owner, in storage \`kind="exercise"\` rather than \`kind="plan"\`. Never written to \`outbox\` regardless of cloud sign-in. They are excluded from \`TrainingState.plans\`, workout calendar, approvals, plan backups and Garmin/activity import records.
- Research comparisons are loaded only for the **current** local-plan owner or signed-in cloud owner; signing in hides local-owner research and signing out restores it. The repository recomputes the research result from actual request/constraints **inside the serialized save operation**, rather than trusting client-provided preview objects.
- Up to 20 saved research comparisons per owner; additional saves defer without deleting existing work. Corrupt/unsupported stored records remain untouched, not replaced or silently assigned another owner.
- Android panel shows the number of stored comparisons for the current owner; no success toast or automatic plan changes are asserted before repository storage returns.

## Actual changed files

- \`IndependentResearchDraft.kt\`: serialized, versioned, bound-checked research document with permanent non-executable status.
- \`IndependentAndroidResearchPreviewPanel.kt\`: save action on an explicitly reviewed M6a/M6b result, with input invalidation and owner-visible saved count.
- \`TracksideWeekReview.kt\` and \`PlanBuilder.kt\`: switch Existing coach vs Research review on the real Android pre-generation screen, disable production CTA in research mode, guard the callback.
- \`PlanActions.kt\` and \`StrideApplication.kt\`: independent typed research action, separate from \`generate\` and all plan mutation actions.
- \`TrainingRepository.kt\`: local-only owner-scoped storage/reload of research comparisons; no cloud sync or saved PlanSnapshot mutation. Existing M10b guest storage and import rules unchanged.

## New regression tests — committed, not run

- \`IndependentResearchDraftTest.kt\`: **8 focused JVM tests**. Existing coach routing, research-vs-production block, immutable distance-only draft, recovery/quality/mileage preservation, owner/week/day validation, daily-cap tampering, non-executable guard, determinism.
- \`IndependentResearchDraftRepositoryTest.kt\`: **5 Android instrumentation tests**, queued for M10.1. Local creation and offline reopen without \`plan\`/outbox mutations, production route independence, cloud sign-in/out visibility isolation, JSON promotion/owner tampering refusal, and rejected request preserving earlier stored artifacts.
- All stacked M8/M9/M10a/M10b code also requires full exact-commit compile and regression testing. A successful M7 build cannot establish any M10 branch as validated.

## Outstanding release blockers (do not declare M10 complete)

1. **M10d UI & source reconciliation:** align with the newer approved Week Preview/Train Your Way UI from PR #78 without regressing the current AMOLED-black/lime component language. Complete the explicit local-plan export/migration decision from M10b; current device-only UUID does not port across installs.
2. **Actual coach admission:** research milestones M7/M8/M9 still cannot certify source coverage, quality intensity/dose, race-specific periodization, mixed-discipline interference or executable durations. Do not swap the saved \`PlanSnapshot\` generator to this research output until the required verified inputs, trained workout dose, attribution and session contracts are independently qualified. If they cannot be qualified, keep the integrated experiment review-only and record it as a failed production-admission gate; never call a read-only comparison the completed running coach.
3. **M10.1 Android testing AFTER finishing M10:** JVM tests, debug compile, instrumented SQLite/account separation, emulator end-to-end Plan Builder, workout logging and offline/restart, UI regression plus backup/import and rollback. Do not install on the physical phone yet.
4. **M10.5 Astra running-coach review AFTER Android tests:** inspect actual integrated generated output and longitudinal scenarios for Train Your Way and race goals, both experienced and novice athletes; identify unsafe/incoherent plans and evidence quality defects.
5. **M10.6:** implement Astra corrections and rerun all affected suites.
6. **M11:** final safe physical-phone install / QA only after the owner-visible acceptance gates pass.

This PR is stacked and draft/unmerged; no production engine, cloud data, previously approved plan, user device, or Engine 2.x output changed.
