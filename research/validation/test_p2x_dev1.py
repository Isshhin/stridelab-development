"""Invented-data P2-X DEV-1 provenance and receipt-fencing regressions."""
from copy import deepcopy
from datetime import datetime, timedelta, timezone
from hashlib import sha256
import json
import unittest
from unittest.mock import patch
from types import ModuleType

from p2x_dev1 import (GENESIS, seal_entry, ledger_state,
                     inspect_cross_track, issue_shadow_receipt,
                     check_shadow_receipt, inspect_bundle)

NOW = datetime(2026, 11, 8, 12, tzinfo=timezone.utc)


def when(hours):
    return (NOW + timedelta(hours=hours)).isoformat()


def content(label):
    return sha256(("invented-" + label).encode()).hexdigest()


def context():
    return {
        "synthetic_only": True, "athlete_id": "invented-A",
        "plan_id": "plan-invented-1", "plan_version": 7,
        "opportunity_id": "op-1", "policy_version": "shadow-policy-v1",
        "decision_as_of": when(0), "ledger": [],
    }


def add(c, kind="ORIGINAL", *, name=None, source="synthetic-source-A",
        target=None, payload=None, at=-4, **kw):
    entries = c["ledger"]
    next_id = name or "event-%d" % (len(entries) + 1)
    if payload is None and kind in ("ORIGINAL", "CORRECT"):
        payload = content(next_id)
    e = {
        "seq": len(entries) + 1, "event_id": next_id, "kind": kind,
        "athlete_id": c["athlete_id"], "plan_id": c["plan_id"],
        "plan_version": c["plan_version"], "source_id": source,
        "target_id": target, "payload_digest": payload,
        "observed_at": when(at), "available_at": when(at),
        "prev_digest": entries[-1]["digest"] if entries else GENESIS,
    }
    e.update(kw)
    e = seal_entry(e, e["prev_digest"])
    entries.append(e)
    return e


def baseline(c):
    return {
        "athlete_id": c["athlete_id"],
        "opportunity_id": c["opportunity_id"],
        "as_of": when(0),
        "common_plan_version": c["plan_version"],
        "p2d_p2e_asserted_plan_id": c["plan_id"],
        "p2c_plan_lineage": "UNVERIFIED_NO_P2C_PLAN_ID",
        "cross_track_source_linkage": "NOT_AUTHENTICATED",
        "coaching_authority": "NONE_SYNTHETIC_RESEARCH",
        "safe_to_auto_apply": False,
        "applied": False,
        "gate2": "CLOSED",
        "review_status": "STRUCTURAL_COMPARISON_ONLY",
    }


def ready():
    c = context()
    add(c)
    a = inspect_cross_track(c, baseline(c))
    r = issue_shadow_receipt(a, when(0))
    return c, a, r


