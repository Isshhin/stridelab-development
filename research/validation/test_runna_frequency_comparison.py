"""Research-only synthetic tests of the capture analyzer, not Runna outputs."""
from __future__ import annotations
import csv
import math
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from runna_frequency_comparison import CASE_FIELDS, WEEK_FIELDS, analyze


def write_csv(path, fields, rows):
    with open(path, "w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


class MatchedFrequencyCaptureTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.cases = Path(self.temp.name) / "cases.csv"
        self.weeks = Path(self.temp.name) / "weeks.csv"
        self.rows = []
        for days in (5, 6, 7):
            for rep in ("A", "B"):
                case = f"F{days}{rep}"
                self.rows.append(dict(
                    case_id=case, runs_per_week=str(days), replicate=rep,
                    baseline_km="65", recent_long_km="18", ability="advanced", current_5k="27:00",
                    plan_type="5K race", target_date="2027-01-31", plan_weeks="12",
                    difficulty="balanced", volume="steady", strength_sessions="0",
                    availability_days="Tue Wed Thu Fri Sun" if days == 5 else "Tue Wed Thu Fri Sat Sun" if days == 6 else "Mon Tue Wed Thu Fri Sat Sun",
                    status="NOT_RUN", app_version="", generated_at="", source_ref="", warning="",
                ))
        write_csv(self.cases, CASE_FIELDS, self.rows)
        write_csv(self.weeks, WEEK_FIELDS, [])

    def populate(self):
        outputs = []
        for c in self.rows:
            days, cid = int(c["runs_per_week"]), c["case_id"]
            c.update(status="GENERATED", app_version="8.50.0", generated_at="2026-11-09T12:00:00+08:00", source_ref=f"capture/{cid}.png")
            for w in range(1, 13):
                phase = "DELOAD" if w in (4, 8) else "TAPER" if w == 11 else "RACE" if w == 12 else "BUILD"
                factor = {5: 1.05, 6: 1.03, 7: 1.02}[days]
                km = 65 * (factor ** (w - 1))
                if phase == "DELOAD": km *= .82
                if phase == "TAPER": km *= .65
                if phase == "RACE": km *= .5
                outputs.append(dict(case_id=cid, week_index=str(w), phase=phase, total_km=f"{km:.6f}",
                                    long_run_km=f"{km*.28:.6f}", quality_count="2", quality_types="tempo|intervals",
                                    easy_count=str(days - 3), run_days=str(days), recovery_days=str(7 - days),
                                    warning_state="", source_ref=f"capture/{cid}-week-{w}.png"))
        write_csv(self.cases, CASE_FIELDS, self.rows)
        write_csv(self.weeks, WEEK_FIELDS, outputs)
        return outputs

    def test_unobserved_cases_report_blocked(self):
        report = analyze(self.cases, self.weeks)
        self.assertEqual(report["status"], "BLOCKED_NO_CURRENT_APP_OUTPUT")
        self.assertEqual(report["generated_drafts"], 0)

    def test_synthetic_controlled_comparison_not_vendor_result(self):
        self.populate()
        report = analyze(self.cases, self.weeks)
        self.assertEqual(report["status"], "DESCRIPTIVE_ONLY_PENDING_CAPTURE_REVIEW")
        self.assertEqual(len(report["pair_details"]), 4)
        for pair in report["pair_details"].values():
            self.assertLess(pair["weekly_progression_delta_pp_mean"], 0)
            self.assertEqual([p["week"] for p in pair["matched_build_weeks"]], [2, 3, 6, 7, 10])

    def test_incomplete_week_not_treated_as_zero(self):
        rows = self.populate()
        rows.pop()
        write_csv(self.weeks, WEEK_FIELDS, rows)
        self.assertEqual(analyze(self.cases, self.weeks)["status"], "BLOCKED_INCOMPLETE_MATCHED_CAPTURE")

    def test_bad_evidence_reference_is_rejected(self):
        rows = self.populate()
        rows[0]["source_ref"] = ""
        write_csv(self.weeks, WEEK_FIELDS, rows)
        with self.assertRaisesRegex(ValueError, "no source capture"):
            analyze(self.cases, self.weeks)

    def test_frequency_baseline_mismatch_is_rejected(self):
        self.rows[0]["baseline_km"] = "60"
        write_csv(self.cases, CASE_FIELDS, self.rows)
        with self.assertRaisesRegex(ValueError, "baseline changed|mismatch"):
            analyze(self.cases, self.weeks)

    def test_impossible_long_run_is_rejected(self):
        rows = self.populate()
        rows[0]["long_run_km"] = "500"
        write_csv(self.weeks, WEEK_FIELDS, rows)
        with self.assertRaisesRegex(ValueError, "long run exceeds"):
            analyze(self.cases, self.weeks)

    def test_version_drift_blocks_comparison(self):
        self.populate()
        self.rows[0]["app_version"] = "8.49.0"
        write_csv(self.cases, CASE_FIELDS, self.rows)
        self.assertEqual(analyze(self.cases, self.weeks)["status"], "BLOCKED_APP_VERSION_MISMATCH")

    def test_actual_build_frequency_deviation_blocks_comparison(self):
        rows = self.populate()
        for row in rows:
            if row["case_id"] == "F6A" and row["week_index"] == "2":
                row["run_days"] = "5"
                row["recovery_days"] = "2"
        write_csv(self.weeks, WEEK_FIELDS, rows)
        report = analyze(self.cases, self.weeks)
        self.assertEqual(report["status"], "BLOCKED_FREQUENCY_NOT_REALIZED")
        self.assertEqual(report["reported_comparisons"], 0)
        self.assertEqual(report["frequency_deviations"][0]["case_id"], "F6A")

    def test_taper_day_reduction_does_not_fake_build_difference(self):
        rows = self.populate()
        for row in rows:
            if row["case_id"] == "F7B" and row["week_index"] == "11":
                row["run_days"] = "6"
                row["recovery_days"] = "1"
        write_csv(self.weeks, WEEK_FIELDS, rows)
        report = analyze(self.cases, self.weeks)
        self.assertEqual(report["status"], "DESCRIPTIVE_ONLY_PENDING_CAPTURE_REVIEW")
        self.assertEqual(report["plan_summaries"]["F7B"]["taper_weeks"], [11])

    def test_replicate_stability_exposes_draft_variability(self):
        rows = self.populate()
        for row in rows:
            if row["case_id"] == "F5B" and row["week_index"] == "3":
                row["total_km"] = str(float(row["total_km"]) + 1)
        write_csv(self.weeks, WEEK_FIELDS, rows)
        report = analyze(self.cases, self.weeks)
        self.assertEqual(report["status"], "DESCRIPTIVE_ONLY_PENDING_CAPTURE_REVIEW")
        self.assertEqual(report["replicate_stability"]["5"]["identical_weekly_total_count"], 11)
        self.assertEqual(report["replicate_stability"]["5"]["max_abs_weekly_total_difference_km"], 1.0)

    def test_transitions_exclude_post_deload_rebound(self):
        self.populate()
        report = analyze(self.cases, self.weeks)
        transitions = report["plan_summaries"]["F5A"]["transitions"]
        self.assertEqual(next(t for t in transitions if t["to_week"] == 5)["kind"], "NON_BUILD_TRANSITION")
        self.assertEqual(next(t for t in transitions if t["to_week"] == 6)["kind"], "BUILD_TO_BUILD")
        self.assertEqual(report["replicate_stability"]["7"]["identical_weekly_total_count"], 12)


if __name__ == "__main__":
    unittest.main()
