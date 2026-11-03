"""Mutation/independence checks for newly generated, reproducible held-out scenarios."""
import unittest
from unittest.mock import patch

import heldout_runner


class SeededAdversarialTests(unittest.TestCase):
    def test_exact_pre_registered_cohort_size(self):
        self.assertEqual(24, len(heldout_runner.COHORTS))
        result = heldout_runner.run_suite(per_cohort=2)
        self.assertEqual(48, result["counts"]["TOTAL"])
        self.assertEqual(48, len({c["id"] for c in result["cases"]}))

    def test_seed_reproduces_all_case_inputs_and_decisions(self):
        a = heldout_runner.run_suite(seed=82603, per_cohort=2)
        b = heldout_runner.run_suite(seed=82603, per_cohort=2)
        self.assertEqual(a["cases"], b["cases"])
        self.assertEqual(a["b0_h1_action_differences"],
                         b["b0_h1_action_differences"])

    def test_changed_seed_changes_input_hashes(self):
        a = heldout_runner.run_suite(seed=82603, per_cohort=1)
        b = heldout_runner.run_suite(seed=82604, per_cohort=1)
        self.assertNotEqual([x["input_sha256"] for x in a["cases"]],
                            [x["input_sha256"] for x in b["cases"]])

    def test_always_keep_mutation_caught_by_unseen_capacity_cases(self):
        genuine = heldout_runner.run_policy

        def broken(which, snapshot):
            result = genuine(which, snapshot)
            if which == "H1":
                result["action"] = "KEEP"
            return result

        with patch.object(heldout_runner, "run_policy", side_effect=broken):
            report = heldout_runner.run_suite(per_cohort=2)
        failed = {c["cohort"] for c in report["cases"] if c["status"] == "FAIL"}
        self.assertIn("OVER_CAP", failed)
        self.assertIn("PAIN_ONLY", failed)
        self.assertIn("TIME_OVER", failed)

    def test_missing_hr_is_kept_unknown(self):
        state = heldout_runner.sample(0, "MISSING_HR",
                                      __import__("random").Random(82603))
        output = heldout_runner.run_policy("H1", state)
        self.assertIsNone(output["observation"]["hr_bpm"])

    def test_no_raw_private_athlete_inputs_required(self):
        result = heldout_runner.run_suite(per_cohort=1)
        self.assertIn("not_longitudinal_outcome_evidence", result)
        self.assertTrue(result["not_longitudinal_outcome_evidence"])
        self.assertIn("scope", result)


if __name__ == "__main__":
    unittest.main()