class ProvenanceBatch1Tests(unittest.TestCase):
    def test_01_empty_is_unknown_not_authorized(self):
        s = ledger_state(context())
        self.assertEqual([], s["active_records"])
        self.assertEqual(GENESIS, s["ledger_tip"])
        self.assertEqual("NOT_PROVEN_SYNTHETIC_FIXTURE", s["source_authentication"])

    def test_02_one_original_is_known(self):
        c = context()
        event = add(c)
        x = ledger_state(c)
        self.assertEqual(event["digest"], x["ledger_tip"])
        self.assertEqual([event["event_id"]], [r["event_id"] for r in x["active_records"]])

    def test_03_plan_identity_scope_rejected(self):
        c = context()
        add(c)
        c["plan_id"] = "different-plan"
        with self.assertRaisesRegex(ValueError, "CROSS_SCOPE_LEDGER_RECORD"):
            ledger_state(c)

    def test_04_athlete_scope_rejected(self):
        c = context()
        add(c)
        c["athlete_id"] = "another-athlete"
        with self.assertRaisesRegex(ValueError, "CROSS_SCOPE_LEDGER_RECORD"):
            ledger_state(c)

    def test_05_version_scope_rejected(self):
        c = context()
        add(c)
        c["plan_version"] = 8
        with self.assertRaisesRegex(ValueError, "CROSS_SCOPE_LEDGER_RECORD"):
            ledger_state(c)

    def test_06_mutated_original_is_detected(self):
        c = context()
        add(c)
        c["ledger"][0]["payload_digest"] = content("tampered")
        with self.assertRaisesRegex(ValueError, "LEDGER_DIGEST_MISMATCH"):
            ledger_state(c)

    def test_07_breaking_prev_hash_fails(self):
        c = context()
        add(c)
        add(c, name="second", at=-3)
        c["ledger"][1]["prev_digest"] = GENESIS
        with self.assertRaisesRegex(ValueError, "BROKEN_LEDGER_CHAIN"):
            ledger_state(c)

    def test_08_gapped_sequence_fails(self):
        c = context()
        add(c)
        c["ledger"][0]["seq"] = 5
        with self.assertRaisesRegex(ValueError, "NONCONTIGUOUS_LEDGER_SEQUENCE"):
            ledger_state(c)

    def test_09_exact_duplicate_event_id_rejected(self):
        c = context()
        add(c, name="duplicate")
        add(c, name="duplicate", at=-3)
        with self.assertRaisesRegex(ValueError, "DUPLICATE_EVENT_ID"):
            ledger_state(c)

    def test_10_event_from_future_is_not_available_at_decision(self):
        c = context()
        e = add(c, at=2)
        x = ledger_state(c)
        self.assertEqual([], x["active_records"])
        self.assertEqual(GENESIS, x["ledger_tip"])
        self.assertEqual(1, x["future_events"])
        self.assertEqual(e["digest"], ledger_state(c, when(3))["ledger_tip"])

    def test_11_backdated_append_rejected(self):
        c = context()
        add(c, at=-2)
        add(c, at=-5)
        with self.assertRaisesRegex(ValueError, "BACKDATED_APPEND"):
            ledger_state(c)

    def test_12_backdated_availability_rejected(self):
        c = context()
        add(c, observed_at=when(-1), available_at=when(-3))
        with self.assertRaisesRegex(ValueError, "BACKDATED_AVAILABILITY"):
            ledger_state(c)

    def test_13_naive_timestamp_rejected(self):
        c = context()
        add(c, observed_at="2026-11-08T08:00:00")
        with self.assertRaisesRegex(ValueError, "INVALID_OFFSET_TIMESTAMP"):
            ledger_state(c)

    def test_14_nonhex_payload_rejected(self):
        c = context()
        add(c, payload="not-a-digest")
        with self.assertRaisesRegex(ValueError, "ORIGINAL_RECORD_INVALID"):
            ledger_state(c)

    def test_15_replay_is_deterministic_and_nonmutating(self):
        c = context()
        add(c)
        before = deepcopy(c)
        self.assertEqual(ledger_state(c), ledger_state(c))
        self.assertEqual(before, c)
        json.dumps(ledger_state(c), sort_keys=True, allow_nan=False)


