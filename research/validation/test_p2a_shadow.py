"""Synthetic-only as-of and abstention checks for P2-A shadow summaries."""
import unittest

from p2a_shadow import summarize


class P2AShadowTests(unittest.TestCase):
    lineage = {"athlete_id": "synthetic-athlete", "discipline": "running",
               "event_at": "2026-01-01T08:00:00Z", "source": "generated",
               "source_version": "fixture-v1", "protocol": "synthetic-v1",
               "device": "synthetic-device", "rights": "synthetic-only",
               "plan_revision": "plan-v1"}

    def setUp(self):
        self.history = [
            {"id": "h1", "observed_at": "2026-01-01T08:00:00Z",
             "available_at": "2026-01-01T09:00:00Z", "value": 10.0,
             "unit": "min", "quality": "valid", **self.lineage},
            {"id": "h2", "observed_at": "2026-01-02T08:00:00Z",
             "available_at": "2026-01-02T09:00:00Z", "value": 12.0,
             "unit": "min", "quality": "valid", **self.lineage},
            {"id": "future", "observed_at": "2026-01-03T08:00:00Z",
             "available_at": "2026-01-04T09:00:00Z", "value": 99.0,
             "unit": "min", "quality": "valid", **self.lineage},
        ]

    def test_future_arrival_excluded_and_checkin_ablation_is_separate(self):
        result = summarize(self.history, "2026-01-03T12:00:00Z", {
            "id": "c1", "observed_at": "2026-01-03T10:00:00Z",
            "available_at": "2026-01-03T11:00:00Z", "value": 4.0,
            "unit": "score", "quality": "reported", **self.lineage,
        })
        self.assertEqual(["h1", "h2"], result["history_only"]["input_ids"])
        self.assertEqual(["h1", "h2", "c1"], result["history_plus_checkin"]["input_ids"])
        self.assertEqual(11.0, result["history_only"]["descriptive_median"])
        self.assertEqual(27 * 3600, result["history_only"]["newest_input_age_seconds"])
        self.assertEqual(self.lineage, result["history_only"]["provenance"][0]["lineage"])
        self.assertEqual("ABSTAIN_UNCALIBRATED", result["history_plus_checkin"]["status"])
        self.assertIsNone(result["history_plus_checkin"]["descriptive_median"])
        self.assertIsNone(result["history_plus_checkin"]["unit"])
        self.assertNotIn("action", result)

    def test_unavailable_checkin_is_not_added(self):
        checkin = {"id": "late", "observed_at": "2026-01-03T10:00:00Z",
                   "available_at": "2026-01-04T11:00:00Z", "value": 4.0,
                   "unit": "score", "quality": "reported", **self.lineage}
        result = summarize(self.history, "2026-01-03T12:00:00Z", checkin)
        self.assertEqual(result["history_only"], result["history_plus_checkin"])
        self.assertEqual("CHECKIN_NOT_AVAILABLE_AS_OF_DECISION", result["checkin_status"])

    def test_sparse_or_invalid_history_abstains_without_personal_baseline(self):
        sparse = summarize(self.history[:1], "2026-01-02T00:00:00Z")
        self.assertIsNone(sparse["history_only"]["descriptive_median"])
        self.assertEqual("INSUFFICIENT_HISTORY", sparse["history_only"]["uncertainty_reason"])
        invalid = dict(self.history[0], value=-1)
        rejected = summarize([invalid], "2026-01-02T00:00:00Z")
        self.assertEqual(1, rejected["history_only"]["excluded_count"])
        self.assertEqual([], rejected["history_only"]["input_ids"])

    def test_duplicate_ids_and_incompatible_units_fail_closed(self):
        duplicate = [self.history[0], dict(self.history[0])]
        with self.assertRaises(ValueError):
            summarize(duplicate, "2026-01-02T00:00:00Z")
        mixed = [self.history[0], dict(self.history[1], unit="km")]
        with self.assertRaises(ValueError):
            summarize(mixed, "2026-01-03T12:00:00Z")

    def test_units_must_be_nonempty_strings(self):
        for unit in (42, [], ""):
            with self.subTest(unit=unit):
                rows = [dict(row, unit=unit) for row in self.history[:2]]
                with self.assertRaises(ValueError):
                    summarize(rows, "2026-01-03T12:00:00Z")

    def test_missing_lineage_fails_closed(self):
        missing = dict(self.history[0])
        del missing["rights"]
        with self.assertRaises(ValueError):
            summarize([missing], "2026-01-02T00:00:00Z")

    def test_non_finite_values_are_excluded_or_abstained(self):
        for value in (float("nan"), float("inf"), float("-inf"), 10 ** 1000):
            with self.subTest(value=value):
                history = summarize([dict(self.history[0], value=value)],
                                    "2026-01-02T00:00:00Z")
                self.assertEqual(1, history["history_only"]["excluded_count"])
                self.assertEqual([], history["history_only"]["input_ids"])
                checkin = {"id": "bad", "observed_at": "2026-01-03T10:00:00Z",
                           "available_at": "2026-01-03T11:00:00Z", "value": value,
                           "unit": "score", "quality": "reported", **self.lineage}
                result = summarize(self.history[:2], "2026-01-03T12:00:00Z", checkin)
                self.assertEqual("CHECKIN_INVALID_OR_UNAVAILABLE", result["checkin_status"])
                self.assertEqual(result["history_only"], result["history_plus_checkin"])


if __name__ == "__main__":
    unittest.main()
