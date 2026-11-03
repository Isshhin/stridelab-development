"""Negative controls: prove the harness catches specific intentionally bad rules.

Patching applies only inside these isolated unit tests; no product model or
source data is modified, and the canonical contract runner executes unpatched.
"""
import unittest
from unittest.mock import patch

import checks
import runner


class HarnessIntegrityTests(unittest.TestCase):
    def test_all_58_registered_cases_have_explicit_checks(self):
        rows = runner.registry_rows()
        self.assertEqual(58, len(rows))
        self.assertEqual({r["case_id"] for r in rows}, set(checks.registered_checks()))

    def test_skipped_workout_mutation_detected(self):
        # Bad implementation erroneously credits planned work to skipped run.
        with patch.object(checks, "observed_run",
                          side_effect=lambda status, planned, observed: planned):
            with self.assertRaises(AssertionError):
                checks.check_L05()

    def test_daily_session_limit_mutation_detected(self):
        # Bad implementation always claims the schedule is feasible.
        with patch.object(checks, "time_feasible", return_value=True):
            with self.assertRaises(AssertionError):
                checks.check_MD21()

    def test_same_day_tsb_lookahead_mutation_detected(self):
        # Bad implementation leaks today's updated load into TODAY's TSB.
        bad = {"today_tsb": -6.66666666667,
               "ctl_after_today": 50.47619047619,
               "atl_after_today": 57.14285714286,
               "next_day_tsb": -6.66666666667}
        with patch.object(checks, "generic_ema", return_value=bad):
            with self.assertRaises(AssertionError):
                checks.check_AL16()

    def test_missing_hr_mutation_detected(self):
        # Bad implementation makes invalid GPS/threshold data look numeric.
        with patch.object(checks, "stress", return_value={
                "valid": True, "value": 55, "reason": None}):
            with self.assertRaises(AssertionError):
                checks.check_L08()


if __name__ == "__main__":
    unittest.main()
