# StrideLab implementation continuation — 2026-10-28

## Stable origin and policy

- Verified native checkpoint: `25be8f3828374d1e539c7e825afa20fbcb652b56` (`checkpoint/native-guided-multisession-verified-25be8f3`).
- Current owner constraints: native Android first, private pre-alpha only, no auto merge/deploy/public release/phone reinstall; preserve existing SQLite v1 data, account scope and signing identity.
- No new GitHub Actions workflow was intentionally dispatched for these candidates; **new source and test files have not passed exact-head Kotlin/Gradle/emulator tests**.
- Existing Engine2 draft stack #13–#20 remains unmerged and unverified. No proposal UI has been enabled.

## Created in this resumed development session

| Batch | PR | Head | Status and exact scope |
|---|---|---|---|
| 3.5 / 26–29 | [#21](https://github.com/Isshhin/stridelab-development/pull/21) | `docs/stride-architecture-batch35-20261028` | Four architecture documents for canonical model, sync/idempotency, single approved-plan authority, and prior PR triage. Docs only; owner approval pending. |
| 4 / 30–33 | [#22](https://github.com/Isshhin/stridelab-development/pull/22) | `feature/native-batch4-p30-33-20261028` | Ports existing PR7 completion/run-action fix/tests; **isolated-copy-only** activity link migration candidate/tests; outbox recovery tests; opt-in APK workflow as a separate commit. No live schema v2 or remote replay. |
| 5 / 34–37 | [#23](https://github.com/Isshhin/stridelab-development/pull/23) | `feature/native-batch5-ui-20261028` | Source-backed Activity Detail view model, read-only local log, Charts/Splits/Route recorded views, last-run overview, neutral guide/image fallbacks and tests. No accepted emulator/screenshot pass. |
| 6 / 38–41 | [#24](https://github.com/Isshhin/stridelab-development/pull/24) | `feature/native-batch6-fit-foundation-20261028` | Bounded FIT header/CRC and opaque message inspection, source-dedup review rules, Garmin mock/wellness contracts, read-only Android file/share URI intake and tests. **Not** complete FIT metric decoding or live import. |
| 7 / 42–45 | [#25](https://github.com/Isshhin/stridelab-development/pull/25) | `prep/engine2-batch7-readonly-coach-20261028` | Stacked on PR20: read-only Engine1 athlete actual context, source-aware cross-discipline shorter-session proposal candidate, deterministic explanations and tests. **Not** writable plan integration or general progression. |

## Dependency and safety diagram

```text
Verified Engine1 checkpoint 25be8f3
    ├─ PR21 architecture documents (independent)
    ├─ PR22 P0 reliability candidate (independent)
    ├─ PR23 native UI candidate (independent)
    ├─ PR24 FIT/provenance foundation (independent)
    └─ PR13 → ... → PR20 → PR25 Engine2 coaching candidate (stacked, unverified)
```

**Do not merge these five PRs wholesale.** First perform compilation and source reconciliation, then exact-head tests and targeted integrations. P0 fix PR7 overlaps PR22; UI PR8 overlaps PR23; activity-link policy PR9 and design PR10 overlap PR22. Check each proposed diff before selecting canonical changes.

## Blocking acceptance work (prioritized)

1. Run Gradle JVM, Android compile, Python and emulator instrumentation on each candidate exact SHA after the Actions budget permits. Fix any source/test errors without deleting valid regressions. Evaluate signed APK metadata only if explicitly requested; no phone uninstall.
2. P31: current link migration candidate is test-copy-only; implementation still requires production v1→v2 recovery strategy, account-scoped unique transactional plan/link/outbox writes, concurrency, actual duplicate data audit, all tested without touching original phone data.
3. P32: current outbox tests validate local durability, NOT real cloud sync; establish genuine versioned server mutation endpoint + idempotency + RLS before replay.
4. P35: visual/layout regression, screen navigation, large text accessibility and synthetic-device state validation; no direct claims of parity with approved prototype.
5. P38: FitContainerReader only structurally validates FIT; profile-aware actual metrics, invalid sentinels, timestamp and developer fields must be validated against a trusted Garmin FIT decoder and authentic fixtures before importing as ActivityRecord.
6. P42–44: Engine2 draft separate ledger is NOT the authority for Engine1 plan JSON. Implement one atomic approved-plan + proposal receipt transaction, account isolation, plan revision CAS and process-death/duplicate replay tests before enabling approval buttons.
7. Real Garmin authorization and phone signer compatibility require actual account/device evidence. Keep future modality auto-suggestions disabled until impact-aware validation.

## M1 first integrated private-alpha gate

One authenticated athlete can approve a mixed running+strength plan offline, save actual strength sets, force-stop/reopen, see a conservative read-only lower-body-vs-long-run proposal supported by confirmed history, explicitly accept or reject it in an **atomic durable single-authority transaction**, and reopen with preserved historical actuals and no account leakage.

As of this checkpoint, **M1 is not achieved**: the atomic approval integration and emulator acceptance are outstanding. Continue from the saved branches; do not claim completed functionality solely on the basis of source commits.
