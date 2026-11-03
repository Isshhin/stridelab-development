"""Independent negative controls for the Batch 8B B0/H1 comparison runner."""
import json
import unittest
from copy import deepcopy
from pathlib import Path
from unittest.mock import patch

import policy_runner
import policy_candidates


class ResearchPolicyComparisonTests(unittest.TestCase):
    def test_58_fixtures_exactly_match_canonical_registry(self):
        fixture = json.loads(policy_runner.FIXTURE.read_text(encoding="utf-8"))
        self.assertEqual(58, len(policy_runner.validate_fixture(fixture)))
        self.assertEqual(58, len({c["id"] for c in fixture["cases"]}))

    def test_missing_registered_case_is_rejected(self):
        fixture = json.loads(policy_runner.FIXTURE.read_text(encoding="utf-8"))
        fixture["cases"].pop()
        with self.assertRaises(ValueError):
            policy_runner.validate_fixture(fixture)

    def test_comparator_detects_mutated_capacity_decision(self):
        genuine = policy_runner.run_policy

        def broken(which, state):
            result = genuine(which, state)
            if which == "H1" and result["reason"] == "CAPACITY_CAP":
                result["action"] = "KEEP"
            return result

        with patch.object(policy_runner, "run_policy", side_effect=broken):
            report = policy_runner.run_suite()
        errors = {case["case_id"]: case for case in report["cases"] if case["status"] == "FAIL"}
        self.assertIn("E003:L09", errors)

    def test_unapproved_write_is_rejected(self):
        genuine = policy_runner.run_policy

        def broken(which, state):
            result = genuine(which, state)
            if which == "H1":
                result["revision"]["applied"] = True
            return result

        with patch.object(policy_runner, "run_policy", side_effect=broken):
            report = policy_runner.run_suite()
        self.assertGreater(report["counts"]["FAIL"], 0)

    def test_policy_inputs_immutable_and_versioned(self):
        fixture = json.loads(policy_runner.FIXTURE.read_text(encoding="utf-8"))
        state = deepcopy(fixture["base_state"])
        original = deepcopy(state)
        for name in ("B0", "H1"):
            result = policy_candidates.run_policy(name, state)
            self.assertEqual(original, state)
            self.assertEqual(result["revision"]["after"], original["weekly"])
            self.assertFalse(result["revision"]["applied"])
            self.assertIn("policy_id", result)

    def test_missing_output_field_never_treated_as_null(self):
        sample = {"H1": {"base": {"observation": {"actual_run": None}}}}
        with self.assertRaises(KeyError):
            policy_runner.get_path(sample, "H1.base.observation.fabricated_field")


if __name__ == "__main__":
    unittest.main()
