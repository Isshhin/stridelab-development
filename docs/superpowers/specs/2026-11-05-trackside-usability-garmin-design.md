# Trackside 3.0 Usability and Garmin Foundation Design

> **Later approved product requirement (2026-11-05):** [Engine 1.5 primary with Engine 1.0 controlled fallback and quality-workout acceptance](../../qa/engine15-primary-plan-quality-requirements-20261105.md). This UI/Garmin QA spec and its original source/test baselines remain historical. The old Engine 1.0 default is a current code mismatch, not a newly accepted release condition. Implementation and tests still pending.

## Intent

Make the frozen Engine 1.5 + Trackside integration genuinely usable on a real phone by verifying outcomes through production routes, correcting confirmed interaction defects, protecting persistence and Engine 1.0 rollback, and adding only a bounded official Garmin Personal API foundation after core usability is stable.

## Baseline and authority

- Repository: `Isshhin/stridelab-development`.
- Starting integration head: `9d3c559e4b92a366a3b52934b19ea37fb0e65084`.
- Starting remote branch: `integration/trackside3-engine15-20261105`.
- Approved Figma file: `v73TsYo6gKxUoiUock2sSE`.
- Authority order: newest explicit approved behavior, newest handoff, newest approved Figma, newest acceptance documentation, roadmap, historical records.
- Product comparison: documentation to Figma to Android implementation to behavioral test.

## Goals

- Zero known P0/P1 interaction defects after current verification, without claiming bug-free software.
- A complete behavior matrix for visible controls and gestures.
- A genuine anchored Activity Detail sheet with cooperative inner scrolling and accessible expand/collapse.
- Correct per-session Running Weekly Setup selection, swap, removal, persistence, and automatic/manual behavior.
- Regression coverage through real production navigation and state owners where practical.
- Full Engine 1.5, offline, restart, and account-isolation verification.
- A clean Garmin source-adapter boundary using only official Personal API behavior, with secure credential handling and mocks when a live key is unavailable.
- Exact-head build, verification, documentation, and unsigned release evidence when permanent signing material is unavailable.

## Non-goals and anti-goals

- No whole-app redesign, post-Engine-2 UI, Engine 2 implementation/research changes, or unrelated PR merge.
- No Engine 1.5 policy changes except a proven compatibility correction.
- No removal of Engine 1.0 rollback, persistence/versioning safeguards, FIT/TCX/GPX, or Intervals.icu.
- No unofficial Garmin endpoints, account scraping, fabricated credentials/data, secret logging, personal athlete data, or replacement signing key.
- No assertion weakening, hidden failures, or release-ready claim for debug signing.

## Architecture

### Activity Detail

Use a screen-owned, saveable sheet anchor state with collapsed and expanded anchors derived from current constraints. A dedicated nested-scroll connection consumes drag only at the sheet boundary: upward motion expands the sheet before inner content scrolls, and downward motion collapses only when inner content is already at its top. Settle to stable anchors using velocity and distance thresholds. Keep the map in the backing layer, preserve tabs across sheet motion, expose expand/collapse semantics actions, and make Back collapse an expanded sheet before leaving.

### Weekly placement

Treat the weekday row as a selector when its day is occupied: select the owning slot first and never mutate placement on that tap. Movement happens only through an explicit destination-day interaction after the slot is selected. Keep slot 0 as Long Run, slots 1 through `qualityCount` as Quality, and remaining slots as Easy. Centralize slot ownership lookup so Running, other disciplines, Availability, Week Preview, Training move flows, and tests use the same index-safe semantics.

### Garmin

Keep transport, secure credential storage, token exchange, normalization, dedupe, persistence, and UI state separate. Garmin produces provenance-bearing source records and must not directly mutate Engine 1.5 coaching. Implement only endpoints and token lifecycle documented by official Garmin sources accessible during this pass. If authenticated specifications or credentials are unavailable, stop at a tested adapter boundary and report the exact live-connection dependency.

## Failure behavior

- Invalid or incomplete manual placement blocks Continue and preserves the user's draft.
- Unsupported or ambiguous Garmin responses fail closed, retain existing data, redact secrets, and surface concise connection state.
- Uncertain dedupe never silently deletes an activity.
- Missing GPS or health metrics remain explicitly unrecorded; no geography or physiological state is fabricated.
- Test, build, emulator, signing, or environment limitations are documented as limitations rather than converted into success claims.

## Verification strategy

Follow red-green-refactor for each correction. Start with targeted behavioral instrumentation, then run module/JVM tests, Android unit tests, production-route instrumentation, four responsive profiles, Engine 1.5 regressions, offline/reopen/account isolation, Garmin mocks, adversarial QA, and fresh exact-head verification. Release evidence is produced only after these gates and remains unsigned unless the permanent identity is available.

## Approaches considered

1. **Small state-owner corrections and existing Compose primitives (selected):** preserves architecture and minimizes persistence risk while delivering real gestures and per-slot behavior.
2. Replace the Activity screen and builder wholesale with new navigation/state architecture: rejected as unnecessary redesign and too risky for the frozen Engine 1.5 line.
3. Depend on Material modal/bottom-sheet defaults without modeling the map-backed full-screen composition: rejected because it does not directly express the approved persistent map and route-owned Back behavior.

