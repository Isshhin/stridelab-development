"""Synthetic-only coaching engine fixture quality gates; no network or user records."""
import json
import unittest
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path

ANDROID = Path(__file__).resolve().parents[2]
DATA = ANDROID / "app" / "src" / "test" / "resources" / "engine"
CATALOG = ANDROID / "app" / "src" / "main" / "assets" / "native-library" / "catalog.json"

REQUEST_KEYS = {
    "goal", "secondaryGoal", "experience", "start", "weeks", "minutes",
    "days", "runDays", "strengthDays", "calDays", "conditioningDays",
    "longDay", "qualityDays", "qualityCount", "weeklyKm", "longestKm",
    "planningPace", "equipment", "calLevel", "returning", "excluded",
    "limitations", "recoveryNotes", "strengthExperience", "pullLevel",
    "coreLevel", "policyChoice", "disciplineGoals", "dailyMinutes",
}
DISCIPLINES = {"run", "str", "cal", "cond"}
SCENARIO_CHECKS = {
    "unknown-data-is-not-zero", "do-not-invent-distance-hr-pace",
    "no-quality-without-confirmed-base", "one-plan-user-approved",
    "respect-observed-base-not-goal-pace", "preserve-long-run-day",
    "avoid-stacking-hard-days", "no-unsupported-vo2max-estimate",
    "coordinate-lower-body-vs-quality", "do-not-sum-km-and-sets",
    "do-not-invent-hr", "no-unapproved-plan-changes",
    "illness-requires-caution-not-diagnosis", "rest-does-not-trigger-catch-up",
    "return-gradual-with-user-approval", "missing-wellness-does-not-mean-recovered",
    "do-not-double-count-same-physical-event", "raw-provider-ids-are-not-canonical",
    "keep-provenance", "do-not-auto-link-ambiguous-runs",
    "use-actual-reps-and-load-not-planned", "do-not-infer-1rm-from-incomplete-set-metadata",
    "respect-equipment", "allow-user-to-decline-increases",
    "no-automatic-skill-level-promotion", "preserve-exercise-progression-identity",
    "actual-reps-different-from-targets", "respect-form-and-assessment",
    "offline-deterministic", "absence-of-heart-rate-is-unknown",
    "no-gps-map-invention", "do-not-reclassify-missing-sync-as-missed",
    "never-prescribe-unavailable-equipment", "preserve-existing-plan-until-user-approves-change",
    "provide-bodyweight-alternative", "no-generic-ml-adaptation-required",
    "no-catchup-volume", "dont-presume-readiness-from-high-mileage-alone",
    "no-fabricated-data-for-partial-week", "one-quality-priority-is-user-configurable",
}


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


class EngineDatasetValidationTest(unittest.TestCase):
    def test_executable_engine1_inputs_are_versioned_and_well_formed(self):
        doc = load("engine1_plan_cases_v1.json")
        self.assertEqual((1, "synthetic-only"),
                         (doc["schemaVersion"], doc["classification"]))
        self.assertEqual(
            "25be8f3828374d1e539c7e825afa20fbcb652b56",
            doc["baselineSha"],
        )
        ids = set()
        counts = Counter()
        for case in doc["cases"]:
            self.assertRegex(case["id"], r"^E1-\d{3}$")
            self.assertNotIn(case["id"], ids)
            ids.add(case["id"])
            self.assertIn(case["expected"], ("accept", "reject"))
            self.assertEqual(case["policy"], case["request"]["policyChoice"])
            self.assertEqual(case["request"]["start"], doc["seed"]["start"])
            self.assertFalse(set(case["request"]) - REQUEST_KEYS)
            date.fromisoformat(case["request"]["start"])
            self.assertTrue(case["request"]["start"] >= "2026-01-01")
            if case["expected"] == "reject":
                self.assertTrue(case["reason"])
            else:
                self.assertTrue(case["guarantees"])
            counts[case["expected"]] += 1
        self.assertGreaterEqual(counts["accept"], 8)
        self.assertGreaterEqual(counts["reject"], 10)

    def test_athlete_histories_are_synthetic_valid_and_missing_data_is_not_faked(self):
        doc = load("synthetic_athlete_scenarios_v1.json")
        self.assertEqual((1, "synthetic-only"),
                         (doc["schemaVersion"], doc["classification"]))
        self.assertIn("not model training data", doc["intent"].lower())
        scenario_ids, record_ids = set(), set()
        sources_by_event = defaultdict(set)
        total = 0
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        exercise_ids = {entry["id"] for entry in catalog["entries"]}
        self.assertEqual(len(catalog["entries"]), len(exercise_ids))
        for scenario in doc["scenarios"]:
            sid = scenario["id"]
            self.assertNotIn(sid, scenario_ids)
            scenario_ids.add(sid)
            self.assertTrue(set(scenario["requiredChecks"]) <= SCENARIO_CHECKS)
            self.assertTrue(scenario["requiredChecks"])
            start = date.fromisoformat(scenario["period"]["start"])
            end = date.fromisoformat(scenario["period"]["end"])
            self.assertLessEqual(start, end)
            if not scenario["profile"]["wearableConnected"]:
                # The absence of a wearable must not fabricate physiological values.
                self.assertFalse(scenario["wellness"])
            for record in scenario["records"]:
                rid = record["id"]
                self.assertNotIn(rid, record_ids)
                record_ids.add(rid)
                self.assertIn(record["discipline"], DISCIPLINES)
                self.assertIn(record["status"], ("recorded", "planned", "skipped"))
                self.assertTrue(record["source"].startswith("synthetic-"))
                self.assertTrue(start <= date.fromisoformat(record["date"]) <= end)
                if record["status"] != "recorded":
                    self.assertFalse({"durationMinutes", "distanceKm", "avgHrBpm", "sets"} & record.keys())
                else:
                    self.assertGreater(record["durationMinutes"], 0)
                    if "distanceKm" in record:
                        self.assertGreater(record["distanceKm"], 0)
                    if "avgHrBpm" in record:
                        self.assertGreater(record["avgHrBpm"], 0)
                    for exercise in record.get("sets", []):
                        self.assertIn(exercise["exerciseId"], exercise_ids)
                        self.assertGreater(exercise["reps"], 0)
                        if "loadKg" in exercise:
                            self.assertGreaterEqual(exercise["loadKg"], 0)
                if "physicalEventKey" in record:
                    self.assertTrue(record.get("providerActivityId"))
                    sources_by_event[(sid, record["physicalEventKey"])].add(record["source"])
                total += 1
            for observation in scenario["wellness"]:
                self.assertTrue(start <= date.fromisoformat(observation["date"]) <= end)
                for name in ("restingHrBpm", "hrvMs", "sleepHours"):
                    value = observation.get(name)
                    self.assertTrue(value is None or value > 0)
            for event in scenario["events"]:
                self.assertTrue(start <= date.fromisoformat(event["date"]) <= end)
                self.assertTrue(event["value"])
        self.assertGreaterEqual(len(scenario_ids), 10)
        self.assertGreaterEqual(total, 100)
        self.assertTrue(any(len(sources) > 1 for sources in sources_by_event.values()))


if __name__ == "__main__":
    unittest.main()
