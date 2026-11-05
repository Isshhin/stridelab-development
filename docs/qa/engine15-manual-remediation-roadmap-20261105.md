# StrideLab Trackside 3.0 — Engine 1.5 manual remediation roadmap (2026-11-05)

**Status: PLAN ONLY. NO IMPLEMENTATION OR NEW VERIFICATION IMPLIED.**  
**Authority:** [Latest approved product and quality-session requirements](engine15-primary-plan-quality-requirements-20261105.md), followed by latest explicit user direction, approved Trackside Figma, current QA and earlier safety handoffs. Older Engine-1.0-default instructions are historical and superseded for product intent.

**Repository:** `Isshhin/stridelab-development`. Reviewed branch `fix/trackside-usability-garmin-20261105` at `646f353bc09511dfaa6dcb9c629c034dcf68fc54`. Engine 1.5 PR #37 remains open/draft/unmerged; Engine 2.x stays untouched.

## Development operating rules

1. **One bounded correction per commit.** Start each task by verifying live branch head, preserving newer changes, reading affected implementation/tests/spec. Do not restart Engine 1.5 or replace working components.
2. Establish the intended behavior and a **failing focused test** before editing, then make the smallest correction; never change safety tests merely to obtain green status.
3. Verify targeted JVM/Compose tests, then the appropriate broader tests and the exact-head Windows MARC workflow. Document actual pass/fail/skipped and source SHA, not aspirational status.
4. Change the next item only after the current item's acceptance criteria pass. Update QA known defects, regression index, source-specific handoff, and functional behavior register with truthful evidence.
5. Do not use Codex/Work unless a task proves impractical manually and the user authorizes it. Use existing GitHub connection and MARC runner. Do not merge PRs, publish a release, replace signing keys, uninstall/clear data, or modify Engine 2.x.
6. Preserve approved Trackside UI; no redesign beyond minimal required interactions and truthful review copy.
7. A signed debug artifact with a different installed-app signer **must not** be installed over the existing data-bearing app.

## Unified priority-ordered execution — one item at a time

This is the **single combined development queue** for Engine 1.5 corrections **and** the user's current Android-emulator observations. Priority means severity; order also reflects dependencies and the user's decision to finish the core engine before unrelated integrations. A reported symptom is not automatically a reproduced defect. Work item IDs `E01–E07` link the original emulator observations below to implementation phases; existing Engine 1.5 mismatches are included in the same queue.