class CorrectionsAndWithdrawalTests(unittest.TestCase):
    def test_16_correction_replaces_active_payload(self):
        c = context()
        first = add(c, name="old", at=-6)
        correction = add(c, "CORRECT", name="new", target="old", at=-3)
        x = ledger_state(c)
        self.assertEqual(["new"], [r["event_id"] for r in x["active_records"]])
        self.assertEqual(["new"], x["corrected_event_ids"])
        self.assertEqual(correction["digest"], x["ledger_tip"])
        self.assertNotEqual(first["digest"], x["ledger_tip"])

    def test_17_correction_of_noncurrent_old_target_is_rejected(self):
        c = context()
        add(c, name="old", at=-7)
        add(c, "CORRECT", name="new", target="old", at=-6)
        add(c, "CORRECT", name="bad", target="old", at=-5)
        with self.assertRaisesRegex(ValueError, "TARGET_NOT_CURRENT"):
            ledger_state(c)

    def test_18_second_correction_to_current_is_valid(self):
        c = context()
        add(c, name="old", at=-7)
        add(c, "CORRECT", name="new1", target="old", at=-6)
        add(c, "CORRECT", name="new2", target="new1", at=-5)
        self.assertEqual(["new2"], [r["event_id"] for r in ledger_state(c)["active_records"]])

    def test_19_cross_source_correction_is_rejected(self):
        c = context()
        add(c, name="old", at=-6)
        add(c, "CORRECT", name="bad", target="old", at=-3, source="other")
        with self.assertRaisesRegex(ValueError, "CROSS_SOURCE_REVISION"):
            ledger_state(c)

    def test_20_unknown_target_rejected(self):
        c = context()
        add(c, "CORRECT", name="bad", target="does-not-exist")
        with self.assertRaisesRegex(ValueError, "TARGET_NOT_CURRENT"):
            ledger_state(c)

    def test_21_withdrawn_event_is_not_active(self):
        c = context()
        add(c, name="original", at=-6)
        add(c, "WITHDRAW", name="withdrawn", target="original", at=-3)
        x = ledger_state(c)
        self.assertEqual([], x["active_records"])
        self.assertEqual(["withdrawn"], x["withdrawal_event_ids"])

    def test_22_withdraw_with_payload_rejected(self):
        c = context()
        add(c, name="original", at=-6)
        add(c, "WITHDRAW", name="bad", target="original", payload=content("bad"), at=-3)
        with self.assertRaisesRegex(ValueError, "WITHDRAWAL_PAYLOAD_FORBIDDEN"):
            ledger_state(c)

    def test_23_rights_revoke_clears_all_source_entries(self):
        c = context()
        add(c, name="one", at=-6)
        add(c, name="two", at=-5)
        add(c, "REVOKE_SOURCE", name="rights-off", at=-3)
        x = ledger_state(c)
        self.assertEqual([], x["active_records"])
        self.assertEqual(["synthetic-source-A"], x["revoked_sources"])

    def test_24_rights_revoke_does_not_clear_other_source(self):
        c = context()
        add(c, name="one", at=-7)
        add(c, name="two", at=-6, source="other")
        add(c, "REVOKE_SOURCE", name="rights-off", at=-3)
        x = ledger_state(c)
        self.assertEqual(["two"], [r["event_id"] for r in x["active_records"]])

    def test_25_records_after_revoke_cannot_renew_by_assertion(self):
        c = context()
        add(c, at=-7)
        add(c, "REVOKE_SOURCE", at=-6)
        add(c, at=-5)
        with self.assertRaisesRegex(ValueError, "SOURCE_EVENT_AFTER_REVOCATION"):
            ledger_state(c)

    def test_26_withdrawal_review_is_deferred(self):
        c = context()
        add(c, name="one", at=-6)
        add(c, "WITHDRAW", name="withdraw", target="one", at=-3)
        x = inspect_cross_track(c, baseline(c))
        self.assertEqual("DEFER_EVIDENCE_WITHDRAWN", x["decision_status"])
        with self.assertRaisesRegex(ValueError, "UNRESOLVED_REVIEW_NOT_RECEIPTABLE"):
            issue_shadow_receipt(x, when(0))

    def test_27_corrected_review_is_deferred(self):
        c = context()
        add(c, name="one", at=-6)
        add(c, "CORRECT", name="revised", target="one", at=-3)
        x = inspect_cross_track(c, baseline(c))
        self.assertEqual("DEFER_CORRECTION_RECONCILIATION", x["decision_status"])

    def test_28_upstream_defer_is_preserved(self):
        c = context()
        b = baseline(c)
        b["review_status"] = "DEFER_SAFETY_BOUNDARY"
        self.assertEqual("DEFER_UPSTREAM_REVIEW",
                         inspect_cross_track(c, b)["decision_status"])

    def test_29_cannot_launder_p2c_lineage(self):
        c = context()
        b = baseline(c)
        b["p2c_plan_lineage"] = "AUTHENTICATED"
        with self.assertRaisesRegex(ValueError, "BASE_AUDIT_AUTHORITY_MISMATCH"):
            inspect_cross_track(c, b)

    def test_30_cross_track_base_plan_mismatch_rejected(self):
        c = context()
        b = baseline(c)
        b["p2d_p2e_asserted_plan_id"] = "other-plan"
        with self.assertRaisesRegex(ValueError, "BASE_AUDIT_SCOPE_MISMATCH"):
            inspect_cross_track(c, b)

    def test_31_cross_track_base_plan_version_mismatch_rejected(self):
        c = context()
        b = baseline(c)
        b["common_plan_version"] = 999
        with self.assertRaisesRegex(ValueError, "BASE_AUDIT_SCOPE_MISMATCH"):
            inspect_cross_track(c, b)

    def test_32_no_upstream_authority_leak(self):
        c = context()
        b = baseline(c)
        b["safe_to_auto_apply"] = True
        with self.assertRaisesRegex(ValueError, "BASE_AUDIT_AUTHORITY_MISMATCH"):
            inspect_cross_track(c, b)


