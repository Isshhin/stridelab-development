"""Synthetic checks for E007 session capture integrity; not vendor observations."""
from __future__ import annotations

import csv
import sys
import tempfile
import unittest
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from runna_frequency_comparison import CASE_FIELDS, WEEK_FIELDS
from runna_session_integrity import SESSION_FIELDS, audit


def save(path, fields, rows):
    with open(path, "w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


class RunnaSessionIntegrityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        p = Path(self.tmp.name)
        self.case_path = p / "cases.csv"
        self.week_path = p / "weeks.csv"
        self.session_path = p / "sessions.csv"
        self.cases = []
        for runs in (5, 6, 7):
            for rep in ("A", "B"):
                cid = f"F{runs}{rep}"
                self.cases.append(dict(
                    case_id=cid, runs_per_week=str(runs), replicate=rep,
                    baseline_km="65", recent_long_km="18", ability="advanced",
                    current_5k="27:00", plan_type="5K race", target_date="2027-01-31",
                    plan_weeks="12", difficulty="balanced", volume="steady",
                    strength_sessions="0", availability_days="", status="NOT_RUN",
                    app_version="", generated_at="", source_ref="", warning="",
                ))
        save(self.case_path, CASE_FIELDS, self.cases)
        save(self.week_path, WEEK_FIELDS, [])
        save(self.session_path, SESSION_FIELDS, [])

    def create_full_capture(self):
        weeks, sessions = [], []
        for c in self.cases:
            cid = c["case_id"]
            runs = int(c["runs_per_week"])
            c.update(status="GENERATED", app_version="8.50.0",
                     generated_at="2026-11-09T12:00:00+08:00",
                     source_ref=f"authorized-redacted/{cid}.png")
            indices = {5: (1, 2, 3, 4, 6), 6: (1, 2, 3, 4, 5, 6), 7: tuple(range(7))}[runs]
            for week in range(1, 13):
                km = 65.0 + week * 0.5
                phase = "DELOAD" if week in (4, 8) else "TAPER" if week == 11 else "RACE" if week == 12 else "BUILD"
                weeks.append(dict(case_id=cid, week_index=str(week), phase=phase,
                                  total_km=str(km), long_run_km="18",
                                  quality_count="2", quality_types="tempo|interval",
                                  easy_count=str(runs - 3), run_days=str(runs),
                                  recovery_days=str(7 - runs), warning_state="",
                                  source_ref=f"authorized-redacted/{cid}-week-{week}.png"))
                monday = date(2026, 11, 9) + timedelta(weeks=week - 1)
                for idx, day in enumerate(indices, 1):
                    scheduled = monday + timedelta(days=day)
                    distance = 18 if day == 6 else (km - 18) / (runs - 1)
                    sessions.append(dict(
                        case_id=cid, week_index=str(week),
                        scheduled_date=scheduled.isoformat(),
                        day_of_week=("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[day],
                        session_index=str(idx),
                        session_category="long" if day == 6 else "easy",
                        prescribed_total_km=f"{distance:.6f}",
                        prescribed_hard_work_minutes="", is_long_run=str(day == 6).lower(),
                        workout_step_detail_ref="", source_ref=f"authorized-redacted/{cid}-w{week}-s{idx}.png", notes="",
                    ))
        save(self.case_path, CASE_FIELDS, self.cases)
        save(self.week_path, WEEK_FIELDS, weeks)
        save(self.session_path, SESSION_FIELDS, sessions)
        return weeks, sessions

    def test_empty_template_remains_blocked(self):
        r = audit(self.case_path, self.week_path, self.session_path)
        self.assertEqual(r["status"], "BLOCKED_NO_SESSION_OUTPUT")
        self.assertEqual(r["observed_vendor_plans"], 0)

    def test_full_synthetic_reconciliation_is_descriptive_only(self):
        self.create_full_capture()
        r = audit(self.case_path, self.week_path, self.session_path)
        self.assertEqual(r["status"], "DESCRIPTIVE_ONLY_PENDING_CAPTURE_REVIEW")
        self.assertEqual(r["complete_case_weeks"], 72)
        self.assertEqual(len(r["per_case_weeks"]["F5A"]), 12)
        self.assertEqual(r["per_case_weeks"]["F7B"][0]["run_days"], 7)
        self.assertEqual(r["per_case_weeks"]["F5A"][0]["hard_minutes_available_for_all_sessions"], False)

    def test_missing_session_is_not_interpreted_as_zero(self):
        weeks, sessions = self.create_full_capture()
        sessions.pop(0)
        save(self.session_path, SESSION_FIELDS, sessions)
        r = audit(self.case_path, self.week_path, self.session_path)
        self.assertEqual(r["status"], "BLOCKED_SESSION_RECONCILIATION")
        self.assertIn("RUN_DAY_COUNT_MISMATCH", {x["code"] for x in r["issues"]})

    def test_wrong_weekly_mileage_blocks(self):
        weeks, sessions = self.create_full_capture()
        weeks[0]["total_km"] = "100"
        save(self.week_path, WEEK_FIELDS, weeks)
        r = audit(self.case_path, self.week_path, self.session_path)
        self.assertIn("WEEKLY_DISTANCE_MISMATCH", {x["code"] for x in r["issues"]})

    def test_long_run_identity_and_distance_blocks(self):
        _, sessions = self.create_full_capture()
        row = next(s for s in sessions if s["case_id"] == "F5A" and s["week_index"] == "1" and s["is_long_run"] == "true")
        row["is_long_run"] = "false"
        save(self.session_path, SESSION_FIELDS, sessions)
        r = audit(self.case_path, self.week_path, self.session_path)
        self.assertIn("LONG_RUN_COUNT_MISMATCH", {x["code"] for x in r["issues"]})

    def test_wrong_calendar_day_rejected(self):
        _, sessions = self.create_full_capture()
        sessions[0]["day_of_week"] = "Mon"
        save(self.session_path, SESSION_FIELDS, sessions)
        with self.assertRaisesRegex(ValueError, "weekday mismatch"):
            audit(self.case_path, self.week_path, self.session_path)

    def test_duplicate_session_identity_rejected(self):
        _, sessions = self.create_full_capture()
        sessions.append(dict(sessions[0]))
        save(self.session_path, SESSION_FIELDS, sessions)
        with self.assertRaisesRegex(ValueError, "duplicate session identity"):
            audit(self.case_path, self.week_path, self.session_path)

    def test_two_sessions_on_one_day_are_not_two_run_days(self):
        _, sessions = self.create_full_capture()
        target = next(s for s in sessions if s["case_id"] == "F5A" and s["week_index"] == "1" and s["session_index"] == "1")
        original = float(target["prescribed_total_km"])
        target["prescribed_total_km"] = f"{original / 2:.6f}"
        extra = dict(target)
        extra["session_index"] = "6"
        extra["source_ref"] = "authorized-redacted/F5A-w1-s6.png"
        sessions.append(extra)
        save(self.session_path, SESSION_FIELDS, sessions)
        r = audit(self.case_path, self.week_path, self.session_path)
        self.assertEqual(r["status"], "DESCRIPTIVE_ONLY_PENDING_CAPTURE_REVIEW")
        self.assertEqual(r["per_case_weeks"]["F5A"][0]["session_count"], 6)
        self.assertEqual(r["per_case_weeks"]["F5A"][0]["run_days"], 5)

    def test_weekly_observations_must_be_complete(self):
        weeks, _ = self.create_full_capture()
        weeks.pop()
        save(self.week_path, WEEK_FIELDS, weeks)
        self.assertEqual(
            audit(self.case_path, self.week_path, self.session_path)["status"],
            "BLOCKED_INCOMPLETE_WEEKLY_CAPTURE",
        )


if __name__ == "__main__":
    unittest.main()