| Order | Priority | Work item | Minimum acceptance criterion | Evidence / status |
|---|---|---|---|---|
| 0 | Gate | Record baseline and create reproducible fixtures | Verify live SHA and safe emulator profile; capture one experienced 60–70 km/week, 5-running-day, one-Threshold plan plus a low-mileage safety case. Do not clear emulator/phone accounts. | Requirements documented; real source test pending |
| 1 | P1 | Restore Engine 1.5 as primary | Default build generates Engine 1.5 version/policy through actual builder. A **separate, explicitly selected** Engine 1.0 fallback works without changing existing plans. | Mismatch confirmed; current default false |
| 2 | P1 | Fix quality request and saved state | Requested count/type/weekday and manual-vs-automatic intent survive UI navigation, serialization, generation and reopen. Fix displayed-but-not-saved quality chips and count/day inconsistency. | Code risks confirmed; device repro pending |
| 3 | P1 | Align quality eligibility and safety | UI and Engine 1.5 agree on 30–39 vs 40+ km two-quality limits, experience, duration, returning, deload/taper, and spacing. No weakened safety thresholds. | Confirmed UI/engine discrepancy |
| 4 | P1 | Make quality type and running goal truthful | Eligible requested Threshold yields correct structured workout; honor supported styles, or label unsupported ones advisory. Use the running-discipline goal in mixed plans. | Type/goal mapping gap confirmed |
| 5 | P1 | Explain actual draft prescriptions | Display requested vs generated quality count, type, day, volume, duration, and exact reason for withheld/downgraded quality before explicit approval. | Not fully implemented |
| 6 | P1 | End-to-end plan-content acceptance | Actual UI selection → Engine 1.5 generation → real prescribed session → approval → offline reopen, with quality assertion and separate safety/fallback scenarios. | Existing test checks Engine 1.0 + session totals |
| 7 | P1 | **Garmin timestamp + error classification** (`E01`, `E02`) | Reproduce source timestamp (without exposing credential/data), accept verified formats correctly, distinguish parsing/protocol errors from invalid credentials, preserve source data and add red/green tests. Avoid implying that this completes canonical activity-history imports. | Misleading header **confirmed**; exact live timestamp cause **suspected**, not proven |
| 8 | P2 | StrideLab account session banner (`E03`) | Trace independent app-session expiry/revocation and refresh, verify offline cached history/account isolation, surface correct sign-in guidance. No conflation with Garmin status. | Emulator warning observed; expiry root cause unverified |
| 9 | P2 | Mixed-discipline scheduling end-to-end | Run 5R+2S+2C over six available days; max two/day, budget, locks, long/quality/lower-body spacing, review relocation and no lost sessions. | Backend tests exist; phone acceptance missing |
| 10 | P2 | Engine 1.5 feedback UI and integrity | Manage/Workout preview is non-mutating; stage/accept/dismiss separate; stale evidence fails; accepted shorter/rest sessions display no obsolete original active targets. Offline/account safe. | Component wiring exists; production acceptance incomplete |
| 11 | P2 | Whole-app reliability and QA evidence | Reconcile the functional matrix's 35/41 `NOT RUN` rows with executed named checks; verify account switch, backup, old-engine history, locks/outbox and failure recovery. | Historical broad suites pass; current requirements not closed |
| 12 | P2 | Compare More → Connections density (`E04`, `E05`) | Compare at **same emulator**, same font/display scaling and viewport with Today as a visually acceptable reference; reduce unnecessary card height/copy only if screen-specific excess is reproduced, preserve accessible actions. | Density concern observed; Today looks fine; scaling/design attribution pending |
| 13 | P2 | Device and Figma interaction acceptance | Check draggable map-backed Activity sheet, weekday selection, Back, keyboard, 360/412dp font profiles, final layout and installed build on safe phone or emulator. | Independent physical-phone test pending |
| 14 | P3 | Remove obsolete More UI (`E06`, `E07`) | Remove Legacy Data & Backups card, original-record message, Open preserved prototype CTA, Private testing notice; remove empty Device & Data heading if appropriate. **Do not delete** account data, history, migrations or actual backup/restore implementation. | User-approved UI removal; no code change |
| 15 | Separate / later | Garmin canonical history + file import | Explicit transactional Garmin→history sync, lawful provenance, FIT intake UI, TCX/GPX ingestion, live service checks. This is **separate** from `E01/E02` parsing repairs and must not silently affect plans. | Foundation exists; incomplete |
| 16 | Deferred | Engine 2.x research and larger redesign | Individualized HRV/heat/fitness readiness, new progression intelligence and seven-day running require independent research/approval; no unsanctioned Engine 1.5 expansion. | Out of scope |

## Emulator observation register — November 5, 2026

The original **seven** emulator items are retained below with stable IDs, not counted as seven additional confirmed crashes. `E04` and `E05` are linked visual observations; `E06` and `E07` are two deliberate UI removals within one implementation task.

| ID | Screen | Observed or requested behavior | Classification | Unified queue |
|---|---|---|---|---|
| E01 | More → Connections → Garmin | A parsing/protocol error appears under the misleading heading **“Authentication expired or needs attention.”** `GarminSettings.kt` labels all `error` states the same. | **Confirmed error-classification defect** | 7 |
| E02 | More → Connections → Garmin | **“Garmin activity has an invalid timestamp.”** Current parser accepts only certain ISO date/time formats. Exact live returned value is uninspected; a space-delimited Garmin timestamp is a plausible but unproven cause. | **Observed failure, parser hypothesis pending fixture** | 7 |
| E03 | More / account banner | **“Your saved session has expired or was revoked. Sign in again; cached activities are retained.”** This is StrideLab account/session state, independent of Garmin bearer token. | **Observed; determine whether genuine expiry or app defect** | 8 |
| E04 | More → Connections | Garmin card and two buttons plus explanatory text consume most vertical space, limiting visibility of Intervals.icu. | **UI-density improvement request; scale comparison needed** | 12 |
| E05 | Today on Medium Phone API | Today cards, spacing and bottom navigation appear appropriately sized; useful comparison against More. | **Reference observation, not a defect** | 12 |
| E06 | More → Device & Data | Remove obsolete **Legacy Data & Backups** card, preservation explanation and **Open preserved prototype** button. | **Explicit UI removal request** | 14 |
| E07 | More → Device & Data | Remove **Private testing** heading and notice; remove **Device & Data** heading only if empty. | **Explicit UI removal request** | 14 |

### Safe reproduction sequence for Garmin and account warnings

