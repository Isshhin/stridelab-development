"""Synthetic-only as-of and abstention checks for P2-A shadow summaries."""
import unittest

from p2a_shadow import summarize


class P2AShadowTests(unittest.TestCase):
    def setUp(self):
        self.history = [
            {"id": "h1", "observed_at": "2026-01-01T08:00:00Z",
             "available_at": "2026-01-01T09:00:00Z", "value": 10.0,
             "unit": "min", "quality": "valid"},
            {"id": "h2", "observed_at": "2026-01-02T08:00:00Z",
             "available_at": "2026-01-02T09:00:00Z", "value": 12.0,
             "unit": "min", "quality": "valid"},
            {"id": "future", "observed_at": "2026-01-03T08:00:00Z",
             "available_at": "2026-01-04T09:00:00Z", "value": 99.0,
             "unit": "min", "quality": "valid"},
        ]

    def test_future_arrival_excluded_and_checkin_ablation_is_separate(self):
        result = summarize(self.history, "2026-01-03T12:00:00Z", {
            "id": "c1", "observed_at": "2026-01-03T10:00:00Z",
            "available_at": "2026-01-03T11:00:00Z", "value": 4.0,
            "unit": "score", "quality": "reported",
        })
        self.assertEqual(["h1", "h2"], result["history_only"]["input_ids"])
        self.assertEqual(["h1", "h2", "c1"], result["history_plus_checkin"]["input_ids"])
        self.assertEqual(11.0, result["history_only"]["descriptive_median"])
        self.assertEqual("ABSTAIN_UNCALIBRATED", result["history_plus_checkin"]["status"])
        self.assertNotIn("action", result)

    def test_unavailable_checkin_is_not_added(self):
        checkin = {"id": "late", "observed_at": "2026-01-03T10:00:00Z",
                   "available_at": "2026-01-04T11:00:00Z", "value": 4.0,
                   "unit": "score", "quality": "reported"}
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


if __name__ == "__main__":
    unittest.main()
