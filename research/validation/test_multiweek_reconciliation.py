"""Independent negative controls for source reconciliation and atomic ingest."""
import tempfile
from pathlib import Path
import unittest
from copy import deepcopy

from multiweek_reconciliation import (
    ingest_source_batch, reconcile_week, source_activity_id, validate_event)
from sqlite_ledger import Conflict, ResearchPlanStore


def event(source="manual", external="a", canonical="w1-run", status="completed",
          *, km=8.0, hard=0, easy=3000, week=1):
    return {"week": week, "source": source, "external_id": external,
            "verified_session_id": canonical, "discipline": "running",
            "status": status, "actual_km": km, "actual_hard_s": hard,
            "actual_easy_s": easy, "actual_sets": None}


class ReconciliationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="stride-multiweek-")
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "events.sqlite3"
        self.store = ResearchPlanStore(self.path)
        self.addCleanup(self.store.close)
        self.store.initialize({"week": 1, "planned_km": 8})
        self.plan = [{"id": "w1-run", "discipline": "running",
                      "planned_km": 8.0, "planned_hard_s": 0}]

    def reconcile(self):
        return reconcile_week(self.plan, self.store.activity_snapshot(), 1)

    def test_unknown_is_not_skipped_or_zero_actual_week(self):
        r = self.reconcile()
        self.assertEqual("unknown", r["observations"]["w1-run"]["status"])
        self.assertEqual(1, r["missing_or_unknown"])
        self.assertFalse(r["total_actual_complete"])
        self.assertEqual(0.0, r["known_actual_running_km"])

    def test_confirmed_skip_is_distinct_from_missing(self):
        skip = event(status="skipped", km=None, hard=None, easy=None)
        ingest_source_batch(self.store, [skip])
        r = self.reconcile()
        self.assertEqual(1, r["skipped"])
        self.assertEqual(0, r["missing_or_unknown"])
        self.assertTrue(r["total_actual_complete"])

    def test_partial_work_is_actual_not_planned(self):
        partial = event(status="partial", km=4.0, hard=600, easy=1200)
        ingest_source_batch(self.store, [partial])
        r = self.reconcile()
        self.assertEqual(4.0, r["known_actual_running_km"])
        self.assertEqual(600, r["known_actual_hard_s"])
        self.assertEqual(1, r["partial"])

    def test_same_source_event_replay_is_idempotent(self):
        x = event()
        self.assertEqual({"inserted": 1, "replayed": 0},
                         ingest_source_batch(self.store, [x]))
        self.assertEqual({"inserted": 0, "replayed": 1},
                         ingest_source_batch(self.store, [x]))
        self.assertEqual(1, len(self.store.activity_snapshot()))
        self.assertEqual(8.0, self.reconcile()["known_actual_running_km"])

    def test_cross_source_explicit_verified_match_counts_once(self):
        ingest_source_batch(self.store, [event("manual", "m1"),
                                          event("watch", "fit-123")])
        r = self.reconcile()
        self.assertEqual(8.0, r["known_actual_running_km"])
        self.assertEqual(2, len(r["observations"]["w1-run"]["sources"]))
        self.assertEqual([], r["conflicts"])

    def test_same_verified_session_with_conflicting_sources_is_quarantined(self):
        ingest_source_batch(self.store, [event("manual", "m1"),
                                          event("watch", "fit-123", km=9.0)])
        r = self.reconcile()
        self.assertEqual(["w1-run"], r["conflicts"])
        self.assertEqual(0, r["known_actual_running_km"])
        self.assertFalse(r["total_actual_complete"])

    def test_unverified_cross_source_matching_never_guessed(self):
        ingest_source_batch(self.store, [event("manual", "x1", canonical=None)])
        r = self.reconcile()
        self.assertEqual(["manual::x1"], r["unlinked_source_records"])
        self.assertEqual("unknown", r["observations"]["w1-run"]["status"])
        self.assertFalse(r["total_actual_complete"])

    def test_verified_unplanned_session_is_separate(self):
        extra = event("watch", "extra", canonical="extra-01",
                      status="unplanned", km=3.0, hard=900, easy=0)
        ingest_source_batch(self.store, [event(), extra])
        r = self.reconcile()
        self.assertEqual(["extra-01"], r["unplanned_verified"])
        self.assertEqual(11.0, r["known_actual_running_km"])
        self.assertEqual(900, r["known_actual_hard_s"])

    def test_conflicting_same_source_id_cannot_overwrite(self):
        ingest_source_batch(self.store, [event()])
        with self.assertRaises(Conflict):
            ingest_source_batch(self.store, [event(km=9)])
        self.assertEqual(8.0, self.reconcile()["known_actual_running_km"])

    def test_batch_partial_exception_rolls_back_all_rows(self):
        with self.assertRaisesRegex(RuntimeError, "SIMULATED_BATCH_INTERRUPTION"):
            ingest_source_batch(self.store, [event("watch", "A"),
                                             event("watch", "B", canonical="w1-other")],
                                inject_after=1)
        self.assertEqual([], self.store.activity_snapshot())
        self.assertEqual("ok", self.store.integrity_check())

    def test_batch_replay_after_restart_remains_idempotent(self):
        batch = [event("watch", "A"), event("watch", "B", canonical="w1-extra",
                                            status="unplanned", km=2, hard=0, easy=600)]
        ingest_source_batch(self.store, batch)
        new = ResearchPlanStore(self.path)
        self.addCleanup(new.close)
        self.assertEqual({"inserted": 0, "replayed": 2},
                         ingest_source_batch(new, batch))
        self.assertEqual(2, len(new.activity_snapshot()))
        self.assertEqual("ok", new.integrity_check())

    def test_batch_conflicting_duplicate_prevalidated_without_writes(self):
        with self.assertRaises(Conflict):
            ingest_source_batch(self.store, [event(), event(km=12.0)])
        self.assertEqual([], self.store.activity_snapshot())

    def test_unknown_with_numeric_dose_is_rejected(self):
        with self.assertRaises(ValueError):
            validate_event(event(status="unknown", km=8))
        self.assertEqual([], self.store.activity_snapshot())

    def test_skipped_with_nonzero_training_is_rejected(self):
        with self.assertRaises(ValueError):
            validate_event(event(status="skipped", km=2))
        self.assertEqual([], self.store.activity_snapshot())

    def test_cross_source_conflict_does_not_erase_observations(self):
        ingest_source_batch(self.store, [event("watch", "A"), event("manual", "M", km=9)])
        before = deepcopy(self.store.activity_snapshot())
        self.reconcile()
        self.assertEqual(before, self.store.activity_snapshot())


if __name__ == "__main__":
    unittest.main()
