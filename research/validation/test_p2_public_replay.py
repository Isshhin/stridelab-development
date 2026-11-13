import unittest
from dataclasses import replace
from p2_public_replay import polar, replay


class ReplayTests(unittest.TestCase):
    def setUp(self):
        self.beats = list(polar("[Params]\nInterval=238\n[HRData]\n600\n400\n800", "A"))

    def test_visibility_and_lineage(self):
        a, b, c = self.beats
        delayed = replace(b, available_ms=1500)
        rows = [replace(a, record="B"), c, delayed, a]
        result = replay(rows, "A", 1000)
        self.assertEqual(result["observation_ids"], [1])
        self.assertEqual(result["dataset"], "10.13026/wce5-fj54")
        self.assertEqual(result["boundary"], "RESEARCH_ONLY_ISOLATED")
        self.assertEqual(result, replay(reversed(rows), "A", 1000))
        self.assertEqual(replay(rows, "A", 1500)["median_rr_ms"], 500)

    def test_missing_and_abstention(self):
        empty = replay(self.beats, "A", 0)
        self.assertIsNone(empty["rr_range_ms"])
        self.assertIn("personal baseline", empty["missing"])
        self.assertEqual(replay(self.beats, "A", 1800)["action"], "ABSTAIN_NEED_MORE_INFORMATION")

    def test_fail_closed(self):
        for invalid in ["Interval=1\n[HRData]\n80", "Interval=238\n[HRData]\n0"]:
            with self.assertRaises(ValueError):
                list(polar(invalid, "A"))
        with self.assertRaises(ValueError):
            replay(self.beats * 2, "A", 1800)
