"""Check repeatable engine dataset generation and never-invent-missing-data rules."""
import json
import subprocess
import sys
import unittest
from pathlib import Path

TOOL = Path(__file__).resolve().parents[1] / "generate_engine_synthetic.py"


def execute(seed=7, count=5):
    return subprocess.run(
        [sys.executable, str(TOOL), "--seed", str(seed), "--count", str(count)],
        capture_output=True, text=True, check=False, timeout=20,
    )


class EngineSyntheticGeneratorTest(unittest.TestCase):
    def test_seeded_output_is_deterministic_private_and_structured(self):
        first = execute(700, 5)
        same = execute(700, 5)
        different = execute(701, 5)
        self.assertEqual(first.returncode, 0, first.stderr)
        self.assertEqual(first.stdout, same.stdout)
        self.assertEqual(different.returncode, 0, different.stderr)
        self.assertNotEqual(first.stdout, different.stdout)
        doc = json.loads(first.stdout)
        self.assertEqual(1, doc["schemaVersion"])
        self.assertEqual("synthetic-only", doc["classification"])
        self.assertEqual({"seed": 700, "count": 5}, doc["generated"])
        self.assertEqual(5, len(doc["scenarios"]))
        all_ids = set()
        for scenario in doc["scenarios"]:
            self.assertTrue(scenario["id"].startswith("G-"))
            if not scenario["profile"]["wearableConnected"]:
                self.assertFalse(scenario["wellness"])
            for record in scenario["records"]:
                self.assertNotIn(record["id"], all_ids)
                all_ids.add(record["id"])
                self.assertTrue(record["source"].startswith("synthetic-"))
                self.assertIn(record["status"], ("recorded", "skipped"))
                if record["status"] == "skipped":
                    self.assertNotIn("durationMinutes", record)
                    self.assertNotIn("distanceKm", record)
                    self.assertNotIn("sets", record)
                else:
                    self.assertGreater(record["durationMinutes"], 0)
                    if "distanceKm" in record:
                        self.assertGreater(record["distanceKm"], 0)

    def test_count_boundaries_fail_without_creating_data(self):
        for count in (0, 1001):
            result = execute(100, count)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(result.stdout)


if __name__ == "__main__":
    unittest.main()
