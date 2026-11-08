"""Regression tests for synthetic-only evidence multiplicity and decision leakage."""
from copy import deepcopy
import unittest

from p2x_lineage import assess
from test_p2x_lineage import fixture


class EvidenceIdentityBoundaries(unittest.TestCase):
    def test_01_identical_p2a_record_twice_must_fail(self):
        b, m, l = fixture()
        b["p2a"]["observations"].append(deepcopy(b["p2a"]["observations"][0]))
        with self.assertRaisesRegex(ValueError, "DUPLICATE_IDENTICAL_NATIVE_EVENT_ID"):
            assess(b, m, l)

    def test_02_identical_p2c_record_twice_must_fail(self):
        b, m, l = fixture()
        b["p2c"]["events"].append(deepcopy(b["p2c"]["events"][0]))
        with self.assertRaisesRegex(ValueError, "DUPLICATE_IDENTICAL_NATIVE_EVENT_ID"):
            assess(b, m, l)

    def test_03_identical_p2d_record_twice_must_fail(self):
        b, m, l = fixture()
        b["p2d"]["observations"].append(deepcopy(b["p2d"]["observations"][0]))
        with self.assertRaisesRegex(ValueError, "DUPLICATE_IDENTICAL_NATIVE_EVENT_ID"):
            assess(b, m, l)

    def test_04_identical_p2e_record_twice_must_fail(self):
        b, m, l = fixture()
        b["p2e"]["observations"].append(deepcopy(b["p2e"]["observations"][0]))
        with self.assertRaisesRegex(ValueError, "DUPLICATE_IDENTICAL_NATIVE_EVENT_ID"):
            assess(b, m, l)

    def test_05_distinct_conflicting_duplicate_must_fail(self):
        b, m, l = fixture()
        different = deepcopy(b["p2a"]["observations"][0])
        different["status"] = "unknown"
        b["p2a"]["observations"].append(different)
        with self.assertRaisesRegex(ValueError, "CONFLICTING_NATIVE_EVENT_ID"):
            assess(b, m, l)

    def test_06_event_ids_remain_namespaced_across_tracks(self):
        b, m, l = fixture()
        # Different tracks may carry equal strings, not duplicate native rows.
        b["p2e"]["observations"][0]["event_id"] = "A1"
        from p2x_lineage import _native_digest
        m["evidence_bindings"][3]["event_id"] = "A1"
        m["evidence_bindings"][3]["payload_sha256"] = _native_digest(b["p2e"]["observations"][0])
        l["active_records"][3]["payload_digest"] = m["evidence_bindings"][3]["payload_sha256"]
        self.assertEqual("SYNTHETIC_ASSERTED_CONSISTENCY_ONLY", assess(b, m, l)["status"])

    def test_07_missing_withdrawal_list_does_not_mean_no_withdrawal(self):
        b, m, l = fixture(); del l["withdrawal_event_ids"]
        with self.assertRaisesRegex(ValueError, "LEDGER_DISPOSITION_LIST_INVALID"):
            assess(b, m, l)

    def test_08_missing_revocation_list_does_not_mean_no_revocation(self):
        b, m, l = fixture(); del l["revoked_sources"]
        with self.assertRaisesRegex(ValueError, "LEDGER_DISPOSITION_LIST_INVALID"):
            assess(b, m, l)

    def test_09_missing_correction_list_does_not_mean_no_correction(self):
        b, m, l = fixture(); del l["corrected_event_ids"]
        with self.assertRaisesRegex(ValueError, "LEDGER_DISPOSITION_LIST_INVALID"):
            assess(b, m, l)

    def test_10_null_disposition_list_rejected(self):
        b, m, l = fixture(); l["revoked_sources"] = None
        with self.assertRaisesRegex(ValueError, "LEDGER_DISPOSITION_LIST_INVALID"):
            assess(b, m, l)

    def test_11_string_disposition_list_rejected(self):
        b, m, l = fixture(); l["corrected_event_ids"] = ""
        with self.assertRaisesRegex(ValueError, "LEDGER_DISPOSITION_LIST_INVALID"):
            assess(b, m, l)

    def test_12_duplicate_disposition_id_rejected(self):
        b, m, l = fixture(); l["withdrawal_event_ids"] = ["w1", "w1"]
        with self.assertRaisesRegex(ValueError, "LEDGER_DISPOSITION_LIST_INVALID"):
            assess(b, m, l)

    def test_13_invalid_disposition_element_rejected(self):
        b, m, l = fixture(); l["revoked_sources"] = [["bad"]]
        with self.assertRaisesRegex(ValueError, "LEDGER_DISPOSITION_LIST_INVALID"):
            assess(b, m, l)

    def test_14_outcome_field_forbidden(self):
        b, m, l = fixture(); b["p2c"]["outcome"] = "improved"
        with self.assertRaisesRegex(ValueError, "FUTURE_OUTCOME_LABEL_FORBIDDEN"):
            assess(b, m, l)

    def test_15_future_performance_forbidden(self):
        b, m, l = fixture(); b["p2e"]["future_performance"] = 4.5
        with self.assertRaisesRegex(ValueError, "FUTURE_OUTCOME_LABEL_FORBIDDEN"):
            assess(b, m, l)

    def test_16_model_accuracy_forbidden(self):
        b, m, l = fixture(); b["p2a"]["model_accuracy"] = 0.98
        with self.assertRaisesRegex(ValueError, "FUTURE_OUTCOME_LABEL_FORBIDDEN"):
            assess(b, m, l)

    def test_17_best_policy_forbidden(self):
        b, m, l = fixture(); b["p2d"]["best_policy"] = "MORE_LOAD"
        with self.assertRaisesRegex(ValueError, "FUTURE_OUTCOME_LABEL_FORBIDDEN"):
            assess(b, m, l)

    def test_18_clean_fixture_preserves_lack_of_authority(self):
        b, m, l = fixture()
        result = assess(b, m, l)
        self.assertEqual("SYNTHETIC_ASSERTED_CONSISTENCY_ONLY", result["status"])
        self.assertFalse(result["can_write_plan"])
        self.assertFalse(result["authenticated_athlete_approval"])
        self.assertEqual("CLOSED", result["gate2"])


if __name__ == "__main__":
    unittest.main()