class ReceiptFenceTests(unittest.TestCase):
    def test_33_receipt_is_nonexecutable(self):
        c, a, r = ready()
        self.assertEqual("NONE_SHADOW_REPLAY_TOKEN", r["capability"])
        self.assertFalse(check_shadow_receipt(r, a, c, when(0))["can_write_plan"])

    def test_34_intact_receipt_remains_shadow_only(self):
        c, a, r = ready()
        x = check_shadow_receipt(r, a, c, when(3))
        self.assertEqual("SHADOW_RECEIPT_INTACT_NOT_APPROVAL", x["status"])

    def test_35_future_correction_invalidates_old_receipt(self):
        c, a, r = ready()
        add(c, "CORRECT", name="updated", target="event-1", at=2)
        self.assertEqual("STALE_SHADOW_RECEIPT",
                         check_shadow_receipt(r, a, c, when(3))["status"])

    def test_36_future_revoke_invalidates_old_receipt(self):
        c, a, r = ready()
        add(c, "REVOKE_SOURCE", name="revoked", at=2)
        self.assertEqual("STALE_SHADOW_RECEIPT",
                         check_shadow_receipt(r, a, c, when(3))["status"])

    def test_37_future_unrelated_source_record_also_fences_token(self):
        c, a, r = ready()
        add(c, name="new-item", source="other-source", at=2)
        self.assertEqual("STALE_SHADOW_RECEIPT",
                         check_shadow_receipt(r, a, c, when(3))["status"])

    def test_38_future_event_does_not_change_past_snapshot(self):
        c, a, r = ready()
        add(c, name="future", source="other-source", at=2)
        self.assertEqual(a["ledger_tip"], ledger_state(c)["ledger_tip"])
        self.assertEqual("STALE_SHADOW_RECEIPT",
                         check_shadow_receipt(r, a, c, when(3))["status"])

    def test_39_policy_change_fences_token(self):
        c, a, r = ready()
        c["policy_version"] = "shadow-policy-v2"
        self.assertEqual("CONTEXT_OR_POLICY_CHANGED",
                         check_shadow_receipt(r, a, c, when(2))["reason"])

    def test_40_plan_version_change_fences_token(self):
        c, a, r = ready()
        c["plan_version"] = 8
        self.assertEqual("STALE_SHADOW_RECEIPT",
                         check_shadow_receipt(r, a, c, when(2))["status"])

    def test_41_changed_review_digest_fences_token(self):
        c, a, r = ready()
        a["base_audit_digest"] = content("different")
        self.assertEqual("STALE_SHADOW_RECEIPT",
                         check_shadow_receipt(r, a, c, when(2))["status"])

    def test_42_review_now_clinical_defer_fences_token(self):
        c, a, r = ready()
        a["decision_status"] = "DEFER_EVIDENCE_WITHDRAWN"
        self.assertEqual("REVIEW_IS_NOT_SAFE",
                         check_shadow_receipt(r, a, c, when(2))["reason"])

    def test_43_tampered_receipt_rejected(self):
        c, a, r = ready()
        r["policy_version"] = "attacker-value"
        with self.assertRaisesRegex(ValueError, "RECEIPT_TAMPERED"):
            check_shadow_receipt(r, a, c, when(2))

    def test_44_receipt_issued_before_decision_rejected(self):
        c = context()
        a = inspect_cross_track(c, baseline(c))
        with self.assertRaisesRegex(ValueError, "RECEIPT_BEFORE_DECISION"):
            issue_shadow_receipt(a, when(-2))

    def test_45_receipt_verified_before_issue_rejected(self):
        c, a, r = ready()
        with self.assertRaisesRegex(ValueError, "CHECK_BEFORE_RECEIPT"):
            check_shadow_receipt(r, a, c, when(-2))

    def test_46_failed_receipt_does_not_change_input(self):
        c, a, r = ready()
        before = deepcopy((c, a, r))
        check_shadow_receipt(r, a, c, when(2))
        self.assertEqual(before, (c, a, r))

    def test_47_same_instant_offset_equivalent(self):
        c, a, r = ready()
        check = "2026-11-08T20:00:00+08:00"
        self.assertEqual("SHADOW_RECEIPT_INTACT_NOT_APPROVAL",
                         check_shadow_receipt(r, a, c, check)["status"])


    def test_48_unauthorized_base_plan_patch_rejected(self):
        c = context()
        b = baseline(c)
        b["plan_patch"] = {"unapproved": "change"}
        with self.assertRaisesRegex(ValueError, "BASE_AUDIT_AUTHORITY_MISMATCH"):
            inspect_cross_track(c, b)

    def test_49_malformed_receipt_schema_rejected(self):
        c, a, r = ready()
        r["unexpected"] = True
        with self.assertRaisesRegex(ValueError, "RECEIPT_SCHEMA_MISMATCH"):
            check_shadow_receipt(r, a, c, when(2))

    def test_50_real_dev0_integration_entrypoint_is_called(self):
        c = context()
        fake = ModuleType("p2x_integration")
        seen = []
        def audited(bundle):
            seen.append(bundle)
            return baseline(c)
        fake.audit = audited
        bundle = {"invented_bundle": 1}
        with patch.dict("sys.modules", {"p2x_integration": fake}):
            output = inspect_bundle(bundle, c)
        self.assertEqual([bundle], seen)
        self.assertEqual("SHADOW_SNAPSHOT_ONLY", output["decision_status"])
        self.assertFalse(output["can_write_plan"])


if __name__ == "__main__":
    unittest.main()
