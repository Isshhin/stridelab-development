"""Adversarial synthetic-only tests for P2-A DEV-0 Batches 1-3."""
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import unittest

from p2a_state import interpret

NOW = datetime(2026, 11, 8, 12, tzinfo=timezone.utc)


def at(days=0, hours=0):
    return (NOW + timedelta(days=days, hours=hours)).isoformat()


def row(event_id, kind, value, days=-1, *, available_days=None, **patch):
    r = {
        "event_id": event_id, "athlete_id": "synthetic-A",
        "kind": kind, "observed_at": at(days=days),
        "available_at": at(days=days if available_days is None else available_days),
        "source_id": "invented-" + event_id, "protocol_id": "synthetic-v1",
        "status": "measured", "quality": "good", "value": value,
    }
    r.update(patch)
    return r


def frame(*rows, **patch):
    s = {
        "athlete_id": "synthetic-A", "as_of": at(),
        "observations": list(rows),
        "registered_benchmarks": {"course-A": {
            "protocol_id": "synthetic-v1",
            "device_epoch": "watch-1",
            "minimum_detectable_change_mps": 0.10,
        }},
        "registered_sessions": {"threshold-A": {
            "protocol_id": "synthetic-v1", "device_epoch": "watch-1",
        }},
        "fatigue_freshness_hours": 24,
    }
    s.update(patch)
    return s


def bench(n, speed, day, **patch):
    return row("B" + str(n), "benchmark", speed, days=day,
               comparison_group="course-A", device_epoch="watch-1", **patch)


def quality(n, outcome, day, **patch):
    return row("Q" + str(n), "quality_session", outcome, days=day,
               comparison_group="threshold-A", device_epoch="watch-1", **patch)


