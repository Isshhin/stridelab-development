# M3 — Confidence-aware athlete baseline contract

**Scope:** Baseline provenance and evidence readiness only. No training plan generation or safety policy changes. M4's single volume skeleton will consume the typed result; the current Engine 1.5 generator continues to consume legacy weeklyKm until separately approved.

## Source of truth

- PlanRequest.weeklyKm is still the **legacy numeric input** to Engine 1.5. The meaning of 0.0 in old snapshots is unclassified. Do not use it to determine whether the runner has no experience.
- PlanRequest.recentMileageBasis is new optional metadata. Its absence means LEGACY_UNSPECIFIED, not CONFIRMED_ZERO.
- PlanRequest.reportedPeakWeeklyKm is an optional peak-week statement, never automatically used as a sustainable average or training target.
- PlanRequest.longestKm is preserved; there is no recency or source proof on this legacy scalar. M3 names it reportedLongestRunKm without claiming verification.
- Existing IndependentRunningHistory already enforces four *fully elapsed* weeks, missing-distance rejection, duplicate/ambiguous source filtering and explicit coverage attestation. M3 reuses IndependentRunningHistoryEvidence as read-only input rather than writing a competing history algorithm.

## Output types

- RecentMileageBasis: LEGACY_UNSPECIFIED, UNKNOWN, SELF_REPORTED_AVERAGE, PEAK_WEEK_ONLY, RECORDED_PARTIAL, CONFIRMED_ZERO.
- AthleteBaselineConfidence: NONE, LEGACY_UNVERIFIED, SELF_REPORTED_PROVISIONAL, PARTIAL_ACTIVITY_ESTIMATE, COMPLETE_FOUR_WEEK_HISTORY.
- AthleteBaselineStatus: UNKNOWN, CONFIRMED_ZERO, PROVISIONAL_AVERAGE, PEAK_ONLY, COMPLETE_HISTORY, NEEDS_REVIEW.
- Engine15AthleteCapacity distinguishes reportedTypicalWeeklyKm, partialRecordedWeeklyKm and legacyUnclassifiedWeeklyKm; these values are **never merged into a false verified claim**.
- verifiedFourWeekAverageKm is non-null only with four explicitly complete, nonambiguous weeks. An incomplete captured week does not become a confirmed 0 km week.
- typicalWeeklyKm is null if unknown, peak-only without confirmed history, or contradictory. It is 0 only for explicitly confirmed zero or a genuinely verified complete-zero reference.
- A runner's self-reported 75 km/week can be a **provisional** initial baseline without an app-import requirement. It is not a verified threshold, quality-dose permission or fitness measurement.
- readyForVolumeSkeleton means structural evidence readiness, **not** clearance to run that distance or permission to increase mileage.

## M4 consumer contract (future; not implemented)

M4 is the **single owner** of the weekly volume skeleton. It consumes Engine15AthleteCapacity and declared schedule constraints. It may use a reported typical week provisionally but should initially use narrower progression rules than fully corroborated history **only after a separate Gate B numeric policy review**. It must never treat unknown as zero, treat a peak as average, infer fitness or automatically increase quality work from a typical mileage number. Adverse recovery evidence and schedules still apply separately.

Do not instantiate a second weekly planner inside Engine15RunningProgram. The current Engine 1.0 → Engine 1.5 allocation remains as-is until M4 and M5 are isolated, measured and approved.

## Compatibility and testing

The two new request fields are omitted from legacy JSON unless explicitly set and are appended to PlanRequest's constructor so existing positional calls remain valid. PlanSnapshot and PlanBackup still use their existing schema and ownership policy. M3 tests check old/new JSON roundtrips, structural contradictions, complete-week evidence, exact original-vs-metadata session parity and no loss of recovery holds.

**Validation status:** code committed; focused local Gradle test and emulator inspection pending. This document is not evidence that the new code has compiled or passed tests.
