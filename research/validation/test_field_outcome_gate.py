"""Real-outcome eligibility checks without accessing any athlete data."""
import unittest

from field_outcome_gate import inspect_manifest


def metadata():
    return {
        "schema": "stridelab-field-outcome-handoff-v1",
        "protocol_id": "run-easy-pace-at-HR-v1",
        "athlete_pseudonym": "athlete-001",
        "dataset_manifest_sha256": "a" * 64,
        "measurement_source": "consented_device_and_route_check",
        "baseline_period": {"start": "2026-01-01", "end": "2026-02-01"},
        "followup_period": {"start": "2026-03-01", "end": "2026-04-01"},
        "primary_endpoint": "easy_speed_at_comparable_hr",
        "consent_review_status": "pending",
        "quality_review_status": "pending",
        "sampling_review_status": "pending",
    }


class FieldReadinessTests(unittest.TestCase):
    def test_no_data_blocks_and_reports_no_performance_effect(self):
        result = inspect_manifest(None)
        self.assertEqual("BLOCKED_NO_CONSENTED_FIELD_DATA", result["status"])
        self.assertFalse(result["actual_performance_effect_demonstrated"])
        self.assertEqual(0, result["athlete_outcomes_analyzed"])

    def test_unsupported_endpoint_is_not_pretended_to_be_preregistered(self):
        handoff = metadata()
        handoff["primary_endpoint"] = "estimated_vo2max"
        self.assertEqual("REJECTED_UNREGISTERED_ENDPOINT",
                         inspect_manifest(handoff)["status"])

    def test_temporal_leakage_rejected(self):
        handoff = metadata()
        handoff["followup_period"]["start"] = "2026-02-01"
        self.assertEqual("REJECTED_NON_TEMPORAL_SPLIT",
                         inspect_manifest(handoff)["status"])

    def test_missing_consent_cannot_be_bypassed_by_good_sensor_quality(self):
        handoff = metadata()
        handoff["quality_review_status"] = "externally_verified"
        handoff["sampling_review_status"] = "externally_verified"
        self.assertEqual("BLOCKED_CONSENT_REVIEW",
                         inspect_manifest(handoff)["status"])

    def test_unverified_measurement_source_blocks(self):
        handoff = metadata()
        handoff["consent_review_status"] = "externally_verified"
        self.assertEqual("BLOCKED_MEASUREMENT_QUALITY",
                         inspect_manifest(handoff)["status"])

    def test_unverified_sampling_blocks(self):
        handoff = metadata()
        handoff["consent_review_status"] = "externally_verified"
        handoff["quality_review_status"] = "externally_verified"
        self.assertEqual("BLOCKED_SAMPLING_REVIEW",
                         inspect_manifest(handoff)["status"])

    def test_even_fully_self_declared_approvals_require_human_review(self):
        handoff = metadata()
        for key in ("consent_review_status", "quality_review_status",
                    "sampling_review_status"):
            handoff[key] = "externally_verified"
        result = inspect_manifest(handoff)
        self.assertEqual("READY_FOR_INDEPENDENT_HUMAN_PROVENANCE_REVIEW",
                         result["status"])
        self.assertEqual("NOT_PERFORMED", result["human_consent_verification"])
        self.assertFalse(result["actual_performance_effect_demonstrated"])
        self.assertEqual(0, result["athlete_outcomes_analyzed"])

    def test_short_checksum_rejected(self):
        handoff = metadata()
        handoff["dataset_manifest_sha256"] = "unverifiable"
        self.assertEqual("REJECTED_DATASET_PROVENANCE",
                         inspect_manifest(handoff)["status"])

    def test_no_result_from_invalid_schema(self):
        handoff = metadata()
        handoff["schema"] = "invented-format"
        result = inspect_manifest(handoff)
        self.assertEqual("REJECTED_UNREGISTERED_ENDPOINT", result["status"])
        self.assertFalse(result["actual_performance_effect_demonstrated"])

    def test_impossible_calendar_date_is_rejected(self):
        handoff = metadata()
        handoff["baseline_period"]["end"] = "2026-02-30"
        self.assertEqual("REJECTED_PERIODS",
                         inspect_manifest(handoff)["status"])

    def test_missing_fields_rejected_not_imputed(self):
        handoff = metadata()
        del handoff["measurement_source"]
        self.assertEqual("REJECTED_INCOMPLETE_OUTCOME_HANDOFF",
                         inspect_manifest(handoff)["status"])


if __name__ == "__main__":
    unittest.main()
