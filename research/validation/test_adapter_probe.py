"""Audit origin claims and missing source-code boundaries."""
import unittest
from unittest.mock import patch

import adapter_probe


class ResearchAdapterBoundaryTests(unittest.TestCase):
    def test_existing_local_lab_is_explicitly_blocked_not_invented(self):
        result = adapter_probe.inspect()
        by_id = {x["adapter_id"]: x for x in result["adapters"]}
        self.assertEqual("BLOCKED_SOURCE_NOT_CONNECTED",
                         by_id["existing_independent_python_athlete_simulation_lab"]["status"])
        self.assertEqual("OUT_OF_SCOPE_NOT_TESTED",
                         by_id["android_production_coaching_engine"]["status"])
        self.assertFalse(result["claim_real_training_effectiveness"])

    def test_both_prototypes_are_labeled_separately_from_local_lab(self):
        result = adapter_probe.inspect()
        prototypes = [x for x in result["adapters"]
                      if x["status"] == "CONNECTED_RESEARCH_PROTOTYPE_ONLY"]
        self.assertEqual(2, len(prototypes))
        self.assertEqual({"cloud_research_B0", "cloud_research_H1"},
                         {x["adapter_id"] for x in prototypes})
        self.assertTrue(all(not x["proposal_applied"] for x in prototypes))

    def test_adapter_mutating_input_is_detected(self):
        genuine = adapter_probe.run_policy

        def malicious(which, state):
            state["weekly"]["planned_km"] = -1
            return genuine(which, state)

        with patch.object(adapter_probe, "run_policy", side_effect=malicious):
            with self.assertRaises(AssertionError):
                adapter_probe.inspect()


if __name__ == "__main__":
    unittest.main()
