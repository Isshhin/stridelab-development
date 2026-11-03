"""Batch 8E independent workout-to-week proposal contract tests."""
from copy import deepcopy
from pathlib import Path
import tempfile
import unittest

from week_change_contract import ProposalBlocked, draft_revision
from sqlite_ledger import ResearchPlanStore


def plan():
    return {"version": 1, "athlete": "synthetic-001", "sessions": [
        {"id": "quality", "discipline": "running", "day": "Tue", "duration_m": 60,
         "planned_steps": [{"kind": "easy", "km": 3, "seconds": 1200},
                           {"kind": "hard", "km": 4, "seconds": 1500}]},
        {"id": "easy", "discipline": "running", "day": "Wed", "duration_m": 45,
         "planned_steps": [{"kind": "easy", "km": 6, "seconds": 2500}]},
        {"id": "gym-lower", "discipline": "strength", "day": "Thu", "duration_m": 40,
         "planned_sets": 3},
        {"id": "long", "discipline": "running", "day": "Sun", "duration_m": 90,
         "planned_steps": [{"kind": "easy", "km": 12, "seconds": 5000}]},
    ]}


def limits():
    return {
        "max_sessions_per_day": 2,
        "available_minutes_by_day": {
            "Mon": 0, "Tue": 120, "Wed": 120, "Thu": 120,
            "Fri": 90, "Sat": 0, "Sun": 120,
        },
        "locked_days": ["Mon", "Sat"],
        "verified_weekly_running_cap_km": 30,
    }


def request(kind="MOVE_SESSION", **other):
    return {
        "kind": kind, "session_id": "quality",
        "target_day": "Fri",
        "expected_version": 1,
        "evidence_status": "athlete_confirmed",
        "evidence_refs": ["manual:user-confirmation-001"],
        "source": "athlete_request",
        "reason": "Athlete explicitly requested a move",
        **other,
    }


