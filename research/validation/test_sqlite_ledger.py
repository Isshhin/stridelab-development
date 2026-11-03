"""SQLite transaction, recovery, concurrency, and history tests on TEMP files.

Tests exercise independent research store. Not production Android Room/SQLite.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest

from sqlite_ledger import Conflict, ResearchPlanStore


class ResearchSQLiteLedgerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="stride-research-sql-")
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "test.sqlite3"
        self.plan = {"week": 1, "easy_km": 60, "quality_s": 1800}
        self.newplan = {"week": 1, "easy_km": 57, "quality_s": 1200}
        self.db = ResearchPlanStore(self.path)
        self.addCleanup(self.db.close)
        self.db.initialize(self.plan)
        self.db.record_activity("run-1", {"status": "completed", "actual_hard_s": 1500})

    def stage(self, identifier="p1"):
        self.db.propose(identifier, self.newplan, "Recovery-aware proposal",
                        "H1_research_only", expected_version=1)

    def _crash_child(self, op: str):
        script = '''
import os
from sqlite_ledger import ResearchPlanStore
s=ResearchPlanStore(%r)
if %r == "approve":
    s.decide("p1","approve",expected_version=1,inject=lambda:os._exit(19))
elif %r == "rollback":
    s.rollback("p1",expected_version=2,inject=lambda:os._exit(20))
''' % (str(self.path), op, op)
        r = subprocess.run([sys.executable, "-c", script], cwd=Path(__file__).parent,
                           capture_output=True, text=True, timeout=15, check=False)
        self.assertEqual(19 if op == "approve" else 20, r.returncode, r.stderr)

    def test_restart_preserves_plan_and_activity(self):
        other = ResearchPlanStore(self.path)
        self.addCleanup(other.close)
        self.assertEqual((1, self.plan), other.current())
        self.assertEqual([("run-1", {"status": "completed", "actual_hard_s": 1500})],
                         other.activity_snapshot())
        self.assertEqual("ok", other.integrity_check())

    def test_pending_is_durable_without_applied_change(self):
        self.stage()
        another = ResearchPlanStore(self.path)
        self.addCleanup(another.close)
        self.assertEqual((1, self.plan), another.current())
        self.assertEqual("pending", another.proposal_status("p1"))
        self.assertEqual([], another.audit_snapshot())

    def test_approve_survives_restart(self):
        self.stage()
        self.db.decide("p1", "approve", expected_version=1)
        another = ResearchPlanStore(self.path)
        self.addCleanup(another.close)
        self.assertEqual((2, self.newplan), another.current())
        self.assertEqual("approved", another.proposal_status("p1"))
        self.assertEqual("approved", another.audit_snapshot()[-1]["action"])

    def test_reject_does_not_modify_plan_and_is_durable(self):
        self.stage()
        self.db.decide("p1", "reject", expected_version=1)
        another = ResearchPlanStore(self.path)
        self.addCleanup(another.close)
        self.assertEqual((1, self.plan), another.current())
        self.assertEqual("rejected", another.proposal_status("p1"))
        self.assertEqual("rejected", another.audit_snapshot()[-1]["action"])

    def test_approved_rollback_survives_restart_and_keeps_activities(self):
        self.stage()
        self.db.decide("p1", "approve", expected_version=1)
        self.db.rollback("p1", expected_version=2)
        another = ResearchPlanStore(self.path)
        self.addCleanup(another.close)
        self.assertEqual((3, self.plan), another.current())
        self.assertEqual(["approved", "rollback"],
                         [row["action"] for row in another.audit_snapshot()])
        self.assertEqual([("run-1", {"status": "completed", "actual_hard_s": 1500})],
                         another.activity_snapshot())

    def test_stale_version_from_second_connection_fails(self):
        self.stage()
        second = ResearchPlanStore(self.path)
        self.addCleanup(second.close)
        self.db.decide("p1", "approve", expected_version=1)
        with self.assertRaises(Conflict):
            second.propose("p2", self.plan, "late", "B0", expected_version=1)
        with self.assertRaises(Conflict):
            second.rollback("p1", expected_version=1)
        self.assertEqual((2, self.newplan), second.current())

    def test_repeated_approve_and_rollback_blocked(self):
        self.stage()
        self.db.decide("p1", "approve", expected_version=1)
        with self.assertRaises(Conflict):
            self.db.decide("p1", "approve", expected_version=2)
        self.db.rollback("p1", expected_version=2)
        with self.assertRaises(Conflict):
            self.db.rollback("p1", expected_version=3)

    def test_duplicate_proposal_id_is_rejected_after_decision(self):
        self.stage()
        with self.assertRaises(Conflict):
            self.stage()
        self.db.decide("p1", "reject", expected_version=1)
        with self.assertRaises(Conflict):
            self.stage()

    def test_activities_idempotent_but_not_overwritable(self):
        self.db.record_activity("run-1", {"status": "completed", "actual_hard_s": 1500})
        with self.assertRaises(Conflict):
            self.db.record_activity("run-1", {"status": "skipped"})
        self.assertEqual(1, len(self.db.activity_snapshot()))

    def test_exception_between_approval_writes_rolls_back_transaction(self):
        self.stage()
        def crash():
            raise RuntimeError("injected error after plan update")
        with self.assertRaisesRegex(RuntimeError, "injected"):
            self.db.decide("p1", "approve", expected_version=1, inject=crash)
        self.assertEqual((1, self.plan), self.db.current())
        self.assertEqual("pending", self.db.proposal_status("p1"))
        self.assertEqual([], self.db.audit_snapshot())

    def test_abrupt_process_exit_during_approval_rolls_back(self):
        self.stage()
        self._crash_child("approve")
        fresh = ResearchPlanStore(self.path)
        self.addCleanup(fresh.close)
        self.assertEqual((1, self.plan), fresh.current())
        self.assertEqual("pending", fresh.proposal_status("p1"))
        self.assertEqual([], fresh.audit_snapshot())
        self.assertEqual("ok", fresh.integrity_check())

    def test_abrupt_process_exit_during_rollback_keeps_approved_state(self):
        self.stage()
        self.db.decide("p1", "approve", expected_version=1)
        self._crash_child("rollback")
        fresh = ResearchPlanStore(self.path)
        self.addCleanup(fresh.close)
        self.assertEqual((2, self.newplan), fresh.current())
        self.assertEqual(["approved"], [r["action"] for r in fresh.audit_snapshot()])
        self.assertEqual("ok", fresh.integrity_check())

    def test_unresolved_proposal_blocks_rollback(self):
        self.stage()
        self.db.decide("p1", "approve", expected_version=1)
        self.db.propose("p2", self.plan, "pending", "H1", expected_version=2)
        with self.assertRaises(Conflict):
            self.db.rollback("p1", expected_version=2)

    def test_stale_proposal_cannot_be_approved_after_new_plan_version(self):
        self.stage("p1")
        self.stage("p2")
        self.db.decide("p1", "approve", expected_version=1)
        with self.assertRaises(Conflict):
            self.db.decide("p2", "approve", expected_version=2)
        self.assertEqual((2, self.newplan), self.db.current())

    def test_integrity_wal_and_no_side_effect_on_original(self):
        self.stage()
        self.db.decide("p1", "approve", expected_version=1)
        self.assertEqual("wal", self.db.connection.execute("PRAGMA journal_mode").fetchone()[0])
        self.assertEqual("ok", self.db.integrity_check())
        self.assertEqual(self.plan, {"week": 1, "easy_km": 60, "quality_s": 1800})


if __name__ == "__main__":
    unittest.main()
