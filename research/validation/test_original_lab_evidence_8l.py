"""Batch 8L manually transferred original-lab evidence negative controls."""
from copy import deepcopy
import json
from pathlib import Path
import unittest

from original_lab_evidence_8l import HandoffRejected, verify

ROOT = Path(__file__).resolve().parent
EVIDENCE = ROOT / "evidence" / "original_lab_8l.json"
PIN = ROOT / "evidence" / "original_lab_8l_review_pin.json"


class CrossRepoEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = json.loads(EVIDENCE.read_text(encoding="utf-8"))
        cls.pin = json.loads(PIN.read_text(encoding="utf-8"))
        cls.verified = verify(cls.evidence, cls.pin)

    def test_original_lab_evidence_admitted_but_source_not_executed_here(self):
        r = self.verified
        self.assertEqual("PINNED_SYNTHETIC_POLICY_EVIDENCE_STRUCTURALLY_VERIFIED",
                         r["status"])
        self.assertEqual(42, r["original_policy_comparison_rows_checked"])
        self.assertEqual(3, r["original_validation_error_plans_preserved"])
        self.assertEqual(6, r["original_infeasible_plans_preserved"])
        self.assertTrue(r["manual_origin_review_required"])
        for field in ("origin_authenticated_cryptographically",
                      "cross_repo_source_checkout_performed",
                      "actual_original_lab_source_executed_by_this_ci",
                      "real_performance_effect_demonstrated",
                      "android_engine2_integration_ready"):
            self.assertFalse(r[field])

    def test_sha_change_without_pin_update_is_rejected(self):
        x = deepcopy(self.evidence)
        x["body"]["source_files"][0]["sha256"] = "0" * 64
        with self.assertRaisesRegex(HandoffRejected, "HANDOFF_BODY_TAMPERED"):
            verify(x, self.pin)

    def test_self_consistent_forgery_fails_independent_manual_pin(self):
        x = deepcopy(self.evidence)
        x["body"]["summary"]["real_athlete_activities_analyzed"] = 3
        from original_lab_evidence_8l import canonical_json
        from hashlib import sha256
        x["body_sha256"] = sha256(canonical_json(x["body"])).hexdigest()
        with self.assertRaisesRegex(HandoffRejected, "NOT_THE_MANUALLY_REVIEWED_EVIDENCE"):
            verify(x, self.pin)

    def test_wiped_capacity_errors_cannot_be_promoted(self):
        x = deepcopy(self.evidence)
        for c in x["body"]["cases"]:
            for r in c["results"]:
                if r["status"] == "GENERATED_WITH_STRUCTURAL_FINDINGS":
                    r["status"] = "GENERATED"
                    r["validator_error_rules"] = []
        from original_lab_evidence_8l import canonical_json
        from hashlib import sha256
        x["body_sha256"] = sha256(canonical_json(x["body"])).hexdigest()
        p = deepcopy(self.pin)
        p["body_sha256"] = x["body_sha256"]
        with self.assertRaisesRegex(HandoffRejected, "UNEXPECTED_ORIGINAL_LAB_RESULT_COUNTS"):
            verify(x, p)

    def test_added_private_activity_fields_rejected_even_when_rehashed(self):
        x = deepcopy(self.evidence)
        x["body"]["user_gps_recordings"] = ["private"]
        from original_lab_evidence_8l import canonical_json
        from hashlib import sha256
        x["body_sha256"] = sha256(canonical_json(x["body"])).hexdigest()
        p = deepcopy(self.pin)
        p["body_sha256"] = x["body_sha256"]
        with self.assertRaisesRegex(HandoffRejected, "INVALID_BODY_SCHEMA"):
            verify(x, p)

    def test_source_digest_drift_rejected(self):
        x = deepcopy(self.evidence)
        x["body"]["original_source_fingerprint_sha256"] = "0" * 64
        from original_lab_evidence_8l import canonical_json
        from hashlib import sha256
        x["body_sha256"] = sha256(canonical_json(x["body"])).hexdigest()
        p = deepcopy(self.pin)
        p["body_sha256"] = x["body_sha256"]
        with self.assertRaisesRegex(HandoffRejected, "BASELINE_SOURCE_OR_FIXTURE_DRIFT"):
            verify(x, p)

    def test_bad_producer_reference_or_origination_claim_rejected(self):
        for key, value in [
            ("producer_ci_head", "unknown"),
            ("producer_branch", "main"),
            ("origin_review", "CRYPTOGRAPHICALLY_SIGNED"),
        ]:
            with self.subTest(key=key):
                p = deepcopy(self.pin)
                p[key] = value
                with self.assertRaises(HandoffRejected):
                    verify(self.evidence, p)

    def test_infeasible_plan_cannot_gain_unreviewed_weekly_schedule(self):
        x = deepcopy(self.evidence)
        for row in x["body"]["cases"]:
            if row["case_id"] == "low-frequency-two-days":
                row["results"][0]["weekly_scheduled_km"] = [10.0] * 6
                break
        from original_lab_evidence_8l import canonical_json
        from hashlib import sha256
        x["body_sha256"] = sha256(canonical_json(x["body"])).hexdigest()
        p = deepcopy(self.pin)
        p["body_sha256"] = x["body_sha256"]
        with self.assertRaisesRegex(HandoffRejected, "INFEASIBILITY_NOT_PRESERVED"):
            verify(x, p)

    def test_real_human_outcomes_not_claimed_anywhere(self):
        result = self.verified
        self.assertEqual(0, result["real_athlete_records_analyzed"])
        self.assertFalse(result["real_performance_effect_demonstrated"])
        self.assertFalse(result["private_data_exposed"])
        self.assertFalse(self.evidence["body"]["private_athlete_data_used"])
        self.assertFalse(self.evidence["body"]["activity_gps_included"])


if __name__ == "__main__":
    unittest.main()
