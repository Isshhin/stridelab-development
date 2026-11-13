"""Independent checks of eight-week synthetic lifecycle and policy boundaries."""
import tempfile
from pathlib import Path
import unittest

from multiweek_runner import (
    PROFILE_NAMES, WEEKS, evaluate_one, generate_week_events,
    plan_for, run_suite, weekly_policy_state,
)
from multiweek_reconciliation import reconcile_week


class MultiweekLifecycleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = run_suite()

    def test_all_profiles_and_32_weeks_report_success(self):
        self.assertEqual(4, self.report["counts"]["PASS"])
        self.assertEqual(0, self.report["counts"]["FAIL"])
        self.assertEqual(32, self.report["total_week_scenarios"])
        self.assertEqual(set(PROFILE_NAMES),
                         {r["profile"] for r in self.report["results"]})

    def test_all_explicit_approvals_rejections_rollbacks_are_audited(self):
        for profile in self.report["results"]:
            self.assertEqual(WEEKS, len(profile["weeks"]))
            self.assertEqual(8, profile["approved_revisions"])
            self.assertEqual(1, profile["rejected_revisions"])
            self.assertEqual(1, profile["rollbacks"])
            self.assertEqual(10, profile["audit_entries"])
            self.assertEqual("ok", profile["sqlite_integrity"])

    def test_all_policy_proposals_remain_unapplied_without_user_approval(self):
        for profile in self.report["results"]:
            for week in profile["weeks"]:
                self.assertEqual("PASS", profile["status"])
                self.assertIsInstance(week["h1_action"], str)
                self.assertEqual(
                    (60, 64, 68, 52, 72, 58, 65, 45)[week["week"] - 1],
                    week["reconciliation"]["planned_running_km"])
                self.assertGreater(week["sources_replayed"], 0)
                self.assertEqual(week["source_events_inserted"],
                                 week["sources_replayed"])
                self.assertEqual(64, len(week["plan_sha256"]))
                self.assertEqual(64, len(week["observed_snapshot_sha256"]))

    def test_interruption_retains_skipped_partial_unplanned_and_missing(self):
        p = next(x for x in self.report["results"] if x["profile"] == "INTERRUPTED")
        self.assertGreaterEqual(p["summary"]["missing_weeks"], 1)
        self.assertGreaterEqual(p["summary"]["partial_weeks"], 1)
        self.assertGreaterEqual(p["summary"]["skipped_weeks"], 1)
        self.assertGreaterEqual(p["summary"]["unplanned_weeks"], 1)
        self.assertFalse(p["weeks"][3]["reconciliation"]["total_actual_complete"])

    def test_source_conflict_and_unlinked_cases_are_not_counted_as_achieved(self):
        p = next(x for x in self.report["results"] if x["profile"] == "SENSOR_GAPS")
        self.assertEqual(1, p["summary"]["conflict_weeks"])
        self.assertEqual(1, p["summary"]["unlinked_weeks"])
        self.assertGreaterEqual(p["summary"]["missing_weeks"], 1)
        conflicted = p["weeks"][3]["reconciliation"]
        self.assertTrue(conflicted["conflicts"])
        self.assertFalse(conflicted["total_actual_complete"])

    def test_stable_profile_all_weeks_have_complete_confirmed_sources(self):
        p = next(x for x in self.report["results"] if x["profile"] == "STEADY")
        self.assertEqual(0, p["summary"]["missing_weeks"])
        self.assertEqual(0, p["summary"]["conflict_weeks"])
        self.assertEqual(0, p["summary"]["unlinked_weeks"])
        self.assertTrue(all(w["reconciliation"]["total_actual_complete"]
                            for w in p["weeks"]))

    def test_week_plan_sessions_are_unique_and_run_distance_sums_match(self):
        for profile in PROFILE_NAMES:
            for week in range(1, WEEKS + 1):
                plan = plan_for(profile, week)
                self.assertEqual(7, len(plan["sessions"]))
                self.assertEqual(7, len({s["id"] for s in plan["sessions"]}))
                self.assertAlmostEqual(plan["planned_km"],
                                       sum(s["planned_km"] for s in plan["sessions"]), 5)

    def test_work_step_dose_preserved_separately_from_session_distance(self):
        p = plan_for("STEADY", 1)
        events = generate_week_events("STEADY", 1, p)
        snapshots = [(e["source"] + "::" + e["external_id"], e) for e in events]
        rec = reconcile_week(p["sessions"], snapshots, 1)
        state = weekly_policy_state("STEADY", 1, p, rec)
        self.assertEqual(2, len(state["session"]["actual_steps"]))
        actual = state["session"]["actual_steps"]
        self.assertEqual(1800, actual[1]["seconds"])
        self.assertEqual("hard", actual[1]["kind"])
        self.assertEqual("easy", actual[0]["kind"])
        self.assertLess(actual[1]["km"], p["sessions"][0]["planned_km"])
        self.assertAlmostEqual(sum(x["km"] for x in actual),
                               p["sessions"][0]["planned_km"], 6)

    def test_multiweek_runner_deterministic_excluding_timestamps(self):
        other = run_suite()
        primary = self.report
        self.assertEqual(primary["counts"], other["counts"])
        self.assertEqual(primary["total_week_scenarios"],
                         other["total_week_scenarios"])
        for a, b in zip(primary["results"], other["results"]):
            self.assertEqual(a["profile"], b["profile"])
            self.assertEqual(a["weeks"], b["weeks"])
            self.assertEqual(a["source_records"], b["source_records"])


if __name__ == "__main__":
    unittest.main()
