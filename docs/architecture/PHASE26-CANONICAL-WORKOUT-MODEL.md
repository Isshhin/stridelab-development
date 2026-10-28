# Phase 26 — Versioned multidisciplinary workout model (proposed contract)

**Status:** Source-checked design draft; no feature implementation, schema migration, or new discipline activated. **Baseline:** `25be8f3828374d1e539c7e825afa20fbcb652b56`.

## Verified legacy model and compatibility rules

`EngineContracts.kt` defines `PLAN_SCHEMA=1`, `ENGINE1_VERSION="1.0.0"`, `PlanSnapshot(owner,id,revision,engineVersion,policyId)`, `PlannedSession(id,date,discipline,title,minutes,distanceKm,kind,lower,quality,exercises,steps,status,log,linkedActivityId,linkPreviousStatus,swaps,logRevision)`, `ExercisePrescription(id,name,sets,reps?,seconds?,rest,equipment,cue)`, `WorkoutLog(at,minutes?,distanceKm?,effort?,sets,notes,performedOn?)`, and `SetRecord(exerciseId,set,reps?,kg?,seconds?)`. The legacy codec writes plan JSON inside account-scoped `records` in SQLite `stridelab-athlete-v1.db`; unknown policy/engine snapshots retain `foreignSnapshot` for read/export and are not regenerated. `WorkoutEntryDraft` represents unconfirmed input and is **not** a logged set.

**Do not rewrite legacy JSON or create artificial actuals.** Version 2 is a read-adapter model first. Keep `PLAN_SCHEMA=1` unchanged and keep existing decoders until a separately approved migration. On an unknown plan engine/policy/schema, preserve the exact original raw bytes and return a safe unsupported/read-only result. An unmapped field is missing/unknown, never silently zero.

## Candidate canonical v2 shape

```text
CanonicalWorkoutV2 {
  version = 2
  ownerId, planId?, sessionId?, planRevision?, originalEngineVersion?, originalPolicyId?
  sourceSnapshot: { kind: "engine1-plan" | "recorded-activity" | "manual-log",
                    originalRecordId, originalSourceId?, rawPreserved: true }
  scheduledAt?, performedAt?, status: PLANNED | IN_PROGRESS | COMPLETED | PARTIAL | MISSED | SKIPPED
  primaryDiscipline: RUNNING | STRENGTH | CALISTHENICS | CONDITIONING | MOBILITY
  attributes: set(ISOMETRIC, PLYOMETRIC, ECCENTRIC, POWER, ENDURANCE, SKILL, IMPACT_*)
  blocks: [{ blockId, type: WARMUP | MAIN | ACCESSORY | COOLDOWN | OTHER,
             items: [{ itemId, exerciseId?, kind: EXERCISE | RUN_INTERVAL | REST | HOLD,
                       targets?, observations: [SetOrIntervalActualV2], extras? }] }]
  sessionActual: { durationS?, distanceM?, sessionRpe?, notes? }
  provenance: { account, source, externalId?, originalHash?, observedAt?, receivedAt? }
}
SetOrIntervalActualV2 {
  exerciseId?, sequence, reps?, loadKg?, durationS?, distanceM?, effortRpe?,
  contacts?, restSeconds?, dataOrigin: MANUAL | DEVICE | IMPORTED, sourceRecordId?
}
```

The above is a **proposed contract**, not persisted columns or Kotlin code. Prefer typed, finite, unit-labelled fields for common metrics. Versioned validated JSON extras are permitted for modality-specific values, but do not embed GPS/HR time series in individual set extras. Their high-frequency records remain separate provider streams with independent source identities. Preserve zero-versus-missing distinctions: zero reps can be explicitly recorded; absent counts are null.

## Read-adapter mapping for Engine 1.0

| Legacy source | v2 mapping | Information unavailable; preserve as unknown |
|---|---|---|
| `PlanSnapshot` | owner, plan ID/revision, original engine/policy, scheduled blocks | Any medical readiness, progression validity |
| `PlannedSession` | date, primary discipline, scheduled duration, target distance, quality/lower flags as attributes | Actual completion solely from schedule; never infer it |
| `ExercisePrescription` | planned MAIN block's exercise targets; preserve ID and target reps/seconds/sets/rest | Movement plane, joint stress, exact impact |
| `WorkoutLog` | session actual date/time/duration/distance/RPE and notes | HRV, HR, per-set RPE, movement quality |
| `SetRecord` | actual set index, reps, kg, seconds | Actual sets not logged, contact counts, range of motion |
| `linkedActivityId` | explicit association with existing recorded source ID; NOT a second event | Provider-to-provider physical equivalence |
| `SessionSuggestion` | legacy accepted lighter target; never invent Engine2 approval receipt | Original source-evidence and decision receipt |

## Seven illustrative modality examples — proposed, NOT observed user records

1. Running: MAIN → easy run interval `durationS=2400`, `distanceM=null`; device stream references stay external.
2. Strength: MAIN → squat → set 1 `reps=8`, `loadKg=20`, `durationS=null`.
3. Calisthenics: MAIN → pull-up → set 1 `reps=5`, `loadKg=null`.
4. Conditioning: MAIN → 3 timed work/rest intervals; `durationS` per interval; optional observation.
5. Isometric attribute: STRENGTH → wall sit → `durationS=45`; isometric is an attribute, not necessarily a discipline.
6. Plyometric attribute: STRENGTH/CONDITIONING → hops → `contacts=20`; no automatic suggestion until impact policy review.
7. Mobility: MOBILITY → hip mobility drill → `durationS=60`, `reps=null`; future unsupported discipline may remain read-only in Engine1.

## Source identity and validated flags

Within one account, a *source record* is identified by provider namespace + external ID or a verified content hash, **not** just a run date/distance. A source can be the same physical event as another source without sharing its identity; do not automatically merge. A plan link is a user decision and must have a separate unique constraint. Per-session demand flags (`lowerBody`, `impact`, `jointTendonDemand`, `cardioDemand`) are descriptive/unknown when missing; they are not clinical injury predictions.

## Acceptance gates before implementation

- Adapter fixtures for running, strength, calisthenics, conditioning, and mixed schedules; no unexplained loss of actual fields.
- Legacy v1 decode → v2 read projection leaves encoded v1 bytes and owner unchanged.
- Unknown future engine/policy/schemas remain recoverable without fabricating an editable plan.
- Two sessions on one day remain independent; partial, unlogged, and source-linked states never become fabricated completed sets.
- No database schema change or installed APK update occurs as part of this document.
