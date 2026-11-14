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

    def test_every_nonrecovery_long_run_has_a_binding_rule(self):
        # Trace completeness: the configured target share often binds, not just the step cap.
        for specimen in CASES:
            out = evaluate(specimen, self.policy)
            for week in out["weeks"]:
                if week["long_run_km"] is None or "RECOVERY_WEEK" in week["rule_ids"]:
                    continue
                reasons = {"LONG_RUN_STEP_BINDS", "LONG_RUN_TARGET_SHARE_BINDS", "LONG_RUN_MAX_SHARE_BINDS"}
                self.assertTrue(reasons.intersection(week["rule_ids"]), (specimen["id"], week))

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
        self.assertEqual(out["status"], "conflict")
        self.assertEqual(len(out["weeks"]), source["weeks"])
        self.assertTrue(any(c["rule"] == "TARGET_ABOVE_CONFIGURED_CEILING" for c in out["conflicts"]))
        self.assertIn("TARGET_ABOVE_CONFIGURED_CEILING", [t["rule"] for t in out["decision_trace"]])
        self.assertTrue(all(w["weekly_km"] <= 93 for w in out["weeks"]))

    def test_fractional_ceiling_never_rounds_above_configured_maximum(self):
        source = case("R05")
        source["configured_ceiling_km"] = 93.0006
        source["target_km"] = 130
        out = evaluate(source, self.policy)
        self.assertEqual(out["status"], "conflict")
        self.assertEqual(len(out["weeks"]), source["weeks"])
        self.assertTrue(all(w["weekly_km"] <= 93.0006 for w in out["weeks"]))
        self.assertTrue(any("FINAL_ROUNDING_CEILING" in w["rule_ids"] for w in out["weeks"]))

    def test_fractional_long_run_share_never_rounds_above_cap(self):
        source = case("R01")
        source["history"] = [
            {"complete": True, "km": 1.02, "long_km": 0.3},
            {"complete": True, "km": 1.05, "long_km": 0.3},
            {"complete": True, "km": 1.01, "long_km": 0.3},
            {"complete": True, "km": 1.05, "long_km": 0.3},
        ]
        out = evaluate(source, self.policy)
        for w in out["weeks"]:
            self.assertLessEqual(w["long_run_km"], w["weekly_km"] * self.policy["long_run_max_fraction"])

    def test_target_weekly_mileage_is_never_silently_exceeded(self):
        source = case("R04")
        for policy in POLICIES:
            out = evaluate(source, policy)
            self.assertEqual(len(out["weeks"]), source["weeks"])
            self.assertTrue(all(w["weekly_km"] <= source["target_km"] for w in out["weeks"]))
            if policy["name"] == "relative_cap_comparator":
                self.assertTrue(any("REQUESTED_TARGET_MILEAGE_BINDS" in w["rule_ids"] for w in out["weeks"]))

    def test_goal_below_recorded_baseline_requires_explicit_decrease_policy(self):
        for policy in POLICIES:
            out = evaluate(case("R17"), policy)
            self.assertEqual(out["status"], "conflict")
            self.assertEqual(out["weeks"], [])
            self.assertEqual(out["conflicts"][0]["rule"], "TARGET_BELOW_BASELINE_UNSUPPORTED")

    def test_fractional_target_respects_precision_without_overshoot(self):
        source = case("R05")
        source["target_km"] = 93.0006
        out = evaluate(source, self.policy)
        self.assertTrue(all(w["weekly_km"] <= source["target_km"] for w in out["weeks"]))
        self.assertIn(out["feasibility"], ("later", "within_window"))

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

    def test_all_emitted_build_weeks_respect_configured_growth(self):
        for source in CASES:
            for policy in POLICIES:
                out = evaluate(source, policy)
                factor = policy["low_confidence_multiplier"] if out["confidence"] == "low" else 1.0
                last_build = None
                for week in out["weeks"]:
                    if last_build is not None and "RECOVERY_WEEK" not in week["rule_ids"]:
                        limit = last_build + min(last_build * policy["growth_fraction"] * factor,
                                                 policy["growth_absolute_km"] * factor)
                        self.assertLessEqual(week["weekly_km"], limit + 0.0005,
                                             (source["id"], policy["name"], week))
                    if "RECOVERY_WEEK" not in week["rule_ids"]:
                        last_build = week["weekly_km"]

if __name__ == "__main__":
    unittest.main()
