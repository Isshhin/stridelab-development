# Engine 1.5 Batch B5 — availability changes and explicit review

Draft PR #37, isolated Engine 1.5 branch. No Trackside UI, Engine 2.0, or production merge.

## Rules

`Engine15Availability.preview(plan, newDays)` returns affected unstarted session IDs plus added/removed weekdays, without mutation. A change that removes an occupied day does NOT move a workout, increase workload, mark it missed, or discard its ID. The affected unstarted sessions become `status="unassigned"`, retaining their previous `date` as historical placement context. Unassigned is not an active scheduled workout. Affected approved plans enter `paused` review state.

An in-progress workout on the removed day is a hard conflict. Completed workouts remain historical. Locked placements cannot be invalidated without an explicit unlock. Additional available days change only the request and never auto-generate workouts. Manual `moveSession` can place an unassigned session back into an available date, checking the same daily budget, compatible double days, and recovery spacing. Plans cannot be reapproved with unresolved unassigned sessions or obsolete long/quality weekday preferences.

`PlanOperations.setLongRunDay` and `setQualityDays` permit explicit preference changes; affected future workouts are unassigned for review, not crammed into another day. A future Trackside UI can show the preview and require user confirmation. All operations are account-scoped and revision-checked through TrainingRepository. No Compose rules were introduced.

## Verification gates

Pure JUnit scenarios cover 9 sessions across six days, newly unavailable/added days, no catch-up, active/completed preservation, hard lock conflicts, manual re-placement, long/quality preference conflicts and JSON round-trip. Android emulator test exercises account-owned availability change, paused/unassigned offline reopen, revision check, manual move and persistence.

Do not mark B5 release-ready until exact-head offline JVM, Gradle unit, source-integrity and Android 35 emulator checks pass. Follow with B6 edited-state backup/reconciliation and B7 broad scenario matrix.