class AthleteStateSyntheticTests(unittest.TestCase):
    def test_01_empty_is_unknown_not_zero(self):
        v = interpret(frame())
        self.assertEqual("unknown", v["fitness_trend"]["state"])
        self.assertEqual("unknown", v["recent_fatigue"]["state"])
        self.assertEqual("unknown", v["workout_tolerance"]["state"])
        self.assertIsNone(v["predicted_performance"])
        self.assertIsNone(v["proposed_workout_change"])

    def test_02_observed_benchmark_rising_not_fitness_prediction(self):
        v = interpret(frame(bench(1, 3.0, -6), bench(2, 3.1, -4),
                            bench(3, 3.25, -2)))
        self.assertEqual("observed_rising", v["fitness_trend"]["state"])
        self.assertEqual("DESCRIPTIVE_ONLY_NOT_FITNESS",
                         v["fitness_trend"]["reason"])
        self.assertEqual("NOT_VALIDATED_NO_MODEL", v["prediction_status"])

    def test_03_observed_falling(self):
        v = interpret(frame(bench(1, 3.3, -6), bench(2, 3.1, -4),
                            bench(3, 3.0, -2)))
        self.assertEqual("observed_falling", v["fitness_trend"]["state"])

    def test_04_within_error_not_claimed_stable(self):
        v = interpret(frame(bench(1, 3.0, -6), bench(2, 3.02, -4),
                            bench(3, 3.03, -2)))
        self.assertEqual("within_registered_error", v["fitness_trend"]["state"])

    def test_05_nonmonotonic_is_mixed(self):
        v = interpret(frame(bench(1, 3.0, -6), bench(2, 3.5, -4),
                            bench(3, 3.2, -2)))
        self.assertEqual("mixed", v["fitness_trend"]["state"])

    def test_06_two_benchmarks_insufficient(self):
        v = interpret(frame(bench(1, 3.0, -6), bench(2, 3.4, -2)))
        self.assertEqual("unknown", v["fitness_trend"]["state"])

    def test_07_new_device_epoch_breaks_trend(self):
        latest = bench(3, 3.5, -1)
        latest["device_epoch"] = "watch-2"
        v = interpret(frame(bench(1, 3.0, -6), bench(2, 3.1, -4), latest))
        self.assertEqual("unknown", v["fitness_trend"]["state"])

    def test_08_missing_detectability_bound(self):
        v = interpret(frame(bench(1, 3.0, -6), bench(2, 3.1, -4),
                            bench(3, 3.25, -2), registered_benchmarks={}))
        self.assertEqual("unknown", v["fitness_trend"]["state"])

    def test_09_bad_metric_rejected(self):
        v = interpret(frame(bench(1, 3.0, -6), bench(2, float("nan"), -4),
                            bench(3, 3.25, -2)))
        self.assertEqual("unknown", v["fitness_trend"]["state"])

    def test_10_high_fatigue_not_overridden_by_hrv(self):
        v = interpret(frame(row("F", "fatigue_report", "high"),
                            row("H", "hrv", 80, assessment="favorable")))
        self.assertEqual("reported_high", v["recent_fatigue"]["state"])
        self.assertIn("FAVORABLE_WEARABLE_DOES_NOT_OVERRIDE_SELF_REPORT",
                      v["evidence_quality"]["warnings"])

    def test_11_stale_fatigue_unknown(self):
        v = interpret(frame(row("F", "fatigue_report", "high", days=-3)))
        self.assertEqual("unknown", v["recent_fatigue"]["state"])
        self.assertEqual("STALE_FATIGUE_REPORT", v["recent_fatigue"]["reason"])

    def test_12_same_time_conflicting_reports_unknown(self):
        v = interpret(frame(row("F1", "fatigue_report", "high"),
                            row("F2", "fatigue_report", "low")))
        self.assertEqual("CONCURRENT_REPORT_CONFLICT",
                         v["recent_fatigue"]["reason"])

    def test_13_quality_session_difficulty(self):
        v = interpret(frame(quality(1, "struggled", -6),
                            quality(2, "struggled", -3)))
        self.assertEqual("observed_difficulty",
                         v["workout_tolerance"]["state"])
        self.assertEqual("unknown", v["fitness_trend"]["state"])

    def test_14_quality_mixed_doesnt_erase_benchmark(self):
        v = interpret(frame(bench(1, 3.0, -6), bench(2, 3.1, -4),
                            bench(3, 3.25, -2), quality(1, "met_intent", -5),
                            quality(2, "struggled", -1)))
        self.assertEqual("mixed", v["workout_tolerance"]["state"])
        self.assertEqual("observed_rising", v["fitness_trend"]["state"])

    def test_15_future_outcome_excluded(self):
        v = interpret(frame(quality(1, "met_intent", -2),
                            quality(2, "met_intent", +1)))
        self.assertEqual("unknown", v["workout_tolerance"]["state"])
        self.assertEqual(1, v["evidence_quality"]["excluded_by_reason"][
            "UNAVAILABLE_AT_DECISION"])

    def test_16_late_upload_excluded(self):
        v = interpret(frame(row("F", "fatigue_report", "high",
                                days=-1, available_days=1)))
        self.assertEqual("unknown", v["recent_fatigue"]["state"])

    def test_17_exact_duplicate_deduplicated(self):
        q = quality(1, "struggled", -2)
        v = interpret(frame(q, deepcopy(q), quality(2, "struggled", -1)))
        self.assertEqual(1, v["evidence_quality"]["exact_duplicate_inputs"])
        self.assertEqual(2, v["evidence_quality"]["unique_inputs"])

    def test_18_conflicting_duplicate_fails_closed(self):
        q = quality(1, "met_intent", -2)
        different = deepcopy(q)
        different["value"] = "struggled"
        with self.assertRaisesRegex(ValueError, "CONFLICTING_EVENT_ID"):
            interpret(frame(q, different))

    def test_19_foreign_athlete_fails_closed(self):
        q = quality(1, "met_intent", -2)
        q["athlete_id"] = "synthetic-B"
        with self.assertRaisesRegex(ValueError, "CROSS_ATHLETE"):
            interpret(frame(q))

    def test_20_no_upload_is_not_skipped(self):
        q = quality(1, None, -1, status="missing_upload")
        v = interpret(frame(q))
        self.assertEqual("unknown", v["workout_tolerance"]["state"])
        self.assertEqual(1, v["evidence_quality"]["excluded_by_reason"][
            "NOT_MEASURED"])

    def test_21_unverified_quality_excluded(self):
        q = quality(1, "struggled", -2, quality="suspect")
        v = interpret(frame(q, quality(2, "struggled", -1)))
        self.assertEqual("unknown", v["workout_tolerance"]["state"])

    def test_22_available_before_observed_not_used(self):
        f = row("F", "fatigue_report", "high", days=-1, available_days=-2)
        v = interpret(frame(f))
        self.assertEqual("unknown", v["recent_fatigue"]["state"])

    def test_23_naive_time_rejected(self):
        f = row("F", "fatigue_report", "high", days=-1)
        f["observed_at"] = "2026-11-07T12:00:00"
        with self.assertRaisesRegex(ValueError, "INVALID_OFFSET_TIMESTAMP"):
            interpret(frame(f))

    def test_24_reordered_input_same_result_and_not_mutated(self):
        rows = [bench(3, 3.25, -2), bench(1, 3.0, -6),
                bench(2, 3.1, -4), row("F", "fatigue_report", "moderate")]
        s = frame(*rows)
        original = deepcopy(s)
        one = interpret(s)
        two = interpret(frame(*reversed(rows)))
        self.assertEqual(one, two)
        self.assertEqual(s, original)

    def test_25_unregistered_quality_group_rejected(self):
        q1 = quality(1, "met_intent", -4)
        q2 = quality(2, "met_intent", -1)
        q1["comparison_group"] = q2["comparison_group"] = "unknown-group"
        v = interpret(frame(q1, q2))
        self.assertEqual("unknown", v["workout_tolerance"]["state"])

    def test_26_protocol_change_in_same_group_cannot_bridge(self):
        r = bench(3, 3.3, -2)
        r["protocol_id"] = "changed-test"
        v = interpret(frame(bench(1, 3.0, -6),
                            bench(2, 3.1, -4), r))
        self.assertEqual("unknown", v["fitness_trend"]["state"])

    def test_27_invalid_type_for_benchmark_cannot_throw(self):
        r = bench(2, "not-a-number", -4)
        v = interpret(frame(bench(1, 3.0, -6), r, bench(3, 3.3, -2)))
        self.assertEqual("unknown", v["fitness_trend"]["state"])

    def test_28_wearable_without_self_report_cannot_create_fatigue(self):
        v = interpret(frame(row("H", "hrv", 70, assessment="favorable"),
                            row("S", "sleep", 8)))
        self.assertEqual("unknown", v["recent_fatigue"]["state"])
        self.assertIn("WEARABLE_CONTEXT_NOT_INDEPENDENT_VOTE",
                      v["evidence_quality"]["warnings"])

    def test_29_no_readiness_percent_or_auto_prescription(self):
        v = interpret(frame(row("F", "fatigue_report", "low")))
        self.assertNotIn("readiness", v)
        self.assertEqual("NONE_SYNTHETIC_RESEARCH", v["coaching_authority"])
        self.assertIsNone(v["proposed_workout_change"])


    def test_30_malformed_quality_fails_closed(self):
        bad = quality(1, "struggled", -2)
        bad["quality"] = {"unexpected": "object"}
        v = interpret(frame(bad, quality(2, "struggled", -1)))
        self.assertEqual("unknown", v["workout_tolerance"]["state"])
        self.assertEqual(1, v["evidence_quality"]["excluded_by_reason"]["LOW_QUALITY"])

    def test_31_unhashable_outcome_is_unknown(self):
        bad = quality(1, ["not", "an", "outcome"], -2)
        v = interpret(frame(bad, quality(2, "struggled", -1)))
        self.assertEqual("unknown", v["workout_tolerance"]["state"])
        self.assertEqual("UNVERIFIED_SESSION_OUTCOME",
                         v["workout_tolerance"]["reason"])


if __name__ == "__main__":
    unittest.main()
