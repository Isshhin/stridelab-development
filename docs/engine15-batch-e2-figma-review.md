# Engine 1.5 — Batch E2 Figma review candidates

Created **3 November 2026**. This is an editable, unapproved visual specification for future Kotlin/Compose integration. **No code/UI integration, PR merge, APK compilation, or production rollout is authorized by this document.**

## Figma links

- [Review page · all five states](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-2) — new page: `37 · ENGINE 1.5 · RECOMMENDATION REVIEW · FOR APPROVAL`.
- [01 Preview, not applied](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-5) — the original workout is still active; Review suggestion or Keep original.
- [02 Explicit decision, pending](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-69) — full source evidence, 60→45-minute comparison; Accept 45 min or Dismiss.
- [03 Accepted shorter run](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-133) — 45-minute time-only current target; no active 8.0 km, pace, or original structured interval target; original prescription retained as history.
- [04 Accepted rest](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-190) — approved 0-minute rest, no workout steps or catch-up mileage; original run remains historical context.
- [05 Stale evidence](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=834-247) — changed source or plan revision invalidates the older decision; refresh or return to original.

Approved sources retained untouched: [Trackside Plan Management 732:3](https://www.figma.com/design/v73TsYo6gKxUoiUock2sSE?node-id=732-3) and floating Training navigation 732:97, both in approved page 35. The new states belong exclusively to review page 37.

## Structural and visual verification

All five frames are native, editable Figma nodes (text, nested autolayout frames, gradients, metric blocks, actions, and cloned native navigation), each **412×950 px**. Source Trackside gradients, semantic color variables, Roboto heading/body typography, charcoal gradient cards, coral running accents, teal recovery status and floating five-tab navigation are reused. The screen content ends at approximately y=664–717; the approved navigation begins at y=858; no content/navigation overlap. Screenshots were inspected for all five states with no visible clipping. This is a design review, **not** a claim of exact parity with an unimplemented Android screen.

The demo event is synthetic **2 November 2026**: self-reported 35 actual minutes and RPE 9/10 after a run; a future easy session originally 60 min / 8.0 km may be recommended as 45 minutes. A separate explicit missed-reason Illness example yields a reviewed 0-minute rest. Values are illustrative and must be bound to the signed-in account's own plan/preview data at implementation time. Do not infer illness or readiness from wearable data.

## E3 implementation contract (after user design approval only)

1. **Default safety:** Engine 1.0 stays default and all saved plans keep their original engine version/policy and log history. Engine 1.5 remains opt-in, experiment-labelled. Do not merge PR #37 into approved Trackside PR #36 automatically.
2. **Preview:** render `TrainingRepository.previewEngine15Adjustments(planId, asOf)` without writes. Explain the source session/date, manually entered RPE or explicit missed reason, proposed change and lack of physiological inference. No auto-application.
3. **Stage/decide:** `stageEngine15Adjustment(id, revision, proposal, asOf)` persists an unaccepted suggestion; `acceptSuggestion(id, latestRevision, targetSessionId)` requires explicit confirmation and revalidates source evidence; `dismissEngine15Adjustment(...)` or `dismissStagedEngine15Adjustment(...)` leaves the original target unchanged and records the dismissal. Revision changes trigger a refresh screen rather than a silent retry.
4. **Correct display:** use the read-only `Engine15SessionPresentation.forSession` projection for active duration, distance/steps, and disclosure. When a shortened run is accepted, show its accepted *time-only* target and `currentDistanceTargetKm=null`; the original 8.0 km is history, **not** a pace/distance requirement for 45 minutes. When rest is accepted, show 0 current minutes and no workout.
5. **Offline/account safety:** no cloud requirement for local review/accept/dismiss. Reload accepted and dismissed decisions after restart; no cross-account information. Do not silently post the proposed change to Garmin or other apps.
6. **Figma fidelity & accessibility:** one-to-one visual implementation from the approved review frame(s), reuse existing Compose Trackside tokens and native components; verify 360dp and 412dp with font scaling 1.3×, tap sizes, status descriptions, card spacing, disabled/stale states and screen-reader labels.
7. **Verification:** Android JVM tests, Android 35 emulator SQLite/offline/account switch, stale revision and duplicate source rejection, screenshot comparison, and full Exercise Library integrity gates must pass on the exact integration commit. Private APK/device validation only after design approval and verified build readiness.

## Review status

**PROPOSED / not approved.** Pending user review of the five Figma frames. PR #37 is draft/unmerged; Trackside visual PR #36 remains untouched. Engine 2.0 and the Athlete Simulation Lab are separate. Next action: review and approve or request visual changes; only then start E3 integration on a separate branch.
