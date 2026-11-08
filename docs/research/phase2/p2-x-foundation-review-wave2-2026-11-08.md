# P2-X Foundation Qualification — Adversarial Review Wave 2

Date: 2026-11-08 (Asia/Manila)
Scope: isolated synthetic research follow-up to internal foundation qualification, **not** an external independent audit.
Base: `2b8393a540137ce546e2ff072f4fcc68b7bfbba8`
Branch: `research/p2x-qualification-hardening-20261108`

## 1. New reproducible findings

Three further fail-open cases were reproduced on the exact previous `p2x_lineage.py` snapshot, each returning `SYNTHETIC_ASSERTED_CONSISTENCY_ONLY` when the research audit should have deferred or rejected:

- **HIGH / cross-ledger omission:** The ledger projection can include an additional active record that is never bound to the reviewed native evidence. Existing checks confirmed each *included* native binding exists in the active ledger, but did not verify the converse. Fixture: add a new ledger active record `p2a:UNBOUND` with a distinct source ID and 64-character payload digest, leave the native manifest unchanged.
- **HIGH / P2-C opportunity version:** A migrated P2-C native opportunity may claim a version different from the shared plan version, even while the snapshot, migration ID, events and manifest otherwise match. Fixture: migrate native P2-C to P-1 at version 7, then alter its planned opportunity version to 6.
- **MEDIUM / ignored unbound malformed observation:** An unbound observation available before the cutoff with an unrecognized status could be completely omitted from completeness accounting and still return synthetic consistency.

All three were added to the test file first and **failed as expected** against the previous code before corrections were applied.

## 2. Implemented corrections

Changes are confined to `research/validation/p2x_lineage.py` and its existing test file:

1. Validate the plan version of every P2-C planned opportunity, even if the native plan-ID migration has not been requested. Reject a mismatched or non-integer opportunity version.
2. For each native record available by the decision cutoff, require a binding to establish complete synthetic consistency. Unbound eligible evidence increments `UNBOUND_ELIGIBLE_NATIVE_EVENT`; unbound invalid/unknown evidence increments `UNBOUND_UNVERIFIED_NATIVE_EVENT` (it does not imply an athlete skipped a workout). Backdated availability now raises `NATIVE_EVENT_AVAILABILITY_BACKDATED`. Future uploads remain invisible when replaying a historical decision.
3. Require the asserted ledger active-event set to contain no additional active event IDs absent from the *validated* bound evidence set. Extra rows increment `UNBOUND_ACTIVE_LEDGER_EVENT` and force `DEFER_UNVERIFIED_EVIDENCE`. Missing validated ledger rows still defer as before.

These are software conformance checks on **caller-supplied synthetic fixtures**. The rules do not prove that a third-party ledger projection, a plan revision digest or a source's correction history is authentic.

## 3. Hosted test evidence

Exact Git blob byte identities of the two corrected files:

- `research/validation/p2x_lineage.py`: `906d17c1641ac7a873efe0f776333ef84e944fbd`.
- `research/validation/test_p2x_lineage.py`: `9b381ea94afc61c61894e6c6594c9f56edd4af37`.

New cases 59–65: extra active ledger record; migrated P2-C plan version mismatch; unbound invalid record; future unbound record is not back-propagated; invalid unbound timestamp; distinct extra active ledger record remains deferred; unmigrated P2-C plan version mismatch.

Hosted sandbox command: `python -m unittest discover -s . -p 'test_*.py' -q` in isolated directory with exact inherited previously verified synthetic research sources. Results:

- `PYTHONHASHSEED=7`: **455 tests passed**, 0 failures/errors.
- `PYTHONHASHSEED=213`: **455 tests passed**, 0 failures/errors.
- `python -m compileall -q p2x_lineage.py test_p2x_lineage.py`: PASS.

The previous 448-test suite remained green. These 455 test results are from hosted author-side synthetic regression, **not** exact-head GitHub CI or separate external independent qualification. Native source custody, live Garmin data, cryptographic authority and real training outcomes were not tested.

## 4. GitHub Actions investigation

Prior non-PC Linux research run [#42482594419](https://github.com/Isshhin/stridelab-development/actions/runs/42482594419) failed about three seconds after launch with no assigned runner or executed steps. Job logs were unavailable. Similar isolated research runs [#42481627243](https://github.com/Isshhin/stridelab-development/actions/runs/42481627243) and [#42476987211](https://github.com/Isshhin/stridelab-development/actions/runs/42476987211) failed before test execution. A separate Android verification workflow [#42477158270](https://github.com/Isshhin/stridelab-development/actions/runs/42477158270) succeeded within the same period. This rules out the blanket claim that *all* GitHub Actions workflows were unavailable; it does not determine the cause of isolated research workflow failure.

**No exact-head non-PC GitHub CI success is claimed.** Do not relabel a self-hosted Windows runner as a solution. The appropriate next diagnostic is permitted review of repo Actions/runners/billing and job rejection metadata without configuring the user's PC.

## 5. Qualification verdict

**Internal software checkpoint:** `P2X_REVIEW_WAVE2_SYNTHETIC_455_HOSTED_PASS`.
**Independent external acceptance:** NOT DONE.
**Operational source/plan authority:** NOT ESTABLISHED.
**GitHub exact-head CI:** BLOCKED BEFORE STEPS / ROOT CAUSE UNKNOWN.
**P2-F Gate 2:** CLOSED.
**Automatic coaching or plan write:** NONE.

Do not merge this branch into PR #34 or production; frozen Session G, Engine 1.5, Android/Trackside, P2-F v7, and existing research branches remain untouched.
