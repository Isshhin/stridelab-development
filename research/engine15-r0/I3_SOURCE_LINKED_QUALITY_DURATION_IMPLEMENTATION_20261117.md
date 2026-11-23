# Engine 1.5 I3 — Repeated source-linked quality duration consistency (shadow)

Date: 2026-11-17. Branch `feature/engine15-i3-source-linked-quality-duration-20261117`, stacked on I2 PR #122 after user reported full selected I2 regressions passing (14s). Frozen preflight: `I3_SOURCE_LINKED_QUALITY_DURATION_PREFLIGHT_20261117.md`.

## Delivered
- New independent I3 source-lap duration gate uses original typed M4B Result, one proposed M5B quality request and exactly two caller-provided linked lap observations with saved PlanSnapshot, FIT/Garmin ActivityRecord, source ActivityDetail, as-of date and local time zone.
- Independently reruns M5B from M4B before accepting any evidence. M5B recovery, long-day, returner, unreviewed-dose and time limits remain effective.
- Reuses existing `IndependentQualitySourceBinding.inspect` for each recorded claim, which checks unique saved session→source links, source types, actual lap starts/durations, no overlap with timer pauses and bounded source window. Source owner namespace and family are still caller assertions, not independently authenticated.
- Rejects duplicate source activity IDs, repeated plan session IDs, same-day claims, wrong asserted owner/family, missing/unmatched details and conflicted or unsupported recording evidence.
- Reconciles a conservative floor of the smaller of two recorded lap work durations. The original explicit M5B confirmedRecentWorkSeconds must NOT exceed this bound. It is NEVER auto-filled or increased from the observed data.
- Preserves the original M5B candidate and source-binding outputs. Candidate flags remain false: ownerSourceNamespaceAuthenticated, qualityIntensityVerified, athleteResponseVerified, doseAuthorized, distanceReconciled, executable, authorizedForProduction.
- No schedule, saved-plan persistence, Android UI, live Engine1, live Engine15 generator or mileage algorithm changes.

## Focused synthetic tests
Nine tests: two distinct linked recordings success only as duration consistency; one/extra/duplicate source/session/day; wrong owner/family; missing/tampered lap/pause; inflated claimed dose; M5B upstream quality holds; stale/other-owner/missing activity; manual source and self-report rejection; deterministic replay.

## Validation status
Source, tests and docs committed. **I3 has not been locally compiled or executed.** Run I3 + IndependentQualitySourceBindingTest + I2/I1 and selected M3–M6/Q2/Q3/R0 regressions from the Q3 validation checkout. Fix any test failures before considering the I3 shadow gate passed. The result is not yet independent quality intensity evidence or coaching dose authority.