class WorkoutProposalTests(unittest.TestCase):
    def test_move_builds_pending_plan_without_changing_dose_or_original(self):
        before = plan()
        snapshot = deepcopy(before)
        result = draft_revision(before, request(), limits())
        self.assertEqual(snapshot, before)
        self.assertFalse(result["applied"])
        self.assertTrue(result["requires_explicit_athlete_approval"])
        self.assertNotEqual(result["before_sha256"], result["candidate_sha256"])
        self.assertEqual("Fri", result["candidate_plan"]["sessions"][0]["day"])
        for dimension in ("running_km", "run_hard_s", "run_easy_s",
                          "run_recovery_s", "strength_sessions", "sessions"):
            self.assertEqual(result["before_metrics"][dimension],
                             result["after_metrics"][dimension])

    def test_missing_target_day_does_not_turn_h1_move_into_executable_edit(self):
        req = request(source="research_policy")
        req.pop("target_day")
        with self.assertRaisesRegex(ProposalBlocked, "EXPLICIT_TARGET_DAY_REQUIRED"):
            draft_revision(plan(), req, limits())

    def test_verified_source_evidence_is_required(self):
        with self.assertRaisesRegex(ProposalBlocked, "EVIDENCE_UNVERIFIED"):
            draft_revision(plan(), request(evidence_status="unknown"), limits())
        with self.assertRaisesRegex(ProposalBlocked, "EVIDENCE_REFERENCE_REQUIRED"):
            draft_revision(plan(), request(evidence_refs=[]), limits())

    def test_stale_version_is_rejected(self):
        with self.assertRaisesRegex(ProposalBlocked, "STALE_PLAN_VERSION"):
            draft_revision(plan(), request(expected_version=2), limits())

    def test_locked_day_never_assigned(self):
        with self.assertRaisesRegex(ProposalBlocked, "LOCKED_DAY_SCHEDULED"):
            draft_revision(plan(), request(target_day="Mon"), limits())

    def test_time_budget_catches_two_sessions_exceeding_daily_minutes(self):
        with self.assertRaisesRegex(ProposalBlocked, "DAY_MINUTES_EXCEEDED"):
            draft_revision(plan(), request(session_id="long", target_day="Thu"), limits())

    def test_daily_session_count_limit_applies(self):
        restrictions = limits()
        restrictions["max_sessions_per_day"] = 1
        with self.assertRaisesRegex(ProposalBlocked, "DAILY_SESSION_LIMIT"):
            draft_revision(plan(), request(session_id="easy", target_day="Tue"), restrictions)

    def test_weekly_cap_never_overridden_by_policy(self):
        restrictions = limits()
        restrictions["verified_weekly_running_cap_km"] = 20
        with self.assertRaisesRegex(ProposalBlocked, "WEEKLY_RUNNING_CAP"):
            draft_revision(plan(), request(), restrictions)

    def test_quality_step_reduction_is_an_explicit_pending_proposal(self):
        req = request(
            kind="REPLACE_RUN_STEPS", target_steps=[
                {"kind": "easy", "km": 3, "seconds": 1200},
                {"kind": "hard", "km": 3, "seconds": 1200},
            ], target_duration_m=50,
            reason="Explicit athlete-requested reduction of hard steps",
        )
        result = draft_revision(plan(), req, limits())
        self.assertEqual(1200, result["after_metrics"]["run_hard_s"])
        self.assertEqual(1500, result["before_metrics"]["run_hard_s"])
        self.assertEqual(24, result["after_metrics"]["running_km"])
        self.assertTrue(result["requires_explicit_athlete_approval"])
        self.assertFalse(result["applied"])

    def test_hard_step_increase_is_not_an_implicit_progression(self):
        req = request(kind="REPLACE_RUN_STEPS", target_steps=[
            {"kind": "easy", "km": 3, "seconds": 1200},
            {"kind": "hard", "km": 4, "seconds": 1800},
        ], target_duration_m=60)
        with self.assertRaisesRegex(ProposalBlocked, "AUTOMATIC_DOSE_INCREASE_NOT_AUTHORIZED"):
            draft_revision(plan(), req, limits())

    def test_session_km_increase_is_not_an_implicit_progression(self):
        req = request(kind="REPLACE_RUN_STEPS", target_steps=[
            {"kind": "easy", "km": 4, "seconds": 1200},
            {"kind": "hard", "km": 4, "seconds": 1500},
        ], target_duration_m=60)
        with self.assertRaisesRegex(ProposalBlocked, "AUTOMATIC_DOSE_INCREASE_NOT_AUTHORIZED"):
            draft_revision(plan(), req, limits())

    def test_invalid_duration_shorter_than_workout_steps_is_rejected(self):
        req = request(kind="REPLACE_RUN_STEPS", target_steps=[
            {"kind": "easy", "km": 3, "seconds": 1200},
            {"kind": "hard", "km": 3, "seconds": 1200},
        ], target_duration_m=15)
        with self.assertRaisesRegex(ProposalBlocked, "TARGET_DURATION_SHORTER_THAN_STEPS"):
            draft_revision(plan(), req, limits())

    def test_strength_cannot_be_replaced_with_running_steps(self):
        req = request(kind="REPLACE_RUN_STEPS", session_id="gym-lower", target_steps=[
            {"kind": "hard", "km": 1, "seconds": 300},
        ], target_duration_m=5)
        with self.assertRaisesRegex(ProposalBlocked, "RUNNING_ONLY_STEP_REPLACEMENT"):
            draft_revision(plan(), req, limits())

    def test_nothing_can_be_committed_without_separate_explicit_decision(self):
        original = plan()
        result = draft_revision(original, request(), limits())
        with tempfile.TemporaryDirectory() as folder:
            db = ResearchPlanStore(Path(folder) / "proposal.sqlite3")
            try:
                db.initialize(original)
                db.record_activity("run-1", {"status": "partial", "hard_s": 850})
                version, initial = db.current()
                db.propose("p8e-1", result["candidate_plan"],
                           result["explanation"], "research-proposal-contract",
                           expected_version=version)
                self.assertEqual((1, initial), db.current())
                db.decide("p8e-1", "approve", expected_version=version)
                self.assertEqual(result["candidate_plan"], db.current()[1])
                self.assertEqual(2, db.current()[0])
                db.rollback("p8e-1", expected_version=2)
                self.assertEqual(original, db.current()[1])
                self.assertEqual(3, db.current()[0])
                self.assertEqual([("run-1", {"status": "partial", "hard_s": 850})],
                                 db.activity_snapshot())
            finally:
                db.close()

    def test_no_op_or_unsupported_change_is_rejected(self):
        with self.assertRaisesRegex(ProposalBlocked, "NO_OP_CHANGE"):
            draft_revision(plan(), request(target_day="Tue"), limits())
        with self.assertRaisesRegex(ProposalBlocked, "UNSUPPORTED_CHANGE_KIND"):
            draft_revision(plan(), request(kind="AUTO_PROGRESS"), limits())


if __name__ == "__main__":
    unittest.main()
