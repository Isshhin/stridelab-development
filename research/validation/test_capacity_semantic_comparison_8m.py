"""Batch 8M: independently scoped structural comparison, no coach ranking."""
from __future__ import annotations

from copy import deepcopy
from hashlib import sha256
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from capacity_semantic_comparison_8m import (
    POLICY_CAPACITY, SemanticComparisonRejected, evaluate,
    verify_semantics,
)
from original_lab_evidence_8l import canonical_json
from policy_candidates import run_policy

ROOT = Path(__file__).resolve().parent
files = [
    "original_lab_8l.json", "original_lab_8l_review_pin.json",
    "original_case_semantics_8m.json",
    "original_case_semantics_8m_review_pin.json",
]


def _load():
    return [json.loads((ROOT / "evidence" / n).read_text(encoding="utf-8"))
            for n in files]


def _repin(sidecar, pin):
    h = sha256(canonical_json(sidecar["body"])).hexdigest()
    sidecar["body_sha256"] = h
    pin["sidecar_body_sha256"] = h


class OriginalCapacitySemanticTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original, cls.pin, cls.sidecar, cls.sidecar_pin = _load()
        cls.result = evaluate(cls.original, cls.pin, cls.sidecar, cls.sidecar_pin)

    def test_216_comparable_original_plan_weeks_432_boolean_checks(self):
        c = self.result["counts"]
        self.assertEqual(216, c["original_generated_weeks_checked"])
        self.assertEqual(432, c["boolean_capacity_checks"])
        self.assertEqual(6, c["infeasible_original_plans"])
        self.assertEqual(3, c["observed_over_configured_capacity_weeks"])
        self.assertEqual(3, c["original_validator_flagged_plans"])
        self.assertEqual(108, c["target_vs_schedule_mismatch_weeks"])
        self.assertEqual(42, len(self.result["original_policy_case_rows"]))

    def test_capacity_arithmetic_only_never_whole_coaching_quality(self):
        r = self.result
        self.assertEqual("HYPOTHETICAL_CAPACITY_FLAGS_MATCH_ONLY", r["status"])
        self.assertEqual(POLICY_CAPACITY, r["scope"])
        self.assertEqual(0, r["b0_h1_full_actions_compared"])
        self.assertFalse(r["original_lab_six_week_generator_vs_b0_h1_equivalent"])
        self.assertFalse(r["human_outcome_effectiveness_measured"])
        self.assertFalse(r["source_capacity_verified_in_real_athletes"])
        self.assertFalse(r["original_policy_quality_ranked"])
        self.assertFalse(r["private_workout_files_used"])
        self.assertGreaterEqual(len(r["blocked_comparisons"]), 5)

    def test_source_infeasible_and_inconsistent_targets_not_hidden(self):
        items = self.result["original_policy_case_rows"]
        self.assertEqual(6, sum(x["status"] == "BLOCKED_ORIGINAL_PLAN_INFEASIBLE"
                                for x in items))
        original_only = [x for x in items if x["original_policy_id"].startswith("base.")
                         and x["status"] != "BLOCKED_ORIGINAL_PLAN_INFEASIBLE"]
        combined = [x for x in items if x["original_policy_id"].startswith("combined.")
                    and x["status"] != "BLOCKED_ORIGINAL_PLAN_INFEASIBLE"]
        self.assertEqual(108, sum(x["target_mismatch_weeks"] for x in original_only))
        self.assertEqual(0, sum(x["target_mismatch_weeks"] for x in combined))

    def test_evidence_sidecar_tamper_rejected_before_any_comparison(self):
        old, pin, sidecar, sidecar_pin = map(deepcopy, _load())
        sidecar["body"]["input_cases"][0]["configured_capacity_km"] = 999
        with self.assertRaisesRegex(SemanticComparisonRejected,
                                    "SIDECAR_DIGEST_MISMATCH"):
            evaluate(old, pin, sidecar, sidecar_pin)

    def test_repin_private_fields_cannot_promote_source(self):
        old, pin, sidecar, sidecar_pin = map(deepcopy, _load())
        sidecar["body"]["input_cases"][0]["garmin_user_token"] = "secret"
        _repin(sidecar, sidecar_pin)
        with self.assertRaisesRegex(SemanticComparisonRejected,
                                    "INPUT_CASE_ID_OR_FIELDS_MISMATCH"):
            evaluate(old, pin, sidecar, sidecar_pin)

    def test_repin_false_human_outcomes_blocked(self):
        old, pin, sidecar, sidecar_pin = map(deepcopy, _load())
        sidecar["body"]["human_effectiveness_data_included"] = True
        _repin(sidecar, sidecar_pin)
        with self.assertRaisesRegex(SemanticComparisonRejected,
                                    "FALSE_REAL_WORLD_EVIDENCE_CLAIM"):
            evaluate(old, pin, sidecar, sidecar_pin)

    def test_unverified_capacity_cannot_be_relabeled_as_verified(self):
        old, pin, sidecar, sidecar_pin = map(deepcopy, _load())
        sidecar["body"]["input_cases"][0]["capacity_basis"] = "VERIFIED_HUMAN_CAPACITY"
        _repin(sidecar, sidecar_pin)
        with self.assertRaisesRegex(SemanticComparisonRejected,
                                    "FABRICATED_REAL_WORLD_INPUT"):
            evaluate(old, pin, sidecar, sidecar_pin)

    def test_wrong_original_policy_snapshot_fails_cross_link(self):
        old, pin, sidecar, sidecar_pin = map(deepcopy, _load())
        sidecar["body"]["source_policy_evidence_8l_body_sha256"] = "0" * 64
        _repin(sidecar, sidecar_pin)
        with self.assertRaisesRegex(SemanticComparisonRejected,
                                    "ORIGINAL_POLICY_OUTPUT_ALIGNMENT_FAILED"):
            evaluate(old, pin, sidecar, sidecar_pin)

    def test_forced_b0_boolean_error_is_detected(self):
        old, pin, sidecar, sidecar_pin = _load()
        def incorrect_b0(policy, state):
            result = run_policy(policy, state)
            if policy == "B0":
                result["indicators"]["weekly_over_cap"] = not result["indicators"]["weekly_over_cap"]
            return result
        with patch("capacity_semantic_comparison_8m.run_policy", side_effect=incorrect_b0):
            with self.assertRaisesRegex(SemanticComparisonRejected,
                                        "SHARED_CAPACITY_BOOLEAN_OR_VALIDATOR_MISMATCH"):
                evaluate(old, pin, sidecar, sidecar_pin)

    def test_relative_availability_and_missing_history_distinctions(self):
        rows = verify_semantics(self.sidecar, self.sidecar_pin, self.original)
        two = next(r for r in rows if r["case_id"] == "low-frequency-two-days")
        self.assertLess(len(two["available_days"]), two["default_required_sessions"])
        partial = next(r for r in rows if r["case_id"] == "partial-quality-unknown-recovery")
        self.assertEqual(1, partial["history_status_counts"]["partial"])
        self.assertEqual(1, partial["history_status_counts"]["unknown"])
        self.assertIsNone(partial["session_duration_minutes"])


if __name__ == "__main__":
    unittest.main()