- Use disposable emulator/profile; record platform API, font/display scaling, current StrideLab account state and screenshots. Do not upload keys, tokens, full raw responses containing personal activity information, or keystore files.
- `E01`: reproduce a `GarminProtocolException` in tests and assert UI identifies a data/protocol issue, not expired credentials.
- `E02`: collect **only sanitized timestamp-format evidence** or a synthetic fixture matching the format, and test each supported format plus truly invalid/missing timestamps. Never silently invent a timestamp or import corrupted activity.
- `E03`: inspect normal expiry versus refresh failure, clarify which source owns the banner, and test reauthentication without deleting cached activities.
- Do not press Disconnect or regenerate an API key solely because `E02` fails. Garmin keys are separate from StrideLab account sessions.

## Additional findings from second source audit

These are **new inspection candidates** beyond the previously registered Engine-default, quality policy, and QA-summary issues. A code-level risk is not counted as reproduced phone failure without a focused test.

- **N1: Quality chips may misrepresent saved values.** `PlanBuilder.advancedQualityCsv` displays Threshold and Tempo selected when `initial.allowedQualityTypes` is empty, but the underlying serialized `PlanRequest.allowedQualityTypes` remains empty until the user toggles a chip. Reproduce in a builder state test before fixing.
- **N2: Advanced quality count does not synchronize requested weekdays.** The Advanced Running `onQualityCount` callback updates `qualityCount` only; `qualityDays` and `weeklySlots` are not updated together. Check whether a subsequent Running Setup/manual Continue produces an unassigned slot, blocks later generation, or loses intent.
- **N3: Manual/automatic intent can be ambiguous.** `runningCustom` starts `true` even if `weeklySlots["run"]` is absent, and Continue can save default suggested weekdays as explicit pins. Distinguish truly athlete-pinned days from coach-suggested days; add state/restore tests.
- **N4: Requested quality type may not affect engine output.** `PlanRequest.preferredQuality` and `allowedQualityTypes` exist but `Engine15.structure(request.goal)` is driven by the overall goal, not these preferences or `disciplineGoals["run"]`. Test a mixed primary-strength + secondary-running goal to verify correct running prescription.
- **N5: Historical "not wired" presentation documentation is stale.** Current `WorkoutScreen`, `Engine15ScheduleReview`, and `TracksideManagePlanScreen` do reference `Engine15SessionPresentation` and recommendation actions. **Do not reimplement the backend/UI blindly**; verify actual production routes, accepted rest/time-only display and Figma fidelity.
- **N6: Review copy is generic in suppression cases.** Engine 1.5 currently explains withheld quality with a combined list of possible mileage/time/deload/return/taper causes. Require one or more **actual** reasons for each affected session and week.
- **N7: Guided race-result entry is not part of current PlanRequest.** Running Ability UI itself says race/time-trial results are not stored. Record as a known limitation, not a failure of current Engine 1.5 physics; decide any expanded input contract separately.
- **N8: Six-run frequency ceiling is a deliberate current policy.** Seven running days are explicitly rejected in the B7 scenario matrix. Do not claim seven-run support or silently reduce a seven-day request.

## Current implemented foundation to preserve, not rewrite

- Deterministic versioned Engine 1.5 generation, conservative running volume caps/deloads/return and safety-limited quality.
- Mixed-discipline generation, 2/day capacity and interference-aware pairing (Engine 1.5-specific rules), selected long/quality days and time budgets.
- Individual locks, reviewed availability relocation/unassigned conflicts, edited-plan integrity, immutable historical activity/logs, Engine 1.0 compatibility.
- Completion/missed evidence-driven recommendation preview, explicit stage/accept/dismiss, stale-evidence and revision rejection, secure account-scoped repository/outbox/backup.
- Active-session time-only/rest projection already referenced in Trackside screens, plus tested Activity sheet and running weekday-selection corrections.
- Existing JVM/Android regression suites and MARC exact-head evidence as historical safeguards, **not** blanket acceptance of revised product.

## Completion rule for each manual task

A task is DONE only when: (1) acceptance criteria were met by source inspection and executed named tests; (2) exact source SHA and results are recorded; (3) previous unaffected engine/repository behaviors remain intact; (4) associated QA defect(s) are updated; and (5) no unrelated feature or branch was changed.

**Next actionable task:** Order 0: add a strict production-route Engine 1.5/quality fixture to the test plan; then Order 1: correct the packaged default and separate rollback assertions. Do not flip only the flag and call the integration finished.
