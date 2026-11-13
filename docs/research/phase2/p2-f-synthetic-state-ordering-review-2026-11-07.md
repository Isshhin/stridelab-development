# Synthetic P2-F state-ordering review — 2026-11-07

Verdict: `SYNTHETIC_AUTHORITY_CUSTODY_NEEDS_CORRECTION`.

Reviewed head: `e21d0b2e0afe4677253bff5de2865f4b95b69760`. The Graphify snapshot previously verified at that head had 1,342 nodes and 3,536 edges. This review uses invented in-process state only. It does not establish private custody or real-data readiness.

## Findings

Two P1 synthetic-model correctness cases remain. The review-only `research/validation/review_p2f_authority_state_ordering.py` deterministically reproduces both using accepted `str` subclasses. The seventh callback selects the final publication validation in the current fixture; this is a fixture-specific schedule, not a general timing guarantee.

1. During final inventory verification, an object's hash callback advances the synthetic anchor directly from generation 0 to 1 after `current` has validated generation 0. The inventory and data checks then pass, both final flags remain false, and `published` is appended even though the anchor and ledger generations disagree.
2. During final modeled-data lookup, a hash callback changes the fake inventory object version after the inventory check. The modeled-data lookup and final flag checks still pass, and `published` is appended with stale inventory. This is an explicit mutation of the public fake inventory dictionary, a controlled model fault; it is not evidence about any external system.

The three new review schedules run with two expected failures and one pass. The passing schedule sets both withdrawal and operation-invalid during final validation: no output is appended, busy and owner are cleared, and the gate can be reacquired. Existing schedules separately verify the withdrawal hash and equality callbacks, partial output, benign custom object, competing thread and nested-read cleanup. The existing regression groups pass; these results do not cover the two newly constructed schedules.

For the supplied `FakeKey` and ordinary built-in output list, source inspection finds no callback-capable operation between the final boolean checks and `self.key.output.append(chunk)`. The new issue occurs earlier: validation steps themselves can mutate state already checked without setting either boolean. This assessment does not cover replacing the fake key or output list with arbitrary custom objects.

## Verification

Local Python 3.12: focused authority suite 81 passed; existing review scripts 6/6, 8/8, 11/11 and 6/6 (31/31); durable store 54 passed; P2 229 passed; full research 393 run, 392 passed and one Windows symlink skip. The new schedules are review-only and excluded from test discovery.

An additional run under the default Python 3.15 produced two failures and one error in private-command tests in both P2 and full research; it is not the documented Python 3.12 baseline and is not counted as a passing run. An initial incorrectly addressed durable unittest import also failed before the properly configured run passed.

Exact-head workflow 42338038021 was queued at the last check before the instruction prohibiting network access. Its current state is unverified; it must not be reported as passed. PR #34 was last observed open, draft and unmerged before that instruction; no subsequent remote action was taken.

The separate prior synthetic journal durability verdict is unchanged. Private-pilot readiness remains blocked; no human or provider data is admitted; `P2_F_GATE_2_NOT_READY` and `P2_B_PROTOCOL_READY_DATA_NOT_READY` remain unchanged.

Next: a separately authorized, bounded synthetic-only correction should address state changes during the complete final validation/publication sequence, add regression coverage for both schedules, and receive independent re-review. No broker implementation changed in this review.
