"""Batch 1 structural tests; no physiological claims."""
import copy
import json
from pathlib import Path
import unittest
from research.engine15_skeleton.harness import evaluate, evaluate_matrix

ROOT = Path(__file__).resolve().parent
CASES = json.loads((ROOT / "fixtures" / "cases.json").read_text())
POLICIES = json.loads((ROOT / "candidate_policies.json").read_text())

def case(id):
    return copy.deepcopy(next(c for c in CASES if c["id"] == id))

class SkeletonTests(unittest.TestCase):
    def setUp(self):
        self.policy = POLICIES[0]

    def test_deterministic_and_input_not_modified(self):
        specimen = case("R04")
        previous = copy.deepcopy(specimen)
        self.assertEqual(evaluate(specimen, self.policy), evaluate(specimen, self.policy))
        self.assertEqual(specimen, previous)

    def test_both_ramp_ceiling_types_bind(self):
        lower = evaluate(case("R01"), self.policy)
        higher = evaluate(case("R05"), self.policy)
        self.assertIn("RELATIVE_LIMIT_BINDS", lower["weeks"][1]["rule_ids"])
        self.assertIn("ABSOLUTE_LIMIT_BINDS", higher["weeks"][1]["rule_ids"])

    def test_declining_history_selects_recent_lower_mean(self):
        output = evaluate(case("R07"), self.policy)
        self.assertEqual(output["baseline_km"], output["evidence"]["last_two_complete_mean_km"])
        self.assertIn("RECENT_DOWNWARD_TREND", [e["rule"] for e in output["decision_trace"]])

    def test_incomplete_history_is_not_zero(self):
        output = evaluate(case("R08"), self.policy)
        self.assertEqual(output["confidence"], "low")
        self.assertEqual(output["evidence"]["complete_weeks"], 2)
        self.assertIn("INCOMPLETE_HISTORY", [e["rule"] for e in output["decision_trace"]])

    def test_manual_disagreement_defaults_lower(self):
        scenario = case("R04")
        scenario["declared_km"] = 30
        out = evaluate(scenario, self.policy)
        self.assertEqual(out["baseline_km"], 30.0)
        self.assertIn("UNRESOLVED_DECLARATION", [e["rule"] for e in out["decision_trace"]])

    def test_recovery_week_is_week_four_not_five(self):
        rows = evaluate(case("R04"), self.policy)["weeks"]
        self.assertIn("RECOVERY_WEEK", rows[3]["rule_ids"])
        self.assertLess(rows[3]["weekly_km"], rows[2]["weekly_km"])
        self.assertGreater(rows[4]["weekly_km"], rows[2]["weekly_km"])

    def test_three_day_high_target_reports_long_run_conflict(self):
        out = evaluate(case("R03"), self.policy)
        self.assertEqual(out["status"], "conflict")
        self.assertTrue(any(c["rule"] == "REQUESTED_LONG_RUN_CONFLICT" for c in out["conflicts"]))
        self.assertTrue(all(w["long_run_km"] <= w["weekly_km"] * self.policy["long_run_max_fraction"] + .001
                            for w in out["weeks"] if w["long_run_km"] is not None))

    def test_infeasible_days_reported(self):
        out = evaluate(case("R14"), self.policy)
        self.assertEqual(out["conflicts"][0]["rule"], "INSUFFICIENT_DAYS")

    def test_illness_never_generates_automated_progression(self):
        out = evaluate(case("R12"), self.policy)
        self.assertEqual(out["status"], "needs_human_review")
        self.assertEqual(out["weeks"], [])

    def test_easy_feedback_does_not_accelerate(self):
        source = case("R04")
        first = evaluate(source, self.policy)
        source["post_run_feedback"] = {"comparison": "easier"}
        second = evaluate(source, self.policy)
        self.assertEqual([w["weekly_km"] for w in first["weeks"]],
                         [w["weekly_km"] for w in second["weeks"]])
        self.assertEqual(second["unmodeled"], ["post_run_feedback"])

    def test_final_ceiling_and_unsupported_target(self):
        source = case("R05")
        source["configured_ceiling_km"] = 93
        source["target_km"] = 130
        out = evaluate(source, self.policy)
        self.assertEqual(out["feasibility"], "unsupported")
        self.assertTrue(all(w["weekly_km"] <= 93 for w in out["weeks"]))

    def test_missing_baseline_blocks_output(self):
        out = evaluate(case("R16"), self.policy)
        self.assertEqual(out["status"], "insufficient_baseline")
        self.assertEqual(out["weeks"], [])

    def test_consent_provenance(self):
        source = case("R04")
        source["origin"] = "real_opt_in"
        with self.assertRaises(ValueError):
            evaluate(source, self.policy)
        source["explicit_consent"] = True
        self.assertEqual(evaluate(source, self.policy)["source_origin"], "real_opt_in")

    def test_invalid_values_fail_closed(self):
        source = case("R04")
        source["history"][0]["km"] = float("nan")
        with self.assertRaises(ValueError):
            evaluate(source, self.policy)

    def test_every_registered_fixture_is_reported(self):
        report = evaluate_matrix(CASES, POLICIES)
        self.assertEqual(len(report["results"]), len(CASES) * len(POLICIES))
        self.assertEqual({r["scenario"] for r in report["results"]},
                         {r["id"] for r in CASES})

if __name__ == "__main__":
    unittest.main()
