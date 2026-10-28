# Phase 29 — PR dependency triage and continuous implementation gates

**Status:** Proposed integration order. **No merge, rebase, close, deploy or workflow dispatch performed.** Reference SHA: `25be8f3828374d1e539c7e825afa20fbcb652b56`. On 2026-10-28, GitHub returned **19 open draft PRs (#2–#20)**.

## Existing source ancestry

| PR | Focus | Based on | Recommended next action (proposal, not executed) |
|---|---|---|---|
| #2 | Multi-session policy | Earlier verified Engine1 goals checkpoint | Superseded in the integrated verified `25be8f3` tree; compare exact diff before considering closure |
| #3 | Guided workout + multi-session integration | Earlier goals checkpoint | Its complete implementation exists in verified `25be8f3`; retain audit trail, compare and handle separately |
| #4 | Android-first roadmap | `main` | Keep updated independent of app source; do not treat as code readiness |
| #5 | T0 baseline audit | `25be8f3` | Retain evidence; documentation only |
| #6 | T5A design audit | `25be8f3` | Retain UX requirements; documentation only |
| #7 | Honest workout completion, run copy + workflow opt-in | `25be8f3` | Reuse Step 30 functionality; split workflow change into separate Step 33 commit |
| #8 | Calendar/pace-chart UX | `25be8f3` | Reconcile later with Batch 5 screen reconstruction; avoid duplicate Compose changes |
| #9 | Explicit unlink before relink | `25be8f3` | Reuse Step 31 policy correction, before DB migration |
| #10 | Activity-link normalized table + migration design | `25be8f3` | Design only; use Step 31 to implement and verify |
| #11 → #12 | Synthetic athlete histories/longitudinal scenarios | `25be8f3` then #11 branch | Retain provenance and use only confirmed fixtures; not proof of coaching quality |
| #13 → #14 → #15 → #16 → #17 | Engine2 foundation and first four batches | `25be8f3` then stacked PRs | Audit compilation/contracts before making any write path active |
| #18 → #19 → #20 | Engine2 hardening, scenario tests, separate experimental SQLite ledger | #17 then stacked PRs | Do not merge all at once; verify Kotlin contracts and limit #20 SQLite to non-authoritative experimentation |

## Known overlapping changes

- PRs #7, #8, and #9 each touch `.github/workflows/native-android.yml`. Preserve owner-controlled workflow triggers and separate APK packaging opt-in from UI/logging patches.
- PRs #7 and #9 touch `PlanOperations.kt`; integrate and compile them together on a dedicated, isolated branch after reviewing both diffs.
- PRs #7 and #8 touch `TrainingScreens.kt`; adopt shared UI behavior without overwriting fixes.
- PR #20 adds a standalone ledger DB; this conflicts with Phase 28's proposed one-authority plan until the same-transaction design exists. No writable activation.

## Suggested development order, review gates and permissions

1. Land **no code yet**: review/approve Phase 26 canonical read adapter, Phase 27 sync contract, Phase 28 single-authority ADR and this triage matrix. This architecture branch contains documents only.
2. Batch 4 Step 30: create a fresh branch from verified checkpoint; reconcile PR #7 but avoid its workflow changes; add focused regression tests first.
3. Batch 4 Step 31: first audit *synthetic/authorized* v1 saved plan data; then additive migration and SQL uniqueness in a transaction. P0 must not be claimed closed on source review alone.
4. Batch 4 Step 32: preserve existing outbox; implement mock/contract tests for replay. Do not enable remote plan writes until an authorized server endpoint, RLS and idempotency are verified.
5. Batch 4 Step 33: simulate account switching and delayed callbacks, separately add opt-in APK packaging without triggering workflow.
6. Batch 5 on its own branch: freeze UI/activity view-model and implement compact screens with source-backed samples. Continue Batch 6 parsers independently after freezing the model.
7. Batch 7 read-only integration first; permit an approval transaction only after Phase 28's single-authority persistence contract is implemented and exact-head tests pass.
8. Review each branch/PR; **do not auto-merge** to main, alter private user data, deploy APIs, upload APK by default or run hosted CI against owner request.

## Checkpoint/acceptance matrix

| Gate | Source check | JVM | SQLite migration | Emulator | Live provider | Android phone |
|---|---|---|---|---|---|---|
| Batch 3.5 spec | Required | Not necessary to claim docs complete | N/A | N/A | N/A | N/A |
| Step 30 logging | Required | Required | N/A | Required before integration | N/A | N/A |
| Step 31 source links | Required | Required | Required | Required | N/A | Later |
| Step 32 offline/sync | Required | Required | If schema changes | Required | Backend contract required for real replay | Later |
| Step 33 account/workflow | Required | Required | N/A | Required | Real delayed auth response for provider acceptance | Later |
| Batch 5 Compose UI | Required | If touched | N/A | Required screenshots/accessibility | Fixtures acceptable | Later |
| Batch 6 FIT | Required | Required | If persisted | Required for picker | Garmin auth deferred | Later |
| Batch 7 coach | Required | Required | Required for writable approval | Required airplane-mode approval | Real-history review before claims | Later |

**Disclosure:** Earlier Engine2 source-draft test functions and stacked PRs are not a verified runnable Engine2 application; successful exact-head Android CI at baseline is not transferable to these branches.

## First milestone scope

M1 is a private, test-account-only offline loop: approve a combined plan, log actual strength sets, restart, generate one conservative lower-body/long-run proposal using confirmed evidence, show original/proposed change and cited data, explicitly approve or reject, reopen with immutable historical actuals and one durable decision. This is blocked until same-transaction approval and revision tests pass. FIT, live Garmin, remote plan replay, elaborate charts, and public packaging do not block M1.
