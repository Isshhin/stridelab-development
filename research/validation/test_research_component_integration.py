"""Synthetic composition-boundary checks; components remain independently versioned."""
import unittest
from copy import deepcopy
from random import Random

from heldout_runner import sample
from p2a_shadow import summarize
from policy_candidates import run_policy


class ResearchComponentIntegrationTests(unittest.TestCase):
    lineage = {"athlete_id": "synthetic-athlete", "discipline": "running",
               "event_at": "2026-01-01T08:00:00Z", "source": "generated",
               "source_version": "fixture-v1", "protocol": "synthetic-v1",
               "device": "synthetic-device", "rights": "synthetic-only",
               "plan_revision": "plan-v1"}

    def test_shadow_uncertainty_does_not_change_existing_policy_semantics(self):
        state = sample(7, "OVER_CAP", Random(82610))
        before = deepcopy(state)
        shadow = summarize([
            {"id": "old", "observed_at": "2026-01-01T08:00:00Z",
             "available_at": "2026-01-01T09:00:00Z", "value": 10,
             "unit": "min", "quality": "valid", **self.lineage},
            {"id": "new", "observed_at": "2026-01-02T08:00:00Z",
             "available_at": "2026-01-04T09:00:00Z", "value": 15,
             "unit": "min", "quality": "valid", **self.lineage},
        ], "2026-01-03T12:00:00Z")
        self.assertEqual(["old"], shadow["history_only"]["input_ids"])
        self.assertEqual("ABSTAIN_UNCALIBRATED", shadow["history_only"]["status"])
        for policy in ("B0", "H1"):
            result = run_policy(policy, state)
            self.assertEqual(before, state)
            self.assertEqual(before["weekly"], result["revision"]["before"])
            self.assertEqual(before["weekly"], result["revision"]["after"])
            self.assertFalse(result["revision"]["applied"])
            self.assertEqual("PROPOSE_WEEK_REDUCTION", result["action"])
            self.assertEqual(f"{policy}_distance_capacity_v1" if policy == "B0"
                             else "H1_constrained_multidose_v1", result["policy_id"])

    def test_hard_restriction_precedes_capacity_proposal_and_shadow_signal(self):
        state = sample(3, "PAIN_AND_CAP", Random(82606))
        state["feedback"]["pain"] = True
        shadow = summarize([], "2026-01-03T12:00:00Z")
        self.assertEqual("ABSTAIN_UNCALIBRATED", shadow["history_only"]["status"])
        result = run_policy("H1", state)
        self.assertEqual(("DEFER_UNKNOWN", "PAIN_NEEDS_HUMAN_REVIEW"),
                         (result["action"], result["reason"]))
        self.assertFalse(result["revision"]["applied"])
        self.assertEqual(state["weekly"], result["revision"]["after"])


if __name__ == "__main__":
    unittest.main()
