"""Synthetic admission/negative-control tests for prospective Garmin Run Coach."""
from __future__ import annotations

import csv
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from garmin_run_coach_prospective_gate import PRE_FIELDS, POST_FIELDS, audit


def put(path, fields, rows):
    with open(path, "w", newline="", encoding="utf-8") as out:
        writer = csv.DictWriter(out, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


class GarminRunCoachProspectiveTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.pre_path = Path(tmp.name) / "pre.csv"
        self.post_path = Path(tmp.name) / "post.csv"
        self.pre = []
        self.post = []
        self.save()

    def save(self):
        put(self.pre_path, PRE_FIELDS, self.pre)
        put(self.post_path, POST_FIELDS, self.post)

    def populate(self):
        before = {f: "" for f in PRE_FIELDS}
        before.update({
            "observation_id": "synthetic-001",
            "captured_at_local": "2026-11-04T16:00:00+08:00",
            "timezone": "Asia/Manila", "source_surface": "Garmin Connect",
            "plan_family": "GARMIN_RUN_COACH", "plan_goal": "5K",
            "plan_phase": "build", "target_event_date": "2027-01-31",
            "device_model": "synthetic Forerunner", "firmware": "synthetic-version",
            "garmin_connect_version": "synthetic-client",
            "target_mode": "HR", "scheduled_workout_date": "2026-11-04",
            "recommendation_type": "easy", "prescribed_duration_min": "48",
            "prescribed_distance_km": "8",
            "prescribed_hr_range_bpm": "130-145",
            "source_ref": "redacted/synthetic-pre.png",
        })
        after = {f: "" for f in POST_FIELDS}
        after.update({
            "observation_id": "synthetic-001",
            "actual_start_at_local": "2026-11-04T17:00:00+08:00",
            "actual_end_at_local": "2026-11-04T17:50:00+08:00",
            "workout_status": "COMPLETED", "actual_type": "easy",
            "actual_duration_min": "50", "actual_distance_km": "8.2",
            "original_prescription_completed": "true",
            "completion_source_ref": "redacted/synthetic-activity.png",
            "next_recommendation_captured_at_local": "2026-11-04T18:00:00+08:00",
            "next_plan_family": "GARMIN_RUN_COACH", "next_recommendation_type": "recovery",
            "next_prescribed_duration_min": "30",
            "next_recommendation_source_ref": "redacted/synthetic-next.png",
            "observed_plan_change": "type changed", "intervening_events_and_confounders": "unknown",
        })
        self.pre = [before]
        self.post = [after]
        self.save()

    def test_blank_templates_are_blocked(self):
        r = audit(self.pre_path, self.post_path)
        self.assertEqual(r["status"], "BLOCKED_NO_PROSPECTIVE_RUN_COACH_CAPTURE")
        self.assertEqual(r["paired"], 0)

    def test_valid_synthetic_pair_stays_descriptive(self):
        self.populate()
        r = audit(self.pre_path, self.post_path)
        self.assertEqual(r["status"], "DESCRIPTIVE_ONLY_PENDING_SOURCE_REVIEW")
        self.assertFalse(r["adaptive_claim_allowed"])
        self.assertAlmostEqual(r["pairs"][0]["distance_difference_km"], 0.2, places=5)

    def test_completed_activity_cannot_backfill_missing_pre(self):
        self.populate()
        self.pre = []
        self.save()
        self.assertEqual(audit(self.pre_path, self.post_path)["status"], "BLOCKED_NO_PRE_WORKOUT_CAPTURE")

    def test_pre_capture_without_post_is_unpaired(self):
        self.populate()
        self.post = []
        self.save()
        r = audit(self.pre_path, self.post_path)
        self.assertEqual(r["status"], "BLOCKED_UNPAIRED_PRE_WORKOUT_CAPTURE")

    def test_dsw_must_not_be_labeled_run_coach(self):
        self.populate()
        self.pre[0]["plan_family"] = "GARMIN_DSW"
        self.save()
        self.assertEqual(audit(self.pre_path, self.post_path)["status"], "BLOCKED_NOT_GARMIN_RUN_COACH")

    def test_next_dsw_does_not_count_as_run_coach_adaptation(self):
        self.populate()
        self.post[0]["next_plan_family"] = "GARMIN_DSW"
        self.save()
        self.assertEqual(audit(self.pre_path, self.post_path)["status"], "BLOCKED_NEXT_NOT_GARMIN_RUN_COACH")

    def test_pre_capture_after_workout_start_is_invalid(self):
        self.populate()
        self.pre[0]["captured_at_local"] = "2026-11-04T17:10:00+08:00"
        self.save()
        with self.assertRaisesRegex(ValueError, "pre-workout capture not before"):
            audit(self.pre_path, self.post_path)

    def test_naive_timestamp_cannot_pass_order_check(self):
        self.populate()
        self.pre[0]["captured_at_local"] = "2026-11-04T16:00:00"
        self.save()
        with self.assertRaisesRegex(ValueError, "missing explicit UTC offset"):
            audit(self.pre_path, self.post_path)

    def test_next_recommendation_preceding_workout_completion_is_invalid(self):
        self.populate()
        self.post[0]["next_recommendation_captured_at_local"] = "2026-11-04T17:05:00+08:00"
        self.save()
        with self.assertRaisesRegex(ValueError, "next recommendation not captured after"):
            audit(self.pre_path, self.post_path)

    def test_missing_next_recommendation_cannot_claim_adaptation(self):
        self.populate()
        for key in (
            "next_recommendation_captured_at_local", "next_plan_family",
            "next_recommendation_type", "next_recommendation_source_ref",
            "next_prescribed_duration_min",
        ):
            self.post[0][key] = ""
        self.save()
        r = audit(self.pre_path, self.post_path)
        self.assertEqual(r["status"], "COMPLETION_ONLY_PENDING_NEXT_RECOMMENDATION")
        self.assertFalse(r["adaptive_claim_allowed"])

    def test_duplicate_pre_capture_identity_is_invalid(self):
        self.populate()
        self.pre.append(dict(self.pre[0]))
        self.save()
        with self.assertRaisesRegex(ValueError, "missing/duplicate pre"):
            audit(self.pre_path, self.post_path)

    def test_skipped_workout_has_no_completed_distance(self):
        self.populate()
        row = self.post[0]
        row["workout_status"] = "SKIPPED"
        row["actual_start_at_local"] = ""
        row["actual_end_at_local"] = ""
        row["actual_duration_min"] = ""
        row["actual_distance_km"] = ""
        self.save()
        r = audit(self.pre_path, self.post_path)
        self.assertEqual(r["status"], "DESCRIPTIVE_ONLY_PENDING_SOURCE_REVIEW")
        self.assertIsNone(r["pairs"][0]["distance_difference_km"])


if __name__ == "__main__":
    unittest.main()
