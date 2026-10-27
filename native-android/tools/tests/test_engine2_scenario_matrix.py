"""Executable V2 long-horizon synthetic recipe and generator quality gates."""
import json
import subprocess
import sys
import tempfile
import unittest
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MATRIX = ROOT / "app/src/test/resources/engine/engine2_longitudinal_recipes_v2.json"
GENERATOR = ROOT / "tools/build_engine2_scenarios.py"
COVERAGE = ROOT / "tools/report_engine2_coverage.py"
CATALOG = ROOT / "app/src/main/assets/native-library/catalog.json"
DIMENSIONS = {
    "running", "strength", "calisthenics", "hybrid",
    "recovery", "provenance", "plan_lifecycle", "edge_conditions",
}
DISCIPLINES = {"run", "str", "cal", "cond"}


def run(*args):
    return subprocess.run(
        [sys.executable, str(GENERATOR), *map(str, args)],
        capture_output=True, text=True, timeout=35, check=False,
    )


class Engine2ScenarioMatrixTest(unittest.TestCase):
    def test_64_reviewable_recipes_are_distinct_versioned_and_bounded(self):
        doc = json.loads(MATRIX.read_text(encoding="utf-8"))
        self.assertEqual(2, doc["schemaVersion"])
        self.assertEqual("synthetic-only", doc["classification"])
        self.assertIn("not validated", doc["purpose"])
        cases = doc["scenarios"]
        self.assertEqual(64, len(cases))
        names, ids, dimensions = set(), set(), Counter()
        known = set(doc["invariantIds"])
        self.assertGreaterEqual(len(known), 20)
        for case in cases:
            self.assertRegex(case["id"], r"^E2-\d{3}$")
            self.assertNotIn(case["id"], ids)
            self.assertNotIn(case["title"], names)
            ids.add(case["id"])
            names.add(case["title"])
            dimensions[case["dimension"]] += 1
            self.assertTrue(case["fictional"])
            self.assertIn(case["weeks"], (12, 16))
            self.assertEqual(set(case["weeklySessions"]), DISCIPLINES)
            self.assertTrue(all(isinstance(n, int) and 0 <= n <= 7
                                for n in case["weeklySessions"].values()))
            self.assertEqual(case["profile"]["device"] == "none",
                             case["wellnessCoverage"] == "none")
            self.assertTrue(set(case["checks"]) <= known)
            self.assertGreaterEqual(len(case["checks"]), 2)
            for week in case["missingWeeks"]:
                self.assertTrue(0 <= week < case["weeks"])
            for event in case["events"]:
                self.assertTrue(0 <= event["week"] < case["weeks"])
                self.assertTrue(0 <= event["day"] < 7)
                self.assertTrue(event["value"].startswith("Fictional event: "))
        self.assertEqual(DIMENSIONS, set(dimensions))
        self.assertEqual(set(dimensions.values()), {8})

    def test_evidence_register_is_scoped_not_unreviewed_rule_parameters(self):
        evidence = json.loads((ROOT / "app/src/test/resources/engine/engine2_evidence_registry_v1.json").read_text(encoding="utf-8"))
        matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
        self.assertEqual(1, evidence["schemaVersion"])
        self.assertEqual("engineering-reference-only", evidence["reviewStatus"])
        self.assertGreaterEqual(len(evidence["sources"]), 3)
        ids = set()
        known_checks = set(matrix["invariantIds"])
        for source in evidence["sources"]:
            self.assertNotIn(source["id"], ids)
            ids.add(source["id"])
            self.assertTrue(source["url"].startswith("https://"))
            self.assertTrue(source["issuer"])
            self.assertTrue(source["takeaways"])
            self.assertTrue(source["prohibitedUses"])
            self.assertTrue(set(source["invariants"]) <= known_checks)
            self.assertNotIn("autoApproved", source)
        self.assertGreaterEqual(len(evidence["governance"]["promoteToExecutableRuleOnlyAfter"]), 4)

    def test_generator_is_deterministic_across_variants_and_does_not_overwrite(self):
        args = ("--seed", 290, "--limit", 8, "--variants", 2)
        first, second = run(*args), run(*args)
        self.assertEqual(0, first.returncode, first.stderr)
        self.assertEqual(first.stdout, second.stdout)
        self.assertNotEqual(first.stdout, run("--seed", 291, "--limit", 8, "--variants", 2).stdout)
        doc = json.loads(first.stdout)
        self.assertEqual((2, 16, "synthetic-only"), (doc["schemaVersion"], len(doc["scenarios"]), doc["classification"]))
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "synthetic-output.json"
            ok = run(*args, "--output", output)
            self.assertEqual(0, ok.returncode, ok.stderr)
            self.assertEqual(first.stdout, output.read_text(encoding="utf-8"))
            blocked = run(*args, "--output", output)
            self.assertNotEqual(0, blocked.returncode)
            self.assertEqual(first.stdout, output.read_text(encoding="utf-8"))

    def test_full_matrix_generated_records_have_truthful_missingness_and_provenance(self):
        result = run("--seed", 20261027, "--limit", 64, "--variants", 1)
        self.assertEqual(0, result.returncode, result.stderr)
        doc = json.loads(result.stdout)
        self.assertEqual(64, len(doc["scenarios"]))
        canonical = {e["id"]: e for e in json.loads(CATALOG.read_text(encoding="utf-8"))["entries"]}
        ids = set()
        dimensions = Counter()
        duplicates = defaultdict(set)
        missing = 0
        n_records = 0
        for scenario in doc["scenarios"]:
            self.assertTrue(scenario["synthetic"])
            dimensions[scenario["dimension"]] += 1
            original = scenario["profile"]
            from_day = date.fromisoformat(scenario["period"]["start"])
            to_day = date.fromisoformat(scenario["period"]["end"])
            self.assertIn((to_day-from_day).days+1, (84, 112))
            for record in scenario["records"]:
                self.assertNotIn(record["id"], ids)
                ids.add(record["id"])
                self.assertTrue(from_day <= date.fromisoformat(record["date"]) <= to_day)
                self.assertTrue(record["source"].startswith("synthetic-"))
                self.assertIn(record["discipline"], DISCIPLINES)
                self.assertIn(record["status"], ("recorded", "planned", "skipped"))
                if record["status"] == "planned":
                    missing += 1
                    self.assertFalse({"durationMinutes", "distanceKm", "avgHrBpm", "sets"} & record.keys())
                    self.assertGreater(record["plannedTargetMinutes"], 0)
                elif record["status"] == "skipped":
                    missing += 1
                    self.assertTrue(record["reason"])
                    self.assertFalse({"durationMinutes", "distanceKm", "avgHrBpm", "sets"} & record.keys())
                else:
                    self.assertGreater(record["durationMinutes"], 0)
                    self.assertTrue(1 <= record["effortRpe"] <= 10)
                    for metric in ("distanceKm", "loadKg"):
                        if metric in record:
                            self.assertGreaterEqual(record[metric], 0)
                    if "avgHrBpm" in record:
                        self.assertTrue(original["wearableConnected"])
                        if record["avgHrBpm"] > 220:
                            self.assertEqual("suspicious-synthetic-sensor-value", record["qualityFlag"])
                    for s in record.get("sets", []):
                        self.assertIn(s["exerciseId"], canonical)
                        self.assertTrue(s.get("reps", 0) > 0 or s.get("seconds", 0) > 0)
                        self.assertGreaterEqual(s.get("loadKg", 0), 0)
                if not original["wearableConnected"]:
                    self.assertNotIn("avgHrBpm", record)
                if "physicalEventKey" in record:
                    self.assertIn("providerActivityId", record)
                    duplicates[(scenario["id"], record["physicalEventKey"])].add(record["source"])
                n_records += 1
            if not original["wearableConnected"]:
                self.assertFalse(scenario["wellness"])
            for row in scenario["wellness"]:
                self.assertTrue(from_day <= date.fromisoformat(row["date"]) <= to_day)
                self.assertTrue(row["source"].startswith("synthetic-"))
                for metric in ("restingHrBpm", "hrvMs", "sleepHours"):
                    self.assertTrue(row[metric] is None or row[metric] > 0)
            for event in scenario["events"]:
                self.assertFalse(event["automaticPlanMutation"])
                self.assertTrue(from_day <= date.fromisoformat(event["date"]) <= to_day)
        self.assertEqual(set(dimensions), DIMENSIONS)
        self.assertGreater(n_records, 1000)
        self.assertGreater(missing, 0)
        self.assertTrue(any(len(sources) >= 2 for sources in duplicates.values()))
        by_recipe = {s["recipeId"]: s for s in doc["scenarios"]}
        # A recipe must actually produce its advertised adversarial evidence, not
        # merely repeat its name in event metadata.
        overlap = [
            r for r in by_recipe["E2-006"]["records"]
            if r["kind"] == "quality" and r["date"] in ("2026-12-15", "2026-12-16")
        ]
        self.assertEqual(2, len(overlap))
        self.assertTrue(any(
            r.get("qualityFlag") == "suspicious-synthetic-sensor-value"
            and r.get("avgHrBpm") == 248
            for r in by_recipe["E2-047"]["records"]
        ))
        self.assertTrue(any(
            r.get("quarantinedInput", {}).get("status") == "rejected"
            for r in by_recipe["E2-015"]["records"]
        ))
        self.assertTrue(any(
            r["status"] == "planned" and "durationMinutes" not in r
            for r in by_recipe["E2-049"]["records"]
        ))
        self.assertTrue(any(
            r["status"] == "skipped" and "durationMinutes" not in r
            for r in by_recipe["E2-060"]["records"]
        ))
        scoped = defaultdict(set)
        for row in by_recipe["E2-048"]["records"]:
            if row.get("accountScope"):
                scoped[row["physicalEventKey"]].add(row["accountScope"])
        self.assertTrue(any(len(accounts) == 2 for accounts in scoped.values()))

    def test_coverage_report_proves_edge_inputs_not_adaptive_coaching(self):
        result = subprocess.run(
            [sys.executable, str(COVERAGE), "--limit", "64", "--variants", "1",
             "--seed", "20261027"],
            capture_output=True, text=True, timeout=35, check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads(result.stdout)
        self.assertEqual("synthetic-only", report["classification"])
        self.assertEqual(64, report["scenarioCount"])
        self.assertGreater(report["activityRecordCount"], 1000)
        self.assertGreater(report["wellnessObservationCount"], 0)
        self.assertGreater(report["missingCalendarWeeks"], 0)
        self.assertGreaterEqual(report["distinctCandidateInvariantIds"], 20)
        self.assertEqual(set(report["byDimension"]), DIMENSIONS)
        self.assertGreater(report["providerDuplicateGroupsWithinAccount"], 0)
        self.assertGreater(report["flaggedSuspiciousSensorRows"], 0)
        self.assertGreater(report["quarantinedInvalidInputRows"], 0)
        self.assertGreater(report["byRecordStatus"].get("planned", 0), 0)
        self.assertGreater(report["byRecordStatus"].get("skipped", 0), 0)
        self.assertIn("do not demonstrate clinical safety", report["note"])

    def test_rejected_unsafe_scale_inputs_do_not_produce_json(self):
        for args in [
            ("--limit", 0), ("--limit", 65), ("--variants", 0),
            ("--variants", 21), ("--limit", 64, "--variants", 21),
        ]:
            result = run(*args)
            self.assertNotEqual(0, result.returncode, args)
            self.assertFalse(result.stdout)


if __name__ == "__main__":
    unittest.main()
