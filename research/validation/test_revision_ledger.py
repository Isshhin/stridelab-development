"""Research-only tests for approval, rejection, version conflict and rollback."""
from copy import deepcopy
import unittest

from revision_ledger import RevisionConflict, decide, new_state, propose, rollback


class PlanRevisionLedgerTests(unittest.TestCase):
    def setUp(self):
        self.original = {"week": 1, "quality_s": 1800, "easy_km": 60}
        self.alternative = {"week": 1, "quality_s": 1200, "easy_km": 58}
        self.activities = [{"id": "run-a", "actual_hard_s": 1600, "completed": True}]
        self.state = new_state(self.original, self.activities)

    def _pending(self):
        return propose(self.state, "proposal-001", self.alternative,
                       "Reduce quality exposure after fatigue report",
                       expected_version=1, policy_id="H1_research_v1")

    def test_proposing_does_not_apply_change(self):
        pending = self._pending()
        self.assertEqual(self.original, pending["plan"])
        self.assertEqual(1, pending["current_version"])
        self.assertEqual("pending", pending["pending"]["proposal-001"]["status"])
        self.assertEqual(self.original, self.state["plan"])

    def test_rejection_preserves_plan_and_history(self):
        denied = decide(self._pending(), "proposal-001", "reject", expected_version=1)
        self.assertEqual(self.original, denied["plan"])
        self.assertEqual(self.activities, denied["observed_activities"])
        self.assertEqual(1, denied["current_version"])
        self.assertEqual("rejected", denied["history"][0]["status"])
        self.assertFalse(denied["pending"])

    def test_explicit_approval_changes_version_and_records_diff(self):
        approved = decide(self._pending(), "proposal-001", "approve", expected_version=1)
        self.assertEqual(self.alternative, approved["plan"])
        self.assertEqual(2, approved["current_version"])
        self.assertEqual(self.original, approved["history"][0]["before"])
        self.assertEqual(self.alternative, approved["history"][0]["candidate"])
        self.assertEqual("H1_research_v1", approved["history"][0]["policy_id"])
        self.assertEqual(self.activities, approved["observed_activities"])

    def test_rollback_restores_plan_without_erasing_observations(self):
        approved = decide(self._pending(), "proposal-001", "approve", expected_version=1)
        restored = rollback(approved, "proposal-001", expected_version=2)
        self.assertEqual(self.original, restored["plan"])
        self.assertEqual(3, restored["current_version"])
        self.assertEqual(self.activities, restored["observed_activities"])
        self.assertEqual(["approved", "rollback"],
                         [x["status"] for x in restored["history"]])
        self.assertEqual(self.alternative, restored["history"][-1]["before"])
        self.assertEqual(self.original, restored["history"][-1]["candidate"])

    def test_stale_approval_is_rejected(self):
        with self.assertRaises(RevisionConflict):
            decide(self._pending(), "proposal-001", "approve", expected_version=2)

    def test_stale_rollback_is_rejected(self):
        approved = decide(self._pending(), "proposal-001", "approve", expected_version=1)
        with self.assertRaises(RevisionConflict):
            rollback(approved, "proposal-001", expected_version=1)

    def test_duplicate_and_repeated_decision_rejected(self):
        pending = self._pending()
        with self.assertRaises(RevisionConflict):
            propose(pending, "proposal-001", self.alternative, "duplicate",
                    expected_version=1, policy_id="H1")
        approved = decide(pending, "proposal-001", "approve", expected_version=1)
        with self.assertRaises(RevisionConflict):
            decide(approved, "proposal-001", "approve", expected_version=2)

    def test_copy_on_write_protects_originals(self):
        before = deepcopy(self.state)
        pending = self._pending()
        pending["pending"]["proposal-001"]["candidate"]["quality_s"] = -1
        self.assertEqual(self.alternative["quality_s"], 1200)
        self.assertEqual(before, self.state)
        self.assertEqual(self.original, self.state["plan"])

    def test_cannot_rollback_rejected_proposal(self):
        denied = decide(self._pending(), "proposal-001", "reject", expected_version=1)
        with self.assertRaises(RevisionConflict):
            rollback(denied, "proposal-001", expected_version=1)

    def test_pending_proposal_prevents_rollback(self):
        approved = decide(self._pending(), "proposal-001", "approve", expected_version=1)
        pending_again = propose(approved, "proposal-002", self.original, "reschedule",
                                expected_version=2, policy_id="H1")
        with self.assertRaises(RevisionConflict):
            rollback(pending_again, "proposal-001", expected_version=2)


if __name__ == "__main__":
    unittest.main()
