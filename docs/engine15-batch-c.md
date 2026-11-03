# Engine 1.5 — Batch C: review-only workout feedback (candidate)

Branch: `feature/engine15-versioned-foundation-20261103`
Review: https://github.com/Isshhin/stridelab-development/pull/37

Status: code and synthetic tests added; **not released**, not enabled in Plan Builder, not merged. This file is an implementation handoff, not evidence that the candidate improves physiological outcomes.

## Architecture and explicit boundaries

`Engine15Feedback.kt` contains a deterministic `preview(plan, asOf, allPlans)` that **never mutates or persists** a plan. Only a supported **Engine 1.5 bounded-running** approved plan can receive new proposals; paused plans can still dismiss previously staged proposals. The older Engine 1.5 foundation and every Engine 1.0 policy are untouched, and unsupported future policies remain read-only. No Engine 2.0 or Python Research Lab code is imported. The generator's existing version/policy pair is unchanged; `stridelab.engine15.feedback-20261103.1` identifies the separate post-generation suggestion policy.

Possible evidence is narrowly defined:
- Explicit **missed/skipped** session with reason `Illness`, `Fatigue`, or `Soreness`, reported within the last three days; absence of a workout log is **not** a miss.
- A **completed** session with a stored `WorkoutLog`, positive log revision, explicit actual `performedOn` within the last three days, and manually recorded RPE at least 8/10, or RPE at least 7/10 together with duration above 135% of planned. Strength/calisthenics requires an actual performed set. Recorded time/effort is historical evidence, not a measured readiness or fitness estimate.
- Linked-only activity records are **not** sufficient to infer exertion. Duplicate source links are rejected as evidence, including across supplied plans. In-progress logs, missing RPE, missing actual performance date, zero actual strength sets, stale/late records, and incomplete weeks do not trigger proposals. Pending same-day sessions are not targeted, avoiding action before late uploads. No missing duration, mileage, heart rate, pace or recovery metric is imputed.

Only the earliest **unstarted, unlogged, unlinked, unsuggested running** session scheduled strictly after today and within four days after the source event can be targeted. A single source proposes at most one reduction; competing sources do not stack reductions. An explicit recent illness or a targeted quality session leads to a **rest** proposal (0 minutes). Other supported signals recommend a ~25–30% shorter session, minimum 10 minutes, never an increase. No quality workout is rewritten into an easy workout while retaining hard workout steps; a 0-minute rest is applied as `skipped` only after explicit acceptance. The original session fields and source log remain intact.

## Reviewing and durability

`TrainingRepository` exposes these non-UI methods:
- `previewEngine15Adjustments(id, asOf)`: read-only list of candidate proposals.
- `stageEngine15Adjustment(id, revision, proposal, asOf)`: validates both plan revision and the complete recalculated proposal inside `changePlan`; persists an **unaccepted** `SessionSuggestion`.
- Existing `acceptSuggestion(id, revision, sessionId)`: explicit user approval; changes only effective minutes or marks a selected rest day skipped.
- `dismissEngine15Adjustment(...)` and `dismissStagedEngine15Adjustment(...)`: retain the original target and write a durable source/target audit record in existing `PlanFeedback`; a dismissed target is not offered again.

The existing repository uses an account-scoped SQLite/outbox transaction and checks optimistic plan revisions. This batch reuses it; there is no silent write, network dependency, schedule movement, mileage redistribution, new JSON schema or daily time-budget increase. Tests cover pure deterministic previews, missing/partial inputs, obsolete revisions, duplicate activity links, recorded strength sets, rejected/accepted proposals, owner isolation, JSON snapshot roundtrips and backup restoration.

## Limitations / outstanding gates

- **No visual review/accept/dismiss controls yet**. No Batch C feature is exposed to app users, deliberately reserving Trackside UI for Batch E.
- The decision is based on one explicitly recorded event, not longitudinal recovery modeling or scientific proof. No automatic weekly volume, threshold, race taper, progression or strength progression revision.
- Linked Garmin/Intervals/FIT records without manual perceived effort remain evidence-insufficient; no attempt is made to convert activity metrics into subjective effort.
- Current `PlanOperations.missed()` contains separate existing Engine 1.0-style suggestions; this candidate does not change that method or auto-stage Engine 1.5 suggestions.
- Batch D must run exact-head Android JVM checks, emulator/device SQLite reopen and account-switch tests, concurrency/rejection checks, source-link uniqueness, restore/backup validation, multi-discipline scenario matrices, and the existing Exercise Library auxiliary JVM harness investigation. Keep the review draft and do not claim full-green CI if that pre-existing harness fails.
- Only Batch E should expose Engine 1.5 in the finished Trackside Plan Builder, after an explicit integration approval.

## Handoff contract

Owner: Engine 1.5 isolated PR #37. Trackside visual work remains in PR #36. Engine 2.0 and its independent Python Simulation/Research Lab are separate. Existing approved workouts and older policies must not be mutated or implicitly migrated. Maintain opt-in and approval gates.
